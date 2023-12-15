/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates.
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

import static com.oracle.graal.python.nodes.BuiltinNames.J_APPEND;
import static com.oracle.graal.python.nodes.BuiltinNames.J_BYTEARRAY;
import static com.oracle.graal.python.nodes.BuiltinNames.J_EXTEND;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DICT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___DELITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___IADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___IMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___LE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___LT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___HASH__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___INIT__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.builtins.objects.bytes.BytesBuiltins.BytesLikeNoGeneralizationNode;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes.ComparisonOp;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes.FindNode;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes.GetBytesStorage;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes.HexStringToBytesNode;
import com.oracle.graal.python.builtins.objects.common.IndexNodes;
import com.oracle.graal.python.builtins.objects.common.IndexNodes.NormalizeIndexNode;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetInternalByteArrayNode;
import com.oracle.graal.python.builtins.objects.iterator.IteratorNodes;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.slice.PSlice.SliceInfo;
import com.oracle.graal.python.builtins.objects.slice.SliceNodes;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsSameTypeNode;
import com.oracle.graal.python.lib.PyByteArrayCheckNode;
import com.oracle.graal.python.lib.PyIndexCheckNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PySliceNew;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.builtins.ListNodes;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CastToByteNode;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PByteArray)
public final class ByteArrayBuiltins extends PythonBuiltins {

    private static final TruffleString T_LATIN_1 = tsLiteral("latin-1");

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ByteArrayBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
        addBuiltinConstant(SpecialAttributeNames.T___DOC__, //
                        "bytearray(iterable_of_ints) -> bytearray\n" + //
                                        "bytearray(string, encoding[, errors]) -> bytearray\n" + //
                                        "bytearray(bytes_or_buffer) -> mutable copy of bytes_or_buffer\n" + //
                                        "bytearray(int) -> bytes array of size given by the parameter " + //
                                        "initialized with null bytes\n" + //
                                        "bytearray() -> empty bytes array\n" + //
                                        "\n" + //
                                        "Construct a mutable bytearray object from:\n" + //
                                        "  - an iterable yielding integers in range(256)\n" + //
                                        "  - a text string encoded using the specified encoding\n" + //
                                        "  - a bytes or a buffer object\n" + //
                                        "  - any object implementing the buffer API.\n" + //
                                        "  - an integer");
        addBuiltinConstant(T___HASH__, PNone.NONE);
    }

    // bytearray([source[, encoding[, errors]]])
    @Builtin(name = J___INIT__, minNumOfPositionalArgs = 1, parameterNames = {"$self", "source", "encoding", "errors"})
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
                        @Bind("this") Node inliningTarget,
                        @Cached BytesNodes.BytesInitNode toBytesNode) {
            self.setSequenceStorage(new ByteSequenceStorage(toBytesNode.execute(frame, inliningTarget, source, encoding, errors)));
            return PNone.NONE;
        }

        @Specialization(guards = "isNone(self)")
        static PNone doInit(@SuppressWarnings("unused") PByteArray self, Object source, @SuppressWarnings("unused") Object encoding, @SuppressWarnings("unused") Object errors,
                        @Shared @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.CANNOT_CONVERT_P_OBJ_TO_S, source, "bytearray");
        }

        @Specialization(guards = "!isBytes(self)")
        static PNone doInit(Object self, @SuppressWarnings("unused") Object source, @SuppressWarnings("unused") Object encoding, @SuppressWarnings("unused") Object errors,
                        @Shared @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.DESCRIPTOR_S_REQUIRES_S_OBJ_RECEIVED_P, T___INIT__, "bytearray", self);
        }
    }

    @Builtin(name = J___GETITEM__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GetitemNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isPSlice(key) || indexCheckNode.execute(inliningTarget, key)", limit = "1")
        static Object doSlice(VirtualFrame frame, PBytesLike self, Object key,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached PyIndexCheckNode indexCheckNode,
                        @Cached("createGetItem()") SequenceStorageNodes.GetItemNode getSequenceItemNode) {
            return getSequenceItemNode.execute(frame, self.getSequenceStorage(), key);
        }

        @SuppressWarnings("unused")
        @Fallback
        static Object doSlice(VirtualFrame frame, Object self, Object key,
                        @Cached PRaiseNode raiseNode) {
            return raiseNode.raise(TypeError, ErrorMessages.OBJ_INDEX_MUST_BE_INT_OR_SLICES, "bytearray", key);
        }

        @NeverDefault
        protected static SequenceStorageNodes.GetItemNode createGetItem() {
            return SequenceStorageNodes.GetItemNode.create(IndexNodes.NormalizeIndexNode.create(), (s, f) -> f.createByteArray(s));
        }
    }

    @Builtin(name = J___SETITEM__, minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    @ImportStatic(SpecialMethodNames.class)
    abstract static class SetItemNode extends PythonTernaryBuiltinNode {

        @Specialization(guards = {"!isPSlice(idx)", "indexCheckNode.execute(inliningTarget, idx)"}, limit = "1")
        static PNone doItem(VirtualFrame frame, PByteArray self, Object idx, Object value,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached PyIndexCheckNode indexCheckNode,
                        @Cached("createSetItem()") SequenceStorageNodes.SetItemNode setItemNode) {
            setItemNode.execute(frame, self.getSequenceStorage(), idx, value);
            return PNone.NONE;
        }

        @Specialization
        static PNone doSliceSequence(VirtualFrame frame, PByteArray self, PSlice slice, PSequence value,
                        @Bind("this") Node inliningTarget,
                        @Cached @Shared InlinedConditionProfile differentLenProfile,
                        @Cached @Shared SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Cached @Shared SequenceStorageNodes.SetItemSliceNode setItemSliceNode,
                        @Cached @Shared SliceNodes.CoerceToIntSlice sliceCast,
                        @Cached @Shared SliceNodes.SliceUnpack unpack,
                        @Cached @Shared SliceNodes.AdjustIndices adjustIndices,
                        @Cached @Shared PRaiseNode.Lazy raiseNode) {
            SequenceStorage storage = self.getSequenceStorage();
            int otherLen = getSequenceStorageNode.execute(inliningTarget, value).length();
            SliceInfo unadjusted = unpack.execute(inliningTarget, sliceCast.execute(inliningTarget, slice));
            SliceInfo info = adjustIndices.execute(inliningTarget, storage.length(), unadjusted);
            if (differentLenProfile.profile(inliningTarget, info.sliceLength != otherLen)) {
                self.checkCanResize(inliningTarget, raiseNode);
            }
            setItemSliceNode.execute(frame, inliningTarget, storage, info, value, false);
            return PNone.NONE;
        }

        @Specialization(guards = "bufferAcquireLib.hasBuffer(value)", limit = "3")
        static PNone doSliceBuffer(VirtualFrame frame, PByteArray self, PSlice slice, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @CachedLibrary("value") PythonBufferAcquireLibrary bufferAcquireLib,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib,
                        @Cached @Shared InlinedConditionProfile differentLenProfile,
                        @Cached @Shared SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Cached @Shared SequenceStorageNodes.SetItemSliceNode setItemSliceNode,
                        @Cached @Shared SliceNodes.CoerceToIntSlice sliceCast,
                        @Cached @Shared SliceNodes.SliceUnpack unpack,
                        @Cached @Shared SliceNodes.AdjustIndices adjustIndices,
                        @Cached PythonObjectFactory factory,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            Object buffer = bufferAcquireLib.acquireReadonly(value, frame, indirectCallData);
            try {
                // TODO avoid copying if possible. Note that it is possible that value is self
                PBytes bytes = factory.createBytes(bufferLib.getCopiedByteArray(value));
                return doSliceSequence(frame, self, slice, bytes, inliningTarget, differentLenProfile, getSequenceStorageNode, setItemSliceNode, sliceCast, unpack, adjustIndices, raiseNode);
            } finally {
                bufferLib.release(buffer, frame, indirectCallData);
            }
        }

        @Specialization(replaces = {"doSliceSequence", "doSliceBuffer"})
        static PNone doSliceGeneric(VirtualFrame frame, PByteArray self, PSlice slice, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached @Shared InlinedConditionProfile differentLenProfile,
                        @Cached @Shared SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Cached @Shared SequenceStorageNodes.SetItemSliceNode setItemSliceNode,
                        @Cached @Shared SliceNodes.CoerceToIntSlice sliceCast,
                        @Cached @Shared SliceNodes.SliceUnpack unpack,
                        @Cached @Shared SliceNodes.AdjustIndices adjustIndices,
                        @Cached ListNodes.ConstructListNode constructListNode,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            PList values = constructListNode.execute(frame, value);
            return doSliceSequence(frame, self, slice, values, inliningTarget, differentLenProfile, getSequenceStorageNode, setItemSliceNode, sliceCast, unpack, adjustIndices, raiseNode);
        }

        @Fallback
        @SuppressWarnings("unused")
        static Object error(Object self, Object idx, Object value,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.OBJ_INDEX_MUST_BE_INT_OR_SLICES, "bytearray", idx);
        }

        @NeverDefault
        protected static SequenceStorageNodes.SetItemNode createSetItem() {
            // Note the error message should never be reached, because the storage should always be
            // writeable and so SetItemScalarNode should always have a specialization for it and
            // inside that specialization the conversion of RHS may fail and produce Python level
            // ValueError
            return SequenceStorageNodes.SetItemNode.create(NormalizeIndexNode.forBytearray(), ErrorMessages.INTEGER_REQUIRED);
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
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached CastToByteNode toByteNode,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            self.checkCanResize(inliningTarget, raiseNode);
            byte v = toByteNode.execute(frame, value);
            ByteSequenceStorage target = (ByteSequenceStorage) self.getSequenceStorage();
            target.insertByteItem(normalizeIndex(index, target.length()), v);
            return PNone.NONE;
        }

        @Specialization
        static PNone insert(VirtualFrame frame, PByteArray self, int index, int value,
                        @Bind("this") Node inliningTarget,
                        @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Cached SequenceStorageNodes.InsertItemNode insertItemNode,
                        @Shared @Cached CastToByteNode toByteNode,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
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

    @Builtin(name = J___REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object repr(PByteArray self,
                        @Bind("this") Node inliningTarget,
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

    @Builtin(name = J___IADD__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class IAddNode extends PythonBinaryBuiltinNode {
        @Specialization
        static PByteArray add(PByteArray self, PBytesLike other,
                        @Bind("this") Node inliningTarget,
                        @Cached @Shared SequenceStorageNodes.ConcatNode concatNode,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            self.checkCanResize(inliningTarget, raiseNode);
            SequenceStorage res = concatNode.execute(self.getSequenceStorage(), other.getSequenceStorage());
            updateSequenceStorage(self, res);
            return self;
        }

        @Specialization(guards = "!isBytes(other)", limit = "3")
        static PByteArray add(VirtualFrame frame, PByteArray self, Object other,
                        @Bind("this") Node inliningTarget,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @CachedLibrary("other") PythonBufferAcquireLibrary bufferAcquireLib,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib,
                        @Cached @Shared SequenceStorageNodes.ConcatNode concatNode,
                        @Cached PythonObjectFactory factory,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            Object buffer;
            try {
                buffer = bufferAcquireLib.acquireReadonly(other, frame, indirectCallData);
            } catch (PException e) {
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.CANT_CONCAT_P_TO_S, other, "bytearray");
            }
            try {
                self.checkCanResize(inliningTarget, raiseNode);
                // TODO avoid copying
                PBytes bytes = factory.createBytes(bufferLib.getCopiedByteArray(buffer));
                SequenceStorage res = concatNode.execute(self.getSequenceStorage(), bytes.getSequenceStorage());
                updateSequenceStorage(self, res);
                return self;
            } finally {
                bufferLib.release(buffer, frame, indirectCallData);
            }
        }

        private static void updateSequenceStorage(PByteArray array, SequenceStorage s) {
            if (array.getSequenceStorage() != s) {
                array.setSequenceStorage(s);
            }
        }
    }

    @Builtin(name = J___IMUL__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class IMulNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object mul(VirtualFrame frame, PByteArray self, int times,
                        @Bind("this") Node inliningTarget,
                        @Cached @Shared SequenceStorageNodes.RepeatNode repeatNode,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            self.checkCanResize(inliningTarget, raiseNode);
            SequenceStorage res = repeatNode.execute(frame, self.getSequenceStorage(), times);
            self.setSequenceStorage(res);
            return self;
        }

        @Specialization
        static Object mul(VirtualFrame frame, PByteArray self, Object times,
                        @Bind("this") Node inliningTarget,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached @Shared SequenceStorageNodes.RepeatNode repeatNode,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            self.checkCanResize(inliningTarget, raiseNode);
            SequenceStorage res = repeatNode.execute(frame, self.getSequenceStorage(), asSizeNode.executeExact(frame, inliningTarget, times));
            self.setSequenceStorage(res);
            return self;
        }

        @SuppressWarnings("unused")
        @Fallback
        static Object mul(Object self, Object other,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.CANT_MULTIPLY_SEQ_BY_NON_INT, other);
        }
    }

    @Builtin(name = "remove", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class RemoveNode extends PythonBinaryBuiltinNode {

        @Specialization
        static PNone remove(VirtualFrame frame, PByteArray self, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached("createCast()") CastToByteNode cast,
                        @Cached SequenceStorageNodes.GetInternalByteArrayNode getBytes,
                        @Cached SequenceStorageNodes.DeleteNode deleteNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            self.checkCanResize(inliningTarget, raiseNode);
            SequenceStorage storage = self.getSequenceStorage();
            int len = storage.length();
            int pos = FindNode.find(getBytes.execute(inliningTarget, self.getSequenceStorage()), len, cast.execute(frame, value), 0, len, false);
            if (pos != -1) {
                deleteNode.execute(frame, storage, pos);
                return PNone.NONE;
            }
            throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.NOT_IN_BYTEARRAY);
        }

        @NeverDefault
        static CastToByteNode createCast() {
            return CastToByteNode.create((val, raiseNode) -> {
                throw raiseNode.raise(ValueError, ErrorMessages.BYTE_MUST_BE_IN_RANGE);
            }, (val, raiseNode) -> {
                throw raiseNode.raise(TypeError, ErrorMessages.OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER, "bytes");
            });
        }

        @Fallback
        static Object doError(@SuppressWarnings("unused") Object self, Object arg,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER, arg);
        }
    }

    @Builtin(name = "pop", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class PopNode extends PythonBuiltinNode {

        @Specialization
        static Object popLast(VirtualFrame frame, PByteArray self, @SuppressWarnings("unused") PNone none,
                        @Bind("this") Node inliningTarget,
                        @Shared("getItem") @Cached SequenceStorageNodes.GetItemNode getItemNode,
                        @Shared @Cached("createDelete()") SequenceStorageNodes.DeleteNode deleteNode,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            self.checkCanResize(inliningTarget, raiseNode);
            SequenceStorage store = self.getSequenceStorage();
            Object ret = getItemNode.execute(store, -1);
            deleteNode.execute(frame, store, -1);
            return ret;
        }

        @Specialization(guards = {"!isNoValue(idx)", "!isPSlice(idx)"})
        static Object doIndex(VirtualFrame frame, PByteArray self, Object idx,
                        @Bind("this") Node inliningTarget,
                        @Shared("getItem") @Cached SequenceStorageNodes.GetItemNode getItemNode,
                        @Shared @Cached("createDelete()") SequenceStorageNodes.DeleteNode deleteNode,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            self.checkCanResize(inliningTarget, raiseNode);
            SequenceStorage store = self.getSequenceStorage();
            Object ret = getItemNode.execute(frame, store, idx);
            deleteNode.execute(frame, store, idx);
            return ret;
        }

        @Fallback
        static Object doError(@SuppressWarnings("unused") Object self, Object arg,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER, arg);
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

    @Builtin(name = J___DELITEM__, minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class DelItemNode extends PythonBinaryBuiltinNode {
        @Specialization
        static PNone doGeneric(VirtualFrame frame, PByteArray self, Object key,
                        @Bind("this") Node inliningTarget,
                        @Cached SequenceStorageNodes.DeleteNode deleteNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            self.checkCanResize(inliningTarget, raiseNode);
            deleteNode.execute(frame, self.getSequenceStorage(), key);
            return PNone.NONE;
        }

        @SuppressWarnings("unused")
        @Fallback
        static Object doGeneric(Object self, Object idx,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.DESCRIPTOR_S_REQUIRES_S_OBJ_RECEIVED_P, "__delitem__", "bytearray", idx);
        }
    }

    @Builtin(name = J_APPEND, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class AppendNode extends PythonBinaryBuiltinNode {

        @Specialization
        static PNone append(VirtualFrame frame, PByteArray byteArray, Object arg,
                        @Bind("this") Node inliningTarget,
                        @Cached("createCast()") CastToByteNode toByteNode,
                        @Cached SequenceStorageNodes.AppendNode appendNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            byteArray.checkCanResize(inliningTarget, raiseNode);
            appendNode.execute(inliningTarget, byteArray.getSequenceStorage(), toByteNode.execute(frame, arg), BytesLikeNoGeneralizationNode.SUPPLIER);
            return PNone.NONE;
        }

        @NeverDefault
        static CastToByteNode createCast() {
            return CastToByteNode.create((val, raiseNode) -> {
                throw raiseNode.raise(ValueError, ErrorMessages.BYTE_MUST_BE_IN_RANGE);
            }, (val, raiseNode) -> {
                throw raiseNode.raise(TypeError, ErrorMessages.OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER, "bytes");
            });
        }
    }

    // bytearray.extend(L)
    @Builtin(name = J_EXTEND, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ExtendNode extends PythonBinaryBuiltinNode {

        @Specialization
        static PNone doBytes(VirtualFrame frame, PByteArray self, PBytesLike source,
                        @Bind("this") Node inliningTarget,
                        @Cached IteratorNodes.GetLength lenNode,
                        @Cached("createExtend()") @Shared SequenceStorageNodes.ExtendNode extendNode,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            self.checkCanResize(inliningTarget, raiseNode);
            int len = lenNode.execute(frame, inliningTarget, source);
            extend(frame, self, source, len, extendNode);
            return PNone.NONE;
        }

        @Specialization(guards = "!isBytes(source)", limit = "3")
        static PNone doGeneric(VirtualFrame frame, PByteArray self, Object source,
                        @Bind("this") Node inliningTarget,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @CachedLibrary("source") PythonBufferAcquireLibrary bufferAcquireLib,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib,
                        @Cached InlinedConditionProfile bufferProfile,
                        @Cached BytesNodes.IterableToByteNode iterableToByteNode,
                        @Cached IsBuiltinObjectProfile errorProfile,
                        @Cached("createExtend()") @Shared SequenceStorageNodes.ExtendNode extendNode,
                        @Cached PythonObjectFactory factory,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
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
                    throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.CANT_EXTEND_BYTEARRAY_WITH_P, source);
                }
            }
            PByteArray bytes = factory.createByteArray(b);
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
            return SequenceStorageNodes.ExtendNode.create(BytesLikeNoGeneralizationNode.SUPPLIER);
        }
    }

    // bytearray.copy()
    @Builtin(name = "copy", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class CopyNode extends PythonBuiltinNode {
        @Specialization
        static PByteArray copy(PByteArray byteArray,
                        @Bind("this") Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached SequenceStorageNodes.ToByteArrayNode toByteArray,
                        @Cached PythonObjectFactory factory) {
            return factory.createByteArray(getClassNode.execute(inliningTarget, byteArray), toByteArray.execute(inliningTarget, byteArray.getSequenceStorage()));
        }
    }

    // bytearray.reverse()
    @Builtin(name = "reverse", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReverseNode extends PythonBuiltinNode {

        @Specialization
        static PNone reverse(PByteArray byteArray) {
            byteArray.reverse();
            return PNone.NONE;
        }
    }

    // bytearray.clear()
    @Builtin(name = "clear", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ClearNode extends PythonUnaryBuiltinNode {

        @Specialization
        static PNone clear(VirtualFrame frame, PByteArray byteArray,
                        @Bind("this") Node inliningTarget,
                        @Cached SequenceStorageNodes.DeleteNode deleteNode,
                        @Cached PySliceNew sliceNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
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

        @Specialization(guards = "isBuiltinBytesType(inliningTarget, cls, isSameType)")
        static PByteArray doBytes(Object cls, TruffleString str,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared("isSameType") @Cached IsSameTypeNode isSameType,
                        @Shared("hexToBytes") @Cached HexStringToBytesNode hexStringToBytesNode,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createByteArray(cls, hexStringToBytesNode.execute(str));
        }

        @Specialization(guards = "!isBuiltinBytesType(inliningTarget, cls, isSameType)")
        static Object doGeneric(VirtualFrame frame, Object cls, TruffleString str,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared("isSameType") @Cached IsSameTypeNode isSameType,
                        @Cached CallNode callNode,
                        @Shared("hexToBytes") @Cached HexStringToBytesNode hexStringToBytesNode,
                        @Shared @Cached PythonObjectFactory factory) {
            PByteArray byteArray = factory.createByteArray(hexStringToBytesNode.execute(str));
            return callNode.execute(frame, cls, byteArray);
        }

        protected static boolean isBuiltinBytesType(Node inliningTarget, Object cls, IsSameTypeNode isSameTypeNode) {
            return isSameTypeNode.execute(inliningTarget, PythonBuiltinClassType.PBytes, cls);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ByteArrayBuiltinsClinicProviders.FromHexNodeClinicProviderGen.INSTANCE;
        }
    }

    // bytearray.translate(table, delete=b'')
    @Builtin(name = "translate", minNumOfPositionalArgs = 2, parameterNames = {"self", "table", "delete"})
    @GenerateNodeFactory
    public abstract static class TranslateNode extends BytesBuiltins.BaseTranslateNode {

        @Specialization(guards = "isNoValue(delete)")
        static PByteArray translate(PByteArray self, @SuppressWarnings("unused") PNone table, @SuppressWarnings("unused") PNone delete,
                        @Shared("toBytes") @Cached BytesNodes.ToBytesNode toBytesNode,
                        @Shared @Cached PythonObjectFactory factory) {
            byte[] content = toBytesNode.execute(self);
            return factory.createByteArray(content);
        }

        @Specialization(guards = "!isNone(table)")
        static PByteArray translate(VirtualFrame frame, PByteArray self, Object table, @SuppressWarnings("unused") PNone delete,
                        @Bind("this") Node inliningTarget,
                        @Shared("profile") @Cached InlinedConditionProfile isLenTable256Profile,
                        @Shared("toBytes") @Cached BytesNodes.ToBytesNode toBytesNode,
                        @Shared @Cached PythonObjectFactory factory,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            byte[] bTable = toBytesNode.execute(frame, table);
            checkLengthOfTable(inliningTarget, bTable, isLenTable256Profile, raiseNode);
            byte[] bSelf = toBytesNode.execute(self);

            Result result = translate(bSelf, bTable);
            return factory.createByteArray(result.array);
        }

        @Specialization(guards = "isNone(table)")
        static PByteArray delete(VirtualFrame frame, PByteArray self, @SuppressWarnings("unused") PNone table, Object delete,
                        @Shared("toBytes") @Cached BytesNodes.ToBytesNode toBytesNode,
                        @Shared @Cached PythonObjectFactory factory) {
            byte[] bSelf = toBytesNode.execute(self);
            byte[] bDelete = toBytesNode.execute(frame, delete);

            Result result = delete(bSelf, bDelete);
            return factory.createByteArray(result.array);
        }

        @Specialization(guards = {"!isPNone(table)", "!isPNone(delete)"})
        static PByteArray translateAndDelete(VirtualFrame frame, PByteArray self, Object table, Object delete,
                        @Bind("this") Node inliningTarget,
                        @Shared("profile") @Cached InlinedConditionProfile isLenTable256Profile,
                        @Shared("toBytes") @Cached BytesNodes.ToBytesNode toBytesNode,
                        @Shared @Cached PythonObjectFactory factory,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            byte[] bTable = toBytesNode.execute(frame, table);
            checkLengthOfTable(inliningTarget, bTable, isLenTable256Profile, raiseNode);
            byte[] bDelete = toBytesNode.execute(frame, delete);
            byte[] bSelf = toBytesNode.execute(self);

            Result result = translateAndDelete(bSelf, bTable, bDelete);
            return factory.createByteArray(result.array);
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

    protected static Object commonReduce(int proto, byte[] bytes, int len, Object clazz, Object dict,
                    PythonObjectFactory factory, TruffleStringBuilder.AppendCodePointNode appendCodePointNode, TruffleStringBuilder.ToStringNode toStringNode) {
        TruffleStringBuilder sb = TruffleStringBuilder.create(TS_ENCODING);
        BytesUtils.repr(sb, bytes, len, appendCodePointNode);
        TruffleString str = toStringNode.execute(sb);
        Object contents;
        if (proto < 3) {
            contents = factory.createTuple(new Object[]{str, T_LATIN_1});
        } else {
            if (len > 0) {
                contents = factory.createTuple(new Object[]{str, len});
            } else {
                contents = factory.createTuple(new Object[0]);
            }
        }
        return factory.createTuple(new Object[]{clazz, contents, dict});
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    protected abstract static class BaseReduceNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object reduce(VirtualFrame frame, PByteArray self,
                        @Bind("this") Node inliningTarget,
                        @Cached SequenceStorageNodes.GetInternalByteArrayNode getBytes,
                        @Cached GetClassNode getClassNode,
                        @Cached PyObjectLookupAttr lookupDict,
                        @Cached TruffleStringBuilder.AppendCodePointNode appendCodePointNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode,
                        @Cached PythonObjectFactory factory) {
            byte[] bytes = getBytes.execute(inliningTarget, self.getSequenceStorage());
            int len = self.getSequenceStorage().length();
            Object dict = lookupDict.execute(frame, inliningTarget, self, T___DICT__);
            if (dict == PNone.NO_VALUE) {
                dict = PNone.NONE;
            }
            Object clazz = getClassNode.execute(inliningTarget, self);
            return commonReduce(2, bytes, len, clazz, dict, factory, appendCodePointNode, toStringNode);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    abstract static class ComparisonHelperNode extends Node {

        abstract Object execute(VirtualFrame frame, Node inliningTarget, Object self, Object other, ComparisonOp op);

        @Specialization
        static boolean cmp(Node inliningTarget, PByteArray self, PBytesLike other, ComparisonOp op,
                        @Exclusive @Cached GetInternalByteArrayNode getArray) {
            SequenceStorage selfStorage = self.getSequenceStorage();
            SequenceStorage otherStorage = other.getSequenceStorage();
            return op.doCmp(getArray.execute(inliningTarget, selfStorage), selfStorage.length(), getArray.execute(inliningTarget, otherStorage), otherStorage.length());
        }

        @Specialization(guards = {"check.execute(inliningTarget, self)", "acquireLib.hasBuffer(other)"}, limit = "3")
        static Object cmp(VirtualFrame frame, Node inliningTarget, Object self, Object other, ComparisonOp op,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @SuppressWarnings("unused") @Exclusive @Cached PyByteArrayCheckNode check,
                        @Cached GetBytesStorage getBytesStorage,
                        @Exclusive @Cached GetInternalByteArrayNode getArray,
                        @CachedLibrary("other") PythonBufferAcquireLibrary acquireLib,
                        @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib) {
            SequenceStorage selfStorage = getBytesStorage.execute(inliningTarget, self);
            Object otherBuffer = acquireLib.acquireReadonly(other, frame, indirectCallData);
            try {
                return op.doCmp(getArray.execute(inliningTarget, selfStorage), selfStorage.length(),
                                bufferLib.getInternalOrCopiedByteArray(otherBuffer), bufferLib.getBufferLength(otherBuffer));
            } finally {
                bufferLib.release(otherBuffer);
            }
        }

        @Specialization(guards = {"check.execute(inliningTarget, self)", "!acquireLib.hasBuffer(other)"})
        @SuppressWarnings("unused")
        static Object cmp(VirtualFrame frame, Node inliningTarget, Object self, Object other, ComparisonOp op,
                        @Shared @Cached PyByteArrayCheckNode check,
                        @CachedLibrary(limit = "3") PythonBufferAcquireLibrary acquireLib) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        @Specialization(guards = "!check.execute(inliningTarget, self)")
        @SuppressWarnings("unused")
        static Object error(VirtualFrame frame, Node inliningTarget, Object self, Object other, ComparisonOp op,
                        @Shared @Cached PyByteArrayCheckNode check,
                        @Cached(inline = false) PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.DESCRIPTOR_S_REQUIRES_S_OBJ_RECEIVED_P, op.name, J_BYTEARRAY, self);
        }
    }

    @Builtin(name = J___EQ__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class EqNode extends PythonBinaryBuiltinNode {

        @Specialization
        static Object cmp(VirtualFrame frame, Object self, Object other,
                        @Bind("this") Node inliningTarget,
                        @Cached ComparisonHelperNode helperNode) {
            return helperNode.execute(frame, inliningTarget, self, other, ComparisonOp.EQ);
        }
    }

    @Builtin(name = J___NE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class NeNode extends PythonBinaryBuiltinNode {

        @Specialization
        static Object cmp(VirtualFrame frame, Object self, Object other,
                        @Bind("this") Node inliningTarget,
                        @Cached ComparisonHelperNode helperNode) {
            return helperNode.execute(frame, inliningTarget, self, other, ComparisonOp.NE);
        }
    }

    @Builtin(name = J___LT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class LtNode extends PythonBinaryBuiltinNode {

        @Specialization
        static Object cmp(VirtualFrame frame, Object self, Object other,
                        @Bind("this") Node inliningTarget,
                        @Cached ComparisonHelperNode helperNode) {
            return helperNode.execute(frame, inliningTarget, self, other, ComparisonOp.LT);
        }
    }

    @Builtin(name = J___LE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class LeNode extends PythonBinaryBuiltinNode {

        @Specialization
        static Object cmp(VirtualFrame frame, Object self, Object other,
                        @Bind("this") Node inliningTarget,
                        @Cached ComparisonHelperNode helperNode) {
            return helperNode.execute(frame, inliningTarget, self, other, ComparisonOp.LE);
        }
    }

    @Builtin(name = J___GT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GtNode extends PythonBinaryBuiltinNode {

        @Specialization
        static Object cmp(VirtualFrame frame, Object self, Object other,
                        @Bind("this") Node inliningTarget,
                        @Cached ComparisonHelperNode helperNode) {
            return helperNode.execute(frame, inliningTarget, self, other, ComparisonOp.GT);
        }
    }

    @Builtin(name = J___GE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GeNode extends PythonBinaryBuiltinNode {

        @Specialization
        static Object cmp(VirtualFrame frame, Object self, Object other,
                        @Bind("this") Node inliningTarget,
                        @Cached ComparisonHelperNode helperNode) {
            return helperNode.execute(frame, inliningTarget, self, other, ComparisonOp.GE);
        }
    }

}
