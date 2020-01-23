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

import static com.oracle.graal.python.nodes.SpecialMethodNames.__ABS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__BOOL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__DIVMOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__EQ__;
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
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RTRUEDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__STR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RSUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__TRUEDIV__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PComplex)
public class ComplexBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ComplexBuiltinsFactory.getFactories();
    }

    @GenerateNodeFactory
    @Builtin(name = __ABS__, minNumOfPositionalArgs = 1)
    abstract static class AbsNode extends PythonBuiltinNode {
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
                    return scalb(scaledH, middleExp);
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
    }

    @Builtin(name = __ADD__, minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class AddNode extends PythonBuiltinNode {
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

    @GenerateNodeFactory
    @Builtin(name = __RADD__, minNumOfPositionalArgs = 2)
    abstract static class RAddNode extends AddNode {
    }

    @GenerateNodeFactory
    @Builtin(name = __TRUEDIV__, minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class DivNode extends PythonBinaryBuiltinNode {
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
        PComplex doComplexPInt(PComplex right, PInt left) {
            return doComplexDouble(right, left.doubleValue());
        }

        @Specialization
        PComplex doComplex(PComplex left, PComplex right) {
            double opNormSq = right.getReal() * right.getReal() + right.getImag() * right.getImag();
            double realPart = left.getReal() * right.getReal() - left.getImag() * -right.getImag();
            double imagPart = left.getReal() * -right.getImag() + left.getImag() * right.getReal();
            return factory().createComplex(realPart / opNormSq, imagPart / opNormSq);
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doComplex(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @GenerateNodeFactory
    @Builtin(name = __RTRUEDIV__, minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class RDivNode extends PythonBinaryBuiltinNode {
        @Specialization
        PComplex doComplexDouble(PComplex right, double left) {
            double oprealSq = right.getReal() * right.getReal();
            double opimagSq = right.getImag() * right.getImag();
            double twice = 2 * right.getImag() * right.getReal();
            double realPart = right.getReal() * left;
            double imagPart = right.getImag() * left;
            return factory().createComplex(realPart / (oprealSq + opimagSq), -imagPart / twice);
        }

        @Specialization
        PComplex doComplexInt(PComplex right, long left) {
            double oprealSq = right.getReal() * right.getReal();
            double opimagSq = right.getImag() * right.getImag();
            double twice = 2 * right.getImag() * right.getReal();
            double realPart = right.getReal() * left;
            double imagPart = right.getImag() * left;
            return factory().createComplex(realPart / (oprealSq + opimagSq), -imagPart / twice);
        }

        @Specialization
        PComplex doComplexPInt(PComplex right, PInt left) {
            return doComplexDouble(right, left.doubleValue());
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doComplex(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @GenerateNodeFactory
    @Builtin(name = __DIVMOD__, minNumOfPositionalArgs = 2)
    abstract static class DivModNode extends PythonBinaryBuiltinNode {

        @Specialization
        @SuppressWarnings("unused")
        PComplex doComplexDouble(Object right, Object left) {
            throw raise(PythonErrorType.TypeError, "can't take floor or mod of complex number.");
        }
    }

    @GenerateNodeFactory
    @Builtin(name = __MUL__, minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class MulNode extends PythonBinaryBuiltinNode {
        @Specialization
        PComplex doComplexDouble(PComplex left, double right) {
            return factory().createComplex(left.getReal() * right, left.getImag() * right);
        }

        @Specialization
        PComplex doComplex(PComplex left, PComplex right) {
            double newReal = left.getReal() * right.getReal() - left.getImag() * right.getImag();
            double newImag = left.getReal() * right.getImag() + left.getImag() * right.getReal();
            return factory().createComplex(newReal, newImag);
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
    }

    @GenerateNodeFactory
    @Builtin(name = __RMUL__, minNumOfPositionalArgs = 2)
    abstract static class RMulNode extends MulNode {
    }

    @GenerateNodeFactory
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

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doComplex(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @GenerateNodeFactory
    @Builtin(name = __RSUB__, minNumOfPositionalArgs = 2)
    abstract static class RSubNode extends SubNode {
        @Specialization
        PComplex doComplexDouble(PComplex left, double right) {
            return factory().createComplex(right - left.getReal(), -left.getImag());
        }

        @Specialization
        PComplex doComplex(PComplex left, long right) {
            return factory().createComplex(right - left.getReal(), -left.getImag());
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
                    return left.getReal() == right.getValue().longValueExact();
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
        @Specialization
        boolean doComplex(PComplex left, PComplex right) {
            return left.greaterEqual(right);
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
        @Specialization
        boolean doComplex(PComplex left, PComplex right) {
            return left.greaterThan(right);
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
        @Specialization
        boolean doComplex(PComplex left, PComplex right) {
            return left.lessThan(right);
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
        @Specialization
        boolean doComplex(PComplex left, PComplex right) {
            return left.lessEqual(right);
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @GenerateNodeFactory
    @Builtin(name = __NE__, minNumOfPositionalArgs = 2)
    abstract static class NeNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean doComplex(PComplex left, PComplex right) {
            return left.notEqual(right);
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @GenerateNodeFactory
    @Builtin(name = __REPR__, minNumOfPositionalArgs = 1)
    abstract static class ReprNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        String repr(PComplex self) {
            return self.toString();
        }
    }

    @GenerateNodeFactory
    @Builtin(name = __STR__, minNumOfPositionalArgs = 1)
    abstract static class StrNode extends PythonBuiltinNode {
        @Specialization
        String repr(PComplex self) {
            return self.toString();
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
    abstract static class NegNode extends PythonBuiltinNode {
        @Specialization
        PComplex neg(PComplex self) {
            return factory().createComplex(-self.getReal(), -self.getImag());
        }
    }

    @GenerateNodeFactory
    @Builtin(name = __POS__, minNumOfPositionalArgs = 1)
    abstract static class PosNode extends PythonBuiltinNode {
        @Specialization
        PComplex pos(PComplex self) {
            return factory().createComplex(self.getReal(), self.getImag());
        }
    }

    @GenerateNodeFactory
    @Builtin(name = __GETNEWARGS__, minNumOfPositionalArgs = 1)
    abstract static class GetNewArgsNode extends PythonBuiltinNode {
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
}
