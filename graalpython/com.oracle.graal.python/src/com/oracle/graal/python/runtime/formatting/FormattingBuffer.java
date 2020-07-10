/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.runtime.formatting;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.IntStream;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

/**
 * Common interface for a buffer used to accumulate the result by {@link FormatProcessor}.
 */
abstract class FormattingBuffer implements CharSequence {
    public abstract FormattingBuffer append(char c);

    public final FormattingBuffer append(int value) {
        return append(Integer.toString(value));
    }

    public abstract FormattingBuffer append(CharSequence str);

    public abstract FormattingBuffer append(CharSequence str, int start, int end);

    public abstract FormattingBuffer appendCodePoint(int value);

    public abstract FormattingBuffer setCharAt(int index, char c);

    public abstract FormattingBuffer replace(int start, int end, String str);

    public abstract FormattingBuffer insert(int index, char c);

    public abstract FormattingBuffer setLength(int newLength);

    @Override
    public abstract int length();

    public abstract String substring(int len);

    public abstract int codePointCount(int mark, int length);

    public abstract Object toResult();

    @Override
    public abstract String toString();

    public abstract FormattingBuffer ensureCapacity(int capacity);

    public final FormattingBuffer ensureAdditionalCapacity(int additional) {
        int newCapacity = length() + additional;
        if (newCapacity < 0) {
            // overflow
            throw new OutOfMemoryError();
        }
        return ensureCapacity(newCapacity);
    }

    static final class StringFormattingBuffer extends FormattingBuffer {
        private final StringBuilder buffer;

        public StringFormattingBuffer() {
            buffer = new StringBuilder();
        }

        public StringFormattingBuffer(int capacity) {
            buffer = new StringBuilder(capacity);
        }

        @Override
        public FormattingBuffer append(char c) {
            buffer.append(c);
            return this;
        }

        @Override
        public FormattingBuffer append(CharSequence str) {
            buffer.append(str);
            return this;
        }

        @Override
        public FormattingBuffer append(CharSequence str, int start, int end) {
            buffer.append(str, start, end);
            return this;
        }

        @Override
        public FormattingBuffer insert(int index, char c) {
            buffer.insert(index, c);
            return this;
        }

        @Override
        public FormattingBuffer setCharAt(int index, char c) {
            buffer.setCharAt(index, c);
            return this;
        }

        @Override
        public FormattingBuffer replace(int start, int end, String str) {
            buffer.replace(start, end, str);
            return this;
        }

        @Override
        public FormattingBuffer setLength(int newLength) {
            buffer.setLength(newLength);
            return this;
        }

        @Override
        public int length() {
            return buffer.length();
        }

        @Override
        public String toResult() {
            return buffer.toString();
        }

        @Override
        public String toString() {
            return toResult();
        }

        @Override
        public IntStream chars() {
            return buffer.chars();
        }

        @Override
        public IntStream codePoints() {
            return buffer.codePoints();
        }

        @Override
        public char charAt(int index) {
            return buffer.charAt(index);
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return buffer.subSequence(start, end);
        }

        @Override
        public FormattingBuffer appendCodePoint(int value) {
            buffer.appendCodePoint(value);
            return this;
        }

        @Override
        public String substring(int len) {
            return buffer.substring(len);
        }

        @Override
        public int codePointCount(int mark, int length) {
            return buffer.codePointCount(mark, length);
        }

        @Override
        public FormattingBuffer ensureCapacity(int capacity) {
            buffer.ensureCapacity(capacity);
            return this;
        }
    }

    static final class BytesFormattingBuffer extends FormattingBuffer {
        private byte[] data = new byte[32];
        private int size;

        @Override
        public FormattingBuffer append(char c) {
            ensureCapacity(size, 1);
            data[size++] = (byte) c;
            return this;
        }

        @Override
        public FormattingBuffer append(CharSequence str) {
            return append(str.toString().getBytes(StandardCharsets.US_ASCII));
        }

        @Override
        public FormattingBuffer append(CharSequence str, int start, int end) {
            byte[] bytes = str.subSequence(start, end).toString().getBytes(StandardCharsets.US_ASCII);
            return append(bytes);
        }

        public FormattingBuffer append(byte[] bytes) {
            ensureCapacity(size, bytes.length);
            System.arraycopy(bytes, 0, data, size, bytes.length);
            size += bytes.length;
            return this;
        }

        @Override
        public FormattingBuffer appendCodePoint(int value) {
            append((char) value);
            return this;
        }

        @Override
        public FormattingBuffer setCharAt(int index, char c) {
            ensureCapacity(index, 1);
            size = Math.max(index + 1, size);
            data[index] = (byte) c;
            return this;
        }

        @Override
        public FormattingBuffer replace(int start, int end, String str) {
            ensureCapacity(end);
            byte[] bytes = str.getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(bytes, 0, data, start, Math.min(bytes.length, end - start));
            return this;
        }

        @Override
        public FormattingBuffer insert(int index, char c) {
            ensureCapacity(size, 1);
            System.arraycopy(data, index, data, index + 1, size - index);
            data[index] = (byte) c;
            size++;
            return this;
        }

        @Override
        public FormattingBuffer setLength(int newLength) {
            size = newLength;
            return this;
        }

        @Override
        public int length() {
            return size;
        }

        @Override
        public char charAt(int index) {
            return (char) data[index];
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return new String(Arrays.copyOfRange(data, start, end), StandardCharsets.US_ASCII);
        }

        @Override
        public String substring(int index) {
            return new String(Arrays.copyOfRange(data, index, size), StandardCharsets.US_ASCII);
        }

        @Override
        public int codePointCount(int mark, int length) {
            return length - mark;
        }

        @Override
        public byte[] toResult() {
            return Arrays.copyOf(data, size);
        }

        @Override
        @TruffleBoundary
        public String toString() {
            // TruffleBoundary is a defensive measure here, this should really not be called, but
            // putting an exception here messes Java debuggers, which call toString
            return Objects.toString(this);
        }

        @Override
        public FormattingBuffer ensureCapacity(int capacity) {
            if (capacity < 0) {
                throw new IllegalArgumentException();
            }
            ensureCapacity(capacity, 0);
            return this;
        }

        private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - Long.BYTES;

        public void ensureCapacity(int capacity, int add) {
            assert capacity >= 0;
            assert add >= 0;
            int newCapacity = capacity + add;
            if (newCapacity < capacity || newCapacity > MAX_ARRAY_SIZE) {
                // overflow or too large
                throw new OutOfMemoryError();
            }
            if (data.length < newCapacity) {
                data = Arrays.copyOf(data, newCapacity(newCapacity));
            }
        }

        private int newCapacity(int minCapacity) {
            int newCapacity = (data.length << 1) + 2;
            if (newCapacity < data.length || newCapacity > MAX_ARRAY_SIZE) {
                // overflow or too large
                // do not use minCapacity or we may be growing, e.g., one by one for too long
                return MAX_ARRAY_SIZE;
            }
            return Math.max(newCapacity, minCapacity);
        }
    }
}
