/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 *     one is included with the Software (each a "Larger Work" to which the
 *     Software is contributed by such licensors),
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

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

@CoreFunctions(extendClasses = PComplex.class)
public class ComplexBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinNode>> getNodeFactories() {
        return ComplexBuiltinsFactory.getFactories();
    }

    @GenerateNodeFactory
    @Builtin(name = SpecialMethodNames.__ABS__, fixedNumOfArguments = 1)
    static abstract class AbsNode extends PythonBuiltinNode {
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

    @GenerateNodeFactory
    @Builtin(name = SpecialMethodNames.__ADD__, fixedNumOfArguments = 2)
    static abstract class AddNode extends PythonBuiltinNode {
        @Specialization
        PComplex doComplexBoolean(PComplex left, boolean right) {
            final double rightDouble = right ? 1.0 : 0.0;
            PComplex result = factory().createComplex(left.getReal() + rightDouble, left.getImag());
            return result;
        }

        @Specialization
        PComplex doComplexInt(PComplex left, int right) {
            return factory().createComplex(left.getReal() + right, left.getImag());
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
        Object doComplex(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @GenerateNodeFactory
    @Builtin(name = SpecialMethodNames.__RADD__, fixedNumOfArguments = 2)
    static abstract class RAddNode extends AddNode {
    }

    @GenerateNodeFactory
    @Builtin(name = SpecialMethodNames.__TRUEDIV__, fixedNumOfArguments = 2)
    static abstract class DivNode extends PythonBuiltinNode {
        @Specialization
        PComplex doComplexDouble(PComplex left, double right) {
            double opNormSq = right * right;
            double realPart = left.getReal() * right;
            double imagPart = left.getImag() * right;
            return factory().createComplex(realPart / opNormSq, imagPart / opNormSq);
        }

        @Specialization
        PComplex doComplex(PComplex left, PComplex right) {
            double opNormSq = right.getReal() * right.getReal() + right.getImag() * right.getImag();
            double realPart = left.getReal() * right.getReal() - left.getImag() * -right.getImag();
            double imagPart = left.getReal() * -right.getImag() + left.getImag() * right.getReal();
            return factory().createComplex(realPart / opNormSq, imagPart / opNormSq);
        }
    }

    @GenerateNodeFactory
    @Builtin(name = SpecialMethodNames.__RTRUEDIV__, fixedNumOfArguments = 2)
    static abstract class RDivNode extends PythonBuiltinNode {
        @Specialization
        PComplex doComplexDouble(PComplex right, double left) {
            double opNormSq = left * left;
            double realPart = right.getReal() * left;
            double imagPart = right.getImag() * left;
            return factory().createComplex(opNormSq / realPart, opNormSq / imagPart);
        }
    }

    @GenerateNodeFactory
    @Builtin(name = SpecialMethodNames.__MUL__, fixedNumOfArguments = 2)
    static abstract class MulNode extends PythonBuiltinNode {
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
        PComplex doComplexInt(PComplex left, int right) {
            return doComplexDouble(left, right);
        }

        @Specialization
        PComplex doComplexBoolean(PComplex left, boolean right) {
            if (right) {
                return left;
            }
            return factory().createComplex(0.0, 0.0);
        }

        @SuppressWarnings("unused")
        @Fallback
        Object doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @GenerateNodeFactory
    @Builtin(name = SpecialMethodNames.__RMUL__, fixedNumOfArguments = 2)
    static abstract class RMulNode extends MulNode {
    }

    @GenerateNodeFactory
    @Builtin(name = SpecialMethodNames.__SUB__, fixedNumOfArguments = 2)
    static abstract class SubNode extends PythonBuiltinNode {
        @Specialization
        PComplex doComplexDouble(PComplex left, double right) {
            return factory().createComplex(left.getReal() - right, left.getImag());
        }

        @Specialization
        PComplex doComplex(PComplex left, PComplex right) {
            return factory().createComplex(left.getReal() - right.getReal(), left.getImag() - right.getImag());
        }
    }

    @GenerateNodeFactory
    @Builtin(name = SpecialMethodNames.__EQ__, fixedNumOfArguments = 2)
    static abstract class EqNode extends PythonBuiltinNode {
        @Specialization
        boolean doComplex(PComplex left, PComplex right) {
            return left.equals(right);
        }

        @SuppressWarnings("unused")
        @Specialization
        boolean doElse(Object left, Object right) {
            return false;
        }
    }

    @GenerateNodeFactory
    @Builtin(name = SpecialMethodNames.__GE__, fixedNumOfArguments = 2)
    static abstract class GeNode extends PythonBuiltinNode {
        @Specialization
        boolean doComplex(PComplex left, PComplex right) {
            return left.greaterEqual(right);
        }

        @SuppressWarnings("unused")
        @Specialization
        boolean doElse(Object left, Object right) {
            return false;
        }
    }

    @GenerateNodeFactory
    @Builtin(name = SpecialMethodNames.__GT__, fixedNumOfArguments = 2)
    static abstract class GtNode extends PythonBuiltinNode {
        @Specialization
        boolean doComplex(PComplex left, PComplex right) {
            return left.greaterThan(right);
        }

        @SuppressWarnings("unused")
        @Specialization
        boolean doElse(Object left, Object right) {
            return false;
        }
    }

    @GenerateNodeFactory
    @Builtin(name = SpecialMethodNames.__LT__, fixedNumOfArguments = 2)
    static abstract class LtNode extends PythonBuiltinNode {
        @Specialization
        boolean doComplex(PComplex left, PComplex right) {
            return left.lessThan(right);
        }

        @SuppressWarnings("unused")
        @Specialization
        boolean doElse(Object left, Object right) {
            return false;
        }
    }

    @GenerateNodeFactory
    @Builtin(name = SpecialMethodNames.__LE__, fixedNumOfArguments = 2)
    static abstract class LeNode extends PythonBuiltinNode {
        @Specialization
        boolean doComplex(PComplex left, PComplex right) {
            return left.lessEqual(right);
        }

        @SuppressWarnings("unused")
        @Specialization
        boolean doElse(Object left, Object right) {
            return false;
        }
    }

    @GenerateNodeFactory
    @Builtin(name = SpecialMethodNames.__NE__, fixedNumOfArguments = 2)
    static abstract class NeNode extends PythonBuiltinNode {
        @Specialization
        boolean doComplex(PComplex left, PComplex right) {
            return left.notEqual(right);
        }

        @SuppressWarnings("unused")
        @Specialization
        boolean doElse(Object left, Object right) {
            return false;
        }
    }

    @GenerateNodeFactory
    @Builtin(name = SpecialMethodNames.__REPR__, fixedNumOfArguments = 1)
    static abstract class ReprNode extends PythonBuiltinNode {
        @Specialization
        String repr(PComplex self) {
            return self.toString();
        }
    }

    @GenerateNodeFactory
    @Builtin(name = SpecialMethodNames.__STR__, fixedNumOfArguments = 1)
    static abstract class StrNode extends PythonBuiltinNode {
        @Specialization
        String repr(PComplex self) {
            return self.toString();
        }
    }

    @GenerateNodeFactory
    @Builtin(name = SpecialMethodNames.__BOOL__, fixedNumOfArguments = 1)
    static abstract class BoolNode extends PythonBuiltinNode {
        @Specialization
        boolean bool(PComplex self) {
            return self.getReal() != 0.0 || self.getImag() != 0.0;
        }
    }

    @GenerateNodeFactory
    @Builtin(name = SpecialMethodNames.__NEG__, fixedNumOfArguments = 1)
    static abstract class NegNode extends PythonBuiltinNode {
        @Specialization
        PComplex neg(PComplex self) {
            return factory().createComplex(-self.getReal(), -self.getImag());
        }
    }

    @GenerateNodeFactory
    @Builtin(name = SpecialMethodNames.__POS__, fixedNumOfArguments = 1)
    static abstract class PosNode extends PythonBuiltinNode {
        @Specialization
        PComplex pos(PComplex self) {
            return factory().createComplex(self.getReal(), self.getImag());
        }
    }

    @GenerateNodeFactory
    @Builtin(name = "__getnewargs__", fixedNumOfArguments = 1)
    static abstract class GetNewArgsNode extends PythonBuiltinNode {
        @Specialization
        PTuple get(PComplex self) {
            return factory().createTuple(new Object[]{self.getReal(), self.getImag()});
        }
    }

    @GenerateNodeFactory
    @Builtin(name = "real", fixedNumOfArguments = 1, isGetter = true, doc = "the real part of a complex number")
    static abstract class RealNode extends PythonBuiltinNode {
        @Specialization
        double get(PComplex self) {
            return self.getReal();
        }
    }

    @GenerateNodeFactory
    @Builtin(name = "imag", fixedNumOfArguments = 1, isGetter = true, doc = "the imaginary part of a complex number")
    static abstract class ImagNode extends PythonBuiltinNode {
        @Specialization
        double get(PComplex self) {
            return self.getReal();
        }
    }
}
