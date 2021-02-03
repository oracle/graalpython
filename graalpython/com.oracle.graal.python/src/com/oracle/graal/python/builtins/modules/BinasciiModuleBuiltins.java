/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.BinasciiError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.SystemError;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.zip.CRC32;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;

@CoreFunctions(defineModule = "binascii")
public class BinasciiModuleBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return BinasciiModuleBuiltinsFactory.getFactories();
    }

    @Builtin(name = "a2b_base64", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class A2bBase64Node extends PythonUnaryBuiltinNode {
        @Specialization
        PBytes doString(String data) {
            return factory().createBytes(b64decode(data));
        }

        @Specialization(guards = "bufferLib.isBuffer(data)", limit = "3")
        PBytes doBuffer(Object data,
                        @CachedLibrary("data") PythonObjectLibrary bufferLib) {
            try {
                return factory().createBytes(b64decode(bufferLib.getBufferBytes(data)));
            } catch (UnsupportedMessageException e) {
                throw raise(SystemError, ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC);
            }
        }

        @TruffleBoundary
        private byte[] b64decode(String data) {
            return b64decode(data.getBytes(StandardCharsets.US_ASCII));
        }

        @TruffleBoundary
        private byte[] b64decode(byte[] data) {
            try {
                // Using MIME decoder because that one skips over anything that is not the alphabet,
                // just like CPython does
                return Base64.getMimeDecoder().decode(data);
            } catch (IllegalArgumentException e) {
                throw raise(BinasciiError, e);
            }
        }
    }

    @Builtin(name = "a2b_hex", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class A2bHexNode extends PythonUnaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        PBytes a2b(String data) {
            int length = data.length();
            if (length % 2 != 0) {
                throw raise(BinasciiError, ErrorMessages.ODD_LENGTH_STRING);
            }
            byte[] output = new byte[length / 2];
            for (int i = 0; i < length / 2; i++) {
                try {
                    output[i] = (byte) (digitValue(data.charAt(i * 2)) * 16 + digitValue(data.charAt(i * 2 + 1)));
                } catch (NumberFormatException e) {
                    throw raise(BinasciiError, ErrorMessages.NON_HEX_DIGIT_FOUND);
                }
            }
            return factory().createBytes(output);
        }

        @Specialization(guards = "bufferLib.isBuffer(buffer)", limit = "2")
        PBytes a2b(Object buffer,
                        @CachedLibrary("buffer") PythonObjectLibrary bufferLib) {
            try {
                return a2b(bufferLib.getBufferBytes(buffer));
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }

        @TruffleBoundary
        private PBytes a2b(byte[] bytes) {
            int length = bytes.length;
            if (length % 2 != 0) {
                throw raise(BinasciiError, ErrorMessages.ODD_LENGTH_STRING);
            }
            byte[] output = new byte[length / 2];
            for (int i = 0; i < length / 2; i++) {
                output[i] = (byte) (digitValue((char) bytes[i * 2]) * 16 + digitValue((char) bytes[i * 2 + 1]));
            }
            return factory().createBytes(output);
        }

        private int digitValue(char b) {
            if (b >= '0' && b <= '9') {
                return b - '0';
            } else if (b >= 'a' && b <= 'f') {
                return b - 'a' + 10;
            } else if (b >= 'A' && b <= 'F') {
                return b - 'A' + 10;
            } else {
                throw raise(BinasciiError, ErrorMessages.NON_HEX_DIGIT_FOUND);
            }
        }

    }

    @Builtin(name = "b2a_base64", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"data"}, keywordOnlyNames = {"newline"})
    @ArgumentClinic(name = "newline", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "1", useDefaultForNone = true)
    @GenerateNodeFactory
    abstract static class B2aBase64Node extends PythonClinicBuiltinNode {
        @TruffleBoundary
        private byte[] b2a(byte[] data, int newline) {
            byte[] encoded;
            try {
                encoded = Base64.getEncoder().encode(data);
            } catch (IllegalArgumentException e) {
                throw raise(BinasciiError, e);
            }
            if (newline != 0) {
                encoded = Arrays.copyOf(encoded, encoded.length + 1);
                encoded[encoded.length - 1] = '\n';
            }
            return encoded;
        }

        @Specialization(guards = "bufferLib.isBuffer(data)", limit = "3")
        PBytes b2aBuffer(Object data, int newline,
                        @CachedLibrary("data") PythonObjectLibrary bufferLib) {
            try {
                return factory().createBytes(b2a(bufferLib.getBufferBytes(data), newline));
            } catch (UnsupportedMessageException e) {
                throw raise(SystemError, ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC);
            }
        }

        @Fallback
        PBytes b2sGeneral(Object data, @SuppressWarnings("unused") Object newline) {
            throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.BYTESLIKE_OBJ_REQUIRED, data);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return BinasciiModuleBuiltinsClinicProviders.B2aBase64NodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "b2a_hex", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class B2aHexNode extends PythonUnaryBuiltinNode {

        @CompilationFinal(dimensions = 1) private static final byte[] HEX_DIGITS = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

        @Specialization(guards = "bufferLib.isBuffer(buffer)", limit = "2")
        PBytes b2a(Object buffer,
                        @CachedLibrary("buffer") PythonObjectLibrary bufferLib) {
            try {
                return b2a(bufferLib.getBufferBytes(buffer));
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }

        @Fallback
        PBytes b2a(Object data) {
            throw raise(TypeError, ErrorMessages.BYTESLIKE_OBJ_REQUIRED, data);
        }

        @TruffleBoundary
        private PBytes b2a(byte[] bytes) {
            byte[] output = new byte[bytes.length * 2];
            for (int i = 0; i < bytes.length; i++) {
                int v = bytes[i] & 0xff;
                output[i * 2] = HEX_DIGITS[v >> 4];
                output[i * 2 + 1] = HEX_DIGITS[v & 0xf];
            }
            return factory().createBytes(output);
        }
    }

    @Builtin(name = "crc32", minNumOfPositionalArgs = 1, parameterNames = {"data", "crc"})
    @GenerateNodeFactory
    abstract static class Crc32Node extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(crc)")
        static long b2a(PBytes data, @SuppressWarnings("unused") PNone crc,
                        @Cached SequenceStorageNodes.ToByteArrayNode toByteArray) {
            return getCrcValue(toByteArray.execute(data.getSequenceStorage()));
        }

        @TruffleBoundary
        private static long getCrcValue(byte[] bytes) {
            CRC32 crc32 = new CRC32();
            crc32.update(bytes);
            return crc32.getValue();
        }
    }

    @Builtin(name = "hexlify", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class HexlifyNode extends B2aHexNode {
    }

    @Builtin(name = "unhexlify", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class UnhexlifyNode extends A2bHexNode {
    }
}
