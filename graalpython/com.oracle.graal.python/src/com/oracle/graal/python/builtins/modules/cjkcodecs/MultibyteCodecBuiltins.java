/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.MultibyteCodec;
import static com.oracle.graal.python.builtins.modules.cjkcodecs.MultibyteCodecUtil.MBENC_RESET;
import static com.oracle.graal.python.builtins.modules.cjkcodecs.MultibyteCodecUtil.encodeEmptyInput;
import static com.oracle.graal.python.builtins.modules.cjkcodecs.MultibyteCodecUtil.internalErrorCallback;
import static com.oracle.graal.python.builtins.modules.cjkcodecs.MultibytecodecModuleBuiltins.MBENC_FLUSH;
import static com.oracle.graal.python.nodes.ErrorMessages.COULDN_T_CONVERT_THE_OBJECT_TO_UNICODE;
import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_STRING;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.lib.PyObjectStrAsObjectNode;
import com.oracle.graal.python.lib.PyUnicodeCheckNode;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = MultibyteCodec)
public final class MultibyteCodecBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return MultibyteCodecBuiltinsFactory.getFactories();
    }

    @Builtin(name = "encode", minNumOfPositionalArgs = 1, parameterNames = {"$self", "errors"}, doc = "encode($self, /, input, errors=None)\n" + //
                    "--\n\nReturn an encoded string version of `input\'.\n" + //
                    "\n\'errors\' may be given to set a different error handling scheme. Default is\n" + //
                    "\'strict\' meaning that encoding errors raise a UnicodeEncodeError. Other possible\n" + //
                    "values are \'ignore\', \'replace\' and \'xmlcharrefreplace\' as well as any other name\n" + //
                    "registered with codecs.register_error that can handle UnicodeEncodeErrors.")
    @ArgumentClinic(name = "errors", conversion = ArgumentClinic.ClinicConversion.TString, defaultValue = "T_STRICT", useDefaultForNone = true)
    @GenerateNodeFactory
    abstract static class EncodeNode extends PythonTernaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return MultibyteCodecBuiltinsClinicProviders.EncodeNodeClinicProviderGen.INSTANCE;
        }

        /*
         * [clinic input] _multibytecodec.MultibyteCodec.encode
         *
         * input: object errors: str(accept={str, NoneType}) = None
         *
         * Return an encoded string version of `input'.
         *
         * 'errors' may be given to set a different error handling scheme. Default is 'strict'
         * meaning that encoding errors raise a UnicodeEncodeError. Other possible values are
         * 'ignore', 'replace' and 'xmlcharrefreplace' as well as any other name registered with
         * codecs.register_error that can handle UnicodeEncodeErrors. [clinic start generated code]
         */

        // _multibytecodec_MultibyteCodec_encode_impl
        @Specialization
        static Object ts(VirtualFrame frame, MultibyteCodecObject self, TruffleString ucvt, TruffleString errors,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached MultibyteCodecUtil.EncodeNode encodeNode,
                        @Shared @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Shared @Cached TruffleString.EqualNode isEqual,
                        @Shared @Cached PythonObjectFactory factory) {
            TruffleString errorcb = internalErrorCallback(errors, isEqual);
            MultibyteCodecState state = self.codec.encinit(errorcb);
            int datalen = codePointLengthNode.execute(ucvt, TS_ENCODING);
            PBytes r = encodeEmptyInput(datalen, MBENC_FLUSH | MBENC_RESET, factory);
            if (r == null) {
                MultibyteEncodeBuffer buf = new MultibyteEncodeBuffer(ucvt);
                r = encodeNode.execute(frame, inliningTarget, self.codec, state, buf, errorcb, MBENC_FLUSH | MBENC_RESET, factory);
            }
            return factory.createTuple(new Object[]{r, datalen});
        }

        @Specialization(guards = "!isTruffleString(input)")
        static Object notTS(VirtualFrame frame, MultibyteCodecObject self, Object input, TruffleString errors,
                        @Bind("this") Node inliningTarget,
                        @Cached PyUnicodeCheckNode unicodeCheck,
                        @Cached PyObjectStrAsObjectNode strNode,
                        @Cached CastToTruffleStringNode toTruffleStringNode,
                        @Exclusive @Cached MultibyteCodecUtil.EncodeNode encodeNode,
                        @Shared @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Shared @Cached TruffleString.EqualNode isEqual,
                        @Shared @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            Object ucvt = input;
            if (!unicodeCheck.execute(inliningTarget, input)) {
                ucvt = strNode.execute(frame, inliningTarget, input);
                if (!unicodeCheck.execute(inliningTarget, ucvt)) {
                    throw raiseNode.get(inliningTarget).raise(TypeError, COULDN_T_CONVERT_THE_OBJECT_TO_UNICODE);
                }
            }

            TruffleString str = toTruffleStringNode.execute(inliningTarget, ucvt);
            return ts(frame, self, str, errors, inliningTarget, encodeNode, codePointLengthNode, isEqual, factory);
        }

    }

    @Builtin(name = "decode", minNumOfPositionalArgs = 1, parameterNames = {"$self", "input", "errors"}, //
                    doc = "decode($self, /, input, errors=None)\n" + //
                                    "--\n\nDecodes \'input\'.\n\n" + //
                                    "\'errors\' may be given to set a different error handling scheme. Default is\n" + //
                                    "\'strict\' meaning that encoding errors raise a UnicodeDecodeError. Other possible\n" + //
                                    "values are \'ignore\' and \'replace\' as well as any other name registered with\n" + //
                                    "codecs.register_error that is able to handle UnicodeDecodeErrors.\"")
    @ArgumentClinic(name = "input", conversion = ArgumentClinic.ClinicConversion.ReadableBuffer)
    @ArgumentClinic(name = "errors", conversion = ArgumentClinic.ClinicConversion.TString, defaultValue = "T_STRICT", useDefaultForNone = true)
    @GenerateNodeFactory
    abstract static class DecodeNode extends PythonTernaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return MultibyteCodecBuiltinsClinicProviders.DecodeNodeClinicProviderGen.INSTANCE;
        }

        /**
         * _multibytecodec.MultibyteCodec.decode input: Py_buffer errors: str(accept={str,
         * NoneType}) = None Decodes 'input'. 'errors' may be given to set a different error
         * handling scheme. Default is 'strict' meaning that encoding errors raise a
         * UnicodeDecodeError. Other possible values are 'ignore' and 'replace' as well as any other
         * name registered with codecs.register_error that is able to handle UnicodeDecodeErrors."
         */

        // _multibytecodec_MultibyteCodec_decode_impl
        @Specialization
        Object decode(VirtualFrame frame, MultibyteCodecObject self, byte[] input, TruffleString errors,
                        @Cached MultibyteCodecUtil.DecodeErrorNode decodeErrorNode,
                        @Cached TruffleString.EqualNode isEqual,
                        @Cached PythonObjectFactory factory) {
            int datalen = input.length;

            TruffleString errorcb = internalErrorCallback(errors, isEqual);

            if (datalen == 0) {
                return factory.createTuple(new Object[]{T_EMPTY_STRING, 0});
            }
            MultibyteDecodeBuffer buf = new MultibyteDecodeBuffer(input);
            MultibyteCodecState state = self.codec.decinit(errorcb);
            while (!buf.isFull()) {
                int r = self.codec.decode(state, buf, this);
                if (r == 0) {
                    break;
                } else {
                    decodeErrorNode.execute(frame, self.codec, buf, errorcb, r);
                }
            }

            return factory.createTuple(new Object[]{buf.toTString(), datalen});
        }
    }

}
