/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.floats;

import static com.oracle.graal.python.nodes.ErrorMessages.FLOAT_TO_LARGE_TO_PACK_WITH_E_FMT;
import static com.oracle.graal.python.nodes.ErrorMessages.RES_O_O_RANGE;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.SystemError;

import java.math.BigDecimal;

import com.oracle.graal.python.builtins.modules.MathModuleBuiltins;
import com.oracle.graal.python.builtins.objects.ints.IntUtils;
import com.oracle.graal.python.nodes.PNodeWithRaise;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.CompilerDirectives.ValueType;

/**
 * Contains helper methods for parsing float numbers in float() and complex() constructors.
 */
public final class FloatUtils {
    private static final double FLOAT_ROUNDING_SCALE = Math.pow(10, 7);
    private static final double EPSILON = .0000001;
    private static final short FP16_INFINITY = (short) 0x7c00;
    private static final short FP16_NEGATIVE_INFINITY = (short) 0xfc00;

    public static int skipAsciiWhitespace(String str, int start, int len) {
        int offset = start;
        while (offset < len && isAsciiSpace(str.charAt(offset))) {
            offset++;
        }
        return offset;
    }

    // Implements logic of Py_ISSPACE
    private static boolean isAsciiSpace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == 0x0b || c == '\f' || c == '\r';
    }

    /**
     * Replaces unicode decimal digits and whitespace, checks the any underscores are preceeded and
     * followed by a digit and removes them. Does not create a copy if the original String does not
     * need any cleanup. Combines _PyUnicode_TransformDecimalAndSpaceToASCII and
     * _Py_string_to_number_with_underscores.
     *
     * @param src the String to transform
     * @return the transformed String, {@code src} if the input does not need cleanup or
     *         {@code null} if there are invalid underscores or unicode characters other than
     *         whitespace or decimal digits
     */
    @TruffleBoundary
    public static String removeUnicodeAndUnderscores(String src) {
        if (!needsCleanup(src)) {
            return src;
        }
        int len = src.length();
        StringBuilder sb = new StringBuilder(len);
        char prev = '\0';
        for (int i = 0; i < len; ++i) {
            char ch = src.charAt(i);
            if (ch == '_') {
                if (!(prev >= '0' && prev <= '9')) {
                    return null;
                }
            } else {
                if (Character.isWhitespace(ch)) {
                    ch = ' ';
                } else if (ch >= 127) {
                    int digit = Character.digit(ch, 10);
                    if (digit < 0) {
                        return null;
                    }
                    ch = Character.forDigit(digit, 10);
                }
                sb.append(ch);
                if (prev == '_' && !(ch >= '0' && ch <= '9')) {
                    return null;
                }
            }
            prev = ch;
        }
        if (prev == '_') {
            return null;
        }
        return sb.toString();
    }

    private static boolean needsCleanup(String src) {
        int len = src.length();
        for (int i = 0; i < len; ++i) {
            char ch = src.charAt(i);
            if (ch > 127 || ch == '_') {
                return true;
            }
        }
        return false;
    }

    @ValueType
    public static class StringToDoubleResult {
        public final double value;
        public final int position;

        public StringToDoubleResult(double value, int position) {
            this.value = value;
            this.position = position;
        }
    }

    /**
     * Attempts to parse a float literal (as defined by Python). Unlike
     * {@link Double#parseDouble(String)}:
     * <ul>
     * <li>tolerates additional characters in the string and reports the position of the first
     * unconsumed character (similar to strtod)</li>
     * <li>does not accept hexadecimal literals</li>
     * <li>does not accept whitespace</li>
     * <li>differentiates between negative and positive NaN</li>
     * <li>besides "infinity" accepts "inf" as well</li>
     * <li>does not throw (but returns {@code null} on error)</li>
     * </ul>
     * Implements PyOS_string_to_double and _PyOS_ascii_strtod except error handling and handling of
     * locale-specific decimal point.
     *
     * @param str the string to parse
     * @param start starting position in the string
     * @param len length of the string
     * @return the parsed value and the position of the first unparsed character or {@code null} if
     *         there is no valid float literal
     */
    @TruffleBoundary
    public static StringToDoubleResult stringToDouble(String str, int start, int len) {
        boolean negate = false;
        int i = start;

        if (i >= len) {
            return null;
        }
        char firstChar = str.charAt(i);
        if (firstChar == '-' || firstChar == '+') {
            negate = firstChar == '-';
            if (++i >= len) {
                return null;
            }
            firstChar = str.charAt(i);
        }

        if (firstChar != '.' && !(firstChar >= '0' && firstChar <= '9')) {
            if (str.regionMatches(true, i, "inf", 0, 3)) {
                i += 3;
                if (str.regionMatches(true, i, "inity", 0, 5)) {
                    i += 5;
                }
                return new StringToDoubleResult(negate ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY, i);
            } else if (str.regionMatches(true, i, "nan", 0, 3)) {
                i += 3;
                return new StringToDoubleResult(negate ? Math.copySign(Double.NaN, -1) : Double.NaN, i);
            } else {
                return null;
            }
        }

        boolean seenDot = false;        // there must be at most one decimal point
        boolean seenDigit = false;      // there must be at least one digit

        while (i < len) {
            char ch = str.charAt(i);
            if (ch >= '0' && ch <= '9') {
                seenDigit = true;
            } else if (ch == '.') {
                if (seenDot) {
                    return null;
                }
                seenDot = true;
            } else if (ch == 'e' || ch == 'E') {
                if (!seenDigit) {
                    return null;
                }
                if (i + 1 < len) {
                    char ch2 = str.charAt(i + 1);
                    if (ch2 == '+' || ch2 == '-') {
                        i++;
                    }
                }
                seenDigit = false;  // there must be at least one digit in the exponent
                seenDot = true;     // there must be no decimal point in the exponent
            } else {
                break;
            }
            i++;
        }
        if (!seenDigit) {
            return null;
        }
        try {
            String substr = str.substring(start, i);
            double d = parseValidString(substr);
            return new StringToDoubleResult(d, i);
        } catch (NumberFormatException e) {
            // Should not happen since the input to Double.parseDouble() / BigDecimal(String) should
            // be correct
            return null;
        }
    }

    /**
     * Parses a string that contains a valid string representation of a float number.
     */
    public static double parseValidString(String substr) {
        double d = Double.parseDouble(substr);
        if (!Double.isFinite(d)) {
            d = new BigDecimal(substr).doubleValue();
        }
        return d;
    }

    @SuppressWarnings("unused")
    public static double floatRoundWithFixedDecimals(float value) {
        return Math.round(value * FLOAT_ROUNDING_SCALE) / FLOAT_ROUNDING_SCALE;
    }

    public static boolean equalsWithinRange(double a, double b) {
        return Math.abs(a - b) < EPSILON;
    }

    @TruffleBoundary
    public static short floatToShortBits(PNodeWithRaise nodeWithRaise, float value) {
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
    public static float shortBitsToFloat(short bits) {
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

    // variations of the _PyFloat_Pack2, _PyFloat_Pack4 and _PyFloat_Pack8
    public static void floatToBytes(double value, byte[] dst, int offset) {
        final long bits = Double.doubleToLongBits(value);
        IntUtils.longToByteArray(bits, Double.BYTES, dst, offset);
    }

    public static byte[] floatToBytes(double value) {
        byte[] bytes = new byte[Double.BYTES];
        floatToBytes(value, bytes, 0);
        return bytes;
    }

    public static void floatToBytes(float value, byte[] dst, int offset) {
        final long bits = Float.floatToIntBits(value);
        IntUtils.longToByteArray(bits, Float.BYTES, dst, offset);
    }

    public static byte[] floatToBytes(float value) {
        byte[] bytes = new byte[Float.BYTES];
        floatToBytes(value, bytes, 0);
        return bytes;
    }

    public static void halfFloatToBytes(PNodeWithRaise nodeWithRaise, float value, byte[] dst, int offset) {
        final short bits = floatToShortBits(nodeWithRaise, value);
        IntUtils.longToByteArray(bits, Float.BYTES / 2, dst, offset);
    }

    public static byte[] halfFloatToBytes(PNodeWithRaise nodeWithRaise, float value) {
        byte[] bytes = new byte[Float.BYTES / 2];
        halfFloatToBytes(nodeWithRaise, value, bytes, 0);
        return bytes;
    }
}
