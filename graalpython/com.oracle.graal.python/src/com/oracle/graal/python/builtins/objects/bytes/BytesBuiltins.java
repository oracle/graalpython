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
import static com.oracle.graal.python.nodes.BuiltinNames.J_BYTES;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___BYTES__;
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
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.SequenceStorageMpSubscriptNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.SequenceStorageSqItemNode;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryFunc.MpSubscriptBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotHashFun.HashBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotRichCompare;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSizeArgFun.SqItemBuiltinNode;
import com.oracle.graal.python.lib.PyBytesCheckExactNode;
import com.oracle.graal.python.lib.PyBytesCheckNode;
import com.oracle.graal.python.lib.PyIndexCheckNode;
import com.oracle.graal.python.lib.RichCmpOp;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
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

    @Slot(value = SlotKind.tp_init, isComplex = true)
    @SlotSignature(minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonVarargsBuiltinNode {

        @SuppressWarnings("unused")
        @Override
        public Object varArgExecute(VirtualFrame frame, Object self, Object[] arguments, PKeyword[] keywords) throws VarargsBuiltinDirectInvocationNotSupported {
            return PNone.NONE;
        }

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
                        @Bind("this") Node inliningTarget,
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
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
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
                        @Bind("this") Node inliningTarget,
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
                        @Bind("this") Node inliningTarget,
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
                        @Bind("this") Node inliningTarget,
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
                        @Bind("$node") Node inliningTarget,
                        @Exclusive @Cached SequenceStorageNodes.GetInternalByteArrayNode getArray) {
            SequenceStorage selfStorage = self.getSequenceStorage();
            SequenceStorage otherStorage = other.getSequenceStorage();
            return compareByteArrays(op, getArray.execute(inliningTarget, selfStorage), selfStorage.length(), getArray.execute(inliningTarget, otherStorage), otherStorage.length());
        }

        @Fallback
        static Object cmp(Object self, Object other, RichCmpOp op,
                        @Bind("$node") Node inliningTarget,
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
                        @Bind("this") Node inliningTarget,
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
