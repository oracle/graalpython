/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.NotImplementedError;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.GetTypeMemberNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.ToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols;
import com.oracle.graal.python.builtins.objects.cext.NativeMemberNames;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeClass;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeVoidPtr;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetInternalObjectArrayNode;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass.FlagsContainer;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetBaseClassNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetBaseClassesNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetInstanceShapeNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetMroStorageNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetNameNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetSubclassesNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetSulongTypeNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetSuperClassNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetTypeFlagsNodeFactory.GetTypeFlagsCachedNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.IsTypeNodeGen;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.object.GetLazyClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.truffle.PythonTypes;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.sequence.storage.MroSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.llvm.spi.ReferenceLibrary;

public abstract class TypeNodes {

    public abstract static class GetTypeFlagsNode extends com.oracle.truffle.api.nodes.Node {
        private static final int HEAPTYPE = 1 << 9;

        public abstract long execute(PythonAbstractClass clazz);

        abstract static class GetTypeFlagsCachedNode extends GetTypeFlagsNode {
            @Specialization(guards = "isInitialized(clazz)")
            long doInitialized(PythonManagedClass clazz) {
                return clazz.getFlagsContainer().flags;
            }

            @Specialization
            long doGeneric(PythonManagedClass clazz) {
                if (!isInitialized(clazz)) {
                    return getValue(clazz, clazz.getFlagsContainer());
                }
                return clazz.getFlagsContainer().flags;
            }

            @Specialization
            long doNative(PythonNativeClass clazz,
                            @Cached CExtNodes.GetTypeMemberNode getTpFlagsNode) {
                return (long) getTpFlagsNode.execute(clazz, NativeMemberNames.TP_FLAGS);
            }
        }

        private static final class GetTypeFlagsUncachedNode extends GetTypeFlagsNode {
            private static final GetTypeFlagsUncachedNode INSTANCE = new GetTypeFlagsUncachedNode();

            @Override
            public long execute(PythonAbstractClass clazz) {
                return doSlowPath(clazz);
            }

        }

        @TruffleBoundary
        private static long getValue(PythonManagedClass clazz, FlagsContainer fc) {
            // This method is only called from C code, i.e., the flags of the initial super class
            // must be available.
            if (fc.initialDominantBase != null) {
                fc.flags = doSlowPath(fc.initialDominantBase);
                fc.initialDominantBase = null;
                if (clazz instanceof PythonClass) {
                    // user classes are heap types
                    fc.flags |= HEAPTYPE;
                }
            }
            return fc.flags;
        }

        @TruffleBoundary
        private static long doSlowPath(PythonAbstractClass clazz) {
            if (PGuards.isManagedClass(clazz)) {
                PythonManagedClass mclazz = (PythonManagedClass) clazz;
                if (isInitialized(mclazz)) {
                    return mclazz.getFlagsContainer().flags;
                } else {
                    return getValue(mclazz, mclazz.getFlagsContainer());
                }
            } else if (PGuards.isNativeClass(clazz)) {
                return (long) CExtNodes.GetTypeMemberNode.getUncached().execute(clazz, NativeMemberNames.TP_FLAGS);
            }
            throw new IllegalStateException("unknown type");

        }

        protected static boolean isInitialized(PythonManagedClass clazz) {
            return clazz.getFlagsContainer().initialDominantBase == null;
        }

        public static GetTypeFlagsNode create() {
            return GetTypeFlagsCachedNodeGen.create();
        }

        public static GetTypeFlagsNode getUncached() {
            return GetTypeFlagsUncachedNode.INSTANCE;
        }
    }

    @GenerateUncached
    public abstract static class GetMroNode extends Node {

        public abstract PythonAbstractClass[] execute(Object obj);

        @Specialization
        PythonAbstractClass[] doIt(Object obj,
                        @Cached GetMroStorageNode getMroStorageNode) {
            return getMroStorageNode.execute(obj).getInternalClassArray();
        }

        public static GetMroNode create() {
            return TypeNodesFactory.GetMroNodeGen.create();
        }

        public static GetMroNode getUncached() {
            return TypeNodesFactory.GetMroNodeGen.getUncached();
        }
    }

    @ImportStatic(NativeMemberNames.class)
    @GenerateUncached
    public abstract static class GetMroStorageNode extends PNodeWithContext {

        public abstract MroSequenceStorage execute(Object obj);

        @Specialization
        MroSequenceStorage doPythonClass(PythonManagedClass obj) {
            return obj.getMethodResolutionOrder();
        }

        @Specialization
        MroSequenceStorage doBuiltinClass(PythonBuiltinClassType obj,
                        @CachedContext(PythonLanguage.class) PythonContext context) {
            return context.getCore().lookupType(obj).getMethodResolutionOrder();
        }

        @Specialization
        MroSequenceStorage doNativeClass(PythonNativeClass obj,
                        @Cached GetTypeMemberNode getTpMroNode,
                        @Cached PRaiseNode raise,
                        @Cached("createClassProfile()") ValueProfile tpMroProfile,
                        @Cached("createClassProfile()") ValueProfile storageProfile) {
            Object tupleObj = getTpMroNode.execute(obj, NativeMemberNames.TP_MRO);
            if (tupleObj == PNone.NO_VALUE) {
                // Special case: lazy type initialization (should happen at most only once per type)
                CompilerDirectives.transferToInterpreter();

                // call 'PyType_Ready' on the type
                int res = (int) PCallCapiFunction.getUncached().call(NativeCAPISymbols.FUN_PY_TYPE_READY, ToSulongNode.getUncached().execute(obj));
                if (res < 0) {
                    throw raise.raise(PythonBuiltinClassType.SystemError, "lazy initialization of type %s failed", GetNameNode.getUncached().execute(obj));
                }

                tupleObj = getTpMroNode.execute(obj, NativeMemberNames.TP_MRO);
                assert tupleObj != PNone.NO_VALUE : "MRO object is still NULL even after lazy type initialization";
            }
            Object profiled = tpMroProfile.profile(tupleObj);
            if (profiled instanceof PTuple) {
                SequenceStorage sequenceStorage = storageProfile.profile(((PTuple) tupleObj).getSequenceStorage());
                if (sequenceStorage instanceof MroSequenceStorage) {
                    return (MroSequenceStorage) sequenceStorage;
                }
            }
            throw raise.raise(PythonBuiltinClassType.SystemError, "invalid mro object");
        }

        @Specialization(replaces = {"doPythonClass", "doBuiltinClass", "doNativeClass"})
        @TruffleBoundary
        static MroSequenceStorage doSlowPath(Object obj) {
            if (obj instanceof PythonManagedClass) {
                return ((PythonManagedClass) obj).getMethodResolutionOrder();
            } else if (obj instanceof PythonBuiltinClassType) {
                return PythonLanguage.getCore().lookupType((PythonBuiltinClassType) obj).getMethodResolutionOrder();
            } else if (PGuards.isNativeClass(obj)) {
                Object tupleObj = GetTypeMemberNode.getUncached().execute(obj, NativeMemberNames.TP_MRO);
                if (tupleObj instanceof PTuple) {
                    SequenceStorage sequenceStorage = ((PTuple) tupleObj).getSequenceStorage();
                    if (sequenceStorage instanceof MroSequenceStorage) {
                        return (MroSequenceStorage) sequenceStorage;
                    }
                }
                throw PythonLanguage.getCore().raise(PythonBuiltinClassType.SystemError, "invalid mro object");
            }
            throw new IllegalStateException("unknown type " + obj.getClass().getName());
        }

        public static GetMroStorageNode create() {
            return GetMroStorageNodeGen.create();
        }

        public static GetMroStorageNode getUncached() {
            return GetMroStorageNodeGen.getUncached();
        }
    }

    @ImportStatic(NativeMemberNames.class)
    @GenerateUncached
    public abstract static class GetNameNode extends Node {

        public abstract String execute(Object obj);

        @Specialization
        String doManagedClass(PythonManagedClass obj) {
            return obj.getName();
        }

        @Specialization
        String doBuiltinClassType(PythonBuiltinClassType obj) {
            return obj.getName();
        }

        @Specialization
        String doNativeClass(PythonNativeClass obj,
                        @Cached CExtNodes.GetTypeMemberNode getTpNameNode) {
            return (String) getTpNameNode.execute(obj, NativeMemberNames.TP_NAME);
        }

        @Specialization(replaces = {"doManagedClass", "doBuiltinClassType", "doNativeClass"})
        @TruffleBoundary
        public static String doSlowPath(Object obj) {
            if (obj instanceof PythonManagedClass) {
                return ((PythonManagedClass) obj).getName();
            } else if (obj instanceof PythonBuiltinClassType) {
                return ((PythonBuiltinClassType) obj).getName();
            } else if (PGuards.isNativeClass(obj)) {
                return (String) CExtNodes.GetTypeMemberNode.getUncached().execute(obj, NativeMemberNames.TP_NAME);
            }
            throw new IllegalStateException("unknown type " + obj.getClass().getName());
        }

        public static GetNameNode create() {
            return GetNameNodeGen.create();
        }

        public static GetNameNode getUncached() {
            return GetNameNodeGen.getUncached();
        }
    }

    @GenerateUncached
    @TypeSystemReference(PythonTypes.class)
    @ImportStatic(NativeMemberNames.class)
    public abstract static class GetSuperClassNode extends Node {

        public abstract LazyPythonClass execute(Object obj);

        @Specialization
        LazyPythonClass doManaged(PythonManagedClass obj) {
            return obj.getSuperClass();
        }

        @Specialization
        LazyPythonClass doBuiltin(PythonBuiltinClassType obj) {
            return obj.getBase();
        }

        @Specialization
        LazyPythonClass doNative(PythonNativeClass obj,
                        @Cached GetTypeMemberNode getTpBaseNode,
                        @Cached PRaiseNode raise,
                        @Cached("createBinaryProfile()") ConditionProfile profile) {
            Object tpBaseObj = getTpBaseNode.execute(obj, NativeMemberNames.TP_BASE);
            if (profile.profile(PGuards.isClass(tpBaseObj))) {
                return (PythonAbstractClass) tpBaseObj;
            }
            CompilerDirectives.transferToInterpreter();
            throw raise.raise(SystemError, "Invalid base type object for class %s (base type was '%p' object).", GetNameNode.doSlowPath(obj), tpBaseObj);
        }

        public static GetSuperClassNode create() {
            return GetSuperClassNodeGen.create();
        }

        public static GetSuperClassNode getUncached() {
            return GetSuperClassNodeGen.getUncached();
        }
    }

    @TypeSystemReference(PythonTypes.class)
    @ImportStatic(NativeMemberNames.class)
    @GenerateUncached
    public abstract static class GetSubclassesNode extends PNodeWithContext {

        public abstract Set<PythonAbstractClass> execute(Object obj);

        @Specialization
        Set<PythonAbstractClass> doPythonClass(PythonManagedClass obj) {
            return obj.getSubClasses();
        }

        @Specialization
        Set<PythonAbstractClass> doPythonClass(PythonBuiltinClassType obj,
                        @CachedContext(PythonLanguage.class) PythonContext context) {
            return context.getCore().lookupType(obj).getSubClasses();
        }

        @Specialization
        Set<PythonAbstractClass> doNativeClass(PythonNativeClass obj,
                        @Cached GetTypeMemberNode getTpSubclassesNode,
                        @Cached("createClassProfile()") ValueProfile profile) {
            Object tpSubclasses = getTpSubclassesNode.execute(obj, NativeMemberNames.TP_SUBCLASSES);

            Object profiled = profile.profile(tpSubclasses);
            if (profiled instanceof PDict) {
                return wrapDict(profiled);
            }
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("invalid subclasses dict " + profiled.getClass().getName());
        }

        @TruffleBoundary
        private static Set<PythonAbstractClass> wrapDict(Object tpSubclasses) {
            return new Set<PythonAbstractClass>() {
                private final PDict dict = (PDict) tpSubclasses;

                public int size() {
                    return HashingStorageLibrary.getUncached().length(dict.getDictStorage());
                }

                public boolean isEmpty() {
                    return size() == 0;
                }

                public boolean contains(Object o) {
                    return HashingStorageLibrary.getUncached().hasKey(dict.getDictStorage(), o);
                }

                @SuppressWarnings("unchecked")
                public Iterator<PythonAbstractClass> iterator() {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new UnsupportedOperationException();
                }

                @TruffleBoundary
                public Object[] toArray() {
                    Object[] result = new Object[size()];
                    Iterator<Object> keys = HashingStorageLibrary.getUncached().keys(dict.getDictStorage());
                    for (int i = 0; i < result.length; i++) {
                        result[i] = keys.next();
                    }
                    return result;
                }

                @SuppressWarnings("unchecked")
                public <T> T[] toArray(T[] a) {
                    if (a.getClass() == Object[].class) {
                        return (T[]) toArray();
                    } else {
                        throw new UnsupportedOperationException();
                    }
                }

                public boolean add(PythonAbstractClass e) {
                    if (PGuards.isNativeClass(e)) {
                        dict.setItem(PythonNativeClass.cast(e).getPtr(), e);
                    }
                    dict.setItem(new PythonNativeVoidPtr(e), e);
                    return true;
                }

                public boolean remove(Object o) {
                    throw new UnsupportedOperationException();
                }

                public boolean containsAll(Collection<?> c) {
                    throw new UnsupportedOperationException();
                }

                public boolean addAll(Collection<? extends PythonAbstractClass> c) {
                    throw new UnsupportedOperationException();
                }

                public boolean retainAll(Collection<?> c) {
                    throw new UnsupportedOperationException();
                }

                public boolean removeAll(Collection<?> c) {
                    throw new UnsupportedOperationException();
                }

                public void clear() {
                    throw new UnsupportedOperationException();
                }

            };
        }

        public static GetSubclassesNode create() {
            return GetSubclassesNodeGen.create();
        }

        public static GetSubclassesNode getUncached() {
            return GetSubclassesNodeGen.getUncached();
        }

    }

    @GenerateUncached
    @ImportStatic(NativeMemberNames.class)
    public abstract static class GetBaseClassesNode extends PNodeWithContext {

        // TODO(fa): this should not return a Java array; maybe a SequenceStorage would fit
        public abstract PythonAbstractClass[] execute(Object obj);

        @Specialization
        static PythonAbstractClass[] doPythonClass(PythonManagedClass obj) {
            return obj.getBaseClasses();
        }

        @Specialization
        static PythonAbstractClass[] doPythonClass(PythonBuiltinClassType obj,
                        @CachedContext(PythonLanguage.class) PythonContext context) {
            return context.getCore().lookupType(obj).getBaseClasses();
        }

        @Specialization
        static PythonAbstractClass[] doNative(PythonNativeClass obj,
                        @Cached PRaiseNode raise,
                        @Cached GetTypeMemberNode getTpBasesNode,
                        @Cached("createClassProfile()") ValueProfile resultTypeProfile,
                        @Cached GetInternalObjectArrayNode toArrayNode) {
            Object result = resultTypeProfile.profile(getTpBasesNode.execute(obj, NativeMemberNames.TP_BASES));
            if (result instanceof PTuple) {
                Object[] values = toArrayNode.execute(((PTuple) result).getSequenceStorage());
                try {
                    return cast(values);
                } catch (ClassCastException e) {
                    throw raise.raise(PythonBuiltinClassType.SystemError, "unsupported object in 'tp_bases'");
                }
            }
            throw raise.raise(PythonBuiltinClassType.SystemError, "type does not provide bases");
        }

        // TODO: get rid of this
        private static PythonAbstractClass[] cast(Object[] arr) {
            PythonAbstractClass[] bases = new PythonAbstractClass[arr.length];
            for (int i = 0; i < arr.length; i++) {
                bases[i] = (PythonAbstractClass) arr[i];
            }
            return bases;
        }
    }

    @ImportStatic(NativeMemberNames.class)
    public abstract static class GetBaseClassNode extends PNodeWithContext {

        public abstract PythonAbstractClass execute(Object obj);

        @Specialization
        PythonAbstractClass doPythonClass(PythonManagedClass obj,
                        @Cached PRaiseNode raise) {
            PythonAbstractClass[] baseClasses = obj.getBaseClasses();
            if (baseClasses.length == 1) {
                return baseClasses[0];
            }
            throw raise.raise(NotImplementedError, "get bestBase case not yet implemented");
        }

        @Specialization
        PythonAbstractClass doPythonClass(PythonBuiltinClassType obj,
                        @Cached PRaiseNode raise,
                        @CachedContext(PythonLanguage.class) PythonContext context) {
            PythonAbstractClass[] baseClasses = context.getCore().lookupType(obj).getBaseClasses();
            if (baseClasses.length == 1) {
                return baseClasses[0];
            }
            throw raise.raise(NotImplementedError, "get bestBase case not yet implemented");
        }

        @Specialization
        PythonAbstractClass doNative(PythonNativeClass obj,
                        @Cached PRaiseNode raise,
                        @Cached GetTypeMemberNode getTpBaseNode,
                        @Cached("createClassProfile()") ValueProfile resultTypeProfile) {
            Object result = resultTypeProfile.profile(getTpBaseNode.execute(obj, NativeMemberNames.TP_BASE));
            if (result instanceof PythonAbstractClass) {
                return (PythonAbstractClass) result;
            }
            throw raise.raise(PythonBuiltinClassType.SystemError, "type does not provide __base__");
        }

        public static GetBaseClassNode create() {
            return GetBaseClassNodeGen.create();
        }
    }

    @GenerateUncached
    @ImportStatic(SpecialMethodNames.class)
    public abstract static class IsSameTypeNode extends PNodeWithContext {

        public abstract boolean execute(Object left, Object right);

        @Specialization
        boolean doManaged(PythonManagedClass left, PythonManagedClass right) {
            return left == right;
        }

        @Specialization
        boolean doManaged(PythonBuiltinClassType left, PythonBuiltinClassType right) {
            return left == right;
        }

        @Specialization
        boolean doManaged(PythonBuiltinClassType left, PythonBuiltinClass right) {
            return left == right.getType();
        }

        @Specialization
        boolean doManaged(PythonBuiltinClass left, PythonBuiltinClassType right) {
            return left.getType() == right;
        }

        @Specialization(limit = "3")
        boolean doNativeSingleContext(PythonAbstractNativeObject left, PythonAbstractNativeObject right,
                        @CachedLibrary("left") ReferenceLibrary referenceLibrary) {
            return referenceLibrary.isSame(left, right);
        }

        @Fallback
        boolean doOther(@SuppressWarnings("unused") Object left, @SuppressWarnings("unused") Object right) {
            return false;
        }
    }

    /** accesses the Sulong type of a class; does no recursive resolving */
    public abstract static class GetSulongTypeNode extends Node {

        public abstract Object execute(PythonAbstractClass clazz);

        @Specialization
        Object doInitialized(PythonManagedClass clazz) {
            return clazz.getSulongType();
        }

        @Specialization
        Object doNative(@SuppressWarnings("unused") PythonNativeClass clazz) {
            return null;
        }

        @TruffleBoundary
        public static Object getSlowPath(PythonAbstractClass clazz) {
            if (clazz instanceof PythonManagedClass) {
                return ((PythonManagedClass) clazz).getSulongType();
            } else if (PGuards.isNativeClass(clazz)) {
                return null;
            }
            throw new IllegalStateException("unknown type " + clazz.getClass().getName());
        }

        @TruffleBoundary
        public static void setSlowPath(PythonAbstractClass clazz, Object sulongType) {
            if (clazz instanceof PythonManagedClass) {
                ((PythonManagedClass) clazz).setSulongType(sulongType);
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
        public static PythonAbstractClass[] doSlowPath(PythonAbstractClass cls) {
            return computeMethodResolutionOrder(cls);
        }

        private static PythonAbstractClass[] computeMethodResolutionOrder(PythonAbstractClass cls) {
            CompilerAsserts.neverPartOfCompilation();

            PythonAbstractClass[] currentMRO = null;

            PythonAbstractClass[] baseClasses = GetBaseClassesNodeGen.getUncached().execute(cls);
            if (baseClasses.length == 0) {
                currentMRO = new PythonAbstractClass[]{cls};
            } else if (baseClasses.length == 1) {
                PythonAbstractClass[] baseMRO = GetMroNode.getUncached().execute(baseClasses[0]);

                if (baseMRO == null) {
                    currentMRO = new PythonAbstractClass[]{cls};
                } else {
                    currentMRO = new PythonAbstractClass[baseMRO.length + 1];
                    System.arraycopy(baseMRO, 0, currentMRO, 1, baseMRO.length);
                    currentMRO[0] = cls;
                }
            } else {
                MROMergeState[] toMerge = new MROMergeState[baseClasses.length + 1];

                for (int i = 0; i < baseClasses.length; i++) {
                    toMerge[i] = new MROMergeState(GetMroNode.getUncached().execute(baseClasses[i]));
                }

                toMerge[baseClasses.length] = new MROMergeState(baseClasses);
                ArrayList<PythonAbstractClass> mro = new ArrayList<>();
                mro.add(cls);
                currentMRO = mergeMROs(toMerge, mro);
            }
            return currentMRO;
        }

        private static PythonAbstractClass[] mergeMROs(MROMergeState[] toMerge, List<PythonAbstractClass> mro) {
            int idx;
            scan: for (idx = 0; idx < toMerge.length; idx++) {
                if (toMerge[idx].isMerged()) {
                    continue scan;
                }

                PythonAbstractClass candidate = toMerge[idx].getCandidate();
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

            return mro.toArray(new PythonAbstractClass[mro.size()]);
        }

    }

    @GenerateUncached
    @ImportStatic(PGuards.class)
    public abstract static class IsTypeNode extends Node {

        public abstract boolean execute(Object obj);

        @Specialization
        boolean doManagedClass(@SuppressWarnings("unused") PythonManagedClass obj) {
            return true;
        }

        @Specialization
        boolean doBuiltinType(@SuppressWarnings("unused") PythonBuiltinClassType obj) {
            return true;
        }

        @Specialization
        boolean doNativeClass(PythonAbstractNativeObject obj,
                        @Cached IsBuiltinClassProfile profile,
                        @Cached GetLazyClassNode getClassNode) {
            // TODO(fa): this check may not be enough since a type object may indirectly inherit
            // from 'type'
            // CPython has two different checks if some object is a type:
            // 1. test if type flag 'Py_TPFLAGS_TYPE_SUBCLASS' is set
            // 2. test if attribute '__bases__' is a tuple
            return profile.profileClass(getClassNode.execute(obj), PythonBuiltinClassType.PythonClass);
        }

        @Specialization(guards = "!isClass(obj)")
        boolean doOther(@SuppressWarnings("unused") Object obj) {
            return false;
        }

        public static IsTypeNode create() {
            return IsTypeNodeGen.create();
        }

        public static IsTypeNode getUncached() {
            return IsTypeNodeGen.getUncached();
        }
    }

    @ImportStatic(PGuards.class)
    public abstract static class GetInstanceShape extends PNodeWithContext {

        public abstract Shape execute(LazyPythonClass clazz);

        @Specialization
        Shape doBuiltinClassType(PythonBuiltinClassType clazz) {
            return clazz.getInstanceShape();
        }

        @Specialization
        Shape doManagedClass(PythonManagedClass clazz) {
            return clazz.getInstanceShape();
        }

        @Specialization(guards = {"!isManagedClass(clazz)", "!isPythonBuiltinClassType(clazz)"})
        Shape doError(@SuppressWarnings("unused") LazyPythonClass clazz,
                        @Cached PRaiseNode raise) {
            throw raise.raise(PythonBuiltinClassType.SystemError, "cannot get shape of native class");
        }

        public static Shape doSlowPath(LazyPythonClass clazz) {
            if (clazz instanceof PythonBuiltinClassType) {
                return ((PythonBuiltinClassType) clazz).getInstanceShape();
            } else if (clazz instanceof PythonManagedClass) {
                return ((PythonManagedClass) clazz).getInstanceShape();
            }
            throw PythonLanguage.getCore().raise(PythonBuiltinClassType.SystemError, "cannot get shape of native class");
        }

        public static GetInstanceShape create() {
            return GetInstanceShapeNodeGen.create();
        }
    }
}
