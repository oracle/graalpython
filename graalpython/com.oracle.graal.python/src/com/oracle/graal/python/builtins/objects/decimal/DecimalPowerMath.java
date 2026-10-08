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

import static com.oracle.graal.python.builtins.objects.decimal.DecimalUtils.digits;
import static com.oracle.graal.python.builtins.objects.decimal.DecimalUtils.pow10;

import java.math.BigInteger;

import com.oracle.graal.python.builtins.objects.decimal.DecimalUtils.Value;
import com.oracle.graal.python.builtins.objects.decimal.PDecimal.Kind;

/**
 * Fixed-point logarithm and exponential algorithms from CPython's _pydecimal. Their integer
 * approximations have explicit error bounds, allowing power to increase precision until the
 * result is unambiguously roundable, including values close to a rounding boundary.
 */
final class DecimalPowerMath {
    private static final BigInteger HUNDRED = BigInteger.valueOf(100);
    private static final BigInteger THOUSAND = BigInteger.valueOf(1000);
    private static String logTenDigits = "23025850929940456840179914546843642076011014886";

    private DecimalPowerMath() {
    }

    private static BigInteger[] floorDivmod(BigInteger a, BigInteger b) {
        BigInteger[] qr = a.divideAndRemainder(b);
        if (qr[1].signum() < 0) {
            qr[0] = qr[0].subtract(BigInteger.ONE);
            qr[1] = qr[1].add(b);
        }
        return qr;
    }

    private static BigInteger nearestDiv(BigInteger a, BigInteger b) {
        BigInteger[] qr = floorDivmod(a, b);
        int half = qr[1].shiftLeft(1).compareTo(b);
        if (half > 0 || half == 0 && qr[0].testBit(0)) {
            return qr[0].add(BigInteger.ONE);
        }
        return qr[0];
    }

    private static BigInteger nearestShift(BigInteger a, int shift) {
        return nearestDiv(a, BigInteger.ONE.shiftLeft(shift));
    }

    private static BigInteger nearestSqrt(BigInteger n) {
        BigInteger root = n.sqrt();
        if (n.subtract(root.multiply(root)).compareTo(root) > 0) {
            return root.add(BigInteger.ONE);
        }
        return root;
    }

    private static BigInteger integerLog(BigInteger x, BigInteger scale, long scaleDigits) {
        BigInteger y = x.subtract(scale);
        int reductions = 0;
        // L=8: after reduction the log1p argument has magnitude less than 2^-8.
        // The resulting error is at most 22 scale units for 0.1 <= x/scale <= 10.
        while (true) {
            BigInteger magnitude;
            if (reductions <= 8) {
                magnitude = y.abs().shiftLeft(8 - reductions);
            } else {
                magnitude = y.abs().shiftRight(reductions - 8);
            }
            if (magnitude.compareTo(scale) < 0) {
                break;
            }
            y = nearestDiv(scale.multiply(y).shiftLeft(1), scale.add(nearestSqrt(scale.multiply(scale.add(nearestShift(y, reductions))))));
            reductions++;
        }
        int terms = Math.toIntExact((10 * scaleDigits + 23) / 24);
        BigInteger reduced = nearestShift(y, reductions);
        BigInteger w = nearestDiv(scale, BigInteger.valueOf(terms));
        for (int k = terms - 1; k > 0; k--) {
            w = nearestDiv(scale, BigInteger.valueOf(k)).subtract(nearestDiv(reduced.multiply(w), scale));
        }
        return nearestDiv(w.multiply(y), scale);
    }

    private static BigInteger logTen(long places) {
        int p = Math.toIntExact(places);
        String logTenDigits = DecimalPowerMath.logTenDigits;
        if (p >= logTenDigits.length()) {
            for (int extra = 3;; extra += 3) {
                long scaleExponent = (long) p + extra + 2;
                BigInteger scale = pow10(scaleExponent);
                String approximation = nearestDiv(integerLog(scale.multiply(BigInteger.TEN), scale, scaleExponent + 1), HUNDRED).toString();
                if (!approximation.endsWith("0".repeat(extra))) {
                    int end = approximation.length();
                    while (approximation.charAt(end - 1) == '0') {
                        end--;
                    }
                    logTenDigits = approximation.substring(0, end - 1);
                    DecimalPowerMath.logTenDigits = logTenDigits;
                    break;
                }
            }
        }
        return new BigInteger(logTenDigits.substring(0, p + 1));
    }

    /** Approximate 10^places * ln(c * 10^e), with absolute error at most one. */
    private static BigInteger decimalLog(BigInteger c, long e, long places) {
        long p = places + 2;
        long f = e + digits(c);
        if (f >= 1) {
            f--;
        }
        BigInteger logSignificand = BigInteger.ZERO;
        if (p > 0) {
            long shift = e + p - f;
            BigInteger scaled;
            if (shift >= 0) {
                scaled = c.multiply(pow10(shift));
            } else {
                scaled = nearestDiv(c, pow10(-shift));
            }
            logSignificand = integerLog(scaled, pow10(p), p + 1);
        }
        BigInteger logPower = BigInteger.ZERO;
        if (f != 0) {
            int extra = digits(Math.abs(f)) - 1;
            if (p + extra >= 0) {
                logPower = nearestDiv(BigInteger.valueOf(f).multiply(logTen(p + extra)), pow10(extra));
            }
        }
        return nearestDiv(logPower.add(logSignificand), HUNDRED);
    }

    private static BigInteger integerExp(BigInteger x, BigInteger scale, long scaleDigits) {
        // x/scale is between 0 and ln(10). With L=8 the absolute error is at most 60.
        int reductions = x.shiftLeft(8).divide(scale).bitLength();
        int terms = Math.toIntExact((10 * scaleDigits + 23) / 24);
        BigInteger y = nearestDiv(x, BigInteger.valueOf(terms));
        BigInteger shiftedScale = scale.shiftLeft(reductions);
        for (int i = terms - 1; i > 0; i--) {
            y = nearestDiv(x.multiply(shiftedScale.add(y)), shiftedScale.multiply(BigInteger.valueOf(i)));
        }
        for (int k = reductions - 1; k >= 0; k--) {
            shiftedScale = scale.shiftLeft(k + 2);
            y = nearestDiv(y.multiply(y.add(shiftedScale)), shiftedScale);
        }
        return scale.add(y);
    }

    private static Value decimalExp(BigInteger c, long e, long precision) {
        long p = precision + 2;
        long extra = Math.max(0, e + digits(c) - 1);
        long q = p + extra;
        long shift = e + q;
        BigInteger scaled;
        if (shift >= 0) {
            scaled = c.multiply(pow10(shift));
        } else {
            scaled = floorDivmod(c, pow10(-shift))[0];
        }
        BigInteger[] qr = floorDivmod(scaled, logTen(q));
        BigInteger remainder = nearestDiv(qr[1], pow10(extra));
        // Beyond the implementation's decimal exponent limits only the direction matters.
        // Keep a finite sentinel so finalization can apply the user's overflow rounding mode.
        BigInteger limit = BigInteger.valueOf(4 * DecimalUtils.MAX_EMAX);
        long exponent = qr[0].max(limit.negate()).min(limit).longValueExact();
        return new Value(false, nearestDiv(integerExp(remainder, pow10(p), p + 1), THOUSAND), exponent - p + 3, Kind.FINITE);
    }

    static Value logarithm(BigInteger coefficient, long exponent, long places) {
        BigInteger approximation = decimalLog(coefficient, exponent, places);
        return new Value(approximation.signum() < 0, approximation.abs(), -places, Kind.FINITE);
    }

    static Value exponential(Value argument, long precision) {
        BigInteger coefficient = argument.coefficient();
        if (argument.negative()) {
            coefficient = coefficient.negate();
        }
        return decimalExp(coefficient, argument.exponent(), precision);
    }
}
