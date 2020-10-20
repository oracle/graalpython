/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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

import static com.oracle.graal.python.nodes.ErrorMessages.DESCRIPTOR_NEED_OBJ;
import static com.oracle.graal.python.nodes.ErrorMessages.SEP_MUST_BE_ASCII;
import static com.oracle.graal.python.nodes.ErrorMessages.SEP_MUST_BE_LENGTH_1;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__BOOL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__CONTAINS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__IMOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LEN__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__MOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__MUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RMOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RMUL__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.UnicodeEncodeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ClinicConverterFactory;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.SysModuleBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.common.IndexNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.iterator.PSequenceIterator;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.str.StringNodes;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.tuple.TupleBuiltins;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.TypeBuiltins;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.builtins.ListNodes;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentCastNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToByteNode;
import com.oracle.graal.python.nodes.util.CastToJavaByteNode;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.formatting.BytesFormatProcessor;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.IntSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PByteArray, PythonBuiltinClassType.PBytes})
public class BytesBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return BytesBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(PythonCore core) {
        super.initialize(core);
        builtinConstants.put(SpecialAttributeNames.__DOC__, //
                        "bytes(iterable_of_ints) -> bytes\n" + //
                                        "bytes(string, encoding[, errors]) -> bytes\n" + //
                                        "bytes(bytes_or_buffer) -> immutable copy of bytes_or_buffer\n" + //
                                        "bytes(int) -> bytes object of size given by the parameter initialized with null bytes\n" + //
                                        "bytes() -> empty bytes object\n\n" + //
                                        "Construct an immutable array of bytes from:\n" + //
                                        "  - an iterable yielding integers in range(256)\n" + //
                                        "  - a text string encoded using the specified encoding\n" + //
                                        "  - any object implementing the buffer API.\n" + //
                                        "  - an integer");
    }

    public static CodingErrorAction toCodingErrorAction(String errors) {
        switch (errors) {
            case "strict":
                return CodingErrorAction.REPORT;
            case "ignore":
                return CodingErrorAction.IGNORE;
            case "replace":
                return CodingErrorAction.REPLACE;
        }
        return null;
    }

    public static CodingErrorAction toCodingErrorAction(String errors, PRaiseNode n) {
        CodingErrorAction action = toCodingErrorAction(errors);
        if (action != null) {
            return action;
        }
        throw n.raise(PythonErrorType.LookupError, ErrorMessages.UNKNOWN_ERROR_HANDLER, errors);
    }

    public static CodingErrorAction toCodingErrorAction(String errors, PythonBuiltinBaseNode n) {
        CodingErrorAction action = toCodingErrorAction(errors);
        if (action != null) {
            return action;
        }
        throw n.raise(PythonErrorType.LookupError, ErrorMessages.UNKNOWN_ERROR_HANDLER, errors);
    }

    @CompilerDirectives.TruffleBoundary
    public static byte[] stringToByte(String source, String encoding, String error, PRaiseNode raiseNode) {
        String encodingName = encoding.equals("latin-1") ? "ISO-8859-1" : encoding;
        CodingErrorAction action = toCodingErrorAction(error, raiseNode);
        try {
            return doEncode(Charset.forName(encodingName), source, action);
        } catch (CharacterCodingException e) {
            throw raiseNode.raise(UnicodeEncodeError, "%m", e);
        }
    }

    @CompilerDirectives.TruffleBoundary
    public static byte[] doEncode(Charset charset, String string, CodingErrorAction action) throws CharacterCodingException {
        CharsetEncoder encoder = charset.newEncoder();
        encoder.onMalformedInput(action).onUnmappableCharacter(action);
        CharBuffer buf = CharBuffer.allocate(string.length());
        buf.put(string);
        buf.flip();
        ByteBuffer encoded = encoder.encode(buf);
        byte[] barr = new byte[encoded.remaining()];
        encoded.get(barr);
        return barr;
    }

    @Builtin(name = __INIT__, minNumOfPositionalArgs = 1, takesVarArgs = true)
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonBuiltinNode {

        @SuppressWarnings("unused")
        @Specialization
        public Object byteDone(VirtualFrame frame, PBytes self, Object args) {
            return PNone.NONE;
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
            return raise(TypeError, ErrorMessages.OBJ_INDEX_MUST_BE_INT_OR_SLICES, "byte", key);
        }

        protected static SequenceStorageNodes.GetItemNode createGetItem() {
            return SequenceStorageNodes.GetItemNode.create(IndexNodes.NormalizeIndexNode.create(), (s, f) -> f.createBytes(s));
        }
    }

    @Builtin(name = __REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        public static Object repr(PBytes self,
                        @Cached SequenceStorageNodes.GetInternalByteArrayNode getBytes,
                        @Cached SequenceStorageNodes.LenNode lenNode) {
            SequenceStorage store = self.getSequenceStorage();
            byte[] bytes = getBytes.execute(store);
            int len = lenNode.execute(store);
            StringBuilder sb = PythonUtils.newStringBuilder();
            BytesUtils.reprLoop(sb, bytes, len);
            return PythonUtils.sbToString(sb);
        }
    }

    // bytes.translate(table, delete=b'')
    @Builtin(name = "translate", minNumOfPositionalArgs = 2, parameterNames = {"self", "table", "delete"})
    @GenerateNodeFactory
    public abstract static class TranslateNode extends BaseTranslateNode {

        @Specialization(guards = "isNoValue(delete)")
        public static PBytes translate(PBytes self, @SuppressWarnings("unused") PNone table, @SuppressWarnings("unused") PNone delete) {
            return self;
        }

        @Specialization(guards = "!isNone(table)")
        PBytes translate(PBytes self, Object table, @SuppressWarnings("unused") PNone delete,
                        @Cached.Shared("profile") @Cached ConditionProfile isLenTable256Profile,
                        @Cached.Shared("toBytes") @Cached BytesNodes.ToBytesNode toBytesNode) {
            byte[] bTable = toBytesNode.execute(table);
            checkLengthOfTable(bTable, isLenTable256Profile);
            byte[] bSelf = toBytesNode.execute(self);

            Result result = translate(bSelf, bTable);
            if (result.changed) {
                return factory().createBytes(result.array);
            }
            return self;
        }

        @Specialization(guards = "isNone(table)")
        PBytes delete(PBytes self, @SuppressWarnings("unused") PNone table, Object delete,
                        @Cached.Shared("toBytes") @Cached BytesNodes.ToBytesNode toBytesNode) {
            byte[] bSelf = toBytesNode.execute(self);
            byte[] bDelete = toBytesNode.execute(delete);

            Result result = delete(bSelf, bDelete);
            if (result.changed) {
                return factory().createBytes(result.array);
            }
            return self;
        }

        @Specialization(guards = {"!isPNone(table)", "!isPNone(delete)"})
        PBytes translateAndDelete(PBytes self, Object table, Object delete,
                        @Cached.Shared("profile") @Cached ConditionProfile isLenTable256Profile,
                        @Cached.Shared("toBytes") @Cached BytesNodes.ToBytesNode toBytesNode) {
            byte[] bTable = toBytesNode.execute(table);
            checkLengthOfTable(bTable, isLenTable256Profile);
            byte[] bDelete = toBytesNode.execute(delete);
            byte[] bSelf = toBytesNode.execute(self);

            Result result = translateAndDelete(bSelf, bTable, bDelete);
            if (result.changed) {
                return factory().createBytes(result.array);
            }
            return self;
        }
    }

    // bytes.fromhex()
    @Builtin(name = "fromhex", minNumOfPositionalArgs = 2, isClassmethod = true)
    @GenerateNodeFactory
    public abstract static class FromHexNode extends PythonBinaryBuiltinNode {

        @Specialization
        PBytes doString(PythonBuiltinClass cls, String str) {
            return factory().createBytes(cls, BytesUtils.fromHex(str, getRaiseNode()));
        }

        @Specialization
        PBytes doGeneric(PythonBuiltinClass cls, Object strObj,
                        @Cached CastToJavaStringNode castToJavaStringNode) {
            try {
                String str = castToJavaStringNode.execute(strObj);
                return factory().createBytes(cls, BytesUtils.fromHex(str, getRaiseNode()));
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
                PBytes bytes = factory().createBytes(BytesUtils.fromHex(str, getRaiseNode()));
                return callNode.varArgExecute(frame, null, new Object[]{cls, bytes}, new PKeyword[0]);
            } catch (CannotCastException e) {
                throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.ARG_MUST_BE_S_NOT_P, "fromhex()", "str", strObj);
            }
        }
    }

    // All below builtins are shared with Bytearray

    // bytes.join(iterable)
    // bytearray.join(iterable)
    @Builtin(name = "join", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class JoinNode extends PythonBinaryBuiltinNode {
        @Specialization
        PBytesLike join(VirtualFrame frame, PBytesLike self, Object iterable,
                        @Cached SequenceStorageNodes.ToByteArrayNode toByteArrayNode,
                        @Cached BytesNodes.BytesJoinNode bytesJoinNode,
                        @Cached BytesNodes.CreateBytesNode create) {
            byte[] res = bytesJoinNode.execute(frame, toByteArrayNode.execute(self.getSequenceStorage()), iterable);
            return create.execute(factory(), self, res);
        }

        @Fallback
        @SuppressWarnings("unused")
        public Object doGeneric(Object self, Object arg) {
            throw raise(TypeError, ErrorMessages.CAN_ONLY_JOIN_ITERABLE);
        }
    }

    @Builtin(name = __ADD__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class AddNode extends PythonBinaryBuiltinNode {

        @Specialization
        public PBytesLike add(PBytesLike self, PBytesLike other,
                        @Cached("createWithOverflowError()") SequenceStorageNodes.ConcatNode concatNode,
                        @Cached BytesNodes.CreateBytesNode create) {
            SequenceStorage res = concatNode.execute(self.getSequenceStorage(), other.getSequenceStorage());
            return create.execute(factory(), self, res);
        }

        @Specialization(guards = "bufferLib.isBuffer(buffer)", limit = "3")
        public PBytesLike add(PBytesLike self, Object buffer,
                        @CachedLibrary("buffer") PythonObjectLibrary bufferLib,
                        @Cached("createWithOverflowError()") SequenceStorageNodes.ConcatNode concatNode,
                        @Cached BytesNodes.CreateBytesNode create) {
            try {
                byte[] bytes = bufferLib.getBufferBytes(buffer);
                SequenceStorage res = concatNode.execute(self.getSequenceStorage(), new ByteSequenceStorage(bytes));
                return create.execute(factory(), self, res);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }

        @Fallback
        public Object add(Object self, Object other) {
            throw raise(TypeError, ErrorMessages.CANT_CONCAT_S_TO_P, self, other);
        }
    }

    @Builtin(name = __RMUL__, minNumOfPositionalArgs = 2)
    @Builtin(name = __MUL__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class MulNode extends PythonBinaryBuiltinNode {
        @Specialization
        public PBytesLike mul(VirtualFrame frame, PBytesLike self, int times,
                        @Cached("createWithOverflowError()") SequenceStorageNodes.RepeatNode repeatNode,
                        @Cached BytesNodes.CreateBytesNode create) {
            SequenceStorage res = repeatNode.execute(frame, self.getSequenceStorage(), times);
            return create.execute(factory(), self, res);
        }

        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()")
        public PBytesLike mul(VirtualFrame frame, PBytesLike self, Object times,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @Cached("createWithOverflowError()") SequenceStorageNodes.RepeatNode repeatNode,
                        @Cached BytesNodes.CreateBytesNode create,
                        @CachedLibrary("times") PythonObjectLibrary lib) {
            SequenceStorage res = repeatNode.execute(frame, self.getSequenceStorage(), getTimesInt(frame, times, hasFrame, lib));
            return create.execute(factory(), self, res);
        }

        private static int getTimesInt(VirtualFrame frame, Object times, ConditionProfile hasFrame, PythonObjectLibrary lib) {
            if (hasFrame.profile(frame != null)) {
                return lib.asSizeWithState(times, PArguments.getThreadState(frame));
            } else {
                return lib.asSize(times);
            }
        }

        @Fallback
        public Object mul(@SuppressWarnings("unused") Object self, Object other) {
            throw raise(TypeError, ErrorMessages.CANT_MULTIPLY_SEQ_BY_NON_INT, other);
        }
    }

    @Builtin(name = __EQ__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @ImportStatic(PGuards.class)
    public abstract static class EqNode extends PythonBinaryBuiltinNode {
        @Child private SequenceStorageNodes.CmpNode eqNode;

        @Specialization
        boolean eq(VirtualFrame frame, PBytesLike self, PBytesLike other) {
            return getEqNode().execute(frame, self.getSequenceStorage(), other.getSequenceStorage());
        }

        @Fallback
        public Object eq(Object self, @SuppressWarnings("unused") Object other) {
            if (self instanceof PBytesLike) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            throw raise(TypeError, ErrorMessages.DESCRIPTOR_REQUIRES_OBJ, "__eq__", "bytes-like", self);
        }

        private SequenceStorageNodes.CmpNode getEqNode() {
            if (eqNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                eqNode = insert(SequenceStorageNodes.CmpNode.createEq());
            }
            return eqNode;
        }
    }

    @Builtin(name = __NE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @ImportStatic(PGuards.class)
    public abstract static class NeNode extends PythonBinaryBuiltinNode {
        @Child SequenceStorageNodes.CmpNode eqNode;

        @Specialization
        boolean ne(VirtualFrame frame, PBytesLike self, PBytesLike other) {
            return !getEqNode().execute(frame, self.getSequenceStorage(), other.getSequenceStorage());
        }

        @Fallback
        Object ne(Object self, @SuppressWarnings("unused") Object other) {
            if (self instanceof PBytesLike) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            throw raise(TypeError, ErrorMessages.DESCRIPTOR_REQUIRES_OBJ, "__ne__", "bytes-like", self);
        }

        private SequenceStorageNodes.CmpNode getEqNode() {
            if (eqNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                eqNode = insert(SequenceStorageNodes.CmpNode.createEq());
            }
            return eqNode;
        }
    }

    public abstract static class CmpNode extends PythonBinaryBuiltinNode {
        @Child private BytesNodes.CmpNode cmpNode;

        int cmp(VirtualFrame frame, PBytesLike self, PBytesLike other) {
            if (cmpNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                cmpNode = insert(BytesNodes.CmpNode.create());
            }
            return cmpNode.execute(frame, self, other);
        }

    }

    @Builtin(name = __LT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class LtNode extends CmpNode {
        @Specialization
        boolean doBytes(VirtualFrame frame, PBytesLike self, PBytesLike other) {
            return cmp(frame, self, other) < 0;
        }

        @Fallback
        @SuppressWarnings("unused")
        public static Object doGeneric(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __LE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class LeNode extends CmpNode {
        @Specialization
        boolean doBytes(VirtualFrame frame, PBytesLike self, PBytesLike other) {
            return cmp(frame, self, other) <= 0;
        }

        @Fallback
        @SuppressWarnings("unused")
        public static Object doGeneric(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __GT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GtNode extends CmpNode {
        @Specialization
        boolean doBytes(VirtualFrame frame, PBytesLike self, PBytesLike other) {
            return cmp(frame, self, other) > 0;
        }

        @Fallback
        @SuppressWarnings("unused")
        public static Object doGeneric(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __GE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GeNode extends CmpNode {
        @Specialization
        boolean doBytes(VirtualFrame frame, PBytesLike self, PBytesLike other) {
            return cmp(frame, self, other) >= 0;
        }

        @Fallback
        @SuppressWarnings("unused")
        public static Object doGeneric(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __BOOL__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class BoolNode extends PythonUnaryBuiltinNode {

        @Specialization(guards = "isEmptyStorage(byteArray)")
        public static boolean doEmpty(@SuppressWarnings("unused") PBytesLike byteArray) {
            return false;
        }

        @Specialization(guards = "isIntStorage(byteArray)")
        public static boolean doInt(PBytesLike byteArray) {
            IntSequenceStorage store = (IntSequenceStorage) byteArray.getSequenceStorage();
            return store.length() != 0;
        }

        @Specialization(guards = "isByteStorage(byteArray)")
        public static boolean doByte(PBytesLike byteArray) {
            ByteSequenceStorage store = (ByteSequenceStorage) byteArray.getSequenceStorage();
            return store.length() != 0;
        }

        @Specialization
        static boolean doLen(PBytesLike operand,
                        @Cached("create()") SequenceStorageNodes.LenNode lenNode) {
            return lenNode.execute(operand.getSequenceStorage()) != 0;
        }

        @Fallback
        static Object doGeneric(@SuppressWarnings("unused") Object self) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __IMOD__, minNumOfPositionalArgs = 2)
    @Builtin(name = __MOD__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class ModNode extends PythonBinaryBuiltinNode {

        @Specialization(limit = "3")
        Object mod(PBytesLike self, Object right,
                        @Cached BytesNodes.CreateBytesNode create,
                        @CachedLibrary("self") PythonObjectLibrary selfLib,
                        @Cached("create(__GETITEM__)") LookupAndCallBinaryNode getItemNode,
                        @Cached TupleBuiltins.GetItemNode getTupleItemNode,
                        @CachedContext(PythonLanguage.class) PythonContext context) {
            byte[] data = format(getBytes(selfLib, self), right, getItemNode, getTupleItemNode, context, getRaiseNode());
            return create.execute(factory(), self, data);
        }

        protected static byte[] format(byte[] self, Object right, LookupAndCallBinaryNode getItemNode, TupleBuiltins.GetItemNode getTupleItemNode, PythonContext context, PRaiseNode raiseNode) {
            BytesFormatProcessor formatter = new BytesFormatProcessor(context.getCore(), raiseNode, getItemNode, getTupleItemNode, self);
            return formatter.format(right);
        }
    }

    @Builtin(name = __RMOD__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class RModNode extends PythonBinaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        Object mod(Object self, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __LEN__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class LenNode extends PythonUnaryBuiltinNode {
        @Specialization
        public static int len(PBytesLike self,
                        @Cached("create()") SequenceStorageNodes.LenNode lenNode) {
            return lenNode.execute(self.getSequenceStorage());
        }
    }

    @Builtin(name = __CONTAINS__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class ContainsNode extends PythonBinaryBuiltinNode {

        @Specialization
        boolean contains(PBytesLike self, PBytesLike other,
                        @Cached.Shared("len") @Cached SequenceStorageNodes.LenNode lenNode,
                        @Cached("create()") BytesNodes.FindNode findNode) {
            int len1 = lenNode.execute(self.getSequenceStorage());
            return findNode.execute(self.getSequenceStorage(), len1, other, 0, len1) != -1;
        }

        @Specialization(guards = "!isBytes(other)")
        boolean contains(VirtualFrame frame, PBytesLike self, Object other,
                        @Cached.Shared("len") @Cached SequenceStorageNodes.LenNode lenNode,
                        @Cached SequenceStorageNodes.GetInternalByteArrayNode getBytes,
                        @Cached("create()") BytesNodes.FindNode findNode,
                        @Cached("createCast()") CastToByteNode cast) {

            int len1 = lenNode.execute(self.getSequenceStorage());
            byte[] bytes = getBytes.execute(self.getSequenceStorage());
            return findNode.execute(bytes, len1, cast.execute(frame, other), 0, len1) != -1;
        }

        protected CastToByteNode createCast() {
            return CastToByteNode.create(null, val -> {
                throw raise(TypeError, ErrorMessages.BYTESLIKE_OBJ_REQUIRED, val);
            });
        }

    }

    @Builtin(name = __ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        PSequenceIterator contains(PBytesLike self) {
            return factory().createSequenceIterator(self);
        }
    }

    abstract static class PrefixSuffixBaseNode extends PythonQuaternaryClinicBuiltinNode {

        private static final String INVALID_RECEIVER = "Method requires a 'bytes' object, got '%p'";
        private static final String INVALID_FIRST_ARG = "first arg must be bytes or a tuple of bytes, not %p";
        private static final String INVALID_ELEMENT_TYPE = "a bytes-like object is required, not '%p'";

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            CompilerAsserts.neverPartOfCompilation();
            throw new IllegalStateException("abstract method");
        }

        // common and specialized cases --------------------

        @Specialization(guards = "!isPTuple(substr)")
        boolean doPrefixStartEnd(PBytesLike self, Object substr, int start, int end,
                        @Cached SequenceStorageNodes.GetInternalByteArrayNode getBytes,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @Cached("createToBytes()") BytesNodes.ToBytesNode tobytes) {
            byte[] bytes = getBytes.execute(self.getSequenceStorage());
            int len = lenNode.execute(self.getSequenceStorage());
            byte[] substrBytes = tobytes.execute(substr);
            int begin = adjustStartIndex(start, len);
            int last = adjustEndIndex(end, len);
            return doIt(bytes, substrBytes, begin, last);
        }

        @Specialization
        boolean doTuplePrefixStartEnd(PBytesLike self, PTuple substrs, int start, int end,
                        @Cached SequenceStorageNodes.GetInternalByteArrayNode getBytes,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @Cached("createToBytesFromTuple()") BytesNodes.ToBytesNode tobytes,
                        @Cached SequenceNodes.GetObjectArrayNode getObjectArrayNode) {
            byte[] bytes = getBytes.execute(self.getSequenceStorage());
            int len = lenNode.execute(self.getSequenceStorage());
            int begin = adjustStartIndex(start, len);
            int last = adjustEndIndex(end, len);
            return doIt(bytes, substrs, begin, last, tobytes, getObjectArrayNode);
        }

        @Fallback
        boolean doGeneric(@SuppressWarnings("unused") Object self, @SuppressWarnings("unused") Object substr,
                        @SuppressWarnings("unused") Object start, @SuppressWarnings("unused") Object end) {
            throw raise(TypeError, INVALID_RECEIVER, substr);
        }

        // the actual operation; will be overridden by subclasses
        @SuppressWarnings("unused")
        protected boolean doIt(byte[] bytes, byte[] prefix, int start, int end) {
            CompilerAsserts.neverPartOfCompilation();
            throw new IllegalStateException("should not reach");
        }

        private boolean doIt(byte[] self, PTuple substrs, int start, int stop,
                        BytesNodes.ToBytesNode tobytes,
                        SequenceNodes.GetObjectArrayNode getObjectArrayNode) {
            for (Object element : getObjectArrayNode.execute(substrs)) {
                byte[] bytes = tobytes.execute(element);
                if (doIt(self, bytes, start, stop)) {
                    return true;
                }
            }
            return false;
        }

        static BytesNodes.ToBytesNode createToBytes() {
            return BytesNodes.ToBytesNode.create(PythonBuiltinClassType.TypeError, INVALID_FIRST_ARG);
        }

        static BytesNodes.ToBytesNode createToBytesFromTuple() {
            return BytesNodes.ToBytesNode.create(PythonBuiltinClassType.TypeError, INVALID_ELEMENT_TYPE);
        }
    }

    // bytes.startswith(prefix[, start[, end]])
    // bytearray.startswith(prefix[, start[, end]])
    @Builtin(name = "startswith", minNumOfPositionalArgs = 2, parameterNames = {"$self", "prefix", "start", "end"})
    @ArgumentClinic(name = "start", conversion = ArgumentClinic.ClinicConversion.SliceIndex, defaultValue = "0", useDefaultForNone = true)
    @ArgumentClinic(name = "end", conversion = ArgumentClinic.ClinicConversion.SliceIndex, defaultValue = "Integer.MAX_VALUE", useDefaultForNone = true)
    @GenerateNodeFactory
    public abstract static class StartsWithNode extends PrefixSuffixBaseNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BytesBuiltinsClinicProviders.StartsWithNodeClinicProviderGen.INSTANCE;
        }

        @Override
        protected boolean doIt(byte[] bytes, byte[] prefix, int start, int end) {
            // start and end must be normalized indices for 'bytes'
            assert start >= 0;
            assert end >= 0 && end <= bytes.length;

            if (end - start < prefix.length) {
                return false;
            }
            for (int i = 0; i < prefix.length; i++) {
                if (bytes[start + i] != prefix[i]) {
                    return false;
                }
            }
            return true;
        }
    }

    // bytes.endswith(suffix[, start[, end]])
    // bytearray.endswith(suffix[, start[, end]])
    @Builtin(name = "endswith", minNumOfPositionalArgs = 2, parameterNames = {"$self", "suffix", "start", "end"})
    @ArgumentClinic(name = "start", conversion = ArgumentClinic.ClinicConversion.SliceIndex, defaultValue = "0", useDefaultForNone = true)
    @ArgumentClinic(name = "end", conversion = ArgumentClinic.ClinicConversion.SliceIndex, defaultValue = "Integer.MAX_VALUE", useDefaultForNone = true)
    @GenerateNodeFactory
    public abstract static class EndsWithNode extends PrefixSuffixBaseNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BytesBuiltinsClinicProviders.EndsWithNodeClinicProviderGen.INSTANCE;
        }

        @Override
        protected boolean doIt(byte[] bytes, byte[] suffix, int start, int end) {
            // start and end must be normalized indices for 'bytes'
            assert start >= 0;
            assert end >= 0 && end <= bytes.length;

            int suffixLen = suffix.length;
            if (end - start < suffixLen) {
                return false;
            }
            for (int i = 0; i < suffix.length; i++) {
                if (bytes[end - suffixLen + i] != suffix[i]) {
                    return false;
                }
            }
            return true;
        }
    }

    // bytes.index(x)
    // bytearray.index(x)
    @Builtin(name = "index", minNumOfPositionalArgs = 2, parameterNames = {"$self", "sub", "start", "end"})
    @ArgumentClinic(name = "start", conversion = ArgumentClinic.ClinicConversion.SliceIndex, defaultValue = "0", useDefaultForNone = true)
    @ArgumentClinic(name = "end", conversion = ArgumentClinic.ClinicConversion.SliceIndex, defaultValue = "Integer.MAX_VALUE", useDefaultForNone = true)
    @GenerateNodeFactory
    public abstract static class IndexNode extends PythonQuaternaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BytesBuiltinsClinicProviders.IndexNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        int index(PBytesLike self, Object arg, int start, int end,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @Cached BytesNodes.FindNode findNode) {
            int len1 = lenNode.execute(self.getSequenceStorage());
            int begin = adjustStartIndex(start, len1);
            int last = adjustEndIndex(end, len1);
            return checkResult(findNode.execute(self.getSequenceStorage(), last, arg, begin, last));
        }

        private int checkResult(int result) {
            if (result == -1) {
                throw raise(PythonBuiltinClassType.ValueError, ErrorMessages.SUBSECTION_NOT_FOUND);
            }
            return result;
        }
    }

    // bytes.rindex(x)
    // bytearray.rindex(x)
    @Builtin(name = "rindex", minNumOfPositionalArgs = 2, parameterNames = {"$self", "sub", "start", "end"})
    @ArgumentClinic(name = "start", conversion = ArgumentClinic.ClinicConversion.SliceIndex, defaultValue = "0", useDefaultForNone = true)
    @ArgumentClinic(name = "end", conversion = ArgumentClinic.ClinicConversion.SliceIndex, defaultValue = "Integer.MAX_VALUE", useDefaultForNone = true)
    @GenerateNodeFactory
    public abstract static class RIndexNode extends PythonQuaternaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BytesBuiltinsClinicProviders.RIndexNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        int indexWithStartEnd(PBytesLike self, Object arg, int start, int end,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @Cached BytesNodes.RFindNode rfindNode) {
            int len1 = lenNode.execute(self.getSequenceStorage());
            int begin = adjustStartIndex(start, len1);
            int last = adjustEndIndex(end, len1);
            return checkResult(rfindNode.execute(self.getSequenceStorage(), last, arg, begin, last));
        }

        private int checkResult(int result) {
            if (result == -1) {
                throw raise(PythonBuiltinClassType.ValueError, ErrorMessages.SUBSECTION_NOT_FOUND);
            }
            return result;
        }
    }

    public abstract static class PartitionAbstractNode extends PythonBinaryBuiltinNode {

        protected static boolean isEmptySep(PBytesLike sep, SequenceStorageNodes.LenNode lenNode) {
            return lenNode.execute(sep.getSequenceStorage()) == 0;
        }

        @Specialization(guards = "!isEmptySep(sep, lenNode)")
        PTuple partition(VirtualFrame frame, PBytesLike self, PBytesLike sep,
                        @Cached ConditionProfile notFound,
                        @Cached BytesNodes.ToBytesNode toBytesNode,
                        @Cached BytesNodes.CreateBytesNode createBytesNode,
                        @Cached SequenceStorageNodes.LenNode lenNode) {
            int len = lenNode.execute(self.getSequenceStorage());
            int lenSep = lenNode.execute(sep.getSequenceStorage());
            byte[] bytes = toBytesNode.execute(self);
            int idx = find(frame, self.getSequenceStorage(), sep, len);
            PBytesLike first, second, third;
            if (notFound.profile(idx == -1)) {
                second = createBytesNode.execute(factory(), self, PythonUtils.EMPTY_BYTE_ARRAY);
                if (isRight()) {
                    third = createBytesNode.execute(factory(), self, bytes);
                    first = createBytesNode.execute(factory(), self, PythonUtils.EMPTY_BYTE_ARRAY);
                } else {
                    first = createBytesNode.execute(factory(), self, bytes);
                    third = createBytesNode.execute(factory(), self, PythonUtils.EMPTY_BYTE_ARRAY);
                }
            } else {
                second = createBytesNode.execute(factory(), self, toBytesNode.execute(sep));
                if (idx == 0) {
                    first = createBytesNode.execute(factory(), self, PythonUtils.EMPTY_BYTE_ARRAY);
                    third = createBytesNode.execute(factory(), self, Arrays.copyOfRange(bytes, lenSep, len));
                } else if (idx == len - 1) {
                    first = createBytesNode.execute(factory(), self, Arrays.copyOfRange(bytes, 0, len - lenSep));
                    third = createBytesNode.execute(factory(), self, PythonUtils.EMPTY_BYTE_ARRAY);
                } else {
                    first = createBytesNode.execute(factory(), self, Arrays.copyOfRange(bytes, 0, idx));
                    third = createBytesNode.execute(factory(), self, Arrays.copyOfRange(bytes, idx + lenSep, len));
                }
            }
            return factory().createTuple(new Object[]{first, second, third});
        }

        @Specialization(guards = "isEmptySep(sep, lenNode)")
        Object error(@SuppressWarnings("unused") PBytesLike self, @SuppressWarnings("unused") PBytesLike sep,
                        @SuppressWarnings("unused") @Cached SequenceStorageNodes.LenNode lenNode) {
            return raise(ValueError, ErrorMessages.EMPTY_SEPARATOR);
        }

        @Specialization(guards = "!isBytes(sep)")
        Object fallback(@SuppressWarnings("unused") PBytesLike self, Object sep) {
            return raise(TypeError, ErrorMessages.BYTESLIKE_OBJ_REQUIRED, sep);
        }

        protected boolean isRight() {
            return false;
        }

        @SuppressWarnings("unused")
        protected int find(VirtualFrame frame, SequenceStorage storage, PBytesLike sep, int len) {
            CompilerAsserts.neverPartOfCompilation();
            throw new IllegalStateException("abstract method");
        }
    }

    // bytes.partition(sep)
    // bytearray.partition(sep)
    @Builtin(name = "partition", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class PartitionNode extends PartitionAbstractNode {
        @Child BytesNodes.FindNode findNode = BytesNodes.FindNode.create();

        @Override
        protected int find(VirtualFrame frame, SequenceStorage storage, PBytesLike sep, int len) {
            return findNode.execute(storage, len, sep, 0, len);
        }
    }

    // bytes.rpartition(sep)
    // bytearray.rpartition(sep)
    @Builtin(name = "rpartition", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class RPartitionNode extends PartitionAbstractNode {
        @Child BytesNodes.RFindNode findNode = BytesNodes.RFindNode.create();

        @Override
        protected boolean isRight() {
            return true;
        }

        @Override
        protected int find(VirtualFrame frame, SequenceStorage storage, PBytesLike sep, int len) {
            return findNode.execute(storage, len, sep, 0, len);
        }
    }

    // bytes.count(x)
    // bytearray.count(x)
    @Builtin(name = "count", minNumOfPositionalArgs = 2, parameterNames = {"$self", "sub", "start", "end"})
    @ArgumentClinic(name = "start", conversion = ArgumentClinic.ClinicConversion.SliceIndex, defaultValue = "0", useDefaultForNone = true)
    @ArgumentClinic(name = "end", conversion = ArgumentClinic.ClinicConversion.SliceIndex, defaultValue = "Integer.MAX_VALUE", useDefaultForNone = true)
    @GenerateNodeFactory
    @ImportStatic(SpecialMethodNames.class)
    public abstract static class CountNode extends PythonQuaternaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BytesBuiltinsClinicProviders.CountNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static int count(PBytesLike self, int sub, int start, int end,
                        @Cached.Shared("castNode") @Cached CastToJavaByteNode cast,
                        @Cached.Shared("len") @Cached SequenceStorageNodes.LenNode lenNode,
                        @Cached.Shared("toBytes") @Cached SequenceStorageNodes.GetInternalByteArrayNode toBytesNode) {
            byte[] bytes = toBytesNode.execute(self.getSequenceStorage());
            int len1 = lenNode.execute(self.getSequenceStorage());
            int begin = adjustStartIndex(start, len1);
            int last = adjustEndIndex(end, len1);
            return countSingle(bytes, begin, last, cast.execute(sub));
        }

        @Specialization(guards = "lib.canBeIndex(sub)")
        int count(VirtualFrame frame, PBytesLike self, Object sub, int start, int end,
                        @Cached.Shared("castNode") @Cached CastToJavaByteNode cast,
                        @Cached.Shared("toBytes") @Cached SequenceStorageNodes.GetInternalByteArrayNode toBytesNode,
                        @Cached.Shared("len") @Cached SequenceStorageNodes.LenNode lenNode,
                        @CachedLibrary(limit = "3") PythonObjectLibrary lib) {
            byte[] bytes = toBytesNode.execute(self.getSequenceStorage());
            int len1 = lenNode.execute(self.getSequenceStorage());
            int begin = adjustStartIndex(start, len1);
            int last = adjustEndIndex(end, len1);
            int elem = lib.asSizeWithFrame(sub, ValueError, frame);
            return countSingle(bytes, begin, last, cast.execute(elem));
        }

        @Specialization
        static int count(PBytesLike self, PBytesLike sub, int start, int end,
                        @Cached.Shared("toBytes") @Cached SequenceStorageNodes.GetInternalByteArrayNode toBytesNode,
                        @Cached.Shared("len") @Cached SequenceStorageNodes.LenNode lenNode,
                        @Cached BytesNodes.FindNode findNode) {
            int len1 = lenNode.execute(self.getSequenceStorage());
            int begin = adjustStartIndex(start, len1);
            int last = adjustEndIndex(end, len1);
            byte[] elems = toBytesNode.execute(sub.getSequenceStorage());
            int len2 = lenNode.execute(sub.getSequenceStorage());
            if (len2 == 1) {
                byte[] bytes = toBytesNode.execute(self.getSequenceStorage());
                return countSingle(bytes, begin, last, elems[0]);
            }
            return countMulti(self.getSequenceStorage(), begin, last, sub, len2, findNode);
        }

        @Specialization(guards = "bufferLib.isBuffer(sub)")
        static int count(PBytesLike self, Object sub, int start, int end,
                        @CachedLibrary(limit = "3") PythonObjectLibrary bufferLib,
                        @Cached.Shared("toBytes") @Cached SequenceStorageNodes.GetInternalByteArrayNode toBytesNode,
                        @Cached.Shared("len") @Cached SequenceStorageNodes.LenNode lenNode,
                        @Cached BytesNodes.FindNode findNode) {
            int len1 = lenNode.execute(self.getSequenceStorage());
            int begin = adjustStartIndex(start, len1);
            int last = adjustEndIndex(end, len1);
            try {
                byte[] elems = bufferLib.getBufferBytes(sub);
                int len2 = elems.length;
                if (len2 == 1) {
                    byte[] bytes = toBytesNode.execute(self.getSequenceStorage());
                    return countSingle(bytes, begin, last, elems[0]);
                }
                return countMulti(self.getSequenceStorage(), begin, last, sub, len2, findNode);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }

        @Fallback
        @SuppressWarnings("unused")
        int count(VirtualFrame frame, Object byteArray, Object elem, Object start, Object end) {
            throw raise(TypeError, ErrorMessages.ARG_SHOULD_BE_INT_BYTESLIKE_OBJ);
        }

        @CompilerDirectives.TruffleBoundary(allowInlining = true)
        private static int countSingle(byte[] bytes, int start, int end, int elem) {
            int count = 0;
            if ((end - start) < 0) {
                return 0;
            }

            for (int i = start; i < end; i++) {
                if (bytes[i] == elem) {
                    count++;
                }
            }
            return count;
        }

        @CompilerDirectives.TruffleBoundary(allowInlining = true)
        private static int countMulti(SequenceStorage bytes, int start, int end, Object elems, int len2, BytesNodes.FindNode findNode) {
            int idx = start;
            int count = 0;
            if ((end - start) < 0) {
                return 0;
            }
            if (len2 == 0) {
                return (end - start) + 1;
            }
            while (idx < end) {
                int found = findNode.execute(bytes, end, elems, idx, end);
                if (found == -1) {
                    break;
                }
                count++;
                idx = found + len2;
            }
            return count;
        }

    }

    // bytes.find(bytes[, start[, end]])
    // bytearray.find(bytes[, start[, end]])
    @Builtin(name = "find", minNumOfPositionalArgs = 2, parameterNames = {"$self", "sub", "start", "end"})
    @ArgumentClinic(name = "start", conversion = ArgumentClinic.ClinicConversion.SliceIndex, defaultValue = "0", useDefaultForNone = true)
    @ArgumentClinic(name = "end", conversion = ArgumentClinic.ClinicConversion.SliceIndex, defaultValue = "Integer.MAX_VALUE", useDefaultForNone = true)
    @GenerateNodeFactory
    abstract static class FindNode extends PythonQuaternaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BytesBuiltinsClinicProviders.FindNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static int find(PBytesLike self, Object sub, int start, int end,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @Cached BytesNodes.FindNode findNode) {
            int len1 = lenNode.execute(self.getSequenceStorage());
            int begin = adjustStartIndex(start, len1);
            int last = adjustEndIndex(end, len1);
            return findNode.execute(self.getSequenceStorage(), last, sub, begin, last);
        }
    }

    // bytes.rfind(bytes[, start[, end]])
    // bytearray.rfind(bytes[, start[, end]])
    @Builtin(name = "rfind", minNumOfPositionalArgs = 2, parameterNames = {"$self", "sub", "start", "end"})
    @ArgumentClinic(name = "start", conversion = ArgumentClinic.ClinicConversion.SliceIndex, defaultValue = "0", useDefaultForNone = true)
    @ArgumentClinic(name = "end", conversion = ArgumentClinic.ClinicConversion.SliceIndex, defaultValue = "Integer.MAX_VALUE", useDefaultForNone = true)
    @GenerateNodeFactory
    abstract static class RFindNode extends PythonQuaternaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BytesBuiltinsClinicProviders.RFindNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static int find(PBytesLike self, Object sub, int start, int end,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @Cached BytesNodes.RFindNode rfindNode) {
            int len1 = lenNode.execute(self.getSequenceStorage());
            int begin = adjustStartIndex(start, len1);
            int last = adjustEndIndex(end, len1);
            return rfindNode.execute(self.getSequenceStorage(), last, sub, begin, last);
        }
    }

    public abstract static class SepExpectByteNode extends ArgumentCastNode.ArgumentCastNodeWithRaise {
        private final Object defaultValue;

        protected SepExpectByteNode(Object defaultValue) {
            this.defaultValue = defaultValue;
        }

        @Override
        public abstract Object execute(VirtualFrame frame, Object value);

        @Specialization(guards = "isNoValue(none)")
        Object none(@SuppressWarnings("unused") PNone none) {
            return defaultValue;
        }

        @Specialization
        byte string(String str) {
            return check(getStringBytes(str));
        }

        @Specialization
        byte pstring(PString str,
                        @Cached StringNodes.StringMaterializeNode toStr) {
            return check(getStringBytes(toStr.execute(str)));
        }

        @Specialization
        byte doBytes(PBytesLike bytesLike,
                        @Cached BytesNodes.ToBytesNode toBytesNode) {
            return check(toBytesNode.execute(bytesLike));
        }

        @Specialization(guards = "bufferLib.isBuffer(buffer)", limit = "3")
        byte doBuffer(Object buffer,
                        @CachedLibrary("buffer") PythonObjectLibrary bufferLib) {
            try {
                return check(bufferLib.getBufferBytes(buffer));
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }

        @SuppressWarnings("unused")
        @Fallback
        byte error(VirtualFrame frame, Object value) {
            throw raise(TypeError, ErrorMessages.SEP_MUST_BE_STR_OR_BYTES);
        }

        private byte check(byte[] bytes) {
            if (bytes.length != 1) {
                throw raise(ValueError, SEP_MUST_BE_LENGTH_1);
            }
            return check(bytes[0]);
        }

        private byte check(byte b) {
            if ((b & 0xFF) > 127) {
                throw raise(ValueError, SEP_MUST_BE_ASCII);
            }
            return b;
        }

        @ClinicConverterFactory
        public static SepExpectByteNode create(@ClinicConverterFactory.DefaultValue Object defaultValue) {
            return BytesBuiltinsFactory.SepExpectByteNodeGen.create(defaultValue);
        }
    }

    @Builtin(name = "hex", minNumOfPositionalArgs = 1, parameterNames = {"$self", "sep", "bytes_per_sep_group"})
    @ArgumentClinic(name = "sep", conversionClass = SepExpectByteNode.class, defaultValue = "PNone.NO_VALUE")
    @ArgumentClinic(name = "bytes_per_sep_group", conversionClass = ExpectIntNode.class, defaultValue = "1")
    @GenerateNodeFactory
    abstract static class HexNode extends PythonTernaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BytesBuiltinsClinicProviders.HexNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        String none(PBytesLike self, @SuppressWarnings("unused") PNone sep, @SuppressWarnings("unused") int bytesPerSepGroup,
                        @Cached.Shared("p") @Cached ConditionProfile earlyExit,
                        @Cached.Shared("l") @Cached SequenceStorageNodes.LenNode lenNode,
                        @Cached.Shared("b") @Cached SequenceStorageNodes.GetInternalByteArrayNode getBytes,
                        @Cached.Shared("h") @Cached BytesNodes.ByteToHexNode toHexNode) {
            return hex(self, (byte) 0, 0, earlyExit, lenNode, getBytes, toHexNode);
        }

        @Specialization
        String hex(PBytesLike self, byte sep, int bytesPerSepGroup,
                        @Cached.Shared("p") @Cached ConditionProfile earlyExit,
                        @Cached.Shared("l") @Cached SequenceStorageNodes.LenNode lenNode,
                        @Cached.Shared("b") @Cached SequenceStorageNodes.GetInternalByteArrayNode getBytes,
                        @Cached.Shared("h") @Cached BytesNodes.ByteToHexNode toHexNode) {
            int len = lenNode.execute(self.getSequenceStorage());
            if (earlyExit.profile(len == 0)) {
                return "";
            }
            byte[] b = getBytes.execute(self.getSequenceStorage());
            return toHexNode.execute(b, len, sep, bytesPerSepGroup);
        }

        @SuppressWarnings("unused")
        @Fallback
        String err(Object self, Object sep, Object bytesPerSepGroup) {
            throw raise(TypeError, DESCRIPTOR_NEED_OBJ, "hex", "bytes");
        }
    }

    @Builtin(name = "isascii", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsASCIINode extends PythonUnaryBuiltinNode {

        @Specialization(limit = "2")
        static boolean check(PBytesLike self,
                        @Cached ConditionProfile earlyExit,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @CachedLibrary("self") PythonObjectLibrary lib) {
            int len = lenNode.execute(self.getSequenceStorage());
            if (earlyExit.profile(len == 0)) {
                return true;
            }
            byte[] b = getBytes(lib, self);
            for (int i = 0; i < len; i++) {
                if (b[i] < 0) {
                    return false;
                }
            }
            return true;
        }

    }

    @Builtin(name = "isalnum", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsAlnumNode extends PythonUnaryBuiltinNode {

        @Specialization(limit = "2")
        static boolean check(PBytesLike self,
                        @Cached ConditionProfile earlyExit,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @CachedLibrary("self") PythonObjectLibrary lib) {
            int len = lenNode.execute(self.getSequenceStorage());
            if (earlyExit.profile(len == 0)) {
                return false;
            }
            byte[] b = getBytes(lib, self);
            for (int i = 0; i < len; i++) {
                if (!BytesUtils.isAlnum(b[i])) {
                    return false;
                }
            }
            return true;
        }
    }

    @Builtin(name = "isalpha", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsAlphaNode extends PythonUnaryBuiltinNode {

        @Specialization(limit = "2")
        static boolean check(PBytesLike self,
                        @Cached ConditionProfile earlyExit,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @CachedLibrary("self") PythonObjectLibrary lib) {
            int len = lenNode.execute(self.getSequenceStorage());
            if (earlyExit.profile(len == 0)) {
                return false;
            }
            byte[] b = getBytes(lib, self);
            for (int i = 0; i < len; i++) {
                if (!BytesUtils.isAlpha(b[i])) {
                    return false;
                }
            }
            return true;
        }
    }

    @Builtin(name = "isdigit", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsDigitNode extends PythonUnaryBuiltinNode {

        @Specialization(limit = "2")
        static boolean check(PBytesLike self,
                        @Cached ConditionProfile earlyExit,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @CachedLibrary("self") PythonObjectLibrary lib) {
            int len = lenNode.execute(self.getSequenceStorage());
            if (earlyExit.profile(len == 0)) {
                return false;
            }
            byte[] b = getBytes(lib, self);
            for (int i = 0; i < len; i++) {
                if (!BytesUtils.isDigit(b[i])) {
                    return false;
                }
            }
            return true;
        }
    }

    @Builtin(name = "islower", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsLowerNode extends PythonUnaryBuiltinNode {

        @Specialization(limit = "2")
        static boolean check(PBytesLike self,
                        @Cached ConditionProfile earlyExit,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @CachedLibrary("self") PythonObjectLibrary lib) {
            int len = lenNode.execute(self.getSequenceStorage());
            if (earlyExit.profile(len == 0)) {
                return false;
            }
            byte[] b = getBytes(lib, self);
            int uncased = 0;
            for (int i = 0; i < len; i++) {
                byte ch = b[i];
                if (!BytesUtils.isLower(ch)) {
                    if (BytesUtils.toLower(ch) == BytesUtils.toUpper(ch)) {
                        uncased++;
                    } else {
                        return false;
                    }
                }
            }
            return uncased == 0 || len > uncased;
        }
    }

    @Builtin(name = "isupper", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsUpperNode extends PythonUnaryBuiltinNode {

        @Specialization(limit = "2")
        static boolean check(PBytesLike self,
                        @Cached ConditionProfile earlyExit,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @CachedLibrary("self") PythonObjectLibrary lib) {
            int len = lenNode.execute(self.getSequenceStorage());
            if (earlyExit.profile(len == 0)) {
                return false;
            }
            byte[] b = getBytes(lib, self);
            int uncased = 0;
            for (int i = 0; i < len; i++) {
                byte ch = b[i];
                if (!BytesUtils.isUpper(ch)) {
                    if (BytesUtils.toLower(ch) == BytesUtils.toUpper(ch)) {
                        uncased++;
                    } else {
                        return false;
                    }
                }
            }
            return uncased == 0 || len > uncased;
        }
    }

    @Builtin(name = "isspace", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsSpaceNode extends PythonUnaryBuiltinNode {

        @Specialization(limit = "2")
        static boolean check(PBytesLike self,
                        @Cached ConditionProfile earlyExit,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @CachedLibrary("self") PythonObjectLibrary lib) {
            int len = lenNode.execute(self.getSequenceStorage());
            if (earlyExit.profile(len == 0)) {
                return false;
            }
            byte[] b = getBytes(lib, self);
            for (int i = 0; i < len; i++) {
                if (!BytesUtils.isSpace(b[i])) {
                    return false;
                }
            }
            return true;
        }
    }

    @Builtin(name = "istitle", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsTitleNode extends PythonUnaryBuiltinNode {

        @Specialization(limit = "2")
        static boolean check(PBytesLike self,
                        @Cached ConditionProfile earlyExit,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @CachedLibrary("self") PythonObjectLibrary lib) {
            int len = lenNode.execute(self.getSequenceStorage());
            if (earlyExit.profile(len == 0)) {
                return false;
            }
            byte[] b = getBytes(lib, self);
            boolean cased = false;
            boolean previousIsCased = false;
            for (int i = 0; i < len; i++) {
                byte ch = b[i];

                if (BytesUtils.isUpper(ch)) {
                    if (previousIsCased) {
                        return false;
                    }
                    previousIsCased = true;
                    cased = true;
                } else if (BytesUtils.isLower(ch)) {
                    if (!previousIsCased) {
                        return false;
                    }
                    previousIsCased = true;
                    cased = true;
                } else {
                    previousIsCased = false;
                }
            }
            return cased;
        }
    }

    @Builtin(name = "center", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class CenterNode extends PythonBuiltinNode {

        @Specialization(guards = "isNoValue(fill)")
        PBytesLike none(PBytesLike self, int width, @SuppressWarnings("unused") PNone fill,
                        @Cached.Shared("copy") @Cached SequenceStorageNodes.CopyNode copyNode,
                        @Cached.Shared("createBytes") @Cached BytesNodes.CreateBytesNode create,
                        @Cached.Shared("tobyte") @CachedLibrary(limit = "2") PythonObjectLibrary lib,
                        @Cached.Shared("length") @Cached SequenceStorageNodes.LenNode lenNode) {
            int len = lenNode.execute(self.getSequenceStorage());
            if (checkSkip(len, width)) {
                return create.execute(factory(), self, copyNode.execute(self.getSequenceStorage()));
            }
            return create.execute(factory(), self, make(getBytes(lib, self), len, width, (byte) ' '));
        }

        @Specialization
        PBytesLike bytes(VirtualFrame frame, PBytesLike self, Object w, PBytesLike fill,
                        @Cached.Shared("length") @Cached SequenceStorageNodes.LenNode lenNode,
                        @Cached.Shared("copy") @Cached SequenceStorageNodes.CopyNode copyNode,
                        @Cached.Shared("createBytes") @Cached BytesNodes.CreateBytesNode create,
                        @Cached.Shared("tobyte") @CachedLibrary(limit = "2") PythonObjectLibrary lib,
                        @Cached("createBinaryProfile()") ConditionProfile errorProfile) {
            int len = lenNode.execute(self.getSequenceStorage());
            if (errorProfile.profile(lenNode.execute(fill.getSequenceStorage()) != 1)) {
                throw raise(TypeError, ErrorMessages.FILL_CHAR_MUST_BE_LENGTH_1);
            }
            int width = lib.asSizeWithState(w, PArguments.getThreadState(frame));
            if (checkSkip(len, width)) {
                return create.execute(factory(), self, copyNode.execute(self.getSequenceStorage()));
            }
            return create.execute(factory(), self, make(getBytes(lib, self), len, width, getBytes(lib, fill)[0]));
        }

        protected String methodName() {
            return "center()";
        }

        @Fallback
        @SuppressWarnings("unused")
        boolean err(VirtualFrame frame, Object self, Object w, Object fill) {
            throw raise(TypeError, ErrorMessages.BYTE_STRING_OF_LEN_ONE_ONLY, methodName(), fill);
        }

        protected byte[] pad(byte[] self, int l, int r, byte fill) {
            int left = (l < 0) ? 0 : l;
            int right = (r < 0) ? 0 : r;
            if (left == 0 && right == 0) {
                return self;
            }

            byte[] u = new byte[left + self.length + right];
            if (left > 0) {
                Arrays.fill(u, 0, left, fill);
            }
            System.arraycopy(self, 0, u, left, self.length);
            if (right > 0) {
                Arrays.fill(u, left + self.length, u.length, fill);
            }
            return u;
        }

        protected byte[] make(byte[] self, int len, int width, byte fillchar) {
            int marg = width - len;
            int left = marg / 2 + (marg & width & 1);
            return pad(self, left, marg - left, fillchar);
        }

        protected boolean checkSkip(int len, int width) {
            return len >= width;
        }
    }

    @Builtin(name = "ljust", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class LJustNode extends CenterNode {

        @Override
        protected String methodName() {
            return "ljust()";
        }

        @Override
        protected boolean checkSkip(int len, int width) {
            return (width - len) <= 0;
        }

        @Override
        protected byte[] make(byte[] self, int len, int width, byte fill) {
            int l = width - len;
            int resLen = l + len;
            byte[] res = new byte[resLen];
            PythonUtils.arraycopy(self, 0, res, 0, len);
            Arrays.fill(res, len, resLen, fill);
            return res;
        }

    }

    @Builtin(name = "rjust", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class RJustNode extends CenterNode {

        @Override
        protected String methodName() {
            return "rjust()";
        }

        @Override
        protected boolean checkSkip(int len, int width) {
            return (width - len) <= 0;
        }

        @Override
        protected byte[] make(byte[] self, int len, int width, byte fill) {
            int l = width - len;
            int resLen = l + len;
            byte[] res = new byte[resLen];
            Arrays.fill(res, 0, l, fill);
            for (int i = l, j = 0; i < (len + l); j++, i++) {
                res[i] = self[j];
            }
            return res;
        }

    }

    @Builtin(name = "replace", minNumOfPositionalArgs = 3, parameterNames = {"$self", "old", "replacement", "count"})
    @ArgumentClinic(name = "count", conversionClass = ExpectIntNode.class, defaultValue = "Integer.MAX_VALUE")
    @GenerateNodeFactory
    abstract static class ReplaceNode extends PythonQuaternaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BytesBuiltinsClinicProviders.ReplaceNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        PBytesLike replace(PBytesLike self, PBytesLike substr, PBytesLike replacement, int count,
                        @Cached SequenceStorageNodes.GetInternalByteArrayNode toInternalBytes,
                        @Cached BytesNodes.ToBytesNode toBytes,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @Cached BytesNodes.FindNode findNode,
                        @Cached ConditionProfile selfSubAreEmpty,
                        @Cached ConditionProfile selfIsEmpty,
                        @Cached ConditionProfile subIsEmpty,
                        @Cached BytesNodes.CreateBytesNode create) {
            int len = lenNode.execute(self.getSequenceStorage());
            byte[] bytes = toInternalBytes.execute(self.getSequenceStorage());
            byte[] subBytes = toBytes.execute(substr);
            byte[] replacementBytes = toBytes.execute(replacement);
            int maxcount = count < 0 ? Integer.MAX_VALUE : count;
            if (selfSubAreEmpty.profile(len == 0 && subBytes.length == 0)) {
                return create.execute(factory(), self, replacementBytes);
            }
            if (selfIsEmpty.profile(len == 0)) {
                return create.execute(factory(), self, PythonUtils.EMPTY_BYTE_ARRAY);
            }
            if (subIsEmpty.profile(subBytes.length == 0)) {
                return create.execute(factory(), self, replaceWithEmptySub(bytes, len, replacementBytes, maxcount));
            }
            // byte[] newBytes = doReplace(bytes, subBytes, replacementBytes);
            byte[] newBytes = replace(bytes, len, subBytes, replacementBytes, maxcount, findNode);
            return create.execute(factory(), self, newBytes);
        }

        @Fallback
        boolean error(@SuppressWarnings("unused") Object self, Object substr, @SuppressWarnings("unused") Object replacement, @SuppressWarnings("unused") Object count) {
            throw raise(TypeError, ErrorMessages.BYTESLIKE_OBJ_REQUIRED, substr);
        }

        @CompilerDirectives.TruffleBoundary(allowInlining = true)
        protected byte[] replaceWithEmptySub(byte[] bytes, int len, byte[] replacementBytes, int count) {
            int repLen = replacementBytes.length;
            byte[] result = new byte[len + repLen * Math.min(count, len + 1)];
            int replacements, i, j = 0;
            for (replacements = 0, i = 0; replacements < count && i < len; replacements++) {
                PythonUtils.arraycopy(replacementBytes, 0, result, j, repLen);
                j += repLen;
                result[j++] = bytes[i++];
            }
            if (replacements < count) {
                PythonUtils.arraycopy(replacementBytes, 0, result, j, repLen);
            }
            if (i < len) {
                PythonUtils.arraycopy(bytes, i, result, j, len - i);
            }
            return result;
        }

        @CompilerDirectives.TruffleBoundary(allowInlining = true)
        protected byte[] replace(byte[] bytes, int len, byte[] sub, byte[] replacementBytes, int count,
                        BytesNodes.FindNode findNode) {
            int i, j, pos, maxcount = count, subLen = sub.length, repLen = replacementBytes.length;
            List<byte[]> list = new ArrayList<>();

            int resultLen = 0;
            i = 0;
            while (maxcount-- > 0) {
                pos = findNode.execute(bytes, len, sub, i, len);
                if (pos < 0) {
                    break;
                }
                j = pos;
                list.add(copyOfRange(bytes, i, j));
                list.add(replacementBytes);
                resultLen += (j - i) + repLen;
                i = j + subLen;
            }

            if (i == 0) {
                return copyOfRange(bytes, 0, len);
            }

            list.add(copyOfRange(bytes, i, len));
            resultLen += (len - i);

            i = 0;
            byte[] result = new byte[resultLen];
            Iterator<byte[]> it = iterator(list);
            while (hasNext(it)) {
                byte[] b = next(it);
                PythonUtils.arraycopy(b, 0, result, i, b.length);
                i += b.length;
            }

            return result;
        }
    }

    @Builtin(name = "lower", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class LowerNode extends PythonUnaryBuiltinNode {

        @Specialization
        PBytesLike replace(PBytesLike self,
                        @Cached BytesNodes.ToBytesNode toBytes,
                        @Cached BytesNodes.CreateBytesNode create) {
            return create.execute(factory(), self, lower(toBytes.execute(self)));
        }

        @CompilerDirectives.TruffleBoundary
        protected static byte[] lower(byte[] bytes) {
            String string = BytesUtils.createASCIIString(bytes);
            return string.toLowerCase().getBytes(StandardCharsets.US_ASCII);
        }
    }

    @Builtin(name = "upper", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class UpperNode extends PythonUnaryBuiltinNode {

        @Specialization
        PBytesLike replace(PBytesLike self,
                        @Cached BytesNodes.ToBytesNode toBytes,
                        @Cached BytesNodes.CreateBytesNode create) {
            return create.execute(factory(), self, upper(toBytes.execute(self)));
        }

        @CompilerDirectives.TruffleBoundary
        protected static byte[] upper(byte[] bytes) {
            String string = BytesUtils.createASCIIString(bytes);
            return string.toUpperCase().getBytes(StandardCharsets.US_ASCII);
        }
    }

    public abstract static class ExpectIntNode extends ArgumentCastNode.ArgumentCastNodeWithRaise {
        private final int defaultValue;

        protected ExpectIntNode(int defaultValue) {
            this.defaultValue = defaultValue;
        }

        @Override
        public abstract Object execute(VirtualFrame frame, Object value);

        public abstract int executeInt(VirtualFrame frame, Object value);

        @Specialization(guards = "isNoValue(none)")
        int handleNone(@SuppressWarnings("unused") PNone none) {
            return defaultValue;
        }

        @Specialization
        static int doInt(int i) {
            // fast-path for the most common case
            return i;
        }

        @Specialization
        public int toInt(long x) {
            try {
                return PInt.intValueExact(x);
            } catch (OverflowException e) {
                throw raise(PythonErrorType.OverflowError, ErrorMessages.PYTHON_INT_TOO_LARGE_TO_CONV_TO, "C long");
            }
        }

        @Specialization
        public int toInt(PInt x) {
            try {
                return x.intValueExact();
            } catch (OverflowException e) {
                throw raise(PythonErrorType.OverflowError, ErrorMessages.PYTHON_INT_TOO_LARGE_TO_CONV_TO, "C long");
            }
        }

        @Specialization(guards = "!isNoValue(value)", limit = "3")
        int doOthers(VirtualFrame frame, Object value,
                        @Cached("createRec()") ExpectIntNode rec,
                        @CachedLibrary("value") PythonObjectLibrary lib) {
            if (lib.canBeIndex(value)) {
                return rec.executeInt(frame, lib.asIndexWithFrame(value, frame));
            }
            throw raise(TypeError, ErrorMessages.OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER, value);
        }

        protected ExpectIntNode createRec() {
            return BytesBuiltinsFactory.ExpectIntNodeGen.create(defaultValue);
        }

        @ClinicConverterFactory(shortCircuitPrimitive = ArgumentClinic.PrimitiveType.Int)
        public static ExpectIntNode create(@ClinicConverterFactory.DefaultValue int defaultValue) {
            return BytesBuiltinsFactory.ExpectIntNodeGen.create(defaultValue);
        }
    }

    public abstract static class ExpectByteLikeNode extends ArgumentCastNode.ArgumentCastNodeWithRaise {
        private final byte[] defaultValue;

        protected ExpectByteLikeNode(byte[] defaultValue) {
            this.defaultValue = defaultValue;
        }

        @Override
        public abstract byte[] execute(VirtualFrame frame, Object value);

        @Specialization
        byte[] handleNone(@SuppressWarnings("unused") PNone none) {
            return defaultValue;
        }

        @Specialization
        static byte[] doBytes(PBytesLike bytesLike,
                        @Cached BytesNodes.ToBytesNode toBytesNode) {
            return toBytesNode.execute(bytesLike);
        }

        @Specialization(guards = "bufferLib.isBuffer(buffer)", limit = "3")
        static byte[] doBuffer(Object buffer,
                        @CachedLibrary("buffer") PythonObjectLibrary bufferLib) {
            try {
                return bufferLib.getBufferBytes(buffer);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }

        @Fallback
        byte[] error(@SuppressWarnings("unused") VirtualFrame frame, Object value) {
            throw raise(TypeError, ErrorMessages.BYTESLIKE_OBJ_REQUIRED, value);
        }

        @ClinicConverterFactory
        public static ExpectByteLikeNode create(@ClinicConverterFactory.DefaultValue byte[] defaultValue) {
            return BytesBuiltinsFactory.ExpectByteLikeNodeGen.create(defaultValue);
        }
    }

    abstract static class AbstractSplitNode extends PythonTernaryClinicBuiltinNode {

        protected static final byte[] WHITESPACE = new byte[]{' '};

        @Override
        public abstract PList execute(VirtualFrame frame, Object bytes, Object sep, Object maxsplit);

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            CompilerAsserts.neverPartOfCompilation();
            throw new RuntimeException();
        }

        @SuppressWarnings("unused")
        protected List<byte[]> splitWhitespace(byte[] bytes, int size, int maxsplit) {
            CompilerAsserts.neverPartOfCompilation();
            throw new RuntimeException();
        }

        @SuppressWarnings("unused")
        protected List<byte[]> splitSingle(byte[] bytes, int size, byte sep, int maxsplit) {
            CompilerAsserts.neverPartOfCompilation();
            throw new RuntimeException();
        }

        @SuppressWarnings("unused")
        protected List<byte[]> splitDelimiter(byte[] bytes, int size, byte[] sep, int maxsplit) {
            CompilerAsserts.neverPartOfCompilation();
            throw new RuntimeException();
        }

        protected static boolean isEmptySep(byte[] sep) {
            return sep.length == 0;
        }

        protected static boolean isSingleSep(byte[] sep) {
            return sep.length == 1;
        }

        protected static boolean isWhitespace(byte[] sep) {
            return sep == WHITESPACE;
        }

        private static int adjustMaxSplit(int maxsplit) {
            return maxsplit < 0 ? Integer.MAX_VALUE : maxsplit;
        }

        @Specialization(guards = "isWhitespace(sep)")
        PList whitespace(PBytesLike self, @SuppressWarnings("unused") byte[] sep, int maxsplit,
                        @Cached.Shared("toBytes") @Cached SequenceStorageNodes.GetInternalByteArrayNode selfToBytesNode,
                        @Cached.Shared("length") @Cached SequenceStorageNodes.LenNode lenNode,
                        @Cached.Shared("append") @Cached ListNodes.AppendNode appendNode,
                        @Cached.Shared("create") @Cached BytesNodes.CreateBytesNode createBytesNode) {
            byte[] splitBs = selfToBytesNode.execute(self.getSequenceStorage());
            int len = lenNode.execute(self.getSequenceStorage());
            return getBytesResult(splitWhitespace(splitBs, len, adjustMaxSplit(maxsplit)), appendNode, self, createBytesNode);
        }

        @Specialization(guards = {"!isWhitespace(sep)", "isSingleSep(sep)"})
        PList single(PBytesLike self, byte[] sep, int maxsplit,
                        @Cached.Shared("toBytes") @Cached SequenceStorageNodes.GetInternalByteArrayNode selfToBytesNode,
                        @Cached.Shared("length") @Cached SequenceStorageNodes.LenNode lenNode,
                        @Cached.Shared("append") @Cached ListNodes.AppendNode appendNode,
                        @Cached.Shared("create") @Cached BytesNodes.CreateBytesNode createBytesNode) {
            byte[] splitBs = selfToBytesNode.execute(self.getSequenceStorage());
            int len = lenNode.execute(self.getSequenceStorage());
            return getBytesResult(splitSingle(splitBs, len, sep[0], adjustMaxSplit(maxsplit)), appendNode, self, createBytesNode);
        }

        @Specialization(guards = {"!isWhitespace(sep)", "!isEmptySep(sep)", "!isSingleSep(sep)"})
        PList split(PBytesLike self, byte[] sep, int maxsplit,
                        @Cached.Shared("toBytes") @Cached SequenceStorageNodes.GetInternalByteArrayNode selfToBytesNode,
                        @Cached.Shared("length") @Cached SequenceStorageNodes.LenNode lenNode,
                        @Cached.Shared("append") @Cached ListNodes.AppendNode appendNode,
                        @Cached.Shared("create") @Cached BytesNodes.CreateBytesNode createBytesNode) {
            byte[] splitBs = selfToBytesNode.execute(self.getSequenceStorage());
            int len = lenNode.execute(self.getSequenceStorage());
            return getBytesResult(splitDelimiter(splitBs, len, sep, adjustMaxSplit(maxsplit)), appendNode, self, createBytesNode);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"isEmptySep(sep)"})
        PList error(PBytesLike bytes, byte[] sep, int maxsplit) {
            throw raise(PythonErrorType.ValueError, ErrorMessages.EMPTY_SEPARATOR);
        }

        protected final PList getBytesResult(List<byte[]> bytes,
                        ListNodes.AppendNode appendNode,
                        PBytesLike self,
                        BytesNodes.CreateBytesNode createBytesNode) {
            PList result = factory().createList();
            Iterator<byte[]> it = iterator(bytes);
            while (hasNext(it)) {
                appendNode.execute(result, createBytesNode.execute(factory(), self, next(it)));
            }
            return result;
        }

        protected static Object asIndex(VirtualFrame frame, Object maxsplit, PythonObjectLibrary lib) {
            return lib.asIndexWithState(maxsplit, PArguments.getThreadState(frame));
        }
    }

    @Builtin(name = "split", minNumOfPositionalArgs = 1, parameterNames = {"$self", "sep", "maxsplit"})
    @ArgumentClinic(name = "sep", conversionClass = ExpectByteLikeNode.class, defaultValue = "BytesBuiltins.AbstractSplitNode.WHITESPACE")
    @ArgumentClinic(name = "maxsplit", conversionClass = ExpectIntNode.class, defaultValue = "Integer.MAX_VALUE")
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class SplitNode extends AbstractSplitNode {

        @Child BytesNodes.FindNode findNode = BytesNodes.FindNode.create();

        protected int find(byte[] bytes, int len, byte[] sep, int start, int end) {
            return findNode.execute(bytes, len, sep, start, end);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BytesBuiltinsClinicProviders.SplitNodeClinicProviderGen.INSTANCE;
        }

        @Override
        @TruffleBoundary
        protected List<byte[]> splitWhitespace(byte[] bytes, int len, int maxsplit) {
            int i, j, maxcount = maxsplit;
            List<byte[]> list = new ArrayList<>();

            i = 0;
            while (maxcount-- > 0) {
                while (i < len && BytesUtils.isSpace(bytes[i])) {
                    i++;
                }
                if (i == len) {
                    break;
                }
                j = i;
                i++;
                while (i < len && !BytesUtils.isSpace(bytes[i])) {
                    i++;
                }
                list.add(copyOfRange(bytes, j, i));
            }

            if (i < len) {
                /* Only occurs when maxcount was reached */
                /* Skip any remaining whitespace and copy to end of string */
                while (i < len && BytesUtils.isSpace(bytes[i])) {
                    i++;
                }
                if (i != len) {
                    list.add(copyOfRange(bytes, i, len));
                }
            }
            return list;
        }

        @Override
        protected List<byte[]> splitSingle(byte[] bytes, int len, byte sep, int maxsplit) {
            int i, j, maxcount = maxsplit;
            List<byte[]> list = new ArrayList<>();

            i = j = 0;
            while ((j < len) && (maxcount-- > 0)) {
                for (; j < len; j++) {
                    if (bytes[j] == sep) {
                        list.add(copyOfRange(bytes, i, j));
                        i = j = j + 1;
                        break;
                    }
                }
            }
            if (i <= len) {
                list.add(copyOfRange(bytes, i, len));
            }

            return list;
        }

        @Override
        protected List<byte[]> splitDelimiter(byte[] bytes, int len, byte[] sep, int maxsplit) {
            int i, j, pos, maxcount = maxsplit, sepLen = sep.length;
            List<byte[]> list = new ArrayList<>();

            i = 0;
            while (maxcount-- > 0) {
                pos = find(bytes, len, sep, i, len);
                if (pos < 0) {
                    break;
                }
                j = pos;
                list.add(copyOfRange(bytes, i, j));
                i = j + sepLen;
            }

            list.add(copyOfRange(bytes, i, len));

            return list;
        }
    }

    @Builtin(name = "rsplit", minNumOfPositionalArgs = 1, parameterNames = {"self", "sep", "maxsplit"})
    @ArgumentClinic(name = "sep", conversionClass = ExpectByteLikeNode.class, defaultValue = "BytesBuiltins.AbstractSplitNode.WHITESPACE")
    @ArgumentClinic(name = "maxsplit", conversionClass = ExpectIntNode.class, defaultValue = "Integer.MAX_VALUE")
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class RSplitNode extends AbstractSplitNode {

        @Child BytesNodes.RFindNode findNode = BytesNodes.RFindNode.create();

        protected int find(byte[] bytes, int len, byte[] sep, int start, int end) {
            return findNode.execute(bytes, len, sep, start, end);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BytesBuiltinsClinicProviders.RSplitNodeClinicProviderGen.INSTANCE;
        }

        @CompilerDirectives.TruffleBoundary
        private static void reverseList(ArrayList<byte[]> list) {
            Collections.reverse(list);
        }

        @Override
        protected List<byte[]> splitWhitespace(byte[] bytes, int len, int maxsplit) {
            int i, j, maxcount = maxsplit;
            ArrayList<byte[]> list = new ArrayList<>();

            i = len - 1;
            while (maxcount-- > 0) {
                while (i >= 0 && BytesUtils.isSpace(bytes[i])) {
                    i--;
                }
                if (i < 0) {
                    break;
                }
                j = i;
                i--;
                while (i >= 0 && !BytesUtils.isSpace(bytes[i])) {
                    i--;
                }
                list.add(copyOfRange(bytes, i + 1, j + 1));
            }

            if (i >= 0) {
                /* Only occurs when maxcount was reached */
                /* Skip any remaining whitespace and copy to beginning of string */
                while (i >= 0 && BytesUtils.isSpace(bytes[i])) {
                    i--;
                }
                if (i >= 0) {
                    list.add(copyOfRange(bytes, 0, i + 1));
                }
            }
            reverseList(list);
            return list;
        }

        @Override
        protected List<byte[]> splitSingle(byte[] bytes, int len, byte sep, int maxsplit) {
            int i, j, maxcount = maxsplit;
            ArrayList<byte[]> list = new ArrayList<>();

            i = j = len - 1;
            while ((i >= 0) && (maxcount-- > 0)) {
                for (; i >= 0; i--) {
                    if (bytes[i] == sep) {
                        list.add(copyOfRange(bytes, i + 1, j + 1));
                        j = i = i - 1;
                        break;
                    }
                }
            }
            if (j >= -1) {
                list.add(copyOfRange(bytes, 0, j + 1));
            }
            reverseList(list);
            return list;
        }

        @Override
        protected List<byte[]> splitDelimiter(byte[] bytes, int len, byte[] sep, int maxsplit) {
            int j, pos, maxcount = maxsplit, sepLen = sep.length;
            ArrayList<byte[]> list = new ArrayList<>();

            if (sepLen == 1) {
                return splitSingle(bytes, len, sep[0], maxcount);
            }

            j = len;
            while (maxcount-- > 0) {
                pos = find(bytes, len, sep, 0, j);
                if (pos < 0) {
                    break;
                }
                list.add(copyOfRange(bytes, pos + sepLen, j));
                j = pos;
            }
            list.add(copyOfRange(bytes, 0, j));
            reverseList(list);
            return list;

        }
    }

    // bytes.splitlines([keepends])
    // bytearray.splitlines([keepends])
    @Builtin(name = "splitlines", minNumOfPositionalArgs = 1, parameterNames = {"self", "keepends"})
    @GenerateNodeFactory
    public abstract static class SplitLinesNode extends PythonBinaryBuiltinNode {

        @Specialization
        PList doSplitlinesDefault(PBytesLike self, @SuppressWarnings("unused") PNone keepends,
                        @Cached.Shared("toByteSelf") @Cached BytesNodes.ToBytesNode toBytesNode,
                        @Cached.Shared("append") @Cached ListNodes.AppendNode appendNode,
                        @Cached.Shared("createBytes") @Cached BytesNodes.CreateBytesNode create) {
            return doSplitlines(self, false, toBytesNode, appendNode, create);
        }

        @Specialization(guards = "!isPNone(keepends)")
        PList doSplitlinesDefault(PBytesLike self, Object keepends,
                        @Cached CastToJavaIntExactNode cast,
                        @Cached.Shared("toByteSelf") @Cached BytesNodes.ToBytesNode toBytesNode,
                        @Cached.Shared("append") @Cached ListNodes.AppendNode appendNode,
                        @Cached.Shared("createBytes") @Cached BytesNodes.CreateBytesNode create) {
            return doSplitlines(self, cast.execute(keepends) != 0, toBytesNode, appendNode, create);
        }

        @Specialization
        PList doSplitlines(PBytesLike self, boolean keepends,
                        @Cached.Shared("toByteSelf") @Cached BytesNodes.ToBytesNode toBytesNode,
                        @Cached.Shared("append") @Cached ListNodes.AppendNode appendNode,
                        @Cached.Shared("createBytes") @Cached BytesNodes.CreateBytesNode create) {
            byte[] bytes = toBytesNode.execute(self);
            PList list = factory().createList();
            int sliceStart = 0;
            for (int i = 0; i < bytes.length; i++) {
                if (bytes[i] == '\n' || bytes[i] == '\r') {
                    int sliceEnd = i;
                    if (bytes[i] == '\r' && i + 1 != bytes.length && bytes[i + 1] == '\n') {
                        i++;
                    }
                    if (keepends) {
                        sliceEnd = i + 1;
                    }
                    byte[] slice = copySlice(bytes, sliceStart, sliceEnd);
                    appendNode.execute(list, create.execute(factory(), self, slice));
                    sliceStart = i + 1;
                }
            }
            // Process the remaining part if any
            if (sliceStart != bytes.length) {
                byte[] slice = copySlice(bytes, sliceStart, bytes.length);
                appendNode.execute(list, create.execute(factory(), self, slice));
            }
            return list;
        }

        private static byte[] copySlice(byte[] bytes, int sliceStart, int sliceEnd) {
            byte[] slice = new byte[sliceEnd - sliceStart];
            PythonUtils.arraycopy(bytes, sliceStart, slice, 0, slice.length);
            return slice;
        }
    }

    abstract static class AStripNode extends PythonBinaryBuiltinNode {

        @Specialization
        PBytesLike strip(PBytesLike self, @SuppressWarnings("unused") PNone bytes,
                        @Cached.Shared("createByte") @Cached BytesNodes.CreateBytesNode create,
                        @Cached.Shared("toByteSelf") @Cached BytesNodes.ToBytesNode toBytesNode) {
            byte[] bs = toBytesNode.execute(self);
            return create.execute(factory(), self, getResultBytes(bs, findIndex(bs)));
        }

        @Specialization
        PBytesLike strip(PBytesLike self, PBytesLike bytes,
                        @Cached.Shared("createByte") @Cached BytesNodes.CreateBytesNode create,
                        @Cached.Shared("toByteSelf") @Cached BytesNodes.ToBytesNode selfToBytesNode,
                        @Cached BytesNodes.ToBytesNode otherToBytesNode) {
            byte[] stripBs = selfToBytesNode.execute(bytes);
            byte[] bs = otherToBytesNode.execute(self);
            return create.execute(factory(), self, getResultBytes(bs, findIndex(bs, stripBs)));
        }

        @Specialization(guards = "bufferLib.isBuffer(buffer)")
        PBytesLike strip(PBytesLike self, Object buffer,
                        @CachedLibrary(limit = "3") PythonObjectLibrary bufferLib,
                        @Cached.Shared("createByte") @Cached BytesNodes.CreateBytesNode create,
                        @Cached.Shared("toByteSelf") @Cached BytesNodes.ToBytesNode selfToBytesNode) {
            try {
                byte[] stripBs = bufferLib.getBufferBytes(buffer);
                byte[] bs = selfToBytesNode.execute(self);
                return create.execute(factory(), self, getResultBytes(bs, findIndex(bs, stripBs)));
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }

        @Fallback
        boolean strip(@SuppressWarnings("unused") Object self, Object bytes) {
            throw raise(TypeError, ErrorMessages.BYTESLIKE_OBJ_REQUIRED, bytes);
        }

        int mod() {
            CompilerAsserts.neverPartOfCompilation();
            throw new RuntimeException();
        }

        @SuppressWarnings("unused")
        int stop(@SuppressWarnings("unused") byte[] bs) {
            CompilerAsserts.neverPartOfCompilation();
            throw new RuntimeException();
        }

        @SuppressWarnings("unused")
        int start(@SuppressWarnings("unused") byte[] bs) {
            CompilerAsserts.neverPartOfCompilation();
            throw new RuntimeException();
        }

        @SuppressWarnings("unused")
        protected byte[] getResultBytes(byte[] bs, int i) {
            CompilerAsserts.neverPartOfCompilation();
            throw new RuntimeException();
        }

        protected int findIndex(byte[] bs) {
            int i = start(bs);
            int stop = stop(bs);
            for (; i != stop; i += mod()) {
                if (!isWhitespace(bs[i])) {
                    break;
                }
            }
            return i;
        }

        @CompilerDirectives.TruffleBoundary
        private static boolean isWhitespace(byte b) {
            return Character.isWhitespace(b);
        }

        protected int findIndex(byte[] bs, byte[] stripBs) {
            int i = start(bs);
            int stop = stop(bs);
            outer: for (; i != stop; i += mod()) {
                for (byte b : stripBs) {
                    if (b == bs[i]) {
                        continue outer;
                    }
                }
                break;
            }
            return i;
        }

    }

    @Builtin(name = "lstrip", minNumOfPositionalArgs = 1, parameterNames = {"self", "bytes"})
    @GenerateNodeFactory
    abstract static class LStripNode extends AStripNode {

        @Override
        protected byte[] getResultBytes(byte[] bs, int i) {
            byte[] out;
            if (i != 0) {
                int len = bs.length - i;
                out = new byte[len];
                PythonUtils.arraycopy(bs, i, out, 0, len);
            } else {
                out = bs;
            }
            return out;
        }

        @Override
        int mod() {
            return 1;
        }

        @Override
        int stop(byte[] bs) {
            return bs.length;
        }

        @Override
        int start(byte[] bs) {
            return 0;
        }
    }

    @Builtin(name = "rstrip", minNumOfPositionalArgs = 1, parameterNames = {"self", "bytes"})
    @GenerateNodeFactory
    abstract static class RStripNode extends AStripNode {

        @Override
        protected byte[] getResultBytes(byte[] bs, int i) {
            byte[] out;
            int len = i + 1;
            if (len != bs.length) {
                out = new byte[len];
                PythonUtils.arraycopy(bs, 0, out, 0, len);
            } else {
                out = bs;
            }
            return out;
        }

        @Override
        int mod() {
            return -1;
        }

        @Override
        int stop(byte[] bs) {
            return -1;
        }

        @Override
        int start(byte[] bs) {
            return bs.length - 1;
        }
    }

    // static bytes.maketrans()
    // static bytearray.maketrans()
    @Builtin(name = "maketrans", minNumOfPositionalArgs = 3, isClassmethod = true)
    @GenerateNodeFactory
    public abstract static class MakeTransNode extends PythonBuiltinNode {

        @Specialization
        PBytes maketrans(@SuppressWarnings("unused") Object cls, Object from, Object to,
                        @Cached("create()") BytesNodes.ToBytesNode toByteNode) {
            byte[] fromB = toByteNode.execute(from);
            byte[] toB = toByteNode.execute(to);
            if (fromB.length != toB.length) {
                throw raise(PythonErrorType.ValueError, ErrorMessages.ARGS_MUST_HAVE_SAME_LENGTH, "maketrans");
            }

            byte[] table = new byte[256];
            for (int i = 0; i < 256; i++) {
                table[i] = (byte) i;
            }

            for (int i = 0; i < fromB.length; i++) {
                byte value = fromB[i];
                table[value < 0 ? value + 256 : value] = toB[i];
            }

            return factory().createBytes(table);
        }

    }

    public abstract static class BaseTranslateNode extends PythonBuiltinNode {

        protected final void checkLengthOfTable(byte[] table, ConditionProfile isLenTable256Profile) {
            if (isLenTable256Profile.profile(table.length != 256)) {
                throw raise(PythonErrorType.ValueError, ErrorMessages.TRANS_TABLE_MUST_BE_256);
            }
        }

        protected static class Result {
            byte[] array;
            // we have to know, whether the result array was changed ->
            // if not in bytes case it has to return the input bytes
            // in bytearray case it has to return always new bytearray
            boolean changed;

            public Result(byte[] array, boolean changed) {
                this.array = array;
                this.changed = changed;
            }
        }

        protected static boolean[] createDeleteTable(byte[] delete) {
            boolean[] result = new boolean[256];
            for (int i = 0; i < 256; i++) {
                result[i] = false;
            }
            for (byte b : delete) {
                result[b] = true;
            }
            return result;
        }

        protected static Result delete(byte[] self, byte[] table) {
            final int length = self.length;
            byte[] result = new byte[length];
            int resultLen = 0;
            boolean[] toDelete = createDeleteTable(table);

            for (byte b : self) {
                if (!toDelete[b & 0xFF]) {
                    result[resultLen] = b;
                    resultLen++;
                }
            }
            if (resultLen == length) {
                return new Result(result, false);
            }
            return new Result(Arrays.copyOf(result, resultLen), true);
        }

        protected static Result translate(byte[] self, byte[] table) {
            final int length = self.length;
            byte[] result = new byte[length];
            boolean changed = false;
            for (int i = 0; i < length; i++) {
                byte b = table[self[i]];
                if (!changed && b != self[i]) {
                    changed = true;
                }
                result[i] = b;
            }
            return new Result(result, changed);
        }

        protected static Result translateAndDelete(byte[] self, byte[] table, byte[] delete) {
            final int length = self.length;
            byte[] result = new byte[length];
            int resultLen = 0;
            boolean changed = false;
            boolean[] toDelete = createDeleteTable(delete);

            for (byte value : self) {
                if (!toDelete[value]) {
                    byte b = table[value];
                    if (!changed && b != value) {
                        changed = true;
                    }
                    result[resultLen] = b;
                    resultLen++;
                }
            }
            if (resultLen == length) {
                return new Result(result, changed);
            }
            return new Result(Arrays.copyOf(result, resultLen), true);
        }

    }

    @Builtin(name = "capitalize", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class CapitalizeNode extends PythonUnaryBuiltinNode {

        @Specialization(limit = "2")
        PBytesLike capitalize(PBytesLike self,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @Cached BytesNodes.CreateBytesNode create,
                        @CachedLibrary("self") PythonObjectLibrary lib) {
            int len = lenNode.execute(self.getSequenceStorage());
            if (len == 0) {
                return create.execute(factory(), self, PythonUtils.EMPTY_BYTE_ARRAY);
            }
            byte[] b = getBytes(lib, self);
            b[0] = BytesUtils.toUpper(b[0]);
            for (int i = 1; i < len; i++) {
                b[i] = BytesUtils.toLower(b[i]);
            }
            return create.execute(factory(), self, b);
        }
    }

    @Builtin(name = "title", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class TitleNode extends PythonUnaryBuiltinNode {

        @Specialization(limit = "2")
        PBytesLike title(PBytesLike self,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @Cached BytesNodes.CreateBytesNode create,
                        @CachedLibrary("self") PythonObjectLibrary lib) {
            int len = lenNode.execute(self.getSequenceStorage());
            if (len == 0) {
                return create.execute(factory(), self, PythonUtils.EMPTY_BYTE_ARRAY);
            }
            byte[] s = getBytes(lib, self);
            byte[] result = new byte[len];
            boolean previousIsCased = false;

            for (int i = 0; i < len; i++) {
                byte c = s[i];
                if (BytesUtils.isLower(c)) {
                    if (!previousIsCased) {
                        c = BytesUtils.toUpper(c);
                    }
                    previousIsCased = true;
                } else if (BytesUtils.isUpper(c)) {
                    if (previousIsCased) {
                        c = BytesUtils.toLower(c);
                    }
                    previousIsCased = true;
                } else {
                    previousIsCased = false;
                }
                result[i] = c;
            }

            return create.execute(factory(), self, result);
        }
    }

    @Builtin(name = "swapcase", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class SwapCaseNode extends PythonUnaryBuiltinNode {

        @Specialization(limit = "2")
        PBytesLike swapcase(PBytesLike self,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @Cached BytesNodes.CreateBytesNode create,
                        @CachedLibrary("self") PythonObjectLibrary lib) {
            int len = lenNode.execute(self.getSequenceStorage());
            if (len == 0) {
                return create.execute(factory(), self, PythonUtils.EMPTY_BYTE_ARRAY);
            }
            byte[] b = getBytes(lib, self);
            for (int i = 0; i < len; i++) {
                if (BytesUtils.isUpper(b[i])) {
                    b[i] = BytesUtils.toLower(b[i]);
                } else {
                    b[i] = BytesUtils.toUpper(b[i]);
                }
            }
            return create.execute(factory(), self, b);
        }
    }

    @Builtin(name = "expandtabs", minNumOfPositionalArgs = 1, parameterNames = {"self", "tabsize"})
    @GenerateNodeFactory
    abstract static class ExpandTabsNode extends PythonBinaryBuiltinNode {

        private static final byte T = '\t';
        private static final byte N = '\n';
        private static final byte R = '\r';
        private static final byte S = ' ';

        @Specialization(limit = "2")
        PBytesLike expandtabs(PBytesLike self, @SuppressWarnings("unused") PNone tabsize,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @Cached BytesNodes.CreateBytesNode create,
                        @CachedLibrary("self") PythonObjectLibrary lib) {
            return expandtabs(self, 8, lenNode, create, lib);
        }

        @Specialization(limit = "2")
        PBytesLike expandtabs(PBytesLike self, int tabsize,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @Cached BytesNodes.CreateBytesNode create,
                        @CachedLibrary("self") PythonObjectLibrary lib) {
            int len = lenNode.execute(self.getSequenceStorage());
            if (len == 0) {
                return create.execute(factory(), self, PythonUtils.EMPTY_BYTE_ARRAY);
            }
            int max = SysModuleBuiltins.MAXSIZE;
            byte[] b = getBytes(lib, self);
            int i = 0, j = 0;
            for (byte p : b) {
                if (p == T) {
                    if (tabsize > 0) {
                        int incr = tabsize - (j % tabsize);
                        if (j > max - incr) {
                            throw raise(OverflowError, "result too long");
                        }
                        j += incr;
                    }
                } else {
                    if (j > max - 1) {
                        throw raise(OverflowError, "result too long");
                    }
                    j++;
                    if (p == N || p == R) {
                        if (i > max - j) {
                            throw raise(OverflowError, "result too long");
                        }
                        i += j;
                        j = 0;
                    }
                }
            }
            if (i > max - j) {
                throw raise(OverflowError, "result too long");
            }

            byte[] q = new byte[i + j];
            j = 0;
            int idx = 0;
            for (byte p : b) {
                if (p == T) {
                    if (tabsize > 0) {
                        i = tabsize - (j % tabsize);
                        j += i;
                        while (i-- > 0) {
                            q[idx++] = S;
                        }
                    }
                } else {
                    j++;
                    q[idx++] = p;
                    if (p == N || p == R) {
                        j = 0;
                    }
                }

            }
            return create.execute(factory(), self, q);
        }
    }

    @Builtin(name = "zfill", minNumOfPositionalArgs = 2, needsFrame = true)
    @GenerateNodeFactory
    abstract static class ZFillNode extends PythonBinaryBuiltinNode {

        public abstract String executeObject(VirtualFrame frame, String self, Object x);

        @Specialization(limit = "2")
        PBytesLike zfill(PBytesLike self, int width,
                        @Cached BytesNodes.CreateBytesNode create,
                        @CachedLibrary("self") PythonObjectLibrary lib) {
            return create.execute(factory(), self, zfill(getBytes(lib, self), width));
        }

        private byte[] zfill(byte[] self, int width) {
            int len = self.length;
            if (len >= width) {
                return self;
            }

            int fill = width - len;
            byte[] p = pad(self, fill, 0, (byte) '0');

            if (len == 0) {
                return p;
            }

            if (p[fill] == '+' || p[fill] == '-') {
                /* move sign to beginning of string */
                p[0] = p[fill];
                p[fill] = '0';
            }
            return p;
        }

        protected byte[] pad(byte[] self, int l, int r, byte fillChar) {
            int left = (l < 0) ? 0 : l;
            int right = (r < 0) ? 0 : r;
            if (left == 0 && right == 0) {
                return self;
            }

            byte[] u = new byte[left + self.length + right];
            if (left > 0) {
                Arrays.fill(u, 0, left, fillChar);
            }
            for (int i = left, j = 0; i < (left + self.length); j++, i++) {
                u[i] = self[j];
            }
            if (right > 0) {
                Arrays.fill(u, left + self.length, u.length, fillChar);
            }
            return u;
        }
    }

    @GenerateUncached
    public abstract static class BytesLikeNoGeneralizationNode extends SequenceStorageNodes.NoGeneralizationNode {

        public static final SequenceStorageNodes.GenNodeSupplier SUPPLIER = new SequenceStorageNodes.GenNodeSupplier() {

            @Override
            public SequenceStorageNodes.GeneralizationNode create() {
                return BytesBuiltinsFactory.BytesLikeNoGeneralizationNodeGen.create();
            }

            @Override
            public SequenceStorageNodes.GeneralizationNode getUncached() {
                return BytesBuiltinsFactory.BytesLikeNoGeneralizationNodeGen.getUncached();
            }

        };

        @Override
        protected final String getErrorMessage() {
            return ErrorMessages.BYTE_MUST_BE_IN_RANGE;
        }

    }

    protected static int adjustStartIndex(int startIn, int len) {
        if (startIn < 0) {
            int start = startIn + len;
            return start < 0 ? 0 : start;
        }
        return startIn;
    }

    protected static int adjustEndIndex(int endIn, int len) {
        if (endIn > len) {
            return len;
        } else if (endIn < 0) {
            int end = endIn + len;
            return end < 0 ? 0 : end;
        }
        return endIn;
    }

    @CompilerDirectives.TruffleBoundary
    protected static byte[] copyOfRange(byte[] bytes, int from, int to) {
        return Arrays.copyOfRange(bytes, from, to);
    }

    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    protected static Iterator<byte[]> iterator(List<byte[]> bytes) {
        return bytes.iterator();
    }

    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    protected static byte[] next(Iterator<byte[]> it) {
        return it.next();
    }

    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    protected static boolean hasNext(Iterator<byte[]> it) {
        return it.hasNext();
    }

    @CompilerDirectives.TruffleBoundary
    protected static byte[] getStringBytes(Object o) {
        return ((String) o).getBytes();
    }

    protected static byte[] getBytes(PythonObjectLibrary lib, Object object) {
        try {
            return lib.getBufferBytes(object);
        } catch (UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException(e);
        }
    }
}
