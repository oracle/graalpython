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
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.objects.type.TypeBuiltins.TYPE_FLAGS;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.BASETYPE;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.BASE_EXC_SUBCLASS;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.BYTES_SUBCLASS;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.DEFAULT;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.DICT_SUBCLASS;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.HAVE_GC;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.HEAPTYPE;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.LIST_SUBCLASS;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.LONG_SUBCLASS;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.METHOD_DESCRIPTOR;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.READY;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.TUPLE_SUBCLASS;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.TYPE_SUBCLASS;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.UNICODE_SUBCLASS;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__DICT__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__SLOTS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.MRO;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETATTRIBUTE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEW__;

import java.util.ArrayList;
import java.util.Arrays;
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
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory;
import com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols;
import com.oracle.graal.python.builtins.objects.cext.NativeMember;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeClass;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeVoidPtr;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes.GetDictStorageNode;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.common.PHashingCollection;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes.GetObjectArrayNode;
import com.oracle.graal.python.builtins.objects.common.SequenceNodesFactory.GetObjectArrayNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetInternalObjectArrayNode;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetBaseClassNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetBaseClassesNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetInstanceShapeNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetMroStorageNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetNameNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetSolidBaseNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetSubclassesNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetSulongTypeNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.IsAcceptableBaseNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.IsTypeNodeGen;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.call.special.LookupSpecialMethodNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.truffle.PythonTypes;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.MroSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;

public abstract class TypeNodes {

    @GenerateUncached
    public abstract static class GetTypeFlagsNode extends Node {

        public abstract long execute(Object clazz);

        @Specialization
        static long doBuiltinClassType(PythonBuiltinClassType clazz) {
            long result;
            switch (clazz) {
                case PythonObject:
                    result = DEFAULT | BASETYPE;
                    break;
                case PythonClass:
                    result = DEFAULT | HAVE_GC | BASETYPE | TYPE_SUBCLASS;
                    break;
                case Super:
                case PythonModule:
                case PSet:
                case PFrozenSet:
                case PReferenceType:
                    result = DEFAULT | HAVE_GC | BASETYPE;
                    break;
                case Boolean:
                    result = DEFAULT | LONG_SUBCLASS;
                    break;
                case PBytes:
                    result = DEFAULT | BASETYPE | BYTES_SUBCLASS;
                    break;
                case PFunction:
                case PBuiltinFunction:
                    result = DEFAULT | HAVE_GC | METHOD_DESCRIPTOR;
                    break;
                case PMethod:
                case PBuiltinMethod:
                case GetSetDescriptor:
                case PMappingproxy:
                case PFrame:
                case PGenerator:
                case PMemoryView:
                case PBuffer:
                case PSlice:
                case PTraceback:
                    result = DEFAULT | HAVE_GC;
                    break;
                case PDict:
                    result = DEFAULT | HAVE_GC | BASETYPE | DICT_SUBCLASS;
                    break;
                case PBaseException:
                    result = DEFAULT | HAVE_GC | BASETYPE | BASE_EXC_SUBCLASS;
                    break;
                case PList:
                    result = DEFAULT | HAVE_GC | BASETYPE | LIST_SUBCLASS;
                    break;
                case PInt:
                    result = DEFAULT | BASETYPE | LONG_SUBCLASS;
                    break;
                case PString:
                    result = DEFAULT | BASETYPE | UNICODE_SUBCLASS;
                    break;
                case PTuple:
                    result = DEFAULT | HAVE_GC | BASETYPE | TUPLE_SUBCLASS;
                    break;
                default:
                    // default case; this includes:
                    // PythonObject, PByteArray, PCode, PInstancemethod, PFloat, PNone,
                    // PNotImplemented, PEllipsis
                    result = DEFAULT | (clazz.isAcceptableBase() ? BASETYPE : 0) | (PythonBuiltinClassType.isExceptionType(clazz) ? BASE_EXC_SUBCLASS : 0L);
                    break;
            }
            // we always claim that all types are fully initialized
            return result | READY;
        }

        @Specialization
        static long doBuiltinClass(PythonBuiltinClass clazz) {
            return doBuiltinClassType(clazz.getType());
        }

        @Specialization
        static long doPythonClass(PythonClass clazz,
                        @Cached ReadAttributeFromObjectNode readHiddenFlagsNode,
                        @Cached WriteAttributeToObjectNode writeHiddenFlagsNode,
                        @Cached("createCountingProfile()") ConditionProfile profile) {

            Object flagsObject = readHiddenFlagsNode.execute(clazz, TYPE_FLAGS);
            if (profile.profile(flagsObject != PNone.NO_VALUE)) {
                // we have it under control; it must be a long
                return (long) flagsObject;
            }
            long flags = computeFlags(clazz);
            writeHiddenFlagsNode.execute(clazz, TYPE_FLAGS, flags);
            return flags;
        }

        @Specialization
        static long doNative(PythonNativeClass clazz,
                        @Cached CExtNodes.GetTypeMemberNode getTpFlagsNode) {
            return (long) getTpFlagsNode.execute(clazz, NativeMember.TP_FLAGS);
        }

        @TruffleBoundary
        private static long computeFlags(PythonClass clazz) {

            // according to 'type_new' in 'typeobject.c', all have DEFAULT, HEAPTYPE, and BASETYPE.
            // The HAVE_GC is inherited. But we do not mimic this behavior in every detail, so it
            // should be fine to just set it.
            long result = DEFAULT | HEAPTYPE | BASETYPE | HAVE_GC;

            // flags are inherited
            MroSequenceStorage mroStorage = GetMroStorageNodeGen.getUncached().execute(clazz);
            int n = SequenceStorageNodes.LenNode.getUncached().execute(mroStorage);
            for (int i = 0; i < n; i++) {
                Object mroEntry = SequenceStorageNodes.GetItemDynamicNode.getUncached().execute(mroStorage, i);
                if (mroEntry instanceof PythonBuiltinClass) {
                    result |= doBuiltinClass((PythonBuiltinClass) mroEntry);
                } else if (mroEntry instanceof PythonBuiltinClassType) {
                    result |= doBuiltinClassType((PythonBuiltinClassType) mroEntry);
                } else if (mroEntry instanceof PythonAbstractNativeObject) {
                    result |= doNative((PythonAbstractNativeObject) mroEntry, CExtNodesFactory.GetTypeMemberNodeGen.getUncached());
                }
                // 'PythonClass' is intentionally ignored because they do not actually add any
                // interesting flags except that we already specify before the loop
            }
            return result;
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

    @GenerateUncached
    public abstract static class GetMroStorageNode extends PNodeWithContext {

        public abstract MroSequenceStorage execute(Object obj);

        @Specialization
        static MroSequenceStorage doPythonClass(PythonManagedClass obj,
                        @Cached("createBinaryProfile()") ConditionProfile notInitialized) {
            if (!notInitialized.profile(obj.getMethodResolutionOrder().isInitialized())) {
                obj.getMethodResolutionOrder().setInternalArrayObject(TypeNodes.ComputeMroNode.doSlowPath(obj, false));
            }
            return obj.getMethodResolutionOrder();
        }

        @Specialization
        static MroSequenceStorage doBuiltinClass(PythonBuiltinClassType obj,
                        @CachedContext(PythonLanguage.class) PythonContext context) {
            return context.getCore().lookupType(obj).getMethodResolutionOrder();
        }

        @Specialization
        static MroSequenceStorage doNativeClass(PythonNativeClass obj,
                        @Cached GetTypeMemberNode getTpMroNode,
                        @Cached PRaiseNode raise,
                        @Cached("createBinaryProfile()") ConditionProfile lazyTypeInitProfile,
                        @Cached("createClassProfile()") ValueProfile tpMroProfile,
                        @Cached("createIdentityProfile()") ValueProfile storageProfile) {
            Object tupleObj = getTpMroNode.execute(obj, NativeMember.TP_MRO);
            if (lazyTypeInitProfile.profile(tupleObj == PNone.NO_VALUE)) {
                // Special case: lazy type initialization (should happen at most only once per type)
                CompilerDirectives.transferToInterpreter();

                // call 'PyType_Ready' on the type
                int res = (int) PCallCapiFunction.getUncached().call(NativeCAPISymbols.FUN_PY_TYPE_READY, ToSulongNode.getUncached().execute(obj));
                if (res < 0) {
                    throw raise.raise(PythonBuiltinClassType.SystemError, ErrorMessages.LAZY_INITIALIZATION_FAILED, GetNameNode.getUncached().execute(obj));
                }

                tupleObj = getTpMroNode.execute(obj, NativeMember.TP_MRO);
                assert tupleObj != PNone.NO_VALUE : "MRO object is still NULL even after lazy type initialization";
            }
            Object profiled = tpMroProfile.profile(tupleObj);
            if (profiled instanceof PTuple) {
                SequenceStorage sequenceStorage = storageProfile.profile(((PTuple) profiled).getSequenceStorage());
                if (sequenceStorage instanceof MroSequenceStorage) {
                    return (MroSequenceStorage) sequenceStorage;
                }
            }
            throw raise.raise(PythonBuiltinClassType.SystemError, ErrorMessages.INVALID_MRO_OBJ);
        }

        @Specialization(replaces = {"doPythonClass", "doBuiltinClass", "doNativeClass"})
        @TruffleBoundary
        static MroSequenceStorage doSlowPath(Object obj) {
            if (obj instanceof PythonManagedClass) {
                return doPythonClass((PythonManagedClass) obj, ConditionProfile.getUncached());
            } else if (obj instanceof PythonBuiltinClassType) {
                return PythonLanguage.getCore().lookupType((PythonBuiltinClassType) obj).getMethodResolutionOrder();
            } else if (PGuards.isNativeClass(obj)) {
                Object tupleObj = GetTypeMemberNode.getUncached().execute(obj, NativeMember.TP_MRO);
                if (tupleObj instanceof PTuple) {
                    SequenceStorage sequenceStorage = ((PTuple) tupleObj).getSequenceStorage();
                    if (sequenceStorage instanceof MroSequenceStorage) {
                        return (MroSequenceStorage) sequenceStorage;
                    }
                }
                throw PythonLanguage.getCore().raise(PythonBuiltinClassType.SystemError, ErrorMessages.INVALID_MRO_OBJ);
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
            return (String) getTpNameNode.execute(obj, NativeMember.TP_NAME);
        }

        @Specialization(replaces = {"doManagedClass", "doBuiltinClassType", "doNativeClass"})
        @TruffleBoundary
        public static String doSlowPath(Object obj) {
            if (obj instanceof PythonManagedClass) {
                return ((PythonManagedClass) obj).getName();
            } else if (obj instanceof PythonBuiltinClassType) {
                return ((PythonBuiltinClassType) obj).getName();
            } else if (PGuards.isNativeClass(obj)) {
                return (String) CExtNodes.GetTypeMemberNode.getUncached().execute(obj, NativeMember.TP_NAME);
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
    public abstract static class GetSuperClassNode extends Node {

        public abstract Object execute(Object obj);

        @Specialization
        static Object doManaged(PythonManagedClass obj) {
            return obj.getSuperClass();
        }

        @Specialization
        static Object doBuiltin(PythonBuiltinClassType obj) {
            return obj.getBase();
        }

        @Specialization
        static Object doNative(PythonNativeClass obj,
                        @Cached GetTypeMemberNode getTpBaseNode,
                        @Cached PRaiseNode raise,
                        @Cached("createClassProfile()") ValueProfile resultTypeProfile) {
            Object result = resultTypeProfile.profile(getTpBaseNode.execute(obj, NativeMember.TP_BASE));
            if (PGuards.isPNone(result)) {
                return null;
            } else if (PGuards.isPythonClass(result)) {
                return result;
            }
            CompilerDirectives.transferToInterpreter();
            throw raise.raise(SystemError, ErrorMessages.INVALID_BASE_TYPE_OBJ_FOR_CLASS, GetNameNode.doSlowPath(obj), result);
        }
    }

    @TypeSystemReference(PythonTypes.class)
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
            Object tpSubclasses = getTpSubclassesNode.execute(obj, NativeMember.TP_SUBCLASSES);

            Object profiled = profile.profile(tpSubclasses);
            if (profiled instanceof PDict) {
                return wrapDict(profiled);
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException("invalid subclasses dict " + profiled.getClass().getName());
        }

        @TruffleBoundary
        private static Set<PythonAbstractClass> wrapDict(Object tpSubclasses) {
            return new Set<PythonAbstractClass>() {
                private final PDict dict = (PDict) tpSubclasses;

                @Override
                public int size() {
                    return HashingStorageLibrary.getUncached().length(dict.getDictStorage());
                }

                @Override
                public boolean isEmpty() {
                    return size() == 0;
                }

                @Override
                public boolean contains(Object o) {
                    return HashingStorageLibrary.getUncached().hasKey(dict.getDictStorage(), o);
                }

                @Override
                @SuppressWarnings("unchecked")
                public Iterator<PythonAbstractClass> iterator() {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new UnsupportedOperationException();
                }

                @Override
                @TruffleBoundary
                public Object[] toArray() {
                    Object[] result = new Object[size()];
                    Iterator<Object> keys = HashingStorageLibrary.getUncached().keys(dict.getDictStorage()).iterator();
                    for (int i = 0; i < result.length; i++) {
                        result[i] = keys.next();
                    }
                    return result;
                }

                @Override
                @SuppressWarnings("unchecked")
                public <T> T[] toArray(T[] a) {
                    if (a.getClass() == Object[].class) {
                        return (T[]) toArray();
                    } else {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        throw new UnsupportedOperationException();
                    }
                }

                @Override
                public boolean add(PythonAbstractClass e) {
                    if (PGuards.isNativeClass(e)) {
                        dict.setItem(PythonNativeClass.cast(e).getPtr(), e);
                    }
                    dict.setItem(new PythonNativeVoidPtr(e), e);
                    return true;
                }

                @Override
                public boolean remove(Object o) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new UnsupportedOperationException();
                }

                @Override
                public boolean containsAll(Collection<?> c) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new UnsupportedOperationException();
                }

                @Override
                public boolean addAll(Collection<? extends PythonAbstractClass> c) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new UnsupportedOperationException();
                }

                @Override
                public boolean retainAll(Collection<?> c) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new UnsupportedOperationException();
                }

                @Override
                public boolean removeAll(Collection<?> c) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new UnsupportedOperationException();
                }

                @Override
                public void clear() {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
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
            Object result = resultTypeProfile.profile(getTpBasesNode.execute(obj, NativeMember.TP_BASES));
            if (result instanceof PTuple) {
                Object[] values = toArrayNode.execute(((PTuple) result).getSequenceStorage());
                try {
                    return cast(values);
                } catch (ClassCastException e) {
                    throw raise.raise(PythonBuiltinClassType.SystemError, ErrorMessages.UNSUPPORTED_OBJ_IN, "tp_bases");
                }
            }
            throw raise.raise(PythonBuiltinClassType.SystemError, ErrorMessages.TYPE_DOES_NOT_PROVIDE_BASES);
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

    @ImportStatic(SpecialMethodNames.class)
    @GenerateUncached
    public abstract static class GetBaseClassNode extends PNodeWithContext {

        public abstract Object execute(Object obj);

        @Specialization
        Object doPythonClass(PythonManagedClass obj,
                        @Cached GetBestBaseClassNode getBestBaseClassNode) {
            PythonAbstractClass[] baseClasses = obj.getBaseClasses();
            if (baseClasses.length == 0) {
                return null;
            }
            if (baseClasses.length == 1) {
                return baseClasses[0];
            }
            return getBestBaseClassNode.execute(baseClasses);
        }

        @Specialization
        Object doPythonClass(PythonBuiltinClassType obj,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @Cached GetBestBaseClassNode getBestBaseClassNode) {
            PythonAbstractClass[] baseClasses = context.getCore().lookupType(obj).getBaseClasses();
            if (baseClasses.length == 0) {
                return null;
            }
            if (baseClasses.length == 1) {
                return baseClasses[0];
            }
            return getBestBaseClassNode.execute(baseClasses);
        }

        @Specialization
        static PythonAbstractClass doNative(PythonNativeClass obj,
                        @Cached PRaiseNode raise,
                        @Cached GetTypeMemberNode getTpBaseNode,
                        @Cached("createClassProfile()") ValueProfile resultTypeProfile,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") InteropLibrary lib) {
            Object result = resultTypeProfile.profile(getTpBaseNode.execute(obj, NativeMember.TP_BASE));
            if (PGuards.isPNone(result)) {
                return null;
            } else if (PGuards.isClass(result, lib)) {
                return (PythonAbstractClass) result;
            }
            CompilerDirectives.transferToInterpreter();
            throw raise.raise(SystemError, ErrorMessages.INVALID_BASE_TYPE_OBJ_FOR_CLASS, GetNameNode.doSlowPath(obj), result);
        }
    }

    @ImportStatic(SpecialMethodNames.class)
    @GenerateUncached
    abstract static class GetBestBaseClassNode extends PNodeWithContext {

        static GetBestBaseClassNode create() {
            return TypeNodesFactory.GetBestBaseClassNodeGen.create();
        }

        public abstract Object execute(Object bases);

        @Specialization(guards = "bases.length == 0")
        PythonAbstractClass getEmpty(@SuppressWarnings("unused") PythonAbstractClass[] bases) {
            return null;
        }

        @Specialization(guards = "bases.length == 1")
        PythonAbstractClass getOne(PythonAbstractClass[] bases) {
            return bases[0];
        }

        @Specialization
        PythonAbstractClass getOne(PythonAbstractClass bases) {
            return bases;
        }

        @Specialization(guards = "bases.length > 1")
        Object getBestBase(PythonAbstractClass[] bases,
                        @Cached IsSubtypeNode isSubTypeNode,
                        @Cached GetSolidBaseNode getSolidBaseNode,
                        @Cached PRaiseNode raiseNode) {
            return bestBase(bases, getSolidBaseNode, isSubTypeNode, raiseNode);
        }

        @Specialization(guards = "isClasses(bases)")
        PythonAbstractClass getBestBase(@SuppressWarnings("unused") Object bases,
                        @Cached PRaiseNode raise) {
            throw raise.raise(TypeError, ErrorMessages.BASES_MUST_BE_TYPES);
        }

        protected static boolean isClasses(Object obj) {
            return obj instanceof PythonAbstractClass[] || PGuards.isPythonClass(obj);
        }

        /**
         * Aims to get as close as possible to typeobject.best_base().
         */
        private static Object bestBase(PythonAbstractClass[] bases, GetSolidBaseNode getSolidBaseNode, IsSubtypeNode isSubTypeNode, PRaiseNode raiseNode) throws PException {
            Object base = null;
            Object winner = null;
            for (int i = 0; i < bases.length; i++) {
                PythonAbstractClass basei = bases[i];
                Object candidate = getSolidBaseNode.execute(basei);
                if (winner == null) {
                    winner = candidate;
                    base = basei;
                } else if (isSubTypeNode.execute(winner, candidate)) {
                    //
                } else if (isSubTypeNode.execute(candidate, winner)) {
                    winner = candidate;
                    base = basei;
                } else {
                    throw raiseNode.raise(SystemError, ErrorMessages.MULTIPLE_BASES_LAYOUT_CONFLICT);
                }
            }
            return base;
        }
    }

    public abstract static class CheckCompatibleForAssigmentNode extends PNodeWithContext {

        @Child private GetBaseClassNode getBaseClassNode;
        @Child private LookupAttributeInMRONode lookupSlotsNode;
        @Child private LookupAttributeInMRONode lookupNewNode;
        @Child private GetDictStorageNode getDictStorageNode;
        @Child private LookupAndCallBinaryNode getDictNode;
        @Child private HashingStorageLibrary hashingStorageLib;
        @Child private PythonObjectLibrary objectLibrary;
        @Child private GetObjectArrayNode getObjectArrayNode;
        @Child private PRaiseNode raiseNode;
        @Child private GetNameNode getTypeNameNode;

        public abstract boolean execute(VirtualFrame frame, Object oldBase, Object newBase);

        @Specialization
        boolean isCompatible(VirtualFrame frame, Object oldBase, PythonAbstractClass newBase,
                        @Cached("create()") BranchProfile errorSlotsBranch) {
            if (!compatibleForAssignment(frame, oldBase, newBase)) {
                errorSlotsBranch.enter();
                throw getRaiseNode().raise(TypeError, ErrorMessages.CLASS_ASIGMENT_S_LAYOUT_DIFFERS_FROM_S, getTypeName(newBase), getTypeName(oldBase));
            }
            return true;
        }

        /**
         * Aims to get as close as possible to typeobject.compatible_for_assignment().
         */
        private boolean compatibleForAssignment(VirtualFrame frame, Object oldB, Object newB) {
            Object newBase = newB;
            Object oldBase = oldB;

            Object newParent = getBaseClassNode().execute(newBase);
            while (newParent != null && compatibleWithBase(frame, newBase, newParent)) {
                newBase = newParent;
                newParent = getBaseClassNode().execute(newBase);
            }

            Object oldParent = getBaseClassNode().execute(oldBase);
            while (oldParent != null && compatibleWithBase(frame, oldBase, oldParent)) {
                oldBase = oldParent;
                oldParent = getBaseClassNode().execute(oldBase);
            }

            if (newBase != oldBase && (newParent != oldParent || !sameSlotsAdded(frame, newBase, oldBase))) {
                return false;
            }
            return true;
        }

        /**
         * Aims to get as close as possible to typeobject.compatible_with_tp_base().
         */
        private boolean compatibleWithBase(VirtualFrame frame, Object child, Object parent) {
            if (PGuards.isNativeClass(child) && PGuards.isNativeClass(parent)) {
                // TODO: call C function 'compatible_for_assignment'
                return false;
            }

            // (child->tp_flags & Py_TPFLAGS_HAVE_GC) == (parent->tp_flags & Py_TPFLAGS_HAVE_GC)
            if (PGuards.isNativeClass(child) != PGuards.isNativeClass(parent)) {
                return false;
            }

            // instead of child->tp_dictoffset == parent->tp_dictoffset
            if (hasDict(frame, child) != hasDict(frame, parent)) {
                return false;
            }

            // instead of child->tp_basicsize == parent->tp_basicsize
            // the assumption is made that a different "allocator" => different basic size, hm
            Object childNewMethod = getLookupNewNode().execute(child);
            Object parentNewMethod = getLookupNewNode().execute(parent);
            if (childNewMethod != parentNewMethod) {
                return false;
            }

            // instead of child->tp_itemsize == parent->tp_itemsize
            Object childSlots = getSlotsFromDict(frame, child);
            Object parentSlots = getSlotsFromDict(frame, parent);
            if (childSlots == null && parentSlots == null) {
                return true;
            }
            if (childSlots == null && parentSlots != null || childSlots != null && parentSlots == null) {
                return false;
            }
            if (!compareSlots(parent, child, parentSlots, childSlots)) {
                return false;
            }

            return true;
        }

        private boolean sameSlotsAdded(VirtualFrame frame, Object a, Object b) {
            // !(a->tp_flags & Py_TPFLAGS_HEAPTYPE) || !(b->tp_flags & Py_TPFLAGS_HEAPTYPE))
            if (a instanceof PythonBuiltinClass || b instanceof PythonBuiltinClass) {
                return false;
            }
            Object aSlots = getSlotsFromDict(frame, a);
            Object bSlots = getSlotsFromDict(frame, b);
            return compareSlots(a, b, aSlots, bSlots);
        }

        private boolean compareSlots(Object aType, Object bType, Object aSlotsArg, Object bSlotsArg) {
            Object aSlots = aSlotsArg;
            Object bSlots = bSlotsArg;

            if (aSlots == null && bSlots == null) {
                return true;
            }

            if (aSlots != null && bSlots != null) {
                return compareSortedSlots(aSlots, bSlots, getObjectArrayNode());
            }

            aSlots = getLookupSlots().execute(aType);
            bSlots = getLookupSlots().execute(bType);
            int aSize = aSlots != PNone.NO_VALUE ? getObjectLibrary().length(aSlots) : 0;
            int bSize = bSlots != PNone.NO_VALUE ? getObjectLibrary().length(bSlots) : 0;
            return aSize == bSize;
        }

        private GetBaseClassNode getBaseClassNode() {
            if (getBaseClassNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getBaseClassNode = insert(GetBaseClassNodeGen.create());
            }
            return getBaseClassNode;
        }

        private String getTypeName(Object clazz) {
            if (getTypeNameNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getTypeNameNode = insert(TypeNodes.GetNameNode.create());
            }
            return getTypeNameNode.execute(clazz);
        }

        private Object getSlotsFromDict(VirtualFrame frame, Object type) {
            Object dict = getObjectLibrary().lookupAttribute(type, frame, __DICT__);
            if (dict != PNone.NO_VALUE) {
                HashingStorage storage = getDictStorageNode().execute((PHashingCollection) dict);
                return getHashingStorageLibrary().getItem(storage, __SLOTS__);
            }
            return null;
        }

        private boolean hasDict(VirtualFrame frame, Object type) {
            return getObjectLibrary().lookupAttribute(type, frame, __DICT__) != PNone.NO_VALUE;
        }

        private GetObjectArrayNode getObjectArrayNode() {
            if (getObjectArrayNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getObjectArrayNode = insert(GetObjectArrayNodeGen.create());
            }
            return getObjectArrayNode;
        }

        private PythonObjectLibrary getObjectLibrary() {
            if (objectLibrary == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                objectLibrary = insert(PythonObjectLibrary.getFactory().createDispatched(4));
            }
            return objectLibrary;
        }

        private GetDictStorageNode getDictStorageNode() {
            if (getDictStorageNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getDictStorageNode = insert(GetDictStorageNode.create());
            }
            return getDictStorageNode;
        }

        private HashingStorageLibrary getHashingStorageLibrary() {
            if (hashingStorageLib == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                hashingStorageLib = insert(HashingStorageLibrary.getFactory().createDispatched(4));
            }
            return hashingStorageLib;
        }

        private LookupAttributeInMRONode getLookupSlots() {
            if (lookupSlotsNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupSlotsNode = insert(LookupAttributeInMRONode.create(__SLOTS__));
            }
            return lookupSlotsNode;
        }

        private LookupAttributeInMRONode getLookupNewNode() {
            if (lookupNewNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupNewNode = insert(LookupAttributeInMRONode.createForLookupOfUnmanagedClasses(__NEW__));
            }
            return lookupNewNode;
        }

        private PRaiseNode getRaiseNode() {
            if (raiseNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                raiseNode = insert(PRaiseNode.create());
            }
            return raiseNode;
        }
    }

    @TruffleBoundary
    private static boolean compareSortedSlots(Object aSlots, Object bSlots, GetObjectArrayNode getObjectArrayNode) {
        Object[] aArray = getObjectArrayNode.execute(aSlots);
        Object[] bArray = getObjectArrayNode.execute(bSlots);
        if (bArray.length != aArray.length) {
            return false;
        }
        aArray = Arrays.copyOf(aArray, aArray.length);
        bArray = Arrays.copyOf(bArray, bArray.length);
        // what cpython does in same_slots_added() is a compare on a sorted slots list
        // ((PyHeapTypeObject *)a)->ht_slots which is populated in type_new() and
        // NOT the same like the unsorted __slots__ attribute.
        Arrays.sort(bArray);
        Arrays.sort(aArray);
        for (int i = 0; i < aArray.length; i++) {
            if (!aArray[i].equals(bArray[i])) {
                return false;
            }
        }
        return true;
    }

    @GenerateUncached
    @ImportStatic(SpecialMethodNames.class)
    abstract static class GetSolidBaseNode extends Node {

        static GetSolidBaseNode create() {
            return GetSolidBaseNodeGen.create();
        }

        static GetSolidBaseNode getUncached() {
            return GetSolidBaseNodeGen.getUncached();
        }

        abstract Object execute(Object type);

        @Specialization
        protected Object exec(Object type,
                        @Cached GetBaseClassNode getBaseClassNode,
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @Cached LookupSpecialMethodNode.Dynamic lookupGetAttribute,
                        @Cached CallBinaryMethodNode callGetAttr,
                        @Cached GetDictStorageNode getDictStorageNode,
                        @CachedLibrary(limit = "4") HashingStorageLibrary storageLibrary,
                        @Cached GetObjectArrayNode getObjectArrayNode,
                        @CachedLibrary(limit = "4") PythonObjectLibrary objectLibrary) {
            return solidBase(type, getBaseClassNode, context, lookupGetAttribute, callGetAttr, getDictStorageNode, storageLibrary, getObjectArrayNode, objectLibrary);
        }

        private Object solidBase(Object type, GetBaseClassNode getBaseClassNode, PythonContext context, LookupSpecialMethodNode.Dynamic lookupGetAttribute,
                        CallBinaryMethodNode callGetAttr, GetDictStorageNode getDictStorageNode, HashingStorageLibrary storageLibrary, GetObjectArrayNode getObjectArrayNode,
                        PythonObjectLibrary objectLibrary) {
            Object base = getBaseClassNode.execute(type);

            if (base != null) {
                base = solidBase(base, getBaseClassNode, context, lookupGetAttribute, callGetAttr, getDictStorageNode, storageLibrary, getObjectArrayNode, objectLibrary);
            } else {
                base = context.getCore().lookupType(PythonBuiltinClassType.PythonObject);
            }

            if (extraivars(type, base, lookupGetAttribute, callGetAttr, getDictStorageNode, storageLibrary, getObjectArrayNode, objectLibrary)) {
                return type;
            } else {
                return base;
            }
        }

        private boolean extraivars(Object type, Object base, LookupSpecialMethodNode.Dynamic lookupGetAttribute, CallBinaryMethodNode callGetAttr,
                        GetDictStorageNode getDictStorageNode, HashingStorageLibrary storageLibrary, GetObjectArrayNode getObjectArrayNode, PythonObjectLibrary objectLibrary) {
            Object typeSlots = getSlotsFromDict(type, lookupGetAttribute, callGetAttr, getDictStorageNode, storageLibrary);
            Object baseSlots = getSlotsFromDict(base, lookupGetAttribute, callGetAttr, getDictStorageNode, storageLibrary);

            if (typeSlots == null ^ baseSlots == null) {
                return true;
            }

            Object typeNewMethod = LookupAttributeInMRONode.lookupSlow(type, __NEW__, GetMroStorageNode.getUncached(), ReadAttributeFromObjectNode.getUncached(), true);
            Object baseNewMethod = LookupAttributeInMRONode.lookupSlow(base, __NEW__, GetMroStorageNode.getUncached(), ReadAttributeFromObjectNode.getUncached(), true);
            if (typeNewMethod != baseNewMethod) {
                return true;
            }

            if (typeSlots != null && baseSlots != null) {
                return compareSortedSlots(typeSlots, baseSlots, getObjectArrayNode);
            }
            return hasDict(base, objectLibrary) != hasDict(type, objectLibrary);
        }

        protected Object getSlotsFromDict(Object type, LookupSpecialMethodNode.Dynamic lookupGetAttribute, CallBinaryMethodNode callGetAttr,
                        GetDictStorageNode getDictStorageNode, HashingStorageLibrary lib) {
            Object getAttr = lookupGetAttribute.execute(type, __GETATTRIBUTE__, type, false);
            Object dict = callGetAttr.executeObject(getAttr, type, __DICT__);
            if (dict != PNone.NO_VALUE) {
                HashingStorage storage = getDictStorageNode.execute((PHashingCollection) dict);
                return lib.getItem(storage, __SLOTS__);
            }
            return null;
        }

        protected boolean hasDict(Object obj, PythonObjectLibrary objectLibrary) {
            return objectLibrary.lookupAttribute(obj, null, __DICT__) != PNone.NO_VALUE;
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

        @Specialization
        boolean doNativeSingleContext(PythonAbstractNativeObject left, PythonAbstractNativeObject right,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {
            return lib.isIdentical(left, right, lib);
        }

        @Fallback
        boolean doOther(@SuppressWarnings("unused") Object left, @SuppressWarnings("unused") Object right) {
            return false;
        }
    }

    @GenerateUncached
    @ImportStatic(SpecialMethodNames.class)
    public abstract static class ProfileClassNode extends PNodeWithContext {

        public abstract Object execute(Object object);

        public final Object profile(Object object) {
            return execute(object);
        }

        public final PythonBuiltinClassType profile(PythonBuiltinClassType object) {
            return (PythonBuiltinClassType) execute(object);
        }

        @Specialization(guards = {"classType == cachedClassType"}, limit = "1")
        static PythonBuiltinClassType doPythonBuiltinClassType(@SuppressWarnings("unused") PythonBuiltinClassType classType,
                        @Cached("classType") PythonBuiltinClassType cachedClassType) {
            return cachedClassType;
        }

        @Specialization(guards = "isPythonAbstractClass(object)", assumptions = "singleContextAssumption()", rewriteOn = NotSameTypeException.class)
        static Object doPythonAbstractClass(Object object,
                        @Cached(value = "object", weak = true) Object cachedObject,
                        @CachedLibrary(limit = "2") InteropLibrary lib) throws NotSameTypeException {
            if (lib.isIdentical(object, cachedObject, lib)) {
                return cachedObject;
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw NotSameTypeException.INSTANCE;
        }

        @Specialization(replaces = {"doPythonBuiltinClassType", "doPythonAbstractClass"})
        static Object doDisabled(Object object) {
            return object;
        }

        protected static boolean isPythonAbstractClass(Object obj) {
            return PGuards.isPythonClass(obj);
        }

        static final class NotSameTypeException extends ControlFlowException {
            private static final long serialVersionUID = 1L;
            static final NotSameTypeException INSTANCE = new NotSameTypeException();
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
            return doSlowPath(cls, true);
        }

        @TruffleBoundary
        public static PythonAbstractClass[] doSlowPath(PythonAbstractClass cls, boolean invokeMro) {
            return computeMethodResolutionOrder(cls, invokeMro);
        }

        private static PythonAbstractClass[] computeMethodResolutionOrder(PythonAbstractClass cls, boolean invokeMro) {
            CompilerAsserts.neverPartOfCompilation();

            PythonAbstractClass[] currentMRO;

            Object type = PythonObjectLibrary.getUncached().getLazyPythonClass(cls);
            if (invokeMro) {
                if (PythonObjectLibrary.getUncached().isLazyPythonClass(type)) {
                    PythonAbstractClass[] typeMRO = getMRO(type, cls);
                    if (typeMRO != null) {
                        return typeMRO;
                    }
                }
            }

            PythonAbstractClass[] baseClasses = GetBaseClassesNodeGen.getUncached().execute(cls);
            if (baseClasses.length == 0) {
                currentMRO = new PythonAbstractClass[]{cls};
            } else if (baseClasses.length == 1) {
                PythonAbstractClass[] baseMRO = GetMroNode.getUncached().execute(baseClasses[0]);

                if (baseMRO == null) {
                    currentMRO = new PythonAbstractClass[]{cls};
                } else {
                    currentMRO = new PythonAbstractClass[baseMRO.length + 1];
                    PythonUtils.arraycopy(baseMRO, 0, currentMRO, 1, baseMRO.length);
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

        private static PythonAbstractClass[] getMRO(Object type, PythonAbstractClass cls) {
            if (type instanceof PythonClass) {
                Object mroMeth = LookupAttributeInMRONode.Dynamic.getUncached().execute(type, MRO);
                if (mroMeth instanceof PFunction) {
                    Object mroObj = CallUnaryMethodNode.getUncached().executeObject(mroMeth, cls);
                    if (mroObj instanceof PSequence) {
                        return mroCheck(cls, ((PSequence) mroObj).getSequenceStorage().getInternalArray());
                    }
                    throw PRaiseNode.getUncached().raise(TypeError, ErrorMessages.OBJ_NOT_ITERABLE, cls);
                }
            }
            return null;
        }

        private static PythonAbstractClass[] mroCheck(Object cls, Object[] mro) {
            List<PythonAbstractClass> resultMro = new ArrayList<>(mro.length);
            GetSolidBaseNode getSolidBase = GetSolidBaseNode.getUncached();
            Object solid = getSolidBase.execute(cls);
            for (int i = 0; i < mro.length; i++) {
                Object object = mro[i];
                if (object == null) {
                    continue;
                }
                if (!PythonObjectLibrary.getUncached().isLazyPythonClass(object)) {
                    throw PRaiseNode.getUncached().raise(TypeError, ErrorMessages.S_RETURNED_NON_CLASS, "mro()", object);
                }
                if (!IsSubtypeNode.getUncached().execute(solid, getSolidBase.execute(object))) {
                    throw PRaiseNode.getUncached().raise(TypeError, ErrorMessages.S_RETURNED_BASE_WITH_UNSUITABLE_LAYOUT, "mro()", object);
                }
                resultMro.add((PythonAbstractClass) object);
            }
            return resultMro.toArray(new PythonAbstractClass[resultMro.size()]);
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

            List<PythonAbstractClass> notMerged = new ArrayList<>();
            for (MROMergeState mergee : toMerge) {
                if (!mergee.isMerged()) {
                    PythonAbstractClass candidate = mergee.getCandidate();
                    if (!notMerged.contains(candidate)) {
                        notMerged.add(candidate);
                    }
                }
            }
            if (!notMerged.isEmpty()) {
                Iterator<PythonAbstractClass> it = notMerged.iterator();
                StringBuilder bases = new StringBuilder(GetNameNode.doSlowPath(it.next()));
                while (it.hasNext()) {
                    bases.append(", ").append(GetNameNode.doSlowPath(it.next()));
                }
                throw PRaiseNode.getUncached().raise(TypeError, ErrorMessages.CANNOT_GET_CONSISTEMT_METHOD_RESOLUTION, bases.toString());
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

        @Specialization(limit = "3")
        boolean doNativeClass(PythonAbstractNativeObject obj,
                        @Cached IsBuiltinClassProfile profile,
                        @CachedLibrary("obj") PythonObjectLibrary plib) {
            // TODO(fa): this check may not be enough since a type object may indirectly inherit
            // from 'type'
            // CPython has two different checks if some object is a type:
            // 1. test if type flag 'Py_TPFLAGS_TYPE_SUBCLASS' is set
            // 2. test if attribute '__bases__' is a tuple
            return profile.profileClass(plib.getLazyPythonClass(obj), PythonBuiltinClassType.PythonClass);
        }

        @Fallback
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

    public abstract static class IsAcceptableBaseNode extends Node {
        private static final long Py_TPFLAGS_BASETYPE = (1L << 10);

        public abstract boolean execute(Object obj);

        @Specialization
        boolean doUserClass(@SuppressWarnings("unused") PythonClass obj) {
            return true;
        }

        @Specialization
        boolean doBuiltinClass(@SuppressWarnings("unused") PythonBuiltinClass obj) {
            return obj.getType().isAcceptableBase();
        }

        @Specialization
        boolean doBuiltinType(@SuppressWarnings("unused") PythonBuiltinClassType obj) {
            return obj.isAcceptableBase();
        }

        @Specialization
        boolean doNativeClass(PythonAbstractNativeObject obj,
                        @Cached IsTypeNode isType,
                        @Cached GetTypeFlagsNode getFlags) {
            if (isType.execute(obj)) {
                return (getFlags.execute(obj) & Py_TPFLAGS_BASETYPE) != 0;
            }
            return false;
        }

        @Fallback
        boolean doOther(@SuppressWarnings("unused") Object obj) {
            return false;
        }

        public static IsAcceptableBaseNode create() {
            return IsAcceptableBaseNodeGen.create();
        }
    }

    @ImportStatic(PGuards.class)
    @GenerateUncached
    @ReportPolymorphism
    public abstract static class GetInstanceShape extends PNodeWithContext {

        public abstract Shape execute(Object clazz);

        @Specialization(guards = "clazz == cachedClazz", limit = "1")
        Shape doBuiltinClassTypeCached(@SuppressWarnings("unused") PythonBuiltinClassType clazz,
                        @Cached("clazz") PythonBuiltinClassType cachedClazz) {
            return cachedClazz.getInstanceShape();
        }

        @Specialization(replaces = "doBuiltinClassTypeCached")
        Shape doBuiltinClassType(PythonBuiltinClassType clazz) {
            return clazz.getInstanceShape();
        }

        @Specialization(guards = "clazz == cachedClazz", assumptions = "singleContextAssumption()")
        Shape doBuiltinClassCached(@SuppressWarnings("unused") PythonBuiltinClass clazz,
                        @Cached("clazz") PythonBuiltinClass cachedClazz) {
            return cachedClazz.getInstanceShape();
        }

        @Specialization(guards = "clazz == cachedClazz", assumptions = "singleContextAssumption()")
        Shape doClassCached(@SuppressWarnings("unused") PythonClass clazz,
                        @Cached("clazz") PythonClass cachedClazz) {
            return cachedClazz.getInstanceShape();
        }

        @Specialization(replaces = {"doClassCached", "doBuiltinClassCached"})
        Shape doManagedClass(PythonManagedClass clazz) {
            return clazz.getInstanceShape();
        }

        @Specialization(guards = {"!isManagedClass(clazz)", "!isPythonBuiltinClassType(clazz)"})
        Shape doError(@SuppressWarnings("unused") Object clazz,
                        @Cached PRaiseNode raise) {
            throw raise.raise(PythonBuiltinClassType.SystemError, ErrorMessages.CANNOT_GET_SHAPE_OF_NATIVE_CLS);
        }

        public static GetInstanceShape create() {
            return GetInstanceShapeNodeGen.create();
        }
    }
}
