/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules;

import static com.oracle.graal.python.nodes.ErrorMessages.EXPECTED_BYTESLIKE_GOT_P;
import static com.oracle.graal.python.nodes.ErrorMessages.RETURNED_NONBYTES;
import static com.oracle.graal.python.nodes.ErrorMessages.RETURNED_NON_STRING;
import static com.oracle.graal.python.nodes.ErrorMessages.UNKNOWN_ENCODING;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ArgumentClinic.ClinicConversion;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.TokenizeModuleBuiltinsClinicProviders.TokenizerIterNodeClinicProviderGen;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.builtins.objects.tokenize.PTokenizerIter;
import com.oracle.graal.python.lib.PyBytesCheckNode;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.pegparser.tokenizer.Tokenizer;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.Supplier;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringIterator;

@CoreFunctions(defineModule = "_tokenize", isEager = true)
public final class TokenizeModuleBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return TokenizeModuleBuiltinsFactory.getFactories();
    }

    @Builtin(name = "TokenizerIter", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"$cls", "readline"}, keywordOnlyNames = {"extra_tokens",
                    "encoding"}, constructsClass = PythonBuiltinClassType.PTokenizerIter)
    @ArgumentClinic(name = "extra_tokens", conversion = ClinicConversion.Boolean, defaultValue = "true")
    @ArgumentClinic(name = "encoding", conversion = ClinicConversion.TString, useDefaultForNone = true, defaultValue = "PNone.NONE")
    @GenerateNodeFactory
    abstract static class TokenizerIterNode extends PythonQuaternaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return TokenizerIterNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static PTokenizerIter tokenizerIterStr(Object cls, Object readline, boolean extraTokens, @SuppressWarnings("unused") PNone encoding,
                        @Shared @Cached PythonObjectFactory factory) {
            Supplier<int[]> inputSupplier = () -> {
                Object o;
                try {
                    o = CallNode.executeUncached(readline);
                } catch (PException e) {
                    e.expectUncached(PythonBuiltinClassType.StopIteration);
                    return null;
                }
                TruffleString line;
                try {
                    line = CastToTruffleStringNode.executeUncached(o);
                } catch (CannotCastException e) {
                    throw PRaiseNode.raiseUncached(null, PythonBuiltinClassType.TypeError, RETURNED_NON_STRING, "readline()", o);
                }
                return getCodePoints(line);
            };
            return factory.createTokenizerIter(cls, inputSupplier, extraTokens);
        }

        @Specialization
        static PTokenizerIter tokenizerIterBytes(Object cls, Object readline, boolean extraTokens, TruffleString encoding,
                        @Shared @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode raiseNode) {
            Charset charset;
            try {
                charset = getCharset(Tokenizer.getNormalName(encoding.toJavaStringUncached()));
            } catch (Exception e) {
                throw raiseNode.raise(PythonBuiltinClassType.LookupError, UNKNOWN_ENCODING, encoding);
            }

            Supplier<int[]> inputSupplier = () -> {
                Object o;
                try {
                    o = CallNode.executeUncached(readline);
                } catch (PException e) {
                    e.expectUncached(PythonBuiltinClassType.StopIteration);
                    return null;
                }
                if (!PyBytesCheckNode.executeUncached(o)) {
                    throw PRaiseNode.raiseUncached(null, PythonBuiltinClassType.TypeError, RETURNED_NONBYTES, "readline()", o);
                }

                Object buffer;
                try {
                    buffer = PythonBufferAcquireLibrary.getUncached().acquireReadonly(o);
                } catch (PException e) {
                    throw PRaiseNode.raiseUncached(null, TypeError, EXPECTED_BYTESLIKE_GOT_P, o);
                }
                PythonBufferAccessLibrary bufferLib = PythonBufferAccessLibrary.getUncached();
                byte[] bytes;
                try {
                    bytes = bufferLib.getInternalOrCopiedByteArray(buffer);
                } finally {
                    bufferLib.release(buffer);
                }
                String line = charset.decode(ByteBuffer.wrap(bytes)).toString();
                return getCodePoints(TruffleString.fromJavaStringUncached(line, TS_ENCODING));
            };
            return factory.createTokenizerIter(cls, inputSupplier, extraTokens);
        }

        @TruffleBoundary
        private static Charset getCharset(String s) {
            return Charset.forName(s);
        }

        private static int[] getCodePoints(TruffleString ts) {
            int len = ts.codePointLengthUncached(TS_ENCODING);
            if (len == 0) {
                return null;
            }
            int[] res = new int[len];
            int i = 0;
            TruffleStringIterator it = ts.createCodePointIteratorUncached(TS_ENCODING);
            while (it.hasNext()) {
                res[i++] = it.nextUncached();
            }
            return res;
        }
    }
}
