/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.builtins.objects.bytes;

import static com.oracle.graal.python.builtins.objects.bytes.BytesNodes.compareByteArrays;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_BYTES_SUBTYPE_NEW;
import static com.oracle.graal.python.nodes.BuiltinNames.J_BYTES;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___BYTES__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___BYTES__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.builtins.objects.bytes.BytesBuiltinsClinicProviders.BytesNewNodeClinicProviderGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions;
import com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.SequenceStorageMpSubscriptNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.SequenceStorageSqItemNode;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryFunc.MpSubscriptBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotHashFun.HashBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotRichCompare;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSizeArgFun.SqItemBuiltinNode;
import com.oracle.graal.python.lib.PyBytesCheckExactNode;
import com.oracle.graal.python.lib.PyBytesCheckNode;
import com.oracle.graal.python.lib.PyIndexCheckNode;
import com.oracle.graal.python.lib.RichCmpOp;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupSpecialMethodNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PBytes)
public class BytesBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = BytesBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return BytesBuiltinsFactory.getFactories();
    }

    // bytes([source[, encoding[, errors]]])
    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = J_BYTES, minNumOfPositionalArgs = 1, parameterNames = {"$self", "source", "encoding", "errors"})
    @ArgumentClinic(name = "encoding", conversionClass = BytesNodes.ExpectStringNode.class, args = "\"bytes()\"")
    @ArgumentClinic(name = "errors", conversionClass = BytesNodes.ExpectStringNode.class, args = "\"bytes()\"")
    @GenerateNodeFactory
    public abstract static class BytesNewNode extends PythonQuaternaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BytesNewNodeClinicProviderGen.INSTANCE;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "isNoValue(source)")
        static Object doEmpty(Object cls, PNone source, PNone encoding, PNone errors,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached CreateBytes createBytes) {
            return createBytes.execute(inliningTarget, cls, PythonUtils.EMPTY_BYTE_ARRAY);
        }

        @Specialization(guards = "!isNoValue(source)")
        static Object doCallBytes(VirtualFrame frame, Object cls, Object source, PNone encoding, PNone errors,
                        @Bind Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached InlinedConditionProfile hasBytes,
                        @Cached("create(T___BYTES__)") LookupSpecialMethodNode lookupBytes,
                        @Cached CallUnaryMethodNode callBytes,
                        @Cached BytesNodes.ToBytesNode toBytesNode,
                        @Cached PyBytesCheckNode check,
                        @Exclusive @Cached BytesNodes.BytesInitNode bytesInitNode,
                        @Exclusive @Cached CreateBytes createBytes,
                        @Cached PRaiseNode raiseNode) {
            Object bytesMethod = lookupBytes.execute(frame, getClassNode.execute(inliningTarget, source), source);
            if (hasBytes.profile(inliningTarget, bytesMethod != PNone.NO_VALUE)) {
                Object bytes = callBytes.executeObject(frame, bytesMethod, source);
                if (check.execute(inliningTarget, bytes)) {
                    if (cls == PythonBuiltinClassType.PBytes) {
                        return bytes;
                    } else {
                        return createBytes.execute(inliningTarget, cls, toBytesNode.execute(frame, bytes));
                    }
                } else {
                    throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.RETURNED_NONBYTES, T___BYTES__, bytes);
                }
            }
            return createBytes.execute(inliningTarget, cls, bytesInitNode.execute(frame, inliningTarget, source, encoding, errors));
        }

        @Specialization(guards = {"isNoValue(source) || (!isNoValue(encoding) || !isNoValue(errors))"})
        static Object dontCallBytes(VirtualFrame frame, Object cls, Object source, Object encoding, Object errors,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached BytesNodes.BytesInitNode bytesInitNode,
                        @Exclusive @Cached CreateBytes createBytes) {
            return createBytes.execute(inliningTarget, cls, bytesInitNode.execute(frame, inliningTarget, source, encoding, errors));
        }

        @GenerateInline
        @GenerateCached(false)
        abstract static class CreateBytes extends PNodeWithContext {
            abstract Object execute(Node inliningTarget, Object cls, byte[] bytes);

            @Specialization(guards = "isBuiltinBytes(cls)")
            static PBytes doBuiltin(@SuppressWarnings("unused") Object cls, byte[] bytes,
                            @Bind PythonLanguage language) {
                return PFactory.createBytes(language, bytes);
            }

            @Specialization(guards = "!needsNativeAllocationNode.execute(inliningTarget, cls)")
            static PBytes doManaged(@SuppressWarnings("unused") Node inliningTarget, Object cls, byte[] bytes,
                            @SuppressWarnings("unused") @Shared @Cached TypeNodes.NeedsNativeAllocationNode needsNativeAllocationNode,
                            @Bind PythonLanguage language,
                            @Cached TypeNodes.GetInstanceShape getInstanceShape) {
                return PFactory.createBytes(language, cls, getInstanceShape.execute(cls), bytes);
            }

            @Specialization(guards = "needsNativeAllocationNode.execute(inliningTarget, cls)")
            static Object doNative(@SuppressWarnings("unused") Node inliningTarget, Object cls, byte[] bytes,
                            @SuppressWarnings("unused") @Shared @Cached TypeNodes.NeedsNativeAllocationNode needsNativeAllocationNode,
                            @Cached(inline = false) CApiTransitions.PythonToNativeNode toNative,
                            @Cached(inline = false) CApiTransitions.NativeToPythonTransferNode toPython,
                            @Cached(inline = false) CExtNodes.PCallCapiFunction call) {
                CArrayWrappers.CByteArrayWrapper wrapper = new CArrayWrappers.CByteArrayWrapper(bytes);
                try {
                    return toPython.execute(call.call(FUN_BYTES_SUBTYPE_NEW, toNative.execute(cls), wrapper, bytes.length));
                } finally {
                    wrapper.free();
                }
            }

            protected static boolean isBuiltinBytes(Object cls) {
                return cls == PythonBuiltinClassType.PBytes;
            }
        }
    }

    @Slot(value = SlotKind.tp_init, isComplex = true)
    @SlotSignature(name = "bytes", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonVarargsBuiltinNode {

        @SuppressWarnings("unused")
        @Specialization
        static Object byteDone(VirtualFrame frame, Object self, Object[] arguments, PKeyword[] keywords) {
            return PNone.NONE;
        }
    }

    @Slot(value = SlotKind.tp_hash, isComplex = true)
    @GenerateNodeFactory
    abstract static class HashNode extends HashBuiltinNode {
        @Specialization(limit = "3")
        static long hash(VirtualFrame frame, Object self,
                        @Bind Node inliningTarget,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @CachedLibrary("self") PythonBufferAcquireLibrary acquireLib,
                        @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                        @Cached BytesNodes.HashBufferNode hashBufferNode) {
            Object buffer = acquireLib.acquireReadonly(self, frame, indirectCallData);
            try {
                return hashBufferNode.execute(inliningTarget, buffer);
            } finally {
                bufferLib.release(buffer, frame, indirectCallData);
            }
        }
    }

    @Slot(value = SlotKind.sq_item, isComplex = true)
    @GenerateNodeFactory
    abstract static class GetitemNode extends SqItemBuiltinNode {
        @Specialization
        static Object doInt(Object self, int key,
                        @SuppressWarnings("unused") @Bind Node inliningTarget,
                        @Cached BytesNodes.GetBytesStorage getBytesStorage,
                        @Cached SequenceStorageSqItemNode sqItemNode) {
            SequenceStorage storage = getBytesStorage.execute(inliningTarget, self);
            return sqItemNode.execute(inliningTarget, storage, key, ErrorMessages.BYTES_OUT_OF_BOUNDS);
        }
    }

    @Slot(value = SlotKind.mp_subscript, isComplex = true)
    @GenerateNodeFactory
    abstract static class BytesSubcript extends MpSubscriptBuiltinNode {
        @Specialization
        static Object doIt(VirtualFrame frame, Object self, Object idx,
                        @Bind Node inliningTarget,
                        @Cached InlinedConditionProfile validProfile,
                        @Cached PyIndexCheckNode indexCheckNode,
                        @Cached PRaiseNode raiseNode,
                        @Cached BytesNodes.GetBytesStorage getBytesStorage,
                        @Cached SequenceStorageMpSubscriptNode subscriptNode) {
            if (!validProfile.profile(inliningTarget, SequenceStorageMpSubscriptNode.isValidIndex(inliningTarget, idx, indexCheckNode))) {
                throw raiseNonIntIndex(inliningTarget, raiseNode, idx);
            }
            return subscriptNode.execute(frame, inliningTarget, getBytesStorage.execute(inliningTarget, self), idx,
                            ErrorMessages.LIST_INDEX_OUT_OF_RANGE, PFactory::createBytes);
        }

        @InliningCutoff
        private static PException raiseNonIntIndex(Node inliningTarget, PRaiseNode raiseNode, Object index) {
            throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.OBJ_INDEX_MUST_BE_INT_OR_SLICES, "byte", index);
        }
    }

    @Slot(value = SlotKind.tp_repr, isComplex = true)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        public static TruffleString repr(Object self,
                        @Bind Node inliningTarget,
                        @Cached BytesNodes.BytesReprNode reprNode) {
            return reprNode.execute(inliningTarget, self);
        }
    }

    // bytes.translate(table, delete=b'')
    @Builtin(name = "translate", minNumOfPositionalArgs = 2, parameterNames = {"self", "table", "delete"})
    @GenerateNodeFactory
    public abstract static class TranslateNode extends BytesNodes.BaseTranslateNode {

        @Specialization
        static Object translate(VirtualFrame frame, Object self, Object table, Object delete,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached InlinedConditionProfile isLenTable256Profile,
                        @Cached InlinedBranchProfile hasTable,
                        @Cached InlinedBranchProfile hasDelete,
                        @Cached BytesNodes.ToBytesNode toBytesNode,
                        @Cached PyBytesCheckExactNode checkExactNode,
                        @Cached PRaiseNode raiseNode) {
            byte[] bTable = null;
            if (table != PNone.NONE) {
                hasTable.enter(inliningTarget);
                bTable = toBytesNode.execute(frame, table);
                checkLengthOfTable(inliningTarget, bTable, isLenTable256Profile, raiseNode);
            }
            byte[] bDelete = null;
            if (delete != PNone.NO_VALUE) {
                hasDelete.enter(inliningTarget);
                bDelete = toBytesNode.execute(frame, delete);
            }
            byte[] bSelf = toBytesNode.execute(null, self);

            Result result;
            if (bTable != null && bDelete != null) {
                result = translateAndDelete(bSelf, bTable, bDelete);
            } else if (bTable != null) {
                result = translate(bSelf, bTable);
            } else if (bDelete != null) {
                result = delete(bSelf, bDelete);
            } else if (!checkExactNode.execute(inliningTarget, self)) {
                return PFactory.createBytes(language, bSelf);
            } else {
                return self;
            }
            if (result.changed || !checkExactNode.execute(inliningTarget, self)) {
                return PFactory.createBytes(language, result.array);
            }
            return self;
        }
    }

    // bytes.fromhex()
    @Builtin(name = "fromhex", minNumOfPositionalArgs = 2, isClassmethod = true, numOfPositionalOnlyArgs = 2, parameterNames = {"$cls", "string"})
    @ArgumentClinic(name = "string", conversion = ArgumentClinic.ClinicConversion.TString)
    @GenerateNodeFactory
    public abstract static class FromHexNode extends PythonBinaryClinicBuiltinNode {

        @Specialization(guards = "isBuiltinBytesType(cls)")
        static PBytes doBytes(@SuppressWarnings("unused") Object cls, TruffleString str,
                        @Shared("hexToBytes") @Cached BytesNodes.HexStringToBytesNode hexStringToBytesNode,
                        @Bind PythonLanguage language) {
            return PFactory.createBytes(language, hexStringToBytesNode.execute(str));
        }

        @Specialization(guards = "!isBuiltinBytesType(cls)")
        static Object doGeneric(VirtualFrame frame, Object cls, TruffleString str,
                        @Cached CallNode callNode,
                        @Shared("hexToBytes") @Cached BytesNodes.HexStringToBytesNode hexStringToBytesNode,
                        @Bind PythonLanguage language) {
            PBytes bytes = PFactory.createBytes(language, hexStringToBytesNode.execute(str));
            return callNode.execute(frame, cls, bytes);
        }

        protected static boolean isBuiltinBytesType(Object cls) {
            return cls == PythonBuiltinClassType.PBytes;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BytesBuiltinsClinicProviders.FromHexNodeClinicProviderGen.INSTANCE;
        }
    }

    @Slot(value = SlotKind.tp_richcompare, isComplex = false)
    @GenerateNodeFactory
    @GenerateUncached
    // N.B. bytes only allow comparing to bytes, bytearray has its own implementation that uses
    // buffer API
    abstract static class RichCmpNode extends TpSlotRichCompare.RichCmpBuiltinNode {
        @Specialization
        static boolean cmp(PBytes self, PBytes other, RichCmpOp op,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached SequenceStorageNodes.GetInternalByteArrayNode getArray) {
            SequenceStorage selfStorage = self.getSequenceStorage();
            SequenceStorage otherStorage = other.getSequenceStorage();
            return compareByteArrays(op, getArray.execute(inliningTarget, selfStorage), selfStorage.length(), getArray.execute(inliningTarget, otherStorage), otherStorage.length());
        }

        @Fallback
        static Object cmp(Object self, Object other, RichCmpOp op,
                        @Bind Node inliningTarget,
                        @SuppressWarnings("unused") @Cached PyBytesCheckNode check,
                        @Cached BytesNodes.GetBytesStorage getBytesStorage,
                        @Exclusive @Cached SequenceStorageNodes.GetInternalByteArrayNode getArray,
                        @Cached PRaiseNode raiseNode) {
            if (check.execute(inliningTarget, self)) {
                if (check.execute(inliningTarget, other)) {
                    SequenceStorage selfStorage = getBytesStorage.execute(inliningTarget, self);
                    SequenceStorage otherStorage = getBytesStorage.execute(inliningTarget, other);
                    return compareByteArrays(op, getArray.execute(inliningTarget, selfStorage), selfStorage.length(), getArray.execute(inliningTarget, otherStorage), otherStorage.length());
                } else {
                    return PNotImplemented.NOT_IMPLEMENTED;
                }
            }
            throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.DESCRIPTOR_S_REQUIRES_S_OBJ_RECEIVED_P, op.getPythonName(), J_BYTES, self);
        }
    }

    @Builtin(name = J___BYTES__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class BytesNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object bytes(Object self,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached PyBytesCheckExactNode check,
                        @Cached BytesNodes.GetBytesStorage getBytesStorage) {
            if (check.execute(inliningTarget, self)) {
                return self;
            } else {
                return PFactory.createBytes(language, getBytesStorage.execute(inliningTarget, self));
            }
        }
    }
}
