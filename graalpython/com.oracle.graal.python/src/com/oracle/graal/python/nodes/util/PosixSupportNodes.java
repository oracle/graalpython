/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.util;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.UnicodeDecodeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.UnicodeEncodeError;
import static com.oracle.graal.python.util.PythonUtils.SURROGATE_CODE_POINT_SET;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_16LE;
import static com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_8;

import java.nio.charset.StandardCharsets;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.PythonOS;
import com.oracle.graal.python.lib.PyUnicodeEncodeFSDefaultNode;
import com.oracle.graal.python.lib.PyUnicodeFSDecoderNode;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.util.PosixSupportNodesFactory.CreateCStringFromStringNodeGen;
import com.oracle.graal.python.nodes.util.PosixSupportNodesFactory.CreatePathFromStringNodeGen;
import com.oracle.graal.python.nodes.util.PosixSupportNodesFactory.CreateWideStringFromStringNodeGen;
import com.oracle.graal.python.nodes.util.PosixSupportNodesFactory.GetCStringAsStringNodeGen;
import com.oracle.graal.python.nodes.util.PosixSupportNodesFactory.GetPathAsStringNodeGen;
import com.oracle.graal.python.runtime.EmulatedPosixSupport;
import com.oracle.graal.python.runtime.LoggingPosixSupport;
import com.oracle.graal.python.runtime.NativePosixSupport;
import com.oracle.graal.python.runtime.PosixSupport;
import com.oracle.graal.python.runtime.PosixSupport.Buffer;
import com.oracle.graal.python.runtime.PreInitPosixSupport;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.AbstractTruffleString;
import com.oracle.truffle.api.strings.TranscodingErrorHandler;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.FromByteArrayNode;
import com.oracle.truffle.api.strings.TruffleString.IsValidNode;
import com.oracle.truffle.api.strings.TruffleString.SwitchEncodingNode;

/**
 * Conversions at the boundary between Python strings and the representation used by a POSIX
 * backend. Keeping these operations here is important: POSIX support methods can then exchange
 * only their backend-native representations and do not need to materialize TruffleStrings.
 */
public final class PosixSupportNodes {

    private PosixSupportNodes() {
    }

    public static Object createPathFromString(PosixSupport posixSupport, TruffleString path) {
        return CreatePathFromStringNodeGen.getUncached().execute(null, null, posixSupport, path);
    }

    public static TruffleString getPathAsString(Node inliningTarget, PosixSupport posixSupport, Object path) {
        return GetPathAsStringNodeGen.getUncached().execute(inliningTarget, posixSupport, path);
    }

    public static Object createCStringFromString(PosixSupport posixSupport, TruffleString string) {
        return CreateCStringFromStringNodeGen.getUncached().execute(null, posixSupport, string);
    }

    public static Object createWideStringFromString(PosixSupport posixSupport, TruffleString string) {
        return CreateWideStringFromStringNodeGen.getUncached().execute(null, posixSupport, string);
    }

    public static TruffleString getCStringAsString(PosixSupport posixSupport, Object string) {
        return GetCStringAsStringNodeGen.getUncached().execute(null, posixSupport, string);
    }

    @TruffleBoundary
    private static String newStringFromUTF8(byte[] utf8Bytes) {
        return new String(utf8Bytes, StandardCharsets.UTF_8);
    }

    @GenerateUncached
    @GenerateInline(inlineByDefault = true)
    public abstract static class CreatePathFromStringNode extends PNodeWithContext {
        public abstract Object execute(Frame frame, Node inliningTarget, PosixSupport posixSupport, TruffleString path);

        @Specialization
        static Object doNative(Frame frame, Node inliningTarget, NativePosixSupport posixSupport, TruffleString path,
                        @Cached TruffleString.SwitchEncodingNode switchEncodingNode,
                        @Cached TruffleString.CopyToByteArrayNode copyToByteArrayNode,
                        @Shared @Cached PyUnicodeEncodeFSDefaultNode encodeFSDefaultNode) {
            if (isWindows()) {
                TruffleString utf16 = switchEncodingNode.execute(path, UTF_16LE, TranscodingErrorHandler.DEFAULT_KEEP_SURROGATES_IN_UTF8);
                return posixSupport.createRawPath(copyToByteArrayNode.execute(utf16, UTF_16LE), true);
            }
            return posixSupport.createRawPath(encodeFSDefaultNode.execute(frame, inliningTarget, path), false);
        }

        @Specialization
        static Object doEmulated(Frame frame, Node inliningTarget, EmulatedPosixSupport posixSupport, TruffleString path,
                        @Cached TruffleString.ToJavaStringNode toJavaStringNode,
                        @Shared @Cached PyUnicodeEncodeFSDefaultNode encodeFSDefaultNode) {
            String javaPath;
            if (isWindows()) {
                javaPath = toJavaStringNode.execute(path);
            } else {
                javaPath = PosixSupportNodes.newStringFromUTF8(encodeFSDefaultNode.execute(frame, inliningTarget, path));
            }
            return posixSupport.createRawPath(javaPath);
        }

        @Specialization
        static Object doLogging(Frame frame, Node inliningTarget, LoggingPosixSupport posixSupport, TruffleString path,
                        @Shared @Cached(inline = false) CreatePathFromStringNode delegateNode) {
            posixSupport.logConversionEnter("createPathFromString", path);
            return posixSupport.logConversionExit("createPathFromString", delegateNode.execute(frame, inliningTarget, posixSupport.getDelegate(), path));
        }

        @Specialization
        static Object doPreInit(Frame frame, Node inliningTarget, PreInitPosixSupport posixSupport, TruffleString path,
                        @Shared @Cached(inline = false) CreatePathFromStringNode delegateNode) {
            return delegateNode.execute(frame, inliningTarget, posixSupport.getCurrentBackend(), path);
        }
    }

    @GenerateUncached
    @GenerateInline(inlineByDefault = true)
    public abstract static class CreatePathFromBytesNode extends PNodeWithContext {
        public abstract Object execute(Node inliningTarget, PosixSupport posixSupport, byte[] path);

        @Specialization
        static Object doNative(Node inliningTarget, NativePosixSupport posixSupport, byte[] path,
                        @Cached TruffleString.FromByteArrayNode fromByteArrayNode,
                        @Cached TruffleString.SwitchEncodingNode switchEncodingNode,
                        @Cached TruffleString.CopyToByteArrayNode copyToByteArrayNode) {
            if (!isWindows()) {
                return posixSupport.createRawPath(path, false);
            }
            TruffleString utf8 = fromByteArrayNode.execute(path, UTF_8, true);
            TruffleString utf16 = switchEncodingNode.execute(utf8, UTF_16LE, windowsPathDecodeErrorHandler(inliningTarget, path));
            return posixSupport.createRawPath(copyToByteArrayNode.execute(utf16, UTF_16LE), true);
        }

        @Specialization
        static Object doEmulated(@SuppressWarnings("unused") Node inliningTarget,
                        EmulatedPosixSupport posixSupport, byte[] path) {
            return posixSupport.createRawPath(PosixSupportNodes.newStringFromUTF8(path));
        }

        @Specialization
        static Object doLogging(Node inliningTarget, LoggingPosixSupport posixSupport, byte[] path,
                        @Shared @Cached(inline = false) CreatePathFromBytesNode delegateNode) {
            posixSupport.logConversionEnter("createPathFromBytes", path);
            return posixSupport.logConversionExit("createPathFromBytes", delegateNode.execute(inliningTarget, posixSupport.getDelegate(), path));
        }

        @Specialization
        static Object doPreInit(Node inliningTarget, PreInitPosixSupport posixSupport, byte[] path,
                        @Shared @Cached(inline = false) CreatePathFromBytesNode delegateNode) {
            return delegateNode.execute(inliningTarget, posixSupport.getCurrentBackend(), path);
        }
    }

    @GenerateUncached
    @GenerateInline(inlineByDefault = true)
    public abstract static class GetPathAsStringNode extends PNodeWithContext {
        public abstract TruffleString execute(Node inliningTarget, PosixSupport posixSupport, Object path);

        @Specialization
        static TruffleString doNative(@SuppressWarnings("unused") Node inliningTarget,
                        NativePosixSupport posixSupport, Object path,
                        @Cached TruffleString.FromByteArrayNode fromByteArrayNode,
                        @Cached TruffleString.SwitchEncodingNode switchEncodingNode,
                        @Cached TruffleString.IsValidNode isValidNode) {
            byte[] data = posixSupport.getRawPathData(path);
            if (posixSupport.isWidePath(path)) {
                return switchEncodingNode.execute(fromByteArrayNode.execute(data, UTF_16LE, true), TS_ENCODING,
                                TranscodingErrorHandler.DEFAULT_KEEP_SURROGATES_IN_UTF8);
            }
            return decodeFilesystemString(data, data.length, fromByteArrayNode, switchEncodingNode, isValidNode);
        }

        @Specialization
        static TruffleString doEmulated(@SuppressWarnings("unused") Node inliningTarget, EmulatedPosixSupport posixSupport, Object path,
                        @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
            return fromJavaStringNode.execute(posixSupport.getRawPath(path), TS_ENCODING);
        }

        @Specialization
        static TruffleString doLogging(Node inliningTarget, LoggingPosixSupport posixSupport, Object path,
                        @Shared @Cached(inline = false) GetPathAsStringNode delegateNode) {
            posixSupport.logConversionEnter("getPathAsString", path);
            return posixSupport.logConversionExit("getPathAsString", delegateNode.execute(inliningTarget, posixSupport.getDelegate(), path));
        }

        @Specialization
        static TruffleString doPreInit(Node inliningTarget, PreInitPosixSupport posixSupport, Object path,
                        @Shared @Cached(inline = false) GetPathAsStringNode delegateNode) {
            return delegateNode.execute(inliningTarget, posixSupport.getCurrentBackend(), path);
        }
    }

    @GenerateUncached
    @GenerateInline(inlineByDefault = true)
    public abstract static class GetPathAsBytesNode extends PNodeWithContext {
        public abstract Buffer execute(Node inliningTarget, PosixSupport posixSupport, Object path);

        @Specialization
        static Buffer doNative(@SuppressWarnings("unused") Node inliningTarget,
                        NativePosixSupport posixSupport, Object path,
                        @Cached TruffleString.FromByteArrayNode fromByteArrayNode,
                        @Cached TruffleString.SwitchEncodingNode switchEncodingNode,
                        @Cached TruffleString.CopyToByteArrayNode copyToByteArrayNode) {
            byte[] data = posixSupport.getRawPathData(path);
            if (!posixSupport.isWidePath(path)) {
                return Buffer.wrap(data);
            }
            TruffleString utf16 = fromByteArrayNode.execute(data, UTF_16LE, true);
            TruffleString utf8 = switchEncodingNode.execute(utf16, UTF_8, TranscodingErrorHandler.DEFAULT_KEEP_SURROGATES_IN_UTF8);
            return Buffer.wrap(copyToByteArrayNode.execute(utf8, UTF_8));
        }

        @Specialization
        @TruffleBoundary
        static Buffer doEmulated(@SuppressWarnings("unused") Node inliningTarget,
                        EmulatedPosixSupport posixSupport, Object path) {
            return Buffer.wrap(posixSupport.getRawPath(path).getBytes(StandardCharsets.UTF_8));
        }

        @Specialization
        static Buffer doLogging(Node inliningTarget, LoggingPosixSupport posixSupport, Object path,
                        @Shared @Cached(inline = false) GetPathAsBytesNode delegateNode) {
            posixSupport.logConversionEnter("getPathAsBytes", path);
            return posixSupport.logConversionExit("getPathAsBytes", delegateNode.execute(inliningTarget, posixSupport.getDelegate(), path));
        }

        @Specialization
        static Buffer doPreInit(Node inliningTarget, PreInitPosixSupport posixSupport, Object path,
                        @Shared @Cached(inline = false) GetPathAsBytesNode delegateNode) {
            return delegateNode.execute(inliningTarget, posixSupport.getCurrentBackend(), path);
        }
    }

    @GenerateUncached
    @GenerateInline(inlineByDefault = true)
    public abstract static class CreateCStringFromStringNode extends PNodeWithContext {
        public abstract Object execute(Node inliningTarget, PosixSupport posixSupport, TruffleString string);

        @Specialization
        static Object doNative(Node inliningTarget, NativePosixSupport posixSupport, TruffleString string,
                        @Cached TruffleString.IsValidNode isValidNode,
                        @Cached TruffleString.SwitchEncodingNode switchEncodingNode,
                        @Cached TruffleString.CopyToByteArrayNode copyToByteArrayNode) {
            return posixSupport.createCStringFromBytes(encodeStrictUtf8(inliningTarget, string, isValidNode, switchEncodingNode, copyToByteArrayNode));
        }

        @Specialization
        static Object doEmulated(@SuppressWarnings("unused") Node inliningTarget,
                        EmulatedPosixSupport posixSupport, TruffleString string,
                        @Cached TruffleString.ToJavaStringNode toJavaStringNode) {
            return posixSupport.createRawCString(toJavaStringNode.execute(string));
        }

        @Specialization
        static Object doLogging(Node inliningTarget, LoggingPosixSupport posixSupport, TruffleString string,
                        @Shared @Cached(inline = false) CreateCStringFromStringNode delegateNode) {
            posixSupport.logConversionEnter("createCStringFromString", string);
            return posixSupport.logConversionExit("createCStringFromString", delegateNode.execute(inliningTarget, posixSupport.getDelegate(), string));
        }

        @Specialization
        static Object doPreInit(Node inliningTarget, PreInitPosixSupport posixSupport, TruffleString string,
                        @Shared @Cached(inline = false) CreateCStringFromStringNode delegateNode) {
            return delegateNode.execute(inliningTarget, posixSupport.getCurrentBackend(), string);
        }
    }

    @GenerateUncached
    @GenerateInline(inlineByDefault = true)
    public abstract static class CreateWideStringFromStringNode extends PNodeWithContext {
        public abstract Object execute(Node inliningTarget, PosixSupport posixSupport, TruffleString string);

        @Specialization
        static Object doNative(@SuppressWarnings("unused") Node inliningTarget,
                        NativePosixSupport posixSupport, TruffleString string,
                        @Cached TruffleString.SwitchEncodingNode switchEncodingNode,
                        @Cached TruffleString.CopyToByteArrayNode copyToByteArrayNode) {
            TruffleString utf16 = switchEncodingNode.execute(string, UTF_16LE, TranscodingErrorHandler.DEFAULT_KEEP_SURROGATES_IN_UTF8);
            return posixSupport.createRawWideString(copyToByteArrayNode.execute(utf16, UTF_16LE));
        }

        @Specialization
        static Object doEmulated(@SuppressWarnings("unused") Node inliningTarget,
                        EmulatedPosixSupport posixSupport, TruffleString string,
                        @Cached TruffleString.ToJavaStringNode toJavaStringNode) {
            return posixSupport.createRawWideString(toJavaStringNode.execute(string));
        }

        @Specialization
        static Object doLogging(Node inliningTarget, LoggingPosixSupport posixSupport, TruffleString string,
                        @Shared @Cached(inline = false) CreateWideStringFromStringNode delegateNode) {
            posixSupport.logConversionEnter("createWideStringFromString", string);
            return posixSupport.logConversionExit("createWideStringFromString", delegateNode.execute(inliningTarget, posixSupport.getDelegate(), string));
        }

        @Specialization
        static Object doPreInit(Node inliningTarget, PreInitPosixSupport posixSupport, TruffleString string,
                        @Shared @Cached(inline = false) CreateWideStringFromStringNode delegateNode) {
            return delegateNode.execute(inliningTarget, posixSupport.getCurrentBackend(), string);
        }
    }

    @GenerateUncached
    @GenerateInline(inlineByDefault = true)
    public abstract static class GetCStringAsStringNode extends PNodeWithContext {
        public abstract TruffleString execute(Node inliningTarget, PosixSupport posixSupport, Object string);

        @Specialization
        static TruffleString doNative(@SuppressWarnings("unused") Node inliningTarget,
                        NativePosixSupport posixSupport, Object string,
                        @Cached TruffleString.FromByteArrayNode fromByteArrayNode,
                        @Cached TruffleString.SwitchEncodingNode switchEncodingNode) {
            byte[] data = posixSupport.getRawStringData(string);
            if (posixSupport.isWideString(string)) {
                return switchEncodingNode.execute(fromByteArrayNode.execute(data, UTF_16LE, true), TS_ENCODING,
                                TranscodingErrorHandler.DEFAULT_KEEP_SURROGATES_IN_UTF8);
            }
            return switchEncodingNode.execute(fromByteArrayNode.execute(data, 0, posixSupport.getRawStringLength(string), UTF_8, true), TS_ENCODING);
        }

        @Specialization
        static TruffleString doEmulated(@SuppressWarnings("unused") Node inliningTarget, EmulatedPosixSupport posixSupport, Object string,
                        @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
            return fromJavaStringNode.execute(posixSupport.getRawCString(string), TS_ENCODING);
        }

        @Specialization
        static TruffleString doLogging(Node inliningTarget, LoggingPosixSupport posixSupport, Object string,
                        @Shared @Cached(inline = false) GetCStringAsStringNode delegateNode) {
            posixSupport.logConversionEnter("getCStringAsString", string);
            return posixSupport.logConversionExit("getCStringAsString", delegateNode.execute(inliningTarget, posixSupport.getDelegate(), string));
        }

        @Specialization
        static TruffleString doPreInit(Node inliningTarget, PreInitPosixSupport posixSupport, Object string,
                        @Shared @Cached(inline = false) GetCStringAsStringNode delegateNode) {
            return delegateNode.execute(inliningTarget, posixSupport.getCurrentBackend(), string);
        }
    }

    @GenerateUncached
    @GenerateInline(inlineByDefault = true)
    public abstract static class GetCStringAsBytesNode extends PNodeWithContext {
        public abstract Buffer execute(Node inliningTarget, PosixSupport posixSupport, Object string);

        @Specialization
        static Buffer doNative(@SuppressWarnings("unused") Node inliningTarget,
                        NativePosixSupport posixSupport, Object string,
                        @Cached TruffleString.FromByteArrayNode fromByteArrayNode,
                        @Cached TruffleString.SwitchEncodingNode switchEncodingNode,
                        @Cached TruffleString.CopyToByteArrayNode copyToByteArrayNode) {
            byte[] data = posixSupport.getRawStringData(string);
            if (!posixSupport.isWideString(string)) {
                return (Buffer) string;
            }
            TruffleString utf16 = fromByteArrayNode.execute(data, UTF_16LE, true);
            TruffleString utf8 = switchEncodingNode.execute(utf16, UTF_8, TranscodingErrorHandler.DEFAULT_KEEP_SURROGATES_IN_UTF8);
            return Buffer.wrap(copyToByteArrayNode.execute(utf8, UTF_8));
        }

        @Specialization
        @TruffleBoundary
        static Buffer doEmulated(@SuppressWarnings("unused") Node inliningTarget,
                        EmulatedPosixSupport posixSupport, Object string) {
            return Buffer.wrap(posixSupport.getRawCString(string).getBytes(StandardCharsets.UTF_8));
        }

        @Specialization
        static Buffer doLogging(Node inliningTarget, LoggingPosixSupport posixSupport, Object string,
                        @Shared @Cached(inline = false) GetCStringAsBytesNode delegateNode) {
            posixSupport.logConversionEnter("getCStringAsBytes", string);
            return posixSupport.logConversionExit("getCStringAsBytes", delegateNode.execute(inliningTarget, posixSupport.getDelegate(), string));
        }

        @Specialization
        static Buffer doPreInit(Node inliningTarget, PreInitPosixSupport posixSupport, Object string,
                        @Shared @Cached(inline = false) GetCStringAsBytesNode delegateNode) {
            return delegateNode.execute(inliningTarget, posixSupport.getCurrentBackend(), string);
        }
    }

    /** Decodes a native byte field using the filesystem encoding and surrogateescape on Unix. */
    @GenerateUncached
    @GenerateInline(inlineByDefault = true)
    public abstract static class DecodeFilesystemStringNode extends PNodeWithContext {
        public abstract TruffleString execute(Node inliningTarget, PosixSupport posixSupport, Object bytes);

        @Specialization
        static TruffleString doNative(@SuppressWarnings("unused") Node inliningTarget, @SuppressWarnings("unused") NativePosixSupport posixSupport,
                        Buffer bytes,
                        @Cached TruffleString.FromByteArrayNode fromByteArrayNode,
                        @Cached TruffleString.SwitchEncodingNode switchEncodingNode,
                        @Cached TruffleString.IsValidNode isValidNode) {
            return decodeFilesystemString(bytes.data, (int) bytes.length, fromByteArrayNode, switchEncodingNode, isValidNode);
        }

        @Specialization
        static TruffleString doEmulated(@SuppressWarnings("unused") Node inliningTarget,
                        @SuppressWarnings("unused") EmulatedPosixSupport posixSupport, String bytes,
                        @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
            return fromJavaStringNode.execute(bytes, TS_ENCODING);
        }

        @Specialization
        static TruffleString doLogging(Node inliningTarget, LoggingPosixSupport posixSupport, Object bytes,
                        @Shared @Cached(inline = false) DecodeFilesystemStringNode delegateNode) {
            return delegateNode.execute(inliningTarget, posixSupport.getDelegate(), bytes);
        }

        @Specialization
        static TruffleString doPreInit(Node inliningTarget, PreInitPosixSupport posixSupport, Object bytes,
                        @Shared @Cached(inline = false) DecodeFilesystemStringNode delegateNode) {
            return delegateNode.execute(inliningTarget, posixSupport.getCurrentBackend(), bytes);
        }
    }

    private static boolean isWindows() {
        return PythonLanguage.getPythonOS() == PythonOS.PLATFORM_WIN32;
    }

    private static byte[] encodeStrictUtf8(Node inliningTarget, TruffleString string, TruffleString.IsValidNode isValidNode,
                    TruffleString.SwitchEncodingNode switchEncodingNode, TruffleString.CopyToByteArrayNode copyToByteArrayNode) {
        if (!isValidNode.execute(string, TS_ENCODING)) {
            throw raiseSurrogatesEncodeError(inliningTarget, string);
        }
        TruffleString utf8 = switchEncodingNode.execute(string, UTF_8);
        return copyToByteArrayNode.execute(utf8, UTF_8);
    }

    @TruffleBoundary
    private static PException raiseSurrogatesEncodeError(Node node, TruffleString string) {
        int byteIndex = TruffleString.ByteIndexOfCodePointSetNode.getUncached().execute(string, 0, string.byteLength(TS_ENCODING), SURROGATE_CODE_POINT_SET);
        int start = byteIndex < 0 ? 0 : byteIndex / 4;
        int length = string.codePointLengthUncached(TS_ENCODING);
        int end = Math.min(start + 1, length);
        while (end < length) {
            int codePoint = string.codePointAtIndexUncached(end, TS_ENCODING);
            if (codePoint < Character.MIN_SURROGATE || codePoint > Character.MAX_SURROGATE) {
                break;
            }
            end++;
        }
        Object exception = CallNode.executeUncached(UnicodeEncodeError, TruffleString.fromJavaStringUncached("utf-8", TS_ENCODING), string, start, end,
                        TruffleString.fromJavaStringUncached("surrogates not allowed", TS_ENCODING));
        return PRaiseNode.raiseExceptionObjectStatic(node, exception);
    }

    private static TruffleString decodeFilesystemString(byte[] data, int length,
                    FromByteArrayNode fromByteArrayNode, SwitchEncodingNode switchEncodingNode,
                    IsValidNode isValidNode) {
        TruffleString utf8 = fromByteArrayNode.execute(data, 0, length, UTF_8, true);
        if (isValidNode.execute(utf8, UTF_8)) {
            return switchEncodingNode.execute(utf8, TS_ENCODING);
        }
        TranscodingErrorHandler handler = isWindows() ? TranscodingErrorHandler.DEFAULT_KEEP_SURROGATES_IN_UTF8 : PyUnicodeFSDecoderNode.SURROGATE_ESCAPE_FROM_UTF8_TRANSCODING_ERROR_HANDLER;
        return switchEncodingNode.execute(utf8, TS_ENCODING, handler);
    }

    private static TranscodingErrorHandler windowsPathDecodeErrorHandler(Node inliningTarget, byte[] input) {
        return (AbstractTruffleString sourceString, int byteIndex, int estimatedByteLength, TruffleString.Encoding sourceEncoding,
                        TruffleString.Encoding targetEncoding) -> {
            if (byteIndex + 2 < input.length && (input[byteIndex] & 0xff) == 0xed &&
                            (input[byteIndex + 1] & 0xe0) == 0xa0 && (input[byteIndex + 2] & 0xc0) == 0x80) {
                int codePoint = ((input[byteIndex] & 0x0f) << 12) | ((input[byteIndex + 1] & 0x3f) << 6) | (input[byteIndex + 2] & 0x3f);
                return new TranscodingErrorHandler.ReplacementString(TruffleString.fromCodePointUncached(codePoint, UTF_16LE, true), 3);
            }
            Object exception = CallNode.executeUncached(UnicodeDecodeError,
                            TruffleString.fromJavaStringUncached("utf-8", TS_ENCODING),
                            com.oracle.graal.python.runtime.object.PFactory.createBytes(PythonLanguage.get(inliningTarget), input), byteIndex,
                            Math.min(input.length, byteIndex + Math.max(1, estimatedByteLength)),
                            TruffleString.fromJavaStringUncached("invalid UTF-8 path", TS_ENCODING));
            throw PRaiseNode.raiseExceptionObjectStatic(inliningTarget, exception);
        };
    }
}
