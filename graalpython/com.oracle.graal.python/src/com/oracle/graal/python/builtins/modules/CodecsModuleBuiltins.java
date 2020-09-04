/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.runtime.exception.PythonErrorType.LookupError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.UnicodeDecodeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.UnicodeEncodeError;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.BytesUtils;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetInternalByteArrayNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.GetInternalByteArrayNodeGen;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.expression.CoerceToBooleanNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNodeGen;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.util.CharsetMapping;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;

@CoreFunctions(defineModule = "_codecs")
public class CodecsModuleBuiltins extends PythonBuiltins {
    private static final Charset UTF32 = Charset.forName("utf-32");

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return CodecsModuleBuiltinsFactory.getFactories();
    }

    abstract static class EncodeBaseNode extends PythonBuiltinNode {

        protected static CodingErrorAction convertCodingErrorAction(String errors) {
            CodingErrorAction errorAction;
            switch (errors) {
                // TODO: see [GR-10256] to implement the correct handling mechanics
                case "ignore":
                case "surrogatepass":
                    errorAction = CodingErrorAction.IGNORE;
                    break;
                case "replace":
                case "surrogateescape":
                case "namereplace":
                case "backslashreplace":
                case "xmlcharrefreplace":
                    errorAction = CodingErrorAction.REPLACE;
                    break;
                default:
                    errorAction = CodingErrorAction.REPORT;
                    break;
            }
            return errorAction;
        }
    }

    @Builtin(name = "unicode_escape_encode", minNumOfPositionalArgs = 1, parameterNames = {"str", "errors"})
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class UnicodeEscapeEncode extends PythonBinaryBuiltinNode {

        @Specialization
        Object encode(String str, @SuppressWarnings("unused") Object errors) {
            return factory().createTuple(new Object[]{factory().createBytes(BytesUtils.unicodeEscape(str)), str.length()});
        }

        @Fallback
        Object encode(Object str, @SuppressWarnings("unused") Object errors) {
            throw raise(TypeError, ErrorMessages.ARG_D_MUST_BE_S_NOT_P, "unicode_escape_encode()", 1, "str", str);
        }
    }

    @Builtin(name = "unicode_escape_decode", minNumOfPositionalArgs = 1, parameterNames = {"str", "errors"})
    @GenerateNodeFactory
    abstract static class UnicodeEscapeDecode extends PythonBinaryBuiltinNode {
        @Specialization
        Object encode(VirtualFrame frame, PBytesLike bytes, @SuppressWarnings("unused") PNone errors,
                        @Shared("toBytes") @Cached("create()") BytesNodes.ToBytesNode toBytes) {
            return encode(frame, bytes, "", toBytes);
        }

        @Specialization
        Object encode(VirtualFrame frame, PBytesLike bytes, @SuppressWarnings("unused") String errors,
                        @Shared("toBytes") @Cached("create()") BytesNodes.ToBytesNode toBytes) {
            // for now we'll just parse this as a String, ignoring any error strategies
            PythonCore core = getCore();
            byte[] byteArray = toBytes.execute(frame, bytes);
            String string = strFromBytes(byteArray);
            String unescapedString = core.getParser().unescapeJavaString(core, string);
            return factory().createTuple(new Object[]{unescapedString, byteArray.length});
        }

        @TruffleBoundary
        private static String strFromBytes(byte[] execute) {
            return new String(execute, StandardCharsets.ISO_8859_1);
        }
    }

    // _codecs.encode(obj, encoding='utf-8', errors='strict')
    @Builtin(name = "__truffle_encode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "encoding", "errors"})
    @GenerateNodeFactory
    public abstract static class CodecsEncodeNode extends EncodeBaseNode {
        @Child private SequenceStorageNodes.LenNode lenNode;

        @Specialization(guards = "isString(str)")
        Object encode(Object str, @SuppressWarnings("unused") PNone encoding, @SuppressWarnings("unused") PNone errors,
                        @Shared("castStr") @Cached CastToJavaStringNode castStr) {
            String profiledStr = cast(castStr, str);
            PBytes bytes = encodeString(profiledStr, "utf-8", "strict");
            return factory().createTuple(new Object[]{bytes, getLength(bytes)});
        }

        @Specialization(guards = {"isString(str)", "isString(encoding)"})
        Object encode(Object str, Object encoding, @SuppressWarnings("unused") PNone errors,
                        @Shared("castStr") @Cached CastToJavaStringNode castStr,
                        @Shared("castEncoding") @Cached CastToJavaStringNode castEncoding) {
            String profiledStr = cast(castStr, str);
            String profiledEncoding = cast(castEncoding, encoding);
            PBytes bytes = encodeString(profiledStr, profiledEncoding, "strict");
            return factory().createTuple(new Object[]{bytes, getLength(bytes)});
        }

        @Specialization(guards = {"isString(str)", "isString(errors)"})
        Object encode(Object str, @SuppressWarnings("unused") PNone encoding, Object errors,
                        @Shared("castStr") @Cached CastToJavaStringNode castStr,
                        @Shared("castErrors") @Cached CastToJavaStringNode castErrors) {
            String profiledStr = cast(castStr, str);
            String profiledErrors = cast(castErrors, errors);
            PBytes bytes = encodeString(profiledStr, "utf-8", profiledErrors);
            return factory().createTuple(new Object[]{bytes, getLength(bytes)});
        }

        @Specialization(guards = {"isString(str)", "isString(encoding)", "isString(errors)"})
        Object encode(Object str, Object encoding, Object errors,
                        @Shared("castStr") @Cached CastToJavaStringNode castStr,
                        @Shared("castEncoding") @Cached CastToJavaStringNode castEncoding,
                        @Shared("castErrors") @Cached CastToJavaStringNode castErrors) {
            String profiledStr = cast(castStr, str);
            String profiledEncoding = cast(castEncoding, encoding);
            String profiledErrors = cast(castErrors, errors);
            PBytes bytes = encodeString(profiledStr, profiledEncoding, profiledErrors);
            return factory().createTuple(new Object[]{bytes, getLength(bytes)});
        }

        private static String cast(CastToJavaStringNode cast, Object obj) {
            try {
                return cast.execute(obj);
            } catch (CannotCastException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException("should not be reached");
            }
        }

        @Fallback
        Object encode(Object str, @SuppressWarnings("unused") Object encoding, @SuppressWarnings("unused") Object errors) {
            throw raise(TypeError, ErrorMessages.CANT_CONVERT_TO_STR_EXPLICITELY, str);
        }

        @TruffleBoundary
        private PBytes encodeString(String self, String encoding, String errors) {
            CodingErrorAction errorAction = convertCodingErrorAction(errors);
            Charset charset = CharsetMapping.getCharset(encoding);
            if (charset == null) {
                throw raise(LookupError, ErrorMessages.UNKNOWN_ENCODING, encoding);
            }
            try {
                ByteBuffer encoded = charset.newEncoder().onMalformedInput(errorAction).onUnmappableCharacter(errorAction).encode(CharBuffer.wrap(self));
                int n = encoded.remaining();
                byte[] data = new byte[n];
                encoded.get(data);
                return factory().createBytes(data);
            } catch (CharacterCodingException e) {
                throw raise(UnicodeEncodeError, e);
            }
        }

        private int getLength(PBytes b) {
            if (lenNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lenNode = insert(SequenceStorageNodes.LenNode.create());
            }
            return lenNode.execute(b.getSequenceStorage());
        }
    }

    // Encoder for raw_unicode_escape
    @Builtin(name = "__truffle_raw_encode", minNumOfPositionalArgs = 1, parameterNames = {"str", "errors"})
    @GenerateNodeFactory
    public abstract static class RawEncodeNode extends EncodeBaseNode {

        @Specialization
        PTuple doStringNone(String self, @SuppressWarnings("unused") PNone none) {
            return doStringString(self, "strict");
        }

        @Specialization
        PTuple doStringString(String self, String errors) {
            return factory().createTuple(encodeString(self, errors));
        }

        @Specialization(replaces = {"doStringNone", "doStringString"})
        PTuple doGeneric(Object selfObj, Object errorsObj,
                        @Cached CastToJavaStringNode castToJavaStringNode) {

            String errors;
            if (PGuards.isPNone(errorsObj)) {
                errors = "strict";
            } else {
                try {
                    errors = castToJavaStringNode.execute(errorsObj);
                } catch (CannotCastException e) {
                    throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.ARG_MUST_BE_S_NOT_P, "'errors'", "str", errorsObj);
                }
            }

            try {
                return factory().createTuple(encodeString(castToJavaStringNode.execute(selfObj), errors));
            } catch (CannotCastException e) {
                throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.MUST_BE_S_NOT_P, "argument", "str", selfObj);
            }
        }

        @TruffleBoundary
        private Object[] encodeString(String self, String errors) {
            CodingErrorAction errorAction = convertCodingErrorAction(errors);

            try {
                ByteBuffer encoded = UTF32.newEncoder().onMalformedInput(errorAction).onUnmappableCharacter(errorAction).encode(CharBuffer.wrap(self));
                int n = encoded.remaining();
                // Worst case is 6 bytes ("\\uXXXX") for every java char
                ByteBuffer buf = ByteBuffer.allocate(self.length() * 6);
                assert n % Integer.BYTES == 0;
                int codePoints = n / Integer.BYTES;

                while (encoded.hasRemaining()) {
                    int codePoint = encoded.getInt();
                    if (codePoint <= 0xFF) {
                        buf.put((byte) codePoint);
                    } else {
                        String hexString = String.format((codePoint <= 0xFFFF ? "\\u%04x" : "\\U%08x"), codePoint);
                        for (int i = 0; i < hexString.length(); i++) {
                            assert hexString.charAt(i) < 128;
                            buf.put((byte) hexString.charAt(i));
                        }
                    }
                }
                buf.flip();
                n = buf.remaining();
                byte[] data = new byte[n];
                buf.get(data);
                // TODO(fa): bytes object creation should not be behind a TruffleBoundary
                return new Object[]{factory().createBytes(data), codePoints};
            } catch (CharacterCodingException e) {
                throw raise(UnicodeEncodeError, e);
            }
        }

    }

    // _codecs.decode(obj, encoding='utf-8', errors='strict', final=False)
    @Builtin(name = "__truffle_decode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "encoding", "errors", "final"})
    @GenerateNodeFactory
    abstract static class CodecsDecodeNode extends EncodeBaseNode {
        @Child private GetInternalByteArrayNode toByteArrayNode;
        @Child private CastToJavaStringNode castEncodingToStringNode;
        @Child private CoerceToBooleanNode castToBooleanNode;

        @Specialization
        Object decode(VirtualFrame frame, PBytesLike bytes, @SuppressWarnings("unused") PNone encoding, @SuppressWarnings("unused") PNone errors, Object finalData) {
            ByteBuffer decoded = getBytes(bytes);
            String string = decodeBytes(decoded, "utf-8", "strict", castToBoolean(frame, finalData));
            return factory().createTuple(new Object[]{string, decoded.position()});
        }

        @Specialization(guards = {"isString(encoding)"})
        Object decode(VirtualFrame frame, PBytesLike bytes, Object encoding, @SuppressWarnings("unused") PNone errors, Object finalData) {
            ByteBuffer decoded = getBytes(bytes);
            String string = decodeBytes(decoded, castToString(encoding), "strict", castToBoolean(frame, finalData));
            return factory().createTuple(new Object[]{string, decoded.position()});
        }

        @Specialization(guards = {"isString(errors)"})
        Object decode(VirtualFrame frame, PBytesLike bytes, @SuppressWarnings("unused") PNone encoding, Object errors, Object finalData) {
            ByteBuffer decoded = getBytes(bytes);
            String string = decodeBytes(decoded, "utf-8", castToString(errors), castToBoolean(frame, finalData));
            return factory().createTuple(new Object[]{string, decoded.position()});
        }

        @Specialization(guards = {"isString(encoding)", "isString(errors)"})
        Object decode(VirtualFrame frame, PBytesLike bytes, Object encoding, Object errors, Object finalData) {
            ByteBuffer decoded = getBytes(bytes);
            String string = decodeBytes(decoded, castToString(encoding), castToString(errors), castToBoolean(frame, finalData));
            return factory().createTuple(new Object[]{string, decoded.position()});
        }

        @Fallback
        Object decode(Object bytes, @SuppressWarnings("unused") Object encoding, @SuppressWarnings("unused") Object errors, @SuppressWarnings("unused") Object finalData) {
            throw raise(TypeError, ErrorMessages.BYTESLIKE_OBJ_REQUIRED, bytes);
        }

        @TruffleBoundary
        private static ByteBuffer wrap(byte[] bytes) {
            return ByteBuffer.wrap(bytes);
        }

        @TruffleBoundary
        String decodeBytes(ByteBuffer byteBuffer, String encoding, String errors, boolean finalData) {
            CodingErrorAction errorAction = convertCodingErrorAction(errors);
            Charset charset = CharsetMapping.getCharset(encoding);
            if (charset == null) {
                throw raise(LookupError, ErrorMessages.UNKNOWN_ENCODING, encoding);
            }
            CharBuffer decoded = CharBuffer.allocate(byteBuffer.capacity());
            CoderResult result = charset.newDecoder().onMalformedInput(errorAction).onUnmappableCharacter(errorAction).decode(byteBuffer, decoded, finalData);
            if (result.isError()) {
                throw raise(UnicodeDecodeError, result.toString());
            }
            return String.valueOf(decoded.flip());
        }

        private ByteBuffer getBytes(PBytesLike bytesLike) {
            if (toByteArrayNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toByteArrayNode = insert(GetInternalByteArrayNodeGen.create());
            }
            return wrap(toByteArrayNode.execute(bytesLike.getSequenceStorage()));
        }

        private String castToString(Object encodingObj) {
            if (castEncodingToStringNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castEncodingToStringNode = insert(CastToJavaStringNodeGen.create());
            }
            try {
                return castEncodingToStringNode.execute(encodingObj);
            } catch (CannotCastException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException("should not be reached");
            }
        }

        private boolean castToBoolean(VirtualFrame frame, Object object) {
            if (object == PNone.NO_VALUE) {
                return false;
            }
            if (castToBooleanNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castToBooleanNode = insert(CoerceToBooleanNode.createIfTrueNode());
            }
            return castToBooleanNode.executeBoolean(frame, object);
        }
    }

    // Decoder for raw_escape_unicode
    @Builtin(name = "__truffle_raw_decode", minNumOfPositionalArgs = 1, parameterNames = {"bytes", "errors"})
    @GenerateNodeFactory
    abstract static class RawDecodeNode extends EncodeBaseNode {
        @Child private GetInternalByteArrayNode toByteArrayNode;

        @Specialization
        Object decode(PBytesLike bytes, @SuppressWarnings("unused") PNone errors) {
            String string = decodeBytes(getBytes(bytes), "strict");
            return factory().createTuple(new Object[]{string, string.length()});
        }

        @Specialization(guards = {"isString(errors)"})
        Object decode(PBytesLike bytes, Object errors,
                        @Cached CastToJavaStringNode castStr) {
            String profiledErrors;
            try {
                profiledErrors = castStr.execute(errors);
            } catch (CannotCastException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException("should not be reached");
            }
            String string = decodeBytes(getBytes(bytes), profiledErrors);
            return factory().createTuple(new Object[]{string, string.length()});
        }

        private byte[] getBytes(PBytesLike bytesLike) {
            if (toByteArrayNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toByteArrayNode = insert(GetInternalByteArrayNodeGen.create());
            }
            return toByteArrayNode.execute(bytesLike.getSequenceStorage());
        }

        @TruffleBoundary
        String decodeBytes(byte[] bytes, String errors) {
            CodingErrorAction errorAction = convertCodingErrorAction(errors);
            try {
                ByteBuffer buf = ByteBuffer.allocate(bytes.length * Integer.BYTES);
                int i = 0;
                while (i < bytes.length) {
                    byte b = bytes[i];
                    if (b == (byte) '\\' && i + 1 < bytes.length) {
                        byte b1 = bytes[i + 1];
                        int numIndex = i + 2;
                        if (b1 == (byte) 'u') {
                            final int count = 4;
                            if (numIndex + count > bytes.length) {
                                throw raise(UnicodeDecodeError);
                            }
                            buf.putInt(Integer.parseInt(new String(bytes, numIndex, count), 16));
                            i = numIndex + count;
                            continue;
                        } else if (b1 == (byte) 'U') {
                            final int count = 8;
                            if (numIndex + count > bytes.length) {
                                throw raise(UnicodeDecodeError);
                            }
                            buf.putInt(Integer.parseInt(new String(bytes, numIndex, count), 16));
                            i = numIndex + count;
                            continue;
                        }
                    }
                    // Bytes that are not an escape sequence are latin-1, which maps to unicode
                    // codepoints directly
                    buf.putInt(b & 0xFF);
                    i++;
                }
                buf.flip();
                CharBuffer decoded = UTF32.newDecoder().onMalformedInput(errorAction).onUnmappableCharacter(errorAction).decode(buf);
                return String.valueOf(decoded);
            } catch (CharacterCodingException | NumberFormatException e) {
                throw raise(UnicodeDecodeError, e);
            }
        }
    }

    @Builtin(name = "__truffle_lookup", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class CodecsLookupNode extends PythonBuiltinNode {
        @Specialization
        Object lookup(String encoding) {
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
}
