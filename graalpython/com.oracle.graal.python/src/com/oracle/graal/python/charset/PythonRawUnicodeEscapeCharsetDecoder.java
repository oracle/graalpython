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

public class PythonRawUnicodeEscapeCharsetDecoder extends CharsetDecoder {
    private boolean seenBackslash = false;

    protected PythonRawUnicodeEscapeCharsetDecoder(Charset cs) {
        super(cs, 1, 1);
    }

    @Override
    protected CoderResult decodeLoop(ByteBuffer source, CharBuffer target) {
        byte[] numbuf = new byte[8];
        while (true) {
            if (!source.hasRemaining()) {
                return CoderResult.UNDERFLOW;
            }
            if (!target.hasRemaining()) {
                return CoderResult.OVERFLOW;
            }
            int initialPosition = source.position();
            byte b = source.get();
            if (seenBackslash) {
                // Report error from the backslash included
                initialPosition--;
                if (b == (byte) 'u' || b == (byte) 'U') {
                    int count = b == (byte) 'u' ? 4 : 8;
                    if (source.remaining() < count) {
                        source.position(initialPosition);
                        return CoderResult.UNDERFLOW;
                    }
                    source.get(numbuf, 0, count);
                    int codePoint;
                    try {
                        codePoint = Integer.parseInt(new String(numbuf, 0, count), 16);
                    } catch (NumberFormatException e) {
                        source.position(initialPosition);
                        return CoderResult.malformedForLength(count + 2);
                    }
                    if (!Character.isValidCodePoint(codePoint)) {
                        source.position(initialPosition);
                        return CoderResult.malformedForLength(count + 2);
                    }
                    if (Character.charCount(codePoint) == 2) {
                        target.put(Character.highSurrogate(codePoint));
                        if (!target.hasRemaining()) {
                            source.position(initialPosition);
                            target.position(target.position());
                            return CoderResult.OVERFLOW;
                        }
                        target.put(Character.lowSurrogate(codePoint));
                    } else {
                        target.put((char) codePoint);
                    }
                    seenBackslash = false;
                } else {
                    target.put('\\');
                    seenBackslash = false;
                }
            } else if (b == (byte) '\\') {
                seenBackslash = true;
            } else {
                // Bytes that are not an escape sequence are latin-1, which maps to unicode
                // codepoints directly
                target.put((char) (b & 0xFF));
            }
        }
    }

    @Override
    protected CoderResult implFlush(CharBuffer target) {
        if (seenBackslash) {
            if (!target.hasRemaining()) {
                return CoderResult.OVERFLOW;
            }
            target.put('\\');
            seenBackslash = false;
        }
        return CoderResult.UNDERFLOW;
    }
}
