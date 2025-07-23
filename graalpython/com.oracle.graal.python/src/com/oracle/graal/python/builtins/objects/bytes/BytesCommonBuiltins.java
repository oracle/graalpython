/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.builtins.objects.bytes.BytesNodes.adjustEndIndex;
import static com.oracle.graal.python.builtins.objects.bytes.BytesNodes.adjustStartIndex;
import static com.oracle.graal.python.builtins.objects.bytes.BytesUtils.toLower;
import static com.oracle.graal.python.builtins.objects.bytes.BytesUtils.toUpper;
import static com.oracle.graal.python.nodes.BuiltinNames.J_DECODE;
import static com.oracle.graal.python.nodes.BuiltinNames.J_ENDSWITH;
import static com.oracle.graal.python.nodes.BuiltinNames.J_REMOVEPREFIX;
import static com.oracle.graal.python.nodes.BuiltinNames.J_REMOVESUFFIX;
import static com.oracle.graal.python.nodes.BuiltinNames.J_STARTSWITH;
import static com.oracle.graal.python.nodes.ErrorMessages.BYTESLIKE_OBJ_REQUIRED;
import static com.oracle.graal.python.nodes.ErrorMessages.DECODER_RETURNED_P_INSTEAD_OF_BYTES;
import static com.oracle.graal.python.nodes.ErrorMessages.DESCRIPTOR_NEED_OBJ;
import static com.oracle.graal.python.nodes.ErrorMessages.FIRST_ARG_MUST_BE_BYTES_OR_A_TUPLE_OF_BYTES_NOT_P;
import static com.oracle.graal.python.nodes.ErrorMessages.METHOD_REQUIRES_A_BYTES_OBJECT_GOT_P;
import static com.oracle.graal.python.nodes.ErrorMessages.SEP_MUST_BE_ASCII;
import static com.oracle.graal.python.nodes.ErrorMessages.SEP_MUST_BE_LENGTH_1;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GETNEWARGS__;
import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_STRING;
import static com.oracle.graal.python.nodes.StringLiterals.T_IGNORE;
import static com.oracle.graal.python.nodes.StringLiterals.T_REPLACE;
import static com.oracle.graal.python.nodes.StringLiterals.T_STRICT;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ArgumentClinic.ClinicConversion;
import com.oracle.graal.python.annotations.ClinicConverterFactory;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.IsInstanceNode;
import com.oracle.graal.python.builtins.modules.CodecsModuleBuiltins;
import com.oracle.graal.python.builtins.modules.SysModuleBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.builtins.objects.bytes.BytesCommonBuiltinsFactory.LStripNodeFactory;
import com.oracle.graal.python.builtins.objects.bytes.BytesCommonBuiltinsFactory.RStripNodeFactory;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes.BytesLikeCheck;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes.GetBytesStorage;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes.NeedleToBytesNode;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes.ToBytesNode;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetInternalByteArrayNode;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.iterator.PSequenceIterator;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryFunc.SqConcatBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryOp.BinaryOpBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotLen.LenBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSizeArgFun.SqRepeatBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSqContains.SqContainsBuiltinNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyNumberIndexNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.builtins.ListNodes;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentCastNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.formatting.BytesFormatProcessor;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PByteArray, PythonBuiltinClassType.PBytes})
public final class BytesCommonBuiltins extends PythonBuiltins {

    public static final TpSlots SLOTS = BytesCommonBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return BytesCommonBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
        addBuiltinConstant(SpecialAttributeNames.T___DOC__, //
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

    public static CodingErrorAction toCodingErrorAction(TruffleString errors, TruffleString.EqualNode eqNode) {
        // TODO: replace CodingErrorAction with TruffleString api [GR-38105]
        if (eqNode.execute(T_STRICT, errors, TS_ENCODING)) {
            return CodingErrorAction.REPORT;
        } else if (eqNode.execute(T_IGNORE, errors, TS_ENCODING)) {
            return CodingErrorAction.IGNORE;
        } else if (eqNode.execute(T_REPLACE, errors, TS_ENCODING)) {
            return CodingErrorAction.REPLACE;
        }
        return null;
    }

    public static CodingErrorAction toCodingErrorAction(Node inliningTarget, TruffleString errors, PRaiseNode raiseNode, TruffleString.EqualNode eqNode) {
        CodingErrorAction action = toCodingErrorAction(errors, eqNode);
        if (action != null) {
            return action;
        }
        throw raiseNode.raise(inliningTarget, PythonErrorType.LookupError, ErrorMessages.UNKNOWN_ERROR_HANDLER, errors);
    }

    @TruffleBoundary
    public static byte[] doEncode(Charset charset, TruffleString s, CodingErrorAction action) throws CharacterCodingException {
        String string = s.toJavaStringUncached();
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

    @Builtin(name = J_DECODE, minNumOfPositionalArgs = 1, parameterNames = {"$self", "encoding", "errors"}, doc = "Decode the bytes using the codec registered for encoding.\n\n" +
                    "encoding\n" +
                    "  The encoding with which to decode the bytes.\n" +
                    "errors\n" +
                    "  The error handling scheme to use for the handling of decoding errors.\n" +
                    "  The default is 'strict' meaning that decoding errors raise a\n" +
                    "  UnicodeDecodeError. Other possible values are 'ignore' and 'replace'\n" +
                    "  as well as any other name registered with codecs.register_error that\n" +
                    "  can handle UnicodeDecodeErrors.")
    @ArgumentClinic(name = "encoding", conversion = ArgumentClinic.ClinicConversion.TString, defaultValue = "T_UTF8")
    @ArgumentClinic(name = "errors", conversion = ArgumentClinic.ClinicConversion.TString, defaultValue = "T_STRICT")
    @GenerateNodeFactory
    public abstract static class DecodeNode extends PythonTernaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BytesCommonBuiltinsClinicProviders.DecodeNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object decode(VirtualFrame frame, Object self, TruffleString encoding, TruffleString errors,
                        @Bind Node inliningTarget,
                        @Cached CodecsModuleBuiltins.DecodeNode decodeNode,
                        @Cached IsInstanceNode isInstanceNode,
                        @Cached PRaiseNode raiseNode) {
            Object result = decodeNode.executeWithStrings(frame, self, encoding, errors);
            if (!isInstanceNode.executeWith(frame, result, PythonBuiltinClassType.PString)) {
                throw raiseNode.raise(inliningTarget, TypeError, DECODER_RETURNED_P_INSTEAD_OF_BYTES, encoding, result);
            }
            return result;
        }
    }

    @Builtin(name = "strip", minNumOfPositionalArgs = 1, parameterNames = {"$self", "what"})
    @GenerateNodeFactory
    abstract static class StripNode extends PythonBinaryBuiltinNode {
        @Specialization
        public Object strip(VirtualFrame frame, Object self, Object what,
                        @Cached LStripNode lstripNode,
                        @Cached RStripNode rstripNode) {
            return rstripNode.execute(frame, lstripNode.execute(frame, self, what), what);
        }
    }

    // All below builtins are shared with Bytearray

    // bytes.join(iterable)
    // bytearray.join(iterable)
    @Builtin(name = "join", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class JoinNode extends PythonBinaryBuiltinNode {
        @Specialization
        static PBytesLike join(VirtualFrame frame, Object self, Object iterable,
                        @Bind Node inliningTarget,
                        @Cached GetBytesStorage getBytesStorage,
                        @Cached SequenceStorageNodes.ToByteArrayNode toByteArrayNode,
                        @Cached BytesNodes.BytesJoinNode bytesJoinNode,
                        @Cached BytesNodes.CreateBytesNode create) {
            byte[] res = bytesJoinNode.execute(frame, inliningTarget, toByteArrayNode.execute(inliningTarget, getBytesStorage.execute(inliningTarget, self)), iterable);
            return create.execute(inliningTarget, self, res);
        }
    }

    @Slot(value = SlotKind.sq_concat, isComplex = true)
    @GenerateNodeFactory
    public abstract static class ConcatNode extends SqConcatBuiltinNode {

        @Specialization
        static PBytesLike add(PBytesLike self, PBytesLike other,
                        @Bind Node inliningTarget,
                        @Shared @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                        @Exclusive @Cached BytesNodes.CreateBytesNode create,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            return concatBuffers(self, self, other, inliningTarget, bufferLib, create, raiseNode);
        }

        // @Exclusive for truffle-interpreted-performance
        @Specialization(limit = "3")
        static PBytesLike add(VirtualFrame frame, Object self, Object other,
                        @Bind Node inliningTarget,
                        @Cached("createFor($node)") IndirectCallData indirectCallData,
                        @Cached GetBytesStorage getBytesStorage,
                        @CachedLibrary("other") PythonBufferAcquireLibrary bufferAcquireLib,
                        @Shared @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                        @Exclusive @Cached BytesNodes.CreateBytesNode create,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            Object otherBuffer;
            try {
                otherBuffer = bufferAcquireLib.acquireReadonly(other, frame, indirectCallData);
            } catch (PException e) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.CANT_CONCAT_P_TO_P, other, self);
            }
            try {
                SequenceStorage selfBuffer = getBytesStorage.execute(inliningTarget, self);
                return concatBuffers(self, selfBuffer, otherBuffer, inliningTarget, bufferLib, create, raiseNode);
            } finally {
                bufferLib.release(otherBuffer, frame, indirectCallData);
            }
        }

        private static PBytesLike concatBuffers(Object self, Object selfBuffer, Object otherBuffer, Node inliningTarget, PythonBufferAccessLibrary bufferLib, BytesNodes.CreateBytesNode create,
                        PRaiseNode raiseNode) {
            try {
                int len = bufferLib.getBufferLength(selfBuffer);
                int otherLen = bufferLib.getBufferLength(otherBuffer);
                int newLen = PythonUtils.addExact(len, otherLen);
                byte[] newBytes = new byte[newLen];
                bufferLib.readIntoByteArray(selfBuffer, 0, newBytes, 0, len);
                bufferLib.readIntoByteArray(otherBuffer, 0, newBytes, len, otherLen);
                return create.execute(inliningTarget, self, new ByteSequenceStorage(newBytes));
            } catch (OverflowException e) {
                throw raiseNode.raise(inliningTarget, OverflowError);
            }
        }
    }

    @Slot(value = SlotKind.sq_repeat, isComplex = true)
    @GenerateNodeFactory
    public abstract static class MulNode extends SqRepeatBuiltinNode {
        @Specialization
        static PBytesLike mul(VirtualFrame frame, Object self, int times,
                        @Bind Node inliningTarget,
                        @Cached GetBytesStorage getBytesStorage,
                        @Cached("createWithOverflowError()") SequenceStorageNodes.RepeatNode repeatNode,
                        @Cached BytesNodes.CreateBytesNode create) {
            SequenceStorage res = repeatNode.execute(frame, getBytesStorage.execute(inliningTarget, self), times);
            return create.execute(inliningTarget, self, res);
        }
    }

    @Slot(value = SlotKind.nb_remainder, isComplex = true)
    @GenerateNodeFactory
    abstract static class ModNode extends BinaryOpBuiltinNode {

        @Specialization(guards = "check.execute(inliningTarget, self)", limit = "1")
        static Object mod(VirtualFrame frame, Object self, Object right,
                        @Bind Node inliningTarget,
                        @SuppressWarnings("unused") @Cached BytesLikeCheck check,
                        @Cached("createFor($node)") IndirectCallData indirectCallData,
                        @CachedLibrary(limit = "3") PythonBufferAcquireLibrary acquireLib,
                        @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                        @Cached BytesNodes.CreateBytesNode create) {
            Object buffer = acquireLib.acquireReadonly(self, frame, indirectCallData);
            try {
                byte[] bytes = bufferLib.getInternalOrCopiedByteArray(buffer);
                int bytesLen = bufferLib.getBufferLength(buffer);
                BytesFormatProcessor formatter = new BytesFormatProcessor(PythonContext.get(inliningTarget), bytes, bytesLen, inliningTarget);
                Object savedState = IndirectCallContext.enter(frame, inliningTarget, indirectCallData);
                try {
                    byte[] data = formatter.format(right);
                    return create.execute(inliningTarget, self, data);
                } finally {
                    IndirectCallContext.exit(frame, inliningTarget, indirectCallData, savedState);
                }
            } finally {
                bufferLib.release(buffer, frame, indirectCallData);
            }
        }

        @Fallback
        static Object doOther(@SuppressWarnings("unused") Object self, @SuppressWarnings("unused") Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Slot(SlotKind.sq_length)
    @Slot(SlotKind.mp_length)
    @GenerateNodeFactory
    @GenerateUncached
    public abstract static class LenNode extends LenBuiltinNode {
        @Specialization
        public static int len(Object self,
                        @Bind Node inliningTarget,
                        @Cached GetBytesStorage getBytesStorage) {
            return getBytesStorage.execute(inliningTarget, self).length();
        }
    }

    @Slot(value = SlotKind.sq_contains, isComplex = true)
    @GenerateNodeFactory
    abstract static class ContainsNode extends SqContainsBuiltinNode {

        @Specialization
        boolean contains(VirtualFrame frame, Object self, Object other,
                        @Bind Node inliningTarget,
                        @Cached BytesNodes.FindNode findNode) {
            return findNode.execute(frame, inliningTarget, self, other, 0, Integer.MAX_VALUE) != -1;
        }
    }

    @Slot(value = SlotKind.tp_iter, isComplex = true)
    @GenerateNodeFactory
    abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        static PSequenceIterator contains(Object self,
                        @Bind PythonLanguage language) {
            return PFactory.createSequenceIterator(language, self);
        }
    }

    @GenerateCached(false)
    abstract static class PrefixSuffixBaseNode extends PythonQuaternaryClinicBuiltinNode {
        // common and specialized cases --------------------

        @Specialization
        @SuppressWarnings("truffle-static-method")
        boolean doIt(VirtualFrame frame, Object self, Object substrs, int start, int end,
                        @Bind Node inliningTarget,
                        @Cached GetBytesStorage getBytesStorage,
                        @Cached SequenceStorageNodes.GetInternalByteArrayNode getBytes,
                        @Cached PrefixSuffixDispatchNode dispatchNode) {
            SequenceStorage storage = getBytesStorage.execute(inliningTarget, self);
            byte[] bytes = getBytes.execute(inliningTarget, storage);
            int len = storage.length();
            int begin = adjustStartIndex(start, len);
            int last = adjustEndIndex(end, len);
            return dispatchNode.execute(frame, inliningTarget, this, bytes, len, substrs, begin, last);
        }

        @Fallback
        static boolean doGeneric(@SuppressWarnings("unused") Object self, @SuppressWarnings("unused") Object substr,
                        @SuppressWarnings("unused") Object start, @SuppressWarnings("unused") Object end,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, METHOD_REQUIRES_A_BYTES_OBJECT_GOT_P, substr);
        }

        protected abstract boolean doIt(byte[] bytes, int len, byte[] prefix, int start, int end);

        private boolean doIt(VirtualFrame frame, byte[] self, int len, PTuple substrs, int start, int stop,
                        Node inliningTarget,
                        BytesNodes.ToBytesNode tobytes,
                        SequenceNodes.GetObjectArrayNode getObjectArrayNode) {
            for (Object element : getObjectArrayNode.execute(inliningTarget, substrs)) {
                byte[] bytes = tobytes.execute(frame, element);
                if (doIt(self, len, bytes, start, stop)) {
                    return true;
                }
            }
            return false;
        }
    }

    @GenerateInline
    @GenerateCached(false)
    abstract static class PrefixSuffixDispatchNode extends Node {
        abstract boolean execute(VirtualFrame frame, Node inliningTarget, PrefixSuffixBaseNode parent, byte[] bytes, int len, Object substrs, int begin, int last);

        @Specialization
        static boolean doTuple(VirtualFrame frame, Node inliningTarget, PrefixSuffixBaseNode parent, byte[] bytes, int len, PTuple substrs, int begin, int last,
                        @Cached(value = "createToBytesFromTuple()", inline = false) BytesNodes.ToBytesNode tobytes,
                        @Cached SequenceNodes.GetObjectArrayNode getObjectArrayNode) {
            return parent.doIt(frame, bytes, len, substrs, begin, last, inliningTarget, tobytes, getObjectArrayNode);
        }

        @Fallback
        static boolean doOthers(VirtualFrame frame, PrefixSuffixBaseNode parent, byte[] bytes, int len, Object substrs, int begin, int last,
                        @Cached(value = "createToBytes()", inline = false) BytesNodes.ToBytesNode tobytes) {
            byte[] substrBytes = tobytes.execute(frame, substrs);
            return parent.doIt(bytes, len, substrBytes, begin, last);
        }

        @NeverDefault
        static BytesNodes.ToBytesNode createToBytes() {
            return BytesNodes.ToBytesNode.create(PythonBuiltinClassType.TypeError, FIRST_ARG_MUST_BE_BYTES_OR_A_TUPLE_OF_BYTES_NOT_P);
        }

        @NeverDefault
        static BytesNodes.ToBytesNode createToBytesFromTuple() {
            return BytesNodes.ToBytesNode.create(PythonBuiltinClassType.TypeError, BYTESLIKE_OBJ_REQUIRED);
        }
    }

    // bytes.startswith(prefix[, start[, end]])
    // bytearray.startswith(prefix[, start[, end]])
    @Builtin(name = J_STARTSWITH, minNumOfPositionalArgs = 2, parameterNames = {"$self", "prefix", "start", "end"})
    @ArgumentClinic(name = "start", conversion = ArgumentClinic.ClinicConversion.SliceIndex, defaultValue = "0", useDefaultForNone = true)
    @ArgumentClinic(name = "end", conversion = ArgumentClinic.ClinicConversion.SliceIndex, defaultValue = "Integer.MAX_VALUE", useDefaultForNone = true)
    @GenerateNodeFactory
    public abstract static class StartsWithNode extends PrefixSuffixBaseNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BytesCommonBuiltinsClinicProviders.StartsWithNodeClinicProviderGen.INSTANCE;
        }

        @Override
        protected boolean doIt(byte[] bytes, int len, byte[] prefix, int start, int end) {
            // start and end must be normalized indices for 'bytes'
            assert start >= 0;
            assert end >= 0 && end <= len;

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
    @Builtin(name = J_ENDSWITH, minNumOfPositionalArgs = 2, parameterNames = {"$self", "suffix", "start", "end"})
    @ArgumentClinic(name = "start", conversion = ArgumentClinic.ClinicConversion.SliceIndex, defaultValue = "0", useDefaultForNone = true)
    @ArgumentClinic(name = "end", conversion = ArgumentClinic.ClinicConversion.SliceIndex, defaultValue = "Integer.MAX_VALUE", useDefaultForNone = true)
    @GenerateNodeFactory
    public abstract static class EndsWithNode extends PrefixSuffixBaseNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BytesCommonBuiltinsClinicProviders.EndsWithNodeClinicProviderGen.INSTANCE;
        }

        @Override
        protected boolean doIt(byte[] bytes, int len, byte[] suffix, int start, int end) {
            // start and end must be normalized indices for 'bytes'
            assert start >= 0;
            assert end >= 0 && end <= len;

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
            return BytesCommonBuiltinsClinicProviders.IndexNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static int index(VirtualFrame frame, Object self, Object arg, int start, int end,
                        @Bind Node inliningTarget,
                        @Cached BytesNodes.FindNode findNode,
                        @Cached PRaiseNode raiseNode) {
            int result = findNode.execute(frame, inliningTarget, self, arg, start, end);
            if (result == -1) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.ValueError, ErrorMessages.SUBSECTION_NOT_FOUND);
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
            return BytesCommonBuiltinsClinicProviders.RIndexNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static int indexWithStartEnd(VirtualFrame frame, Object self, Object arg, int start, int end,
                        @Bind Node inliningTarget,
                        @Cached BytesNodes.FindNode findNode,
                        @Cached PRaiseNode raiseNode) {
            int result = findNode.executeReverse(frame, inliningTarget, self, arg, start, end);
            if (result == -1) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.ValueError, ErrorMessages.SUBSECTION_NOT_FOUND);
            }
            return result;
        }
    }

    @GenerateCached(false)
    public abstract static class PartitionAbstractNode extends PythonBinaryBuiltinNode {

        @Specialization(limit = "3")
        @SuppressWarnings("truffle-static-method")  // TODO: inh
        PTuple partition(VirtualFrame frame, Object self, Object sep,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached("createFor($node)") IndirectCallData indirectCallData,
                        @CachedLibrary("sep") PythonBufferAcquireLibrary acquireLib,
                        @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                        @Cached GetBytesStorage getBytesStorage,
                        @Cached InlinedConditionProfile notFound,
                        @Cached BytesNodes.ToBytesNode toBytesNode,
                        @Cached BytesNodes.CreateBytesNode createBytesNode,
                        @Cached PRaiseNode raiseNode) {
            SequenceStorage storage = getBytesStorage.execute(inliningTarget, self);
            int len = storage.length();
            byte[] bytes = toBytesNode.execute(frame, self);
            Object sepBuffer = acquireLib.acquireReadonly(sep, frame, indirectCallData);
            try {
                int lenSep = bufferLib.getBufferLength(sepBuffer);
                if (lenSep == 0) {
                    throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.EMPTY_SEPARATOR);
                }
                byte[] sepBytes = bufferLib.getCopiedByteArray(sepBuffer);
                int idx = BytesNodes.FindNode.find(bytes, len, sepBytes, 0, bytes.length, isRight());
                PBytesLike first, second, third;
                if (notFound.profile(inliningTarget, idx == -1)) {
                    second = createBytesNode.execute(inliningTarget, self, PythonUtils.EMPTY_BYTE_ARRAY);
                    if (isRight()) {
                        third = createBytesNode.execute(inliningTarget, self, bytes);
                        first = createBytesNode.execute(inliningTarget, self, PythonUtils.EMPTY_BYTE_ARRAY);
                    } else {
                        first = createBytesNode.execute(inliningTarget, self, bytes);
                        third = createBytesNode.execute(inliningTarget, self, PythonUtils.EMPTY_BYTE_ARRAY);
                    }
                } else {
                    second = createBytesNode.execute(inliningTarget, self, sepBytes);
                    if (idx == 0) {
                        first = createBytesNode.execute(inliningTarget, self, PythonUtils.EMPTY_BYTE_ARRAY);
                        third = createBytesNode.execute(inliningTarget, self, Arrays.copyOfRange(bytes, lenSep, len));
                    } else if (idx == len - 1) {
                        first = createBytesNode.execute(inliningTarget, self, Arrays.copyOfRange(bytes, 0, len - lenSep));
                        third = createBytesNode.execute(inliningTarget, self, PythonUtils.EMPTY_BYTE_ARRAY);
                    } else {
                        first = createBytesNode.execute(inliningTarget, self, Arrays.copyOfRange(bytes, 0, idx));
                        third = createBytesNode.execute(inliningTarget, self, Arrays.copyOfRange(bytes, idx + lenSep, len));
                    }
                }
                return PFactory.createTuple(language, new Object[]{first, second, third});
            } finally {
                bufferLib.release(sepBuffer, frame, indirectCallData);
            }
        }

        protected boolean isRight() {
            return false;
        }
    }

    // bytes.partition(sep)
    // bytearray.partition(sep)
    @Builtin(name = "partition", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class PartitionNode extends PartitionAbstractNode {
    }

    // bytes.rpartition(sep)
    // bytearray.rpartition(sep)
    @Builtin(name = "rpartition", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class RPartitionNode extends PartitionAbstractNode {

        @Override
        protected boolean isRight() {
            return true;
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
            return BytesCommonBuiltinsClinicProviders.CountNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static int count(VirtualFrame frame, Object self, Object needle, int start, int end,
                        @Bind Node inliningTarget,
                        @Cached GetBytesStorage getBytesStorage,
                        @Cached NeedleToBytesNode needleToBytesNode,
                        @Cached GetInternalByteArrayNode getInternalByteArrayNode) {
            SequenceStorage storage = getBytesStorage.execute(inliningTarget, self);
            int len = storage.length();
            byte[] bytes = getInternalByteArrayNode.execute(inliningTarget, storage);
            byte[] needleBytes = needleToBytesNode.execute(frame, inliningTarget, needle);
            int begin = adjustStartIndex(start, storage.length());
            int last = adjustEndIndex(end, storage.length());
            return count(bytes, len, begin, last, needleBytes);
        }

        @TruffleBoundary(allowInlining = true)
        private static int count(byte[] bytes, int len, int start, int end, byte[] needleBytes) {
            int idx = start;
            int count = 0;
            if ((end - start) < 0) {
                return 0;
            }
            if (needleBytes.length == 0) {
                return (end - start) + 1;
            }
            while (idx < end) {
                int found = BytesNodes.FindNode.find(bytes, len, needleBytes, idx, end, false);
                if (found == -1) {
                    break;
                }
                count++;
                idx = found + needleBytes.length;
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
            return BytesCommonBuiltinsClinicProviders.FindNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static int find(VirtualFrame frame, Object self, Object needle, int start, int end,
                        @Bind Node inliningTarget,
                        @Cached BytesNodes.FindNode findNode) {
            return findNode.execute(frame, inliningTarget, self, needle, start, end);
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
            return BytesCommonBuiltinsClinicProviders.RFindNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static int find(VirtualFrame frame, Object self, Object needle, int start, int end,
                        @Bind Node inliningTarget,
                        @Cached BytesNodes.FindNode findNode) {
            return findNode.executeReverse(frame, inliningTarget, self, needle, start, end);
        }
    }

    public abstract static class SepExpectByteNode extends ArgumentCastNode {
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

        @Specialization(guards = "isString(strObj)")
        static byte pstring(Object strObj,
                        @Bind Node inliningTarget,
                        @Cached CastToTruffleStringNode toStr,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.CodePointAtIndexNode codePointAtIndexNode,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            TruffleString str = toStr.execute(inliningTarget, strObj);
            if (codePointLengthNode.execute(str, TS_ENCODING) != 1) {
                throw raiseNode.raise(inliningTarget, ValueError, SEP_MUST_BE_LENGTH_1);
            }
            int cp = codePointAtIndexNode.execute(str, 0, TS_ENCODING);
            if (cp > 127) {
                throw raiseNode.raise(inliningTarget, ValueError, SEP_MUST_BE_ASCII);
            }
            return (byte) cp;
        }

        @Specialization(guards = "bufferAcquireLib.hasBuffer(object)", limit = "3")
        static byte doBuffer(VirtualFrame frame, Object object,
                        @Bind Node inliningTarget,
                        @Cached("createFor($node)") IndirectCallData indirectCallData,
                        @CachedLibrary("object") PythonBufferAcquireLibrary bufferAcquireLib,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            PythonContext context = PythonContext.get(inliningTarget);
            PythonLanguage language = context.getLanguage(inliningTarget);
            Object buffer = bufferAcquireLib.acquireReadonly(object, frame, context, language, indirectCallData);
            try {
                if (bufferLib.getBufferLength(buffer) != 1) {
                    throw raiseNode.raise(inliningTarget, ValueError, SEP_MUST_BE_LENGTH_1);
                }
                byte b = bufferLib.readByte(buffer, 0);
                if (b < 0) {
                    throw raiseNode.raise(inliningTarget, ValueError, SEP_MUST_BE_ASCII);
                }
                return b;
            } finally {
                bufferLib.release(buffer, frame, context, language, indirectCallData);
            }
        }

        @SuppressWarnings("unused")
        @Fallback
        static byte error(VirtualFrame frame, Object value,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.SEP_MUST_BE_STR_OR_BYTES);
        }

        @ClinicConverterFactory
        @NeverDefault
        public static SepExpectByteNode create(@ClinicConverterFactory.DefaultValue Object defaultValue) {
            return BytesCommonBuiltinsFactory.SepExpectByteNodeGen.create(defaultValue);
        }
    }

    @Builtin(name = "hex", minNumOfPositionalArgs = 1, parameterNames = {"$self", "sep", "bytes_per_sep_group"})
    @ArgumentClinic(name = "sep", conversionClass = SepExpectByteNode.class, defaultValue = "PNone.NO_VALUE")
    @ArgumentClinic(name = "bytes_per_sep_group", conversionClass = ExpectIntNode.class, defaultValue = "1")
    @GenerateNodeFactory
    abstract static class HexNode extends PythonTernaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BytesCommonBuiltinsClinicProviders.HexNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = "check.execute(inliningTarget, self)")
        static TruffleString none(Object self, @SuppressWarnings("unused") PNone sep, @SuppressWarnings("unused") int bytesPerSepGroup,
                        @Bind Node inliningTarget,
                        @SuppressWarnings("unused") @Shared @Cached BytesLikeCheck check,
                        @Shared @Cached GetBytesStorage getBytesStorage,
                        @Shared @Cached InlinedConditionProfile earlyExit,
                        @Shared @Cached SequenceStorageNodes.GetInternalByteArrayNode getBytes,
                        @Shared @Cached BytesNodes.ByteToHexNode toHexNode) {
            return hex(self, (byte) 0, 0, inliningTarget, check, getBytesStorage, earlyExit, getBytes, toHexNode);
        }

        @Specialization(guards = "check.execute(inliningTarget, self)")
        static TruffleString hex(Object self, byte sep, int bytesPerSepGroup,
                        @Bind Node inliningTarget,
                        @SuppressWarnings("unused") @Shared @Cached BytesLikeCheck check,
                        @Shared @Cached GetBytesStorage getBytesStorage,
                        @Shared @Cached InlinedConditionProfile earlyExit,
                        @Shared @Cached SequenceStorageNodes.GetInternalByteArrayNode getBytes,
                        @Shared @Cached BytesNodes.ByteToHexNode toHexNode) {
            SequenceStorage storage = getBytesStorage.execute(inliningTarget, self);
            int len = storage.length();
            if (earlyExit.profile(inliningTarget, len == 0)) {
                return T_EMPTY_STRING;
            }
            byte[] b = getBytes.execute(inliningTarget, storage);
            return toHexNode.execute(inliningTarget, b, len, sep, bytesPerSepGroup);
        }

        @SuppressWarnings("unused")
        @Fallback
        static TruffleString err(Object self, Object sep, Object bytesPerSepGroup,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, DESCRIPTOR_NEED_OBJ, "hex", "bytes");
        }
    }

    @Builtin(name = "isascii", minNumOfPositionalArgs = 1, parameterNames = {"$self"})
    @ArgumentClinic(name = "$self", conversion = ClinicConversion.ReadableBuffer)
    @GenerateNodeFactory
    abstract static class IsASCIINode extends PythonUnaryClinicBuiltinNode {

        @Specialization(limit = "3")
        static boolean check(Object buffer,
                        @Bind Node inliningTarget,
                        @CachedLibrary("buffer") PythonBufferAccessLibrary bufferLib,
                        @Cached InlinedConditionProfile earlyExit) {
            try {
                int len = bufferLib.getBufferLength(buffer);
                if (earlyExit.profile(inliningTarget, len == 0)) {
                    return true;
                }
                byte[] b = bufferLib.getInternalOrCopiedByteArray(buffer);
                for (int i = 0; i < len; i++) {
                    if (b[i] < 0) {
                        return false;
                    }
                }
                return true;
            } finally {
                bufferLib.release(buffer);
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BytesCommonBuiltinsClinicProviders.IsASCIINodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "isalnum", minNumOfPositionalArgs = 1, parameterNames = {"$self"})
    @ArgumentClinic(name = "$self", conversion = ClinicConversion.ReadableBuffer)
    @GenerateNodeFactory
    abstract static class IsAlnumNode extends PythonUnaryClinicBuiltinNode {

        @Specialization(limit = "3")
        static boolean check(Object buffer,
                        @Bind Node inliningTarget,
                        @CachedLibrary("buffer") PythonBufferAccessLibrary bufferLib,
                        @Cached InlinedConditionProfile earlyExit) {
            try {
                int len = bufferLib.getBufferLength(buffer);
                if (earlyExit.profile(inliningTarget, len == 0)) {
                    return false;
                }
                byte[] b = bufferLib.getInternalOrCopiedByteArray(buffer);
                for (int i = 0; i < len; i++) {
                    if (!BytesUtils.isAlnum(b[i])) {
                        return false;
                    }
                }
                return true;
            } finally {
                bufferLib.release(buffer);
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BytesCommonBuiltinsClinicProviders.IsAlnumNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "isalpha", minNumOfPositionalArgs = 1, parameterNames = {"$self"})
    @ArgumentClinic(name = "$self", conversion = ClinicConversion.ReadableBuffer)
    @GenerateNodeFactory
    abstract static class IsAlphaNode extends PythonUnaryClinicBuiltinNode {

        @Specialization(limit = "3")
        static boolean check(Object buffer,
                        @Bind Node inliningTarget,
                        @CachedLibrary("buffer") PythonBufferAccessLibrary bufferLib,
                        @Cached InlinedConditionProfile earlyExit) {
            try {
                int len = bufferLib.getBufferLength(buffer);
                if (earlyExit.profile(inliningTarget, len == 0)) {
                    return false;
                }
                byte[] b = bufferLib.getInternalOrCopiedByteArray(buffer);
                for (int i = 0; i < len; i++) {
                    if (!BytesUtils.isAlpha(b[i])) {
                        return false;
                    }
                }
                return true;
            } finally {
                bufferLib.release(buffer);
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BytesCommonBuiltinsClinicProviders.IsAlphaNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "isdigit", minNumOfPositionalArgs = 1, parameterNames = {"$self"})
    @ArgumentClinic(name = "$self", conversion = ClinicConversion.ReadableBuffer)
    @GenerateNodeFactory
    abstract static class IsDigitNode extends PythonUnaryClinicBuiltinNode {

        @Specialization(limit = "3")
        static boolean check(Object buffer,
                        @Bind Node inliningTarget,
                        @CachedLibrary("buffer") PythonBufferAccessLibrary bufferLib,
                        @Cached InlinedConditionProfile earlyExit) {
            try {
                int len = bufferLib.getBufferLength(buffer);
                if (earlyExit.profile(inliningTarget, len == 0)) {
                    return false;
                }
                byte[] b = bufferLib.getInternalOrCopiedByteArray(buffer);
                for (int i = 0; i < len; i++) {
                    if (!BytesUtils.isDigit(b[i])) {
                        return false;
                    }
                }
                return true;
            } finally {
                bufferLib.release(buffer);
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BytesCommonBuiltinsClinicProviders.IsDigitNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "islower", minNumOfPositionalArgs = 1, parameterNames = {"$self"})
    @ArgumentClinic(name = "$self", conversion = ClinicConversion.ReadableBuffer)
    @GenerateNodeFactory
    abstract static class IsLowerNode extends PythonUnaryClinicBuiltinNode {

        @Specialization(limit = "3")
        static boolean check(Object buffer,
                        @Bind Node inliningTarget,
                        @CachedLibrary("buffer") PythonBufferAccessLibrary bufferLib,
                        @Cached InlinedConditionProfile earlyExit) {
            try {
                int len = bufferLib.getBufferLength(buffer);
                if (earlyExit.profile(inliningTarget, len == 0)) {
                    return false;
                }
                byte[] b = bufferLib.getInternalOrCopiedByteArray(buffer);
                int uncased = 0;
                for (int i = 0; i < len; i++) {
                    byte ch = b[i];
                    if (!BytesUtils.isLower(ch)) {
                        if (toLower(ch) == toUpper(ch)) {
                            uncased++;
                        } else {
                            return false;
                        }
                    }
                }
                return uncased == 0 || len > uncased;
            } finally {
                bufferLib.release(buffer);
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BytesCommonBuiltinsClinicProviders.IsLowerNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "isupper", minNumOfPositionalArgs = 1, parameterNames = {"$self"})
    @ArgumentClinic(name = "$self", conversion = ClinicConversion.ReadableBuffer)
    @GenerateNodeFactory
    abstract static class IsUpperNode extends PythonUnaryClinicBuiltinNode {

        @Specialization(limit = "3")
        static boolean check(Object buffer,
                        @Bind Node inliningTarget,
                        @CachedLibrary("buffer") PythonBufferAccessLibrary bufferLib,
                        @Cached InlinedConditionProfile earlyExit) {
            try {
                int len = bufferLib.getBufferLength(buffer);
                if (earlyExit.profile(inliningTarget, len == 0)) {
                    return false;
                }
                byte[] b = bufferLib.getInternalOrCopiedByteArray(buffer);
                int uncased = 0;
                for (int i = 0; i < len; i++) {
                    byte ch = b[i];
                    if (!BytesUtils.isUpper(ch)) {
                        if (toLower(ch) == toUpper(ch)) {
                            uncased++;
                        } else {
                            return false;
                        }
                    }
                }
                return uncased == 0 || len > uncased;
            } finally {
                bufferLib.release(buffer);
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BytesCommonBuiltinsClinicProviders.IsUpperNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "isspace", minNumOfPositionalArgs = 1, parameterNames = {"$self"})
    @ArgumentClinic(name = "$self", conversion = ClinicConversion.ReadableBuffer)
    @GenerateNodeFactory
    abstract static class IsSpaceNode extends PythonUnaryClinicBuiltinNode {

        @Specialization(limit = "3")
        static boolean check(Object buffer,
                        @Bind Node inliningTarget,
                        @CachedLibrary("buffer") PythonBufferAccessLibrary bufferLib,
                        @Cached InlinedConditionProfile earlyExit) {
            try {
                int len = bufferLib.getBufferLength(buffer);
                if (earlyExit.profile(inliningTarget, len == 0)) {
                    return false;
                }
                byte[] b = bufferLib.getInternalOrCopiedByteArray(buffer);
                for (int i = 0; i < len; i++) {
                    if (!BytesUtils.isSpace(b[i])) {
                        return false;
                    }
                }
                return true;
            } finally {
                bufferLib.release(buffer);
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BytesCommonBuiltinsClinicProviders.IsSpaceNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "istitle", minNumOfPositionalArgs = 1, parameterNames = {"$self"})
    @ArgumentClinic(name = "$self", conversion = ClinicConversion.ReadableBuffer)
    @GenerateNodeFactory
    abstract static class IsTitleNode extends PythonUnaryClinicBuiltinNode {

        @Specialization(limit = "3")
        static boolean check(Object buffer,
                        @Bind Node inliningTarget,
                        @CachedLibrary("buffer") PythonBufferAccessLibrary bufferLib,
                        @Cached InlinedConditionProfile earlyExit) {
            try {
                int len = bufferLib.getBufferLength(buffer);
                if (earlyExit.profile(inliningTarget, len == 0)) {
                    return false;
                }
                byte[] b = bufferLib.getInternalOrCopiedByteArray(buffer);
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
                    } else {
                        previousIsCased = false;
                    }
                }
                return cased;
            } finally {
                bufferLib.release(buffer);
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BytesCommonBuiltinsClinicProviders.IsTitleNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "center", minNumOfPositionalArgs = 2, parameterNames = {"$self", "width", "fillchar"})
    @GenerateNodeFactory
    abstract static class CenterNode extends PythonTernaryBuiltinNode {

        @Specialization
        @SuppressWarnings("truffle-static-method")  // TODO: inh
        PBytesLike bytes(VirtualFrame frame, Object self, Object widthObj, Object fillObj,
                        @Bind Node inliningTarget,
                        @Cached GetBytesStorage getBytesStorage,
                        @Cached SequenceStorageNodes.CopyNode copyNode,
                        @Cached BytesNodes.CreateBytesNode create,
                        @CachedLibrary(limit = "2") PythonBufferAccessLibrary bufferLib,
                        @Cached InlinedBranchProfile hasFill,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached PRaiseNode raiseNode) {
            int width = asSizeNode.executeExact(frame, inliningTarget, widthObj);
            SequenceStorage storage = getBytesStorage.execute(inliningTarget, self);
            int len = storage.length();
            byte fillByte = ' ';
            if (fillObj != PNone.NO_VALUE) {
                hasFill.enter(inliningTarget);
                if (fillObj instanceof PBytesLike && bufferLib.getBufferLength(fillObj) == 1) {
                    fillByte = bufferLib.readByte(fillObj, 0);
                } else {
                    throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.BYTE_STRING_OF_LEN_ONE_ONLY, methodName(), fillObj);
                }
            }
            if (checkSkip(len, width)) {
                return create.execute(inliningTarget, self, copyNode.execute(inliningTarget, storage));
            }
            return create.execute(inliningTarget, self, make(bufferLib.getCopiedByteArray(self), len, width, fillByte));
        }

        protected String methodName() {
            return "center()";
        }

        private byte[] pad(byte[] self, int len, int l, int r, byte fill) {
            int left = (l < 0) ? 0 : l;
            int right = (r < 0) ? 0 : r;
            if (left == 0 && right == 0) {
                return self;
            }

            byte[] u = new byte[left + len + right];
            if (left > 0) {
                Arrays.fill(u, 0, left, fill);
            }
            PythonUtils.arraycopy(self, 0, u, left, len);
            if (right > 0) {
                Arrays.fill(u, left + len, u.length, fill);
            }
            return u;
        }

        protected byte[] make(byte[] self, int len, int width, byte fillchar) {
            int marg = width - len;
            int left = marg / 2 + (marg & width & 1);
            return pad(self, len, left, marg - left, fillchar);
        }

        protected boolean checkSkip(int len, int width) {
            return len >= width;
        }
    }

    @Builtin(name = "ljust", minNumOfPositionalArgs = 2, parameterNames = {"$self", "width", "fillchar"})
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

    @Builtin(name = "rjust", minNumOfPositionalArgs = 2, parameterNames = {"$self", "width", "fillchar"})
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
    @ArgumentClinic(name = "old", conversion = ClinicConversion.ReadableBuffer)
    @ArgumentClinic(name = "replacement", conversion = ClinicConversion.ReadableBuffer)
    @ArgumentClinic(name = "count", conversionClass = ExpectIntNode.class, defaultValue = "Integer.MAX_VALUE")
    @GenerateNodeFactory
    abstract static class ReplaceNode extends PythonQuaternaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BytesCommonBuiltinsClinicProviders.ReplaceNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static PBytesLike replace(Object self, Object substrBuffer, Object replacementBuffer, int count,
                        @Bind Node inliningTarget,
                        @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                        @Cached GetBytesStorage getBytesStorage,
                        @Cached SequenceStorageNodes.GetInternalByteArrayNode toInternalBytes,
                        @Cached InlinedConditionProfile selfSubAreEmpty,
                        @Cached InlinedConditionProfile selfIsEmpty,
                        @Cached InlinedConditionProfile subIsEmpty,
                        @Cached BytesNodes.CreateBytesNode create) {
            try {
                SequenceStorage storage = getBytesStorage.execute(inliningTarget, self);
                int len = storage.length();
                byte[] bytes = toInternalBytes.execute(inliningTarget, storage);
                byte[] subBytes = bufferLib.getCopiedByteArray(substrBuffer);
                byte[] replacementBytes = bufferLib.getCopiedByteArray(replacementBuffer);
                int maxcount = count < 0 ? Integer.MAX_VALUE : count;
                if (selfSubAreEmpty.profile(inliningTarget, len == 0 && subBytes.length == 0)) {
                    return create.execute(inliningTarget, self, replacementBytes);
                }
                if (selfIsEmpty.profile(inliningTarget, len == 0)) {
                    return create.execute(inliningTarget, self, PythonUtils.EMPTY_BYTE_ARRAY);
                }
                if (subIsEmpty.profile(inliningTarget, subBytes.length == 0)) {
                    return create.execute(inliningTarget, self, replaceWithEmptySub(bytes, len, replacementBytes, maxcount));
                }
                byte[] newBytes = replace(bytes, len, subBytes, replacementBytes, maxcount);
                return create.execute(inliningTarget, self, newBytes);
            } finally {
                bufferLib.release(substrBuffer);
                bufferLib.release(replacementBuffer);
            }
        }

        @Fallback
        static boolean error(@SuppressWarnings("unused") Object self, Object substr, @SuppressWarnings("unused") Object replacement, @SuppressWarnings("unused") Object count,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, BYTESLIKE_OBJ_REQUIRED, substr);
        }

        @TruffleBoundary(allowInlining = true)
        static byte[] replaceWithEmptySub(byte[] bytes, int len, byte[] replacementBytes, int count) {
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

        @TruffleBoundary(allowInlining = true)
        static byte[] replace(byte[] bytes, int len, byte[] sub, byte[] replacementBytes, int count) {
            int i, j, pos, maxcount = count, subLen = sub.length, repLen = replacementBytes.length;
            List<byte[]> list = new ArrayList<>();

            int resultLen = 0;
            i = 0;
            while (maxcount-- > 0) {
                pos = BytesNodes.FindNode.find(bytes, len, sub, i, len, false);
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
        static PBytesLike replace(Object self,
                        @Bind Node node,
                        @Cached BytesNodes.ToBytesNode toBytes,
                        @Cached BytesNodes.CreateBytesNode create) {
            byte[] bytes = toBytes.execute(null, self);
            for (int i = 0; i < bytes.length; ++i) {
                bytes[i] = toLower(bytes[i]);
            }
            return create.execute(node, self, bytes);
        }
    }

    @Builtin(name = "upper", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class UpperNode extends PythonUnaryBuiltinNode {

        @Specialization
        static PBytesLike replace(Object self,
                        @Bind Node inliningTarget,
                        @Cached BytesNodes.ToBytesNode toBytes,
                        @Cached BytesNodes.CreateBytesNode create) {
            byte[] bytes = toBytes.execute(null, self);
            for (int i = 0; i < bytes.length; ++i) {
                bytes[i] = toUpper(bytes[i]);
            }
            return create.execute(inliningTarget, self, bytes);
        }
    }

    public abstract static class ExpectIntNode extends ArgumentCastNode {
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
        static int toInt(long x,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode raiseNode) {
            try {
                return PInt.intValueExact(x);
            } catch (OverflowException e) {
                throw raiseNode.raise(inliningTarget, PythonErrorType.OverflowError, ErrorMessages.PYTHON_INT_TOO_LARGE_TO_CONV_TO, "C long");
            }
        }

        @Specialization
        static int toInt(PInt x,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode raiseNode) {
            try {
                return x.intValueExact();
            } catch (OverflowException e) {
                throw raiseNode.raise(inliningTarget, PythonErrorType.OverflowError, ErrorMessages.PYTHON_INT_TOO_LARGE_TO_CONV_TO, "C long");
            }
        }

        @Specialization(guards = "!isNoValue(value)")
        static int doOthers(VirtualFrame frame, Object value,
                        @Bind Node inliningTarget,
                        @Cached("createRec()") ExpectIntNode rec,
                        @Cached PyNumberIndexNode indexNode) {
            return rec.executeInt(frame, indexNode.execute(frame, inliningTarget, value));
        }

        protected ExpectIntNode createRec() {
            return BytesCommonBuiltinsFactory.ExpectIntNodeGen.create(defaultValue);
        }

        @ClinicConverterFactory(shortCircuitPrimitive = ArgumentClinic.PrimitiveType.Int)
        @NeverDefault
        public static ExpectIntNode create(@ClinicConverterFactory.DefaultValue int defaultValue) {
            return BytesCommonBuiltinsFactory.ExpectIntNodeGen.create(defaultValue);
        }
    }

    public abstract static class ExpectByteLikeNode extends ArgumentCastNode {
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

        @Specialization(guards = {"!isPNone(object)"}, limit = "3")
        static byte[] doBuffer(VirtualFrame frame, Object object,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @Cached("createFor($node)") IndirectCallData indirectCallData,
                        @CachedLibrary("object") PythonBufferAcquireLibrary bufferAcquireLib,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib) {
            PythonLanguage language = context.getLanguage(inliningTarget);
            Object buffer = bufferAcquireLib.acquireReadonly(object, frame, context, language, indirectCallData);
            try {
                // TODO avoid copying
                return bufferLib.getCopiedByteArray(buffer);
            } finally {
                bufferLib.release(buffer, frame, context, language, indirectCallData);
            }
        }

        @ClinicConverterFactory
        @NeverDefault
        public static ExpectByteLikeNode create(@ClinicConverterFactory.DefaultValue byte[] defaultValue) {
            return BytesCommonBuiltinsFactory.ExpectByteLikeNodeGen.create(defaultValue);
        }

        @ClinicConverterFactory
        @NeverDefault
        public static ExpectByteLikeNode create() {
            return null;
        }
    }

    @GenerateCached(false)
    abstract static class AbstractSplitNode extends PythonTernaryClinicBuiltinNode {

        protected static final byte[] WHITESPACE = new byte[]{' '};

        protected abstract List<byte[]> splitWhitespace(byte[] bytes, int size, int maxsplit);

        protected abstract List<byte[]> splitSingle(byte[] bytes, int size, byte sep, int maxsplit);

        protected abstract List<byte[]> splitDelimiter(byte[] bytes, int size, byte[] sep, int maxsplit);

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
        PList whitespace(Object self, @SuppressWarnings("unused") byte[] sep, int maxsplit,
                        @Bind Node inliningTarget,
                        @Shared @Cached GetBytesStorage getBytesStorage,
                        @Shared("toBytes") @Cached SequenceStorageNodes.GetInternalByteArrayNode selfToBytesNode,
                        @Shared("append") @Cached ListNodes.AppendNode appendNode,
                        @Shared("create") @Cached BytesNodes.CreateBytesNode createBytesNode,
                        @Bind PythonLanguage language) {
            SequenceStorage storage = getBytesStorage.execute(inliningTarget, self);
            byte[] splitBs = selfToBytesNode.execute(inliningTarget, storage);
            return getBytesResult(splitWhitespace(splitBs, storage.length(), adjustMaxSplit(maxsplit)), appendNode, self, inliningTarget, createBytesNode, language);
        }

        @Specialization(guards = {"!isWhitespace(sep)", "isSingleSep(sep)"})
        PList single(Object self, byte[] sep, int maxsplit,
                        @Bind Node inliningTarget,
                        @Shared @Cached GetBytesStorage getBytesStorage,
                        @Shared("toBytes") @Cached SequenceStorageNodes.GetInternalByteArrayNode selfToBytesNode,
                        @Shared("append") @Cached ListNodes.AppendNode appendNode,
                        @Shared("create") @Cached BytesNodes.CreateBytesNode createBytesNode,
                        @Bind PythonLanguage language) {
            SequenceStorage storage = getBytesStorage.execute(inliningTarget, self);
            byte[] splitBs = selfToBytesNode.execute(inliningTarget, storage);
            return getBytesResult(splitSingle(splitBs, storage.length(), sep[0], adjustMaxSplit(maxsplit)), appendNode, self, inliningTarget, createBytesNode, language);
        }

        @Specialization(guards = {"!isWhitespace(sep)", "!isEmptySep(sep)", "!isSingleSep(sep)"})
        PList split(Object self, byte[] sep, int maxsplit,
                        @Bind Node inliningTarget,
                        @Shared @Cached GetBytesStorage getBytesStorage,
                        @Shared("toBytes") @Cached SequenceStorageNodes.GetInternalByteArrayNode selfToBytesNode,
                        @Shared("append") @Cached ListNodes.AppendNode appendNode,
                        @Shared("create") @Cached BytesNodes.CreateBytesNode createBytesNode,
                        @Bind PythonLanguage language) {
            SequenceStorage storage = getBytesStorage.execute(inliningTarget, self);
            byte[] splitBs = selfToBytesNode.execute(inliningTarget, storage);
            return getBytesResult(splitDelimiter(splitBs, storage.length(), sep, adjustMaxSplit(maxsplit)), appendNode, self, inliningTarget, createBytesNode, language);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"isEmptySep(sep)"})
        static PList error(Object bytes, byte[] sep, int maxsplit,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, PythonErrorType.ValueError, ErrorMessages.EMPTY_SEPARATOR);
        }

        private static PList getBytesResult(List<byte[]> bytes, ListNodes.AppendNode appendNode, Object self, Node inliningTarget, BytesNodes.CreateBytesNode createBytesNode,
                        PythonLanguage language) {
            PList result = PFactory.createList(language);
            Iterator<byte[]> it = iterator(bytes);
            while (hasNext(it)) {
                appendNode.execute(result, createBytesNode.execute(inliningTarget, self, next(it)));
            }
            return result;
        }
    }

    @Builtin(name = "split", minNumOfPositionalArgs = 1, parameterNames = {"$self", "sep", "maxsplit"})
    @ArgumentClinic(name = "sep", conversionClass = ExpectByteLikeNode.class, defaultValue = "BytesCommonBuiltins.AbstractSplitNode.WHITESPACE")
    @ArgumentClinic(name = "maxsplit", conversionClass = ExpectIntNode.class, defaultValue = "Integer.MAX_VALUE")
    @GenerateNodeFactory
    abstract static class SplitNode extends AbstractSplitNode {

        protected int find(byte[] bytes, int len, byte[] sep, int start, int end) {
            return BytesNodes.FindNode.find(bytes, len, sep, start, end, false);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BytesCommonBuiltinsClinicProviders.SplitNodeClinicProviderGen.INSTANCE;
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
    @ArgumentClinic(name = "sep", conversionClass = ExpectByteLikeNode.class, defaultValue = "BytesCommonBuiltins.AbstractSplitNode.WHITESPACE")
    @ArgumentClinic(name = "maxsplit", conversionClass = ExpectIntNode.class, defaultValue = "Integer.MAX_VALUE")
    @GenerateNodeFactory
    abstract static class RSplitNode extends AbstractSplitNode {

        protected int find(byte[] bytes, int len, byte[] sep, int start, int end) {
            return BytesNodes.FindNode.find(bytes, len, sep, start, end, true);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BytesCommonBuiltinsClinicProviders.RSplitNodeClinicProviderGen.INSTANCE;
        }

        @TruffleBoundary
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
        static PList doSplitlines(Object self, Object keependsObj,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached InlinedBranchProfile isPNoneProfile,
                        @Cached InlinedBranchProfile isBooleanProfile,
                        @Cached InlinedConditionProfile keependsProfile,
                        @Cached CastToJavaIntExactNode cast,
                        @Cached BytesNodes.ToBytesNode toBytesNode,
                        @Cached ListNodes.AppendNode appendNode,
                        @Cached BytesNodes.CreateBytesNode create) {
            boolean keepends;
            if (keependsObj instanceof Boolean b) {
                isBooleanProfile.enter(inliningTarget);
                keepends = b;
            } else if (PGuards.isPNone(keependsObj)) {
                isPNoneProfile.enter(inliningTarget);
                keepends = false;
            } else {
                keepends = cast.execute(inliningTarget, keependsObj) != 0;
            }
            keepends = keependsProfile.profile(inliningTarget, keepends);
            byte[] bytes = toBytesNode.execute(null, self);
            PList list = PFactory.createList(language);
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
                    appendNode.execute(list, create.execute(inliningTarget, self, slice));
                    sliceStart = i + 1;
                }
            }
            // Process the remaining part if any
            if (sliceStart != bytes.length) {
                byte[] slice = copySlice(bytes, sliceStart, bytes.length);
                appendNode.execute(list, create.execute(inliningTarget, self, slice));
            }
            return list;
        }

        private static byte[] copySlice(byte[] bytes, int sliceStart, int sliceEnd) {
            byte[] slice = new byte[sliceEnd - sliceStart];
            PythonUtils.arraycopy(bytes, sliceStart, slice, 0, slice.length);
            return slice;
        }
    }

    @GenerateCached(false)
    abstract static class AStripNode extends PythonBinaryBuiltinNode {

        @Specialization
        PBytesLike strip(VirtualFrame frame, Object self, @SuppressWarnings("unused") PNone bytes,
                        @Bind Node node,
                        @Shared("createByte") @Cached BytesNodes.CreateBytesNode create,
                        @Shared("toByteSelf") @Cached BytesNodes.ToBytesNode toBytesNode) {
            byte[] bs = toBytesNode.execute(frame, self);
            return create.execute(node, self, getResultBytes(bs, findIndex(bs)));
        }

        @Specialization(guards = "!isPNone(object)")
        PBytesLike strip(VirtualFrame frame, Object self, Object object,
                        @Bind Node node,
                        @Cached("createFor($node)") IndirectCallData indirectCallData,
                        @CachedLibrary(limit = "3") PythonBufferAcquireLibrary bufferAcquireLib,
                        @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                        @Shared("createByte") @Cached BytesNodes.CreateBytesNode create,
                        @Shared("toByteSelf") @Cached BytesNodes.ToBytesNode selfToBytesNode) {
            Object buffer = bufferAcquireLib.acquireReadonly(object, frame, indirectCallData);
            try {
                byte[] stripBs = bufferLib.getInternalOrCopiedByteArray(buffer);
                int stripBsLen = bufferLib.getBufferLength(buffer);
                byte[] bs = selfToBytesNode.execute(frame, self);
                return create.execute(node, self, getResultBytes(bs, findIndex(bs, stripBs, stripBsLen)));
            } finally {
                bufferLib.release(buffer, frame, indirectCallData);
            }
        }

        @Fallback
        @SuppressWarnings("unused")
        static Object strip(Object self, Object object,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, SystemError, ErrorMessages.INVALID_ARGS, "lstrip/rstrip");
        }

        protected abstract int mod();

        protected abstract int stop(byte[] bs);

        protected abstract int start(byte[] bs);

        protected abstract byte[] getResultBytes(byte[] bs, int i);

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

        @TruffleBoundary
        private static boolean isWhitespace(byte b) {
            return Character.isWhitespace(b);
        }

        protected int findIndex(byte[] bs, byte[] stripBs, int stripBsLen) {
            int i = start(bs);
            int stop = stop(bs);
            outer: for (; i != stop; i += mod()) {
                for (int j = 0; j < stripBsLen; j++) {
                    if (stripBs[j] == bs[i]) {
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

        @NeverDefault
        static LStripNode create() {
            return LStripNodeFactory.create();
        }

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
        protected int mod() {
            return 1;
        }

        @Override
        protected int stop(byte[] bs) {
            return bs.length;
        }

        @Override
        protected int start(byte[] bs) {
            return 0;
        }
    }

    @Builtin(name = "rstrip", minNumOfPositionalArgs = 1, parameterNames = {"self", "bytes"})
    @GenerateNodeFactory
    abstract static class RStripNode extends AStripNode {

        @NeverDefault
        static RStripNode create() {
            return RStripNodeFactory.create();
        }

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
        protected int mod() {
            return -1;
        }

        @Override
        protected int stop(byte[] bs) {
            return -1;
        }

        @Override
        protected int start(byte[] bs) {
            return bs.length - 1;
        }
    }

    // static bytes.maketrans()
    // static bytearray.maketrans()
    @Builtin(name = "maketrans", minNumOfPositionalArgs = 3, isStaticmethod = true)
    @GenerateNodeFactory
    public abstract static class MakeTransNode extends PythonBuiltinNode {

        @Specialization
        static PBytes maketrans(VirtualFrame frame, @SuppressWarnings("unused") Object cls, Object from, Object to,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached BytesNodes.ToBytesNode toByteNode,
                        @Cached PRaiseNode raiseNode) {
            byte[] fromB = toByteNode.execute(frame, from);
            byte[] toB = toByteNode.execute(frame, to);
            if (fromB.length != toB.length) {
                throw raiseNode.raise(inliningTarget, PythonErrorType.ValueError, ErrorMessages.ARGS_MUST_HAVE_SAME_LENGTH, "maketrans");
            }

            byte[] table = new byte[256];
            for (int i = 0; i < 256; i++) {
                table[i] = (byte) i;
            }

            for (int i = 0; i < fromB.length; i++) {
                byte value = fromB[i];
                table[value < 0 ? value + 256 : value] = toB[i];
            }

            return PFactory.createBytes(language, table);
        }

    }

    @Builtin(name = "capitalize", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class CapitalizeNode extends PythonUnaryBuiltinNode {

        @Specialization
        static PBytesLike capitalize(Object self,
                        @Bind Node inliningTarget,
                        @Cached ToBytesNode toBytesNode,
                        @Cached BytesNodes.CreateBytesNode create) {
            byte[] b = toBytesNode.execute(null, self);
            if (b.length == 0) {
                return create.execute(inliningTarget, self, PythonUtils.EMPTY_BYTE_ARRAY);
            }
            b[0] = toUpper(b[0]);
            for (int i = 1; i < b.length; i++) {
                b[i] = toLower(b[i]);
            }
            return create.execute(inliningTarget, self, b);
        }
    }

    @Builtin(name = "title", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class TitleNode extends PythonUnaryBuiltinNode {

        @Specialization
        static PBytesLike title(Object self,
                        @Bind Node inliningTarget,
                        @Cached ToBytesNode toBytesNode,
                        @Cached BytesNodes.CreateBytesNode create) {
            byte[] b = toBytesNode.execute(null, self);
            if (b.length == 0) {
                return create.execute(inliningTarget, self, PythonUtils.EMPTY_BYTE_ARRAY);
            }
            boolean previousIsCased = false;

            for (int i = 0; i < b.length; i++) {
                byte c = b[i];
                if (BytesUtils.isLower(c)) {
                    if (!previousIsCased) {
                        c = toUpper(c);
                    }
                    previousIsCased = true;
                } else if (BytesUtils.isUpper(c)) {
                    if (previousIsCased) {
                        c = toLower(c);
                    }
                    previousIsCased = true;
                } else {
                    previousIsCased = false;
                }
                b[i] = c;
            }

            return create.execute(inliningTarget, self, b);
        }
    }

    @Builtin(name = "swapcase", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class SwapCaseNode extends PythonUnaryBuiltinNode {

        @Specialization
        static PBytesLike swapcase(Object self,
                        @Bind Node inliningTarget,
                        @Cached ToBytesNode toBytesNode,
                        @Cached BytesNodes.CreateBytesNode create) {
            byte[] b = toBytesNode.execute(null, self);
            if (b.length == 0) {
                return create.execute(inliningTarget, self, PythonUtils.EMPTY_BYTE_ARRAY);
            }
            for (int i = 0; i < b.length; i++) {
                if (BytesUtils.isUpper(b[i])) {
                    b[i] = toLower(b[i]);
                } else {
                    b[i] = toUpper(b[i]);
                }
            }
            return create.execute(inliningTarget, self, b);
        }
    }

    @Builtin(name = "expandtabs", minNumOfPositionalArgs = 1, parameterNames = {"self", "tabsize"})
    @ArgumentClinic(name = "tabsize", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "8")
    @GenerateNodeFactory
    abstract static class ExpandTabsNode extends PythonBinaryClinicBuiltinNode {

        private static final byte T = '\t';
        private static final byte N = '\n';
        private static final byte R = '\r';
        private static final byte S = ' ';

        @Specialization
        static PBytesLike expandtabs(Object self, int tabsize,
                        @Bind Node inliningTarget,
                        @Cached GetBytesStorage getBytesStorage,
                        @Cached GetInternalByteArrayNode getInternalByteArrayNode,
                        @Cached BytesNodes.CreateBytesNode create,
                        @Cached PRaiseNode raiseNode) {
            SequenceStorage storage = getBytesStorage.execute(inliningTarget, self);
            int len = storage.length();
            if (len == 0) {
                return create.execute(inliningTarget, self, PythonUtils.EMPTY_BYTE_ARRAY);
            }
            int max = SysModuleBuiltins.MAXSIZE;
            byte[] b = getInternalByteArrayNode.execute(inliningTarget, storage);
            int i = 0, j = 0;
            for (int k = 0; k < len; k++) {
                byte p = b[k];
                if (p == T) {
                    if (tabsize > 0) {
                        int incr = tabsize - (j % tabsize);
                        if (j > max - incr) {
                            throw raiseNode.raise(inliningTarget, OverflowError, ErrorMessages.RESULT_TOO_LONG);
                        }
                        j += incr;
                    }
                } else {
                    if (j > max - 1) {
                        throw raiseNode.raise(inliningTarget, OverflowError, ErrorMessages.RESULT_TOO_LONG);
                    }
                    j++;
                    if (p == N || p == R) {
                        if (i > max - j) {
                            throw raiseNode.raise(inliningTarget, OverflowError, ErrorMessages.RESULT_TOO_LONG);
                        }
                        i += j;
                        j = 0;
                    }
                }
            }
            if (i > max - j) {
                throw raiseNode.raise(inliningTarget, OverflowError, ErrorMessages.RESULT_TOO_LONG);
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
            return create.execute(inliningTarget, self, q);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BytesCommonBuiltinsClinicProviders.ExpandTabsNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "zfill", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"$self", "width"})
    @ArgumentClinic(name = "width", conversion = ArgumentClinic.ClinicConversion.Index)
    @GenerateNodeFactory
    abstract static class ZFillNode extends PythonBinaryClinicBuiltinNode {

        @Specialization
        static PBytesLike zfill(Object self, int width,
                        @Bind Node inliningTarget,
                        @Cached GetBytesStorage getBytesStorage,
                        @Cached GetInternalByteArrayNode getInternalByteArrayNode,
                        @Cached BytesNodes.CreateBytesNode create) {
            SequenceStorage storage = getBytesStorage.execute(inliningTarget, self);
            return create.execute(inliningTarget, self, zfill(getInternalByteArrayNode.execute(inliningTarget, storage), storage.length(), width));
        }

        private static byte[] zfill(byte[] self, int len, int width) {
            if (len >= width) {
                return self;
            }

            int fill = width - len;
            byte[] p = pad(self, len, fill, 0, (byte) '0');

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

        private static byte[] pad(byte[] self, int len, int l, int r, byte fillChar) {
            int left = (l < 0) ? 0 : l;
            int right = (r < 0) ? 0 : r;
            if (left == 0 && right == 0) {
                return self;
            }

            byte[] u = new byte[left + len + right];
            if (left > 0) {
                Arrays.fill(u, 0, left, fillChar);
            }
            for (int i = left, j = 0; i < (left + len); j++, i++) {
                u[i] = self[j];
            }
            if (right > 0) {
                Arrays.fill(u, left + len, u.length, fillChar);
            }
            return u;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BytesCommonBuiltinsClinicProviders.ZFillNodeClinicProviderGen.INSTANCE;
        }
    }

    @TruffleBoundary
    static byte[] copyOfRange(byte[] bytes, int from, int to) {
        return Arrays.copyOfRange(bytes, from, to);
    }

    @TruffleBoundary(allowInlining = true)
    static Iterator<byte[]> iterator(List<byte[]> bytes) {
        return bytes.iterator();
    }

    @TruffleBoundary(allowInlining = true)
    static byte[] next(Iterator<byte[]> it) {
        return it.next();
    }

    @TruffleBoundary(allowInlining = true)
    static boolean hasNext(Iterator<byte[]> it) {
        return it.hasNext();
    }

    @Builtin(name = J___GETNEWARGS__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class GetNewargsNode extends PythonUnaryBuiltinNode {
        @Specialization
        static PTuple doBytes(Object self,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached GetBytesStorage getBytesStorage) {
            return PFactory.createTuple(language, new Object[]{PFactory.createBytes(language, getBytesStorage.execute(inliningTarget, self))});
        }
    }

    @Builtin(name = J_REMOVEPREFIX, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class RemovePrefixNode extends PythonBinaryBuiltinNode {
        @Specialization
        static PBytesLike remove(VirtualFrame frame, Object self, Object prefix,
                        @Bind Node node,
                        @Cached("createFor($node)") IndirectCallData indirectCallData,
                        @CachedLibrary(limit = "1") PythonBufferAcquireLibrary bufferAcquireLib,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib,
                        @Cached BytesNodes.CreateBytesNode create,
                        @Cached InlinedConditionProfile profile) {

            Object selfBuffer = bufferAcquireLib.acquireReadonly(self, frame, indirectCallData);
            Object prefixBuffer = bufferAcquireLib.acquireReadonly(prefix, frame, indirectCallData);
            try {
                int selfBsLen = bufferLib.getBufferLength(selfBuffer);
                int prefixBsLen = bufferLib.getBufferLength(prefixBuffer);

                byte[] selfBs = bufferLib.getInternalOrCopiedByteArray(selfBuffer);
                if (profile.profile(node, selfBsLen >= prefixBsLen && prefixBsLen > 0)) {
                    byte[] prefixBs = bufferLib.getInternalOrCopiedByteArray(prefixBuffer);
                    byte[] result = new byte[selfBsLen - prefixBsLen];
                    int j = 0;
                    for (int i = 0; i < selfBsLen; i++) {
                        if (i < prefixBsLen) {
                            if (selfBs[i] != prefixBs[i]) {
                                return create.execute(node, self, selfBs);
                            }
                        } else {
                            result[j++] = selfBs[i];
                        }
                    }
                    return create.execute(node, self, result);
                }
                return create.execute(node, self, selfBs);
            } finally {
                bufferLib.release(selfBuffer, frame, indirectCallData);
                bufferLib.release(prefixBuffer, frame, indirectCallData);
            }
        }
    }

    @Builtin(name = J_REMOVESUFFIX, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class RemoveSuffixNode extends PythonBinaryBuiltinNode {
        @Specialization
        static PBytesLike remove(VirtualFrame frame, Object self, Object suffix,
                        @Bind Node node,
                        @Cached("createFor($node)") IndirectCallData indirectCallData,
                        @CachedLibrary(limit = "1") PythonBufferAcquireLibrary bufferAcquireLib,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib,
                        @Cached BytesNodes.CreateBytesNode create,
                        @Cached InlinedConditionProfile profile) {
            Object selfBuffer = bufferAcquireLib.acquireReadonly(self, frame, indirectCallData);
            Object suffixBuffer = bufferAcquireLib.acquireReadonly(suffix, frame, indirectCallData);
            try {
                int selfBsLen = bufferLib.getBufferLength(selfBuffer);
                int suffixBsLen = bufferLib.getBufferLength(suffixBuffer);

                byte[] selfBs = bufferLib.getInternalOrCopiedByteArray(selfBuffer);
                if (profile.profile(node, selfBsLen >= suffixBsLen && suffixBsLen > 0)) {
                    byte[] suffixBs = bufferLib.getInternalOrCopiedByteArray(suffixBuffer);
                    byte[] result = new byte[selfBsLen - suffixBsLen];
                    int k = 1;
                    for (int i = selfBsLen - 1, j = 1; i >= 0; i--, j++) {
                        if (i >= selfBsLen - suffixBsLen) {
                            if (selfBs[i] != suffixBs[suffixBsLen - j]) {
                                return create.execute(node, self, selfBs);
                            }
                        } else {
                            result[result.length - k++] = selfBs[i];
                        }
                    }
                    return create.execute(node, self, result);
                }
                return create.execute(node, self, selfBs);
            } finally {
                bufferLib.release(selfBuffer, frame, indirectCallData);
                bufferLib.release(suffixBuffer, frame, indirectCallData);
            }
        }
    }
}
