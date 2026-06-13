/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.lib;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_8;

import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.AbstractTruffleString;
import com.oracle.truffle.api.strings.TranscodingErrorHandler;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.Encoding;

/**
 * Equivalent of CPython's {@code PyUnicode_FSDecoder}. Converts a string, bytes or path-like object
 * to a TruffleString path.
 */
@GenerateUncached
@GenerateInline(false)
public abstract class PyUnicodeFSDecoderNode extends PNodeWithContext {
    public static final TranscodingErrorHandler SURROGATE_ESCAPE_FROM_UTF8_TRANSCODING_ERROR_HANDLER = PyUnicodeFSDecoderNode::surrogateEscapeTranscodingError;
    public static final TranscodingErrorHandler SURROGATE_ESCAPE_TO_UTF8_TRANSCODING_ERROR_HANDLER = PyUnicodeFSDecoderNode::surrogateEscapeToUTF8Handler;

    public abstract TruffleString execute(Frame frame, Object object);

    @Specialization
    TruffleString doString(TruffleString object,
                    @Shared("byteIndexOfCP") @Cached TruffleString.ByteIndexOfCodePointNode byteIndexOfCodePointNode) {
        return checkString(this, object, byteIndexOfCodePointNode);
    }

    @Specialization
    static TruffleString doPString(PString object,
                    @Bind Node inliningTarget,
                    @Cached CastToTruffleStringNode cast,
                    @Shared("byteIndexOfCP") @Cached TruffleString.ByteIndexOfCodePointNode byteIndexOfCodePointNode) {
        return checkString(inliningTarget, cast.execute(inliningTarget, object), byteIndexOfCodePointNode);
    }

    @Specialization(limit = "1")
    TruffleString doBytes(PBytes object,
                    @CachedLibrary("object") PythonBufferAccessLibrary bufferLib,
                    @Cached TruffleString.FromByteArrayNode fromByteArrayNode,
                    @Cached TruffleString.IsValidNode isValidNode,
                    @Cached TruffleString.SwitchEncodingNode switchEncodingNode,
                    @Shared("byteIndexOfCP") @Cached TruffleString.ByteIndexOfCodePointNode byteIndexOfCodePointNode) {
        TruffleString utf8 = fromByteArrayNode.execute(bufferLib.getCopiedByteArray(object), UTF_8, false);
        TruffleString str;
        if (isValidNode.execute(utf8, UTF_8)) {
            str = switchEncodingNode.execute(utf8, TS_ENCODING);
        } else {
            str = switchEncodingNode.execute(utf8, TS_ENCODING, SURROGATE_ESCAPE_FROM_UTF8_TRANSCODING_ERROR_HANDLER);
        }
        return checkString(this, str, byteIndexOfCodePointNode);
    }

    @Fallback
    static TruffleString doPathLike(VirtualFrame frame, Object object,
                    @Bind Node inliningTarget,
                    @Cached PyOSFSPathNode fspathNode,
                    @Cached PyUnicodeFSDecoderNode recursive) {
        Object path = fspathNode.execute(frame, inliningTarget, object);
        assert path instanceof TruffleString || path instanceof PString || path instanceof PBytes;
        return recursive.execute(frame, path);
    }

    private static TruffleString checkString(Node raisingNode, TruffleString str, TruffleString.ByteIndexOfCodePointNode byteIndexOfCodePointNode) {
        if (byteIndexOfCodePointNode.execute(str, 0, 0, str.byteLength(TS_ENCODING), TS_ENCODING) >= 0) {
            throw PRaiseNode.raiseStatic(raisingNode, ValueError, ErrorMessages.EMBEDDED_NULL_BYTE);
        }
        return str;
    }

    private static TranscodingErrorHandler.ReplacementString surrogateEscapeTranscodingError(AbstractTruffleString sourceString, int byteIndex, int estimatedByteLength,
                    Encoding sourceEncoding, Encoding targetEncoding) {
        assert sourceEncoding == UTF_8 && targetEncoding == TS_ENCODING;
        int b = sourceString.readByteUncached(byteIndex, UTF_8);
        assert b >= 0x80;
        return new TranscodingErrorHandler.ReplacementString(TruffleString.fromCodePointUncached(0xdc00 | b, TS_ENCODING, true), 1);
    }

    private static TranscodingErrorHandler.ReplacementString surrogateEscapeToUTF8Handler(AbstractTruffleString sourceString, int byteIndex,
                    @SuppressWarnings("unused") int estimatedByteLength, Encoding sourceEncoding, Encoding targetEncoding) {
        assert sourceEncoding == TS_ENCODING && targetEncoding == UTF_8;
        int codepoint = sourceString.codePointAtByteIndexUncached(byteIndex, TS_ENCODING);
        return new TranscodingErrorHandler.ReplacementString(TruffleString.fromByteArrayUncached(new byte[]{(byte) codepoint}, TruffleString.Encoding.UTF_8), 4);
    }
}
