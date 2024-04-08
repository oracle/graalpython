/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;

import com.oracle.graal.python.util.PythonUtils;

public class PythonUTF32CharsetWrapper extends Charset {
    private final ByteOrder byteOrder;
    private final Charset delegate;

    public PythonUTF32CharsetWrapper(Charset delegate, ByteOrder byteOrder) {
        super("x-python-UTF32" + (byteOrder == ByteOrder.BIG_ENDIAN ? "BE" : "LE"), PythonUtils.EMPTY_STRING_ARRAY);
        this.byteOrder = byteOrder;
        this.delegate = delegate;
    }

    @Override
    public boolean contains(Charset cs) {
        return delegate.contains(cs);
    }

    @Override
    public CharsetDecoder newDecoder() {
        return new DecoderWrapper(this, delegate.newDecoder(), byteOrder);
    }

    @Override
    public CharsetEncoder newEncoder() {
        return delegate.newEncoder();
    }

    private static class DecoderWrapper extends CharsetDecoder {
        private final CharsetDecoder delegate;
        private final ByteOrder byteOrder;

        private DecoderWrapper(Charset charset, CharsetDecoder delegate, ByteOrder byteOrder) {
            super(charset, 4, 4);
            this.delegate = delegate;
            this.byteOrder = byteOrder;
        }

        @Override
        protected CoderResult decodeLoop(ByteBuffer in, CharBuffer out) {
            ByteOrder originalByteOrder = in.order();
            int originalPosition = in.position();
            in.order(byteOrder);
            try {
                while (in.remaining() >= 4) {
                    int cp = in.getInt();
                    if (0xD800 <= cp && cp <= 0xDFFF) {
                        in.position(in.position() - 4);
                        return CoderResult.malformedForLength(4);
                    }
                }
            } finally {
                in.order(originalByteOrder);
            }
            in.position(originalPosition);
            return delegate.decode(in, out, false);
        }
    }
}
