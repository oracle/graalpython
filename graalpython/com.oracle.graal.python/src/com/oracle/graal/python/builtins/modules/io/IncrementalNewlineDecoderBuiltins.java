/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.builtins.modules.CodecsModuleBuiltins.STRICT;
import static com.oracle.graal.python.builtins.modules.io.IONodes.DECODE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.GETSTATE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.NEWLINES;
import static com.oracle.graal.python.builtins.modules.io.IONodes.RESET;
import static com.oracle.graal.python.builtins.modules.io.IONodes.SETSTATE;
import static com.oracle.graal.python.nodes.ErrorMessages.ILLEGAL_STATE_ARGUMENT;
import static com.oracle.graal.python.nodes.ErrorMessages.STATE_ARGUMENT_MUST_BE_A_TUPLE;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INIT__;

import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyIndexCheckNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

@CoreFunctions(extendClasses = PIncrementalNewlineDecoder)
public final class IncrementalNewlineDecoderBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return IncrementalNewlineDecoderBuiltinsFactory.getFactories();
    }

    public static final int SEEN_CR = 1;
    public static final int SEEN_LF = 2;
    public static final int SEEN_CRLF = 4;
    public static final int SEEN_ALL = (SEEN_CR | SEEN_LF | SEEN_CRLF);

    // BufferedReader(raw[, buffer_size=DEFAULT_BUFFER_SIZE])
    @Builtin(name = __INIT__, minNumOfPositionalArgs = 2, parameterNames = {"$self", "decoder", "translate", "errors"})
    @ArgumentClinic(name = "translate", conversion = ArgumentClinic.ClinicConversion.Boolean)
    @ArgumentClinic(name = "errors", conversion = ArgumentClinic.ClinicConversion.String, defaultValue = "\"strict\"", useDefaultForNone = true)
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonQuaternaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return IncrementalNewlineDecoderBuiltinsClinicProviders.InitNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static PNone doInit(PNLDecoder self, Object decoder, boolean translate, String errors) {
            self.setDecoder(decoder);
            self.setErrors(errors);
            self.setTranslate(translate);
            self.setSeenNewline(0);
            self.setPendingCR(false);
            return PNone.NONE;
        }

        public static void internalInit(PNLDecoder self, Object decoder, boolean translate) {
            doInit(self, decoder, translate, STRICT);
        }
    }

    @Builtin(name = DECODE, minNumOfPositionalArgs = 1, parameterNames = {"$self", "input", "final"})
    @ArgumentClinic(name = "final", conversion = ArgumentClinic.ClinicConversion.Boolean, defaultValue = "false", useDefaultForNone = true)
    @GenerateNodeFactory
    abstract static class DecodeNode extends PythonTernaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return IncrementalNewlineDecoderBuiltinsClinicProviders.DecodeNodeClinicProviderGen.INSTANCE;
        }

        static String noDecoder(PNLDecoder self, String input, boolean isFinal) {
            String output = input;
            int outputLen = PString.length(output);
            if (self.isPendingCR() && (isFinal || outputLen > 0)) {
                /* Prefix output with CR */
                output = PString.cat('\r', output); /* output remains ready */
                self.setPendingCR(false);
                outputLen++;
            }

            /*
             * retain last \r even when not translating data: then readline() is sure to get \r\n in
             * one pass
             */
            if (!isFinal) {
                if (outputLen > 0 && PString.charAt(output, outputLen - 1) == '\r') {
                    output = PString.substring(output, 0, outputLen - 1);
                    self.setPendingCR(true);
                }
            }

            /*
             * Record which newlines are read and do newline translation if desired, all in one
             * pass.
             */
            int len = PString.length(output);
            int seenNewline = self.getSeenNewline();
            boolean onlyLF = false;

            if (len == 0) {
                return output;
            }

            /*
             * If, up to now, newlines are consistently \n, do a quick check for the \r *byte* with
             * the libc's optimized memchr.
             */
            if (seenNewline == SEEN_LF || seenNewline == 0) {
                onlyLF = PString.indexOf(output, "\r", 0) == -1;
            }

            if (onlyLF) {
                /*
                 * If not already seen, quick scan for a possible "\n" character. (there's nothing
                 * else to be done, even when in translation mode)
                 */
                if (seenNewline == 0 && PString.indexOf(output, "\n", 0) != -1) {
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

                char[] inString = PString.toCharArray(output);
                int i = 0;
                while (i < len && seenNewline != SEEN_ALL) {
                    while (i < len && inString[i] > '\n') {
                        i++;
                    }
                    char c = i < len ? inString[i++] : '\0';
                    if (c == '\n') {
                        seenNewline |= SEEN_LF;
                    } else if (c == '\r') {
                        if (inString[i] == '\n') {
                            seenNewline |= SEEN_CRLF;
                            i++;
                        } else {
                            seenNewline |= SEEN_CR;
                        }
                    }
                }
            } else {
                char[] inString = PString.toCharArray(output);
                char[] translated = new char[len];
                int in = 0, out = 0;
                while (true) {
                    char c = '\0';
                    while (in < len && (c = inString[in++]) > '\r') {
                        translated[out++] = c;
                    }
                    if (c == '\n') {
                        translated[out++] = c;
                        seenNewline |= SEEN_LF;
                        continue;
                    }
                    if (c == '\r') {
                        if (in < len && inString[in] == '\n') {
                            in++;
                            seenNewline |= SEEN_CRLF;
                        } else {
                            seenNewline |= SEEN_CR;
                        }
                        translated[out++] = '\n';
                        continue;
                    }
                    if (in >= len) {
                        break;
                    }
                    translated[out++] = c;
                }
                output = PythonUtils.newString(PythonUtils.arrayCopyOf(translated, out));
            }
            self.setSeenNewline(self.getSeenNewline() | seenNewline);

            return output;
        }

        @Specialization(guards = "!self.hasDecoder()")
        static String noDecoder(PNLDecoder self, Object input, boolean isFinal,
                        @Cached CastToJavaStringNode toString) {
            return noDecoder(self, toString.execute(input), isFinal);
        }

        @Specialization(guards = "self.hasDecoder()")
        static String withDecoder(VirtualFrame frame, PNLDecoder self, Object input, boolean isFinal,
                        @Cached CastToJavaStringNode toString,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            Object res = callMethod.execute(frame, self.getDecoder(), DECODE, input, isFinal);
            return noDecoder(self, toString.execute(res), isFinal);
        }
    }

    @Builtin(name = GETSTATE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class GetStateNode extends PythonUnaryBuiltinNode {

        @Specialization(guards = "!self.hasDecoder()")
        Object noDecoder(PNLDecoder self) {
            PBytes buffer = factory().createBytes(PythonUtils.EMPTY_BYTE_ARRAY);
            int flag = self.isPendingCR() ? 1 : 0;
            return factory().createTuple(new Object[]{buffer, flag});
        }

        @Specialization(guards = "self.hasDecoder()")
        Object withDecoder(VirtualFrame frame, PNLDecoder self,
                        @Cached SequenceNodes.GetObjectArrayNode getObjectArrayNode,
                        @Cached PyIndexCheckNode indexCheckNode,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            Object state = callMethod.execute(frame, self.getDecoder(), GETSTATE);
            if (!(state instanceof PTuple)) {
                throw raise(TypeError, ILLEGAL_STATE_ARGUMENT);
            }
            Object[] objects = getObjectArrayNode.execute(state);
            if (objects.length != 2 || !indexCheckNode.execute(objects[1])) {
                throw raise(TypeError, ILLEGAL_STATE_ARGUMENT);
            }
            int flag = asSizeNode.executeExact(frame, objects[1]);
            flag <<= 1;
            if (self.isPendingCR()) {
                flag |= 1;
            }
            return factory().createTuple(new Object[]{objects[0], flag});
        }
    }

    @Builtin(name = SETSTATE, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class SetStateNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = "!self.hasDecoder()")
        Object noDecoder(VirtualFrame frame, PNLDecoder self, PTuple state,
                        @Shared("o") @Cached SequenceNodes.GetObjectArrayNode getObjectArrayNode,
                        @Shared("i") @Cached PyIndexCheckNode indexCheckNode,
                        @Shared("s") @Cached PyNumberAsSizeNode asSizeNode) {
            Object[] objects = getObjectArrayNode.execute(state);
            if (objects.length != 2 || !indexCheckNode.execute(objects[1])) {
                throw raise(TypeError, ILLEGAL_STATE_ARGUMENT);
            }
            int flag = asSizeNode.executeExact(frame, objects[1]);
            self.setPendingCR((flag & 1) != 0);
            return PNone.NONE;
        }

        @Specialization(guards = "self.hasDecoder()")
        Object withDecoder(VirtualFrame frame, PNLDecoder self, PTuple state,
                        @Shared("o") @Cached SequenceNodes.GetObjectArrayNode getObjectArrayNode,
                        @Shared("i") @Cached PyIndexCheckNode indexCheckNode,
                        @Shared("s") @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            Object[] objects = getObjectArrayNode.execute(state);
            if (objects.length != 2 || !indexCheckNode.execute(objects[1])) {
                throw raise(TypeError, ILLEGAL_STATE_ARGUMENT);
            }
            int flag = asSizeNode.executeExact(frame, objects[1]);
            self.setPendingCR((flag & 1) != 0);
            flag >>= 1;
            PTuple tuple = factory().createTuple(new Object[]{objects[0], flag});
            return callMethod.execute(frame, self.getDecoder(), SETSTATE, tuple);
        }

        @Fallback
        Object err(@SuppressWarnings("unused") Object self, @SuppressWarnings("unused") Object state) {
            throw raise(TypeError, STATE_ARGUMENT_MUST_BE_A_TUPLE);
        }
    }

    @Builtin(name = RESET, minNumOfPositionalArgs = 1)
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
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            noDecoder(self);
            return callMethod.execute(frame, self.getDecoder(), RESET);
        }
    }

    @Builtin(name = NEWLINES, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class NewlineNode extends PythonBuiltinNode {
        @Specialization
        Object newline(PNLDecoder self) {
            switch (self.getSeenNewline()) {
                case SEEN_CR:
                    return "\r";
                case SEEN_LF:
                    return "\n";
                case SEEN_CRLF:
                    return "\r\n";
                case SEEN_CR | SEEN_LF:
                    return factory().createTuple(new Object[]{"\r", "\n"});
                case SEEN_CR | SEEN_CRLF:
                    return factory().createTuple(new Object[]{"\r", "\r\n"});
                case SEEN_LF | SEEN_CRLF:
                    return factory().createTuple(new Object[]{"\n", "\r\n"});
                case SEEN_CR | SEEN_LF | SEEN_CRLF:
                    return factory().createTuple(new Object[]{"\r", "\n", "\r\n"});
                default:
                    return PNone.NONE;
            }
        }
    }
}
