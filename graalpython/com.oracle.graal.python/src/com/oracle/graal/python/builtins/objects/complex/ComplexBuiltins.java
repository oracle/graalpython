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
import com.oracle.graal.python.lib.PyComplexCheckNode;
import com.oracle.graal.python.lib.PyFloatAsDoubleNode;
import com.oracle.graal.python.lib.PyFloatCheckNode;
import com.oracle.graal.python.lib.PyLongAsDoubleNode;
import com.oracle.graal.python.lib.PyLongCheckNode;
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
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.formatting.ComplexFormatter;
import com.oracle.graal.python.runtime.formatting.InternalFormat;
import com.oracle.graal.python.runtime.formatting.InternalFormat.Spec;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PComplex)
public final class ComplexBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ComplexBuiltinsFactory.getFactories();
    }

    @ValueType
    static final class ComplexValue {
        private final double real;
        private final double imag;

        ComplexValue(double real, double imag) {
            this.real = real;
            this.imag = imag;
        }

        public double getReal() {
            return real;
        }

        public double getImag() {
            return imag;
        }
    }

    @GenerateInline
    @GenerateCached(false)
    abstract static class ToComplexValueNode extends Node {
        public abstract ComplexValue execute(Node inliningTarget, Object v);

        @Specialization
        static ComplexValue doComplex(PComplex v) {
            return new ComplexValue(v.getReal(), v.getImag());
        }

        @Specialization(guards = "check.execute(inliningTarget, v)", limit = "1")
        static ComplexValue doNative(@SuppressWarnings("unused") Node inliningTarget, PythonAbstractNativeObject v,
                        @SuppressWarnings("unused") @Cached PyComplexCheckNode check,
                        @Cached(inline = false) CStructAccess.ReadDoubleNode read) {
            double real = read.readFromObj(v, PyComplexObject__cval__real);
            double imag = read.readFromObj(v, PyComplexObject__cval__imag);
            return new ComplexValue(real, imag);
        }

        @Specialization
        static ComplexValue doInt(int v) {
            return new ComplexValue(v, 0);
        }

        @Specialization
        static ComplexValue doDouble(double v) {
            return new ComplexValue(v, 0);
        }

        @Specialization(guards = "check.execute(inliningTarget, v)", limit = "1")
        static ComplexValue doIntGeneric(Node inliningTarget, Object v,
                        @SuppressWarnings("unused") @Cached PyLongCheckNode check,
                        @Cached PyLongAsDoubleNode longAsDoubleNode) {
            return new ComplexValue(longAsDoubleNode.execute(inliningTarget, v), 0);
        }

        @Specialization(guards = "check.execute(inliningTarget, v)", limit = "1")
        static ComplexValue doFloatGeneric(Node inliningTarget, Object v,
                        @SuppressWarnings("unused") @Cached PyFloatCheckNode check,
                        @Cached PyFloatAsDoubleNode floatAsDoubleNode) {
            return new ComplexValue(floatAsDoubleNode.execute(null, inliningTarget, v), 0);
        }

        @Fallback
        @SuppressWarnings("unused")
        static ComplexValue doOther(Node inliningTarget, Object v) {
            return null;
        }
    }

    @Builtin(name = J___COMPLEX__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ComplexNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object complex(Object self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyComplexCheckExactNode check,
                        @Cached ToComplexValueNode toComplexValueNode,
                        @Cached PythonObjectFactory.Lazy factory) {
            if (check.execute(inliningTarget, self)) {
                return self;
            } else {
                ComplexValue c = toComplexValueNode.execute(inliningTarget, self);
                return factory.get(inliningTarget).createComplex(c.real, c.imag);
            }
        }
    }

    @GenerateNodeFactory
    @Builtin(name = J___ABS__, minNumOfPositionalArgs = 1)
    public abstract static class AbsNode extends PythonUnaryBuiltinNode {

        public abstract double executeDouble(Object arg);

        @Specialization
        static double abs(Object self,
                        @Bind("this") Node inliningTarget,
                        @Cached ToComplexValueNode toComplexValueNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            ComplexValue c = toComplexValueNode.execute(inliningTarget, self);
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
    @GenerateNodeFactory
    abstract static class AddNode extends PythonBinaryBuiltinNode {
        @Specialization
        static PComplex doComplex(PComplex left, int right,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createComplex(left.getReal() + right, left.getImag());
        }

        @Specialization
        static PComplex doComplex(PComplex left, double right,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createComplex(left.getReal() + right, left.getImag());
        }

        @Specialization
        static Object doComplex(Object leftObj, Object rightObj,
                        @Bind("this") Node inliningTarget,
                        @Cached ToComplexValueNode toComplexLeft,
                        @Cached ToComplexValueNode toComplexRight,
                        @Cached InlinedConditionProfile notImplementedProfile,
                        @Shared @Cached PythonObjectFactory factory) {
            ComplexValue left = toComplexLeft.execute(inliningTarget, leftObj);
            ComplexValue right = toComplexRight.execute(inliningTarget, rightObj);
            if (notImplementedProfile.profile(inliningTarget, left == null || right == null)) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            return factory.createComplex(left.getReal() + right.getReal(), left.getImag() + right.getImag());
        }
    }

    @Builtin(name = J___RTRUEDIV__, minNumOfPositionalArgs = 2, reverseOperation = true)
    @Builtin(name = J___TRUEDIV__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class DivNode extends PythonBinaryBuiltinNode {

        public abstract PComplex executeComplex(VirtualFrame frame, Object left, Object right);

        @NeverDefault
        public static DivNode create() {
            return ComplexBuiltinsFactory.DivNodeFactory.create();
        }

        @Specialization
        static Object doComplex(Object leftObj, Object rightObj,
                        @Bind("this") Node inliningTarget,
                        @Cached ToComplexValueNode toComplexLeft,
                        @Cached ToComplexValueNode toComplexRight,
                        @Cached InlinedConditionProfile notImplementedProfile,
                        @Cached InlinedConditionProfile topConditionProfile,
                        @Cached InlinedConditionProfile zeroDivisionProfile,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            ComplexValue left = toComplexLeft.execute(inliningTarget, leftObj);
            ComplexValue right = toComplexRight.execute(inliningTarget, rightObj);
            if (notImplementedProfile.profile(inliningTarget, left == null || right == null)) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
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
    abstract static class MulNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doComplex(Object leftObj, Object rightObj,
                        @Bind("this") Node inliningTarget,
                        @Cached ToComplexValueNode toComplexLeft,
                        @Cached ToComplexValueNode toComplexRight,
                        @Cached InlinedConditionProfile notImplementedProfile,
                        @Cached PythonObjectFactory factory) {
            ComplexValue left = toComplexLeft.execute(inliningTarget, leftObj);
            ComplexValue right = toComplexRight.execute(inliningTarget, rightObj);
            if (notImplementedProfile.profile(inliningTarget, left == null || right == null)) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            ComplexValue res = multiply(left, right);
            return factory.createComplex(res.getReal(), res.getImag());
        }

        static ComplexValue multiply(ComplexValue left, ComplexValue right) {
            double newReal = left.getReal() * right.getReal() - left.getImag() * right.getImag();
            double newImag = left.getReal() * right.getImag() + left.getImag() * right.getReal();
            return new ComplexValue(newReal, newImag);
        }
    }

    @GenerateNodeFactory
    @Builtin(name = J___RSUB__, minNumOfPositionalArgs = 2, reverseOperation = true)
    @Builtin(name = J___SUB__, minNumOfPositionalArgs = 2)
    abstract static class SubNode extends PythonBinaryBuiltinNode {
        static PComplex doComplex(PComplex left, double right,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createComplex(left.getReal() - right, left.getImag());
        }

        @Specialization
        static PComplex doComplex(PComplex left, int right,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createComplex(left.getReal() - right, left.getImag());
        }

        @Specialization
        static Object doComplex(Object leftObj, Object rightObj,
                        @Bind("this") Node inliningTarget,
                        @Cached ToComplexValueNode toComplexLeft,
                        @Cached ToComplexValueNode toComplexRight,
                        @Cached InlinedConditionProfile notImplementedProfile,
                        @Shared @Cached PythonObjectFactory factory) {
            ComplexValue left = toComplexLeft.execute(inliningTarget, leftObj);
            ComplexValue right = toComplexRight.execute(inliningTarget, rightObj);
            if (notImplementedProfile.profile(inliningTarget, left == null || right == null)) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            return factory.createComplex(left.getReal() - right.getReal(), left.getImag() - right.getImag());
        }
    }

    @Builtin(name = J___RPOW__, minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3, reverseOperation = true)
    @Builtin(name = J___POW__, minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class PowerNode extends PythonTernaryBuiltinNode {

        @Specialization
        static Object doGeneric(Object leftObj, Object rightObj, @SuppressWarnings("unused") PNone mod,
                        @Bind("this") Node inliningTarget,
                        @Cached ToComplexValueNode toComplexLeft,
                        @Cached ToComplexValueNode toComplexRight,
                        @Cached InlinedConditionProfile notImplementedProfile,
                        @Cached InlinedBranchProfile rightZeroProfile,
                        @Cached InlinedBranchProfile leftZeroProfile,
                        @Cached InlinedBranchProfile smallPositiveProfile,
                        @Cached InlinedBranchProfile smallNegativeProfile,
                        @Cached InlinedBranchProfile complexProfile,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            ComplexValue left = toComplexLeft.execute(inliningTarget, leftObj);
            ComplexValue right = toComplexRight.execute(inliningTarget, rightObj);
            if (notImplementedProfile.profile(inliningTarget, left == null || right == null)) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            PComplex result;
            if (right.getReal() == 0.0 && right.getImag() == 0.0) {
                rightZeroProfile.enter(inliningTarget);
                result = factory.createComplex(1.0, 0.0);
            } else if (left.getReal() == 0.0 && left.getImag() == 0.0) {
                leftZeroProfile.enter(inliningTarget);
                if (right.getImag() != 0.0 || right.getReal() < 0.0) {
                    throw PRaiseNode.raiseUncached(inliningTarget, ZeroDivisionError, ErrorMessages.COMPLEX_ZERO_TO_NEGATIVE_POWER);
                }
                result = factory.createComplex(0.0, 0.0);
            } else if (right.getImag() == 0.0 && right.getReal() == (int) right.getReal() && right.getReal() < 100 && right.getReal() > -100) {
                if (right.getReal() >= 0) {
                    smallPositiveProfile.enter(inliningTarget);
                    result = complexToSmallPositiveIntPower(left, (int) right.getReal(), factory);
                } else {
                    smallNegativeProfile.enter(inliningTarget);
                    result = DivNode.doubleDivComplex(1.0, complexToSmallPositiveIntPower(left, -(int) right.getReal(), factory), factory);
                }
            } else {
                complexProfile.enter(inliningTarget);
                result = complexToComplexBoundary(left.getReal(), left.getImag(), right.getReal(), right.getImag(), factory);
            }
            if (Double.isInfinite(result.getReal()) || Double.isInfinite(result.getImag())) {
                throw raiseNode.get(inliningTarget).raise(OverflowError, ErrorMessages.COMPLEX_EXPONENTIATION);
            }
            return result;
        }

        @Specialization(guards = "!isPNone(mod)")
        @SuppressWarnings("unused")
        static Object doGeneric(Object left, Object right, Object mod,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(ValueError, ErrorMessages.COMPLEX_MODULO);
        }

        private static PComplex complexToSmallPositiveIntPower(ComplexValue x, long n, PythonObjectFactory factory) {
            long mask = 1;
            ComplexValue r = new ComplexValue(1.0, 0.0);
            ComplexValue p = x;
            while (mask > 0 && n >= mask) {
                if ((n & mask) != 0) {
                    r = MulNode.multiply(r, p);
                }
                mask <<= 1;
                p = MulNode.multiply(p, p);
            }
            return factory.createComplex(r.getReal(), r.getImag());
        }

        @TruffleBoundary
        private static PComplex complexToComplexBoundary(double leftRead, double leftImag, double rightReal, double rightImag, PythonObjectFactory factory) {
            PComplex result;
            double vabs = Math.hypot(leftRead, leftImag);
            double len = Math.pow(vabs, rightReal);
            double at = Math.atan2(leftImag, leftRead);
            double phase = at * rightReal;
            if (rightImag != 0.0) {
                len /= Math.exp(at * rightImag);
                phase += rightImag * Math.log(vabs);
            }
            result = factory.createComplex(len * Math.cos(phase), len * Math.sin(phase));
            return result;
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class ComplexEqNode extends Node {
        public abstract Object execute(Node inliningTarget, Object left, Object right);

        @Specialization
        static Object doComplex(PComplex left, PComplex right) {
            return left.getReal() == right.getReal() && left.getImag() == right.getImag();
        }

        @Specialization(guards = "check.execute(inliningTarget, rightObj)", limit = "1")
        static Object doComplex(Node inliningTarget, Object leftObj, Object rightObj,
                        @SuppressWarnings("unused") @Cached PyComplexCheckNode check,
                        @Exclusive @Cached ToComplexValueNode toComplexLeft,
                        @Exclusive @Cached ToComplexValueNode toComplexRight) {
            ComplexValue left = toComplexLeft.execute(inliningTarget, leftObj);
            ComplexValue right = toComplexRight.execute(inliningTarget, rightObj);
            return left.getReal() == right.getReal() && left.getImag() == right.getImag();
        }

        @Specialization
        static boolean doComplexDouble(Node inliningTarget, Object leftObj, double right,
                        @Exclusive @Cached ToComplexValueNode toComplexLeft) {
            ComplexValue left = toComplexLeft.execute(inliningTarget, leftObj);
            return left.getImag() == 0 && left.getReal() == right;
        }

        @Specialization
        static boolean doComplexInt(Node inliningTarget, Object leftObj, long right,
                        @Exclusive @Cached ToComplexValueNode toComplexLeft,
                        @Cached InlinedConditionProfile longFitsToDoubleProfile) {
            ComplexValue left = toComplexLeft.execute(inliningTarget, leftObj);
            return left.getImag() == 0 && FloatBuiltins.ComparisonHelperNode.compareDoubleToLong(inliningTarget, left.getReal(), right, longFitsToDoubleProfile) == 0;
        }

        @Specialization
        static boolean doComplexInt(Node inliningTarget, Object leftObj, PInt right,
                        @Exclusive @Cached ToComplexValueNode toComplexLeft) {
            ComplexValue left = toComplexLeft.execute(inliningTarget, leftObj);
            return left.getImag() == 0 && FloatBuiltins.ComparisonHelperNode.compareDoubleToLargeInt(left.getReal(), right) == 0;
        }

        @SuppressWarnings("unused")
        @Fallback
        static PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @GenerateNodeFactory
    @Builtin(name = J___EQ__, minNumOfPositionalArgs = 2)
    abstract static class EqNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doComplex(Object left, Object right,
                        @Bind("this") Node inliningTarget,
                        @Cached ComplexEqNode complexEqNode) {
            return complexEqNode.execute(inliningTarget, left, right);
        }
    }

    @GenerateNodeFactory
    @Builtin(name = J___NE__, minNumOfPositionalArgs = 2)
    abstract static class NeNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doComplex(Object left, Object right,
                        @Bind("this") Node inliningTarget,
                        @Cached ComplexEqNode complexEqNode) {
            Object res = complexEqNode.execute(inliningTarget, left, right);
            if (res == PNotImplemented.NOT_IMPLEMENTED) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            return !(boolean) res;
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
    @Builtin(name = J___REPR__, minNumOfPositionalArgs = 1)
    abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        static TruffleString repr(Object self,
                        @Bind("this") Node inliningTarget,
                        @Cached ToComplexValueNode toComplexValueNode) {
            ComplexValue c = toComplexValueNode.execute(inliningTarget, self);
            return doFormat(inliningTarget, c.getReal(), c.getImag());
        }

        @TruffleBoundary
        private static TruffleString doFormat(Node inliningTarget, double real, double imag) {
            ComplexFormatter formatter = new ComplexFormatter(new Spec(-1, Spec.NONE), inliningTarget);
            formatter.format(real, imag);
            return formatter.pad().getResult();
        }
    }

    @Builtin(name = J___FORMAT__, minNumOfPositionalArgs = 2, parameterNames = {"$self", "format_spec"})
    @ArgumentClinic(name = "format_spec", conversion = ClinicConversion.TString)
    @GenerateNodeFactory
    abstract static class FormatNode extends FormatNodeBase {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return FormatNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static TruffleString format(Object self, TruffleString formatString,
                        @Bind("this") Node inliningTarget,
                        @Cached ToComplexValueNode toComplexValueNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            ComplexValue c = toComplexValueNode.execute(inliningTarget, self);
            InternalFormat.Spec spec = InternalFormat.fromText(formatString, Spec.NONE, '>', inliningTarget);
            validateSpec(inliningTarget, spec, raiseNode);
            return doFormat(inliningTarget, c.getReal(), c.getImag(), spec);
        }

        @TruffleBoundary
        private static TruffleString doFormat(Node raisingNode, double real, double imag, Spec spec) {
            ComplexFormatter formatter = new ComplexFormatter(validateForFloat(spec, "complex", raisingNode), raisingNode);
            formatter.format(real, imag);
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
        static boolean bool(Object self,
                        @Bind("this") Node inliningTarget,
                        @Cached ToComplexValueNode toComplexValueNode) {
            ComplexValue c = toComplexValueNode.execute(inliningTarget, self);
            return c.getReal() != 0.0 || c.getImag() != 0.0;
        }
    }

    @GenerateNodeFactory
    @Builtin(name = J___NEG__, minNumOfPositionalArgs = 1)
    abstract static class NegNode extends PythonUnaryBuiltinNode {
        @Specialization
        static PComplex neg(Object self,
                        @Bind("this") Node inliningTarget,
                        @Cached ToComplexValueNode toComplexValueNode,
                        @Cached PythonObjectFactory factory) {
            ComplexValue c = toComplexValueNode.execute(inliningTarget, self);
            return factory.createComplex(-c.getReal(), -c.getImag());
        }
    }

    @GenerateNodeFactory
    @Builtin(name = J___POS__, minNumOfPositionalArgs = 1)
    abstract static class PosNode extends PythonUnaryBuiltinNode {
        @Specialization
        static PComplex pos(Object self,
                        @Bind("this") Node inliningTarget,
                        @Cached ToComplexValueNode toComplexValueNode,
                        @Cached PythonObjectFactory factory) {
            ComplexValue c = toComplexValueNode.execute(inliningTarget, self);
            return factory.createComplex(c.getReal(), c.getImag());
        }
    }

    @GenerateNodeFactory
    @Builtin(name = J___GETNEWARGS__, minNumOfPositionalArgs = 1)
    abstract static class GetNewArgsNode extends PythonUnaryBuiltinNode {
        @Specialization
        static PTuple get(Object self,
                        @Bind("this") Node inliningTarget,
                        @Cached ToComplexValueNode toComplexValueNode,
                        @Cached PythonObjectFactory factory) {
            ComplexValue c = toComplexValueNode.execute(inliningTarget, self);
            return factory.createTuple(new Object[]{c.getReal(), c.getImag()});
        }
    }

    @GenerateNodeFactory
    @Builtin(name = "real", minNumOfPositionalArgs = 1, isGetter = true, doc = "the real part of a complex number")
    abstract static class RealNode extends PythonBuiltinNode {
        @Specialization
        static double get(PComplex self) {
            return self.getReal();
        }

        @Specialization
        static double getNative(PythonAbstractNativeObject self,
                        @Cached CStructAccess.ReadDoubleNode read) {
            return read.readFromObj(self, PyComplexObject__cval__real);
        }
    }

    @GenerateNodeFactory
    @Builtin(name = "imag", minNumOfPositionalArgs = 1, isGetter = true, doc = "the imaginary part of a complex number")
    abstract static class ImagNode extends PythonBuiltinNode {
        @Specialization
        static double get(PComplex self) {
            return self.getImag();
        }

        @Specialization
        static double getNative(PythonAbstractNativeObject self,
                        @Cached CStructAccess.ReadDoubleNode read) {
            return read.readFromObj(self, PyComplexObject__cval__imag);
        }
    }

    @GenerateNodeFactory
    @Builtin(name = J___HASH__, minNumOfPositionalArgs = 1)
    abstract static class HashNode extends PythonUnaryBuiltinNode {
        @Specialization
        static long doPComplex(Object self,
                        @Bind("this") Node inliningTarget,
                        @Cached ToComplexValueNode toComplexValueNode) {
            ComplexValue c = toComplexValueNode.execute(inliningTarget, self);
            // just like CPython
            long realHash = PyObjectHashNode.hash(c.getReal());
            long imagHash = PyObjectHashNode.hash(c.getImag());
            return realHash + SysModuleBuiltins.HASH_IMAG * imagHash;
        }

    }

    @GenerateNodeFactory
    @Builtin(name = "conjugate", minNumOfPositionalArgs = 1)
    abstract static class ConjugateNode extends PythonUnaryBuiltinNode {
        @Specialization
        static PComplex hash(Object self,
                        @Bind("this") Node inliningTarget,
                        @Cached ToComplexValueNode toComplexValueNode,
                        @Cached PythonObjectFactory factory) {
            ComplexValue c = toComplexValueNode.execute(inliningTarget, self);
            return factory.createComplex(c.getReal(), -c.getImag());
        }
    }
}
