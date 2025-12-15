/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.NotImplementedError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.RuntimeWarning;
import static com.oracle.graal.python.builtins.objects.bytes.BytesUtils.HEXDIGITS;
import static com.oracle.graal.python.builtins.objects.bytes.BytesUtils.digitValue;
import static com.oracle.graal.python.nodes.BuiltinNames.J_ENCODE;
import static com.oracle.graal.python.nodes.BuiltinNames.J__CODECS;
import static com.oracle.graal.python.nodes.BuiltinNames.T_ASCII;
import static com.oracle.graal.python.nodes.BuiltinNames.T_CP437;
import static com.oracle.graal.python.nodes.BuiltinNames.T__CODECS_TRUFFLE;
import static com.oracle.graal.python.nodes.ErrorMessages.ARG_MUST_BE_CALLABLE;
import static com.oracle.graal.python.nodes.ErrorMessages.BYTESLIKE_OBJ_REQUIRED;
import static com.oracle.graal.python.nodes.ErrorMessages.CODEC_SEARCH_MUST_RETURN_4;
import static com.oracle.graal.python.nodes.ErrorMessages.ENCODING_ERROR_WITH_CODE;
import static com.oracle.graal.python.nodes.ErrorMessages.INVALID_ESCAPE_AT;
import static com.oracle.graal.python.nodes.ErrorMessages.S_MUST_RETURN_TUPLE;
import static com.oracle.graal.python.nodes.ErrorMessages.UNKNOWN_ENCODING;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J_DECODE;
import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_STRING;
import static com.oracle.graal.python.nodes.StringLiterals.T_IGNORE;
import static com.oracle.graal.python.nodes.StringLiterals.T_NAMEREPLACE;
import static com.oracle.graal.python.nodes.StringLiterals.T_REPLACE;
import static com.oracle.graal.python.nodes.StringLiterals.T_STRICT;
import static com.oracle.graal.python.nodes.StringLiterals.T_UTF8;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.LookupError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.MemoryError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.WarningsModuleBuiltins.WarnNode;
import com.oracle.graal.python.builtins.modules.codecs.CharmapNodes.PyUnicodeBuildEncodingMapNode;
import com.oracle.graal.python.builtins.modules.codecs.CharmapNodes.PyUnicodeDecodeCharmapNode;
import com.oracle.graal.python.builtins.modules.codecs.CharmapNodes.PyUnicodeEncodeCharmapNode;
import com.oracle.graal.python.builtins.modules.codecs.CodecsRegistry;
import com.oracle.graal.python.builtins.modules.codecs.CodecsRegistry.PyCodecLookupErrorNode;
import com.oracle.graal.python.builtins.modules.codecs.CodecsRegistry.PyCodecRegisterErrorNode;
import com.oracle.graal.python.builtins.modules.codecs.ErrorHandlers;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.builtins.objects.bytes.ByteArrayBuffer;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetInternalByteArrayNode;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyCallableCheckNode;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.lib.PyObjectTypeCheck;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.ExecutionContext.BoundaryCallContext;
import com.oracle.graal.python.runtime.IndirectCallData.BoundaryCallData;
import com.oracle.graal.python.runtime.IndirectCallData.InteropCallData;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.runtime.sequence.storage.ObjectSequenceStorage;
import com.oracle.graal.python.util.CharsetMapping;
import com.oracle.graal.python.util.CharsetMapping.NormalizeEncodingNameNode;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(defineModule = J__CODECS)
public final class CodecsModuleBuiltins extends PythonBuiltins {

    public static final TruffleString T_UTF_7 = tsLiteral("utf-7");
    public static final TruffleString T_UTF_16 = tsLiteral("utf-16");
    public static final TruffleString T_UTF_16_LE = tsLiteral("utf-16-le");
    public static final TruffleString T_UTF_16_BE = tsLiteral("utf-16-be");
    public static final TruffleString T_UTF_32 = tsLiteral("utf-32");
    public static final TruffleString T_UTF_32_LE = tsLiteral("utf-32-le");
    public static final TruffleString T_UTF_32_BE = tsLiteral("utf-32-be");
    public static final TruffleString T_RAW_UNICODE_ESCAPE = tsLiteral("raw_unicode_escape");
    public static final TruffleString T_UNICODE_ESCAPE = tsLiteral("unicode_escape");
    public static final TruffleString T_LATIN_1 = tsLiteral("latin_1");

    public static CodingErrorAction convertCodingErrorAction(TruffleString errors, TruffleString.EqualNode equalNode) {
        // TODO: see [GR-10256] to implement the correct handling mechanics
        // TODO: replace CodingErrorAction with TruffleString api [GR-38105]
        if (equalNode.execute(T_IGNORE, errors, TS_ENCODING)) {
            return CodingErrorAction.IGNORE;
        }
        if (equalNode.execute(T_REPLACE, errors, TS_ENCODING) || equalNode.execute(T_NAMEREPLACE, errors, TS_ENCODING)) {
            return CodingErrorAction.REPLACE;
        }
        return CodingErrorAction.REPORT;
    }

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return CodecsModuleBuiltinsFactory.getFactories();
    }

    @GenerateUncached
    @GenerateInline(false) // footprint reduction 48 -> 30
    public abstract static class CodecsEncodeToJavaBytesNode extends Node {
        public abstract byte[] execute(Frame frame, Object self, TruffleString encoding, TruffleString errors);

        @Specialization
        byte[] encode(VirtualFrame frame, Object self, TruffleString encoding, TruffleString errors,
                        @Bind Node inliningTarget,
                        @Cached CastToTruffleStringNode castTruffleStr,
                        @Cached TruffleString.ToJavaStringNode toJavaStringNode,
                        @Cached TruffleString.EqualNode equalNode,
                        @Cached ErrorHandlers.CallEncodingErrorHandlerNode errorHandler,
                        @CachedLibrary(limit = "3") PythonBufferAcquireLibrary acquireLib,
                        @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                        @Cached CastToJavaStringNode castToJavaStringNode,
                        @Cached PRaiseNode raiseNode,
                        @Cached NormalizeEncodingNameNode normalizeEncodingNameNode) {
            TruffleString input = castTruffleStr.castKnownString(inliningTarget, self);
            String inputStr = toJavaStringNode.execute(input);
            CodingErrorAction errorAction = convertCodingErrorAction(errors, equalNode);
            TruffleString normalizedEncoding = normalizeEncodingNameNode.execute(inliningTarget, encoding);
            Charset charset = CharsetMapping.getCharsetNormalized(normalizedEncoding);
            if (charset == null) {
                throw raiseNode.raise(inliningTarget, LookupError, ErrorMessages.UNKNOWN_ENCODING, encoding);
            }
            TruffleEncoder encoder;
            ErrorHandlers.ErrorHandlerCache errorHandlerCache = new ErrorHandlers.ErrorHandlerCache();
            try {
                encoder = new TruffleEncoder(charset, inputStr, errorAction);
                while (!encoder.encodingStep()) {
                    int pos = encoder.getInputPosition();
                    ErrorHandlers.EncodingErrorHandlerResult result = errorHandler.execute(frame, inliningTarget, errorHandlerCache, errors, encoding, input,
                                    pos, pos + encoder.getErrorLength(), encoder.getErrorReason());
                    encoder.setInputPosition(result.newPos);
                    if (result.isUnicode) {
                        String replacementStr = castToJavaStringNode.execute(result.replacement);
                        encoder.replace(replacementStr, charset);
                    } else {
                        Object buffer = acquireLib.acquireReadonly(result.replacement);
                        try {
                            encoder.replace(bufferLib.getInternalOrCopiedByteArray(buffer), 0, bufferLib.getBufferLength(buffer));
                        } finally {
                            bufferLib.release(buffer);
                        }
                    }
                }
            } catch (OutOfMemoryError e) {
                throw raiseNode.raise(inliningTarget, MemoryError);
            }
            return encoder.getBytes();
        }
    }

    // _codecs.encode(obj, encoding='utf-8', errors='strict')
    @Builtin(name = "__truffle_encode__", minNumOfPositionalArgs = 1, parameterNames = {"obj", "encoding", "errors"})
    @ArgumentClinic(name = "encoding", conversion = ArgumentClinic.ClinicConversion.TString, defaultValue = "T_UTF8", useDefaultForNone = true)
    @ArgumentClinic(name = "errors", conversion = ArgumentClinic.ClinicConversion.TString, defaultValue = "T_STRICT", useDefaultForNone = true)
    @GenerateNodeFactory
    public abstract static class CodecsEncodeNode extends PythonTernaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return CodecsModuleBuiltinsClinicProviders.CodecsEncodeNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = {"isString(self)"})
        static Object encode(VirtualFrame frame, Object self, TruffleString encoding, TruffleString errors,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached CastToTruffleStringNode castStr,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached CodecsEncodeToJavaBytesNode encode) {
            TruffleString input = castStr.execute(inliningTarget, self);
            PBytes bytes = PFactory.createBytes(language, encode.execute(frame, self, encoding, errors));
            return PFactory.createTuple(language, new Object[]{bytes, codePointLengthNode.execute(input, TS_ENCODING)});
        }

        @Fallback
        static Object encode(Object str, @SuppressWarnings("unused") Object encoding, @SuppressWarnings("unused") Object errors,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.CANT_CONVERT_TO_STR_IMPLICITLY, str);
        }
    }

    // _codecs.decode(obj, encoding='utf-8', errors='strict', final=False)
    @Builtin(name = "__truffle_decode__", minNumOfPositionalArgs = 1, parameterNames = {"obj", "encoding", "errors", "final"})
    @ArgumentClinic(name = "encoding", conversion = ArgumentClinic.ClinicConversion.TString, defaultValue = "T_UTF8", useDefaultForNone = true)
    @ArgumentClinic(name = "errors", conversion = ArgumentClinic.ClinicConversion.TString, defaultValue = "T_STRICT", useDefaultForNone = true)
    @ArgumentClinic(name = "final", conversion = ArgumentClinic.ClinicConversion.Boolean, defaultValue = "false", useDefaultForNone = true)
    @GenerateNodeFactory
    public abstract static class CodecsDecodeNode extends PythonQuaternaryClinicBuiltinNode {

        public final Object call(VirtualFrame frame, Object input, Object encoding, Object errors, Object finalData) {
            return execute(frame, input, encoding, errors, finalData);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return CodecsModuleBuiltinsClinicProviders.CodecsDecodeNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(limit = "3")
        static Object decode(VirtualFrame frame, Object input, TruffleString encoding, TruffleString errors, boolean finalData,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached("createFor($node)") InteropCallData callData,
                        @CachedLibrary("input") PythonBufferAcquireLibrary acquireLib,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib,
                        @Cached TruffleString.EqualNode equalNode,
                        @Cached NormalizeEncodingNameNode normalizeEncodingNameNode,
                        @Cached ErrorHandlers.CallDecodingErrorHandlerNode callDecodingErrorHandlerNode,
                        @Cached TruffleString.ToJavaStringNode toJavaStringNode,
                        @Cached InlinedBranchProfile inputReplaced,
                        @Cached PRaiseNode raiseNode) {
            Object buffer = acquireLib.acquireReadonly(input, frame, callData);
            try {
                int len = bufferLib.getBufferLength(buffer);
                byte[] bytes = bufferLib.getInternalOrCopiedByteArray(buffer);
                CodingErrorAction errorAction = convertCodingErrorAction(errors, equalNode);
                TruffleString normalizedEncoding = normalizeEncodingNameNode.execute(inliningTarget, encoding);
                Charset charset = CharsetMapping.getCharsetForDecodingNormalized(normalizedEncoding, bytes, len);
                if (charset == null) {
                    throw raiseNode.raise(inliningTarget, LookupError, ErrorMessages.UNKNOWN_ENCODING, encoding);
                }
                ErrorHandlers.ErrorHandlerCache handlerCache = new ErrorHandlers.ErrorHandlerCache();
                TruffleDecoder decoder;
                try {
                    decoder = new TruffleDecoder(normalizedEncoding, charset, bytes, len, errorAction);
                    while (!decoder.decodingStep(finalData)) {
                        int pos = decoder.getInputPosition();
                        ErrorHandlers.DecodingErrorHandlerResult result = callDecodingErrorHandlerNode.execute(frame, inliningTarget, handlerCache, errors, encoding, input,
                                        pos, pos + decoder.getErrorLength(), decoder.getErrorReason());
                        toJavaStringNode.execute(result.str);
                        if (result.newSrcObj != input) {
                            inputReplaced.enter(inliningTarget);
                            bufferLib.release(buffer);
                            buffer = acquireLib.acquireReadonly(result.newSrcObj, frame, callData);
                            decoder.setNewInput(bufferLib.getInternalOrCopiedByteArray(result.newSrcObj), 0, bufferLib.getBufferLength(result.newSrcObj), result.newPos);
                        } else {
                            decoder.setInputPosition(result.newPos);
                        }
                        decoder.replace(toJavaStringNode.execute(result.str));
                    }
                } catch (OutOfMemoryError e) {
                    throw raiseNode.raise(inliningTarget, MemoryError);
                }
                return PFactory.createTuple(language, new Object[]{decoder.getString(), decoder.getInputPosition()});
            } finally {
                bufferLib.release(buffer, frame, callData);
            }
        }
    }

    @Builtin(name = "escape_decode", minNumOfPositionalArgs = 1, parameterNames = {"data", "errors"})
    @ArgumentClinic(name = "data", conversion = ArgumentClinic.ClinicConversion.ReadableBuffer)
    @ArgumentClinic(name = "errors", conversion = ArgumentClinic.ClinicConversion.TString, defaultValue = "T_STRICT", useDefaultForNone = true)
    @GenerateNodeFactory
    public abstract static class CodecsEscapeDecodeNode extends PythonBinaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return CodecsModuleBuiltinsClinicProviders.CodecsEscapeDecodeNodeClinicProviderGen.INSTANCE;
        }

        public abstract Object execute(VirtualFrame frame, byte[] bytes, TruffleString errors);

        @Specialization
        Object decodeByteArray(byte[] bytes, TruffleString errors,
                        @Bind PythonLanguage language) {
            return decodeBytes(bytes, bytes.length, errors, language);
        }

        @Specialization(limit = "3")
        Object decode(VirtualFrame frame, Object buffer, TruffleString errors,
                        @Bind PythonLanguage language,
                        @Cached("createFor($node)") InteropCallData callData,
                        @CachedLibrary("buffer") PythonBufferAccessLibrary bufferLib) {
            try {
                int len = bufferLib.getBufferLength(buffer);
                return decodeBytes(bufferLib.getInternalOrCopiedByteArray(buffer), len, errors, language);
            } finally {
                bufferLib.release(buffer, frame, callData);
            }
        }

        private Object decodeBytes(byte[] bytes, int len, TruffleString errors, PythonLanguage language) {
            ByteArrayBuffer result = doDecode(bytes, len, errors);
            return PFactory.createTuple(language, new Object[]{PFactory.createBytes(language, result.getInternalBytes(), result.getLength()), len});
        }

        @TruffleBoundary
        private ByteArrayBuffer doDecode(byte[] bytes, int bytesLen, TruffleString errors) {
            ByteArrayBuffer buffer = new ByteArrayBuffer(bytesLen);
            for (int i = 0; i < bytesLen; i++) {
                char chr = (char) bytes[i];
                if (chr != '\\') {
                    buffer.append(chr);
                    continue;
                }

                i++;
                if (i >= bytesLen) {
                    throw PRaiseNode.raiseStatic(this, ValueError, ErrorMessages.TRAILING_S_IN_STR, "\\");
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
                        if (T_STRICT.equalsUncached(errors, TS_ENCODING)) {
                            throw PRaiseNode.raiseStatic(this, ValueError, INVALID_ESCAPE_AT, "\\x", i - 2);
                        }
                        if (T_REPLACE.equalsUncached(errors, TS_ENCODING)) {
                            buffer.append('?');
                        } else if (T_IGNORE.equalsUncached(errors, TS_ENCODING)) {
                            // do nothing
                        } else {
                            throw PRaiseNode.raiseStatic(this, ValueError, ENCODING_ERROR_WITH_CODE, errors);
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
    @ArgumentClinic(name = "errors", conversion = ArgumentClinic.ClinicConversion.TString, defaultValue = "T_STRICT", useDefaultForNone = true)
    @GenerateNodeFactory
    public abstract static class CodecsEscapeEncodeNode extends PythonBinaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return CodecsModuleBuiltinsClinicProviders.CodecsEscapeEncodeNodeClinicProviderGen.INSTANCE;
        }

        @TruffleBoundary
        @Specialization
        Object encode(PBytes data, @SuppressWarnings("unused") TruffleString errors,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached GetInternalByteArrayNode getInternalByteArrayNode) {
            byte[] bytes = getInternalByteArrayNode.execute(inliningTarget, data.getSequenceStorage());
            int size = data.getSequenceStorage().length();
            ByteArrayBuffer buffer = new ByteArrayBuffer();
            char c;
            for (int i = 0; i < size; i++) {
                byte aByte = bytes[i];
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

            return PFactory.createTuple(language, new Object[]{
                            PFactory.createBytes(language, buffer.getByteArray()),
                            size
            });
        }

        @Fallback
        static Object encode(Object data, @SuppressWarnings("unused") Object errors,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, BYTESLIKE_OBJ_REQUIRED, data);
        }
    }

    @Builtin(name = "__truffle_lookup__", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class CodecsLookupNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object lookup(TruffleString encoding,
                        @Bind Node inliningTarget,
                        @Cached NormalizeEncodingNameNode normalizeEncodingNameNode) {
            if (hasTruffleEncodingNormalized(normalizeEncodingNameNode.execute(inliningTarget, encoding))) {
                return true;
            } else {
                return PNone.NONE;
            }
        }
    }

    private static boolean hasTruffleEncodingNormalized(TruffleString normalizedEncoding) {
        return CharsetMapping.getCharsetNormalized(normalizedEncoding) != null;
    }

    @Builtin(name = "lookup", minNumOfPositionalArgs = 1, parameterNames = {"encoding"})
    @ArgumentClinic(name = "encoding", conversion = ArgumentClinic.ClinicConversion.TString)
    @GenerateNodeFactory
    abstract static class LookupNode extends PythonUnaryClinicBuiltinNode {
        @Specialization
        static PTuple lookup(VirtualFrame frame, TruffleString encoding,
                        @Bind Node inliningTarget,
                        @Cached PyCodecLookupNode lookup) {
            return lookup.execute(frame, inliningTarget, encoding);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return CodecsModuleBuiltinsClinicProviders.LookupNodeClinicProviderGen.INSTANCE;
        }
    }

    @GenerateUncached
    @GenerateInline // always used eagerly, reconsider if used "lazily"
    @GenerateCached(false)
    public abstract static class PyCodecLookupNode extends PNodeWithContext {
        public abstract PTuple execute(Frame frame, Node inliningTarget, TruffleString encoding);

        @Specialization
        static PTuple lookup(VirtualFrame frame, Node inliningTarget, TruffleString encoding,
                        @Cached CodecsRegistry.EnsureRegistryInitializedNode ensureRegistryInitializedNode,
                        @Cached(inline = false) CallUnaryMethodNode callNode,
                        @Cached PyObjectTypeCheck typeCheck,
                        @Cached(inline = false) PyObjectSizeNode sizeNode,
                        @Cached InlinedConditionProfile hasSearchPathProfile,
                        @Cached InlinedConditionProfile hasTruffleEncodingProfile,
                        @Cached InlinedConditionProfile isTupleProfile,
                        @Cached NormalizeEncodingNameNode normalizeEncodingNameNode,
                        @Cached("createFor($node)") BoundaryCallData boundaryCallData,
                        @Cached PRaiseNode raiseNode) {
            TruffleString normalizedEncoding = normalizeEncodingNameNode.execute(inliningTarget, encoding);
            PythonContext context = PythonContext.get(inliningTarget);
            ensureRegistryInitializedNode.execute(frame, inliningTarget, context);
            PTuple result = getSearchPath(context, normalizedEncoding);
            if (hasSearchPathProfile.profile(inliningTarget, result != null)) {
                return result;
            }
            if (hasTruffleEncodingProfile.profile(inliningTarget, hasTruffleEncodingNormalized(normalizedEncoding))) {
                PythonModule codecs = context.lookupBuiltinModule(T__CODECS_TRUFFLE);
                Object savedState = BoundaryCallContext.enter(frame, boundaryCallData);
                try {
                    result = CodecsTruffleModuleBuiltins.codecsInfo(codecs, encoding, context);
                } finally {
                    BoundaryCallContext.exit(frame, boundaryCallData, savedState);
                }
            } else {
                Object[] searchPaths = getSearchPaths(context);
                for (Object func : searchPaths) {
                    Object obj = callNode.executeObject(func, normalizedEncoding);
                    if (obj != PNone.NONE) {
                        if (isTupleProfile.profile(inliningTarget, !isTupleInstanceCheck(frame, inliningTarget, obj, 4, typeCheck, sizeNode))) {
                            throw raiseNode.raise(inliningTarget, TypeError, CODEC_SEARCH_MUST_RETURN_4);
                        }
                        result = (PTuple) obj;
                        break;
                    }
                }
            }
            if (result != null) {
                putSearchPath(context, normalizedEncoding, result);
                return result;
            }
            throw raiseNode.raise(inliningTarget, LookupError, UNKNOWN_ENCODING, encoding);
        }

        @TruffleBoundary
        private static void putSearchPath(PythonContext ctx, TruffleString key, PTuple value) {
            ctx.getCodecSearchCache().put(key, value);
        }

        @TruffleBoundary
        private static PTuple getSearchPath(PythonContext ctx, TruffleString key) {
            return ctx.getCodecSearchCache().get(key);
        }

        @TruffleBoundary
        private static Object[] getSearchPaths(PythonContext ctx) {
            List<Object> l = ctx.getCodecSearchPath();
            return ctx.getCodecSearchPath().toArray(new Object[l.size()]);
        }
    }

    private static boolean isTupleInstanceCheck(VirtualFrame frame, Node inliningTarget, Object result, int len, PyObjectTypeCheck typeCheck, PyObjectSizeNode sizeNode) throws PException {
        return typeCheck.execute(inliningTarget, result, PythonBuiltinClassType.PTuple) && sizeNode.execute(frame, inliningTarget, result) == len;
    }

    @Builtin(name = "register", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class RegisterNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object lookup(VirtualFrame frame, Object searchFunction,
                        @Bind Node inliningTarget,
                        @Cached CodecsRegistry.EnsureRegistryInitializedNode ensureRegistryInitializedNode,
                        @Cached PyCallableCheckNode callableCheckNode,
                        @Cached PRaiseNode raiseNode) {
            if (callableCheckNode.execute(inliningTarget, searchFunction)) {
                PythonContext context = PythonContext.get(inliningTarget);
                ensureRegistryInitializedNode.execute(frame, inliningTarget, context);
                add(context, searchFunction);
                return PNone.NONE;
            } else {
                throw raiseNode.raise(inliningTarget, TypeError, ARG_MUST_BE_CALLABLE);
            }
        }

        @TruffleBoundary
        private static void add(PythonContext context, Object searchFunction) {
            context.getCodecSearchPath().add(searchFunction);
        }
    }

    @Builtin(name = "unregister", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class UnregisterNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object unregister(Object searchFunction) {
            PythonContext context = PythonContext.get(this);
            remove(context, searchFunction);
            return PNone.NONE;
        }

        @TruffleBoundary
        private static void remove(PythonContext context, Object searchFunction) {
            context.getCodecSearchPath().remove(searchFunction);
            context.getCodecSearchCache().clear();
        }
    }

    @Builtin(name = "_forget_codec", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ForgetCodecNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object forget(VirtualFrame frame, PBytesLike encoding,
                        @Cached AsciiDecodeNode asciiDecodeNode) {
            PTuple decodeResult = (PTuple) asciiDecodeNode.execute(frame, encoding, PNone.NO_VALUE);
            ObjectSequenceStorage resultStorage = (ObjectSequenceStorage) decodeResult.getSequenceStorage();
            forget((TruffleString) resultStorage.getObjectItemNormalized(0));
            return PNone.NONE;
        }

        @Specialization
        @TruffleBoundary
        Object forget(TruffleString encoding) {
            PythonContext.get(this).getCodecSearchCache().remove(encoding);
            return PNone.NONE;
        }
    }

    @Builtin(name = "register_error", minNumOfPositionalArgs = 2, parameterNames = {"name", "handler"})
    @ArgumentClinic(name = "name", conversion = ArgumentClinic.ClinicConversion.TString)
    @GenerateNodeFactory
    abstract static class RegisterErrorNode extends PythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return CodecsModuleBuiltinsClinicProviders.RegisterErrorNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        Object register(VirtualFrame frame, TruffleString name, Object handler,
                        @Bind Node inliningTarget,
                        @Cached PyCodecRegisterErrorNode registerErrorNode) {
            registerErrorNode.execute(frame, inliningTarget, name, handler);
            return PNone.NONE;
        }
    }

    @Builtin(name = "lookup_error", minNumOfPositionalArgs = 1, parameterNames = {"name"})
    @ArgumentClinic(name = "name", conversion = ArgumentClinic.ClinicConversion.TString)
    @GenerateNodeFactory
    abstract static class LookupErrorNode extends PythonUnaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return CodecsModuleBuiltinsClinicProviders.LookupErrorNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        Object lookup(VirtualFrame frame, TruffleString name,
                        @Bind Node inliningTarget,
                        @Cached PyCodecLookupErrorNode errorNode) {
            return errorNode.execute(frame, inliningTarget, name);
        }
    }

    @Builtin(name = J_ENCODE, minNumOfPositionalArgs = 1, parameterNames = {"obj", "encoding", "errors"})
    @ArgumentClinic(name = "encoding", conversion = ArgumentClinic.ClinicConversion.TString, defaultValue = "T_UTF8")
    @ArgumentClinic(name = "errors", conversion = ArgumentClinic.ClinicConversion.TString, defaultValue = "T_STRICT")
    @GenerateNodeFactory
    public abstract static class EncodeNode extends PythonTernaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return CodecsModuleBuiltinsClinicProviders.EncodeNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object encode(VirtualFrame frame, Object obj, TruffleString encoding, TruffleString errors,
                        @Bind Node inliningTarget,
                        @Cached SequenceStorageNodes.GetItemNode getItemNode,
                        @Cached SequenceStorageNodes.GetItemNode getResultItemNode,
                        @Cached PyCodecLookupNode lookupNode,
                        @Cached CallBinaryMethodNode callEncoderNode,
                        @Cached PyObjectSizeNode sizeNode,
                        @Cached PyObjectTypeCheck typeCheck,
                        @Cached InlinedConditionProfile isTupleProfile,
                        @Cached PRaiseNode raiseNode) {
            Object encoder = CodecsModuleBuiltins.encoder(frame, inliningTarget, encoding, lookupNode, getItemNode);
            Object result = callEncoderNode.executeObject(encoder, obj, errors);
            if (isTupleProfile.profile(inliningTarget, !isTupleInstanceCheck(frame, inliningTarget, result, 2, typeCheck, sizeNode))) {
                throw raiseNode.raise(inliningTarget, TypeError, S_MUST_RETURN_TUPLE, "encoder");
            }
            return getResultItemNode.execute(((PTuple) result).getSequenceStorage(), 0);
        }
    }

    @Builtin(name = J_DECODE, minNumOfPositionalArgs = 1, parameterNames = {"obj", "encoding", "errors"})
    @ArgumentClinic(name = "encoding", conversion = ArgumentClinic.ClinicConversion.TString, defaultValue = "T_UTF8")
    @ArgumentClinic(name = "errors", conversion = ArgumentClinic.ClinicConversion.TString, defaultValue = "T_STRICT")
    @GenerateNodeFactory
    public abstract static class DecodeNode extends PythonTernaryClinicBuiltinNode {

        public final Object executeWithStrings(VirtualFrame frame, Object obj, TruffleString encoding, TruffleString errors) {
            return executeWithoutClinic(frame, obj, encoding, errors);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return CodecsModuleBuiltinsClinicProviders.DecodeNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object decode(VirtualFrame frame, Object obj, TruffleString encoding, TruffleString errors,
                        @Bind Node inliningTarget,
                        @Cached SequenceStorageNodes.GetItemNode getItemNode,
                        @Cached SequenceStorageNodes.GetItemNode getResultItemNode,
                        @Cached PyCodecLookupNode lookupNode,
                        @Cached CallBinaryMethodNode callEncoderNode,
                        @Cached PyObjectSizeNode sizeNode,
                        @Cached PyObjectTypeCheck typeCheck,
                        @Cached InlinedConditionProfile isTupleProfile,
                        @Cached PRaiseNode raiseNode) {
            Object decoder = CodecsModuleBuiltins.decoder(frame, inliningTarget, encoding, lookupNode, getItemNode);
            Object result = callEncoderNode.executeObject(decoder, obj, errors);
            if (isTupleProfile.profile(inliningTarget, !isTupleInstanceCheck(frame, inliningTarget, result, 2, typeCheck, sizeNode))) {
                throw raiseNode.raise(inliningTarget, TypeError, S_MUST_RETURN_TUPLE, "decoder");
            }
            return getResultItemNode.execute(((PTuple) result).getSequenceStorage(), 0);
        }
    }

    private static Object codecGetItem(VirtualFrame frame, Node inliningTarget, TruffleString encoding, int index, PyCodecLookupNode lookupNode, SequenceStorageNodes.GetItemNode getItemNode) {
        PTuple t = lookupNode.execute(frame, inliningTarget, encoding);
        return getItemNode.execute(t.getSequenceStorage(), index);
    }

    private static Object encoder(VirtualFrame frame, Node inliningTarget, TruffleString encoding, PyCodecLookupNode lookupNode, SequenceStorageNodes.GetItemNode getItemNode) {
        return codecGetItem(frame, inliningTarget, encoding, 0, lookupNode, getItemNode);
    }

    private static Object decoder(VirtualFrame frame, Node inliningTarget, TruffleString encoding, PyCodecLookupNode lookupNode, SequenceStorageNodes.GetItemNode getItemNode) {
        return codecGetItem(frame, inliningTarget, encoding, 1, lookupNode, getItemNode);
    }

    @Builtin(name = "utf_8_encode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors"})
    @GenerateNodeFactory
    abstract static class UTF8EncodeNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object encode(VirtualFrame frame, Object obj, Object errors,
                        @Cached CodecsEncodeNode encode) {
            return encode.execute(frame, obj, T_UTF8, errors);
        }
    }

    @Builtin(name = "utf_8_decode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors", "final"})
    @GenerateNodeFactory
    abstract static class UTF8DecodeNode extends PythonTernaryBuiltinNode {
        @Specialization
        Object encode(VirtualFrame frame, Object obj, Object errors, Object ffinal,
                        @Cached CodecsDecodeNode decode) {
            return decode.execute(frame, obj, T_UTF8, errors, ffinal);
        }
    }

    @Builtin(name = "utf_7_encode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors"})
    @GenerateNodeFactory
    abstract static class UTF7EncodeNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object encode(VirtualFrame frame, Object obj, Object errors,
                        @Cached CodecsEncodeNode encode) {
            return encode.execute(frame, obj, T_UTF_7, errors);
        }
    }

    @Builtin(name = "utf_7_decode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors", "final"})
    @GenerateNodeFactory
    abstract static class UTF7DecodeNode extends PythonTernaryBuiltinNode {
        @Specialization
        Object encode(VirtualFrame frame, Object obj, Object errors, Object ffinal,
                        @Cached CodecsDecodeNode decode) {
            return decode.execute(frame, obj, T_UTF_7, errors, ffinal);
        }
    }

    @Builtin(name = "utf_16_encode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors", "byteorder"})
    @GenerateNodeFactory
    abstract static class UTF16EncodeNode extends PythonTernaryBuiltinNode {
        @Specialization
        Object encode(VirtualFrame frame, Object obj, Object errors, @SuppressWarnings("unused") Object byteorder,
                        @Cached CodecsEncodeNode encode) {
            return encode.execute(frame, obj, T_UTF_16, errors);
        }
    }

    @Builtin(name = "utf_16_decode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors", "final"})
    @GenerateNodeFactory
    abstract static class UTF16DecodeNode extends PythonTernaryBuiltinNode {
        @Specialization
        Object encode(VirtualFrame frame, Object obj, Object errors, Object ffinal,
                        @Cached CodecsDecodeNode decode) {
            return decode.execute(frame, obj, T_UTF_16, errors, ffinal);
        }
    }

    @Builtin(name = "utf_16_le_encode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors"})
    @GenerateNodeFactory
    abstract static class UTF16LEEncodeNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object encode(VirtualFrame frame, Object obj, Object errors,
                        @Cached CodecsEncodeNode encode) {
            return encode.execute(frame, obj, T_UTF_16_LE, errors);
        }
    }

    @Builtin(name = "utf_16_le_decode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors", "final"})
    @GenerateNodeFactory
    abstract static class UTF16LEDecodeNode extends PythonTernaryBuiltinNode {
        @Specialization
        Object encode(VirtualFrame frame, Object obj, Object errors, Object ffinal,
                        @Cached CodecsDecodeNode decode) {
            return decode.execute(frame, obj, T_UTF_16_LE, errors, ffinal);
        }
    }

    @Builtin(name = "utf_16_be_encode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors"})
    @GenerateNodeFactory
    abstract static class UTF16BEEncodeNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object encode(VirtualFrame frame, Object obj, Object errors,
                        @Cached CodecsEncodeNode encode) {
            return encode.execute(frame, obj, T_UTF_16_BE, errors);
        }
    }

    @Builtin(name = "utf_16_be_decode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors", "final"})
    @GenerateNodeFactory
    abstract static class UTF16BEDecodeNode extends PythonTernaryBuiltinNode {
        @Specialization
        Object encode(VirtualFrame frame, Object obj, Object errors, Object ffinal,
                        @Cached CodecsDecodeNode decode) {
            return decode.execute(frame, obj, T_UTF_16_BE, errors, ffinal);
        }
    }

    @Builtin(name = "utf_16_ex_decode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors", "byteorder", "final"})
    @GenerateNodeFactory
    abstract static class UTF16EXDecodeNode extends PythonQuaternaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        static Object encode(VirtualFrame frame, Object obj, Object errors, Object byteorder, Object ffinal,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, NotImplementedError, toTruffleStringUncached("utf_16_ex_decode"));
        }
    }

    @Builtin(name = "utf_32_encode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors", "byteorder"})
    @GenerateNodeFactory
    abstract static class UTF32EncodeNode extends PythonTernaryBuiltinNode {
        @Specialization
        Object encode(VirtualFrame frame, Object obj, Object errors, @SuppressWarnings("unused") Object byteorder,
                        @Cached CodecsEncodeNode encode) {
            return encode.execute(frame, obj, T_UTF_32, errors);
        }
    }

    @Builtin(name = "utf_32_decode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors", "final"})
    @GenerateNodeFactory
    abstract static class UTF32DecodeNode extends PythonTernaryBuiltinNode {
        @Specialization
        Object encode(VirtualFrame frame, Object obj, Object errors, Object ffinal,
                        @Cached CodecsDecodeNode decode) {
            return decode.execute(frame, obj, T_UTF_32, errors, ffinal);
        }
    }

    @Builtin(name = "utf_32_le_encode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors"})
    @GenerateNodeFactory
    abstract static class UTF32LEEncodeNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object encode(VirtualFrame frame, Object obj, Object errors,
                        @Cached CodecsEncodeNode encode) {
            return encode.execute(frame, obj, T_UTF_32_LE, errors);
        }
    }

    @Builtin(name = "utf_32_le_decode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors", "final"})
    @GenerateNodeFactory
    abstract static class UTF32LEDecodeNode extends PythonTernaryBuiltinNode {
        @Specialization
        Object encode(VirtualFrame frame, Object obj, Object errors, Object ffinal,
                        @Cached CodecsDecodeNode decode) {
            return decode.execute(frame, obj, T_UTF_32_LE, errors, ffinal);
        }
    }

    @Builtin(name = "utf_32_be_encode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors"})
    @GenerateNodeFactory
    abstract static class UTF32BEEncodeNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object encode(VirtualFrame frame, Object obj, Object errors,
                        @Cached CodecsEncodeNode encode) {
            return encode.execute(frame, obj, T_UTF_32_BE, errors);
        }
    }

    @Builtin(name = "utf_32_be_decode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors", "final"})
    @GenerateNodeFactory
    abstract static class UTF32BEDecodeNode extends PythonTernaryBuiltinNode {
        @Specialization
        Object encode(VirtualFrame frame, Object obj, Object errors, Object ffinal,
                        @Cached CodecsDecodeNode decode) {
            return decode.execute(frame, obj, T_UTF_32_BE, errors, ffinal);
        }
    }

    @Builtin(name = "utf_32_ex_decode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors", "final", "byteorder"})
    @GenerateNodeFactory
    abstract static class UTF32EXDecodeNode extends PythonQuaternaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        @TruffleBoundary
        Object encode(Object obj, Object errors, Object byteorder, Object ffinal) {
            throw PRaiseNode.raiseStatic(this, NotImplementedError, toTruffleStringUncached("utf_32_ex_decode"));
        }
    }

    @Builtin(name = "unicode_internal_encode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors"})
    @GenerateNodeFactory
    abstract static class UnicodeInternalEncodeNode extends PythonBinaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        @TruffleBoundary
        Object encode(Object obj, Object errors) {
            throw PRaiseNode.raiseStatic(this, NotImplementedError, toTruffleStringUncached("unicode_internal_encode"));
        }
    }

    @Builtin(name = "unicode_internal_decode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors"})
    @GenerateNodeFactory
    abstract static class UnicodeInternalDecodeNode extends PythonBinaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        @TruffleBoundary
        Object encode(Object obj, Object errors) {
            throw PRaiseNode.raiseStatic(this, NotImplementedError, toTruffleStringUncached("unicode_internal_decode"));
        }
    }

    @Builtin(name = "raw_unicode_escape_encode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors"})
    @GenerateNodeFactory
    abstract static class RawUnicodeEscapeEncodeNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object encode(VirtualFrame frame, Object obj, Object errors,
                        @Cached CodecsEncodeNode encode) {
            return encode.execute(frame, obj, T_RAW_UNICODE_ESCAPE, errors);
        }
    }

    @Builtin(name = "raw_unicode_escape_decode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors"})
    @GenerateNodeFactory
    abstract static class RawUnicodeEscapeDecodeNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object encode(VirtualFrame frame, Object obj, Object errors,
                        @Cached CodecsDecodeNode decode) {
            return decode.execute(frame, obj, T_RAW_UNICODE_ESCAPE, errors, true);
        }
    }

    @Builtin(name = "unicode_escape_encode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors"})
    @GenerateNodeFactory
    public abstract static class UnicodeEscapeEncodeNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object encode(VirtualFrame frame, Object obj, Object errors,
                        @Cached CodecsEncodeNode encode) {
            return encode.execute(frame, obj, T_UNICODE_ESCAPE, errors);
        }
    }

    @Builtin(name = "unicode_escape_decode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors"})
    @GenerateNodeFactory
    abstract static class UnicodeEscapeDecodeNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object encode(VirtualFrame frame, Object obj, Object errors,
                        @Cached CodecsDecodeNode decode) {
            return decode.execute(frame, obj, T_UNICODE_ESCAPE, errors, true);
        }
    }

    @Builtin(name = "latin_1_encode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors"})
    @GenerateNodeFactory
    abstract static class Latin1EscapeEncodeNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object encode(VirtualFrame frame, Object obj, Object errors,
                        @Cached CodecsEncodeNode encode) {
            return encode.execute(frame, obj, T_LATIN_1, errors);
        }
    }

    @Builtin(name = "latin_1_decode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors"})
    @GenerateNodeFactory
    abstract static class Latin1EscapeDecodeNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object encode(VirtualFrame frame, Object obj, Object errors,
                        @Cached CodecsDecodeNode decode) {
            return decode.execute(frame, obj, T_LATIN_1, errors, true);
        }
    }

    @Builtin(name = "ascii_encode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors"})
    @GenerateNodeFactory
    abstract static class AsciiEscapeEncodeNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object encode(VirtualFrame frame, Object obj, Object errors,
                        @Cached CodecsEncodeNode encode) {
            return encode.execute(frame, obj, T_ASCII, errors);
        }
    }

    @Builtin(name = "ascii_decode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors"})
    @GenerateNodeFactory
    abstract static class AsciiDecodeNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object decode(VirtualFrame frame, Object obj, Object errors,
                        @Cached CodecsDecodeNode decode) {
            return decode.execute(frame, obj, T_ASCII, errors, true);
        }
    }

    @Builtin(name = "charmap_encode", minNumOfPositionalArgs = 1, parameterNames = {"str", "errors", "mapping"})
    @ArgumentClinic(name = "str", conversion = ArgumentClinic.ClinicConversion.TString)
    @ArgumentClinic(name = "errors", conversion = ArgumentClinic.ClinicConversion.TString, defaultValue = "T_STRICT", useDefaultForNone = true)
    @GenerateNodeFactory
    abstract static class CharmapEncodeNode extends PythonTernaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return CodecsModuleBuiltinsClinicProviders.CharmapEncodeNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        Object doIt(VirtualFrame frame, TruffleString str, TruffleString errors, Object mapping,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached PyUnicodeEncodeCharmapNode encodeCharmapNode) {
            int len = codePointLengthNode.execute(str, TS_ENCODING);
            PBytes result = PFactory.createBytes(language, encodeCharmapNode.execute(frame, inliningTarget, str, errors, mapping));
            return PFactory.createTuple(language, new Object[]{result, len});
        }
    }

    @Builtin(name = "charmap_decode", minNumOfPositionalArgs = 1, parameterNames = {"data", "errors", "mapping"})
    @ArgumentClinic(name = "errors", conversion = ArgumentClinic.ClinicConversion.TString, defaultValue = "T_STRICT", useDefaultForNone = true)
    @GenerateNodeFactory
    abstract static class CharmapDecodeNode extends PythonTernaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return CodecsModuleBuiltinsClinicProviders.CharmapDecodeNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(limit = "3")
        Object doIt(VirtualFrame frame, Object data, TruffleString errors, Object mapping,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @Cached("createFor($node)") InteropCallData callData,
                        @CachedLibrary("data") PythonBufferAcquireLibrary bufferAcquireLib,
                        @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                        @Cached PyUnicodeDecodeCharmapNode pyUnicodeDecodeCharmapNode) {
            Object dataBuffer = bufferAcquireLib.acquireReadonly(data, frame, context, context.getLanguage(inliningTarget), callData);
            int len;
            try {
                len = bufferLib.getBufferLength(dataBuffer);
            } finally {
                bufferLib.release(dataBuffer, frame, callData);
            }
            TruffleString result = len == 0 ? T_EMPTY_STRING : pyUnicodeDecodeCharmapNode.execute(frame, data, errors, mapping);
            return PFactory.createTuple(context.getLanguage(inliningTarget), new Object[]{result, len});
        }
    }

    @Builtin(name = "readbuffer_encode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors"})
    @GenerateNodeFactory
    abstract static class ReadbufferEncodeNode extends PythonBinaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        @TruffleBoundary
        Object encode(Object obj, Object errors) {
            throw PRaiseNode.raiseStatic(this, NotImplementedError, toTruffleStringUncached("readbuffer_encode"));
        }
    }

    @Builtin(name = "mbcs_encode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors"})
    @GenerateNodeFactory
    abstract static class MBCSEncodeNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object encode(VirtualFrame frame, Object obj, Object errors,
                        @Cached CodecsEncodeNode encode,
                        @Cached WarnNode warnNode) {
            warnNode.execute(frame, null, RuntimeWarning, toTruffleStringUncached("mbcs_encode assumes cp437"), 1);
            return encode.execute(frame, obj, T_CP437, errors);
        }
    }

    @Builtin(name = "mbcs_decode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors", "ffinal"})
    @GenerateNodeFactory
    abstract static class MBCSDecodeNode extends PythonTernaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        Object decode(VirtualFrame frame, Object obj, Object errors, Object ffinal,
                        @Cached CodecsDecodeNode decode,
                        @Cached WarnNode warnNode) {
            warnNode.execute(frame, null, RuntimeWarning, toTruffleStringUncached("mbcs_decode assumes cp437"), 1);
            return decode.execute(frame, obj, T_CP437, errors, ffinal);
        }
    }

    @Builtin(name = "oem_encode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors"})
    @GenerateNodeFactory
    abstract static class OEMEncodeNode extends PythonBinaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        @TruffleBoundary
        Object encode(Object obj, Object errors) {
            throw PRaiseNode.raiseStatic(this, NotImplementedError, toTruffleStringUncached("oem_encode"));
        }
    }

    @Builtin(name = "oem_decode", minNumOfPositionalArgs = 1, parameterNames = {"obj", "errors", "ffinal"})
    @GenerateNodeFactory
    abstract static class OEMDecodeNode extends PythonTernaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        static Object decode(Object obj, Object errors, Object ffinal,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, NotImplementedError, toTruffleStringUncached("oem_decode"));
        }
    }

    @Builtin(name = "code_page_encode", minNumOfPositionalArgs = 1, parameterNames = {"code_page", "string", "errors"})
    @GenerateNodeFactory
    abstract static class CodePageEncodeNode extends PythonTernaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        static Object encode(Object code_page, Object string, Object errors,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, NotImplementedError, toTruffleStringUncached("code_page_encode"));
        }
    }

    @Builtin(name = "code_page_decode", minNumOfPositionalArgs = 1, parameterNames = {"code_page", "string", "errors", "ffinal"})
    @GenerateNodeFactory
    abstract static class CodePageDecodeNode extends PythonQuaternaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        static Object decode(Object code_page, Object obj, Object errors, Object ffinal,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, NotImplementedError, toTruffleStringUncached("code_page_decode"));
        }
    }

    @Builtin(name = "charmap_build", minNumOfPositionalArgs = 1, parameterNames = {"map"})
    @ArgumentClinic(name = "map", conversion = ArgumentClinic.ClinicConversion.TString)
    @GenerateNodeFactory
    abstract static class CharmapBuildNode extends PythonUnaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return CodecsModuleBuiltinsClinicProviders.CharmapBuildNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        Object doIt(VirtualFrame frame, TruffleString map,
                        @Bind Node inliningTarget,
                        @Cached PyUnicodeBuildEncodingMapNode buildEncodingMapNode) {
            return buildEncodingMapNode.execute(frame, inliningTarget, map);
        }
    }

    static class TruffleEncoder {
        private final CharsetEncoder encoder;
        private final String inputString;
        private final CharBuffer inputBuffer;
        private ByteBuffer outputBuffer;
        private CoderResult coderResult;

        @TruffleBoundary
        public TruffleEncoder(Charset charset, String input, CodingErrorAction errorAction) {
            this.inputString = input;
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
        private TruffleString getErrorReason() {
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
            return inputString.codePointCount(0, inputBuffer.position());
        }

        @TruffleBoundary
        public void setInputPosition(int position) {
            inputBuffer.position(inputString.offsetByCodePoints(0, position));
        }

        @TruffleBoundary
        public int getErrorLength() {
            return inputString.codePointCount(inputBuffer.position(), inputBuffer.position() + coderResult.length());
        }

        @TruffleBoundary
        public byte[] getBytes() {
            byte[] data = new byte[outputBuffer.remaining()];
            outputBuffer.get(data);
            return data;
        }

        @TruffleBoundary
        public void replace(byte[] replacement, int offset, int length) {
            while (outputBuffer.remaining() < replacement.length) {
                grow();
            }
            outputBuffer.put(replacement, offset, length);
        }

        @TruffleBoundary
        public void replace(String replacement, Charset charset) {
            byte[] replacementBytes = replacement.getBytes(charset);
            replace(replacementBytes, 0, replacementBytes.length);
        }
    }

    static class TruffleDecoder {
        private final TruffleString encodingName;
        private final CharsetDecoder decoder;
        private ByteBuffer inputBuffer;
        private CharBuffer outputBuffer;
        private CoderResult coderResult;

        @TruffleBoundary
        public TruffleDecoder(TruffleString encodingName, Charset charset, byte[] input, int inputLen, CodingErrorAction errorAction) {
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
        public TruffleString getErrorReason() {
            if (coderResult.isMalformed()) {
                return ErrorMessages.MALFORMED_INPUT;
            } else if (coderResult.isUnmappable()) {
                return ErrorMessages.UNMAPPABLE_CHARACTER;
            } else {
                throw new IllegalArgumentException("Unicode error constructed from non-error result");
            }
        }

        @TruffleBoundary
        public TruffleString getString() {
            return toTruffleStringUncached(outputBuffer.toString());
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
        public void setNewInput(byte[] bytes, int offset, int length, int newPosition) {
            inputBuffer = ByteBuffer.wrap(bytes, offset, length);
            inputBuffer.position(newPosition);
        }

        public void replace(int skipInput, char[] chars) {
            replace(skipInput, chars, 0, chars.length);
        }

        @TruffleBoundary
        public void setInputPosition(int position) {
            inputBuffer.position(position);
        }

        @TruffleBoundary
        public void replace(String str) {
            while (outputBuffer.remaining() < str.length()) {
                grow();
            }
            outputBuffer.put(str);
        }

        @TruffleBoundary
        public void replace(int skipInput, char[] chars, int offset, int length) {
            while (outputBuffer.remaining() < chars.length) {
                grow();
            }
            outputBuffer.put(chars, offset, length);
            inputBuffer.position(inputBuffer.position() + skipInput);
        }

        public TruffleString getEncodingName() {
            return encodingName;
        }
    }
}
