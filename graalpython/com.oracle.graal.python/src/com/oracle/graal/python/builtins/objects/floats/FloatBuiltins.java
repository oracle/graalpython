/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates.
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
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.nodes.BuiltinNames.J_FLOAT;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ABS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___BOOL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___DIVMOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___FLOAT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___FLOORDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___FORMAT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GETFORMAT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GETNEWARGS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___HASH__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___LE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___LT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___MOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___MUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NEG__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___POS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___POW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RDIVMOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RFLOORDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RMOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ROUND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RPOW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RSUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RTRUEDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___STR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___TRUEDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___TRUNC__;
import static com.oracle.graal.python.runtime.formatting.FormattingUtils.validateForFloat;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.ByteOrder;
import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ArgumentClinic.ClinicConversion;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.MathGuards;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.FromNativeSubclassNode;
import com.oracle.graal.python.builtins.objects.common.FormatNodeBase;
import com.oracle.graal.python.builtins.objects.floats.FloatBuiltinsClinicProviders.AsIntegerRatioClinicProviderGen;
import com.oracle.graal.python.builtins.objects.floats.FloatBuiltinsClinicProviders.FormatNodeClinicProviderGen;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyObjectHashNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.call.special.LookupAndCallTernaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallVarargsNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.InlinedGetClassNode.GetPythonObjectClassNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaDoubleNode;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.formatting.FloatFormatter;
import com.oracle.graal.python.runtime.formatting.InternalFormat;
import com.oracle.graal.python.runtime.formatting.InternalFormat.Spec;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PFloat)
public final class FloatBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return FloatBuiltinsFactory.getFactories();
    }

    public static double asDouble(boolean right) {
        return right ? 1.0 : 0.0;
    }

    @Builtin(name = J___STR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(FromNativeSubclassNode.class)
    public abstract static class StrNode extends PythonUnaryBuiltinNode {
        public static final Spec spec = new Spec(' ', '>', Spec.NONE, false, Spec.UNSPECIFIED, Spec.NONE, 0, 'r');

        @Specialization
        TruffleString str(double self) {
            FloatFormatter f = new FloatFormatter(getRaiseNode(), spec);
            f.setMinFracDigits(1);
            return doFormat(self, f);
        }

        @Specialization(guards = "isFloatSubtype(frame, inliningTarget, object, getClass, isSubtype)", limit = "1")
        static TruffleString doNativeFloat(VirtualFrame frame, PythonAbstractNativeObject object,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached GetPythonObjectClassNode getClass,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtype,
                        @SuppressWarnings("unused") @Cached FromNativeSubclassNode getFloat) {
            return PFloat.doubleToString(getFloat.execute(frame, object));
        }

        @TruffleBoundary
        public static TruffleString doFormat(double d, FloatFormatter f) {
            return f.format(d).getResult();
        }
    }

    @Builtin(name = J___REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReprNode extends StrNode {
    }

    @Builtin(name = J___FORMAT__, minNumOfPositionalArgs = 2, parameterNames = {"$self", "format_spec"})
    @TypeSystemReference(PythonArithmeticTypes.class)
    @ArgumentClinic(name = "format_spec", conversion = ClinicConversion.TString)
    @GenerateNodeFactory
    abstract static class FormatNode extends FormatNodeBase {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return FormatNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = "!formatString.isEmpty()")
        TruffleString formatPF(double self, TruffleString formatString) {
            return doFormat(self, formatString);
        }

        @TruffleBoundary
        private TruffleString doFormat(double self, TruffleString formatString) {
            InternalFormat.Spec spec = InternalFormat.fromText(getRaiseNode(), formatString, InternalFormat.Spec.NONE, '>');
            FloatFormatter formatter = new FloatFormatter(getRaiseNode(), validateForFloat(getRaiseNode(), spec, "float"));
            formatter.format(self);
            return formatter.pad().getResult();
        }
    }

    @Builtin(name = J___ABS__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class AbsNode extends PythonUnaryBuiltinNode {

        @Specialization
        static double abs(double arg) {
            return Math.abs(arg);
        }
    }

    @Builtin(name = J___BOOL__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class BoolNode extends PythonUnaryBuiltinNode {
        @Specialization
        static boolean bool(double self) {
            return self != 0.0;
        }
    }

    @Builtin(name = J___INT__, minNumOfPositionalArgs = 1)
    @Builtin(name = J___TRUNC__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @ImportStatic(MathGuards.class)
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class IntNode extends PythonUnaryBuiltinNode {

        public abstract Object executeWithDouble(double self);

        @Specialization(guards = "fitInt(self)")
        static int doIntRange(double self) {
            return (int) self;
        }

        @Specialization(guards = "fitLong(self)")
        static long doLongRange(double self) {
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

    @Builtin(name = J___FLOAT__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class FloatNode extends PythonUnaryBuiltinNode {
        @Specialization
        static double doDouble(double self) {
            return self;
        }

        @Specialization
        static PFloat doPFloat(PFloat self) {
            return self;
        }

        @Specialization(guards = "isFloatSubtype(inliningTarget, object, getClassNode, isSubtype)", limit = "1")
        static PythonAbstractNativeObject doNativeFloat(PythonAbstractNativeObject object,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached GetPythonObjectClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtype) {
            return object;
        }

        @Fallback
        Object doFallback(Object possibleBase) {
            throw raise(PythonErrorType.TypeError, ErrorMessages.MUST_BE_REAL_NUMBER, possibleBase);
        }

        static boolean isFloatSubtype(Node inliningTarget, PythonAbstractNativeObject object, GetPythonObjectClassNode getClass, IsSubtypeNode isSubtype) {
            return FromNativeSubclassNode.isFloatSubtype(null, inliningTarget, object, getClass, isSubtype);
        }
    }

    static Object convertToDouble(Object obj,
                    CastToJavaDoubleNode asDoubleNode) {
        try {
            return asDoubleNode.execute(obj);
        } catch (CannotCastException e) {
            // This can only happen to values that are expected to be long.
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = J___RADD__, minNumOfPositionalArgs = 2)
    @Builtin(name = J___ADD__, minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class AddNode extends PythonBinaryBuiltinNode {
        @Specialization
        static double doDD(double left, double right) {
            return left + right;
        }

        @Specialization
        static double doDL(double left, long right) {
            return left + right;
        }

        @Specialization
        static double doLD(long left, double right) {
            return left + right;
        }

        @Specialization
        double doDPi(double left, PInt right) {
            return left + right.doubleValueWithOverflow(getRaiseNode());
        }

        @Specialization
        static Object doDP(VirtualFrame frame, PythonAbstractNativeObject left, long right,
                        @Cached FromNativeSubclassNode getFloat) {
            Double leftPrimitive = getFloat.execute(frame, left);
            if (leftPrimitive != null) {
                return leftPrimitive + right;
            } else {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }

        @Specialization
        static Object doDP(VirtualFrame frame, PythonAbstractNativeObject left, double right,
                        @Cached FromNativeSubclassNode getFloat) {
            Double leftPrimitive = getFloat.execute(frame, left);
            if (leftPrimitive != null) {
                return leftPrimitive + right;
            } else {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }

        @Fallback
        Object doGeneric(Object left, Object right,
                        @Cached CastToJavaDoubleNode castToJavaDoubleNode) {

            Object objLeft = convertToDouble(left, castToJavaDoubleNode);
            if (objLeft == PNotImplemented.NOT_IMPLEMENTED) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }

            Object objRight = convertToDouble(right, castToJavaDoubleNode);
            if (objRight == PNotImplemented.NOT_IMPLEMENTED) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }

            double leftDouble = (double) objLeft;
            double rightDouble = (double) objRight;
            return leftDouble + rightDouble;
        }
    }

    @Builtin(name = J___RSUB__, minNumOfPositionalArgs = 2, reverseOperation = true)
    @Builtin(name = J___SUB__, minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class SubNode extends PythonBinaryBuiltinNode {
        @Specialization
        static double doDD(double left, double right) {
            return left - right;
        }

        @Specialization
        static double doDL(double left, long right) {
            return left - right;
        }

        @Specialization
        double doDPi(double left, PInt right) {
            return left - right.doubleValueWithOverflow(getRaiseNode());
        }

        @Specialization
        static double doLD(long left, double right) {
            return left - right;
        }

        @Specialization
        double doPiD(PInt left, double right) {
            return left.doubleValueWithOverflow(getRaiseNode()) - right;
        }

        @Fallback
        Object doGeneric(Object left, Object right,
                        @Cached CastToJavaDoubleNode castToJavaDoubleNode) {

            Object objLeft = convertToDouble(left, castToJavaDoubleNode);
            if (objLeft == PNotImplemented.NOT_IMPLEMENTED) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }

            Object objRight = convertToDouble(right, castToJavaDoubleNode);
            if (objRight == PNotImplemented.NOT_IMPLEMENTED) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }

            double leftDouble = (double) objLeft;
            double rightDouble = (double) objRight;
            return leftDouble - rightDouble;
        }
    }

    @Builtin(name = J___RMUL__, minNumOfPositionalArgs = 2)
    @Builtin(name = J___MUL__, minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class MulNode extends PythonBinaryBuiltinNode {
        @Specialization
        static double doDL(double left, long right) {
            return left * right;
        }

        @Specialization
        static double doLD(long left, double right) {
            return left * right;
        }

        @Specialization
        static double doDD(double left, double right) {
            return left * right;
        }

        @Specialization
        double doDP(double left, PInt right) {
            return left * right.doubleValueWithOverflow(getRaiseNode());
        }

        @Specialization
        static Object doDP(VirtualFrame frame, PythonAbstractNativeObject left, long right,
                        @Cached FromNativeSubclassNode getFloat) {
            Double leftPrimitive = getFloat.execute(frame, left);
            if (leftPrimitive != null) {
                return leftPrimitive * right;
            } else {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }

        @Specialization
        static Object doDP(VirtualFrame frame, PythonAbstractNativeObject left, double right,
                        @Cached FromNativeSubclassNode getFloat) {
            Double leftPrimitive = getFloat.execute(frame, left);
            if (leftPrimitive != null) {
                return leftPrimitive * right;
            } else {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }

        @Specialization
        Object doDP(VirtualFrame frame, PythonAbstractNativeObject left, PInt right,
                        @Cached FromNativeSubclassNode getFloat) {
            Double leftPrimitive = getFloat.execute(frame, left);
            if (leftPrimitive != null) {
                return leftPrimitive * right.doubleValueWithOverflow(getRaiseNode());
            } else {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }

        @Fallback
        Object doGeneric(Object left, Object right,
                        @Cached CastToJavaDoubleNode castToJavaDoubleNode) {

            Object objLeft = convertToDouble(left, castToJavaDoubleNode);
            if (objLeft == PNotImplemented.NOT_IMPLEMENTED) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }

            Object objRight = convertToDouble(right, castToJavaDoubleNode);
            if (objRight == PNotImplemented.NOT_IMPLEMENTED) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }

            double leftDouble = (double) objLeft;
            double rightDouble = (double) objRight;
            return leftDouble * rightDouble;
        }
    }

    @Builtin(name = J___RPOW__, minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3, reverseOperation = true)
    @Builtin(name = J___POW__, minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    @ReportPolymorphism
    public abstract static class PowNode extends PythonTernaryBuiltinNode {
        protected abstract double executeDouble(VirtualFrame frame, double left, double right, PNone none) throws UnexpectedResultException;

        protected abstract Object execute(VirtualFrame frame, double left, double right, PNone none);

        public final double executeDouble(double left, double right) throws UnexpectedResultException {
            return executeDouble(null, left, right, PNone.NO_VALUE);
        }

        public final Object execute(double left, double right) {
            return execute(null, left, right, PNone.NO_VALUE);
        }

        @Specialization
        double doDL(double left, long right, @SuppressWarnings("unused") PNone none) {
            return doOperation(left, right);
        }

        @Specialization
        double doDPi(double left, PInt right, @SuppressWarnings("unused") PNone none) {
            return doOperation(left, right.doubleValueWithOverflow(getRaiseNode()));
        }

        /**
         * The special cases we need to deal with always return 1, so 0 means no special case, not a
         * result.
         */
        private double doSpecialCases(double left, double right) {
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
                // 0**w is an error if w is finite and negative, unlike Java
                throw raise(PythonBuiltinClassType.ZeroDivisionError, ErrorMessages.POW_ZERO_CANNOT_RAISE_TO_NEGATIVE_POWER);
            }
            return 0;
        }

        private double doOperation(double left, double right) {
            if (doSpecialCases(left, right) == 1) {
                return 1.0;
            }
            return Math.pow(left, right);
        }

        @Specialization(rewriteOn = UnexpectedResultException.class)
        double doDD(VirtualFrame frame, double left, double right, @SuppressWarnings("unused") PNone none,
                        @Shared("powCall") @Cached("create(Pow)") LookupAndCallTernaryNode callPow) throws UnexpectedResultException {
            if (doSpecialCases(left, right) == 1) {
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
                        @Shared("powCall") @Cached("create(Pow)") LookupAndCallTernaryNode callPow) {
            if (doSpecialCases(left, right) == 1) {
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
                        @Shared("powCall") @Cached("create(Pow)") LookupAndCallTernaryNode callPow) throws UnexpectedResultException {
            return doDD(frame, left, right, none, callPow);
        }

        @Specialization(replaces = "doDL")
        Object doDLComplex(VirtualFrame frame, long left, double right, PNone none,
                        @Shared("powCall") @Cached("create(Pow)") LookupAndCallTernaryNode callPow) {
            return doDDToComplex(frame, left, right, none, callPow);
        }

        @Specialization(rewriteOn = UnexpectedResultException.class)
        double doDPi(VirtualFrame frame, PInt left, double right, @SuppressWarnings("unused") PNone none,
                        @Shared("powCall") @Cached("create(Pow)") LookupAndCallTernaryNode callPow) throws UnexpectedResultException {
            return doDD(frame, left.doubleValueWithOverflow(getRaiseNode()), right, none, callPow);
        }

        @Specialization(replaces = "doDPi")
        Object doDPiToComplex(VirtualFrame frame, PInt left, double right, @SuppressWarnings("unused") PNone none,
                        @Shared("powCall") @Cached("create(Pow)") LookupAndCallTernaryNode callPow) {
            return doDDToComplex(frame, left.doubleValueWithOverflow(getRaiseNode()), right, none, callPow);
        }

        @Specialization
        Object doGeneric(VirtualFrame frame, Object left, Object right, Object mod,
                        @Cached CastToJavaDoubleNode castToJavaDoubleNode,
                        @Shared("powCall") @Cached("create(Pow)") LookupAndCallTernaryNode callPow) {
            if (!(mod instanceof PNone)) {
                throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.POW_3RD_ARG_NOT_ALLOWED_UNLESS_INTEGERS);
            }

            Object objLeft = convertToDouble(left, castToJavaDoubleNode);
            if (objLeft == PNotImplemented.NOT_IMPLEMENTED) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }

            Object objRight = convertToDouble(right, castToJavaDoubleNode);
            if (objRight == PNotImplemented.NOT_IMPLEMENTED) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }

            double leftDouble = (double) objLeft;
            double rightDouble = (double) objRight;
            return doDDToComplex(frame, leftDouble, rightDouble, PNone.NONE, callPow);
        }

        public static PowNode create() {
            return FloatBuiltinsFactory.PowNodeFactory.create();
        }
    }

    @Builtin(name = J___RFLOORDIV__, minNumOfPositionalArgs = 2, reverseOperation = true)
    @Builtin(name = J___FLOORDIV__, minNumOfPositionalArgs = 2)
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
        static PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = J___RDIVMOD__, minNumOfPositionalArgs = 2, reverseOperation = true)
    @Builtin(name = J___DIVMOD__, minNumOfPositionalArgs = 2)
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

        @Fallback
        Object doGeneric(Object left, Object right,
                        @Cached CastToJavaDoubleNode castToJavaDoubleNode) {

            Object objLeft = convertToDouble(left, castToJavaDoubleNode);
            if (objLeft == PNotImplemented.NOT_IMPLEMENTED) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }

            Object objRight = convertToDouble(right, castToJavaDoubleNode);
            if (objRight == PNotImplemented.NOT_IMPLEMENTED) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }

            double leftDouble = (double) objLeft;
            double rightDouble = (double) objRight;
            return doDD(leftDouble, rightDouble);
        }

        protected static boolean accepts(Object obj) {
            return obj instanceof Double || obj instanceof Integer || obj instanceof Long || obj instanceof PInt || obj instanceof PFloat;
        }
    }

    @Builtin(name = J___HASH__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class HashNode extends PythonUnaryBuiltinNode {
        @Specialization
        static long doDouble(double self) {
            return PyObjectHashNode.hash(self);
        }

        @Specialization(guards = "dval != null")
        static long doNativeFloat(@SuppressWarnings("unused") VirtualFrame frame, @SuppressWarnings("unused") PythonAbstractNativeObject object,
                        @SuppressWarnings("unused") @Cached FromNativeSubclassNode getFloat,
                        @Bind("getFloat.execute(frame, object)") Double dval) {
            return PyObjectHashNode.hash(dval);
        }
    }

    @Builtin(name = "fromhex", minNumOfPositionalArgs = 2, isClassmethod = true)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class FromHexNode extends PythonBuiltinNode {

        @TruffleBoundary
        private double fromHex(String arg) {
            boolean negative = false;
            String str = arg.trim().toLowerCase();

            if (str.isEmpty()) {
                throw raise(PythonErrorType.ValueError, ErrorMessages.INVALID_STRING);
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
                throw raise(PythonErrorType.ValueError, ErrorMessages.INVALID_STRING);
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
                throw raise(PythonErrorType.ValueError, ErrorMessages.INVALID_STRING);
            }
        }

        @Specialization(guards = "isPythonBuiltinClass(cl)")
        public double fromhexFloat(@SuppressWarnings("unused") Object cl, TruffleString arg,
                        @Shared("ts2js") @Cached TruffleString.ToJavaStringNode toJavaStringNode) {
            return fromHex(toJavaStringNode.execute(arg));
        }

        @Specialization(guards = "!isPythonBuiltinClass(cl)")
        public Object fromhexO(Object cl, TruffleString arg,
                        @Cached("create(T___CALL__)") LookupAndCallVarargsNode constr,
                        @Shared("ts2js") @Cached TruffleString.ToJavaStringNode toJavaStringNode) {
            double value = fromHex(toJavaStringNode.execute(arg));
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
        public static TruffleString hexD(double value,
                        @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
            return fromJavaStringNode.execute(makeHexNumber(value), TS_ENCODING);
        }
    }

    @Builtin(name = J___RMOD__, minNumOfPositionalArgs = 2, reverseOperation = true)
    @Builtin(name = J___MOD__, minNumOfPositionalArgs = 2)
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

        @Fallback
        Object doGeneric(Object left, Object right,
                        @Cached CastToJavaDoubleNode castToJavaDoubleNode) {

            Object objLeft = convertToDouble(left, castToJavaDoubleNode);
            if (objLeft == PNotImplemented.NOT_IMPLEMENTED) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }

            Object objRight = convertToDouble(right, castToJavaDoubleNode);
            if (objRight == PNotImplemented.NOT_IMPLEMENTED) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }

            double leftDouble = (double) objLeft;
            double rightDouble = (double) objRight;
            return doDD(leftDouble, rightDouble);
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

    @Builtin(name = J___RTRUEDIV__, minNumOfPositionalArgs = 2, reverseOperation = true)
    @Builtin(name = J___TRUEDIV__, minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class DivNode extends FloatBinaryBuiltinNode {
        @Specialization
        double doDD(double left, double right) {
            raiseDivisionByZero(right == 0.0);
            return left / right;
        }

        @Specialization
        double doDL(double left, long right) {
            raiseDivisionByZero(right == 0);
            return left / right;
        }

        @Specialization
        double doDPi(double left, PInt right) {
            raiseDivisionByZero(right.isZero());
            return left / right.doubleValueWithOverflow(getRaiseNode());
        }

        @Specialization
        double div(long left, double right) {
            raiseDivisionByZero(right == 0.0);
            return left / right;
        }

        @Specialization
        double div(PInt left, double right) {
            raiseDivisionByZero(right == 0.0);
            return left.doubleValueWithOverflow(getRaiseNode()) / right;
        }

        @Specialization
        Object doDP(VirtualFrame frame, long left, PythonAbstractNativeObject right,
                        @Cached FromNativeSubclassNode getFloat) {
            Double rPrimitive = getFloat.execute(frame, right);
            if (rPrimitive != null) {
                raiseDivisionByZero(rPrimitive == 0.0);
                return left / rPrimitive;
            } else {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }

        @Fallback
        Object doGeneric(Object left, Object right,
                        @Cached CastToJavaDoubleNode castToJavaDoubleNode) {

            Object objLeft = convertToDouble(left, castToJavaDoubleNode);
            if (objLeft == PNotImplemented.NOT_IMPLEMENTED) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }

            Object objRight = convertToDouble(right, castToJavaDoubleNode);
            if (objRight == PNotImplemented.NOT_IMPLEMENTED) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }

            double leftDouble = (double) objLeft;
            double rightDouble = (double) objRight;
            return doDD(leftDouble, rightDouble);
        }
    }

    @Builtin(name = J___ROUND__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
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
        Object round(double x, @SuppressWarnings("unused") PNone none,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedConditionProfile nanProfile,
                        @Cached InlinedConditionProfile infProfile,
                        @Cached InlinedConditionProfile isLongProfile) {
            if (nanProfile.profile(inliningTarget, Double.isNaN(x))) {
                throw raise(PythonErrorType.ValueError, ErrorMessages.CANNOT_CONVERT_S_TO_INT, "float NaN");
            }
            if (infProfile.profile(inliningTarget, Double.isInfinite(x))) {
                throw raise(PythonErrorType.OverflowError, ErrorMessages.CANNOT_CONVERT_S_TO_INT, "float infinity");
            }
            double result = round(x, 0);
            if (isLongProfile.profile(inliningTarget, result > Long.MAX_VALUE || result < Long.MIN_VALUE)) {
                return factory().createInt(toBigInteger(result));
            } else {
                return (long) result;
            }
        }

        @Fallback
        double roundFallback(Object x, Object n) {
            if (MathGuards.isFloat(x)) {
                throw raise(PythonErrorType.TypeError, ErrorMessages.OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER, n);
            } else {
                throw raise(PythonErrorType.TypeError, ErrorMessages.DESCRIPTOR_REQUIRES_OBJ, "__round__", "float", x);
            }
        }

        @TruffleBoundary
        private static BigInteger toBigInteger(double d) {
            return BigDecimal.valueOf(d).toBigInteger();
        }
    }

    @Builtin(name = J___EQ__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class EqNode extends PythonBinaryBuiltinNode {

        @Specialization
        static boolean eqDbDb(double a, double b) {
            return a == b;
        }

        @Specialization
        static boolean doDI(double x, int y) {
            return x == y;
        }

        @Specialization
        static boolean doID(int x, double y) {
            return x == y;
        }

        @Specialization
        static boolean eqDbLn(double a, long b,
                        @Bind("this") Node inliningTarget,
                        @Shared("longFitsToDouble") @Cached InlinedConditionProfile longFitsToDoubleProfile) {
            return compareDoubleToLong(inliningTarget, a, b, longFitsToDoubleProfile) == 0;
        }

        @Specialization
        static boolean eqLnDb(long a, double b,
                        @Bind("this") Node inliningTarget,
                        @Shared("longFitsToDouble") @Cached InlinedConditionProfile longFitsToDoubleProfile) {
            return compareDoubleToLong(inliningTarget, b, a, longFitsToDoubleProfile) == 0;
        }

        @Specialization
        static boolean eqDbPI(double a, PInt b) {
            return compareDoubleToLargeInt(a, b) == 0;
        }

        @Specialization
        static Object eqPDb(VirtualFrame frame, PythonAbstractNativeObject left, double right,
                        @Cached FromNativeSubclassNode getFloat) {
            return getFloat.execute(frame, left) == right;
        }

        @Specialization
        static Object eqPDb(VirtualFrame frame, PythonAbstractNativeObject left, long right,
                        @Cached FromNativeSubclassNode getFloat) {
            return getFloat.execute(frame, left) == right;
        }

        @Specialization
        static Object eqPDb(VirtualFrame frame, PythonAbstractNativeObject left, PInt right,
                        @Cached FromNativeSubclassNode getFloat) {
            return eqDbPI(getFloat.execute(frame, left), right);
        }

        @Fallback
        @SuppressWarnings("unused")
        static PNotImplemented eq(Object a, Object b) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        // adapted from CPython's float_richcompare in floatobject.c
        public static double compareDoubleToLong(Node inliningTarget, double v, long w, InlinedConditionProfile wFitsInDoubleProfile) {
            if (wFitsInDoubleProfile.profile(inliningTarget, w > -0x1000000000000L && w < 0x1000000000000L)) {
                // w is at most 48 bits and thus fits into a double without any loss
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

    @Builtin(name = J___NE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class NeNode extends PythonBinaryBuiltinNode {
        @Specialization
        static boolean neDbDb(double a, double b) {
            return a != b;
        }

        @Specialization
        static boolean doDI(double x, int y) {
            return x != y;
        }

        @Specialization
        static boolean doID(int x, double y) {
            return x != y;
        }

        @Specialization
        static boolean neDbLn(double a, long b,
                        @Bind("this") Node inliningTarget,
                        @Shared("longFitsToDouble") @Cached InlinedConditionProfile longFitsToDoubleProfile) {
            return EqNode.compareDoubleToLong(inliningTarget, a, b, longFitsToDoubleProfile) != 0;
        }

        @Specialization
        static boolean neLnDb(long a, double b,
                        @Bind("this") Node inliningTarget,
                        @Shared("longFitsToDouble") @Cached InlinedConditionProfile longFitsToDoubleProfile) {
            return EqNode.compareDoubleToLong(inliningTarget, b, a, longFitsToDoubleProfile) != 0;
        }

        @Specialization
        static boolean neDbPI(double a, PInt b) {
            return EqNode.compareDoubleToLargeInt(a, b) != 0;
        }

        @Specialization
        static Object eqPDb(VirtualFrame frame, PythonAbstractNativeObject left, double right,
                        @Cached FromNativeSubclassNode getFloat) {
            return getFloat.execute(frame, left) != right;
        }

        @Specialization
        static Object eqPDb(VirtualFrame frame, PythonAbstractNativeObject left, long right,
                        @Cached FromNativeSubclassNode getFloat) {
            return getFloat.execute(frame, left) != right;
        }

        @Specialization
        static Object eqPDb(VirtualFrame frame, PythonAbstractNativeObject left, PInt right,
                        @Cached FromNativeSubclassNode getFloat) {
            return neDbPI(getFloat.execute(frame, left), right);
        }

        @Fallback
        @SuppressWarnings("unused")
        static PNotImplemented eq(Object a, Object b) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = J___LT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(FromNativeSubclassNode.class)
    public abstract static class LtNode extends PythonBinaryBuiltinNode {
        @Specialization
        static boolean doDD(double x, double y) {
            return x < y;
        }

        @Specialization
        static boolean doDI(double x, int y) {
            return x < y;
        }

        @Specialization
        static boolean doID(int x, double y) {
            return x < y;
        }

        @Specialization
        static boolean doDL(double x, long y,
                        @Bind("this") Node inliningTarget,
                        @Shared("longFitsToDouble") @Cached InlinedConditionProfile longFitsToDoubleProfile) {
            return EqNode.compareDoubleToLong(inliningTarget, x, y, longFitsToDoubleProfile) < 0;
        }

        @Specialization
        static boolean doLD(long x, double y,
                        @Bind("this") Node inliningTarget,
                        @Shared("longFitsToDouble") @Cached InlinedConditionProfile longFitsToDoubleProfile) {
            return EqNode.compareDoubleToLong(inliningTarget, y, x, longFitsToDoubleProfile) > 0;
        }

        @Specialization
        static boolean doPI(double x, PInt y) {
            return EqNode.compareDoubleToLargeInt(x, y) < 0;
        }

        @Specialization(guards = "isFloatSubtype(frame, inliningTarget, y, getClass, isSubtype)", limit = "1")
        static boolean doDN(VirtualFrame frame, double x, PythonAbstractNativeObject y,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached GetPythonObjectClassNode getClass,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtype,
                        @Cached FromNativeSubclassNode fromNativeNode) {
            return x < fromNativeNode.execute(frame, y);
        }

        @Specialization(guards = {
                        "isFloatSubtype(frame, inliningTarget, x, getClass, isSubtype)",
                        "isFloatSubtype(frame, inliningTarget, y, getClass, isSubtype)"}, limit = "1")
        static boolean doDN(VirtualFrame frame, PythonAbstractNativeObject x, PythonAbstractNativeObject y,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached GetPythonObjectClassNode getClass,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtype,
                        @Cached FromNativeSubclassNode nativeLeft,
                        @Cached FromNativeSubclassNode nativeRight) {
            return nativeLeft.execute(frame, x) < nativeRight.execute(frame, y);
        }

        @Specialization(guards = "isFloatSubtype(frame, inliningTarget, x, getClass, isSubtype)", limit = "1")
        static boolean doDN(VirtualFrame frame, PythonAbstractNativeObject x, double y,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached GetPythonObjectClassNode getClass,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtype,
                        @Cached FromNativeSubclassNode fromNativeNode) {
            return fromNativeNode.execute(frame, x) < y;
        }

        @Specialization(guards = "isFloatSubtype(frame, inliningTarget, x, getClass, isSubtype)", limit = "1")
        static boolean doDN(VirtualFrame frame, PythonAbstractNativeObject x, long y,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached GetPythonObjectClassNode getClass,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtype,
                        @Cached FromNativeSubclassNode fromNativeNode) {
            return fromNativeNode.execute(frame, x) < y;
        }

        @Fallback
        @SuppressWarnings("unused")
        static PNotImplemented doGeneric(Object a, Object b) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = J___LE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(FromNativeSubclassNode.class)
    public abstract static class LeNode extends PythonBinaryBuiltinNode {
        @Specialization
        static boolean doDD(double x, double y) {
            return x <= y;
        }

        @Specialization
        static boolean doDI(double x, int y) {
            return x <= y;
        }

        @Specialization
        static boolean doID(int x, double y) {
            return x <= y;
        }

        @Specialization
        static boolean doDL(double x, long y,
                        @Bind("this") Node inliningTarget,
                        @Shared("longFitsToDouble") @Cached InlinedConditionProfile longFitsToDoubleProfile) {
            return EqNode.compareDoubleToLong(inliningTarget, x, y, longFitsToDoubleProfile) <= 0;
        }

        @Specialization
        static boolean doLD(long x, double y,
                        @Bind("this") Node inliningTarget,
                        @Shared("longFitsToDouble") @Cached InlinedConditionProfile longFitsToDoubleProfile) {
            return EqNode.compareDoubleToLong(inliningTarget, y, x, longFitsToDoubleProfile) >= 0;
        }

        @Specialization
        static boolean doPI(double x, PInt y) {
            return EqNode.compareDoubleToLargeInt(x, y) <= 0;
        }

        @Specialization(guards = "isFloatSubtype(frame, inliningTarget, y, getClass, isSubtype)", limit = "1")
        static boolean doDN(VirtualFrame frame, double x, PythonAbstractNativeObject y,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached GetPythonObjectClassNode getClass,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtype,
                        @Cached FromNativeSubclassNode fromNativeNode) {
            return x <= fromNativeNode.execute(frame, y);
        }

        @Specialization(guards = {
                        "isFloatSubtype(frame, inliningTarget, x, getClass, isSubtype)",
                        "isFloatSubtype(frame, inliningTarget, y, getClass, isSubtype)"}, limit = "1")
        static boolean doNN(VirtualFrame frame, PythonAbstractNativeObject x, PythonAbstractNativeObject y,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached GetPythonObjectClassNode getClass,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtype,
                        @Cached FromNativeSubclassNode nativeLeft,
                        @Cached FromNativeSubclassNode nativeRight) {
            return nativeLeft.execute(frame, x) <= nativeRight.execute(frame, y);
        }

        @Specialization(guards = "isFloatSubtype(frame, inliningTarget, x, getClass, isSubtype)", limit = "1")
        static boolean doND(VirtualFrame frame, PythonAbstractNativeObject x, double y,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached GetPythonObjectClassNode getClass,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtype,
                        @Cached FromNativeSubclassNode fromNativeNode) {
            return fromNativeNode.execute(frame, x) <= y;
        }

        @Specialization(guards = "isFloatSubtype(frame, inliningTarget, x, getClass, isSubtype)", limit = "1")
        static boolean doNL(VirtualFrame frame, PythonAbstractNativeObject x, long y,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached GetPythonObjectClassNode getClass,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtype,
                        @Cached FromNativeSubclassNode fromNativeNode) {
            return fromNativeNode.execute(frame, x) <= y;
        }

        @Fallback
        @SuppressWarnings("unused")
        static PNotImplemented doGeneric(Object a, Object b) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = J___GT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(FromNativeSubclassNode.class)
    public abstract static class GtNode extends PythonBinaryBuiltinNode {
        @Specialization
        static boolean doDD(double x, double y) {
            return x > y;
        }

        @Specialization
        static boolean doDI(double x, int y) {
            return x > y;
        }

        @Specialization
        static boolean doID(int x, double y) {
            return x > y;
        }

        @Specialization
        static boolean doDL(double x, long y,
                        @Bind("this") Node inliningTarget,
                        @Shared("longFitsToDouble") @Cached InlinedConditionProfile longFitsToDoubleProfile) {
            return EqNode.compareDoubleToLong(inliningTarget, x, y, longFitsToDoubleProfile) > 0;
        }

        @Specialization
        static boolean doLD(long x, double y,
                        @Bind("this") Node inliningTarget,
                        @Shared("longFitsToDouble") @Cached InlinedConditionProfile longFitsToDoubleProfile) {
            return EqNode.compareDoubleToLong(inliningTarget, y, x, longFitsToDoubleProfile) < 0;
        }

        @Specialization
        static boolean doPI(double x, PInt y) {
            return EqNode.compareDoubleToLargeInt(x, y) > 0;
        }

        @Specialization(guards = "isFloatSubtype(frame, inliningTarget, y, getClass, isSubtype)", limit = "1")
        static boolean doDN(VirtualFrame frame, double x, PythonAbstractNativeObject y,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached GetPythonObjectClassNode getClass,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtype,
                        @Cached FromNativeSubclassNode fromNativeNode) {
            return x > fromNativeNode.execute(frame, y);
        }

        @Specialization(guards = {
                        "isFloatSubtype(frame, inliningTarget, x, getClass, isSubtype)",
                        "isFloatSubtype(frame, inliningTarget, y, getClass, isSubtype)"}, limit = "1")
        static boolean doNN(VirtualFrame frame, PythonAbstractNativeObject x, PythonAbstractNativeObject y,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached GetPythonObjectClassNode getClass,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtype,
                        @Cached FromNativeSubclassNode nativeLeft,
                        @Cached FromNativeSubclassNode nativeRight) {
            return nativeLeft.execute(frame, x) > nativeRight.execute(frame, y);
        }

        @Specialization(guards = "isFloatSubtype(frame, inliningTarget, x, getClass, isSubtype)", limit = "1")
        static boolean doND(VirtualFrame frame, PythonAbstractNativeObject x, double y,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached GetPythonObjectClassNode getClass,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtype,
                        @Cached FromNativeSubclassNode fromNativeNode) {
            return fromNativeNode.execute(frame, x) > y;
        }

        @Specialization(guards = "isFloatSubtype(frame, inliningTarget, x, getClass, isSubtype)", limit = "1")
        static boolean doNL(VirtualFrame frame, PythonAbstractNativeObject x, long y,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached GetPythonObjectClassNode getClass,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtype,
                        @Cached FromNativeSubclassNode fromNativeNode) {
            return fromNativeNode.execute(frame, x) > y;
        }

        @Fallback
        @SuppressWarnings("unused")
        static PNotImplemented doGeneric(Object a, Object b) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = J___GE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(FromNativeSubclassNode.class)
    public abstract static class GeNode extends PythonBinaryBuiltinNode {
        @Specialization
        static boolean doDD(double x, double y) {
            return x >= y;
        }

        @Specialization
        static boolean doDI(double x, int y) {
            return x >= y;
        }

        @Specialization
        static boolean doID(int x, double y) {
            return x >= y;
        }

        @Specialization
        static boolean doDL(double x, long y,
                        @Bind("this") Node inliningTarget,
                        @Shared("longFitsToDouble") @Cached InlinedConditionProfile longFitsToDoubleProfile) {
            return EqNode.compareDoubleToLong(inliningTarget, x, y, longFitsToDoubleProfile) >= 0;
        }

        @Specialization
        static boolean doLD(long x, double y,
                        @Bind("this") Node inliningTarget,
                        @Shared("longFitsToDouble") @Cached InlinedConditionProfile longFitsToDoubleProfile) {
            return EqNode.compareDoubleToLong(inliningTarget, y, x, longFitsToDoubleProfile) <= 0;
        }

        @Specialization
        static boolean doPI(double x, PInt y) {
            return EqNode.compareDoubleToLargeInt(x, y) >= 0;
        }

        @Specialization(guards = "isFloatSubtype(frame, inliningTarget, y, getClass, isSubtype)", limit = "1")
        static boolean doDN(VirtualFrame frame, double x, PythonAbstractNativeObject y,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached GetPythonObjectClassNode getClass,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtype,
                        @Cached FromNativeSubclassNode fromNativeNode) {
            return x >= fromNativeNode.execute(frame, y);
        }

        @Specialization(guards = {
                        "isFloatSubtype(frame, inliningTarget, x, getClass, isSubtype)",
                        "isFloatSubtype(frame, inliningTarget, y, getClass, isSubtype)"}, limit = "1")
        static boolean doNN(VirtualFrame frame, PythonAbstractNativeObject x, PythonAbstractNativeObject y,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached GetPythonObjectClassNode getClass,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtype,
                        @Cached FromNativeSubclassNode nativeLeft,
                        @Cached FromNativeSubclassNode nativeRight) {
            return nativeLeft.execute(frame, x) >= nativeRight.execute(frame, y);
        }

        @Specialization(guards = "isFloatSubtype(frame, inliningTarget, x, getClass, isSubtype)", limit = "1")
        static boolean doND(VirtualFrame frame, PythonAbstractNativeObject x, double y,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached GetPythonObjectClassNode getClass,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtype,
                        @Cached FromNativeSubclassNode fromNativeNode) {
            return fromNativeNode.execute(frame, x) >= y;
        }

        @Specialization(guards = "isFloatSubtype(frame, inliningTarget, x, getClass, isSubtype)", limit = "1")
        static boolean doNL(VirtualFrame frame, PythonAbstractNativeObject x, long y,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached GetPythonObjectClassNode getClass,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtype,
                        @Cached FromNativeSubclassNode fromNativeNode) {
            return fromNativeNode.execute(frame, x) >= y;
        }

        @Fallback
        @SuppressWarnings("unused")
        static PNotImplemented doGeneric(Object a, Object b) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = J___POS__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class PosNode extends PythonUnaryBuiltinNode {
        @Specialization
        static double pos(double arg) {
            return arg;
        }
    }

    @Builtin(name = J___NEG__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class NegNode extends PythonUnaryBuiltinNode {
        @Specialization
        static double neg(double arg) {
            return -arg;
        }
    }

    @GenerateNodeFactory
    @Builtin(name = "real", minNumOfPositionalArgs = 1, isGetter = true, doc = "the real part of a complex number")
    abstract static class RealNode extends PythonBuiltinNode {

        @Specialization
        static double get(double self) {
            return self;
        }

        @Specialization(guards = "cannotBeOverridden(self, getClassNode)", limit = "1")
        PFloat getPFloat(PFloat self,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode) {
            return self;
        }

        @Specialization(guards = "!cannotBeOverridden(self, getClassNode)", limit = "1")
        PFloat getPFloatOverriden(PFloat self,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode) {
            return factory().createFloat(self.getValue());
        }
    }

    @GenerateNodeFactory
    @Builtin(name = "imag", minNumOfPositionalArgs = 1, isGetter = true, doc = "the imaginary part of a complex number")
    abstract static class ImagNode extends PythonBuiltinNode {

        @Specialization
        static double get(@SuppressWarnings("unused") Object self) {
            return 0;
        }

    }

    @GenerateNodeFactory
    @Builtin(name = "as_integer_ratio", parameterNames = "x")
    @ArgumentClinic(name = "x", defaultValue = "0.0", conversion = ClinicConversion.Double)
    abstract static class AsIntegerRatio extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return AsIntegerRatioClinicProviderGen.INSTANCE;
        }

        @Specialization
        PTuple get(double self,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedConditionProfile nanProfile,
                        @Cached InlinedConditionProfile infProfile) {
            if (nanProfile.profile(inliningTarget, Double.isNaN(self))) {
                throw raise(PythonErrorType.ValueError, ErrorMessages.CANNOT_CONVERT_S_TO_INT_RATIO, "NaN");
            }
            if (infProfile.profile(inliningTarget, Double.isInfinite(self))) {
                throw raise(PythonErrorType.OverflowError, ErrorMessages.CANNOT_CONVERT_S_TO_INT_RATIO, "Infinity");
            }

            // At the first time find mantissa and exponent. This is functionanlity of
            // Math.frexp
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

    @Builtin(name = J___GETFORMAT__, minNumOfPositionalArgs = 2, isClassmethod = true)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class GetFormatNode extends PythonBinaryBuiltinNode {
        private static final TruffleString T_FLOAT = tsLiteral(J_FLOAT);
        private static final TruffleString T_DOUBLE = tsLiteral("double");
        private static final TruffleString T_UNKNOWN = tsLiteral("unknown");
        private static final TruffleString T_IEEE_LITTLE = tsLiteral("IEEE, little-endian");
        private static final TruffleString T_IEEE_BIG = tsLiteral("IEEE, big-endian");

        private static TruffleString getDetectedEndianess() {
            try {
                ByteOrder byteOrder = ByteOrder.nativeOrder();
                if (byteOrder == ByteOrder.BIG_ENDIAN) {
                    return T_IEEE_BIG;
                } else if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
                    return T_IEEE_LITTLE;
                }
            } catch (Error ignored) {
            }
            return T_UNKNOWN;
        }

        protected boolean isValidTypeStr(TruffleString typeStr, TruffleString.EqualNode equalNode) {
            return equalNode.execute(typeStr, T_FLOAT, TS_ENCODING) || equalNode.execute(typeStr, T_DOUBLE, TS_ENCODING);
        }

        @Specialization(guards = "isValidTypeStr(typeStr, equalNode)", limit = "1")
        static TruffleString getFormat(@SuppressWarnings("unused") Object cls, @SuppressWarnings("unused") TruffleString typeStr,
                        @Cached @SuppressWarnings("unused") TruffleString.EqualNode equalNode) {
            return getDetectedEndianess();
        }

        @Fallback
        TruffleString getFormat(@SuppressWarnings("unused") Object cls, @SuppressWarnings("unused") Object typeStr) {
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
        static boolean isInteger(double value) {
            return Double.isFinite(value) && (long) value == value;
        }

        @Specialization
        static boolean trunc(PFloat pValue) {
            return isInteger(pValue.getValue());
        }

    }

    @Builtin(name = J___GETNEWARGS__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class GetNewArgsNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object doL(double self) {
            return factory().createTuple(new Object[]{factory().createFloat(self)});
        }

        @Specialization
        Object getPI(PFloat self) {
            return factory().createTuple(new Object[]{factory().createFloat(self.getValue())});
        }
    }
}
