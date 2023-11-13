/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates.
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
import static com.oracle.graal.python.nodes.BuiltinNames.J_FLOAT;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ABS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CEIL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___DIVMOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___FLOAT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___FLOORDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___FLOOR__;
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
import java.math.RoundingMode;
import java.nio.ByteOrder;
import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ArgumentClinic.ClinicConversion;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.common.FormatNodeBase;
import com.oracle.graal.python.builtins.objects.floats.FloatBuiltinsClinicProviders.FormatNodeClinicProviderGen;
import com.oracle.graal.python.builtins.objects.floats.FloatUtils.PFloatUnboxing;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotInquiry.InquiryBuiltinNode;
import com.oracle.graal.python.lib.PyFloatCheckNode;
import com.oracle.graal.python.lib.PyLongFromDoubleNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectHashNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallTernaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallVarargsNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaDoubleNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.formatting.FloatFormatter;
import com.oracle.graal.python.runtime.formatting.InternalFormat;
import com.oracle.graal.python.runtime.formatting.InternalFormat.Spec;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.FromJavaStringNode;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PFloat)
public final class FloatBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = FloatBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return FloatBuiltinsFactory.getFactories();
    }

    public static double asDouble(boolean right) {
        return right ? 1.0 : 0.0;
    }

    private static double castToDoubleChecked(Node inliningTarget, Object obj, CastToJavaDoubleNode cast) {
        try {
            return cast.execute(inliningTarget, obj);
        } catch (CannotCastException e) {
            throw raiseWrongSelf(obj);
        }
    }

    @InliningCutoff
    private static PException raiseWrongSelf(Object obj) {
        throw PRaiseNode.getUncached().raise(TypeError, ErrorMessages.DESCRIPTOR_REQUIRES_S_OBJ_RECEIVED_P, "float", obj);
    }

    @GenerateCached(false)
    abstract static class AbstractNumericUnaryBuiltin extends PythonUnaryBuiltinNode {
        protected abstract Object op(double num);

        @Specialization
        Object doDouble(double num) {
            return op(num);
        }

        @Specialization(replaces = "doDouble")
        Object doOther(Object object,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToJavaDoubleNode cast) {
            return op(castToDoubleChecked(inliningTarget, object, cast));
        }
    }

    @GenerateCached(false)
    abstract static class AbstractNumericBinaryBuiltin extends PythonBinaryBuiltinNode {

        @Child private PRaiseNode raiseNode;

        protected abstract Object op(double a, double b);

        @Specialization
        Object doDD(double a, double b) {
            return op(a, b);
        }

        @Specialization
        Object doDI(double a, int b) {
            return op(a, b);
        }

        @Specialization(replaces = {"doDD", "doDI"})
        @SuppressWarnings("truffle-static-method")
        Object doOther(Object a, Object b,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToJavaDoubleNode cast) {
            double aDouble, bDouble;
            try {
                // Note the cast accepts integers too, which is what we want
                aDouble = cast.execute(inliningTarget, a);
                bDouble = cast.execute(inliningTarget, b);
            } catch (CannotCastException e) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            return op(aDouble, bDouble);
        }

        void raiseDivisionByZero(boolean cond) {
            if (cond) {
                if (raiseNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    if (isAdoptable()) {
                        raiseNode = insert(PRaiseNode.create());
                    } else {
                        raiseNode = PRaiseNode.getUncached();
                    }
                }
                throw raiseNode.raise(PythonErrorType.ZeroDivisionError, ErrorMessages.DIVISION_BY_ZERO);
            }
        }
    }

    @Builtin(name = J___STR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class StrNode extends AbstractNumericUnaryBuiltin {
        public static final Spec spec = new Spec(' ', '>', Spec.NONE, false, Spec.UNSPECIFIED, Spec.NONE, 0, 'r');

        @Override
        protected TruffleString op(double self) {
            FloatFormatter f = new FloatFormatter(spec, this);
            f.setMinFracDigits(1);
            return doFormat(self, f);
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
    @ArgumentClinic(name = "format_spec", conversion = ClinicConversion.TString)
    @GenerateNodeFactory
    abstract static class FormatNode extends FormatNodeBase {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return FormatNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = "!formatString.isEmpty()")
        static TruffleString formatPF(Object self, TruffleString formatString,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToJavaDoubleNode cast) {
            return doFormat(inliningTarget, castToDoubleChecked(inliningTarget, self, cast), formatString);
        }

        @TruffleBoundary
        private static TruffleString doFormat(Node raisingNode, double self, TruffleString formatString) {
            InternalFormat.Spec spec = InternalFormat.fromText(formatString, InternalFormat.Spec.NONE, '>', raisingNode);
            FloatFormatter formatter = new FloatFormatter(validateForFloat(spec, "float", raisingNode), raisingNode);
            formatter.format(self);
            return formatter.pad().getResult();
        }
    }

    @Builtin(name = J___ABS__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class AbsNode extends AbstractNumericUnaryBuiltin {

        @Override
        protected Object op(double arg) {
            return Math.abs(arg);
        }
    }

    @Slot(SlotKind.nb_bool)
    @GenerateUncached
    @GenerateNodeFactory
    abstract static class BoolNode extends InquiryBuiltinNode {
        static boolean op(double self) {
            return self != 0.0;
        }

        @Specialization
        static boolean doDouble(double num) {
            return op(num);
        }

        @Specialization(replaces = "doDouble")
        static boolean doOther(Object object,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToJavaDoubleNode cast) {
            return op(castToDoubleChecked(inliningTarget, object, cast));
        }
    }

    @Builtin(name = J___INT__, minNumOfPositionalArgs = 1)
    @Builtin(name = J___TRUNC__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IntNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object doDouble(Object self,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToJavaDoubleNode cast,
                        @Cached PyLongFromDoubleNode pyLongFromDoubleNode) {
            return pyLongFromDoubleNode.execute(inliningTarget, castToDoubleChecked(inliningTarget, self, cast));
        }
    }

    @Builtin(name = J___FLOAT__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class FloatNode extends AbstractNumericUnaryBuiltin {
        @Override
        protected Object op(double self) {
            return self;
        }
    }

    @Builtin(name = J___RADD__, minNumOfPositionalArgs = 2)
    @Builtin(name = J___ADD__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class AddNode extends AbstractNumericBinaryBuiltin {

        @Override
        protected Object op(double a, double b) {
            return a + b;
        }
    }

    @Builtin(name = J___RSUB__, minNumOfPositionalArgs = 2, reverseOperation = true)
    @Builtin(name = J___SUB__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class SubNode extends AbstractNumericBinaryBuiltin {

        @Override
        protected Object op(double a, double b) {
            return a - b;
        }
    }

    @Builtin(name = J___RMUL__, minNumOfPositionalArgs = 2)
    @Builtin(name = J___MUL__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class MulNode extends AbstractNumericBinaryBuiltin {

        @Override
        protected Object op(double a, double b) {
            return a * b;
        }
    }

    @Builtin(name = J___RPOW__, minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3, reverseOperation = true)
    @Builtin(name = J___POW__, minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
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
        static double doDI(double left, int right, @SuppressWarnings("unused") PNone none,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            return doOperation(inliningTarget, left, right, raiseNode);
        }

        /**
         * The special cases we need to deal with always return 1, so 0 means no special case, not a
         * result.
         */
        private static double doSpecialCases(Node inliningTarget, double left, double right, PRaiseNode.Lazy raiseNode) {
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
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.ZeroDivisionError, ErrorMessages.POW_ZERO_CANNOT_RAISE_TO_NEGATIVE_POWER);
            }
            return 0;
        }

        private static double doOperation(Node inliningTarget, double left, double right, PRaiseNode.Lazy raiseNode) {
            if (doSpecialCases(inliningTarget, left, right, raiseNode) == 1) {
                return 1.0;
            }
            return Math.pow(left, right);
        }

        @Specialization(rewriteOn = UnexpectedResultException.class)
        @InliningCutoff
        static double doDD(VirtualFrame frame, double left, double right, @SuppressWarnings("unused") PNone none,
                        @Bind("this") Node inliningTarget,
                        @Shared("powCall") @Cached("create(Pow)") LookupAndCallTernaryNode callPow,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) throws UnexpectedResultException {
            if (doSpecialCases(inliningTarget, left, right, raiseNode) == 1) {
                return 1.0;
            }
            if (left < 0 && Double.isFinite(left) && Double.isFinite(right) && (right % 1 != 0)) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                // Negative numbers raised to fractional powers become complex.
                PythonObjectFactory factory = PythonObjectFactory.getUncached();
                throw new UnexpectedResultException(callPow.execute(frame, factory.createComplex(left, 0), factory.createComplex(right, 0), none));
            }
            return Math.pow(left, right);
        }

        @Specialization(replaces = "doDD")
        @InliningCutoff
        static Object doDDToComplex(VirtualFrame frame, double left, double right, PNone none,
                        @Bind("this") Node inliningTarget,
                        @Shared("powCall") @Cached("create(Pow)") LookupAndCallTernaryNode callPow,
                        @Exclusive @Cached PythonObjectFactory.Lazy factory,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            if (doSpecialCases(inliningTarget, left, right, raiseNode) == 1) {
                return 1.0;
            }
            if (left < 0 && Double.isFinite(left) && Double.isFinite(right) && (right % 1 != 0)) {
                // Negative numbers raised to fractional powers become complex.
                PythonObjectFactory pof = factory.get(inliningTarget);
                return callPow.execute(frame, pof.createComplex(left, 0), pof.createComplex(right, 0), none);
            }
            return Math.pow(left, right);
        }

        @Specialization
        @InliningCutoff
        static Object doGeneric(VirtualFrame frame, Object left, Object right, Object mod,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToJavaDoubleNode castToJavaDoubleNode,
                        @Shared("powCall") @Cached("create(Pow)") LookupAndCallTernaryNode callPow,
                        @Exclusive @Cached PythonObjectFactory.Lazy factory,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            if (!(mod instanceof PNone)) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, ErrorMessages.POW_3RD_ARG_NOT_ALLOWED_UNLESS_INTEGERS);
            }

            double leftDouble, rightDouble;
            try {
                leftDouble = castToJavaDoubleNode.execute(inliningTarget, left);
                rightDouble = castToJavaDoubleNode.execute(inliningTarget, right);
            } catch (CannotCastException e) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            return doDDToComplex(frame, leftDouble, rightDouble, PNone.NONE, inliningTarget, callPow, factory, raiseNode);
        }

        public static PowNode create() {
            return FloatBuiltinsFactory.PowNodeFactory.create();
        }
    }

    @Builtin(name = J___RFLOORDIV__, minNumOfPositionalArgs = 2, reverseOperation = true)
    @Builtin(name = J___FLOORDIV__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class FloorDivNode extends AbstractNumericBinaryBuiltin {
        @Override
        protected Object op(double left, double right) {
            raiseDivisionByZero(right == 0);
            return Math.floor(left / right);
        }
    }

    @Builtin(name = J___RDIVMOD__, minNumOfPositionalArgs = 2, reverseOperation = true)
    @Builtin(name = J___DIVMOD__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class DivModNode extends AbstractNumericBinaryBuiltin {
        @Child private PythonObjectFactory factory = PythonObjectFactory.create();

        @Override
        protected PTuple op(double left, double right) {
            raiseDivisionByZero(right == 0);
            return factory.createTuple(new Object[]{Math.floor(left / right), ModNode.mod(left, right)});
        }
    }

    @Builtin(name = J___HASH__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class HashNode extends AbstractNumericUnaryBuiltin {
        @Override
        protected Object op(double self) {
            return PyObjectHashNode.hash(self);
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
                throw PRaiseNode.raiseUncached(this, PythonErrorType.ValueError, ErrorMessages.INVALID_STRING);
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
                throw PRaiseNode.raiseUncached(this, PythonErrorType.ValueError, ErrorMessages.INVALID_STRING);
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
                    throw PRaiseNode.raiseUncached(this, PythonErrorType.OverflowError, ErrorMessages.HEX_VALUE_TOO_LARGE_AS_FLOAT);
                }

                return result;
            } catch (NumberFormatException ex) {
                throw PRaiseNode.raiseUncached(this, PythonErrorType.ValueError, ErrorMessages.INVALID_STRING);
            }
        }

        @Specialization(guards = "isPythonBuiltinClass(cl)")
        double fromhexFloat(@SuppressWarnings("unused") Object cl, TruffleString arg,
                        @Shared("ts2js") @Cached TruffleString.ToJavaStringNode toJavaStringNode) {
            return fromHex(toJavaStringNode.execute(arg));
        }

        @Specialization(guards = "!isPythonBuiltinClass(cl)")
        Object fromhexO(Object cl, TruffleString arg,
                        @Cached("create(T___CALL__)") LookupAndCallVarargsNode constr,
                        @Shared("ts2js") @Cached TruffleString.ToJavaStringNode toJavaStringNode) {
            double value = fromHex(toJavaStringNode.execute(arg));
            return constr.execute(null, cl, new Object[]{cl, value});
        }

        @Fallback
        @SuppressWarnings("unused")
        static double fromhex(Object object, Object arg,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(PythonErrorType.TypeError, ErrorMessages.BAD_ARG_TYPE_FOR_BUILTIN_OP);
        }
    }

    @Builtin(name = "hex", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class HexNode extends PythonUnaryBuiltinNode {

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
        static TruffleString doDouble(Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToJavaDoubleNode cast,
                        @Cached FromJavaStringNode fromJavaStringNode) {
            return fromJavaStringNode.execute(makeHexNumber(castToDoubleChecked(inliningTarget, value, cast)), TS_ENCODING);
        }
    }

    @Builtin(name = J___RMOD__, minNumOfPositionalArgs = 2, reverseOperation = true)
    @Builtin(name = J___MOD__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ModNode extends AbstractNumericBinaryBuiltin {
        @Override
        protected Object op(double left, double right) {
            raiseDivisionByZero(right == 0);
            return mod(left, right);
        }

        public static double mod(double left, double right) {
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
    @GenerateNodeFactory
    abstract static class DivNode extends AbstractNumericBinaryBuiltin {
        @Override
        protected Object op(double left, double right) {
            raiseDivisionByZero(right == 0.0);
            return left / right;
        }
    }

    @Builtin(name = J___ROUND__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class RoundNode extends PythonBinaryBuiltinNode {
        /**
         * The logic is borrowed from Jython.
         */
        @TruffleBoundary
        private static double op(double x, int n) {
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
                BigDecimal rr = xx.setScale(n, RoundingMode.HALF_EVEN);
                return rr.doubleValue();
            }
        }

        @Specialization
        static double round(double x, int n,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            if (Double.isNaN(x) || Double.isInfinite(x) || x == 0.0) {
                // nans, infinities and zeros round to themselves
                return x;
            }
            double d = op(x, n);
            if (Double.isInfinite(d)) {
                throw raiseNode.get(inliningTarget).raise(OverflowError, ErrorMessages.ROUNDED_VALUE_TOO_LARGE);
            }
            return d;
        }

        @Specialization(guards = "!isPNone(n)")
        static Object round(VirtualFrame frame, Object x, Object n,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached CastToJavaDoubleNode cast,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            return round(castToDoubleChecked(inliningTarget, x, cast), asSizeNode.executeLossy(frame, inliningTarget, n), inliningTarget, raiseNode);
        }

        @Specialization
        static Object round(Object xObj, @SuppressWarnings("unused") PNone none,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached CastToJavaDoubleNode cast,
                        @Cached InlinedConditionProfile nanProfile,
                        @Cached InlinedConditionProfile infProfile,
                        @Cached InlinedConditionProfile isLongProfile,
                        @Cached PythonObjectFactory.Lazy factory,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            double x = castToDoubleChecked(inliningTarget, xObj, cast);
            if (nanProfile.profile(inliningTarget, Double.isNaN(x))) {
                throw raiseNode.get(inliningTarget).raise(PythonErrorType.ValueError, ErrorMessages.CANNOT_CONVERT_S_TO_INT, "float NaN");
            }
            if (infProfile.profile(inliningTarget, Double.isInfinite(x))) {
                throw raiseNode.get(inliningTarget).raise(PythonErrorType.OverflowError, ErrorMessages.CANNOT_CONVERT_S_TO_INT, "float infinity");
            }
            double result = round(x, 0, inliningTarget, raiseNode);
            if (isLongProfile.profile(inliningTarget, result > Long.MAX_VALUE || result < Long.MIN_VALUE)) {
                return factory.get(inliningTarget).createInt(toBigInteger(result));
            } else {
                return (long) result;
            }
        }

        @TruffleBoundary
        private static BigInteger toBigInteger(double d) {
            return BigDecimal.valueOf(d).toBigInteger();
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @TypeSystemReference(PFloatUnboxing.class)
    public abstract static class ComparisonHelperNode extends Node {

        @FunctionalInterface
        interface Op {
            boolean compute(double a, double b);
        }

        abstract Object execute(Node inliningTarget, Object left, Object right, Op op);

        @Specialization
        static boolean doDD(double a, double b, Op op) {
            return op.compute(a, b);
        }

        @Specialization
        static boolean doDI(double a, int b, Op op) {
            return op.compute(a, b);
        }

        @Specialization(guards = "check.execute(inliningTarget, bObj)", replaces = "doDD", limit = "1")
        @InliningCutoff
        static boolean doOO(Node inliningTarget, Object aObj, Object bObj, Op op,
                        @SuppressWarnings("unused") @Cached PyFloatCheckNode check,
                        @Exclusive @Cached CastToJavaDoubleNode cast) {
            double a = castToDoubleChecked(inliningTarget, aObj, cast);
            double b = castToDoubleChecked(inliningTarget, bObj, cast);
            return op.compute(a, b);
        }

        @Specialization(replaces = "doDI")
        @InliningCutoff
        static boolean doOI(Node inliningTarget, Object aObj, int b, Op op,
                        @Shared @Cached CastToJavaDoubleNode cast) {
            double a = castToDoubleChecked(inliningTarget, aObj, cast);
            return op.compute(a, b);
        }

        @Specialization
        @InliningCutoff
        static boolean doOL(Node inliningTarget, Object aObj, long b, Op op,
                        @Exclusive @Cached CastToJavaDoubleNode cast,
                        @Cached InlinedConditionProfile longFitsToDoubleProfile) {
            double a = castToDoubleChecked(inliningTarget, aObj, cast);
            return op.compute(compareDoubleToLong(inliningTarget, a, b, longFitsToDoubleProfile), 0.0);
        }

        @Specialization
        @InliningCutoff
        static boolean doOPInt(Node inliningTarget, Object aObj, PInt b, Op op,
                        @Shared @Cached CastToJavaDoubleNode cast) {
            double a = castToDoubleChecked(inliningTarget, aObj, cast);
            return op.compute(compareDoubleToLargeInt(a, b), 0.0);
        }

        @Specialization
        @InliningCutoff
        static boolean doOB(Node inliningTarget, Object aObj, boolean b, Op op,
                        @Shared @Cached CastToJavaDoubleNode cast) {
            double a = castToDoubleChecked(inliningTarget, aObj, cast);
            return op.compute(a, b ? 1 : 0);
        }

        @Fallback
        @SuppressWarnings("unused")
        static PNotImplemented fallback(Object a, Object b, Op op) {
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
            if (!Double.isFinite(v)) {
                return v;
            }
            return new BigDecimal(v).compareTo(new BigDecimal(w));
        }
    }

    @Builtin(name = J___EQ__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class EqNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doIt(Object left, Object right,
                        @Bind("this") Node inliningTarget,
                        @Cached ComparisonHelperNode comparisonHelperNode) {
            return comparisonHelperNode.execute(inliningTarget, left, right, (a, b) -> a == b);
        }
    }

    @Builtin(name = J___NE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class NeNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doIt(Object left, Object right,
                        @Bind("this") Node inliningTarget,
                        @Cached ComparisonHelperNode comparisonHelperNode) {
            return comparisonHelperNode.execute(inliningTarget, left, right, (a, b) -> a != b);
        }
    }

    @Builtin(name = J___LT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class LtNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doIt(Object left, Object right,
                        @Bind("this") Node inliningTarget,
                        @Cached ComparisonHelperNode comparisonHelperNode) {
            return comparisonHelperNode.execute(inliningTarget, left, right, (a, b) -> a < b);
        }
    }

    @Builtin(name = J___LE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class LeNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doIt(Object left, Object right,
                        @Bind("this") Node inliningTarget,
                        @Cached ComparisonHelperNode comparisonHelperNode) {
            return comparisonHelperNode.execute(inliningTarget, left, right, (a, b) -> a <= b);
        }
    }

    @Builtin(name = J___GT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class GtNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doIt(Object left, Object right,
                        @Bind("this") Node inliningTarget,
                        @Cached ComparisonHelperNode comparisonHelperNode) {
            return comparisonHelperNode.execute(inliningTarget, left, right, (a, b) -> a > b);
        }
    }

    @Builtin(name = J___GE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class GeNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doIt(Object left, Object right,
                        @Bind("this") Node inliningTarget,
                        @Cached ComparisonHelperNode comparisonHelperNode) {
            return comparisonHelperNode.execute(inliningTarget, left, right, (a, b) -> a >= b);
        }
    }

    @Builtin(name = J___POS__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PosNode extends FloatNode {
    }

    @Builtin(name = J___NEG__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class NegNode extends AbstractNumericUnaryBuiltin {
        @Override
        protected Object op(double arg) {
            return -arg;
        }
    }

    @Builtin(name = J___FLOOR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class FloorNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object floor(Object self,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToJavaDoubleNode cast,
                        @Cached PyLongFromDoubleNode pyLongFromDoubleNode) {
            return pyLongFromDoubleNode.execute(inliningTarget, Math.floor(castToDoubleChecked(inliningTarget, self, cast)));
        }
    }

    @Builtin(name = J___CEIL__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class CeilNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object ceil(Object self,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToJavaDoubleNode cast,
                        @Cached PyLongFromDoubleNode pyLongFromDoubleNode) {
            return pyLongFromDoubleNode.execute(inliningTarget, Math.ceil(castToDoubleChecked(inliningTarget, self, cast)));
        }
    }

    @Builtin(name = "real", minNumOfPositionalArgs = 1, isGetter = true, doc = "the real part of a complex number")
    @GenerateNodeFactory
    abstract static class RealNode extends FloatNode {
    }

    @GenerateNodeFactory
    @Builtin(name = "imag", minNumOfPositionalArgs = 1, isGetter = true, doc = "the imaginary part of a complex number")
    abstract static class ImagNode extends PythonUnaryBuiltinNode {

        @Specialization
        static double get(@SuppressWarnings("unused") Object self) {
            return 0;
        }

    }

    @GenerateNodeFactory
    @Builtin(name = "as_integer_ratio", parameterNames = "x")
    abstract static class AsIntegerRatio extends PythonUnaryBuiltinNode {

        @Specialization
        static PTuple get(Object selfObj,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToJavaDoubleNode cast,
                        @Cached InlinedConditionProfile nanProfile,
                        @Cached InlinedConditionProfile infProfile,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            double self = castToDoubleChecked(inliningTarget, selfObj, cast);
            if (nanProfile.profile(inliningTarget, Double.isNaN(self))) {
                throw raiseNode.get(inliningTarget).raise(PythonErrorType.ValueError, ErrorMessages.CANNOT_CONVERT_S_TO_INT_RATIO, "NaN");
            }
            if (infProfile.profile(inliningTarget, Double.isInfinite(self))) {
                throw raiseNode.get(inliningTarget).raise(PythonErrorType.OverflowError, ErrorMessages.CANNOT_CONVERT_S_TO_INT_RATIO, "Infinity");
            }

            // At the first time find mantissa and exponent. This is functionality of
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
            return factory.createTuple(countIt(mantissa, exponent));
        }

        @TruffleBoundary
        private static Object[] countIt(double mantissa, int exponent) {
            double m = mantissa;
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
            PythonObjectFactory factory = PythonObjectFactory.getUncached();
            return new Object[]{factory.createInt(numerator), factory.createInt(denominator)};
        }
    }

    @GenerateNodeFactory
    @Builtin(name = "conjugate", minNumOfPositionalArgs = 1, doc = "Returns self, the complex conjugate of any float.")
    abstract static class ConjugateNode extends FloatNode {
    }

    @Builtin(name = J___GETFORMAT__, minNumOfPositionalArgs = 2, isClassmethod = true, parameterNames = {"$cls", "typestr"})
    @ArgumentClinic(name = "typestr", conversion = ClinicConversion.TString)
    @GenerateNodeFactory
    abstract static class GetFormatNode extends PythonBinaryClinicBuiltinNode {
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
        static TruffleString getFormat(@SuppressWarnings("unused") Object cls, @SuppressWarnings("unused") Object typeStr,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(PythonErrorType.ValueError, ErrorMessages.ARG_D_MUST_BE_S_OR_S, "__getformat__()", 1, "double", "float");
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return FloatBuiltinsClinicProviders.GetFormatNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "is_integer", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsIntegerNode extends AbstractNumericUnaryBuiltin {
        @Override
        protected Object op(double value) {
            return Double.isFinite(value) && (long) value == value;
        }
    }

    @Builtin(name = J___GETNEWARGS__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class GetNewArgsNode extends AbstractNumericUnaryBuiltin {
        @Child private PythonObjectFactory factory = PythonObjectFactory.create();

        @Override
        protected Object op(double self) {
            return factory.createTuple(new Object[]{factory.createFloat(self)});
        }
    }
}
