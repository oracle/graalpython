/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PBaseException;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_SUBCLASS_CHECK;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_TRUFFLE_SET_TP_FLAGS;
import static com.oracle.graal.python.builtins.objects.str.StringUtils.compareStringsUncached;
import static com.oracle.graal.python.builtins.objects.type.TypeBuiltins.TYPE_BASICSIZE;
import static com.oracle.graal.python.builtins.objects.type.TypeBuiltins.TYPE_DICTOFFSET;
import static com.oracle.graal.python.builtins.objects.type.TypeBuiltins.TYPE_FLAGS;
import static com.oracle.graal.python.builtins.objects.type.TypeBuiltins.TYPE_ITEMSIZE;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.BASETYPE;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.BASE_EXC_SUBCLASS;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.BYTES_SUBCLASS;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.COLLECTION_FLAGS;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.DEFAULT;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.DICT_SUBCLASS;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.HAVE_GC;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.HEAPTYPE;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.IMMUTABLETYPE;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.IS_ABSTRACT;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.LIST_SUBCLASS;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.LONG_SUBCLASS;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.MAPPING;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.MATCH_SELF;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.METHOD_DESCRIPTOR;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.READY;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.SEQUENCE;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.SUBCLASS_FLAGS;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.TUPLE_SUBCLASS;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.TYPE_SUBCLASS;
import static com.oracle.graal.python.builtins.objects.type.TypeFlags.UNICODE_SUBCLASS;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___CLASSCELL__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DICT__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DOC__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___QUALNAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___SLOTS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___WEAKREF__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T_MRO;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___CLASS_GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___INIT_SUBCLASS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___NEW__;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

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
import com.oracle.graal.python.builtins.objects.cext.PythonNativeVoidPtr;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiMemberAccessNodes;
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
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetItem;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorKey;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorKeyHash;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorNext;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorValue;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageLen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageSetItemWithHash;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes.GetObjectArrayNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetInternalObjectArrayNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.NoGeneralizationNode;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.GetSetDescriptor;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.HiddenKeyDescriptor;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltins;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltinsFactory.DictNodeGen;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.str.StringBuiltins.IsIdentifierNode;
import com.oracle.graal.python.builtins.objects.superobject.SuperObject;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetBaseClassNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetBaseClassesNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetMroStorageNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetNameNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetSolidBaseNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetSubclassesNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.InlinedIsSameTypeNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.IsAcceptableBaseNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.IsSameTypeNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.IsTypeNodeGen;
import com.oracle.graal.python.lib.PyDictDelItem;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.IndirectCallNode;
import com.oracle.graal.python.nodes.PConstructAndRaiseNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.attributes.LookupInheritedSlotNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromDynamicObjectNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToDynamicObjectNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.expression.CastToListExpressionNode.CastToListNode;
import com.oracle.graal.python.nodes.frame.ReadCallerFrameNode;
import com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode;
import com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode.StandaloneBuiltinFactory;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.InlineIsBuiltinClassProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetOrCreateDictNode;
import com.oracle.graal.python.nodes.truffle.PythonTypes;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.MroSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ObjectSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.HiddenKey;
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

    @GenerateUncached
    public abstract static class GetTypeFlagsNode extends Node {

        public abstract long execute(Object clazz);

        @Specialization
        long doBuiltinClassType(PythonBuiltinClassType clazz,
                        @Bind("this") Node inliningTarget,
                        @Shared("read") @Cached ReadAttributeFromDynamicObjectNode readHiddenFlagsNode,
                        @Shared("write") @Cached WriteAttributeToDynamicObjectNode writeHiddenFlagsNode,
                        @Shared("profile") @Cached InlinedCountingConditionProfile profile) {
            return doManaged(PythonContext.get(this).getCore().lookupType(clazz), inliningTarget, readHiddenFlagsNode, writeHiddenFlagsNode, profile);
        }

        @Specialization
        long doManaged(PythonManagedClass clazz,
                        @Bind("this") Node inliningTarget,
                        @Shared("read") @Cached ReadAttributeFromDynamicObjectNode readHiddenFlagsNode,
                        @Shared("write") @Cached WriteAttributeToDynamicObjectNode writeHiddenFlagsNode,
                        @Shared("profile") @Cached InlinedCountingConditionProfile profile) {

            Object flagsObject = readHiddenFlagsNode.execute(clazz, TYPE_FLAGS);
            if (profile.profile(inliningTarget, flagsObject != PNone.NO_VALUE)) {
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
            Object result = getTpFlagsNode.execute(clazz, NativeMember.TP_FLAGS);
            if (result instanceof Long) {
                return (long) result;
            } else {
                return (int) result;
            }
        }

        @TruffleBoundary
        private long computeFlags(PythonManagedClass clazz) {
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

            PythonContext context = PythonContext.get(this);
            // flags are inherited
            MroSequenceStorage mroStorage = GetMroStorageNodeGen.getUncached().execute(clazz);
            int n = mroStorage.length();
            for (int i = 0; i < n; i++) {
                Object mroEntry = SequenceStorageNodes.GetItemDynamicNode.getUncached().execute(mroStorage, i);
                if (mroEntry instanceof PythonBuiltinClassType) {
                    mroEntry = context.getCore().lookupType((PythonBuiltinClassType) mroEntry);
                }
                if (mroEntry instanceof PythonAbstractNativeObject) {
                    result = setFlags(result, doNative((PythonAbstractNativeObject) mroEntry, GetTypeMemberNodeGen.getUncached()));
                } else if (mroEntry != clazz && mroEntry instanceof PythonManagedClass) {
                    long flags = doManaged((PythonManagedClass) mroEntry, null, ReadAttributeFromDynamicObjectNode.getUncached(), WriteAttributeToDynamicObjectNode.getUncached(),
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
                    result = DEFAULT | BASETYPE;
                    break;
                case PArray:
                    result = DEFAULT | BASETYPE | SEQUENCE;
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
                case PReferenceType:
                case PProperty:
                case PDeque:
                case PSimpleQueue:
                    result = DEFAULT | HAVE_GC | BASETYPE;
                    break;
                case PFrozenSet:
                case PSet:
                    result = DEFAULT | HAVE_GC | BASETYPE | MATCH_SELF;
                    break;
                case Boolean:
                    result = DEFAULT | LONG_SUBCLASS | MATCH_SELF;
                    break;
                case PBytes:
                    result = DEFAULT | BASETYPE | BYTES_SUBCLASS | MATCH_SELF;
                    break;
                case PFunction:
                case PBuiltinFunction:
                    result = DEFAULT | HAVE_GC | METHOD_DESCRIPTOR;
                    break;
                case WrapperDescriptor:
                    result = DEFAULT | HAVE_GC | METHOD_DESCRIPTOR;
                    break;
                case PMethod:
                case PBuiltinFunctionOrMethod:
                case PBuiltinMethod:
                case MethodWrapper:
                    result = DEFAULT | HAVE_GC;
                    break;
                case PInstancemethod:
                    result = DEFAULT | HAVE_GC;
                    break;
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
                    result = DEFAULT | HAVE_GC;
                    break;
                case PMappingproxy:
                    result = DEFAULT | HAVE_GC | MAPPING;
                    break;
                case PMemoryView:
                    result = DEFAULT | HAVE_GC | SEQUENCE;
                    break;
                case PDict:
                    result = DEFAULT | HAVE_GC | BASETYPE | DICT_SUBCLASS | MATCH_SELF | MAPPING;
                    break;
                case PDefaultDict:
                    result = DEFAULT | HAVE_GC | BASETYPE | MAPPING;
                    break;
                case PBaseException:
                    result = DEFAULT | HAVE_GC | BASETYPE | BASE_EXC_SUBCLASS;
                    break;
                case PList:
                    result = DEFAULT | HAVE_GC | BASETYPE | LIST_SUBCLASS | MATCH_SELF | SEQUENCE;
                    break;
                case PInt:
                    result = DEFAULT | BASETYPE | LONG_SUBCLASS | MATCH_SELF;
                    break;
                case PString:
                    result = DEFAULT | BASETYPE | UNICODE_SUBCLASS | MATCH_SELF;
                    break;
                case PTuple:
                    result = DEFAULT | HAVE_GC | BASETYPE | TUPLE_SUBCLASS | MATCH_SELF | SEQUENCE;
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
                    result = DEFAULT | BASETYPE | MATCH_SELF;
                    break;
                default:
                    // default case; this includes: PythonObject, PCode, PInstancemethod, PNone,
                    // PNotImplemented, PEllipsis, exceptions
                    result = DEFAULT | (clazz.isAcceptableBase() ? BASETYPE : 0) | (PythonBuiltinClassType.isExceptionType(clazz) ? BASE_EXC_SUBCLASS | HAVE_GC : 0L);
                    break;
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
    public abstract static class SetTypeFlagsNode extends Node {

        public abstract void execute(Object clazz, long flags);

        @Specialization
        void doPBCT(PythonBuiltinClassType clazz, long flags,
                        @Shared("write") @Cached WriteAttributeToDynamicObjectNode writeHiddenFlagsNode) {
            doManaged(PythonContext.get(this).getCore().lookupType(clazz), flags, writeHiddenFlagsNode);
        }

        @Specialization
        static void doManaged(PythonManagedClass clazz, long flags,
                        @Shared("write") @Cached WriteAttributeToDynamicObjectNode writeHiddenFlagsNode) {
            writeHiddenFlagsNode.execute(clazz, TYPE_FLAGS, flags);
        }

        @Specialization
        static void doNative(PythonNativeClass clazz, long flags,
                        @Cached ToSulongNode toSulongNode,
                        @Cached CExtNodes.PCallCapiFunction callCapiFunction) {
            callCapiFunction.call(FUN_TRUFFLE_SET_TP_FLAGS, toSulongNode.execute(clazz), flags);
        }

        public static SetTypeFlagsNode getUncached() {
            return TypeNodesFactory.SetTypeFlagsNodeGen.getUncached();
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
        return result;
    }

    @GenerateUncached
    public abstract static class GetMroNode extends Node {

        public abstract PythonAbstractClass[] execute(Object obj);

        @Specialization
        PythonAbstractClass[] doIt(Object obj,
                        @Cached GetMroStorageNode getMroStorageNode) {
            return getMroStorageNode.execute(obj).getInternalClassArray();
        }

        @NeverDefault
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

        private static MroSequenceStorage doPythonClass(PythonManagedClass obj, Node inliningTarget, InlinedConditionProfile notInitialized, InlinedConditionProfile isPythonClass,
                        PythonLanguage language) {
            if (!notInitialized.profile(inliningTarget, obj.isMROInitialized())) {
                PythonAbstractClass[] mro = ComputeMroNode.doSlowPath(obj, false);
                if (isPythonClass.profile(inliningTarget, obj instanceof PythonClass)) {
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
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedConditionProfile notInitialized,
                        @Cached InlinedConditionProfile isPythonClass) {
            return doPythonClass(obj, inliningTarget, notInitialized, isPythonClass, getLanguage());
        }

        @Specialization
        MroSequenceStorage doBuiltinClass(PythonBuiltinClassType obj) {
            return PythonContext.get(this).lookupType(obj).getMethodResolutionOrder();
        }

        @Specialization
        static MroSequenceStorage doNativeClass(PythonNativeClass obj,
                        @Bind("this") Node inliningTarget,
                        @Cached GetTypeMemberNode getTpMroNode,
                        @Cached PRaiseNode raise,
                        @Cached InlinedConditionProfile lazyTypeInitProfile,
                        @Cached InlinedExactClassProfile tpMroProfile,
                        @Cached InlinedExactClassProfile storageProfile) {
            Object tupleObj = getTpMroNode.execute(obj, NativeMember.TP_MRO);
            if (lazyTypeInitProfile.profile(inliningTarget, tupleObj == PNone.NO_VALUE)) {
                tupleObj = initializeType(obj, getTpMroNode, raise);
            }
            Object profiled = tpMroProfile.profile(inliningTarget, tupleObj);
            if (profiled instanceof PTuple) {
                SequenceStorage sequenceStorage = storageProfile.profile(inliningTarget, ((PTuple) profiled).getSequenceStorage());
                if (sequenceStorage instanceof MroSequenceStorage) {
                    return (MroSequenceStorage) sequenceStorage;
                }
            }
            throw raise.raise(PythonBuiltinClassType.SystemError, ErrorMessages.INVALID_MRO_OBJ);
        }

        private static Object initializeType(PythonNativeClass obj, GetTypeMemberNode getTpMroNode, PRaiseNode raise) {
            // Special case: lazy type initialization (should happen at most only once per type)
            CompilerDirectives.transferToInterpreter();

            // call 'PyType_Ready' on the type
            int res = (int) PCallCapiFunction.getUncached().call(NativeCAPISymbol.FUN_PY_TYPE_READY, ToSulongNode.getUncached().execute(obj));
            if (res < 0) {
                throw raise.raise(PythonBuiltinClassType.SystemError, ErrorMessages.LAZY_INITIALIZATION_FAILED, GetNameNode.getUncached().execute(obj));
            }

            Object tupleObj = getTpMroNode.execute(obj, NativeMember.TP_MRO);
            assert tupleObj != PNone.NO_VALUE : "MRO object is still NULL even after lazy type initialization";
            return tupleObj;
        }

        @Specialization(replaces = {"doPythonClass", "doBuiltinClass", "doNativeClass"})
        @TruffleBoundary
        static MroSequenceStorage doSlowPath(Object obj,
                        @Bind("this") Node inliningTarget,
                        @Cached PRaiseNode raise) {
            if (obj instanceof PythonManagedClass) {
                return doPythonClass((PythonManagedClass) obj, inliningTarget, InlinedConditionProfile.getUncached(), InlinedConditionProfile.getUncached(), PythonLanguage.get(null));
            } else if (obj instanceof PythonBuiltinClassType) {
                return PythonContext.get(null).lookupType((PythonBuiltinClassType) obj).getMethodResolutionOrder();
            } else if (PGuards.isNativeClass(obj)) {
                GetTypeMemberNode getTypeMemeberNode = GetTypeMemberNode.getUncached();
                Object tupleObj = getTypeMemeberNode.execute(obj, NativeMember.TP_MRO);
                if (tupleObj == PNone.NO_VALUE) {
                    tupleObj = initializeType((PythonNativeClass) obj, GetTypeMemberNode.getUncached(), raise);
                }
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

        @NeverDefault
        public static GetMroStorageNode create() {
            return GetMroStorageNodeGen.create();
        }

        public static GetMroStorageNode getUncached() {
            return GetMroStorageNodeGen.getUncached();
        }
    }

    @GenerateUncached
    public abstract static class GetNameNode extends Node {

        public abstract TruffleString execute(Object obj);

        @Specialization
        TruffleString doManagedClass(PythonManagedClass obj) {
            return obj.getName();
        }

        @Specialization
        TruffleString doBuiltinClassType(PythonBuiltinClassType obj) {
            return obj.getName();
        }

        @Specialization
        TruffleString doNativeClass(PythonNativeClass obj,
                        @Cached CExtNodes.GetTypeMemberNode getTpNameNode,
                        @Cached CastToTruffleStringNode castToStringNode) {
            return castToStringNode.execute(getTpNameNode.execute(obj, NativeMember.TP_NAME));
        }

        @Specialization(replaces = {"doManagedClass", "doBuiltinClassType", "doNativeClass"})
        @TruffleBoundary
        public static TruffleString doSlowPath(Object obj) {
            if (obj instanceof PythonManagedClass) {
                return ((PythonManagedClass) obj).getName();
            } else if (obj instanceof PythonBuiltinClassType) {
                return ((PythonBuiltinClassType) obj).getName();
            } else if (PGuards.isNativeClass(obj)) {
                return CastToTruffleStringNode.getUncached().execute(CExtNodes.GetTypeMemberNode.getUncached().execute(obj, NativeMember.TP_NAME));
            }
            throw new IllegalStateException("unknown type " + obj.getClass().getName());
        }

        @NeverDefault
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
                        @Bind("this") Node inliningTarget,
                        @Cached GetTypeMemberNode getTpBaseNode,
                        @Cached PRaiseNode raise,
                        @Cached InlinedExactClassProfile resultTypeProfile) {
            Object result = resultTypeProfile.profile(inliningTarget, getTpBaseNode.execute(obj, NativeMember.TP_BASE));
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
                        @Bind("this") Node inliningTarget,
                        @Cached GetTypeMemberNode getTpSubclassesNode,
                        @Cached InlinedExactClassProfile profile) {
            Object tpSubclasses = getTpSubclassesNode.execute(obj, NativeMember.TP_SUBCLASSES);

            Object profiled = profile.profile(inliningTarget, tpSubclasses);
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
            return new Set<>() {
                private final PDict dict = (PDict) tpSubclasses;

                @Override
                public int size() {
                    return HashingStorageLen.executeUncached(dict.getDictStorage());
                }

                @Override
                public boolean isEmpty() {
                    return size() == 0;
                }

                @Override
                public boolean contains(Object o) {
                    return HashingStorageGetItem.hasKeyUncached(dict.getDictStorage(), o);
                }

                @Override
                @SuppressWarnings("unchecked")
                public Iterator<PythonAbstractClass> iterator() {
                    final HashingStorageNodes.HashingStorageIterator it = HashingStorageGetIterator.executeUncached(dict.getDictStorage());
                    Boolean[] hasNext = new Boolean[1];

                    return new Iterator<>() {
                        @Override
                        public boolean hasNext() {
                            if (hasNext[0] == null) {
                                hasNext[0] = HashingStorageIteratorNext.executeUncached(dict.getDictStorage(), it);
                            }
                            return hasNext[0];
                        }

                        @Override
                        public PythonAbstractClass next() {
                            if (hasNext[0] == null) {
                                hasNext[0] = HashingStorageIteratorNext.executeUncached(dict.getDictStorage(), it);
                            }
                            if (!hasNext[0]) {
                                throw new NoSuchElementException();
                            }
                            PythonAbstractClass result = (PythonAbstractClass) HashingStorageIteratorValue.executeUncached(dict.getDictStorage(), it);
                            hasNext[0] = null;
                            return result;
                        }
                    };
                }

                @Override
                @TruffleBoundary
                public Object[] toArray() {
                    Object[] result = new Object[size()];
                    int i = 0;
                    for (PythonAbstractClass item : this) {
                        result[i++] = item;
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

        @NeverDefault
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
                        @Bind("this") Node inliningTarget,
                        @Cached PRaiseNode raise,
                        @Cached GetTypeMemberNode getTpBasesNode,
                        @Cached InlinedExactClassProfile resultTypeProfile,
                        @Cached GetInternalObjectArrayNode toArrayNode) {
            Object result = resultTypeProfile.profile(inliningTarget, getTpBasesNode.execute(obj, NativeMember.TP_BASES));
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
                        @Bind("this") Node inliningTarget,
                        @Cached PRaiseNode raise,
                        @Cached GetTypeMemberNode getTpBaseNode,
                        @Cached InlinedExactClassProfile resultTypeProfile,
                        @Cached IsTypeNode isTypeNode) {
            Object result = resultTypeProfile.profile(inliningTarget, getTpBaseNode.execute(obj, NativeMember.TP_BASE));
            if (PGuards.isPNone(result)) {
                return null;
            } else if (PGuards.isClass(result, isTypeNode)) {
                return (PythonAbstractClass) result;
            }
            CompilerDirectives.transferToInterpreter();
            throw raise.raise(SystemError, ErrorMessages.INVALID_BASE_TYPE_OBJ_FOR_CLASS, GetNameNode.doSlowPath(obj), result);
        }
    }

    @ImportStatic(SpecialMethodNames.class)
    @GenerateUncached
    public abstract static class GetBestBaseClassNode extends PNodeWithContext {

        @NeverDefault
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
        @Child private PRaiseNode raiseNode;
        @Child private GetNameNode getTypeNameNode;
        @Child private ReadAttributeFromObjectNode readAttr;
        @Child private InstancesOfTypeHaveDictNode instancesHaveDictNode;

        public abstract boolean execute(VirtualFrame frame, Object oldBase, Object newBase);

        @Specialization
        boolean isCompatible(VirtualFrame frame, Object oldBase, Object newBase,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedBranchProfile errorSlotsBranch) {
            if (!compatibleForAssignment(frame, oldBase, newBase)) {
                errorSlotsBranch.enter(inliningTarget);
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
                return compareSortedSlots(aSlots, bSlots);
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

        private TruffleString getTypeName(Object clazz) {
            if (getTypeNameNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getTypeNameNode = insert(TypeNodes.GetNameNode.create());
            }
            return getTypeNameNode.execute(clazz);
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
            if (aArray[i] instanceof TruffleString) {
                aArray[i] = ((TruffleString) aArray[i]).toJavaStringUncached();
            }
            if (bArray[i] instanceof TruffleString) {
                bArray[i] = ((TruffleString) bArray[i]).toJavaStringUncached();
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
                        @Bind("this") Node inliningTarget,
                        @Cached GetBaseClassNode getBaseClassNode,
                        @Cached("createForceType()") ReadAttributeFromObjectNode readAttr,
                        @Cached InlinedBranchProfile typeIsNotBase,
                        @Cached InlinedBranchProfile hasBase,
                        @Cached InlinedBranchProfile hasNoBase) {
            return solidBase(type, inliningTarget, getBaseClassNode, PythonContext.get(this), readAttr, typeIsNotBase, hasBase,
                            hasNoBase, 0);
        }

        @TruffleBoundary
        protected Object solidBaseTB(Object type, Node inliningTarget, GetBaseClassNode getBaseClassNode, PythonContext context, int depth) {
            return solidBase(type, inliningTarget, getBaseClassNode, context, ReadAttributeFromObjectNode.getUncachedForceType(), InlinedBranchProfile.getUncached(),
                            InlinedBranchProfile.getUncached(), InlinedBranchProfile.getUncached(), depth);
        }

        protected Object solidBase(Object type, Node inliningTarget, GetBaseClassNode getBaseClassNode, PythonContext context, ReadAttributeFromObjectNode readAttr,
                        InlinedBranchProfile typeIsNotBase, InlinedBranchProfile hasBase, InlinedBranchProfile hasNoBase, int depth) {
            CompilerAsserts.partialEvaluationConstant(depth);
            Object base = getBaseClassNode.execute(type);
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

            Object typeSlots = getSlotsFromType(type, readAttr);
            if (extraivars(type, base, typeSlots)) {
                return type;
            } else {
                return base;
            }
        }

        @TruffleBoundary
        private static boolean extraivars(Object type, Object base, Object typeSlots) {
            if (typeSlots != null && length(typeSlots) != 0) {
                return true;
            }
            Object typeNewMethod = LookupAttributeInMRONode.lookup(type, T___NEW__, GetMroStorageNode.getUncached(), ReadAttributeFromObjectNode.getUncached(), true,
                            DynamicObjectLibrary.getUncached());
            Object baseNewMethod = LookupAttributeInMRONode.lookup(base, T___NEW__, GetMroStorageNode.getUncached(), ReadAttributeFromObjectNode.getUncached(), true,
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
                Object[] slots = GetInternalObjectArrayNode.getUncached().execute(storage);
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
    @GenerateInline
    @GenerateCached(false)
    public abstract static class InlinedIsSameTypeNode extends PNodeWithContext {
        public abstract boolean execute(Node inliningTarget, Object left, Object right);

        public static boolean executeUncached(Object left, Object right) {
            return InlinedIsSameTypeNodeGen.getUncached().execute(null, left, right);
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
        static boolean doNativeSingleContext(PythonAbstractNativeObject left, PythonAbstractNativeObject right,
                        @CachedLibrary(limit = "3") InteropLibrary lib1,
                        @CachedLibrary(limit = "3") InteropLibrary lib2) {
            if (lib1.isPointer(left.getPtr())) {
                if (lib2.isPointer(right.getPtr())) {
                    try {
                        return lib1.asPointer(left.getPtr()) == lib2.asPointer(right.getPtr());
                    } catch (UnsupportedMessageException e) {
                        throw CompilerDirectives.shouldNotReachHere(e);
                    }
                } else {
                    return false;
                }
            } else {
                if (lib2.isPointer(right.getPtr())) {
                    return false;
                } else {
                    return lib1.isIdentical(left.getPtr(), right.getPtr(), lib2);
                }
            }
        }

        @Fallback
        static boolean doOther(@SuppressWarnings("unused") Object left, @SuppressWarnings("unused") Object right) {
            return false;
        }
    }

    @GenerateUncached
    @ImportStatic(SpecialMethodNames.class)
    @Deprecated // TODO: DSL inlining
    public abstract static class IsSameTypeNode extends PNodeWithContext {

        @Deprecated // TODO: DSL inlining
        public abstract boolean execute(Object left, Object right);

        @Specialization
        boolean doIt(Object left, Object right,

                        @Cached InlinedIsSameTypeNode isSameTypeNode) {
            return isSameTypeNode.execute(this, left, right);
        }

        @NeverDefault
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
                Object mroMeth = LookupAttributeInMRONode.Dynamic.getUncached().execute(type, T_MRO);
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
                StringBuilder bases = new StringBuilder(GetNameNode.doSlowPath(it.next()).toJavaStringUncached());
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
                        @Bind("this") Node inliningTarget,
                        @Cached InlineIsBuiltinClassProfile profile,
                        @Cached GetClassNode getClassNode,
                        @Cached CExtNodes.PCallCapiFunction nativeTypeCheck) {
            Object type = getClassNode.execute(obj);
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

        public static IsTypeNode getUncached() {
            return IsTypeNodeGen.getUncached();
        }
    }

    @GenerateUncached
    public abstract static class IsAcceptableBaseNode extends Node {

        public abstract boolean execute(Object obj);

        @Specialization
        static boolean doUserClass(PythonClass obj,
                        @Bind("this") Node inliningTarget,
                        @Cached ReadAttributeFromDynamicObjectNode readAttributeFromObjectNode,
                        @Cached InlinedBranchProfile hasHPyFlagsProfile) {
            // Special case for custom classes created via HPy: They are managed classes but can
            // have custom flags. The flags may prohibit subtyping.
            Object flagsObj = readAttributeFromObjectNode.execute(obj, GraalHPyDef.TYPE_HPY_FLAGS);
            if (flagsObj != PNone.NO_VALUE) {
                hasHPyFlagsProfile.enter(inliningTarget);
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
        @SuppressWarnings("unused")
        protected Shape doBuiltinClassTypeCached(PythonBuiltinClassType clazz,
                        @Cached("clazz") PythonBuiltinClassType cachedClazz) {
            return cachedClazz.getInstanceShape(getLanguage());
        }

        @Specialization(replaces = "doBuiltinClassTypeCached")
        protected Shape doBuiltinClassType(PythonBuiltinClassType clazz) {
            return clazz.getInstanceShape(getLanguage());
        }

        @Specialization(guards = {"isSingleContext()", "clazz == cachedClazz"})
        @SuppressWarnings("unused")
        protected static Shape doBuiltinClassCached(PythonBuiltinClass clazz,
                        @Cached("clazz") PythonBuiltinClass cachedClazz) {
            return cachedClazz.getInstanceShape();
        }

        @Specialization(guards = {"isSingleContext()", "clazz == cachedClazz"})
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
        protected static Shape doNativeClass(PythonAbstractNativeObject clazz,
                        @Cached GetTypeMemberNode getTpDictNode,
                        @CachedLibrary(limit = "1") DynamicObjectLibrary lib) {
            Object tpDictObj = getTpDictNode.execute(clazz, NativeMember.TP_DICT);
            if (tpDictObj instanceof PythonManagedClass) {
                return ((PythonManagedClass) tpDictObj).getInstanceShape();
            }
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
        protected static Shape doError(@SuppressWarnings("unused") Object clazz,
                        @Cached PRaiseNode raise) {
            throw raise.raise(PythonBuiltinClassType.SystemError, ErrorMessages.CANNOT_GET_SHAPE_OF_NATIVE_CLS);
        }

    }

    @ImportStatic({SpecialMethodNames.class, SpecialAttributeNames.class, SpecialMethodSlot.class})
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
                        @Cached HashingStorage.InitNode initNode,
                        @Cached HashingStorageGetItem getItemGlobals,
                        @Cached HashingStorageGetItem getItemNamespace,
                        @Cached HashingStorageGetIterator getIterator,
                        @Cached HashingStorageIteratorNext itNext,
                        @Cached HashingStorageIteratorKey itKey,
                        @Cached HashingStorageIteratorValue itValue,
                        @Cached PyDictDelItem delItemNamespace,
                        @Cached("create(SetName)") LookupInheritedSlotNode getSetNameNode,
                        @Cached CallNode callSetNameNode,
                        @Cached CallNode callInitSubclassNode,
                        @Cached("create(T___INIT_SUBCLASS__)") GetAttributeNode getInitSubclassNode,
                        @Cached GetMroStorageNode getMroStorageNode,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode raise,
                        @Cached AllocateTypeWithMetaclassNode typeMetaclass) {
            try {
                assert SpecialMethodSlot.pushInitializedTypePlaceholder();
                PDict namespace = factory.createDict();
                PythonLanguage language = PythonLanguage.get(this);
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
                        TruffleString moduleName = getModuleNameFromGlobals(globals, getItemGlobals);
                        if (moduleName != null) {
                            newType.setAttribute(SpecialAttributeNames.T___MODULE__, moduleName);
                        }
                    }
                }

                // delete __qualname__ from namespace
                delItemNamespace.execute(namespace, T___QUALNAME__);

                // initialize '__doc__' attribute
                if (newType.getAttribute(SpecialAttributeNames.T___DOC__) == PNone.NO_VALUE) {
                    newType.setAttribute(SpecialAttributeNames.T___DOC__, PNone.NONE);
                }

                // set __class__ cell contents
                Object classcell = getItemNamespace.execute(namespace.getDictStorage(), SpecialAttributeNames.T___CLASSCELL__);
                if (classcell != null) {
                    if (classcell instanceof PCell) {
                        ((PCell) classcell).setRef(newType);
                    } else {
                        throw raise.raise(TypeError, ErrorMessages.MUST_BE_A_CELL, "__classcell__");
                    }
                    delItemNamespace.execute(namespace, SpecialAttributeNames.T___CLASSCELL__);
                }

                SpecialMethodSlot.initializeSpecialMethodSlots(newType, getMroStorageNode, language);

                HashingStorage storage = namespace.getDictStorage();
                HashingStorageIterator it = getIterator.execute(storage);
                while (itNext.execute(storage, it)) {
                    Object value = itValue.execute(storage, it);
                    Object setName = getSetNameNode.execute(value);
                    if (setName != PNone.NO_VALUE) {
                        Object key = itKey.execute(storage, it);
                        try {
                            callSetNameNode.execute(frame, setName, value, newType, key);
                        } catch (PException e) {
                            throw raise.raiseWithCause(PythonBuiltinClassType.RuntimeError, e.getEscapedException(), ErrorMessages.ERROR_CALLING_SET_NAME, value, key, newType);
                        }
                    }
                }

                // Call __init_subclass__ on the parent of a newly generated type
                SuperObject superObject = factory.createSuperObject(PythonBuiltinClassType.Super);
                superObject.init(newType, newType, newType);
                callInitSubclassNode.execute(frame, getInitSubclassNode.executeObject(frame, superObject), PythonUtils.EMPTY_OBJECT_ARRAY, kwds);

                newType.initializeMroShape(language);

                return newType;
            } finally {
                assert SpecialMethodSlot.popInitializedType();
            }
        }

        private TruffleString getModuleNameFromGlobals(PythonObject globals, HashingStorageGetItem getItem) {
            Object nameAttr;
            if (globals instanceof PythonModule) {
                nameAttr = ensureReadAttrNode().execute(globals, SpecialAttributeNames.T___NAME__);
            } else if (globals instanceof PDict) {
                nameAttr = getItem.execute(((PDict) globals).getDictStorage(), SpecialAttributeNames.T___NAME__);
            } else {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException("invalid globals object");
            }
            if (nameAttr == null || nameAttr == PNone.NO_VALUE) {
                return null;
            }
            try {
                return ensureCastToStringNode().execute(nameAttr);
            } catch (CannotCastException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException();
            }
        }
    }

    @ImportStatic({SpecialMethodNames.class, SpecialAttributeNames.class, SpecialMethodSlot.class})
    protected abstract static class AllocateTypeWithMetaclassNode extends Node implements IndirectCallNode {
        private static final int SIZEOF_PY_OBJECT_PTR = Long.BYTES;

        @Child private ReadAttributeFromObjectNode readAttr;
        @Child private CastToListNode castToList;
        @Child private SequenceStorageNodes.GetItemNode getItemNode;
        @Child private GetMroNode getMroNode;
        @Child private CastToTruffleStringNode castToStringNode;

        private final Assumption dontNeedExceptionState = Truffle.getRuntime().createAssumption();
        private final Assumption dontNeedCallerFrame = Truffle.getRuntime().createAssumption();

        @Override
        public Assumption needNotPassFrameAssumption() {
            return dontNeedCallerFrame;
        }

        @Override
        public Assumption needNotPassExceptionAssumption() {
            return dontNeedExceptionState;
        }

        public abstract PythonClass execute(VirtualFrame frame, TruffleString name, PTuple bases, PDict namespace, Object metaclass);

        @Specialization
        protected PythonClass typeMetaclass(VirtualFrame frame, TruffleString name, PTuple bases, PDict namespace, Object metaclass,
                        @Bind("this") Node inliningTarget,
                        @Cached HashingStorageGetItem getHashingStorageItem,
                        @Cached HashingStorageSetItemWithHash setHashingStorageItem,
                        @Cached GetOrCreateDictNode getOrCreateDictNode,
                        @Cached HashingStorageGetIterator getHashingStorageIterator,
                        @Cached HashingStorageIteratorNext hashingStorageItNext,
                        @Cached HashingStorageIteratorKey hashingStorageItKey,
                        @Cached HashingStorageIteratorKeyHash hashingStorageItKeyHash,
                        @Cached HashingStorageIteratorValue hashingStorageItValue,
                        @Cached("create(T___DICT__)") LookupAttributeInMRONode getDictAttrNode,
                        @Cached("create(T___WEAKREF__)") LookupAttributeInMRONode getWeakRefAttrNode,
                        @Cached GetBestBaseClassNode getBestBaseNode,
                        @Cached IsIdentifierNode isIdentifier,
                        @Cached PConstructAndRaiseNode constructAndRaiseNode,
                        @Cached PRaiseNode raise,
                        @Cached GetObjectArrayNode getObjectArray,
                        @Cached PythonObjectFactory factory,
                        @Cached TruffleString.IsValidNode isValidNode,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.IndexOfCodePointNode indexOfCodePointNode,
                        @Cached TruffleString.EqualNode equalNode,
                        @Cached TruffleString.ToJavaStringNode toJavaStringNode,
                        @Cached GetBasicSizeNode getBasicSizeNode,
                        @Cached SetBasicSizeNode setBasicSizeNode,
                        @Cached GetItemSizeNode getItemSize,
                        @Cached GetDictOffsetNode getDictOffsetNode) {
            PythonLanguage language = PythonLanguage.get(this);
            PythonContext context = PythonContext.get(this);
            Python3Core core = context.getCore();
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
                        throw raise.raise(PythonBuiltinClassType.NotImplementedError, ErrorMessages.CREATING_CLASS_NON_CLS_BASES);
                    }
                }
            }
            // check for possible layout conflicts
            Object base = getBestBaseNode.execute(basesArray);

            assert metaclass != null;

            if (!isValidNode.execute(name, TS_ENCODING)) {
                throw constructAndRaiseNode.raiseUnicodeEncodeError(frame, "utf-8", name, 0, codePointLengthNode.execute(name, TS_ENCODING), "can't encode class name");
            }
            if (indexOfCodePointNode.execute(name, 0, 0, codePointLengthNode.execute(name, TS_ENCODING), TS_ENCODING) >= 0) {
                throw raise.raise(PythonBuiltinClassType.ValueError, ErrorMessages.TYPE_NAME_NO_NULL_CHARS);
            }

            // 1.) create class, but avoid calling mro method - it might try to access __dict__ so
            // we have to copy dict slots first
            PythonClass pythonClass = factory.createPythonClass(metaclass, name, false, basesArray);
            assert SpecialMethodSlot.replaceInitializedTypeTop(pythonClass);

            // 2.) copy the dictionary slots
            Object[] slots = new Object[1];
            boolean[] qualnameSet = new boolean[]{false};
            copyDictSlots(frame, pythonClass, namespace, setHashingStorageItem,
                            getHashingStorageIterator, hashingStorageItNext, hashingStorageItKey, hashingStorageItKeyHash, hashingStorageItValue,
                            slots, qualnameSet, constructAndRaiseNode, factory, raise, isValidNode, equalNode,
                            codePointLengthNode, getOrCreateDictNode);
            if (!qualnameSet[0]) {
                pythonClass.setQualName(name);
            }

            // 3.) invoke metaclass mro() method
            pythonClass.invokeMro();

            // CPython masks the __hash__ method with None when __eq__ is overriden, but __hash__ is
            // not
            HashingStorage namespaceStorage = namespace.getDictStorage();
            Object hashMethod = getHashingStorageItem.execute(frame, namespaceStorage, SpecialMethodNames.T___HASH__);
            if (hashMethod == null) {
                Object eqMethod = getHashingStorageItem.execute(frame, namespaceStorage, SpecialMethodNames.T___EQ__);
                if (eqMethod != null) {
                    pythonClass.setAttribute(SpecialMethodNames.T___HASH__, PNone.NONE);
                }
            }

            boolean addDict = false;
            boolean addWeakRef = false;
            // may_add_dict = base->tp_dictoffset == 0
            boolean mayAddDict = getDictAttrNode.execute(base) == PNone.NO_VALUE;
            // may_add_weak = base->tp_weaklistoffset == 0 && base->tp_itemsize == 0
            boolean hasItemSize = getItemSize.execute(inliningTarget, base) != 0;
            boolean mayAddWeakRef = getWeakRefAttrNode.execute(base) == PNone.NO_VALUE && !hasItemSize;

            PythonAbstractClass[] mro = getMro(pythonClass);
            if (slots[0] == null) {
                // takes care of checking if we may_add_dict and adds it if needed
                addDictIfNative(inliningTarget, pythonClass, mro, getBasicSizeNode, getItemSize, getDictOffsetNode);
                addDictDescrAttribute(basesArray, pythonClass, factory);
                if (mayAddWeakRef) {
                    addWeakrefDescrAttribute(pythonClass, factory);
                }
            } else {
                // have slots
                // Make it into a list
                SequenceStorage slotsStorage;
                Object slotsObject;
                if (slots[0] instanceof TruffleString) {
                    slotsObject = slots[0];
                    slotsStorage = new ObjectSequenceStorage(slots);
                } else if (slots[0] instanceof PTuple) {
                    slotsObject = slots[0];
                    slotsStorage = ((PTuple) slots[0]).getSequenceStorage();
                } else if (slots[0] instanceof PList) {
                    slotsObject = slots[0];
                    slotsStorage = ((PList) slots[0]).getSequenceStorage();
                } else {
                    slotsObject = getCastToListNode().execute(frame, slots[0]);
                    slotsStorage = ((PList) slotsObject).getSequenceStorage();
                }
                int slotlen = slotsStorage.length();

                if (slotlen > 0 && hasItemSize) {
                    throw raise.raise(TypeError, ErrorMessages.NONEMPTY_SLOTS_NOT_ALLOWED_FOR_SUBTYPE_OF_S, base);
                }

                if (isAnyBaseWithoutSlots(pythonClass, mro)) {
                    addDictIfNative(inliningTarget, pythonClass, mro, getBasicSizeNode, getItemSize, getDictOffsetNode);
                    addDictDescrAttribute(basesArray, pythonClass, factory);
                }
                for (int i = 0; i < slotlen; i++) {
                    TruffleString slotName;
                    Object element = getSlotItemNode().execute(slotsStorage, i);
                    // Check valid slot name
                    if (element instanceof TruffleString) {
                        slotName = (TruffleString) element;
                        if (!(boolean) isIdentifier.execute(frame, slotName)) {
                            throw raise.raise(TypeError, ErrorMessages.SLOTS_MUST_BE_IDENTIFIERS);
                        }
                    } else {
                        throw raise.raise(TypeError, ErrorMessages.MUST_BE_STRINGS_NOT_P, "__slots__ items", element);
                    }
                    if (equalNode.execute(slotName, T___DICT__, TS_ENCODING)) {
                        if (!mayAddDict || addDict || addDictIfNative(inliningTarget, pythonClass, mro, getBasicSizeNode, getItemSize, getDictOffsetNode)) {
                            throw raise.raise(TypeError, ErrorMessages.DICT_SLOT_DISALLOWED_WE_GOT_ONE);
                        }
                        addDict = true;
                        addDictDescrAttribute(basesArray, pythonClass, factory);
                    } else if (equalNode.execute(slotName, T___WEAKREF__, TS_ENCODING)) {
                        if (!mayAddWeakRef || addWeakRef) {
                            throw raise.raise(TypeError, ErrorMessages.WEAKREF_SLOT_DISALLOWED_WE_GOT_ONE);
                        }
                        addWeakRef = true;
                        addWeakrefDescrAttribute(pythonClass, factory);
                    } else {
                        // TODO: check for __weakref__
                        // TODO avoid if native slots are inherited
                        TruffleString mangledName;
                        try {
                            mangledName = PythonUtils.mangleName(name, slotName);

                        } catch (OutOfMemoryError e) {
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            throw raise.raise(PythonBuiltinClassType.OverflowError, ErrorMessages.PRIVATE_IDENTIFIER_TOO_LARGE_TO_BE_MANGLED);
                        }
                        HiddenKey hiddenSlotKey = createTypeKey(toJavaStringNode.execute(mangledName));
                        HiddenKeyDescriptor slotDesc = factory.createHiddenKeyDescriptor(hiddenSlotKey, pythonClass);
                        pythonClass.setAttribute(mangledName, slotDesc);
                    }
                    // Make slots into a tuple
                }
                Object state = IndirectCallContext.enter(frame, language, context, this);
                try {
                    pythonClass.setAttribute(T___SLOTS__, slotsObject);
                    if (basesArray.length > 1) {
                        // TODO: tfel - check if secondary bases provide weakref or dict when we
                        // don't already have one
                    }

                    // checks for some name errors too
                    PTuple newSlots = copySlots(name, slotsStorage, slotlen, addDict, addWeakRef, namespace, context.factory());

                    // add native slot descriptors
                    if (pythonClass.needsNativeAllocation()) {
                        addNativeSlots(inliningTarget, pythonClass, mro, newSlots, getBasicSizeNode, setBasicSizeNode);
                    }
                } finally {
                    IndirectCallContext.exit(frame, language, context, state);
                }
                Object dict = LookupAttributeInMRONode.lookupSlowPath(pythonClass, T___DICT__);
                if (!addDict && dict == PNone.NO_VALUE) {
                    pythonClass.setHasSlotsButNoDictFlag();
                }
            }
            ensureBasicsize(inliningTarget, pythonClass, mro, getBasicSizeNode, setBasicSizeNode);

            return pythonClass;
        }

        private boolean isAnyBaseWithoutSlots(PythonClass pythonClass, PythonAbstractClass[] mro) {
            for (PythonAbstractClass cls : mro) {
                if (cls != pythonClass && !PGuards.isNativeClass(cls) && !PGuards.isPythonBuiltinClass(cls)) {
                    if (!hasSlots(cls)) {
                        return true;
                    }
                }
            }
            return false;
        }

        @TruffleBoundary
        private static void addDictDescrAttribute(PythonAbstractClass[] basesArray, PythonClass pythonClass, PythonObjectFactory factory) {
            // Note: we need to avoid MRO lookup of __dict__ using slots because they are not
            // initialized yet
            if ((!hasPythonClassBases(basesArray) && LookupAttributeInMRONode.lookupSlowPath(pythonClass, T___DICT__) == PNone.NO_VALUE) || basesHaveSlots(basesArray)) {
                Builtin dictBuiltin = ObjectBuiltins.DictNode.class.getAnnotation(Builtin.class);
                RootCallTarget callTarget = PythonLanguage.get(null).createCachedCallTarget(
                                l -> new BuiltinFunctionRootNode(l, dictBuiltin, new StandaloneBuiltinFactory<PythonBinaryBuiltinNode>(DictNodeGen.create()), true), ObjectBuiltins.DictNode.class,
                                StandaloneBuiltinFactory.class);
                setAttribute(T___DICT__, dictBuiltin, callTarget, pythonClass, factory);
            }
        }

        @TruffleBoundary
        private static void addWeakrefDescrAttribute(PythonClass pythonClass, PythonObjectFactory factory) {
            Builtin builtin = GetWeakRefsNode.class.getAnnotation(Builtin.class);
            RootCallTarget callTarget = PythonLanguage.get(null).createCachedCallTarget(
                            l -> new BuiltinFunctionRootNode(l, builtin, WeakRefModuleBuiltinsFactory.GetWeakRefsNodeFactory.getInstance(), true), GetWeakRefsNode.class,
                            WeakRefModuleBuiltinsFactory.class);
            setAttribute(T___WEAKREF__, builtin, callTarget, pythonClass, factory);
        }

        @TruffleBoundary
        private static HiddenKey createTypeKey(String name) {
            return PythonLanguage.get(null).typeHiddenKeys.computeIfAbsent(name, n -> new HiddenKey(n));
        }

        private static void setAttribute(TruffleString name, Builtin builtin, RootCallTarget callTarget, PythonClass pythonClass, PythonObjectFactory factory) {
            int flags = PBuiltinFunction.getFlags(builtin, callTarget);
            PBuiltinFunction function = factory.createBuiltinFunction(name, pythonClass, 1, flags, callTarget);
            GetSetDescriptor desc = factory.createGetSetDescriptor(function, function, name, pythonClass, true);
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

        private boolean hasSlots(Object type) {
            Object slots = getReadAttr().execute(type, T___SLOTS__);
            return slots != PNone.NO_VALUE;
        }

        private ReadAttributeFromObjectNode getReadAttr() {
            if (readAttr == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readAttr = insert(ReadAttributeFromObjectNode.createForceType());
            }
            return readAttr;
        }

        private SequenceStorageNodes.GetItemNode getSlotItemNode() {
            if (getItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getItemNode = insert(SequenceStorageNodes.GetItemNode.create());
            }
            return getItemNode;
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
         * </ol>
         */
        private void addNativeSlots(Node inliningTarget, PythonManagedClass pythonClass, PythonAbstractClass[] mro, PTuple slots,
                        GetBasicSizeNode getBasicSizeNode, SetBasicSizeNode setBasicSizeNode) {
            SequenceStorage slotsStorage = slots.getSequenceStorage();
            if (slotsStorage.length() != 0) {
                // __basicsize__ may not have been inherited yet. Therefore, iterate over the MRO
                // and/ look for the first native class.
                long slotOffset = getBasicSizeNode.execute(inliningTarget, pythonClass);
                if (slotOffset == 0) {
                    for (PythonAbstractClass cls : mro) {
                        if (PGuards.isNativeClass(cls)) {
                            slotOffset = getBasicSizeNode.execute(inliningTarget, cls);
                            break;
                        }
                    }
                }
                if (slotOffset == 0) {
                    throw CompilerDirectives.shouldNotReachHere();
                }
                slotOffset = installMemberDescriptors(pythonClass, slotsStorage, slotOffset);
                // commit new basicSize
                setBasicSizeNode.execute(inliningTarget, pythonClass, slotOffset);
            }
        }

        @TruffleBoundary
        private static long installMemberDescriptors(PythonManagedClass pythonClass, SequenceStorage slotsStorage, long slotOffset) {
            PDict typeDict = GetOrCreateDictNode.getUncached().execute(pythonClass);
            for (int i = 0; i < slotsStorage.length(); i++) {
                Object slotName = SequenceStorageNodes.GetItemScalarNode.getUncached().execute(slotsStorage, i);
                PyTruffleType_AddMember.addMember(pythonClass, typeDict, (TruffleString) slotName, CApiMemberAccessNodes.T_OBJECT_EX, slotOffset, 1, PNone.NO_VALUE);
                slotOffset += SIZEOF_PY_OBJECT_PTR;
            }
            return slotOffset;
        }

        private CastToListNode getCastToListNode() {
            if (castToList == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castToList = insert(CastToListNode.create());
            }
            return castToList;
        }

        private PythonAbstractClass[] getMro(PythonAbstractClass pythonClass) {
            if (getMroNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getMroNode = insert(GetMroNode.create());
            }
            return getMroNode.execute(pythonClass);
        }

        private CastToTruffleStringNode ensureCastToStringNode() {
            if (castToStringNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castToStringNode = insert(CastToTruffleStringNode.create());
            }
            return castToStringNode;
        }

        private void copyDictSlots(VirtualFrame frame, PythonClass pythonClass, PDict namespace, HashingStorageSetItemWithHash setHashingStorageItem,
                        HashingStorageGetIterator getHashingStorageIterator, HashingStorageIteratorNext hashingStorageItNext, HashingStorageIteratorKey hashingStorageItKey,
                        HashingStorageIteratorKeyHash hashingStorageItKeyHash, HashingStorageIteratorValue hashingStorageItValue, Object[] slots,
                        boolean[] qualnameSet, PConstructAndRaiseNode constructAndRaiseNode, PythonObjectFactory factory, PRaiseNode raise, IsValidNode isValidNode,
                        EqualNode equalNode, CodePointLengthNode codePointLengthNode, GetOrCreateDictNode getOrCreateDictNode) {
            // copy the dictionary slots over, as CPython does through PyDict_Copy
            // Also check for a __slots__ sequence variable in dict
            PDict typeDict = null;
            HashingStorage namespaceStorage = namespace.getDictStorage();
            HashingStorageIterator it = getHashingStorageIterator.execute(namespaceStorage);
            while (hashingStorageItNext.execute(namespaceStorage, it)) {
                Object keyObj = hashingStorageItKey.execute(namespaceStorage, it);
                Object value = hashingStorageItValue.execute(namespaceStorage, it);
                if (keyObj instanceof TruffleString) {
                    TruffleString key = (TruffleString) keyObj;
                    if (equalNode.execute(T___SLOTS__, key, TS_ENCODING)) {
                        slots[0] = value;
                        continue;
                    }
                    if (equalNode.execute(T___NEW__, key, TS_ENCODING)) {
                        // see CPython: if it's a plain function, make it a static function
                        if (value instanceof PFunction) {
                            pythonClass.setAttribute(key, factory.createStaticmethodFromCallableObj(value));
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
                            pythonClass.setAttribute(key, factory.createClassmethodFromCallableObj(value));
                        } else {
                            pythonClass.setAttribute(key, value);
                        }
                        continue;
                    }
                    if (equalNode.execute(T___DOC__, key, TS_ENCODING)) {
                        // CPython sets tp_doc to a copy of dict['__doc__'], if that is a string. It
                        // forcibly encodes the string as UTF-8, and raises an error if that is not
                        // possible.
                        TruffleString doc = null;
                        if (value instanceof TruffleString) {
                            doc = (TruffleString) value;
                        } else if (value instanceof PString) {
                            doc = ((PString) value).getValueUncached();
                        }
                        if (doc != null) {
                            if (!isValidNode.execute(doc, TS_ENCODING)) {
                                throw constructAndRaiseNode.raiseUnicodeEncodeError(frame, "utf-8", doc, 0, codePointLengthNode.execute(doc, TS_ENCODING), "can't encode docstring");
                            }
                        }
                        pythonClass.setAttribute(key, value);
                        continue;
                    }
                    if (equalNode.execute(T___QUALNAME__, key, TS_ENCODING)) {
                        try {
                            pythonClass.setQualName(ensureCastToStringNode().execute(value));
                            qualnameSet[0] = true;
                        } catch (CannotCastException e) {
                            throw raise.raise(PythonBuiltinClassType.TypeError, ErrorMessages.MUST_BE_S_NOT_P, "type __qualname__", "str", value);
                        }
                        continue;
                    }
                    if (equalNode.execute(T___CLASSCELL__, key, TS_ENCODING)) {
                        // don't populate this attribute
                        continue;
                    }
                    if (typeDict == null) {
                        pythonClass.setAttribute(key, value);
                        continue;
                    }
                }
                // Creates DynamicObjectStorage which ignores non-string keys
                typeDict = getOrCreateDictNode.execute(pythonClass);
                // Writing a non string key converts DynamicObjectStorage to EconomicMapStorage
                long keyHash = hashingStorageItKeyHash.execute(namespaceStorage, it);
                HashingStorage updatedStore = setHashingStorageItem.execute(frame, typeDict.getDictStorage(), keyObj, keyHash, value);
                typeDict.setDictStorage(updatedStore);
            }
        }

        @TruffleBoundary
        private PTuple copySlots(TruffleString className, SequenceStorage slotList, int slotlen, boolean add_dict, boolean add_weak, PDict namespace,
                        PythonObjectFactory factory) {
            SequenceStorage newSlots = new ObjectSequenceStorage(slotlen - PInt.intValue(add_dict) - PInt.intValue(add_weak));
            int j = 0;
            for (int i = 0; i < slotlen; i++) {
                // the cast is ensured by the previous loop
                // n.b.: passing the null frame here is fine, since the storage and index are known
                // types
                TruffleString slotName = (TruffleString) getSlotItemNode().execute(slotList, i);
                if ((add_dict && T___DICT__.equalsUncached(slotName, TS_ENCODING)) || (add_weak && T___WEAKREF__.equalsUncached(slotName, TS_ENCODING))) {
                    continue;
                }

                try {
                    slotName = PythonUtils.mangleName(className, slotName);
                } catch (OutOfMemoryError e) {
                    throw PRaiseNode.raiseUncached(this, PythonBuiltinClassType.OverflowError, ErrorMessages.PRIVATE_IDENTIFIER_TOO_LARGE_TO_BE_MANGLED);
                }
                if (slotName == null) {
                    return null;
                }

                SequenceStorageNodes.AppendNode.getUncached().execute(newSlots, slotName, NoGeneralizationNode.DEFAULT);
                // Passing 'null' frame is fine because the caller already transfers the exception
                // state to the context.
                if (!T___CLASSCELL__.equalsUncached(slotName, TS_ENCODING) && !T___QUALNAME__.equalsUncached(slotName, TS_ENCODING) &&
                                HashingStorageGetItem.hasKeyUncached(namespace.getDictStorage(), slotName)) {
                    // __qualname__ and __classcell__ will be deleted later
                    throw PRaiseNode.raiseUncached(this, PythonBuiltinClassType.ValueError, ErrorMessages.S_S_CONFLICTS_WITH_CLASS_VARIABLE, slotName, "__slots__");
                }
                j++;
            }
            assert j == slotlen - PInt.intValue(add_dict) - PInt.intValue(add_weak);

            // sort newSlots
            Arrays.sort(newSlots.getInternalArray(), (a, b) -> compareStringsUncached((TruffleString) a, (TruffleString) b));

            return factory.createTuple(newSlots);
        }

        /**
         * check that the native base does not already have tp_dictoffset
         */
        private boolean addDictIfNative(Node inliningTarget, PythonManagedClass pythonClass, PythonAbstractClass[] mro, GetBasicSizeNode getBasicSizeNode,
                        GetItemSizeNode getItemSizeNode, GetDictOffsetNode getDictOffsetNode) {
            boolean addedNewDict = false;
            if (pythonClass.needsNativeAllocation()) {
                for (PythonAbstractClass cls : mro) {
                    if (PGuards.isNativeClass(cls)) {
                        // Use GetAnyAttributeNode since these are get-set-descriptors
                        long dictoffset = getDictOffsetNode.execute(inliningTarget, cls);
                        long basicsize = getBasicSizeNode.execute(inliningTarget, cls);
                        long itemsize = getItemSizeNode.execute(inliningTarget, cls);
                        if (dictoffset == 0) {
                            addedNewDict = true;
                            // add_dict
                            if (itemsize != 0) {
                                dictoffset = -SIZEOF_PY_OBJECT_PTR;
                            } else {
                                dictoffset = basicsize;
                                basicsize += SIZEOF_PY_OBJECT_PTR;
                            }
                        }
                        SetDictOffsetNode.executeUncached(pythonClass, dictoffset);
                        SetBasicSizeNode.executeUncached(pythonClass, basicsize);
                        SetItemSizeNode.executeUncached(pythonClass, itemsize);
                        break;
                    }
                }
            }
            return addedNewDict;
        }

        /**
         * check that the native base does not already have tp_dictoffset
         */
        private void ensureBasicsize(Node inliningTarget, PythonManagedClass pythonClass, PythonAbstractClass[] mro, GetBasicSizeNode getBasicSizeNode, SetBasicSizeNode setBasicSizeNode) {
            if (pythonClass.needsNativeAllocation() && getBasicSizeNode.execute(inliningTarget, pythonClass) == 0) {
                long basicsize = 0;
                for (PythonAbstractClass cls : mro) {
                    if (PGuards.isNativeClass(cls)) {
                        basicsize = getBasicSizeNode.execute(inliningTarget, cls);
                        break;
                    }
                }
                if (basicsize <= 0) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw CompilerDirectives.shouldNotReachHere(String.format("class %s needs native allocation but has basicsize <= 0", pythonClass.getName()));
                }
                setBasicSizeNode.execute(inliningTarget, pythonClass, basicsize);
            }
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class GetBasicSizeNode extends Node {
        public abstract long execute(Node inliningTarget, Object cls);

        @Specialization
        long lookup(Object cls,
                        @Cached CExtNodes.LookupNativeMemberInMRONode lookup) {
            Object value = lookup.execute(cls, NativeMember.TP_BASICSIZE, TYPE_BASICSIZE);
            if (value != PNone.NO_VALUE) {
                return (long) value;
            }
            return 0;
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
        void set(PythonManagedClass cls, long value,
                        @Cached WriteAttributeToDynamicObjectNode write) {
            write.execute(cls, TYPE_BASICSIZE, value);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class GetItemSizeNode extends Node {
        public abstract long execute(Node inliningTarget, Object cls);

        @Specialization
        long lookup(Object cls,
                        @Cached CExtNodes.LookupNativeMemberInMRONode lookup) {
            Object value = lookup.execute(cls, NativeMember.TP_ITEMSIZE, TYPE_ITEMSIZE, GetItemSizeNode::getBuiltinTypeItemsize);
            if (value != PNone.NO_VALUE) {
                return (long) value;
            }
            return 0;
        }

        private static long getBuiltinTypeItemsize(PythonBuiltinClassType cls) {
            // Our formatter currently forces all the case labels on a single line
            // @formatter:off
            return switch (cls) {
                case PBytes -> 1;
                case PInt -> 4;
                case PFrame, PMemoryView, PTuple, PStatResult, PTerminalSize, PUnameResult, PStructTime, PProfilerEntry,
                        PProfilerSubentry, PStructPasswd, PStructRusage, PVersionInfo, PFlags, PFloatInfo,
                        PIntInfo, PHashInfo, PThreadInfo, PUnraisableHookArgs, PIOBase, PFileIO, PBufferedIOBase,
                        PBufferedReader, PBufferedWriter, PBufferedRWPair, PBufferedRandom, PIncrementalNewlineDecoder,
                        PTextIOWrapper, CArgObject, CThunkObject, StgDict, Structure, Union, PyCPointer, PyCArray,
                        PyCData, SimpleCData, PyCFuncPtr, CField, DictRemover, StructParam -> 8;
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
        void set(PythonManagedClass cls, long value,
                        @Cached WriteAttributeToDynamicObjectNode write) {
            write.execute(cls, TYPE_ITEMSIZE, value);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class GetDictOffsetNode extends Node {
        public abstract long execute(Node inliningTarget, Object cls);

        @Specialization
        long lookup(Object cls,
                        @Cached CExtNodes.LookupNativeMemberInMRONode lookup) {
            Object value = lookup.execute(cls, NativeMember.TP_DICTOFFSET, TYPE_DICTOFFSET, GetDictOffsetNode::getBuiltinTypeItemsize);
            if (value != PNone.NO_VALUE) {
                return (long) value;
            }
            return 0;
        }

        private static long getBuiltinTypeItemsize(PythonBuiltinClassType cls) {
            // TODO properly specify for all builtin classes
            PythonBuiltinClassType current = cls;
            do {
                if (current == PBaseException) {
                    return 16;
                }
            } while ((current = current.getBase()) != null);
            return 0;
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
        void set(PythonManagedClass cls, long value,
                        @Cached WriteAttributeToDynamicObjectNode write) {
            write.execute(cls, TYPE_DICTOFFSET, value);
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
}
