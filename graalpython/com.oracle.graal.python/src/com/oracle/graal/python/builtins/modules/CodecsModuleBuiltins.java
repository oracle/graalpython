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
import static com.oracle.graal.python.runtime.exception.PythonErrorType.MemoryError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.UnicodeDecodeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.UnicodeEncodeError;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.BytesUtils;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetInternalByteArrayNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.GetInternalByteArrayNodeGen;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.expression.CoerceToBooleanNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNodeGen;
import com.oracle.graal.python.util.CharsetMapping;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.CachedLanguage;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ValueProfile;

@CoreFunctions(defineModule = "_codecs")
public class CodecsModuleBuiltins extends PythonBuiltins {

    public static final String STRICT = "strict";
    public static final String IGNORE = "ignore";
    public static final String REPLACE = "replace";
    public static final String BACKSLASHREPLACE = "backslashreplace";
    public static final String NAMEREPLACE = "namereplace";
    public static final String XMLCHARREFREPLACE = "xmlcharrefreplace";
    public static final String SURROGATEESCAPE = "surrogateescape";
    public static final String SURROGATEPASS = "surrogatepass";

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return CodecsModuleBuiltinsFactory.getFactories();
    }

    @GenerateUncached
    public abstract static class RaiseEncodingErrorNode extends Node {
        public abstract RuntimeException execute(TruffleEncoder encoder, Object inputObject);

        @Specialization
        public RuntimeException doRaise(TruffleEncoder encoder, Object inputObject,
                        @Cached CallNode callNode,
                        @Cached PRaiseNode raiseNode,
                        @CachedLanguage PythonLanguage pythonLanguage) {
            int start = encoder.getInputPosition();
            int end = start + encoder.getErrorLenght();
            Object exception = callNode.execute(UnicodeEncodeError, encoder.getEncodingName(), inputObject, start, end, encoder.getErrorReason());
            if (exception instanceof PBaseException) {
                throw raiseNode.raiseExceptionObject((PBaseException) exception, pythonLanguage);
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
        void handle(TruffleEncoder encoder, String errorAction, Object inputObject,
                        @Cached("createIdentityProfile()") ValueProfile actionProfile,
                        @Cached RaiseEncodingErrorNode raiseEncodingErrorNode,
                        @Cached PRaiseNode raiseNode) {
            try {
                // Ignore and replace are handled by Java Charset
                switch (actionProfile.profile(errorAction)) {
                    case STRICT:
                        break;
                    case BACKSLASHREPLACE:
                        if (backslashreplace(encoder)) {
                            return;
                        }
                        break;
                    case SURROGATEPASS:
                        if (surrogatepass(encoder)) {
                            return;
                        }
                        break;
                    default:
                        raiseNode.raise(LookupError, ErrorMessages.UNKNOWN_ERROR_HANDLER, errorAction);
                }
            } catch (OutOfMemoryError e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raiseNode.raise(MemoryError);
            }
            throw raiseEncodingErrorNode.execute(encoder, inputObject);
        }

        @TruffleBoundary
        private static boolean backslashreplace(TruffleEncoder encoder) {
            String p = new String(encoder.getInputChars(encoder.getErrorLenght()));
            StringBuilder sb = new StringBuilder();
            byte[] buf = new byte[10];
            for (int i = 0; i < p.length();) {
                int ch = p.codePointAt(i);
                int len;
                if (ch < 0x100) {
                    BytesUtils.byteEscape(ch, 0, buf);
                    len = 4;
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
                String p = new String(encoder.getInputChars(encoder.getErrorLenght()));
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
                encoder.replace(encoder.getErrorLenght(), replacement, 0, outp);
                return true;
            }
            return false;
        }

        public static HandleEncodingErrorNode create() {
            return CodecsModuleBuiltinsFactory.HandleEncodingErrorNodeGen.create();
        }
    }

    @GenerateUncached
    public abstract static class RaiseDecodingErrorNode extends Node {
        public abstract RuntimeException execute(TruffleDecoder decoder, Object inputObject);

        @Specialization
        public RuntimeException doRaise(TruffleDecoder decoder, Object inputObject,
                        @Cached CallNode callNode,
                        @Cached PRaiseNode raiseNode,
                        @CachedLanguage PythonLanguage pythonLanguage) {
            int start = decoder.getInputPosition();
            int end = start + decoder.getErrorLenght();
            Object exception = callNode.execute(UnicodeDecodeError, decoder.getEncodingName(), inputObject, start, end, decoder.getErrorReason());
            if (exception instanceof PBaseException) {
                throw raiseNode.raiseExceptionObject((PBaseException) exception, pythonLanguage);
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
        void doStrict(TruffleDecoder decoder, String errorAction, Object inputObject,
                        @Cached("createIdentityProfile()") ValueProfile actionProfile,
                        @Cached RaiseDecodingErrorNode raiseDecodingErrorNode,
                        @Cached PRaiseNode raiseNode) {
            // Ignore and replace are handled by Java Charset
            switch (actionProfile.profile(errorAction)) {
                case STRICT:
                    break;
                case BACKSLASHREPLACE:
                    if (backslashreplace(decoder)) {
                        return;
                    }
                    break;
                case SURROGATEPASS:
                    if (surrogatepass(decoder)) {
                        return;
                    }
                    break;
                default:
                    raiseNode.raise(LookupError, ErrorMessages.UNKNOWN_ERROR_HANDLER, errorAction);
            }
            throw raiseDecodingErrorNode.execute(decoder, inputObject);
        }

        @TruffleBoundary
        private static boolean backslashreplace(TruffleDecoder decoder) {
            byte[] p = decoder.getInputBytes(decoder.getErrorLenght());
            char[] replacement = new char[p.length * 4];
            int outp = 0;
            byte[] buf = new byte[4];
            for (int i = 0; i < p.length; i++) {
                BytesUtils.byteEscape(p[i], 0, buf);
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

        public static HandleDecodingErrorNode create() {
            return CodecsModuleBuiltinsFactory.HandleDecodingErrorNodeGen.create();
        }
    }

    abstract static class EncodeBaseNode extends PythonBuiltinNode {

        protected static CodingErrorAction convertCodingErrorAction(String errors) {
            CodingErrorAction errorAction;
            switch (errors) {
                // TODO: see [GR-10256] to implement the correct handling mechanics
                case IGNORE:
                    errorAction = CodingErrorAction.IGNORE;
                    break;
                case REPLACE:
                case SURROGATEESCAPE:
                case NAMEREPLACE:
                case XMLCHARREFREPLACE:
                    errorAction = CodingErrorAction.REPLACE;
                    break;
                case STRICT:
                case BACKSLASHREPLACE:
                case SURROGATEPASS:
                default:
                    // Everything else will be handled by our Handle nodes
                    errorAction = CodingErrorAction.REPORT;
                    break;
            }
            return errorAction;
        }
    }

    // _codecs.encode(obj, encoding='utf-8', errors='strict')
    @Builtin(name = "__truffle_encode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "encoding", "errors"})
    @GenerateNodeFactory
    public abstract static class CodecsEncodeNode extends EncodeBaseNode {
        @Child private HandleEncodingErrorNode handleEncodingErrorNode;

        @Specialization(guards = "isString(str)")
        Object encode(Object str, @SuppressWarnings("unused") PNone encoding, @SuppressWarnings("unused") PNone errors,
                        @Shared("castStr") @Cached CastToJavaStringNode castStr) {
            return encodeString(str, cast(castStr, str), "utf-8", STRICT);
        }

        @Specialization(guards = {"isString(str)", "isString(encoding)"})
        Object encode(Object str, Object encoding, @SuppressWarnings("unused") PNone errors,
                        @Shared("castStr") @Cached CastToJavaStringNode castStr,
                        @Shared("castEncoding") @Cached CastToJavaStringNode castEncoding) {
            return encodeString(str, cast(castStr, str), cast(castEncoding, encoding), STRICT);
        }

        @Specialization(guards = {"isString(str)", "isString(errors)"})
        Object encode(Object str, @SuppressWarnings("unused") PNone encoding, Object errors,
                        @Shared("castStr") @Cached CastToJavaStringNode castStr,
                        @Shared("castErrors") @Cached CastToJavaStringNode castErrors) {
            return encodeString(str, cast(castStr, str), "utf-8", cast(castErrors, errors));
        }

        @Specialization(guards = {"isString(str)", "isString(encoding)", "isString(errors)"})
        Object encode(Object str, Object encoding, Object errors,
                        @Shared("castStr") @Cached CastToJavaStringNode castStr,
                        @Shared("castEncoding") @Cached CastToJavaStringNode castEncoding,
                        @Shared("castErrors") @Cached CastToJavaStringNode castErrors) {
            return encodeString(str, cast(castStr, str), cast(castEncoding, encoding), cast(castErrors, errors));
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

        private Object encodeString(Object self, String input, String encoding, String errors) {
            CodingErrorAction errorAction = convertCodingErrorAction(errors);
            Charset charset = CharsetMapping.getCharset(encoding);
            if (charset == null) {
                throw raise(LookupError, ErrorMessages.UNKNOWN_ENCODING, encoding);
            }
            TruffleEncoder encoder;
            try {
                encoder = new TruffleEncoder(CharsetMapping.normalize(encoding), charset, input, errorAction);
                while (!encoder.encodingStep()) {
                    handleEncodingError(encoder, errors, self);
                }
            } catch (OutOfMemoryError e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raise(MemoryError);
            }
            PBytes bytes = factory().createBytes(encoder.getBytes());
            return factory().createTuple(new Object[]{bytes, input.length()});
        }

        private void handleEncodingError(TruffleEncoder encoder, String errorAction, Object input) {
            if (handleEncodingErrorNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                handleEncodingErrorNode = insert(HandleEncodingErrorNode.create());
            }
            handleEncodingErrorNode.execute(encoder, errorAction, input);
        }
    }

    // _codecs.decode(obj, encoding='utf-8', errors='strict', final=False)
    @Builtin(name = "__truffle_decode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "encoding", "errors", "final"})
    @GenerateNodeFactory
    abstract static class CodecsDecodeNode extends EncodeBaseNode {
        @Child private GetInternalByteArrayNode toByteArrayNode;
        @Child private CastToJavaStringNode castEncodingToStringNode;
        @Child private CoerceToBooleanNode castToBooleanNode;
        @Child private HandleDecodingErrorNode handleDecodingErrorNode;

        @Specialization
        Object decode(VirtualFrame frame, PBytesLike bytes, @SuppressWarnings("unused") PNone encoding, @SuppressWarnings("unused") PNone errors, Object finalData) {
            return decodeBytes(bytes, "utf-8", STRICT, castToBoolean(frame, finalData));
        }

        @Specialization(guards = {"isString(encoding)"})
        Object decode(VirtualFrame frame, PBytesLike bytes, Object encoding, @SuppressWarnings("unused") PNone errors, Object finalData) {
            return decodeBytes(bytes, castToString(encoding), STRICT, castToBoolean(frame, finalData));
        }

        @Specialization(guards = {"isString(errors)"})
        Object decode(VirtualFrame frame, PBytesLike bytes, @SuppressWarnings("unused") PNone encoding, Object errors, Object finalData) {
            return decodeBytes(bytes, "utf-8", castToString(errors), castToBoolean(frame, finalData));
        }

        @Specialization(guards = {"isString(encoding)", "isString(errors)"})
        Object decode(VirtualFrame frame, PBytesLike bytes, Object encoding, Object errors, Object finalData) {
            return decodeBytes(bytes, castToString(encoding), castToString(errors), castToBoolean(frame, finalData));
        }

        @Fallback
        Object decode(Object bytes, @SuppressWarnings("unused") Object encoding, @SuppressWarnings("unused") Object errors, @SuppressWarnings("unused") Object finalData) {
            throw raise(TypeError, ErrorMessages.BYTESLIKE_OBJ_REQUIRED, bytes);
        }

        Object decodeBytes(PBytesLike input, String encoding, String errors, boolean finalData) {
            byte[] bytes = getBytes(input);
            CodingErrorAction errorAction = convertCodingErrorAction(errors);
            Charset charset = CharsetMapping.getCharset(encoding);
            if (charset == null) {
                throw raise(LookupError, ErrorMessages.UNKNOWN_ENCODING, encoding);
            }
            TruffleDecoder decoder;
            try {
                decoder = new TruffleDecoder(CharsetMapping.normalize(encoding), charset, bytes, errorAction);
                while (!decoder.decodingStep(finalData)) {
                    handleDecodingError(decoder, errors, input);
                }
            } catch (OutOfMemoryError e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raise(MemoryError);
            }
            return factory().createTuple(new Object[]{decoder.getString(), decoder.getInputPosition()});
        }

        private byte[] getBytes(PBytesLike bytesLike) {
            if (toByteArrayNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toByteArrayNode = insert(GetInternalByteArrayNodeGen.create());
            }
            return toByteArrayNode.execute(bytesLike.getSequenceStorage());
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

        private void handleDecodingError(TruffleDecoder encoder, String errorAction, Object input) {
            if (handleDecodingErrorNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                handleDecodingErrorNode = insert(HandleDecodingErrorNode.create());
            }
            handleDecodingErrorNode.execute(encoder, errorAction, input);
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
        public int getErrorLenght() {
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
        public TruffleDecoder(String encodingName, Charset charset, byte[] input, CodingErrorAction errorAction) {
            this.encodingName = encodingName;
            this.inputBuffer = ByteBuffer.wrap(input);
            this.decoder = charset.newDecoder().onMalformedInput(errorAction).onUnmappableCharacter(errorAction);
            this.outputBuffer = CharBuffer.allocate((int) (input.length * decoder.averageCharsPerByte()));
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
        public int getErrorLenght() {
            return coderResult.length();
        }

        public void replace(int skipInput, char[] chars) {
            replace(skipInput, chars, 0, chars.length);
        }

        @TruffleBoundary
        public void replace(int skipInput, char[] chars, int offset, int lenght) {
            while (outputBuffer.remaining() < chars.length) {
                grow();
            }
            outputBuffer.put(chars, offset, lenght);
            inputBuffer.position(inputBuffer.position() + skipInput);
        }

        public String getEncodingName() {
            return encodingName;
        }
    }
}
