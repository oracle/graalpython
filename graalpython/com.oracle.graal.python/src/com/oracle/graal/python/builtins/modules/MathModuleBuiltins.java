/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
 * Copyright (c) 2014, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.builtins.modules;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ZeroDivisionError;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.profiles.ConditionProfile;
import java.math.MathContext;

@CoreFunctions(defineModule = "math")
public class MathModuleBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinNode>> getNodeFactories() {
        return MathModuleBuiltinsFactory.getFactories();
    }

    public MathModuleBuiltins() {
        // Add constant values
        builtinConstants.put("pi", Math.PI);
        builtinConstants.put("e", Math.E);
        builtinConstants.put("tau", 2 * Math.PI);
    }

    // math.sqrt
    @Builtin(name = "sqrt", fixedNumOfArguments = 1, doc = "Return the square root of x.")
    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(MathGuards.class)
    @GenerateNodeFactory
    public abstract static class SqrtNode extends PythonUnaryBuiltinNode {

        public abstract double executeObject(Object value);

        protected static BigDecimal sqrtBigNumber(BigInteger value) {
            BigDecimal number = new BigDecimal(value);
            BigDecimal result = BigDecimal.ZERO;
            BigDecimal guess = BigDecimal.ONE;
            BigDecimal BigDecimalTWO = new BigDecimal(2);
            BigDecimal flipA = result;
            BigDecimal flipB = result;
            boolean first = true;
            while (result.compareTo(guess) != 0) {
                if (!first) {
                    guess = result;
                } else {
                    first = false;
                }
                // Do we need such precision?
                result = number.divide(guess, MathContext.DECIMAL128).add(guess).divide(BigDecimalTWO, MathContext.DECIMAL128);
                // handle flip flops
                if (result.equals(flipB)) {
                    return flipA;
                }

                flipB = flipA;
                flipA = result;
            }
            return result;
        }

        @Specialization
        public double sqrtLong(long value,
                        @Cached("createBinaryProfile()") ConditionProfile doNotFit) {
            return sqrtDouble(value, doNotFit);
        }

        @Specialization
        @TruffleBoundary
        public double sqrtPInt(PInt value,
                        @Cached("createBinaryProfile()") ConditionProfile doNotFit) {
            BigInteger bValue = value.getValue();
            if (doNotFit.profile(bValue.compareTo(BigInteger.ZERO) == -1)) {
                throw raise(ValueError, "math domain error");
            }
            return sqrtBigNumber(bValue).doubleValue();
        }

        @Specialization
        public double sqrtDouble(double value,
                        @Cached("createBinaryProfile()") ConditionProfile doNotFit) {
            if (doNotFit.profile(value < 0)) {
                throw raise(ValueError, "math domain error");
            }
            return Math.sqrt(value);
        }

        @Specialization(guards = "!isNumber(value)")
        public double acosh(Object value,
                        @Cached("create(__FLOAT__)") LookupAndCallUnaryNode dispatchFloat,
                        @Cached("create()") SqrtNode sqrtNode) {
            Object result = dispatchFloat.executeObject(value);
            if (result == PNone.NO_VALUE) {
                throw raise(TypeError, "must be real number, not %p", value);
            }
            return sqrtNode.executeObject(result);
        }

        public static SqrtNode create() {
            return MathModuleBuiltinsFactory.SqrtNodeFactory.create(new PNode[0]);
        }
    }

    @Builtin(name = "exp", fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class ExpNode extends PythonBuiltinNode {

        @Specialization
        public double exp(int value) {
            return Math.exp(value);
        }

        @Specialization
        public double exp(double value) {
            return Math.exp(value);
        }
    }

    @Builtin(name = "ceil", fixedNumOfArguments = 1)
    @ImportStatic(MathGuards.class)
    @GenerateNodeFactory
    public abstract static class CeilNode extends PythonBuiltinNode {

        @Specialization(guards = {"fitLong(value)"})
        public long ceilLong(double value) {
            return (long) Math.ceil(value);
        }

        @Specialization(guards = {"!fitLong(value)"})
        @TruffleBoundary
        public PInt ceil(double value) {
            return factory().createInt(BigDecimal.valueOf(Math.ceil(value)).toBigInteger());
        }

        @Specialization
        public int ceil(int value) {
            return value;
        }

        @Specialization
        public long ceil(long value) {
            return value;
        }

        @Specialization
        public int ceil(boolean value) {
            if (value) {
                return 1;
            }
            return 0;
        }

        @Specialization
        @TruffleBoundary
        public Object ceil(PFloat value,
                        @Cached("create(__CEIL__)") LookupAndCallUnaryNode dispatchCeil) {
            Object result = dispatchCeil.executeObject(value);
            if (PNone.NO_VALUE.equals(result)) {
                if (value.getValue() <= Long.MAX_VALUE) {
                    result = Math.ceil(value.getValue());
                } else {
                    result = factory().createInt(BigDecimal.valueOf(Math.ceil(value.getValue())).toBigInteger());
                }
            }
            return result;
        }

        @Specialization
        public Object ceil(PInt value,
                        @Cached("create(__CEIL__)") LookupAndCallUnaryNode dispatchCeil) {
            return dispatchCeil.executeObject(value);
        }

        @Specialization(guards = {"!isNumber(value)"})
        public Object ceil(Object value,
                        @Cached("create(__CEIL__)") LookupAndCallUnaryNode dispatchCeil) {
            Object result = dispatchCeil.executeObject(value);
            if (PNone.NO_VALUE.equals(result)) {
                throw raise(TypeError, "must be real number, not %p", value);
            }
            return result;
        }

    }

    @Builtin(name = "copysign", fixedNumOfArguments = 2)
    @ImportStatic(MathGuards.class)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class CopySignNode extends PythonBuiltinNode {

        @Specialization
        public double copySignLL(long magnitude, long sign) {
            return Math.copySign((double) magnitude, sign);
        }

        @Specialization
        public double copySignDL(double magnitude, long sign) {
            return Math.copySign(magnitude, sign);
        }

        @Specialization
        public double copySignLD(long magnitude, double sign) {
            return Math.copySign(magnitude, sign);
        }

        @Specialization
        public double copySignDD(double magnitude, double sign) {
            return Math.copySign(magnitude, sign);
        }

        @Specialization
        public double copySignPIL(PInt magnitude, long sign) {
            return Math.copySign(magnitude.getValue().doubleValue(), sign);
        }

        @Specialization
        public double copySignPID(PInt magnitude, double sign) {
            return Math.copySign(magnitude.getValue().doubleValue(), sign);
        }

        @Specialization
        public double copySignLPI(long magnitude, PInt sign) {
            return Math.copySign(magnitude, sign.getValue().doubleValue());
        }

        @Specialization
        public double copySignDPI(double magnitude, PInt sign) {
            return Math.copySign(magnitude, sign.getValue().doubleValue());
        }

        @Specialization
        public double copySignPIPI(PInt magnitude, PInt sign) {
            return Math.copySign(magnitude.getValue().doubleValue(), sign.getValue().doubleValue());
        }

        @Fallback
        public double copySignOO(Object magnitude, Object sign) {
            if (!MathGuards.isNumber(magnitude)) {
                throw raise(TypeError, "must be real number, not %p", magnitude);
            }
            throw raise(TypeError, "must be real number, not %p", sign);
        }
    }

    @Builtin(name = "factorial", fixedNumOfArguments = 1)
    @ImportStatic(Double.class)
    @SuppressWarnings("unused")
    @GenerateNodeFactory
    public abstract static class FactorialNode extends PythonBuiltinNode {

        @CompilationFinal(dimensions = 1) protected final static long[] SMALL_FACTORIALS = new long[]{
                        1, 1, 2, 6, 24, 120, 720, 5040, 40320,
                        362880, 3628800, 39916800, 479001600,
                        6227020800L, 87178291200L, 1307674368000L,
                        20922789888000L, 355687428096000L, 6402373705728000L,
                        121645100408832000L, 2432902008176640000L};

        private BigInteger factorialPart(long start, long n) {
            long i;
            if (n <= 16) {
                BigInteger r = new BigInteger(String.valueOf(start));
                for (i = start + 1; i < start + n; i++) {
                    r = r.multiply(BigInteger.valueOf(i));
                }
                return r;
            }
            i = n / 2;
            return factorialPart(start, i).multiply(factorialPart(start + i, n - i));
        }

        @Specialization
        public int factorialBoolean(boolean value) {
            return 1;
        }

        @Specialization(guards = {"value < 0"})
        public long factorialNegativeInt(int value) {
            throw raise(PythonErrorType.ValueError, "factorial() not defined for negative values");
        }

        @Specialization(guards = {"0 <= value", "value < SMALL_FACTORIALS.length"})
        public long factorialSmallInt(int value) {
            return SMALL_FACTORIALS[value];
        }

        @Specialization(guards = {"value >= SMALL_FACTORIALS.length"})
        @TruffleBoundary
        public PInt factorialInt(int value) {
            return factory().createInt(factorialPart(1, value));
        }

        @Specialization(guards = {"value < 0"})
        public long factorialNegativeLong(long value) {
            throw raise(PythonErrorType.ValueError, "factorial() not defined for negative values");
        }

        @Specialization(guards = {"0 <= value", "value < SMALL_FACTORIALS.length"})
        public long factorialSmallLong(long value) {
            return SMALL_FACTORIALS[(int) value];
        }

        @Specialization(guards = {"value >= SMALL_FACTORIALS.length"})
        @TruffleBoundary
        public PInt factorialLong(long value) {
            return factory().createInt(factorialPart(1, value));
        }

        @Specialization(guards = "isNegative(value)")
        public Object factorialPINegative(PInt value) {
            throw raise(PythonErrorType.ValueError, "factorial() not defined for negative values");
        }

        @Specialization(guards = "isOvf(value)")
        public Object factorialPIOvf(PInt value) {
            throw raise(PythonErrorType.OverflowError, "factorial() argument should not exceed %l", Long.MAX_VALUE);
        }

        @Specialization(guards = {"!isOvf(value)", "!isNegative(value)"})
        @TruffleBoundary
        public Object factorial(PInt value) {
            BigInteger biValue = value.getValue();
            if (biValue.compareTo(BigInteger.valueOf(SMALL_FACTORIALS.length)) < 0) {
                return SMALL_FACTORIALS[value.intValue()];
            }
            return factory().createInt(factorialPart(1, value.longValue()));
        }

        @Specialization(guards = {"isNaN(value)"})
        public long factorialDoubleNaN(double value) {
            throw raise(PythonErrorType.ValueError, "cannot convert float NaN to integer");
        }

        @Specialization(guards = {"isInfinite(value)"})
        public long factorialDoubleInfinite(double value) {
            throw raise(PythonErrorType.ValueError, "cannot convert float infinity to integer");
        }

        @Specialization(guards = "isNegative(value)")
        public PInt factorialDoubleNegative(double value) {
            throw raise(PythonErrorType.ValueError, "factorial() not defined for negative values");
        }

        @Specialization(guards = "!isInteger(value)")
        public PInt factorialDoubleNotInteger(double value) {
            throw raise(PythonErrorType.ValueError, "factorial() only accepts integral values");
        }

        @Specialization(guards = "isOvf(value)")
        public PInt factorialDoubleOvf(double value) {
            throw raise(PythonErrorType.OverflowError, "factorial() argument should not exceed %l", Long.MAX_VALUE);
        }

        @Specialization(guards = {"!isNaN(value)", "!isInfinite(value)",
                        "!isNegative(value)", "isInteger(value)", "!isOvf(value)"})
        @TruffleBoundary
        public Object factorialDouble(double value) {
            if (value < SMALL_FACTORIALS.length) {
                return SMALL_FACTORIALS[(int) value];
            }
            return factory().createInt(factorialPart(1, (long) value));
        }

        @Specialization(guards = {"isNaN(value.getValue())"})
        public long factorialPFLNaN(PFloat value) {
            throw raise(PythonErrorType.ValueError, "cannot convert float NaN to integer");
        }

        @Specialization(guards = {"isInfinite(value.getValue())"})
        public long factorialPFLInfinite(PFloat value) {
            throw raise(PythonErrorType.ValueError, "cannot convert float infinity to integer");
        }

        @Specialization(guards = "isNegative(value.getValue())")
        public PInt factorialPFLNegative(PFloat value) {
            throw raise(PythonErrorType.ValueError, "factorial() not defined for negative values");
        }

        @Specialization(guards = "!isInteger(value.getValue())")
        public PInt factorialPFLNotInteger(PFloat value) {
            throw raise(PythonErrorType.ValueError, "factorial() only accepts integral values");
        }

        @Specialization(guards = "isOvf(value.getValue())")
        public PInt factorialPFLOvf(PFloat value) {
            throw raise(PythonErrorType.OverflowError, "factorial() argument should not exceed %l", Long.MAX_VALUE);
        }

        @Specialization(guards = {"!isNaN(value.getValue())", "!isInfinite(value.getValue())",
                        "!isNegative(value.getValue())", "isInteger(value.getValue())", "!isOvf(value.getValue())"})
        @TruffleBoundary
        public Object factorialPFL(PFloat value) {
            double pfValue = value.getValue();
            if (pfValue < SMALL_FACTORIALS.length) {
                return SMALL_FACTORIALS[(int) pfValue];
            }
            return factory().createInt(factorialPart(1, (long) pfValue));
        }

        @Fallback
        public Object factorialObject(Object value) {
            throw raise(TypeError, "an integer is required (got type %p)", value);
        }

        protected boolean isInteger(double value) {
            return Double.isFinite(value) && value == Math.floor(value);
        }

        protected boolean isNegative(PInt value) {
            return value.getValue().compareTo(BigInteger.ZERO) < 0;
        }

        protected boolean isNegative(double value) {
            return value < 0;
        }

        protected boolean isOvf(double value) {
            return value > Long.MAX_VALUE;
        }

        protected boolean isOvf(PInt value) {
            return value.getValue().compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0;
        }
    }

    @Builtin(name = "floor", fixedNumOfArguments = 1)
    @GenerateNodeFactory
    @ImportStatic(MathGuards.class)
    public abstract static class FloorNode extends PythonBuiltinNode {

        @Specialization(guards = {"fitLong(value)"})
        public long floorDL(double value) {
            return (long) Math.floor(value);
        }

        @Specialization(guards = {"!fitLong(value)"})
        @TruffleBoundary
        public PInt floorD(double value) {
            return factory().createInt(BigDecimal.valueOf(Math.floor(value)).toBigInteger());
        }

        @Specialization
        public int floorI(int value) {
            return value;
        }

        @Specialization
        public long floorL(long value) {
            return value;
        }

        @Specialization
        public int floorB(boolean value) {
            if (value) {
                return 1;
            }
            return 0;
        }

        @Specialization
        @TruffleBoundary
        public Object floorPF(PFloat value,
                        @Cached("create(__FLOOR__)") LookupAndCallUnaryNode dispatchFloor) {
            Object result = dispatchFloor.executeObject(value);
            if (result == PNone.NO_VALUE) {
                if (value.getValue() <= Long.MAX_VALUE) {
                    result = Math.floor(value.getValue());
                } else {
                    result = factory().createInt(BigDecimal.valueOf(Math.floor(value.getValue())).toBigInteger());
                }
            }
            return result;
        }

        @Specialization
        public Object floorPI(PInt value,
                        @Cached("create(__FLOOR__)") LookupAndCallUnaryNode dispatchFloor) {
            return dispatchFloor.executeObject(value);
        }

        @Specialization(guards = {"!isNumber(value)"})
        public Object floor(Object value,
                        @Cached("create(__FLOOR__)") LookupAndCallUnaryNode dispatchFloor) {
            Object result = dispatchFloor.executeObject(value);
            if (PNone.NO_VALUE.equals(result)) {
                throw raise(TypeError, "must be real number, not %p", value);
            }
            return result;
        }

    }

    @Builtin(name = "fmod", fixedNumOfArguments = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(MathGuards.class)
    @GenerateNodeFactory
    public abstract static class FmodNode extends PythonBuiltinNode {

        @Specialization
        public double fmodDD(double left, double right,
                        @Cached("createBinaryProfile()") ConditionProfile infProfile,
                        @Cached("createBinaryProfile()") ConditionProfile zeroProfile) {
            raiseMathDomainError(infProfile.profile(Double.isInfinite(left)));
            raiseMathDomainError(zeroProfile.profile(right == 0));
            return left % right;
        }

        @Specialization
        public double fmodDL(double left, long right,
                        @Cached("createBinaryProfile()") ConditionProfile infProfile,
                        @Cached("createBinaryProfile()") ConditionProfile zeroProfile) {
            raiseMathDomainError(infProfile.profile(Double.isInfinite(left)));
            raiseMathDomainError(zeroProfile.profile(right == 0));
            return left % right;
        }

        @Specialization
        @TruffleBoundary
        public double fmodDPI(double left, PInt right) {
            raiseMathDomainError(Double.isInfinite(left));
            double rvalue = right.getValue().doubleValue();
            raiseMathDomainError(rvalue == 0);
            return left % rvalue;
        }

        @Specialization
        public double fmodLL(long left, long right,
                        @Cached("createBinaryProfile()") ConditionProfile zeroProfile) {
            raiseMathDomainError(zeroProfile.profile(right == 0));
            return left % right;
        }

        @Specialization
        public double fmodLD(long left, double right,
                        @Cached("createBinaryProfile()") ConditionProfile zeroProfile) {
            raiseMathDomainError(zeroProfile.profile(right == 0));
            return left % right;
        }

        @Specialization
        @TruffleBoundary
        public double fmodLPI(long left, PInt right) {
            double rvalue = right.getValue().doubleValue();
            raiseMathDomainError(rvalue == 0);
            return left % rvalue;
        }

        @Specialization
        public double fmodPIPI(PInt left, PInt right) {
            double rvalue = right.getValue().doubleValue();
            raiseMathDomainError(rvalue == 0);
            double lvalue = left.getValue().doubleValue();
            return lvalue % rvalue;
        }

        @Specialization
        @TruffleBoundary
        public double fmodPIL(PInt left, long right) {
            raiseMathDomainError(right == 0);
            double lvalue = left.getValue().doubleValue();
            return lvalue % right;
        }

        @Specialization
        @TruffleBoundary
        public double fmodPID(PInt left, double right) {
            raiseMathDomainError(right == 0);
            double lvalue = left.getValue().doubleValue();
            return lvalue % right;
        }

        @Specialization(guards = {"!isNumber(left) || !isNumber(right)"})
        public double fmodLO(Object left, Object right) {
            // the right the first one to be complient with python
            if (!MathGuards.isNumber(right)) {
                throw raise(PythonErrorType.TypeError, "must be real number, not %p", right);
            }
            throw raise(PythonErrorType.TypeError, "must be real number, not %p", left);
        }

        protected void raiseMathDomainError(boolean con) {
            if (con) {
                throw raise(PythonErrorType.ValueError, "math domain error");
            }
        }

    }

    @Builtin(name = "frexp", fixedNumOfArguments = 1)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(MathGuards.class)
    @GenerateNodeFactory
    public abstract static class FrexpNode extends PythonBuiltinNode {

        public static PTuple frexp(double value, PythonObjectFactory factory) {
            int exponent = 0;
            double mantissa = 0.0;

            if (value == 0.0 || value == -0.0) {
                return factory.createTuple(new Object[]{mantissa, exponent});
            }

            if (Double.isNaN(value)) {
                mantissa = Double.NaN;
                exponent = -1;
                return factory.createTuple(new Object[]{mantissa, exponent});
            }

            if (Double.isInfinite(value)) {
                mantissa = value;
                exponent = -1;
                return factory.createTuple(new Object[]{mantissa, exponent});
            }

            boolean neg = false;
            mantissa = value;

            if (mantissa < 0) {
                mantissa = -mantissa;
                neg = true;
            }
            if (mantissa >= 1.0) {
                while (mantissa >= 1) {
                    ++exponent;
                    mantissa /= 2;
                }
            } else if (mantissa < 0.5) {
                while (mantissa < 0.5) {
                    --exponent;
                    mantissa *= 2;
                }
            }
            return factory.createTuple(new Object[]{neg ? -mantissa : mantissa, exponent});
        }

        @Specialization
        public PTuple frexpD(double value) {
            return frexp(value, factory());
        }

        @Specialization
        public PTuple frexpL(long value) {
            return frexp(value, factory());
        }

        @Specialization
        public PTuple frexpPI(PInt value) {
            return frexp(value.getValue().doubleValue(), factory());
        }

        @Fallback
        public PTuple frexpO(Object value) {
            throw raise(PythonErrorType.TypeError, "must be real number, not %p", value);
        }
    }

    @Builtin(name = "isnan", fixedNumOfArguments = 1)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(MathGuards.class)
    @GenerateNodeFactory
    public abstract static class IsNanNode extends PythonUnaryBuiltinNode {

        public abstract boolean executeObject(Object value);

        @Specialization
        public boolean isNan(@SuppressWarnings("unused") long value) {
            return false;
        }

        @Specialization
        public boolean isNan(@SuppressWarnings("unused") PInt value) {
            return false;
        }

        @Specialization
        public boolean isNan(double value) {
            return Double.isNaN(value);
        }

        @Specialization(guards = "!isNumber(value)")
        public boolean isinf(Object value,
                        @Cached("create(__FLOAT__)") LookupAndCallUnaryNode dispatchFloat,
                        @Cached("create()") IsNanNode isNanNode) {
            Object result = dispatchFloat.executeObject(value);
            if (result == PNone.NO_VALUE) {
                throw raise(TypeError, "must be real number, not %p", value);
            }
            return isNanNode.executeObject(result);
        }

        protected static IsNanNode create() {
            return MathModuleBuiltinsFactory.IsNanNodeFactory.create(new PNode[0]);
        }
    }

    @Builtin(name = "isclose", minNumOfArguments = 2, keywordArguments = {"rel_tol", "abs_tol"})
    @GenerateNodeFactory
    public abstract static class IsCloseNode extends PythonBuiltinNode {
        private static double DEFAULT_REL = 1e-09;
        private static double DEFAULT_ABS = 0.0;

        private boolean isCloseDouble(double a, double b, double rel_tol, double abs_tol) {
            double diff;

            if (rel_tol < 0.0 || abs_tol < 0.0) {
                throw raise(ValueError, "tolerances must be non-negative");
            }

            if (a == b) {
                return true;
            }

            if (Double.isInfinite(a) || Double.isInfinite(b)) {
                return false;
            }

            diff = Math.abs(b - a);
            return (((diff <= Math.abs(rel_tol * b)) ||
                            (diff <= Math.abs(rel_tol * a))) ||
                            (diff <= abs_tol));
        }

        @Specialization
        public boolean isClose(double a, double b, @SuppressWarnings("unused") PNone rel_tol, @SuppressWarnings("unused") PNone abs_tol) {
            return isCloseDouble(a, b, DEFAULT_REL, DEFAULT_ABS);
        }

        @Specialization
        public boolean isClose(double a, double b, @SuppressWarnings("unused") PNone rel_tol, double abs_tol) {
            return isCloseDouble(a, b, DEFAULT_REL, abs_tol);
        }

        @Specialization
        public boolean isClose(double a, double b, double rel_tol, @SuppressWarnings("unused") PNone abs_tol) {
            return isCloseDouble(a, b, rel_tol, DEFAULT_ABS);
        }

        @Specialization
        public boolean isClose(double a, double b, double rel_tol, double abs_tol) {
            return isCloseDouble(a, b, rel_tol, abs_tol);
        }
    }

    @Builtin(name = "ldexp", fixedNumOfArguments = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    @SuppressWarnings("unused")
    public abstract static class LdexpNode extends PythonBuiltinNode {

        private static final String EXPECTED_INT_MESSAGE = "Expected an int as second argument to ldexp.";

        private static int makeInt(long x) {
            long result = x;
            if (x < Integer.MIN_VALUE) {
                result = Integer.MIN_VALUE;
            } else if (x > Integer.MAX_VALUE) {
                result = Integer.MAX_VALUE;
            }
            return (int) result;
        }

        private static int makeInt(PInt x) {
            int result;
            BigInteger value = x.getValue();
            if (value.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) < 0) {
                result = Integer.MIN_VALUE;
            } else if (value.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0) {
                result = Integer.MAX_VALUE;
            } else {
                result = value.intValue();
            }
            return result;
        }

        private double exceptInfinity(double result, double arg) {
            if (Double.isInfinite(result) && !Double.isInfinite(arg)) {
                throw raise(OverflowError, "math range error");
            } else {
                return result;
            }
        }

        @Specialization
        public double ldexpDD(double mantissa, double exp) {
            throw raise(TypeError, EXPECTED_INT_MESSAGE);
        }

        @Specialization
        public double ldexpDD(double mantissa, long exp) {
            return exceptInfinity(Math.scalb(mantissa, makeInt(exp)), mantissa);
        }

        @Specialization
        public double ldexpLD(long mantissa, double exp) {
            throw raise(TypeError, EXPECTED_INT_MESSAGE);
        }

        @Specialization
        public double ldexpLL(long mantissa, long exp) {
            return exceptInfinity(Math.scalb(mantissa, makeInt(exp)), mantissa);
        }

        @Specialization
        @TruffleBoundary
        public double ldexpDPI(double mantissa, PInt exp) {
            return exceptInfinity(Math.scalb(mantissa, makeInt(exp)), mantissa);
        }

        @Specialization
        @TruffleBoundary
        public double ldexpLPI(long mantissa, PInt exp) {
            return exceptInfinity(Math.scalb(mantissa, makeInt(exp)), mantissa);
        }

        @Specialization
        @TruffleBoundary
        public double ldexpPIPI(PInt mantissa, PInt exp) {
            double dm = mantissa.getValue().doubleValue();
            return exceptInfinity(Math.scalb(dm, makeInt(exp)), dm);
        }

        @Specialization
        public double ldexpPID(PInt mantissa, double exp) {
            throw raise(TypeError, EXPECTED_INT_MESSAGE);
        }

        @Specialization
        public double ldexpPIL(PInt mantissa, long exp) {
            double dm = mantissa.getValue().doubleValue();
            return exceptInfinity(Math.scalb(dm, makeInt(exp)), dm);
        }

        @Fallback
        public double ldexpOO(Object mantissa, Object exp) {
            if (!MathGuards.isNumber(mantissa)) {
                throw raise(TypeError, "must be real number, not %p", mantissa);
            }
            throw raise(TypeError, EXPECTED_INT_MESSAGE);
        }

    }

    @Builtin(name = "acos", fixedNumOfArguments = 1, doc = "Return the arc cosine (measured in radians) of x.")
    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(MathGuards.class)
    @GenerateNodeFactory
    public abstract static class AcosNode extends PythonUnaryBuiltinNode {

        public abstract double executeObject(Object value);

        @Specialization
        public double acos(long value,
                        @Cached("createBinaryProfile()") ConditionProfile doNotFit) {
            if (doNotFit.profile(value > 1 || value < -1)) {
                throw raise(ValueError, "math domain error");
            }
            return Math.acos(value);
        }

        @Specialization
        public double acos(PInt value,
                        @Cached("createBinaryProfile()") ConditionProfile doNotFit) {
            return acos(value.intValue(), doNotFit);
        }

        @Specialization
        public double acos(double value,
                        @Cached("createBinaryProfile()") ConditionProfile doNotFit) {
            if (doNotFit.profile(Double.isInfinite(value) || -1 > value || value > 1)) {
                throw raise(ValueError, "math domain error");
            }
            return Math.acos(value);
        }

        @Specialization(guards = "!isNumber(value)")
        public double acos(Object value,
                        @Cached("create(__FLOAT__)") LookupAndCallUnaryNode dispatchFloat,
                        @Cached("create()") AcosNode acosNode) {
            Object result = dispatchFloat.executeObject(value);
            if (result == PNone.NO_VALUE) {
                throw raise(TypeError, "must be real number, not %p", value);
            }
            return acosNode.executeObject(result);
        }

        protected static AcosNode create() {
            return MathModuleBuiltinsFactory.AcosNodeFactory.create(new PNode[0]);
        }
    }

    @Builtin(name = "acosh", fixedNumOfArguments = 1, doc = "Return the inverse hyperbolic cosine of x.")
    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(MathGuards.class)
    @GenerateNodeFactory
    public abstract static class AcoshNode extends PythonUnaryBuiltinNode {

        public abstract double executeObject(Object value);

        @Specialization
        public double acoshInt(long value,
                        @Cached("createBinaryProfile()") ConditionProfile doNotFit) {
            return acoshDouble(value, doNotFit);
        }

        @Specialization
        public double acoshDouble(double value,
                        @Cached("createBinaryProfile()") ConditionProfile doNotFit) {
            if (doNotFit.profile(value < 1)) {
                throw raise(ValueError, "math domain error");
            }
            return Math.log(value + Math.sqrt(value * value - 1.0));
        }

        @Specialization
        @TruffleBoundary
        public double acoshDouble(PInt value,
                        @Cached("createBinaryProfile()") ConditionProfile doNotFit) {
            BigInteger bValue = value.getValue();
            if (doNotFit.profile(bValue.compareTo(BigInteger.ONE) == -1)) {
                throw raise(ValueError, "math domain error");
            }

            BigDecimal sqrt = SqrtNode.sqrtBigNumber(bValue.multiply(bValue).subtract(BigInteger.ONE));
            BigDecimal bd = new BigDecimal(bValue);
            return Math.log(bd.add(sqrt).doubleValue());
        }

        @Specialization(guards = "!isNumber(value)")
        public double acosh(Object value,
                        @Cached("create(__FLOAT__)") LookupAndCallUnaryNode dispatchFloat,
                        @Cached("create()") AcoshNode acoshNode) {
            Object result = dispatchFloat.executeObject(value);
            if (result == PNone.NO_VALUE) {
                throw raise(TypeError, "must be real number, not %p", value);
            }
            return acoshNode.executeObject(result);
        }

        protected static AcoshNode create() {
            return MathModuleBuiltinsFactory.AcoshNodeFactory.create(new PNode[0]);
        }
    }

    @Builtin(name = "asin", fixedNumOfArguments = 1, doc = "Return the arc sine (measured in radians) of x.")
    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(MathGuards.class)
    @GenerateNodeFactory
    public abstract static class AsinNode extends PythonUnaryBuiltinNode {

        public abstract double executeObject(Object value);

        @Specialization
        public double asinInt(long value,
                        @Cached("createBinaryProfile()") ConditionProfile doNotFit) {
            return asinDouble(value, doNotFit);
        }

        @Specialization
        @TruffleBoundary
        public double asinPInt(PInt value,
                        @Cached("createBinaryProfile()") ConditionProfile doNotFit) {
            return asinDouble(value.intValue(), doNotFit);
        }

        @Specialization
        public double asinDouble(double value,
                        @Cached("createBinaryProfile()") ConditionProfile doNotFit) {
            if (doNotFit.profile(value < -1 || value > 1)) {
                throw raise(ValueError, "math domain error");
            }
            return Math.asin(value);
        }

        @Specialization(guards = "!isNumber(value)")
        public double acosh(Object value,
                        @Cached("create(__FLOAT__)") LookupAndCallUnaryNode dispatchFloat,
                        @Cached("create()") AsinNode asinNode) {
            Object result = dispatchFloat.executeObject(value);
            if (result == PNone.NO_VALUE) {
                throw raise(TypeError, "must be real number, not %p", value);
            }
            return asinNode.executeObject(result);
        }

        protected static AsinNode create() {
            return MathModuleBuiltinsFactory.AsinNodeFactory.create(new PNode[0]);
        }
    }

    @Builtin(name = "cos", fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class CosNode extends PythonBuiltinNode {

        @Specialization
        public double cos(int value) {
            return Math.cos(value);
        }

        @Specialization
        public double cos(double value) {
            return Math.cos(value);
        }
    }

    @Builtin(name = "sin", fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class SinNode extends PythonBuiltinNode {

        @Specialization
        public double sin(int value) {
            return Math.sin(value);
        }

        @Specialization
        public double sin(double value) {
            return Math.sin(value);
        }
    }

    @Builtin(name = "isfinite", fixedNumOfArguments = 1)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(MathGuards.class)
    @GenerateNodeFactory
    public abstract static class IsFiniteNode extends PythonUnaryBuiltinNode {

        public abstract boolean executeObject(Object value);

        @Specialization
        public boolean isfinite(@SuppressWarnings("unused") long value) {
            return true;
        }

        @Specialization
        public boolean isfinite(@SuppressWarnings("unused") PInt value) {
            return true;
        }

        @Specialization
        public boolean isfinite(double value) {
            return Double.isFinite(value);
        }

        @Specialization(guards = "!isNumber(value)")
        public boolean isinf(Object value,
                        @Cached("create(__FLOAT__)") LookupAndCallUnaryNode dispatchFloat,
                        @Cached("create()") IsFiniteNode isFiniteNode) {
            Object result = dispatchFloat.executeObject(value);
            if (result == PNone.NO_VALUE) {
                throw raise(TypeError, "must be real number, not %p", value);
            }
            return isFiniteNode.executeObject(result);
        }

        protected static IsFiniteNode create() {
            return MathModuleBuiltinsFactory.IsFiniteNodeFactory.create(new PNode[0]);
        }
    }

    @Builtin(name = "isinf", fixedNumOfArguments = 1)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(MathGuards.class)
    @GenerateNodeFactory
    public abstract static class IsInfNode extends PythonUnaryBuiltinNode {

        public abstract boolean executeObject(Object value);

        @Specialization
        public boolean isinf(@SuppressWarnings("unused") long value) {
            return false;
        }

        @Specialization
        public boolean isfinite(@SuppressWarnings("unused") PInt value) {
            return false;
        }

        @Specialization
        public boolean isinf(double value) {
            return Double.isInfinite(value);
        }

        @Specialization(guards = "!isNumber(value)")
        public boolean isinf(Object value,
                        @Cached("create(__FLOAT__)") LookupAndCallUnaryNode dispatchFloat,
                        @Cached("create()") IsInfNode isInfNode) {
            Object result = dispatchFloat.executeObject(value);
            if (result == PNone.NO_VALUE) {
                throw raise(TypeError, "must be real number, not %p", value);
            }
            return isInfNode.executeObject(result);
        }

        protected static IsInfNode create() {
            return MathModuleBuiltinsFactory.IsInfNodeFactory.create(new PNode[0]);
        }
    }

    @Builtin(name = "log", minNumOfArguments = 1, maxNumOfArguments = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(MathGuards.class)
    @GenerateNodeFactory
    public abstract static class LogNode extends PythonUnaryBuiltinNode {

        @Child private LookupAndCallUnaryNode valueDispatchNode;
        @Child private LookupAndCallUnaryNode baseDispatchNode;
        @Child private LogNode recLogNode;
        
        private LookupAndCallUnaryNode getValueDispatchNode() {
            if (valueDispatchNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                valueDispatchNode = insert(LookupAndCallUnaryNode.create(SpecialMethodNames.__FLOAT__));
            }
            return valueDispatchNode;
        }
        
        private LookupAndCallUnaryNode getBaseDispatchNode() {
            if (baseDispatchNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                baseDispatchNode = insert(LookupAndCallUnaryNode.create(SpecialMethodNames.__FLOAT__));
            }
            return baseDispatchNode;
        }
        
        private double executeRecursiveLogNode(Object value, Object base) {
            if (recLogNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                recLogNode = insert(LogNode.create());
            }
            return recLogNode.executeObject(value, base);
        }
        
        public abstract double executeObject(Object value, Object base);

        private static final double LOG2 = Math.log(2.0);

        private static double logBigInteger(BigInteger val) {
            int blex = val.bitLength() - 1022; // any value in 60..1023 is ok
            if (blex > 0)
                val = val.shiftRight(blex);
            double res = Math.log(val.doubleValue());
            return blex > 0 ? res + blex * LOG2 : res;
        }

        private double countBase(double base, ConditionProfile divByZero) {
            double logBase = Math.log(base);
            if (divByZero.profile(logBase == 0)) {
                throw raise(ZeroDivisionError, "float division by zero");
            }
            return logBase;
        }

        private double countBase(BigInteger base, ConditionProfile divByZero) {
            double logBase = logBigInteger(base);
            if (divByZero.profile(logBase == 0)) {
                throw raise(ZeroDivisionError, "float division by zero");
            }
            return logBase;
        }
   
        @Specialization
        public double log(long value, @SuppressWarnings("unused") PNone novalue,
                        @Cached("createBinaryProfile()") ConditionProfile doNotFit) {
            return logDN(value, novalue, doNotFit);
        }

        @Specialization
        public double logDN(double value, @SuppressWarnings("unused") PNone novalue,
                        @Cached("createBinaryProfile()") ConditionProfile doNotFit) {
            raiseMathError(doNotFit, value < 0);
            return Math.log(value);
        }

        @Specialization
        @TruffleBoundary
        public double logPIN(PInt value, @SuppressWarnings("unused") PNone novalue,
                        @Cached("createBinaryProfile()") ConditionProfile doNotFit) {
            BigInteger bValue = value.getValue();
            raiseMathError(doNotFit, bValue.compareTo(BigInteger.ZERO) == -1);
            return logBigInteger(bValue);
        }

        @Specialization
        public double logLL(long value, long base,
                        @Cached("createBinaryProfile()") ConditionProfile doNotFit,
                        @Cached("createBinaryProfile()") ConditionProfile divByZero) {
            return logDD(value, base, doNotFit, divByZero);
        }

        @Specialization
        public double logDL(double value, long base,
                        @Cached("createBinaryProfile()") ConditionProfile doNotFit,
                        @Cached("createBinaryProfile()") ConditionProfile divByZero) {
            return logDD(value, base, doNotFit, divByZero);
        }

        @Specialization
        public double logLD(long value, double base,
                        @Cached("createBinaryProfile()") ConditionProfile doNotFit,
                        @Cached("createBinaryProfile()") ConditionProfile divByZero) {
            return logDD(value, base, doNotFit, divByZero);
        }

        @Specialization
        public double logDD(double value, double base,
                        @Cached("createBinaryProfile()") ConditionProfile doNotFit,
                        @Cached("createBinaryProfile()") ConditionProfile divByZero) {
            raiseMathError(doNotFit, value < 0 || base <= 0);
            double logBase = countBase(base, divByZero);
            return Math.log(value) / logBase;
        }

        @Specialization
        @TruffleBoundary
        public double logDPI(double value, PInt base,
                        @Cached("createBinaryProfile()") ConditionProfile doNotFit,
                        @Cached("createBinaryProfile()") ConditionProfile divByZero) {
            BigInteger bBase = base.getValue();
            raiseMathError(doNotFit, value < 0 || bBase.compareTo(BigInteger.ZERO) <= 0);
            double logBase = countBase(bBase, divByZero);
            return Math.log(value) / logBase;
        }

        @Specialization
        public double logPIL(PInt value, long base,
                        @Cached("createBinaryProfile()") ConditionProfile doNotFit,
                        @Cached("createBinaryProfile()") ConditionProfile divByZero) {
            return logPID(value, base, doNotFit, divByZero);
        }

        @Specialization
        @TruffleBoundary
        public double logPID(PInt value, double base,
                        @Cached("createBinaryProfile()") ConditionProfile doNotFit,
                        @Cached("createBinaryProfile()") ConditionProfile divByZero) {
            BigInteger bValue = value.getValue();
            raiseMathError(doNotFit, bValue.compareTo(BigInteger.ZERO) == -1 || base <= 0);
            double logBase = countBase(base, divByZero);
            return logBigInteger(bValue) / logBase;
        }

        @Specialization
        @TruffleBoundary
        public double logLPI(long value, PInt base,
                        @Cached("createBinaryProfile()") ConditionProfile doNotFit,
                        @Cached("createBinaryProfile()") ConditionProfile divByZero) {
            BigInteger bBase = base.getValue();
            raiseMathError(doNotFit, value < 0 || bBase.compareTo(BigInteger.ZERO) <= 0);
            double logBase = countBase(bBase, divByZero);
            return Math.log(value) / logBase;
        }

        @Specialization
        @TruffleBoundary
        public double logPIPI(PInt value, PInt base,
                        @Cached("createBinaryProfile()") ConditionProfile doNotFit,
                        @Cached("createBinaryProfile()") ConditionProfile divByZero) {
            BigInteger bValue = value.getValue();
            BigInteger bBase = base.getValue();
            raiseMathError(doNotFit, bValue.compareTo(BigInteger.ZERO) == -1 || bBase.compareTo(BigInteger.ZERO) <= 0);
            double logBase = countBase(bBase, divByZero);
            return logBigInteger(bValue) / logBase;
        }

        @Specialization(guards = "!isNumber(value)")
        public double logO(Object value, @SuppressWarnings("unused") PNone novalue,
                        @Cached("createBinaryProfile()") ConditionProfile notNumber) {
            Object result = getRealNumber(value, getValueDispatchNode(), notNumber);
            return executeRecursiveLogNode(result, novalue);
        }

        @Specialization(guards = {"!isNumber(value)", "!isNoValue(base)"})
        public double logOO(Object value, Object base,
                        @Cached("createBinaryProfile()") ConditionProfile notNumberValue,
                        @Cached("createBinaryProfile()") ConditionProfile notNumberBase) {
            Object resultValue = getRealNumber(value, getValueDispatchNode(), notNumberValue);
            Object resultBase = getRealNumber(base, getBaseDispatchNode(), notNumberBase);
            return executeRecursiveLogNode(resultValue, resultBase);
        }

        @Specialization(guards = {"!isNumber(base)"})
        public double logLO(long value, Object base,
                        @Cached("createBinaryProfile()") ConditionProfile notNumberBase) {
            return logDO(value, base, notNumberBase);
        }

        @Specialization(guards = {"!isNumber(base)"})
        public double logDO(double value, Object base,
                        @Cached("createBinaryProfile()") ConditionProfile notNumberBase) {
            Object resultBase = getRealNumber(base, getBaseDispatchNode(), notNumberBase);
            return executeRecursiveLogNode(value, resultBase);
        }

        @Specialization(guards = {"!isNumber(base)"})
        public double logPIO(PInt value, Object base, 
                        @Cached("createBinaryProfile()") ConditionProfile notNumberBase) {
            Object resultBase = getRealNumber(base, getBaseDispatchNode(), notNumberBase);
            return executeRecursiveLogNode(value, resultBase);
        }
        
        private void raiseMathError(ConditionProfile doNotFit, boolean con) {
            if (doNotFit.profile(con)) {
                throw raise(ValueError, "math domain error");
            }
        }
        
        private Object getRealNumber(Object object, LookupAndCallUnaryNode dispatchNode, ConditionProfile isNotRealNumber) {
            Object result = dispatchNode.executeObject(object);
            if (result == PNone.NO_VALUE) {
                throw raise(TypeError, "must be real number, not %p", object);
            }
            return result;
        }

        public static LogNode create() {
            return MathModuleBuiltinsFactory.LogNodeFactory.create(new PNode[0]);
        }
    }

    @Builtin(name = "fabs", fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class fabsNode extends PythonBuiltinNode {

        @Specialization
        public double fabs(int value) {
            return Math.abs((long) value);
        }

        @Specialization
        public double fabs(long value) {
            return Math.abs(value);
        }

        @Specialization
        @TruffleBoundary
        public PInt fabs(PInt value) {
            BigInteger xabs = value.getValue().abs();
            return factory().createInt(xabs);
        }

        @Specialization
        public double fabs(double value) {
            return Math.abs(value);
        }

        @Specialization
        public double fabs(PFloat value) {
            return Math.abs(value.getValue());
        }

        @Specialization
        public double fabs(boolean value) {
            return value ? 1.0 : 0.0;
        }

        @Fallback
        public double fabs(Object value) {
            throw raise(TypeError, "must be real number, not %p", value);
        }
    }

    @Builtin(name = "pow", fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class PowNode extends PythonBuiltinNode {

        @Specialization
        double pow(int left, int right) {
            return pow((double) left, (double) right);
        }

        @Specialization
        double pow(PInt left, PInt right) {
            return pow(left.doubleValue(), right.doubleValue());
        }

        @TruffleBoundary
        @Specialization
        double pow(double left, double right) {
            return Math.pow(left, right);
        }
    }

    @Builtin(name = "trunc", fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class TruncNode extends PythonUnaryBuiltinNode {

        @Specialization
        Object trunc(Object obj,
                        @Cached("create(__TRUNC__)") LookupAndCallUnaryNode callTrunc) {
            Object result = callTrunc.executeObject(obj);
            if (result == PNone.NO_VALUE) {
                raise(TypeError, "type %p doesn't define __trunc__ method", obj);
            }
            return result;
        }
    }

    @Builtin(name = "atan2", fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class Atan2Node extends PythonBuiltinNode {

        @Specialization
        double atan2(double left, double right) {
            return Math.atan2(left, right);
        }
    }
}
