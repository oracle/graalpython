/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.decimal;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.graalvm.shadowed.com.ibm.icu.lang.UCharacter;

import com.oracle.graal.python.builtins.objects.decimal.PDecimal.Kind;
import com.oracle.graal.python.builtins.objects.str.StringUtils;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

/** Exact conversions, independent of the arithmetic context's precision and rounding. */
public final class DecimalUtils {
    // Match MPD_MAX_PREC in CPython's Modules/_decimal/libmpdec/mpdecimal.h (CONFIG_64):
    // the maximum context precision is 10^18 - 1 decimal digits.
    public static final long MAX_PREC = 999999999999999999L;
    // MPD_MAX_EMAX is also 10^18 - 1, one below libmpdec's exponent limit MPD_ELIMIT.
    public static final long MAX_EMAX = MAX_PREC;
    // The minimum context exponent is the negative of the maximum context exponent.
    public static final long MIN_EMIN = -MAX_EMAX;
    // Etiny = Emin - precision + 1: the exponent of the smallest subnormal at maximum
    // precision. The precision - 1 offset accounts for the remaining coefficient digits.
    public static final long MIN_ETINY = MIN_EMIN - (MAX_PREC - 1);

    // Bound retained storage independently of the user's precision and exponent limits.
    private static final BigInteger[] SMALL_POWERS_OF_TEN = createSmallPowersOfTen();

    private DecimalUtils() {
    }

    private static BigInteger[] createSmallPowersOfTen() {
        BigInteger[] powers = new BigInteger[128];
        powers[0] = BigInteger.ONE;
        for (int i = 1; i < powers.length; i++) {
            powers[i] = powers[i - 1].multiply(BigInteger.TEN);
        }
        return powers;
    }

    static BigInteger pow10(long exponent) {
        if (exponent >= 0 && exponent < SMALL_POWERS_OF_TEN.length) {
            return SMALL_POWERS_OF_TEN[(int) exponent];
        }
        // Java arrays (and BigInteger itself) have int-sized lengths. Check before narrowing.
        return BigInteger.TEN.pow(Math.toIntExact(exponent));
    }

    static int digits(long coefficient) {
        assert coefficient >= 0;
        int count = 1;
        for (long c = coefficient; c >= 10; c /= 10) {
            count++;
        }
        return count;
    }

    static int digits(BigInteger value) {
        BigInteger magnitude = value.abs();
        int bits = magnitude.bitLength();
        if (bits < Long.SIZE) {
            return digits(magnitude.longValue());
        }
        // 646456993 / 2^31 is a lower approximation of log10(2). For every int-sized
        // bit length its error, plus the width of the bit-length interval, is less than one
        // decimal digit. The magnitude therefore has either estimate or estimate + 1 digits.
        int estimate = (int) (((bits - 1L) * 646456993L) >>> 31) + 1;
        return magnitude.compareTo(pow10(estimate)) >= 0 ? estimate + 1 : estimate;
    }

    // Special values have an unused exponent of zero. A zero NaN coefficient means no
    // diagnostic payload; infinity always has a zero coefficient.
    record Value(boolean negative, BigInteger coefficient, long exponent, Kind kind) {
    }

    static final class ConversionException extends Exception {
        private static final long serialVersionUID = 1L;
        final DecimalExceptionTypes condition;

        ConversionException(DecimalExceptionTypes condition) {
            this.condition = condition;
        }
    }

    @TruffleBoundary
    static Value fromInteger(BigInteger integer) {
        return new Value(integer.signum() < 0, integer.abs(), 0, Kind.FINITE);
    }

    @TruffleBoundary
    static Value fromFloat(double value) {
        if (Double.isNaN(value)) {
            return new Value(false, BigInteger.ZERO, 0, Kind.NAN);
        }
        boolean negative = Double.doubleToRawLongBits(value) < 0;
        if (Double.isInfinite(value)) {
            return new Value(negative, BigInteger.ZERO, 0, Kind.INFINITY);
        }
        // BigDecimal(double) converts the binary value exactly, unlike valueOf(double).
        // BigDecimal's scale counts fractional decimal places, hence exponent = -scale.
        BigDecimal decimal = new BigDecimal(value);
        return new Value(negative, decimal.unscaledValue().abs(), -((long) decimal.scale()), Kind.FINITE);
    }

    @TruffleBoundary
    static Value parse(String input) throws ConversionException {
        int start = 0;
        int end = input.length();
        while (start < end && StringUtils.isUnicodeWhitespace(input.codePointAt(start))) {
            start += Character.charCount(input.codePointAt(start));
        }
        while (end > start && StringUtils.isUnicodeWhitespace(input.codePointBefore(end))) {
            end -= Character.charCount(input.codePointBefore(end));
        }
        StringBuilder ascii = new StringBuilder(end - start);
        for (int i = start; i < end;) {
            int ch = input.codePointAt(i);
            i += Character.charCount(ch);
            if (ch == '_') {
                continue;
            }
            // ASCII ends at U+007F (127). Beyond it, only Unicode base-10 digits are
            // accepted and translated to the contiguous ASCII range '0' through '9'.
            if (ch > 127) {
                int digit = UCharacter.digit(ch, 10);
                if (digit < 0) {
                    throw new ConversionException(DecimalExceptionTypes.ConversionSyntax);
                }
                ch = '0' + digit;
            }
            ascii.append((char) ch);
        }
        String text = ascii.toString();
        int pos = 0;
        boolean negative = false;
        if (!text.isEmpty() && (text.charAt(0) == '-' || text.charAt(0) == '+')) {
            negative = text.charAt(0) == '-';
            pos++;
        }
        String unsigned = text.substring(pos);
        if (unsigned.equalsIgnoreCase("inf") || unsigned.equalsIgnoreCase("infinity")) {
            return new Value(negative, BigInteger.ZERO, 0, Kind.INFINITY);
        }
        Kind kind = Kind.FINITE;
        // Skip the four-character "sNaN" or three-character "NaN" prefix; any remaining
        // digits form the diagnostic payload.
        if (unsigned.regionMatches(true, 0, "sNaN", 0, 4)) {
            kind = Kind.SNAN;
            pos += 4;
        } else if (unsigned.regionMatches(true, 0, "NaN", 0, 3)) {
            kind = Kind.NAN;
            pos += 3;
        }
        StringBuilder coefficient = new StringBuilder();
        int fractionalDigits = 0;
        boolean point = false;
        while (pos < text.length()) {
            char ch = text.charAt(pos);
            if (ch >= '0' && ch <= '9') {
                coefficient.append(ch);
                if (point) {
                    fractionalDigits++;
                }
            } else if (ch == '.' && !point && kind == Kind.FINITE) {
                point = true;
            } else {
                break;
            }
            pos++;
        }
        if (kind != Kind.FINITE) {
            if (pos != text.length()) {
                throw new ConversionException(DecimalExceptionTypes.ConversionSyntax);
            }
            BigInteger integer;
            if (coefficient.isEmpty()) {
                integer = BigInteger.ZERO;
            } else {
                integer = new BigInteger(coefficient.toString());
            }
            return new Value(negative, integer, 0, kind);
        }
        if (coefficient.isEmpty()) {
            throw new ConversionException(DecimalExceptionTypes.ConversionSyntax);
        }
        long exponent = 0;
        if (pos < text.length() && (text.charAt(pos) == 'e' || text.charAt(pos) == 'E')) {
            int exponentStart = ++pos;
            if (pos < text.length() && (text.charAt(pos) == '+' || text.charAt(pos) == '-')) {
                pos++;
            }
            int digitsStart = pos;
            while (pos < text.length() && text.charAt(pos) >= '0' && text.charAt(pos) <= '9') {
                pos++;
            }
            if (pos == digitsStart || pos != text.length()) {
                throw new ConversionException(DecimalExceptionTypes.ConversionSyntax);
            }
            try {
                exponent = new BigInteger(text.substring(exponentStart)).longValueExact();
            } catch (ArithmeticException e) {
                throw new ConversionException(DecimalExceptionTypes.InvalidOperation);
            }
        }
        if (pos != text.length()) {
            throw new ConversionException(DecimalExceptionTypes.ConversionSyntax);
        }
        try {
            exponent = Math.subtractExact(exponent, fractionalDigits);
        } catch (ArithmeticException e) {
            throw new ConversionException(DecimalExceptionTypes.InvalidOperation);
        }
        return finite(negative, new BigInteger(coefficient.toString()), exponent);
    }

    @TruffleBoundary
    static Value finite(boolean negative, BigInteger coefficient, long exponent) throws ConversionException {
        // The adjusted exponent locates the most significant digit: exponent + digits - 1.
        // Both the stored exponent and the adjusted exponent must fit libmpdec's limits.
        if (exponent < MIN_ETINY || exponent > MAX_EMAX || exponent + digits(coefficient) - 1 > MAX_EMAX) {
            throw new ConversionException(DecimalExceptionTypes.InvalidOperation);
        }
        return new Value(negative, coefficient, exponent, Kind.FINITE);
    }
}
