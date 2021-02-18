/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.ints;

import java.math.BigInteger;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.nodes.ExplodeLoop;

public final class IntUtils {
    public static void reverse(byte[] values) {
        reverse(values, 0, values.length);
    }

    @ExplodeLoop
    public static void reverse(byte[] values, int offset, int count) {
        CompilerAsserts.partialEvaluationConstant(count);
        assert offset + count <= values.length : "cannot reverse byte array, offset + count exceeds byte array length";
        int a, b;
        byte tmp;
        for (int i = 0; i < (count / 2); i++) {
            a = i + offset;
            b = (count - i - 1) + offset;
            tmp = values[a];
            values[a] = values[b];
            values[b] = tmp;
        }
    }

    @ExplodeLoop
    public static void longToByteArray(long value, int size, byte[] dst, int offset) {
        CompilerAsserts.partialEvaluationConstant(size);
        assert dst.length - offset <= size;
        for (int i = 0; i < size; i++) {
            dst[offset + i] = (byte) ((value >> (8 * i)) & 0xff);
        }
    }

    public static byte[] longToByteArray(long value, int size) {
        byte[] bytes = new byte[size];
        longToByteArray(value, size, bytes, 0);
        return bytes;
    }

    public static void longToByteArray(long value, int size, boolean bigEndian, byte[] dst, int offset) {
        longToByteArray(value, size, dst, offset);
        if (bigEndian) {
            reverse(dst, offset, size);
        }
    }

    public static byte[] longToByteArray(long value, int size, boolean bigEndian) {
        byte[] bytes = new byte[size];
        longToByteArray(value, size, bigEndian, bytes,0);
        return bytes;
    }

    @CompilerDirectives.TruffleBoundary
    public static byte[] bigIntToByteArray(BigInteger value, int size) {
        byte[] bytes = new byte[size];
        for (int i = 0; i < size; i++) {
            bytes[i] = value.shiftRight(8 * i).byteValue();
        }
        return bytes;
    }

    @ExplodeLoop
    public static long byteArrayToLong(byte[] bytes, int size, int offset) {
        CompilerAsserts.partialEvaluationConstant(size);
        long value = 0L;
        for (int i = size - 1; i >= 0; i--) {
            value |= ((long) (bytes[offset + i] & 0xff)) << (8 * (size - 1 - i));
        }
        return value;
    }
}
