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
import com.oracle.graal.python.builtins.objects.type.TypeNodes.InlinedIsSameTypeNode;
import com.oracle.graal.python.lib.PyByteArrayCheckNode;
import com.oracle.graal.python.lib.PyIndexCheckNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PySliceNew;
import com.oracle.graal.python.nodes.ErrorMessages;
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
import com.oracle.graal.python.nodes.object.InlinedGetClassNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CastToByteNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
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
public class ByteArrayBuiltins extends PythonBuiltins {

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
        public PNone doInit(VirtualFrame frame, PByteArray self, Object source, Object encoding, Object errors,
                        @Bind("this") Node inliningTarget,
                        @Cached BytesNodes.BytesInitNode toBytesNode) {
            self.setSequenceStorage(new ByteSequenceStorage(toBytesNode.execute(frame, inliningTarget, source, encoding, errors)));
            return PNone.NONE;
        }

        @Specialization(guards = "isNone(self)")
        public PNone doInit(@SuppressWarnings("unused") PByteArray self, Object source, @SuppressWarnings("unused") Object encoding, @SuppressWarnings("unused") Object errors) {
            throw raise(TypeError, ErrorMessages.CANNOT_CONVERT_P_OBJ_TO_S, source, "bytearray");
        }

        @Specialization(guards = "!isBytes(self)")
        public PNone doInit(Object self, @SuppressWarnings("unused") Object source, @SuppressWarnings("unused") Object encoding, @SuppressWarnings("unused") Object errors) {
            throw raise(TypeError, ErrorMessages.DESCRIPTOR_S_REQUIRES_S_OBJ_RECEIVED_P, T___INIT__, "bytearray", self);
        }
    }

    @Builtin(name = J___GETITEM__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GetitemNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isPSlice(key) || indexCheckNode.execute(key)", limit = "1")
        static Object doSlice(VirtualFrame frame, PBytesLike self, Object key,
                        @SuppressWarnings("unused") @Cached PyIndexCheckNode indexCheckNode,
                        @Cached("createGetItem()") SequenceStorageNodes.GetItemNode getSequenceItemNode) {
            return getSequenceItemNode.execute(frame, self.getSequenceStorage(), key);
        }

        @SuppressWarnings("unused")
        @Fallback
        Object doSlice(VirtualFrame frame, Object self, Object key) {
            return raise(TypeError, ErrorMessages.OBJ_INDEX_MUST_BE_INT_OR_SLICES, "bytearray", key);
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

        @Specialization(guards = {"!isPSlice(idx)", "indexCheckNode.execute(idx)"}, limit = "1")
        static PNone doItem(VirtualFrame frame, PByteArray self, Object idx, Object value,
                        @SuppressWarnings("unused") @Cached PyIndexCheckNode indexCheckNode,
                        @Cached("createSetItem()") SequenceStorageNodes.SetItemNode setItemNode) {
            setItemNode.execute(frame, self.getSequenceStorage(), idx, value);
            return PNone.NONE;
        }

        @Specialization
        PNone doSliceSequence(VirtualFrame frame, PByteArray self, PSlice slice, PSequence value,
                        @Bind("this") Node inliningTarget,
                        @Cached @Shared InlinedConditionProfile differentLenProfile,
                        @Cached @Shared SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Cached @Shared SequenceStorageNodes.SetItemSliceNode setItemSliceNode,
                        @Cached @Shared SliceNodes.CoerceToIntSlice sliceCast,
                        @Cached @Shared SliceNodes.SliceUnpack unpack,
                        @Cached @Shared SliceNodes.AdjustIndices adjustIndices) {
            SequenceStorage storage = self.getSequenceStorage();
            int otherLen = getSequenceStorageNode.execute(value).length();
            SliceInfo unadjusted = unpack.execute(sliceCast.execute(slice));
            SliceInfo info = adjustIndices.execute(storage.length(), unadjusted);
            if (differentLenProfile.profile(inliningTarget, info.sliceLength != otherLen)) {
                self.checkCanResize(this);
            }
            setItemSliceNode.execute(frame, storage, info, value, false);
            return PNone.NONE;
        }

        @Specialization(guards = "bufferAcquireLib.hasBuffer(value)", limit = "3")
        @SuppressWarnings("truffle-static-method")
        PNone doSliceBuffer(VirtualFrame frame, PByteArray self, PSlice slice, Object value,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary("value") PythonBufferAcquireLibrary bufferAcquireLib,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib,
                        @Cached @Shared InlinedConditionProfile differentLenProfile,
                        @Cached @Shared SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Cached @Shared SequenceStorageNodes.SetItemSliceNode setItemSliceNode,
                        @Cached @Shared SliceNodes.CoerceToIntSlice sliceCast,
                        @Cached @Shared SliceNodes.SliceUnpack unpack,
                        @Cached @Shared SliceNodes.AdjustIndices adjustIndices) {
            Object buffer = bufferAcquireLib.acquireReadonly(value, frame, this);
            try {
                // TODO avoid copying if possible. Note that it is possible that value is self
                PBytes bytes = factory().createBytes(bufferLib.getCopiedByteArray(value));
                return doSliceSequence(frame, self, slice, bytes, inliningTarget, differentLenProfile, getSequenceStorageNode, setItemSliceNode, sliceCast, unpack, adjustIndices);
            } finally {
                bufferLib.release(buffer, frame, this);
            }
        }

        @Specialization(replaces = {"doSliceSequence", "doSliceBuffer"})
        PNone doSliceGeneric(VirtualFrame frame, PByteArray self, PSlice slice, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached @Shared InlinedConditionProfile differentLenProfile,
                        @Cached @Shared SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Cached @Shared SequenceStorageNodes.SetItemSliceNode setItemSliceNode,
                        @Cached @Shared SliceNodes.CoerceToIntSlice sliceCast,
                        @Cached @Shared SliceNodes.SliceUnpack unpack,
                        @Cached @Shared SliceNodes.AdjustIndices adjustIndices,
                        @Cached ListNodes.ConstructListNode constructListNode) {
            PList values = constructListNode.execute(frame, value);
            return doSliceSequence(frame, self, slice, values, inliningTarget, differentLenProfile, getSequenceStorageNode, setItemSliceNode, sliceCast, unpack, adjustIndices);
        }

        @Fallback
        @SuppressWarnings("unused")
        Object error(Object self, Object idx, Object value) {
            throw raise(TypeError, ErrorMessages.OBJ_INDEX_MUST_BE_INT_OR_SLICES, "bytearray", idx);
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
    @SuppressWarnings("truffle-static-method")
    public abstract static class InsertNode extends PythonTernaryClinicBuiltinNode {

        public abstract PNone execute(VirtualFrame frame, PByteArray list, Object index, Object value);

        @Specialization(guards = "isByteStorage(self)")
        PNone insert(VirtualFrame frame, PByteArray self, int index, int value,
                        @Cached @Shared CastToByteNode toByteNode) {
            self.checkCanResize(this);
            byte v = toByteNode.execute(frame, value);
            ByteSequenceStorage target = (ByteSequenceStorage) self.getSequenceStorage();
            target.insertByteItem(normalizeIndex(index, target.length()), v);
            return PNone.NONE;
        }

        @Specialization
        PNone insert(VirtualFrame frame, PByteArray self, int index, int value,
                        @Bind("this") Node inliningTarget,
                        @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Cached SequenceStorageNodes.InsertItemNode insertItemNode,
                        @Cached @Shared CastToByteNode toByteNode) {
            self.checkCanResize(this);
            byte v = toByteNode.execute(frame, value);
            SequenceStorage storage = getSequenceStorageNode.execute(self);
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
                        @Cached InlinedGetClassNode getClassNode,
                        @Cached TruffleStringBuilder.AppendCodePointNode appendCodePointNode,
                        @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            SequenceStorage store = self.getSequenceStorage();
            byte[] bytes = getBytes.execute(store);
            int len = store.length();
            TruffleStringBuilder sb = TruffleStringBuilder.create(TS_ENCODING);
            TruffleString typeName = getNameNode.execute(getClassNode.execute(inliningTarget, self));
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
        PByteArray add(PByteArray self, PBytesLike other,
                        @Cached @Shared SequenceStorageNodes.ConcatNode concatNode) {
            self.checkCanResize(this);
            SequenceStorage res = concatNode.execute(self.getSequenceStorage(), other.getSequenceStorage());
            updateSequenceStorage(self, res);
            return self;
        }

        @Specialization(guards = "!isBytes(other)", limit = "3")
        PByteArray add(VirtualFrame frame, PByteArray self, Object other,
                        @CachedLibrary("other") PythonBufferAcquireLibrary bufferAcquireLib,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib,
                        @Cached @Shared SequenceStorageNodes.ConcatNode concatNode) {
            Object buffer;
            try {
                buffer = bufferAcquireLib.acquireReadonly(other, frame, this);
            } catch (PException e) {
                throw raise(TypeError, ErrorMessages.CANT_CONCAT_P_TO_S, other, "bytearray");
            }
            try {
                self.checkCanResize(this);
                // TODO avoid copying
                PBytes bytes = factory().createBytes(bufferLib.getCopiedByteArray(buffer));
                SequenceStorage res = concatNode.execute(self.getSequenceStorage(), bytes.getSequenceStorage());
                updateSequenceStorage(self, res);
                return self;
            } finally {
                bufferLib.release(buffer, frame, this);
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
        public Object mul(VirtualFrame frame, PByteArray self, int times,
                        @Cached @Shared SequenceStorageNodes.RepeatNode repeatNode) {
            self.checkCanResize(this);
            SequenceStorage res = repeatNode.execute(frame, self.getSequenceStorage(), times);
            self.setSequenceStorage(res);
            return self;
        }

        @Specialization
        public Object mul(VirtualFrame frame, PByteArray self, Object times,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached @Shared SequenceStorageNodes.RepeatNode repeatNode) {
            self.checkCanResize(this);
            SequenceStorage res = repeatNode.execute(frame, self.getSequenceStorage(), asSizeNode.executeExact(frame, times));
            self.setSequenceStorage(res);
            return self;
        }

        @SuppressWarnings("unused")
        @Fallback
        public Object mul(Object self, Object other) {
            throw raise(TypeError, ErrorMessages.CANT_MULTIPLY_SEQ_BY_NON_INT, other);
        }
    }

    @Builtin(name = "remove", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class RemoveNode extends PythonBinaryBuiltinNode {

        @Specialization
        PNone remove(VirtualFrame frame, PByteArray self, Object value,
                        @Cached("createCast()") CastToByteNode cast,
                        @Cached SequenceStorageNodes.GetInternalByteArrayNode getBytes,
                        @Cached SequenceStorageNodes.DeleteNode deleteNode) {
            self.checkCanResize(this);
            SequenceStorage storage = self.getSequenceStorage();
            int len = storage.length();
            int pos = FindNode.find(getBytes.execute(self.getSequenceStorage()), len, cast.execute(frame, value), 0, len, false);
            if (pos != -1) {
                deleteNode.execute(frame, storage, pos);
                return PNone.NONE;
            }
            throw raise(ValueError, ErrorMessages.NOT_IN_BYTEARRAY);
        }

        @NeverDefault
        protected CastToByteNode createCast() {
            return CastToByteNode.create(val -> {
                throw raise(ValueError, ErrorMessages.BYTE_MUST_BE_IN_RANGE);
            }, val -> {
                throw raise(TypeError, ErrorMessages.OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER, "bytes");
            });
        }

        @Fallback
        public Object doError(@SuppressWarnings("unused") Object self, Object arg) {
            throw raise(TypeError, ErrorMessages.OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER, arg);
        }
    }

    @Builtin(name = "pop", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class PopNode extends PythonBuiltinNode {

        @Specialization
        public Object popLast(VirtualFrame frame, PByteArray self, @SuppressWarnings("unused") PNone none,
                        @Cached.Shared("getItem") @Cached SequenceStorageNodes.GetItemNode getItemNode,
                        @Cached("createDelete()") @Shared SequenceStorageNodes.DeleteNode deleteNode) {
            self.checkCanResize(this);
            SequenceStorage store = self.getSequenceStorage();
            Object ret = getItemNode.execute(store, -1);
            deleteNode.execute(frame, store, -1);
            return ret;
        }

        @Specialization(guards = {"!isNoValue(idx)", "!isPSlice(idx)"})
        public Object doIndex(VirtualFrame frame, PByteArray self, Object idx,
                        @Cached.Shared("getItem") @Cached SequenceStorageNodes.GetItemNode getItemNode,
                        @Cached("createDelete()") @Shared SequenceStorageNodes.DeleteNode deleteNode) {
            self.checkCanResize(this);
            SequenceStorage store = self.getSequenceStorage();
            Object ret = getItemNode.execute(frame, store, idx);
            deleteNode.execute(frame, store, idx);
            return ret;
        }

        @Fallback
        public Object doError(@SuppressWarnings("unused") Object self, Object arg) {
            throw raise(TypeError, ErrorMessages.OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER, arg);
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
        protected PNone doGeneric(VirtualFrame frame, PByteArray self, Object key,
                        @Cached SequenceStorageNodes.DeleteNode deleteNode) {
            self.checkCanResize(this);
            deleteNode.execute(frame, self.getSequenceStorage(), key);
            return PNone.NONE;
        }

        @SuppressWarnings("unused")
        @Fallback
        protected Object doGeneric(Object self, Object idx) {
            throw raise(TypeError, ErrorMessages.DESCRIPTOR_S_REQUIRES_S_OBJ_RECEIVED_P, "__delitem__", "bytearray", idx);
        }
    }

    @Builtin(name = J_APPEND, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class AppendNode extends PythonBinaryBuiltinNode {

        @Specialization
        public PNone append(VirtualFrame frame, PByteArray byteArray, Object arg,
                        @Cached("createCast()") CastToByteNode toByteNode,
                        @Cached SequenceStorageNodes.AppendNode appendNode) {
            byteArray.checkCanResize(this);
            appendNode.execute(byteArray.getSequenceStorage(), toByteNode.execute(frame, arg), BytesLikeNoGeneralizationNode.SUPPLIER);
            return PNone.NONE;
        }

        @NeverDefault
        protected CastToByteNode createCast() {
            return CastToByteNode.create(val -> {
                throw raise(ValueError, ErrorMessages.BYTE_MUST_BE_IN_RANGE);
            }, val -> {
                throw raise(TypeError, ErrorMessages.OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER, "bytes");
            });
        }
    }

    // bytearray.extend(L)
    @Builtin(name = J_EXTEND, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ExtendNode extends PythonBinaryBuiltinNode {

        @Specialization
        PNone doBytes(VirtualFrame frame, PByteArray self, PBytesLike source,
                        @Cached IteratorNodes.GetLength lenNode,
                        @Cached("createExtend()") @Shared SequenceStorageNodes.ExtendNode extendNode) {
            self.checkCanResize(this);
            int len = lenNode.execute(frame, source);
            extend(frame, self, source, len, extendNode);
            return PNone.NONE;
        }

        @Specialization(guards = "!isBytes(source)", limit = "3")
        @SuppressWarnings("truffle-static-method")
        PNone doGeneric(VirtualFrame frame, PByteArray self, Object source,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary("source") PythonBufferAcquireLibrary bufferAcquireLib,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib,
                        @Cached InlinedConditionProfile bufferProfile,
                        @Cached BytesNodes.IterableToByteNode iterableToByteNode,
                        @Cached IsBuiltinObjectProfile errorProfile,
                        @Cached("createExtend()") @Shared SequenceStorageNodes.ExtendNode extendNode) {
            self.checkCanResize(this);
            byte[] b;
            if (bufferProfile.profile(inliningTarget, bufferAcquireLib.hasBuffer(source))) {
                Object buffer = bufferAcquireLib.acquireReadonly(source, frame, this);
                try {
                    // TODO avoid copying
                    b = bufferLib.getCopiedByteArray(buffer);
                } finally {
                    bufferLib.release(buffer, frame, this);
                }
            } else {
                try {
                    b = iterableToByteNode.execute(frame, source);
                } catch (PException e) {
                    e.expect(inliningTarget, TypeError, errorProfile);
                    throw raise(TypeError, ErrorMessages.CANT_EXTEND_BYTEARRAY_WITH_P, source);
                }
            }
            PByteArray bytes = factory().createByteArray(b);
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
        public PByteArray copy(PByteArray byteArray,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedGetClassNode getClassNode,
                        @Cached SequenceStorageNodes.ToByteArrayNode toByteArray) {
            return factory().createByteArray(getClassNode.execute(inliningTarget, byteArray), toByteArray.execute(byteArray.getSequenceStorage()));
        }
    }

    // bytearray.reverse()
    @Builtin(name = "reverse", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReverseNode extends PythonBuiltinNode {

        @Specialization
        public static PNone reverse(PByteArray byteArray) {
            byteArray.reverse();
            return PNone.NONE;
        }
    }

    // bytearray.clear()
    @Builtin(name = "clear", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ClearNode extends PythonUnaryBuiltinNode {

        @Specialization
        public PNone clear(VirtualFrame frame, PByteArray byteArray,
                        @Cached SequenceStorageNodes.DeleteNode deleteNode,
                        @Cached PySliceNew sliceNode) {
            byteArray.checkCanResize(this);
            deleteNode.execute(frame, byteArray.getSequenceStorage(), sliceNode.execute(PNone.NONE, PNone.NONE, 1));
            return PNone.NONE;
        }
    }

    // bytearray.fromhex()
    @Builtin(name = "fromhex", minNumOfPositionalArgs = 2, isClassmethod = true, numOfPositionalOnlyArgs = 2, parameterNames = {"$cls", "string"})
    @ArgumentClinic(name = "string", conversion = ArgumentClinic.ClinicConversion.TString)
    @GenerateNodeFactory
    public abstract static class FromHexNode extends PythonBinaryClinicBuiltinNode {

        @Specialization(guards = "isBuiltinBytesType(inliningTarget, cls, isSameType)", limit = "1")
        PByteArray doBytes(Object cls, TruffleString str,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared("isSameType") @Cached InlinedIsSameTypeNode isSameType,
                        @Shared("hexToBytes") @Cached HexStringToBytesNode hexStringToBytesNode) {
            return factory().createByteArray(cls, hexStringToBytesNode.execute(str));
        }

        @Specialization(guards = "!isBuiltinBytesType(inliningTarget, cls, isSameType)", limit = "1")
        Object doGeneric(VirtualFrame frame, Object cls, TruffleString str,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared("isSameType") @Cached InlinedIsSameTypeNode isSameType,
                        @Cached CallNode callNode,
                        @Shared("hexToBytes") @Cached HexStringToBytesNode hexStringToBytesNode) {
            PByteArray byteArray = factory().createByteArray(hexStringToBytesNode.execute(str));
            return callNode.execute(frame, cls, byteArray);
        }

        protected static boolean isBuiltinBytesType(Node inliningTarget, Object cls, InlinedIsSameTypeNode isSameTypeNode) {
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
        public PByteArray translate(PByteArray self, @SuppressWarnings("unused") PNone table, @SuppressWarnings("unused") PNone delete,
                        @Cached.Shared("toBytes") @Cached BytesNodes.ToBytesNode toBytesNode) {
            byte[] content = toBytesNode.execute(self);
            return factory().createByteArray(content);
        }

        @Specialization(guards = "!isNone(table)")
        PByteArray translate(VirtualFrame frame, PByteArray self, Object table, @SuppressWarnings("unused") PNone delete,
                        @Cached.Shared("profile") @Cached InlinedConditionProfile isLenTable256Profile,
                        @Cached.Shared("toBytes") @Cached BytesNodes.ToBytesNode toBytesNode) {
            byte[] bTable = toBytesNode.execute(frame, table);
            checkLengthOfTable(bTable, isLenTable256Profile);
            byte[] bSelf = toBytesNode.execute(self);

            Result result = translate(bSelf, bTable);
            return factory().createByteArray(result.array);
        }

        @Specialization(guards = "isNone(table)")
        PByteArray delete(VirtualFrame frame, PByteArray self, @SuppressWarnings("unused") PNone table, Object delete,
                        @Cached.Shared("toBytes") @Cached BytesNodes.ToBytesNode toBytesNode) {
            byte[] bSelf = toBytesNode.execute(self);
            byte[] bDelete = toBytesNode.execute(frame, delete);

            Result result = delete(bSelf, bDelete);
            return factory().createByteArray(result.array);
        }

        @Specialization(guards = {"!isPNone(table)", "!isPNone(delete)"})
        PByteArray translateAndDelete(VirtualFrame frame, PByteArray self, Object table, Object delete,
                        @Cached.Shared("profile") @Cached InlinedConditionProfile isLenTable256Profile,
                        @Cached.Shared("toBytes") @Cached BytesNodes.ToBytesNode toBytesNode) {
            byte[] bTable = toBytesNode.execute(frame, table);
            checkLengthOfTable(bTable, isLenTable256Profile);
            byte[] bDelete = toBytesNode.execute(frame, delete);
            byte[] bSelf = toBytesNode.execute(self);

            Result result = translateAndDelete(bSelf, bTable, bDelete);
            return factory().createByteArray(result.array);
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
        public Object reduce(VirtualFrame frame, PByteArray self,
                        @Bind("this") Node inliningTarget,
                        @Cached SequenceStorageNodes.GetInternalByteArrayNode getBytes,
                        @Cached InlinedGetClassNode getClassNode,
                        @Cached PyObjectLookupAttr lookupDict,
                        @Cached TruffleStringBuilder.AppendCodePointNode appendCodePointNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            byte[] bytes = getBytes.execute(self.getSequenceStorage());
            int len = self.getSequenceStorage().length();
            Object dict = lookupDict.execute(frame, self, T___DICT__);
            if (dict == PNone.NO_VALUE) {
                dict = PNone.NONE;
            }
            Object clazz = getClassNode.execute(inliningTarget, self);
            return commonReduce(2, bytes, len, clazz, dict, factory(), appendCodePointNode, toStringNode);
        }
    }

    @GenerateCached(false)
    abstract static class AbstractComparisonNode extends BytesNodes.AbstractComparisonBaseNode {
        @Specialization
        @SuppressWarnings("truffle-static-method")
        boolean cmp(PByteArray self, PBytesLike other,
                        @Shared @Cached GetInternalByteArrayNode getArray) {
            SequenceStorage selfStorage = self.getSequenceStorage();
            SequenceStorage otherStorage = other.getSequenceStorage();
            return doCmp(getArray.execute(selfStorage), selfStorage.length(), getArray.execute(otherStorage), otherStorage.length());
        }

        @Specialization(guards = {"check.execute(inliningTarget, self)", "acquireLib.hasBuffer(other)"}, limit = "3")
        @SuppressWarnings("truffle-static-method")
        Object cmp(VirtualFrame frame, Object self, Object other,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached PyByteArrayCheckNode check,
                        @Cached GetBytesStorage getBytesStorage,
                        @Shared @Cached GetInternalByteArrayNode getArray,
                        @CachedLibrary("other") PythonBufferAcquireLibrary acquireLib,
                        @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib) {
            SequenceStorage selfStorage = getBytesStorage.execute(inliningTarget, self);
            Object otherBuffer = acquireLib.acquireReadonly(other, frame, this);
            try {
                return doCmp(getArray.execute(selfStorage), selfStorage.length(),
                                bufferLib.getInternalOrCopiedByteArray(otherBuffer), bufferLib.getBufferLength(otherBuffer));
            } finally {
                bufferLib.release(otherBuffer);
            }
        }

        @Specialization(guards = {"check.execute(inliningTarget, self)", "!acquireLib.hasBuffer(other)"}, limit = "1")
        @SuppressWarnings("unused")
        static Object cmp(VirtualFrame frame, Object self, Object other,
                        @Bind("this") Node inliningTarget,
                        @Cached PyByteArrayCheckNode check,
                        @CachedLibrary(limit = "3") PythonBufferAcquireLibrary acquireLib) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        @Specialization(guards = "!check.execute(inliningTarget, self)", limit = "1")
        @SuppressWarnings({"truffle-static-method", "unused"})
        Object error(VirtualFrame frame, Object self, Object other,
                        @Bind("this") Node inliningTarget,
                        @Cached PyByteArrayCheckNode check) {
            throw raise(TypeError, ErrorMessages.DESCRIPTOR_S_REQUIRES_S_OBJ_RECEIVED_P, J___EQ__, J_BYTEARRAY, self);
        }
    }

    @Builtin(name = J___EQ__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class EqNode extends AbstractComparisonNode {
        @Override
        protected boolean fromCompareResult(int compareResult) {
            return compareResult == 0;
        }

        @Override
        protected boolean shortcutLength() {
            return true;
        }
    }

    @Builtin(name = J___NE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class NeNode extends AbstractComparisonNode {
        @Override
        protected boolean fromCompareResult(int compareResult) {
            return compareResult != 0;
        }

        @Override
        protected boolean shortcutLength() {
            return true;
        }

        @Override
        protected boolean shortcutLengthResult() {
            return true;
        }
    }

    @Builtin(name = J___LT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class LtNode extends AbstractComparisonNode {
        @Override
        protected boolean fromCompareResult(int compareResult) {
            return compareResult < 0;
        }
    }

    @Builtin(name = J___LE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class LeNode extends AbstractComparisonNode {
        @Override
        protected boolean fromCompareResult(int compareResult) {
            return compareResult <= 0;
        }
    }

    @Builtin(name = J___GT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GtNode extends AbstractComparisonNode {
        @Override
        protected boolean fromCompareResult(int compareResult) {
            return compareResult > 0;
        }
    }

    @Builtin(name = J___GE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GeNode extends AbstractComparisonNode {
        @Override
        protected boolean fromCompareResult(int compareResult) {
            return compareResult >= 0;
        }
    }

}
