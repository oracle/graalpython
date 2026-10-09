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

import static com.oracle.graal.python.builtins.objects.decimal.DecimalRounding.ROUND_FLOOR;
import static com.oracle.graal.python.builtins.objects.decimal.DecimalRounding.ROUND_HALF_EVEN;
import static com.oracle.graal.python.builtins.objects.decimal.DecimalUtils.pow10;

import java.math.BigInteger;

import com.oracle.graal.python.builtins.objects.decimal.DecimalUtils.Value;
import com.oracle.graal.python.builtins.objects.decimal.PDecimal.Kind;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

/**
 * Decimal coefficient arithmetic and context finalization. The algorithms follow CPython's
 * _pydecimal, using integer remainders (rather than pre-rounded BigDecimal operations) to retain
 * exactness and rounding information. No Python calls or native arithmetic are involved.
 */
final class DecimalArithmetic {
    record Result(Value value, int status) {
    }

    record ResultWithRemainder(Value value, Value remainder, int status) {
    }

    private static final BigInteger FIVE = BigInteger.valueOf(5);
    private static final int INVALID = DecimalContextUtils.conditionMask(DecimalExceptionTypes.InvalidOperation);
    private static final int INEXACT = DecimalContextUtils.conditionMask(DecimalExceptionTypes.Inexact);
    private static final int ROUNDED = DecimalContextUtils.conditionMask(DecimalExceptionTypes.Rounded);
    private static final int CLAMPED = DecimalContextUtils.conditionMask(DecimalExceptionTypes.Clamped);
    private static final int SUBNORMAL = DecimalContextUtils.conditionMask(DecimalExceptionTypes.Subnormal);
    private static final int UNDERFLOW = DecimalContextUtils.conditionMask(DecimalExceptionTypes.Underflow);
    private static final int OVERFLOW = DecimalContextUtils.conditionMask(DecimalExceptionTypes.Overflow);
    private static final int DIVISION_ZERO = DecimalContextUtils.conditionMask(DecimalExceptionTypes.DivisionByZero);
    private static final int DIVISION_UNDEFINED = DecimalContextUtils.conditionMask(DecimalExceptionTypes.DivisionUndefined);
    private static final int DIVISION_IMPOSSIBLE = DecimalContextUtils.conditionMask(DecimalExceptionTypes.DivisionImpossible);

    private final long precision;
    private final long emin;
    private final long emax;
    private final int clamp;
    private final DecimalRounding rounding;
    private int status;

    private DecimalArithmetic(PDecimalContext context) {
        this(context.getPrecision(), context.getEmin(), context.getEmax(), context.getRounding(), context.getClamp());
    }

    private DecimalArithmetic(long precision, long emin, long emax, DecimalRounding rounding, int clamp) {
        this.precision = precision;
        this.emin = emin;
        this.emax = emax;
        this.rounding = rounding;
        this.clamp = clamp;
    }

    static int digits(long coefficient) {
        return DecimalUtils.digits(coefficient);
    }

    private static int digits(BigInteger coefficient) {
        return DecimalUtils.digits(coefficient);
    }

    static boolean fitsContext(boolean zero, long exponent, int digits, PDecimalContext context) {
        long etiny = context.getEmin() - context.getPrecision() + 1;
        long etop = context.getEmax() - context.getPrecision() + 1;
        if (zero) {
            return exponent >= etiny && exponent <= (context.getClamp() == 0 ? context.getEmax() : etop);
        }
        long adjusted = exponent + digits - 1;
        return digits <= context.getPrecision() && adjusted >= context.getEmin() && adjusted <= context.getEmax() &&
                        (context.getClamp() == 0 || exponent <= etop);
    }

    private static Value value(PDecimal decimal) {
        return new Value(decimal.isNegative(), decimal.getCoefficient(), decimal.getExponent(), decimal.getKind());
    }

    private static Value finite(boolean negative, BigInteger coefficient, long exponent) {
        return new Value(negative, coefficient, exponent, Kind.FINITE);
    }

    private static Value zero(boolean negative, long exponent) {
        return finite(negative, BigInteger.ZERO, exponent);
    }

    private static Value infinity(boolean negative) {
        return new Value(negative, BigInteger.ZERO, 0, Kind.INFINITY);
    }

    private static boolean isZero(Value value) {
        return value.kind() == Kind.FINITE && value.coefficient().signum() == 0;
    }

    private static long adjusted(Value value) {
        return value.exponent() + digits(value.coefficient()) - 1;
    }

    private Value invalid(int condition) {
        status |= condition;
        return new Value(false, BigInteger.ZERO, 0, Kind.NAN);
    }

    private Value checkNaNs(Value operand) {
        return checkNaNs(operand, null, null);
    }

    private Value checkNaNs(Value left, Value right) {
        return checkNaNs(left, right, null);
    }

    private Value checkNaNs(Value first, Value second, Value third) {
        // Signaling NaNs take precedence over quiet NaNs, in operand order.
        Value signaling = firstNaN(Kind.SNAN, first, second, third);
        if (signaling != null) {
            status |= INVALID;
            return fixNaN(signaling);
        }
        Value quiet = firstNaN(Kind.NAN, first, second, third);
        return quiet == null ? null : fixNaN(quiet);
    }

    private static Value firstNaN(Kind kind, Value first, Value second, Value third) {
        if (first.kind() == kind) {
            return first;
        }
        if (second != null && second.kind() == kind) {
            return second;
        }
        return third != null && third.kind() == kind ? third : null;
    }

    private Value fixNaN(Value operand) {
        BigInteger payload = operand.coefficient();
        long limit = precision - clamp;
        if (payload.signum() != 0 && digits(payload) > limit) {
            payload = payload.remainder(pow10(limit));
        }
        return new Value(operand.negative(), payload, 0, Kind.NAN);
    }

    private boolean increment(boolean negative, BigInteger quotient, BigInteger remainder, BigInteger divisor) {
        if (remainder.signum() == 0) {
            return false;
        }
        return switch (rounding) {
            case ROUND_DOWN -> false;
            case ROUND_UP -> true;
            case ROUND_CEILING -> !negative;
            case ROUND_FLOOR -> negative;
            case ROUND_05UP -> quotient.remainder(FIVE).signum() == 0;
            case ROUND_HALF_UP -> remainder.shiftLeft(1).compareTo(divisor) >= 0;
            case ROUND_HALF_DOWN -> remainder.shiftLeft(1).compareTo(divisor) > 0;
            case ROUND_HALF_EVEN -> {
                int half = remainder.shiftLeft(1).compareTo(divisor);
                yield half > 0 || half == 0 && quotient.testBit(0);
            }
        };
    }

    private Value overflow(boolean negative) {
        status |= OVERFLOW | INEXACT | ROUNDED;
        boolean finite = switch (rounding) {
            case ROUND_DOWN, ROUND_05UP -> true;
            case ROUND_CEILING -> negative;
            case ROUND_FLOOR -> !negative;
            case ROUND_UP, ROUND_HALF_UP, ROUND_HALF_DOWN, ROUND_HALF_EVEN -> false;
        };
        if (finite) {
            return finite(negative, pow10(precision).subtract(BigInteger.ONE), emax - precision + 1);
        }
        return infinity(negative);
    }

    private Value finish(Value operand) {
        if (operand.kind() != Kind.FINITE) {
            return operand;
        }
        long etiny = emin - precision + 1;
        long etop = emax - precision + 1;
        long exponent = operand.exponent();
        BigInteger coefficient = operand.coefficient();
        if (coefficient.signum() == 0) {
            long max = clamp == 0 ? emax : etop;
            long clamped = Math.min(Math.max(exponent, etiny), max);
            if (clamped != exponent) {
                status |= CLAMPED;
            }
            return zero(operand.negative(), clamped);
        }
        int length = digits(coefficient);
        long adjusted = exponent + length - 1;
        if (adjusted > emax) {
            return overflow(operand.negative());
        }
        boolean subnormal = adjusted < emin;
        if (subnormal) {
            status |= SUBNORMAL;
        }
        // Apply precision and the subnormal exponent floor in one rounding step.
        long targetExponent = Math.max(exponent + length - precision, etiny);
        if (exponent < targetExponent) {
            long shift = targetExponent - exponent;
            BigInteger quotient;
            BigInteger remainder;
            BigInteger divisor;
            if (shift > length) {
                quotient = BigInteger.ZERO;
                remainder = BigInteger.ONE;
                divisor = BigInteger.TEN;
            } else {
                divisor = pow10(shift);
                BigInteger[] qr = coefficient.divideAndRemainder(divisor);
                quotient = qr[0];
                remainder = qr[1];
            }
            status |= ROUNDED;
            if (remainder.signum() != 0) {
                status |= INEXACT;
                if (subnormal) {
                    status |= UNDERFLOW;
                }
            }
            if (increment(operand.negative(), quotient, remainder, divisor)) {
                coefficient = quotient.add(BigInteger.ONE);
            } else {
                coefficient = quotient;
            }
            exponent = targetExponent;
            length = digits(coefficient);
            if (length > precision) {
                coefficient = coefficient.divide(BigInteger.TEN);
                exponent++;
                length--;
            }
            if (exponent + length - 1 > emax) {
                return overflow(operand.negative());
            }
            if (coefficient.signum() == 0) {
                status |= CLAMPED;
            }
        }
        if (clamp != 0 && exponent > etop) {
            coefficient = coefficient.multiply(pow10(exponent - etop));
            exponent = etop;
            status |= CLAMPED;
        }
        return finite(operand.negative(), coefficient, exponent);
    }

    @TruffleBoundary
    static Result plus(PDecimal self, PDecimalContext context) {
        return applySign(self, context, self.isNegative());
    }

    @TruffleBoundary
    static Result minus(PDecimal self, PDecimalContext context) {
        return applySign(self, context, !self.isNegative());
    }

    @TruffleBoundary
    static Result abs(PDecimal self, PDecimalContext context) {
        return applySign(self, context, false);
    }

    private static Result applySign(PDecimal self, PDecimalContext context, boolean negative) {
        DecimalArithmetic arithmetic = new DecimalArithmetic(context);
        Value operand = value(self);
        Value result = arithmetic.checkNaNs(operand);
        if (result == null) {
            if (isZero(operand) && context.getRounding() != ROUND_FLOOR) {
                negative = false;
            }
            result = arithmetic.finish(new Value(negative, operand.coefficient(), operand.exponent(), operand.kind()));
        }
        return new Result(result, arithmetic.status);
    }

    @TruffleBoundary
    static Result add(PDecimal left, PDecimal right, PDecimalContext context) {
        DecimalArithmetic arithmetic = new DecimalArithmetic(context);
        Value a = value(left);
        Value b = value(right);
        Value result = arithmetic.checkNaNs(a, b);
        if (result == null) {
            result = arithmetic.add(a, b);
        }
        return new Result(result, arithmetic.status);
    }

    @TruffleBoundary
    static Result subtract(PDecimal left, PDecimal right, PDecimalContext context) {
        DecimalArithmetic arithmetic = new DecimalArithmetic(context);
        Value a = value(left);
        Value b = value(right);
        Value result = arithmetic.checkNaNs(a, b);
        if (result == null) {
            b = new Value(!b.negative(), b.coefficient(), b.exponent(), b.kind());
            result = arithmetic.add(a, b);
        }
        return new Result(result, arithmetic.status);
    }

    @TruffleBoundary
    static Result multiply(PDecimal left, PDecimal right, PDecimalContext context) {
        DecimalArithmetic arithmetic = new DecimalArithmetic(context);
        Value a = value(left);
        Value b = value(right);
        Value result = arithmetic.checkNaNs(a, b);
        if (result == null) {
            result = arithmetic.multiply(a, b);
        }
        return new Result(result, arithmetic.status);
    }

    @TruffleBoundary
    static Result divide(PDecimal left, PDecimal right, PDecimalContext context) {
        DecimalArithmetic arithmetic = new DecimalArithmetic(context);
        Value a = value(left);
        Value b = value(right);
        Value result = arithmetic.checkNaNs(a, b);
        if (result == null) {
            result = arithmetic.divide(a, b);
        }
        return new Result(result, arithmetic.status);
    }

    @TruffleBoundary
    static Result divideInt(PDecimal left, PDecimal right, PDecimalContext context) {
        DecimalArithmetic arithmetic = new DecimalArithmetic(context);
        Value a = value(left);
        Value b = value(right);
        Value result = arithmetic.checkNaNs(a, b);
        if (result == null) {
            result = arithmetic.divmod(a, b, true, false).value();
        }
        return new Result(result, arithmetic.status);
    }

    @TruffleBoundary
    static Result remainder(PDecimal left, PDecimal right, PDecimalContext context) {
        DecimalArithmetic arithmetic = new DecimalArithmetic(context);
        Value a = value(left);
        Value b = value(right);
        Value result = arithmetic.checkNaNs(a, b);
        if (result == null) {
            result = arithmetic.divmod(a, b, false, true).remainder();
        }
        return new Result(result, arithmetic.status);
    }

    @TruffleBoundary
    static ResultWithRemainder divmod(PDecimal left, PDecimal right, PDecimalContext context) {
        DecimalArithmetic arithmetic = new DecimalArithmetic(context);
        Value a = value(left);
        Value b = value(right);
        Value result = arithmetic.checkNaNs(a, b);
        if (result == null) {
            return arithmetic.divmod(a, b, true, true);
        }
        return new ResultWithRemainder(result, result, arithmetic.status);
    }

    private Value add(Value a, Value b) {
        if (a.kind() == Kind.INFINITY || b.kind() == Kind.INFINITY) {
            if (a.kind() == b.kind() && a.negative() != b.negative()) {
                return invalid(INVALID);
            }
            return a.kind() == Kind.INFINITY ? a : b;
        }
        long exponent = Math.min(a.exponent(), b.exponent());
        boolean negativeZero;
        if (a.negative() == b.negative()) {
            negativeZero = a.negative();
        } else {
            negativeZero = rounding == ROUND_FLOOR;
        }
        if (isZero(a) && isZero(b)) {
            return finish(zero(negativeZero, exponent));
        }
        if (isZero(a) || isZero(b)) {
            Value nonzero = isZero(a) ? b : a;
            exponent = Math.max(exponent, nonzero.exponent() - precision - 1);
            return finish(finite(nonzero.negative(), nonzero.coefficient().multiply(pow10(nonzero.exponent() - exponent)), exponent));
        }
        // Replace an operand too small to affect the retained digits with a signed sticky
        // digit. This also handles subtraction correctly, without enormous coefficient shifts.
        Value big = a.exponent() >= b.exponent() ? a : b;
        Value small = a.exponent() >= b.exponent() ? b : a;
        long cutoff = big.exponent() + Math.min(-1, digits(big.coefficient()) - precision - 2);
        if (adjusted(small) < cutoff) {
            small = finite(small.negative(), BigInteger.ONE, cutoff);
        }
        long commonExponent = Math.min(big.exponent(), small.exponent());
        BigInteger bigCoefficient = big.coefficient().multiply(pow10(big.exponent() - commonExponent));
        BigInteger smallCoefficient = small.coefficient().multiply(pow10(small.exponent() - commonExponent));
        if (big.negative()) {
            bigCoefficient = bigCoefficient.negate();
        }
        if (small.negative()) {
            smallCoefficient = smallCoefficient.negate();
        }
        BigInteger sum = bigCoefficient.add(smallCoefficient);
        if (sum.signum() == 0) {
            return finish(finite(negativeZero, sum.abs(), exponent));
        }
        return finish(finite(sum.signum() < 0, sum.abs(), commonExponent));
    }

    private Value multiply(Value a, Value b) {
        boolean negative = a.negative() ^ b.negative();
        if (a.kind() == Kind.INFINITY || b.kind() == Kind.INFINITY) {
            return isZero(a) || isZero(b) ? invalid(INVALID) : infinity(negative);
        }
        return finish(finite(negative, a.coefficient().multiply(b.coefficient()), a.exponent() + b.exponent()));
    }

    private Value divide(Value a, Value b) {
        boolean negative = a.negative() ^ b.negative();
        if (a.kind() == Kind.INFINITY) {
            return b.kind() == Kind.INFINITY ? invalid(INVALID) : infinity(negative);
        }
        if (b.kind() == Kind.INFINITY) {
            status |= CLAMPED;
            return zero(negative, emin - precision + 1);
        }
        if (isZero(b)) {
            if (isZero(a)) {
                return invalid(DIVISION_UNDEFINED);
            }
            status |= DIVISION_ZERO;
            return infinity(negative);
        }
        long idealExponent = a.exponent() - b.exponent();
        if (isZero(a)) {
            return finish(zero(negative, idealExponent));
        }
        long shift = digits(b.coefficient()) - digits(a.coefficient()) + precision + 1;
        BigInteger numerator = a.coefficient();
        BigInteger denominator = b.coefficient();
        if (shift >= 0) {
            numerator = numerator.multiply(pow10(shift));
        } else {
            denominator = denominator.multiply(pow10(-shift));
        }
        BigInteger[] qr = numerator.divideAndRemainder(denominator);
        BigInteger coefficient = qr[0];
        long exponent = idealExponent - shift;
        if (qr[1].signum() != 0) {
            // A nonzero remainder beyond a zero or five rounding digit must remain sticky.
            if (coefficient.remainder(FIVE).signum() == 0) {
                coefficient = coefficient.add(BigInteger.ONE);
            }
        } else {
            while (exponent < idealExponent && coefficient.remainder(BigInteger.TEN).signum() == 0) {
                coefficient = coefficient.divide(BigInteger.TEN);
                exponent++;
            }
        }
        return finish(finite(negative, coefficient, exponent));
    }

    private ResultWithRemainder divmod(Value a, Value b, boolean quotientNeeded, boolean remainderNeeded) {
        boolean negative = a.negative() ^ b.negative();
        Value quotient;
        Value remainder;
        if (a.kind() == Kind.INFINITY) {
            quotient = b.kind() == Kind.INFINITY ? invalid(INVALID) : infinity(negative);
            remainder = remainderNeeded ? invalid(INVALID) : null;
        } else if (isZero(b)) {
            if (isZero(a)) {
                quotient = invalid(DIVISION_UNDEFINED);
                remainder = quotient;
            } else {
                quotient = infinity(negative);
                if (quotientNeeded) {
                    status |= DIVISION_ZERO;
                }
                remainder = remainderNeeded ? invalid(INVALID) : null;
            }
        } else if (b.kind() == Kind.INFINITY) {
            quotient = zero(negative, 0);
            remainder = remainderNeeded ? finish(a) : null;
        } else {
            long exponent = Math.min(a.exponent(), b.exponent());
            long expdiff = adjusted(a) - adjusted(b);
            if (!isZero(a) && expdiff > precision) {
                quotient = invalid(DIVISION_IMPOSSIBLE);
                remainder = quotient;
            } else {
                BigInteger q;
                BigInteger r;
                if (isZero(a) || expdiff < 0 && compareMagnitude(a, b) < 0) {
                    q = BigInteger.ZERO;
                    if (remainderNeeded) {
                        r = a.coefficient().multiply(pow10(a.exponent() - exponent));
                    } else {
                        r = BigInteger.ZERO;
                    }
                } else {
                    BigInteger numerator = a.coefficient().multiply(pow10(a.exponent() - exponent));
                    BigInteger denominator = b.coefficient().multiply(pow10(b.exponent() - exponent));
                    BigInteger[] qr = numerator.divideAndRemainder(denominator);
                    q = qr[0];
                    r = qr[1];
                }
                if (digits(q) > precision) {
                    quotient = invalid(DIVISION_IMPOSSIBLE);
                    remainder = quotient;
                } else {
                    quotient = zero(negative, 0);
                    if (q.signum() != 0) {
                        quotient = finite(negative, q, 0);
                    }
                    if (quotientNeeded) {
                        quotient = finish(quotient);
                    }
                    if (remainderNeeded) {
                        remainder = finish(finite(a.negative(), r, exponent));
                    } else {
                        remainder = null;
                    }
                }
            }
        }
        return new ResultWithRemainder(quotient, remainder, status);
    }

    private static int compareMagnitude(Value a, Value b) {
        if (isZero(a) || isZero(b)) {
            return a.coefficient().signum() - b.coefficient().signum();
        }
        int cmp = Long.compare(adjusted(a), adjusted(b));
        if (cmp != 0) {
            return cmp;
        }
        long exponent = Math.min(a.exponent(), b.exponent());
        return a.coefficient().multiply(pow10(a.exponent() - exponent)).compareTo(b.coefficient().multiply(pow10(b.exponent() - exponent)));
    }

    private static boolean integral(Value value) {
        if (value.kind() != Kind.FINITE) {
            return false;
        }
        if (isZero(value) || value.exponent() >= 0) {
            return true;
        }
        return -value.exponent() < digits(value.coefficient()) && value.coefficient().remainder(pow10(-value.exponent())).signum() == 0;
    }

    private static Value integralParts(Value value) {
        if (isZero(value)) {
            return zero(value.negative(), 0);
        }
        if (value.exponent() >= 0) {
            return value;
        }
        return finite(value.negative(), value.coefficient().divide(pow10(-value.exponent())), 0);
    }

    private static boolean odd(Value value) {
        return !isZero(value) && value.exponent() <= 0 && integralParts(value).coefficient().testBit(0);
    }

    @TruffleBoundary
    static Result power(PDecimal left, PDecimal right, PDecimal modulo, PDecimalContext context) {
        DecimalArithmetic arithmetic = new DecimalArithmetic(context);
        Value a = value(left);
        Value b = value(right);
        Value result;
        if (modulo == null) {
            result = arithmetic.checkNaNs(a, b);
            if (result == null) {
                result = arithmetic.power(a, b);
            }
        } else {
            Value m = value(modulo);
            result = arithmetic.checkNaNs(a, b, m);
            if (result == null) {
                result = arithmetic.powerModulo(a, b, m);
            }
        }
        return new Result(result, arithmetic.status);
    }

    private Value powerModulo(Value a, Value b, Value modulo) {
        if (!integral(a) || !integral(b) || !integral(modulo) || b.negative() && !isZero(b) || isZero(modulo) ||
                        adjusted(modulo) >= precision || isZero(a) && isZero(b)) {
            return invalid(INVALID);
        }
        boolean negative = a.negative() && odd(b);
        Value modulus = integralParts(modulo);
        BigInteger m = modulus.coefficient().multiply(pow10(modulus.exponent()));
        if (isZero(a)) {
            return zero(negative, 0);
        }
        Value base = integralParts(a);
        Value exponent = integralParts(b);
        BigInteger reduced = base.coefficient().mod(m).multiply(BigInteger.TEN.modPow(BigInteger.valueOf(base.exponent()), m)).mod(m);
        for (long i = 0; i < exponent.exponent(); i++) {
            BigInteger next = reduced.modPow(BigInteger.TEN, m);
            if (next.equals(reduced)) {
                break;
            }
            reduced = next;
        }
        return finite(negative, reduced.modPow(exponent.coefficient(), m), 0);
    }

    private Value power(Value a, Value b) {
        boolean integerExponent = integral(b);
        boolean negative = a.negative() && integerExponent && odd(b);
        if (isZero(b)) {
            return isZero(a) ? invalid(INVALID) : finite(false, BigInteger.ONE, 0);
        }
        if (isZero(a)) {
            return b.negative() ? infinity(negative) : zero(negative, 0);
        }
        if (a.negative() && !integerExponent) {
            return invalid(INVALID);
        }
        if (a.kind() == Kind.INFINITY) {
            return b.negative() ? zero(negative, 0) : infinity(negative);
        }
        Value one = finite(false, BigInteger.ONE, 0);
        int comparedToOne = compareMagnitude(a, one);
        if (comparedToOne == 0) {
            long exponent;
            if (!integerExponent) {
                status |= INEXACT | ROUNDED;
                exponent = 1 - precision;
            } else if (b.negative() || a.exponent() == 0) {
                exponent = 0;
            } else {
                Value parts = integralParts(b);
                BigInteger multiplier = parts.coefficient();
                // Only need to distinguish the ideal quantum from the precision floor.
                if (adjusted(b) >= digits(precision)) {
                    multiplier = BigInteger.valueOf(precision);
                } else {
                    multiplier = multiplier.multiply(pow10(parts.exponent()));
                }
                BigInteger ideal = BigInteger.valueOf(a.exponent()).multiply(multiplier);
                long floor = 1 - precision;
                if (ideal.compareTo(BigInteger.valueOf(floor)) < 0) {
                    status |= ROUNDED;
                    exponent = floor;
                } else {
                    exponent = ideal.longValueExact();
                }
            }
            return finish(finite(negative, pow10(-exponent), exponent));
        }
        if (b.kind() == Kind.INFINITY) {
            if ((comparedToOne > 0) != b.negative()) {
                return infinity(negative);
            }
            return zero(negative, 0);
        }
        long bound = logExponentBound(a) + adjusted(b);
        boolean growing = (comparedToOne > 0) != b.negative();
        long etiny = emin - precision + 1;
        long limit = growing ? emax : -etiny;
        if (bound > digits(limit)) {
            return finish(finite(negative, BigInteger.ONE, growing ? emax + 1 : etiny - 1));
        }
        if (integerExponent) {
            return integerPower(a, b, negative);
        }
        return realPower(a, b);
    }

    /** libmpdec computes real powers as exp(rounded(y * rounded(ln(x)))). */
    private Value realPower(Value base, Value exponent) {
        long workPrecision = Math.max(precision, digits(base.coefficient())) + 4 + 19;
        DecimalArithmetic work = new DecimalArithmetic(workPrecision, DecimalUtils.MIN_EMIN, DecimalUtils.MAX_EMAX, ROUND_HALF_EVEN, 0);
        Value logarithm;
        long bound = logExponentBound(base);
        for (long extra = 3;; extra += 3) {
            Value approximation = DecimalPowerMath.logarithm(base.coefficient(), base.exponent(), workPrecision - bound + extra);
            logarithm = work.roundInterval(approximation);
            if (logarithm != null) {
                break;
            }
        }
        Value argument = work.multiply(logarithm, exponent);
        Value result;
        if (isZero(argument) || adjusted(argument) < -workPrecision - 1) {
            result = finite(false, pow10(workPrecision - 1), 1 - workPrecision);
        } else {
            for (long extra = 3;; extra += 3) {
                Value approximation = DecimalPowerMath.exponential(argument, workPrecision + extra);
                result = work.roundInterval(approximation);
                if (result != null) {
                    break;
                }
            }
        }
        status |= INEXACT | ROUNDED;
        if (result.kind() == Kind.INFINITY) {
            result = finite(false, BigInteger.ONE, 4 * DecimalUtils.MAX_EMAX);
        } else if (compareMagnitude(result, finite(false, BigInteger.ONE, 0)) == 0) {
            result = finite(false, pow10(precision - 1), 1 - precision);
        }
        result = finish(result);
        if ((status & SUBNORMAL) != 0) {
            status |= UNDERFLOW;
        }
        return result;
    }

    /** Round both ends of the proven approximation interval, including tininess detection. */
    private Value roundInterval(Value approximation) {
        DecimalArithmetic low = new DecimalArithmetic(precision, emin, emax, rounding, clamp);
        DecimalArithmetic high = new DecimalArithmetic(precision, emin, emax, rounding, clamp);
        Value lower = low.finish(finite(approximation.negative(), approximation.coefficient().subtract(BigInteger.ONE), approximation.exponent()));
        Value upper = high.finish(finite(approximation.negative(), approximation.coefficient().add(BigInteger.ONE), approximation.exponent()));
        if (lower.equals(upper) && (low.status & SUBNORMAL) == (high.status & SUBNORMAL)) {
            return lower;
        }
        return null;
    }

    /** Match libmpdec's integer-power working precision and rounding of intermediate values. */
    private Value integerPower(Value base, Value exponent, boolean negative) {
        long workPrecision = precision + digits(exponent.coefficient()) + exponent.exponent() + 2 + (exponent.negative() ? 1 : 0);
        DecimalArithmetic work = new DecimalArithmetic(workPrecision, emin, emax, ROUND_HALF_EVEN, 0);
        Value positiveBase = finite(false, base.coefficient(), base.exponent());
        if (exponent.negative()) {
            positiveBase = work.divide(finite(false, BigInteger.ONE, 0), positiveBase);
        }
        Value parts = integralParts(exponent);
        BigInteger n = parts.coefficient().multiply(pow10(parts.exponent()));
        Value result;
        if (n.bitLength() <= 64) {
            // libmpdec's machine-word implementation traverses the exponent from the top.
            result = positiveBase;
            for (int bit = n.bitLength() - 2; bit >= 0; bit--) {
                result = work.multiply(result, result);
                if (n.testBit(bit)) {
                    result = work.multiply(result, positiveBase);
                }
                if (result.kind() != Kind.FINITE || isZero(result) && (work.status & CLAMPED) != 0) {
                    break;
                }
            }
        } else {
            result = finite(false, BigInteger.ONE, 0);
            while (n.signum() != 0) {
                if (n.testBit(0)) {
                    result = work.multiply(result, positiveBase);
                    if (result.kind() != Kind.FINITE || isZero(result) && (work.status & CLAMPED) != 0) {
                        break;
                    }
                }
                positiveBase = work.multiply(positiveBase, positiveBase);
                n = n.shiftRight(1);
            }
        }
        status |= work.status;
        if (result.kind() == Kind.INFINITY) {
            // Reapply the user's rounding mode to an overflow discovered in the work context.
            return overflow(negative);
        }
        return finish(finite(negative, result.coefficient(), result.exponent()));
    }

    /** Lower bound for the decimal exponent of abs(log10(a)), for early range checks. */
    private static long logExponentBound(Value a) {
        long adjusted = adjusted(a);
        if (adjusted >= 1) {
            return digits(adjusted) - 1;
        }
        if (adjusted <= -2) {
            return digits(-1 - adjusted) - 1;
        }
        BigInteger difference = a.coefficient().subtract(pow10(-a.exponent())).abs();
        return a.exponent() + digits(difference) - 1 - (adjusted == 0 ? 2 : 1);
    }

    /** Context-free rescaling used by integer conversions and formatting. */
    @TruffleBoundary
    static Value rescale(Value operand, long exponent, DecimalRounding rounding) {
        BigInteger coefficient = operand.coefficient();
        long shift = operand.exponent() - exponent;
        if (coefficient.signum() == 0) {
            return finite(operand.negative(), coefficient, exponent);
        }
        if (shift >= 0) {
            return finite(operand.negative(), coefficient.multiply(pow10(shift)), exponent);
        }
        BigInteger quotient;
        BigInteger remainder;
        BigInteger divisor;
        if (-shift > digits(coefficient)) {
            // Preserve the direction of rounding without allocating 10**(-shift).
            quotient = BigInteger.ZERO;
            remainder = BigInteger.ONE;
            divisor = BigInteger.TEN;
        } else {
            divisor = pow10(-shift);
            BigInteger[] parts = coefficient.divideAndRemainder(divisor);
            quotient = parts[0];
            remainder = parts[1];
        }
        DecimalArithmetic work = new DecimalArithmetic(1, 0, 0, rounding, 0);
        if (work.increment(operand.negative(), quotient, remainder, divisor)) {
            quotient = quotient.add(BigInteger.ONE);
        }
        return finite(operand.negative(), quotient, exponent);
    }

    @TruffleBoundary
    static BigInteger toInteger(PDecimal decimal, DecimalRounding rounding) {
        if (decimal.getExponent() >= 0) {
            return toInteger(decimal);
        }
        Value rounded = rescale(value(decimal), 0, rounding);
        if (rounded.negative()) {
            return rounded.coefficient().negate();
        }
        return rounded.coefficient();
    }

    @TruffleBoundary
    static Result quantize(PDecimal decimal, long exponent, PDecimalContext context, DecimalRounding rounding) {
        DecimalArithmetic work = new DecimalArithmetic(context.getPrecision(), context.getEmin(), context.getEmax(), rounding, context.getClamp());
        Value operand = value(decimal);
        Value result = work.checkNaNs(operand);
        if (result == null) {
            result = work.quantize(operand, exponent);
        }
        return new Result(result, work.status);
    }

    @TruffleBoundary
    static Result quantize(PDecimal decimal, PDecimal quantum, PDecimalContext context, DecimalRounding rounding) {
        DecimalArithmetic work = new DecimalArithmetic(context.getPrecision(), context.getEmin(), context.getEmax(), rounding, context.getClamp());
        Value operand = value(decimal);
        Value exp = value(quantum);
        Value result = work.checkNaNs(operand, exp);
        if (result == null) {
            if (operand.kind() == Kind.INFINITY && exp.kind() == Kind.INFINITY) {
                result = operand;
            } else if (exp.kind() == Kind.INFINITY) {
                result = work.invalid(INVALID);
            } else {
                result = work.quantize(operand, exp.exponent());
            }
        }
        return new Result(result, work.status);
    }

    private Value quantize(Value operand, long exponent) {
        long etiny = emin - precision + 1;
        if (operand.kind() == Kind.INFINITY || exponent < etiny || exponent > emax ||
                        !isZero(operand) && (adjusted(operand) > emax || adjusted(operand) - exponent + 1 > precision)) {
            return invalid(INVALID);
        }
        Value result = rescale(operand, exponent, rounding);
        if (digits(result.coefficient()) > precision || adjusted(result) > emax) {
            return invalid(INVALID);
        }
        if (!isZero(operand) && exponent > operand.exponent()) {
            status |= ROUNDED;
            if (compare(operand, result) != 0) {
                status |= INEXACT;
            }
        }
        if (!isZero(result) && adjusted(result) < emin) {
            status |= SUBNORMAL;
        }
        return finish(result);
    }

    /** Compare non-NaN values without constructing powers for widely separated exponents. */
    @TruffleBoundary
    static int compare(Value a, Value b) {
        boolean azero = isZero(a);
        boolean bzero = isZero(b);
        if (azero && bzero) {
            return 0;
        }
        if (a.negative() != b.negative()) {
            return a.negative() ? -1 : 1;
        }
        int magnitude;
        if (a.kind() == Kind.INFINITY || b.kind() == Kind.INFINITY) {
            if (a.kind() == b.kind()) {
                magnitude = 0;
            } else if (a.kind() == Kind.INFINITY) {
                magnitude = 1;
            } else {
                magnitude = -1;
            }
        } else if (azero || bzero) {
            magnitude = azero ? -1 : 1;
        } else {
            magnitude = Long.compare(adjusted(a), adjusted(b));
            if (magnitude == 0) {
                String adigits = a.coefficient().toString();
                String bdigits = b.coefficient().toString();
                int length = Math.max(adigits.length(), bdigits.length());
                for (int i = 0; i < length; i++) {
                    char ac = '0';
                    if (i < adigits.length()) {
                        ac = adigits.charAt(i);
                    }
                    char bc = '0';
                    if (i < bdigits.length()) {
                        bc = bdigits.charAt(i);
                    }
                    if (ac != bc) {
                        magnitude = Character.compare(ac, bc);
                        break;
                    }
                }
            }
        }
        return a.negative() ? -magnitude : magnitude;
    }

    @TruffleBoundary
    static BigInteger toInteger(PDecimal decimal) {
        BigInteger coefficient = decimal.getCoefficient();
        if (coefficient.signum() == 0) {
            return BigInteger.ZERO;
        }
        long exponent = decimal.getExponent();
        if (exponent >= 0) {
            coefficient = coefficient.multiply(pow10(exponent));
        } else if (-exponent >= digits(coefficient)) {
            return BigInteger.ZERO;
        } else {
            coefficient = coefficient.divide(pow10(-exponent));
        }
        if (decimal.isNegative()) {
            return coefficient.negate();
        }
        return coefficient;
    }

    @TruffleBoundary
    static double toFloat(PDecimal decimal) {
        if (decimal.getKind() == Kind.INFINITY) {
            return decimal.isNegative() ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        }
        if (decimal.getKind() == Kind.NAN) {
            return Math.copySign(Double.NaN, decimal.isNegative() ? -1.0 : 1.0);
        }
        if (decimal.getCoefficient().signum() == 0) {
            return decimal.isNegative() ? -0.0 : 0.0;
        }
        long adjusted = decimal.getExponent() + digits(decimal.getCoefficient()) - 1;
        if (adjusted > 308) {
            return decimal.isNegative() ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        }
        if (adjusted < -324) {
            return decimal.isNegative() ? -0.0 : 0.0;
        }
        return Double.parseDouble(decimal.toScientificString(true));
    }
}
