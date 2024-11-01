/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.pegparser.tokenizer;

import java.util.Arrays;

public final class CodePoints {

    public static final CodePoints EMPTY = new CodePoints(new int[0], 0, 0);

    private final int[] buffer;
    private final int offset;
    private final int length;

    private CodePoints(int[] buffer, int offset, int length) {
        assert 0 <= offset && 0 <= length && offset + length <= buffer.length;
        this.buffer = buffer;
        this.offset = offset;
        this.length = length;

    }

    public static CodePoints fromBuffer(int[] buffer, int offset, int length) {
        return length == 0 ? EMPTY : new CodePoints(buffer, offset, length);
    }

    public static CodePoints fromJavaString(String s) {
        Builder b = new Builder(s.length());
        int o = 0;
        while (o < s.length()) {
            int cp = s.codePointAt(o);
            b.appendCodePoint(cp);
            o += Character.charCount(cp);
        }
        return b.build();
    }

    public int[] getBuffer() {
        return buffer;
    }

    public int getOffset() {
        return offset;
    }

    public int getLength() {
        return length;
    }

    public boolean isEmpty() {
        return length == 0;
    }

    public String toJavaString() {
        return new String(buffer, offset, length);
    }

    private int indexOf(int cp) {
        for (int i = 0; i < length; ++i) {
            if (buffer[offset + i] == cp) {
                return i;
            }
        }
        return -1;
    }

    public boolean contains(int cp) {
        return indexOf(cp) >= 0;
    }

    public int get(int index) {
        assert index >= 0 && index < length;
        return buffer[offset + index];
    }

    public CodePoints withLength(int l) {
        assert 0 <= l && l <= length;
        return l == length ? this : new CodePoints(buffer, offset, l);
    }

    public static final class Builder {
        private int[] buffer;
        private int length;

        public Builder(int initialCapacity) {
            buffer = new int[initialCapacity];
        }

        public void appendCodePoint(int codePoint) {
            ensureCanAppend(1);
            buffer[length++] = codePoint;
        }

        public void appendCodePoints(int[] srcBuffer, int srcOffset, int srcLength) {
            ensureCanAppend(srcLength);
            System.arraycopy(srcBuffer, srcOffset, buffer, length, srcLength);
            length += srcLength;
        }

        public void appendCodePoints(CodePoints cp) {
            appendCodePoints(cp.buffer, cp.offset, cp.length);
        }

        public CodePoints build() {
            return length == 0 ? EMPTY : new CodePoints(buffer, 0, length);
        }

        private void ensureCanAppend(int delta) {
            int newLength = length + delta;
            if (newLength > buffer.length) {
                newLength = Math.max(newLength, buffer.length + buffer.length >> 1);
                buffer = Arrays.copyOf(buffer, newLength);
            }
        }
    }

}
