/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates.
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
import static com.oracle.graal.python.runtime.exception.PythonErrorType.SystemError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ZeroDivisionError;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.Arrays;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.ints.IntBuiltins;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.IteratorExhausted;
import com.oracle.graal.python.lib.PyBoolCheckNode;
import com.oracle.graal.python.lib.PyFloatAsDoubleNode;
import com.oracle.graal.python.lib.PyFloatCheckExactNode;
import com.oracle.graal.python.lib.PyIterNextNode;
import com.oracle.graal.python.lib.PyLongAsDoubleNode;
import com.oracle.graal.python.lib.PyLongAsLongAndOverflowNode;
import com.oracle.graal.python.lib.PyLongCheckExactNode;
import com.oracle.graal.python.lib.PyLongFromDoubleNode;
import com.oracle.graal.python.lib.PyNumberAddNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyNumberIndexNode;
import com.oracle.graal.python.lib.PyNumberMultiplyNode;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.builtins.TupleNodes;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.call.special.LookupSpecialMethodNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.truffle.PythonIntegerAndFloatTypes;
import com.oracle.graal.python.nodes.truffle.PythonIntegerTypes;
import com.oracle.graal.python.nodes.util.CastToJavaLongLossyNode;
import com.oracle.graal.python.nodes.util.NarrowBigIntegerNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.profiles.InlinedLoopConditionProfile;
import com.oracle.truffle.api.profiles.LoopConditionProfile;
import com.oracle.truffle.api.utilities.MathUtils;

@CoreFunctions(defineModule = "math")
public final class MathModuleBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return MathModuleBuiltinsFactory.getFactories();
    }

    public MathModuleBuiltins() {
        // Add constant values
        addBuiltinConstant("pi", Math.PI);
        addBuiltinConstant("e", Math.E);
        addBuiltinConstant("tau", 2 * Math.PI);
        addBuiltinConstant("inf", Double.POSITIVE_INFINITY);
        addBuiltinConstant("nan", Double.NaN);
    }

    static void checkMathRangeError(boolean con, Node inliningTarget, PRaiseNode raiseNode) {
        if (con) {
            throw raiseNode.raise(inliningTarget, OverflowError, ErrorMessages.MATH_RANGE_ERROR);
        }
    }

    static void checkMathDomainError(boolean con, Node inliningTarget, PRaiseNode raiseNode) {
        if (con) {
            throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.MATH_DOMAIN_ERROR);
        }
    }

    static void checkMathDomainErrorUncached(boolean con, Node raisingNode) {
        if (con) {
            throw PRaiseNode.raiseStatic(raisingNode, ValueError, ErrorMessages.MATH_DOMAIN_ERROR);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @TypeSystemReference(PythonIntegerAndFloatTypes.class)
    @ImportStatic(MathGuards.class)
    public abstract static class MathUnaryHelperNode extends Node {

        @FunctionalInterface
        interface Op {
            double compute(Node inliningTarget, double arg, PRaiseNode raiseNode);
        }

        abstract double execute(VirtualFrame frame, Node inliningTarget, Object value, Op op);

        @Specialization
        static double doL(Node inliningTarget, long value, Op op,
                        @Shared @Cached PRaiseNode raiseNode) {
            return computeAndCheckDomain(inliningTarget, value, op, raiseNode);
        }

        @Specialization
        static double doD(Node inliningTarget, double value, Op op,
                        @Shared @Cached PRaiseNode raiseNode) {
            return computeAndCheckDomain(inliningTarget, value, op, raiseNode);
        }

        @Specialization
        static double doPI(Node inliningTarget, PInt value, Op op,
                        @Shared @Cached PRaiseNode raiseNode) {
            return computeAndCheckDomain(inliningTarget, value.doubleValueWithOverflow(inliningTarget), op, raiseNode);
        }

        @Specialization(guards = "!isNumber(value)")
        static double doGeneral(VirtualFrame frame, Node inliningTarget, Object value, Op op,
                        @Cached PyFloatAsDoubleNode asDoubleNode,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            return computeAndCheckDomain(inliningTarget, asDoubleNode.execute(frame, inliningTarget, value), op, raiseNode);
        }

        private static double computeAndCheckDomain(Node inliningTarget, double arg, Op op, PRaiseNode raiseNode) {
            double res = op.compute(inliningTarget, arg, raiseNode);
            checkMathDomainError(Double.isNaN(res) && !Double.isNaN(arg), inliningTarget, raiseNode);
            return res;
        }
    }

    // math.sqrt
    @Builtin(name = "sqrt", minNumOfPositionalArgs = 1, doc = "Return the square root of x.")
    @GenerateNodeFactory
    public abstract static class SqrtNode extends PythonUnaryBuiltinNode {

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
        double doPI(PInt value) {
            // Tests require that OverflowError is raised when the value does not fit into double
            // but we don't actually need the double value, so this is called for side-effect only:
            value.doubleValueWithOverflow(this);
            BigInteger bValue = value.getValue();
            checkMathDomainErrorUncached(bValue.compareTo(BigInteger.ZERO) < 0, this);
            return sqrtBigNumber(bValue).doubleValue();
        }

        @Specialization(guards = "!isPInt(value)")
        static double doGeneric(VirtualFrame frame, Object value,
                        @Bind Node inliningTarget,
                        @Cached MathUnaryHelperNode helperNode) {
            return helperNode.execute(frame, inliningTarget, value, SqrtNode::compute);
        }

        private static double compute(Node inliningTarget, double value, PRaiseNode raiseNode) {
            checkMathDomainError(value < 0, inliningTarget, raiseNode);
            return Math.sqrt(value);
        }
    }

    @Builtin(name = "exp", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ExpNode extends PythonUnaryBuiltinNode {

        @Specialization
        static double doGeneric(VirtualFrame frame, Object value,
                        @Bind Node inliningTarget,
                        @Cached MathUnaryHelperNode helperNode) {
            return helperNode.execute(frame, inliningTarget, value, ExpNode::compute);
        }

        private static double compute(Node inliningTarget, double value, PRaiseNode raiseNode) {
            double result = Math.exp(value);
            checkMathRangeError(Double.isFinite(value) && Double.isInfinite(result), inliningTarget, raiseNode);
            return result;
        }
    }

    @Builtin(name = "expm1", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class Expm1Node extends PythonUnaryBuiltinNode {

        @Specialization
        static double doGeneric(VirtualFrame frame, Object value,
                        @Bind Node inliningTarget,
                        @Cached MathUnaryHelperNode helperNode) {
            return helperNode.execute(frame, inliningTarget, value, Expm1Node::compute);
        }

        private static double compute(Node inliningTarget, double value, PRaiseNode raiseNode) {
            double result = Math.expm1(value);
            checkMathRangeError(Double.isFinite(value) && Double.isInfinite(result), inliningTarget, raiseNode);
            return result;
        }
    }

    @Builtin(name = "ceil", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class CeilNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object ceilDouble(double value,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached PyLongFromDoubleNode pyLongFromDoubleNode) {
            return pyLongFromDoubleNode.execute(inliningTarget, Math.ceil(value));
        }

        @Fallback
        static Object ceil(VirtualFrame frame, Object value,
                        @Bind Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached("create(T___CEIL__)") LookupSpecialMethodNode lookupCeil,
                        @Cached CallUnaryMethodNode callCeil,
                        @Cached PyFloatAsDoubleNode asDoubleNode,
                        @Exclusive @Cached PyLongFromDoubleNode pyLongFromDoubleNode) {
            Object method = lookupCeil.execute(frame, getClassNode.execute(inliningTarget, value), value);
            if (method != PNone.NO_VALUE) {
                return callCeil.executeObject(frame, method, value);
            }
            double doubleValue = asDoubleNode.execute(frame, inliningTarget, value);
            return pyLongFromDoubleNode.execute(inliningTarget, Math.ceil(doubleValue));
        }
    }

    @Builtin(name = "copysign", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"magnitude", "sign"})
    @ArgumentClinic(name = "magnitude", conversion = ArgumentClinic.ClinicConversion.Double)
    @ArgumentClinic(name = "sign", conversion = ArgumentClinic.ClinicConversion.Double)
    @GenerateNodeFactory
    public abstract static class CopySignNode extends PythonBinaryClinicBuiltinNode {
        @Specialization
        public double copySign(double magnitude, double sign) {
            return Math.copySign(magnitude, sign);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return MathModuleBuiltinsClinicProviders.CopySignNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "factorial", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class FactorialNode extends PythonUnaryBuiltinNode {
        public abstract Object execute(VirtualFrame frame, long value);

        @CompilationFinal(dimensions = 1) protected static final long[] SMALL_FACTORIALS = new long[]{
                        1, 1, 2, 6, 24, 120, 720, 5040, 40320,
                        362880, 3628800, 39916800, 479001600,
                        6227020800L, 87178291200L, 1307674368000L,
                        20922789888000L, 355687428096000L, 6402373705728000L,
                        121645100408832000L, 2432902008176640000L};

        @TruffleBoundary
        private static BigInteger factorialPart(long start, long n) {
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

        @Specialization(guards = {"value < 0"})
        static long factorialNegativeInt(@SuppressWarnings("unused") int value,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.FACTORIAL_NOT_DEFINED_FOR_NEGATIVE);
        }

        @Specialization(guards = {"0 <= value", "value < SMALL_FACTORIALS.length"})
        static long factorialSmallInt(int value) {
            return SMALL_FACTORIALS[value];
        }

        @Specialization(guards = {"value >= SMALL_FACTORIALS.length"})
        static PInt factorialInt(int value,
                        @Bind PythonLanguage language) {
            return PFactory.createInt(language, factorialPart(1, value));
        }

        @Specialization(guards = {"value < 0"})
        static long factorialNegativeLong(@SuppressWarnings("unused") long value,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.FACTORIAL_NOT_DEFINED_FOR_NEGATIVE);
        }

        @Specialization(guards = {"0 <= value", "value < SMALL_FACTORIALS.length"})
        static long factorialSmallLong(long value) {
            return SMALL_FACTORIALS[(int) value];
        }

        @Specialization(guards = {"value >= SMALL_FACTORIALS.length"})
        static PInt factorialLong(long value,
                        @Bind PythonLanguage language) {
            return PFactory.createInt(language, factorialPart(1, value));
        }

        @Fallback
        static Object factorialObject(VirtualFrame frame, Object value,
                        @Bind Node inliningTarget,
                        @Cached PyLongAsLongAndOverflowNode convert,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached FactorialNode recursiveNode,
                        @Cached PRaiseNode raiseNode) {
            try {
                return recursiveNode.execute(frame, convert.execute(frame, inliningTarget, value));
            } catch (OverflowException e) {
                if (asSizeNode.executeLossy(frame, inliningTarget, value) >= 0) {
                    throw raiseNode.raise(inliningTarget, OverflowError, ErrorMessages.FACTORIAL_ARGUMENT_SHOULD_NOT_EXCEED_D, Long.MAX_VALUE);
                } else {
                    throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.FACTORIAL_NOT_DEFINED_FOR_NEGATIVE);
                }
            }
        }

    }

    @Builtin(name = "comb", minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonIntegerAndFloatTypes.class)
    @GenerateNodeFactory
    @ImportStatic(MathGuards.class)
    public abstract static class CombNode extends PythonBinaryBuiltinNode {

        @TruffleBoundary
        private BigInteger calculateComb(BigInteger n, BigInteger k) {
            if (n.signum() < 0) {
                throw PRaiseNode.raiseStatic(this, ValueError, ErrorMessages.MUST_BE_NON_NEGATIVE_INTEGER, "n");
            }
            if (k.signum() < 0) {
                throw PRaiseNode.raiseStatic(this, ValueError, ErrorMessages.MUST_BE_NON_NEGATIVE_INTEGER, "k");
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
        PInt comb(long n, long k,
                        @Bind PythonLanguage language) {
            return PFactory.createInt(language, calculateComb(PInt.longToBigInteger(n), PInt.longToBigInteger(k)));
        }

        @Specialization
        PInt comb(long n, PInt k,
                        @Bind PythonLanguage language) {
            return PFactory.createInt(language, calculateComb(PInt.longToBigInteger(n), k.getValue()));
        }

        @Specialization
        PInt comb(PInt n, long k,
                        @Bind PythonLanguage language) {
            return PFactory.createInt(language, calculateComb(n.getValue(), PInt.longToBigInteger(k)));
        }

        @Specialization
        PInt comb(PInt n, PInt k,
                        @Bind PythonLanguage language) {
            return PFactory.createInt(language, calculateComb(n.getValue(), k.getValue()));
        }

        @Specialization
        static Object comb(VirtualFrame frame, Object n, Object k,
                        @Bind Node inliningTarget,
                        @Cached PyNumberIndexNode indexNode,
                        @Cached CombNode recursiveNode) {
            Object nValue = indexNode.execute(frame, inliningTarget, n);
            Object kValue = indexNode.execute(frame, inliningTarget, k);
            return recursiveNode.execute(frame, nValue, kValue);
        }

    }

    @Builtin(name = "perm", minNumOfPositionalArgs = 1, parameterNames = {"n", "k"})
    @TypeSystemReference(PythonIntegerAndFloatTypes.class)
    @GenerateNodeFactory
    @ImportStatic(MathGuards.class)
    public abstract static class PermNode extends PythonBinaryBuiltinNode {

        @TruffleBoundary
        private BigInteger calculatePerm(BigInteger n, BigInteger k) {
            if (n.signum() < 0) {
                throw PRaiseNode.raiseStatic(this, ValueError, ErrorMessages.MUST_BE_NON_NEGATIVE_INTEGER, "n");
            }
            if (k.signum() < 0) {
                throw PRaiseNode.raiseStatic(this, ValueError, ErrorMessages.MUST_BE_NON_NEGATIVE_INTEGER, "k");
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
        PInt perm(long n, long k,
                        @Bind PythonLanguage language) {
            return PFactory.createInt(language, calculatePerm(PInt.longToBigInteger(n), PInt.longToBigInteger(k)));
        }

        @Specialization
        PInt perm(long n, PInt k,
                        @Bind PythonLanguage language) {
            return PFactory.createInt(language, calculatePerm(PInt.longToBigInteger(n), k.getValue()));
        }

        @Specialization
        PInt perm(PInt n, long k,
                        @Bind PythonLanguage language) {
            return PFactory.createInt(language, calculatePerm(n.getValue(), PInt.longToBigInteger(k)));
        }

        @Specialization
        PInt perm(PInt n, PInt k,
                        @Bind PythonLanguage language) {
            return PFactory.createInt(language, calculatePerm(n.getValue(), k.getValue()));
        }

        @Specialization
        Object perm(VirtualFrame frame, Object n, @SuppressWarnings("unused") PNone k,
                        @Cached FactorialNode factorialNode) {
            return factorialNode.execute(frame, n);
        }

        @Specialization(guards = "!isPNone(k)")
        static Object perm(VirtualFrame frame, Object n, Object k,
                        @Bind Node inliningTarget,
                        @Cached PyNumberIndexNode indexNode,
                        @Cached PermNode recursiveNode) {
            Object nValue = indexNode.execute(frame, inliningTarget, n);
            Object kValue = indexNode.execute(frame, inliningTarget, k);
            return recursiveNode.execute(frame, nValue, kValue);
        }

    }

    @Builtin(name = "floor", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class FloorNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object floorDouble(double value,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached PyLongFromDoubleNode pyLongFromDoubleNode) {
            return pyLongFromDoubleNode.execute(inliningTarget, Math.floor(value));
        }

        @Fallback
        static Object floor(VirtualFrame frame, Object value,
                        @Bind Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached("create(T___FLOOR__)") LookupSpecialMethodNode lookupFloor,
                        @Cached CallUnaryMethodNode callFloor,
                        @Cached PyFloatAsDoubleNode asDoubleNode,
                        @Exclusive @Cached PyLongFromDoubleNode pyLongFromDoubleNode) {
            Object method = lookupFloor.execute(frame, getClassNode.execute(inliningTarget, value), value);
            if (method != PNone.NO_VALUE) {
                return callFloor.executeObject(frame, method, value);
            }
            double doubleValue = asDoubleNode.execute(frame, inliningTarget, value);
            return pyLongFromDoubleNode.execute(inliningTarget, Math.floor(doubleValue));
        }
    }

    @Builtin(name = "fmod", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"left", "right"})
    @ArgumentClinic(name = "left", conversion = ArgumentClinic.ClinicConversion.Double)
    @ArgumentClinic(name = "right", conversion = ArgumentClinic.ClinicConversion.Double)
    @GenerateNodeFactory
    public abstract static class FmodNode extends PythonBinaryClinicBuiltinNode {

        @Specialization
        static double fmodDD(double left, double right,
                        @Bind Node inliningTarget,
                        @Cached InlinedConditionProfile infProfile,
                        @Cached InlinedConditionProfile zeroProfile,
                        @Cached PRaiseNode raiseNode) {
            raiseMathDomainError(inliningTarget, Double.isInfinite(left), infProfile, raiseNode);
            raiseMathDomainError(inliningTarget, right == 0, zeroProfile, raiseNode);
            return left % right;
        }

        static void raiseMathDomainError(Node inliningTarget, boolean con, InlinedConditionProfile profile, PRaiseNode raiseNode) {
            if (profile.profile(inliningTarget, con)) {
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.MATH_DOMAIN_ERROR);
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return MathModuleBuiltinsClinicProviders.FmodNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "remainder", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"x", "y"})
    @ArgumentClinic(name = "x", conversion = ArgumentClinic.ClinicConversion.Double)
    @ArgumentClinic(name = "y", conversion = ArgumentClinic.ClinicConversion.Double)
    @GenerateNodeFactory
    public abstract static class RemainderNode extends PythonBinaryClinicBuiltinNode {

        @Specialization
        static double remainderDD(double x, double y,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            if (Double.isFinite(x) && Double.isFinite(y)) {
                if (y == 0.0) {
                    throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.MATH_DOMAIN_ERROR);
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
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.MATH_DOMAIN_ERROR);
            }
            return x;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return MathModuleBuiltinsClinicProviders.RemainderNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "frexp", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"value"})
    @ArgumentClinic(name = "value", conversion = ArgumentClinic.ClinicConversion.Double)
    @GenerateNodeFactory
    public abstract static class FrexpNode extends PythonUnaryClinicBuiltinNode {
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
        static PTuple frexpD(double value,
                        @Bind PythonLanguage language) {
            Object[] content = new Object[2];
            double[] primContent = frexp(value);
            content[0] = primContent[0];
            content[1] = (int) primContent[1];
            return PFactory.createTuple(language, content);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return MathModuleBuiltinsClinicProviders.FrexpNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "isnan", minNumOfPositionalArgs = 1)
    @TypeSystemReference(PythonIntegerAndFloatTypes.class)
    @ImportStatic(MathGuards.class)
    @GenerateNodeFactory
    public abstract static class IsNanNode extends PythonUnaryBuiltinNode {
        @Specialization
        public static boolean isNan(@SuppressWarnings("unused") long value) {
            return false;
        }

        @Specialization
        public static boolean isNan(@SuppressWarnings("unused") PInt value) {
            return false;
        }

        @Specialization
        public static boolean isNan(double value) {
            return Double.isNaN(value);
        }

        @Specialization(guards = "!isNumber(value)")
        public static boolean isinf(VirtualFrame frame, Object value,
                        @Bind Node inliningTarget,
                        @Cached PyFloatAsDoubleNode asDoubleNode) {
            return isNan(asDoubleNode.execute(frame, inliningTarget, value));
        }
    }

    @Builtin(name = "isclose", minNumOfPositionalArgs = 2, parameterNames = {"a", "b"}, keywordOnlyNames = {"rel_tol", "abs_tol"})
    @ArgumentClinic(name = "a", conversion = ArgumentClinic.ClinicConversion.Double)
    @ArgumentClinic(name = "b", conversion = ArgumentClinic.ClinicConversion.Double)
    @ArgumentClinic(name = "rel_tol", conversion = ArgumentClinic.ClinicConversion.Double, defaultValue = "1e-09")
    @ArgumentClinic(name = "abs_tol", conversion = ArgumentClinic.ClinicConversion.Double, defaultValue = "0.0")
    @GenerateNodeFactory
    public abstract static class IsCloseNode extends PythonClinicBuiltinNode {
        @Specialization
        static boolean isCloseDouble(double a, double b, double rel_tol, double abs_tol,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            double diff;

            if (rel_tol < 0.0 || abs_tol < 0.0) {
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.TOLERANCE_MUST_NON_NEGATIVE);
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

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return MathModuleBuiltinsClinicProviders.IsCloseNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "ldexp", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"x", "i"})
    @ArgumentClinic(name = "x", conversion = ArgumentClinic.ClinicConversion.Double)
    @TypeSystemReference(PythonIntegerAndFloatTypes.class)
    @GenerateNodeFactory
    public abstract static class LdexpNode extends PythonBinaryClinicBuiltinNode {

        private static int makeInt(long x) {
            long result = x;
            if (x < Integer.MIN_VALUE) {
                result = Integer.MIN_VALUE;
            } else if (x > Integer.MAX_VALUE) {
                result = Integer.MAX_VALUE;
            }
            return (int) result;
        }

        private static double exceptInfinity(Node inliningTarget, double result, double arg, PRaiseNode raiseNode) {
            if (Double.isInfinite(result) && !Double.isInfinite(arg)) {
                throw raiseNode.raise(inliningTarget, OverflowError, ErrorMessages.MATH_RANGE_ERROR);
            } else {
                return result;
            }
        }

        @Specialization
        static double ldexp(double mantissa, long exp,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            return exceptInfinity(inliningTarget, Math.scalb(mantissa, makeInt(exp)), mantissa, raiseNode);
        }

        @Specialization(guards = "!isInteger(exp)")
        static double ldexp(VirtualFrame frame, double mantissa, Object exp,
                        @Bind Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PyNumberIndexNode indexNode,
                        @Cached CastToJavaLongLossyNode cast,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            if (isSubtypeNode.execute(getClassNode.execute(inliningTarget, exp), PythonBuiltinClassType.PInt)) {
                long longExp = cast.execute(inliningTarget, indexNode.execute(frame, inliningTarget, exp));
                return ldexp(mantissa, longExp, inliningTarget, raiseNode);
            } else {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.EXPECTED_INT_MESSAGE);
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return MathModuleBuiltinsClinicProviders.LdexpNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "modf", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"x"})
    @ArgumentClinic(name = "x", conversion = ArgumentClinic.ClinicConversion.Double)
    @GenerateNodeFactory
    public abstract static class ModfNode extends PythonUnaryClinicBuiltinNode {
        @Specialization
        static PTuple modfD(double value,
                        @Bind PythonLanguage language) {
            if (!Double.isFinite(value)) {
                if (Double.isInfinite(value)) {
                    return PFactory.createTuple(language, new Object[]{Math.copySign(0., value), value});
                } else if (Double.isNaN(value)) {
                    return PFactory.createTuple(language, new Object[]{value, value});
                }
            }
            double fraction = value % 1;
            double integral = value - fraction;
            return PFactory.createTuple(language, new Object[]{fraction, integral});
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return MathModuleBuiltinsClinicProviders.ModfNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "fsum", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class FsumNode extends PythonUnaryBuiltinNode {

        @Specialization
        static double doIt(VirtualFrame frame, Object iterable,
                        @Bind Node inliningTarget,
                        @Cached PyObjectGetIter getIter,
                        @Cached PyIterNextNode nextNode,
                        @Cached PyFloatAsDoubleNode asDoubleNode,
                        @Cached InlinedLoopConditionProfile loopProfile,
                        @Cached PRaiseNode raiseNode) {
            /*
             * This implementation is taken from CPython. The performance is not good. Should be
             * faster. It can be easily replace with much simpler code based on BigDecimal:
             *
             * BigDecimal result = BigDecimal.ZERO;
             *
             * in cycle just: result = result.add(BigDecimal.valueof(x); ... The current
             * implementation is little bit faster. The testFSum in test_math.py takes in different
             * implementations: CPython ~0.6s CurrentImpl: ~14.3s Using BigDecimal: ~15.1
             */
            Object iterator = getIter.execute(frame, inliningTarget, iterable);
            double x, y, t, hi, lo = 0, yr, inf_sum = 0, special_sum = 0, sum;
            double xsave;
            int i, j, n = 0, arayLength = 32;
            double[] p = new double[arayLength];
            boolean exhausted = false;
            while (loopProfile.profile(inliningTarget, !exhausted)) {
                try {
                    Object next = nextNode.execute(frame, inliningTarget, iterator);
                    x = asDoubleNode.execute(frame, inliningTarget, next);
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
                             * a nonfinite x could arise either as a result of intermediate
                             * overflow, or as a result of a nan or inf in the summands
                             */
                            if (Double.isFinite(xsave)) {
                                throw raiseNode.raise(inliningTarget, OverflowError, ErrorMessages.INTERMEDIATE_OVERFLOW_IN, "fsum");
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
                } catch (IteratorExhausted e) {
                    exhausted = true;
                }
            }

            if (special_sum != 0.0) {
                if (Double.isNaN(inf_sum)) {
                    throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.NEG_INF_PLUS_INF_IN);
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

    @Builtin(name = "gcd", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, declaresExplicitSelf = true)
    @GenerateNodeFactory
    public abstract static class GcdNode extends PythonVarargsBuiltinNode {

        @Specialization(guards = {"args.length > 1", "keywords.length == 0"})
        public static Object gcd(VirtualFrame frame, @SuppressWarnings("unused") Object self, Object[] args, @SuppressWarnings("unused") PKeyword[] keywords,
                        @Cached Gcd2Node gdcNode,
                        @Cached LoopConditionProfile profile) {
            Object res = args[0];
            profile.profileCounted(args.length);
            for (int i = 1; profile.inject(i < args.length); i++) {
                res = gdcNode.execute(frame, res, args[i]);
            }
            return res;
        }

        @Specialization(guards = {"args.length == 1", "keywords.length == 0"})
        public static Object gcdOne(VirtualFrame frame, @SuppressWarnings("unused") Object self, Object[] args, @SuppressWarnings("unused") PKeyword[] keywords,
                        @Bind Node inliningTarget,
                        @Cached PyNumberIndexNode indexNode,
                        @Cached BuiltinFunctions.AbsNode absNode) {
            return indexNode.execute(frame, inliningTarget, absNode.execute(frame, args[0]));
        }

        @Specialization(guards = {"args.length == 0", "keywords.length == 0"})
        @SuppressWarnings("unused")
        public static int gcdEmpty(Object self, Object[] args, PKeyword[] keywords) {
            return 0;
        }

        @Specialization(guards = "keywords.length != 0")
        @SuppressWarnings("unused")
        public int gcdKeywords(Object self, Object[] args, PKeyword[] keywords) {
            throw PRaiseNode.raiseStatic(this, PythonBuiltinClassType.TypeError, ErrorMessages.S_TAKES_NO_KEYWORD_ARGS, "gcd()");
        }
    }

    @TypeSystemReference(PythonIntegerAndFloatTypes.class)
    @ImportStatic(MathGuards.class)
    public abstract static class Gcd2Node extends Node {

        protected final boolean isRecursive;

        public Gcd2Node(boolean isRecursive) {
            this.isRecursive = isRecursive;
        }

        abstract Object execute(VirtualFrame frame, Object a, Object b);

        private static long count(long a, long b) {
            if (b == 0) {
                return a;
            }
            return count(b, a % b);
        }

        @Specialization
        static long gcd(long x, long y) {
            return Math.abs(count(x, y));
        }

        @Specialization
        static PInt gcd(long x, PInt y,
                        @Bind PythonLanguage language) {
            return PFactory.createInt(language, op(PInt.longToBigInteger(x), y.getValue()));
        }

        @Specialization
        static PInt gcd(PInt x, long y,
                        @Bind PythonLanguage language) {
            return PFactory.createInt(language, op(x.getValue(), PInt.longToBigInteger(y)));
        }

        @TruffleBoundary
        private static BigInteger op(BigInteger x, BigInteger y) {
            return x.gcd(y);
        }

        @Specialization
        PInt gcd(PInt x, PInt y,
                        @Bind PythonLanguage language) {
            return PFactory.createInt(language, op(x.getValue(), y.getValue()));
        }

        @Specialization
        static int gcd(@SuppressWarnings("unused") double x, @SuppressWarnings("unused") double y,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER, "float");
        }

        @Specialization
        static int gcd(@SuppressWarnings("unused") long x, @SuppressWarnings("unused") double y,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER, "float");
        }

        @Specialization
        static int gcd(@SuppressWarnings("unused") double x, @SuppressWarnings("unused") long y,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER, "float");
        }

        @Specialization
        static int gcd(@SuppressWarnings("unused") double x, @SuppressWarnings("unused") PInt y,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER, "float");
        }

        @Specialization(guards = "!isRecursive")
        static int gcd(@SuppressWarnings("unused") PInt x, @SuppressWarnings("unused") double y,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER, "float");
        }

        @Specialization(guards = {"!isRecursive", "!isNumber(x) || !isNumber(y)"})
        static Object gcd(VirtualFrame frame, Object x, Object y,
                        @Bind Node inliningTarget,
                        @Cached PyNumberIndexNode indexNode,
                        @Cached("create(true)") Gcd2Node recursiveNode) {
            Object xValue = indexNode.execute(frame, inliningTarget, x);
            Object yValue = indexNode.execute(frame, inliningTarget, y);
            return recursiveNode.execute(frame, xValue, yValue);
        }

        @Specialization
        Object gcdNative(@SuppressWarnings("unused") PythonAbstractNativeObject a, @SuppressWarnings("unused") Object b) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw PRaiseNode.raiseStatic(this, SystemError, ErrorMessages.GCD_FOR_NATIVE_NOT_SUPPORTED);
        }

        @NeverDefault
        public static Gcd2Node create() {
            return MathModuleBuiltinsFactory.Gcd2NodeGen.create(false);
        }

        public static Gcd2Node create(boolean isRecursive) {
            return MathModuleBuiltinsFactory.Gcd2NodeGen.create(isRecursive);
        }
    }

    @Builtin(name = "lcm", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, declaresExplicitSelf = true)
    @GenerateNodeFactory
    public abstract static class LcmNode extends PythonVarargsBuiltinNode {

        @Specialization(guards = {"args.length > 1", "keywords.length == 0"})
        static Object gcd(VirtualFrame frame, @SuppressWarnings("unused") Object self, Object[] args, @SuppressWarnings("unused") PKeyword[] keywords,
                        @Bind Node inliningTarget,
                        @Cached LoopConditionProfile profile,
                        @Cached IsZeroNode isZeroNode,
                        @Shared @Cached PyNumberIndexNode indexNode,
                        @Cached Gcd2Node gcdNode,
                        @Cached IntBuiltins.FloorDivNode floorDivNode,
                        @Cached IntBuiltins.MulNode mulNode,
                        @Shared @Cached BuiltinFunctions.AbsNode absNode) {
            Object a = indexNode.execute(frame, inliningTarget, args[0]);
            profile.profileCounted(args.length);
            for (int i = 1; profile.inject(i < args.length); i++) {
                Object b = indexNode.execute(frame, inliningTarget, args[i]);
                if (isZeroNode.execute(inliningTarget, a)) {
                    continue;
                }
                Object g = gcdNode.execute(frame, a, b);
                Object f = floorDivNode.execute(frame, a, g);
                Object m = mulNode.execute(frame, f, b);
                a = absNode.execute(frame, m);
            }
            return a;
        }

        @Specialization(guards = {"args.length == 1", "keywords.length == 0"})
        public static Object gcdOne(VirtualFrame frame, @SuppressWarnings("unused") Object self, Object[] args, @SuppressWarnings("unused") PKeyword[] keywords,
                        @Bind Node inliningTarget,
                        @Shared @Cached PyNumberIndexNode indexNode,
                        @Shared @Cached BuiltinFunctions.AbsNode absNode) {
            return indexNode.execute(frame, inliningTarget, absNode.execute(frame, args[0]));
        }

        @Specialization(guards = {"args.length == 0", "keywords.length == 0"})
        @SuppressWarnings("unused")
        public static int gcdEmpty(Object self, Object[] args, PKeyword[] keywords) {
            return 1;
        }

        @Specialization(guards = "keywords.length != 0")
        @SuppressWarnings("unused")
        public int gcdKeywords(Object self, Object[] args, PKeyword[] keywords) {
            throw PRaiseNode.raiseStatic(this, PythonBuiltinClassType.TypeError, ErrorMessages.S_TAKES_NO_KEYWORD_ARGS, "gcd()");
        }

        // Fast-path from CPython uses identity comparison to 0 as best effort check
        @TypeSystemReference(PythonIntegerTypes.class)
        @GenerateInline
        @GenerateCached(false)
        abstract static class IsZeroNode extends Node {
            abstract boolean execute(Node inliningTarget, Object value);

            @Specialization(guards = "a == 0")
            static boolean isLongZero(long a) {
                return true;
            }

            @Specialization(guards = "a != 0")
            static boolean isLongNonZero(long a) {
                return false;
            }

            @Specialization(guards = "i.isZero()")
            static boolean isPIntZero(PInt i) {
                return true;
            }

            @Specialization(guards = "!i.isZero()")
            static boolean isPIntNonZero(PInt i) {
                return false;
            }

            @Fallback
            static boolean others(Object o) {
                return false;
            }
        }
    }

    @Builtin(name = "nextafter", minNumOfPositionalArgs = 2, parameterNames = {"start", "direction"}, keywordOnlyNames = {"steps"})
    @ArgumentClinic(name = "start", conversion = ArgumentClinic.ClinicConversion.Double)
    @ArgumentClinic(name = "direction", conversion = ArgumentClinic.ClinicConversion.Double)
    @ArgumentClinic(name = "steps", defaultValue = "PNone.NONE", useDefaultForNone = true)
    @TypeSystemReference(PythonIntegerAndFloatTypes.class)
    @GenerateNodeFactory
    @ImportStatic(MathGuards.class)
    public abstract static class NextAfterNode extends PythonTernaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return MathModuleBuiltinsClinicProviders.NextAfterNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static double fastpath(double start, double direction, PNone steps) {
            return Math.nextAfter(start, direction);
        }

        @Specialization(guards = "!isNone(stepsArg)")
        double nextAfter(VirtualFrame frame, double x, double y, Object stepsArg,
                        @Bind Node inliningTarget,
                        @Cached PyNumberIndexNode pyNumberIndexNode,
                        @Cached PyLongAsLongAndOverflowNode asLongAndOverflowNode,
                        @Cached PRaiseNode raiseNode) {
            Object steps = pyNumberIndexNode.execute(frame, inliningTarget, stepsArg);

            // Conveniently, uint64_t and double have the same number of bits
            // on all the platforms we care about.
            // So if an overflow occurs, we can just use UINT64_MAX.
            long usteps;
            try {
                usteps = asLongAndOverflowNode.execute(frame, inliningTarget, steps);
            } catch (OverflowException e) {
                // This branch includes the case where an error occurred, since
                // (unsigned long long)(-1) = ULLONG_MAX >= UINT64_MAX. Note that
                // usteps can be strictly larger than UINT64_MAX on a machine
                // where unsigned long long has width > 64 bits.
                usteps = Long.MAX_VALUE;
            }
            if (usteps < 0) {
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.STEPS_MUST_BE_A_NON_NEGATIVE_INTEGER);
            }

            if (usteps == 0) {
                return x;
            }
            if (Double.isNaN(x)) {
                return x;
            }
            if (Double.isNaN(y)) {
                return y;
            }

            long uxi = Double.doubleToRawLongBits(x);
            long uyi = Double.doubleToRawLongBits(y);
            if (uxi == uyi) {
                return x;
            }

            long sign_bit = 1L << 63;

            long ax = uxi & ~sign_bit;
            long ay = uyi & ~sign_bit;

            // opposite signs
            if (((uxi ^ uyi) & sign_bit) != 0) {
                // NOTE: ax + ay can never overflow, because their most significant bit
                // ain't set.
                if (add(ax, ay) <= usteps) {
                    return y;
                    // This comparison has to use <, because <= would get +0.0 vs -0.0
                    // wrong.
                } else if (ax < usteps) {
                    long result = (uyi & sign_bit) | (usteps - ax);
                    return Double.longBitsToDouble(result);
                } else {
                    uxi = subtract(uxi, usteps);
                    return Double.longBitsToDouble(uxi);
                }
                // same sign
            } else if (ax > ay) {
                if (ax - ay >= usteps) {
                    uxi = subtract(uxi, usteps);
                    return Double.longBitsToDouble(uxi);
                } else {
                    return Double.longBitsToDouble(uyi);
                }
            } else {
                if (subtract(ay, ax) >= usteps) {
                    uxi = add(uxi, usteps);
                    return Double.longBitsToDouble(uxi);
                } else {
                    return Double.longBitsToDouble(uyi);
                }
            }
        }

        @TruffleBoundary
        private static long add(long a, long b) {
            try {
                return Math.addExact(a, b);
            } catch (ArithmeticException e) {
                return a > 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
            }
        }

        @TruffleBoundary
        private static long subtract(long a, long b) {
            try {
                return Math.subtractExact(a, b);
            } catch (ArithmeticException e) {
                return a > 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
            }
        }
    }

    @Builtin(name = "ulp", minNumOfPositionalArgs = 1, parameterNames = {"x"})
    @ArgumentClinic(name = "x", conversion = ArgumentClinic.ClinicConversion.Double)
    @TypeSystemReference(PythonIntegerAndFloatTypes.class)
    @GenerateNodeFactory
    @ImportStatic(MathGuards.class)
    public abstract static class UlpNode extends PythonUnaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return MathModuleBuiltinsClinicProviders.UlpNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static double ulp(double x) {
            if (Double.isNaN(x)) {
                return x;
            }
            x = Math.abs(x);
            if (Double.isInfinite(x)) {
                return x;
            }
            double x2 = Math.nextAfter(x, Double.POSITIVE_INFINITY);
            if (Double.isInfinite(x2)) {
                x2 = Math.nextAfter(x, Double.NEGATIVE_INFINITY);
                return x - x2;
            }
            return x2 - x;
        }
    }

    @Builtin(name = "acos", minNumOfPositionalArgs = 1, doc = "Return the arc cosine (measured in radians) of x.")
    @GenerateNodeFactory
    public abstract static class AcosNode extends PythonUnaryBuiltinNode {

        @Specialization
        static double doGeneric(VirtualFrame frame, Object value,
                        @Bind Node inliningTarget,
                        @Cached MathUnaryHelperNode helperNode) {
            return helperNode.execute(frame, inliningTarget, value, AcosNode::compute);
        }

        private static double compute(Node inliningTarget, double value, PRaiseNode raiseNode) {
            checkMathDomainError(Double.isInfinite(value) || -1 > value || value > 1, inliningTarget, raiseNode);
            return Math.acos(value);
        }
    }

    @Builtin(name = "acosh", minNumOfPositionalArgs = 1, doc = "Return the inverse hyperbolic cosine of x.")
    @GenerateNodeFactory
    public abstract static class AcoshNode extends PythonUnaryBuiltinNode {

        private static final double LN_2 = 6.93147180559945286227e-01;

        @Specialization
        @TruffleBoundary
        double doPI(PInt value) {
            BigInteger bValue = value.getValue();
            checkMathDomainErrorUncached(bValue.compareTo(BigInteger.ONE) < 0, this);

            if (bValue.bitLength() >= 28) {
                return Math.log(bValue.doubleValue()) + LN_2;
            }

            BigDecimal sqrt = SqrtNode.sqrtBigNumber(bValue.multiply(bValue).subtract(BigInteger.ONE));
            BigDecimal bd = new BigDecimal(bValue);
            return Math.log(bd.add(sqrt).doubleValue());
        }

        @Specialization(guards = "!isPInt(value)")
        static double doGeneric(VirtualFrame frame, Object value,
                        @Bind Node inliningTarget,
                        @Cached MathUnaryHelperNode helperNode) {
            return helperNode.execute(frame, inliningTarget, value, AcoshNode::compute);
        }

        private static double compute(Node inliningTarget, double value, PRaiseNode raiseNode) {
            checkMathDomainError(value < 1, inliningTarget, raiseNode);
            return MathUtils.acosh(value);
        }
    }

    @Builtin(name = "asin", minNumOfPositionalArgs = 1, doc = "Return the arc sine (measured in radians) of x.")
    @GenerateNodeFactory
    public abstract static class AsinNode extends PythonUnaryBuiltinNode {

        @Specialization
        static double doGeneric(VirtualFrame frame, Object value,
                        @Bind Node inliningTarget,
                        @Cached MathUnaryHelperNode helperNode) {
            return helperNode.execute(frame, inliningTarget, value, AsinNode::compute);
        }

        private static double compute(Node inliningTarget, double value, PRaiseNode raiseNode) {
            checkMathDomainError(value < -1 || value > 1, inliningTarget, raiseNode);
            return Math.asin(value);
        }
    }

    @Builtin(name = "cos", minNumOfPositionalArgs = 1, doc = "Return the cosine of x (measured in radians).")
    @GenerateNodeFactory
    public abstract static class CosNode extends PythonUnaryBuiltinNode {

        @Specialization
        static double doGeneric(VirtualFrame frame, Object value,
                        @Bind Node inliningTarget,
                        @Cached MathUnaryHelperNode helperNode) {
            return helperNode.execute(frame, inliningTarget, value, CosNode::compute);
        }

        private static double compute(Node inliningTarget, double value, PRaiseNode raiseNode) {
            return Math.cos(value);
        }
    }

    @Builtin(name = "cosh", minNumOfPositionalArgs = 1, doc = "Return the hyperbolic cosine of x.")
    @GenerateNodeFactory
    public abstract static class CoshNode extends PythonUnaryBuiltinNode {

        @Specialization
        static double doGeneric(VirtualFrame frame, Object value,
                        @Bind Node inliningTarget,
                        @Cached MathUnaryHelperNode helperNode) {
            return helperNode.execute(frame, inliningTarget, value, CoshNode::compute);
        }

        private static double compute(Node inliningTarget, double value, PRaiseNode raiseNode) {
            double result = Math.cosh(value);
            checkMathRangeError(Double.isInfinite(result) && Double.isFinite(value), inliningTarget, raiseNode);
            return result;
        }
    }

    @Builtin(name = "sin", minNumOfPositionalArgs = 1, doc = "Return the sine of x (measured in radians).")
    @GenerateNodeFactory
    public abstract static class SinNode extends PythonUnaryBuiltinNode {

        @Specialization
        static double doGeneric(VirtualFrame frame, Object value,
                        @Bind Node inliningTarget,
                        @Cached MathUnaryHelperNode helperNode) {
            return helperNode.execute(frame, inliningTarget, value, SinNode::compute);
        }

        private static double compute(Node inliningTarget, double value, PRaiseNode raiseNode) {
            return Math.sin(value);
        }
    }

    @Builtin(name = "sinh", minNumOfPositionalArgs = 1, doc = "Return the hyperbolic sine of x.")
    @GenerateNodeFactory
    public abstract static class SinhNode extends PythonUnaryBuiltinNode {

        @Specialization
        static double doGeneric(VirtualFrame frame, Object value,
                        @Bind Node inliningTarget,
                        @Cached MathUnaryHelperNode helperNode) {
            return helperNode.execute(frame, inliningTarget, value, SinhNode::compute);
        }

        private static double compute(Node inliningTarget, double value, PRaiseNode raiseNode) {
            double result = Math.sinh(value);
            checkMathRangeError(Double.isInfinite(result) && Double.isFinite(value), inliningTarget, raiseNode);
            return result;
        }
    }

    @Builtin(name = "tan", minNumOfPositionalArgs = 1, doc = "Return the tangent of x (measured in radians).")
    @GenerateNodeFactory
    public abstract static class TanNode extends PythonUnaryBuiltinNode {

        @Specialization
        static double doGeneric(VirtualFrame frame, Object value,
                        @Bind Node inliningTarget,
                        @Cached MathUnaryHelperNode helperNode) {
            return helperNode.execute(frame, inliningTarget, value, TanNode::compute);
        }

        private static double compute(Node inliningTarget, double value, PRaiseNode raiseNode) {
            return Math.tan(value);
        }
    }

    @Builtin(name = "tanh", minNumOfPositionalArgs = 1, doc = "Return the hyperbolic tangent of x.")
    @GenerateNodeFactory
    public abstract static class TanhNode extends PythonUnaryBuiltinNode {

        @Specialization
        static double doGeneric(VirtualFrame frame, Object value,
                        @Bind Node inliningTarget,
                        @Cached MathUnaryHelperNode helperNode) {
            return helperNode.execute(frame, inliningTarget, value, TanhNode::compute);
        }

        private static double compute(Node inliningTarget, double value, PRaiseNode raiseNode) {
            return Math.tanh(value);
        }
    }

    @Builtin(name = "atan", minNumOfPositionalArgs = 1, doc = "Return the arc tangent (measured in radians) of x.")
    @GenerateNodeFactory
    public abstract static class AtanNode extends PythonUnaryBuiltinNode {

        @Specialization
        static double doGeneric(VirtualFrame frame, Object value,
                        @Bind Node inliningTarget,
                        @Cached MathUnaryHelperNode helperNode) {
            return helperNode.execute(frame, inliningTarget, value, AtanNode::compute);
        }

        private static double compute(Node inliningTarget, double value, PRaiseNode raiseNode) {
            return Math.atan(value);
        }
    }

    @Builtin(name = "atanh", minNumOfPositionalArgs = 1, doc = "Return the inverse hyperbolic tangent of x.")
    @GenerateNodeFactory
    public abstract static class AtanhNode extends PythonUnaryBuiltinNode {

        @Specialization
        static double doGeneric(VirtualFrame frame, Object value,
                        @Bind Node inliningTarget,
                        @Cached MathUnaryHelperNode helperNode) {
            return helperNode.execute(frame, inliningTarget, value, AtanhNode::compute);
        }

        private static double compute(Node inliningTarget, double value, PRaiseNode raiseNode) {
            double abs = Math.abs(value);
            checkMathDomainError(abs >= 1.0, inliningTarget, raiseNode);
            return MathUtils.atanh(value);
        }
    }

    @Builtin(name = "asinh", minNumOfPositionalArgs = 1, doc = "Return the inverse hyperbolic sine of x.")
    @TypeSystemReference(PythonIntegerAndFloatTypes.class)
    @ImportStatic(MathGuards.class)
    @GenerateNodeFactory
    public abstract static class AsinhNode extends PythonUnaryBuiltinNode {

        @Specialization
        static double doGeneric(VirtualFrame frame, Object value,
                        @Bind Node inliningTarget,
                        @Cached MathUnaryHelperNode helperNode) {
            return helperNode.execute(frame, inliningTarget, value, AsinhNode::compute);
        }

        private static double compute(Node inliningTarget, double value, PRaiseNode raiseNode) {
            return MathUtils.asinh(value);
        }

        public static AsinhNode create() {
            return MathModuleBuiltinsFactory.AsinhNodeFactory.create();
        }
    }

    @Builtin(name = "isfinite", minNumOfPositionalArgs = 1)
    @TypeSystemReference(PythonIntegerAndFloatTypes.class)
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
        public static boolean isfinite(double value) {
            return Double.isFinite(value);
        }

        @Specialization(guards = "!isNumber(value)")
        public static boolean isinf(VirtualFrame frame, Object value,
                        @Bind Node inliningTarget,
                        @Cached PyFloatAsDoubleNode asDoubleNode) {
            return isfinite(asDoubleNode.execute(frame, inliningTarget, value));
        }
    }

    @Builtin(name = "isinf", minNumOfPositionalArgs = 1)
    @TypeSystemReference(PythonIntegerAndFloatTypes.class)
    @ImportStatic(MathGuards.class)
    @GenerateNodeFactory
    public abstract static class IsInfNode extends PythonUnaryBuiltinNode {

        @Specialization
        public static boolean isinf(@SuppressWarnings("unused") long value) {
            return false;
        }

        @Specialization
        public static boolean isinf(@SuppressWarnings("unused") PInt value) {
            return false;
        }

        @Specialization
        public static boolean isinf(double value) {
            return Double.isInfinite(value);
        }

        @Specialization(guards = "!isNumber(value)")
        public static boolean isinf(VirtualFrame frame, Object value,
                        @Bind Node inliningTarget,
                        @Cached PyFloatAsDoubleNode asDoubleNode) {
            return isinf(asDoubleNode.execute(frame, inliningTarget, value));
        }
    }

    @Builtin(name = "log", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonIntegerAndFloatTypes.class)
    @ImportStatic(MathGuards.class)
    @GenerateNodeFactory
    @SuppressWarnings("truffle-static-method")
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

        private static double countBase(double base, Node inliningTarget, InlinedConditionProfile divByZero, PRaiseNode raiseNode) {
            double logBase = Math.log(base);
            if (divByZero.profile(inliningTarget, logBase == 0)) {
                throw raiseNode.raise(inliningTarget, ZeroDivisionError, ErrorMessages.S_DIVISION_BY_ZERO, "float");
            }
            return logBase;
        }

        private static double countBase(BigInteger base, Node inliningTarget, InlinedConditionProfile divByZero, PRaiseNode raiseNode) {
            double logBase = logBigInteger(base);
            if (divByZero.profile(inliningTarget, logBase == 0)) {
                throw raiseNode.raise(inliningTarget, ZeroDivisionError, ErrorMessages.S_DIVISION_BY_ZERO, "float");
            }
            return logBase;
        }

        @Specialization
        static double log(long value, PNone novalue,
                        @Bind Node inliningTarget,
                        @Shared @Cached InlinedConditionProfile doNotFit,
                        @Shared @Cached PRaiseNode raiseNode) {
            return logDN(value, novalue, inliningTarget, doNotFit, raiseNode);
        }

        @Specialization
        static double logDN(double value, @SuppressWarnings("unused") PNone novalue,
                        @Bind Node inliningTarget,
                        @Shared @Cached InlinedConditionProfile doNotFit,
                        @Shared @Cached PRaiseNode raiseNode) {
            raiseMathError(inliningTarget, doNotFit, value <= 0, raiseNode);
            return Math.log(value);
        }

        @Specialization
        @TruffleBoundary
        static double logPIN(PInt value, @SuppressWarnings("unused") PNone novalue,
                        @Bind Node inliningTarget,
                        @Shared @Cached InlinedConditionProfile doNotFit,
                        @Shared @Cached PRaiseNode raiseNode) {
            BigInteger bValue = value.getValue();
            raiseMathError(inliningTarget, doNotFit, bValue.compareTo(BigInteger.ZERO) < 0, raiseNode);
            return logBigInteger(bValue);
        }

        @Specialization
        static double logLL(long value, long base,
                        @Bind Node inliningTarget,
                        @Shared @Cached InlinedConditionProfile doNotFit,
                        @Shared @Cached InlinedConditionProfile divByZero,
                        @Shared @Cached PRaiseNode raiseNode) {
            return logDD(value, base, inliningTarget, doNotFit, divByZero, raiseNode);
        }

        @Specialization
        static double logDL(double value, long base,
                        @Bind Node inliningTarget,
                        @Shared @Cached InlinedConditionProfile doNotFit,
                        @Shared @Cached InlinedConditionProfile divByZero,
                        @Shared @Cached PRaiseNode raiseNode) {
            return logDD(value, base, inliningTarget, doNotFit, divByZero, raiseNode);
        }

        @Specialization
        static double logLD(long value, double base,
                        @Bind Node inliningTarget,
                        @Shared @Cached InlinedConditionProfile doNotFit,
                        @Shared @Cached InlinedConditionProfile divByZero,
                        @Shared @Cached PRaiseNode raiseNode) {
            return logDD(value, base, inliningTarget, doNotFit, divByZero, raiseNode);
        }

        @Specialization
        static double logDD(double value, double base,
                        @Bind Node inliningTarget,
                        @Shared @Cached InlinedConditionProfile doNotFit,
                        @Shared @Cached InlinedConditionProfile divByZero,
                        @Shared @Cached PRaiseNode raiseNode) {
            raiseMathError(inliningTarget, doNotFit, value < 0 || base <= 0, raiseNode);
            double logBase = countBase(base, inliningTarget, divByZero, raiseNode);
            return Math.log(value) / logBase;
        }

        @Specialization
        @TruffleBoundary
        static double logDPI(double value, PInt base,
                        @Bind Node inliningTarget,
                        @Shared @Cached InlinedConditionProfile doNotFit,
                        @Shared @Cached InlinedConditionProfile divByZero,
                        @Shared @Cached PRaiseNode raiseNode) {
            BigInteger bBase = base.getValue();
            raiseMathError(inliningTarget, doNotFit, value < 0 || bBase.compareTo(BigInteger.ZERO) <= 0, raiseNode);
            double logBase = countBase(bBase, inliningTarget, divByZero, raiseNode);
            return Math.log(value) / logBase;
        }

        @Specialization
        static double logPIL(PInt value, long base,
                        @Bind Node inliningTarget,
                        @Shared @Cached InlinedConditionProfile doNotFit,
                        @Shared @Cached InlinedConditionProfile divByZero,
                        @Shared @Cached PRaiseNode raiseNode) {
            return logPID(value, base, inliningTarget, doNotFit, divByZero, raiseNode);
        }

        @Specialization
        @TruffleBoundary
        static double logPID(PInt value, double base,
                        @Bind Node inliningTarget,
                        @Shared @Cached InlinedConditionProfile doNotFit,
                        @Shared @Cached InlinedConditionProfile divByZero,
                        @Shared @Cached PRaiseNode raiseNode) {
            BigInteger bValue = value.getValue();
            raiseMathError(inliningTarget, doNotFit, bValue.compareTo(BigInteger.ZERO) < 0 || base <= 0, raiseNode);
            double logBase = countBase(base, inliningTarget, divByZero, raiseNode);
            return logBigInteger(bValue) / logBase;
        }

        @Specialization
        @TruffleBoundary
        static double logLPI(long value, PInt base,
                        @Bind Node inliningTarget,
                        @Shared @Cached InlinedConditionProfile doNotFit,
                        @Shared @Cached InlinedConditionProfile divByZero,
                        @Shared @Cached PRaiseNode raiseNode) {
            BigInteger bBase = base.getValue();
            raiseMathError(inliningTarget, doNotFit, value < 0 || bBase.compareTo(BigInteger.ZERO) <= 0, raiseNode);
            double logBase = countBase(bBase, inliningTarget, divByZero, raiseNode);
            return Math.log(value) / logBase;
        }

        @Specialization
        @TruffleBoundary
        static double logPIPI(PInt value, PInt base,
                        @Bind Node inliningTarget,
                        @Shared @Cached InlinedConditionProfile doNotFit,
                        @Shared @Cached InlinedConditionProfile divByZero,
                        @Shared @Cached PRaiseNode raiseNode) {
            BigInteger bValue = value.getValue();
            BigInteger bBase = base.getValue();
            raiseMathError(inliningTarget, doNotFit, bValue.compareTo(BigInteger.ZERO) < 0 || bBase.compareTo(BigInteger.ZERO) <= 0, raiseNode);
            double logBase = countBase(bBase, inliningTarget, divByZero, raiseNode);
            return logBigInteger(bValue) / logBase;
        }

        @Specialization(guards = "!isNumber(value)")
        double logO(VirtualFrame frame, Object value, PNone novalue,
                        @Bind Node inliningTarget,
                        @Shared @Cached PyFloatAsDoubleNode asDoubleNode) {
            return executeRecursiveLogNode(frame, asDoubleNode.execute(frame, inliningTarget, value), novalue);
        }

        @Specialization(guards = {"!isNumber(value)", "!isNoValue(base)"})
        double logOO(VirtualFrame frame, Object value, Object base,
                        @Bind Node inliningTarget,
                        @Shared @Cached PyFloatAsDoubleNode asDoubleNode) {
            return executeRecursiveLogNode(frame, asDoubleNode.execute(frame, inliningTarget, value), asDoubleNode.execute(frame, inliningTarget, base));
        }

        @Specialization(guards = {"!isNumber(base)"})
        double logLO(VirtualFrame frame, long value, Object base,
                        @Bind Node inliningTarget,
                        @Shared @Cached PyFloatAsDoubleNode asDoubleNode) {
            return executeRecursiveLogNode(frame, value, asDoubleNode.execute(frame, inliningTarget, base));
        }

        @Specialization(guards = {"!isNumber(base)"})
        double logDO(VirtualFrame frame, double value, Object base,
                        @Bind Node inliningTarget,
                        @Shared @Cached PyFloatAsDoubleNode asDoubleNode) {
            return executeRecursiveLogNode(frame, value, asDoubleNode.execute(frame, inliningTarget, base));
        }

        @Specialization(guards = {"!isNumber(base)"})
        double logPIO(VirtualFrame frame, PInt value, Object base,
                        @Bind Node inliningTarget,
                        @Shared @Cached PyFloatAsDoubleNode asDoubleNode) {
            return executeRecursiveLogNode(frame, value, asDoubleNode.execute(frame, inliningTarget, base));
        }

        private static void raiseMathError(Node inliningTarget, InlinedConditionProfile doNotFit, boolean con, PRaiseNode raiseNode) {
            if (doNotFit.profile(inliningTarget, con)) {
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.MATH_DOMAIN_ERROR);
            }
        }

        public static LogNode create() {
            return MathModuleBuiltinsFactory.LogNodeFactory.create();
        }
    }

    @Builtin(name = "log1p", minNumOfPositionalArgs = 1, doc = "Return the natural logarithm of 1+x (base e).\n\nThe result is computed in a way which is accurate for x near zero.")
    @GenerateNodeFactory
    public abstract static class Log1pNode extends PythonUnaryBuiltinNode {

        @Specialization
        static double doGeneric(VirtualFrame frame, Object value,
                        @Bind Node inliningTarget,
                        @Cached MathUnaryHelperNode helperNode) {
            return helperNode.execute(frame, inliningTarget, value, Log1pNode::compute);
        }

        private static double compute(Node inliningTarget, double value, PRaiseNode raiseNode) {
            if (value == 0 || value == Double.POSITIVE_INFINITY || Double.isNaN(value)) {
                return value;
            }
            double result = Math.log1p(value);
            checkMathDomainError(!Double.isFinite(result), inliningTarget, raiseNode);
            return result;
        }
    }

    @Builtin(name = "log2", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class Log2Node extends PythonUnaryBuiltinNode {
        private static final double LOG2 = Math.log(2);
        private static final BigInteger TWO = BigInteger.valueOf(2);

        @Specialization
        @TruffleBoundary
        double doPI(PInt value) {
            BigInteger bValue = value.getValue();
            checkMathDomainErrorUncached(bValue.compareTo(BigInteger.ZERO) <= 0, this);
            int e = bValue.bitLength() - 1;
            if (bValue.compareTo(TWO.pow(e)) == 0) {
                return e;
            }
            // this doesn't have to be as accured as should be
            return LogNode.logBigInteger(bValue) / LOG2;
        }

        @Specialization(guards = "!isPInt(value)")
        static double doGeneric(VirtualFrame frame, Object value,
                        @Bind Node inliningTarget,
                        @Cached MathUnaryHelperNode helperNode) {
            return helperNode.execute(frame, inliningTarget, value, Log2Node::compute);
        }

        private static double compute(Node inliningTarget, double value, PRaiseNode raiseNode) {
            checkMathDomainError(value <= 0, inliningTarget, raiseNode);
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
    public abstract static class Log10Node extends PythonUnaryBuiltinNode {

        private static final double LOG10 = Math.log(10);

        @TruffleBoundary
        public static int getDigitCount(BigInteger number) {
            double factor = Math.log(2) / Math.log(10);
            int digitCount = (int) (factor * number.bitLength() + 1);
            if (BigInteger.TEN.pow(digitCount - 1).compareTo(number) > 0) {
                return digitCount - 1;
            }
            return digitCount;
        }

        @Specialization
        @TruffleBoundary
        double doPI(PInt value) {
            BigInteger bValue = value.getValue();
            checkMathDomainErrorUncached(bValue.compareTo(BigInteger.ZERO) <= 0, this);
            int digitCount = getDigitCount(bValue) - 1;
            if (bValue.compareTo(BigInteger.TEN.pow(digitCount)) == 0) {
                return digitCount;
            }
            return LogNode.logBigInteger(bValue) / LOG10;
        }

        @Specialization(guards = "!isPInt(value)")
        static double doGeneric(VirtualFrame frame, Object value,
                        @Bind Node inliningTarget,
                        @Cached MathUnaryHelperNode helperNode) {
            return helperNode.execute(frame, inliningTarget, value, Log10Node::compute);
        }

        private static double compute(Node inliningTarget, double value, PRaiseNode raiseNode) {
            checkMathDomainError(value <= 0, inliningTarget, raiseNode);
            return Math.log10(value);
        }
    }

    @Builtin(name = "fabs", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"value"})
    @ArgumentClinic(name = "value", conversion = ArgumentClinic.ClinicConversion.Double)
    @GenerateNodeFactory
    public abstract static class FabsNode extends PythonUnaryClinicBuiltinNode {
        @Specialization
        public double fabs(double value) {
            return Math.abs(value);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return MathModuleBuiltinsClinicProviders.FabsNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "sumprod", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"p", "q"}, doc = "sumprod($module, p, q, /)\n" + //
                    "--\n" + //
                    "\n" + //
                    "Return the sum of products of values from two iterables p and q.\n" + //
                    "\n" + //
                    "Roughly equivalent to:\n" + //
                    "\n" + //
                    "    sum(itertools.starmap(operator.mul, zip(p, q, strict=True)))\n" + //
                    "\n" + //
                    "For float and mixed int/float inputs, the intermediate products\n" + //
                    "and sums are computed with extended precision.")
    @GenerateNodeFactory
    public abstract static class SumprodNode extends PythonBinaryBuiltinNode {

        static boolean int_path(VirtualFrame frame, Node inliningTarget, Object p_i, Object q_i, long[] int_total,
                        PyLongCheckExactNode pyLongCheckExactNode,
                        PyLongAsLongAndOverflowNode pyLongAsLongAndOverflowNode) {
            if (pyLongCheckExactNode.execute(inliningTarget, p_i) && pyLongCheckExactNode.execute(inliningTarget, q_i)) {
                try {
                    long int_p = pyLongAsLongAndOverflowNode.execute(frame, inliningTarget, p_i);
                    long int_q = pyLongAsLongAndOverflowNode.execute(frame, inliningTarget, q_i);
                    long int_prod = Math.multiplyExact(int_p, int_q);
                    int_total[0] = Math.addExact(int_total[0], int_prod);
                } catch (OverflowException | ArithmeticException e) {
                    return false; // goto finalize_int_path
                }
                return true; // continue
            }
            return false; // goto finalize_int_path
        }

        static boolean flt_path(VirtualFrame frame, Node inliningTarget, Object p_i, Object q_i, TripleLength[] flt_total,
                        IsBuiltinObjectProfile errorProfile,
                        PyFloatCheckExactNode pyFloatCheckExactNode,
                        PyLongCheckExactNode pyLongCheckExactNode,
                        PyBoolCheckNode pyBoolCheckNode,
                        PyLongAsDoubleNode pyLongAsDoubleNode,
                        PyFloatAsDoubleNode pyFloatAsDoubleNode) {
            double flt_p, flt_q;
            boolean p_type_float = pyFloatCheckExactNode.execute(inliningTarget, p_i);
            boolean q_type_float = pyFloatCheckExactNode.execute(inliningTarget, q_i);
            if (p_type_float && q_type_float) {
                flt_p = pyFloatAsDoubleNode.execute(frame, inliningTarget, p_i);
                flt_q = pyFloatAsDoubleNode.execute(frame, inliningTarget, q_i);
            } else if (p_type_float && (pyLongCheckExactNode.execute(inliningTarget, q_i) || pyBoolCheckNode.execute(inliningTarget, q_i))) {
                /*
                 * We care about float/int pairs and int/float pairs because they arise naturally in
                 * several use cases such as price times quantity, measurements with integer
                 * weights, or data selected by a vector of bools.
                 */
                flt_p = pyFloatAsDoubleNode.execute(frame, inliningTarget, p_i);
                try {
                    flt_q = pyLongAsDoubleNode.execute(inliningTarget, q_i);
                } catch (PException e) {
                    e.expectOverflowError(inliningTarget, errorProfile);
                    return false; // goto finalize_flt_path
                }
            } else if (q_type_float && (pyLongCheckExactNode.execute(inliningTarget, p_i) || pyBoolCheckNode.execute(inliningTarget, p_i))) {
                flt_q = pyFloatAsDoubleNode.execute(frame, inliningTarget, q_i);
                try {
                    flt_p = pyLongAsDoubleNode.execute(inliningTarget, p_i);
                } catch (PException e) {
                    e.expectOverflowError(inliningTarget, errorProfile);
                    return false; // goto finalize_flt_path
                }
            } else {
                return false; // goto finalize_flt_path
            }
            TripleLength new_flt_total = tl_fma(flt_p, flt_q, flt_total[0]);
            if (Double.isFinite(new_flt_total.hi)) {
                flt_total[0] = new_flt_total;
                return true; // continue
            }
            return false; // goto finalize_flt_path
        }

        @Specialization
        static Object sumprod(VirtualFrame frame, Object p, Object q,
                        @Bind Node inliningTarget,
                        @Cached PyObjectGetIter pyObjectGetIter,
                        @Cached PyIterNextNode nextNode,
                        @Cached PyLongAsLongAndOverflowNode pyLongAsLongAndOverflowNode,
                        @Cached IsBuiltinObjectProfile errorProfile,
                        @Cached PyFloatCheckExactNode pyFloatCheckExactNode,
                        @Cached PyLongCheckExactNode pyLongCheckExactNode,
                        @Cached PyBoolCheckNode pyBoolCheckNode,
                        @Cached PyLongAsDoubleNode pyLongAsDoubleNode,
                        @Cached PyFloatAsDoubleNode pyFloatAsDoubleNode,
                        @Cached PyNumberAddNode pyNumberAddNode,
                        @Cached PyNumberMultiplyNode pyNumberMultiplyNode,
                        @Cached PRaiseNode raiseNode) {
            boolean p_stopped = false, q_stopped = false;
            boolean int_path_enabled = true, int_total_in_use = false;
            boolean flt_path_enabled = true, flt_total_in_use = false;
            long[] int_total = new long[]{0};
            TripleLength[] flt_total = new TripleLength[]{TL_ZERO};

            Object p_it = pyObjectGetIter.execute(frame, inliningTarget, p);
            Object q_it = pyObjectGetIter.execute(frame, inliningTarget, q);
            Object total = 0L;
            while (true) {
                Object p_i = null, q_i = null;
                try {
                    p_i = nextNode.execute(frame, inliningTarget, p_it);
                } catch (IteratorExhausted e) {
                    p_stopped = true;
                }

                try {
                    q_i = nextNode.execute(frame, inliningTarget, q_it);
                } catch (IteratorExhausted e) {
                    q_stopped = true;
                }

                if (p_stopped != q_stopped) {
                    raiseNode.raise(inliningTarget, ValueError, ErrorMessages.INPUTS_ARE_NOT_THE_SAME_LENGTH);
                }
                boolean finished = p_stopped && q_stopped;

                if (int_path_enabled) {
                    if (!finished && int_path(frame, inliningTarget, p_i, q_i, int_total,
                                    pyLongCheckExactNode, pyLongAsLongAndOverflowNode)) {
                        int_total_in_use = true;
                        continue;
                    }
                    // We're finished, overflowed, or have a non-int
                    int_path_enabled = false;
                    if (int_total_in_use) {
                        total = pyNumberAddNode.execute(frame, total, int_total[0]);
                        int_total[0] = 0;   // An ounce of prevention, ...
                        int_total_in_use = false;
                    }
                }

                if (flt_path_enabled) {

                    if (!finished && flt_path(frame, inliningTarget, p_i, q_i, flt_total,
                                    errorProfile, pyFloatCheckExactNode, pyLongCheckExactNode, pyBoolCheckNode, pyLongAsDoubleNode, pyFloatAsDoubleNode)) {
                        flt_total_in_use = true;
                        continue;
                    }
                    // We're finished, overflowed, have a non-float, or got a non-finite value
                    flt_path_enabled = false;
                    if (flt_total_in_use) {
                        total = pyNumberAddNode.execute(frame, total, tl_to_d(flt_total[0]));
                        flt_total[0] = TL_ZERO;
                        flt_total_in_use = false;
                    }
                }

                assert (!int_total_in_use);
                assert (!flt_total_in_use);

                if (finished) {
                    break; // goto normal_exit
                }
                total = pyNumberAddNode.execute(frame, total, pyNumberMultiplyNode.execute(frame, p_i, q_i));
            }

            // normal_exit
            return total;
        }

        /*
         * Double and triple length extended precision algorithms from:
         *
         * Accurate Sum and Dot Product by Takeshi Ogita, Siegfried M. Rump, and Shin’Ichi Oishi
         * https://doi.org/10.1137/030601818 https://www.tuhh.de/ti3/paper/rump/OgRuOi05.pdf
         *
         */

        record DoubleLength(double hi, double lo) {
        }

        static DoubleLength dl_sum(double a, double b) {
            /* Algorithm 3.1 Error-free transformation of the sum */
            double x = a + b;
            double z = x - a;
            double y = (a - (x - z)) + (b - z);
            return new DoubleLength(x, y);
        }

        static DoubleLength dl_mul(double x, double y) {
            /* Algorithm 3.5. Error-free transformation of a product */
            double z = x * y;
            double zz = fma(x, y, -z);
            return new DoubleLength(z, zz);
        }

        @TruffleBoundary
        static double fma(double a, double b, double c) {
            return Math.fma(a, b, c);
        }

        record TripleLength(double hi, double lo, double tiny) {
        }

        static final TripleLength TL_ZERO = new TripleLength(0.0, 0.0, 0.0);

        static TripleLength tl_fma(double x, double y, TripleLength total) {
            /* Algorithm 5.10 with SumKVert for K=3 */
            DoubleLength pr = dl_mul(x, y);
            DoubleLength sm = dl_sum(total.hi, pr.hi);
            DoubleLength r1 = dl_sum(total.lo, pr.lo);
            DoubleLength r2 = dl_sum(r1.hi, sm.lo);
            return new TripleLength(sm.hi, r2.hi, total.tiny + r1.lo + r2.lo);
        }

        static double tl_to_d(TripleLength total) {
            DoubleLength last = dl_sum(total.lo, total.hi);
            return total.tiny + last.lo + last.hi;
        }
    }

    @Builtin(name = "pow", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"x", "y"})
    @ArgumentClinic(name = "x", conversion = ArgumentClinic.ClinicConversion.Double)
    @ArgumentClinic(name = "y", conversion = ArgumentClinic.ClinicConversion.Double)
    @GenerateNodeFactory
    public abstract static class PowNode extends PythonBinaryClinicBuiltinNode {
        @Specialization
        static double pow(double left, double right,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
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
                    } else {
                        result = 0;
                    }
                }
            } else {
                result = Math.pow(left, right);
                if (!Double.isFinite(result)) {
                    if (Double.isNaN(result)) {
                        throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.MATH_DOMAIN_ERROR);
                    } else if (Double.isInfinite(result)) {
                        if (left == 0) {
                            throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.MATH_DOMAIN_ERROR);
                        } else {
                            throw raiseNode.raise(inliningTarget, OverflowError, ErrorMessages.MATH_RANGE_ERROR);
                        }
                    }
                }
            }
            return result;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return MathModuleBuiltinsClinicProviders.PowNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "trunc", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class TruncNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object trunc(VirtualFrame frame, Object obj,
                        @Bind Node inliningTarget,
                        @Cached("create(T___TRUNC__)") LookupAndCallUnaryNode callTrunc,
                        @Cached PRaiseNode raiseNode) {
            Object result = callTrunc.executeObject(frame, obj);
            if (result == PNone.NO_VALUE) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.TYPE_DOESNT_DEFINE_METHOD, obj, "__trunc__");
            }
            return result;
        }
    }

    @Builtin(name = "atan2", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"left", "right"})
    @ArgumentClinic(name = "left", conversion = ArgumentClinic.ClinicConversion.Double)
    @ArgumentClinic(name = "right", conversion = ArgumentClinic.ClinicConversion.Double)
    @GenerateNodeFactory
    public abstract static class Atan2Node extends PythonBinaryClinicBuiltinNode {
        @Specialization
        double atan2DD(double left, double right) {
            return Math.atan2(left, right);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return MathModuleBuiltinsClinicProviders.Atan2NodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "degrees", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class DegreesNode extends PythonUnaryBuiltinNode {
        private static final double RAD_TO_DEG = 180.0 / Math.PI;

        @Specialization
        static double doGeneric(VirtualFrame frame, Object value,
                        @Bind Node inliningTarget,
                        @Cached MathUnaryHelperNode helperNode) {
            return helperNode.execute(frame, inliningTarget, value, DegreesNode::compute);
        }

        private static double compute(Node inliningTarget, double value, PRaiseNode raiseNode) {
            return value * RAD_TO_DEG;
        }
    }

    @Builtin(name = "radians", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class RadiansNode extends PythonUnaryBuiltinNode {
        private static final double DEG_TO_RAD = Math.PI / 180.0;

        @Specialization
        static double doGeneric(VirtualFrame frame, Object value,
                        @Bind Node inliningTarget,
                        @Cached MathUnaryHelperNode helperNode) {
            return helperNode.execute(frame, inliningTarget, value, RadiansNode::compute);
        }

        private static double compute(Node inliningTarget, double value, PRaiseNode raiseNode) {
            return value * DEG_TO_RAD;
        }
    }

    @Builtin(name = "hypot", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, declaresExplicitSelf = true)
    @TypeSystemReference(PythonIntegerAndFloatTypes.class)
    @GenerateNodeFactory
    @ImportStatic(MathGuards.class)
    public abstract static class HypotNode extends PythonVarargsBuiltinNode {

        @Specialization(guards = "arguments.length == 2")
        public double hypot2(VirtualFrame frame, @SuppressWarnings("unused") Object self, Object[] arguments, PKeyword[] keywords,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached PRaiseNode raiseNode,
                        @Exclusive @Cached PyFloatAsDoubleNode xAsDouble,
                        @Exclusive @Cached PyFloatAsDoubleNode yAsDouble) {
            if (keywords.length != 0) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.S_TAKES_NO_KEYWORD_ARGS, "hypot()");
            }
            double x = xAsDouble.execute(frame, inliningTarget, arguments[0]);
            double y = yAsDouble.execute(frame, inliningTarget, arguments[1]);
            return Math.hypot(x, y);
        }

        @Specialization
        @SuppressWarnings("truffle-static-method")
        double hypotGeneric(VirtualFrame frame, @SuppressWarnings("unused") Object self, Object[] arguments, PKeyword[] keywords,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached PRaiseNode raiseNode,
                        @Exclusive @Cached PyFloatAsDoubleNode asDoubleNode) {
            if (keywords.length != 0) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.S_TAKES_NO_KEYWORD_ARGS, "hypot()");
            }
            double max = 0.0;
            boolean foundNan = false;
            double[] coordinates = new double[arguments.length];
            for (int i = 0; i < arguments.length; i++) {
                double x = asDoubleNode.execute(frame, inliningTarget, arguments[i]);
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
    public abstract static class ErfNode extends PythonUnaryBuiltinNode {
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

        @Specialization
        static double doGeneric(VirtualFrame frame, Object value,
                        @Bind Node inliningTarget,
                        @Cached MathUnaryHelperNode helperNode) {
            return helperNode.execute(frame, inliningTarget, value, ErfNode::compute);
        }

        private static double compute(Node inliningTarget, double value, PRaiseNode raiseNode) {
            double absx, cf;

            if (Double.isNaN(value)) {
                return value;
            }
            absx = Math.abs(value);
            if (absx < ERF_SERIES_CUTOFF) {
                return m_erf_series(value);
            } else {
                cf = m_erfc_contfrac(absx);
                return value > 0.0 ? 1.0 - cf : cf - 1.0;
            }
        }
    }

    @Builtin(name = "erfc", minNumOfPositionalArgs = 1, doc = "Error function at x.")
    @GenerateNodeFactory
    public abstract static class ErfcNode extends PythonUnaryBuiltinNode {
        // Adapted implementation from CPython
        @Specialization
        static double doGeneric(VirtualFrame frame, Object value,
                        @Bind Node inliningTarget,
                        @Cached MathUnaryHelperNode helperNode) {
            return helperNode.execute(frame, inliningTarget, value, ErfcNode::compute);
        }

        private static double compute(Node inliningTarget, double x, PRaiseNode raiseNode) {
            double absx, cf;

            if (Double.isNaN(x)) {
                return x;
            }
            absx = Math.abs(x);
            if (absx < ErfNode.ERF_SERIES_CUTOFF) {
                return 1.0 - ErfNode.m_erf_series(x);
            } else {
                cf = ErfNode.m_erfc_contfrac(absx);
                return x > 0.0 ? cf : 2.0 - cf;
            }
        }
    }

    @Builtin(name = "gamma", minNumOfPositionalArgs = 1, doc = "Gamma function at x")
    @GenerateNodeFactory
    public abstract static class GammaNode extends PythonUnaryBuiltinNode {
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

        @Specialization
        static double doGeneric(VirtualFrame frame, Object value,
                        @Bind Node inliningTarget,
                        @Cached MathUnaryHelperNode helperNode) {
            return helperNode.execute(frame, inliningTarget, value, GammaNode::compute);
        }

        private static double compute(Node inliningTarget, double x, PRaiseNode raiseNode) {
            double absx, r, y, z, sqrtpow;

            /* special cases */
            if (!Double.isFinite(x)) {
                if (Double.isNaN(x) || x > 0.0) {
                    return x; /* tgamma(nan) = nan, tgamma(inf) = inf */
                } else {
                    checkMathDomainError(false, inliningTarget, raiseNode);
                }
            }
            checkMathDomainError(x == 0, inliningTarget, raiseNode);

            /* integer arguments */
            if (x == Math.floor(x)) {
                checkMathDomainError(x < 0.0, inliningTarget, raiseNode);
                if (x <= NGAMMA_INTEGRAL) {
                    return GAMMA_INTEGRAL[(int) x - 1];
                }
            }
            absx = Math.abs(x);

            /* tiny arguments: tgamma(x) ~ 1/x for x near 0 */
            if (absx < 1e-20) {
                r = 1.0 / x;
                checkMathRangeError(Double.isInfinite(r), inliningTarget, raiseNode);
                return r;
            }

            /*
             * large arguments: assuming IEEE 754 doubles, tgamma(x) overflows for x > 200, and
             * underflows to +-0.0 for x < -200, not a negative integer.
             */
            if (absx > 200.0) {
                checkMathRangeError(x >= 0.0, inliningTarget, raiseNode);
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
            checkMathRangeError(Double.isInfinite(r), inliningTarget, raiseNode);
            return r;
        }

    }

    @Builtin(name = "lgamma", minNumOfPositionalArgs = 1, doc = "Natural logarithm of absolute value of Gamma function at x.")
    @GenerateNodeFactory
    public abstract static class LgammaNode extends PythonUnaryBuiltinNode {
        // Adapted implementation from CPython
        private static final double LOGPI = 1.144729885849400174143427351353058711647;

        @Specialization
        static double doGeneric(VirtualFrame frame, Object value,
                        @Bind Node inliningTarget,
                        @Cached MathUnaryHelperNode helperNode) {
            return helperNode.execute(frame, inliningTarget, value, LgammaNode::compute);
        }

        private static double compute(Node inliningTarget, double x, PRaiseNode raiseNode) {
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
                checkMathDomainError(x <= 0.0, inliningTarget, raiseNode);
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
            r = Math.log(GammaNode.lanczos_sum(absx)) - GammaNode.LANCZOS_G;
            r += (absx - 0.5) * (Math.log(absx + GammaNode.LANCZOS_G - 0.5) - 1);
            if (x < 0.0) {
                /* Use reflection formula to get value for negative x. */
                r = LOGPI - Math.log(Math.abs(GammaNode.sinpi(absx))) - Math.log(absx) - r;
            }
            checkMathRangeError(Double.isInfinite(r), inliningTarget, raiseNode);

            return r;
        }

    }

    @Builtin(name = "isqrt", minNumOfPositionalArgs = 1)
    @TypeSystemReference(PythonIntegerAndFloatTypes.class)
    @GenerateNodeFactory
    @ImportStatic(MathGuards.class)
    public abstract static class IsqrtNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object isqrtLong(long x,
                        @Bind Node inliningTarget,
                        @Shared @Cached NarrowBigIntegerNode makeInt,
                        @Shared @Cached PRaiseNode raiseNode) {
            raiseIfNegative(inliningTarget, x < 0, raiseNode);
            return makeInt.execute(inliningTarget, op(PInt.longToBigInteger(x)));
        }

        @Specialization
        static Object isqrtPInt(PInt x,
                        @Bind Node inliningTarget,
                        @Shared @Cached NarrowBigIntegerNode makeInt,
                        @Shared @Cached PRaiseNode raiseNode) {
            raiseIfNegative(inliningTarget, x.isNegative(), raiseNode);
            return makeInt.execute(inliningTarget, op(x.getValue()));
        }

        @Specialization(guards = "!isInteger(x)")
        static Object doGeneral(VirtualFrame frame, Object x,
                        @Bind Node inliningTarget,
                        @Cached PyNumberIndexNode indexNode,
                        @Cached IsqrtNode recursiveNode) {
            return recursiveNode.execute(frame, indexNode.execute(frame, inliningTarget, x));
        }

        @TruffleBoundary
        private static BigInteger op(BigInteger x) {
            // assumes x >= 0
            if (x.equals(BigInteger.ZERO) || x.equals(BigInteger.ONE)) {
                return x;
            }
            BigInteger start = BigInteger.ONE;
            BigInteger end = x;
            BigInteger result = BigInteger.ZERO;
            BigInteger two = BigInteger.valueOf(2);
            while (start.compareTo(end) <= 0) {
                BigInteger mid = (start.add(end).divide(two));
                int cmp = mid.multiply(mid).compareTo(x);
                if (cmp == 0) {
                    return mid;
                }
                if (cmp < 0) {
                    start = mid.add(BigInteger.ONE);
                    result = mid;
                } else {
                    end = mid.subtract(BigInteger.ONE);
                }
            }
            return result;
        }

        private static void raiseIfNegative(Node inliningTarget, boolean condition, PRaiseNode raiseNode) {
            if (condition) {
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.MUST_BE_NON_NEGATIVE, "isqrt() argument");
            }
        }
    }

    @Builtin(name = "prod", minNumOfPositionalArgs = 1, parameterNames = {"iterable"}, keywordOnlyNames = {"start"})
    @GenerateNodeFactory
    public abstract static class ProdNode extends PythonBuiltinNode {

        @Specialization
        public Object doGeneric(VirtualFrame frame, Object iterable, Object startIn,
                        @Bind Node inliningTarget,
                        @Cached InlinedConditionProfile startIsNoValueProfile,
                        @Cached PyObjectGetIter getIter,
                        @Cached PyIterNextNode nextNode,
                        @Cached PyNumberMultiplyNode multiplyNode,
                        @Cached InlinedLoopConditionProfile loopProfile) {
            Object start = startIsNoValueProfile.profile(inliningTarget, PGuards.isNoValue(startIn)) ? 1 : startIn;
            Object iterator = getIter.execute(frame, inliningTarget, iterable);
            Object acc = start;
            boolean exhausted = false;
            while (loopProfile.profile(inliningTarget, !exhausted)) {
                try {
                    Object next = nextNode.execute(frame, inliningTarget, iterator);
                    acc = multiplyNode.execute(frame, acc, next);
                } catch (IteratorExhausted e) {
                    exhausted = true;
                }
            }
            return acc;
        }
    }

    @Builtin(name = "dist", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"p", "q"})
    @GenerateNodeFactory
    public abstract static class DistNode extends PythonBuiltinNode {

        @Specialization
        static double doGeneric(VirtualFrame frame, Object p, Object q,
                        @Bind Node inliningTarget,
                        @Cached PyFloatAsDoubleNode asDoubleNode,
                        @Cached TupleNodes.ConstructTupleNode tupleCtor,
                        @Cached SequenceNodes.GetObjectArrayNode getObjectArray,
                        @Cached InlinedLoopConditionProfile loopProfile1,
                        @Cached InlinedLoopConditionProfile loopProfile2,
                        @Cached InlinedConditionProfile infProfile,
                        @Cached InlinedConditionProfile nanProfile,
                        @Cached InlinedConditionProfile trivialProfile,
                        @Cached PRaiseNode raiseNode) {
            // adapted from CPython math_dist_impl and vector_norm
            Object[] ps = getObjectArray.execute(inliningTarget, tupleCtor.execute(frame, p));
            Object[] qs = getObjectArray.execute(inliningTarget, tupleCtor.execute(frame, q));
            int len = ps.length;
            if (len != qs.length) {
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.BOTH_POINTS_MUST_HAVE_THE_SAME_NUMBER_OF_DIMENSIONS);
            }
            double[] diffs = new double[len];
            double max = 0.0;
            boolean foundNan = false;
            loopProfile1.profileCounted(inliningTarget, len);
            for (int i = 0; loopProfile1.inject(inliningTarget, i < len); ++i) {
                double a = asDoubleNode.execute(frame, inliningTarget, ps[i]);
                double b = asDoubleNode.execute(frame, inliningTarget, qs[i]);
                double x = Math.abs(a - b);
                diffs[i] = x;
                foundNan |= Double.isNaN(x);
                if (x > max) {
                    max = x;
                }
            }
            if (infProfile.profile(inliningTarget, Double.isInfinite(max))) {
                return max;
            }
            if (nanProfile.profile(inliningTarget, foundNan)) {
                return Double.NaN;
            }
            if (trivialProfile.profile(inliningTarget, max == 0.0 || len <= 1)) {
                return max;
            }

            double csum = 1.0;
            double frac = 0.0;
            loopProfile2.profileCounted(inliningTarget, len);
            for (int i = 0; loopProfile2.inject(inliningTarget, i < len); ++i) {
                double x = diffs[i];
                x /= max;
                x = x * x;
                double oldcsum = csum;
                csum += x;
                frac += (oldcsum - csum) + x;
            }
            return max * Math.sqrt(csum - 1.0 + frac);
        }
    }
}
