/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.charset;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;

import com.oracle.graal.python.parser.sst.StringUtils;

public class PythonUnicodeEscapeCharsetDecoder extends CharsetDecoder {
    // Decoding octals is annoying, because they are variable lenght and we don't
    // have variable lookahead. We have to incrementally remember the current code
    // point and emit it only when we know the octal has really ended
    private int octalCodePoint;
    private int octalLength;

    protected PythonUnicodeEscapeCharsetDecoder(Charset cs) {
        super(cs, 1, 1);
    }

    static CoderResult decodeHexUnicodeEscape(ByteBuffer source, CharBuffer target, byte b, int initialPosition) {
        int count;
        switch (b) {
            case 'x':
                count = 2;
                break;
            case 'u':
                count = 4;
                break;
            case 'U':
                count = 8;
                break;
            default:
                throw new IllegalArgumentException();
        }
        if (source.remaining() < count) {
            source.position(initialPosition);
            return CoderResult.UNDERFLOW;
        }
        byte[] numbuf = new byte[count];
        source.get(numbuf, 0, count);
        int codePoint;
        try {
            codePoint = Integer.parseInt(new String(numbuf, 0, count), 16);
        } catch (NumberFormatException e) {
            int pos = source.position();
            source.position(initialPosition);
            return CoderResult.malformedForLength(pos - initialPosition);
        }
        return outputCodePoint(source, target, codePoint, initialPosition);
    }

    static CoderResult outputCodePoint(ByteBuffer source, CharBuffer target, int codePoint, int initialPosition) {
        if (!Character.isValidCodePoint(codePoint)) {
            int pos = source.position();
            source.position(initialPosition);
            return CoderResult.malformedForLength(pos - initialPosition);
        }
        if (Character.charCount(codePoint) == 2) {
            target.put(Character.highSurrogate(codePoint));
            if (!target.hasRemaining()) {
                source.position(initialPosition);
                target.position(target.position() - 1);
                return CoderResult.OVERFLOW;
            }
            target.put(Character.lowSurrogate(codePoint));
        } else {
            target.put((char) codePoint);
        }
        return null;
    }

    @Override
    protected CoderResult decodeLoop(ByteBuffer source, CharBuffer target) {
        while (true) {
            if (!source.hasRemaining()) {
                return CoderResult.UNDERFLOW;
            }
            if (!target.hasRemaining()) {
                return CoderResult.OVERFLOW;
            }
            int initialPosition = source.position();
            byte b = source.get();
            CoderResult result;

            if (b == '\\') {
                if (!source.hasRemaining()) {
                    source.position(initialPosition);
                    return CoderResult.UNDERFLOW;
                }
                b = source.get();
                if (b >= '0' && b <= '7') {
                    octalLength = 1;
                    octalCodePoint = b - '0';
                    continue;
                }
                switch (b) {
                    case '\\':
                        target.put('\\');
                        continue;
                    case 'a':
                        target.put('\u0007');
                        continue;
                    case 'b':
                        target.put('\b');
                        continue;
                    case 'f':
                        target.put('\f');
                        continue;
                    case 'n':
                        target.put('\n');
                        continue;
                    case 'r':
                        target.put('\r');
                        continue;
                    case 't':
                        target.put('\t');
                        continue;
                    case 'v':
                        target.put('\u000b');
                        continue;
                    case '\"':
                        target.put('\"');
                        continue;
                    case '\'':
                        target.put('\'');
                        continue;
                    case '\r':
                    case '\n':
                        continue;
                    case 'u':
                    case 'U':
                    case 'x':
                        result = decodeHexUnicodeEscape(source, target, b, initialPosition);
                        if (result != null) {
                            return result;
                        }
                        continue;
                    case 'N':
                        if (!source.hasRemaining()) {
                            source.position(initialPosition);
                            return CoderResult.UNDERFLOW;
                        }
                        b = source.get();
                        if (b != '{') {
                            source.position(initialPosition);
                            return CoderResult.malformedForLength(2);
                        }
                        do {
                            if (!source.hasRemaining()) {
                                source.position(initialPosition);
                                return CoderResult.UNDERFLOW;
                            }
                            b = source.get();
                        } while (b != '}');
                        int end = source.position();
                        source.position(initialPosition + 3);
                        byte[] nameBytes = new byte[end - source.position() - 1];
                        source.get(nameBytes);
                        int codePoint = StringUtils.getCodePoint(new String(nameBytes, StandardCharsets.US_ASCII));
                        if (codePoint < 0) {
                            source.position(initialPosition);
                            return CoderResult.malformedForLength(end - initialPosition);
                        }
                        result = outputCodePoint(source, target, codePoint, initialPosition);
                        if (result != null) {
                            return result;
                        }
                        continue;
                    default:
                        // TODO warning?
                        target.put('\\');
                        source.position(source.position() - 1);
                        continue;
                }
            } else if (octalLength > 0 && b >= '0' && b <= '7') {
                octalLength++;
                octalCodePoint = octalCodePoint * 8 + (b - '0');
                if (octalLength >= 3) {
                    outputOctal(target);
                }
            } else {
                if (octalLength >= 1) {
                    outputOctal(target);
                    if (!target.hasRemaining()) {
                        return CoderResult.OVERFLOW;
                    }
                }
                target.put((char) b);
            }
        }
    }

    private void outputOctal(CharBuffer target) {
        assert Character.isValidCodePoint(octalCodePoint) && Character.charCount(octalCodePoint) == 1;
        target.put((char) octalCodePoint);
        octalCodePoint = 0;
        octalLength = 0;
    }

    @Override
    protected void implReset() {
        octalCodePoint = 0;
        octalLength = 0;
    }

    @Override
    protected CoderResult implFlush(CharBuffer target) {
        if (octalLength > 0) {
            if (!target.hasRemaining()) {
                return CoderResult.OVERFLOW;
            }
            outputOctal(target);
        }
        return CoderResult.UNDERFLOW;
    }
}
