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

import java.math.BigInteger;

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.object.Shape;

public final class PDecimal extends PythonBuiltinObject {
    // abs(Long.MIN_VALUE) is 2^63, the only Java long magnitude that does not fit a
    // signed long. Share its coefficient so this conversion needs no BigInteger allocation.
    private static final BigInteger MIN_LONG_COEFFICIENT = BigInteger.ONE.shiftLeft(Long.SIZE - 1);

    public enum Kind {
        FINITE,
        INFINITY,
        NAN,
        SNAN
    }

    // Finite values are (-1)^negative * coefficient * 10^exponent. The exponent
    // preserves the quantum, including trailing zeros and the exponent of zero.
    private final boolean negative;
    private final long exponent;
    private final Kind kind;
    private final long compactCoefficient;
    private final BigInteger largeCoefficient;
    private long hash = -1;

    public PDecimal(Object cls, Shape instanceShape, long value) {
        super(cls, instanceShape);
        negative = value < 0;
        exponent = 0;
        kind = Kind.FINITE;
        if (value == Long.MIN_VALUE) {
            compactCoefficient = 0;
            largeCoefficient = MIN_LONG_COEFFICIENT;
        } else {
            compactCoefficient = Math.abs(value);
            largeCoefficient = null;
        }
    }

    public PDecimal(Object cls, Shape instanceShape, boolean negative, long coefficient, long exponent, Kind kind) {
        super(cls, instanceShape);
        assert coefficient >= 0;
        this.negative = negative;
        this.exponent = exponent;
        this.kind = kind;
        compactCoefficient = coefficient;
        largeCoefficient = null;
    }

    @TruffleBoundary
    public PDecimal(Object cls, Shape instanceShape, boolean negative, BigInteger coefficient, long exponent, Kind kind) {
        super(cls, instanceShape);
        assert coefficient.signum() >= 0;
        this.negative = negative;
        this.exponent = exponent;
        this.kind = kind;
        // A nonnegative signed long has at most 63 bits; the 64th bit is its sign bit.
        if (coefficient.bitLength() < Long.SIZE) {
            compactCoefficient = coefficient.longValue();
            largeCoefficient = null;
        } else {
            compactCoefficient = 0;
            largeCoefficient = coefficient;
        }
    }

    public PDecimal(Object cls, Shape instanceShape, PDecimal value) {
        super(cls, instanceShape);
        negative = value.negative;
        exponent = value.exponent;
        kind = value.kind;
        compactCoefficient = value.compactCoefficient;
        largeCoefficient = value.largeCoefficient;
    }

    public long getHash() {
        return hash;
    }

    public void setHash(long hash) {
        assert hash != -1;
        this.hash = hash;
    }

    public boolean isNegative() {
        return negative;
    }

    public long getExponent() {
        return exponent;
    }

    public Kind getKind() {
        return kind;
    }

    public boolean isCompact() {
        return largeCoefficient == null;
    }

    public long getCompactCoefficient() {
        assert isCompact();
        return compactCoefficient;
    }

    @TruffleBoundary
    public BigInteger getCoefficient() {
        return isCompact() ? BigInteger.valueOf(compactCoefficient) : largeCoefficient;
    }

    @TruffleBoundary
    public String toScientificString(boolean capitals) {
        StringBuilder result = new StringBuilder();
        if (negative) {
            result.append('-');
        }
        if (kind == Kind.INFINITY) {
            return result.append("Infinity").toString();
        }
        String digits;
        if (isCompact()) {
            digits = Long.toString(compactCoefficient);
        } else {
            digits = largeCoefficient.toString();
        }
        if (kind != Kind.FINITE) {
            result.append(kind == Kind.NAN ? "NaN" : "sNaN");
            if (!digits.equals("0")) {
                result.append(digits);
            }
            return result.toString();
        }
        // Moving the decimal point after the first digit adds digits.length() - 1
        // to the stored exponent, giving the exponent of the most significant digit.
        long adjustedExponent = exponent + digits.length() - 1;
        // General Decimal Arithmetic's to-scientific-string rule (libmpdec's io.c):
        // use fixed notation for nonpositive stored exponents and adjusted exponents
        // of at least -6. Thus 0.000001 stays fixed, while 0.0000001 becomes 1E-7.
        if (exponent <= 0 && adjustedExponent >= -6) {
            int point = (int) (digits.length() + exponent);
            if (point <= 0) {
                return result.append("0.").append("0".repeat(-point)).append(digits).toString();
            } else if (point < digits.length()) {
                return result.append(digits, 0, point).append('.').append(digits, point, digits.length()).toString();
            }
            return result.append(digits).toString();
        }
        // Scientific notation puts exactly one coefficient digit before the point.
        if (digits.length() != 1) {
            result.append(digits.charAt(0)).append('.').append(digits, 1, digits.length());
        } else {
            result.append(digits);
        }
        result.append(capitals ? 'E' : 'e');
        if (adjustedExponent >= 0) {
            result.append('+');
        }
        return result.append(adjustedExponent).toString();
    }
}
