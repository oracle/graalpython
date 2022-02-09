/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_SUBCLASS_CHECK;
import static com.oracle.graal.python.builtins.objects.type.TypeBuiltins.TYPE_FLAGS;
import static com.oracle.graal.python.builtins.objects.type.TypeBuiltins.TYPE_ITEMSIZE;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.BASETYPE;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.BASE_EXC_SUBCLASS;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.BYTES_SUBCLASS;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.DEFAULT;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.DICT_SUBCLASS;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.HAVE_GC;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.HAVE_VECTORCALL;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.HEAPTYPE;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.IS_ABSTRACT;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.LIST_SUBCLASS;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.LONG_SUBCLASS;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.METHOD_DESCRIPTOR;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.READY;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.TUPLE_SUBCLASS;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.TYPE_SUBCLASS;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.UNICODE_SUBCLASS;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__DICT__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__SLOTS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__WEAKREF__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.MRO;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEW__;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeClass;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeVoidPtr;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.GetTypeMemberNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.GetTypeMemberNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeMember;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef;
import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary.HashingStorageIterator;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes.GetObjectArrayNode;
import com.oracle.graal.python.builtins.objects.common.SequenceNodesFactory.GetObjectArrayNodeGen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetInternalObjectArrayNode;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetBaseClassNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetBaseClassesNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetInstanceShapeNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetItemsizeNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetMroStorageNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetNameNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetSolidBaseNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetSubclassesNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.IsAcceptableBaseNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.IsSameTypeNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.IsTypeNodeGen;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.truffle.PythonTypes;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.MroSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
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
import com.oracle.truffle.api.object.DynamicObjectLibrary;
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
                case DictRemover:
                case StructParam:
                case CArgObject:
                    result = DEFAULT;
                    break;
                case PythonObject:
                case StgDict:
                case PyCData:
                case PyCArray:
                case PyCPointer:
                case PyCFuncPtr:
                case Structure:
                case Union:
                case SimpleCData:
                    result = DEFAULT | BASETYPE;
                    break;
                case PyCArrayType: // DEFAULT | BASETYPE | PythonClass.flags
                case PyCSimpleType: // DEFAULT | BASETYPE | PythonClass.flags
                case PyCFuncPtrType: // DEFAULT | BASETYPE | PythonClass.flags
                case PyCStructType: // DEFAULT | HAVE_GC | BASETYPE | PythonClass.flags
                case PyCPointerType: // DEFAULT | HAVE_GC | BASETYPE | PythonClass.flags
                case UnionType: // DEFAULT | HAVE_GC | BASETYPE | PythonClass.flags
                case PythonClass:
                    result = DEFAULT | HAVE_GC | BASETYPE | TYPE_SUBCLASS;
                    break;
                case Super:
                case PythonModule:
                case PSet:
                case PFrozenSet:
                case PReferenceType:
                case PProperty:
                case PDeque:
                case PSimpleQueue:
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
                    result = DEFAULT | HAVE_GC | METHOD_DESCRIPTOR | HAVE_VECTORCALL;
                    break;
                case WrapperDescriptor:
                    result = DEFAULT | HAVE_GC | METHOD_DESCRIPTOR;
                    break;
                case PMethod:
                case PBuiltinMethod:
                    result = DEFAULT | HAVE_GC | HAVE_VECTORCALL;
                    break;
                case GetSetDescriptor:
                case MemberDescriptor:
                case PMappingproxy:
                case PFrame:
                case PGenerator:
                case PMemoryView:
                case PBuffer:
                case PSlice:
                case PTraceback:
                case PDequeIter:
                case PDequeRevIter:
                case CField:
                case CThunkObject:
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
                case PythonModuleDef:
                    result = 0;
                    break;
                default:
                    // default case; this includes:
                    // PythonObject, PByteArray, PCode, PInstancemethod, PFloat, PNone,
                    // PNotImplemented, PEllipsis, exceptions
                    result = DEFAULT | (clazz.isAcceptableBase() ? BASETYPE : 0) | (PythonBuiltinClassType.isExceptionType(clazz) ? BASE_EXC_SUBCLASS | HAVE_GC : 0L);
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

            if (clazz.isAbstractClass()) {
                result |= IS_ABSTRACT;
            }

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
                    result |= doNative((PythonAbstractNativeObject) mroEntry, GetTypeMemberNodeGen.getUncached());
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

        static MroSequenceStorage doPythonClass(PythonManagedClass obj,
                        @Cached ConditionProfile notInitialized,
                        @Cached ConditionProfile isPythonClass,
                        PythonLanguage language) {
            if (!notInitialized.profile(obj.isMROInitialized())) {
                PythonAbstractClass[] mro = ComputeMroNode.doSlowPath(obj, false);
                if (isPythonClass.profile(obj instanceof PythonClass)) {
                    ((PythonClass) obj).setMRO(mro, language);
                } else {
                    assert obj instanceof PythonBuiltinClass;
                    // the cast is here to help the compiler
                    ((PythonBuiltinClass) obj).setMRO(mro);
                }
            }
            return obj.getMethodResolutionOrder();
        }

        @Specialization
        MroSequenceStorage doPythonClass(PythonManagedClass obj,
                        @Cached ConditionProfile notInitialized,
                        @Cached ConditionProfile isPythonClass) {
            return doPythonClass(obj, notInitialized, isPythonClass, getLanguage());
        }

        @Specialization
        MroSequenceStorage doBuiltinClass(PythonBuiltinClassType obj) {
            return PythonContext.get(this).lookupType(obj).getMethodResolutionOrder();
        }

        @Specialization
        static MroSequenceStorage doNativeClass(PythonNativeClass obj,
                        @Cached GetTypeMemberNode getTpMroNode,
                        @Cached PRaiseNode raise,
                        @Cached ConditionProfile lazyTypeInitProfile,
                        @Cached("createClassProfile()") ValueProfile tpMroProfile,
                        @Cached("createClassProfile()") ValueProfile storageProfile) {
            Object tupleObj = getTpMroNode.execute(obj, NativeMember.TP_MRO);
            if (lazyTypeInitProfile.profile(tupleObj == PNone.NO_VALUE)) {
                // Special case: lazy type initialization (should happen at most only once per type)
                CompilerDirectives.transferToInterpreter();

                // call 'PyType_Ready' on the type
                int res = (int) PCallCapiFunction.getUncached().call(NativeCAPISymbol.FUN_PY_TYPE_READY, ToSulongNode.getUncached().execute(obj));
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
        static MroSequenceStorage doSlowPath(Object obj,
                        @Cached PRaiseNode raise) {
            if (obj instanceof PythonManagedClass) {
                return doPythonClass((PythonManagedClass) obj, ConditionProfile.getUncached(), ConditionProfile.getUncached(), PythonLanguage.get(null));
            } else if (obj instanceof PythonBuiltinClassType) {
                return PythonContext.get(null).lookupType((PythonBuiltinClassType) obj).getMethodResolutionOrder();
            } else if (PGuards.isNativeClass(obj)) {
                GetTypeMemberNode getTypeMemeberNode = GetTypeMemberNode.getUncached();
                Object tupleObj = getTypeMemeberNode.execute(obj, NativeMember.TP_MRO);
                if (tupleObj instanceof PTuple) {
                    SequenceStorage sequenceStorage = ((PTuple) tupleObj).getSequenceStorage();
                    if (sequenceStorage instanceof MroSequenceStorage) {
                        return (MroSequenceStorage) sequenceStorage;
                    }
                }
                throw raise.raise(PythonBuiltinClassType.SystemError, ErrorMessages.INVALID_MRO_OBJ);
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
                        @Cached CExtNodes.GetTypeMemberNode getTpNameNode,
                        @Cached CastToJavaStringNode castToJavaStringNode) {
            return castToJavaStringNode.execute(getTpNameNode.execute(obj, NativeMember.TP_NAME));
        }

        @Specialization(replaces = {"doManagedClass", "doBuiltinClassType", "doNativeClass"})
        @TruffleBoundary
        public static String doSlowPath(Object obj) {
            if (obj instanceof PythonManagedClass) {
                return ((PythonManagedClass) obj).getName();
            } else if (obj instanceof PythonBuiltinClassType) {
                return ((PythonBuiltinClassType) obj).getName();
            } else if (PGuards.isNativeClass(obj)) {
                return CastToJavaStringNode.getUncached().execute(CExtNodes.GetTypeMemberNode.getUncached().execute(obj, NativeMember.TP_NAME));
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
        static Object doPythonClass(PythonClass obj) {
            return obj.getSuperClass();
        }

        @Specialization
        static Object doBuiltin(PythonBuiltinClass obj) {
            return obj.getType().getBase();
        }

        @Specialization
        static Object doBuiltinType(PythonBuiltinClassType obj) {
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
        Set<PythonAbstractClass> doPythonClass(PythonBuiltinClassType obj) {
            return PythonContext.get(this).lookupType(obj).getSubClasses();
        }

        @Specialization
        Set<PythonAbstractClass> doNativeClass(PythonNativeClass obj,
                        @Cached GetTypeMemberNode getTpSubclassesNode,
                        @Cached("createClassProfile()") ValueProfile profile) {
            Object tpSubclasses = getTpSubclassesNode.execute(obj, NativeMember.TP_SUBCLASSES);

            Object profiled = profile.profile(tpSubclasses);
            if (profiled instanceof PDict) {
                return wrapDict(profiled);
            } else if (profiled instanceof PNone) {
                return Collections.emptySet();
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
                    final HashingStorageIterator<Object> storageIt = HashingStorageLibrary.getUncached().keys(dict.getDictStorage()).iterator();
                    return new Iterator<PythonAbstractClass>() {
                        @Override
                        public boolean hasNext() {
                            return storageIt.hasNext();
                        }

                        @Override
                        public PythonAbstractClass next() {
                            return (PythonAbstractClass) storageIt.next();
                        }
                    };
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
        PythonAbstractClass[] doPythonClass(PythonBuiltinClassType obj) {
            return PythonContext.get(this).lookupType(obj).getBaseClasses();
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
                        @Cached GetBestBaseClassNode getBestBaseClassNode) {
            PythonAbstractClass[] baseClasses = PythonContext.get(this).lookupType(obj).getBaseClasses();
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
    public abstract static class GetBestBaseClassNode extends PNodeWithContext {

        static GetBestBaseClassNode create() {
            return TypeNodesFactory.GetBestBaseClassNodeGen.create();
        }

        public abstract Object execute(PythonAbstractClass[] bases);

        @Specialization(guards = "bases.length == 0")
        PythonAbstractClass getEmpty(@SuppressWarnings("unused") PythonAbstractClass[] bases) {
            return null;
        }

        @Specialization(guards = "bases.length == 1")
        PythonAbstractClass getOne(PythonAbstractClass[] bases) {
            return bases[0];
        }

        @Specialization(guards = "bases.length > 1")
        Object getBestBase(PythonAbstractClass[] bases,
                        @Cached IsSubtypeNode isSubTypeNode,
                        @Cached GetSolidBaseNode getSolidBaseNode,
                        @Cached PRaiseNode raiseNode) {
            return bestBase(bases, getSolidBaseNode, isSubTypeNode, raiseNode);
        }

        @Fallback
        @SuppressWarnings("unused")
        // The fallback is necessary because the DSL otherwise generates code with a warning on
        // varargs ambiguity
        Object fallback(PythonAbstractClass[] bases) {
            throw CompilerDirectives.shouldNotReachHere();
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
                    throw raiseNode.raise(TypeError, ErrorMessages.MULTIPLE_BASES_LAYOUT_CONFLICT);
                }
            }
            return base;
        }
    }

    public abstract static class CheckCompatibleForAssigmentNode extends PNodeWithContext {

        @Child private GetBaseClassNode getBaseClassNode;
        @Child private IsSameTypeNode isSameTypeNode;
        @Child private LookupAttributeInMRONode lookupSlotsNode;
        @Child private LookupAttributeInMRONode lookupNewNode;
        @Child private PyObjectSizeNode sizeNode;
        @Child private GetObjectArrayNode getObjectArrayNode;
        @Child private PRaiseNode raiseNode;
        @Child private GetNameNode getTypeNameNode;
        @Child private ReadAttributeFromObjectNode readAttr;
        @Child private InstancesOfTypeHaveDictNode instancesHaveDictNode;

        public abstract boolean execute(VirtualFrame frame, Object oldBase, Object newBase);

        @Specialization
        boolean isCompatible(VirtualFrame frame, Object oldBase, Object newBase,
                        @Cached BranchProfile errorSlotsBranch) {
            if (!compatibleForAssignment(frame, oldBase, newBase)) {
                errorSlotsBranch.enter();
                throw getRaiseNode().raise(TypeError, ErrorMessages.CLASS_ASSIGNMENT_S_LAYOUT_DIFFERS_FROM_S, getTypeName(newBase), getTypeName(oldBase));
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

            return getIsSameTypeNode().execute(newBase, oldBase) || (getIsSameTypeNode().execute(newParent, oldParent) && sameSlotsAdded(frame, newBase, oldBase));
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
            if (instancesHaveDict(child) != instancesHaveDict(parent)) {
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
            Object childSlots = getSlotsFromType(child);
            Object parentSlots = getSlotsFromType(parent);
            if (childSlots == null && parentSlots == null) {
                return true;
            }
            if (childSlots == null || parentSlots == null) {
                return false;
            }
            return compareSlots(frame, parent, child, parentSlots, childSlots);
        }

        private boolean sameSlotsAdded(VirtualFrame frame, Object a, Object b) {
            // !(a->tp_flags & Py_TPFLAGS_HEAPTYPE) || !(b->tp_flags & Py_TPFLAGS_HEAPTYPE))
            if (PGuards.isKindOfBuiltinClass(a) || PGuards.isKindOfBuiltinClass(b)) {
                return false;
            }
            Object aSlots = getSlotsFromType(a);
            Object bSlots = getSlotsFromType(b);
            return compareSlots(frame, a, b, aSlots, bSlots);
        }

        private boolean compareSlots(VirtualFrame frame, Object aType, Object bType, Object aSlotsArg, Object bSlotsArg) {
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
            int aSize = aSlots != PNone.NO_VALUE ? getSizeNode().execute(frame, aSlots) : 0;
            int bSize = bSlots != PNone.NO_VALUE ? getSizeNode().execute(frame, bSlots) : 0;
            return aSize == bSize;
        }

        private GetBaseClassNode getBaseClassNode() {
            if (getBaseClassNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getBaseClassNode = insert(GetBaseClassNodeGen.create());
            }
            return getBaseClassNode;
        }

        private IsSameTypeNode getIsSameTypeNode() {
            if (isSameTypeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isSameTypeNode = insert(IsSameTypeNode.create());
            }
            return isSameTypeNode;
        }

        private String getTypeName(Object clazz) {
            if (getTypeNameNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getTypeNameNode = insert(TypeNodes.GetNameNode.create());
            }
            return getTypeNameNode.execute(clazz);
        }

        private Object getSlotsFromType(Object type) {
            Object slots = getReadAttr().execute(type, __SLOTS__);
            return slots != PNone.NO_VALUE ? slots : null;
        }

        private boolean instancesHaveDict(Object type) {
            if (instancesHaveDictNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                instancesHaveDictNode = insert(InstancesOfTypeHaveDictNode.create());
            }
            return instancesHaveDictNode.execute(type);
        }

        private GetObjectArrayNode getObjectArrayNode() {
            if (getObjectArrayNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getObjectArrayNode = insert(GetObjectArrayNodeGen.create());
            }
            return getObjectArrayNode;
        }

        private ReadAttributeFromObjectNode getReadAttr() {
            if (readAttr == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readAttr = insert(ReadAttributeFromObjectNode.createForceType());
            }
            return readAttr;
        }

        private PyObjectSizeNode getSizeNode() {
            if (sizeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                sizeNode = insert(PyObjectSizeNode.create());
            }
            return sizeNode;
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

    /**
     * Equivalent of checking type->tp_dictoffset != 0 in CPython
     */
    abstract static class InstancesOfTypeHaveDictNode extends PNodeWithContext {
        public abstract boolean execute(Object type);

        @Specialization
        static boolean doPBCT(PythonBuiltinClassType type) {
            return type.isBuiltinWithDict();
        }

        @Specialization
        static boolean doPythonClass(PythonManagedClass type) {
            return (type.getInstanceShape().getFlags() & PythonObject.HAS_SLOTS_BUT_NO_DICT_FLAG) == 0;
        }

        @Specialization
        static boolean doNativeObject(PythonAbstractNativeObject type,
                        @Cached GetTypeMemberNode getMember,
                        @Cached CastToJavaIntExactNode cast) {
            return cast.execute(getMember.execute(type, NativeMember.TP_DICTOFFSET)) != 0;
        }

        @Fallback
        static boolean doOther(@SuppressWarnings("unused") Object type) {
            return true;
        }

        public static InstancesOfTypeHaveDictNode create() {
            return TypeNodesFactory.InstancesOfTypeHaveDictNodeGen.create();
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
        protected Object getSolid(Object type,
                        @Cached GetBaseClassNode getBaseClassNode,
                        @Cached("createForceType()") ReadAttributeFromObjectNode readAttr,
                        @Cached GetInternalObjectArrayNode getArrayNode,
                        @Cached BranchProfile typeIsNotBase,
                        @Cached BranchProfile hasBase,
                        @Cached BranchProfile hasNoBase) {
            return solidBase(type, getBaseClassNode, PythonContext.get(this), readAttr, getArrayNode, typeIsNotBase, hasBase,
                            hasNoBase, 0);
        }

        @TruffleBoundary
        protected Object solidBaseTB(Object type, GetBaseClassNode getBaseClassNode, PythonContext context, GetInternalObjectArrayNode getArrayNode, int depth) {
            return solidBase(type, getBaseClassNode, context, ReadAttributeFromObjectNode.getUncachedForceType(), getArrayNode, BranchProfile.getUncached(),
                            BranchProfile.getUncached(), BranchProfile.getUncached(), depth);
        }

        protected Object solidBase(Object type, GetBaseClassNode getBaseClassNode, PythonContext context, ReadAttributeFromObjectNode readAttr,
                        GetInternalObjectArrayNode getArrayNode, BranchProfile typeIsNotBase, BranchProfile hasBase, BranchProfile hasNoBase, int depth) {
            CompilerAsserts.partialEvaluationConstant(depth);
            Object base = getBaseClassNode.execute(type);
            if (base != null) {
                hasBase.enter();
                if (depth > 3) {
                    base = solidBaseTB(base, getBaseClassNode, context, getArrayNode, depth);
                } else {
                    base = solidBase(base, getBaseClassNode, context, readAttr, getArrayNode, typeIsNotBase, hasBase,
                                    hasNoBase, depth + 1);
                }
            } else {
                hasNoBase.enter();
                base = context.lookupType(PythonBuiltinClassType.PythonObject);
            }

            if (type == base) {
                return type;
            }
            typeIsNotBase.enter();

            Object typeSlots = getSlotsFromType(type, readAttr);
            if (extraivars(type, base, typeSlots, getArrayNode)) {
                return type;
            } else {
                return base;
            }
        }

        @TruffleBoundary
        private static boolean extraivars(Object type, Object base, Object typeSlots, GetInternalObjectArrayNode getArrayNode) {
            if (typeSlots != null && length(typeSlots, getArrayNode) != 0) {
                return true;
            }
            Object typeNewMethod = LookupAttributeInMRONode.lookup(type, __NEW__, GetMroStorageNode.getUncached(), ReadAttributeFromObjectNode.getUncached(), true);
            Object baseNewMethod = LookupAttributeInMRONode.lookup(base, __NEW__, GetMroStorageNode.getUncached(), ReadAttributeFromObjectNode.getUncached(), true);
            return typeNewMethod != baseNewMethod;
        }

        @TruffleBoundary
        private static int length(Object slotsObject, GetInternalObjectArrayNode getArrayNode) {
            assert PGuards.isString(slotsObject) || PGuards.isPSequence(slotsObject) : "slotsObject must be either a String or a PSequence";

            if (PGuards.isString(slotsObject)) {
                return (slotsObject.equals(__DICT__) || slotsObject.equals(__WEAKREF__)) ? 0 : 1;
            } else {
                SequenceStorage storage = ((PSequence) slotsObject).getSequenceStorage();

                int count = 0;
                int length = storage.length();
                Object[] slots = getArrayNode.execute(storage);
                for (int i = 0; i < length; i++) {
                    // omit __DICT__ and __WEAKREF__, they cause no class layout conflict
                    // see also test_slts.py#test_no_bases_have_class_layout_conflict
                    if (!(slots[i].equals(__DICT__) || slots[i].equals(__WEAKREF__))) {
                        count++;
                    }
                }
                return count;
            }
        }

        private static Object getSlotsFromType(Object type, ReadAttributeFromObjectNode readAttr) {
            Object slots = readAttr.execute(type, __SLOTS__);
            return slots != PNone.NO_VALUE ? slots : null;
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

        public static IsSameTypeNode create() {
            return IsSameTypeNodeGen.create();
        }

        public static IsSameTypeNode getUncached() {
            return IsSameTypeNodeGen.getUncached();
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

        @Specialization(guards = {"classType == cachedClassType"}, limit = "1")
        static PythonBuiltinClassType doPythonBuiltinClassType(@SuppressWarnings("unused") PythonBuiltinClass builtinClass,
                        @Bind("builtinClass.getType()") @SuppressWarnings("unused") PythonBuiltinClassType classType,
                        @Cached("classType") PythonBuiltinClassType cachedClassType) {
            return cachedClassType;
        }

        @Specialization(guards = {"isSingleContext()", "isPythonAbstractClass(object)"}, rewriteOn = NotSameTypeException.class)
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
            return PythonAbstractClass.isInstance(obj);
        }

        static final class NotSameTypeException extends ControlFlowException {
            private static final long serialVersionUID = 1L;
            static final NotSameTypeException INSTANCE = new NotSameTypeException();
        }

        public static ProfileClassNode getUncached() {
            return TypeNodesFactory.ProfileClassNodeGen.getUncached();
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

        @TruffleBoundary
        static PythonAbstractClass[] invokeMro(PythonAbstractClass cls) {
            Object type = GetClassNode.getUncached().execute(cls);
            if (IsTypeNode.getUncached().execute(type) && type instanceof PythonClass) {
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

        private static PythonAbstractClass[] computeMethodResolutionOrder(PythonAbstractClass cls, boolean invokeMro) {
            CompilerAsserts.neverPartOfCompilation();

            PythonAbstractClass[] currentMRO;
            if (invokeMro) {
                PythonAbstractClass[] mro = invokeMro(cls);
                if (mro != null) {
                    return mro;
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

        private static PythonAbstractClass[] mroCheck(Object cls, Object[] mro) {
            List<PythonAbstractClass> resultMro = new ArrayList<>(mro.length);
            GetSolidBaseNode getSolidBase = GetSolidBaseNode.getUncached();
            Object solid = getSolidBase.execute(cls);
            for (int i = 0; i < mro.length; i++) {
                Object object = mro[i];
                if (object == null) {
                    continue;
                }
                if (!IsTypeNode.getUncached().execute(object)) {
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
        static boolean doManagedClass(@SuppressWarnings("unused") PythonClass obj) {
            return true;
        }

        @Specialization
        static boolean doManagedClass(@SuppressWarnings("unused") PythonBuiltinClass obj) {
            return true;
        }

        @Specialization
        static boolean doBuiltinType(@SuppressWarnings("unused") PythonBuiltinClassType obj) {
            return true;
        }

        @Specialization
        static boolean doNativeClass(PythonAbstractNativeObject obj,
                        @Cached IsBuiltinClassProfile profile,
                        @Cached GetClassNode getClassNode,
                        @Cached CExtNodes.PCallCapiFunction nativeTypeCheck) {
            Object type = getClassNode.execute(obj);
            if (profile.profileClass(type, PythonBuiltinClassType.PythonClass)) {
                return true;
            }
            if (PythonNativeClass.isInstance(type)) {
                return (int) nativeTypeCheck.call(FUN_SUBCLASS_CHECK, obj.getPtr()) == 1;
            }
            return false;
        }

        @Fallback
        static boolean doOther(@SuppressWarnings("unused") Object obj) {
            return false;
        }

        public static IsTypeNode create() {
            return IsTypeNodeGen.create();
        }

        public static IsTypeNode getUncached() {
            return IsTypeNodeGen.getUncached();
        }
    }

    @GenerateUncached
    public abstract static class IsAcceptableBaseNode extends Node {

        public abstract boolean execute(Object obj);

        @Specialization
        static boolean doUserClass(PythonClass obj,
                        @Cached ReadAttributeFromObjectNode readAttributeFromObjectNode,
                        @Cached BranchProfile hasHPyFlagsProfile) {
            // Special case for custom classes created via HPy: They are managed classes but can
            // have custom flags. The flags may prohibit subtyping.
            Object flagsObj = readAttributeFromObjectNode.execute(obj, GraalHPyDef.TYPE_HPY_FLAGS);
            if (flagsObj != PNone.NO_VALUE) {
                hasHPyFlagsProfile.enter();
                return (((long) flagsObj) & GraalHPyDef.HPy_TPFLAGS_BASETYPE) != 0;
            }
            return true;
        }

        @Specialization
        static boolean doBuiltinClass(@SuppressWarnings("unused") PythonBuiltinClass obj) {
            return obj.getType().isAcceptableBase();
        }

        @Specialization
        static boolean doBuiltinType(PythonBuiltinClassType obj) {
            return obj.isAcceptableBase();
        }

        @Specialization
        static boolean doNativeClass(PythonAbstractNativeObject obj,
                        @Cached IsTypeNode isType,
                        @Cached GetTypeFlagsNode getFlags) {
            if (isType.execute(obj)) {
                return (getFlags.execute(obj) & BASETYPE) != 0;
            }
            return false;
        }

        @Fallback
        static boolean doOther(@SuppressWarnings("unused") Object obj) {
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
            return cachedClazz.getInstanceShape(getLanguage());
        }

        @Specialization(replaces = "doBuiltinClassTypeCached")
        Shape doBuiltinClassType(PythonBuiltinClassType clazz) {
            return clazz.getInstanceShape(getLanguage());
        }

        @Specialization(guards = {"isSingleContext()", "clazz == cachedClazz"})
        static Shape doBuiltinClassCached(@SuppressWarnings("unused") PythonBuiltinClass clazz,
                        @Cached("clazz") PythonBuiltinClass cachedClazz) {
            return cachedClazz.getInstanceShape();
        }

        @Specialization(guards = {"isSingleContext()", "clazz == cachedClazz"})
        static Shape doClassCached(@SuppressWarnings("unused") PythonClass clazz,
                        @Cached("clazz") PythonClass cachedClazz) {
            return cachedClazz.getInstanceShape();
        }

        @Specialization(replaces = {"doClassCached", "doBuiltinClassCached"})
        static Shape doManagedClass(PythonManagedClass clazz) {
            return clazz.getInstanceShape();
        }

        @Specialization
        static Shape doNativeClass(PythonAbstractNativeObject clazz,
                        @Cached GetTypeMemberNode getTpDictNode,
                        @CachedLibrary(limit = "1") DynamicObjectLibrary lib) {
            Object tpDictObj = getTpDictNode.execute(clazz, NativeMember.TP_DICT);
            if (tpDictObj instanceof PDict) {
                HashingStorage dictStorage = ((PDict) tpDictObj).getDictStorage();
                if (dictStorage instanceof DynamicObjectStorage) {
                    Object instanceShapeObj = lib.getOrDefault(((DynamicObjectStorage) dictStorage).getStore(), PythonNativeClass.INSTANCESHAPE, PNone.NO_VALUE);
                    if (instanceShapeObj != PNone.NO_VALUE) {
                        return (Shape) instanceShapeObj;
                    }
                    throw CompilerDirectives.shouldNotReachHere("instanceshape object is not a shape");
                }
            }
            // TODO(fa): track unique shape per native class in language?
            throw CompilerDirectives.shouldNotReachHere("custom dicts for native classes are unsupported");
        }

        @Specialization(guards = {"!isManagedClass(clazz)", "!isPythonBuiltinClassType(clazz)"})
        static Shape doError(@SuppressWarnings("unused") Object clazz,
                        @Cached PRaiseNode raise) {
            throw raise.raise(PythonBuiltinClassType.SystemError, ErrorMessages.CANNOT_GET_SHAPE_OF_NATIVE_CLS);
        }

        public static GetInstanceShape create() {
            return GetInstanceShapeNodeGen.create();
        }
    }

    @GenerateUncached
    @ImportStatic(PythonOptions.class)
    public abstract static class GetItemsizeNode extends Node {
        // We avoid PythonOptions here because it would require language reference
        static final int MAX_RECURSION_DEPTH = 4;

        public final long execute(Object cls) {
            return execute(cls, 0);
        }

        public abstract long execute(Object cls, int depth);

        @Specialization
        static long getItemsizeType(PythonBuiltinClassType cls, @SuppressWarnings("unused") int depth) {
            return getBuiltinTypeItemsize(cls);
        }

        @Specialization
        static long getItemsizeManaged(PythonBuiltinClass cls, @SuppressWarnings("unused") int depth) {
            return getItemsizeType(cls.getType(), depth);
        }

        @Specialization(guards = "depth < MAX_RECURSION_DEPTH")
        static long getItemsizeManagedRecursiveNode(PythonClass cls, int depth,
                        @Shared("hasVal") @Cached ConditionProfile hasValueProfile,
                        @Shared("read") @Cached ReadAttributeFromObjectNode readNode,
                        @Shared("write") @Cached WriteAttributeToObjectNode writeNode,
                        @Shared("getBase") @Cached GetBaseClassNode getBaseNode,
                        @Cached GetItemsizeNode baseItemsizeNode) {
            CompilerAsserts.partialEvaluationConstant(depth);
            Object itemsize = readNode.execute(cls, TYPE_ITEMSIZE);
            if (hasValueProfile.profile(itemsize != PNone.NO_VALUE)) {
                return (long) itemsize;
            }

            Object base = getBaseNode.execute(cls);
            assert base != null;
            itemsize = baseItemsizeNode.execute(base, depth + 1);
            writeNode.execute(cls, TYPE_ITEMSIZE, itemsize);
            return (long) itemsize;
        }

        @Specialization(guards = "depth >= MAX_RECURSION_DEPTH")
        static long getItemsizeManagedRecursiveCall(PythonClass cls, int depth,
                        @Shared("hasVal") @Cached ConditionProfile hasValueProfile,
                        @Shared("read") @Cached ReadAttributeFromObjectNode readNode,
                        @Shared("write") @Cached WriteAttributeToObjectNode writeNode,
                        @Shared("getBase") @Cached GetBaseClassNode getBaseNode) {
            return getItemsizeManagedRecursiveNode(cls, depth, hasValueProfile, readNode, writeNode, getBaseNode, GetItemsizeNodeGen.getUncached());
        }

        @Specialization
        static long getNative(PythonNativeClass cls, @SuppressWarnings("unused") int depth,
                        @Cached GetTypeMemberNode getTpDictoffsetNode) {
            return (long) getTpDictoffsetNode.execute(cls, NativeMember.TP_ITEMSIZE);
        }

        private static long getBuiltinTypeItemsize(PythonBuiltinClassType cls) {
            switch (cls) {
                case PBytes:
                    return 1;
                case PInt:
                    return 4;
                case PFrame:
                case PMemoryView:
                case PTuple:
                case PStatResult:
                case PTerminalSize:
                case PUnameResult:
                case PStructTime:
                case PProfilerEntry:
                case PProfilerSubentry:
                case PStructPasswd:
                case PStructRusage:
                case PVersionInfo:
                case PFlags:
                case PFloatInfo:
                case PIntInfo:
                case PHashInfo:
                case PThreadInfo:
                case PUnraisableHookArgs:
                    // io
                case PIOBase:
                case PFileIO:
                case PBufferedIOBase:
                case PBufferedReader:
                case PBufferedWriter:
                case PBufferedRWPair:
                case PBufferedRandom:
                case PIncrementalNewlineDecoder:
                case PTextIOWrapper:
                    // ctypes
                case CArgObject:
                case CThunkObject:
                case StgDict:
                case Structure:
                case Union:
                case PyCPointer:
                case PyCArray:
                case PyCData:
                case SimpleCData:
                case PyCFuncPtr:
                case CField:
                case DictRemover:
                case StructParam:
                    return 8;
                case PythonClass:
                    return 40;
                default:
                    return 0;
            }
        }
    }
}
