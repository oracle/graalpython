package com.oracle.graal.python.util;

import static com.oracle.graal.python.nodes.ErrorMessages.FLOAT_TO_LARGE_TO_PACK_WITH_E_FMT;
import static com.oracle.graal.python.nodes.ErrorMessages.RES_O_O_RANGE;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.SystemError;

import java.math.BigInteger;

import com.oracle.graal.python.builtins.modules.MathModuleBuiltins;
import com.oracle.graal.python.nodes.PNodeWithRaise;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.memory.ByteArraySupport;
import com.oracle.truffle.api.nodes.ExplodeLoop;

public final class NumericSupport {
    private static final double EPSILON = .0000001;
    private static final short FP16_INFINITY = (short) 0x7c00;
    private static final short FP16_NEGATIVE_INFINITY = (short) 0xfc00;
    private final ByteArraySupport support;
    private final boolean reversed;

    private NumericSupport(boolean bigEndian) {
        this.support = bigEndian ? ByteArraySupport.bigEndian(): ByteArraySupport.littleEndian();
        this.reversed = !bigEndian;
    }

    private final static NumericSupport BE_NUM_SUPPORT = new NumericSupport(true);
    private final static NumericSupport LE_NUM_SUPPORT = new NumericSupport(false);

    public static NumericSupport bigEndian() {
        return BE_NUM_SUPPORT;
    }

    public static NumericSupport littleEndian() {
        return LE_NUM_SUPPORT;
    }

    @ExplodeLoop
    static void reverse(byte[] buffer, int offset, int numBytes) {
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

    static boolean equalsWithinRange(double a, double b) {
        return Math.abs(a - b) < EPSILON;
    }

    @TruffleBoundary
    static short floatToShortBits(PNodeWithRaise nodeWithRaise, float value) {
        int sign;
        int e;
        double f;
        short bits;

        if (value == 0.0f) {
            sign = (Math.signum(value) == -1.0f) ? 1 : 0;
            e = 0;
            bits = 0;
        } else if (Float.isInfinite(value)) {
            sign = (value < 0.0f) ? 1 : 0;
            e = 0x1f;
            bits = 0;
        } else if (Float.isNaN(value)) {
            sign = (Math.signum(value) == -1.0f) ? 1 : 0;
            e = 0x1f;
            bits = 512;
        } else {
            sign = (value < 0.0f) ? 1 : 0;
            float v = (sign == 1) ? -value : value;
            double[] fraction = MathModuleBuiltins.FrexpNode.frexp(v);
            f = fraction[0];
            e = (int) fraction[1];

            if (f < 0.5 || f >= 1.0) {
                throw nodeWithRaise.raise(SystemError, RES_O_O_RANGE, "frexp()");
            }

            /* Normalize f to be in the range [1.0, 2.0) */
            f *= 2.0;
            e--;

            if (e >= 16) {
                throw nodeWithRaise.raise(OverflowError, FLOAT_TO_LARGE_TO_PACK_WITH_E_FMT);
            } else if (e < -25) {
                /* |x| < 2**-25. Underflow to zero. */
                f = 0.0;
                e = 0;
            } else if (e < -14) {
                /* |x| < 2**-14. Gradual underflow */
                f = Math.scalb(f, 14 + e);
                e = 0;
            } else /* if (!(e == 0 && f == 0.0)) */ {
                e += 15;
                f -= 1.0; /* Get rid of leading 1 */
            }

            f *= 1024.0; /* 2**10 */
            /* Round to even */
            bits = (short) f; /* Note the truncation */
            assert bits < 1024;
            assert e < 31;

            if ((f - bits > 0.5) || ((equalsWithinRange(f - bits, 0.5)) && ((bits & 1) != 0))) {
                ++bits;
                if (bits == 1024) {
                    /* The carry propagated out of a string of 10 1 bits. */
                    bits = 0;
                    ++e;
                    if (e == 31) {
                        throw nodeWithRaise.raise(OverflowError, FLOAT_TO_LARGE_TO_PACK_WITH_E_FMT);
                    }
                }
            }
        }

        bits |= (e << 10) | (sign << 15);
        return bits;
    }

    @TruffleBoundary
    static float shortBitsToFloat(short bits) {
        int sign;
        int e;
        int f;
        float value;

        sign = (bits >> 7) & 1;
        e = (bits & 0x7C00) >> 10;
        f = bits & 0x03ff;

        if (e == 0x1f) {
            if (f == 0) {
                return (sign == 1) ? FP16_NEGATIVE_INFINITY : FP16_INFINITY;
            } else {
                return (sign == 1) ? -Float.NaN : Float.NaN;
            }
        }

        value = f / 1024.0f;

        if (e == 0) {
            e = -14;
        } else {
            value += 1.0;
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

    @ExplodeLoop
    private static long getLongInternal(byte[] buffer, int index, int numBytes) throws IndexOutOfBoundsException {
        CompilerAsserts.partialEvaluationConstant(numBytes);
        long value = 0L;
        for (int i = 0; i < numBytes; i++) {
            value |= ((long) (buffer[i + index] & 0xFF)) << (Byte.SIZE * (numBytes - 1 - i));
        }
        return value;
    }

    public long getLong(byte[] buffer, int index, int numBytes) throws IndexOutOfBoundsException {
        assert numBytes <= buffer.length - index;
        if (reversed) {
            reverse(buffer, index, numBytes);
        }
        return getLongInternal(buffer, index, numBytes);
    }

    @ExplodeLoop
    private static void putLongInternal(byte[] buffer, int index, long value, int numBytes) throws IndexOutOfBoundsException {
        CompilerAsserts.partialEvaluationConstant(numBytes);
        for (int i = 0; i < numBytes; i++) {
            buffer[i + index] = (byte) ((value >> (Byte.SIZE * (numBytes - 1 - i))) & 0xFF);
        }
    }

    public void putLong(byte[] buffer, int index, long value, int numBytes) throws IndexOutOfBoundsException {
        assert numBytes <= buffer.length - index;
        putLongInternal(buffer, index, value, numBytes);
        if (reversed) {
            reverse(buffer, index, numBytes);
        }
    }

    @TruffleBoundary
    public BigInteger getBigInteger(byte[] buffer, int index, int numBytes) throws IndexOutOfBoundsException{
        assert numBytes <= buffer.length - index;
        if (reversed) {
            reverse(buffer, index, numBytes);
        }

        BigInteger value = BigInteger.ZERO;
        for (int i = 0; i < numBytes; i++) {
            final long longVal = ((long) (buffer[index + i] & 0xFF)) << (Byte.SIZE * (numBytes - 1 - i));
            value = value.or(BigInteger.valueOf(longVal));
        }
        return value;
    }

    @TruffleBoundary
    public void putBigInteger(byte[] buffer, int index, BigInteger value, int numBytes) throws IndexOutOfBoundsException{
        assert numBytes <= buffer.length - index;
        for (int i = 0; i < numBytes; i++) {
            buffer[index + i] = value.shiftRight(Byte.SIZE * (numBytes - 1 - i)).byteValue();
        }
        if (reversed) {
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

    public void putHalfFloat(PNodeWithRaise node, byte[] buffer, int index, float value) throws IndexOutOfBoundsException {
        final short bits = floatToShortBits(node, value);
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
                throw new IllegalStateException("number of bytes must be 2,4 or 8");
        }
    }

    public void putDouble(PNodeWithRaise node, byte[] buffer, int index, double value, int numBytes) throws IndexOutOfBoundsException {
        switch (numBytes) {
            case 2:
                putHalfFloat(node, buffer, index, (float) value);
                break;
            case 4:
                putFloat(buffer, index, (float) value);
                break;
            case 8:
                putDouble(buffer, index, value);
                break;
            default:
                throw new IllegalStateException("number of bytes must be 2,4 or 8");
        }
    }
}
