/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyComplexObject__cval__imag;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyComplexObject__cval__real;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ABS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___BOOL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___COMPLEX__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___FORMAT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GETNEWARGS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___HASH__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___LE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___LT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___MUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NEG__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___POS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___POW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RPOW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RSUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RTRUEDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___STR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___TRUEDIV__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ZeroDivisionError;
import static com.oracle.graal.python.runtime.formatting.FormattingUtils.validateForFloat;

import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ArgumentClinic.ClinicConversion;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.SysModuleBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.common.FormatNodeBase;
import com.oracle.graal.python.builtins.objects.complex.ComplexBuiltinsClinicProviders.FormatNodeClinicProviderGen;
import com.oracle.graal.python.builtins.objects.floats.FloatBuiltins;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyComplexCheckExactNode;
import com.oracle.graal.python.lib.PyObjectHashNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CoerceToComplexNode;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.formatting.ComplexFormatter;
import com.oracle.graal.python.runtime.formatting.InternalFormat;
import com.oracle.graal.python.runtime.formatting.InternalFormat.Spec;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PComplex)
public final class ComplexBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ComplexBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___COMPLEX__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ComplexNode extends PythonUnaryBuiltinNode {
        @Specialization
        PComplex complex(PComplex self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyComplexCheckExactNode check,
                        @Cached PythonObjectFactory.Lazy factory) {
            if (check.execute(inliningTarget, self)) {
                return self;
            } else {
                return factory.get(inliningTarget).createComplex(self.getReal(), self.getImag());
            }
        }
    }

    @GenerateNodeFactory
    @Builtin(name = J___ABS__, minNumOfPositionalArgs = 1)
    public abstract static class AbsNode extends PythonUnaryBuiltinNode {

        public abstract double executeDouble(Object arg);

        @Specialization
        static double abs(PComplex c,
                        @Bind("this") Node inliningTarget,
                        @Cached PRaiseNode.Lazy raiseNode) {
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
                        throw raiseNode.get(inliningTarget).raise(PythonErrorType.OverflowError, ErrorMessages.ABSOLUTE_VALUE_TOO_LARGE);
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

        @NeverDefault
        public static AbsNode create() {
            return ComplexBuiltinsFactory.AbsNodeFactory.create();
        }

    }

    @Builtin(name = J___RADD__, minNumOfPositionalArgs = 2)
    @Builtin(name = J___ADD__, minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class AddNode extends PythonBinaryBuiltinNode {
        @Specialization
        static PComplex doComplexLong(PComplex left, long right,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createComplex(left.getReal() + right, left.getImag());
        }

        @Specialization
        static PComplex doComplexPInt(PComplex left, PInt right,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createComplex(left.getReal() + right.doubleValue(), left.getImag());
        }

        @Specialization
        static PComplex doComplexDouble(PComplex left, double right,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createComplex(left.getReal() + right, left.getImag());
        }

        @Specialization
        static PComplex doComplex(PComplex left, PComplex right,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createComplex(left.getReal() + right.getReal(), left.getImag() + right.getImag());
        }

        @SuppressWarnings("unused")
        @Fallback
        static PNotImplemented doComplex(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = J___RTRUEDIV__, minNumOfPositionalArgs = 2, reverseOperation = true)
    @Builtin(name = J___TRUEDIV__, minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class DivNode extends PythonBinaryBuiltinNode {

        public abstract PComplex executeComplex(VirtualFrame frame, Object left, Object right);

        @NeverDefault
        public static DivNode create() {
            return ComplexBuiltinsFactory.DivNodeFactory.create();
        }

        @Specialization
        static PComplex doComplexDouble(PComplex left, double right,
                        @Shared @Cached PythonObjectFactory factory) {
            double opNormSq = right * right;
            double realPart = left.getReal() * right;
            double imagPart = left.getImag() * right;
            return factory.createComplex(realPart / opNormSq, imagPart / opNormSq);
        }

        @Specialization
        static PComplex doComplexInt(PComplex left, long right,
                        @Shared @Cached PythonObjectFactory factory) {
            double opNormSq = right * right;
            double realPart = left.getReal() * right;
            double imagPart = left.getImag() * right;
            return factory.createComplex(realPart / opNormSq, imagPart / opNormSq);
        }

        @Specialization
        static PComplex doComplexPInt(PComplex left, PInt right,
                        @Shared @Cached PythonObjectFactory factory) {
            return doComplexDouble(left, right.doubleValue(), factory);
        }

        @Specialization
        static PComplex doComplex(PComplex left, PComplex right,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedConditionProfile topConditionProfile,
                        @Cached InlinedConditionProfile zeroDivisionProfile,
                        @Shared @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            double absRightReal = right.getReal() < 0 ? -right.getReal() : right.getReal();
            double absRightImag = right.getImag() < 0 ? -right.getImag() : right.getImag();
            double real;
            double imag;
            if (topConditionProfile.profile(inliningTarget, absRightReal >= absRightImag)) {
                /* divide tops and bottom by right.real */
                if (zeroDivisionProfile.profile(inliningTarget, absRightReal == 0.0)) {
                    throw raiseNode.get(inliningTarget).raise(PythonErrorType.ZeroDivisionError, ErrorMessages.S_DIVISION_BY_ZERO, "complex");
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
            return factory.createComplex(real, imag);
        }

        @Specialization
        static PComplex doComplexDouble(double left, PComplex right,
                        @Shared @Cached PythonObjectFactory factory) {
            return doubleDivComplex(left, right, factory);
        }

        @Specialization
        static PComplex doComplexInt(long left, PComplex right,
                        @Shared @Cached PythonObjectFactory factory) {
            double oprealSq = right.getReal() * right.getReal();
            double opimagSq = right.getImag() * right.getImag();
            double realPart = right.getReal() * left;
            double imagPart = right.getImag() * left;
            double denom = oprealSq + opimagSq;
            return factory.createComplex(realPart / denom, -imagPart / denom);
        }

        @Specialization
        static PComplex doComplexPInt(PInt left, PComplex right,
                        @Shared @Cached PythonObjectFactory factory) {
            return doComplexDouble(left.doubleValue(), right, factory);
        }

        @SuppressWarnings("unused")
        @Fallback
        static PNotImplemented doComplex(Object left, Object right) {
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
    @Builtin(name = J___RMUL__, minNumOfPositionalArgs = 2)
    @Builtin(name = J___MUL__, minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class MulNode extends PythonBinaryBuiltinNode {
        @Specialization
        static PComplex doComplexDouble(PComplex left, double right,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createComplex(left.getReal() * right, left.getImag() * right);
        }

        @Specialization
        static PComplex doComplex(PComplex left, PComplex right,
                        @Shared @Cached PythonObjectFactory factory) {
            return multiply(left, right, factory);
        }

        @Specialization
        static PComplex doComplexLong(PComplex left, long right,
                        @Shared @Cached PythonObjectFactory factory) {
            return doComplexDouble(left, right, factory);
        }

        @Specialization
        static PComplex doComplexPInt(PComplex left, PInt right,
                        @Shared @Cached PythonObjectFactory factory) {
            return doComplexDouble(left, right.doubleValue(), factory);
        }

        @SuppressWarnings("unused")
        @Fallback
        static PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        static PComplex multiply(PComplex left, PComplex right, PythonObjectFactory factory) {
            double newReal = left.getReal() * right.getReal() - left.getImag() * right.getImag();
            double newImag = left.getReal() * right.getImag() + left.getImag() * right.getReal();
            return factory.createComplex(newReal, newImag);
        }
    }

    @GenerateNodeFactory
    @Builtin(name = J___RSUB__, minNumOfPositionalArgs = 2, reverseOperation = true)
    @Builtin(name = J___SUB__, minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class SubNode extends PythonBinaryBuiltinNode {
        @Specialization
        static PComplex doComplexDouble(PComplex left, double right,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createComplex(left.getReal() - right, left.getImag());
        }

        @Specialization
        static PComplex doComplex(PComplex left, PComplex right,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createComplex(left.getReal() - right.getReal(), left.getImag() - right.getImag());
        }

        @Specialization
        static PComplex doComplex(PComplex left, long right,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createComplex(left.getReal() - right, left.getImag());
        }

        @Specialization
        static PComplex doComplexDouble(double left, PComplex right,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createComplex(left - right.getReal(), -right.getImag());
        }

        @Specialization
        static PComplex doComplex(long left, PComplex right,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createComplex(left - right.getReal(), -right.getImag());
        }

        @Specialization
        static PComplex doComplexPInt(PComplex left, PInt right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createComplex(left.getReal() - right.doubleValueWithOverflow(inliningTarget), left.getImag());
        }

        @Specialization
        static PComplex doComplexPInt(PInt left, PComplex right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createComplex(left.doubleValueWithOverflow(inliningTarget) - right.getReal(), -right.getImag());
        }

        @SuppressWarnings("unused")
        @Fallback
        static PNotImplemented doComplex(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = J___RPOW__, minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3, reverseOperation = true)
    @Builtin(name = J___POW__, minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class PowerNode extends PythonTernaryBuiltinNode {

        static boolean isSmallPositive(long l) {
            return l > 0 && l <= 100;
        }

        static boolean isSmallNegative(long l) {
            return l <= 0 && l >= -100;
        }

        @Specialization(guards = "isSmallPositive(right)")
        static PComplex doComplexLongSmallPos(PComplex left, long right, @SuppressWarnings("unused") PNone mod,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PythonObjectFactory factory,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            return checkOverflow(inliningTarget, complexToSmallPositiveIntPower(left, right, factory), raiseNode);
        }

        @Specialization(guards = "isSmallNegative(right)")
        static PComplex doComplexLongSmallNeg(PComplex left, long right, @SuppressWarnings("unused") PNone mod,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PythonObjectFactory factory,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            return checkOverflow(inliningTarget, DivNode.doubleDivComplex(1.0, complexToSmallPositiveIntPower(left, -right, factory), factory), raiseNode);
        }

        @Specialization(guards = "!isSmallPositive(right) || !isSmallNegative(right)")
        static PComplex doComplexLong(PComplex left, long right, @SuppressWarnings("unused") PNone mod,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PythonObjectFactory factory,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            return checkOverflow(inliningTarget, complexToComplexPower(left, factory.createComplex(right, 0.0), inliningTarget), raiseNode);
        }

        @Specialization
        static PComplex doComplexComplex(PComplex left, PComplex right, @SuppressWarnings("unused") PNone mod,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            return checkOverflow(inliningTarget, complexToComplexPower(left, right, inliningTarget), raiseNode);
        }

        @Specialization
        static PComplex doGeneric(VirtualFrame frame, Object left, Object right, @SuppressWarnings("unused") PNone mod,
                        @Bind("this") Node inliningTarget,
                        @Cached CoerceToComplexNode coerceLeft,
                        @Cached CoerceToComplexNode coerceRight,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            return checkOverflow(inliningTarget, complexToComplexPower(coerceLeft.execute(frame, inliningTarget, left), coerceRight.execute(frame, inliningTarget, right), inliningTarget), raiseNode);
        }

        @Specialization(guards = "!isPNone(mod)")
        @SuppressWarnings("unused")
        static Object doGeneric(Object left, Object right, Object mod,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(ValueError, ErrorMessages.COMPLEX_MODULO);
        }

        private static PComplex complexToSmallPositiveIntPower(PComplex x, long n, PythonObjectFactory factory) {
            long mask = 1;
            PComplex r = factory.createComplex(1.0, 0.0);
            PComplex p = x;
            while (mask > 0 && n >= mask) {
                if ((n & mask) != 0) {
                    r = MulNode.multiply(r, p, factory);
                }
                mask <<= 1;
                p = MulNode.multiply(p, p, factory);
            }
            return r;
        }

        @TruffleBoundary
        private static PComplex complexToComplexPower(PComplex a, PComplex b, Node raisingNode) {
            PythonObjectFactory factory = PythonObjectFactory.getUncached();
            if (b.getReal() == 0.0 && b.getImag() == 0.0) {
                return factory.createComplex(1.0, 0.0);
            }
            if (a.getReal() == 0.0 && a.getImag() == 0.0) {
                if (b.getImag() != 0.0 || b.getReal() < 0.0) {
                    throw PRaiseNode.raiseUncached(raisingNode, ZeroDivisionError, ErrorMessages.COMPLEX_ZERO_TO_NEGATIVE_POWER);
                }
                return factory.createComplex(0.0, 0.0);
            }
            double vabs = Math.hypot(a.getReal(), a.getImag());
            double len = Math.pow(vabs, b.getReal());
            double at = Math.atan2(a.getImag(), a.getReal());
            double phase = at * b.getReal();
            if (b.getImag() != 0.0) {
                len /= Math.exp(at * b.getImag());
                phase += b.getImag() * Math.log(vabs);
            }
            return factory.createComplex(len * Math.cos(phase), len * Math.sin(phase));
        }

        private static PComplex checkOverflow(Node inliningTarget, PComplex result, PRaiseNode.Lazy raiseNode) {
            if (Double.isInfinite(result.getReal()) || Double.isInfinite(result.getImag())) {
                throw raiseNode.get(inliningTarget).raise(OverflowError, ErrorMessages.COMPLEX_EXPONENTIATION);
            }
            return result;
        }
    }

    @GenerateNodeFactory
    @Builtin(name = J___EQ__, minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class EqNode extends PythonBinaryBuiltinNode {
        @Specialization
        static boolean doComplex(PComplex left, PComplex right) {
            return left.equals(right);
        }

        @Specialization
        static boolean doComplexInt(PComplex left, long right,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedConditionProfile longFitsToDoubleProfile) {
            return left.getImag() == 0 && FloatBuiltins.ComparisonHelperNode.compareDoubleToLong(inliningTarget, left.getReal(), right, longFitsToDoubleProfile) == 0;
        }

        @Specialization
        static boolean doComplexInt(PComplex left, PInt right) {
            return left.getImag() == 0 && FloatBuiltins.ComparisonHelperNode.compareDoubleToLargeInt(left.getReal(), right) == 0;
        }

        @Specialization
        static boolean doComplexInt(PComplex left, double right) {
            return left.getImag() == 0 && left.getReal() == right;
        }

        @SuppressWarnings("unused")
        @Fallback
        static PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @GenerateNodeFactory
    @Builtin(name = J___GE__, minNumOfPositionalArgs = 2)
    abstract static class GeNode extends PythonBinaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        static PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @GenerateNodeFactory
    @Builtin(name = J___GT__, minNumOfPositionalArgs = 2)
    abstract static class GtNode extends PythonBinaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        static PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @GenerateNodeFactory
    @Builtin(name = J___LT__, minNumOfPositionalArgs = 2)
    abstract static class LtNode extends PythonBinaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        static PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @GenerateNodeFactory
    @Builtin(name = J___LE__, minNumOfPositionalArgs = 2)
    abstract static class LeNode extends PythonBinaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        static PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    @Builtin(name = J___NE__, minNumOfPositionalArgs = 2)
    abstract static class NeNode extends PythonBinaryBuiltinNode {
        @Specialization
        static boolean doComplex(PComplex left, PComplex right) {
            return left.notEqual(right);
        }

        @Specialization
        static boolean doComplex(PComplex left, long right,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedConditionProfile longFitsToDoubleProfile) {
            return left.getImag() != 0 || FloatBuiltins.ComparisonHelperNode.compareDoubleToLong(inliningTarget, left.getReal(), right, longFitsToDoubleProfile) != 0;
        }

        @Specialization
        static boolean doComplex(PComplex left, PInt right) {
            return left.getImag() != 0 || FloatBuiltins.ComparisonHelperNode.compareDoubleToLargeInt(left.getReal(), right) != 0;
        }

        @Specialization
        static boolean doComplex(PComplex left, double right) {
            return left.getImag() != 0 || left.getReal() != right;
        }

        @SuppressWarnings("unused")
        @Fallback
        static PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @GenerateNodeFactory
    @Builtin(name = J___REPR__, minNumOfPositionalArgs = 1)
    abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        TruffleString repr(PComplex self) {
            ComplexFormatter formatter = new ComplexFormatter(new Spec(-1, Spec.NONE), this);
            formatter.format(self);
            return formatter.pad().getResult();
        }
    }

    @GenerateNodeFactory
    @Builtin(name = J___STR__, minNumOfPositionalArgs = 1)
    abstract static class StrNode extends ReprNode {
    }

    @Builtin(name = J___FORMAT__, minNumOfPositionalArgs = 2, parameterNames = {"$self", "format_spec"})
    @ArgumentClinic(name = "format_spec", conversion = ClinicConversion.TString)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class FormatNode extends FormatNodeBase {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return FormatNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = "!formatString.isEmpty()")
        static TruffleString format(PComplex self, TruffleString formatString,
                        @Bind("this") Node inliningTarget,
                        @Cached PRaiseNode.Lazy raiseNode) {
            InternalFormat.Spec spec = InternalFormat.fromText(formatString, Spec.NONE, '>', inliningTarget);
            validateSpec(inliningTarget, spec, raiseNode);
            return doFormat(inliningTarget, self, spec);
        }

        @TruffleBoundary
        private static TruffleString doFormat(Node raisingNode, PComplex self, Spec spec) {
            ComplexFormatter formatter = new ComplexFormatter(validateForFloat(spec, "complex", raisingNode), raisingNode);
            formatter.format(self);
            return formatter.pad().getResult();
        }

        private static void validateSpec(Node inliningTarget, Spec spec, PRaiseNode.Lazy raiseNode) {
            if (spec.getFill(' ') == '0') {
                throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.ZERO_PADDING_NOT_ALLOWED_FOR_COMPLEX_FMT);
            }

            char align = spec.getAlign('>');
            if (align == '=') {
                throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.S_ALIGNMENT_FLAG_NOT_ALLOWED_FOR_COMPLEX_FMT, align);
            }
        }
    }

    @GenerateNodeFactory
    @Builtin(name = J___BOOL__, minNumOfPositionalArgs = 1)
    abstract static class BoolNode extends PythonUnaryBuiltinNode {
        @Specialization
        static boolean bool(PComplex self) {
            return self.getReal() != 0.0 || self.getImag() != 0.0;
        }
    }

    @GenerateNodeFactory
    @Builtin(name = J___NEG__, minNumOfPositionalArgs = 1)
    abstract static class NegNode extends PythonUnaryBuiltinNode {
        @Specialization
        static PComplex neg(PComplex self,
                        @Cached PythonObjectFactory factory) {
            return factory.createComplex(-self.getReal(), -self.getImag());
        }
    }

    @GenerateNodeFactory
    @Builtin(name = J___POS__, minNumOfPositionalArgs = 1)
    abstract static class PosNode extends PythonUnaryBuiltinNode {
        @Specialization
        static PComplex pos(PComplex self,
                        @Cached PythonObjectFactory factory) {
            return factory.createComplex(self.getReal(), self.getImag());
        }
    }

    @GenerateNodeFactory
    @Builtin(name = J___GETNEWARGS__, minNumOfPositionalArgs = 1)
    abstract static class GetNewArgsNode extends PythonUnaryBuiltinNode {
        @Specialization
        static PTuple get(PComplex self,
                        @Cached PythonObjectFactory factory) {
            return factory.createTuple(new Object[]{self.getReal(), self.getImag()});
        }
    }

    @GenerateNodeFactory
    @Builtin(name = "real", minNumOfPositionalArgs = 1, isGetter = true, doc = "the real part of a complex number")
    abstract static class RealNode extends PythonBuiltinNode {
        @Specialization
        static double get(PComplex self) {
            return self.getReal();
        }
    }

    @GenerateNodeFactory
    @Builtin(name = "imag", minNumOfPositionalArgs = 1, isGetter = true, doc = "the imaginary part of a complex number")
    abstract static class ImagNode extends PythonBuiltinNode {
        @Specialization
        static double get(PComplex self) {
            return self.getImag();
        }
    }

    @GenerateNodeFactory
    @Builtin(name = J___HASH__, minNumOfPositionalArgs = 1)
    abstract static class HashNode extends PythonUnaryBuiltinNode {
        @Specialization
        static long doPComplex(PComplex self) {
            return complexHash(self.getReal(), self.getImag());
        }

        @Specialization
        static long doNative(PythonAbstractNativeObject self,
                        @Cached CStructAccess.ReadDoubleNode read) {
            double real = read.readFromObj(self, PyComplexObject__cval__real);
            double imag = read.readFromObj(self, PyComplexObject__cval__imag);
            return complexHash(real, imag);
        }

        private static long complexHash(double real, double imag) {
            // just like CPython
            long realHash = PyObjectHashNode.hash(real);
            long imagHash = PyObjectHashNode.hash(imag);
            return realHash + SysModuleBuiltins.HASH_IMAG * imagHash;
        }
    }

    @GenerateNodeFactory
    @Builtin(name = "conjugate", minNumOfPositionalArgs = 1)
    abstract static class ConjugateNode extends PythonUnaryBuiltinNode {
        @Specialization
        static PComplex hash(PComplex self,
                        @Cached PythonObjectFactory factory) {
            return factory.createComplex(self.getReal(), -self.getImag());
        }
    }
}
