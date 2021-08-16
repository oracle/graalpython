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

import static com.oracle.graal.python.builtins.objects.bytes.BytesUtils.HEXDIGITS;
import static com.oracle.graal.python.builtins.objects.bytes.BytesUtils.digitValue;
import static com.oracle.graal.python.nodes.ErrorMessages.BYTESLIKE_OBJ_REQUIRED;
import static com.oracle.graal.python.nodes.ErrorMessages.ENCODING_ERROR_WITH_CODE;
import static com.oracle.graal.python.nodes.ErrorMessages.INVALID_ESCAPE_AT;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.LookupError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.MemoryError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.UnicodeDecodeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.UnicodeEncodeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.bytes.ByteArrayBuffer;
import com.oracle.graal.python.builtins.objects.bytes.BytesUtils;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetInternalByteArrayNode;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.util.CharsetMapping;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(defineModule = "_codecs", isEager = true)
public class CodecsModuleBuiltins extends PythonBuiltins {

    public static final String STRICT = "strict";
    public static final String IGNORE = "ignore";
    public static final String REPLACE = "replace";
    public static final String BACKSLASHREPLACE = "backslashreplace";
    public static final String NAMEREPLACE = "namereplace";
    public static final String XMLCHARREFREPLACE = "xmlcharrefreplace";
    public static final String SURROGATEESCAPE = "surrogateescape";
    public static final String SURROGATEPASS = "surrogatepass";

    static CodingErrorAction convertCodingErrorAction(String errors) {
        CodingErrorAction errorAction;
        switch (errors) {
            // TODO: see [GR-10256] to implement the correct handling mechanics
            case IGNORE:
                errorAction = CodingErrorAction.IGNORE;
                break;
            case REPLACE:
            case NAMEREPLACE:
                errorAction = CodingErrorAction.REPLACE;
                break;
            case STRICT:
            case BACKSLASHREPLACE:
            case SURROGATEPASS:
            case SURROGATEESCAPE:
            case XMLCHARREFREPLACE:
            default:
                // Everything else will be handled by our Handle nodes
                errorAction = CodingErrorAction.REPORT;
                break;
        }
        return errorAction;
    }

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return CodecsModuleBuiltinsFactory.getFactories();
    }

    @GenerateUncached
    public abstract static class RaiseEncodingErrorNode extends Node {
        public abstract RuntimeException execute(TruffleEncoder encoder, Object inputObject);

        @Specialization
        static RuntimeException doRaise(TruffleEncoder encoder, Object inputObject,
                        @Cached CallNode callNode,
                        @Cached PRaiseNode raiseNode) {
            int start = encoder.getInputPosition();
            int end = start + encoder.getErrorLength();
            Object exception = callNode.execute(UnicodeEncodeError, encoder.getEncodingName(), inputObject, start, end, encoder.getErrorReason());
            if (exception instanceof PBaseException) {
                throw raiseNode.raiseExceptionObject((PBaseException) exception);
            } else {
                // Shouldn't happen unless the user manually replaces the method, which is really
                // unexpected and shouldn't be permitted at all, but currently it is
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raiseNode.raise(TypeError, ErrorMessages.SHOULD_HAVE_RETURNED_EXCEPTION, UnicodeEncodeError, exception);
            }
        }
    }

    @GenerateUncached
    public abstract static class HandleEncodingErrorNode extends Node {
        public abstract void execute(TruffleEncoder encoder, String errorAction, Object inputObject);

        @Specialization
        static void handle(TruffleEncoder encoder, String errorAction, Object inputObject,
                        @Cached ConditionProfile strictProfile,
                        @Cached ConditionProfile backslashreplaceProfile,
                        @Cached ConditionProfile surrogatepassProfile,
                        @Cached ConditionProfile surrogateescapeProfile,
                        @Cached ConditionProfile xmlcharrefreplaceProfile,
                        @Cached RaiseEncodingErrorNode raiseEncodingErrorNode,
                        @Cached PRaiseNode raiseNode) {
            boolean fixed;
            try {
                // Ignore and replace are handled by Java Charset
                if (strictProfile.profile(STRICT.equals(errorAction))) {
                    fixed = false;
                } else if (backslashreplaceProfile.profile(BACKSLASHREPLACE.equals(errorAction))) {
                    fixed = backslashreplace(encoder);
                } else if (surrogatepassProfile.profile(SURROGATEPASS.equals(errorAction))) {
                    fixed = surrogatepass(encoder);
                } else if (surrogateescapeProfile.profile(SURROGATEESCAPE.equals(errorAction))) {
                    fixed = surrogateescape(encoder);
                } else if (xmlcharrefreplaceProfile.profile(XMLCHARREFREPLACE.equals(errorAction))) {
                    fixed = xmlcharrefreplace(encoder);
                } else {
                    throw raiseNode.raise(LookupError, ErrorMessages.UNKNOWN_ERROR_HANDLER, errorAction);
                }
            } catch (OutOfMemoryError e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raiseNode.raise(MemoryError);
            }
            if (!fixed) {
                throw raiseEncodingErrorNode.execute(encoder, inputObject);
            }
        }

        @TruffleBoundary
        private static boolean backslashreplace(TruffleEncoder encoder) {
            String p = new String(encoder.getInputChars(encoder.getErrorLength()));
            StringBuilder sb = new StringBuilder();
            byte[] buf = new byte[10];
            for (int i = 0; i < p.length();) {
                int ch = p.codePointAt(i);
                int len;
                if (ch < 0x100) {
                    len = BytesUtils.byteEscape(ch, 0, buf);
                } else {
                    len = BytesUtils.unicodeNonAsciiEscape(ch, 0, buf);
                }
                for (int j = 0; j < len; j++) {
                    sb.append((char) buf[j]);
                }
                i += Character.charCount(ch);
            }
            encoder.replace(p.length(), sb.toString());
            return true;
        }

        @TruffleBoundary
        private static boolean surrogatepass(TruffleEncoder encoder) {
            // UTF-8 only for now. The name should be normalized already
            if (encoder.getEncodingName().equals("utf_8")) {
                String p = new String(encoder.getInputChars(encoder.getErrorLength()));
                byte[] replacement = new byte[p.length() * 3];
                int outp = 0;
                for (int i = 0; i < p.length();) {
                    int ch = p.codePointAt(i);
                    if (!(0xD800 <= ch && ch <= 0xDFFF)) {
                        // Not a surrogate
                        return false;
                    }
                    replacement[outp++] = (byte) (0xe0 | (ch >> 12));
                    replacement[outp++] = (byte) (0x80 | ((ch >> 6) & 0x3f));
                    replacement[outp++] = (byte) (0x80 | (ch & 0x3f));
                    i += Character.charCount(ch);
                }
                encoder.replace(encoder.getErrorLength(), replacement, 0, outp);
                return true;
            }
            return false;
        }

        @TruffleBoundary
        private static boolean surrogateescape(TruffleEncoder encoder) {
            String p = new String(encoder.getInputChars(encoder.getErrorLength()));
            byte[] replacement = new byte[p.length()];
            int outp = 0;
            for (int i = 0; i < p.length();) {
                int ch = p.codePointAt(i);
                if (!(0xDC80 <= ch && ch <= 0xDCFF)) {
                    // Not a surrogate
                    return false;
                }
                replacement[outp++] = (byte) (ch - 0xdc00);
                i += Character.charCount(ch);
            }
            encoder.replace(encoder.getErrorLength(), replacement, 0, outp);
            return true;
        }

        @TruffleBoundary
        private static boolean xmlcharrefreplace(TruffleEncoder encoder) {
            String p = new String(encoder.getInputChars(encoder.getErrorLength()));
            int size = 0;
            for (int i = 0; i < encoder.getErrorLength(); ++i) {
                // object is guaranteed to be "ready"
                int ch = p.codePointAt(i);
                if (ch < 10) {
                    size += 2 + 1 + 1;
                } else if (ch < 100) {
                    size += 2 + 2 + 1;
                } else if (ch < 1000) {
                    size += 2 + 3 + 1;
                } else if (ch < 10000) {
                    size += 2 + 4 + 1;
                } else if (ch < 100000) {
                    size += 2 + 5 + 1;
                } else if (ch < 1000000) {
                    size += 2 + 6 + 1;
                } else {
                    size += 2 + 7 + 1;
                }
            }

            byte[] replacement = new byte[size];
            int consumed = 0;
            // generate replacement
            for (int i = 0; i < p.length(); ++i) {
                int digits;
                int base;
                int ch = p.codePointAt(i);
                replacement[consumed++] = '&';
                replacement[consumed++] = '#';
                if (ch < 10) {
                    digits = 1;
                    base = 1;
                } else if (ch < 100) {
                    digits = 2;
                    base = 10;
                } else if (ch < 1000) {
                    digits = 3;
                    base = 100;
                } else if (ch < 10000) {
                    digits = 4;
                    base = 1000;
                } else if (ch < 100000) {
                    digits = 5;
                    base = 10000;
                } else if (ch < 1000000) {
                    digits = 6;
                    base = 100000;
                } else {
                    digits = 7;
                    base = 1000000;
                }
                while (digits-- > 0) {
                    replacement[consumed++] = (byte) ('0' + ch / base);
                    ch %= base;
                    base /= 10;
                }
                replacement[consumed++] = ';';
            }
            encoder.replace(encoder.getErrorLength(), replacement, 0, consumed);
            return true;
        }

        public static HandleEncodingErrorNode create() {
            return CodecsModuleBuiltinsFactory.HandleEncodingErrorNodeGen.create();
        }
    }

    @GenerateUncached
    public abstract static class RaiseDecodingErrorNode extends Node {
        public abstract RuntimeException execute(TruffleDecoder decoder, Object inputObject);

        @Specialization
        static RuntimeException doRaise(TruffleDecoder decoder, Object inputObject,
                        @Cached CallNode callNode,
                        @Cached PRaiseNode raiseNode) {
            int start = decoder.getInputPosition();
            int end = start + decoder.getErrorLength();
            Object exception = callNode.execute(UnicodeDecodeError, decoder.getEncodingName(), inputObject, start, end, decoder.getErrorReason());
            if (exception instanceof PBaseException) {
                throw raiseNode.raiseExceptionObject((PBaseException) exception);
            } else {
                // Shouldn't happen unless the user manually replaces the method, which is really
                // unexpected and shouldn't be permitted at all, but currently it is
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raiseNode.raise(TypeError, ErrorMessages.SHOULD_HAVE_RETURNED_EXCEPTION, UnicodeDecodeError, exception);
            }
        }
    }

    @GenerateUncached
    public abstract static class HandleDecodingErrorNode extends Node {
        public abstract void execute(TruffleDecoder decoder, String errorAction, Object inputObject);

        @Specialization
        static void doStrict(TruffleDecoder decoder, String errorAction, Object inputObject,
                        @Cached ConditionProfile strictProfile,
                        @Cached ConditionProfile backslashreplaceProfile,
                        @Cached ConditionProfile surrogatepassProfile,
                        @Cached ConditionProfile surrogateescapeProfile,
                        @Cached RaiseDecodingErrorNode raiseDecodingErrorNode,
                        @Cached PRaiseNode raiseNode) {
            boolean fixed;
            try {
                // Ignore and replace are handled by Java Charset
                if (strictProfile.profile(STRICT.equals(errorAction))) {
                    fixed = false;
                } else if (backslashreplaceProfile.profile(BACKSLASHREPLACE.equals(errorAction))) {
                    fixed = backslashreplace(decoder);
                } else if (surrogatepassProfile.profile(SURROGATEPASS.equals(errorAction))) {
                    fixed = surrogatepass(decoder);
                } else if (surrogateescapeProfile.profile(SURROGATEESCAPE.equals(errorAction))) {
                    fixed = surrogateescape(decoder);
                } else {
                    throw raiseNode.raise(LookupError, ErrorMessages.UNKNOWN_ERROR_HANDLER, errorAction);
                }
            } catch (OutOfMemoryError e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raiseNode.raise(MemoryError);
            }
            if (!fixed) {
                throw raiseDecodingErrorNode.execute(decoder, inputObject);
            }
        }

        @TruffleBoundary
        private static boolean backslashreplace(TruffleDecoder decoder) {
            byte[] p = decoder.getInputBytes(decoder.getErrorLength());
            char[] replacement = new char[p.length * 4];
            int outp = 0;
            byte[] buf = new byte[4];
            for (byte b : p) {
                BytesUtils.byteEscape(b, 0, buf);
                replacement[outp++] = (char) buf[0];
                replacement[outp++] = (char) buf[1];
                replacement[outp++] = (char) buf[2];
                replacement[outp++] = (char) buf[3];
            }
            decoder.replace(p.length, replacement, 0, outp);
            return true;
        }

        @TruffleBoundary
        private static boolean surrogatepass(TruffleDecoder decoder) {
            // UTF-8 only for now. The name should be normalized already
            if (decoder.getEncodingName().equals("utf_8")) {
                if (decoder.getInputRemaining() >= 3) {
                    byte[] p = decoder.getInputBytes(3);
                    if ((p[0] & 0xf0) == 0xe0 && (p[1] & 0xc0) == 0x80 && (p[2] & 0xc0) == 0x80) {
                        int codePoint = ((p[0] & 0x0f) << 12) + ((p[1] & 0x3f) << 6) + (p[2] & 0x3f);
                        if (0xD800 <= codePoint && codePoint <= 0xDFFF) {
                            decoder.replace(3, Character.toChars(codePoint));
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        @TruffleBoundary
        private static boolean surrogateescape(TruffleDecoder decoder) {
            int errorLength = decoder.getErrorLength();
            // decode up to 4 bytes
            int consumed = 0;
            boolean replaced = false;
            byte[] inputBytes = decoder.getInputBytes(errorLength);
            while (consumed < 4 && consumed < errorLength) {
                int b = inputBytes[consumed] & 0xff;
                // Refuse to escape ASCII bytes.
                if (b < 128) {
                    break;
                }
                int codePoint = 0xdc00 + b;
                decoder.replace(1, Character.toChars(codePoint));
                replaced = true;
                consumed += 1;
            }
            return replaced;
        }

        public static HandleDecodingErrorNode create() {
            return CodecsModuleBuiltinsFactory.HandleDecodingErrorNodeGen.create();
        }
    }

    // _codecs.encode(obj, encoding='utf-8', errors='strict')
    @Builtin(name = "__truffle_encode__", minNumOfPositionalArgs = 1, parameterNames = {"obj", "encoding", "errors"})
    @ArgumentClinic(name = "encoding", conversion = ArgumentClinic.ClinicConversion.String, defaultValue = "\"utf-8\"", useDefaultForNone = true)
    @ArgumentClinic(name = "errors", conversion = ArgumentClinic.ClinicConversion.String, defaultValue = "\"strict\"", useDefaultForNone = true)
    @GenerateNodeFactory
    public abstract static class CodecsEncodeNode extends PythonTernaryClinicBuiltinNode {
        public abstract Object execute(Object str, Object encoding, Object errors);

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return CodecsModuleBuiltinsClinicProviders.CodecsEncodeNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = {"isString(self)"})
        Object encode(Object self, String encoding, String errors,
                        @Cached CastToJavaStringNode castStr,
                        @Cached HandleEncodingErrorNode errorHandler) {
            String input = castStr.execute(self);
            CodingErrorAction errorAction = convertCodingErrorAction(errors);
            Charset charset = CharsetMapping.getCharset(encoding);
            if (charset == null) {
                throw raise(LookupError, ErrorMessages.UNKNOWN_ENCODING, encoding);
            }
            TruffleEncoder encoder;
            try {
                encoder = new TruffleEncoder(CharsetMapping.normalize(encoding), charset, input, errorAction);
                while (!encoder.encodingStep()) {
                    errorHandler.execute(encoder, errors, self);
                }
            } catch (OutOfMemoryError e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raise(MemoryError);
            }
            PBytes bytes = factory().createBytes(encoder.getBytes());
            return factory().createTuple(new Object[]{bytes, input.length()});
        }

        @Fallback
        Object encode(Object str, @SuppressWarnings("unused") Object encoding, @SuppressWarnings("unused") Object errors) {
            throw raise(TypeError, ErrorMessages.CANT_CONVERT_TO_STR_EXPLICITELY, str);
        }
    }

    // _codecs.decode(obj, encoding='utf-8', errors='strict', final=False)
    @Builtin(name = "__truffle_decode__", minNumOfPositionalArgs = 1, parameterNames = {"obj", "encoding", "errors", "final"})
    @ArgumentClinic(name = "encoding", conversion = ArgumentClinic.ClinicConversion.String, defaultValue = "\"utf-8\"", useDefaultForNone = true)
    @ArgumentClinic(name = "errors", conversion = ArgumentClinic.ClinicConversion.String, defaultValue = "\"strict\"", useDefaultForNone = true)
    @ArgumentClinic(name = "final", conversion = ArgumentClinic.ClinicConversion.Boolean, defaultValue = "false", useDefaultForNone = true)
    @GenerateNodeFactory
    public abstract static class CodecsDecodeNode extends PythonQuaternaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return CodecsModuleBuiltinsClinicProviders.CodecsDecodeNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        Object decode(PBytesLike input, String encoding, String errors, boolean finalData,
                        @Cached GetInternalByteArrayNode getBytes,
                        @Cached HandleDecodingErrorNode errorHandler) {
            byte[] bytes = getBytes.execute(input.getSequenceStorage());
            CodingErrorAction errorAction = convertCodingErrorAction(errors);
            Charset charset = CharsetMapping.getCharset(encoding);
            if (charset == null) {
                throw raise(LookupError, ErrorMessages.UNKNOWN_ENCODING, encoding);
            }
            TruffleDecoder decoder;
            try {
                decoder = new TruffleDecoder(CharsetMapping.normalize(encoding), charset, bytes, bytes.length, errorAction);
                while (!decoder.decodingStep(finalData)) {
                    errorHandler.execute(decoder, errors, input);
                }
            } catch (OutOfMemoryError e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raise(MemoryError);
            }
            return factory().createTuple(new Object[]{decoder.getString(), decoder.getInputPosition()});
        }

        @Fallback
        Object decode(Object bytes, @SuppressWarnings("unused") Object encoding, @SuppressWarnings("unused") Object errors, @SuppressWarnings("unused") Object finalData) {
            throw raise(TypeError, BYTESLIKE_OBJ_REQUIRED, bytes);
        }
    }

    @Builtin(name = "escape_decode", minNumOfPositionalArgs = 1, parameterNames = {"data", "errors"})
    @ArgumentClinic(name = "data", conversion = ArgumentClinic.ClinicConversion.ReadableBuffer)
    @ArgumentClinic(name = "errors", conversion = ArgumentClinic.ClinicConversion.String, defaultValue = "\"strict\"", useDefaultForNone = true)
    @GenerateNodeFactory
    public abstract static class CodecsEscapeDecodeNode extends PythonBinaryClinicBuiltinNode {
        enum Errors {
            ERR_STRICT,
            ERR_IGNORE,
            ERR_REPLACE,
            ERR_UNKNOWN,
        }

        private static Errors getErrors(String err) {
            switch (err) {
                case STRICT:
                    return Errors.ERR_STRICT;
                case REPLACE:
                    return Errors.ERR_REPLACE;
                case IGNORE:
                    return Errors.ERR_IGNORE;
                default:
                    return Errors.ERR_UNKNOWN;
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return CodecsModuleBuiltinsClinicProviders.CodecsEscapeDecodeNodeClinicProviderGen.INSTANCE;
        }

        public final Object execute(@SuppressWarnings("unused") VirtualFrame frame, byte[] bytes, String errors) {
            return decodeBytes(bytes, bytes.length, errors);
        }

        @Specialization(limit = "3")
        Object decode(Object buffer, String errors,
                        @CachedLibrary("buffer") PythonBufferAccessLibrary bufferLib) {
            try {
                int len = bufferLib.getBufferLength(buffer);
                return decodeBytes(bufferLib.getInternalOrCopiedByteArray(buffer), len, errors);
            } finally {
                bufferLib.release(buffer);
            }
        }

        private Object decodeBytes(byte[] bytes, int len, String errors) {
            ByteArrayBuffer result = doDecode(bytes, len, errors);
            return factory().createTuple(new Object[]{factory().createBytes(result.getInternalBytes(), result.getLength()), len});
        }

        @TruffleBoundary
        private ByteArrayBuffer doDecode(byte[] bytes, int bytesLen, String errors) {
            Errors err = getErrors(errors);
            ByteArrayBuffer buffer = new ByteArrayBuffer(bytesLen);
            for (int i = 0; i < bytesLen; i++) {
                char chr = (char) bytes[i];
                if (chr != '\\') {
                    buffer.append(chr);
                    continue;
                }

                i++;
                if (i >= bytesLen) {
                    throw raise(ValueError, ErrorMessages.TRAILING_S_IN_STR, "\\");
                }

                chr = (char) bytes[i];
                switch (chr) {
                    case '\n':
                        break;
                    case '\\':
                        buffer.append('\\');
                        break;
                    case '\'':
                        buffer.append('\'');
                        break;
                    case '\"':
                        buffer.append('\"');
                        break;
                    case 'b':
                        buffer.append('\b');
                        break;
                    case 'f':
                        buffer.append('\014');
                        break; /* FF */
                    case 't':
                        buffer.append('\t');
                        break;
                    case 'n':
                        buffer.append('\n');
                        break;
                    case 'r':
                        buffer.append('\r');
                        break;
                    case 'v':
                        buffer.append('\013');
                        break; /* VT */
                    case 'a':
                        buffer.append('\007');
                        break; /* BEL */
                    case '0':
                    case '1':
                    case '2':
                    case '3':
                    case '4':
                    case '5':
                    case '6':
                    case '7':
                        int code = chr - '0';
                        if (i + 1 < bytesLen) {
                            char nextChar = (char) bytes[i + 1];
                            if ('0' <= nextChar && nextChar <= '7') {
                                code = (code << 3) + nextChar - '0';
                                i++;

                                if (i + 1 < bytesLen) {
                                    nextChar = (char) bytes[i + 1];
                                    if ('0' <= nextChar && nextChar <= '7') {
                                        code = (code << 3) + nextChar - '0';
                                        i++;
                                    }
                                }
                            }
                        }
                        buffer.append((char) code);
                        break;
                    case 'x':
                        if (i + 2 < bytesLen) {
                            int digit1 = digitValue(bytes[i + 1]);
                            int digit2 = digitValue(bytes[i + 2]);
                            if (digit1 < 16 && digit2 < 16) {
                                buffer.append((digit1 << 4) + digit2);
                                i += 2;
                                break;
                            }
                        }
                        // invalid hexadecimal digits
                        if (err == Errors.ERR_STRICT) {
                            throw raise(ValueError, INVALID_ESCAPE_AT, "\\x", i - 2);
                        }
                        if (err == Errors.ERR_REPLACE) {
                            buffer.append('?');
                        } else if (err == Errors.ERR_IGNORE) {
                            // do nothing
                        } else {
                            throw raise(ValueError, ENCODING_ERROR_WITH_CODE, errors);
                        }

                        // skip \x
                        if (i + 1 < bytesLen && isHexDigit((char) bytes[i + 1])) {
                            i++;
                        }
                        break;

                    default:
                        buffer.append('\\');
                        buffer.append(chr);
                }
            }

            return buffer;
        }

        private static boolean isHexDigit(char digit) {
            return ('0' <= digit && digit <= '9') || ('a' <= digit && digit <= 'f') || ('A' <= digit && digit <= 'F');
        }
    }

    @Builtin(name = "escape_encode", minNumOfPositionalArgs = 1, parameterNames = {"data", "errors"})
    @ArgumentClinic(name = "errors", conversion = ArgumentClinic.ClinicConversion.String, defaultValue = "\"strict\"", useDefaultForNone = true)
    @GenerateNodeFactory
    public abstract static class CodecsEscapeEncodeNode extends PythonBinaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return CodecsModuleBuiltinsClinicProviders.CodecsEscapeEncodeNodeClinicProviderGen.INSTANCE;
        }

        @TruffleBoundary
        @Specialization
        Object encode(PBytes data, @SuppressWarnings("unused") String errors,
                        @Cached GetInternalByteArrayNode getInternalByteArrayNode) {
            byte[] bytes = getInternalByteArrayNode.execute(data.getSequenceStorage());
            int size = bytes.length;
            ByteArrayBuffer buffer = new ByteArrayBuffer();
            char c;
            for (byte aByte : bytes) {
                // There's at least enough room for a hex escape
                c = (char) aByte;
                if (c == '\'' || c == '\\') {
                    buffer.append('\\');
                    buffer.append(c);
                } else if (c == '\t') {
                    buffer.append('\\');
                    buffer.append('t');
                } else if (c == '\n') {
                    buffer.append('\\');
                    buffer.append('n');
                } else if (c == '\r') {
                    buffer.append('\\');
                    buffer.append('r');
                } else if (c < ' ' || c >= 0x7f) {
                    buffer.append('\\');
                    buffer.append('x');
                    buffer.append(HEXDIGITS[(c & 0xf0) >> 4]);
                    buffer.append(HEXDIGITS[c & 0xf]);
                } else {
                    buffer.append(c);
                }
            }

            return factory().createTuple(new Object[]{
                            factory().createBytes(buffer.getByteArray()),
                            size
            });
        }

        @Fallback
        Object encode(Object data, @SuppressWarnings("unused") Object errors) {
            throw raise(TypeError, BYTESLIKE_OBJ_REQUIRED, data);
        }
    }

    @Builtin(name = "__truffle_lookup__", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class CodecsLookupNode extends PythonBuiltinNode {
        @Specialization
        static Object lookup(String encoding) {
            if (CharsetMapping.getCharset(encoding) != null) {
                return true;
            } else {
                return PNone.NONE;
            }
        }
    }

    @Builtin(name = "charmap_build", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class CharmapBuildNode extends PythonBuiltinNode {
        // This is replaced in the core _codecs.py with the full functionality
        @Specialization
        Object lookup(String chars,
                        @CachedLibrary(limit = "3") HashingStorageLibrary lib) {
            HashingStorage store = PDict.createNewStorage(false, chars.length());
            PDict dict = factory().createDict(store);
            int pos = 0;
            int num = 0;

            while (pos < chars.length()) {
                int charid = codePointAt(chars, pos);
                store = lib.setItem(store, charid, num);
                pos += charCount(charid);
                num++;
            }
            dict.setDictStorage(store);
            return dict;
        }

        @TruffleBoundary
        private static int charCount(int charid) {
            return Character.charCount(charid);
        }

        @TruffleBoundary
        private static int codePointAt(String chars, int pos) {
            return Character.codePointAt(chars, pos);
        }
    }

    static class TruffleEncoder {
        private final String encodingName;
        private final CharsetEncoder encoder;
        private CharBuffer inputBuffer;
        private ByteBuffer outputBuffer;
        private CoderResult coderResult;

        @TruffleBoundary
        public TruffleEncoder(String encodingName, Charset charset, String input, CodingErrorAction errorAction) {
            this.encodingName = encodingName;
            this.inputBuffer = CharBuffer.wrap(input);
            this.encoder = charset.newEncoder().onMalformedInput(errorAction).onUnmappableCharacter(errorAction);
            this.outputBuffer = ByteBuffer.allocate((int) (input.length() * encoder.averageBytesPerChar()));
        }

        @TruffleBoundary
        public boolean encodingStep() {
            while (true) {
                coderResult = encoder.encode(inputBuffer, outputBuffer, true);
                if (coderResult.isUnderflow()) {
                    coderResult = encoder.flush(outputBuffer);
                }

                if (coderResult.isUnderflow()) {
                    outputBuffer.flip();
                    return true;
                }

                if (coderResult.isOverflow()) {
                    grow();
                } else if (coderResult.isError()) {
                    return false;
                }
            }
        }

        private void grow() {
            int newCapacity = 2 * outputBuffer.capacity() + 1;
            // Overflow check - (2 * Integer.MAX_VALUE + 1 == -1) => overflown result cannot be
            // positive
            if (newCapacity < 0) {
                throw new OutOfMemoryError();
            }
            ByteBuffer newBuffer = ByteBuffer.allocate(newCapacity);
            outputBuffer.flip();
            newBuffer.put(outputBuffer);
            outputBuffer = newBuffer;
        }

        @TruffleBoundary
        private String getErrorReason() {
            if (coderResult.isMalformed()) {
                return ErrorMessages.MALFORMED_INPUT;
            } else if (coderResult.isUnmappable()) {
                return ErrorMessages.UNMAPPABLE_CHARACTER;
            } else {
                throw new IllegalArgumentException("Unicode error constructed from non-error result");
            }
        }

        @TruffleBoundary
        public int getInputPosition() {
            return inputBuffer.position();
        }

        @TruffleBoundary
        public int getErrorLength() {
            return coderResult.length();
        }

        @TruffleBoundary
        public byte[] getBytes() {
            byte[] data = new byte[outputBuffer.remaining()];
            outputBuffer.get(data);
            return data;
        }

        @TruffleBoundary
        public int getInputRemaining() {
            return inputBuffer.remaining();
        }

        @TruffleBoundary
        public char[] getInputChars(int num) {
            char[] chars = new char[num];
            int pos = inputBuffer.position();
            inputBuffer.get(chars);
            inputBuffer.position(pos);
            return chars;
        }

        @TruffleBoundary
        public void replace(int skipInput, byte[] replacement, int offset, int length) {
            while (outputBuffer.remaining() < replacement.length) {
                grow();
            }
            outputBuffer.put(replacement, offset, length);
            inputBuffer.position(inputBuffer.position() + skipInput);
        }

        @TruffleBoundary
        public void replace(int skipInput, String replacement) {
            inputBuffer.position(inputBuffer.position() + skipInput);
            CharBuffer newBuffer = CharBuffer.allocate(inputBuffer.remaining() + replacement.length());
            newBuffer.put(replacement);
            newBuffer.put(inputBuffer);
            newBuffer.flip();
            inputBuffer = newBuffer;
        }

        public String getEncodingName() {
            return encodingName;
        }
    }

    static class TruffleDecoder {
        private final String encodingName;
        private final CharsetDecoder decoder;
        private final ByteBuffer inputBuffer;
        private CharBuffer outputBuffer;
        private CoderResult coderResult;

        @TruffleBoundary
        public TruffleDecoder(String encodingName, Charset charset, byte[] input, int inputLen, CodingErrorAction errorAction) {
            this.encodingName = encodingName;
            this.inputBuffer = ByteBuffer.wrap(input, 0, inputLen);
            this.decoder = charset.newDecoder().onMalformedInput(errorAction).onUnmappableCharacter(errorAction);
            this.outputBuffer = CharBuffer.allocate((int) (inputLen * decoder.averageCharsPerByte()));
        }

        @TruffleBoundary
        public boolean decodingStep(boolean finalData) {
            while (true) {
                coderResult = decoder.decode(inputBuffer, outputBuffer, finalData);
                if (finalData && coderResult.isUnderflow()) {
                    coderResult = decoder.flush(outputBuffer);
                }

                if (coderResult.isUnderflow()) {
                    outputBuffer.flip();
                    return true;
                }

                if (coderResult.isOverflow()) {
                    grow();
                } else if (coderResult.isError()) {
                    return false;
                }
            }
        }

        private void grow() {
            int newCapacity = 2 * outputBuffer.capacity() + 1;
            // Overflow check - (2 * Integer.MAX_VALUE + 1 == -1) => overflown result cannot be
            // positive
            if (newCapacity < 0) {
                throw new OutOfMemoryError();
            }
            CharBuffer newBuffer = CharBuffer.allocate(newCapacity);
            outputBuffer.flip();
            newBuffer.put(outputBuffer);
            outputBuffer = newBuffer;
        }

        @TruffleBoundary
        private String getErrorReason() {
            if (coderResult.isMalformed()) {
                return ErrorMessages.MALFORMED_INPUT;
            } else if (coderResult.isUnmappable()) {
                return ErrorMessages.UNMAPPABLE_CHARACTER;
            } else {
                throw new IllegalArgumentException("Unicode error constructed from non-error result");
            }
        }

        @TruffleBoundary
        public int getInputRemaining() {
            return inputBuffer.remaining();
        }

        @TruffleBoundary
        public byte[] getInputBytes(int num) {
            byte[] bytes = new byte[num];
            int pos = inputBuffer.position();
            inputBuffer.get(bytes);
            inputBuffer.position(pos);
            return bytes;
        }

        @TruffleBoundary
        public String getString() {
            return outputBuffer.toString();
        }

        @TruffleBoundary
        public int getInputPosition() {
            return inputBuffer.position();
        }

        @TruffleBoundary
        public int getErrorLength() {
            return coderResult.length();
        }

        public void replace(int skipInput, char[] chars) {
            replace(skipInput, chars, 0, chars.length);
        }

        @TruffleBoundary
        public void replace(int skipInput, char[] chars, int offset, int length) {
            while (outputBuffer.remaining() < chars.length) {
                grow();
            }
            outputBuffer.put(chars, offset, length);
            inputBuffer.position(inputBuffer.position() + skipInput);
        }

        public String getEncodingName() {
            return encodingName;
        }
    }
}
