/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.cjkcodecs;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.MultibyteIncrementalEncoder;
import static com.oracle.graal.python.builtins.modules.cjkcodecs.MultibyteCodecUtil.MBENC_RESET;
import static com.oracle.graal.python.builtins.modules.cjkcodecs.MultibyteCodecUtil.MULTIBYTECODECSTATE;
import static com.oracle.graal.python.builtins.modules.cjkcodecs.MultibyteCodecUtil.encodeEmptyInput;
import static com.oracle.graal.python.builtins.modules.cjkcodecs.MultibyteCodecUtil.internalErrorCallback;
import static com.oracle.graal.python.builtins.modules.cjkcodecs.MultibytecodecModuleBuiltins.MBENC_FLUSH;
import static com.oracle.graal.python.nodes.ErrorMessages.CODEC_IS_UNEXPECTED_TYPE;
import static com.oracle.graal.python.nodes.ErrorMessages.COULDN_T_CONVERT_THE_OBJECT_TO_STR;
import static com.oracle.graal.python.nodes.ErrorMessages.PENDING_BUFFER_OVERFLOW;
import static com.oracle.graal.python.nodes.ErrorMessages.PENDING_BUFFER_TOO_LARGE;
import static com.oracle.graal.python.nodes.StringLiterals.T_STRICT;
import static com.oracle.graal.python.nodes.StringLiterals.T_UTF8;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.UnicodeError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.CodecsModuleBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.ints.IntNodes;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectStrAsObjectNode;
import com.oracle.graal.python.lib.PyUnicodeCheckNode;
import com.oracle.graal.python.nodes.HiddenAttr;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.StringLiterals;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = MultibyteIncrementalEncoder)
public final class MultibyteIncrementalEncoderBuiltins extends PythonBuiltins {

    private static final int MAXENCPENDING = 2;

    public static final TpSlots SLOTS = MultibyteIncrementalEncoderBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return MultibyteIncrementalEncoderBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(minNumOfPositionalArgs = 1, parameterNames = {"$cls", "errors"})
    @GenerateNodeFactory
    protected abstract static class NewNode extends PythonBinaryBuiltinNode {

        @Specialization
        static Object mbstreamreaderNew(VirtualFrame frame, Object type, Object err,
                        @Bind Node inliningTarget,
                        @Cached CastToTruffleStringNode castToStringNode,
                        @Cached PyObjectGetAttr getAttr,
                        @Cached TruffleString.EqualNode isEqual,
                        @Bind PythonLanguage language,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape,
                        @Cached PRaiseNode raiseNode) { // "|s:IncrementalEncoder"

            TruffleString errors = null;
            if (err != PNone.NO_VALUE) {
                errors = castToStringNode.execute(inliningTarget, err);
            }

            MultibyteIncrementalEncoderObject self = PFactory.createMultibyteIncrementalEncoderObject(language, type, getInstanceShape.execute(type));

            Object codec = getAttr.execute(frame, inliningTarget, type, StringLiterals.T_CODEC);
            if (!(codec instanceof MultibyteCodecObject)) {
                throw raiseNode.raise(inliningTarget, TypeError, CODEC_IS_UNEXPECTED_TYPE);
            }

            self.codec = ((MultibyteCodecObject) codec).codec;
            self.pending = null;
            self.errors = internalErrorCallback(errors, isEqual);
            self.codec.encinit(self.errors);
            return self;
        }
    }

    @Slot(value = SlotKind.tp_init, isComplex = true)
    @SlotSignature(name = "MultibyteIncrementalEncoder", minNumOfPositionalArgs = 1, parameterNames = {"$self"})
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonUnaryBuiltinNode {

        @Specialization
        static PNone init(@SuppressWarnings("unused") MultibyteIncrementalEncoderObject self) {
            return PNone.NONE;
        }
    }

    @ImportStatic(PGuards.class)
    @SuppressWarnings("truffle-inlining")       // footprint reduction 44 -> 25
    protected abstract static class EncodeStatefulNode extends Node {

        abstract Object execute(VirtualFrame frame, MultibyteStatefulEncoderContext ctx, Object unistr, int end);

        // encoder_encode_stateful
        @Specialization
        static Object ts(VirtualFrame frame, MultibyteStatefulEncoderContext ctx, TruffleString ucvt, int end,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached MultibyteCodecUtil.EncodeNode encodeNode,
                        @Shared @Cached TruffleString.ConcatNode concatNode,
                        @Shared @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Shared @Cached TruffleString.SubstringNode substringNode,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            TruffleString inbuf = ucvt;
            TruffleString origpending = null;
            if (ctx.pending != null) {
                origpending = ctx.pending;
                inbuf = concatNode.execute(ctx.pending, ucvt, TS_ENCODING, false);
                ctx.pending = null;
            }

            int datalen = codePointLengthNode.execute(inbuf, TS_ENCODING);
            PBytes r;
            try {
                r = encodeEmptyInput(inliningTarget, datalen, MBENC_FLUSH | MBENC_RESET);
                if (r == null) {
                    MultibyteEncodeBuffer buf = new MultibyteEncodeBuffer(inbuf);
                    r = encodeNode.execute(frame, inliningTarget, ctx.codec, ctx.state, buf,
                                    ctx.errors, end != 0 ? MBENC_FLUSH | MBENC_RESET : 0);
                    if (buf.getInpos() < datalen) {
                        if (datalen - buf.getInpos() > MAXENCPENDING) {
                            /* normal codecs can't reach here */
                            throw raiseNode.raise(inliningTarget, UnicodeError, PENDING_BUFFER_OVERFLOW);
                        }
                        ctx.pending = substringNode.execute(inbuf, buf.getInpos(), datalen, TS_ENCODING, false);
                    }
                }
            } catch (Exception e) {
                /* recover the original pending buffer */
                ctx.pending = origpending;
                throw e;
            }

            return r;
        }

        @Specialization(guards = "!isTruffleString(unistr)")
        static Object notTS(VirtualFrame frame, MultibyteStatefulEncoderContext ctx, Object unistr, int end,
                        @Bind Node inliningTarget,
                        @Cached PyObjectStrAsObjectNode strNode,
                        @Cached PyUnicodeCheckNode unicodeCheckNode,
                        @Cached CastToTruffleStringNode toTruffleStringNode,
                        @Exclusive @Cached MultibyteCodecUtil.EncodeNode encodeNode,
                        @Shared @Cached TruffleString.ConcatNode concatNode,
                        @Shared @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Shared @Cached TruffleString.SubstringNode substringNode,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            Object ucvt = unistr;
            if (!unicodeCheckNode.execute(inliningTarget, unistr)) {
                ucvt = strNode.execute(frame, inliningTarget, unistr);
                if (!unicodeCheckNode.execute(inliningTarget, unistr)) {
                    throw raiseNode.raise(inliningTarget, TypeError, COULDN_T_CONVERT_THE_OBJECT_TO_STR);
                }
            }
            TruffleString str = toTruffleStringNode.execute(inliningTarget, ucvt);
            return ts(frame, ctx, str, end, inliningTarget, encodeNode, concatNode, codePointLengthNode, substringNode, raiseNode);
        }

    }

    @Builtin(name = "encode", minNumOfPositionalArgs = 1, parameterNames = {"$self", "input", "final"}, doc = "encode($self, /, input, final=False)\n--\n\n")
    @ArgumentClinic(name = "final", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "-1", useDefaultForNone = true)
    // PyFloat_Check throw raise(TypeError, INTEGER_ARGUMENT_EXPECTED_GOT_FLOAT );
    @GenerateNodeFactory
    abstract static class EncodeNode extends PythonTernaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return MultibyteIncrementalEncoderBuiltinsClinicProviders.EncodeNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object encode(VirtualFrame frame, MultibyteStatefulEncoderContext ctx, Object unistr, int end,
                        @Cached EncodeStatefulNode encodeStatefulNode) {
            return encodeStatefulNode.execute(frame, ctx, unistr, end);
        }
    }

    @Builtin(name = "getstate", minNumOfPositionalArgs = 1, parameterNames = {"$self"}, doc = "getstate($self, /)\n--\n\n")
    @GenerateNodeFactory
    abstract static class GetStateNode extends PythonUnaryBuiltinNode {

        // _multibytecodec_MultibyteIncrementalEncoder_getstate_impl
        @Specialization
        static Object getstate(MultibyteIncrementalEncoderObject self,
                        @Bind Node inliningTarget,
                        @Cached HiddenAttr.WriteNode writeHiddenAttrNode,
                        @Cached CodecsModuleBuiltins.CodecsEncodeToJavaBytesNode asUTF8AndSize,
                        @Cached IntNodes.PyLongFromByteArray fromByteArray,
                        @Cached PRaiseNode raiseNode) {
            /*
             * state made up of 1 byte for buffer size, up to MAXENCPENDING*4 bytes for UTF-8
             * encoded buffer (each character can use up to 4 bytes), and required bytes for
             * MultibyteCodec_State.c. A byte array is used to avoid different compilers generating
             * different values for the same state, e.g. as a result of struct padding.
             */
            byte[] statebytes = new byte[1 + MAXENCPENDING * 4 + MULTIBYTECODECSTATE];
            statebytes[0] = 0;
            // int statesize = 1;

            if (self.pending != null) {
                byte[] pendingbuffer = asUTF8AndSize.execute(self.pending, T_UTF8, T_STRICT);
                int pendingsize = pendingbuffer.length;
                if (pendingsize > MAXENCPENDING * 4) {
                    throw raiseNode.raise(inliningTarget, UnicodeError, PENDING_BUFFER_TOO_LARGE);
                }
                statebytes[0] = (byte) pendingsize;
                PythonUtils.arraycopy(pendingbuffer, 0, statebytes, 1, pendingsize);
                // statesize = 1 + pendingsize;
            }
            // mq: we will ignore setting the state and opt on using a hidden key
            // for the encoder object.
            // memcpy(statebytes + statesize, self.state.c, MULTIBYTECODECSTATE);
            // statesize += MULTIBYTECODECSTATE;
            Object stateobj = fromByteArray.execute(inliningTarget, statebytes, true, true);
            assert (stateobj instanceof PInt); // since statebytes.length > 8, we will get a PInt
            writeHiddenAttrNode.execute(inliningTarget, (PInt) stateobj, HiddenAttr.ENCODER_OBJECT, self.state);
            return stateobj;
        }
    }

    @Builtin(name = "setstate", parameterNames = {"$self", "state"}, doc = "setstate($self, state, /)\n--\n\n")
    @GenerateNodeFactory
    abstract static class SetStateNode extends PythonBinaryBuiltinNode {

        @Specialization
        // _multibytecodec_MultibyteIncrementalEncoder_setstate_impl
        static Object setstate(MultibyteIncrementalEncoderObject self, PInt statelong,
                        @Bind Node inliningTarget,
                        @Cached HiddenAttr.ReadNode readHiddenAttrNode,
                        @Cached IntNodes.PyLongAsByteArray asByteArray,
                        @Cached PRaiseNode raiseNode) {
            int sizeOfStateBytes = 1 + MAXENCPENDING * 4 + MULTIBYTECODECSTATE;

            byte[] statebytes = asByteArray.execute(inliningTarget, statelong, sizeOfStateBytes, false);

            if (statebytes[0] > MAXENCPENDING * 4) {
                throw raiseNode.raise(inliningTarget, UnicodeError, PENDING_BUFFER_TOO_LARGE);
            }

            self.pending = decodeUTF8(statebytes, 1, statebytes[0]);

            // PythonUtils.arraycopy(statebytes, 1 + statebytes[0], self.state.c, 0,
            // MULTIBYTECODECSTATE);
            Object s = readHiddenAttrNode.execute(inliningTarget, statelong, HiddenAttr.ENCODER_OBJECT, null);
            assert s == null || s instanceof MultibyteCodecState : "Not MultibyteCodecState object!";
            self.state = (MultibyteCodecState) s;
            return PNone.NONE;
        }

        @TruffleBoundary
        private static TruffleString decodeUTF8(byte[] buf, int off, int len) { // T_STRICT
            ByteBuffer in = ByteBuffer.wrap(buf, off, len);
            CharBuffer out = CharBuffer.allocate(len * 4);
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
            decoder.onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(in, out, true);
            return TruffleString.fromCharArrayUTF16Uncached(out.array());
        }
    }

    @Builtin(name = "reset", minNumOfPositionalArgs = 1, parameterNames = {"$self"}, doc = "reset($self, /)\n--\n\n")
    @GenerateNodeFactory
    abstract static class ResetNode extends PythonUnaryBuiltinNode {

        /** Longest output: 4 bytes (b'\x0F\x1F(B') with ISO 2022 */
        private static final MultibyteEncodeBuffer SINK = new MultibyteEncodeBuffer(4);

        // _multibytecodec_MultibyteIncrementalEncoder_reset_impl
        @Specialization
        static Object reset(MultibyteIncrementalEncoderObject self) {
            if (self.codec.canEncreset()) {
                self.codec.encreset(self.state, SINK);
                SINK.rewindOutbuf();
            }
            self.pending = null;
            return PNone.NONE;
        }
    }
}
