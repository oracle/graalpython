/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.decimal;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.MemoryError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.decimal.DecimalArithmetic.Result;
import com.oracle.graal.python.builtins.objects.decimal.DecimalArithmetic.ResultWithRemainder;
import com.oracle.graal.python.builtins.objects.decimal.DecimalUtils.Value;
import com.oracle.graal.python.builtins.objects.decimal.PDecimal.Kind;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.lib.PyLongCheckNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.util.CastToJavaBigIntegerNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

public final class DecimalNodes {
    private DecimalNodes() {
    }

    /** Resolve an optional context argument, matching CPython's CONTEXT_CHECK_VA. */
    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class GetContextNode extends Node {
        public abstract PDecimalContext execute(Node inliningTarget, Object context);

        @Specialization
        static PDecimalContext doContext(@SuppressWarnings("unused") Node inliningTarget, PDecimalContext context) {
            return context;
        }

        @Specialization
        static PDecimalContext doDefault(Node inliningTarget, @SuppressWarnings("unused") PNone context) {
            return DecimalContextUtils.getState(inliningTarget).getCurrentContext(inliningTarget);
        }

        @Fallback
        static PDecimalContext doInvalid(Node inliningTarget, @SuppressWarnings("unused") Object context,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.OPTIONAL_DECIMAL_CONTEXT);
        }
    }

    /** Numeric slots accept Decimal and int operands, without invoking conversion protocols. */
    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class ToDecimalNode extends Node {
        public abstract PDecimal execute(Node inliningTarget, Object value);

        @Specialization
        static PDecimal decimal(@SuppressWarnings("unused") Node inliningTarget, PDecimal value) {
            return value;
        }

        @Specialization
        static PDecimal smallInteger(Node inliningTarget, int value) {
            return integer(inliningTarget, value);
        }

        @Specialization
        static PDecimal integer(Node inliningTarget, long value) {
            PythonLanguage language = PythonLanguage.get(inliningTarget);
            return PFactory.createDecimal(language, value);
        }

        @Specialization
        static PDecimal bool(Node inliningTarget, boolean value) {
            return integer(inliningTarget, value ? 1L : 0L);
        }

        @Specialization
        static PDecimal bigInteger(Node inliningTarget, PInt value) {
            DecimalUtils.Value integer = DecimalUtils.fromInteger(value.getValue());
            PythonLanguage language = PythonLanguage.get(inliningTarget);
            return create(language, integer);
        }

        @Specialization
        @TruffleBoundary
        static PDecimal nativeInteger(Node inliningTarget, PythonAbstractNativeObject value) {
            if (!PyLongCheckNode.executeUncached(value)) {
                return null;
            }
            return create(PythonLanguage.get(inliningTarget), DecimalUtils.fromInteger(CastToJavaBigIntegerNode.executeUncached(value)));
        }

        @Fallback
        static PDecimal unsupported(@SuppressWarnings("unused") Node inliningTarget, @SuppressWarnings("unused") Object value) {
            return null;
        }
    }

    static PDecimal create(PythonLanguage language, Value value) {
        return PFactory.createDecimal(language, value.negative(), value.coefficient(), value.exponent(), value.kind());
    }

    private static PDecimal compact(PythonLanguage language, boolean negative, long coefficient, long exponent) {
        return PFactory.createDecimal(language, negative, coefficient, exponent, Kind.FINITE);
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class PlusDecimalNode extends Node {
        public abstract PDecimal execute(VirtualFrame frame, Node inliningTarget, PDecimal self);

        @Specialization
        static PDecimal plus(VirtualFrame frame, Node inliningTarget, PDecimal self,
                        @Cached InlinedBranchProfile nonCompactProfile,
                        @Cached RaiseSignalNode raiseSignal,
                        @Cached PRaiseNode raiseNode) {
            PDecimalContext context = DecimalContextUtils.getState(inliningTarget).getCurrentContext(inliningTarget);
            PythonLanguage language = PythonLanguage.get(inliningTarget);
            PDecimal fast = compactUnary(language, self, context, self.isNegative());
            if (fast != null) {
                return fast;
            }
            nonCompactProfile.enter(inliningTarget);
            Result result;
            try {
                result = DecimalArithmetic.plus(self, context);
            } catch (ArithmeticException e) {
                // BigInteger cannot allocate coefficients beyond its int-sized representation.
                throw raiseNode.raise(inliningTarget, MemoryError);
            }
            return finishResult(frame, language, context, result, raiseSignal);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class MinusDecimalNode extends Node {
        public abstract PDecimal execute(VirtualFrame frame, Node inliningTarget, PDecimal self);

        @Specialization
        static PDecimal minus(VirtualFrame frame, Node inliningTarget, PDecimal self,
                        @Cached InlinedBranchProfile nonCompactProfile,
                        @Cached RaiseSignalNode raiseSignal,
                        @Cached PRaiseNode raiseNode) {
            PDecimalContext context = DecimalContextUtils.getState(inliningTarget).getCurrentContext(inliningTarget);
            PythonLanguage language = PythonLanguage.get(inliningTarget);
            PDecimal fast = compactUnary(language, self, context, !self.isNegative());
            if (fast != null) {
                return fast;
            }
            nonCompactProfile.enter(inliningTarget);
            Result result;
            try {
                result = DecimalArithmetic.minus(self, context);
            } catch (ArithmeticException e) {
                // BigInteger cannot allocate coefficients beyond its int-sized representation.
                throw raiseNode.raise(inliningTarget, MemoryError);
            }
            return finishResult(frame, language, context, result, raiseSignal);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class AbsDecimalNode extends Node {
        public abstract PDecimal execute(VirtualFrame frame, Node inliningTarget, PDecimal self);

        @Specialization
        static PDecimal abs(VirtualFrame frame, Node inliningTarget, PDecimal self,
                        @Cached InlinedBranchProfile nonCompactProfile,
                        @Cached RaiseSignalNode raiseSignal,
                        @Cached PRaiseNode raiseNode) {
            PDecimalContext context = DecimalContextUtils.getState(inliningTarget).getCurrentContext(inliningTarget);
            PythonLanguage language = PythonLanguage.get(inliningTarget);
            PDecimal fast = compactUnary(language, self, context, false);
            if (fast != null) {
                return fast;
            }
            nonCompactProfile.enter(inliningTarget);
            Result result;
            try {
                result = DecimalArithmetic.abs(self, context);
            } catch (ArithmeticException e) {
                // BigInteger cannot allocate coefficients beyond its int-sized representation.
                throw raiseNode.raise(inliningTarget, MemoryError);
            }
            return finishResult(frame, language, context, result, raiseSignal);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class AddDecimalNode extends Node {
        public abstract Object execute(VirtualFrame frame, Node inliningTarget, Object left, Object right);

        @Specialization
        static Object add(VirtualFrame frame, Node inliningTarget, Object left, Object right,
                        @Cached ToDecimalNode toLeft,
                        @Cached ToDecimalNode toRight,
                        @Cached InlinedConditionProfile notImplementedProfile,
                        @Cached InlinedBranchProfile nonCompactProfile,
                        @Cached RaiseSignalNode raiseSignal,
                        @Cached PRaiseNode raiseNode) {
            PDecimal a = toLeft.execute(inliningTarget, left);
            PDecimal b = toRight.execute(inliningTarget, right);
            if (notImplementedProfile.profile(inliningTarget, a == null || b == null)) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            PDecimalContext context = DecimalContextUtils.getState(inliningTarget).getCurrentContext(inliningTarget);
            PythonLanguage language = PythonLanguage.get(inliningTarget);
            PDecimal fast = compactAdd(language, a, b, context, b.isNegative());
            if (fast != null) {
                return fast;
            }
            nonCompactProfile.enter(inliningTarget);
            Result result;
            try {
                result = DecimalArithmetic.add(a, b, context);
            } catch (ArithmeticException e) {
                throw raiseNode.raise(inliningTarget, MemoryError);
            }
            return finishResult(frame, language, context, result, raiseSignal);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class SubtractDecimalNode extends Node {
        public abstract Object execute(VirtualFrame frame, Node inliningTarget, Object left, Object right);

        @Specialization
        static Object subtract(VirtualFrame frame, Node inliningTarget, Object left, Object right,
                        @Cached ToDecimalNode toLeft,
                        @Cached ToDecimalNode toRight,
                        @Cached InlinedConditionProfile notImplementedProfile,
                        @Cached InlinedBranchProfile nonCompactProfile,
                        @Cached RaiseSignalNode raiseSignal,
                        @Cached PRaiseNode raiseNode) {
            PDecimal a = toLeft.execute(inliningTarget, left);
            PDecimal b = toRight.execute(inliningTarget, right);
            if (notImplementedProfile.profile(inliningTarget, a == null || b == null)) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            PDecimalContext context = DecimalContextUtils.getState(inliningTarget).getCurrentContext(inliningTarget);
            PythonLanguage language = PythonLanguage.get(inliningTarget);
            PDecimal fast = compactAdd(language, a, b, context, !b.isNegative());
            if (fast != null) {
                return fast;
            }
            nonCompactProfile.enter(inliningTarget);
            Result result;
            try {
                result = DecimalArithmetic.subtract(a, b, context);
            } catch (ArithmeticException e) {
                throw raiseNode.raise(inliningTarget, MemoryError);
            }
            return finishResult(frame, language, context, result, raiseSignal);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class MultiplyDecimalNode extends Node {
        public abstract Object execute(VirtualFrame frame, Node inliningTarget, Object left, Object right);

        @Specialization
        static Object multiply(VirtualFrame frame, Node inliningTarget, Object left, Object right,
                        @Cached ToDecimalNode toLeft,
                        @Cached ToDecimalNode toRight,
                        @Cached InlinedConditionProfile notImplementedProfile,
                        @Cached InlinedBranchProfile nonCompactProfile,
                        @Cached RaiseSignalNode raiseSignal,
                        @Cached PRaiseNode raiseNode) {
            PDecimal a = toLeft.execute(inliningTarget, left);
            PDecimal b = toRight.execute(inliningTarget, right);
            if (notImplementedProfile.profile(inliningTarget, a == null || b == null)) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            PDecimalContext context = DecimalContextUtils.getState(inliningTarget).getCurrentContext(inliningTarget);
            PythonLanguage language = PythonLanguage.get(inliningTarget);
            PDecimal fast = compactMultiply(language, a, b, context);
            if (fast != null) {
                return fast;
            }
            nonCompactProfile.enter(inliningTarget);
            Result result;
            try {
                result = DecimalArithmetic.multiply(a, b, context);
            } catch (ArithmeticException e) {
                throw raiseNode.raise(inliningTarget, MemoryError);
            }
            return finishResult(frame, language, context, result, raiseSignal);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class DivideDecimalNode extends Node {
        public abstract Object execute(VirtualFrame frame, Node inliningTarget, Object left, Object right);

        @Specialization
        static Object divide(VirtualFrame frame, Node inliningTarget, Object left, Object right,
                        @Cached ToDecimalNode toLeft,
                        @Cached ToDecimalNode toRight,
                        @Cached InlinedConditionProfile notImplementedProfile,
                        @Cached RaiseSignalNode raiseSignal,
                        @Cached PRaiseNode raiseNode) {
            PDecimal a = toLeft.execute(inliningTarget, left);
            PDecimal b = toRight.execute(inliningTarget, right);
            if (notImplementedProfile.profile(inliningTarget, a == null || b == null)) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            PDecimalContext context = DecimalContextUtils.getState(inliningTarget).getCurrentContext(inliningTarget);
            Result result;
            try {
                result = DecimalArithmetic.divide(a, b, context);
            } catch (ArithmeticException e) {
                throw raiseNode.raise(inliningTarget, MemoryError);
            }
            PythonLanguage language = PythonLanguage.get(inliningTarget);
            return finishResult(frame, language, context, result, raiseSignal);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class DivideIntDecimalNode extends Node {
        public abstract Object execute(VirtualFrame frame, Node inliningTarget, Object left, Object right);

        @Specialization
        static Object divideInt(VirtualFrame frame, Node inliningTarget, Object left, Object right,
                        @Cached ToDecimalNode toLeft,
                        @Cached ToDecimalNode toRight,
                        @Cached InlinedConditionProfile notImplementedProfile,
                        @Cached RaiseSignalNode raiseSignal,
                        @Cached PRaiseNode raiseNode) {
            PDecimal a = toLeft.execute(inliningTarget, left);
            PDecimal b = toRight.execute(inliningTarget, right);
            if (notImplementedProfile.profile(inliningTarget, a == null || b == null)) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            PDecimalContext context = DecimalContextUtils.getState(inliningTarget).getCurrentContext(inliningTarget);
            Result result;
            try {
                result = DecimalArithmetic.divideInt(a, b, context);
            } catch (ArithmeticException e) {
                throw raiseNode.raise(inliningTarget, MemoryError);
            }
            PythonLanguage language = PythonLanguage.get(inliningTarget);
            return finishResult(frame, language, context, result, raiseSignal);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class RemainderDecimalNode extends Node {
        public abstract Object execute(VirtualFrame frame, Node inliningTarget, Object left, Object right);

        @Specialization
        static Object remainder(VirtualFrame frame, Node inliningTarget, Object left, Object right,
                        @Cached ToDecimalNode toLeft,
                        @Cached ToDecimalNode toRight,
                        @Cached InlinedConditionProfile notImplementedProfile,
                        @Cached RaiseSignalNode raiseSignal,
                        @Cached PRaiseNode raiseNode) {
            PDecimal a = toLeft.execute(inliningTarget, left);
            PDecimal b = toRight.execute(inliningTarget, right);
            if (notImplementedProfile.profile(inliningTarget, a == null || b == null)) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            PDecimalContext context = DecimalContextUtils.getState(inliningTarget).getCurrentContext(inliningTarget);
            Result result;
            try {
                result = DecimalArithmetic.remainder(a, b, context);
            } catch (ArithmeticException e) {
                throw raiseNode.raise(inliningTarget, MemoryError);
            }
            PythonLanguage language = PythonLanguage.get(inliningTarget);
            return finishResult(frame, language, context, result, raiseSignal);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class DivmodDecimalNode extends Node {
        public abstract Object execute(VirtualFrame frame, Node inliningTarget, Object left, Object right);

        @Specialization
        static Object divmod(VirtualFrame frame, Node inliningTarget, Object left, Object right,
                        @Cached ToDecimalNode toLeft,
                        @Cached ToDecimalNode toRight,
                        @Cached InlinedConditionProfile notImplementedProfile,
                        @Cached RaiseSignalNode raiseSignal,
                        @Cached PRaiseNode raiseNode) {
            PDecimal a = toLeft.execute(inliningTarget, left);
            PDecimal b = toRight.execute(inliningTarget, right);
            if (notImplementedProfile.profile(inliningTarget, a == null || b == null)) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            PDecimalContext context = DecimalContextUtils.getState(inliningTarget).getCurrentContext(inliningTarget);
            ResultWithRemainder result;
            try {
                result = DecimalArithmetic.divmod(a, b, context);
            } catch (ArithmeticException e) {
                throw raiseNode.raise(inliningTarget, MemoryError);
            }
            DecimalContextUtils.addStatus(frame, context, result.status(), raiseSignal);
            PythonLanguage language = PythonLanguage.get(inliningTarget);
            PDecimal value = create(language, result.value());
            PDecimal remainder = create(language, result.remainder());
            return PFactory.createTuple(language, new Object[]{value, remainder});
        }
    }

    private static PDecimal finishResult(VirtualFrame frame, PythonLanguage language, PDecimalContext context, Result result, RaiseSignalNode raiseSignal) {
        DecimalContextUtils.addStatus(frame, context, result.status(), raiseSignal);
        return create(language, result.value());
    }

    private static boolean isCompactFinite(PDecimal value) {
        return value.getKind() == Kind.FINITE && value.isCompact();
    }

    private static PDecimal compactUnary(PythonLanguage language, PDecimal self, PDecimalContext context, boolean negative) {
        if (!isCompactFinite(self) || !DecimalArithmetic.fitsContext(self.getCompactCoefficient() == 0, self.getExponent(), DecimalArithmetic.digits(self.getCompactCoefficient()), context)) {
            return null;
        }
        if (self.getCompactCoefficient() == 0 && context.getRounding() != DecimalRounding.ROUND_FLOOR) {
            negative = false;
        }
        return compact(language, negative, self.getCompactCoefficient(), self.getExponent());
    }

    /** Scale a nonnegative coefficient, or return -1 if it no longer fits a signed long. */
    static long scaleCompactCoefficient(long coefficient, long shift) {
        assert coefficient >= 0 && shift >= 0;
        if (coefficient == 0) {
            return 0;
        }
        // Even a one-digit coefficient cannot fit after a shift of 19 decimal places.
        if (shift > 18) {
            return -1;
        }
        for (int i = 0; i < shift; i++) {
            if (coefficient > Long.MAX_VALUE / 10) {
                return -1;
            }
            coefficient *= 10;
        }
        return coefficient;
    }

    private static PDecimal compactAdd(PythonLanguage language, PDecimal a, PDecimal b, PDecimalContext context, boolean rightNegative) {
        if (!isCompactFinite(a) || !isCompactFinite(b)) {
            return null;
        }
        // Preserve the finer quantum by scaling the operand with the larger exponent.
        long left = a.getCompactCoefficient();
        long right = b.getCompactCoefficient();
        long exponent = a.getExponent();
        if (exponent > b.getExponent()) {
            left = scaleCompactCoefficient(left, exponent - b.getExponent());
            exponent = b.getExponent();
        } else if (exponent < b.getExponent()) {
            right = scaleCompactCoefficient(right, b.getExponent() - exponent);
        }
        if (left < 0 || right < 0) {
            return null;
        }
        if (a.isNegative()) {
            left = -left;
        }
        if (rightNegative) {
            right = -right;
        }
        long sum;
        try {
            sum = Math.addExact(left, right);
        } catch (ArithmeticException e) {
            return null;
        }
        if (sum == Long.MIN_VALUE) {
            return null;
        }
        long coefficient = Math.abs(sum);
        boolean negative;
        if (sum == 0) {
            if (a.isNegative() == rightNegative) {
                negative = a.isNegative();
            } else {
                negative = context.getRounding() == DecimalRounding.ROUND_FLOOR;
            }
        } else {
            negative = sum < 0;
        }
        if (DecimalArithmetic.fitsContext(coefficient == 0, exponent, DecimalArithmetic.digits(coefficient), context)) {
            return compact(language, negative, coefficient, exponent);
        }
        return null;
    }

    private static PDecimal compactMultiply(PythonLanguage language, PDecimal a, PDecimal b, PDecimalContext context) {
        if (!isCompactFinite(a) || !isCompactFinite(b)) {
            return null;
        }
        long coefficient;
        try {
            coefficient = Math.multiplyExact(a.getCompactCoefficient(), b.getCompactCoefficient());
        } catch (ArithmeticException e) {
            return null;
        }
        long exponent = a.getExponent() + b.getExponent();
        boolean negative = a.isNegative() ^ b.isNegative();
        if (DecimalArithmetic.fitsContext(coefficient == 0, exponent, DecimalArithmetic.digits(coefficient), context)) {
            return compact(language, negative, coefficient, exponent);
        }
        return null;
    }

    @GenerateInline(false) // Error path
    @GenerateUncached
    public abstract static class RaiseSignalNode extends Node {
        public abstract void execute(VirtualFrame frame, int trapped);

        @Specialization
        static void raise(VirtualFrame frame, int trapped,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached CallNode callNode,
                        @Cached PRaiseNode raiseNode) {
            DecimalModuleState state = DecimalContextUtils.getState(inliningTarget);
            Object[] conditions = new Object[Integer.bitCount(trapped)];
            int index = 0;
            for (DecimalExceptionTypes condition : DecimalContextUtils.INVALID_CONDITIONS) {
                if ((trapped & DecimalContextUtils.conditionMask(condition)) != 0) {
                    conditions[index++] = state.getExceptionType(condition);
                }
            }
            for (int i = 1; i < DecimalContextUtils.SIGNALS.length; i++) {
                if ((trapped & DecimalContextUtils.mask(DecimalContextUtils.SIGNALS[i])) != 0) {
                    conditions[index++] = state.getExceptionType(DecimalContextUtils.SIGNALS[i]);
                }
            }
            assert index == conditions.length;
            for (DecimalExceptionTypes signal : DecimalContextUtils.SIGNALS) {
                if ((trapped & DecimalContextUtils.mask(signal)) != 0) {
                    PList args = PFactory.createList(language, conditions);
                    Object exception = callNode.execute(frame, state.getExceptionType(signal), args);
                    throw raiseNode.raiseExceptionObject(inliningTarget, exception);
                }
            }
        }
    }
}
