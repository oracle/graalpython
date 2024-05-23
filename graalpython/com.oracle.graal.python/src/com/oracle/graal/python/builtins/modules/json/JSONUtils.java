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
package com.oracle.graal.python.builtins.modules.json;

import static com.oracle.graal.python.builtins.objects.bytes.BytesUtils.HEXDIGITS;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.SubstringNode;
import com.oracle.truffle.api.strings.TruffleStringBuilder;
import com.oracle.truffle.api.strings.TruffleStringBuilder.AppendStringNode;
import com.oracle.truffle.api.strings.TruffleStringBuilderUTF32;
import com.oracle.truffle.api.strings.TruffleStringIterator;

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

    static void appendStringUncached(TruffleString ts, TruffleStringBuilderUTF32 builder, boolean asciiOnly) {
        // Note: appending in chunks does not pay off in the uncached case
        builder.appendCodePointUncached('"');
        TruffleStringIterator it = ts.createCodePointIteratorUncached(TS_ENCODING);
        while (it.hasNext()) {
            int c = it.nextUncached();
            switch (c) {
                case '\\':
                    builder.appendStringUncached(T_ESC_BACKSLASH);
                    break;
                case '"':
                    builder.appendStringUncached(T_ESC_QUOTE);
                    break;
                case '\b':
                    builder.appendStringUncached(T_ESC_B);
                    break;
                case '\f':
                    builder.appendStringUncached(T_ESC_F);
                    break;
                case '\n':
                    builder.appendStringUncached(T_ESC_N);
                    break;
                case '\r':
                    builder.appendStringUncached(T_ESC_R);
                    break;
                case '\t':
                    builder.appendStringUncached(T_ESC_T);
                    break;
                default:
                    if (c <= 0x1f || (asciiOnly && c > '~')) {
                        // appendSubstringUncached(builder, ts, chunkStart, currentIndex);
                        if (c <= 0xffff) {
                            appendEscapedUtf16Uncached((char) c, builder);
                        } else {
                            // split SMP codepoint to surrogate pair
                            appendEscapedUtf16Uncached((char) (0xD800 + ((c - 0x10000) >> 10)), builder);
                            appendEscapedUtf16Uncached((char) (0xDC00 + ((c - 0x10000) & 0x3FF)), builder);
                        }
                    } else {
                        builder.appendCodePointUncached(c, 1, true);
                    }
                    break;
            }
        }
        builder.appendCodePointUncached('"');
    }

    private static void appendEscapedUtf16Uncached(char c, TruffleStringBuilderUTF32 builder) {
        builder.appendStringUncached(TruffleString.fromByteArrayUncached(
                        new byte[]{'\\', 'u', HEXDIGITS[(c >> 12) & 0xf], HEXDIGITS[(c >> 8) & 0xf], HEXDIGITS[(c >> 4) & 0xf], HEXDIGITS[c & 0xf]}, TruffleString.Encoding.US_ASCII));
    }

    static void appendString(TruffleString s, TruffleStringIterator it, TruffleStringBuilder builder, boolean asciiOnly, TruffleStringIterator.NextNode nextNode,
                    TruffleStringBuilder.AppendCodePointNode appendCodePointNode, TruffleStringBuilder.AppendStringNode appendStringNode, SubstringNode substringNode) {
        appendCodePointNode.execute(builder, '"', 1, true);

        int chunkStart = 0;
        int currentIndex = 0;
        while (it.hasNext()) {
            int c = nextNode.execute(it);
            switch (c) {
                case '\\':
                    appendSubstring(builder, s, chunkStart, currentIndex, appendStringNode, substringNode);
                    chunkStart = currentIndex + 1;
                    appendStringNode.execute(builder, T_ESC_BACKSLASH);
                    break;
                case '"':
                    appendSubstring(builder, s, chunkStart, currentIndex, appendStringNode, substringNode);
                    chunkStart = currentIndex + 1;
                    appendStringNode.execute(builder, T_ESC_QUOTE);
                    break;
                case '\b':
                    appendSubstring(builder, s, chunkStart, currentIndex, appendStringNode, substringNode);
                    chunkStart = currentIndex + 1;
                    appendStringNode.execute(builder, T_ESC_B);
                    break;
                case '\f':
                    appendSubstring(builder, s, chunkStart, currentIndex, appendStringNode, substringNode);
                    chunkStart = currentIndex + 1;
                    appendStringNode.execute(builder, T_ESC_F);
                    break;
                case '\n':
                    appendSubstring(builder, s, chunkStart, currentIndex, appendStringNode, substringNode);
                    chunkStart = currentIndex + 1;
                    appendStringNode.execute(builder, T_ESC_N);
                    break;
                case '\r':
                    appendSubstring(builder, s, chunkStart, currentIndex, appendStringNode, substringNode);
                    chunkStart = currentIndex + 1;
                    appendStringNode.execute(builder, T_ESC_R);
                    break;
                case '\t':
                    appendSubstring(builder, s, chunkStart, currentIndex, appendStringNode, substringNode);
                    chunkStart = currentIndex + 1;
                    appendStringNode.execute(builder, T_ESC_T);
                    break;
                default:
                    if (c <= 0x1f || (asciiOnly && c > '~')) {
                        appendSubstring(builder, s, chunkStart, currentIndex, appendStringNode, substringNode);
                        chunkStart = currentIndex + 1;
                        if (c <= 0xffff) {
                            appendEscapedUtf16((char) c, builder, appendCodePointNode);
                        } else {
                            // split SMP codepoint to surrogate pair
                            appendEscapedUtf16((char) (0xD800 + ((c - 0x10000) >> 10)), builder, appendCodePointNode);
                            appendEscapedUtf16((char) (0xDC00 + ((c - 0x10000) & 0x3FF)), builder, appendCodePointNode);
                        }
                    }
                    break;
            }
            currentIndex++;
        }
        appendSubstring(builder, s, chunkStart, currentIndex, appendStringNode, substringNode);
        appendCodePointNode.execute(builder, '"', 1, true);
    }

    private static void appendSubstring(TruffleStringBuilder builder, TruffleString s, int startIndex, int endIndex, AppendStringNode appendStringNode, SubstringNode substringNode) {
        if (startIndex < endIndex) {
            appendStringNode.execute(builder, substringNode.execute(s, startIndex, endIndex - startIndex, TS_ENCODING, true));
        }
    }

    private static void appendEscapedUtf16(char c, TruffleStringBuilder builder, TruffleStringBuilder.AppendCodePointNode appendCodePointNode) {
        appendCodePointNode.execute(builder, '\\', 1, true);
        appendCodePointNode.execute(builder, 'u', 1, true);
        appendCodePointNode.execute(builder, HEXDIGITS[(c >> 12) & 0xf], 1, true);
        appendCodePointNode.execute(builder, HEXDIGITS[(c >> 8) & 0xf], 1, true);
        appendCodePointNode.execute(builder, HEXDIGITS[(c >> 4) & 0xf], 1, true);
        appendCodePointNode.execute(builder, HEXDIGITS[c & 0xf], 1, true);
    }
}
