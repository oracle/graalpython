/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.hpy;

public class GraalHPyBoxing {

    // see the corresponding implementation in hpy_native.c

    /*-
     * This NaN boxing mechanism puts all non-double values into the range
     * [0 - NAN_BOXING_BASE[. Boxing a double value adds NAN_BOXING_BASE, and
     * unboxing a double value subtracts NAN_BOXING_BASE.
     * Therefore, unboxing the non-double values ends up in the range
     * [fff9_0000_0000_0000 - ffff_ffff_ffff_ffff], which are non-standard
     * quiet NaNs with sign bit - these don't appear in normal calculations.
     *
     * The range [0 - 7fff_ffff] is currently used for HPy handles,
     * and the range [0001_0000_0000_0000 - 0001_0000_ffff_ffff] is currently
     * used to represent primitive integers.
     *
     * There is space left to add other types and extend the bit size of
     * handles and integers.
     */

    private static final long NAN_BOXING_BASE = 0x0007_0000_0000_0000L;
    private static final long NAN_BOXING_MASK = 0xFFFF_0000_0000_0000L;
    private static final long NAN_BOXING_INT = 0x0001_0000_0000_0000L;

    private static final long NAN_BOXING_INT_MASK = 0x00000000FFFFFFFFL;
    private static final long NAN_BOXING_MAX_HANDLE = Integer.MAX_VALUE;

    public static boolean isBoxedDouble(long value) {
        return Long.compareUnsigned(value, NAN_BOXING_BASE) >= 0;
    }

    public static boolean isBoxedHandle(long value) {
        return Long.compareUnsigned(value, NAN_BOXING_MAX_HANDLE) <= 0;
    }

    public static boolean isBoxedInt(long value) {
        return (value & NAN_BOXING_MASK) == NAN_BOXING_INT;
    }

    public static boolean isBoxedNullHandle(long value) {
        return value == 0;
    }

    public static int unboxHandle(long value) {
        return (int) value;
    }

    public static long boxHandle(int handle) {
        return handle;
    }

    public static double unboxDouble(long value) {
        return Double.longBitsToDouble(value - NAN_BOXING_BASE);
    }

    public static long boxDouble(double value) {
        // assumes that value doesn't contain non-standard silent NaNs
        assert Long.compareUnsigned(Double.doubleToRawLongBits(value) + NAN_BOXING_BASE, NAN_BOXING_BASE) >= 0;

        long doubleBits = Double.doubleToRawLongBits(value);
        return doubleBits + NAN_BOXING_BASE;
    }

    public static int unboxInt(long value) {
        return (int) (value - NAN_BOXING_INT);
    }

    public static long boxInt(int value) {
        return (value & NAN_BOXING_INT_MASK) + NAN_BOXING_INT;
    }

    public static boolean isBoxablePrimitive(Object value) {
        return value instanceof Double || value instanceof Integer;
    }
}
