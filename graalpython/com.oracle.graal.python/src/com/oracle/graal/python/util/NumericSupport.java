/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.util;

import static com.oracle.graal.python.nodes.ErrorMessages.FLOAT_TO_LARGE_TO_PACK_WITH_S_FMT;
import static com.oracle.graal.python.nodes.ErrorMessages.RES_O_O_RANGE;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.SystemError;

import java.math.BigInteger;

import com.oracle.graal.python.builtins.modules.MathModuleBuiltins;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.memory.ByteArraySupport;
import com.oracle.truffle.api.nodes.Node;

public final class NumericSupport {
    private static final long NEG_ZERO_RAWBITS = Double.doubleToRawLongBits(-0.0);
    private static final double EPSILON = .00000000000000001;
    private final ByteArraySupport support;
    private final boolean bigEndian;

    private NumericSupport(boolean bigEndian) {
        this.support = bigEndian ? ByteArraySupport.bigEndian() : ByteArraySupport.littleEndian();
        this.bigEndian = bigEndian;
    }

    private static final NumericSupport BE_NUM_SUPPORT = new NumericSupport(true);
    private static final NumericSupport LE_NUM_SUPPORT = new NumericSupport(false);

    public static NumericSupport bigEndian() {
        return BE_NUM_SUPPORT;
    }

    public static NumericSupport littleEndian() {
        return LE_NUM_SUPPORT;
    }

    private static void reverse(byte[] buffer) {
        reverse(buffer, 0, buffer.length);
    }

    private static void reverse(byte[] buffer, int offset, int numBytes) {
        CompilerAsserts.partialEvaluationConstant(numBytes);
        assert offset + numBytes <= buffer.length : "cannot reverse byte array, offset + numBytes exceeds byte array length";
        int a, b;
        byte tmp;
        for (int i = 0; i < (numBytes / 2); i++) {
            a = i + offset;
            b = (numBytes - i - 1) + offset;
            tmp = buffer[a];
            buffer[a] = buffer[b];
            buffer[b] = tmp;
        }
    }

    public static boolean equalsApprox(double a, double b) {
        return Math.abs(a - b) < EPSILON;
    }

    @TruffleBoundary
    static short floatToShortBits(double value, Node raisingNode) {
        int sign;
        int e;
        double f;
        short bits;

        final double signum = Math.signum(value);
        if (value == 0.0f) {
            sign = Double.doubleToRawLongBits(value) == NEG_ZERO_RAWBITS ? 1 : 0;
            e = 0;
            bits = 0;
        } else if (Double.isInfinite(value)) {
            sign = (signum == -1.0f) ? 1 : 0;
            e = 0x1f;
            bits = 0;
        } else if (Double.isNaN(value)) {
            sign = (signum == -1.0f) ? 1 : 0;
            e = 0x1f;
            bits = 512;
        } else {
            sign = (value < 0.0f) ? 1 : 0;
            double v = (sign == 1) ? -value : value;
            double[] fraction = MathModuleBuiltins.FrexpNode.frexp(v);
            f = fraction[0];
            e = (int) fraction[1];

            if (f < 0.5 || f >= 1.0) {
                throw PRaiseNode.raiseUncached(raisingNode, SystemError, RES_O_O_RANGE, "frexp()");
            }

            // Normalize f to be in the range [1.0, 2.0)
            f *= 2.0;
            e--;

            if (e >= 16) {
                throw PRaiseNode.raiseUncached(raisingNode, OverflowError, FLOAT_TO_LARGE_TO_PACK_WITH_S_FMT, "e");
            } else if (e < -25) {
                // |x| < 2**-25. Underflow to zero.
                f = 0.0;
                e = 0;
            } else if (e < -14) {
                // |x| < 2**-14. Gradual underflow
                f = Math.scalb(f, 14 + e);
                e = 0;
            } else {
                e += 15;
                f -= 1.0; // Get rid of leading 1
            }

            f *= 1024.0; // 2**10
            // Round to even
            bits = (short) f; // Note the truncation
            assert bits < 1024;
            assert e < 31;

            if ((f - bits > 0.5) || (equalsApprox(f - bits, 0.5) && ((bits & 1) != 0))) {
                ++bits;
                if (bits == 1024) {
                    // The carry propagated out of a string of 10 1 bits.
                    bits = 0;
                    ++e;
                    if (e == 31) {
                        throw PRaiseNode.raiseUncached(raisingNode, OverflowError, FLOAT_TO_LARGE_TO_PACK_WITH_S_FMT, "e");
                    }
                }
            }
        }

        bits |= (short) ((e << 10) | (sign << 15));
        return bits;
    }

    @TruffleBoundary
    static float shortBitsToFloat(short bits) {
        int sign;
        int e;
        int f;
        float value;

        sign = (bits & 0x8000) >> 15;
        e = (bits & 0x7C00) >> 10;
        f = bits & 0x03ff;

        if (e == 0x1f) {
            if (f == 0) {
                return (sign == 1) ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY;
            } else {
                return (sign == 1) ? -Float.NaN : Float.NaN;
            }
        }

        value = f / 1024.0f;

        if (e == 0) {
            e = -14;
        } else {
            value += (float) 1.0;
            e -= 15;
        }
        value = Math.scalb(value, e);

        if (sign == 1) {
            value = -value;
        }

        return value;
    }

    public byte getByte(byte[] buffer, int index) throws IndexOutOfBoundsException {
        return support.getByte(buffer, index);
    }

    public void putByte(byte[] buffer, int index, byte value) throws IndexOutOfBoundsException {
        support.putByte(buffer, index, value);
    }

    public short getShort(byte[] buffer, int index) throws IndexOutOfBoundsException {
        return support.getShort(buffer, index);
    }

    public void putShort(byte[] buffer, int index, short value) throws IndexOutOfBoundsException {
        support.putShort(buffer, index, value);
    }

    public int getInt(byte[] buffer, int index) throws IndexOutOfBoundsException {
        return support.getInt(buffer, index);
    }

    public void putInt(byte[] buffer, int index, int value) throws IndexOutOfBoundsException {
        support.putInt(buffer, index, value);
    }

    public long getLong(byte[] buffer, int index) throws IndexOutOfBoundsException {
        return support.getLong(buffer, index);
    }

    public void putLong(byte[] buffer, int index, long value) throws IndexOutOfBoundsException {
        support.putLong(buffer, index, value);
    }

    public long getLong(byte[] buffer, int index, int numBytes) throws IndexOutOfBoundsException {
        switch (numBytes) {
            case 1:
                return getByte(buffer, index);
            case 2:
                return getShort(buffer, index);
            case 4:
                return getInt(buffer, index);
            case 8:
                return getLong(buffer, index);
            default:
                throw CompilerDirectives.shouldNotReachHere("number of bytes must be 1,2,4 or 8");
        }
    }

    public long getLongUnsigned(byte[] buffer, int index, int numBytes) throws IndexOutOfBoundsException {
        switch (numBytes) {
            case 1:
                return getByte(buffer, index) & 0x0000000000000ffL;
            case 2:
                return getShort(buffer, index) & 0x000000000000ffffL;
            case 4:
                return getInt(buffer, index) & 0x00000000ffffffffL;
            case 8:
                return getLong(buffer, index);
            default:
                throw CompilerDirectives.shouldNotReachHere("number of bytes must be 1,2,4 or 8");
        }
    }

    public void putLong(byte[] buffer, int index, long value, int numBytes) throws IndexOutOfBoundsException {
        switch (numBytes) {
            case 1:
                putByte(buffer, index, (byte) value);
                break;
            case 2:
                putShort(buffer, index, (short) value);
                break;
            case 4:
                putInt(buffer, index, (int) value);
                break;
            case 8:
                putLong(buffer, index, value);
                break;
            default:
                throw CompilerDirectives.shouldNotReachHere("number of bytes must be 1,2,4 or 8");
        }
    }

    public BigInteger getBigInteger(byte[] buffer, int index) {
        return getBigInteger(buffer, index, buffer.length - index);
    }

    @TruffleBoundary
    public BigInteger getBigInteger(byte[] buffer, int index, int numBytes) throws IndexOutOfBoundsException {
        assert numBytes <= buffer.length - index;
        final byte[] bytes;
        if (index == 0 && numBytes == buffer.length) {
            bytes = PythonUtils.arrayCopyOfRange(buffer, index, index + numBytes);
        } else {
            bytes = buffer;
        }
        // bytes are always in big endian order
        if (!bigEndian) {
            reverse(bytes);
        }
        return new BigInteger(bytes);
    }

    @TruffleBoundary
    public void putBigInteger(byte[] buffer, int index, BigInteger value, int numBytes) throws IndexOutOfBoundsException, OverflowException {
        assert numBytes <= buffer.length - index;
        // src byte array is always returned in big endian order
        final byte[] src = value.toByteArray();
        int srcIndex = 0;
        int srcBytes = src.length;
        if (srcBytes > numBytes) {
            for (int i = 0; i < src.length; i++) {
                srcIndex = i;
                if (src[i] != 0) {
                    break;
                }
            }
            srcBytes -= srcIndex;
            if (srcBytes > numBytes) {
                throw OverflowException.INSTANCE;
            }
        } else if (srcBytes < numBytes) {
            // perform sign extension
            if (value.signum() < 0) {
                for (int i = 0; i < numBytes - srcBytes; i++) {
                    buffer[i] = -1;
                }
            }
        }
        int dstIndex = index + (numBytes - srcBytes);
        PythonUtils.arraycopy(src, srcIndex, buffer, dstIndex, srcBytes);
        if (!bigEndian) {
            reverse(buffer, index, numBytes);
        }
    }

    public float getFloat(byte[] buffer, int index) throws IndexOutOfBoundsException {
        return support.getFloat(buffer, index);
    }

    public void putFloat(byte[] buffer, int index, float value) throws IndexOutOfBoundsException {
        support.putFloat(buffer, index, value);
    }

    public float getHalfFloat(byte[] buffer, int index) throws IndexOutOfBoundsException {
        final short bits = support.getShort(buffer, index);
        return shortBitsToFloat(bits);
    }

    public void putHalfFloat(byte[] buffer, int index, double value, Node raisingNode) throws IndexOutOfBoundsException {
        final short bits = floatToShortBits(value, raisingNode);
        support.putShort(buffer, index, bits);
    }

    public double getDouble(byte[] buffer, int index) throws IndexOutOfBoundsException {
        return support.getDouble(buffer, index);
    }

    public void putDouble(byte[] buffer, int index, double value) throws IndexOutOfBoundsException {
        support.putDouble(buffer, index, value);
    }

    public double getDouble(byte[] buffer, int index, int numBytes) throws IndexOutOfBoundsException {
        switch (numBytes) {
            case 2:
                return getHalfFloat(buffer, index);
            case 4:
                return getFloat(buffer, index);
            case 8:
                return getDouble(buffer, index);
            default:
                throw CompilerDirectives.shouldNotReachHere("number of bytes must be 2,4 or 8");
        }
    }

    public void putDouble(Node inliningTarget, byte[] buffer, int index, double value, int numBytes, PRaiseNode.Lazy raiseNode) throws IndexOutOfBoundsException {
        switch (numBytes) {
            case 2:
                putHalfFloat(buffer, index, value, inliningTarget);
                break;
            case 4:
                final float fValue = (float) value;
                if (Float.isInfinite(fValue)) {
                    throw raiseNode.get(inliningTarget).raise(OverflowError, FLOAT_TO_LARGE_TO_PACK_WITH_S_FMT, "f");
                }
                putFloat(buffer, index, fValue);
                break;
            case 8:
                putDouble(buffer, index, value);
                break;
            default:
                throw CompilerDirectives.shouldNotReachHere("number of bytes must be 2,4 or 8");
        }
    }

    public static short asUnsigned(byte value) {
        return (short) (value & 0x00ff);
    }

    public static int asUnsigned(short value) {
        return value & 0x0000ffff;
    }

    public static long asUnsigned(int value) {
        return value & 0x00000000ffffffffL;
    }

    @TruffleBoundary
    public static BigInteger asUnsigned(long value) {
        if (value >= 0L) {
            return BigInteger.valueOf(value);
        } else {
            int upper = (int) (value >>> 32);
            int lower = (int) value;

            // return (upper << 32) + lower
            return (BigInteger.valueOf(Integer.toUnsignedLong(upper))).shiftLeft(32).add(BigInteger.valueOf(Integer.toUnsignedLong(lower)));
        }
    }
}
