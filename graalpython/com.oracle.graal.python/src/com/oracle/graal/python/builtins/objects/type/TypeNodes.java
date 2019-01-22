/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.builtins.objects.type;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__EQ__;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.GetTypeMemberNode;
import com.oracle.graal.python.builtins.objects.cext.NativeMemberNames;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeClass;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeObject;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.ManagedPythonClass.FlagsContainer;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetBaseClassesNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetMroNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetNameNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetSubclassesNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetSulongTypeNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetSuperClassNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetTypeFlagsNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.IsSameTypeNodeGen;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.runtime.sequence.storage.MroSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ValueProfile;

public abstract class TypeNodes {

    public abstract static class GetTypeFlagsNode extends PNodeWithContext {

        public abstract long execute(AbstractPythonClass clazz);

        @Specialization(guards = "isInitialized(clazz)")
        long doInitialized(ManagedPythonClass clazz) {
            return clazz.getFlagsContainer().flags;
        }

        @Specialization
        long doGeneric(ManagedPythonClass clazz) {
            if (!isInitialized(clazz)) {
                return getValue(clazz.getFlagsContainer());
            }
            return clazz.getFlagsContainer().flags;
        }

        @Specialization
        long doNative(PythonNativeClass clazz,
                        @Cached("createReadNode()") Node readNode) {
            return doNativeGeneric(clazz, readNode);
        }

        @TruffleBoundary
        private static long getValue(FlagsContainer fc) {
            // This method is only called from C code, i.e., the flags of the initial super class
            // must be available.
            if (fc.initialDominantBase != null) {
                fc.flags = doSlowPath(fc.initialDominantBase);
                fc.initialDominantBase = null;
            }
            return fc.flags;
        }

        @TruffleBoundary
        public static long doSlowPath(AbstractPythonClass clazz) {
            if (clazz instanceof ManagedPythonClass) {
                ManagedPythonClass mclazz = (ManagedPythonClass) clazz;
                if (isInitialized(mclazz)) {
                    return mclazz.getFlagsContainer().flags;
                } else {
                    return getValue(mclazz.getFlagsContainer());
                }
            } else if (clazz instanceof PythonNativeClass) {
                return doNativeGeneric((PythonNativeClass) clazz, createReadNode());
            }
            throw new IllegalStateException("unknown type");

        }

        static long doNativeGeneric(PythonNativeClass clazz, Node readNode) {
            try {
                return (long) ForeignAccess.sendRead(readNode, (TruffleObject) clazz.getPtr(), NativeMemberNames.TP_FLAGS);
            } catch (UnknownIdentifierException | UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw e.raise();
            }
        }

        protected static boolean isInitialized(ManagedPythonClass clazz) {
            return clazz.getFlagsContainer().initialDominantBase == null;
        }

        protected static Node createReadNode() {
            return Message.READ.createNode();
        }

        public static GetTypeFlagsNode create() {
            return GetTypeFlagsNodeGen.create();
        }
    }

    @ImportStatic(NativeMemberNames.class)
    public abstract static class GetMroNode extends PNodeWithContext {

        public abstract AbstractPythonClass[] execute(Object obj);

        @Specialization
        AbstractPythonClass[] doPythonClass(ManagedPythonClass obj) {
            return obj.getMethodResolutionOrder();
        }

        @Specialization
        AbstractPythonClass[] doPythonClass(PythonBuiltinClassType obj) {
            return getBuiltinPythonClass(obj).getMethodResolutionOrder();
        }

        @Specialization
        AbstractPythonClass[] doNativeClass(PythonNativeClass obj,
                        @Cached("create(TP_MRO)") GetTypeMemberNode getTpMroNode) {
            Object tupleObj = getTpMroNode.execute(obj);
            if (tupleObj instanceof PTuple) {
                SequenceStorage sequenceStorage = ((PTuple) tupleObj).getSequenceStorage();
                if (sequenceStorage instanceof MroSequenceStorage) {
                    return ((MroSequenceStorage) sequenceStorage).getInternalClassArray();
                }
            }
            throw raise(PythonBuiltinClassType.SystemError, "invalid mro object");
        }

        @TruffleBoundary
        public static AbstractPythonClass[] doSlowPath(Object obj) {
            if (obj instanceof ManagedPythonClass) {
                return ((ManagedPythonClass) obj).getMethodResolutionOrder();
            } else if (obj instanceof PythonBuiltinClassType) {
                return PythonLanguage.getCore().lookupType((PythonBuiltinClassType) obj).getMethodResolutionOrder();
            } else if (obj instanceof PythonNativeClass) {
                Object tupleObj = GetTypeMemberNode.doSlowPath(obj, NativeMemberNames.TP_MRO);
                if (tupleObj instanceof PTuple) {
                    SequenceStorage sequenceStorage = ((PTuple) tupleObj).getSequenceStorage();
                    if (sequenceStorage instanceof MroSequenceStorage) {
                        return ((MroSequenceStorage) sequenceStorage).getInternalClassArray();
                    }
                }
                throw PythonLanguage.getCore().raise(PythonBuiltinClassType.SystemError, "invalid mro object");
            }
            throw new IllegalStateException("unknown type " + obj.getClass().getName());
        }

        public static GetMroNode create() {
            return GetMroNodeGen.create();
        }
    }

    @ImportStatic(NativeMemberNames.class)
    public abstract static class GetNameNode extends PNodeWithContext {

        public abstract String execute(Object obj);

        @Specialization
        String doManagedClass(ManagedPythonClass obj) {
            return obj.getName();
        }

        @Specialization
        String doBuiltinClassType(PythonBuiltinClassType obj) {
            return obj.getName();
        }

        @Specialization
        String doNativeClass(PythonNativeClass obj,
                        @Cached("create(TP_NAME)") CExtNodes.GetTypeMemberNode getTpNameNode) {
            return (String) getTpNameNode.execute(obj);
        }

        @TruffleBoundary
        public static String doSlowPath(Object obj) {
            if (obj instanceof ManagedPythonClass) {
                return ((ManagedPythonClass) obj).getName();
            } else if (obj instanceof PythonBuiltinClassType) {
                // TODO(fa): remove this special case
                if (obj == PythonBuiltinClassType.TruffleObject) {
                    return BuiltinNames.FOREIGN;
                }
                return ((PythonBuiltinClassType) obj).getName();
            } else if (obj instanceof PythonNativeClass) {
                return (String) CExtNodes.GetTypeMemberNode.doSlowPath(obj, NativeMemberNames.TP_NAME);
            }
            throw new IllegalStateException("unknown type " + obj.getClass().getName());
        }

        public static GetNameNode create() {
            return GetNameNodeGen.create();
        }

    }

    public abstract static class GetSuperClassNode extends PNodeWithContext {

        public abstract LazyPythonClass execute(Object obj);

        @Specialization
        LazyPythonClass doPythonClass(ManagedPythonClass obj) {
            return obj.getSuperClass();
        }

        @Specialization
        LazyPythonClass doPythonClass(PythonBuiltinClassType obj) {
            return obj.getBase();
        }

        @TruffleBoundary
        public static LazyPythonClass doSlowPath(Object obj) {
            if (obj instanceof ManagedPythonClass) {
                return ((ManagedPythonClass) obj).getSuperClass();
            } else if (obj instanceof PythonBuiltinClassType) {
                return ((PythonBuiltinClassType) obj).getBase();
            } else if (obj instanceof PythonNativeClass) {
                // TODO implement
            }
            throw new IllegalStateException("unknown type " + obj.getClass().getName());
        }

        public static GetSuperClassNode create() {
            return GetSuperClassNodeGen.create();
        }

    }

    public abstract static class GetSubclassesNode extends PNodeWithContext {

        public abstract Set<AbstractPythonClass> execute(Object obj);

        @Specialization
        Set<AbstractPythonClass> doPythonClass(ManagedPythonClass obj) {
            return obj.getSubClasses();
        }

        @Specialization
        Set<AbstractPythonClass> doPythonClass(PythonBuiltinClassType obj) {
            return getBuiltinPythonClass(obj).getSubClasses();
        }

        @TruffleBoundary
        public static Set<AbstractPythonClass> doSlowPath(Object obj) {
            if (obj instanceof ManagedPythonClass) {
                return ((ManagedPythonClass) obj).getSubClasses();
            } else if (obj instanceof PythonBuiltinClassType) {
                return PythonLanguage.getCore().lookupType((PythonBuiltinClassType) obj).getSubClasses();
            } else if (obj instanceof PythonNativeClass) {
                // TODO implement
            }
            throw new IllegalStateException("unknown type " + obj.getClass().getName());
        }

        public static GetSubclassesNode create() {
            return GetSubclassesNodeGen.create();
        }

    }

    @ImportStatic(NativeMemberNames.class)
    public abstract static class GetBaseClassesNode extends PNodeWithContext {

        // TODO(fa): this should not return a Java array; maybe a SequenceStorage would fit
        public abstract AbstractPythonClass[] execute(Object obj);

        @Specialization
        AbstractPythonClass[] doPythonClass(ManagedPythonClass obj) {
            return obj.getBaseClasses();
        }

        @Specialization
        AbstractPythonClass[] doPythonClass(PythonBuiltinClassType obj) {
            return getBuiltinPythonClass(obj).getBaseClasses();
        }

        @Specialization
        AbstractPythonClass[] doNative(PythonNativeClass obj,
                        @Cached("create(TP_BASES)") GetTypeMemberNode getTpBasesNode,
                        @Cached("createClassProfile()") ValueProfile resultTypeProfile,
                        @Cached("createToArray()") SequenceStorageNodes.ToArrayNode toArrayNode) {
            Object result = resultTypeProfile.profile(getTpBasesNode.execute(obj));
            if (result instanceof PTuple) {
                Object[] values = toArrayNode.execute(((PTuple) result).getSequenceStorage());
                try {
                    return cast(values);
                } catch (ClassCastException e) {
                    throw raise(PythonBuiltinClassType.SystemError, "unsupported object in 'tp_bases'");
                }
            }
            throw raise(PythonBuiltinClassType.SystemError, "type does not provide bases");
        }

        @TruffleBoundary
        public static AbstractPythonClass[] doSlowPath(Object obj) {
            if (obj instanceof ManagedPythonClass) {
                return ((ManagedPythonClass) obj).getBaseClasses();
            } else if (obj instanceof PythonBuiltinClassType) {
                return PythonLanguage.getCore().lookupType((PythonBuiltinClassType) obj).getBaseClasses();
            } else if (obj instanceof PythonNativeClass) {
                Object basesObj = GetTypeMemberNode.doSlowPath(obj, NativeMemberNames.TP_BASES);
                if (!(basesObj instanceof PTuple)) {
                    throw PythonLanguage.getCore().raise(PythonBuiltinClassType.SystemError, "invalid type of tp_bases (was %p)", basesObj);
                }
                PTuple basesTuple = (PTuple) basesObj;
                try {
                    return cast(SequenceStorageNodes.ToArrayNode.doSlowPath(basesTuple.getSequenceStorage()));
                } catch (ClassCastException e) {
                    throw PythonLanguage.getCore().raise(PythonBuiltinClassType.SystemError, "unsupported object in 'tp_bases' (msg: %s)", e.getMessage());
                }
            }
            throw new IllegalStateException("unknown type " + obj.getClass().getName());
        }

        protected static SequenceStorageNodes.ToArrayNode createToArray() {
            return SequenceStorageNodes.ToArrayNode.create(false);
        }

        public static GetBaseClassesNode create() {
            return GetBaseClassesNodeGen.create();
        }

        // TODO: get rid of this
        private static AbstractPythonClass[] cast(Object[] arr) {
            AbstractPythonClass[] bases = new AbstractPythonClass[arr.length];
            for (int i = 0; i < arr.length; i++) {
                bases[i] = (AbstractPythonClass) arr[i];
            }
            return bases;
        }

    }

    @ImportStatic(SpecialMethodNames.class)
    public abstract static class IsSameTypeNode extends PNodeWithContext {

        public abstract boolean execute(Object left, Object right);

        @Specialization
        boolean doManaged(ManagedPythonClass left, ManagedPythonClass right) {
            return left == right;
        }

        @Specialization
        boolean doNative(PythonNativeClass left, PythonNativeClass right,
                        @Cached("create(__EQ__)") CExtNodes.PointerCompareNode pointerCompareNode) {
            return pointerCompareNode.execute(left, right);
        }

        @Specialization
        boolean doNative(PythonNativeObject left, PythonNativeObject right,
                        @Cached("create(__EQ__)") CExtNodes.PointerCompareNode pointerCompareNode) {
            return pointerCompareNode.execute(left, right);
        }

        @Fallback
        boolean doOther(@SuppressWarnings("unused") Object left, @SuppressWarnings("unused") Object right) {
            return false;
        }

        @TruffleBoundary
        public static boolean doSlowPath(Object left, Object right) {
            if (left instanceof ManagedPythonClass && right instanceof ManagedPythonClass) {
                return left == right;
            } else if (left instanceof PythonNativeClass && right instanceof PythonNativeClass) {
                return CExtNodes.PointerCompareNode.create(__EQ__).execute((PythonNativeClass) left, (PythonNativeClass) right);
            } else if (left instanceof PythonNativeObject && right instanceof PythonNativeObject) {
                return CExtNodes.PointerCompareNode.create(__EQ__).execute((PythonNativeObject) left, (PythonNativeObject) right);
            }
            return false;
        }

        public static IsSameTypeNode create() {
            return IsSameTypeNodeGen.create();
        }

    }

    /** accesses the Sulong type of a class; does no recursive resolving */
    public abstract static class GetSulongTypeNode extends Node {

        public abstract Object execute(AbstractPythonClass clazz);

        @Specialization
        Object doInitialized(ManagedPythonClass clazz) {
            return clazz.getSulongType();
        }

        @Specialization
        Object doNative(@SuppressWarnings("unused") PythonNativeClass clazz) {
            return null;
        }

        @TruffleBoundary
        public static Object getSlowPath(AbstractPythonClass clazz) {
            if (clazz instanceof ManagedPythonClass) {
                return ((ManagedPythonClass) clazz).getSulongType();
            } else if (clazz instanceof PythonNativeClass) {
                return null;
            }
            throw new IllegalStateException("unknown type " + clazz.getClass().getName());
        }

        @TruffleBoundary
        public static void setSlowPath(AbstractPythonClass clazz, Object sulongType) {
            if (clazz instanceof ManagedPythonClass) {
                ((ManagedPythonClass) clazz).setSulongType(sulongType);
            } else {
                throw new IllegalStateException("cannot set Sulong type for " + clazz.getClass().getName());
            }
        }

        public static GetSulongTypeNode create() {
            return GetSulongTypeNodeGen.create();
        }

    }

    public abstract static class ComputeMroNode extends Node {

        @TruffleBoundary
        public static AbstractPythonClass[] doSlowPath(AbstractPythonClass cls) {
            return computeMethodResolutionOrder(cls);
        }

        private static AbstractPythonClass[] computeMethodResolutionOrder(AbstractPythonClass cls) {
            CompilerAsserts.neverPartOfCompilation();

            AbstractPythonClass[] currentMRO = null;

            AbstractPythonClass[] baseClasses = GetBaseClassesNode.doSlowPath(cls);
            if (baseClasses.length == 0) {
                currentMRO = new AbstractPythonClass[]{cls};
            } else if (baseClasses.length == 1) {
                AbstractPythonClass[] baseMRO = GetMroNode.doSlowPath(baseClasses[0]);

                if (baseMRO == null) {
                    currentMRO = new AbstractPythonClass[]{cls};
                } else {
                    currentMRO = new AbstractPythonClass[baseMRO.length + 1];
                    System.arraycopy(baseMRO, 0, currentMRO, 1, baseMRO.length);
                    currentMRO[0] = cls;
                }
            } else {
                MROMergeState[] toMerge = new MROMergeState[baseClasses.length + 1];

                for (int i = 0; i < baseClasses.length; i++) {
                    toMerge[i] = new MROMergeState();
                    toMerge[i].mro = GetMroNode.doSlowPath(baseClasses[i]);
                }

                toMerge[baseClasses.length] = new MROMergeState();
                toMerge[baseClasses.length].mro = baseClasses;
                ArrayList<AbstractPythonClass> mro = new ArrayList<>();
                mro.add(cls);
                currentMRO = mergeMROs(toMerge, mro);
            }

// for (AbstractPythonClass c : currentMRO) {
// if (c instanceof PythonNativeClass) {
// needsNativeAllocation = true;
// break;
// }
// }

            return currentMRO;
        }

        private static AbstractPythonClass[] mergeMROs(MROMergeState[] toMerge, List<AbstractPythonClass> mro) {
            int idx;
            scan: for (idx = 0; idx < toMerge.length; idx++) {
                if (toMerge[idx].isMerged()) {
                    continue scan;
                }

                AbstractPythonClass candidate = toMerge[idx].getCandidate();
                for (MROMergeState mergee : toMerge) {
                    if (mergee.pastnextContains(candidate)) {
                        continue scan;
                    }
                }

                mro.add(candidate);

                for (MROMergeState element : toMerge) {
                    element.noteMerged(candidate);
                }

                // restart scan
                idx = -1;
            }

            for (MROMergeState mergee : toMerge) {
                if (!mergee.isMerged()) {
                    throw new IllegalStateException();
                }
            }

            return mro.toArray(new AbstractPythonClass[mro.size()]);
        }

    }

}
