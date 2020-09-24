/*
 * Copyright (c) 2020, Oracle and/or its affiliates.
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

import static com.oracle.graal.python.nodes.SpecialAttributeNames.__DICT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__DELITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__HASH__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__IADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__IMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SETITEM__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.BufferError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.SystemError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.BytesBuiltins.BytesLikeNoGeneralizationNode;
import com.oracle.graal.python.builtins.objects.common.IndexNodes;
import com.oracle.graal.python.builtins.objects.common.IndexNodes.NormalizeIndexNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.iterator.IteratorNodes;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.TypeBuiltins;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.subscript.SliceLiteralNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToByteNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PByteArray)
public class ByteArrayBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ByteArrayBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(PythonCore core) {
        super.initialize(core);
        builtinConstants.put(SpecialAttributeNames.__DOC__, //
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
        builtinConstants.put(__HASH__, PNone.NONE);
    }

    // bytearray([source[, encoding[, errors]]])
    @Builtin(name = __INIT__, minNumOfPositionalArgs = 1, parameterNames = {"$self", "source", "encoding", "errors"})
    @ArgumentClinic(name = "encoding", customConversion = "createExpectStringNodeEncoding")
    @ArgumentClinic(name = "errors", customConversion = "createExpectStringNodeErrors")
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonQuaternaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ByteArrayBuiltinsClinicProviders.InitNodeClinicProviderGen.INSTANCE;
        }

        public static BytesNodes.ExpectStringNode createExpectStringNodeEncoding() {
            return BytesNodesFactory.ExpectStringNodeGen.create(2, "bytearray()");
        }

        public static BytesNodes.ExpectStringNode createExpectStringNodeErrors() {
            return BytesNodesFactory.ExpectStringNodeGen.create(3, "bytearray()");
        }

        @Specialization(guards = "!isNone(source)")
        public static PNone doInit(VirtualFrame frame, PByteArray self, Object source, Object encoding, Object errors,
                        @Cached BytesNodes.BytesInitNode toBytesNode) {
            self.setSequenceStorage(new ByteSequenceStorage(toBytesNode.execute(frame, source, encoding, errors)));
            return PNone.NONE;
        }

        @Specialization(guards = "isNone(self)")
        public PNone doInit(@SuppressWarnings("unused") PByteArray self, Object source, @SuppressWarnings("unused") Object encoding, @SuppressWarnings("unused") Object errors) {
            throw raise(TypeError, ErrorMessages.CANNOT_CONVERT_P_OBJ_TO_S, source, PythonBuiltinClassType.PByteArray);
        }

        @Specialization(guards = "!isBytes(self)")
        public PNone doInit(Object self, @SuppressWarnings("unused") Object source, @SuppressWarnings("unused") Object encoding, @SuppressWarnings("unused") Object errors) {
            throw raise(TypeError, ErrorMessages.DESCRIPTOR_REQUIRES_OBJ, __INIT__, PythonBuiltinClassType.PByteArray, self);
        }
    }

    @Builtin(name = __GETITEM__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GetitemNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isPSlice(key) || lib.canBeIndex(key)", limit = "3")
        Object doSlice(VirtualFrame frame, PBytesLike self, Object key,
                        @SuppressWarnings("unused") @CachedLibrary("key") PythonObjectLibrary lib,
                        @Cached("createGetItem()") SequenceStorageNodes.GetItemNode getSequenceItemNode) {
            return getSequenceItemNode.execute(frame, self.getSequenceStorage(), key);
        }

        @SuppressWarnings("unused")
        @Specialization
        Object none(VirtualFrame frame, PBytesLike self, PNone key) {
            return raise(ValueError, ErrorMessages.BYTESLIKE_OBJ_REQUIRED, key);
        }

        @SuppressWarnings("unused")
        @Fallback
        Object doSlice(VirtualFrame frame, Object self, Object key) {
            return raise(TypeError, ErrorMessages.OBJ_INDEX_MUST_BE_INT_OR_SLICES, "bytearray", key);
        }

        protected static SequenceStorageNodes.GetItemNode createGetItem() {
            return SequenceStorageNodes.GetItemNode.create(IndexNodes.NormalizeIndexNode.create(), (s, f) -> f.createByteArray(s));
        }
    }

    @Builtin(name = __SETITEM__, minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    @ImportStatic(SpecialMethodNames.class)
    abstract static class SetItemNode extends PythonTernaryBuiltinNode {

        @Child private PRaiseNode raiseNode = PRaiseNode.create();

        @Specialization(guards = {"!isPSlice(idx)", "lib.canBeIndex(idx)", "!isMemoryView(value)"}, limit = "3")
        PNone doItem(VirtualFrame frame, PByteArray self, Object idx, Object value,
                        @SuppressWarnings("unused") @CachedLibrary("idx") PythonObjectLibrary lib,
                        @Cached("createSetItem()") SequenceStorageNodes.SetItemNode setItemNode) {
            setItemNode.execute(frame, self.getSequenceStorage(), idx, value);
            return PNone.NONE;
        }

        @Specialization
        PNone doSliceMemoryview(VirtualFrame frame, PByteArray self, PSlice slice, PMemoryView value,
                        @Cached("create(TOBYTES)") LookupAndCallUnaryNode callToBytesNode,
                        @Cached("createBinaryProfile()") ConditionProfile isBytesProfile,
                        @Cached("createSetSlice()") SequenceStorageNodes.SetItemNode setItemNode) {
            Object bytesObj = callToBytesNode.executeObject(frame, value);
            if (isBytesProfile.profile(bytesObj instanceof PBytes)) {
                setItemNode.execute(frame, self.getSequenceStorage(), slice, bytesObj);
                return PNone.NONE;
            }
            throw raise(SystemError, ErrorMessages.COULD_NOT_GET_BYTES_OF_MEMORYVIEW);
        }

        @Specialization(guards = "!isMemoryView(value)")
        PNone doSlice(VirtualFrame frame, PByteArray self, PSlice idx, Object value,
                        @Cached("createSetSlice()") SequenceStorageNodes.SetItemNode setItemNode) {
            // this is really just a separate specialization due to the different error message
            setItemNode.execute(frame, self.getSequenceStorage(), idx, value);
            return PNone.NONE;
        }

        @Fallback
        @SuppressWarnings("unused")
        Object error(Object self, Object idx, Object value) {
            throw raiseNode.raise(TypeError, ErrorMessages.OBJ_INDEX_MUST_BE_INT_OR_SLICES, "bytearray", idx);
        }

        protected SequenceStorageNodes.SetItemNode createSetItem() {
            return SequenceStorageNodes.SetItemNode.create(NormalizeIndexNode.forBytearray(), "an integer is required");
        }

        protected SequenceStorageNodes.SetItemNode createSetSlice() {
            return SequenceStorageNodes.SetItemNode.create(NormalizeIndexNode.forBytearray(), "can assign only bytes, buffers, or iterables of ints in range(0, 256)");
        }

        protected static boolean isMemoryView(Object value) {
            return value instanceof PMemoryView;
        }
    }

    @Builtin(name = "insert", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class InsertNode extends PythonTernaryBuiltinNode {

        public abstract PNone execute(VirtualFrame frame, PByteArray list, Object index, Object value);

        @Specialization(guards = "isByteStorage(self)")
        PNone insert(VirtualFrame frame, PByteArray self, int index, int value,
                        @Cached CastToByteNode toByteNode) {
            byte v = toByteNode.execute(frame, value);
            ByteSequenceStorage target = (ByteSequenceStorage) self.getSequenceStorage();
            target.insertByteItem(normalizeIndex(index, target.length()), v);
            return PNone.NONE;
        }

        @Specialization(guards = {"isByteStorage(self)", "lib.canBeIndex(index)", "lib.canBeIndex(value)"})
        PNone insert(VirtualFrame frame, PByteArray self, Object index, Object value,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") PythonObjectLibrary lib,
                        @Cached("create(0)") BytesBuiltins.ExpectIntNode toInt,
                        @Cached CastToByteNode toByteNode) {
            byte v = toByteNode.execute(frame, value);
            int idx = toInt.executeInt(frame, index);
            ByteSequenceStorage target = (ByteSequenceStorage) self.getSequenceStorage();
            target.insertByteItem(normalizeIndex(idx, target.length()), v);
            return PNone.NONE;
        }

        @Specialization(guards = "!lib.canBeIndex(index) || !lib.canBeIndex(value)")
        PNone error(@SuppressWarnings("unused") PByteArray self, Object index, @SuppressWarnings("unused") Object value,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") PythonObjectLibrary lib) {
            Object errValue = !lib.canBeIndex(index) ? index : value;
            throw raise(TypeError, ErrorMessages.OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER, errValue);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isByteStorage(self)")
        PNone insert(VirtualFrame frame, PByteArray self, Object index, Object value) {
            // TODO: (mq) need to check for the number of ob_exports, i.e. reference counter.
            throw raise(BufferError, ErrorMessages.EXPORTS_CANNOT_RESIZE);
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
    }

    @Builtin(name = __REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReprNode extends PythonUnaryBuiltinNode {

        @Specialization
        public static Object repr(PByteArray self,
                        @Cached SequenceStorageNodes.GetInternalByteArrayNode getBytes,
                        @Cached TypeNodes.GetNameNode getNameNode,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @CachedLibrary(limit = "2") PythonObjectLibrary lib) {
            SequenceStorage store = self.getSequenceStorage();
            byte[] bytes = getBytes.execute(store);
            int len = lenNode.execute(store);
            StringBuilder sb = BytesUtils.newStringBuilder();
            String typeName = getNameNode.execute(lib.getLazyPythonClass(self));
            BytesUtils.sbAppend(sb, typeName);
            BytesUtils.sbAppend(sb, '(');
            BytesUtils.reprLoop(sb, bytes, len);
            BytesUtils.sbAppend(sb, ')');
            return BytesUtils.sbToString(sb);
        }
    }

    @Builtin(name = __IADD__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class IAddNode extends PythonBinaryBuiltinNode {
        @Specialization
        public PByteArray add(PByteArray self, PBytesLike other,
                        @Cached SequenceStorageNodes.ConcatNode concatNode) {
            SequenceStorage res = concatNode.execute(self.getSequenceStorage(), other.getSequenceStorage());
            updateSequenceStorage(self, res);
            return self;
        }

        @Specialization
        public PByteArray add(VirtualFrame frame, PByteArray self, PMemoryView other,
                        @Cached("create(TOBYTES)") LookupAndCallUnaryNode toBytesNode,
                        @Cached("createBinaryProfile()") ConditionProfile isBytesProfile,
                        @Cached SequenceStorageNodes.ConcatNode concatNode) {

            Object bytesObj = toBytesNode.executeObject(frame, other);
            if (isBytesProfile.profile(bytesObj instanceof PBytes)) {
                SequenceStorage res = concatNode.execute(self.getSequenceStorage(), ((PBytes) bytesObj).getSequenceStorage());
                updateSequenceStorage(self, res);
                return self;
            }
            throw raise(SystemError, ErrorMessages.COULD_NOT_GET_BYTES_OF_MEMORYVIEW);
        }

        @SuppressWarnings("unused")
        @Fallback
        public Object add(Object self, Object other) {
            throw raise(TypeError, ErrorMessages.CANT_CONCAT_S_TO_P, "bytearray", other);
        }

        private static void updateSequenceStorage(PByteArray array, SequenceStorage s) {
            if (array.getSequenceStorage() != s) {
                array.setSequenceStorage(s);
            }
        }
    }

    @Builtin(name = __IMUL__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class IMulNode extends PythonBinaryBuiltinNode {
        @Specialization
        public Object mul(VirtualFrame frame, PByteArray self, int times,
                        @Cached SequenceStorageNodes.RepeatNode repeatNode) {
            SequenceStorage res = repeatNode.execute(frame, self.getSequenceStorage(), times);
            self.setSequenceStorage(res);
            return self;
        }

        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()")
        public Object mul(VirtualFrame frame, PByteArray self, Object times,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @Cached SequenceStorageNodes.RepeatNode repeatNode,
                        @CachedLibrary("times") PythonObjectLibrary lib) {
            SequenceStorage res = repeatNode.execute(frame, self.getSequenceStorage(), getTimesInt(frame, times, hasFrame, lib));
            self.setSequenceStorage(res);
            return self;
        }

        private static int getTimesInt(VirtualFrame frame, Object times, ConditionProfile hasFrame, PythonObjectLibrary lib) {
            if (hasFrame.profile(frame != null)) {
                return lib.asSizeWithState(times, PArguments.getThreadState(frame));
            } else {
                return lib.asSize(times);
            }
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

        private static final String NOT_IN_BYTEARRAY = "value not found in bytearray";

        @Specialization(guards = {"isByteStorage(self)", "lib.canBeIndex(value)"})
        PNone remove(VirtualFrame frame, PByteArray self, Object value,
                        @Cached BytesNodes.FindNode findNode,
                        @Cached SequenceStorageNodes.DeleteNode deleteNode,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "getCallSiteInlineCacheMaxDepth()") PythonObjectLibrary lib) {
            SequenceStorage storage = self.getSequenceStorage();
            int len = lenNode.execute(storage);
            int pos = findNode.execute(self.getSequenceStorage(), len, value, 0, len);
            if (pos != -1) {
                deleteNode.execute(frame, storage, pos);
                return PNone.NONE;
            }
            throw raise(ValueError, NOT_IN_BYTEARRAY);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isByteStorage(self)")
        Object bufferError(PByteArray self, Object value) {
            // TODO: (mq) need to check for the number of ob_exports, i.e. reference counter.
            throw raise(BufferError, ErrorMessages.EXPORTS_CANNOT_RESIZE);
        }

        @Fallback
        public Object doError(@SuppressWarnings("unused") Object self, Object arg) {
            throw raise(TypeError, ErrorMessages.OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER, arg);
        }
    }

    @Builtin(name = "pop", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class PopNode extends PythonBuiltinNode {

        @Specialization(guards = "isByteStorage(self)")
        public Object popLast(VirtualFrame frame, PByteArray self, @SuppressWarnings("unused") PNone none,
                        @Cached.Shared("getItem") @Cached SequenceStorageNodes.GetItemNode getItemNode,
                        @Cached("createDelete()") SequenceStorageNodes.DeleteNode deleteNode) {
            SequenceStorage store = self.getSequenceStorage();
            Object ret = getItemNode.execute(frame, store, -1);
            deleteNode.execute(frame, store, -1);
            return ret;
        }

        @Specialization(guards = {"isByteStorage(self)", "!isNoValue(idx)", "!isPSlice(idx)"})
        public Object doIndex(VirtualFrame frame, PByteArray self, Object idx,
                        @Cached.Shared("getItem") @Cached SequenceStorageNodes.GetItemNode getItemNode,
                        @Cached("createDelete()") SequenceStorageNodes.DeleteNode deleteNode) {
            SequenceStorage store = self.getSequenceStorage();
            Object ret = getItemNode.execute(frame, store, idx);
            deleteNode.execute(frame, store, idx);
            return ret;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isByteStorage(self)")
        Object bufferError(PByteArray self, Object idx) {
            // TODO: (mq) need to check for the number of ob_exports, i.e. reference counter.
            throw raise(BufferError, ErrorMessages.EXPORTS_CANNOT_RESIZE);
        }

        @Fallback
        public Object doError(@SuppressWarnings("unused") Object self, Object arg) {
            throw raise(TypeError, ErrorMessages.OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER, arg);
        }

        protected static SequenceStorageNodes.DeleteNode createDelete() {
            return SequenceStorageNodes.DeleteNode.create(createNormalize());
        }

        private static NormalizeIndexNode createNormalize() {
            return NormalizeIndexNode.create(ErrorMessages.POP_INDEX_OUT_OF_RANGE);
        }
    }

    @Builtin(name = __DELITEM__, minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class DelItemNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isByteStorage(self)")
        protected PNone doGeneric(VirtualFrame frame, PByteArray self, Object key,
                        @Cached SequenceStorageNodes.DeleteNode deleteNode) {
            deleteNode.execute(frame, self.getSequenceStorage(), key);
            return PNone.NONE;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isByteStorage(self)")
        Object bufferError(PByteArray self, Object key) {
            // TODO: (mq) need to check for the number of ob_exports, i.e. reference counter.
            throw raise(BufferError, ErrorMessages.EXPORTS_CANNOT_RESIZE);
        }

        @SuppressWarnings("unused")
        @Fallback
        protected Object doGeneric(Object self, Object idx) {
            throw raise(TypeError, ErrorMessages.DESCRIPTOR_REQUIRES_OBJ, "__delitem__", "bytearray", idx);
        }
    }

    @Builtin(name = "append", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class AppendNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = "lib.canBeIndex(arg)", limit = "3")
        public PNone append(VirtualFrame frame, PByteArray byteArray, Object arg,
                        @Cached("createCast()") CastToByteNode toByteNode,
                        @Cached SequenceStorageNodes.AppendNode appendNode,
                        @SuppressWarnings("unused") @CachedLibrary("arg") PythonObjectLibrary lib) {
            appendNode.execute(byteArray.getSequenceStorage(), toByteNode.execute(frame, arg), BytesLikeNoGeneralizationNode.SUPPLIER);
            return PNone.NONE;
        }

        @Fallback
        public Object doError(@SuppressWarnings("unused") Object list, Object arg) {
            throw raise(TypeError, ErrorMessages.OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER, arg);
        }

        protected CastToByteNode createCast() {
            return CastToByteNode.create(val -> {
                throw raise(TypeError, ErrorMessages.OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER, "bytes");
            }, val -> {
                throw raise(ValueError, ErrorMessages.BYTE_MUST_BE_IN_RANGE);
            });
        }
    }

    // bytearray.extend(L)
    @Builtin(name = "extend", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ExtendNode extends PythonBinaryBuiltinNode {

        @Specialization
        PNone doBytes(VirtualFrame frame, PByteArray self, PBytesLike source,
                        @Cached IteratorNodes.GetLength lenNode,
                        @Cached("createExtend()") SequenceStorageNodes.ExtendNode extendNode) {
            int len = lenNode.execute(frame, source);
            extend(frame, self, source, len, extendNode);
            return PNone.NONE;
        }

        @Specialization
        PNone doMemoryview(VirtualFrame frame, PByteArray self, PMemoryView value,
                        @Cached("create(TOBYTES)") LookupAndCallUnaryNode callToBytesNode,
                        @Cached("createBinaryProfile()") ConditionProfile isBytesProfile,
                        @Cached IteratorNodes.GetLength lenNode,
                        @Cached("createExtend()") SequenceStorageNodes.ExtendNode extendNode) {
            Object bytesObj = callToBytesNode.executeObject(frame, value);
            if (isBytesProfile.profile(bytesObj instanceof PBytes)) {
                extend(frame, self, bytesObj, lenNode.execute(frame, bytesObj), extendNode);
                return PNone.NONE;
            }
            throw raise(SystemError, ErrorMessages.COULD_NOT_GET_BYTES_OF_MEMORYVIEW);
        }

        @Specialization(guards = "!isBytes(source)")
        PNone doGeneric(VirtualFrame frame, PByteArray self, Object source,
                        @Cached("createCast()") BytesNodes.IterableToByteNode toByteNode,
                        @Cached IteratorNodes.GetLength lenNode,
                        @Cached("createExtend()") SequenceStorageNodes.ExtendNode extendNode) {
            byte[] b = toByteNode.execute(frame, source, lenNode.execute(frame, source));
            PByteArray bytes = factory().createByteArray(b);
            extend(frame, self, bytes, b.length, extendNode);
            return PNone.NONE;
        }

        private static void extend(VirtualFrame frame, PByteArray self, Object source,
                        int len, SequenceStorageNodes.ExtendNode extendNode) {
            SequenceStorage execute = extendNode.execute(frame, self.getSequenceStorage(), source, len);
            assert self.getSequenceStorage() == execute : "Unexpected storage generalization!";
        }

        protected static SequenceStorageNodes.ExtendNode createExtend() {
            return SequenceStorageNodes.ExtendNode.create(BytesLikeNoGeneralizationNode.SUPPLIER);
        }

        protected static BytesNodes.IterableToByteNode createCast() {
            return BytesNodes.IterableToByteNode.create(val -> PythonLanguage.getCore().raise(TypeError, "can't extend bytearray with %p"));
        }
    }

    // bytearray.copy()
    @Builtin(name = "copy", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class CopyNode extends PythonBuiltinNode {
        @Specialization(limit = "3")
        public PByteArray copy(PByteArray byteArray,
                        @CachedLibrary("byteArray") PythonObjectLibrary lib,
                        @Cached SequenceStorageNodes.ToByteArrayNode toByteArray) {
            return factory().createByteArray(lib.getLazyPythonClass(byteArray), toByteArray.execute(byteArray.getSequenceStorage()));
        }
    }

    // bytearray.reverse()
    @Builtin(name = "reverse", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReverseNode extends PythonBuiltinNode {

        @Specialization
        public PNone reverse(PByteArray byteArray) {
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
                        @Cached SliceLiteralNode slice) {
            deleteNode.execute(frame, byteArray.getSequenceStorage(), slice.execute(frame, PNone.NONE, PNone.NONE, 1));
            return PNone.NONE;
        }
    }

    // bytearray.fromhex()
    @Builtin(name = "fromhex", minNumOfPositionalArgs = 2, isClassmethod = true)
    @GenerateNodeFactory
    public abstract static class FromHexNode extends PythonBinaryBuiltinNode {

        @Specialization
        PByteArray doString(PythonBuiltinClass cls, String str) {
            return factory().createByteArray(cls, BytesUtils.fromHex(str, getRaiseNode()));
        }

        @Specialization
        PByteArray doGeneric(PythonBuiltinClass cls, Object strObj,
                        @Cached CastToJavaStringNode castToJavaStringNode) {
            try {
                String str = castToJavaStringNode.execute(strObj);
                return factory().createByteArray(cls, BytesUtils.fromHex(str, getRaiseNode()));
            } catch (CannotCastException e) {
                throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.ARG_MUST_BE_S_NOT_P, "fromhex()", "str", strObj);
            }
        }

        @Specialization(guards = "!isPythonBuiltinClass(cls)")
        Object doGeneric(VirtualFrame frame, Object cls, Object strObj,
                        @Cached TypeBuiltins.CallNode callNode,
                        @Cached CastToJavaStringNode castToJavaStringNode) {
            try {
                String str = castToJavaStringNode.execute(strObj);
                PByteArray byteArray = factory().createByteArray(BytesUtils.fromHex(str, getRaiseNode()));
                return callNode.varArgExecute(frame, null, new Object[]{cls, byteArray}, new PKeyword[0]);
            } catch (CannotCastException e) {
                throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.ARG_MUST_BE_S_NOT_P, "fromhex()", "str", strObj);
            }
        }
    }

    // bytearray.translate(table, delete=b'')
    @Builtin(name = "translate", minNumOfPositionalArgs = 2, parameterNames = {"self", "table", "delete"})
    @GenerateNodeFactory
    public abstract static class TranslateNode extends BytesBuiltins.BaseTranslateNode {

        @Specialization(guards = "isNoValue(delete)")
        public PByteArray translate(VirtualFrame frame, PByteArray self, @SuppressWarnings("unused") PNone table, @SuppressWarnings("unused") PNone delete,
                        @Cached.Shared("toBytes") @Cached BytesNodes.ToBytesNode toBytesNode) {
            byte[] content = toBytesNode.execute(frame, self);
            return factory().createByteArray(content);
        }

        @Specialization(guards = "!isNone(table)")
        PByteArray translate(VirtualFrame frame, PByteArray self, Object table, @SuppressWarnings("unused") PNone delete,
                        @Cached.Shared("profile") @Cached ConditionProfile isLenTable256Profile,
                        @Cached.Shared("toBytes") @Cached BytesNodes.ToBytesNode toBytesNode) {
            byte[] bTable = toBytesNode.execute(frame, table);
            checkLengthOfTable(bTable, isLenTable256Profile);
            byte[] bSelf = toBytesNode.execute(frame, self);

            Result result = translate(bSelf, bTable);
            return factory().createByteArray(result.array);
        }

        @Specialization(guards = "isNone(table)")
        PByteArray delete(VirtualFrame frame, PByteArray self, @SuppressWarnings("unused") PNone table, Object delete,
                        @Cached.Shared("toBytes") @Cached BytesNodes.ToBytesNode toBytesNode) {
            byte[] bSelf = toBytesNode.execute(frame, self);
            byte[] bDelete = toBytesNode.execute(frame, delete);

            Result result = delete(bSelf, bDelete);
            return factory().createByteArray(result.array);
        }

        @Specialization(guards = {"!isPNone(table)", "!isPNone(delete)"})
        PByteArray translateAndDelete(VirtualFrame frame, PByteArray self, Object table, Object delete,
                        @Cached.Shared("profile") @Cached ConditionProfile isLenTable256Profile,
                        @Cached.Shared("toBytes") @Cached BytesNodes.ToBytesNode toBytesNode) {
            byte[] bTable = toBytesNode.execute(frame, table);
            checkLengthOfTable(bTable, isLenTable256Profile);
            byte[] bDelete = toBytesNode.execute(frame, delete);
            byte[] bSelf = toBytesNode.execute(frame, self);

            Result result = translateAndDelete(bSelf, bTable, bDelete);
            return factory().createByteArray(result.array);
        }
    }

    // bytearray.clear()
    @Builtin(name = "__alloc__", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class AllocNode extends PythonUnaryBuiltinNode {

        @Specialization
        public int alloc(PByteArray byteArray,
                        @Cached SequenceStorageNodes.LenNode lenNode) {
            // XXX: (mq) We return a fake allocation size.
            // The actual number might useful for manual memory management.
            return lenNode.execute(byteArray.getSequenceStorage()) + 1;
        }
    }

    protected static Object commonReduce(int proto, byte[] bytes, int len, Object clazz, Object dict,
                    PythonObjectFactory factory) {
        StringBuilder sb = BytesUtils.newStringBuilder();
        BytesUtils.repr(sb, bytes, len);
        String str = BytesUtils.sbToString(sb);
        Object contents;
        if (proto < 3) {
            contents = factory.createTuple(new Object[]{str, "latin-1"});
        } else {
            if (len > 0) {
                contents = factory.createTuple(new Object[]{str, len});
            } else {
                contents = factory.createTuple(new Object[0]);
            }
        }
        return factory.createTuple(new Object[]{clazz, contents, dict});
    }

    @Builtin(name = __REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    protected abstract static class BaseReduceNode extends PythonUnaryBuiltinNode {

        @Specialization(limit = "1")
        public Object reduce(VirtualFrame frame, PByteArray self,
                        @Cached SequenceStorageNodes.GetInternalByteArrayNode getBytes,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @CachedLibrary("self") PythonObjectLibrary plib) {
            byte[] bytes = getBytes.execute(self.getSequenceStorage());
            int len = lenNode.execute(self.getSequenceStorage());
            Object dict = plib.lookupAttribute(self, frame, __DICT__);
            if (dict == PNone.NO_VALUE) {
                dict = PNone.NONE;
            }
            Object clazz = plib.getLazyPythonClass(self);
            return commonReduce(2, bytes, len, clazz, dict, factory());
        }
    }
}
