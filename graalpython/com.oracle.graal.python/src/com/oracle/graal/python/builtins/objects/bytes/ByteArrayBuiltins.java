/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates.
 * Copyright (c) 2014, Regents of the University of California
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
import static com.oracle.graal.python.nodes.BuiltinNames.J_APPEND;
import static com.oracle.graal.python.nodes.BuiltinNames.J_BYTEARRAY;
import static com.oracle.graal.python.nodes.BuiltinNames.J_EXTEND;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___INIT__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.MemoryError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.HashNotImplemented;
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
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes.FindNode;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes.GetBytesStorage;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes.HexStringToBytesNode;
import com.oracle.graal.python.builtins.objects.common.IndexNodes.NormalizeIndexNode;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetInternalByteArrayNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.SequenceStorageMpSubscriptNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.SequenceStorageSqItemNode;
import com.oracle.graal.python.builtins.objects.iterator.IteratorNodes;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.slice.PSlice.SliceInfo;
import com.oracle.graal.python.builtins.objects.slice.SliceNodes;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryFunc.MpSubscriptBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotMpAssSubscript.MpAssSubscriptBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotRichCompare;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSizeArgFun.SqItemBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSizeArgFun.SqRepeatBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSqAssItem.SqAssItemBuiltinNode;
import com.oracle.graal.python.lib.PyByteArrayCheckNode;
import com.oracle.graal.python.lib.PyIndexCheckNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectGetStateNode;
import com.oracle.graal.python.lib.PySliceNew;
import com.oracle.graal.python.lib.RichCmpOp;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.builtins.ListNodes;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CastToByteNode;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PByteArray)
@HashNotImplemented
public final class ByteArrayBuiltins extends PythonBuiltins {

    public static final TpSlots SLOTS = ByteArrayBuiltinsSlotsGen.SLOTS;

    private static final TruffleString T_LATIN_1 = tsLiteral("latin-1");

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ByteArrayBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = J_BYTEARRAY, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class ByteArrayNode extends PythonBuiltinNode {
        @Specialization
        public PByteArray setEmpty(Object cls, @SuppressWarnings("unused") Object arg,
                        @Bind PythonLanguage language,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape) {
            // data filled in subsequent __init__ call - see BytesCommonBuiltins.InitNode
            return PFactory.createByteArray(language, cls, getInstanceShape.execute(cls), PythonUtils.EMPTY_BYTE_ARRAY);
        }

        // TODO: native allocation?
    }

    // bytearray([source[, encoding[, errors]]])
    @Slot(value = SlotKind.tp_init, isComplex = true)
    @SlotSignature(name = J_BYTEARRAY, minNumOfPositionalArgs = 1, parameterNames = {"$self", "source", "encoding", "errors"})
    @ArgumentClinic(name = "encoding", conversionClass = BytesNodes.ExpectStringNode.class, args = "\"bytearray()\"")
    @ArgumentClinic(name = "errors", conversionClass = BytesNodes.ExpectStringNode.class, args = "\"bytearray()\"")
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonQuaternaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ByteArrayBuiltinsClinicProviders.InitNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = "!isNone(source)")
        static PNone doInit(VirtualFrame frame, PByteArray self, Object source, Object encoding, Object errors,
                        @Bind Node inliningTarget,
                        @Cached BytesNodes.BytesInitNode toBytesNode) {
            self.setSequenceStorage(new ByteSequenceStorage(toBytesNode.execute(frame, inliningTarget, source, encoding, errors)));
            return PNone.NONE;
        }

        @Specialization(guards = "isNone(self)")
        static PNone doInit(@SuppressWarnings("unused") PByteArray self, Object source, @SuppressWarnings("unused") Object encoding, @SuppressWarnings("unused") Object errors,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.CANNOT_CONVERT_P_OBJ_TO_S, source, "bytearray");
        }

        @Specialization(guards = "!isBytes(self)")
        static PNone doInit(Object self, @SuppressWarnings("unused") Object source, @SuppressWarnings("unused") Object encoding, @SuppressWarnings("unused") Object errors,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.DESCRIPTOR_S_REQUIRES_S_OBJ_RECEIVED_P, T___INIT__, "bytearray", self);
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
            return sqItemNode.execute(inliningTarget, storage, key, ErrorMessages.BYTEARRAY_OUT_OF_BOUNDS);
        }
    }

    @Slot(value = SlotKind.mp_subscript, isComplex = true)
    @GenerateNodeFactory
    abstract static class ByteArraySubcript extends MpSubscriptBuiltinNode {
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
                            ErrorMessages.LIST_INDEX_OUT_OF_RANGE, PFactory::createByteArray);
        }

        @InliningCutoff
        private static PException raiseNonIntIndex(Node inliningTarget, PRaiseNode raiseNode, Object index) {
            throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.OBJ_INDEX_MUST_BE_INT_OR_SLICES, "bytearray", index);
        }
    }

    @Slot(value = SlotKind.sq_ass_item, isComplex = true)
    @GenerateNodeFactory
    abstract static class SetItemNode extends SqAssItemBuiltinNode {

        @Specialization(guards = "!isNoValue(value)")
        static void set(PByteArray self, int index, Object value,
                        @Bind Node inliningTarget,
                        @Shared @Cached("forBytearray()") NormalizeIndexNode normalizeIndexNode,
                        @Cached SequenceStorageNodes.SetItemScalarNode setItemNode) {
            index = normalizeIndexNode.execute(index, self.getSequenceStorage().length());
            setItemNode.execute(inliningTarget, self.getSequenceStorage(), index, value);
        }

        @Specialization(guards = "isNoValue(value)")
        static void del(PByteArray self, int index, @SuppressWarnings("unused") Object value,
                        @Bind Node inliningTarget,
                        @Shared @Cached("forBytearray()") NormalizeIndexNode normalizeIndexNode,
                        @Cached SequenceStorageNodes.DeleteItemNode deleteItemNode) {
            index = normalizeIndexNode.execute(index, self.getSequenceStorage().length());
            deleteItemNode.execute(inliningTarget, self.getSequenceStorage(), index);
        }
    }

    @Slot(value = SlotKind.mp_ass_subscript, isComplex = true)
    @GenerateNodeFactory
    abstract static class SetSubscriptNode extends MpAssSubscriptBuiltinNode {

        @Specialization(guards = {"!isPSlice(indexObj)", "!isNoValue(value)"})
        static void set(VirtualFrame frame, PByteArray self, Object indexObj, Object value,
                        @Bind Node inliningTarget,
                        @Cached PyIndexCheckNode indexCheckNode,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached("forBytearray()") NormalizeIndexNode normalizeIndexNode,
                        @Cached SequenceStorageNodes.SetItemScalarNode setItemNode,
                        @Cached PRaiseNode raiseNode) {
            if (indexCheckNode.execute(inliningTarget, indexObj)) {
                int index = asSizeNode.executeExact(frame, inliningTarget, indexObj);
                index = normalizeIndexNode.execute(index, self.getSequenceStorage().length());
                setItemNode.execute(inliningTarget, self.getSequenceStorage(), index, value);
            } else {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.OBJ_INDEX_MUST_BE_INT_OR_SLICES, "bytearray", indexObj);
            }
        }

        @Specialization(guards = "!isPString(value)")
        static void doSliceSequence(VirtualFrame frame, PByteArray self, PSlice slice, PSequence value,
                        @Bind Node inliningTarget,
                        @Cached @Shared InlinedConditionProfile differentLenProfile,
                        @Cached @Shared SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Cached @Shared SequenceStorageNodes.SetItemSliceNode setItemSliceNode,
                        @Cached @Shared SliceNodes.CoerceToIntSlice sliceCast,
                        @Cached @Shared SliceNodes.SliceUnpack unpack,
                        @Cached @Shared SliceNodes.AdjustIndices adjustIndices,
                        @Cached @Shared PRaiseNode raiseNode) {
            SequenceStorage storage = self.getSequenceStorage();
            int otherLen = getSequenceStorageNode.execute(inliningTarget, value).length();
            SliceInfo unadjusted = unpack.execute(inliningTarget, sliceCast.execute(inliningTarget, slice));
            SliceInfo info = adjustIndices.execute(inliningTarget, storage.length(), unadjusted);
            if (differentLenProfile.profile(inliningTarget, info.sliceLength != otherLen)) {
                self.checkCanResize(inliningTarget, raiseNode);
            }
            setItemSliceNode.execute(frame, inliningTarget, storage, info, value, false);
        }

        @Specialization(guards = {"!isNoValue(value)", "bufferAcquireLib.hasBuffer(value)"}, limit = "3")
        static void doSliceBuffer(VirtualFrame frame, PByteArray self, PSlice slice, Object value,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached("createFor($node)") IndirectCallData indirectCallData,
                        @CachedLibrary("value") PythonBufferAcquireLibrary bufferAcquireLib,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib,
                        @Cached @Shared InlinedConditionProfile differentLenProfile,
                        @Cached @Shared SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Cached @Shared SequenceStorageNodes.SetItemSliceNode setItemSliceNode,
                        @Cached @Shared SliceNodes.CoerceToIntSlice sliceCast,
                        @Cached @Shared SliceNodes.SliceUnpack unpack,
                        @Cached @Shared SliceNodes.AdjustIndices adjustIndices,
                        @Shared @Cached PRaiseNode raiseNode) {
            Object buffer = bufferAcquireLib.acquireReadonly(value, frame, indirectCallData);
            try {
                // TODO avoid copying if possible. Note that it is possible that value is self
                PBytes bytes = PFactory.createBytes(language, bufferLib.getCopiedByteArray(value));
                doSliceSequence(frame, self, slice, bytes, inliningTarget, differentLenProfile, getSequenceStorageNode, setItemSliceNode, sliceCast, unpack, adjustIndices, raiseNode);
            } finally {
                bufferLib.release(buffer, frame, indirectCallData);
            }
        }

        @Specialization(guards = "!isNoValue(value)", replaces = {"doSliceSequence", "doSliceBuffer"})
        static void doSliceGeneric(VirtualFrame frame, PByteArray self, PSlice slice, Object value,
                        @Bind Node inliningTarget,
                        @Cached @Shared InlinedConditionProfile differentLenProfile,
                        @Cached @Shared SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Cached @Shared SequenceStorageNodes.SetItemSliceNode setItemSliceNode,
                        @Cached @Shared SliceNodes.CoerceToIntSlice sliceCast,
                        @Cached @Shared SliceNodes.SliceUnpack unpack,
                        @Cached @Shared SliceNodes.AdjustIndices adjustIndices,
                        @Cached ListNodes.ConstructListNode constructListNode,
                        @Shared @Cached PRaiseNode raiseNode) {
            PList values = constructListNode.execute(frame, value);
            doSliceSequence(frame, self, slice, values, inliningTarget, differentLenProfile, getSequenceStorageNode, setItemSliceNode, sliceCast, unpack, adjustIndices, raiseNode);
        }

        @Specialization(guards = "isNoValue(value)")
        static void doDelete(VirtualFrame frame, PByteArray self, Object key, @SuppressWarnings("unused") Object value,
                        @Bind Node inliningTarget,
                        @Cached SequenceStorageNodes.DeleteNode deleteNode,
                        @Cached PRaiseNode raiseNode) {
            self.checkCanResize(inliningTarget, raiseNode);
            deleteNode.execute(frame, self.getSequenceStorage(), key);
        }
    }

    @Builtin(name = "insert", minNumOfPositionalArgs = 3, parameterNames = {"$self", "index", "item"}, numOfPositionalOnlyArgs = 3)
    @GenerateNodeFactory
    @ArgumentClinic(name = "index", conversion = ArgumentClinic.ClinicConversion.Index)
    @ArgumentClinic(name = "item", conversion = ArgumentClinic.ClinicConversion.Index)
    public abstract static class InsertNode extends PythonTernaryClinicBuiltinNode {

        public abstract PNone execute(VirtualFrame frame, PByteArray list, Object index, Object value);

        @Specialization(guards = "isByteStorage(self)")
        static PNone insert(VirtualFrame frame, PByteArray self, int index, int value,
                        @Bind Node inliningTarget,
                        @Shared @Cached CastToByteNode toByteNode,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            self.checkCanResize(inliningTarget, raiseNode);
            byte v = toByteNode.execute(frame, value);
            ByteSequenceStorage target = (ByteSequenceStorage) self.getSequenceStorage();
            target.insertByteItem(normalizeIndex(index, target.length()), v);
            return PNone.NONE;
        }

        @Specialization
        static PNone insert(VirtualFrame frame, PByteArray self, int index, int value,
                        @Bind Node inliningTarget,
                        @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Cached SequenceStorageNodes.InsertItemNode insertItemNode,
                        @Shared @Cached CastToByteNode toByteNode,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            self.checkCanResize(inliningTarget, raiseNode);
            byte v = toByteNode.execute(frame, value);
            SequenceStorage storage = getSequenceStorageNode.execute(inliningTarget, self);
            insertItemNode.execute(inliningTarget, storage, normalizeIndex(index, storage.length()), v);
            return PNone.NONE;
        }

        private static int normalizeIndex(int index, int len) {
            int idx = index;
            if (idx < 0) {
                idx += len;
                if (idx < 0) {
                    idx = 0;
                }
            }
            if (idx > len) {
                idx = len;
            }
            return idx;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ByteArrayBuiltinsClinicProviders.InsertNodeClinicProviderGen.INSTANCE;
        }
    }

    @Slot(value = SlotKind.tp_repr, isComplex = true)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object repr(PByteArray self,
                        @Bind Node inliningTarget,
                        @Cached SequenceStorageNodes.GetInternalByteArrayNode getBytes,
                        @Cached TypeNodes.GetNameNode getNameNode,
                        @Cached GetClassNode getClassNode,
                        @Cached TruffleStringBuilder.AppendCodePointNode appendCodePointNode,
                        @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            SequenceStorage store = self.getSequenceStorage();
            byte[] bytes = getBytes.execute(inliningTarget, store);
            int len = store.length();
            TruffleStringBuilder sb = TruffleStringBuilder.create(TS_ENCODING);
            TruffleString typeName = getNameNode.execute(inliningTarget, getClassNode.execute(inliningTarget, self));
            appendStringNode.execute(sb, typeName);
            appendCodePointNode.execute(sb, '(', 1, true);
            BytesUtils.reprLoop(sb, bytes, len, appendCodePointNode);
            appendCodePointNode.execute(sb, ')', 1, true);
            return toStringNode.execute(sb);
        }
    }

    @Slot(value = SlotKind.sq_inplace_concat, isComplex = true)
    @GenerateNodeFactory
    public abstract static class IAddNode extends PythonBinaryBuiltinNode {
        @Specialization
        static PByteArray add(PByteArray self, PBytesLike other,
                        @Bind Node inliningTarget,
                        @Shared @Cached SequenceStorageNodes.EnsureCapacityNode ensureCapacityNode,
                        @Shared @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                        @Shared @Cached PRaiseNode raiseNode) {
            return extendWithBuffer(self, other, inliningTarget, ensureCapacityNode, bufferLib, raiseNode);
        }

        @Specialization(guards = "!isBytes(other)", limit = "3")
        static PByteArray add(VirtualFrame frame, PByteArray self, Object other,
                        @Bind Node inliningTarget,
                        @Cached("createFor($node)") IndirectCallData indirectCallData,
                        @CachedLibrary("other") PythonBufferAcquireLibrary bufferAcquireLib,
                        @Shared @Cached SequenceStorageNodes.EnsureCapacityNode ensureCapacityNode,
                        @Shared @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                        @Shared @Cached PRaiseNode raiseNode) {
            Object otherBuffer;
            try {
                otherBuffer = bufferAcquireLib.acquireReadonly(other, frame, indirectCallData);
            } catch (PException e) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.CANT_CONCAT_P_TO_S, other, "bytearray");
            }
            try {
                return extendWithBuffer(self, otherBuffer, inliningTarget, ensureCapacityNode, bufferLib, raiseNode);
            } finally {
                bufferLib.release(otherBuffer, frame, indirectCallData);
            }
        }

        private static PByteArray extendWithBuffer(PByteArray self, Object otherBuffer, Node inliningTarget, SequenceStorageNodes.EnsureCapacityNode ensureCapacityNode,
                        PythonBufferAccessLibrary bufferLib, PRaiseNode raiseNode) {
            self.checkCanResize(inliningTarget, raiseNode);
            try {
                int len = self.getSequenceStorage().length();
                int otherLen = bufferLib.getBufferLength(otherBuffer);
                int newLen = PythonUtils.addExact(len, otherLen);
                ensureCapacityNode.execute(inliningTarget, self.getSequenceStorage(), newLen);
                self.getSequenceStorage().setNewLength(newLen);
                bufferLib.readIntoBuffer(otherBuffer, 0, self, len, otherLen, bufferLib);
                return self;
            } catch (OverflowException e) {
                throw raiseNode.raise(inliningTarget, MemoryError);
            }
        }
    }

    @Slot(value = SlotKind.sq_inplace_repeat, isComplex = true)
    @GenerateNodeFactory
    public abstract static class IMulNode extends SqRepeatBuiltinNode {
        @Specialization
        static Object mul(VirtualFrame frame, PByteArray self, int times,
                        @Bind Node inliningTarget,
                        @Cached SequenceStorageNodes.RepeatNode repeatNode,
                        @Cached PRaiseNode raiseNode) {
            self.checkCanResize(inliningTarget, raiseNode);
            SequenceStorage res = repeatNode.execute(frame, self.getSequenceStorage(), times);
            self.setSequenceStorage(res);
            return self;
        }
    }

    @Builtin(name = "remove", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class RemoveNode extends PythonBinaryBuiltinNode {

        @Specialization
        static PNone remove(VirtualFrame frame, PByteArray self, Object value,
                        @Bind Node inliningTarget,
                        @Cached("createCast()") CastToByteNode cast,
                        @Cached SequenceStorageNodes.GetInternalByteArrayNode getBytes,
                        @Cached SequenceStorageNodes.DeleteNode deleteNode,
                        @Cached PRaiseNode raiseNode) {
            self.checkCanResize(inliningTarget, raiseNode);
            SequenceStorage storage = self.getSequenceStorage();
            int len = storage.length();
            int pos = FindNode.find(getBytes.execute(inliningTarget, self.getSequenceStorage()), len, cast.execute(frame, value), 0, len, false);
            if (pos != -1) {
                deleteNode.execute(frame, storage, pos);
                return PNone.NONE;
            }
            throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.NOT_IN_BYTEARRAY);
        }

        @NeverDefault
        static CastToByteNode createCast() {
            return CastToByteNode.create((node, val) -> {
                throw PRaiseNode.raiseStatic(node, ValueError, ErrorMessages.BYTE_MUST_BE_IN_RANGE);
            }, (node, val) -> {
                throw PRaiseNode.raiseStatic(node, TypeError, ErrorMessages.OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER, "bytes");
            });
        }

        @Fallback
        static Object doError(@SuppressWarnings("unused") Object self, Object arg,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER, arg);
        }
    }

    @Builtin(name = "pop", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class PopNode extends PythonBuiltinNode {

        @Specialization
        static Object popLast(VirtualFrame frame, PByteArray self, @SuppressWarnings("unused") PNone none,
                        @Bind Node inliningTarget,
                        @Shared("getItem") @Cached SequenceStorageNodes.GetItemNode getItemNode,
                        @Shared @Cached("createDelete()") SequenceStorageNodes.DeleteNode deleteNode,
                        @Shared @Cached PRaiseNode raiseNode) {
            self.checkCanResize(inliningTarget, raiseNode);
            SequenceStorage store = self.getSequenceStorage();
            Object ret = getItemNode.execute(store, -1);
            deleteNode.execute(frame, store, -1);
            return ret;
        }

        @Specialization(guards = {"!isNoValue(idx)", "!isPSlice(idx)"})
        static Object doIndex(VirtualFrame frame, PByteArray self, Object idx,
                        @Bind Node inliningTarget,
                        @Shared("getItem") @Cached SequenceStorageNodes.GetItemNode getItemNode,
                        @Shared @Cached("createDelete()") SequenceStorageNodes.DeleteNode deleteNode,
                        @Shared @Cached PRaiseNode raiseNode) {
            self.checkCanResize(inliningTarget, raiseNode);
            SequenceStorage store = self.getSequenceStorage();
            Object ret = getItemNode.execute(frame, store, idx);
            deleteNode.execute(frame, store, idx);
            return ret;
        }

        @Fallback
        static Object doError(@SuppressWarnings("unused") Object self, Object arg,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER, arg);
        }

        @NeverDefault
        protected static SequenceStorageNodes.DeleteNode createDelete() {
            return SequenceStorageNodes.DeleteNode.create(createNormalize());
        }

        @NeverDefault
        private static NormalizeIndexNode createNormalize() {
            return NormalizeIndexNode.create(ErrorMessages.POP_INDEX_OUT_OF_RANGE);
        }
    }

    @Builtin(name = J_APPEND, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class AppendNode extends PythonBinaryBuiltinNode {

        @Specialization
        static PNone append(VirtualFrame frame, PByteArray byteArray, Object arg,
                        @Bind Node inliningTarget,
                        @Cached("createCast()") CastToByteNode toByteNode,
                        @Cached SequenceStorageNodes.AppendNode appendNode,
                        @Cached PRaiseNode raiseNode) {
            byteArray.checkCanResize(inliningTarget, raiseNode);
            appendNode.execute(inliningTarget, byteArray.getSequenceStorage(), toByteNode.execute(frame, arg), BytesNodes.BytesLikeNoGeneralizationNode.SUPPLIER);
            return PNone.NONE;
        }

        @NeverDefault
        static CastToByteNode createCast() {
            return CastToByteNode.create((node, val) -> {
                throw PRaiseNode.raiseStatic(node, ValueError, ErrorMessages.BYTE_MUST_BE_IN_RANGE);
            }, (node, val) -> {
                throw PRaiseNode.raiseStatic(node, TypeError, ErrorMessages.OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER, "bytes");
            });
        }
    }

    // bytearray.extend(L)
    @Builtin(name = J_EXTEND, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ExtendNode extends PythonBinaryBuiltinNode {

        @Specialization
        static PNone doBytes(VirtualFrame frame, PByteArray self, PBytesLike source,
                        @Bind Node inliningTarget,
                        @Cached IteratorNodes.GetLength lenNode,
                        @Cached("createExtend()") @Shared SequenceStorageNodes.ExtendNode extendNode,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            self.checkCanResize(inliningTarget, raiseNode);
            int len = lenNode.execute(frame, inliningTarget, source);
            extend(frame, self, source, len, extendNode);
            return PNone.NONE;
        }

        @Specialization(guards = "!isBytes(source)", limit = "3")
        static PNone doGeneric(VirtualFrame frame, PByteArray self, Object source,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached("createFor($node)") IndirectCallData indirectCallData,
                        @CachedLibrary("source") PythonBufferAcquireLibrary bufferAcquireLib,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib,
                        @Cached InlinedConditionProfile bufferProfile,
                        @Cached BytesNodes.IterableToByteNode iterableToByteNode,
                        @Cached IsBuiltinObjectProfile errorProfile,
                        @Cached("createExtend()") @Shared SequenceStorageNodes.ExtendNode extendNode,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            self.checkCanResize(inliningTarget, raiseNode);
            byte[] b;
            if (bufferProfile.profile(inliningTarget, bufferAcquireLib.hasBuffer(source))) {
                Object buffer = bufferAcquireLib.acquireReadonly(source, frame, indirectCallData);
                try {
                    // TODO avoid copying
                    b = bufferLib.getCopiedByteArray(buffer);
                } finally {
                    bufferLib.release(buffer, frame, indirectCallData);
                }
            } else {
                try {
                    b = iterableToByteNode.execute(frame, source);
                } catch (PException e) {
                    e.expect(inliningTarget, TypeError, errorProfile);
                    throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.CANT_EXTEND_BYTEARRAY_WITH_P, source);
                }
            }
            PByteArray bytes = PFactory.createByteArray(language, b);
            extend(frame, self, bytes, b.length, extendNode);
            return PNone.NONE;
        }

        private static void extend(VirtualFrame frame, PByteArray self, Object source,
                        int len, SequenceStorageNodes.ExtendNode extendNode) {
            SequenceStorage execute = extendNode.execute(frame, self.getSequenceStorage(), source, len);
            assert self.getSequenceStorage() == execute : "Unexpected storage generalization!";
        }

        @NeverDefault
        protected static SequenceStorageNodes.ExtendNode createExtend() {
            return SequenceStorageNodes.ExtendNode.create(BytesNodes.BytesLikeNoGeneralizationNode.SUPPLIER);
        }
    }

    // bytearray.copy()
    @Builtin(name = "copy", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class CopyNode extends PythonBuiltinNode {
        @Specialization
        static PByteArray copy(PByteArray byteArray,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached GetClassNode getClassNode,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape,
                        @Cached SequenceStorageNodes.ToByteArrayNode toByteArray) {
            Object cls = getClassNode.execute(inliningTarget, byteArray);
            return PFactory.createByteArray(language, cls, getInstanceShape.execute(cls), toByteArray.execute(inliningTarget, byteArray.getSequenceStorage()));
        }
    }

    // bytearray.reverse()
    @Builtin(name = "reverse", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReverseNode extends PythonBuiltinNode {

        @Specialization
        static PNone reverse(PByteArray byteArray,
                        @Bind Node inliningTarget,
                        @Cached SequenceStorageNodes.ReverseNode reverseNode) {
            reverseNode.execute(inliningTarget, byteArray.getSequenceStorage());
            return PNone.NONE;
        }
    }

    // bytearray.clear()
    @Builtin(name = "clear", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ClearNode extends PythonUnaryBuiltinNode {

        @Specialization
        static PNone clear(VirtualFrame frame, PByteArray byteArray,
                        @Bind Node inliningTarget,
                        @Cached SequenceStorageNodes.DeleteNode deleteNode,
                        @Cached PySliceNew sliceNode,
                        @Cached PRaiseNode raiseNode) {
            byteArray.checkCanResize(inliningTarget, raiseNode);
            deleteNode.execute(frame, byteArray.getSequenceStorage(), sliceNode.execute(inliningTarget, PNone.NONE, PNone.NONE, 1));
            return PNone.NONE;
        }
    }

    // bytearray.fromhex()
    @Builtin(name = "fromhex", minNumOfPositionalArgs = 2, isClassmethod = true, numOfPositionalOnlyArgs = 2, parameterNames = {"$cls", "string"})
    @ArgumentClinic(name = "string", conversion = ArgumentClinic.ClinicConversion.TString)
    @GenerateNodeFactory
    public abstract static class FromHexNode extends PythonBinaryClinicBuiltinNode {

        @Specialization(guards = "isBuiltinByteArrayType(cls)")
        static PByteArray doBytes(@SuppressWarnings("unused") Object cls, TruffleString str,
                        @Shared("hexToBytes") @Cached HexStringToBytesNode hexStringToBytesNode,
                        @Bind PythonLanguage language) {
            return PFactory.createByteArray(language, hexStringToBytesNode.execute(str));
        }

        @Specialization(guards = "!isBuiltinByteArrayType(cls)")
        static Object doGeneric(VirtualFrame frame, Object cls, TruffleString str,
                        @Cached CallNode callNode,
                        @Shared("hexToBytes") @Cached HexStringToBytesNode hexStringToBytesNode,
                        @Bind PythonLanguage language) {
            PByteArray byteArray = PFactory.createByteArray(language, hexStringToBytesNode.execute(str));
            return callNode.execute(frame, cls, byteArray);
        }

        protected static boolean isBuiltinByteArrayType(Object cls) {
            return cls == PythonBuiltinClassType.PByteArray;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ByteArrayBuiltinsClinicProviders.FromHexNodeClinicProviderGen.INSTANCE;
        }
    }

    // bytearray.translate(table, delete=b'')
    @Builtin(name = "translate", minNumOfPositionalArgs = 2, parameterNames = {"self", "table", "delete"})
    @GenerateNodeFactory
    public abstract static class TranslateNode extends BytesNodes.BaseTranslateNode {

        @Specialization
        static PByteArray translate(VirtualFrame frame, PByteArray self, Object table, Object delete,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached InlinedConditionProfile isLenTable256Profile,
                        @Cached InlinedBranchProfile hasTable,
                        @Cached InlinedBranchProfile hasDelete,
                        @Cached BytesNodes.ToBytesNode toBytesNode,
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
            byte[] bSelf = toBytesNode.execute(self);

            Result result;
            if (bTable != null && bDelete != null) {
                result = translateAndDelete(bSelf, bTable, bDelete);
            } else if (bTable != null) {
                result = translate(bSelf, bTable);
            } else if (bDelete != null) {
                result = delete(bSelf, bDelete);
            } else {
                return PFactory.createByteArray(language, bSelf);
            }
            return PFactory.createByteArray(language, result.array);
        }
    }

    // bytearray.clear()
    @Builtin(name = "__alloc__", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class AllocNode extends PythonUnaryBuiltinNode {

        @Specialization
        public static int alloc(PByteArray byteArray) {
            // XXX: (mq) We return a fake allocation size.
            // The actual number might useful for manual memory management.
            return byteArray.getSequenceStorage().length() + 1;
        }
    }

    static Object commonReduce(int proto, byte[] bytes, int len, Object clazz, Object dict,
                    PythonLanguage language, TruffleStringBuilder.AppendCodePointNode appendCodePointNode, TruffleStringBuilder.ToStringNode toStringNode) {
        TruffleStringBuilder sb = TruffleStringBuilder.create(TS_ENCODING);
        BytesUtils.repr(sb, bytes, len, appendCodePointNode);
        TruffleString str = toStringNode.execute(sb);
        Object contents;
        if (proto < 3) {
            contents = PFactory.createTuple(language, new Object[]{str, T_LATIN_1});
        } else {
            if (len > 0) {
                contents = PFactory.createTuple(language, new Object[]{str, len});
            } else {
                contents = PFactory.createTuple(language, new Object[0]);
            }
        }
        return PFactory.createTuple(language, new Object[]{clazz, contents, dict});
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    protected abstract static class ReduceNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object reduce(VirtualFrame frame, PByteArray self,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached SequenceStorageNodes.GetInternalByteArrayNode getBytes,
                        @Cached GetClassNode getClassNode,
                        @Cached PyObjectGetStateNode getStateNode,
                        @Cached TruffleStringBuilder.AppendCodePointNode appendCodePointNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            byte[] bytes = getBytes.execute(inliningTarget, self.getSequenceStorage());
            int len = self.getSequenceStorage().length();
            Object state = getStateNode.execute(frame, inliningTarget, self);
            Object clazz = getClassNode.execute(inliningTarget, self);
            return commonReduce(2, bytes, len, clazz, state, language, appendCodePointNode, toStringNode);
        }
    }

    @Slot(value = SlotKind.tp_richcompare, isComplex = true)
    @GenerateNodeFactory
    abstract static class RichCmpNode extends TpSlotRichCompare.RichCmpBuiltinNode {
        @Specialization
        static boolean cmp(PByteArray self, PBytesLike other, RichCmpOp op,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached GetInternalByteArrayNode getArray) {
            SequenceStorage selfStorage = self.getSequenceStorage();
            SequenceStorage otherStorage = other.getSequenceStorage();
            return compareByteArrays(op, getArray.execute(inliningTarget, selfStorage), selfStorage.length(), getArray.execute(inliningTarget, otherStorage), otherStorage.length());
        }

        @Specialization(guards = {"check.execute(inliningTarget, self)", "acquireLib.hasBuffer(other)"}, limit = "3")
        @InliningCutoff
        static Object cmp(VirtualFrame frame, Object self, Object other, RichCmpOp op,
                        @Bind Node inliningTarget,
                        @Cached("createFor($node)") IndirectCallData indirectCallData,
                        @SuppressWarnings("unused") @Exclusive @Cached PyByteArrayCheckNode check,
                        @Cached GetBytesStorage getBytesStorage,
                        @Exclusive @Cached GetInternalByteArrayNode getArray,
                        @CachedLibrary("other") PythonBufferAcquireLibrary acquireLib,
                        @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib) {
            SequenceStorage selfStorage = getBytesStorage.execute(inliningTarget, self);
            Object otherBuffer = acquireLib.acquireReadonly(other, frame, indirectCallData);
            try {
                return compareByteArrays(op, getArray.execute(inliningTarget, selfStorage), selfStorage.length(),
                                bufferLib.getInternalOrCopiedByteArray(otherBuffer), bufferLib.getBufferLength(otherBuffer));
            } finally {
                bufferLib.release(otherBuffer);
            }
        }

        @Specialization(guards = {"check.execute(inliningTarget, self)", "!acquireLib.hasBuffer(other)"})
        @SuppressWarnings("unused")
        static Object cmp(VirtualFrame frame, Object self, Object other, RichCmpOp op,
                        @Bind Node inliningTarget,
                        @Shared @Cached PyByteArrayCheckNode check,
                        @CachedLibrary(limit = "3") PythonBufferAcquireLibrary acquireLib) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        @Specialization(guards = "!check.execute(inliningTarget, self)")
        @InliningCutoff
        @SuppressWarnings("unused")
        static Object error(VirtualFrame frame, Object self, Object other, RichCmpOp op,
                        @Bind Node inliningTarget,
                        @Shared @Cached PyByteArrayCheckNode check) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.DESCRIPTOR_S_REQUIRES_S_OBJ_RECEIVED_P, op.getPythonName(), J_BYTEARRAY, self);
        }
    }
}
