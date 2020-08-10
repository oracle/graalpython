/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.ints;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__FORMAT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LT__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinConstructors;
import com.oracle.graal.python.builtins.modules.BuiltinConstructorsFactory;
import com.oracle.graal.python.builtins.modules.MathGuards;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.array.PArray;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.PByteArray;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.bytes.PIBytesLike;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.FromNativeSubclassNode;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeObject;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeVoidPtr;
import com.oracle.graal.python.builtins.objects.common.FormatNodeBase;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallVarargsNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.control.GetIteratorExpressionNode.GetIteratorNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.formatting.FloatFormatter;
import com.oracle.graal.python.runtime.formatting.IntegerFormatter;
import com.oracle.graal.python.runtime.formatting.InternalFormat;
import com.oracle.graal.python.runtime.formatting.InternalFormat.Spec;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
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
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PInt)
public class IntBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return IntBuiltinsFactory.getFactories();
    }

    private abstract static class IntBinaryBuiltinNode extends PythonBinaryBuiltinNode {
        private final BranchProfile divisionByZeroProfile = BranchProfile.create();

        protected void raiseDivisionByZero(boolean cond) {
            if (cond) {
                divisionByZeroProfile.enter();
                throw raise(PythonErrorType.ZeroDivisionError, ErrorMessages.S_DIVISION_OR_MODULO_BY_ZERO, "integer");
            }
        }
    }

    @Builtin(name = SpecialMethodNames.__ROUND__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @ImportStatic(MathGuards.class)
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class RoundNode extends PythonBinaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public int roundIntNone(int arg, PNone n) {
            return arg;
        }

        @SuppressWarnings("unused")
        @Specialization
        public long roundLongNone(long arg, PNone n) {
            return arg;
        }

        @SuppressWarnings("unused")
        @Specialization
        public PInt roundPIntNone(PInt arg, PNone n) {
            return factory().createInt(arg.getValue());
        }

        @Specialization
        public Object roundLongInt(long arg, int n) {
            if (n >= 0) {
                return arg;
            }
            return makeInt(op(arg, n));
        }

        @Specialization
        public Object roundPIntInt(PInt arg, int n) {
            if (n >= 0) {
                return arg;
            }
            return makeInt(op(arg.getValue(), n));
        }

        @Specialization
        public Object roundLongLong(long arg, long n) {
            if (n >= 0) {
                return arg;
            }
            if (n < Integer.MIN_VALUE) {
                return 0;
            }
            return makeInt(op(arg, (int) n));
        }

        @Specialization
        public Object roundPIntLong(PInt arg, long n) {
            if (n >= 0) {
                return arg;
            }
            if (n < Integer.MIN_VALUE) {
                return 0;
            }
            return makeInt(op(arg.getValue(), (int) n));
        }

        @Specialization
        public Object roundPIntLong(long arg, PInt n) {
            if (n.isZeroOrPositive()) {
                return arg;
            }
            try {
                return makeInt(op(arg, n.intValueExact()));
            } catch (ArithmeticException e) {
                // n is < -2^31, max. number of base-10 digits in BigInteger is 2^31 * log10(2)
                return 0;
            }
        }

        @Specialization
        public Object roundPIntPInt(PInt arg, PInt n) {
            if (n.isZeroOrPositive()) {
                return arg;
            }
            try {
                return makeInt(op(arg.getValue(), n.intValueExact()));
            } catch (ArithmeticException e) {
                // n is < -2^31, max. number of base-10 digits in BigInteger is 2^31 * log10(2)
                return 0;
            }
        }

        @Specialization(guards = {"!isInteger(n)"})
        @SuppressWarnings("unused")
        public Object roundPIntPInt(Object arg, Object n) {
            throw raise(PythonErrorType.TypeError, ErrorMessages.OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER, n);
        }

        private Object makeInt(BigDecimal d) {
            try {
                return intValueExact(d);
            } catch (ArithmeticException e) {
                // does not fit int, so try long
            }
            try {
                return longValueExact(d);
            } catch (ArithmeticException e) {
                // does not fit long, try BigInteger
            }
            try {
                return factory().createInt(d.toBigIntegerExact());
            } catch (ArithmeticException e) {
                // has non-zero fractional part, which should not happen
                throw CompilerDirectives.shouldNotReachHere("non-integer produced after rounding an integer", e);
            }
        }

        @TruffleBoundary
        private static int intValueExact(BigDecimal d) {
            return d.intValueExact();
        }

        @TruffleBoundary
        private static long longValueExact(BigDecimal d) {
            return d.longValueExact();
        }

        @TruffleBoundary
        private static BigDecimal op(long arg, int n) {
            try {
                return new BigDecimal(arg).setScale(n, RoundingMode.HALF_EVEN);
            } catch (ArithmeticException e) {
                // -n exceeds max. number of base-10 digits in BigInteger
                return BigDecimal.ZERO;
            }
        }

        @TruffleBoundary
        private static BigDecimal op(BigInteger arg, int n) {
            try {
                return new BigDecimal(arg).setScale(n, RoundingMode.HALF_EVEN);
            } catch (ArithmeticException e) {
                // -n exceeds max. number of base-10 digits in BigInteger
                return BigDecimal.ZERO;
            }
        }
    }

    @Builtin(name = SpecialMethodNames.__RADD__, minNumOfPositionalArgs = 2)
    @Builtin(name = SpecialMethodNames.__ADD__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class AddNode extends PythonBinaryBuiltinNode {

        @Specialization(rewriteOn = ArithmeticException.class)
        int add(int left, int right) {
            return Math.addExact(left, right);
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        long addLong(long left, long right) {
            return Math.addExact(left, right);
        }

        @Specialization
        Object addLongWithOverflow(long x, long y) {
            /* Inlined version of Math.addExact(x, y) with BigInteger fallback. */
            long r = x + y;
            // HD 2-12 Overflow iff both arguments have the opposite sign of the result
            if (((x ^ r) & (y ^ r)) < 0) {
                return factory().createInt(op(PInt.longToBigInteger(x), PInt.longToBigInteger(y)));
            }
            return r;
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        Object addPIntLongAndNarrow(PInt left, long right) {
            return PInt.longValueExact(op(left.getValue(), PInt.longToBigInteger(right)));
        }

        @Specialization(replaces = "addPIntLongAndNarrow")
        Object addPIntLong(PInt left, long right) {
            return factory().createInt(op(left.getValue(), PInt.longToBigInteger(right)));
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        Object addLongPIntAndNarrow(long left, PInt right) {
            return PInt.longValueExact(op(PInt.longToBigInteger(left), right.getValue()));
        }

        @Specialization(replaces = "addLongPIntAndNarrow")
        Object addLongPInt(long left, PInt right) {
            return factory().createInt(op(PInt.longToBigInteger(left), right.getValue()));
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        Object addPIntPIntAndNarrow(PInt left, PInt right) {
            return PInt.longValueExact(op(left.getValue(), right.getValue()));
        }

        @Specialization(replaces = "addPIntPIntAndNarrow")
        Object addPIntPInt(PInt left, PInt right) {
            return factory().createInt(op(left.getValue(), right.getValue()));
        }

        @TruffleBoundary
        BigInteger op(BigInteger left, BigInteger right) {
            return left.add(right);
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = SpecialMethodNames.__RSUB__, minNumOfPositionalArgs = 2, reverseOperation = true)
    @Builtin(name = SpecialMethodNames.__SUB__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class SubNode extends PythonBinaryBuiltinNode {

        @Specialization(rewriteOn = ArithmeticException.class)
        static int doII(int x, int y) throws ArithmeticException {
            return Math.subtractExact(x, y);
        }

        @Specialization
        static long doIIOvf(int x, int y) {
            return (long) x - (long) y;
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        static long doLL(long x, long y) throws ArithmeticException {
            return Math.subtractExact(x, y);
        }

        @Specialization
        Object doLongWithOverflow(long x, long y) {
            /* Inlined version of Math.subtractExact(x, y) with BigInteger fallback. */
            long r = x - y;
            // HD 2-12 Overflow iff the arguments have different signs and
            // the sign of the result is different than the sign of x
            if (((x ^ y) & (x ^ r)) < 0) {
                return factory().createInt(op(PInt.longToBigInteger(x), PInt.longToBigInteger(y)));
            }
            return r;
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        long doPIntLongAndNarrow(PInt left, long right) {
            return PInt.longValueExact(op(left.getValue(), PInt.longToBigInteger(right)));
        }

        @Specialization(replaces = "doPIntLongAndNarrow")
        PInt doPIntLong(PInt left, long right) {
            return factory().createInt(op(left.getValue(), PInt.longToBigInteger(right)));
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        long doLongPIntAndNarrow(long left, PInt right) {
            return PInt.longValueExact(op(PInt.longToBigInteger(left), right.getValue()));
        }

        @Specialization(replaces = "doLongPIntAndNarrow")
        PInt doLongPInt(long left, PInt right) {
            return factory().createInt(op(PInt.longToBigInteger(left), right.getValue()));
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        long doPIntPIntAndNarrow(PInt left, PInt right) {
            return PInt.longValueExact(op(left.getValue(), right.getValue()));
        }

        @Specialization(replaces = "doPIntPIntAndNarrow")
        PInt doPIntPInt(PInt left, PInt right) {
            return factory().createInt(op(left.getValue(), right.getValue()));
        }

        @TruffleBoundary
        private static BigInteger op(BigInteger left, BigInteger right) {
            return left.subtract(right);
        }

        @Fallback
        @SuppressWarnings("unused")
        static PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = SpecialMethodNames.__RTRUEDIV__, minNumOfPositionalArgs = 2, reverseOperation = true)
    @Builtin(name = SpecialMethodNames.__TRUEDIV__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class TrueDivNode extends PythonBinaryBuiltinNode {

        @Specialization
        double divII(int x, int y) {
            return divDD(x, y);
        }

        @Specialization
        double divLL(long x, long y) {
            return divDD(x, y);
        }

        double divDD(double x, double y) {
            if (y == 0) {
                throw raise(PythonErrorType.ZeroDivisionError, ErrorMessages.DIVISION_BY_ZERO);
            }
            return x / y;
        }

        @Specialization
        double doPI(long left, PInt right) {
            if (right.isZero()) {
                throw raise(PythonErrorType.ZeroDivisionError, ErrorMessages.DIVISION_BY_ZERO);
            }
            return op(PInt.longToBigInteger(left), right.getValue(), getRaiseNode());
        }

        @Specialization
        double doPL(PInt left, long right) {
            if (right == 0) {
                throw raise(PythonErrorType.ZeroDivisionError, ErrorMessages.DIVISION_BY_ZERO);
            }
            return op(left.getValue(), PInt.longToBigInteger(right), getRaiseNode());
        }

        @Specialization
        double doPP(PInt left, PInt right) {
            if (right.isZero()) {
                throw raise(PythonErrorType.ZeroDivisionError, ErrorMessages.DIVISION_BY_ZERO);
            }
            return op(left.getValue(), right.getValue(), getRaiseNode());
        }

        /*
         * We must take special care to do double conversion late (if possible), to avoid loss of
         * precision.
         */
        @TruffleBoundary
        private static double op(BigInteger a, BigInteger b, PRaiseNode raiseNode) {
            final int precisionOfDouble = 17;
            if (fitsIntoDouble(a) && fitsIntoDouble(b)) {
                return a.doubleValue() / b.doubleValue();
            }
            BigDecimal aDecimal = new BigDecimal(a);
            BigDecimal bDecimal = new BigDecimal(b);
            int aPrec = aDecimal.precision();
            int bPrec = bDecimal.precision();
            BigDecimal result = aDecimal.divide(bDecimal, bPrec - aPrec + precisionOfDouble, RoundingMode.HALF_EVEN);
            double d = result.doubleValue();
            if (Double.isInfinite(d)) {
                throw raiseNode.raise(OverflowError, ErrorMessages.INTEGER_DIVISION_RESULT_TOO_LARGE);
            }
            return d;
        }

        private static boolean fitsIntoDouble(BigInteger x) {
            return x.bitLength() < 53;
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = SpecialMethodNames.__RFLOORDIV__, minNumOfPositionalArgs = 2, reverseOperation = true)
    @Builtin(name = SpecialMethodNames.__FLOORDIV__, minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class FloorDivNode extends IntBinaryBuiltinNode {
        @Specialization
        int doLL(int left, int right) {
            raiseDivisionByZero(right == 0);
            return Math.floorDiv(left, right);
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        long doLL(long left, long right) {
            if (left == Long.MIN_VALUE && right == -1) {
                throw new ArithmeticException();
            }
            raiseDivisionByZero(right == 0);
            return Math.floorDiv(left, right);
        }

        @Specialization(replaces = "doLL")
        PInt doLLOverflow(long left, long right) {
            return doPiPi(factory().createInt(left), factory().createInt(right));
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        int doLPi(int left, PInt right) throws ArithmeticException {
            raiseDivisionByZero(right.isZero());
            return Math.floorDiv(left, right.intValueExact());
        }

        @Specialization
        int doLPiOvf(int left, PInt right) {
            raiseDivisionByZero(right.isZero());
            try {
                return Math.floorDiv(left, right.intValueExact());
            } catch (ArithmeticException e) {
                return left < 0 == right.isNegative() ? 0 : -1;
            }
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        long doLPi(long left, PInt right) throws ArithmeticException {
            raiseDivisionByZero(right.isZero());
            return Math.floorDiv(left, right.longValueExact());
        }

        @Specialization
        long doLPiOvf(long left, PInt right) {
            raiseDivisionByZero(right.isZero());
            try {
                return Math.floorDiv(left, right.longValueExact());
            } catch (ArithmeticException e) {
                return left < 0 == right.isNegative() ? 0 : -1;
            }
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        long doPiIAndNarrow(PInt left, int right) {
            raiseDivisionByZero(right == 0);
            return PInt.longValueExact(op(left.getValue(), PInt.longToBigInteger(right)));
        }

        @Specialization(replaces = "doPiIAndNarrow")
        PInt doPiI(PInt left, int right) {
            raiseDivisionByZero(right == 0);
            return factory().createInt(op(left.getValue(), PInt.longToBigInteger(right)));
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        long doPiLAndNarrow(PInt left, long right) {
            raiseDivisionByZero(right == 0);
            return PInt.longValueExact(op(left.getValue(), PInt.longToBigInteger(right)));
        }

        @Specialization(replaces = "doPiLAndNarrow")
        PInt doPiL(PInt left, long right) {
            raiseDivisionByZero(right == 0);
            return factory().createInt(op(left.getValue(), PInt.longToBigInteger(right)));
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        long doPiPiAndNarrow(PInt left, PInt right) {
            raiseDivisionByZero(right.isZero());
            return PInt.longValueExact(op(left.getValue(), right.getValue()));
        }

        @Specialization(replaces = "doPiPiAndNarrow")
        PInt doPiPi(PInt left, PInt right) {
            raiseDivisionByZero(right.isZero());
            return factory().createInt(op(left.getValue(), right.getValue()));
        }

        @TruffleBoundary
        static BigInteger op(BigInteger left, BigInteger right) {
            // Math.floorDiv for BigInteger
            BigInteger r = left.divide(right);
            // if the signs are different and modulo not zero, round down
            if ((left.xor(right)).signum() < 0 && (r.multiply(right).compareTo(left)) != 0) {
                r = r.subtract(BigInteger.ONE);
            }
            return r;
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object right, Object left) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

    }

    @Builtin(name = SpecialMethodNames.__RDIVMOD__, minNumOfPositionalArgs = 2, reverseOperation = true)
    @Builtin(name = SpecialMethodNames.__DIVMOD__, minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class DivModNode extends IntBinaryBuiltinNode {
        @Specialization
        PTuple doLL(int left, int right) {
            raiseDivisionByZero(right == 0);
            return factory().createTuple(new Object[]{Math.floorDiv(left, right), Math.floorMod(left, right)});
        }

        @Specialization
        PTuple doLL(long left, long right) {
            raiseDivisionByZero(right == 0);
            return factory().createTuple(new Object[]{Math.floorDiv(left, right), Math.floorMod(left, right)});
        }

        @Specialization(guards = {"accepts(left)", "accepts(right)"})
        PTuple doGenericInt(VirtualFrame frame, Object left, Object right,
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
            return obj instanceof Integer || obj instanceof Long || obj instanceof PInt;
        }
    }

    @Builtin(name = SpecialMethodNames.__MOD__, minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class ModNode extends IntBinaryBuiltinNode {
        @Specialization
        int doII(int left, int right) {
            raiseDivisionByZero(right == 0);
            return Math.floorMod(left, right);
        }

        @Specialization
        long doLL(long left, long right) {
            raiseDivisionByZero(right == 0);
            return Math.floorMod(left, right);
        }

        @Specialization(guards = "right.isZeroOrPositive()", rewriteOn = ArithmeticException.class)
        long doLPiAndNarrow(long left, PInt right) {
            raiseDivisionByZero(right.isZero());
            return PInt.longValueExact(op(PInt.longToBigInteger(left), right.getValue()));
        }

        @Specialization(guards = "right.isZeroOrPositive()", replaces = "doLPiAndNarrow")
        PInt doLPi(long left, PInt right) {
            raiseDivisionByZero(right.isZero());
            return factory().createInt(op(PInt.longToBigInteger(left), right.getValue()));
        }

        @Specialization(guards = "!right.isZeroOrPositive()", rewriteOn = ArithmeticException.class)
        long doLPiNegativeAndNarrow(long left, PInt right) {
            raiseDivisionByZero(right.isZero());
            return PInt.longValueExact(opNeg(PInt.longToBigInteger(left), right.getValue()));
        }

        @Specialization(guards = "!right.isZeroOrPositive()", replaces = "doLPiNegativeAndNarrow")
        PInt doLPiNegative(long left, PInt right) {
            raiseDivisionByZero(right.isZero());
            return factory().createInt(opNeg(PInt.longToBigInteger(left), right.getValue()));
        }

        @Specialization(guards = "right >= 0", rewriteOn = ArithmeticException.class)
        long doPiLAndNarrow(PInt left, long right) {
            raiseDivisionByZero(right == 0);
            return PInt.longValueExact(op(left.getValue(), PInt.longToBigInteger(right)));
        }

        @Specialization(guards = "right >= 0", replaces = "doPiLAndNarrow")
        PInt doPiL(PInt left, long right) {
            raiseDivisionByZero(right == 0);
            return factory().createInt(op(left.getValue(), PInt.longToBigInteger(right)));
        }

        @Specialization(guards = "right < 0", rewriteOn = ArithmeticException.class)
        long doPiLNegAndNarrow(PInt left, long right) {
            raiseDivisionByZero(right == 0);
            return PInt.longValueExact(opNeg(left.getValue(), PInt.longToBigInteger(right)));
        }

        @Specialization(guards = "right < 0", replaces = "doPiLNegAndNarrow")
        PInt doPiLNeg(PInt left, long right) {
            raiseDivisionByZero(right == 0);
            return factory().createInt(opNeg(left.getValue(), PInt.longToBigInteger(right)));
        }

        @Specialization(guards = "right.isZeroOrPositive()", rewriteOn = ArithmeticException.class)
        long doPiPiAndNarrow(PInt left, PInt right) {
            raiseDivisionByZero(right.isZero());
            return PInt.longValueExact(op(left.getValue(), right.getValue()));
        }

        @Specialization(guards = "right.isZeroOrPositive()", replaces = "doPiPiAndNarrow")
        PInt doPiPi(PInt left, PInt right) {
            raiseDivisionByZero(right.isZero());
            return factory().createInt(op(left.getValue(), right.getValue()));
        }

        @Specialization(guards = "!right.isZeroOrPositive()", rewriteOn = ArithmeticException.class)
        long doPiPiNegAndNarrow(PInt left, PInt right) {
            return PInt.longValueExact(opNeg(left.getValue(), right.getValue()));
        }

        @Specialization(guards = "!right.isZeroOrPositive()", replaces = "doPiPiNegAndNarrow")
        PInt doPiPiNeg(PInt left, PInt right) {
            return factory().createInt(opNeg(left.getValue(), right.getValue()));
        }

        @TruffleBoundary
        static BigInteger op(BigInteger a, BigInteger b) {
            return a.mod(b);
        }

        @TruffleBoundary
        static BigInteger opNeg(BigInteger a, BigInteger b) {
            if (a.signum() == 0) {
                return BigInteger.ZERO;
            }
            BigInteger mod = a.mod(b.negate());
            if (mod.signum() == 0) {
                return BigInteger.ZERO;
            }
            return a.mod(b.negate()).subtract(b.negate());
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = SpecialMethodNames.__RMUL__, minNumOfPositionalArgs = 2)
    @Builtin(name = SpecialMethodNames.__MUL__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class MulNode extends PythonBinaryBuiltinNode {

        @Specialization(rewriteOn = ArithmeticException.class)
        int doII(int x, int y) throws ArithmeticException {
            return Math.multiplyExact(x, y);
        }

        @Specialization(replaces = "doII")
        long doIIL(int x, int y) {
            return x * (long) y;
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        long doLL(long x, long y) {
            return Math.multiplyExact(x, y);
        }

        @Specialization
        Object doLongWithOverflow(long x, long y) {
            /* Inlined version of Math.multiplyExact(x, y) with BigInteger fallback. */
            long r = x * y;
            long ax = Math.abs(x);
            long ay = Math.abs(y);
            if (((ax | ay) >>> 31 != 0)) {
                // Some bits greater than 2^31 that might cause overflow
                // Check the result using the divide operator
                // and check for the special case of Long.MIN_VALUE * -1
                if (((y != 0) && (r / y != x)) ||
                                (x == Long.MIN_VALUE && y == -1)) {
                    return factory().createInt(mul(PInt.longToBigInteger(x), PInt.longToBigInteger(y)));
                }
            }
            return r;
        }

        @Specialization(guards = "right == 0")
        int doPIntLongZero(@SuppressWarnings("unused") PInt left, @SuppressWarnings("unused") long right) {
            return 0;
        }

        @Specialization(guards = "right == 1")
        PInt doPIntLongOne(PInt left, @SuppressWarnings("unused") long right) {
            // we must return a new object with the same value
            return factory().createInt(left.getValue());
        }

        @Specialization(guards = {"right != 0", "right != 1"})
        PInt doPIntLong(PInt left, long right) {
            return factory().createInt(mul(left.getValue(), PInt.longToBigInteger(right)));
        }

        @Specialization
        PInt doPIntPInt(PInt left, PInt right) {
            return factory().createInt(mul(left.getValue(), right.getValue()));
        }

        @TruffleBoundary
        BigInteger mul(BigInteger a, BigInteger b) {
            if (!BigInteger.ZERO.equals(b) && b.and(b.subtract(BigInteger.ONE)).equals(BigInteger.ZERO)) {
                return bigIntegerShift(a, b.getLowestSetBit());
            } else {
                return bigIntegerMul(a, b);
            }
        }

        @TruffleBoundary
        BigInteger bigIntegerMul(BigInteger a, BigInteger b) {
            return a.multiply(b);
        }

        @TruffleBoundary
        BigInteger bigIntegerShift(BigInteger a, int n) {
            return a.shiftLeft(n);
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = SpecialMethodNames.__RPOW__, minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3, reverseOperation = true)
    @Builtin(name = SpecialMethodNames.__POW__, minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(MathGuards.class)
    @ReportPolymorphism
    abstract static class PowNode extends PythonTernaryBuiltinNode {

        @Specialization(guards = "right >= 0", rewriteOn = ArithmeticException.class)
        static long doLLFast(long left, long right, @SuppressWarnings("unused") PNone none) {
            long result = 1;
            long exponent = right;
            long base = left;
            while (exponent != 0) {
                if ((exponent & 1) != 0) {
                    result = Math.multiplyExact(result, base);
                }
                exponent >>= 1;
                if (exponent != 0) {    // prevent overflow in last iteration
                    base = Math.multiplyExact(base, base);
                }
            }
            return result;
        }

        @Specialization(guards = "right >= 0", replaces = "doLLFast")
        PInt doLLPos(long left, long right, @SuppressWarnings("unused") PNone none) {
            return factory().createInt(op(PInt.longToBigInteger(left), right));
        }

        @Specialization(guards = "right < 0")
        double doLLNeg(long left, long right, @SuppressWarnings("unused") PNone none,
                        @Shared("leftIsZero") @Cached ConditionProfile leftIsZero) {
            if (leftIsZero.profile(left == 0)) {
                throw raise(PythonBuiltinClassType.ZeroDivisionError, ErrorMessages.POW_ZERO_CANNOT_RAISE_TO_NEGATIVE_POWER);
            }
            return Math.pow(left, right);
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        Object doLPNarrow(long left, PInt right, @SuppressWarnings("unused") PNone none,
                        @Shared("leftIsZero") @Cached ConditionProfile leftIsZero) {
            long lright = right.longValueExact();
            if (lright >= 0) {
                return doLLFast(left, lright, none);
            }
            return doLLNeg(left, lright, none, leftIsZero);
        }

        @Specialization(replaces = "doLPNarrow")
        Object doLP(long left, PInt right, @SuppressWarnings("unused") PNone none) {
            Object result = op(PInt.longToBigInteger(left), right.getValue());
            if (result instanceof BigInteger) {
                return factory().createInt((BigInteger) result);
            } else {
                return result;
            }
        }

        @Specialization(guards = "right >= 0", rewriteOn = ArithmeticException.class)
        long doPLNarrow(PInt left, long right, @SuppressWarnings("unused") PNone none) {
            return PInt.longValueExact(op(left.getValue(), right));
        }

        @Specialization(guards = "right >= 0", replaces = "doPLNarrow")
        PInt doPLPos(PInt left, long right, @SuppressWarnings("unused") PNone none) {
            return factory().createInt(op(left.getValue(), right));
        }

        @Specialization(guards = "right < 0")
        double doPLNeg(PInt left, long right, @SuppressWarnings("unused") PNone none,
                        @Shared("leftIsZero") @Cached ConditionProfile leftIsZero) {
            if (leftIsZero.profile(left.isZero())) {
                throw raise(PythonBuiltinClassType.ZeroDivisionError, ErrorMessages.POW_ZERO_CANNOT_RAISE_TO_NEGATIVE_POWER);
            }
            return TrueDivNode.op(BigInteger.ONE, op(left.getValue(), -right), getRaiseNode());
        }

        @Specialization
        Object doPP(PInt left, PInt right, @SuppressWarnings("unused") PNone none) {
            Object result = op(left.getValue(), right.getValue());
            if (result instanceof BigInteger) {
                return factory().createInt((BigInteger) result);
            } else {
                return result;
            }
        }

        @Specialization(guards = {"right >= 0", "mod > 0"})
        static long doLLPosLPos(long left, long right, long mod) {
            try {
                return PInt.longValueExact(op(left, right, mod));
            } catch (ArithmeticException e) {
                // cannot happen since we took modulo long AND 'mod > 0'
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException();
            }
        }

        @Specialization(guards = "right >= 0", replaces = "doLLPosLPos")
        long doLLPosLGeneric(long left, long right, long mod,
                        @Cached("createBinaryProfile()") ConditionProfile errorProfile,
                        @Cached("createBinaryProfile()") ConditionProfile modNegativeProfile) {
            if (errorProfile.profile(mod == 0)) {
                throw raise(ValueError, ErrorMessages.POW_THIRD_ARG_CANNOT_BE_ZERO);
            }
            try {
                if (modNegativeProfile.profile(mod < 0)) {
                    return PInt.longValueExact(opNeg(left, right, mod));
                }
                return PInt.longValueExact(op(left, right, mod));
            } catch (ArithmeticException e) {
                // cannot happen since we took modulo long AND 'mod != 0'
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException();
            }
        }

        // see cpython://Objects/longobject.c#long_pow
        @Specialization(replaces = "doPP")
        Object powModulo(Object x, Object y, Object z) {
            if (!(MathGuards.isInteger(x) && MathGuards.isInteger(y))) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            Object result;
            if (z instanceof PNone) {
                result = objectOp(x, y);
            } else if (MathGuards.isInteger(z)) {
                result = objectOp(x, y, z);
            } else {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            if (result instanceof BigInteger) {
                return factory().createInt((BigInteger) result);
            } else {
                return result;
            }
        }

        @TruffleBoundary
        private Object objectOp(Object left, Object right) {
            BigInteger bigLeft = integerToBigInteger(left);
            BigInteger bigRight = integerToBigInteger(right);
            return op(bigLeft, bigRight);
        }

        @TruffleBoundary
        private Object objectOp(Object left, Object right, Object mod) {
            BigInteger bigLeft = integerToBigInteger(left);
            BigInteger bigRight = integerToBigInteger(right);
            BigInteger bigMod = integerToBigInteger(mod);
            if (bigMod.signum() == 0) {
                throw raise(ValueError, ErrorMessages.POW_THIRD_ARG_CANNOT_BE_ZERO);
            } else {
                BigInteger bigModPos;
                if (bigMod.signum() < 0) {
                    bigModPos = bigMod.abs();
                } else {
                    bigModPos = bigMod;
                }
                try {
                    BigInteger pow = bigLeft.modPow(bigRight, bigModPos);
                    if (bigModPos != bigMod && !BigInteger.ZERO.equals(pow)) {
                        return pow.subtract(bigModPos);
                    } else {
                        return pow;
                    }
                } catch (ArithmeticException e) {
                    // a positive mod was used, so this exception must mean the exponent was
                    // negative and the base is not relatively prime to the exponent
                    throw raise(ValueError, ErrorMessages.POW_BASE_NOT_INVERTIBLE);
                }
            }
        }

        private static BigInteger integerToBigInteger(Object value) {
            if (value instanceof Boolean) {
                return ((boolean) value) ? BigInteger.ONE : BigInteger.ZERO;
            } else if (value instanceof Integer) {
                return BigInteger.valueOf((Integer) value);
            } else if (value instanceof Long) {
                return BigInteger.valueOf((long) value);
            } else if (value instanceof PInt) {
                return ((PInt) value).getValue();
            } else {
                throw new IllegalStateException("never reached");
            }
        }

        @TruffleBoundary
        private static BigInteger op(long left, long right, long mod) {
            assert mod > 0;
            assert right >= 0;
            return BigInteger.valueOf(left).modPow(BigInteger.valueOf(right), BigInteger.valueOf(mod));
        }

        @TruffleBoundary
        private static BigInteger opNeg(long left, long right, long mod) {
            assert mod < 0;
            BigInteger pow;
            BigInteger modPos = BigInteger.valueOf(-mod);
            if (right == 0) {
                pow = BigInteger.ONE;
            } else {
                pow = BigInteger.valueOf(left).modPow(BigInteger.valueOf(right), modPos);
            }
            if (!BigInteger.ZERO.equals(pow)) {
                return pow.subtract(modPos);
            }
            return pow;
        }

        @TruffleBoundary
        private Object op(BigInteger left, BigInteger right) {
            if (right.signum() >= 0) {
                try {
                    return op(left, right.longValueExact());
                } catch (ArithmeticException e) {
                    // we'll raise unless left is one of the shortcut values
                    return op(left, Long.MAX_VALUE);
                }
            } else if (left.signum() == 0) {
                throw raise(PythonBuiltinClassType.ZeroDivisionError, ErrorMessages.POW_ZERO_CANNOT_RAISE_TO_NEGATIVE_POWER);
            } else {
                try {
                    return Math.pow(left.longValueExact(), right.longValueExact());
                } catch (ArithmeticException e) {
                    return Math.pow(left.doubleValue(), right.doubleValue());
                }
            }
        }

        @TruffleBoundary
        private BigInteger op(BigInteger a, long b) {
            assert b >= 0;
            try {
                // handle shortcut cases:
                int value = a.intValueExact();
                if (value == 0) {
                    if (b == 0) {
                        return BigInteger.ONE;
                    } else {
                        return BigInteger.ZERO;
                    }
                } else if (value == 1) {
                    return BigInteger.ONE;
                } else if (value == -1) {
                    return (b & 1) != 0 ? PInt.longToBigInteger(-1) : BigInteger.ONE;
                }
            } catch (ArithmeticException e) {
                // fall through to normal computation
            }
            if (b != (int) b) {
                // exponent does not fit in an int, this is likely going to cause out-of-memory
                throw raise(PythonErrorType.ArithmeticError, ErrorMessages.EXPONENT_TOO_LARGE);
            }
            return a.pow((int) b);
        }
    }

    @Builtin(name = SpecialMethodNames.__ABS__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class AbsNode extends PythonUnaryBuiltinNode {
        @Specialization
        boolean pos(boolean arg) {
            return arg;
        }

        @Specialization(rewriteOn = {ArithmeticException.class, OverflowException.class})
        int pos(int arg) throws OverflowException {
            int result = Math.abs(arg);
            if (result < 0) {
                throw OverflowException.INSTANCE;
            }
            return result;
        }

        @Specialization
        long posOvf(int arg) {
            return Math.abs((long) arg);
        }

        @Specialization(rewriteOn = {ArithmeticException.class, OverflowException.class})
        long pos(long arg) throws OverflowException {
            long result = Math.abs(arg);
            if (result < 0) {
                throw OverflowException.INSTANCE;
            }
            return result;
        }

        @Specialization(rewriteOn = IllegalArgumentException.class)
        PInt posOvf(long arg) throws IllegalArgumentException {
            long result = Math.abs(arg);
            if (result < 0) {
                return factory().createInt(op(PInt.longToBigInteger(arg)));
            } else {
                return factory().createInt(PInt.longToBigInteger(arg));
            }
        }

        @Specialization
        PInt pos(PInt arg) {
            return factory().createInt(op(arg.getValue()));
        }

        @TruffleBoundary
        static BigInteger op(BigInteger value) {
            return value.abs();
        }
    }

    @Builtin(name = SpecialMethodNames.__CEIL__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class CeilNode extends PythonUnaryBuiltinNode {
        @Specialization
        int ceil(int arg) {
            return arg;
        }

        @Specialization
        long ceil(long arg) {
            return arg;
        }

        @Specialization
        PInt ceil(PInt arg) {
            return arg;
        }
    }

    @Builtin(name = SpecialMethodNames.__FLOOR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class FloorNode extends PythonUnaryBuiltinNode {
        @Specialization
        int floor(int arg) {
            return arg;
        }

        @Specialization
        long floor(long arg) {
            return arg;
        }

        @Specialization
        PInt floor(PInt arg) {
            return factory().createInt(arg.getValue());
        }
    }

    @Builtin(name = SpecialMethodNames.__POS__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class PosNode extends PythonUnaryBuiltinNode {
        @Specialization
        int pos(int arg) {
            return arg;
        }

        @Specialization
        long pos(long arg) {
            return arg;
        }

        @Specialization
        PInt pos(PInt arg) {
            return factory().createInt(arg.getValue());
        }
    }

    @Builtin(name = SpecialMethodNames.__NEG__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class NegNode extends PythonUnaryBuiltinNode {
        @Specialization(rewriteOn = ArithmeticException.class)
        int neg(int arg) {
            return Math.negateExact(arg);
        }

        @Specialization
        long negOvf(int arg) {
            return -((long) arg);
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        long neg(long arg) {
            return Math.negateExact(arg);
        }

        @Specialization
        PInt negOvf(long arg) {
            BigInteger value = arg == Long.MIN_VALUE ? negate(PInt.longToBigInteger(arg)) : PInt.longToBigInteger(-arg);
            return factory().createInt(value);
        }

        @Specialization
        PInt doPInt(PInt operand) {
            return factory().createInt(negate(operand.getValue()));
        }

        @TruffleBoundary
        static BigInteger negate(BigInteger value) {
            return value.negate();
        }
    }

    @Builtin(name = SpecialMethodNames.__INVERT__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class InvertNode extends PythonUnaryBuiltinNode {
        @Specialization
        int neg(boolean arg) {
            return ~(arg ? 1 : 0);
        }

        @Specialization
        int neg(int arg) {
            return ~arg;
        }

        @Specialization
        long neg(long arg) {
            return ~arg;
        }

        @Specialization
        PInt doPInt(PInt operand) {
            return factory().createInt(not(operand.getValue()));
        }

        @TruffleBoundary
        static BigInteger not(BigInteger value) {
            return value.not();
        }
    }

    @Builtin(name = SpecialMethodNames.__LSHIFT__, minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class LShiftNode extends PythonBinaryBuiltinNode {

        private long leftShiftExact(long left, long right) throws OverflowException {
            if (right >= Long.SIZE || right < 0) {
                shiftError(right);
            }

            long result = left << right;

            if (left != result >> right) {
                throw OverflowException.INSTANCE;
            }

            return result;
        }

        private int leftShiftExact(int left, int right) throws OverflowException {
            if (right >= Integer.SIZE || right < 0) {
                shiftError(right);
            }

            int result = left << right;

            if (left != result >> right) {
                throw OverflowException.INSTANCE;
            }

            return result;
        }

        private void shiftError(long shiftCount) throws OverflowException {
            if (shiftCount >= Integer.SIZE) {
                throw OverflowException.INSTANCE;
            } else if (shiftCount < 0) {
                throw raise(ValueError, ErrorMessages.NEGATIVE_SHIFT_COUNT);
            }
        }

        @Specialization(rewriteOn = OverflowException.class)
        int doII(int left, int right) throws OverflowException {
            raiseNegativeShiftCount(right < 0);
            return leftShiftExact(left, right);
        }

        @Specialization
        Object doIIOvf(int left, int right) {
            raiseNegativeShiftCount(right < 0);
            try {
                return leftShiftExact(left, right);
            } catch (OverflowException e) {
                return doGuardedBiI(PInt.longToBigInteger(left), right);
            }
        }

        @Specialization(rewriteOn = OverflowException.class)
        long doLL(long left, long right) throws OverflowException {
            raiseNegativeShiftCount(right < 0);
            return leftShiftExact(left, right);
        }

        @Specialization
        Object doLLOvf(long left, long right) {
            raiseNegativeShiftCount(right < 0);
            try {
                return leftShiftExact(left, right);
            } catch (OverflowException e) {
                int rightI = (int) right;
                if (rightI == right) {
                    return factory().createInt(op(PInt.longToBigInteger(left), rightI));
                } else {
                    throw raise(PythonErrorType.OverflowError);
                }
            }
        }

        @Specialization(guards = {"left == 0", "right.isZeroOrPositive()"})
        static int doLPiZero(@SuppressWarnings("unused") long left, @SuppressWarnings("unused") PInt right) {
            return 0;
        }

        @Specialization(replaces = "doLPiZero")
        PInt doLPi(long left, PInt right) {
            raiseNegativeShiftCount(!right.isZeroOrPositive());
            if (left == 0) {
                return factory().createInt(BigInteger.ZERO);
            }
            try {
                int iright = right.intValueExact();
                return factory().createInt(op(PInt.longToBigInteger(left), iright));
            } catch (ArithmeticException e) {
                throw raise(PythonErrorType.OverflowError);
            }
        }

        @Specialization
        PInt doPiI(PInt left, int right) {
            raiseNegativeShiftCount(right < 0);
            return doGuardedBiI(left.getValue(), right);
        }

        protected PInt doGuardedBiI(BigInteger left, int right) {
            try {
                return factory().createInt(op(left, right));
            } catch (ArithmeticException e) {
                throw raise(PythonErrorType.OverflowError);
            }
        }

        @Specialization
        PInt doPiL(PInt left, long right) {
            int rightI = (int) right;
            if (rightI == right) {
                return doPiI(left, rightI);
            } else {
                throw raise(PythonErrorType.OverflowError);
            }
        }

        @Specialization(guards = {"left.isZero()", "right.isZeroOrPositive()"})
        static int doPiPiZero(@SuppressWarnings("unused") PInt left, @SuppressWarnings("unused") PInt right) {
            return 0;
        }

        @Specialization(replaces = "doPiPiZero")
        PInt doPiPi(PInt left, PInt right) {
            raiseNegativeShiftCount(!right.isZeroOrPositive());
            if (left.isZero()) {
                return factory().createInt(BigInteger.ZERO);
            }
            try {
                return factory().createInt(op(left.getValue(), right.intValueExact()));
            } catch (ArithmeticException e) {
                throw raise(PythonErrorType.OverflowError);
            }
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object a, Object b) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        @TruffleBoundary
        public static BigInteger op(BigInteger left, int right) {
            return left.shiftLeft(right);
        }

        private void raiseNegativeShiftCount(boolean cond) {
            if (cond) {
                throw raise(PythonErrorType.ValueError, ErrorMessages.NEGATIVE_SHIFT_COUNT);
            }
        }

    }

    @Builtin(name = SpecialMethodNames.__RSHIFT__, minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class RShiftNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "right < 32")
        int doIISmall(int left, int right) {
            raiseNegativeShiftCount(right < 0);
            return left >> right;
        }

        @Specialization(replaces = "doIISmall")
        int doII(int left, int right) {
            raiseNegativeShiftCount(right < 0);
            // Note: according to JLS, if 'left' is an int, then only the 5 LSBs of 'right' are
            // considered. However, Python would consider more bits, so do the max possible shift.
            return left >> (right >= 32 ? 31 : right);
        }

        @Specialization(guards = "right < 64")
        long doLLSmall(long left, long right) {
            raiseNegativeShiftCount(right < 0);
            return left >> right;
        }

        @Specialization(replaces = "doLLSmall")
        long doLL(long left, long right) {
            raiseNegativeShiftCount(right < 0);
            // for explanation, see 'doII'
            return left >> (right >= 64 ? 63 : right);
        }

        @Specialization
        Object doIPi(int left, PInt right) {
            return doHugeShift(PInt.longToBigInteger(left), right);
        }

        @Specialization
        Object doLPi(long left, PInt right) {
            return doHugeShift(PInt.longToBigInteger(left), right);
        }

        @Specialization
        PInt doPiI(PInt left, int right) {
            raiseNegativeShiftCount(right < 0);
            return factory().createInt(op(left.getValue(), right));
        }

        @Specialization
        Object doPiL(PInt left, long right) {
            raiseNegativeShiftCount(right < 0);
            int rightI = (int) right;
            if (rightI == right) {
                return factory().createInt(op(left.getValue(), rightI));
            }
            // right is >= 2**31, BigInteger's bitLength is at most 2**31-1
            // therefore the result of shifting right is just the sign bit
            return left.isNegative() ? -1 : 0;
        }

        @Specialization
        Object doPInt(PInt left, PInt right) {
            return doHugeShift(left.getValue(), right);
        }

        private void raiseNegativeShiftCount(boolean cond) {
            if (cond) {
                throw raise(PythonErrorType.ValueError, ErrorMessages.NEGATIVE_SHIFT_COUNT);
            }
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object a, Object b) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        private Object doHugeShift(BigInteger left, PInt right) {
            raiseNegativeShiftCount(!right.isZeroOrPositive());
            try {
                return factory().createInt(op(left, right.intValueExact()));
            } catch (ArithmeticException e) {
                // right is >= 2**31, BigInteger's bitLength is at most 2**31-1
                // therefore the result of shifting right is just the sign bit
                return left.signum() < 0 ? -1 : 0;
            }
        }

        @TruffleBoundary
        private static BigInteger op(BigInteger left, int right) {
            return left.shiftRight(right);
        }

    }

    abstract static class BinaryBitwiseNode extends PythonBinaryBuiltinNode {

        @SuppressWarnings("unused")
        protected int op(int left, int right) {
            throw new RuntimeException("should not reach here");
        }

        @SuppressWarnings("unused")
        protected long op(long left, long right) {
            throw new RuntimeException("should not reach here");
        }

        @SuppressWarnings("unused")
        protected BigInteger op(BigInteger left, BigInteger right) {
            throw new RuntimeException("should not reach here");
        }

        @Specialization
        int doInteger(int left, int right) {
            return op(left, right);
        }

        @Specialization
        long doInteger(long left, long right) {
            return op(left, right);
        }

        @Specialization
        PInt doPInt(long left, PInt right) {
            return factory().createInt(op(PInt.longToBigInteger(left), right.getValue()));
        }

        @Specialization
        PInt doPInt(PInt left, long right) {
            return factory().createInt(op(left.getValue(), PInt.longToBigInteger(right)));
        }

        @Specialization
        PInt doPInt(PInt left, PInt right) {
            return factory().createInt(op(left.getValue(), right.getValue()));
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object a, Object b) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = SpecialMethodNames.__RAND__, minNumOfPositionalArgs = 2)
    @Builtin(name = SpecialMethodNames.__AND__, minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class AndNode extends BinaryBitwiseNode {

        @Override
        protected int op(int left, int right) {
            return left & right;
        }

        @Override
        protected long op(long left, long right) {
            return left & right;
        }

        @Override
        @TruffleBoundary
        public BigInteger op(BigInteger left, BigInteger right) {
            return left.and(right);
        }
    }

    @Builtin(name = SpecialMethodNames.__ROR__, minNumOfPositionalArgs = 2)
    @Builtin(name = SpecialMethodNames.__OR__, minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class OrNode extends BinaryBitwiseNode {

        @Override
        protected int op(int left, int right) {
            return left | right;
        }

        @Override
        protected long op(long left, long right) {
            return left | right;
        }

        @Override
        @TruffleBoundary
        public BigInteger op(BigInteger left, BigInteger right) {
            return left.or(right);
        }
    }

    @Builtin(name = SpecialMethodNames.__RXOR__, minNumOfPositionalArgs = 2)
    @Builtin(name = SpecialMethodNames.__XOR__, minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class XorNode extends BinaryBitwiseNode {
        @Override
        protected int op(int left, int right) {
            return left ^ right;
        }

        @Override
        protected long op(long left, long right) {
            return left ^ right;
        }

        @Override
        @TruffleBoundary
        public BigInteger op(BigInteger left, BigInteger right) {
            return left.xor(right);
        }
    }

    @Builtin(name = SpecialMethodNames.__EQ__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class EqNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean eqLL(long a, long b) {
            return a == b;
        }

        @Specialization
        boolean eqPIntBoolean(PInt a, boolean b) {
            return b ? a.isOne() : a.isZero();
        }

        @Specialization
        boolean eqBooleanPInt(boolean a, PInt b) {
            return a ? b.isOne() : b.isZero();
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        boolean eqPiL(PInt a, long b) throws ArithmeticException {
            return a.longValueExact() == b;
        }

        @Specialization
        boolean eqPiLOvf(PInt a, long b) {
            try {
                return a.longValueExact() == b;
            } catch (ArithmeticException e) {
                return false;
            }
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        boolean eqLPi(long b, PInt a) throws ArithmeticException {
            return a.longValueExact() == b;
        }

        @Specialization
        boolean eqPiLOvf(long b, PInt a) {
            try {
                return a.longValueExact() == b;
            } catch (ArithmeticException e) {
                return false;
            }
        }

        @Specialization
        boolean eqPiPi(PInt a, PInt b) {
            return a.compareTo(b) == 0;
        }

        @Specialization
        boolean eqVoidPtrLong(PythonNativeVoidPtr a, long b,
                        @Shared("lib") @CachedLibrary(limit = "1") InteropLibrary lib) {
            if (lib.isPointer(a.object)) {
                try {
                    long ptrVal = lib.asPointer(a.object);
                    // pointers are considered unsigned
                    return ptrVal >= 0L && ptrVal == b;
                } catch (UnsupportedMessageException e) {
                    // fall through
                }
            }
            return doHash(a.object, b);
        }

        @Specialization
        boolean eqLongVoidPtr(long a, PythonNativeVoidPtr b,
                        @Shared("lib") @CachedLibrary(limit = "1") InteropLibrary lib) {
            return eqVoidPtrLong(b, a, lib);
        }

        @Specialization
        @TruffleBoundary
        boolean eqVoidPtrPInt(PythonNativeVoidPtr a, PInt b,
                        @Shared("lib") @CachedLibrary(limit = "1") InteropLibrary lib) {
            if (lib.isPointer(a.object)) {
                try {
                    long ptrVal = lib.asPointer(a.object);
                    if (ptrVal < 0) {
                        // pointers are considered unsigned
                        BigInteger bi = PInt.longToBigInteger(ptrVal).add(BigInteger.ONE.shiftLeft(64));
                        return bi.equals(b.getValue());
                    }
                    return PInt.longToBigInteger(ptrVal).equals(b.getValue());
                } catch (UnsupportedMessageException e) {
                    // fall through
                }
            }
            try {
                return a.object.hashCode() == b.longValueExact();
            } catch (ArithmeticException e) {
                return false;
            }
        }

        @Specialization
        boolean eqPIntVoidPtr(PInt a, PythonNativeVoidPtr b,
                        @Shared("lib") @CachedLibrary(limit = "1") InteropLibrary lib) {
            return eqVoidPtrPInt(b, a, lib);
        }

        @TruffleBoundary
        private static boolean doHash(Object object, long b) {
            return object.hashCode() == b;
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented eq(Object a, Object b) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = SpecialMethodNames.__NE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class NeNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean eqLL(long a, long b) {
            return a != b;
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        boolean eqPiL(PInt a, long b) {
            return a.longValueExact() != b;
        }

        @Specialization
        boolean eqPiLOvf(PInt a, long b) {
            try {
                return a.longValueExact() != b;
            } catch (ArithmeticException e) {
                return true;
            }
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        boolean eqLPi(long b, PInt a) {
            return a.longValueExact() != b;
        }

        @Specialization
        boolean eqLPiOvf(long b, PInt a) {
            try {
                return a.longValueExact() != b;
            } catch (ArithmeticException e) {
                return true;
            }
        }

        @Specialization
        boolean eqPiPi(PInt a, PInt b) {
            return a.compareTo(b) != 0;
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented eq(Object a, Object b) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __LT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class LtNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean doII(int left, int right) {
            return left < right;
        }

        @Specialization
        boolean doLL(long left, long right) {
            return left < right;
        }

        @Specialization
        boolean doLP(long left, PInt right) {
            try {
                return left < right.longValueExact();
            } catch (ArithmeticException e) {
                return right.doubleValue() > 0;
            }
        }

        @Specialization
        boolean doPL(PInt left, long right) {
            try {
                return left.longValueExact() < right;
            } catch (ArithmeticException e) {
                return left.doubleValue() < 0;
            }
        }

        @Specialization
        boolean doPP(PInt left, PInt right) {
            return left.compareTo(right) < 0;
        }

        @Specialization(guards = "fromNativeNode.isFloatSubtype(frame, y, getClass, isSubtype, context)", limit = "1")
        boolean doDN(VirtualFrame frame, long x, PythonNativeObject y,
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

        @Specialization
        boolean doVoidPtr(PythonNativeVoidPtr x, long y,
                        @Cached CExtNodes.PointerCompareNode ltNode) {
            return ltNode.execute(__LT__, x, y);
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object a, Object b) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = SpecialMethodNames.__LE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class LeNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean doII(int left, int right) {
            return left <= right;
        }

        @Specialization
        boolean doLL(long left, long right) {
            return left <= right;
        }

        @Specialization
        boolean doLP(long left, PInt right) {
            try {
                return left <= right.longValueExact();
            } catch (ArithmeticException e) {
                return right.doubleValue() > 0;
            }
        }

        @Specialization
        boolean doPL(PInt left, long right) {
            try {
                return left.longValueExact() <= right;
            } catch (ArithmeticException e) {
                return left.doubleValue() < 0;
            }
        }

        @Specialization
        boolean doPP(PInt left, PInt right) {
            return left.compareTo(right) <= 0;
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object a, Object b) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = SpecialMethodNames.__GT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class GtNode extends PythonBinaryBuiltinNode {

        @Specialization
        boolean doII(int left, int right) {
            return left > right;
        }

        @Specialization
        boolean doLL(long left, long right) {
            return left > right;
        }

        @Specialization
        boolean doLP(long left, PInt right) {
            try {
                return left > right.longValueExact();
            } catch (ArithmeticException e) {
                return right.doubleValue() < 0;
            }
        }

        @Specialization
        boolean doPL(PInt left, long right) {
            try {
                return left.longValueExact() > right;
            } catch (ArithmeticException e) {
                return left.doubleValue() > 0;
            }
        }

        @Specialization
        boolean doPP(PInt left, PInt right) {
            return left.compareTo(right) > 0;
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object a, Object b) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = SpecialMethodNames.__GE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class GeNode extends PythonBinaryBuiltinNode {

        @Specialization
        boolean doII(int left, int right) {
            return left >= right;
        }

        @Specialization
        boolean doLL(long left, long right) {
            return left >= right;
        }

        @Specialization
        boolean doLP(long left, PInt right) {
            try {
                return left >= right.longValueExact();
            } catch (ArithmeticException e) {
                return right.doubleValue() < 0;
            }
        }

        @Specialization
        boolean doPL(PInt left, long right) {
            try {
                return left.longValueExact() >= right;
            } catch (ArithmeticException e) {
                return left.doubleValue() > 0;
            }
        }

        @Specialization
        boolean doPP(PInt left, PInt right) {
            return left.compareTo(right) >= 0;
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object a, Object b) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    // to_bytes
    @Builtin(name = "to_bytes", minNumOfPositionalArgs = 3, parameterNames = {"self", "bytecount", "byteorder", "signed"})
    @GenerateNodeFactory
    @SuppressWarnings("unused")
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class ToBytesNode extends PythonBuiltinNode {

        private static final String MESSAGE_INT_TO_BIG = "int too big to convert";
        private static final String MESSAGE_LENGTH_ARGUMENT = "length argument must be non-negative";
        private static final String MESSAGE_CONVERT_NEGATIVE = "can't convert negative int to unsigned";

        public abstract PBytes execute(VirtualFrame frame, Object self, Object byteCount, Object StringOrder, Object signed);

        // used for obtaining int, which will be the size of craeted array
        @Child private ToBytesNode recursiveNode;

        @TruffleBoundary
        private boolean isBigEndian(String order) {
            if (order.equals("big")) {
                return true;
            }
            if (order.equals("little")) {
                return false;
            }
            throw raise(PythonErrorType.ValueError, ErrorMessages.BYTEORDER_MUST_BE_LITTLE_OR_BIG);
        }

        @Specialization
        public PBytes fromLong(long self, int byteCount, String byteorder, PNone signed) {
            return fromLong(self, byteCount, byteorder, false);
        }

        private final ConditionProfile negativeByteCountProfile = ConditionProfile.createBinaryProfile();
        private final ConditionProfile negativeNumberProfile = ConditionProfile.createBinaryProfile();
        private final ConditionProfile overflowProfile = ConditionProfile.createBinaryProfile();

        @Specialization
        public PBytes fromLong(long self, int byteCount, String byteorder, boolean signed) {
            if (negativeByteCountProfile.profile(byteCount < 0)) {
                throw raise(PythonErrorType.ValueError, MESSAGE_LENGTH_ARGUMENT);
            }
            byte signByte = 0;
            if (self < 0) {
                if (negativeNumberProfile.profile(!signed)) {
                    throw raise(PythonErrorType.OverflowError, MESSAGE_CONVERT_NEGATIVE);
                }
                signByte = -1;
            }
            int index;
            int delta;
            if (isBigEndian(byteorder)) {
                index = byteCount - 1;
                delta = -1;
            } else {
                index = 0;
                delta = 1;
            }

            byte[] bytes = new byte[byteCount];
            long number = self;

            while (number != 0 && 0 <= index && index <= (byteCount - 1)) {
                bytes[index] = (byte) (number & 0xFF);
                if (number == signByte) {
                    number = 0;
                }
                number >>= 8;
                index += delta;
            }
            if (overflowProfile.profile((number != 0 && bytes.length == 1 && bytes[0] != self) || (signed && bytes.length == 1 && bytes[0] != self) || (byteCount == 0 && self != 0))) {

                throw raise(PythonErrorType.OverflowError, MESSAGE_INT_TO_BIG);
            }
            if (signed) {
                while (0 <= index && index <= (byteCount - 1)) {
                    bytes[index] = signByte;
                    index += delta;
                }
            }
            return factory().createBytes(bytes);
        }

        @Specialization
        public PBytes fromLongLong(VirtualFrame frame, long self, long byteCount, String byteorder, PNone signed,
                        @Shared("castLib") @CachedLibrary(limit = "3") PythonObjectLibrary lib) {
            return fromLongLong(frame, self, byteCount, byteorder, false, lib);
        }

        @Specialization
        public PBytes fromLongLong(VirtualFrame frame, long self, long byteCount, String byteorder, boolean signed,
                        @Shared("castLib") @CachedLibrary(limit = "3") PythonObjectLibrary lib) {
            int count = lib.asSizeWithState(byteCount, PArguments.getThreadState(frame));
            return fromLong(self, count, byteorder, signed);
        }

        @Specialization
        public PBytes fromLongPInt(VirtualFrame frame, long self, PInt byteCount, String byteorder, PNone signed,
                        @Shared("castLib") @CachedLibrary(limit = "3") PythonObjectLibrary lib) {
            return fromLongPInt(frame, self, byteCount, byteorder, false, lib);
        }

        @Specialization
        public PBytes fromLongPInt(VirtualFrame frame, long self, PInt byteCount, String byteorder, boolean signed,
                        @Shared("castLib") @CachedLibrary(limit = "3") PythonObjectLibrary lib) {
            int count = lib.asSizeWithState(byteCount, PArguments.getThreadState(frame));
            return fromLong(self, count, byteorder, signed);
        }

        @Specialization
        public PBytes fromPIntInt(PInt self, int byteCount, String byteorder, PNone signed) {
            return fromPIntInt(self, byteCount, byteorder, false);
        }

        @TruffleBoundary
        private byte getSingByte(BigInteger value, boolean signed) {
            if (value.compareTo(BigInteger.ZERO) < 0) {
                if (!signed) {
                    throw raise(PythonErrorType.OverflowError, MESSAGE_CONVERT_NEGATIVE);
                }
                return -1;
            }
            return 0;
        }

        @TruffleBoundary
        private static byte[] getBytes(BigInteger value) {
            return value.toByteArray();
        }

        @Specialization
        public PBytes fromPIntInt(PInt self, int byteCount, String byteorder, boolean signed) {
            if (negativeByteCountProfile.profile(byteCount < 0)) {
                throw raise(PythonErrorType.ValueError, MESSAGE_LENGTH_ARGUMENT);
            }
            BigInteger value = self.getValue();
            byte signByte = getSingByte(value, signed);
            byte[] bytes = getBytes(value);
            if (bytes.length > byteCount) {
                // Check, whether we need to cut unneeded sign bytes.
                int len = bytes.length;
                int startIndex = 0;
                if (!signed) {
                    for (startIndex = 0; startIndex < bytes.length; startIndex++) {
                        if (bytes[startIndex] != 0) {
                            break;
                        }
                    }
                    len = Math.max(bytes.length - startIndex, byteCount);
                }
                if (overflowProfile.profile(len > byteCount)) {
                    // the corrected len is still bigger then we need.
                    throw raise(PythonErrorType.OverflowError, MESSAGE_INT_TO_BIG);
                }
                if (bytes.length > byteCount) {
                    // the array starts with sign bytes and has to be truncated to the requested
                    // size
                    byte[] tmp = bytes;
                    bytes = new byte[len];
                    PythonUtils.arraycopy(tmp, startIndex, bytes, 0, len);
                }
            }

            if (isBigEndian(byteorder)) {
                if (byteCount > bytes.length) {
                    // requested array is bigger then we obtained from BigInteger
                    byte[] resultBytes = new byte[byteCount];
                    PythonUtils.arraycopy(bytes, 0, resultBytes, resultBytes.length - bytes.length, bytes.length);
                    if (signByte == -1) {
                        // add sign bytes
                        for (int i = 0; i < resultBytes.length - bytes.length; i++) {
                            resultBytes[i] = signByte;
                        }
                    }
                    return factory().createBytes(resultBytes);
                } else {
                    return factory().createBytes(bytes);
                }
            } else {
                // little endian -> need to switch bytes
                byte[] resultBytes = new byte[byteCount];
                for (int i = 0; i < bytes.length; i++) {
                    resultBytes[i] = bytes[bytes.length - 1 - i];
                }
                if (byteCount > bytes.length && signByte == -1) {
                    // add sign negative bytes
                    for (int i = bytes.length; i < resultBytes.length; i++) {
                        resultBytes[i] = signByte;
                    }
                }
                return factory().createBytes(resultBytes);
            }
        }

        @Specialization
        public PBytes fromPIntLong(VirtualFrame frame, PInt self, long byteCount, String byteorder, PNone signed,
                        @Shared("castLib") @CachedLibrary(limit = "3") PythonObjectLibrary lib) {
            return fromPIntLong(frame, self, byteCount, byteorder, false, lib);
        }

        @Specialization
        public PBytes fromPIntLong(VirtualFrame frame, PInt self, long byteCount, String byteorder, boolean signed,
                        @Shared("castLib") @CachedLibrary(limit = "3") PythonObjectLibrary lib) {
            int count = lib.asSizeWithState(byteCount, PArguments.getThreadState(frame));
            return fromPIntInt(self, count, byteorder, signed);
        }

        @Specialization
        public PBytes fromPIntPInt(VirtualFrame frame, PInt self, PInt byteCount, String byteorder, PNone signed,
                        @Shared("castLib") @CachedLibrary(limit = "3") PythonObjectLibrary lib) {
            return fromPIntPInt(frame, self, byteCount, byteorder, false, lib);
        }

        @Specialization
        public PBytes fromPIntPInt(VirtualFrame frame, PInt self, PInt byteCount, String byteorder, boolean signed,
                        @Shared("castLib") @CachedLibrary(limit = "3") PythonObjectLibrary lib) {
            int count = lib.asSizeWithState(byteCount, PArguments.getThreadState(frame));
            return fromPIntInt(self, count, byteorder, signed);
        }

        public static boolean isNumber(Object value) {
            return value instanceof Integer || value instanceof Long || value instanceof PInt;
        }

        @Fallback
        PBytes general(VirtualFrame frame, Object self, Object byteCount, Object byteorder, Object oSigned) {
            int count = PythonObjectLibrary.getUncached().asSizeWithState(byteCount, PArguments.getThreadState(frame));
            if (!PGuards.isString(byteorder)) {
                throw raise(PythonErrorType.TypeError, ErrorMessages.ARG_D_MUST_BE_S_NOT_P, "to_bytes()", 2, "str", byteorder);
            }
            boolean signed = oSigned instanceof Boolean ? (boolean) oSigned : false;
            if (recursiveNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                recursiveNode = insert(create());
            }
            return recursiveNode.execute(frame, self, count, byteorder, signed);
        }

        protected static ToBytesNode create() {
            return IntBuiltinsFactory.ToBytesNodeFactory.create(null);
        }
    }

    @Builtin(name = "from_bytes", minNumOfPositionalArgs = 3, parameterNames = {"cls", "bytes", "byteorder"}, varArgsMarker = true, keywordOnlyNames = {"signed"}, isClassmethod = true)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class FromBytesNode extends PythonBuiltinNode {

        @Child private LookupAndCallVarargsNode constructNode;
        @Child private BytesNodes.FromSequenceNode fromSequenceNode;
        @Child private BytesNodes.FromIteratorNode fromIteratorNode;
        @Child private GetIteratorNode getIteratorNode;

        @Child private BytesNodes.ToBytesNode toBytesNode;
        @Child private LookupAndCallUnaryNode callBytesNode;

        protected BytesNodes.FromSequenceNode getFromSequenceNode() {
            if (fromSequenceNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                fromSequenceNode = insert(BytesNodes.FromSequenceNode.create());
            }
            return fromSequenceNode;
        }

        protected BytesNodes.FromIteratorNode getFromIteratorNode() {
            if (fromIteratorNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                fromIteratorNode = insert(BytesNodes.FromIteratorNode.create());
            }
            return fromIteratorNode;
        }

        protected GetIteratorNode getGetIteratorNode() {
            if (getIteratorNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getIteratorNode = insert(GetIteratorNode.create());
            }
            return getIteratorNode;
        }

        protected BytesNodes.ToBytesNode getToBytesNode() {
            if (toBytesNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toBytesNode = insert(BytesNodes.ToBytesNode.create());
            }
            return toBytesNode;
        }

        private static byte[] littleToBig(byte[] bytes) {
            // PInt uses Java BigInteger which are big-endian
            byte[] bigEndianBytes = new byte[bytes.length];
            for (int i = 0; i < bytes.length; i++) {
                bigEndianBytes[bytes.length - i - 1] = bytes[i];
            }
            return bigEndianBytes;
        }

        @TruffleBoundary
        private static BigInteger createBigInteger(byte[] bytes, boolean isBigEndian, boolean signed) {
            if (bytes.length == 0) {
                // in case of empty byte array
                return BigInteger.ZERO;
            }
            BigInteger result;
            if (isBigEndian) { // big byteorder
                result = signed ? new BigInteger(bytes) : new BigInteger(1, bytes);
            } else { // little byteorder
                byte[] converted = littleToBig(bytes);
                result = signed ? new BigInteger(converted) : new BigInteger(1, converted);
            }
            return result;
        }

        @TruffleBoundary
        private boolean isBigEndian(String order) {
            if (order.equals("big")) {
                return true;
            }
            if (order.equals("little")) {
                return false;
            }
            throw raise(PythonErrorType.ValueError, ErrorMessages.BYTEORDER_MUST_BE_LITTLE_OR_BIG);
        }

        private Object createIntObject(Object cl, BigInteger number) {
            PythonBuiltinClassType type = null;
            if (cl instanceof PythonBuiltinClass) {
                type = ((PythonBuiltinClass) cl).getType();
            } else if (cl instanceof PythonBuiltinClassType) {
                type = (PythonBuiltinClassType) cl;
            }
            if (type == PythonBuiltinClassType.PInt) {
                return factory().createInt(number);
            }
            if (constructNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                constructNode = insert(LookupAndCallVarargsNode.create(SpecialMethodNames.__CALL__));
            }
            return constructNode.execute(null, cl, new Object[]{cl, factory().createInt(number)});
        }

        private Object compute(Object cl, byte[] bytes, String byteorder, boolean signed) {
            BigInteger bi = createBigInteger(bytes, isBigEndian(byteorder), signed);
            return createIntObject(cl, bi);
        }

        // from PBytes
        @Specialization
        public Object fromPBytes(VirtualFrame frame, Object cl, PBytes bytes, String byteorder, boolean signed) {
            return compute(cl, getToBytesNode().execute(frame, bytes), byteorder, signed);
        }

        @Specialization
        public Object fromPBytes(VirtualFrame frame, Object cl, PBytes bytes, String byteorder, @SuppressWarnings("unused") PNone signed) {
            return fromPBytes(frame, cl, bytes, byteorder, false);
        }

        // from PByteArray
        @Specialization
        public Object fromPByteArray(VirtualFrame frame, Object cl, PByteArray bytes, String byteorder, boolean signed) {
            return compute(cl, getToBytesNode().execute(frame, bytes), byteorder, signed);
        }

        @Specialization
        public Object fromPByteArray(VirtualFrame frame, Object cl, PByteArray bytes, String byteorder, @SuppressWarnings("unused") PNone signed) {
            return fromPByteArray(frame, cl, bytes, byteorder, false);
        }

        // from PArray
        @Specialization
        public Object fromPArray(VirtualFrame frame, Object cl, PArray array, String byteorder, boolean signed,
                        @Cached("create()") BytesNodes.FromSequenceStorageNode fromSequenceStorageNode) {
            return compute(cl, fromSequenceStorageNode.execute(frame, array.getSequenceStorage()), byteorder, signed);
        }

        @Specialization
        public Object fromPArray(VirtualFrame frame, Object cl, PArray array, String byteorder, @SuppressWarnings("unused") PNone signed,
                        @Cached("create()") BytesNodes.FromSequenceStorageNode fromSequenceStorageNode) {
            return fromPArray(frame, cl, array, byteorder, false, fromSequenceStorageNode);
        }

        // from PMemoryView
        @Specialization
        public Object fromPMemoryView(VirtualFrame frame, Object cl, PMemoryView view, String byteorder, boolean signed) {
            return compute(cl, getToBytesNode().execute(frame, view), byteorder, signed);
        }

        @Specialization
        public Object fromPMemoryView(VirtualFrame frame, Object cl, PMemoryView view, String byteorder, @SuppressWarnings("unused") PNone signed) {
            return fromPMemoryView(frame, cl, view, byteorder, false);
        }

        // from PList, only if it is not extended
        @Specialization(guards = "cannotBeOverridden(lib.getLazyPythonClass(list))", limit = "3")
        public Object fromPList(VirtualFrame frame, Object cl, PList list, String byteorder, boolean signed,
                        @SuppressWarnings("unused") @CachedLibrary("list") PythonObjectLibrary lib) {
            return compute(cl, getFromSequenceNode().execute(frame, list), byteorder, signed);
        }

        @Specialization(guards = "cannotBeOverridden(lib.getLazyPythonClass(list))", limit = "3")
        public Object fromPList(VirtualFrame frame, Object cl, PList list, String byteorder, @SuppressWarnings("unused") PNone signed,
                        @SuppressWarnings("unused") @CachedLibrary("list") PythonObjectLibrary lib) {
            return fromPList(frame, cl, list, byteorder, false, lib);
        }

        // from PTuple, only if it is not extended
        @Specialization(guards = "cannotBeOverridden(lib.getLazyPythonClass(tuple))", limit = "3")
        public Object fromPTuple(VirtualFrame frame, Object cl, PTuple tuple, String byteorder, boolean signed,
                        @SuppressWarnings("unused") @CachedLibrary("tuple") PythonObjectLibrary lib) {
            return compute(cl, getFromSequenceNode().execute(frame, tuple), byteorder, signed);
        }

        @Specialization(guards = "cannotBeOverridden(lib.getLazyPythonClass(tuple))", limit = "3")
        public Object fromPTuple(VirtualFrame frame, Object cl, PTuple tuple, String byteorder, @SuppressWarnings("unused") PNone signed,
                        @SuppressWarnings("unused") @CachedLibrary("tuple") PythonObjectLibrary lib) {
            return fromPTuple(frame, cl, tuple, byteorder, false, lib);
        }

        // rest objects
        @Specialization
        public Object fromObject(VirtualFrame frame, Object cl, PythonObject object, String byteorder, @SuppressWarnings("unused") PNone signed,
                        @Shared("ctxRef") @CachedContext(PythonLanguage.class) ContextReference<PythonContext> ctxRef,
                        @CachedLibrary(limit = "1") PythonObjectLibrary dataModelLibrary) {
            return fromObject(frame, cl, object, byteorder, false, ctxRef, dataModelLibrary);
        }

        @Specialization
        public Object fromObject(VirtualFrame frame, Object cl, PythonObject object, String byteorder, boolean signed,
                        @Shared("ctxRef") @CachedContext(PythonLanguage.class) ContextReference<PythonContext> ctxRef,
                        @CachedLibrary(limit = "1") PythonObjectLibrary dataModelLibrary) {
            if (callBytesNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callBytesNode = insert(LookupAndCallUnaryNode.create(SpecialMethodNames.__BYTES__));
            }
            Object result = callBytesNode.executeObject(frame, object);
            if (result != PNone.NO_VALUE) { // first try o use __bytes__ call result
                if (!(result instanceof PIBytesLike)) {
                    raise(PythonErrorType.TypeError, ErrorMessages.RETURNED_NONBYTES, "__bytes__", result);
                }
                BigInteger bi = createBigInteger(getToBytesNode().execute(frame, result), isBigEndian(byteorder), false);
                return createIntObject(cl, bi);
            }
            if (PythonObjectLibrary.checkIsIterable(dataModelLibrary, ctxRef, frame, object, this)) {
                byte[] bytes = getFromIteratorNode().execute(frame, getGetIteratorNode().executeWith(frame, object));
                return compute(cl, bytes, byteorder, signed);
            }
            return general(cl, object, byteorder, signed);
        }

        @Fallback
        public Object general(@SuppressWarnings("unused") Object cl, Object object, @SuppressWarnings("unused") Object byteorder, @SuppressWarnings("unused") Object signed) {
            throw raise(PythonErrorType.TypeError, ErrorMessages.CANNOT_CONVERT_S_OBJ_TO_BYTES, object);
        }
    }

    @Builtin(name = SpecialMethodNames.__BOOL__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class BoolNode extends PythonBuiltinNode {
        @Specialization
        public boolean toBoolean(boolean self) {
            return self;
        }

        @Specialization
        public boolean toBoolean(int self) {
            return self != 0;
        }

        @Specialization
        public boolean toBoolean(long self) {
            return self != 0;
        }

        @Specialization
        public boolean toBoolean(PInt self) {
            return !self.isZero();
        }
    }

    @Builtin(name = SpecialMethodNames.__STR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class StrNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        public String doI(int self) {
            return Integer.toString(self);
        }

        @Specialization
        @TruffleBoundary
        public String doL(long self) {
            return Long.toString(self);
        }

        @Specialization
        @TruffleBoundary
        public String doPInt(PInt self) {
            return self.toString();
        }

        @Specialization
        public String doNativeVoidPtr(PythonNativeVoidPtr self,
                        @CachedLibrary(limit = "1") InteropLibrary lib) {
            if (lib.isPointer(self.object)) {
                try {
                    return Long.toString(lib.asPointer(self.object));
                } catch (UnsupportedMessageException e) {
                    // fall through
                }
            }
            return doHash(self.object);
        }

        @TruffleBoundary
        private static String doHash(Object object) {
            return Integer.toString(object.hashCode());
        }
    }

    @Builtin(name = SpecialMethodNames.__REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReprNode extends StrNode {
    }

    @Builtin(name = __FORMAT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class FormatNode extends FormatNodeBase {
        @Child private BuiltinConstructors.FloatNode floatNode;

        // We cannot use PythonArithmeticTypes, because for empty format string we need to call the
        // boolean's __str__ and not int's __str__
        @Specialization
        Object formatB(VirtualFrame frame, boolean self, Object formatStringObj,
                        @Shared("cast") @Cached CastToJavaStringNode castToStringNode) {
            String formatString = castFormatString(formatStringObj, castToStringNode);
            if (formatString.isEmpty()) {
                return ensureStrCallNode().executeObject(frame, self);
            }
            return doFormatInt(self ? 1 : 0, formatString);
        }

        @Specialization
        Object formatI(VirtualFrame frame, int self, Object formatStringObj,
                        @Shared("cast") @Cached CastToJavaStringNode castToStringNode) {
            String formatString = castFormatString(formatStringObj, castToStringNode);
            if (formatString.isEmpty()) {
                return ensureStrCallNode().executeObject(frame, self);
            }
            return doFormatInt(self, formatString);
        }

        private String doFormatInt(int self, String formatString) {
            PythonCore core = getCore();
            Spec spec = getSpec(formatString, core);
            if (isDoubleSpec(spec)) {
                return formatDouble(core, spec, self);
            }
            validateIntegerSpec(core, spec);
            return formatInt(self, core, spec);
        }

        @Specialization
        Object formatL(VirtualFrame frame, long self, Object formatString,
                        @Shared("cast") @Cached CastToJavaStringNode castToStringNode) {
            return formatPI(frame, factory().createInt(self), formatString, castToStringNode);
        }

        @Specialization
        Object formatPI(VirtualFrame frame, PInt self, Object formatStringObj,
                        @Shared("cast") @Cached CastToJavaStringNode castToStringNode) {
            String formatString = castFormatString(formatStringObj, castToStringNode);
            if (formatString.isEmpty()) {
                return ensureStrCallNode().executeObject(frame, self);
            }
            PythonCore core = getCore();
            Spec spec = getSpec(formatString, core);
            if (isDoubleSpec(spec)) {
                return formatDouble(core, spec, asDouble(frame, self));
            }
            validateIntegerSpec(core, spec);
            return formatPInt(self, core, spec);
        }

        private double asDouble(VirtualFrame frame, Object self) {
            if (floatNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                floatNode = insert(BuiltinConstructorsFactory.FloatNodeFactory.create());
            }
            // We cannot use asJavaDouble, because this should have the semantics of PyNumber_Float
            return (double) floatNode.executeWith(frame, PythonBuiltinClassType.PFloat, self);
        }

        private static Spec getSpec(String formatString, PythonCore core) {
            Spec spec = InternalFormat.fromText(core, formatString, __FORMAT__);
            return spec.withDefaults(Spec.NUMERIC);
        }

        private static boolean isDoubleSpec(Spec spec) {
            return spec.type == 'e' || spec.type == 'E' || spec.type == 'f' || //
                            spec.type == 'F' || spec.type == 'g' || //
                            spec.type == 'G' || spec.type == '%';
        }

        @TruffleBoundary
        private static String formatDouble(PythonCore core, Spec spec, double value) {
            FloatFormatter formatter = new FloatFormatter(core, spec);
            formatter.format(value);
            return formatter.pad().getResult();
        }

        @TruffleBoundary
        private static String formatInt(int self, PythonCore core, Spec spec) {
            IntegerFormatter formatter = new IntegerFormatter(core, spec);
            formatter.format(self);
            return formatter.pad().getResult();
        }

        @TruffleBoundary
        private static String formatPInt(PInt self, PythonCore core, Spec spec) {
            IntegerFormatter formatter = new IntegerFormatter(core, spec);
            formatter.format(self.getValue());
            return formatter.pad().getResult();
        }

        private static void validateIntegerSpec(PythonCore core, Spec spec) {
            if (Spec.specified(spec.precision)) {
                throw core.raise(ValueError, ErrorMessages.PRECISION_NOT_ALLOWED_FOR_INT);
            }
            if (spec.type == 'c') {
                if (Spec.specified(spec.sign)) {
                    throw core.raise(ValueError, ErrorMessages.SIGN_NOT_ALLOWED_WITH_C_FOR_INT);
                } else if (spec.alternate) {
                    throw core.raise(ValueError, ErrorMessages.ALTERNATE_NOT_ALLOWED_WITH_C_FOR_INT);
                }
            }
        }
    }

    @Builtin(name = SpecialMethodNames.__HASH__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class HashNode extends PythonUnaryBuiltinNode {

        @Specialization
        int hash(int self) {
            return self;
        }

        @Specialization
        long hash(long self) {
            return self;
        }

        @Specialization
        long hash(PInt self) {
            return self.longValue();
        }

        @Specialization
        @TruffleBoundary
        long hash(PythonNativeVoidPtr self) {
            return self.object.hashCode();
        }
    }

    @Builtin(name = "bit_length", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class BitLengthNode extends PythonBuiltinNode {
        @Specialization
        int bitLength(int argument) {
            return Integer.SIZE - Integer.numberOfLeadingZeros(Math.abs(argument));
        }

        @Specialization
        int bitLength(long argument) {
            return Long.SIZE - Long.numberOfLeadingZeros(Math.abs(argument));
        }

        @Specialization
        @TruffleBoundary
        int bitLength(PInt argument) {
            return argument.getValue().abs().bitLength();
        }
    }

    @GenerateNodeFactory
    @Builtin(name = "real", minNumOfPositionalArgs = 1, isGetter = true, doc = "the real part of a complex number")
    abstract static class RealNode extends IntNode {

    }

    @GenerateNodeFactory
    @Builtin(name = "imag", minNumOfPositionalArgs = 1, isGetter = true, doc = "the imaginary part of a complex number")
    abstract static class ImagNode extends PythonBuiltinNode {
        @Specialization
        int get(@SuppressWarnings("unused") Object self) {
            return 0;
        }
    }

    @GenerateNodeFactory
    @Builtin(name = "numerator", minNumOfPositionalArgs = 1, isGetter = true, doc = "the numerator of a rational number in lowest terms")
    abstract static class NumeratorNode extends IntNode {

    }

    @GenerateNodeFactory
    @Builtin(name = "conjugate", minNumOfPositionalArgs = 1, doc = "Returns self, the complex conjugate of any int.")
    abstract static class ConjugateNode extends IntNode {

    }

    @GenerateNodeFactory
    @Builtin(name = "denominator", minNumOfPositionalArgs = 1, isGetter = true, doc = "the denominator of a rational number in lowest terms")
    abstract static class DenominatorNode extends PythonBuiltinNode {
        @Specialization
        int get(@SuppressWarnings("unused") Object self) {
            return 1;
        }
    }

    @GenerateNodeFactory
    @Builtin(name = "as_integer_ratio", minNumOfPositionalArgs = 1, doc = "Return integer ratio.")
    abstract static class AsIntegerRatioNode extends PythonBuiltinNode {
        @Specialization
        Object get(VirtualFrame frame, Object self, @Cached IntNode intNode) {
            return factory().createTuple(new Object[]{intNode.execute(frame, self), 1});
        }
    }

    @GenerateNodeFactory
    @Builtin(name = SpecialMethodNames.__TRUNC__, minNumOfPositionalArgs = 1, doc = "Truncating an Integral returns itself.")
    abstract static class TruncNode extends IntNode {

    }

    @Builtin(name = SpecialMethodNames.__INT__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class IntNode extends PythonUnaryBuiltinNode {

        @Specialization
        int doB(boolean self) {
            return self ? 1 : 0;
        }

        @Specialization
        int doI(int self) {
            return self;
        }

        @Specialization
        long doL(long self) {
            return self;
        }

        @Specialization(guards = "cannotBeOverridden(lib.getLazyPythonClass(self))", limit = "3")
        PInt doPInt(PInt self,
                        @SuppressWarnings("unused") @CachedLibrary("self") PythonObjectLibrary lib) {
            return self;
        }

        @Specialization(guards = "!cannotBeOverridden(lib.getLazyPythonClass(self))", rewriteOn = ArithmeticException.class, limit = "3")
        int doPIntOverridenNarrowInt(PInt self,
                        @SuppressWarnings("unused") @CachedLibrary("self") PythonObjectLibrary lib) {
            return self.intValueExact();
        }

        @Specialization(guards = "!cannotBeOverridden(lib.getLazyPythonClass(self))", replaces = "doPIntOverridenNarrowInt", rewriteOn = ArithmeticException.class, limit = "3")
        long doPIntOverridenNarrowLong(PInt self,
                        @SuppressWarnings("unused") @CachedLibrary("self") PythonObjectLibrary lib) {
            return self.longValueExact();
        }

        @Specialization(guards = "!cannotBeOverridden(lib.getLazyPythonClass(self))", replaces = "doPIntOverridenNarrowLong", limit = "3")
        PInt doPIntOverriden(PInt self,
                        @SuppressWarnings("unused") @CachedLibrary("self") PythonObjectLibrary lib) {
            return factory().createInt(self.getValue());
        }

        @Specialization
        PythonNativeVoidPtr doL(PythonNativeVoidPtr self) {
            return self;
        }
    }

    @Builtin(name = SpecialMethodNames.__INDEX__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IndexNode extends IntNode {
    }

    @Builtin(name = SpecialMethodNames.__FLOAT__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class FloatNode extends PythonUnaryBuiltinNode {
        @Specialization
        double doBoolean(boolean self) {
            return self ? 1.0 : 0.0;
        }

        @Specialization
        double doInt(int self) {
            return self;
        }

        @Specialization
        double doLong(long self) {
            return self;
        }

        @Specialization
        double doPInt(PInt self) {
            return self.doubleValue();
        }

        @Fallback
        PNotImplemented doGeneric(@SuppressWarnings("unused") Object self) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }
}
