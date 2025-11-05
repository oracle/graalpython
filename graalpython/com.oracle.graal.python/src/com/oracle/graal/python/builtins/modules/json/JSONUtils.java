/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.json;

import static com.oracle.graal.python.builtins.objects.bytes.BytesUtils.HEXDIGITS;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import com.oracle.graal.python.builtins.objects.str.StringUtils;
import com.oracle.truffle.api.HostCompilerDirectives.InliningRoot;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;
import com.oracle.truffle.api.strings.TruffleStringBuilderUTF32;

public abstract class JSONUtils {
    private JSONUtils() {
    }

    private static final TruffleString T_ESC_BACKSLASH = tsLiteral("\\\\");
    private static final TruffleString T_ESC_QUOTE = tsLiteral("\\\"");
    private static final TruffleString T_ESC_B = tsLiteral("\\b");
    private static final TruffleString T_ESC_F = tsLiteral("\\f");
    private static final TruffleString T_ESC_N = tsLiteral("\\n");
    private static final TruffleString T_ESC_R = tsLiteral("\\r");
    private static final TruffleString T_ESC_T = tsLiteral("\\t");

    private static final TruffleString.CodePointSet JSON_ESCAPE_CHARS = TruffleString.CodePointSet.fromRanges(new int[]{
                    0, 0x1f, // includes \b, \f, \n, \r, \t
                    '"', '"',
                    '\\', '\\',
    }, TS_ENCODING);
    private static final TruffleString.CodePointSet JSON_ESCAPE_CHARS_ASCII_ONLY = TruffleString.CodePointSet.fromRanges(new int[]{
                    0, 0x1f, // includes \b, \f, \n, \r, \t
                    '"', '"',
                    '\\', '\\',
                    0x7f, 0x10ffff,
    }, TS_ENCODING);

    static void appendString(TruffleString ts, TruffleStringBuilderUTF32 builder, boolean asciiOnly,
                    TruffleString.ByteIndexOfCodePointSetNode byteIndexOfCodePointSetNode1,
                    TruffleString.ByteIndexOfCodePointSetNode byteIndexOfCodePointSetNode2,
                    TruffleString.CodePointAtIndexUTF32Node codePointAtNode,
                    TruffleStringBuilder.AppendCodePointNode appendCodePointNode,
                    TruffleStringBuilder.AppendStringNode appendStringNode,
                    TruffleStringBuilder.AppendSubstringByteIndexNode appendSubstringNode,
                    TruffleString.FromByteArrayNode fromByteArrayNode) {
        appendCodePointNode.execute(builder, '"');
        int byteLength = ts.byteLength(TS_ENCODING);
        int codepointLength = StringUtils.byteIndexToCodepointIndex(byteLength);
        if (codepointLength < 16) {
            int i = 0;
            for (; i < codepointLength; i++) {
                int c = codePointAtNode.execute(ts, i);
                if (c <= 0x1f || c == '"' || c == '\\' || (asciiOnly && c >= 0x7f)) {
                    break;
                }
            }
            if (i > 0) {
                appendSubstringNode.execute(builder, ts, 0, StringUtils.codepointIndexToByteIndex(i));
            }
            for (; i < codepointLength; i++) {
                int c = codePointAtNode.execute(ts, i);
                if (c <= 0x1f || c == '"' || c == '\\' || (asciiOnly && c >= 0x7f)) {
                    appendStringNode.execute(builder, getEscaped(c, fromByteArrayNode));
                } else {
                    appendCodePointNode.execute(builder, c);
                }
            }
        } else {
            appendLongString(ts, builder, asciiOnly, byteIndexOfCodePointSetNode1, byteIndexOfCodePointSetNode2, codePointAtNode, appendStringNode, appendSubstringNode, fromByteArrayNode, byteLength);
        }
        appendCodePointNode.execute(builder, '"');
    }

    @InliningRoot
    private static void appendLongString(TruffleString ts, TruffleStringBuilderUTF32 builder, boolean asciiOnly,
                    TruffleString.ByteIndexOfCodePointSetNode byteIndexOfCodePointSetNode1,
                    TruffleString.ByteIndexOfCodePointSetNode byteIndexOfCodePointSetNode2,
                    TruffleString.CodePointAtIndexUTF32Node codePointAtNode,
                    TruffleStringBuilder.AppendStringNode appendStringNode,
                    TruffleStringBuilder.AppendSubstringByteIndexNode appendSubstringNode,
                    TruffleString.FromByteArrayNode fromByteArrayNode,
                    int byteLength) {
        int lastEscape = 0;
        while (lastEscape < byteLength) {
            int pos = asciiOnly
                            ? byteIndexOfCodePointSetNode1.execute(ts, lastEscape, byteLength, JSON_ESCAPE_CHARS_ASCII_ONLY)
                            : byteIndexOfCodePointSetNode2.execute(ts, lastEscape, byteLength, JSON_ESCAPE_CHARS);
            int substringLength = (pos < 0 ? ts.byteLength(TS_ENCODING) : pos) - lastEscape;
            if (substringLength > 0) {
                appendSubstringNode.execute(builder, ts, lastEscape, substringLength);
            }
            if (pos < 0) {
                break;
            }
            appendStringNode.execute(builder, getEscaped(codePointAtNode.execute(ts, StringUtils.byteIndexToCodepointIndex(pos)), fromByteArrayNode));
            lastEscape = pos + 4;
        }
    }

    private static TruffleString getEscaped(int c, TruffleString.FromByteArrayNode fromByteArrayNode) {
        return switch (c) {
            case '\\' -> T_ESC_BACKSLASH;
            case '"' -> T_ESC_QUOTE;
            case '\b' -> T_ESC_B;
            case '\f' -> T_ESC_F;
            case '\n' -> T_ESC_N;
            case '\r' -> T_ESC_R;
            case '\t' -> T_ESC_T;
            default -> fromByteArrayNode.execute(utf16Escape(c), TruffleString.Encoding.US_ASCII, false);
        };
    }

    private static byte[] utf16Escape(int c) {
        if (c <= 0xffff) {
            return new byte[]{'\\', 'u', HEXDIGITS[(c >> 12) & 0xf], HEXDIGITS[(c >> 8) & 0xf], HEXDIGITS[(c >> 4) & 0xf], HEXDIGITS[c & 0xf]};
        } else {
            // split SMP codepoint to surrogate pair
            char c1 = (char) (0xD800 + ((c - 0x10000) >> 10));
            char c2 = (char) (0xDC00 + ((c - 0x10000) & 0x3FF));
            return new byte[]{
                            '\\', 'u', HEXDIGITS[(c1 >> 12) & 0xf], HEXDIGITS[(c1 >> 8) & 0xf], HEXDIGITS[(c1 >> 4) & 0xf], HEXDIGITS[c1 & 0xf],
                            '\\', 'u', HEXDIGITS[(c2 >> 12) & 0xf], HEXDIGITS[(c2 >> 8) & 0xf], HEXDIGITS[(c2 >> 4) & 0xf], HEXDIGITS[c2 & 0xf]};
        }
    }
}
