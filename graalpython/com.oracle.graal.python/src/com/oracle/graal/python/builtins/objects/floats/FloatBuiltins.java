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
package com.oracle.graal.python.builtins.objects.floats;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OverflowError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ABS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__BOOL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__DIVMOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__FLOAT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__FLOORDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__FORMAT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETFORMAT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__MOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__MUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEG__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__POS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__POW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RDIVMOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RFLOORDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RMOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ROUND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RPOW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RSUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RTRUEDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__STR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__TRUEDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__TRUNC__;
import static com.oracle.graal.python.runtime.formatting.FormattingUtils.validateAndPrepareForFloat;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.ByteOrder;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.MathGuards;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.FromNativeSubclassNode;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeObject;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.special.LookupAndCallTernaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallVarargsNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.formatting.FloatFormatter;
import com.oracle.graal.python.runtime.formatting.InternalFormat;
import com.oracle.graal.python.runtime.formatting.InternalFormat.Spec;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PFloat)
public final class FloatBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return FloatBuiltinsFactory.getFactories();
    }

    public static double asDouble(boolean right) {
        return right ? 1.0 : 0.0;
    }

    @Builtin(name = __STR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class StrNode extends PythonUnaryBuiltinNode {
        @Specialization
        String str(double self) {
            Spec spec = new Spec(' ', '>', Spec.NONE, false, Spec.UNSPECIFIED, Spec.NONE, 0, 'r');
            FloatFormatter f = new FloatFormatter(getCore(), spec);
            f.setMinFracDigits(1);
            return doFormat(self, f);
        }

        public static StrNode create() {
            return FloatBuiltinsFactory.StrNodeFactory.create();
        }

        @Specialization(guards = "getFloat.isFloatSubtype(frame, object, getClass, isSubtype, context)", limit = "1")
        String doNativeFloat(VirtualFrame frame, PythonNativeObject object,
                        @SuppressWarnings("unused") @CachedContext(PythonLanguage.class) PythonContext context,
                        @SuppressWarnings("unused") @Cached GetClassNode getClass,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtype,
                        @SuppressWarnings("unused") @Cached FromNativeSubclassNode getFloat) {
            return PFloat.doubleToString(getFloat.execute(frame, object));
        }

        @TruffleBoundary
        private static String doFormat(double d, FloatFormatter f) {
            return f.format(d).getResult();
        }
    }

    @Builtin(name = __REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReprNode extends StrNode {
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
        String format(double self, String formatString) {
            InternalFormat.Spec spec = InternalFormat.fromText(getCore(), formatString, __FORMAT__);
            FloatFormatter formatter = new FloatFormatter(getCore(), validateAndPrepareForFloat(spec, getCore(), "float"));
            formatter.format(self);
            return formatter.pad().getResult();
        }

        @Fallback
        String other(@SuppressWarnings("unused") Object self, Object formatString) {
            throw raise(TypeError, ErrorMessages.ARG_D_MUST_BE_S_NOT_P, "format()", 2, "str", formatString);
        }
    }

    @Builtin(name = __ABS__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class AbsNode extends PythonUnaryBuiltinNode {

        @Specialization
        double abs(double arg) {
            return Math.abs(arg);
        }
    }

    @Builtin(name = __BOOL__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class BoolNode extends PythonUnaryBuiltinNode {
        @Specialization
        boolean bool(double self) {
            return self != 0.0;
        }
    }

    @Builtin(name = __INT__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @ImportStatic(MathGuards.class)
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class IntNode extends PythonUnaryBuiltinNode {

        public abstract Object executeWithDouble(double self);

        @Specialization(guards = "fitInt(self)")
        int doIntRange(double self) {
            return (int) self;
        }

        @Specialization(guards = "fitLong(self)")
        long doLongRange(double self) {
            return (long) self;
        }

        @Specialization(guards = "!fitLong(self)", rewriteOn = NumberFormatException.class)
        PInt doDoubleGeneric(double self) {
            return factory().createInt(fromDouble(self));
        }

        @Specialization(guards = "!fitLong(self)", replaces = "doDoubleGeneric")
        PInt doDoubleGenericError(double self) {
            try {
                return factory().createInt(fromDouble(self));
            } catch (NumberFormatException e) {
                throw raise(Double.isNaN(self) ? ValueError : OverflowError, ErrorMessages.CANNOT_CONVERT_FLOAT_F_TO_INT, self);
            }
        }

        @TruffleBoundary(transferToInterpreterOnException = false)
        private static BigInteger fromDouble(double self) {
            return new BigDecimal(self, MathContext.UNLIMITED).toBigInteger();
        }
    }

    @Builtin(name = __FLOAT__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class FloatNode extends PythonUnaryBuiltinNode {
        @Specialization
        double doDouble(double self) {
            return self;
        }

        @Specialization
        PFloat doPFloat(PFloat self) {
            return self;
        }

        @Specialization(guards = "getFloat.isFloatSubtype(frame, possibleBase, getClass, isSubtype, context)", limit = "1")
        PythonNativeObject doNativeFloat(@SuppressWarnings("unused") VirtualFrame frame, PythonNativeObject possibleBase,
                        @SuppressWarnings("unused") @CachedContext(PythonLanguage.class) PythonContext context,
                        @SuppressWarnings("unused") @Cached GetClassNode getClass,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtype,
                        @SuppressWarnings("unused") @Cached FromNativeSubclassNode getFloat) {
            return possibleBase;
        }

        @Fallback
        Object doFallback(Object possibleBase) {
            throw raise(PythonErrorType.TypeError, ErrorMessages.MUST_BE_REAL_NUMBER, possibleBase);
        }
    }

    @Builtin(name = __RADD__, minNumOfPositionalArgs = 2)
    @Builtin(name = __ADD__, minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class AddNode extends PythonBinaryBuiltinNode {
        @Specialization
        double doDD(double left, double right) {
            return left + right;
        }

        @Specialization
        double doDL(double left, long right) {
            return left + right;
        }

        @Specialization
        double doDPi(double left, PInt right) {
            return left + right.doubleValueWithOverflow(getRaiseNode());
        }

        @Specialization
        Object doDP(VirtualFrame frame, PythonNativeObject left, long right,
                        @Cached FromNativeSubclassNode getFloat) {
            Double leftPrimitive = getFloat.execute(frame, left);
            if (leftPrimitive != null) {
                return leftPrimitive + right;
            } else {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }

        @Specialization
        Object doDP(VirtualFrame frame, PythonNativeObject left, double right,
                        @Cached FromNativeSubclassNode getFloat) {
            Double leftPrimitive = getFloat.execute(frame, left);
            if (leftPrimitive != null) {
                return leftPrimitive + right;
            } else {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __RSUB__, minNumOfPositionalArgs = 2, reverseOperation = true)
    @Builtin(name = __SUB__, minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class SubNode extends PythonBinaryBuiltinNode {
        @Specialization
        double doDD(double left, double right) {
            return left - right;
        }

        @Specialization
        double doDL(double left, long right) {
            return left - right;
        }

        @Specialization
        double doDPi(double left, PInt right) {
            return left - right.doubleValueWithOverflow(getRaiseNode());
        }

        @Specialization
        double doLD(long left, double right) {
            return left - right;
        }

        @Specialization
        double doPiD(PInt left, double right) {
            return left.doubleValueWithOverflow(getRaiseNode()) - right;
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __RMUL__, minNumOfPositionalArgs = 2)
    @Builtin(name = __MUL__, minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class MulNode extends PythonBinaryBuiltinNode {
        @Specialization
        double doDL(double left, long right) {
            return left * right;
        }

        @Specialization
        double doDD(double left, double right) {
            return left * right;
        }

        @Specialization
        double doDP(double left, PInt right) {
            return left * right.doubleValueWithOverflow(getRaiseNode());
        }

        @Specialization
        Object doDP(VirtualFrame frame, PythonNativeObject left, long right,
                        @Cached FromNativeSubclassNode getFloat) {
            Double leftPrimitive = getFloat.execute(frame, left);
            if (leftPrimitive != null) {
                return leftPrimitive * right;
            } else {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }

        @Specialization
        Object doDP(VirtualFrame frame, PythonNativeObject left, double right,
                        @Cached FromNativeSubclassNode getFloat) {
            Double leftPrimitive = getFloat.execute(frame, left);
            if (leftPrimitive != null) {
                return leftPrimitive * right;
            } else {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }

        @Specialization
        Object doDP(VirtualFrame frame, PythonNativeObject left, PInt right,
                        @Cached FromNativeSubclassNode getFloat) {
            Double leftPrimitive = getFloat.execute(frame, left);
            if (leftPrimitive != null) {
                return leftPrimitive * right.doubleValueWithOverflow(getRaiseNode());
            } else {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __RPOW__, minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3, reverseOperation = true)
    @Builtin(name = __POW__, minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    @ReportPolymorphism
    abstract static class PowerNode extends PythonTernaryBuiltinNode {
        @Specialization
        double doDL(double left, long right, @SuppressWarnings("unused") PNone none,
                        @Shared("negativeRaise") @Cached BranchProfile negativeRaise) {
            return doOperation(left, right, negativeRaise);
        }

        @Specialization
        double doDPi(double left, PInt right, @SuppressWarnings("unused") PNone none,
                        @Shared("negativeRaise") @Cached BranchProfile negativeRaise) {
            return doOperation(left, right.doubleValueWithOverflow(getRaiseNode()), negativeRaise);
        }

        /**
         * The special cases we need to deal with always return 1, so 0 means no special case, not a
         * result.
         */
        private double doSpecialCases(double left, double right, BranchProfile negativeRaise) {
            // see cpython://Objects/floatobject.c#float_pow for special cases
            if (Double.isNaN(right) && left == 1) {
                // 1**nan = 1, unlike on Java
                return 1;
            }
            if (Double.isInfinite(right) && (left == 1 || left == -1)) {
                // v**(+/-)inf is 1.0 if abs(v) == 1, unlike on Java
                return 1;
            }
            if (left == 0 && right < 0 && Double.isFinite(right)) {
                negativeRaise.enter();
                // 0**w is an error if w is finite and negative, unlike Java
                throw raise(PythonBuiltinClassType.ZeroDivisionError, ErrorMessages.POW_ZERO_CANNOT_RAISE_TO_NEGATIVE_POWER);
            }
            return 0;
        }

        private double doOperation(double left, double right, BranchProfile negativeRaise) {
            if (doSpecialCases(left, right, negativeRaise) == 1) {
                return 1.0;
            }
            return Math.pow(left, right);
        }

        @Specialization(rewriteOn = UnexpectedResultException.class)
        double doDD(VirtualFrame frame, double left, double right, @SuppressWarnings("unused") PNone none,
                        @Shared("powCall") @Cached("create(__POW__)") LookupAndCallTernaryNode callPow,
                        @Shared("negativeRaise") @Cached BranchProfile negativeRaise) throws UnexpectedResultException {
            if (doSpecialCases(left, right, negativeRaise) == 1) {
                return 1.0;
            }
            if (left < 0 && Double.isFinite(left) && Double.isFinite(right) && (right % 1 != 0)) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                // Negative numbers raised to fractional powers become complex.
                throw new UnexpectedResultException(callPow.execute(frame, factory().createComplex(left, 0), factory().createComplex(right, 0), none));
            }
            return Math.pow(left, right);
        }

        @Specialization(replaces = "doDD")
        Object doDDToComplex(VirtualFrame frame, double left, double right, PNone none,
                        @Shared("powCall") @Cached("create(__POW__)") LookupAndCallTernaryNode callPow,
                        @Shared("negativeRaise") @Cached BranchProfile negativeRaise) {
            if (doSpecialCases(left, right, negativeRaise) == 1) {
                return 1.0;
            }
            if (left < 0 && Double.isFinite(left) && Double.isFinite(right) && (right % 1 != 0)) {
                // Negative numbers raised to fractional powers become complex.
                return callPow.execute(frame, factory().createComplex(left, 0), factory().createComplex(right, 0), none);
            }
            return Math.pow(left, right);
        }

        @Specialization(rewriteOn = UnexpectedResultException.class)
        double doDL(VirtualFrame frame, long left, double right, PNone none,
                        @Shared("powCall") @Cached("create(__POW__)") LookupAndCallTernaryNode callPow,
                        @Shared("negativeRaise") @Cached BranchProfile negativeRaise) throws UnexpectedResultException {
            return doDD(frame, left, right, none, callPow, negativeRaise);
        }

        @Specialization(replaces = "doDL")
        Object doDLComplex(VirtualFrame frame, long left, double right, PNone none,
                        @Shared("powCall") @Cached("create(__POW__)") LookupAndCallTernaryNode callPow,
                        @Shared("negativeRaise") @Cached BranchProfile negativeRaise) {
            return doDDToComplex(frame, left, right, none, callPow, negativeRaise);
        }

        @Specialization(rewriteOn = UnexpectedResultException.class)
        double doDPi(VirtualFrame frame, PInt left, double right, @SuppressWarnings("unused") PNone none,
                        @Shared("powCall") @Cached("create(__POW__)") LookupAndCallTernaryNode callPow,
                        @Shared("negativeRaise") @Cached BranchProfile negativeRaise) throws UnexpectedResultException {
            return doDD(frame, left.doubleValueWithOverflow(getRaiseNode()), right, none, callPow, negativeRaise);
        }

        @Specialization(replaces = "doDPi")
        Object doDPiToComplex(VirtualFrame frame, PInt left, double right, @SuppressWarnings("unused") PNone none,
                        @Shared("powCall") @Cached("create(__POW__)") LookupAndCallTernaryNode callPow,
                        @Shared("negativeRaise") @Cached BranchProfile negativeRaise) {
            return doDDToComplex(frame, left.doubleValueWithOverflow(getRaiseNode()), right, none, callPow, negativeRaise);
        }

        @Specialization
        Object doGeneric(VirtualFrame frame, Object left, Object right, Object mod,
                        @CachedLibrary(limit = "5") PythonObjectLibrary lib,
                        @Shared("powCall") @Cached("create(__POW__)") LookupAndCallTernaryNode callPow,
                        @Shared("negativeRaise") @Cached BranchProfile negativeRaise) {
            if (!(mod instanceof PNone)) {
                throw raise(PythonBuiltinClassType.TypeError, "pow() 3rd argument not allowed unless all arguments are integers");
            }
            double leftDouble;
            double rightDouble;
            if (lib.canBeJavaDouble(left)) {
                leftDouble = lib.asJavaDouble(left);
            } else if (left instanceof PInt) {
                leftDouble = ((PInt) left).doubleValue();
            } else {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            if (lib.canBeJavaDouble(right)) {
                rightDouble = lib.asJavaDouble(right);
            } else if (right instanceof PInt) {
                rightDouble = ((PInt) right).doubleValue();
            } else {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            return doDDToComplex(frame, leftDouble, rightDouble, PNone.NONE, callPow, negativeRaise);
        }
    }

    @Builtin(name = __RFLOORDIV__, minNumOfPositionalArgs = 2, reverseOperation = true)
    @Builtin(name = __FLOORDIV__, minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class FloorDivNode extends FloatBinaryBuiltinNode {
        @Specialization
        double doDL(double left, long right) {
            raiseDivisionByZero(right == 0);
            return Math.floor(left / right);
        }

        @Specialization
        double doDL(double left, PInt right) {
            raiseDivisionByZero(right.isZero());
            return Math.floor(left / right.doubleValueWithOverflow(getRaiseNode()));
        }

        @Specialization
        double doDD(double left, double right) {
            raiseDivisionByZero(right == 0.0);
            return Math.floor(left / right);
        }

        @Specialization
        double doLD(long left, double right) {
            raiseDivisionByZero(right == 0.0);
            return Math.floor(left / right);
        }

        @Specialization
        double doPiD(PInt left, double right) {
            raiseDivisionByZero(right == 0.0);
            return Math.floor(left.doubleValueWithOverflow(getRaiseNode()) / right);
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __RDIVMOD__, minNumOfPositionalArgs = 2, reverseOperation = true)
    @Builtin(name = __DIVMOD__, minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class DivModNode extends FloatBinaryBuiltinNode {
        @Specialization
        PTuple doDL(double left, long right) {
            raiseDivisionByZero(right == 0);
            return factory().createTuple(new Object[]{Math.floor(left / right), ModNode.op(left, right)});
        }

        @Specialization
        PTuple doDD(double left, double right) {
            raiseDivisionByZero(right == 0.0);
            return factory().createTuple(new Object[]{Math.floor(left / right), ModNode.op(left, right)});
        }

        @Specialization
        PTuple doLD(long left, double right) {
            raiseDivisionByZero(right == 0.0);
            return factory().createTuple(new Object[]{Math.floor(left / right), ModNode.op(left, right)});
        }

        @Specialization(guards = {"accepts(left)", "accepts(right)"})
        PTuple doGenericFloat(VirtualFrame frame, Object left, Object right,
                        @Cached FloorDivNode floorDivNode,
                        @Cached ModNode modNode) {
            return factory().createTuple(new Object[]{floorDivNode.execute(frame, left, right), modNode.execute(frame, left, right)});
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        protected static boolean accepts(Object obj) {
            return obj instanceof Double || obj instanceof Integer || obj instanceof Long || obj instanceof PInt || obj instanceof PFloat;
        }
    }

    @Builtin(name = SpecialMethodNames.__HASH__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class HashNode extends PythonUnaryBuiltinNode {
        protected boolean noDecimals(float num) {
            return num % 1 == 0;
        }

        protected boolean noDecimals(double num) {
            return num % 1 == 0;
        }

        protected boolean noDecimals(PFloat num) {
            return num.getValue() % 1 == 0;
        }

        @Specialization(guards = {"noDecimals(self)"})
        long hashFloatNoDecimals(float self) {
            return (long) self;
        }

        @Specialization(guards = {"!noDecimals(self)"})
        @TruffleBoundary
        long hashFloatWithDecimals(float self) {
            return Float.valueOf(self).hashCode();
        }

        @Specialization(guards = {"noDecimals(self)"})
        long hashDoubleNoDecimals(double self) {
            return (long) self;
        }

        @Specialization(guards = {"!noDecimals(self)"})
        @TruffleBoundary
        long hashDoubleWithDecimals(double self) {
            return Double.valueOf(self).hashCode();
        }

        @Specialization(guards = {"noDecimals(self)"})
        long hashPFloatNoDecimals(PFloat self) {
            return (long) self.getValue();
        }

        @Specialization(guards = {"!noDecimals(self)"})
        @TruffleBoundary
        long hashPFloatWithDecimals(PFloat self) {
            return Double.valueOf(self.getValue()).hashCode();
        }
    }

    @Builtin(name = "fromhex", minNumOfPositionalArgs = 2, isClassmethod = true)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class FromHexNode extends PythonBuiltinNode {

        private static final String INVALID_STRING = "invalid hexadecimal floating-point string";

        @TruffleBoundary
        private double fromHex(String arg) {
            boolean negative = false;
            String str = arg.trim().toLowerCase();

            if (str.isEmpty()) {
                throw raise(PythonErrorType.ValueError, INVALID_STRING);
            } else if (str.equals("inf") || str.equals("infinity") || str.equals("+inf") || str.equals("+infinity")) {
                return Double.POSITIVE_INFINITY;
            } else if (str.equals("-inf") || str.equals("-infinity")) {
                return Double.NEGATIVE_INFINITY;
            } else if (str.equals("nan") || str.equals("+nan") || str.equals("-nan")) {
                return Double.NaN;
            }

            if (str.charAt(0) == '+') {
                str = str.substring(1);
            } else if (str.charAt(0) == '-') {
                str = str.substring(1);
                negative = true;
            }

            if (str.isEmpty()) {
                throw raise(PythonErrorType.ValueError, INVALID_STRING);
            }

            if (!str.startsWith("0x")) {
                str = "0x" + str;
            }

            if (negative) {
                str = "-" + str;
            }

            if (str.indexOf('p') == -1) {
                str = str + "p0";
            }

            try {
                double result = Double.parseDouble(str);
                if (Double.isInfinite(result)) {
                    throw raise(PythonErrorType.OverflowError, ErrorMessages.HEX_VALUE_TOO_LARGE_AS_FLOAT);
                }

                return result;
            } catch (NumberFormatException ex) {
                throw raise(PythonErrorType.ValueError, INVALID_STRING);
            }
        }

        @Specialization(guards = "isPythonBuiltinClass(cl)")
        public double fromhexFloat(@SuppressWarnings("unused") Object cl, String arg) {
            return fromHex(arg);
        }

        @Specialization(guards = "!isPythonBuiltinClass(cl)")
        public Object fromhexO(Object cl, String arg,
                        @Cached("create(__CALL__)") LookupAndCallVarargsNode constr) {
            double value = fromHex(arg);
            return constr.execute(null, cl, new Object[]{cl, value});
        }

        @Fallback
        @SuppressWarnings("unused")
        public double fromhex(Object object, Object arg) {
            throw raise(PythonErrorType.TypeError, ErrorMessages.BAD_ARG_TYPE_FOR_BUILTIN_OP);
        }
    }

    @Builtin(name = "hex", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class HexNode extends PythonBuiltinNode {

        @TruffleBoundary
        private static String makeHexNumber(double value) {

            if (Double.isNaN(value)) {
                return "nan";
            } else if (Double.POSITIVE_INFINITY == value) {
                return "inf";
            } else if (Double.NEGATIVE_INFINITY == value) {
                return "-inf";
            } else if (Double.compare(value, 0d) == 0) {
                return "0x0.0p+0";
            } else if (Double.compare(value, -0d) == 0) {
                return "-0x0.0p+0";
            }

            String result = Double.toHexString(value);
            int length = result.length();
            boolean start_exponent = false;
            StringBuilder sb = new StringBuilder(length + 1);
            int padding = value > 0 ? 17 : 18;
            for (int i = 0; i < length; i++) {
                char c = result.charAt(i);
                if (c == 'p') {
                    for (int pad = i; pad < padding; pad++) {
                        sb.append('0');
                    }
                    start_exponent = true;
                } else if (start_exponent) {
                    if (c != '-') {
                        sb.append('+');
                    }
                    start_exponent = false;
                }
                sb.append(c);
            }
            return sb.toString();
        }

        @Specialization
        public String hexD(double value) {
            return makeHexNumber(value);
        }
    }

    @Builtin(name = __RMOD__, minNumOfPositionalArgs = 2, reverseOperation = true)
    @Builtin(name = __MOD__, minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class ModNode extends FloatBinaryBuiltinNode {
        @Specialization
        double doDL(double left, long right) {
            raiseDivisionByZero(right == 0);
            return op(left, right);
        }

        @Specialization
        double doDL(double left, PInt right) {
            raiseDivisionByZero(right.isZero());
            return op(left, right.doubleValue());
        }

        @Specialization
        double doDD(double left, double right) {
            raiseDivisionByZero(right == 0.0);
            return op(left, right);
        }

        @Specialization
        double doLD(long left, double right) {
            raiseDivisionByZero(right == 0.0);
            return op(left, right);
        }

        @Specialization
        double doPiD(PInt left, double right) {
            raiseDivisionByZero(right == 0.0);
            return op(left.doubleValue(), right);
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object right, Object left) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        public static double op(double left, double right) {
            double mod = left % right;
            if (mod != 0.0) {
                if ((right < 0) != (mod < 0)) {
                    mod += right;
                }
            } else {
                mod = right < 0 ? -0.0 : 0.0;
            }
            return mod;
        }
    }

    @Builtin(name = __RTRUEDIV__, minNumOfPositionalArgs = 2, reverseOperation = true)
    @Builtin(name = __TRUEDIV__, minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class DivNode extends FloatBinaryBuiltinNode {
        @Specialization
        double doDD(double left, double right) {
            return left / right;
        }

        @Specialization
        double doDL(double left, long right) {
            return left / right;
        }

        @Specialization
        double doDPi(double left, PInt right) {
            return left / right.doubleValueWithOverflow(getRaiseNode());
        }

        @Specialization
        double div(long left, double right) {
            return left / right;
        }

        @Specialization
        double div(PInt left, double right) {
            return left.doubleValueWithOverflow(getRaiseNode()) / right;
        }

        @Specialization
        Object doDP(VirtualFrame frame, long left, PythonNativeObject right,
                        @Cached FromNativeSubclassNode getFloat) {
            Double rPrimitive = getFloat.execute(frame, right);
            if (rPrimitive != null) {
                return left / rPrimitive;
            } else {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __ROUND__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class RoundNode extends PythonBinaryBuiltinNode {
        /**
         * The logic is borrowed from Jython.
         */
        @TruffleBoundary
        private static double op(double x, long n) {
            // (Slightly less than) n*log2(10).
            float nlog2_10 = 3.3219f * n;

            // x = a * 2^b and a<2.
            int b = Math.getExponent(x);

            if (nlog2_10 > 52 - b) {
                // When n*log2(10) > nmax, the lsb of abs(x) is >1, so x rounds to itself.
                return x;
            } else if (nlog2_10 < -(b + 2)) {
                // When n*log2(10) < -(b+2), abs(x)<0.5*10^n so x rounds to (signed) zero.
                return Math.copySign(0.0, x);
            } else {
                // We have to work it out properly.
                BigDecimal xx = new BigDecimal(x);
                BigDecimal rr = xx.setScale((int) n, RoundingMode.HALF_EVEN);
                return rr.doubleValue();
            }
        }

        @Specialization
        double round(double x, long n) {
            if (Double.isNaN(x) || Double.isInfinite(x) || x == 0.0) {
                // nans, infinities and zeros round to themselves
                return x;
            }
            double d = op(x, n);
            if (Double.isInfinite(d)) {
                throw raise(OverflowError, ErrorMessages.ROUNDED_VALUE_TOO_LARGE);
            }
            return d;
        }

        @Specialization
        double round(double x, PInt n) {
            long nLong;
            if (n.compareTo(Long.MAX_VALUE) > 0) {
                nLong = Long.MAX_VALUE;
            } else if (n.compareTo(Long.MIN_VALUE) < 0) {
                nLong = Long.MIN_VALUE;
            } else {
                nLong = n.longValue();
            }
            return round(x, nLong);
        }

        @Specialization
        long round(double x, @SuppressWarnings("unused") PNone none,
                        @Cached("createBinaryProfile()") ConditionProfile nanProfile,
                        @Cached("createBinaryProfile()") ConditionProfile infProfile) {
            if (nanProfile.profile(Double.isNaN(x))) {
                throw raise(PythonErrorType.ValueError, ErrorMessages.CANNOT_CONVERT_S_TO_INT, "float NaN");
            }
            if (infProfile.profile(Double.isInfinite(x))) {
                throw raise(PythonErrorType.OverflowError, ErrorMessages.CANNOT_CONVERT_S_TO_INT, "float infinity");
            }
            return (long) round(x, 0);
        }

        @Fallback
        double roundFallback(Object x, Object n) {
            if (MathGuards.isFloat(x)) {
                throw raise(PythonErrorType.TypeError, ErrorMessages.OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER, n);
            } else {
                throw raise(PythonErrorType.TypeError, ErrorMessages.DESCRIPTOR_REQUIRES_OBJ, "__round__", "float", x);
            }
        }
    }

    @Builtin(name = __EQ__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class EqNode extends PythonBinaryBuiltinNode {

        @Specialization
        boolean eqDbDb(double a, double b) {
            return a == b;
        }

        @Specialization
        boolean eqDbLn(double a, long b) {
            return compareDoubleToLong(a, b) == 0;
        }

        @Specialization
        boolean eqDbPI(double a, PInt b) {
            return compareDoubleToLargeInt(a, b) == 0;
        }

        @Specialization
        Object eqPDb(VirtualFrame frame, PythonNativeObject left, double right,
                        @Cached FromNativeSubclassNode getFloat) {
            return getFloat.execute(frame, left) == right;
        }

        @Specialization
        Object eqPDb(VirtualFrame frame, PythonNativeObject left, long right,
                        @Cached FromNativeSubclassNode getFloat) {
            return getFloat.execute(frame, left) == right;
        }

        @Specialization
        Object eqPDb(VirtualFrame frame, PythonNativeObject left, PInt right,
                        @Cached FromNativeSubclassNode getFloat) {
            return eqDbPI(getFloat.execute(frame, left), right);
        }

        @Fallback
        @SuppressWarnings("unused")
        PNotImplemented eq(Object a, Object b) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        // adapted from CPython's float_richcompare in floatobject.c
        public static double compareDoubleToLong(double v, long w) {
            if (!Double.isFinite(v)) {
                return v;
            }
            int vsign = v == 0.0 ? 0 : v < 0.0 ? -1 : 1;
            int wsign = Long.signum(w);
            if (vsign != wsign) {
                return vsign - wsign;
            }
            if (w > -0x1000000000000L && w < 0x1000000000000L) {    // w is at most 48 bits
                return v - w;
            } else {
                return compareUsingBigDecimal(v, PInt.longToBigInteger(w));
            }
        }

        // adapted from CPython's float_richcompare in floatobject.c
        public static double compareDoubleToLargeInt(double v, PInt w) {
            if (!Double.isFinite(v)) {
                return v;
            }
            int vsign = v == 0.0 ? 0 : v < 0.0 ? -1 : 1;
            int wsign = w.isZero() ? 0 : w.isNegative() ? -1 : 1;
            if (vsign != wsign) {
                return vsign - wsign;
            }
            if (w.bitLength() <= 48) {
                return v - w.doubleValue();
            } else {
                return compareUsingBigDecimal(v, w.getValue());
            }
        }

        @TruffleBoundary
        private static double compareUsingBigDecimal(double v, BigInteger w) {
            return new BigDecimal(v).compareTo(new BigDecimal(w));
        }
    }

    @Builtin(name = __NE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class NeNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean neDbDb(double a, double b) {
            return a != b;
        }

        @Specialization
        boolean neDbLn(double a, long b) {
            return EqNode.compareDoubleToLong(a, b) != 0;
        }

        @Specialization
        boolean neDbPI(double a, PInt b) {
            return EqNode.compareDoubleToLargeInt(a, b) != 0;
        }

        @Specialization
        Object eqPDb(VirtualFrame frame, PythonNativeObject left, double right,
                        @Cached FromNativeSubclassNode getFloat) {
            return getFloat.execute(frame, left) != right;
        }

        @Specialization
        Object eqPDb(VirtualFrame frame, PythonNativeObject left, long right,
                        @Cached FromNativeSubclassNode getFloat) {
            return getFloat.execute(frame, left) != right;
        }

        @Specialization
        Object eqPDb(VirtualFrame frame, PythonNativeObject left, PInt right,
                        @Cached FromNativeSubclassNode getFloat) {
            return neDbPI(getFloat.execute(frame, left), right);
        }

        @Fallback
        @SuppressWarnings("unused")
        PNotImplemented eq(Object a, Object b) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __LT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class LtNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean doDD(double x, double y) {
            return x < y;
        }

        @Specialization
        boolean doDL(double x, long y) {
            return EqNode.compareDoubleToLong(x, y) < 0;
        }

        @Specialization
        boolean doPI(double x, PInt y) {
            return EqNode.compareDoubleToLargeInt(x, y) < 0;
        }

        @Specialization(guards = "fromNativeNode.isFloatSubtype(frame, y, getClass, isSubtype, context)", limit = "1")
        boolean doDN(VirtualFrame frame, double x, PythonNativeObject y,
                        @SuppressWarnings("unused") @CachedContext(PythonLanguage.class) PythonContext context,
                        @SuppressWarnings("unused") @Cached GetClassNode getClass,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtype,
                        @Cached FromNativeSubclassNode fromNativeNode) {
            return x < fromNativeNode.execute(frame, y);
        }

        @Specialization(guards = {
                        "nativeLeft.isFloatSubtype(frame, x, getClass, isSubtype, context)",
                        "nativeRight.isFloatSubtype(frame, y, getClass, isSubtype, context)"}, limit = "1")
        boolean doDN(VirtualFrame frame, PythonNativeObject x, PythonNativeObject y,
                        @SuppressWarnings("unused") @CachedContext(PythonLanguage.class) PythonContext context,
                        @SuppressWarnings("unused") @Cached GetClassNode getClass,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtype,
                        @Cached FromNativeSubclassNode nativeLeft,
                        @Cached FromNativeSubclassNode nativeRight) {
            return nativeLeft.execute(frame, x) < nativeRight.execute(frame, y);
        }

        @Specialization(guards = "fromNativeNode.isFloatSubtype(frame, x, getClass, isSubtype, context)", limit = "1")
        boolean doDN(VirtualFrame frame, PythonNativeObject x, double y,
                        @SuppressWarnings("unused") @CachedContext(PythonLanguage.class) PythonContext context,
                        @SuppressWarnings("unused") @Cached GetClassNode getClass,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtype,
                        @Cached FromNativeSubclassNode fromNativeNode) {
            return fromNativeNode.execute(frame, x) < y;
        }

        @Specialization(guards = "fromNativeNode.isFloatSubtype(frame, x, getClass, isSubtype, context)", limit = "1")
        boolean doDN(VirtualFrame frame, PythonNativeObject x, long y,
                        @SuppressWarnings("unused") @CachedContext(PythonLanguage.class) PythonContext context,
                        @SuppressWarnings("unused") @Cached GetClassNode getClass,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtype,
                        @Cached FromNativeSubclassNode fromNativeNode) {
            return fromNativeNode.execute(frame, x) < y;
        }

        @Fallback
        @SuppressWarnings("unused")
        PNotImplemented doGeneric(Object a, Object b) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __LE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class LeNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean doDD(double x, double y) {
            return x <= y;
        }

        @Specialization
        boolean doDL(double x, long y) {
            return EqNode.compareDoubleToLong(x, y) <= 0;
        }

        @Specialization
        boolean doPI(double x, PInt y) {
            return EqNode.compareDoubleToLargeInt(x, y) <= 0;
        }

        @Specialization(guards = "fromNativeNode.isFloatSubtype(frame, y, getClass, isSubtype, context)", limit = "1")
        boolean doDN(VirtualFrame frame, double x, PythonNativeObject y,
                        @SuppressWarnings("unused") @CachedContext(PythonLanguage.class) PythonContext context,
                        @SuppressWarnings("unused") @Cached GetClassNode getClass,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtype,
                        @Cached FromNativeSubclassNode fromNativeNode) {
            return x <= fromNativeNode.execute(frame, y);
        }

        @Specialization(guards = {
                        "nativeLeft.isFloatSubtype(frame, x, getClass, isSubtype, context)",
                        "nativeRight.isFloatSubtype(frame, y, getClass, isSubtype, context)"}, limit = "1")
        boolean doNN(VirtualFrame frame, PythonNativeObject x, PythonNativeObject y,
                        @SuppressWarnings("unused") @CachedContext(PythonLanguage.class) PythonContext context,
                        @SuppressWarnings("unused") @Cached GetClassNode getClass,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtype,
                        @Cached FromNativeSubclassNode nativeLeft,
                        @Cached FromNativeSubclassNode nativeRight) {
            return nativeLeft.execute(frame, x) <= nativeRight.execute(frame, y);
        }

        @Specialization(guards = "fromNativeNode.isFloatSubtype(frame, x, getClass, isSubtype, context)", limit = "1")
        boolean doND(VirtualFrame frame, PythonNativeObject x, double y,
                        @SuppressWarnings("unused") @CachedContext(PythonLanguage.class) PythonContext context,
                        @SuppressWarnings("unused") @Cached GetClassNode getClass,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtype,
                        @Cached FromNativeSubclassNode fromNativeNode) {
            return fromNativeNode.execute(frame, x) <= y;
        }

        @Specialization(guards = "fromNativeNode.isFloatSubtype(frame, x, getClass, isSubtype, context)", limit = "1")
        boolean doNL(VirtualFrame frame, PythonNativeObject x, long y,
                        @SuppressWarnings("unused") @CachedContext(PythonLanguage.class) PythonContext context,
                        @SuppressWarnings("unused") @Cached GetClassNode getClass,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtype,
                        @Cached FromNativeSubclassNode fromNativeNode) {
            return fromNativeNode.execute(frame, x) <= y;
        }

        @Fallback
        @SuppressWarnings("unused")
        PNotImplemented doGeneric(Object a, Object b) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __GT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class GtNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean doDD(double x, double y) {
            return x > y;
        }

        @Specialization
        boolean doDL(double x, long y) {
            return EqNode.compareDoubleToLong(x, y) > 0;
        }

        @Specialization
        boolean doPI(double x, PInt y) {
            return EqNode.compareDoubleToLargeInt(x, y) > 0;
        }

        @Specialization(guards = "fromNativeNode.isFloatSubtype(frame, y, getClass, isSubtype, context)", limit = "1")
        boolean doDN(VirtualFrame frame, double x, PythonNativeObject y,
                        @SuppressWarnings("unused") @CachedContext(PythonLanguage.class) PythonContext context,
                        @SuppressWarnings("unused") @Cached GetClassNode getClass,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtype,
                        @Cached FromNativeSubclassNode fromNativeNode) {
            return x > fromNativeNode.execute(frame, y);
        }

        @Specialization(guards = {
                        "nativeLeft.isFloatSubtype(frame, x, getClass, isSubtype, context)",
                        "nativeRight.isFloatSubtype(frame, y, getClass, isSubtype, context)"}, limit = "1")
        boolean doNN(VirtualFrame frame, PythonNativeObject x, PythonNativeObject y,
                        @SuppressWarnings("unused") @CachedContext(PythonLanguage.class) PythonContext context,
                        @SuppressWarnings("unused") @Cached GetClassNode getClass,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtype,
                        @Cached FromNativeSubclassNode nativeLeft,
                        @Cached FromNativeSubclassNode nativeRight) {
            return nativeLeft.execute(frame, x) > nativeRight.execute(frame, y);
        }

        @Specialization(guards = "fromNativeNode.isFloatSubtype(frame, x, getClass, isSubtype, context)", limit = "1")
        boolean doND(VirtualFrame frame, PythonNativeObject x, double y,
                        @SuppressWarnings("unused") @CachedContext(PythonLanguage.class) PythonContext context,
                        @SuppressWarnings("unused") @Cached GetClassNode getClass,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtype,
                        @Cached FromNativeSubclassNode fromNativeNode) {
            return fromNativeNode.execute(frame, x) > y;
        }

        @Specialization(guards = "fromNativeNode.isFloatSubtype(frame, x, getClass, isSubtype, context)", limit = "1")
        boolean doNL(VirtualFrame frame, PythonNativeObject x, long y,
                        @SuppressWarnings("unused") @CachedContext(PythonLanguage.class) PythonContext context,
                        @SuppressWarnings("unused") @Cached GetClassNode getClass,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtype,
                        @Cached FromNativeSubclassNode fromNativeNode) {
            return fromNativeNode.execute(frame, x) > y;
        }

        @Fallback
        @SuppressWarnings("unused")
        PNotImplemented doGeneric(Object a, Object b) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __GE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class GeNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean doDD(double x, double y) {
            return x >= y;
        }

        @Specialization
        boolean doDL(double x, long y) {
            return EqNode.compareDoubleToLong(x, y) >= 0;
        }

        @Specialization
        boolean doPI(double x, PInt y) {
            return EqNode.compareDoubleToLargeInt(x, y) >= 0;
        }

        @Specialization(guards = "fromNativeNode.isFloatSubtype(frame, y, getClass, isSubtype, context)", limit = "1")
        boolean doDN(VirtualFrame frame, double x, PythonNativeObject y,
                        @SuppressWarnings("unused") @CachedContext(PythonLanguage.class) PythonContext context,
                        @SuppressWarnings("unused") @Cached GetClassNode getClass,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtype,
                        @Cached FromNativeSubclassNode fromNativeNode) {
            return x >= fromNativeNode.execute(frame, y);
        }

        @Specialization(guards = {
                        "nativeLeft.isFloatSubtype(frame, x, getClass, isSubtype, context)",
                        "nativeRight.isFloatSubtype(frame, y, getClass, isSubtype, context)"}, limit = "1")
        boolean doNN(VirtualFrame frame, PythonNativeObject x, PythonNativeObject y,
                        @SuppressWarnings("unused") @CachedContext(PythonLanguage.class) PythonContext context,
                        @SuppressWarnings("unused") @Cached GetClassNode getClass,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtype,
                        @Cached FromNativeSubclassNode nativeLeft,
                        @Cached FromNativeSubclassNode nativeRight) {
            return nativeLeft.execute(frame, x) >= nativeRight.execute(frame, y);
        }

        @Specialization(guards = "fromNativeNode.isFloatSubtype(frame, x, getClass, isSubtype, context)", limit = "1")
        boolean doND(VirtualFrame frame, PythonNativeObject x, double y,
                        @SuppressWarnings("unused") @CachedContext(PythonLanguage.class) PythonContext context,
                        @SuppressWarnings("unused") @Cached GetClassNode getClass,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtype,
                        @Cached FromNativeSubclassNode fromNativeNode) {
            return fromNativeNode.execute(frame, x) >= y;
        }

        @Specialization(guards = "fromNativeNode.isFloatSubtype(frame, x, getClass, isSubtype, context)", limit = "1")
        boolean doNL(VirtualFrame frame, PythonNativeObject x, long y,
                        @SuppressWarnings("unused") @CachedContext(PythonLanguage.class) PythonContext context,
                        @SuppressWarnings("unused") @Cached GetClassNode getClass,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtype,
                        @Cached FromNativeSubclassNode fromNativeNode) {
            return fromNativeNode.execute(frame, x) >= y;
        }

        @Fallback
        @SuppressWarnings("unused")
        PNotImplemented doGeneric(Object a, Object b) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __POS__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class PosNode extends PythonUnaryBuiltinNode {
        @Specialization
        double pos(double arg) {
            return arg;
        }
    }

    @Builtin(name = __NEG__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class NegNode extends PythonUnaryBuiltinNode {
        @Specialization
        double neg(double arg) {
            return -arg;
        }
    }

    @GenerateNodeFactory
    @Builtin(name = "real", minNumOfPositionalArgs = 1, isGetter = true, doc = "the real part of a complex number")
    abstract static class RealNode extends PythonBuiltinNode {

        @Specialization
        double get(double self) {
            return self;
        }

        @Specialization(guards = "cannotBeOverridden(lib.getLazyPythonClass(self))", limit = "2")
        PFloat getPFloat(PFloat self,
                        @SuppressWarnings("unused") @CachedLibrary("self") PythonObjectLibrary lib) {
            return self;
        }

        @Specialization(guards = "!cannotBeOverridden(lib.getLazyPythonClass(self))", limit = "2")
        PFloat getPFloatOverriden(PFloat self,
                        @SuppressWarnings("unused") @CachedLibrary("self") PythonObjectLibrary lib) {
            return factory().createFloat(self.getValue());
        }
    }

    @GenerateNodeFactory
    @Builtin(name = "imag", minNumOfPositionalArgs = 1, isGetter = true, doc = "the imaginary part of a complex number")
    abstract static class ImagNode extends PythonBuiltinNode {

        @Specialization
        double get(@SuppressWarnings("unused") Object self) {
            return 0;
        }

    }

    @GenerateNodeFactory
    @Builtin(name = "as_integer_ratio", minNumOfPositionalArgs = 1)
    abstract static class AsIntegerRatio extends PythonBuiltinNode {

        @Specialization
        PTuple get(double self,
                        @Cached("createBinaryProfile()") ConditionProfile nanProfile,
                        @Cached("createBinaryProfile()") ConditionProfile infProfile) {
            if (nanProfile.profile(Double.isNaN(self))) {
                throw raise(PythonErrorType.ValueError, ErrorMessages.CANNOT_CONVERT_S_TO_INT_RATIO, "NaN");
            }
            if (infProfile.profile(Double.isInfinite(self))) {
                throw raise(PythonErrorType.OverflowError, ErrorMessages.CANNOT_CONVERT_S_TO_INT_RATIO, "Infinity");
            }

            // At the first time find mantissa and exponent. This is functionanlity of Math.frexp
            // node basically.
            int exponent = 0;
            double mantissa = 0.0;

            if (!(self == 0.0 || self == -0.0)) {
                boolean neg = false;
                mantissa = self;

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
                if (neg) {
                    mantissa = -mantissa;
                }
            }

            // count the ratio
            return factory().createTuple(countIt(mantissa, exponent));
        }

        @TruffleBoundary
        private Object[] countIt(double manitssa, int exponent) {
            double m = manitssa;
            int e = exponent;
            for (int i = 0; i < 300 && Double.compare(m, Math.floor(m)) != 0; i++) {
                m *= 2.0;
                e--;
            }

            BigInteger numerator = BigInteger.valueOf(((Double) m).longValue());
            BigInteger denominator = BigInteger.ONE;
            BigInteger py_exponent = denominator.shiftLeft(Math.abs(e));
            if (e > 0) {
                numerator = numerator.multiply(py_exponent);
            } else {
                denominator = py_exponent;
            }
            if (numerator.bitLength() < Long.SIZE && denominator.bitLength() < Long.SIZE) {
                return new Object[]{numerator.longValue(), denominator.longValue()};
            }
            return new Object[]{factory().createInt(numerator), factory().createInt(denominator)};
        }
    }

    @GenerateNodeFactory
    @Builtin(name = "conjugate", minNumOfPositionalArgs = 1, doc = "Returns self, the complex conjugate of any float.")
    abstract static class ConjugateNode extends RealNode {

    }

    @Builtin(name = __TRUNC__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class TruncNode extends PythonUnaryBuiltinNode {

        @TruffleBoundary
        protected static int truncate(double value) {
            return (int) (value < 0 ? Math.ceil(value) : Math.floor(value));
        }

        @Specialization
        int trunc(double value,
                        @Cached("createBinaryProfile()") ConditionProfile nanProfile,
                        @Cached("createBinaryProfile()") ConditionProfile infProfile) {
            if (nanProfile.profile(Double.isNaN(value))) {
                throw raise(PythonErrorType.ValueError, ErrorMessages.CANNOT_CONVERT_S_TO_INT, "float NaN");
            }
            if (infProfile.profile(Double.isInfinite(value))) {
                throw raise(PythonErrorType.OverflowError, ErrorMessages.CANNOT_CONVERT_S_TO_INT, "float infinity");
            }
            return truncate(value);
        }

        @Specialization
        int trunc(PFloat pValue,
                        @Cached("createBinaryProfile()") ConditionProfile nanProfile,
                        @Cached("createBinaryProfile()") ConditionProfile infProfile) {
            double value = pValue.getValue();
            if (nanProfile.profile(Double.isNaN(value))) {
                throw raise(PythonErrorType.ValueError, ErrorMessages.CANNOT_CONVERT_S_TO_INT, "float NaN");
            }
            if (infProfile.profile(Double.isInfinite(value))) {
                throw raise(PythonErrorType.OverflowError, ErrorMessages.CANNOT_CONVERT_S_TO_INT, "float infinity");
            }
            return truncate(value);
        }

    }

    @Builtin(name = __GETFORMAT__, minNumOfPositionalArgs = 2, isClassmethod = true)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class GetFormatNode extends PythonBinaryBuiltinNode {
        private static String getDetectedEndianess() {
            try {
                ByteOrder byteOrder = ByteOrder.nativeOrder();
                if (byteOrder == ByteOrder.BIG_ENDIAN) {
                    return "IEEE, big-endian";
                } else if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
                    return "IEEE, little-endian";
                }
            } catch (Error ignored) {
            }
            return "unknown";
        }

        protected boolean isValidTypeStr(String typeStr) {
            return typeStr.equals("float") || typeStr.equals("double");
        }

        @Specialization(guards = "isValidTypeStr(typeStr)")
        String getFormat(@SuppressWarnings("unused") Object cls, @SuppressWarnings("unused") String typeStr) {
            return getDetectedEndianess();
        }

        @Fallback
        String getFormat(@SuppressWarnings("unused") Object cls, @SuppressWarnings("unused") Object typeStr) {
            throw raise(PythonErrorType.ValueError, ErrorMessages.ARG_D_MUST_BE_S_OR_S, "__getformat__()", 1, "double", "float");
        }
    }

    private abstract static class FloatBinaryBuiltinNode extends PythonBinaryBuiltinNode {
        protected void raiseDivisionByZero(boolean cond) {
            if (cond) {
                throw raise(PythonErrorType.ZeroDivisionError, ErrorMessages.DIVISION_BY_ZERO);
            }
        }
    }

    @Builtin(name = "is_integer", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsIntegerNode extends PythonUnaryBuiltinNode {
        @Specialization
        boolean isInteger(double value) {
            return Double.isFinite(value) && (long) value == value;
        }

        @Specialization
        boolean trunc(PFloat pValue) {
            return isInteger(pValue.getValue());
        }

    }
}
