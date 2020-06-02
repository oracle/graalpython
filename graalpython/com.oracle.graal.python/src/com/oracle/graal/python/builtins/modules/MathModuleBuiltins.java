/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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

import static com.oracle.graal.python.runtime.exception.PythonErrorType.NotImplementedError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ZeroDivisionError;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.Arrays;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.control.GetIteratorExpressionNode.GetIteratorNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
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
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(defineModule = "math")
public class MathModuleBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return MathModuleBuiltinsFactory.getFactories();
    }

    public MathModuleBuiltins() {
        // Add constant values
        builtinConstants.put("pi", Math.PI);
        builtinConstants.put("e", Math.E);
        builtinConstants.put("tau", 2 * Math.PI);
        builtinConstants.put("inf", Double.POSITIVE_INFINITY);
        builtinConstants.put("nan", Double.NaN);
    }

    public abstract static class MathUnaryBuiltinNode extends PythonUnaryBuiltinNode {

        public void checkMathRangeError(boolean con) {
            if (con) {
                throw raise(OverflowError, ErrorMessages.MATH_RAGE_ERROR);
            }
        }

        public void checkMathDomainError(boolean con) {
            if (con) {
                throw raise(ValueError, ErrorMessages.MATH_DOMAIN_ERROR);
            }
        }
    }

    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(MathGuards.class)
    public abstract static class MathDoubleUnaryBuiltinNode extends MathUnaryBuiltinNode {

        public abstract double executeObject(VirtualFrame frame, Object value);

        public double count(@SuppressWarnings("unused") double value) {
            throw raise(NotImplementedError, ErrorMessages.COUNT_FUNC_MATH);
        }

        @Specialization
        public double doL(long value) {
            return count(value);
        }

        @Specialization
        public double doD(double value) {
            return count(value);
        }

        @Specialization
        public double doPI(PInt value) {
            return count(value.doubleValue());
        }

        @Specialization(guards = "!isNumber(value)", limit = "1")
        public double doGeneral(Object value,
                        @CachedLibrary("value") PythonObjectLibrary lib) {
            return count(lib.asJavaDouble(value));
        }
    }

    // math.sqrt
    @Builtin(name = "sqrt", minNumOfPositionalArgs = 1, doc = "Return the square root of x.")
    @GenerateNodeFactory
    public abstract static class SqrtNode extends MathDoubleUnaryBuiltinNode {

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
        @TruffleBoundary
        @Override
        public double doPI(PInt value) {
            BigInteger bValue = value.getValue();
            checkMathDomainError(bValue.compareTo(BigInteger.ZERO) < 0);
            return sqrtBigNumber(bValue).doubleValue();
        }

        @Override
        public double count(double value) {
            checkMathDomainError(value < 0);
            return Math.sqrt(value);
        }
    }

    @Builtin(name = "exp", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ExpNode extends MathDoubleUnaryBuiltinNode {

        @Override
        public double count(double value) {
            double result = Math.exp(value);
            checkMathRangeError(Double.isFinite(value) && Double.isInfinite(result));
            return result;
        }
    }

    @Builtin(name = "expm1", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class Expm1Node extends MathDoubleUnaryBuiltinNode {

        @Override
        public double count(double value) {
            double result = Math.expm1(value);
            checkMathRangeError(Double.isFinite(value) && Double.isInfinite(result));
            return result;
        }
    }

    @Builtin(name = "ceil", minNumOfPositionalArgs = 1)
    @ImportStatic(MathGuards.class)
    @GenerateNodeFactory
    public abstract static class CeilNode extends MathUnaryBuiltinNode {

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
            return value ? 1 : 0;
        }

        @Specialization
        public Object ceil(VirtualFrame frame, PFloat value,
                        @Cached("create(__CEIL__)") LookupAndCallUnaryNode dispatchCeil) {
            Object result = dispatchCeil.executeObject(frame, value);
            if (PNone.NO_VALUE == result) {
                if (MathGuards.fitLong(value.getValue())) {
                    return ceilLong(value.getValue());
                } else {
                    return ceil(value.getValue());
                }
            }
            return result;
        }

        @Specialization
        public Object ceil(VirtualFrame frame, PInt value,
                        @Cached("create(__CEIL__)") LookupAndCallUnaryNode dispatchCeil) {
            return dispatchCeil.executeObject(frame, value);
        }

        @Specialization(guards = {"!isNumber(value)"})
        public Object ceil(VirtualFrame frame, Object value,
                        @CachedLibrary(limit = "1") PythonObjectLibrary lib,
                        @Cached("create(__CEIL__)") LookupAndCallUnaryNode dispatchCeil) {
            Object result = dispatchCeil.executeObject(frame, value);
            if (result == PNone.NO_VALUE) {
                return ceil(lib.asJavaDouble(value));
            }
            return result;
        }

    }

    @Builtin(name = "copysign", minNumOfPositionalArgs = 2)
    @ImportStatic(MathGuards.class)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class CopySignNode extends PythonBinaryBuiltinNode {

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
            return Math.copySign(magnitude.doubleValue(), sign);
        }

        @Specialization
        public double copySignPID(PInt magnitude, double sign) {
            return Math.copySign(magnitude.doubleValue(), sign);
        }

        @Specialization
        public double copySignLPI(long magnitude, PInt sign) {
            return Math.copySign(magnitude, sign.doubleValue());
        }

        @Specialization
        public double copySignDPI(double magnitude, PInt sign) {
            return Math.copySign(magnitude, sign.doubleValue());
        }

        @Specialization
        public double copySignPIPI(PInt magnitude, PInt sign) {
            return Math.copySign(magnitude.doubleValue(), sign.doubleValue());
        }

        @Specialization(guards = "!isNumber(magnitude) || !isNumber(sign)", limit = "1")
        public double copySignOO(Object magnitude, Object sign,
                        @CachedLibrary("magnitude") PythonObjectLibrary magnitudeLib,
                        @CachedLibrary("sign") PythonObjectLibrary signLib) {
            return copySignDD(magnitudeLib.asJavaDouble(magnitude), signLib.asJavaDouble(sign));
        }
    }

    @Builtin(name = "factorial", minNumOfPositionalArgs = 1)
    @ImportStatic({Double.class, MathGuards.class})
    @GenerateNodeFactory
    public abstract static class FactorialNode extends PythonUnaryBuiltinNode {

        @CompilationFinal(dimensions = 1) protected static final long[] SMALL_FACTORIALS = new long[]{
                        1, 1, 2, 6, 24, 120, 720, 5040, 40320,
                        362880, 3628800, 39916800, 479001600,
                        6227020800L, 87178291200L, 1307674368000L,
                        20922789888000L, 355687428096000L, 6402373705728000L,
                        121645100408832000L, 2432902008176640000L};

        @TruffleBoundary
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
        public int factorialBoolean(@SuppressWarnings("unused") boolean value) {
            return 1;
        }

        @Specialization(guards = {"value < 0"})
        public long factorialNegativeInt(@SuppressWarnings("unused") int value) {
            throw raise(PythonErrorType.ValueError, ErrorMessages.FACTORIAL_NOT_DEFINED_FOR_NEGATIVE);
        }

        @Specialization(guards = {"0 <= value", "value < SMALL_FACTORIALS.length"})
        public long factorialSmallInt(int value) {
            return SMALL_FACTORIALS[value];
        }

        @Specialization(guards = {"value >= SMALL_FACTORIALS.length"})
        public PInt factorialInt(int value) {
            return factory().createInt(factorialPart(1, value));
        }

        @Specialization(guards = {"value < 0"})
        public long factorialNegativeLong(@SuppressWarnings("unused") long value) {
            throw raise(PythonErrorType.ValueError, ErrorMessages.FACTORIAL_NOT_DEFINED_FOR_NEGATIVE);
        }

        @Specialization(guards = {"0 <= value", "value < SMALL_FACTORIALS.length"})
        public long factorialSmallLong(long value) {
            return SMALL_FACTORIALS[(int) value];
        }

        @Specialization(guards = {"value >= SMALL_FACTORIALS.length"})
        public PInt factorialLong(long value) {
            return factory().createInt(factorialPart(1, value));
        }

        @Specialization(guards = "isNegative(value)")
        public Object factorialPINegative(@SuppressWarnings("unused") PInt value) {
            throw raise(PythonErrorType.ValueError, ErrorMessages.FACTORIAL_NOT_DEFINED_FOR_NEGATIVE);
        }

        @Specialization(guards = "isOvf(value)")
        public Object factorialPIOvf(@SuppressWarnings("unused") PInt value) {
            throw raise(PythonErrorType.OverflowError, ErrorMessages.ARG_SHOULD_NOT_EXCEED, "factorial()", Long.MAX_VALUE);
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
        public long factorialDoubleNaN(@SuppressWarnings("unused") double value) {
            throw raise(PythonErrorType.ValueError, ErrorMessages.CANNOT_CONVERT_TO, "float NaN", "integer");
        }

        @Specialization(guards = {"isInfinite(value)"})
        public long factorialDoubleInfinite(@SuppressWarnings("unused") double value) {
            throw raise(PythonErrorType.ValueError, ErrorMessages.CANNOT_CONVERT_TO, "float infinity", "integer");
        }

        @Specialization(guards = "isNegative(value)")
        public PInt factorialDoubleNegative(@SuppressWarnings("unused") double value) {
            throw raise(PythonErrorType.ValueError, ErrorMessages.FACTORIAL_NOT_DEFINED_FOR_NEGATIVE);
        }

        @Specialization(guards = "!isInteger(value)")
        public PInt factorialDoubleNotInteger(@SuppressWarnings("unused") double value) {
            throw raise(PythonErrorType.ValueError, ErrorMessages.ONLY_ACCEPTS_INTEGRAL_VALUES, "factorial()");
        }

        @Specialization(guards = "isOvf(value)")
        public PInt factorialDoubleOvf(@SuppressWarnings("unused") double value) {
            throw raise(PythonErrorType.OverflowError, ErrorMessages.ARG_SHOULD_NOT_EXCEED, "factorial()", Long.MAX_VALUE);
        }

        @Specialization(guards = {"!isNaN(value)", "!isInfinite(value)",
                        "!isNegative(value)", "isInteger(value)", "!isOvf(value)"})
        public Object factorialDouble(double value) {
            if (value < SMALL_FACTORIALS.length) {
                return SMALL_FACTORIALS[(int) value];
            }
            return factory().createInt(factorialPart(1, (long) value));
        }

        @Specialization(guards = {"isNaN(value.getValue())"})
        public long factorialPFLNaN(@SuppressWarnings("unused") PFloat value) {
            throw raise(PythonErrorType.ValueError, ErrorMessages.CANNOT_CONVERT_TO, "float NaN", "integer");
        }

        @Specialization(guards = {"isInfinite(value.getValue())"})
        public long factorialPFLInfinite(@SuppressWarnings("unused") PFloat value) {
            throw raise(PythonErrorType.ValueError, ErrorMessages.CANNOT_CONVERT_TO, "float infinity", "integer");
        }

        @Specialization(guards = "isNegative(value.getValue())")
        public PInt factorialPFLNegative(@SuppressWarnings("unused") PFloat value) {
            throw raise(PythonErrorType.ValueError, ErrorMessages.FACTORIAL_NOT_DEFINED_FOR_NEGATIVE);
        }

        @Specialization(guards = "!isInteger(value.getValue())")
        public PInt factorialPFLNotInteger(@SuppressWarnings("unused") PFloat value) {
            throw raise(PythonErrorType.ValueError, ErrorMessages.ONLY_ACCEPTS_INTEGRAL_VALUES, "factorial()");
        }

        @Specialization(guards = "isOvf(value.getValue())")
        public PInt factorialPFLOvf(@SuppressWarnings("unused") PFloat value) {
            throw raise(PythonErrorType.OverflowError, ErrorMessages.ARG_SHOULD_NOT_EXCEED, "factorial()", Long.MAX_VALUE);
        }

        @Specialization(guards = {"!isNaN(value.getValue())", "!isInfinite(value.getValue())",
                        "!isNegative(value.getValue())", "isInteger(value.getValue())", "!isOvf(value.getValue())"})
        public Object factorialPFL(PFloat value) {
            double pfValue = value.getValue();
            if (pfValue < SMALL_FACTORIALS.length) {
                return SMALL_FACTORIALS[(int) pfValue];
            }
            return factory().createInt(factorialPart(1, (long) pfValue));
        }

        @Specialization(guards = "!isNumber(value)", limit = "1")
        public Object factorialObject(VirtualFrame frame, Object value,
                        @CachedLibrary("value") PythonObjectLibrary lib,
                        @Cached("create()") FactorialNode recursiveNode) {
            return recursiveNode.execute(frame, lib.asIndex(value));
        }

        protected boolean isInteger(double value) {
            return Double.isFinite(value) && value == Math.floor(value);
        }

        protected boolean isNegative(PInt value) {
            return value.isNegative();
        }

        protected boolean isNegative(double value) {
            return value < 0;
        }

        protected boolean isOvf(double value) {
            return value > Long.MAX_VALUE;
        }

        protected boolean isOvf(PInt value) {
            return value.compareTo(Long.MAX_VALUE) > 0;
        }

        protected static FactorialNode create() {
            return MathModuleBuiltinsFactory.FactorialNodeFactory.create();
        }
    }

    @Builtin(name = "comb", minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    @ImportStatic(MathGuards.class)
    public abstract static class CombNode extends PythonBinaryBuiltinNode {

        @TruffleBoundary
        private BigInteger calculateComb(BigInteger n, BigInteger k) {
            if (n.signum() < 0) {
                throw raise(ValueError, ErrorMessages.MUST_BE_NON_NEGATIVE_INTEGER, "n");
            }
            if (k.signum() < 0) {
                throw raise(ValueError, ErrorMessages.MUST_BE_NON_NEGATIVE_INTEGER, "k");
            }

            BigInteger factors = k.min(n.subtract(k));
            if (factors.signum() < 0) {
                return BigInteger.ZERO;
            }
            if (factors.signum() == 0) {
                return BigInteger.ONE;
            }
            BigInteger result = n;
            BigInteger factor = n;
            BigInteger i = BigInteger.ONE;
            while (i.compareTo(factors) < 0) {
                factor = factor.subtract(BigInteger.ONE);
                result = result.multiply(factor);
                i = i.add(BigInteger.ONE);
                result = result.divide(i);
            }
            return result;
        }

        @Specialization
        PInt comb(long n, long k) {
            return factory().createInt(calculateComb(PInt.longToBigInteger(n), PInt.longToBigInteger(k)));
        }

        @Specialization
        PInt comb(long n, PInt k) {
            return factory().createInt(calculateComb(PInt.longToBigInteger(n), k.getValue()));
        }

        @Specialization
        PInt comb(PInt n, long k) {
            return factory().createInt(calculateComb(n.getValue(), PInt.longToBigInteger(k)));
        }

        @Specialization
        PInt comb(PInt n, PInt k) {
            return factory().createInt(calculateComb(n.getValue(), k.getValue()));
        }

        @Specialization
        int comb(@SuppressWarnings("unused") double n, @SuppressWarnings("unused") Object k) {
            throw raise(TypeError, ErrorMessages.OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER, "float");
        }

        @Specialization
        int comb(@SuppressWarnings("unused") Object n, @SuppressWarnings("unused") double k) {
            throw raise(TypeError, ErrorMessages.OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER, "float");
        }

        @Specialization(guards = "!isNumber(n) || !isNumber(k)")
        Object comb(VirtualFrame frame, Object n, Object k,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @CachedLibrary(limit = "2") PythonObjectLibrary lib,
                        @Cached CombNode recursiveNode) {
            Object nValue = lib.asIndexWithFrame(n, hasFrame, frame);
            Object kValue = lib.asIndexWithFrame(k, hasFrame, frame);
            return recursiveNode.execute(frame, nValue, kValue);
        }

        public static CombNode create() {
            return MathModuleBuiltinsFactory.CombNodeFactory.create();
        }
    }

    @Builtin(name = "perm", minNumOfPositionalArgs = 1, parameterNames = {"n", "k"})
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    @ImportStatic(MathGuards.class)
    public abstract static class PermNode extends PythonBinaryBuiltinNode {

        @TruffleBoundary
        private BigInteger calculatePerm(BigInteger n, BigInteger k) {
            if (n.signum() < 0) {
                throw raise(ValueError, ErrorMessages.MUST_BE_NON_NEGATIVE_INTEGER, "n");
            }
            if (k.signum() < 0) {
                throw raise(ValueError, ErrorMessages.MUST_BE_NON_NEGATIVE_INTEGER, "k");
            }
            if (n.compareTo(k) < 0) {
                return BigInteger.ZERO;
            }
            if (k.equals(BigInteger.ZERO)) {
                return BigInteger.ONE;
            }
            if (k.equals(BigInteger.ONE)) {
                return n;
            }

            BigInteger result = n;
            BigInteger factor = n;
            BigInteger i = BigInteger.ONE;
            while (i.compareTo(k) < 0) {
                factor = factor.subtract(BigInteger.ONE);
                result = result.multiply(factor);
                i = i.add(BigInteger.ONE);
            }
            return result;
        }

        @Specialization
        PInt perm(long n, long k) {
            return factory().createInt(calculatePerm(PInt.longToBigInteger(n), PInt.longToBigInteger(k)));
        }

        @Specialization
        PInt perm(long n, PInt k) {
            return factory().createInt(calculatePerm(PInt.longToBigInteger(n), k.getValue()));
        }

        @Specialization
        PInt perm(PInt n, long k) {
            return factory().createInt(calculatePerm(n.getValue(), PInt.longToBigInteger(k)));
        }

        @Specialization
        PInt perm(PInt n, PInt k) {
            return factory().createInt(calculatePerm(n.getValue(), k.getValue()));
        }

        @Specialization
        int perm(@SuppressWarnings("unused") double n, @SuppressWarnings("unused") Object k) {
            throw raise(TypeError, ErrorMessages.OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER, "float");
        }

        @Specialization
        int perm(@SuppressWarnings("unused") Object n, @SuppressWarnings("unused") double k) {
            throw raise(TypeError, ErrorMessages.OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER, "float");
        }

        @Specialization
        Object perm(VirtualFrame frame, Object n, @SuppressWarnings("unused") PNone k,
                        @Cached FactorialNode factorialNode) {
            return factorialNode.execute(frame, n);
        }

        @Specialization(guards = "!isNumber(n) || !isNumber(k)")
        Object perm(VirtualFrame frame, Object n, Object k,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @CachedLibrary(limit = "2") PythonObjectLibrary lib,
                        @Cached PermNode recursiveNode) {
            Object nValue = lib.asIndexWithFrame(n, hasFrame, frame);
            Object kValue = lib.asIndexWithFrame(k, hasFrame, frame);
            return recursiveNode.execute(frame, nValue, kValue);
        }

        public static PermNode create() {
            return MathModuleBuiltinsFactory.PermNodeFactory.create();
        }
    }

    @Builtin(name = "floor", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @ImportStatic(MathGuards.class)
    public abstract static class FloorNode extends PythonUnaryBuiltinNode {

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
        public Object floorPF(VirtualFrame frame, PFloat value,
                        @Cached("create(__FLOOR__)") LookupAndCallUnaryNode dispatchFloor) {
            Object result = dispatchFloor.executeObject(frame, value);
            if (result == PNone.NO_VALUE) {
                if (value.getValue() <= Long.MAX_VALUE) {
                    result = floor(value.getValue());
                } else {
                    result = factory().createInt(createBigInteger(value));
                }
            }
            return result;
        }

        @TruffleBoundary
        private static BigInteger createBigInteger(PFloat value) {
            return BigDecimal.valueOf(Math.floor(value.getValue())).toBigInteger();
        }

        private static double floor(double value) {
            return Math.floor(value);
        }

        @Specialization
        public Object floorPI(VirtualFrame frame, PInt value,
                        @Cached("create(__FLOOR__)") LookupAndCallUnaryNode dispatchFloor) {
            return dispatchFloor.executeObject(frame, value);
        }

        @Specialization(guards = {"!isNumber(value)"})
        public Object floor(VirtualFrame frame, Object value,
                        @Cached("create(__FLOOR__)") LookupAndCallUnaryNode dispatchFloor,
                        @CachedLibrary(limit = "1") PythonObjectLibrary lib,
                        @Cached("create()") FloorNode recursiveNode) {
            Object result = dispatchFloor.executeObject(frame, value);
            if (PNone.NO_VALUE == result) {
                return recursiveNode.execute(frame, lib.asJavaDouble(value));
            }
            return result;
        }

        protected static FloorNode create() {
            return MathModuleBuiltinsFactory.FloorNodeFactory.create();
        }
    }

    @Builtin(name = "fmod", minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(MathGuards.class)
    @GenerateNodeFactory
    public abstract static class FmodNode extends PythonBinaryBuiltinNode {

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
        public double fmodDPI(double left, PInt right) {
            raiseMathDomainError(Double.isInfinite(left));
            double rvalue = right.doubleValue();
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
            double rvalue = right.doubleValue();
            raiseMathDomainError(rvalue == 0);
            return left % rvalue;
        }

        @Specialization
        public double fmodPIPI(PInt left, PInt right) {
            double rvalue = right.doubleValue();
            raiseMathDomainError(rvalue == 0);
            double lvalue = left.doubleValue();
            return lvalue % rvalue;
        }

        @Specialization
        public double fmodPIL(PInt left, long right) {
            raiseMathDomainError(right == 0);
            double lvalue = left.doubleValue();
            return lvalue % right;
        }

        @Specialization
        public double fmodPID(PInt left, double right) {
            raiseMathDomainError(right == 0);
            double lvalue = left.doubleValue();
            return lvalue % right;
        }

        @Specialization(guards = {"!isNumber(left) || !isNumber(right)"}, limit = "1")
        public double fmodLO(Object left, Object right,
                        @CachedLibrary("left") PythonObjectLibrary leftLib,
                        @CachedLibrary("right") PythonObjectLibrary rightLib,
                        @Cached("createBinaryProfile()") ConditionProfile infProfile,
                        @Cached("createBinaryProfile()") ConditionProfile zeroProfile) {
            return fmodDD(leftLib.asJavaDouble(left), rightLib.asJavaDouble(right), infProfile, zeroProfile);
        }

        protected void raiseMathDomainError(boolean con) {
            if (con) {
                throw raise(PythonErrorType.ValueError, ErrorMessages.MATH_DOMAIN_ERROR);
            }
        }

    }

    @Builtin(name = "remainder", minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(MathGuards.class)
    @GenerateNodeFactory
    public abstract static class RemainderNode extends PythonBinaryBuiltinNode {

        @Specialization
        double remainderDD(double x, double y) {
            if (Double.isFinite(x) && Double.isFinite(y)) {
                if (y == 0.0) {
                    throw raise(ValueError, ErrorMessages.MATH_DOMAIN_ERROR);
                }
                double absx = Math.abs(x);
                double absy = Math.abs(y);
                double m = absx % absy;
                double c = absy - m;
                double r;
                if (m < c) {
                    r = m;
                } else if (m > c) {
                    r = -c;
                } else {
                    r = m - 2.0 * ((0.5 * (absx - m)) % absy);
                }
                return Math.copySign(1.0, x) * r;
            }
            if (Double.isNaN(x)) {
                return x;
            }
            if (Double.isNaN(y)) {
                return y;
            }
            if (Double.isInfinite(x)) {
                throw raise(ValueError, ErrorMessages.MATH_DOMAIN_ERROR);
            }
            return x;
        }

        @Specialization(limit = "1")
        double remainderOO(Object x, Object y,
                        @CachedLibrary("x") PythonObjectLibrary xLib,
                        @CachedLibrary("y") PythonObjectLibrary yLib) {
            return remainderDD(xLib.asJavaDouble(x), yLib.asJavaDouble(y));
        }
    }

    @Builtin(name = "frexp", minNumOfPositionalArgs = 1)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(MathGuards.class)
    @GenerateNodeFactory
    public abstract static class FrexpNode extends PythonUnaryBuiltinNode {
        public static double[] frexp(double value) {
            // double can represent int without loss of data
            int exponent = 0;
            double mantissa = 0.0;

            if (value == 0.0 || value == -0.0) {
                return new double[]{mantissa, exponent};
            }

            if (Double.isNaN(value)) {
                mantissa = Double.NaN;
                exponent = -1;
                return new double[]{mantissa, exponent};
            }

            if (Double.isInfinite(value)) {
                mantissa = value;
                exponent = -1;
                return new double[]{mantissa, exponent};
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
            return new double[]{neg ? -mantissa : mantissa, exponent};
        }

        @Specialization
        public PTuple frexpD(double value) {
            Object[] content = new Object[2];
            double[] primContent = frexp(value);
            content[0] = primContent[0];
            content[1] = (int) primContent[1];
            return factory().createTuple(content);
        }

        @Specialization
        public PTuple frexpL(long value) {
            return frexpD(value);
        }

        @Specialization
        public PTuple frexpPI(VirtualFrame frame, PInt value,
                        @Cached("createNotNormalized()") SequenceStorageNodes.GetItemNode getItemNode) {
            PTuple result = frexpD(value.doubleValue());
            if (Double.isInfinite((double) getItemNode.execute(frame, result.getSequenceStorage(), 0))) {
                throw raise(OverflowError, ErrorMessages.INT_TOO_LARGE_TO_CONVERT_TO_FLOAT);
            }
            return result;
        }

        @Specialization(guards = "!isNumber(value)", limit = "1")
        public PTuple frexpO(Object value,
                        @CachedLibrary("value") PythonObjectLibrary lib) {
            return frexpD(lib.asJavaDouble(value));
        }
    }

    @Builtin(name = "isnan", minNumOfPositionalArgs = 1)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(MathGuards.class)
    @GenerateNodeFactory
    public abstract static class IsNanNode extends PythonUnaryBuiltinNode {
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

        @Specialization(guards = "!isNumber(value)", limit = "1")
        public boolean isinf(Object value,
                        @CachedLibrary("value") PythonObjectLibrary lib) {
            return isNan(lib.asJavaDouble(value));
        }
    }

    @Builtin(name = "isclose", minNumOfPositionalArgs = 2, parameterNames = {"a", "b", "rel_tol", "abs_tol"})
    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(MathGuards.class)
    @GenerateNodeFactory
    public abstract static class IsCloseNode extends PythonBuiltinNode {
        private static final double DEFAULT_REL = 1e-09;
        private static final double DEFAULT_ABS = 0.0;

        private boolean isCloseDouble(double a, double b, double rel_tol, double abs_tol) {
            double diff;

            if (rel_tol < 0.0 || abs_tol < 0.0) {
                throw raise(ValueError, ErrorMessages.TOLERANCE_MUST_NON_NEGATIVE);
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

        @Specialization
        public boolean isClose(double a, long b, double rel_tol, @SuppressWarnings("unused") PNone abs_tol) {
            return isCloseDouble(a, b, rel_tol, DEFAULT_ABS);
        }

        @Specialization
        public boolean isClose(long a, long b, double rel_tol, @SuppressWarnings("unused") PNone abs_tol) {
            return isCloseDouble(a, b, rel_tol, DEFAULT_ABS);
        }

        @Specialization
        public boolean isClose(long a, double b, double rel_tol, @SuppressWarnings("unused") PNone abs_tol) {
            return isCloseDouble(a, b, rel_tol, DEFAULT_ABS);
        }

        @Specialization
        public boolean isClose(long a, double b, double rel_tol, double abs_tol) {
            return isCloseDouble(a, b, rel_tol, abs_tol);
        }

        @Specialization
        public boolean isClose(long a, long b, double rel_tol, double abs_tol) {
            return isCloseDouble(a, b, rel_tol, abs_tol);
        }

        @Specialization(limit = "1")
        public boolean isClose(Object a, Object b, Object rel_tol, Object abs_tol,
                        @CachedLibrary("a") PythonObjectLibrary aLib,
                        @CachedLibrary("b") PythonObjectLibrary bLib,
                        @CachedLibrary(limit = "1") PythonObjectLibrary absLib,
                        @CachedLibrary(limit = "1") PythonObjectLibrary relLib) {
            double a_value = aLib.asJavaDouble(a);
            double b_value = bLib.asJavaDouble(b);
            double rel_tol_value = PGuards.isNoValue(rel_tol) ? DEFAULT_REL : relLib.asJavaDouble(rel_tol);
            double abs_tol_value = PGuards.isNoValue(abs_tol) ? DEFAULT_ABS : absLib.asJavaDouble(abs_tol);
            return isCloseDouble(a_value, b_value, rel_tol_value, abs_tol_value);
        }
    }

    @Builtin(name = "ldexp", minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class LdexpNode extends PythonBinaryBuiltinNode {

        private static final String EXPECTED_INT_MESSAGE = "Expected an int as second argument to ldexp.";

        abstract double execute(VirtualFrame frame, double mantissa, Object exp);

        private static int makeInt(long x) {
            long result = x;
            if (x < Integer.MIN_VALUE) {
                result = Integer.MIN_VALUE;
            } else if (x > Integer.MAX_VALUE) {
                result = Integer.MAX_VALUE;
            }
            return (int) result;
        }

        @TruffleBoundary
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
                throw raise(OverflowError, ErrorMessages.MATH_RAGE_ERROR);
            } else {
                return result;
            }
        }

        @Specialization
        public double ldexpDD(@SuppressWarnings("unused") double mantissa, @SuppressWarnings("unused") double exp) {
            throw raise(TypeError, EXPECTED_INT_MESSAGE);
        }

        @Specialization
        public double ldexpDD(double mantissa, long exp) {
            return exceptInfinity(Math.scalb(mantissa, makeInt(exp)), mantissa);
        }

        @Specialization
        public double ldexpLD(@SuppressWarnings("unused") long mantissa, @SuppressWarnings("unused") double exp) {
            throw raise(TypeError, EXPECTED_INT_MESSAGE);
        }

        @Specialization
        public double ldexpLL(long mantissa, long exp) {
            return exceptInfinity(Math.scalb((double) mantissa, makeInt(exp)), mantissa);
        }

        @Specialization
        public double ldexpDPI(double mantissa, PInt exp) {
            return exceptInfinity(Math.scalb(mantissa, makeInt(exp)), mantissa);
        }

        @Specialization
        public double ldexpLPI(long mantissa, PInt exp) {
            return exceptInfinity(Math.scalb((double) mantissa, makeInt(exp)), mantissa);
        }

        @Specialization
        public double ldexpPIPI(PInt mantissa, PInt exp) {
            double dm = mantissa.doubleValue();
            return exceptInfinity(Math.scalb(dm, makeInt(exp)), dm);
        }

        @Specialization
        public double ldexpPID(@SuppressWarnings("unused") PInt mantissa, @SuppressWarnings("unused") double exp) {
            throw raise(TypeError, EXPECTED_INT_MESSAGE);
        }

        @Specialization
        public double ldexpPIL(PInt mantissa, long exp) {
            double dm = mantissa.doubleValue();
            return exceptInfinity(Math.scalb(dm, makeInt(exp)), dm);
        }

        @Child private PythonObjectLibrary mantissaLib;
        @Child private LdexpNode recursiveNode;

        @Fallback
        public double ldexpOO(VirtualFrame frame, Object mantissa, Object exp) {
            if (PGuards.isInteger(exp) || PGuards.isPInt(exp) || (exp instanceof Boolean)) {
                if (recursiveNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    mantissaLib = insert(PythonObjectLibrary.getFactory().createDispatched(1));
                    recursiveNode = insert(LdexpNode.create());
                }
                return recursiveNode.execute(frame, mantissaLib.asJavaDouble(mantissa), exp);
            }
            throw raise(TypeError, EXPECTED_INT_MESSAGE);
        }

        static LdexpNode create() {
            return MathModuleBuiltinsFactory.LdexpNodeFactory.create();
        }

    }

    @Builtin(name = "modf", minNumOfPositionalArgs = 1)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(MathGuards.class)
    @GenerateNodeFactory
    public abstract static class ModfNode extends MathUnaryBuiltinNode {

        @Specialization
        PTuple modfD(double value) {
            if (!Double.isFinite(value)) {
                if (Double.isInfinite(value)) {
                    return factory().createTuple(new Object[]{Math.copySign(0., value), value});
                } else if (Double.isNaN(value)) {
                    return factory().createTuple(new Object[]{value, value});
                }
            }
            double fraction = value % 1;
            double integral = value - fraction;
            return factory().createTuple(new Object[]{fraction, integral});
        }

        @Specialization
        PTuple modfL(long value) {
            return modfD(value);
        }

        @Specialization
        PTuple modfPI(PInt value) {
            return modfD(value.doubleValue());
        }

        @Specialization(guards = "!isNumber(value)", limit = "1")
        PTuple modfO(Object value,
                        @CachedLibrary("value") PythonObjectLibrary lib) {
            return modfD(lib.asJavaDouble(value));
        }
    }

    @Builtin(name = "fsum", minNumOfPositionalArgs = 1)
    @ImportStatic(PGuards.class)
    @GenerateNodeFactory
    public abstract static class FsumNode extends PythonUnaryBuiltinNode {

        @Specialization
        @SuppressWarnings("try")
        double doIt(VirtualFrame frame, Object iterable,
                        @Cached GetIteratorNode getIterator,
                        @Cached("create(__NEXT__)") LookupAndCallUnaryNode next,
                        @CachedLibrary(limit = "3") PythonObjectLibrary lib,
                        @Cached IsBuiltinClassProfile stopProfile) {
            Object iterator = getIterator.executeWith(frame, iterable);
            return fsum(frame, iterator, next, lib, stopProfile);
        }

        /*
         * This implementation is taken from CPython. The performance is not good. Should be faster.
         * It can be easily replace with much simpler code based on BigDecimal:
         *
         * BigDecimal result = BigDecimal.ZERO;
         *
         * in cycle just: result = result.add(BigDecimal.valueof(x); ... The current implementation
         * is little bit faster. The testFSum in test_math.py takes in different implementations:
         * CPython ~0.6s CurrentImpl: ~14.3s Using BigDecimal: ~15.1
         */
        private double fsum(VirtualFrame frame, Object iterator, LookupAndCallUnaryNode next, PythonObjectLibrary lib, IsBuiltinClassProfile stopProfile) {
            double x, y, t, hi, lo = 0, yr, inf_sum = 0, special_sum = 0, sum;
            double xsave;
            int i, j, n = 0, arayLength = 32;
            double[] p = new double[arayLength];
            while (true) {
                try {
                    x = lib.asJavaDouble(next.executeObject(frame, iterator));
                } catch (PException e) {
                    e.expectStopIteration(stopProfile);
                    break;
                }
                xsave = x;
                for (i = j = 0; j < n; j++) { /* for y in partials */
                    y = p[j];
                    if (Math.abs(x) < Math.abs(y)) {
                        t = x;
                        x = y;
                        y = t;
                    }
                    hi = x + y;
                    yr = hi - x;
                    lo = y - yr;
                    if (lo != 0.0) {
                        p[i++] = lo;
                    }
                    x = hi;
                }

                n = i;
                if (x != 0.0) {
                    if (!Double.isFinite(x)) {
                        /*
                         * a nonfinite x could arise either as a result of intermediate overflow, or
                         * as a result of a nan or inf in the summands
                         */
                        if (Double.isFinite(xsave)) {
                            throw raise(OverflowError, ErrorMessages.INTERMEDIATE_OVERFLOW_IN, "fsum");
                        }
                        if (Double.isInfinite(xsave)) {
                            inf_sum += xsave;
                        }
                        special_sum += xsave;
                        /* reset partials */
                        n = 0;
                    } else if (n >= arayLength) {
                        arayLength += arayLength;
                        p = Arrays.copyOf(p, arayLength);
                    } else {
                        p[n++] = x;
                    }
                }
            }

            if (special_sum != 0.0) {
                if (Double.isNaN(inf_sum)) {
                    throw raise(ValueError, ErrorMessages.NEG_INF_PLUS_INF_IN);
                } else {
                    sum = special_sum;
                    return sum;
                }
            }

            hi = 0.0;
            if (n > 0) {
                hi = p[--n];
                /*
                 * sum_exact(ps, hi) from the top, stop when the sum becomes inexact.
                 */
                while (n > 0) {
                    x = hi;
                    y = p[--n];
                    assert (Math.abs(y) < Math.abs(x));
                    hi = x + y;
                    yr = hi - x;
                    lo = y - yr;
                    if (lo != 0.0) {
                        break;
                    }
                }
                /*
                 * Make half-even rounding work across multiple partials. Needed so that sum([1e-16,
                 * 1, 1e16]) will round-up the last digit to two instead of down to zero (the 1e-16
                 * makes the 1 slightly closer to two). With a potential 1 ULP rounding error
                 * fixed-up, math.fsum() can guarantee commutativity.
                 */
                if (n > 0 && ((lo < 0.0 && p[n - 1] < 0.0) ||
                                (lo > 0.0 && p[n - 1] > 0.0))) {
                    y = lo * 2.0;
                    x = hi + y;
                    yr = x - hi;
                    if (compareAsBigDecimal(y, yr) == 0) {
                        hi = x;
                    }
                }
            }
            return hi;
        }

        @TruffleBoundary
        private static int compareAsBigDecimal(double y, double yr) {
            return BigDecimal.valueOf(y).compareTo(BigDecimal.valueOf(yr));
        }
    }

    @Builtin(name = "gcd", minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    @ImportStatic(MathGuards.class)
    public abstract static class GcdNode extends PythonBinaryBuiltinNode {

        private long count(long a, long b) {
            if (b == 0) {
                return a;
            }
            return count(b, a % b);
        }

        @Specialization
        long gcd(long x, long y) {
            return Math.abs(count(x, y));
        }

        @Specialization
        PInt gcd(long x, PInt y) {
            return factory().createInt(op(PInt.longToBigInteger(x), y.getValue()));
        }

        @Specialization
        PInt gcd(PInt x, long y) {
            return factory().createInt(op(x.getValue(), PInt.longToBigInteger(y)));
        }

        @TruffleBoundary
        private static BigInteger op(BigInteger x, BigInteger y) {
            return x.gcd(y);
        }

        @Specialization
        PInt gcd(PInt x, PInt y) {
            return factory().createInt(op(x.getValue(), y.getValue()));
        }

        @Specialization
        int gcd(@SuppressWarnings("unused") double x, @SuppressWarnings("unused") double y) {
            throw raise(TypeError, ErrorMessages.OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER, "float");
        }

        @Specialization
        int gcd(@SuppressWarnings("unused") long x, @SuppressWarnings("unused") double y) {
            throw raise(TypeError, ErrorMessages.OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER, "float");
        }

        @Specialization
        int gcd(@SuppressWarnings("unused") double x, @SuppressWarnings("unused") long y) {
            throw raise(TypeError, ErrorMessages.OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER, "float");
        }

        @Specialization
        int gcd(@SuppressWarnings("unused") double x, @SuppressWarnings("unused") PInt y) {
            throw raise(TypeError, ErrorMessages.OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER, "float");
        }

        @Specialization
        int gcd(@SuppressWarnings("unused") PInt x, @SuppressWarnings("unused") double y) {
            throw raise(TypeError, ErrorMessages.OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER, "float");
        }

        @Specialization(guards = "!isNumber(x) || !isNumber(y)")
        Object gcd(VirtualFrame frame, Object x, Object y,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @CachedLibrary(limit = "2") PythonObjectLibrary lib,
                        @Cached("create()") GcdNode recursiveNode) {
            Object xValue = lib.asIndexWithFrame(x, hasFrame, frame);
            Object yValue = lib.asIndexWithFrame(y, hasFrame, frame);
            return recursiveNode.execute(frame, xValue, yValue);
        }

        public static GcdNode create() {
            return MathModuleBuiltinsFactory.GcdNodeFactory.create();
        }
    }

    @Builtin(name = "acos", minNumOfPositionalArgs = 1, doc = "Return the arc cosine (measured in radians) of x.")
    @GenerateNodeFactory
    public abstract static class AcosNode extends MathDoubleUnaryBuiltinNode {

        @Override
        public double count(double value) {
            checkMathDomainError(Double.isInfinite(value) || -1 > value || value > 1);
            return Math.acos(value);
        }
    }

    @Builtin(name = "acosh", minNumOfPositionalArgs = 1, doc = "Return the inverse hyperbolic cosine of x.")
    @GenerateNodeFactory
    public abstract static class AcoshNode extends MathDoubleUnaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        @Override
        public double doPI(PInt value) {
            BigInteger bValue = value.getValue();
            checkMathDomainError(bValue.compareTo(BigInteger.ONE) < 0);

            BigDecimal sqrt = SqrtNode.sqrtBigNumber(bValue.multiply(bValue).subtract(BigInteger.ONE));
            BigDecimal bd = new BigDecimal(bValue);
            return Math.log(bd.add(sqrt).doubleValue());
        }

        @Override
        public double count(double value) {
            checkMathDomainError(value < 1);
            return Math.log(value + Math.sqrt(value * value - 1.0));
        }
    }

    @Builtin(name = "asin", minNumOfPositionalArgs = 1, doc = "Return the arc sine (measured in radians) of x.")
    @GenerateNodeFactory
    public abstract static class AsinNode extends MathDoubleUnaryBuiltinNode {

        @Override
        public double count(double value) {
            checkMathDomainError(value < -1 || value > 1);
            return Math.asin(value);
        }
    }

    @Builtin(name = "cos", minNumOfPositionalArgs = 1, doc = "Return the cosine of x (measured in radians).")
    @GenerateNodeFactory
    public abstract static class CosNode extends MathDoubleUnaryBuiltinNode {

        @Override
        public double count(double value) {
            return Math.cos(value);
        }
    }

    @Builtin(name = "cosh", minNumOfPositionalArgs = 1, doc = "Return the hyperbolic cosine of x.")
    @GenerateNodeFactory
    public abstract static class CoshNode extends MathDoubleUnaryBuiltinNode {

        @Override
        public double count(double value) {
            double result = Math.cosh(value);
            checkMathRangeError(Double.isInfinite(result) && Double.isFinite(value));
            return result;
        }
    }

    @Builtin(name = "sin", minNumOfPositionalArgs = 1, doc = "Return the sine of x (measured in radians).")
    @GenerateNodeFactory
    public abstract static class SinNode extends MathDoubleUnaryBuiltinNode {

        @Override
        public double count(double value) {
            return Math.sin(value);
        }
    }

    @Builtin(name = "sinh", minNumOfPositionalArgs = 1, doc = "Return the hyperbolic sine of x.")
    @GenerateNodeFactory
    public abstract static class SinhNode extends MathDoubleUnaryBuiltinNode {

        @Override
        public double count(double value) {
            double result = Math.sinh(value);
            checkMathRangeError(Double.isInfinite(result) && Double.isFinite(value));
            return result;
        }
    }

    @Builtin(name = "tan", minNumOfPositionalArgs = 1, doc = "Return the tangent of x (measured in radians).")
    @GenerateNodeFactory
    public abstract static class TanNode extends MathDoubleUnaryBuiltinNode {

        @Override
        public double count(double value) {
            return Math.tan(value);
        }
    }

    @Builtin(name = "tanh", minNumOfPositionalArgs = 1, doc = "Return the hyperbolic tangent of x.")
    @GenerateNodeFactory
    public abstract static class TanhNode extends MathDoubleUnaryBuiltinNode {

        @Override
        public double count(double value) {
            return Math.tanh(value);
        }
    }

    @Builtin(name = "atan", minNumOfPositionalArgs = 1, doc = "Return the arc tangent (measured in radians) of x.")
    @GenerateNodeFactory
    public abstract static class AtanNode extends MathDoubleUnaryBuiltinNode {

        @Override
        public double count(double value) {
            return Math.atan(value);
        }
    }

    @Builtin(name = "atanh", minNumOfPositionalArgs = 1, doc = "Return the inverse hyperbolic tangent of x.")
    @GenerateNodeFactory
    public abstract static class AtanhNode extends MathDoubleUnaryBuiltinNode {

        @Override
        public double count(double value) {
            if (value == 0) {
                return 0;
            }
            checkMathDomainError(value <= -1 || value >= 1);
            return Math.log((1 / value + 1) / (1 / value - 1)) / 2;
        }
    }

    @Builtin(name = "asinh", minNumOfPositionalArgs = 1, doc = "Return the inverse hyperbolic sine of x.")
    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(MathGuards.class)
    @GenerateNodeFactory
    public abstract static class AsinhNode extends MathDoubleUnaryBuiltinNode {

        @Override
        public double count(double value) {
            if (Double.isInfinite(value)) {
                return value;
            }
            return Math.log(value + Math.sqrt(value * value + 1.0));
        }
    }

    @Builtin(name = "isfinite", minNumOfPositionalArgs = 1)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(MathGuards.class)
    @GenerateNodeFactory
    public abstract static class IsFiniteNode extends PythonUnaryBuiltinNode {

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

        @Specialization(guards = "!isNumber(value)", limit = "1")
        public boolean isinf(Object value,
                        @CachedLibrary("value") PythonObjectLibrary lib) {
            return isfinite(lib.asJavaDouble(value));
        }
    }

    @Builtin(name = "isinf", minNumOfPositionalArgs = 1)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(MathGuards.class)
    @GenerateNodeFactory
    public abstract static class IsInfNode extends PythonUnaryBuiltinNode {

        @Specialization
        public boolean isinf(@SuppressWarnings("unused") long value) {
            return false;
        }

        @Specialization
        public boolean isinf(@SuppressWarnings("unused") PInt value) {
            return false;
        }

        @Specialization
        public boolean isinf(double value) {
            return Double.isInfinite(value);
        }

        @Specialization(guards = "!isNumber(value)", limit = "1")
        public boolean isinf(Object value,
                        @CachedLibrary("value") PythonObjectLibrary lib) {
            return isinf(lib.asJavaDouble(value));
        }
    }

    @Builtin(name = "log", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(MathGuards.class)
    @GenerateNodeFactory
    public abstract static class LogNode extends PythonBinaryBuiltinNode {

        @Child private LogNode recLogNode;

        private double executeRecursiveLogNode(VirtualFrame frame, Object value, Object base) {
            if (recLogNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                recLogNode = insert(LogNode.create());
            }
            return recLogNode.executeObject(frame, value, base);
        }

        public abstract double executeObject(VirtualFrame frame, Object value, Object base);

        private static final double LOG2 = Math.log(2.0);

        protected static double logBigInteger(BigInteger val) {
            int blex = val.bitLength() - 1022; // any value in 60..1023 is ok
            BigInteger value = blex > 0 ? val.shiftRight(blex) : val;
            double res = Math.log(value.doubleValue());
            return blex > 0 ? res + blex * LOG2 : res;
        }

        private double countBase(double base, ConditionProfile divByZero) {
            double logBase = Math.log(base);
            if (divByZero.profile(logBase == 0)) {
                throw raise(ZeroDivisionError, ErrorMessages.S_DIVISION_BY_ZERO, "float");
            }
            return logBase;
        }

        private double countBase(BigInteger base, ConditionProfile divByZero) {
            double logBase = logBigInteger(base);
            if (divByZero.profile(logBase == 0)) {
                throw raise(ZeroDivisionError, ErrorMessages.S_DIVISION_BY_ZERO, "float");
            }
            return logBase;
        }

        @Specialization
        public double log(long value, PNone novalue,
                        @Cached("createBinaryProfile()") ConditionProfile doNotFit) {
            return logDN(value, novalue, doNotFit);
        }

        @Specialization
        public double logDN(double value, @SuppressWarnings("unused") PNone novalue,
                        @Cached("createBinaryProfile()") ConditionProfile doNotFit) {
            raiseMathError(doNotFit, value <= 0);
            return Math.log(value);
        }

        @Specialization
        @TruffleBoundary
        public double logPIN(PInt value, @SuppressWarnings("unused") PNone novalue,
                        @Cached("createBinaryProfile()") ConditionProfile doNotFit) {
            BigInteger bValue = value.getValue();
            raiseMathError(doNotFit, bValue.compareTo(BigInteger.ZERO) < 0);
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
            raiseMathError(doNotFit, bValue.compareTo(BigInteger.ZERO) < 0 || base <= 0);
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
            raiseMathError(doNotFit, bValue.compareTo(BigInteger.ZERO) < 0 || bBase.compareTo(BigInteger.ZERO) <= 0);
            double logBase = countBase(bBase, divByZero);
            return logBigInteger(bValue) / logBase;
        }

        @Specialization(guards = "!isNumber(value)", limit = "1")
        public double logO(VirtualFrame frame, Object value, PNone novalue,
                        @CachedLibrary("value") PythonObjectLibrary lib) {
            return executeRecursiveLogNode(frame, lib.asJavaDouble(value), novalue);
        }

        @Specialization(guards = {"!isNumber(value)", "!isNoValue(base)"}, limit = "1")
        public double logOO(VirtualFrame frame, Object value, Object base,
                        @CachedLibrary("value") PythonObjectLibrary valueLib,
                        @CachedLibrary("base") PythonObjectLibrary baseLib) {
            return executeRecursiveLogNode(frame, valueLib.asJavaDouble(value), baseLib.asJavaDouble(base));
        }

        @Specialization(guards = {"!isNumber(base)"}, limit = "1")
        public double logLO(VirtualFrame frame, long value, Object base,
                        @CachedLibrary("base") PythonObjectLibrary lib) {
            return executeRecursiveLogNode(frame, value, lib.asJavaDouble(base));
        }

        @Specialization(guards = {"!isNumber(base)"}, limit = "1")
        public double logDO(VirtualFrame frame, double value, Object base,
                        @CachedLibrary("base") PythonObjectLibrary lib) {
            return executeRecursiveLogNode(frame, value, lib.asJavaDouble(base));
        }

        @Specialization(guards = {"!isNumber(base)"}, limit = "1")
        public double logPIO(VirtualFrame frame, PInt value, Object base,
                        @CachedLibrary("base") PythonObjectLibrary lib) {
            return executeRecursiveLogNode(frame, value, lib.asJavaDouble(base));
        }

        private void raiseMathError(ConditionProfile doNotFit, boolean con) {
            if (doNotFit.profile(con)) {
                throw raise(ValueError, ErrorMessages.MATH_DOMAIN_ERROR);
            }
        }

        public static LogNode create() {
            return MathModuleBuiltinsFactory.LogNodeFactory.create();
        }
    }

    @Builtin(name = "log1p", minNumOfPositionalArgs = 1, doc = "Return the natural logarithm of 1+x (base e).\n\nThe result is computed in a way which is accurate for x near zero.")
    @GenerateNodeFactory
    public abstract static class Log1pNode extends MathDoubleUnaryBuiltinNode {

        @Override
        public double count(double value) {
            if (value == 0 || value == Double.POSITIVE_INFINITY || Double.isNaN(value)) {
                return value;
            }
            double result = Math.log1p(value);
            checkMathDomainError(Double.isInfinite(result));
            return result;
        }
    }

    @Builtin(name = "log2", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class Log2Node extends MathDoubleUnaryBuiltinNode {
        private static final double LOG2 = Math.log(2);
        private static final BigInteger TWO = BigInteger.valueOf(2);

        @Specialization
        @TruffleBoundary
        @Override
        public double doPI(PInt value) {
            BigInteger bValue = value.getValue();
            checkMathDomainError(bValue.compareTo(BigInteger.ZERO) <= 0);
            int e = bValue.bitLength() - 1;
            if (bValue.compareTo(TWO.pow(e)) == 0) {
                return e;
            }
            // this doesn't have to be as accured as should be
            return LogNode.logBigInteger(bValue) / LOG2;
        }

        @Override
        public double count(double value) {
            checkMathDomainError(value <= 0);
            double[] frexpR = FrexpNode.frexp(value);
            double m = frexpR[0];
            int e = (int) frexpR[1];
            if (value >= 1.0) {
                return Math.log(2.0 * m) / LOG2 + (e - 1);
            } else {
                return Math.log(m) / LOG2 + e;
            }
        }
    }

    @Builtin(name = "log10", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class Log10Node extends MathDoubleUnaryBuiltinNode {

        private static final double LOG10 = Math.log(10);

        private static int getDigitCount(BigInteger number) {
            double factor = Math.log(2) / Math.log(10);
            int digitCount = (int) (factor * number.bitLength() + 1);
            if (BigInteger.TEN.pow(digitCount - 1).compareTo(number) > 0) {
                return digitCount - 1;
            }
            return digitCount;
        }

        @Specialization
        @TruffleBoundary
        @Override
        public double doPI(PInt value) {
            BigInteger bValue = value.getValue();
            checkMathDomainError(bValue.compareTo(BigInteger.ZERO) <= 0);
            int digitCount = getDigitCount(bValue) - 1;
            if (bValue.compareTo(BigInteger.TEN.pow(digitCount)) == 0) {
                return digitCount;
            }
            return LogNode.logBigInteger(bValue) / LOG10;
        }

        @Override
        public double count(double value) {
            checkMathDomainError(value <= 0);
            return Math.log10(value);
        }
    }

    @Builtin(name = "fabs", minNumOfPositionalArgs = 1)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(MathGuards.class)
    @GenerateNodeFactory
    public abstract static class FabsNode extends PythonUnaryBuiltinNode {

        @Specialization
        public double fabs(long value) {
            return Math.abs(value);
        }

        @Specialization
        public PInt fabs(PInt value) {
            BigInteger xabs = value.abs();
            return factory().createInt(xabs);
        }

        @Specialization
        public double fabs(double value) {
            return Math.abs(value);
        }

        @Specialization(guards = "!isNumber(value)", limit = "1")
        public double fabs(Object value,
                        @CachedLibrary("value") PythonObjectLibrary lib) {
            return fabs(lib.asJavaDouble(value));
        }
    }

    @Builtin(name = "pow", minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(MathGuards.class)
    @GenerateNodeFactory
    public abstract static class PowNode extends PythonBinaryBuiltinNode {

        @Specialization
        double pow(long left, long right) {
            return pow((double) left, (double) right);
        }

        @Specialization
        double pow(double left, long right) {
            return pow(left, (double) right);
        }

        @Specialization
        double pow(long left, double right) {
            return pow((double) left, right);
        }

        @Specialization
        double pow(PInt left, PInt right) {
            return pow(left.doubleValue(), right.doubleValue());
        }

        @Specialization
        double pow(long left, PInt right) {
            return pow((double) left, right.doubleValue());
        }

        @Specialization
        double pow(PInt left, long right) {
            return pow(left.doubleValue(), (double) right);
        }

        @Specialization
        double pow(double left, PInt right) {
            return pow(left, right.doubleValue());
        }

        @Specialization
        double pow(PInt left, double right) {
            return pow(left.doubleValue(), right);
        }

        @Specialization
        double pow(double left, double right) {
            double result = 0;
            if (!Double.isFinite(left) || !Double.isFinite(right)) {
                if (Double.isNaN(left)) {
                    result = right == 0 ? 1 : left;
                } else if (Double.isNaN(right)) {
                    result = left == 1 ? 1 : right;
                } else if (Double.isInfinite(left)) {
                    boolean oddRight = Double.isFinite(right) && (Math.abs(right) % 2.0) == 1;
                    if (right > 0) {
                        result = oddRight ? left : Math.abs(left);
                    } else if (right == 0) {
                        result = 1;
                    } else {
                        result = oddRight ? Math.copySign(0., left) : 0;
                    }
                } else if (Double.isInfinite(right)) {
                    if (Math.abs(left) == 1) {
                        result = 1;
                    } else if (right > 0 && Math.abs(left) > 1) {
                        result = right;
                    } else if (right < 0 && Math.abs(left) < 1) {
                        result = -right;
                        if (left == 0) {
                            throw raise(ValueError, ErrorMessages.MATH_DOMAIN_ERROR);
                        }
                    } else {
                        result = 0;
                    }
                }
            } else {
                result = Math.pow(left, right);
                if (!Double.isFinite(result)) {
                    if (Double.isNaN(result)) {
                        throw raise(ValueError, ErrorMessages.MATH_DOMAIN_ERROR);
                    } else if (Double.isInfinite(result)) {
                        if (left == 0) {
                            throw raise(ValueError, ErrorMessages.MATH_DOMAIN_ERROR);
                        } else {
                            throw raise(OverflowError, ErrorMessages.MATH_RAGE_ERROR);
                        }
                    }
                }
            }
            return result;
        }

        @Specialization(guards = {"!isNumber(left)||!isNumber(right)"}, limit = "1")
        double pow(Object left, Object right,
                        @CachedLibrary("left") PythonObjectLibrary leftLib,
                        @CachedLibrary("right") PythonObjectLibrary rightLib) {
            return pow(leftLib.asJavaDouble(left), rightLib.asJavaDouble(right));
        }
    }

    @Builtin(name = "trunc", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class TruncNode extends PythonUnaryBuiltinNode {

        @Specialization
        Object trunc(VirtualFrame frame, Object obj,
                        @Cached("create(__TRUNC__)") LookupAndCallUnaryNode callTrunc) {
            Object result = callTrunc.executeObject(frame, obj);
            if (result == PNone.NO_VALUE) {
                raise(TypeError, ErrorMessages.TYPE_DOESNT_DEFINE_METHOD, obj, "__trunc__");
            }
            return result;
        }
    }

    @Builtin(name = "atan2", minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(MathGuards.class)
    @GenerateNodeFactory
    public abstract static class Atan2Node extends PythonBinaryBuiltinNode {

        @Specialization
        double atan2(long left, long right) {
            return atan2DD(left, right);
        }

        @Specialization
        double atan2(long left, double right) {
            return atan2DD(left, right);
        }

        @Specialization
        double atan2(double left, long right) {
            return atan2DD(left, right);
        }

        @Specialization
        double atan2(PInt left, PInt right) {
            return atan2DD(left.doubleValue(), right.doubleValue());
        }

        @Specialization
        double atan2(PInt left, long right) {
            return atan2DD(left.doubleValue(), right);
        }

        @Specialization
        double atan2(PInt left, double right) {
            return atan2DD(left.doubleValue(), right);
        }

        @Specialization
        double atan2(long left, PInt right) {
            return atan2DD(left, right.doubleValue());
        }

        @Specialization
        double atan2(double left, PInt right) {
            return atan2DD(left, right.doubleValue());
        }

        @Specialization
        double atan2DD(double left, double right) {
            return Math.atan2(left, right);
        }

        @Specialization(guards = "!isNumber(left) || !isNumber(right)", limit = "1")
        double atan2(Object left, Object right,
                        @CachedLibrary("left") PythonObjectLibrary leftLib,
                        @CachedLibrary("right") PythonObjectLibrary rightLib) {
            return atan2DD(leftLib.asJavaDouble(left), rightLib.asJavaDouble(right));
        }
    }

    @Builtin(name = "degrees", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class DegreesNode extends MathDoubleUnaryBuiltinNode {
        private static final double RAD_TO_DEG = 180.0 / Math.PI;

        @Override
        public double count(double value) {
            return value * RAD_TO_DEG;
        }
    }

    @Builtin(name = "radians", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class RadiansNode extends MathDoubleUnaryBuiltinNode {
        private static final double DEG_TO_RAD = Math.PI / 180.0;

        @Override
        public double count(double value) {
            return value * DEG_TO_RAD;
        }
    }

    @Builtin(name = "hypot", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, declaresExplicitSelf = true)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    @ImportStatic(MathGuards.class)
    public abstract static class HypotNode extends PythonVarargsBuiltinNode {

        @Override
        public Object varArgExecute(VirtualFrame frame, Object self, Object[] arguments, PKeyword[] keywords) throws VarargsBuiltinDirectInvocationNotSupported {
            return execute(frame, self, arguments, keywords);
        }

        @Specialization(guards = "arguments.length == 2")
        public double hypot2(@SuppressWarnings("unused") Object self, Object[] arguments, PKeyword[] keywords,
                        @CachedLibrary(limit = "1") PythonObjectLibrary xLib,
                        @CachedLibrary(limit = "1") PythonObjectLibrary yLib) {
            if (keywords.length != 0) {
                throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.TAKES_NO_KEYWORD_ARGS, "hypot()");
            }
            double x = xLib.asJavaDouble(arguments[0]);
            double y = yLib.asJavaDouble(arguments[1]);
            return Math.hypot(x, y);
        }

        @Specialization
        public double hypotGeneric(@SuppressWarnings("unused") Object self, Object[] arguments, PKeyword[] keywords,
                        @CachedLibrary(limit = "3") PythonObjectLibrary lib) {
            if (keywords.length != 0) {
                throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.TAKES_NO_KEYWORD_ARGS, "hypot()");
            }
            double max = 0.0;
            boolean foundNan = false;
            double[] coordinates = new double[arguments.length];
            for (int i = 0; i < arguments.length; i++) {
                double x = lib.asJavaDouble(arguments[i]);
                x = Math.abs(x);
                if (Double.isNaN(x)) {
                    foundNan = true;
                }
                if (x > max) {
                    max = x;
                }
                coordinates[i] = x;
            }
            if (Double.isInfinite(max)) {
                return max;
            }
            if (foundNan) {
                return Double.NaN;
            }
            if (max == 0.0 || arguments.length <= 1) {
                return max;
            }

            double csum = 1.0;
            double oldcsum;
            double frac = 0.0;

            for (int i = 0; i < arguments.length; i++) {
                double x = coordinates[i];
                x /= max;
                x = x * x;
                oldcsum = csum;
                csum += x;
                frac += (oldcsum - csum) + x;
            }
            return max * Math.sqrt(csum - 1.0 + frac);
        }
    }

    @Builtin(name = "erf", minNumOfPositionalArgs = 1, doc = "Error function at x.")
    @GenerateNodeFactory
    public abstract static class ErfNode extends MathDoubleUnaryBuiltinNode {
        // Adapted implementation from CPython
        private static final double ERF_SERIES_CUTOFF = 1.5;
        private static final int ERF_SERIES_TERMS = 25;
        protected static final double ERFC_CONTFRAC_CUTOFF = 30.0;
        private static final int ERFC_CONTFRAC_TERMS = 50;
        private static final double SQRTPI = 1.772453850905516027298167483341145182798;

        static double m_erf_series(double x) {
            double x2, acc, fk;
            int i;

            x2 = x * x;
            acc = 0.0;
            fk = ERF_SERIES_TERMS + 0.5;
            for (i = 0; i < ERF_SERIES_TERMS; i++) {
                acc = 2.0 + x2 * acc / fk;
                fk -= 1.0;
            }

            return acc * x * Math.exp(-x2) / SQRTPI;
        }

        static double m_erfc_contfrac(double x) {
            double x2, a, da, p, p_last, q, q_last, b;
            int i;

            if (x >= ERFC_CONTFRAC_CUTOFF) {
                return 0.0;
            }

            x2 = x * x;
            a = 0.0;
            da = 0.5;
            p = 1.0;
            p_last = 0.0;
            q = da + x2;
            q_last = 1.0;
            for (i = 0; i < ERFC_CONTFRAC_TERMS; i++) {
                double temp;
                a += da;
                da += 2.0;
                b = da + x2;
                temp = p;
                p = b * p - a * p_last;
                p_last = temp;
                temp = q;
                q = b * q - a * q_last;
                q_last = temp;
            }

            return p / q * x * Math.exp(-x2) / SQRTPI;
        }

        @Override
        public double count(double x) {
            double absx, cf;

            if (Double.isNaN(x)) {
                return x;
            }
            absx = Math.abs(x);
            if (absx < ERF_SERIES_CUTOFF) {
                return m_erf_series(x);
            } else {
                cf = m_erfc_contfrac(absx);
                return x > 0.0 ? 1.0 - cf : cf - 1.0;
            }
        }
    }

    @Builtin(name = "erfc", minNumOfPositionalArgs = 1, doc = "Error function at x.")
    @GenerateNodeFactory
    public abstract static class ErfcNode extends ErfNode {
        // Adapted implementation from CPython
        @Override
        public double count(double x) {
            double absx, cf;

            if (Double.isNaN(x)) {
                return x;
            }
            absx = Math.abs(x);
            if (absx < ErfNode.ERF_SERIES_CUTOFF) {
                return 1.0 - m_erf_series(x);
            } else {
                cf = m_erfc_contfrac(absx);
                return x > 0.0 ? cf : 2.0 - cf;
            }
        }
    }

    @Builtin(name = "gamma", minNumOfPositionalArgs = 1, doc = "Gamma function at x")
    @GenerateNodeFactory
    public abstract static class GammaNode extends MathDoubleUnaryBuiltinNode {
        // Adapted implementation from CPython
        private static final int NGAMMA_INTEGRAL = 23;
        private static final int LANCZOS_N = 13;
        protected static final double LANCZOS_G = 6.024680040776729583740234375;
        private static final double LANZOS_G_MINUS_HALF = 5.524680040776729583740234375;
        @CompilationFinal(dimensions = 1) protected static final double[] LANCZOS_NUM_COEFFS = new double[]{
                        23531376880.410759688572007674451636754734846804940,
                        42919803642.649098768957899047001988850926355848959,
                        35711959237.355668049440185451547166705960488635843,
                        17921034426.037209699919755754458931112671403265390,
                        6039542586.3520280050642916443072979210699388420708,
                        1439720407.3117216736632230727949123939715485786772,
                        248874557.86205415651146038641322942321632125127801,
                        31426415.585400194380614231628318205362874684987640,
                        2876370.6289353724412254090516208496135991145378768,
                        186056.26539522349504029498971604569928220784236328,
                        8071.6720023658162106380029022722506138218516325024,
                        210.82427775157934587250973392071336271166969580291,
                        2.5066282746310002701649081771338373386264310793408
        };

        @CompilationFinal(dimensions = 1) protected static final double[] LANCZOS_DEN_COEFFS = new double[]{
                        0.0, 39916800.0, 120543840.0, 150917976.0, 105258076.0, 45995730.0,
                        13339535.0, 2637558.0, 357423.0, 32670.0, 1925.0, 66.0, 1.0};

        @CompilationFinal(dimensions = 1) protected static final double[] GAMMA_INTEGRAL = new double[]{
                        1.0, 1.0, 2.0, 6.0, 24.0, 120.0, 720.0, 5040.0, 40320.0, 362880.0,
                        3628800.0, 39916800.0, 479001600.0, 6227020800.0, 87178291200.0,
                        1307674368000.0, 20922789888000.0, 355687428096000.0,
                        6402373705728000.0, 121645100408832000.0, 2432902008176640000.0,
                        51090942171709440000.0, 1124000727777607680000.0,
        };

        static double sinpi(double x) {
            double y, r = 0;
            int n;
            /* this function should only ever be called for finite arguments */
            assert (Double.isFinite(x));
            y = Math.abs(x) % 2.0;
            n = (int) Math.round(2.0 * y);
            assert (0 <= n && n <= 4);
            switch (n) {
                case 0:
                    r = Math.sin(Math.PI * y);
                    break;
                case 1:
                    r = Math.cos(Math.PI * (y - 0.5));
                    break;
                case 2:
                    /*
                     * N.B. -sin(pi*(y-1.0)) is *not* equivalent: it would give -0.0 instead of 0.0
                     * when y == 1.0.
                     */
                    r = Math.sin(Math.PI * (1.0 - y));
                    break;
                case 3:
                    r = -Math.cos(Math.PI * (y - 1.5));
                    break;
                case 4:
                    r = Math.sin(Math.PI * (y - 2.0));
                    break;
                default:

            }
            return Math.copySign(1.0, x) * r;
        }

        static double lanczos_sum(double x) {
            double num = 0.0, den = 0.0;
            int i;
            assert (x > 0.0);
            /*
             * evaluate the rational function lanczos_sum(x). For large x, the obvious algorithm
             * risks overflow, so we instead rescale the denominator and numerator of the rational
             * function by x**(1-LANCZOS_N) and treat this as a rational function in 1/x. This also
             * reduces the error for larger x values. The choice of cutoff point (5.0 below) is
             * somewhat arbitrary; in tests, smaller cutoff values than this resulted in lower
             * accuracy.
             */
            if (x < 5.0) {
                for (i = LANCZOS_N; --i >= 0;) {
                    num = num * x + LANCZOS_NUM_COEFFS[i];
                    den = den * x + LANCZOS_DEN_COEFFS[i];
                }
            } else {
                for (i = 0; i < LANCZOS_N; i++) {
                    num = num / x + LANCZOS_NUM_COEFFS[i];
                    den = den / x + LANCZOS_DEN_COEFFS[i];
                }
            }
            assert den > 0.0 : "den cannot be zero, because LANCZOS_DEN_COEFFS are added";
            return num / den;
        }

        @Override
        public double count(double x) {
            double absx, r, y, z, sqrtpow;

            /* special cases */
            if (!Double.isFinite(x)) {
                if (Double.isNaN(x) || x > 0.0) {
                    return x; /* tgamma(nan) = nan, tgamma(inf) = inf */
                } else {
                    checkMathDomainError(false);
                }
            }
            checkMathDomainError(x == 0);

            /* integer arguments */
            if (x == Math.floor(x)) {
                checkMathDomainError(x < 0.0);
                if (x <= NGAMMA_INTEGRAL) {
                    return GAMMA_INTEGRAL[(int) x - 1];
                }
            }
            absx = Math.abs(x);

            /* tiny arguments: tgamma(x) ~ 1/x for x near 0 */
            if (absx < 1e-20) {
                r = 1.0 / x;
                checkMathRangeError(Double.isInfinite(r));
                return r;
            }

            /*
             * large arguments: assuming IEEE 754 doubles, tgamma(x) overflows for x > 200, and
             * underflows to +-0.0 for x < -200, not a negative integer.
             */
            if (absx > 200.0) {
                checkMathRangeError(x >= 0.0);
                return 0.0 / sinpi(x);
            }

            y = absx + LANZOS_G_MINUS_HALF;
            /* compute error in sum */
            if (absx > LANZOS_G_MINUS_HALF) {
                /*
                 * note: the correction can be foiled by an optimizing compiler that (incorrectly)
                 * thinks that an expression like a + b - a - b can be optimized to 0.0. This
                 * shouldn't happen in a standards-conforming compiler.
                 */
                double q = y - absx;
                z = q - LANZOS_G_MINUS_HALF;
            } else {
                double q = y - LANZOS_G_MINUS_HALF;
                z = q - absx;
            }
            z = z * LANCZOS_G / y;
            if (x < 0.0) {
                r = -Math.PI / sinpi(absx) / absx * Math.exp(y) / lanczos_sum(absx);
                r -= z * r;
                if (absx < 140.0) {
                    r /= Math.pow(y, absx - 0.5);
                } else {
                    sqrtpow = Math.pow(y, absx / 2.0 - 0.25);
                    r /= sqrtpow;
                    r /= sqrtpow;
                }
            } else {
                r = lanczos_sum(absx) / Math.exp(y);
                r += z * r;
                if (absx < 140.0) {
                    r *= Math.pow(y, absx - 0.5);
                } else {
                    sqrtpow = Math.pow(y, absx / 2.0 - 0.25);
                    r *= sqrtpow;
                    r *= sqrtpow;
                }
            }
            checkMathRangeError(Double.isInfinite(r));
            return r;
        }

    }

    @Builtin(name = "lgamma", minNumOfPositionalArgs = 1, doc = "Natural logarithm of absolute value of Gamma function at x.")
    @GenerateNodeFactory
    public abstract static class LgammaNode extends GammaNode {
        // Adapted implementation from CPython
        private static final double LOGPI = 1.144729885849400174143427351353058711647;

        @Override
        public double count(double x) {
            double r;
            double absx;

            /* special cases */
            if (!Double.isFinite(x)) {
                if (Double.isNaN(x)) {
                    return x; /* lgamma(nan) = nan */
                } else {
                    return Double.POSITIVE_INFINITY; /* lgamma(+-inf) = +inf */
                }
            }

            /* integer arguments */
            if (x == Math.floor(x) && x <= 2.0) {
                checkMathDomainError(x <= 0.0);
                return 0.0;
                /* lgamma(1) = lgamma(2) = 0.0 */
            }

            absx = Math.abs(x);
            /* tiny arguments: lgamma(x) ~ -log(fabs(x)) for small x */
            if (absx < 1e-20) {
                return -Math.log(absx);
            }
            /*
             * Lanczos' formula. We could save a fraction of a ulp in accuracy by having a second
             * set of numerator coefficients for lanczos_sum that absorbed the exp(-lanczos_g) term,
             * and throwing out the lanczos_g subtraction below; it's probably not worth it.
             */
            r = Math.log(lanczos_sum(absx)) - LANCZOS_G;
            r += (absx - 0.5) * (Math.log(absx + LANCZOS_G - 0.5) - 1);
            if (x < 0.0) {
                /* Use reflection formula to get value for negative x. */
                r = LOGPI - Math.log(Math.abs(sinpi(absx))) - Math.log(absx) - r;
            }
            checkMathRangeError(Double.isInfinite(r));

            return r;
        }

    }
}
