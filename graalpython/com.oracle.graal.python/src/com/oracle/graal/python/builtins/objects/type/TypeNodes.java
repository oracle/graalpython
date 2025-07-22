/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.builtins.objects.PNone.NO_VALUE;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_SUBCLASS_CHECK;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyHeapTypeObject__ht_qualname;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_base;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_bases;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_basicsize;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_dictoffset;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_flags;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_itemsize;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_mro;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_name;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_subclasses;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_weaklistoffset;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.BASETYPE;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.BASE_EXC_SUBCLASS;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.BYTES_SUBCLASS;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.COLLECTION_FLAGS;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.DEFAULT;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.DICT_SUBCLASS;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.DISALLOW_INSTANTIATION;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.HAVE_GC;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.HEAPTYPE;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.IMMUTABLETYPE;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.IS_ABSTRACT;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.LIST_SUBCLASS;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.LONG_SUBCLASS;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.MANAGED_DICT;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.MAPPING;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.MATCH_SELF;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.METHOD_DESCRIPTOR;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.READY;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.SEQUENCE;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.SUBCLASS_FLAGS;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.TUPLE_SUBCLASS;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.TYPE_SUBCLASS;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.UNICODE_SUBCLASS;
import static com.oracle.graal.python.nodes.HiddenAttr.BASICSIZE;
import static com.oracle.graal.python.nodes.HiddenAttr.DICTOFFSET;
import static com.oracle.graal.python.nodes.HiddenAttr.ITEMSIZE;
import static com.oracle.graal.python.nodes.HiddenAttr.WEAKLISTOFFSET;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___CLASSCELL__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___CLASSDICTCELL__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___CLASS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DICT__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DOC__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___QUALNAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___SLOTS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___WEAKREF__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T_MRO;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___CLASS_GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___HASH__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___INIT_SUBCLASS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___NEW__;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.WeakRefModuleBuiltins.GetWeakRefsNode;
import com.oracle.graal.python.builtins.modules.WeakRefModuleBuiltinsFactory;
import com.oracle.graal.python.builtins.modules.cext.PythonCextTypeBuiltins.PyTruffleType_AddMember;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeClass;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiMemberAccessNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitionsFactory.PythonToNativeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.common.EconomicMapStorage;
import com.oracle.graal.python.builtins.objects.common.EmptyStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageDelItem;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageForEach;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageForEachCallback;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetItem;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorKey;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorKeyHash;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorNext;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorValue;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageSetItemWithHash;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes.GetObjectArrayNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetInternalObjectArrayNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetItemScalarNode;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.exception.ExceptionNodes;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.GetSetDescriptor;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.IndexedSlotDescriptor;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltins;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltinsFactory;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.referencetype.PReferenceType;
import com.oracle.graal.python.builtins.objects.str.StringBuiltins.IsIdentifierNode;
import com.oracle.graal.python.builtins.objects.str.StringUtils;
import com.oracle.graal.python.builtins.objects.superobject.SuperObject;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TpSlots.Builder;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetCachedTpSlotsNode;
import com.oracle.graal.python.builtins.objects.type.TpSlots.TpSlotMeta;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetBaseClassNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetBaseClassesNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetBasicSizeNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetIndexedSlotsCountNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetMroStorageNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetNameNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetSolidBaseNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetTpNameNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetTypeFlagsNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.InstancesOfTypeHaveDictNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.InstancesOfTypeHaveWeakrefsNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.IsAcceptableBaseNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.IsSameTypeNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.IsTypeNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.SetTypeFlagsNodeGen;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotHashFun;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.lib.PyUnicodeCheckNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.HiddenAttr;
import com.oracle.graal.python.nodes.PConstructAndRaiseNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupSpecialMethodNode;
import com.oracle.graal.python.nodes.classes.AbstractObjectGetBasesNode;
import com.oracle.graal.python.nodes.classes.AbstractObjectIsSubclassNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.expression.CastToListExpressionNode.CastToListNode;
import com.oracle.graal.python.nodes.frame.ReadCallerFrameNode;
import com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetClassNode.GetPythonObjectClassNode;
import com.oracle.graal.python.nodes.object.GetOrCreateDictNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.MroSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ObjectSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.profiles.InlinedCountingConditionProfile;
import com.oracle.truffle.api.profiles.InlinedExactClassProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.CodePointLengthNode;
import com.oracle.truffle.api.strings.TruffleString.EqualNode;
import com.oracle.truffle.api.strings.TruffleString.IsValidNode;

public abstract class TypeNodes {

    private static final int SIZEOF_PY_OBJECT_PTR = Long.BYTES;

    @GenerateUncached
    @SuppressWarnings("truffle-inlining")       // footprint reduction 40 -> 21
    public abstract static class GetTypeFlagsNode extends Node {

        public abstract long execute(Object clazz);

        public static long executeUncached(Object clazz) {
            return GetTypeFlagsNodeGen.getUncached().execute(clazz);
        }

        @Specialization
        static long doBuiltinClassType(PythonBuiltinClassType clazz,
                        @Bind Node inliningTarget,
                        @Shared("read") @Cached HiddenAttr.ReadNode readHiddenFlagsNode,
                        @Shared("write") @Cached HiddenAttr.WriteNode writeHiddenFlagsNode,
                        @Shared("profile") @Cached InlinedCountingConditionProfile profile) {
            return doManaged(PythonContext.get(inliningTarget).getCore().lookupType(clazz), inliningTarget, readHiddenFlagsNode, writeHiddenFlagsNode, profile);
        }

        @Specialization
        static long doManaged(PythonManagedClass clazz,
                        @Bind Node inliningTarget,
                        @Shared("read") @Cached HiddenAttr.ReadNode readHiddenFlagsNode,
                        @Shared("write") @Cached HiddenAttr.WriteNode writeHiddenFlagsNode,
                        @Shared("profile") @Cached InlinedCountingConditionProfile profile) {

            Object flagsObject = readHiddenFlagsNode.execute(inliningTarget, clazz, HiddenAttr.FLAGS, null);
            if (profile.profile(inliningTarget, flagsObject != null)) {
                // we have it under control; it must be a long
                return (long) flagsObject;
            }
            long flags = computeFlags(clazz);
            writeHiddenFlagsNode.execute(inliningTarget, clazz, HiddenAttr.FLAGS, flags);
            return flags;
        }

        @Specialization
        @InliningCutoff
        static long doNative(PythonNativeClass clazz,
                        @Cached CStructAccess.ReadI64Node getTpFlagsNode) {
            return getTpFlagsNode.readFromObj(clazz, PyTypeObject__tp_flags);
        }

        @TruffleBoundary
        private static long computeFlags(PythonManagedClass clazz) {
            if (clazz instanceof PythonBuiltinClass) {
                return defaultBuiltinFlags(((PythonBuiltinClass) clazz).getType());
            }
            // according to 'type_new' in 'typeobject.c', all have DEFAULT, HEAPTYPE, and BASETYPE.
            // The HAVE_GC is inherited. But we do not mimic this behavior in every detail, so it
            // should be fine to just set it.
            long result = DEFAULT | HEAPTYPE | BASETYPE | HAVE_GC;

            if (clazz.isAbstractClass()) {
                result |= IS_ABSTRACT;
            }

            if ((clazz.getInstanceShape().getFlags() & PythonObject.HAS_SLOTS_BUT_NO_DICT_FLAG) == 0) {
                result |= MANAGED_DICT;
            }

            PythonContext context = PythonContext.get(null);
            // flags are inherited
            MroSequenceStorage mroStorage = GetMroStorageNode.executeUncached(clazz);
            int n = mroStorage.length();
            for (int i = 0; i < n; i++) {
                Object mroEntry = SequenceStorageNodes.GetItemDynamicNode.executeUncached(mroStorage, i);
                if (mroEntry instanceof PythonBuiltinClassType) {
                    mroEntry = context.getCore().lookupType((PythonBuiltinClassType) mroEntry);
                }
                if (mroEntry instanceof PythonAbstractNativeObject) {
                    result = setFlags(result, doNative((PythonAbstractNativeObject) mroEntry, CStructAccess.ReadI64Node.getUncached()));
                } else if (mroEntry != clazz && mroEntry instanceof PythonManagedClass) {
                    long flags = doManaged((PythonManagedClass) mroEntry, null, HiddenAttr.ReadNode.getUncached(), HiddenAttr.WriteNode.getUncached(),
                                    InlinedCountingConditionProfile.getUncached());
                    result = setFlags(result, flags);
                }
            }
            return result;
        }

        private static long defaultBuiltinFlags(PythonBuiltinClassType clazz) {
            long result;
            switch (clazz) {
                case DictRemover:
                case StructParam:
                case CArgObject:
                case MultibyteCodec:
                case PEllipsis:
                case PNotImplemented:
                case PNone:
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
                case MultibyteIncrementalEncoder:
                case MultibyteIncrementalDecoder:
                case MultibyteStreamReader:
                case MultibyteStreamWriter:
                case PyCSimpleType:
                case PyCFuncPtrType:
                    result = DEFAULT | BASETYPE;
                    break;
                case PArray:
                    result = DEFAULT | BASETYPE | SEQUENCE;
                    break;
                case PythonClass:
                case Super:
                case PythonModule:
                case PReferenceType:
                case PProperty:
                case PDeque:
                case POrderedDict:
                case PSimpleQueue:
                case PSimpleNamespace:
                case PMap:
                case PStaticmethod:
                case PZip:
                case PReverseIterator:
                case PCycle:
                case PEnumerate:
                case PyCStructType:
                case PyCPointerType:
                case PyCArrayType:
                case UnionType:
                case PBaseException:
                    result = DEFAULT | HAVE_GC | BASETYPE;
                    break;
                case PFrozenSet:
                case PSet:
                    result = DEFAULT | HAVE_GC | BASETYPE | MATCH_SELF;
                    break;
                case Boolean:
                    result = DEFAULT | MATCH_SELF;
                    break;
                case PFunction:
                case PBuiltinFunction:
                case WrapperDescriptor:
                case PLruCacheWrapper:
                    result = DEFAULT | HAVE_GC | METHOD_DESCRIPTOR;
                    break;
                case PLruListElem:
                    result = DEFAULT | HAVE_GC;
                    break;
                case PBytesIOBuf:
                case PMethod:
                case PBuiltinFunctionOrMethod:
                case PBuiltinMethod:
                case MethodWrapper:
                case PInstancemethod:
                case GetSetDescriptor:
                case MemberDescriptor:
                case PFrame:
                case PGenerator:
                case PSlice:
                case PTraceback:
                case PDequeIter:
                case PDequeRevIter:
                case CField:
                case CThunkObject:
                case PArrayIterator:
                case PAsyncGenerator:
                case PCell:
                case PIterator:
                    result = DEFAULT | HAVE_GC;
                    break;
                case PMappingproxy:
                    result = DEFAULT | HAVE_GC | MAPPING;
                    break;
                case PMemoryView:
                    result = DEFAULT | HAVE_GC | SEQUENCE;
                    break;
                case PDict:
                    result = DEFAULT | HAVE_GC | BASETYPE | MATCH_SELF | MAPPING;
                    break;
                case PDefaultDict:
                    result = DEFAULT | HAVE_GC | BASETYPE | MAPPING;
                    break;
                case PList:
                case PTuple:
                    result = DEFAULT | HAVE_GC | BASETYPE | MATCH_SELF | SEQUENCE;
                    break;
                case PRange:
                    result = DEFAULT | SEQUENCE;
                    break;
                case PythonModuleDef:
                case Capsule:
                    result = 0;
                    break;
                case PByteArray:
                case PFloat:
                case PInt:
                case PString:
                case PBytes:
                    result = DEFAULT | BASETYPE | MATCH_SELF;
                    break;
                case PIOBase:
                    result = DEFAULT | BASETYPE | HEAPTYPE;
                    break;
                default:
                    // default case; this includes: PythonObject, PCode, PInstancemethod, PNone,
                    // PNotImplemented, PEllipsis, exceptions
                    result = DEFAULT;
                    break;
            }
            result |= clazz.isAcceptableBase() ? BASETYPE : 0;
            result |= clazz.disallowInstantiation() ? DISALLOW_INSTANTIATION : 0;
            PythonBuiltinClassType iter = clazz;
            while (iter != null) {
                if (iter == PythonBuiltinClassType.PBaseException) {
                    result |= BASE_EXC_SUBCLASS | HAVE_GC;
                } else if (iter == PythonBuiltinClassType.PythonClass) {
                    result |= TYPE_SUBCLASS;
                } else if (iter == PythonBuiltinClassType.PInt) {
                    result |= LONG_SUBCLASS;
                } else if (iter == PythonBuiltinClassType.PBytes) {
                    result |= BYTES_SUBCLASS;
                } else if (iter == PythonBuiltinClassType.PString) {
                    result |= UNICODE_SUBCLASS;
                } else if (iter == PythonBuiltinClassType.PTuple) {
                    result |= TUPLE_SUBCLASS;
                } else if (iter == PythonBuiltinClassType.PList) {
                    result |= LIST_SUBCLASS;
                } else if (iter == PythonBuiltinClassType.PDict) {
                    result |= DICT_SUBCLASS;
                }
                iter = iter.getBase();
            }
            // we always claim that all types are fully initialized
            // so far, all builtin types we care about are IMMUTABLE
            return result | READY | IMMUTABLETYPE;
        }

        public static GetTypeFlagsNode getUncached() {
            return TypeNodesFactory.GetTypeFlagsNodeGen.getUncached();
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class SetTypeFlagsNode extends Node {

        public abstract void execute(Node inliningTarget, Object clazz, long flags);

        public static void executeUncached(Object clazz, long flags) {
            SetTypeFlagsNodeGen.getUncached().execute(null, clazz, flags);
        }

        @Specialization
        static void doPBCT(Node inliningTarget, PythonBuiltinClassType clazz, long flags,
                        @Shared("write") @Cached HiddenAttr.WriteNode writeHiddenFlagsNode) {
            doManaged(inliningTarget, PythonContext.get(inliningTarget).getCore().lookupType(clazz), flags, writeHiddenFlagsNode);
        }

        @Specialization
        static void doManaged(Node inliningTarget, PythonManagedClass clazz, long flags,
                        @Shared("write") @Cached HiddenAttr.WriteNode writeHiddenFlagsNode) {
            writeHiddenFlagsNode.execute(inliningTarget, clazz, HiddenAttr.FLAGS, flags);
        }

        @Specialization
        static void doNative(PythonNativeClass clazz, long flags,
                        @Cached(inline = false) CStructAccess.WriteLongNode write) {
            write.writeToObject(clazz, PyTypeObject__tp_flags, flags);
        }
    }

    private static long setFlags(long result, long flags) {
        if ((flags & IS_ABSTRACT) != 0) {
            flags &= ~IS_ABSTRACT;
        }
        if ((result & COLLECTION_FLAGS) != 0) {
            // SEQUENCE and MAPPING are mutually exclusive.
            // If multiple inheritance, the first one wins.
            flags &= ~COLLECTION_FLAGS;
        }
        result |= flags & (COLLECTION_FLAGS | SUBCLASS_FLAGS | MATCH_SELF);
        return result | READY;
    }

    @GenerateUncached
    @GenerateInline(inlineByDefault = true)
    @GenerateCached
    public abstract static class GetMroNode extends Node {

        public abstract PythonAbstractClass[] execute(Node inliningTarget, Object obj);

        public final PythonAbstractClass[] executeCached(Object obj) {
            return execute(this, obj);
        }

        public static PythonAbstractClass[] executeUncached(Object obj) {
            return TypeNodesFactory.GetMroNodeGen.getUncached().execute(null, obj);
        }

        @Specialization
        static PythonAbstractClass[] doIt(Node inliningTarget, Object obj,
                        @Cached GetMroStorageNode getMroStorageNode) {
            return getMroStorageNode.execute(inliningTarget, obj).getInternalClassArray();
        }

        @NeverDefault
        public static GetMroNode create() {
            return TypeNodesFactory.GetMroNodeGen.create();
        }
    }

    @GenerateUncached
    @GenerateInline(inlineByDefault = true)
    @GenerateCached
    public abstract static class GetMroStorageNode extends PNodeWithContext {

        public abstract MroSequenceStorage execute(Node inliningTarget, Object obj);

        public final MroSequenceStorage executeCached(Object obj) {
            return execute(this, obj);
        }

        public static MroSequenceStorage executeUncached(Object obj) {
            return GetMroStorageNodeGen.getUncached().execute(null, obj);
        }

        private static MroSequenceStorage doPythonClass(PythonManagedClass obj, Node inliningTarget, InlinedConditionProfile notInitialized, InlinedConditionProfile isPythonClass,
                        PythonLanguage language) {
            if (!notInitialized.profile(inliningTarget, obj.isMROInitialized())) {
                initializeMRO(obj, inliningTarget, isPythonClass, language);
            }
            return obj.getMethodResolutionOrder();
        }

        @InliningCutoff
        private static void initializeMRO(PythonManagedClass obj, Node inliningTarget, InlinedConditionProfile isPythonClass, PythonLanguage language) {
            PythonAbstractClass[] mro = ComputeMroNode.doSlowPath(inliningTarget, obj, false);
            if (isPythonClass.profile(inliningTarget, obj instanceof PythonClass)) {
                ((PythonClass) obj).setMRO(mro, language);
            } else {
                assert obj instanceof PythonBuiltinClass;
                // the cast is here to help the compiler
                ((PythonBuiltinClass) obj).setMRO(mro);
            }
        }

        @Specialization
        static MroSequenceStorage doPythonClass(Node inliningTarget, PythonManagedClass obj,
                        @Exclusive @Cached InlinedConditionProfile notInitialized,
                        @Exclusive @Cached InlinedConditionProfile isPythonClass) {
            return doPythonClass(obj, inliningTarget, notInitialized, isPythonClass, PythonLanguage.get(inliningTarget));
        }

        @Specialization
        static MroSequenceStorage doBuiltinClass(Node inliningTarget, PythonBuiltinClassType obj) {
            return PythonContext.get(inliningTarget).lookupType(obj).getMethodResolutionOrder();
        }

        @Specialization
        @InliningCutoff
        static MroSequenceStorage doNativeClass(Node inliningTarget, PythonNativeClass obj,
                        @Cached(inline = false) CStructAccess.ReadObjectNode getTpMroNode,
                        @Exclusive @Cached InlinedConditionProfile lazyTypeInitProfile,
                        @Exclusive @Cached InlinedExactClassProfile tpMroProfile,
                        @Exclusive @Cached InlinedExactClassProfile storageProfile,
                        @Exclusive @Cached InlinedBranchProfile raiseSystemErrorBranch) {
            Object tupleObj = getTpMroNode.readFromObj(obj, PyTypeObject__tp_mro);
            if (lazyTypeInitProfile.profile(inliningTarget, tupleObj == PNone.NO_VALUE)) {
                tupleObj = initializeType(inliningTarget, obj, getTpMroNode);
            }
            Object profiled = tpMroProfile.profile(inliningTarget, tupleObj);
            if (profiled instanceof PTuple) {
                SequenceStorage sequenceStorage = storageProfile.profile(inliningTarget, ((PTuple) profiled).getSequenceStorage());
                if (sequenceStorage instanceof MroSequenceStorage) {
                    return (MroSequenceStorage) sequenceStorage;
                }
            }
            raiseSystemErrorBranch.enter(inliningTarget);
            throw PRaiseNode.raiseStatic(inliningTarget, SystemError, ErrorMessages.INVALID_MRO_OBJ);
        }

        private static Object initializeType(Node inliningTarget, PythonNativeClass obj, CStructAccess.ReadObjectNode getTpMroNode) {
            // Special case: lazy type initialization (should happen at most only once per type)
            CompilerDirectives.transferToInterpreter();

            // call 'PyType_Ready' on the type
            int res = (int) PCallCapiFunction.callUncached(NativeCAPISymbol.FUN_PY_TYPE_READY, PythonToNativeNodeGen.getUncached().execute(obj));
            if (res < 0) {
                throw PRaiseNode.raiseStatic(inliningTarget, SystemError, ErrorMessages.LAZY_INITIALIZATION_FAILED, obj);
            }

            Object tupleObj = getTpMroNode.readFromObj(obj, PyTypeObject__tp_mro);
            assert tupleObj != PNone.NO_VALUE : "MRO object is still NULL even after lazy type initialization";
            return tupleObj;
        }

        @Specialization(replaces = {"doPythonClass", "doBuiltinClass", "doNativeClass"})
        @TruffleBoundary
        static MroSequenceStorage doSlowPath(Node inliningTarget, Object obj) {
            if (obj instanceof PythonManagedClass) {
                return doPythonClass((PythonManagedClass) obj, inliningTarget, InlinedConditionProfile.getUncached(), InlinedConditionProfile.getUncached(), PythonLanguage.get(null));
            } else if (obj instanceof PythonBuiltinClassType) {
                return PythonContext.get(null).lookupType((PythonBuiltinClassType) obj).getMethodResolutionOrder();
            } else if (PGuards.isNativeClass(obj)) {
                CStructAccess.ReadObjectNode getTypeMemeberNode = CStructAccess.ReadObjectNode.getUncached();
                Object tupleObj = getTypeMemeberNode.readFromObj((PythonNativeClass) obj, PyTypeObject__tp_mro);
                if (tupleObj == PNone.NO_VALUE) {
                    tupleObj = initializeType(inliningTarget, (PythonNativeClass) obj, CStructAccess.ReadObjectNode.getUncached());
                }
                if (tupleObj instanceof PTuple) {
                    SequenceStorage sequenceStorage = ((PTuple) tupleObj).getSequenceStorage();
                    if (sequenceStorage instanceof MroSequenceStorage) {
                        return (MroSequenceStorage) sequenceStorage;
                    }
                }
                throw PRaiseNode.raiseStatic(inliningTarget, SystemError, ErrorMessages.INVALID_MRO_OBJ);
            }
            throw new IllegalStateException("unknown type " + obj.getClass().getName());
        }

        @NeverDefault
        public static GetMroStorageNode create() {
            return GetMroStorageNodeGen.create();
        }

        public static GetMroStorageNode getUncached() {
            return GetMroStorageNodeGen.getUncached();
        }
    }

    /**
     * Equivalent of {@code _PyType_Name}. Returns unqualified name. Different from
     * {@code GetTpNameNode}.
     */
    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class GetNameNode extends Node {

        public abstract TruffleString execute(Node inliningTarget, Object obj);

        public static TruffleString executeUncached(Object obj) {
            return GetNameNodeGen.getUncached().execute(null, obj);
        }

        @Specialization
        static TruffleString doManagedClass(PythonManagedClass obj) {
            return obj.getName();
        }

        @Specialization
        static TruffleString doBuiltinClassType(PythonBuiltinClassType obj) {
            return obj.getName();
        }

        @Specialization
        TruffleString doNativeClass(PythonNativeClass obj,
                        @Cached CStructAccess.ReadCharPtrNode getTpNameNode,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.LastIndexOfCodePointNode indexOfCodePointNode,
                        @Cached TruffleString.SubstringNode substringNode) {
            // 'tp_name' contains the fully-qualified name, i.e., 'module.A.B...'
            TruffleString tpName = getTpNameNode.readFromObj(obj, PyTypeObject__tp_name);
            int nameLen = codePointLengthNode.execute(tpName, TS_ENCODING);
            int lastDot = indexOfCodePointNode.execute(tpName, '.', nameLen, 0, TS_ENCODING);
            if (lastDot < 0) {
                return tpName;
            }
            return substringNode.execute(tpName, lastDot + 1, nameLen - lastDot - 1, TS_ENCODING, true);
        }
    }

    /*
     * Equivalent of getting {@code tp_name} field. Returns unqualified name for heaptypes, but
     * qualified name for builtins. Typically used in exception messages.
     */
    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class GetTpNameNode extends Node {

        public abstract TruffleString execute(Node inliningTarget, Object obj);

        public static TruffleString executeUncached(Object obj) {
            return GetTpNameNodeGen.getUncached().execute(null, obj);
        }

        @Specialization
        static TruffleString doPythonClass(PythonClass obj) {
            return obj.getName();
        }

        @Specialization
        static TruffleString doBuiltinClass(PythonBuiltinClass obj) {
            return doBuiltinClassType(obj.getType());
        }

        @Specialization
        static TruffleString doBuiltinClassType(PythonBuiltinClassType obj) {
            return obj.getPrintName();
        }

        @Specialization
        TruffleString doNativeClass(PythonNativeClass obj,
                        @Cached(inline = false) CStructAccess.ReadCharPtrNode getTpNameNode) {
            return getTpNameNode.readFromObj(obj, PyTypeObject__tp_name);
        }
    }

    // Equivalent of PyType_GetQualName
    @GenerateInline
    @GenerateUncached
    @GenerateCached(false)
    public abstract static class GetQualNameNode extends Node {
        public abstract TruffleString execute(Node inliningTarget, Object type);

        @Specialization
        static TruffleString getQualName(PythonManagedClass clazz) {
            return clazz.getQualName();
        }

        @Specialization
        static TruffleString getQualName(Node inliningTarget, PythonAbstractNativeObject clazz,
                        @Cached(inline = false) GetTypeFlagsNode getTypeFlagsNode,
                        @Cached(inline = false) CStructAccess.ReadObjectNode readObjectNode,
                        @Cached(inline = false) CStructAccess.ReadCharPtrNode readCharPtrNode,
                        @Cached CastToTruffleStringNode cast) {
            assert IsTypeNode.executeUncached(clazz);
            long flags = getTypeFlagsNode.execute(clazz);
            if ((flags & HEAPTYPE) != 0) {
                Object qualname = readObjectNode.readFromObj(clazz, PyHeapTypeObject__ht_qualname);
                try {
                    return cast.execute(inliningTarget, qualname);
                } catch (CannotCastException e) {
                    throw CompilerDirectives.shouldNotReachHere("Cannot cast ht_qualname to string");
                }
            } else {
                return readCharPtrNode.readFromObj(clazz, PyTypeObject__tp_name);
            }
        }
    }

    public static final class GetSubclassesNode {
        static record KeyAndHash(Object key, long hash) {
        }

        static final class CollectEmptyKeys extends HashingStorageForEachCallback<List<KeyAndHash>> {
            @Override
            public final List<KeyAndHash> execute(Frame frame, Node inliningTarget, HashingStorage storage, HashingStorageIterator it, List<KeyAndHash> acc) {
                Object value = HashingStorageIteratorValue.executeUncached(storage, it);
                if (value instanceof PReferenceType pref) {
                    Object subclassValue = pref.getObject();
                    if (subclassValue == null) {
                        Object key = HashingStorageIteratorKey.executeUncached(storage, it);
                        // CPython uses the object pointer as the key, but we cannot so we do not
                        // really know how the hash is arrived at and need to make sure to remove
                        // the same hash that was stored.
                        long hash = HashingStorageIteratorKeyHash.executeUncached(storage, it);
                        acc.add(new KeyAndHash(key, hash));
                    }
                }
                return acc;
            }
        }

        static void clearEmptyReferences(EconomicMapStorage storage) {
            List<KeyAndHash> acc = new ArrayList<>();
            HashingStorageForEach.executeUncached(storage, new CollectEmptyKeys(), acc);
            for (KeyAndHash k : acc) {
                storage.removeUncached(k.key(), k.hash());
            }
        }

        protected static void addSubclass(PythonAbstractClass base, PythonManagedClass subclass) {
            CompilerAsserts.neverPartOfCompilation();
            PDict dict = executeUncached(base);
            // CPython uses the object pointer as the key, but we cannot since identity hashes are
            // not unique. So we need to use the actual weakref as the key here, but not all
            // classes are hashable. So we use the identity hash code for the map storage hash.
            long hash = System.identityHashCode(subclass);
            HashingStorage storage = dict.getDictStorage();
            Object weakref = PFactory.createReferenceType(PythonLanguage.get(null), subclass);
            if (!(storage instanceof EconomicMapStorage)) {
                assert storage == EmptyStorage.INSTANCE : "Unexpected storage type!";
                storage = EconomicMapStorage.create();
                dict.setDictStorage(storage);
            } else {
                clearEmptyReferences((EconomicMapStorage) storage);
            }
            ((EconomicMapStorage) storage).putUncached(weakref, hash, weakref);
        }

        static final class RemoveSubclassValue extends HashingStorageForEachCallback<PythonManagedClass> {
            @Override
            public final PythonManagedClass execute(Frame frame, Node inliningTarget, HashingStorage storage, HashingStorageIterator it, PythonManagedClass toRemove) {
                if (toRemove == null) {
                    return null;
                }
                Object value = HashingStorageIteratorValue.executeUncached(storage, it);
                if (value instanceof PReferenceType pref) {
                    Object subclassValue = pref.getObject();
                    if (subclassValue == toRemove) {
                        pref.clearRef();
                        return null;
                    }
                }
                return toRemove;
            }
        }

        protected static void removeSubclass(PythonAbstractClass base, PythonManagedClass subclass) {
            CompilerAsserts.neverPartOfCompilation();
            PDict dict = executeUncached(base);
            HashingStorage storage = dict.getDictStorage();
            if (storage instanceof EconomicMapStorage ems) {
                HashingStorageForEach.executeUncached(ems, new RemoveSubclassValue(), subclass);
                clearEmptyReferences(ems);
            } else {
                assert storage == EmptyStorage.INSTANCE : "Unexpected storage type!";
            }
        }

        public static PDict executeUncached(PythonAbstractClass clazz) {
            if (clazz instanceof PythonManagedClass mc) {
                return mc.getSubClasses();
            } else if (PythonNativeClass.isInstance(clazz)) {
                Object tpSubclasses = CStructAccess.ReadObjectNode.getUncached().readFromObj(PythonNativeClass.cast(clazz), PyTypeObject__tp_subclasses);
                if (tpSubclasses instanceof PDict dict) {
                    return dict;
                }
                throw CompilerDirectives.shouldNotReachHere("invalid subclasses dict " + tpSubclasses.getClass().getName());
            } else {
                throw CompilerDirectives.shouldNotReachHere("unexpected value for GetSubclassesNode: " + clazz.getClass().getName());
            }
        }
    }

    public static final class GetSubclassesAsArrayNode {
        private static final PythonAbstractClass[] EMPTY = new PythonAbstractClass[0];

        static final class EachSubclassAdd extends HashingStorageForEachCallback<ArrayList<PythonAbstractClass>> {
            @Override
            public final ArrayList<PythonAbstractClass> execute(Frame frame, Node inliningTarget, HashingStorage storage, HashingStorageIterator it, ArrayList<PythonAbstractClass> subclasses) {
                Object value = HashingStorageIteratorValue.executeUncached(storage, it);
                PythonAbstractClass clazz = PythonAbstractClass.cast(((PReferenceType) value).getObject());
                if (clazz != null) {
                    subclasses.add(clazz);
                }
                return subclasses;
            }
        }

        public static PythonAbstractClass[] executeUncached(Object object) {
            PythonAbstractClass clazz;
            if (object instanceof PythonBuiltinClassType bt) {
                clazz = PythonContext.get(null).lookupType(bt);
            } else {
                clazz = PythonAbstractClass.cast(object);
            }
            PDict subclasses = GetSubclassesNode.executeUncached(clazz);
            if (subclasses == null) {
                return EMPTY;
            }
            HashingStorage storage = subclasses.getDictStorage();
            if (storage == EmptyStorage.INSTANCE) {
                return EMPTY;
            }
            ArrayList<PythonAbstractClass> list = new ArrayList<>();
            HashingStorageForEach.executeUncached(storage, new EachSubclassAdd(), list);
            return list.toArray(EMPTY);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class GetBaseClassesNode extends PNodeWithContext {

        // TODO(fa): this should not return a Java array; maybe a SequenceStorage would fit
        public abstract PythonAbstractClass[] execute(Node inliningTarget, Object obj);

        public static PythonAbstractClass[] executeUncached(Object obj) {
            return GetBaseClassesNodeGen.getUncached().execute(null, obj);
        }

        @Specialization
        static PythonAbstractClass[] doPythonClass(PythonManagedClass obj) {
            return obj.getBaseClasses();
        }

        @Specialization
        PythonAbstractClass[] doPythonClass(PythonBuiltinClassType obj) {
            return PythonContext.get(this).lookupType(obj).getBaseClasses();
        }

        @Specialization
        static PythonAbstractClass[] doNative(Node inliningTarget, PythonNativeClass obj,
                        @Cached PRaiseNode raise,
                        @Cached(inline = false) CStructAccess.ReadObjectNode getTpBasesNode,
                        @Cached InlinedExactClassProfile resultTypeProfile,
                        @Cached GetInternalObjectArrayNode toArrayNode) {
            Object result = resultTypeProfile.profile(inliningTarget, getTpBasesNode.readFromObj(obj, PyTypeObject__tp_bases));
            if (result instanceof PTuple tuple) {
                SequenceStorage storage = tuple.getSequenceStorage();
                Object[] values = toArrayNode.execute(inliningTarget, storage);
                try {
                    return cast(values, storage);
                } catch (ClassCastException e) {
                    throw raise.raise(inliningTarget, PythonBuiltinClassType.SystemError, ErrorMessages.UNSUPPORTED_OBJ_IN, "tp_bases");
                }
            }
            throw raise.raise(inliningTarget, PythonBuiltinClassType.SystemError, ErrorMessages.TYPE_DOES_NOT_PROVIDE_BASES);
        }

        // TODO: get rid of this
        private static PythonAbstractClass[] cast(Object[] arr, SequenceStorage storage) {
            PythonAbstractClass[] bases = new PythonAbstractClass[storage.length()];
            for (int i = 0; i < storage.length(); i++) {
                bases[i] = (PythonAbstractClass) arr[i];
            }
            return bases;
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class GetBaseClassNode extends Node {

        public abstract Object execute(Node inliningTarget, Object obj);

        public static Object executeUncached(Object obj) {
            return GetBaseClassNodeGen.getUncached().execute(null, obj);
        }

        @Specialization
        static Object doPythonClass(PythonManagedClass obj) {
            return obj.getBase();
        }

        @Specialization
        static Object doBuiltinType(PythonBuiltinClassType obj) {
            return obj.getBase();
        }

        @Specialization
        static Object doNative(Node inliningTarget, PythonNativeClass obj,
                        @Cached(inline = false) CStructAccess.ReadObjectNode getTpBaseNode,
                        @Cached InlinedExactClassProfile resultTypeProfile) {
            Object result = resultTypeProfile.profile(inliningTarget, getTpBaseNode.readFromObj(obj, PyTypeObject__tp_base));
            if (PGuards.isPNone(result)) {
                return null;
            } else if (PGuards.isPythonClass(result)) {
                return result;
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw PRaiseNode.raiseStatic(inliningTarget, SystemError, ErrorMessages.INVALID_BASE_TYPE_OBJ_FOR_CLASS, obj, result);
        }

        public static GetBaseClassNode getUncached() {
            return GetBaseClassNodeGen.getUncached();
        }
    }

    @ImportStatic(SpecialMethodNames.class)
    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class GetBestBaseClassNode extends PNodeWithContext {

        public abstract PythonAbstractClass execute(Node inliningTarget, PythonAbstractClass[] bases);

        @Specialization(guards = "bases.length == 0")
        static PythonAbstractClass getEmpty(@SuppressWarnings("unused") PythonAbstractClass[] bases) {
            return null;
        }

        @Specialization(guards = "bases.length == 1")
        static PythonAbstractClass getOne(PythonAbstractClass[] bases) {
            return bases[0];
        }

        @Specialization(guards = "bases.length > 1")
        static PythonAbstractClass getBestBase(Node inliningTarget, PythonAbstractClass[] bases,
                        @Cached(inline = false) IsSubtypeNode isSubTypeNode,
                        @Cached GetSolidBaseNode getSolidBaseNode,
                        @Cached PRaiseNode raiseNode) {
            return bestBase(inliningTarget, bases, getSolidBaseNode, isSubTypeNode, raiseNode);
        }

        @Fallback
        @SuppressWarnings("unused")
        // The fallback is necessary because the DSL otherwise generates code with a warning on
        // varargs ambiguity
        static PythonAbstractClass fallback(PythonAbstractClass[] bases) {
            throw CompilerDirectives.shouldNotReachHere();
        }

        /**
         * Aims to get as close as possible to typeobject.best_base().
         */
        private static PythonAbstractClass bestBase(Node inliningTarget, PythonAbstractClass[] bases, GetSolidBaseNode getSolidBaseNode, IsSubtypeNode isSubTypeNode, PRaiseNode raiseNode)
                        throws PException {
            PythonAbstractClass base = null;
            Object winner = null;
            for (int i = 0; i < bases.length; i++) {
                PythonAbstractClass basei = bases[i];
                Object candidate = getSolidBaseNode.execute(inliningTarget, basei);
                if (winner == null) {
                    winner = candidate;
                    base = basei;
                } else if (isSubTypeNode.execute(winner, candidate)) {
                    //
                } else if (isSubTypeNode.execute(candidate, winner)) {
                    winner = candidate;
                    base = basei;
                } else {
                    throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.MULTIPLE_BASES_LAYOUT_CONFLICT);
                }
            }
            return base;
        }
    }

    @GenerateInline(inlineByDefault = true)
    @GenerateCached
    @GenerateUncached
    public abstract static class GetIndexedSlotsCountNode extends Node {
        public abstract int execute(Node inliningTarget, Object cls);

        public static int executeUncached(Object cls) {
            return GetIndexedSlotsCountNodeGen.getUncached().execute(null, cls);
        }

        @Specialization
        static int doManaged(PythonManagedClass cls) {
            return cls.getIndexedSlotCount();
        }

        @Specialization
        static int getNative(Node inliningTarget, PythonNativeClass cls,
                        @Cached GetBaseClassNode getBaseClassNode,
                        @Cached(inline = false) GetIndexedSlotsCountNode recursiveNode) {
            return recursiveNode.execute(inliningTarget, getBaseClassNode.execute(inliningTarget, cls));
        }

        @Fallback
        static int fallback(@SuppressWarnings("unused") Object cls) {
            return 0;
        }
    }

    public abstract static class CheckCompatibleForAssigmentNode extends PNodeWithContext {

        @Child private LookupAttributeInMRONode lookupSlotsNode;
        @Child private LookupAttributeInMRONode lookupNewNode;
        @Child private PyObjectSizeNode sizeNode;
        @Child private ReadAttributeFromObjectNode readAttr;
        @Child private InstancesOfTypeHaveDictNode instancesHaveDictNode;

        public abstract boolean execute(VirtualFrame frame, Object oldBase, Object newBase);

        @Specialization
        boolean isCompatible(VirtualFrame frame, Object oldBase, Object newBase,
                        @Bind Node inliningTarget,
                        @Cached InlinedBranchProfile errorSlotsBranch,
                        @Cached IsSameTypeNode isSameTypeNode,
                        @Cached GetBaseClassNode getBaseClassNode) {
            if (!compatibleForAssignment(frame, inliningTarget, oldBase, newBase, isSameTypeNode, getBaseClassNode)) {
                errorSlotsBranch.enter(inliningTarget);
                throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.CLASS_ASSIGNMENT_N_LAYOUT_DIFFERS_FROM_N, newBase, oldBase);
            }
            return true;
        }

        /**
         * Aims to get as close as possible to typeobject.compatible_for_assignment().
         */
        private boolean compatibleForAssignment(VirtualFrame frame, Node inliningTarget, Object oldB, Object newB, IsSameTypeNode isSameTypeNode, GetBaseClassNode getBaseClassNode) {
            Object newBase = newB;
            Object oldBase = oldB;

            Object newParent = getBaseClassNode.execute(inliningTarget, newBase);
            while (newParent != null && compatibleWithBase(frame, newBase, newParent)) {
                newBase = newParent;
                newParent = getBaseClassNode.execute(inliningTarget, newBase);
            }

            Object oldParent = getBaseClassNode.execute(inliningTarget, oldBase);
            while (oldParent != null && compatibleWithBase(frame, oldBase, oldParent)) {
                oldBase = oldParent;
                oldParent = getBaseClassNode.execute(inliningTarget, oldBase);
            }

            return isSameTypeNode.execute(inliningTarget, newBase, oldBase) || (isSameTypeNode.execute(inliningTarget, newParent, oldParent) && sameSlotsAdded(frame, newBase, oldBase));
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
                return compareSortedSlots(aSlots, bSlots);
            }

            aSlots = getLookupSlots().execute(aType);
            bSlots = getLookupSlots().execute(bType);
            int aSize = aSlots != PNone.NO_VALUE ? getSizeNode().executeCached(frame, aSlots) : 0;
            int bSize = bSlots != PNone.NO_VALUE ? getSizeNode().executeCached(frame, bSlots) : 0;
            return aSize == bSize;
        }

        private Object getSlotsFromType(Object type) {
            Object slots = getReadAttr().execute(type, T___SLOTS__);
            return slots != PNone.NO_VALUE ? slots : null;
        }

        private boolean instancesHaveDict(Object type) {
            if (instancesHaveDictNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                instancesHaveDictNode = insert(InstancesOfTypeHaveDictNode.create());
            }
            return instancesHaveDictNode.execute(type);
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
                lookupSlotsNode = insert(LookupAttributeInMRONode.create(T___SLOTS__));
            }
            return lookupSlotsNode;
        }

        private LookupAttributeInMRONode getLookupNewNode() {
            if (lookupNewNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lookupNewNode = insert(LookupAttributeInMRONode.createForLookupOfUnmanagedClasses(T___NEW__));
            }
            return lookupNewNode;
        }
    }

    /**
     * Equivalent of checking type->tp_dictoffset != 0 in CPython
     */
    @GenerateInline(false)
    @GenerateUncached
    abstract static class InstancesOfTypeHaveDictNode extends PNodeWithContext {
        public abstract boolean execute(Object type);

        public static boolean executeUncached(Object type) {
            return InstancesOfTypeHaveDictNodeGen.getUncached().execute(type);
        }

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
                        @Cached CStructAccess.ReadI64Node getMember) {
            return getMember.readFromObj(type, PyTypeObject__tp_dictoffset) != 0;
        }

        @Fallback
        static boolean doOther(@SuppressWarnings("unused") Object type) {
            return true;
        }

        @NeverDefault
        public static InstancesOfTypeHaveDictNode create() {
            return TypeNodesFactory.InstancesOfTypeHaveDictNodeGen.create();
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    abstract static class InstancesOfTypeHaveWeakrefsNode extends PNodeWithContext {
        public abstract boolean execute(Node inliningTarget, Object type);

        public static boolean executeUncached(Object type) {
            return InstancesOfTypeHaveWeakrefsNodeGen.getUncached().execute(null, type);
        }

        @Specialization
        static boolean check(Node inliningTarget, Object type,
                        @Cached NeedsNativeAllocationNode needsNativeAllocationNode,
                        @Cached(inline = false) ReadAttributeFromObjectNode read,
                        @Cached GetWeakListOffsetNode getWeakListOffsetNode) {
            if (needsNativeAllocationNode.execute(inliningTarget, type)) {
                return getWeakListOffsetNode.execute(inliningTarget, type) != 0;
            } else {
                return read.execute(type, T___WEAKREF__) != PNone.NO_VALUE;
            }
        }
    }

    @TruffleBoundary
    private static boolean compareSortedSlots(Object aSlots, Object bSlots) {
        Object[] aArray = GetObjectArrayNode.executeUncached(aSlots);
        Object[] bArray = GetObjectArrayNode.executeUncached(bSlots);
        if (bArray.length != aArray.length) {
            return false;
        }
        aArray = Arrays.copyOf(aArray, aArray.length);
        bArray = Arrays.copyOf(bArray, bArray.length);
        // what cpython does in same_slots_added() is a compare on a sorted slots list
        // ((PyHeapTypeObject *)a)->ht_slots which is populated in type_new() and
        // NOT the same like the unsorted __slots__ attribute.
        for (int i = 0; i < aArray.length; ++i) {
            try {
                aArray[i] = CastToTruffleStringNode.executeUncached(aArray[i]).toJavaStringUncached();
                bArray[i] = CastToTruffleStringNode.executeUncached(bArray[i]).toJavaStringUncached();
            } catch (CannotCastException e) {
                throw CompilerDirectives.shouldNotReachHere("slots are not strings");
            }
        }
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
    @GenerateInline
    @GenerateCached(false)
    @ImportStatic(SpecialMethodNames.class)
    abstract static class GetSolidBaseNode extends Node {

        abstract Object execute(Node inliningTarget, Object type);

        static Object executeUncached(Object type) {
            return GetSolidBaseNodeGen.getUncached().execute(null, type);
        }

        @Specialization
        protected static Object getSolid(Node inliningTarget, Object type,
                        @Cached GetBaseClassNode getBaseClassNode,
                        @Cached(value = "createForceType()", inline = false) ReadAttributeFromObjectNode readAttr,
                        @Cached InlinedBranchProfile typeIsNotBase,
                        @Cached InlinedBranchProfile hasBase,
                        @Cached InlinedBranchProfile hasNoBase) {
            return solidBase(type, inliningTarget, getBaseClassNode, PythonContext.get(inliningTarget), readAttr, typeIsNotBase, hasBase,
                            hasNoBase, 0);
        }

        @TruffleBoundary
        protected static Object solidBaseTB(Object type, Node inliningTarget, GetBaseClassNode getBaseClassNode, PythonContext context, int depth) {
            return solidBase(type, inliningTarget, getBaseClassNode, context, ReadAttributeFromObjectNode.getUncachedForceType(), InlinedBranchProfile.getUncached(),
                            InlinedBranchProfile.getUncached(), InlinedBranchProfile.getUncached(), depth);
        }

        protected static Object solidBase(Object type, Node inliningTarget, GetBaseClassNode getBaseClassNode, PythonContext context, ReadAttributeFromObjectNode readAttr,
                        InlinedBranchProfile typeIsNotBase, InlinedBranchProfile hasBase, InlinedBranchProfile hasNoBase, int depth) {
            CompilerAsserts.partialEvaluationConstant(depth);
            Object base = getBaseClassNode.execute(inliningTarget, type);
            if (base != null) {
                hasBase.enter(inliningTarget);
                if (depth > 3) {
                    base = solidBaseTB(base, inliningTarget, getBaseClassNode, context, depth);
                } else {
                    base = solidBase(base, inliningTarget, getBaseClassNode, context, readAttr, typeIsNotBase, hasBase,
                                    hasNoBase, depth + 1);
                }
            } else {
                hasNoBase.enter(inliningTarget);
                base = context.lookupType(PythonBuiltinClassType.PythonObject);
            }

            if (type == base) {
                return type;
            }
            typeIsNotBase.enter(inliningTarget);

            if (shapeDiffers(type, base, readAttr)) {
                return type;
            } else {
                return base;
            }
        }

        @TruffleBoundary
        static boolean shapeDiffers(Object type, Object base, ReadAttributeFromObjectNode readAttr) {
            if (NeedsNativeAllocationNode.executeUncached(type) || NeedsNativeAllocationNode.executeUncached(base)) {
                long tSize = GetBasicSizeNode.executeUncached(type);
                long bSize = GetBasicSizeNode.executeUncached(base);
                if (tSize != bSize) {
                    return true;
                }
                long tItemSize = GetItemSizeNode.executeUncached(type);
                long bItemSize = GetItemSizeNode.executeUncached(base);
                return tItemSize != bItemSize;
            }

            Object typeSlots = getSlotsFromType(type, readAttr);
            if (typeSlots != null && length(typeSlots) != 0) {
                return true;
            }
            Object typeNewMethod = LookupAttributeInMRONode.lookup(T___NEW__, GetMroStorageNode.executeUncached(type), ReadAttributeFromObjectNode.getUncached(), true,
                            DynamicObjectLibrary.getUncached());
            Object baseNewMethod = LookupAttributeInMRONode.lookup(T___NEW__, GetMroStorageNode.executeUncached(base), ReadAttributeFromObjectNode.getUncached(), true,
                            DynamicObjectLibrary.getUncached());
            return typeNewMethod != baseNewMethod;
        }

        @TruffleBoundary
        private static int length(Object slotsObject) {
            assert PGuards.isString(slotsObject) || PGuards.isPSequence(slotsObject) : "slotsObject must be either a String or a PSequence";

            if (PGuards.isString(slotsObject)) {
                TruffleString slotName = (TruffleString) slotsObject;
                return (T___DICT__.equalsUncached(slotName, TS_ENCODING) || T___WEAKREF__.equalsUncached(slotName, TS_ENCODING)) ? 0 : 1;
            } else {
                SequenceStorage storage = ((PSequence) slotsObject).getSequenceStorage();

                int count = 0;
                int length = storage.length();
                Object[] slots = GetInternalObjectArrayNode.executeUncached(storage);
                for (int i = 0; i < length; i++) {
                    // omit __DICT__ and __WEAKREF__, they cause no class layout conflict
                    // see also test_slts.py#test_no_bases_have_class_layout_conflict
                    Object s = slots[i];
                    if (!(s instanceof TruffleString && (T___DICT__.equalsUncached((TruffleString) s, TS_ENCODING) || T___WEAKREF__.equalsUncached((TruffleString) s, TS_ENCODING)))) {
                        count++;
                    }
                }
                return count;
            }
        }

        private static Object getSlotsFromType(Object type, ReadAttributeFromObjectNode readAttr) {
            Object slots = readAttr.execute(type, T___SLOTS__);
            return slots != PNone.NO_VALUE ? slots : null;
        }
    }

    @GenerateUncached
    @ImportStatic(SpecialMethodNames.class)
    @GenerateInline(inlineByDefault = true)
    @GenerateCached
    public abstract static class IsSameTypeNode extends PNodeWithContext {
        public abstract boolean execute(Node inliningTarget, Object left, Object right);

        public final boolean executeCached(Object left, Object right) {
            return execute(this, left, right);
        }

        public static boolean executeUncached(Object left, Object right) {
            return IsSameTypeNodeGen.getUncached().execute(null, left, right);
        }

        public static IsSameTypeNode create() {
            return IsSameTypeNodeGen.create();
        }

        @Specialization
        static boolean doManaged(PythonManagedClass left, PythonManagedClass right) {
            return left == right;
        }

        @Specialization
        static boolean doManaged(PythonBuiltinClassType left, PythonBuiltinClassType right) {
            return left == right;
        }

        @Specialization
        static boolean doManaged(PythonBuiltinClassType left, PythonBuiltinClass right) {
            return left == right.getType();
        }

        @Specialization
        static boolean doManaged(PythonBuiltinClass left, PythonBuiltinClassType right) {
            return left.getType() == right;
        }

        @Specialization
        @InliningCutoff
        static boolean doNativeSingleContext(PythonAbstractNativeObject left, PythonAbstractNativeObject right,
                        @CachedLibrary(limit = "1") InteropLibrary lib) {
            if (left == right) {
                return true;
            }
            if (left.getPtr() instanceof Long && right.getPtr() instanceof Long) {
                return (long) left.getPtr() == (long) right.getPtr();
            }
            return lib.isIdentical(left.getPtr(), right.getPtr(), lib);
        }

        @Fallback
        static boolean doOther(@SuppressWarnings("unused") Object left, @SuppressWarnings("unused") Object right) {
            return false;
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    @ImportStatic(SpecialMethodNames.class)
    public abstract static class ProfileClassNode extends PNodeWithContext {

        public abstract Object execute(Node inliningTarget, Object object);

        public final Object profile(Node inliningTarget, Object object) {
            return execute(inliningTarget, object);
        }

        public final PythonBuiltinClassType profile(Node inliningTarget, PythonBuiltinClassType object) {
            return (PythonBuiltinClassType) execute(inliningTarget, object);
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
    }

    public abstract static class ComputeMroNode extends Node {

        @TruffleBoundary
        public static PythonAbstractClass[] doSlowPath(Node node, PythonAbstractClass cls) {
            return doSlowPath(node, cls, true);
        }

        @TruffleBoundary
        public static PythonAbstractClass[] doSlowPath(Node node, PythonAbstractClass cls, boolean invokeMro) {
            return computeMethodResolutionOrder(node, cls, invokeMro);
        }

        @TruffleBoundary
        static PythonAbstractClass[] invokeMro(Node node, PythonAbstractClass cls) {
            Object type = GetClassNode.executeUncached(cls);
            if (IsTypeNode.executeUncached(type) && type instanceof PythonClass) {
                Object mroMeth = LookupAttributeInMRONode.Dynamic.getUncached().execute(type, T_MRO);
                if (mroMeth instanceof PFunction) {
                    Object mroObj = CallUnaryMethodNode.getUncached().executeObject(mroMeth, cls);
                    if (mroObj instanceof PSequence mroSequence) {
                        SequenceStorage mroStorage = mroSequence.getSequenceStorage();
                        return mroCheck(node, cls, GetInternalObjectArrayNode.executeUncached(mroStorage), mroStorage);
                    }
                    throw PRaiseNode.raiseStatic(node, TypeError, ErrorMessages.OBJ_NOT_ITERABLE, cls);
                }
            }
            return null;
        }

        private static PythonAbstractClass[] computeMethodResolutionOrder(Node node, PythonAbstractClass cls, boolean invokeMro) {
            CompilerAsserts.neverPartOfCompilation();

            PythonAbstractClass[] currentMRO;
            if (invokeMro) {
                PythonAbstractClass[] mro = invokeMro(node, cls);
                if (mro != null) {
                    return mro;
                }
            }

            PythonAbstractClass[] baseClasses = GetBaseClassesNode.executeUncached(cls);
            if (baseClasses.length == 0) {
                currentMRO = new PythonAbstractClass[]{cls};
            } else if (baseClasses.length == 1) {
                PythonAbstractClass[] baseMRO = GetMroNode.executeUncached(baseClasses[0]);

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
                    toMerge[i] = new MROMergeState(GetMroNode.executeUncached(baseClasses[i]));
                }

                toMerge[baseClasses.length] = new MROMergeState(baseClasses);
                ArrayList<PythonAbstractClass> mro = new ArrayList<>();
                mro.add(cls);
                currentMRO = mergeMROs(node, toMerge, mro);
            }
            return currentMRO;
        }

        private static PythonAbstractClass[] mroCheck(Node node, Object cls, Object[] mro, SequenceStorage storage) {
            List<PythonAbstractClass> resultMro = new ArrayList<>(storage.length());
            Object solid = GetSolidBaseNode.executeUncached(cls);
            for (int i = 0; i < storage.length(); i++) {
                Object object = mro[i];
                if (object == null) {
                    continue;
                }
                if (!IsTypeNode.executeUncached(object)) {
                    throw PRaiseNode.raiseStatic(node, TypeError, ErrorMessages.S_RETURNED_NON_CLASS, "mro()", object);
                }
                if (!IsSubtypeNode.getUncached().execute(solid, GetSolidBaseNode.executeUncached(object))) {
                    throw PRaiseNode.raiseStatic(node, TypeError, ErrorMessages.S_RETURNED_BASE_WITH_UNSUITABLE_LAYOUT, "mro()", object);
                }
                resultMro.add((PythonAbstractClass) object);
            }
            return resultMro.toArray(new PythonAbstractClass[resultMro.size()]);
        }

        private static PythonAbstractClass[] mergeMROs(Node node, MROMergeState[] toMerge, List<PythonAbstractClass> mro) {
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
                StringBuilder bases = new StringBuilder(GetNameNode.executeUncached(it.next()).toJavaStringUncached());
                while (it.hasNext()) {
                    bases.append(", ").append(GetNameNode.executeUncached(it.next()));
                }
                throw PRaiseNode.raiseStatic(node, TypeError, ErrorMessages.CANNOT_GET_CONSISTEMT_METHOD_RESOLUTION, bases.toString());
            }

            return mro.toArray(new PythonAbstractClass[mro.size()]);
        }

    }

    @GenerateUncached
    @GenerateInline(inlineByDefault = true)
    @GenerateCached
    @ImportStatic(PGuards.class)
    public abstract static class IsTypeNode extends Node {

        public abstract boolean execute(Node inliningTarget, Object obj);

        public final boolean executeCached(Object obj) {
            return execute(this, obj);
        }

        public static boolean executeUncached(Object obj) {
            return IsTypeNodeGen.getUncached().execute(null, obj);
        }

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
        @InliningCutoff
        static boolean doNativeClass(Node inliningTarget, PythonAbstractNativeObject obj,
                        @Cached IsBuiltinClassProfile profile,
                        @Cached GetPythonObjectClassNode getClassNode,
                        @Cached(inline = false) CExtNodes.PCallCapiFunction nativeTypeCheck) {
            Object type = getClassNode.execute(inliningTarget, obj);
            if (profile.profileClass(inliningTarget, type, PythonBuiltinClassType.PythonClass)) {
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

        @NeverDefault
        public static IsTypeNode create() {
            return IsTypeNodeGen.create();
        }
    }

    @GenerateUncached
    @GenerateInline(false)
    public abstract static class IsAcceptableBaseNode extends Node {

        public abstract boolean execute(Object obj);

        @Specialization
        static boolean doUserClass(PythonClass obj) {
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
                        @Bind Node inliningTarget,
                        @Cached IsTypeNode isType,
                        @Cached GetTypeFlagsNode getFlags) {
            if (isType.execute(inliningTarget, obj)) {
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
    @SuppressWarnings("truffle-inlining")       // footprint reduction 36 -> 18
    public abstract static class GetInstanceShape extends PNodeWithContext {

        public abstract Shape execute(Object clazz);

        public static Shape executeUncached(Object clazz) {
            return TypeNodesFactory.GetInstanceShapeNodeGen.getUncached().execute(clazz);
        }

        @Specialization(guards = "clazz == cachedClazz", limit = "1")
        @SuppressWarnings("unused")
        protected Shape doBuiltinClassTypeCached(PythonBuiltinClassType clazz,
                        @Cached("clazz") PythonBuiltinClassType cachedClazz) {
            return cachedClazz.getInstanceShape(getLanguage());
        }

        @Specialization(replaces = "doBuiltinClassTypeCached")
        protected Shape doBuiltinClassType(PythonBuiltinClassType clazz) {
            return clazz.getInstanceShape(getLanguage());
        }

        @Specialization(guards = {"isSingleContext()", "clazz == cachedClazz"}, limit = "3")
        @SuppressWarnings("unused")
        protected static Shape doBuiltinClassCached(PythonBuiltinClass clazz,
                        @Cached("clazz") PythonBuiltinClass cachedClazz) {
            return cachedClazz.getInstanceShape();
        }

        @Specialization(guards = {"isSingleContext()", "clazz == cachedClazz"}, limit = "3")
        @SuppressWarnings("unused")
        protected static Shape doClassCached(PythonClass clazz,
                        @Cached("clazz") PythonClass cachedClazz) {
            return cachedClazz.getInstanceShape();
        }

        @Specialization(replaces = {"doClassCached", "doBuiltinClassCached"})
        protected static Shape doManagedClass(PythonManagedClass clazz) {
            return clazz.getInstanceShape();
        }

        @Specialization
        @InliningCutoff
        protected static Shape doNativeClass(PythonAbstractNativeObject clazz,
                        @Bind Node inliningTarget,
                        @Cached CStructAccess.ReadObjectNode getTpDictNode,
                        @Cached HiddenAttr.ReadNode readAttrNode) {
            Object tpDictObj = getTpDictNode.readFromObj(clazz, CFields.PyTypeObject__tp_dict);
            if (tpDictObj instanceof PythonManagedClass) {
                return ((PythonManagedClass) tpDictObj).getInstanceShape();
            }
            if (tpDictObj instanceof PDict dict) {
                Object instanceShapeObj = readAttrNode.execute(inliningTarget, dict, HiddenAttr.INSTANCESHAPE, PNone.NO_VALUE);
                if (instanceShapeObj != PNone.NO_VALUE) {
                    return (Shape) instanceShapeObj;
                }
                throw CompilerDirectives.shouldNotReachHere("instanceshape object is not a shape");
            }
            // TODO(fa): track unique shape per native class in language?
            throw CompilerDirectives.shouldNotReachHere("custom dicts for native classes are unsupported");
        }

        @Specialization(guards = {"!isManagedClass(clazz)", "!isPythonBuiltinClassType(clazz)"})
        @InliningCutoff
        protected static Shape doError(@SuppressWarnings("unused") Object clazz,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, PythonBuiltinClassType.SystemError, ErrorMessages.CANNOT_GET_SHAPE_OF_NATIVE_CLS);
        }

        public static GetInstanceShape getUncached() {
            return TypeNodesFactory.GetInstanceShapeNodeGen.getUncached();
        }
    }

    @ImportStatic({SpecialMethodNames.class, SpecialAttributeNames.class})
    public abstract static class CreateTypeNode extends Node {
        public abstract PythonClass execute(VirtualFrame frame, PDict namespaceOrig, TruffleString name, PTuple bases, Object metaclass, PKeyword[] kwds);

        @Child private ReadAttributeFromObjectNode readAttrNode;
        @Child private ReadCallerFrameNode readCallerFrameNode;
        @Child private CastToTruffleStringNode castToStringNode;

        private ReadAttributeFromObjectNode ensureReadAttrNode() {
            if (readAttrNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readAttrNode = insert(ReadAttributeFromObjectNode.create());
            }
            return readAttrNode;
        }

        private ReadCallerFrameNode getReadCallerFrameNode() {
            if (readCallerFrameNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readCallerFrameNode = insert(ReadCallerFrameNode.create());
            }
            return readCallerFrameNode;
        }

        private CastToTruffleStringNode ensureCastToStringNode() {
            if (castToStringNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castToStringNode = insert(CastToTruffleStringNode.create());
            }
            return castToStringNode;
        }

        @Specialization
        protected PythonClass makeType(VirtualFrame frame, PDict namespaceOrig, TruffleString name, PTuple bases, Object metaclass, PKeyword[] kwds,
                        @Bind Node inliningTarget,
                        @Cached HashingStorage.InitNode initNode,
                        @Cached HashingStorageGetItem getItemGlobals,
                        @Cached HashingStorageGetItem getItemNamespace,
                        @Cached HashingStorageGetIterator getIterator,
                        @Cached HashingStorageIteratorNext itNext,
                        @Cached HashingStorageIteratorKey itKey,
                        @Cached HashingStorageIteratorValue itValue,
                        @Cached HashingStorageDelItem delItemNamespace,
                        @Cached GetClassNode getClassNode,
                        @Cached("create(T___SET_NAME__)") LookupSpecialMethodNode getSetNameNode,
                        @Cached CallNode callSetNameNode,
                        @Cached CallNode callInitSubclassNode,
                        @Cached("create(T___INIT_SUBCLASS__)") GetAttributeNode getInitSubclassNode,
                        @Cached GetMroStorageNode getMroStorageNode,
                        @Bind PythonLanguage language,
                        @Cached PRaiseNode raise,
                        @Cached ExceptionNodes.FormatNoteNode formatNoteNode,
                        @Cached AllocateTypeWithMetaclassNode typeMetaclass,
                        @Cached GetOrCreateDictNode getOrCreateDictNode) {
            PDict namespace = PFactory.createDict(language);
            namespace.setDictStorage(initNode.execute(frame, namespaceOrig, PKeyword.EMPTY_KEYWORDS));
            PythonClass newType = typeMetaclass.execute(frame, name, bases, namespace, metaclass);

            // set '__module__' attribute
            Object moduleAttr = ensureReadAttrNode().execute(newType, SpecialAttributeNames.T___MODULE__);
            if (moduleAttr == PNone.NO_VALUE) {
                PythonObject globals;
                if (getRootNode() instanceof BuiltinFunctionRootNode) {
                    PFrame callerFrame = getReadCallerFrameNode().executeWith(frame, 0);
                    globals = callerFrame != null ? callerFrame.getGlobals() : null;
                } else {
                    globals = PArguments.getGlobals(frame);
                }
                if (globals != null) {
                    TruffleString moduleName = getModuleNameFromGlobals(inliningTarget, globals, getItemGlobals);
                    if (moduleName != null) {
                        newType.setAttribute(SpecialAttributeNames.T___MODULE__, moduleName);
                    }
                }
            }

            // delete __qualname__ from namespace
            delItemNamespace.execute(inliningTarget, namespace.getDictStorage(), T___QUALNAME__, namespace);

            // initialize '__doc__' attribute
            if (newType.getAttribute(SpecialAttributeNames.T___DOC__) == PNone.NO_VALUE) {
                newType.setAttribute(SpecialAttributeNames.T___DOC__, PNone.NONE);
            }

            // set __class__ cell contents
            Object classcell = getItemNamespace.execute(inliningTarget, namespace.getDictStorage(), SpecialAttributeNames.T___CLASSCELL__);
            if (classcell != null) {
                if (classcell instanceof PCell) {
                    ((PCell) classcell).setRef(newType);
                } else {
                    throw raise.raise(inliningTarget, TypeError, ErrorMessages.MUST_BE_A_CELL, "__classcell__");
                }
                delItemNamespace.execute(inliningTarget, namespace.getDictStorage(), SpecialAttributeNames.T___CLASSCELL__, namespace);
            }

            // set __classdict__ cell contents
            Object classdictcell = getItemNamespace.execute(inliningTarget, namespace.getDictStorage(), SpecialAttributeNames.T___CLASSDICTCELL__);
            if (classdictcell != null) {
                if (classdictcell instanceof PCell cell) {
                    cell.setRef(getOrCreateDictNode.execute(inliningTarget, newType));
                } else {
                    throw raise.raise(inliningTarget, TypeError, ErrorMessages.MUST_BE_A_CELL, "__classdictcell__");
                }
                delItemNamespace.execute(inliningTarget, namespace.getDictStorage(), SpecialAttributeNames.T___CLASSDICTCELL__, namespace);
            }

            // Initialization of the type slots:
            //
            // For now, we have the same helper functions as CPython and we execute them in the
            // same order even-though we could squash them and optimize the wrapping and
            // unwrapping of slots, but it would be more difficult to make sure that at the end
            // of the day we produce exactly the same results, especially if there are native
            // types in the MRO (we can, e.g., optimize this only for pure Python types to keep
            // it still simple).
            //
            // From CPython point of view: we are in "type_new_impl", we call PyType_Ready,
            // which calls:
            //
            // - type_ready_fill_dict, which calls add_operators, which fills the dunder methods
            // from the slots. Here we are creating a managed type, so there cannot be any
            // native slots, so this is a no-op for us.
            //
            // - type_ready_inherit to inherit the type slots. Here we may inherit slots from
            // native classes in the MRO and things would get complicated if we did not follow
            // CPython structure.
            //
            // - type_ready_set_hash: sets tp_hash=PyObject_HashNotImplemented and __hash__=None
            // if tp_hash is NULL (it must be, this is managed class) and there's no __hash__
            // magic method.
            //
            // - fixup_slot_dispatchers to set the slots according to magic methods.

            Builder inheritedSlots = TpSlots.buildInherited(newType, namespace, getMroStorageNode.execute(inliningTarget, newType), true);
            // type_ready_set_hash
            if (inheritedSlots.get(TpSlotMeta.TP_HASH) == null) {
                Object dunderHash = getItemNamespace.execute(inliningTarget, namespace.getDictStorage(), T___HASH__);
                if (dunderHash == null) {
                    inheritedSlots.set(TpSlotMeta.TP_HASH, TpSlotHashFun.HASH_NOT_IMPLEMENTED);
                    newType.setAttribute(T___HASH__, PNone.NONE);
                }
            }
            TpSlots.fixupSlotDispatchers(newType, inheritedSlots);
            newType.setTpSlots(inheritedSlots.build());

            HashingStorage storage = namespace.getDictStorage();
            HashingStorageIterator it = getIterator.execute(inliningTarget, storage);
            while (itNext.execute(inliningTarget, storage, it)) {
                Object value = itValue.execute(inliningTarget, storage, it);
                Object setName = getSetNameNode.execute(frame, getClassNode.execute(inliningTarget, value), value);
                if (setName != PNone.NO_VALUE) {
                    Object key = itKey.execute(inliningTarget, storage, it);
                    try {
                        callSetNameNode.execute(frame, setName, value, newType, key);
                    } catch (PException e) {
                        formatNoteNode.execute(frame, inliningTarget, e, ErrorMessages.ERROR_CALLING_SET_NAME, value, key, newType);
                        throw e;
                    }
                }
            }

            // Call __init_subclass__ on the parent of a newly generated type
            SuperObject superObject = PFactory.createSuperObject(language);
            superObject.init(newType, newType, newType);
            callInitSubclassNode.execute(frame, getInitSubclassNode.executeObject(frame, superObject), PythonUtils.EMPTY_OBJECT_ARRAY, kwds);

            newType.initializeMroShape(language);

            return newType;
        }

        private TruffleString getModuleNameFromGlobals(Node inliningTarget, PythonObject globals, HashingStorageGetItem getItem) {
            Object nameAttr;
            if (globals instanceof PythonModule) {
                nameAttr = ensureReadAttrNode().execute(globals, SpecialAttributeNames.T___NAME__);
            } else if (globals instanceof PDict) {
                nameAttr = getItem.execute(inliningTarget, ((PDict) globals).getDictStorage(), SpecialAttributeNames.T___NAME__);
            } else {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException("invalid globals object");
            }
            if (nameAttr == null || nameAttr == PNone.NO_VALUE) {
                return null;
            }
            try {
                return ensureCastToStringNode().executeCached(nameAttr);
            } catch (CannotCastException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException();
            }
        }
    }

    @ImportStatic({SpecialMethodNames.class, SpecialAttributeNames.class})
    @GenerateInline(false) // footprint reduction 208 -> 190
    protected abstract static class AllocateTypeWithMetaclassNode extends Node {

        public abstract PythonClass execute(VirtualFrame frame, TruffleString name, PTuple bases, PDict namespace, Object metaclass);

        @ValueType
        private static class TypeNewContext {
            boolean addDict;
            boolean addWeak;
            boolean mayAddDict;
            boolean mayAddWeak;
            Object slotsObject;
            TruffleString[] copiedSlots;
            boolean qualnameSet;
        }

        @Specialization
        static PythonClass typeMetaclass(VirtualFrame frame, TruffleString name, PTuple bases, PDict namespace, Object metaclass,
                        @Bind Node inliningTarget,
                        @Cached("createFor($node)") IndirectCallData indirectCallData,
                        @Cached HashingStorageSetItemWithHash setHashingStorageItem,
                        @Cached GetOrCreateDictNode getOrCreateDictNode,
                        @Cached HashingStorageGetIterator getHashingStorageIterator,
                        @Cached HashingStorageIteratorNext hashingStorageItNext,
                        @Cached HashingStorageIteratorKey hashingStorageItKey,
                        @Cached HashingStorageIteratorKeyHash hashingStorageItKeyHash,
                        @Cached HashingStorageIteratorValue hashingStorageItValue,
                        @Cached SequenceStorageNodes.GetItemScalarNode getItemNode,
                        @Cached GetDictOffsetNode dictOffsetNode,
                        @Cached InstancesOfTypeHaveWeakrefsNode hasWeakrefsNode,
                        @Cached GetBestBaseClassNode getBestBaseNode,
                        @Cached GetIndexedSlotsCountNode getIndexedSlotsCountNode,
                        @Cached IsIdentifierNode isIdentifier,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode,
                        @Cached PRaiseNode raise,
                        @Cached GetObjectArrayNode getObjectArray,
                        @Cached GetInstanceShape getInstanceShape,
                        @Cached CastToListNode castToListNode,
                        @Cached PyUnicodeCheckNode stringCheck,
                        @Cached TruffleString.IsValidNode isValidNode,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.IndexOfCodePointNode indexOfCodePointNode,
                        @Cached TruffleString.EqualNode equalNode,
                        @Cached CastToTruffleStringNode castToStringNode,
                        @Cached GetItemSizeNode getItemSize) {
            PythonContext context = PythonContext.get(inliningTarget);
            PythonLanguage language = context.getLanguage(inliningTarget);
            Python3Core core = context.getCore();
            TypeNewContext ctx = new TypeNewContext();
            Object[] array = getObjectArray.execute(inliningTarget, bases);

            PythonAbstractClass[] basesArray;
            if (array.length == 0) {
                // Adjust for empty tuple bases
                basesArray = new PythonAbstractClass[]{core.lookupType(PythonBuiltinClassType.PythonObject)};
            } else {
                basesArray = new PythonAbstractClass[array.length];
                for (int i = 0; i < array.length; i++) {
                    // TODO: deal with non-class bases
                    if (PythonAbstractClass.isInstance(array[i])) {
                        basesArray[i] = (PythonAbstractClass) array[i];
                    } else if (array[i] instanceof PythonBuiltinClassType) {
                        basesArray[i] = core.lookupType((PythonBuiltinClassType) array[i]);
                    } else {
                        throw raise.raise(inliningTarget, PythonBuiltinClassType.NotImplementedError, ErrorMessages.CREATING_CLASS_NON_CLS_BASES);
                    }
                }
            }
            // check for possible layout conflicts
            PythonAbstractClass base = getBestBaseNode.execute(inliningTarget, basesArray);

            assert metaclass != null;

            if (!isValidNode.execute(name, TS_ENCODING)) {
                throw constructAndRaiseNode.get(inliningTarget).raiseUnicodeEncodeError(frame, "utf-8", name, 0, codePointLengthNode.execute(name, TS_ENCODING), "can't encode class name");
            }
            if (indexOfCodePointNode.execute(name, 0, 0, codePointLengthNode.execute(name, TS_ENCODING), TS_ENCODING) >= 0) {
                throw raise.raise(inliningTarget, PythonBuiltinClassType.ValueError, ErrorMessages.TYPE_NAME_NO_NULL_CHARS);
            }

            // 1.) create class, but avoid calling mro method - it might try to access __dict__ so
            // we have to copy dict slots first
            PythonClass pythonClass = PFactory.createPythonClass(language, metaclass, getInstanceShape.execute(metaclass), name, false, base, basesArray);

            // 2.) copy the dictionary slots
            copyDictSlots(frame, inliningTarget, language, ctx, pythonClass, namespace, setHashingStorageItem,
                            getHashingStorageIterator, hashingStorageItNext, hashingStorageItKey, hashingStorageItKeyHash, hashingStorageItValue,
                            constructAndRaiseNode, raise, isValidNode, equalNode, codePointLengthNode, getOrCreateDictNode, stringCheck, castToStringNode);
            if (!ctx.qualnameSet) {
                pythonClass.setQualName(name);
            }

            // 3.) invoke metaclass mro() method
            pythonClass.invokeMro(inliningTarget);

            // see cpython://Objects/typeobject.c#type_new_slots
            // may_add_dict = base->tp_dictoffset == 0
            ctx.mayAddDict = dictOffsetNode.execute(inliningTarget, base) == 0;
            // may_add_weak = base->tp_weaklistoffset == 0 && base->tp_itemsize == 0
            boolean hasItemSize = getItemSize.execute(inliningTarget, base) != 0;
            ctx.mayAddWeak = !hasWeakrefsNode.execute(inliningTarget, base) && !hasItemSize;

            if (ctx.slotsObject == null) {
                if (ctx.mayAddDict) {
                    ctx.addDict = true;
                }
                if (ctx.mayAddWeak) {
                    ctx.addWeak = true;
                }
            } else {
                // see cpython://Objects/typeobject.c#type_new_slots_impl
                // have slots
                // Make it into a list
                SequenceStorage slotsStorage;
                Object slotsObject = ctx.slotsObject;
                if (stringCheck.execute(inliningTarget, ctx.slotsObject)) {
                    slotsStorage = new ObjectSequenceStorage(new Object[]{castToStringNode.execute(inliningTarget, ctx.slotsObject)});
                } else if (ctx.slotsObject instanceof PTuple slotsTuple) {
                    slotsStorage = slotsTuple.getSequenceStorage();
                } else if (ctx.slotsObject instanceof PList slotsList) {
                    slotsStorage = slotsList.getSequenceStorage();
                } else {
                    PList slotsList = castToListNode.execute(frame, ctx.slotsObject);
                    slotsObject = slotsList;
                    slotsStorage = slotsList.getSequenceStorage();
                }
                int slotlen = slotsStorage.length();

                if (slotlen > 0 && hasItemSize) {
                    throw raise.raise(inliningTarget, TypeError, ErrorMessages.NONEMPTY_SLOTS_NOT_ALLOWED_FOR_SUBTYPE_OF_S, base);
                }

                for (int i = 0; i < slotlen; i++) {
                    TruffleString slotName;
                    Object element = getItemNode.execute(inliningTarget, slotsStorage, i);
                    // Check valid slot name
                    if (stringCheck.execute(inliningTarget, element)) {
                        slotName = castToStringNode.execute(inliningTarget, element);
                        if (!(boolean) isIdentifier.execute(frame, slotName)) {
                            throw raise.raise(inliningTarget, TypeError, ErrorMessages.SLOTS_MUST_BE_IDENTIFIERS);
                        }
                    } else {
                        throw raise.raise(inliningTarget, TypeError, ErrorMessages.MUST_BE_STRINGS_NOT_P, "__slots__ items", element);
                    }
                    if (equalNode.execute(slotName, T___DICT__, TS_ENCODING)) {
                        if (!ctx.mayAddDict || ctx.addDict) {
                            throw raise.raise(inliningTarget, TypeError, ErrorMessages.DICT_SLOT_DISALLOWED_WE_GOT_ONE);
                        }
                        ctx.addDict = true;
                        addDictDescrAttribute(basesArray, pythonClass, language);
                    } else if (equalNode.execute(slotName, T___WEAKREF__, TS_ENCODING)) {
                        if (!ctx.mayAddWeak || ctx.addWeak) {
                            throw raise.raise(inliningTarget, TypeError, ErrorMessages.WEAKREF_SLOT_DISALLOWED_WE_GOT_ONE);
                        }
                        ctx.addWeak = true;
                    }
                }
                // Make slots into a tuple
                Object state = IndirectCallContext.enter(frame, language, context, indirectCallData);
                try {
                    pythonClass.setAttribute(T___SLOTS__, slotsObject);

                    // checks for some name errors too
                    ctx.copiedSlots = copySlots(inliningTarget, ctx, name, slotsStorage, slotlen, namespace);
                } finally {
                    IndirectCallContext.exit(frame, language, context, state);
                }
                /* Secondary bases may provide weakrefs or dict */
                typeNewSlotBases(ctx, base, basesArray);
            }

            int indexedSlotCount = getIndexedSlotsCountNode.execute(inliningTarget, base);
            if (ctx.copiedSlots != null) {
                for (TruffleString slotName : ctx.copiedSlots) {
                    IndexedSlotDescriptor slotDesc = PFactory.createIndexedSlotDescriptor(language, slotName, indexedSlotCount++, pythonClass);
                    pythonClass.setAttribute(slotName, slotDesc);
                }
            }
            pythonClass.setIndexedSlotCount(indexedSlotCount);

            if (ctx.addDict) {
                addDictDescrAttribute(basesArray, pythonClass, language);
            } else if (ctx.mayAddDict) {
                pythonClass.setHasSlotsButNoDictFlag();
            }
            if (ctx.addWeak) {
                addWeakrefDescrAttribute(pythonClass, language);
            }

            if (pythonClass.needsNativeAllocation()) {
                addNativeSlots(ctx, pythonClass, base);
            }

            return pythonClass;
        }

        // equivalent of type_new_slot_bases in CPython
        private static void typeNewSlotBases(TypeNewContext ctx, Object primaryBase, PythonAbstractClass[] basesArray) {
            if (basesArray.length > 1 && (ctx.mayAddDict && !ctx.addDict || ctx.mayAddWeak && !ctx.addWeak)) {
                for (PythonAbstractClass base : basesArray) {
                    if (base == primaryBase) {
                        /* Skip primary base */
                        continue;
                    }
                    if (ctx.mayAddDict && !ctx.addDict && InstancesOfTypeHaveDictNode.executeUncached(base)) {
                        ctx.addDict = true;
                    }
                    if (ctx.mayAddWeak && !ctx.addWeak && InstancesOfTypeHaveWeakrefsNode.executeUncached(base)) {
                        ctx.addWeak = true;
                    }
                    if (!(ctx.mayAddDict && !ctx.addDict || ctx.mayAddWeak && !ctx.addWeak)) {
                        break;
                    }
                }
            }
        }

        @TruffleBoundary
        private static void addDictDescrAttribute(PythonAbstractClass[] basesArray, PythonClass pythonClass, PythonLanguage language) {
            // Note: we need to avoid MRO lookup of __dict__ using slots because they are not
            // initialized yet
            if ((!hasPythonClassBases(basesArray) && LookupAttributeInMRONode.lookupSlowPath(pythonClass, T___DICT__) == PNone.NO_VALUE) || basesHaveSlots(basesArray)) {
                Builtin dictBuiltin = ObjectBuiltins.DictNode.class.getAnnotation(Builtin.class);
                RootCallTarget callTarget = PythonLanguage.get(null).createCachedCallTarget(
                                l -> new BuiltinFunctionRootNode(l, dictBuiltin, ObjectBuiltinsFactory.DictNodeFactory.getInstance(), true), ObjectBuiltins.DictNode.class);
                setAttribute(T___DICT__, dictBuiltin, callTarget, pythonClass, language);
            }
        }

        @TruffleBoundary
        private static void addWeakrefDescrAttribute(PythonClass pythonClass, PythonLanguage language) {
            if (LookupAttributeInMRONode.lookupSlowPath(pythonClass, T___WEAKREF__) == PNone.NO_VALUE) {
                Builtin builtin = GetWeakRefsNode.class.getAnnotation(Builtin.class);
                RootCallTarget callTarget = PythonLanguage.get(null).createCachedCallTarget(
                                l -> new BuiltinFunctionRootNode(l, builtin, WeakRefModuleBuiltinsFactory.GetWeakRefsNodeFactory.getInstance(), true), GetWeakRefsNode.class);
                setAttribute(T___WEAKREF__, builtin, callTarget, pythonClass, language);
            }
        }

        private static void setAttribute(TruffleString name, Builtin builtin, RootCallTarget callTarget, PythonClass pythonClass, PythonLanguage language) {
            int flags = PBuiltinFunction.getFlags(builtin, callTarget);
            PBuiltinFunction function = PFactory.createBuiltinFunction(language, name, pythonClass, 1, flags, callTarget);
            GetSetDescriptor desc = PFactory.createGetSetDescriptor(language, function, function, name, pythonClass, true);
            pythonClass.setAttribute(name, desc);
        }

        private static boolean basesHaveSlots(PythonAbstractClass[] basesArray) {
            // this is merely based on empirical observation
            // see also test_type.py#test_dict()
            for (PythonAbstractClass c : basesArray) {
                // TODO: what about native?
                if (c instanceof PythonClass) {
                    if (((PythonClass) c).getAttribute(T___SLOTS__) != PNone.NO_VALUE) {
                        return true;
                    }
                }
            }
            return false;
        }

        private static boolean hasPythonClassBases(PythonAbstractClass[] basesArray) {
            for (PythonAbstractClass c : basesArray) {
                if (c instanceof PythonClass) {
                    return true;
                }
            }
            return false;
        }

        /**
         * If a managed type inherits from a native type (which means that the object will be
         * allocated in native) and if the type has {@code __slots__}, we need to do following:
         *
         * <ol>
         * <li>We need to increase the basicsize by {@code sizeof(PyObject *)} for each name in
         * {@code __slots__} since each dynamic slot automatically becomes a
         * {@link CApiMemberAccessNodes#T_OBJECT_EX} member.</li>
         * <li>We need to install a member descriptor for each dynamic slot.</li>
         * <li>We need to set tp_dictoffset and tp_weaklistoffset and adjust the basicsize
         * accordingly</li>
         * </ol>
         * <p>
         * Mostly based on type_new_descriptors
         */
        @TruffleBoundary
        private static void addNativeSlots(TypeNewContext ctx, PythonManagedClass pythonClass, Object base) {
            long slotOffset = GetBasicSizeNode.executeUncached(base);
            if (ctx.copiedSlots != null) {
                if (ctx.copiedSlots.length != 0) {
                    if (slotOffset == 0) {
                        throw CompilerDirectives.shouldNotReachHere("tp_basicsize not set on a type");
                    }
                    slotOffset = installMemberDescriptors(pythonClass, ctx.copiedSlots, slotOffset);
                }
            }
            long dictOffset = GetDictOffsetNode.executeUncached(base);
            long weakListOffset = GetWeakListOffsetNode.executeUncached(base);
            long itemSize = GetItemSizeNode.executeUncached(base);
            if (ctx.addDict) {
                long flags = GetTypeFlagsNode.executeUncached(pythonClass);
                SetTypeFlagsNode.executeUncached(pythonClass, flags | MANAGED_DICT);
                dictOffset = -1;
            }
            if (ctx.addWeak) {
                weakListOffset = slotOffset;
            }

            SetDictOffsetNode.executeUncached(pythonClass, dictOffset);
            SetBasicSizeNode.executeUncached(pythonClass, slotOffset);
            SetItemSizeNode.executeUncached(pythonClass, itemSize);
            SetWeakListOffsetNode.executeUncached(pythonClass, weakListOffset);
        }

        @TruffleBoundary
        private static long installMemberDescriptors(PythonManagedClass pythonClass, TruffleString[] slotNames, long slotOffset) {
            PDict typeDict = GetOrCreateDictNode.executeUncached(pythonClass);
            for (TruffleString slotName : slotNames) {
                PyTruffleType_AddMember.addMember(pythonClass, typeDict, slotName, CApiMemberAccessNodes.T_OBJECT_EX, slotOffset, 1, PNone.NO_VALUE);
                slotOffset += SIZEOF_PY_OBJECT_PTR;
            }
            return slotOffset;
        }

        private static void copyDictSlots(VirtualFrame frame, Node inliningTarget, PythonLanguage language, TypeNewContext ctx, PythonClass pythonClass, PDict namespace,
                        HashingStorageSetItemWithHash setHashingStorageItem,
                        HashingStorageGetIterator getHashingStorageIterator, HashingStorageIteratorNext hashingStorageItNext, HashingStorageIteratorKey hashingStorageItKey,
                        HashingStorageIteratorKeyHash hashingStorageItKeyHash, HashingStorageIteratorValue hashingStorageItValue,
                        PConstructAndRaiseNode.Lazy constructAndRaiseNode, PRaiseNode raise, IsValidNode isValidNode,
                        EqualNode equalNode, CodePointLengthNode codePointLengthNode, GetOrCreateDictNode getOrCreateDictNode, PyUnicodeCheckNode stringCheck,
                        CastToTruffleStringNode castToStringNode) {
            // copy the dictionary slots over, as CPython does through PyDict_Copy
            // Also check for a __slots__ sequence variable in dict
            PDict typeDict = null;
            HashingStorage namespaceStorage = namespace.getDictStorage();
            HashingStorageIterator it = getHashingStorageIterator.execute(inliningTarget, namespaceStorage);
            while (hashingStorageItNext.execute(inliningTarget, namespaceStorage, it)) {
                Object keyObj = hashingStorageItKey.execute(inliningTarget, namespaceStorage, it);
                Object value = hashingStorageItValue.execute(inliningTarget, namespaceStorage, it);
                if (stringCheck.execute(inliningTarget, keyObj)) {
                    TruffleString key = castToStringNode.execute(inliningTarget, keyObj);
                    if (equalNode.execute(T___SLOTS__, key, TS_ENCODING)) {
                        ctx.slotsObject = value;
                        continue;
                    }
                    if (equalNode.execute(T___NEW__, key, TS_ENCODING)) {
                        // see CPython: if it's a plain function, make it a static function
                        if (value instanceof PFunction) {
                            pythonClass.setAttribute(key, PFactory.createStaticmethodFromCallableObj(language, value));
                        } else {
                            pythonClass.setAttribute(key, value);
                        }
                        continue;
                    }
                    if (equalNode.execute(T___INIT_SUBCLASS__, key, TS_ENCODING) || equalNode.execute(T___CLASS_GETITEM__, key, TS_ENCODING)) {
                        // see CPython: Special-case __init_subclass__ and
                        // __class_getitem__: if they are plain functions, make them
                        // classmethods
                        if (value instanceof PFunction) {
                            pythonClass.setAttribute(key, PFactory.createClassmethodFromCallableObj(language, value));
                        } else {
                            pythonClass.setAttribute(key, value);
                        }
                        continue;
                    }
                    if (equalNode.execute(T___DOC__, key, TS_ENCODING)) {
                        // CPython sets tp_doc to a copy of dict['__doc__'], if that is a string. It
                        // forcibly encodes the string as UTF-8, and raises an error if that is not
                        // possible.
                        try {
                            TruffleString doc = castToStringNode.execute(inliningTarget, value);
                            if (!isValidNode.execute(doc, TS_ENCODING)) {
                                throw constructAndRaiseNode.get(inliningTarget).raiseUnicodeEncodeError(frame, "utf-8", doc, 0, codePointLengthNode.execute(doc, TS_ENCODING),
                                                "can't encode docstring");
                            }
                        } catch (CannotCastException e) {
                            // ignore
                        }
                        pythonClass.setAttribute(key, value);
                        continue;
                    }
                    if (equalNode.execute(T___QUALNAME__, key, TS_ENCODING)) {
                        try {
                            pythonClass.setQualName(castToStringNode.execute(inliningTarget, value));
                            ctx.qualnameSet = true;
                        } catch (CannotCastException e) {
                            throw raise.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.MUST_BE_S_NOT_P, "type __qualname__", "str", value);
                        }
                        continue;
                    }
                    if (equalNode.execute(T___CLASSCELL__, key, TS_ENCODING)) {
                        // don't populate this attribute
                        continue;
                    }
                    if (typeDict == null && keyObj instanceof TruffleString) {
                        pythonClass.setAttribute(key, value);
                        continue;
                    }
                }
                // Creates DynamicObjectStorage which ignores non-string keys
                typeDict = getOrCreateDictNode.execute(inliningTarget, pythonClass);
                // Writing a non string key converts DynamicObjectStorage to EconomicMapStorage
                long keyHash = hashingStorageItKeyHash.execute(frame, inliningTarget, namespaceStorage, it);
                HashingStorage updatedStore = setHashingStorageItem.execute(frame, inliningTarget, typeDict.getDictStorage(), keyObj, keyHash, value);
                typeDict.setDictStorage(updatedStore);
            }
        }

        @TruffleBoundary
        private static TruffleString[] copySlots(Node inliningTarget, TypeNewContext ctx, TruffleString className, SequenceStorage slotList, int slotlen, PDict namespace) {
            int nslots = slotlen - PInt.intValue(ctx.addDict) - PInt.intValue(ctx.addWeak);
            TruffleString[] newSlots = new TruffleString[nslots];
            int j = 0;
            for (int i = 0; i < slotlen; i++) {
                // the cast is ensured by the previous loop
                // n.b.: passing the null frame here is fine, since the storage and index are known
                // types
                TruffleString slotName = CastToTruffleStringNode.executeUncached(GetItemScalarNode.executeUncached(slotList, i));
                if ((ctx.addDict && T___DICT__.equalsUncached(slotName, TS_ENCODING)) || (ctx.addWeak && T___WEAKREF__.equalsUncached(slotName, TS_ENCODING))) {
                    continue;
                }

                try {
                    slotName = PythonUtils.mangleName(className, slotName);
                } catch (OutOfMemoryError e) {
                    throw PRaiseNode.raiseStatic(inliningTarget, PythonBuiltinClassType.OverflowError, ErrorMessages.PRIVATE_IDENTIFIER_TOO_LARGE_TO_BE_MANGLED);
                }
                if (slotName == null) {
                    return null;
                }

                newSlots[j] = slotName;
                // Passing 'null' frame is fine because the caller already transfers the exception
                // state to the context.
                if (!T___CLASSCELL__.equalsUncached(slotName, TS_ENCODING) && !T___CLASSDICTCELL__.equalsUncached(slotName, TS_ENCODING) && !T___QUALNAME__.equalsUncached(slotName, TS_ENCODING) &&
                                HashingStorageGetItem.hasKeyUncached(namespace.getDictStorage(), slotName)) {
                    // __qualname__, __classcell__ and __classdictcell__ will be deleted later
                    throw PRaiseNode.raiseStatic(inliningTarget, PythonBuiltinClassType.ValueError, ErrorMessages.S_S_CONFLICTS_WITH_CLASS_VARIABLE, slotName, "__slots__");
                }
                j++;
            }
            assert j == nslots;

            // sort newSlots
            Arrays.sort(newSlots, StringUtils::compareStringsUncached);

            return newSlots;
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    @SuppressWarnings("truffle-inlining")       // footprint reduction 44 -> 26
    public abstract static class GetBasicSizeNode extends Node {
        public abstract long execute(Node inliningTarget, Object cls);

        public static long executeUncached(Object cls) {
            return GetBasicSizeNodeGen.getUncached().execute(null, cls);
        }

        @Specialization
        long lookup(Object cls,
                        @Cached CExtNodes.LookupNativeI64MemberFromBaseNode lookup) {
            return lookup.execute(cls, PyTypeObject__tp_basicsize, BASICSIZE);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class SetBasicSizeNode extends Node {
        public abstract void execute(Node inliningTarget, PythonManagedClass cls, long value);

        public static void executeUncached(PythonManagedClass cls, long value) {
            TypeNodesFactory.SetBasicSizeNodeGen.getUncached().execute(null, cls, value);
        }

        @Specialization
        static void set(Node inliningTarget, PythonManagedClass cls, long value,
                        @Cached HiddenAttr.WriteNode write) {
            write.execute(inliningTarget, cls, BASICSIZE, value);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class GetItemSizeNode extends Node {
        public abstract long execute(Node inliningTarget, Object cls);

        public static long executeUncached(Object cls) {
            return TypeNodesFactory.GetItemSizeNodeGen.getUncached().execute(null, cls);
        }

        @Specialization
        static long lookup(Object cls,
                        @Cached(inline = false) CExtNodes.LookupNativeI64MemberFromBaseNode lookup) {
            return lookup.execute(cls, PyTypeObject__tp_itemsize, ITEMSIZE, GetItemSizeNode::getBuiltinTypeItemsize);
        }

        private static int getBuiltinTypeItemsize(PythonBuiltinClassType cls) {
            // Our formatter currently forces all the case labels on a single line
            // @formatter:off
            return switch (cls) {
                case PBytes -> 1;
                case PCode -> 2;
                case PInt, Boolean -> 4;
                case PAsyncGenerator, PFlags, PHashInfo, PTuple, PCoroutine, PGenerator, PThreadInfo, PMemoryView,
                     PStatResult, PUnameResult, PStructTime, PFloatInfo, PStatvfsResult, PIntInfo, PFrame,
                     PTerminalSize, PUnraisableHookArgs -> 8;
                case PythonClass -> 40;
                default -> 0;
            };
            // @formatter:on
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class SetItemSizeNode extends Node {
        public abstract void execute(Node inliningTarget, PythonManagedClass cls, long value);

        public static void executeUncached(PythonManagedClass cls, long value) {
            TypeNodesFactory.SetItemSizeNodeGen.getUncached().execute(null, cls, value);
        }

        @Specialization
        static void set(Node inliningTarget, PythonManagedClass cls, long value,
                        @Cached HiddenAttr.WriteNode write) {
            write.execute(inliningTarget, cls, ITEMSIZE, value);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class GetDictOffsetNode extends Node {
        private static final long MANAGED_DICT_OFFSET = -8;

        public abstract long execute(Node inliningTarget, Object cls);

        public static long executeUncached(Object cls) {
            return TypeNodesFactory.GetDictOffsetNodeGen.getUncached().execute(null, cls);
        }

        @Specialization
        static long lookup(Object cls,
                        @Cached(inline = false) GetTypeFlagsNode getTypeFlagsNode,
                        @Cached(inline = false) CExtNodes.LookupNativeI64MemberFromBaseNode lookup) {
            long result = lookup.execute(cls, PyTypeObject__tp_dictoffset, DICTOFFSET, PythonBuiltinClassType::getDictoffset);
            if (result == 0 && (getTypeFlagsNode.execute(cls) & TypeFlags.MANAGED_DICT) != 0) {
                return MANAGED_DICT_OFFSET;
            }
            return result;
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class SetDictOffsetNode extends Node {
        public abstract void execute(Node inliningTarget, PythonManagedClass cls, long value);

        public static void executeUncached(PythonManagedClass cls, long value) {
            TypeNodesFactory.SetDictOffsetNodeGen.getUncached().execute(null, cls, value);
        }

        @Specialization
        static void set(Node inliningTarget, PythonManagedClass cls, long value,
                        @Cached HiddenAttr.WriteNode write) {
            write.execute(inliningTarget, cls, DICTOFFSET, value);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class GetWeakListOffsetNode extends Node {
        public abstract long execute(Node inliningTarget, Object cls);

        public static long executeUncached(Object cls) {
            return TypeNodesFactory.GetWeakListOffsetNodeGen.getUncached().execute(null, cls);
        }

        @Specialization
        static long lookup(Object cls,
                        @Cached(inline = false) CExtNodes.LookupNativeI64MemberFromBaseNode lookup) {
            return lookup.execute(cls, PyTypeObject__tp_weaklistoffset, WEAKLISTOFFSET, PythonBuiltinClassType::getWeaklistoffset);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class SetWeakListOffsetNode extends Node {
        public abstract void execute(Node inliningTarget, PythonManagedClass cls, long value);

        public static void executeUncached(PythonManagedClass cls, long value) {
            TypeNodesFactory.SetWeakListOffsetNodeGen.getUncached().execute(null, cls, value);
        }

        @Specialization
        static void set(Node inliningTarget, PythonManagedClass cls, long value,
                        @Cached HiddenAttr.WriteNode write) {
            write.execute(inliningTarget, cls, WEAKLISTOFFSET, value);
        }
    }

    /**
     * Tests if the given {@code cls} is a Python class that needs a native allocation. This is the
     * case if {@code cls} either is a native class or it is a managed class that (indirectly)
     * inherits from a native class.
     */
    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class NeedsNativeAllocationNode extends Node {
        public abstract boolean execute(Node inliningTarget, Object cls);

        public static boolean executeUncached(Object cls) {
            return TypeNodesFactory.NeedsNativeAllocationNodeGen.getUncached().execute(null, cls);
        }

        @Specialization
        static boolean doPBCT(@SuppressWarnings("unused") PythonBuiltinClassType cls) {
            return false;
        }

        @Specialization
        static boolean doBuiltin(@SuppressWarnings("unused") PythonBuiltinClass cls) {
            return false;
        }

        @Specialization
        static boolean doManaged(PythonManagedClass cls) {
            return cls.needsNativeAllocation();
        }

        @Specialization
        static boolean doNative(@SuppressWarnings("unused") PythonNativeClass cls) {
            return true;
        }

        @Fallback
        static boolean doOther(@SuppressWarnings("unused") Object cls) {
            return false;
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class HasSameConstructorNode extends Node {

        public abstract boolean execute(Node inliningTarget, Object leftClass, Object rightClass);

        @Specialization
        static boolean doGeneric(Node inliningTarget, Object left, Object right,
                        @Cached(inline = false) LookupAttributeInMRONode.Dynamic lookupNew,
                        @Cached InlinedExactClassProfile leftNewProfile,
                        @Cached InlinedExactClassProfile rightNewProfile) {
            assert IsTypeNode.executeUncached(left);
            assert IsTypeNode.executeUncached(right);

            Object leftNew = leftNewProfile.profile(inliningTarget, lookupNew.execute(left, T___NEW__));
            Object rightNew = rightNewProfile.profile(inliningTarget, lookupNew.execute(right, T___NEW__));
            return leftNew == rightNew;
        }
    }

    /**
     * Check whether given type didn't override default object {@code __init__}. Equivalent of
     * CPython's {@code type->tp_init == object_init} check.
     */
    @GenerateInline(inlineByDefault = true)
    public abstract static class HasObjectInitNode extends Node {
        public abstract boolean execute(Node inliningTarget, Object type);

        public final boolean executeCached(Object type) {
            return execute(this, type);
        }

        @Specialization
        static boolean check(Node inliningTarget, Object type,
                        @Cached GetCachedTpSlotsNode getSlots) {
            TpSlots slots = getSlots.execute(inliningTarget, type);
            return slots.tp_init() == ObjectBuiltins.SLOTS.tp_init();
        }
    }

    /** Equivalent of CPython's {@code recursive_isinstance} */
    @GenerateInline
    @GenerateCached(false)
    public abstract static class GenericInstanceCheckNode extends Node {

        public abstract boolean execute(VirtualFrame frame, Node inliningTarget, Object instance, Object cls);

        @Specialization(guards = "isTypeNode.execute(inliningTarget, cls)", limit = "1")
        static boolean isInstance(VirtualFrame frame, Node inliningTarget, Object instance, Object cls,
                        @Cached PyObjectLookupAttr lookupAttr,
                        @Cached IsTypeNode isTypeNode,
                        @Cached InlinedConditionProfile classSameResult,
                        @Cached GetClassNode getClassNode,
                        @Cached IsSubtypeNode isSubtypeNode) {
            Object type = getClassNode.execute(inliningTarget, instance);
            if (isSubtypeNode.execute(type, cls)) {
                return true;
            }

            Object instanceClass = lookupAttr.execute(frame, inliningTarget, instance, T___CLASS__);
            if (classSameResult.profile(inliningTarget, instanceClass == type)) {
                // We already did a check on this type
                return false;
            } else if (isTypeNode.execute(inliningTarget, instanceClass)) {
                return isSubtypeNode.execute(instanceClass, cls);
            } else {
                return false;
            }
        }

        @Fallback
        static boolean isInstance(VirtualFrame frame, Node inliningTarget, Object instance, Object cls,
                        @Cached PyObjectLookupAttr lookupAttr,
                        @Cached AbstractObjectIsSubclassNode abstractIsSubclassNode,
                        @Cached AbstractObjectGetBasesNode getBasesNode,
                        @Cached PRaiseNode raiseNode) {
            if (getBasesNode.execute(frame, inliningTarget, cls) == null) {
                throw raiseNode.raise(inliningTarget, PythonErrorType.TypeError, ErrorMessages.ISINSTANCE_ARG_2_MUST_BE_TYPE_OR_TUPLE_OF_TYPE, instance);
            }

            Object instanceClass = lookupAttr.execute(frame, inliningTarget, instance, T___CLASS__);
            if (instanceClass != NO_VALUE) {
                return abstractIsSubclassNode.execute(frame, instanceClass, cls);
            } else {
                return false;
            }
        }
    }

    /** Equivalent of CPython's {@code recursive_issubclass} */
    @GenerateInline
    @GenerateCached(false)
    public abstract static class GenericSubclassCheckNode extends Node {

        public abstract boolean execute(VirtualFrame frame, Node inliningTarget, Object derived, Object cls);

        @Specialization(guards = {"isTypeNode.execute(inliningTarget, cls)", "isTypeNode.execute(inliningTarget, derived)"}, limit = "1")
        static boolean doTypes(Node inliningTarget, Object derived, Object cls,
                        @SuppressWarnings("unused") @Cached IsTypeNode isTypeNode,
                        @Cached IsSameTypeNode isSameTypeNode,
                        @Cached IsSubtypeNode isSubtypeNode) {
            return isSameTypeNode.execute(inliningTarget, cls, derived) || isSubtypeNode.execute(derived, cls);
        }

        @Fallback
        @InliningCutoff
        static boolean doObjects(VirtualFrame frame, Node inliningTarget, Object derived, Object cls,
                        @Cached AbstractObjectGetBasesNode getBasesNode,
                        @Cached AbstractObjectIsSubclassNode abstractIsSubclassNode,
                        @Cached PRaiseNode raiseDerived,
                        @Cached PRaiseNode raiseCls) {
            if (getBasesNode.execute(frame, inliningTarget, derived) == null) {
                throw raiseDerived.raise(inliningTarget, TypeError, ErrorMessages.ARG_D_MUST_BE_S, "issubclass()", 1, "class");
            }
            if (getBasesNode.execute(frame, inliningTarget, cls) == null) {
                throw raiseCls.raise(inliningTarget, TypeError, ErrorMessages.ISSUBCLASS_MUST_BE_CLASS_OR_TUPLE);
            }
            return abstractIsSubclassNode.execute(frame, derived, cls);
        }
    }
}
