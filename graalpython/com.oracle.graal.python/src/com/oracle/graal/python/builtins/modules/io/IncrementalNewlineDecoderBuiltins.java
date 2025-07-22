/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.io;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PIncrementalNewlineDecoder;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_DECODE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_GETSTATE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_NEWLINES;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_RESET;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_SETSTATE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_DECODE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_GETSTATE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_RESET;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_SETSTATE;
import static com.oracle.graal.python.nodes.ErrorMessages.ILLEGAL_STATE_ARGUMENT;
import static com.oracle.graal.python.nodes.ErrorMessages.STATE_ARGUMENT_MUST_BE_A_TUPLE;
import static com.oracle.graal.python.nodes.StringLiterals.T_CR;
import static com.oracle.graal.python.nodes.StringLiterals.T_CRLF;
import static com.oracle.graal.python.nodes.StringLiterals.T_NEWLINE;
import static com.oracle.graal.python.nodes.StringLiterals.T_STRICT;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.lib.PyIndexCheckNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;

@CoreFunctions(extendClasses = PIncrementalNewlineDecoder)
public final class IncrementalNewlineDecoderBuiltins extends PythonBuiltins {

    public static final TpSlots SLOTS = IncrementalNewlineDecoderBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return IncrementalNewlineDecoderBuiltinsFactory.getFactories();
    }

    public static final int SEEN_CR = 1;
    public static final int SEEN_LF = 2;
    public static final int SEEN_CRLF = 4;
    public static final int SEEN_ALL = (SEEN_CR | SEEN_LF | SEEN_CRLF);

    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = "IncrementalNewlineDecoder", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class IncrementalNewlineDecoderNode extends PythonBuiltinNode {
        @Specialization
        static PNLDecoder doNew(Object cls, @SuppressWarnings("unused") Object arg,
                        @Bind PythonLanguage language,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape) {
            // data filled in subsequent __init__ call - see
            // IncrementalNewlineDecoderBuiltins.InitNode
            return PFactory.createNLDecoder(language, cls, getInstanceShape.execute(cls));
        }
    }

    // BufferedReader(raw[, buffer_size=DEFAULT_BUFFER_SIZE])
    @Slot(value = SlotKind.tp_init, isComplex = true)
    @SlotSignature(name = "IncrementalNewlineDecoder", minNumOfPositionalArgs = 2, parameterNames = {"$self", "decoder", "translate", "errors"})
    @ArgumentClinic(name = "translate", conversion = ArgumentClinic.ClinicConversion.Boolean)
    @ArgumentClinic(name = "errors", conversion = ArgumentClinic.ClinicConversion.TString, defaultValue = "T_STRICT", useDefaultForNone = true)
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonQuaternaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return IncrementalNewlineDecoderBuiltinsClinicProviders.InitNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static PNone doInit(PNLDecoder self, Object decoder, boolean translate, TruffleString errors) {
            self.setDecoder(decoder);
            self.setErrors(errors);
            self.setTranslate(translate);
            self.setSeenNewline(0);
            self.setPendingCR(false);
            return PNone.NONE;
        }

        public static void internalInit(PNLDecoder self, Object decoder, boolean translate) {
            doInit(self, decoder, translate, T_STRICT);
        }
    }

    @Builtin(name = J_DECODE, minNumOfPositionalArgs = 1, parameterNames = {"$self", "input", "final"})
    @ArgumentClinic(name = "final", conversion = ArgumentClinic.ClinicConversion.Boolean, defaultValue = "false", useDefaultForNone = true)
    @GenerateNodeFactory
    abstract static class DecodeNode extends PythonTernaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return IncrementalNewlineDecoderBuiltinsClinicProviders.DecodeNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static TruffleString noDecoder(VirtualFrame frame, PNLDecoder self, Object inputIn, boolean isFinal,
                        @Bind Node inliningTarget,
                        @Cached InlinedBranchProfile hasDecoderProfile,
                        @Cached InlinedConditionProfile len0Profile,
                        @Cached CastToTruffleStringNode toString,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.CodePointAtIndexNode codePointAtIndexNode,
                        @Cached TruffleString.IndexOfCodePointNode indexOfCodePointNode,
                        @Cached TruffleString.SubstringNode substringNode,
                        @Cached TruffleString.ConcatNode concatNode,
                        @Cached TruffleStringBuilder.AppendCodePointNode appendCodePointNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            Object input = inputIn;
            if (self.hasDecoder()) {
                hasDecoderProfile.enter(inliningTarget);
                input = callMethod.execute(frame, inliningTarget, self.getDecoder(), T_DECODE, input, isFinal);
            }

            TruffleString output = toString.execute(inliningTarget, input);
            int outputLen = codePointLengthNode.execute(output, TS_ENCODING);
            if (self.isPendingCR() && (isFinal || outputLen > 0)) {
                /* Prefix output with CR */
                output = concatNode.execute(T_CR, output, TS_ENCODING, false);
                self.setPendingCR(false);
                outputLen++;
            }

            /*
             * retain last \r even when not translating data: then readline() is sure to get \r\n in
             * one pass
             */
            if (!isFinal) {
                if (outputLen > 0 && codePointAtIndexNode.execute(output, outputLen - 1, TS_ENCODING) == '\r') {
                    output = substringNode.execute(output, 0, outputLen - 1, TS_ENCODING, false);
                    self.setPendingCR(true);
                }
            }

            /*
             * Record which newlines are read and do newline translation if desired, all in one
             * pass.
             */
            int len = codePointLengthNode.execute(output, TS_ENCODING);
            int seenNewline = self.getSeenNewline();
            boolean onlyLF = false;

            if (len0Profile.profile(inliningTarget, len == 0)) {
                return output;
            }

            /*
             * If, up to now, newlines are consistently \n, do a quick check for the \r *byte* with
             * the libc's optimized memchr.
             */
            if (seenNewline == SEEN_LF || seenNewline == 0) {
                onlyLF = indexOfCodePointNode.execute(output, '\r', 0, len, TS_ENCODING) < 0;
            }

            if (onlyLF) {
                /*
                 * If not already seen, quick scan for a possible "\n" character. (there's nothing
                 * else to be done, even when in translation mode)
                 */
                if (seenNewline == 0 && indexOfCodePointNode.execute(output, '\n', 0, len, TS_ENCODING) >= 0) {
                    seenNewline |= SEEN_LF;
                }
                /*
                 * Finished: we have scanned for newlines, and none of them need translating
                 */
            } else if (!self.isTranslate()) {
                /* We have already seen all newline types, no need to scan again */
                if (seenNewline == SEEN_ALL) {
                    return output;
                }

                int i = 0;
                while (i < len && seenNewline != SEEN_ALL) {
                    while (i < len && codePointAtIndexNode.execute(output, i, TS_ENCODING) > '\r') {
                        i++;
                    }
                    int c = i < len ? codePointAtIndexNode.execute(output, i++, TS_ENCODING) : '\0';
                    if (c == '\n') {
                        seenNewline |= SEEN_LF;
                    } else if (c == '\r') {
                        assert i < len || isFinal;
                        if (i < len && codePointAtIndexNode.execute(output, i, TS_ENCODING) == '\n') {
                            seenNewline |= SEEN_CRLF;
                            i++;
                        } else {
                            seenNewline |= SEEN_CR;
                        }
                    }
                }
            } else {
                TruffleStringBuilder sb = TruffleStringBuilder.create(TS_ENCODING, output.byteLength(TS_ENCODING));
                int in = 0;
                while (true) {
                    int c = '\0';
                    while (in < len && (c = codePointAtIndexNode.execute(output, in++, TS_ENCODING)) > '\r') {
                        appendCodePointNode.execute(sb, c, 1, true);
                    }
                    if (c == '\n') {
                        appendCodePointNode.execute(sb, c, 1, true);
                        seenNewline |= SEEN_LF;
                        continue;
                    }
                    if (c == '\r') {
                        if (in < len && codePointAtIndexNode.execute(output, in, TS_ENCODING) == '\n') {
                            in++;
                            seenNewline |= SEEN_CRLF;
                        } else {
                            seenNewline |= SEEN_CR;
                        }
                        appendCodePointNode.execute(sb, '\n', 1, true);
                        continue;
                    }
                    if (in >= len) {
                        break;
                    }
                    appendCodePointNode.execute(sb, c, 1, true);
                }
                output = toStringNode.execute(sb);
            }
            self.setSeenNewline(self.getSeenNewline() | seenNewline);

            return output;
        }
    }

    @Builtin(name = J_GETSTATE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class GetStateNode extends PythonUnaryBuiltinNode {

        @Specialization(guards = "!self.hasDecoder()")
        static Object noDecoder(PNLDecoder self,
                        @Bind PythonLanguage language) {
            PBytes buffer = PFactory.createEmptyBytes(language);
            int flag = self.isPendingCR() ? 1 : 0;
            return PFactory.createTuple(language, new Object[]{buffer, flag});
        }

        @Specialization(guards = "self.hasDecoder()")
        static Object withDecoder(VirtualFrame frame, PNLDecoder self,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached SequenceNodes.GetObjectArrayNode getObjectArrayNode,
                        @Cached PyIndexCheckNode indexCheckNode,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached PyObjectCallMethodObjArgs callMethod,
                        @Cached PRaiseNode raiseNode) {
            Object state = callMethod.execute(frame, inliningTarget, self.getDecoder(), T_GETSTATE);
            if (!(state instanceof PTuple)) {
                throw raiseNode.raise(inliningTarget, TypeError, ILLEGAL_STATE_ARGUMENT);
            }
            Object[] objects = getObjectArrayNode.execute(inliningTarget, state);
            if (objects.length != 2 || !indexCheckNode.execute(inliningTarget, objects[1])) {
                throw raiseNode.raise(inliningTarget, TypeError, ILLEGAL_STATE_ARGUMENT);
            }
            int flag = asSizeNode.executeExact(frame, inliningTarget, objects[1]);
            flag <<= 1;
            if (self.isPendingCR()) {
                flag |= 1;
            }
            return PFactory.createTuple(language, new Object[]{objects[0], flag});
        }
    }

    @Builtin(name = J_SETSTATE, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class SetStateNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = "!self.hasDecoder()")
        static Object noDecoder(VirtualFrame frame, PNLDecoder self, PTuple state,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached SequenceNodes.GetObjectArrayNode getObjectArrayNode,
                        @Exclusive @Cached PyIndexCheckNode indexCheckNode,
                        @Exclusive @Cached PyNumberAsSizeNode asSizeNode,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            Object[] objects = getObjectArrayNode.execute(inliningTarget, state);
            if (objects.length != 2 || !indexCheckNode.execute(inliningTarget, objects[1])) {
                throw raiseNode.raise(inliningTarget, TypeError, ILLEGAL_STATE_ARGUMENT);
            }
            int flag = asSizeNode.executeExact(frame, inliningTarget, objects[1]);
            self.setPendingCR((flag & 1) != 0);
            return PNone.NONE;
        }

        @Specialization(guards = "self.hasDecoder()")
        static Object withDecoder(VirtualFrame frame, PNLDecoder self, PTuple state,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Exclusive @Cached SequenceNodes.GetObjectArrayNode getObjectArrayNode,
                        @Exclusive @Cached PyIndexCheckNode indexCheckNode,
                        @Exclusive @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached PyObjectCallMethodObjArgs callMethod,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            Object[] objects = getObjectArrayNode.execute(inliningTarget, state);
            if (objects.length != 2 || !indexCheckNode.execute(inliningTarget, objects[1])) {
                throw raiseNode.raise(inliningTarget, TypeError, ILLEGAL_STATE_ARGUMENT);
            }
            int flag = asSizeNode.executeExact(frame, inliningTarget, objects[1]);
            self.setPendingCR((flag & 1) != 0);
            flag >>= 1;
            PTuple tuple = PFactory.createTuple(language, new Object[]{objects[0], flag});
            return callMethod.execute(frame, inliningTarget, self.getDecoder(), T_SETSTATE, tuple);
        }

        @Fallback
        static Object err(@SuppressWarnings("unused") Object self, @SuppressWarnings("unused") Object state,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, STATE_ARGUMENT_MUST_BE_A_TUPLE);
        }
    }

    @Builtin(name = J_RESET, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ResetNode extends PythonUnaryBuiltinNode {

        @Specialization(guards = "!self.hasDecoder()")
        static Object noDecoder(PNLDecoder self) {
            self.setSeenNewline(0);
            self.setPendingCR(false);
            return PNone.NONE;
        }

        @Specialization(guards = "self.hasDecoder()")
        static Object withDecoder(VirtualFrame frame, PNLDecoder self,
                        @Bind Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            noDecoder(self);
            return callMethod.execute(frame, inliningTarget, self.getDecoder(), T_RESET);
        }
    }

    @Builtin(name = J_NEWLINES, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class NewlineNode extends PythonBuiltinNode {
        @Specialization
        static Object newline(PNLDecoder self,
                        @Bind Node inliningTarget) {
            switch (self.getSeenNewline()) {
                case SEEN_CR:
                    return T_CR;
                case SEEN_LF:
                    return T_NEWLINE;
                case SEEN_CRLF:
                    return T_CRLF;
                case SEEN_CR | SEEN_LF:
                    return PFactory.createTuple(PythonLanguage.get(inliningTarget), new Object[]{T_CR, T_NEWLINE});
                case SEEN_CR | SEEN_CRLF:
                    return PFactory.createTuple(PythonLanguage.get(inliningTarget), new Object[]{T_CR, T_CRLF});
                case SEEN_LF | SEEN_CRLF:
                    return PFactory.createTuple(PythonLanguage.get(inliningTarget), new Object[]{T_NEWLINE, T_CRLF});
                case SEEN_CR | SEEN_LF | SEEN_CRLF:
                    return PFactory.createTuple(PythonLanguage.get(inliningTarget), new Object[]{T_CR, T_NEWLINE, T_CRLF});
                default:
                    return PNone.NONE;
            }
        }
    }
}
