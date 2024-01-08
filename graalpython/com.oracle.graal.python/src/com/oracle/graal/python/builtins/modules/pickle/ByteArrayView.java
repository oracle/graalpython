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
package com.oracle.graal.python.builtins.modules.pickle;

import com.oracle.graal.python.util.PythonUtils;

public class ByteArrayView {
    private final byte[] bytes;
    private int offset;

    public ByteArrayView(byte[] bytes) {
        this(bytes, 0);
    }

    public ByteArrayView(byte[] bytes, int offset) {
        this.bytes = bytes;
        this.offset = offset;
        validateOffset(0);
    }

    private void validateOffset(int n) {
        assert offset + n >= 0 && offset + n <= bytes.length;
    }

    public void add(int n) {
        validateOffset(n);
        this.offset += n;
    }

    public void sub(int n) {
        this.add(-n);
    }

    public byte[] getBytes(int len) {
        assert len >= 0 && (len + offset) >= 0 && (len + offset) <= bytes.length;
        if (offset == 0 && len == bytes.length) {
            return bytes;
        }
        byte[] arr = new byte[len];
        PythonUtils.arraycopy(this.bytes, this.offset, arr, 0, len);
        return arr;
    }

    public int getOffset() {
        return offset;
    }

    public byte get(int i) {
        return this.bytes[this.offset + i];
    }

    public int getUnsigned(int i) {
        return get(i) & 0xff;
    }

    public void put(int i, byte value) {
        this.bytes[this.offset + i] = value;
    }

    public void writeSize64(int value) {
        PickleUtils.writeSize64(bytes, offset, value);
    }

    public void memmove(int off, int num) {
        PythonUtils.arraycopy(this.bytes, this.offset + off, this.bytes, this.offset, num);
    }
}
