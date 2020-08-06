/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.complex;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ABS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__BOOL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__DIVMOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__FORMAT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETNEWARGS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__HASH__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__MUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEG__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__POS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__POW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RDIVMOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RPOW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RSUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RTRUEDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__STR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__TRUEDIV__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ZeroDivisionError;
import static com.oracle.graal.python.runtime.formatting.FormattingUtils.validateAndPrepareForFloat;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CoerceToComplexNode;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.formatting.ComplexFormatter;
import com.oracle.graal.python.runtime.formatting.InternalFormat;
import com.oracle.graal.python.runtime.formatting.InternalFormat.Spec;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PComplex)
public class ComplexBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ComplexBuiltinsFactory.getFactories();
    }

    @GenerateNodeFactory
    @Builtin(name = __ABS__, minNumOfPositionalArgs = 1)
    public abstract static class AbsNode extends PythonUnaryBuiltinNode {

        public abstract double executeDouble(Object arg);

        @Specialization
        double abs(PComplex c) {
            double x = c.getReal();
            double y = c.getImag();
            if (Double.isInfinite(x) || Double.isInfinite(y)) {
                return Double.POSITIVE_INFINITY;
            } else if (Double.isNaN(x) || Double.isNaN(y)) {
                return Double.NaN;
            } else {

                final int expX = getExponent(x);
                final int expY = getExponent(y);
                if (expX > expY + 27) {
                    // y is neglectible with respect to x
                    return abs(x);
                } else if (expY > expX + 27) {
                    // x is neglectible with respect to y
                    return abs(y);
                } else {

                    // find an intermediate scale to avoid both overflow and
                    // underflow
                    final int middleExp = (expX + expY) / 2;

                    // scale parameters without losing precision
                    final double scaledX = scalb(x, -middleExp);
                    final double scaledY = scalb(y, -middleExp);

                    // compute scaled hypotenuse
                    final double scaledH = Math.sqrt(scaledX * scaledX + scaledY * scaledY);

                    // remove scaling
                    double r = scalb(scaledH, middleExp);
                    if (Double.isInfinite(r)) {
                        throw raise(PythonErrorType.OverflowError, ErrorMessages.ABSOLUTE_VALUE_TOO_LARGE);
                    }
                    return r;
                }
            }
        }

        private static final long MASK_NON_SIGN_LONG = 0x7fffffffffffffffL;

        static double abs(double x) {
            return Double.longBitsToDouble(MASK_NON_SIGN_LONG & Double.doubleToRawLongBits(x));
        }

        static double scalb(final double d, final int n) {

            // first simple and fast handling when 2^n can be represented using
            // normal numbers
            if ((n > -1023) && (n < 1024)) {
                return d * Double.longBitsToDouble(((long) (n + 1023)) << 52);
            }

            // handle special cases
            if (Double.isNaN(d) || Double.isInfinite(d) || (d == 0)) {
                return d;
            }
            if (n < -2098) {
                return (d > 0) ? 0.0 : -0.0;
            }
            if (n > 2097) {
                return (d > 0) ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
            }

            // decompose d
            final long bits = Double.doubleToRawLongBits(d);
            final long sign = bits & 0x8000000000000000L;
            int exponent = ((int) (bits >>> 52)) & 0x7ff;
            long mantissa = bits & 0x000fffffffffffffL;

            // compute scaled exponent
            int scaledExponent = exponent + n;

            if (n < 0) {
                // we are really in the case n <= -1023
                if (scaledExponent > 0) {
                    // both the input and the result are normal numbers, we only
                    // adjust the exponent
                    return Double.longBitsToDouble(sign | (((long) scaledExponent) << 52) | mantissa);
                } else if (scaledExponent > -53) {
                    // the input is a normal number and the result is a subnormal
                    // number

                    // recover the hidden mantissa bit
                    mantissa = mantissa | (1L << 52);

                    // scales down complete mantissa, hence losing least significant
                    // bits
                    final long mostSignificantLostBit = mantissa & (1L << (-scaledExponent));
                    mantissa = mantissa >>> (1 - scaledExponent);
                    if (mostSignificantLostBit != 0) {
                        // we need to add 1 bit to round up the result
                        mantissa++;
                    }
                    return Double.longBitsToDouble(sign | mantissa);

                } else {
                    // no need to compute the mantissa, the number scales down to 0
                    return (sign == 0L) ? 0.0 : -0.0;
                }
            } else {
                // we are really in the case n >= 1024
                if (exponent == 0) {

                    // the input number is subnormal, normalize it
                    while ((mantissa >>> 52) != 1) {
                        mantissa = mantissa << 1;
                        --scaledExponent;
                    }
                    ++scaledExponent;
                    mantissa = mantissa & 0x000fffffffffffffL;

                    if (scaledExponent < 2047) {
                        return Double.longBitsToDouble(sign | (((long) scaledExponent) << 52) | mantissa);
                    } else {
                        return (sign == 0L) ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
                    }

                } else if (scaledExponent < 2047) {
                    return Double.longBitsToDouble(sign | (((long) scaledExponent) << 52) | mantissa);
                } else {
                    return (sign == 0L) ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
                }
            }
        }

        static int getExponent(final double d) {
            // NaN and Infinite will return 1024 anywho so can use raw bits
            return (int) ((Double.doubleToRawLongBits(d) >>> 52) & 0x7ff) - 1023;
        }

        public static AbsNode create() {
            return ComplexBuiltinsFactory.AbsNodeFactory.create();
        }

    }

    @Builtin(name = __RADD__, minNumOfPositionalArgs = 2)
    @Builtin(name = __ADD__, minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class AddNode extends PythonBinaryBuiltinNode {
        @Specialization
        PComplex doComplexLong(PComplex left, long right) {
            return factory().createComplex(left.getReal() + right, left.getImag());
        }

        @Specialization
        PComplex doComplexPInt(PComplex left, PInt right) {
            return factory().createComplex(left.getReal() + right.doubleValue(), left.getImag());
        }

        @Specialization
        PComplex doComplexDouble(PComplex left, double right) {
            PComplex result = factory().createComplex(left.getReal() + right, left.getImag());
            return result;
        }

        @Specialization
        PComplex doComplex(PComplex left, PComplex right) {
            return factory().createComplex(left.getReal() + right.getReal(), left.getImag() + right.getImag());
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doComplex(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __RTRUEDIV__, minNumOfPositionalArgs = 2, reverseOperation = true)
    @Builtin(name = __TRUEDIV__, minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class DivNode extends PythonBinaryBuiltinNode {

        public abstract PComplex executeComplex(VirtualFrame frame, Object left, Object right);

        public static DivNode create() {
            return ComplexBuiltinsFactory.DivNodeFactory.create();
        }

        @Specialization
        PComplex doComplexDouble(PComplex left, double right) {
            double opNormSq = right * right;
            double realPart = left.getReal() * right;
            double imagPart = left.getImag() * right;
            return factory().createComplex(realPart / opNormSq, imagPart / opNormSq);
        }

        @Specialization
        PComplex doComplexInt(PComplex left, long right) {
            double opNormSq = right * right;
            double realPart = left.getReal() * right;
            double imagPart = left.getImag() * right;
            return factory().createComplex(realPart / opNormSq, imagPart / opNormSq);
        }

        @Specialization
        PComplex doComplexPInt(PComplex left, PInt right) {
            return doComplexDouble(left, right.doubleValue());
        }

        @Specialization
        PComplex doComplex(PComplex left, PComplex right,
                        @Cached("createBinaryProfile()") ConditionProfile topConditionProfile,
                        @Cached("createBinaryProfile()") ConditionProfile zeroDivisionProfile) {
            double absRightReal = right.getReal() < 0 ? -right.getReal() : right.getReal();
            double absRightImag = right.getImag() < 0 ? -right.getImag() : right.getImag();
            double real;
            double imag;
            if (topConditionProfile.profile(absRightReal >= absRightImag)) {
                /* divide tops and bottom by right.real */
                if (zeroDivisionProfile.profile(absRightReal == 0.0)) {
                    throw raise(PythonErrorType.ZeroDivisionError, ErrorMessages.S_DIVISION_BY_ZERO, "complex");
                } else {
                    double ratio = right.getImag() / right.getReal();
                    double denom = right.getReal() + right.getImag() * ratio;
                    real = (left.getReal() + left.getImag() * ratio) / denom;
                    imag = (left.getImag() - left.getReal() * ratio) / denom;
                }
            } else {
                /* divide tops and bottom by right.imag */
                double ratio = right.getReal() / right.getImag();
                double denom = right.getReal() * ratio + right.getImag();
                real = (left.getReal() * ratio + left.getImag()) / denom;
                imag = (left.getImag() * ratio - left.getReal()) / denom;
            }
            return factory().createComplex(real, imag);
        }

        @Specialization
        PComplex doComplexDouble(double left, PComplex right) {
            return doubleDivComplex(left, right, factory());
        }

        @Specialization
        PComplex doComplexInt(long left, PComplex right) {
            double oprealSq = right.getReal() * right.getReal();
            double opimagSq = right.getImag() * right.getImag();
            double realPart = right.getReal() * left;
            double imagPart = right.getImag() * left;
            double denom = oprealSq + opimagSq;
            return factory().createComplex(realPart / denom, -imagPart / denom);
        }

        @Specialization
        PComplex doComplexPInt(PInt left, PComplex right) {
            return doComplexDouble(left.doubleValue(), right);
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doComplex(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        static PComplex doubleDivComplex(double left, PComplex right, PythonObjectFactory factory) {
            double oprealSq = right.getReal() * right.getReal();
            double opimagSq = right.getImag() * right.getImag();
            double realPart = right.getReal() * left;
            double imagPart = right.getImag() * left;
            double denom = oprealSq + opimagSq;
            return factory.createComplex(realPart / denom, -imagPart / denom);
        }
    }

    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class RDivNode extends PythonBinaryBuiltinNode {
        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doComplex(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @GenerateNodeFactory
    @Builtin(name = __RDIVMOD__, minNumOfPositionalArgs = 2, reverseOperation = true)
    @Builtin(name = __DIVMOD__, minNumOfPositionalArgs = 2)
    abstract static class DivModNode extends PythonBinaryBuiltinNode {

        @Specialization
        @SuppressWarnings("unused")
        PComplex doComplexDouble(Object right, Object left) {
            throw raise(PythonErrorType.TypeError, ErrorMessages.CANT_TAKE_FLOOR_OR_MOD_OF_COMPLEX);
        }
    }

    @GenerateNodeFactory
    @Builtin(name = __RMUL__, minNumOfPositionalArgs = 2)
    @Builtin(name = __MUL__, minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class MulNode extends PythonBinaryBuiltinNode {
        @Specialization
        PComplex doComplexDouble(PComplex left, double right) {
            return factory().createComplex(left.getReal() * right, left.getImag() * right);
        }

        @Specialization
        PComplex doComplex(PComplex left, PComplex right) {
            return multiply(left, right, factory());
        }

        @Specialization
        PComplex doComplexLong(PComplex left, long right) {
            return doComplexDouble(left, right);
        }

        @Specialization
        PComplex doComplexPInt(PComplex left, PInt right) {
            return doComplexDouble(left, right.doubleValue());
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        static PComplex multiply(PComplex left, PComplex right, PythonObjectFactory factory) {
            double newReal = left.getReal() * right.getReal() - left.getImag() * right.getImag();
            double newImag = left.getReal() * right.getImag() + left.getImag() * right.getReal();
            return factory.createComplex(newReal, newImag);
        }
    }

    @GenerateNodeFactory
    @Builtin(name = __RSUB__, minNumOfPositionalArgs = 2, reverseOperation = true)
    @Builtin(name = __SUB__, minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class SubNode extends PythonBinaryBuiltinNode {
        @Specialization
        PComplex doComplexDouble(PComplex left, double right) {
            return factory().createComplex(left.getReal() - right, left.getImag());
        }

        @Specialization
        PComplex doComplex(PComplex left, PComplex right) {
            return factory().createComplex(left.getReal() - right.getReal(), left.getImag() - right.getImag());
        }

        @Specialization
        PComplex doComplex(PComplex left, long right) {
            return factory().createComplex(left.getReal() - right, left.getImag());
        }

        @Specialization
        PComplex doComplexDouble(double left, PComplex right) {
            return factory().createComplex(left - right.getReal(), -right.getImag());
        }

        @Specialization
        PComplex doComplex(long left, PComplex right) {
            return factory().createComplex(left - right.getReal(), -right.getImag());
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doComplex(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __RPOW__, minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3, reverseOperation = true)
    @Builtin(name = __POW__, minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    @ReportPolymorphism
    abstract static class PowerNode extends PythonTernaryBuiltinNode {

        static boolean isSmallPositive(long l) {
            return l > 0 && l <= 100;
        }

        static boolean isSmallNegative(long l) {
            return l <= 0 && l >= -100;
        }

        @Specialization(guards = "isSmallPositive(right)")
        PComplex doComplexLongSmallPos(PComplex left, long right, @SuppressWarnings("unused") PNone mod) {
            return checkOverflow(complexToSmallPositiveIntPower(left, right));
        }

        @Specialization(guards = "isSmallNegative(right)")
        PComplex doComplexLongSmallNeg(PComplex left, long right, @SuppressWarnings("unused") PNone mod) {
            return checkOverflow(DivNode.doubleDivComplex(1.0, complexToSmallPositiveIntPower(left, -right), factory()));
        }

        @Specialization(guards = "!isSmallPositive(right) || !isSmallNegative(right)")
        PComplex doComplexLong(PComplex left, long right, @SuppressWarnings("unused") PNone mod) {
            return checkOverflow(complexToComplexPower(left, factory().createComplex(right, 0.0)));
        }

        @Specialization
        PComplex doComplexComplex(PComplex left, PComplex right, @SuppressWarnings("unused") PNone mod) {
            return checkOverflow(complexToComplexPower(left, right));
        }

        @Specialization
        PComplex doGeneric(VirtualFrame frame, Object left, Object right, @SuppressWarnings("unused") PNone mod,
                        @Cached CoerceToComplexNode coerceLeft,
                        @Cached CoerceToComplexNode coerceRight) {
            return checkOverflow(complexToComplexPower(coerceLeft.execute(frame, left), coerceRight.execute(frame, right)));
        }

        @Specialization(guards = "!isPNone(mod)")
        @SuppressWarnings("unused")
        Object doGeneric(Object left, Object right, Object mod) {
            throw raise(ValueError, ErrorMessages.COMPLEX_MODULO);
        }

        private PComplex complexToSmallPositiveIntPower(PComplex x, long n) {
            long mask = 1;
            PComplex r = factory().createComplex(1.0, 0.0);
            PComplex p = x;
            while (mask > 0 && n >= mask) {
                if ((n & mask) != 0) {
                    r = MulNode.multiply(r, p, factory());
                }
                mask <<= 1;
                p = MulNode.multiply(p, p, factory());
            }
            return r;
        }

        @TruffleBoundary
        private PComplex complexToComplexPower(PComplex a, PComplex b) {
            if (b.getReal() == 0.0 && b.getImag() == 0.0) {
                return factory().createComplex(1.0, 0.0);
            }
            if (a.getReal() == 0.0 && a.getImag() == 0.0) {
                if (b.getImag() != 0.0 || b.getReal() < 0.0) {
                    throw raise(ZeroDivisionError, ErrorMessages.COMPLEX_ZERO_TO_NEGATIVE_POWER);
                }
                return factory().createComplex(0.0, 0.0);
            }
            double vabs = Math.hypot(a.getReal(), a.getImag());
            double len = Math.pow(vabs, b.getReal());
            double at = Math.atan2(a.getImag(), a.getReal());
            double phase = at * b.getReal();
            if (b.getImag() != 0.0) {
                len /= Math.exp(at * b.getImag());
                phase += b.getImag() * Math.log(vabs);
            }
            return factory().createComplex(len * Math.cos(phase), len * Math.sin(phase));
        }

        private PComplex checkOverflow(PComplex result) {
            if (Double.isInfinite(result.getReal()) || Double.isInfinite(result.getImag())) {
                throw raise(OverflowError, ErrorMessages.COMPLEX_EXPONENTIATION);
            }
            return result;
        }
    }

    @GenerateNodeFactory
    @Builtin(name = __EQ__, minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class EqNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean doComplex(PComplex left, PComplex right) {
            return left.equals(right);
        }

        @Specialization
        boolean doComplexInt(PComplex left, long right) {
            if (left.getImag() == 0) {
                return left.getReal() == right;
            }
            return false;
        }

        @Specialization
        boolean doComplexInt(PComplex left, PInt right) {
            if (left.getImag() == 0) {
                try {
                    return left.getReal() == right.longValueExact();
                } catch (ArithmeticException e) {
                    // do nothing -> return false;
                }
            }
            return false;
        }

        @Specialization
        boolean doComplexInt(PComplex left, double right) {
            if (left.getImag() == 0) {
                return left.getReal() == right;
            }
            return false;
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @GenerateNodeFactory
    @Builtin(name = __GE__, minNumOfPositionalArgs = 2)
    abstract static class GeNode extends PythonBinaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        PNotImplemented doComplex(PComplex left, PComplex right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @GenerateNodeFactory
    @Builtin(name = __GT__, minNumOfPositionalArgs = 2)
    abstract static class GtNode extends PythonBinaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        PNotImplemented doComplex(PComplex left, PComplex right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @GenerateNodeFactory
    @Builtin(name = __LT__, minNumOfPositionalArgs = 2)
    abstract static class LtNode extends PythonBinaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        PNotImplemented doComplex(PComplex left, PComplex right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @GenerateNodeFactory
    @Builtin(name = __LE__, minNumOfPositionalArgs = 2)
    abstract static class LeNode extends PythonBinaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        PNotImplemented doComplex(PComplex left, PComplex right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    @Builtin(name = __NE__, minNumOfPositionalArgs = 2)
    abstract static class NeNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean doComplex(PComplex left, PComplex right) {
            return left.notEqual(right);
        }

        @Specialization
        boolean doComplex(PComplex left, long right) {
            return left.getImag() != 0 || left.getReal() != right;
        }

        @Specialization
        boolean doComplex(PComplex left, PInt right) {
            return left.getImag() != 0 || left.getReal() != right.doubleValue();
        }

        @Specialization
        boolean doComplex(PComplex left, double right) {
            return left.getImag() != 0 || left.getReal() != right;
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @GenerateNodeFactory
    @Builtin(name = __REPR__, minNumOfPositionalArgs = 1)
    abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        String repr(PComplex self) {
            return repr(self, getCore());
        }

        @TruffleBoundary
        private static String repr(PComplex self, PythonCore core) {
            ComplexFormatter formatter = new ComplexFormatter(core, new Spec(-1, Spec.NONE));
            formatter.format(self);
            return formatter.pad().getResult();
        }
    }

    @GenerateNodeFactory
    @Builtin(name = __STR__, minNumOfPositionalArgs = 1)
    abstract static class StrNode extends ReprNode {
    }

    @Builtin(name = __FORMAT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class FormatNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = "formatString.isEmpty()")
        Object emptyFormat(VirtualFrame frame, Object self, @SuppressWarnings("unused") String formatString,
                        @Cached("create(__STR__)") LookupAndCallUnaryNode strCall) {
            return strCall.executeObject(frame, self);
        }

        @Specialization(guards = "!formatString.isEmpty()")
        @TruffleBoundary
        String format(PComplex self, String formatString) {
            InternalFormat.Spec spec = InternalFormat.fromText(getCore(), formatString, __FORMAT__);
            validateSpec(spec);
            ComplexFormatter formatter = new ComplexFormatter(getCore(), validateAndPrepareForFloat(spec, getCore(), "complex"));
            formatter.format(self);
            return formatter.pad().getResult();
        }

        @Fallback
        Object doOther(@SuppressWarnings("unused") Object self, Object format) {
            throw raise(TypeError, ErrorMessages.ARG_D_MUST_BE_S_NOT_P, "format()", 2, "str", format);
        }

        private void validateSpec(Spec spec) {
            if (spec.getFill(' ') == '0') {
                throw raise(ValueError, ErrorMessages.ZERO_PADDING_NOT_ALLOWED_FOR_COMPLEX_FMT);
            }

            char align = spec.getAlign('>');
            if (align == '=') {
                throw raise(ValueError, ErrorMessages.S_ALIGNMENT_FLAG_NOT_ALLOWED_FOR_COMPLEX_FMT, align);
            }
        }
    }

    @GenerateNodeFactory
    @Builtin(name = __BOOL__, minNumOfPositionalArgs = 1)
    abstract static class BoolNode extends PythonUnaryBuiltinNode {
        @Specialization
        boolean bool(PComplex self) {
            return self.getReal() != 0.0 || self.getImag() != 0.0;
        }
    }

    @GenerateNodeFactory
    @Builtin(name = __NEG__, minNumOfPositionalArgs = 1)
    abstract static class NegNode extends PythonUnaryBuiltinNode {
        @Specialization
        PComplex neg(PComplex self) {
            return factory().createComplex(-self.getReal(), -self.getImag());
        }
    }

    @GenerateNodeFactory
    @Builtin(name = __POS__, minNumOfPositionalArgs = 1)
    abstract static class PosNode extends PythonUnaryBuiltinNode {
        @Specialization
        PComplex pos(PComplex self) {
            return factory().createComplex(self.getReal(), self.getImag());
        }
    }

    @GenerateNodeFactory
    @Builtin(name = __GETNEWARGS__, minNumOfPositionalArgs = 1)
    abstract static class GetNewArgsNode extends PythonUnaryBuiltinNode {
        @Specialization
        PTuple get(PComplex self) {
            return factory().createTuple(new Object[]{self.getReal(), self.getImag()});
        }
    }

    @GenerateNodeFactory
    @Builtin(name = "real", minNumOfPositionalArgs = 1, isGetter = true, doc = "the real part of a complex number")
    abstract static class RealNode extends PythonBuiltinNode {
        @Specialization
        double get(PComplex self) {
            return self.getReal();
        }
    }

    @GenerateNodeFactory
    @Builtin(name = "imag", minNumOfPositionalArgs = 1, isGetter = true, doc = "the imaginary part of a complex number")
    abstract static class ImagNode extends PythonBuiltinNode {
        @Specialization
        double get(PComplex self) {
            return self.getImag();
        }
    }

    @GenerateNodeFactory
    @Builtin(name = __HASH__, minNumOfPositionalArgs = 1)
    abstract static class HashNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        int hash(PComplex self) {
            // just like CPython
            int realHash = Double.hashCode(self.getReal());
            int imagHash = Double.hashCode(self.getImag());
            return realHash + PComplex.IMAG_MULTIPLIER * imagHash;
        }
    }

    @GenerateNodeFactory
    @Builtin(name = "conjugate", minNumOfPositionalArgs = 1)
    abstract static class ConjugateNode extends PythonUnaryBuiltinNode {
        @Specialization
        PComplex hash(PComplex self) {
            return factory().createComplex(self.getReal(), -self.getImag());
        }
    }
}
