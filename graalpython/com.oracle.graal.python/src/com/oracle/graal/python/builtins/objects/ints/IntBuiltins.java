/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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
package com.oracle.graal.python.builtins.objects.ints;

import java.math.BigInteger;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.runtime.ArithmeticUtil;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.profiles.BranchProfile;

@CoreFunctions(extendClasses = PInt.class)
public class IntBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinNode>> getNodeFactories() {
        return IntBuiltinsFactory.getFactories();
    }

    private abstract static class IntBinaryBuiltinNode extends PythonBinaryBuiltinNode {
        BranchProfile divisionByZeroProfile = BranchProfile.create();

        protected void raiseDivisionByZero(boolean cond) {
            if (cond) {
                divisionByZeroProfile.enter();
                throw raise(PythonErrorType.ZeroDivisionError, "division by zero");
            }
        }
    }

    @Builtin(name = SpecialMethodNames.__ROUND__, minNumOfArguments = 1, maxNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class RoundNode extends PythonBinaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public int round(int arg, int n) {
            return arg;
        }
    }

    @Builtin(name = SpecialMethodNames.__ADD__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class AddNode extends PythonBinaryBuiltinNode {

        @Specialization
        Object add(boolean left, boolean right) {
            return PInt.intValue(left) + PInt.intValue(right);
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        int add(int left, boolean right) {
            return add(left, PInt.intValue(right));
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        int add(int left, int right) {
            return Math.addExact(left, right);
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        int add(boolean left, int right) {
            return addInt(PInt.intValue(left), right);
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        long add(boolean left, long right) {
            return addLong(PInt.intValue(left), right);
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        int addInt(int left, int right) {
            return Math.addExact(left, right);
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        long addLong(long left, long right) {
            return Math.addExact(left, right);
        }

        @Specialization
        PInt addPInt(boolean left, long right) {
            return addPInt(PInt.intValue(left), right);
        }

        @Specialization
        PInt addPInt(long left, long right) {
            return factory().createInt(op(BigInteger.valueOf(left), BigInteger.valueOf(right)));
        }

        @Specialization
        Object add(int left, PInt right) {
            return add(factory().createInt(left), right);
        }

        @Specialization
        Object add(PInt left, int right) {
            return add(left, factory().createInt(right));
        }

        @Specialization
        Object add(PInt left, long right) {
            return add(left, factory().createInt(right));
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        long add(long left, boolean right) {
            return addLong(left, PInt.intValue(right));
        }

        @Specialization
        Object add(long left, PInt right) {
            return add(factory().createInt(left), right);
        }

        @Specialization
        PInt add(PInt left, PInt right) {
            return factory().createInt(op(left.getValue(), right.getValue()));
        }

        @TruffleBoundary
        BigInteger op(BigInteger left, BigInteger right) {
            return left.add(right);
        }

        @SuppressWarnings("unused")
        @Fallback
        Object doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = SpecialMethodNames.__RADD__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class RAddNode extends AddNode {
    }

    @Builtin(name = SpecialMethodNames.__SUB__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class SubNode extends PythonBinaryBuiltinNode {
        @Specialization
        int doBB(boolean left, boolean right) {
            return PInt.intValue(left) - PInt.intValue(right);
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        int doBI(boolean left, int right) {
            return doII(PInt.intValue(left), right);
        }

        @Specialization
        long doBIOvf(boolean left, int right) {
            return doIIOvf(PInt.intValue(left), right);
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        long doBL(boolean left, long right) {
            return doLL(PInt.intValue(left), right);
        }

        @Specialization
        PInt doBLOvf(boolean left, long right) {
            return doLLOvf(PInt.intValue(left), right);
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        int doIB(int left, boolean right) {
            return doII(left, PInt.intValue(right));
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        int doII(int x, int y) throws ArithmeticException {
            return Math.subtractExact(x, y);
        }

        @Specialization
        long doIIOvf(int x, int y) {
            return (long) x - (long) y;
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        long doLL(long x, long y) throws ArithmeticException {
            return Math.subtractExact(x, y);
        }

        @Specialization
        PInt doLLOvf(long x, long y) {
            return factory().createInt(op(BigInteger.valueOf(x), BigInteger.valueOf(y)));
        }

        @Specialization
        PInt doIntegerPInt(int left, PInt right) {
            return factory().createInt(op(BigInteger.valueOf(left), right.getValue()));
        }

        @Specialization
        PInt doPIntInteger(PInt left, int right) {
            return factory().createInt(op(left.getValue(), BigInteger.valueOf(right)));
        }

        @Specialization
        Object doPIntLong(PInt left, long right) {
            return doPIntPInt(left, factory().createInt(right));
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        long doLB(long left, boolean right) {
            return doLL(left, PInt.intValue(right));
        }

        @Specialization
        PInt doLBOvf(long left, boolean right) {
            return doLLOvf(left, PInt.intValue(right));
        }

        @Specialization
        Object doLongPInt(long left, PInt right) {
            return doPIntPInt(factory().createInt(left), right);
        }

        @Specialization
        PInt doPIntPInt(PInt left, PInt right) {
            return factory().createInt(op(left.getValue(), right.getValue()));
        }

        @TruffleBoundary
        BigInteger op(BigInteger left, BigInteger right) {
            return left.subtract(right);
        }

        @SuppressWarnings("unused")
        @Fallback
        Object doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = SpecialMethodNames.__RSUB__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class RSubNode extends PythonBinaryBuiltinNode {
        @Specialization
        int doBB(boolean right, boolean left) {
            return PInt.intValue(left) - PInt.intValue(right);
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        int doBI(boolean right, int left) {
            return doII(PInt.intValue(right), left);
        }

        @Specialization
        long doBIOvf(boolean right, int left) {
            return doIIOvf(PInt.intValue(right), left);
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        long doBL(boolean right, long left) {
            return doLL(PInt.intValue(right), left);
        }

        @Specialization
        PInt doBLOvf(boolean right, long left) {
            return doLLOvf(PInt.intValue(right), left);
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        int doIB(int right, boolean left) {
            return doII(right, PInt.intValue(left));
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        int doII(int y, int x) throws ArithmeticException {
            return Math.subtractExact(x, y);
        }

        @Specialization
        long doIIOvf(int y, int x) {
            return (long) x - (long) y;
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        long doLL(long y, long x) throws ArithmeticException {
            return Math.subtractExact(x, y);
        }

        @Specialization
        PInt doLLOvf(long y, long x) {
            return factory().createInt(op(BigInteger.valueOf(x), BigInteger.valueOf(y)));
        }

        @Specialization
        PInt doIntegerPInt(int right, PInt left) {
            return factory().createInt(op(left.getValue(), BigInteger.valueOf(right)));
        }

        @Specialization
        PInt doPIntInteger(PInt right, int left) {
            return factory().createInt(op(BigInteger.valueOf(left), right.getValue()));
        }

        @Specialization
        Object doPIntLong(PInt right, long left) {
            return doPIntPInt(factory().createInt(left), right);
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        long doLB(long right, boolean left) {
            return doLL(right, PInt.intValue(left));
        }

        @Specialization
        PInt doLBOvf(long right, boolean left) {
            return doLLOvf(right, PInt.intValue(left));
        }

        @Specialization
        Object doLongPInt(long right, PInt left) {
            return doPIntPInt(factory().createInt(right), left);
        }

        @Specialization
        PInt doPIntPInt(PInt right, PInt left) {
            return factory().createInt(op(left.getValue(), right.getValue()));
        }

        @TruffleBoundary
        BigInteger op(BigInteger left, BigInteger right) {
            return left.subtract(right);
        }

        @SuppressWarnings("unused")
        @Fallback
        Object doGeneric(Object right, Object left) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = SpecialMethodNames.__TRUEDIV__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class TrueDivNode extends PythonBinaryBuiltinNode {
        @Specialization
        double divBB(boolean left, boolean right) {
            return divDD(PInt.doubleValue(left), PInt.doubleValue(right));
        }

        @Specialization
        double divBI(boolean left, int right) {
            return divDD(PInt.doubleValue(left), right);
        }

        @Specialization
        double divBL(boolean left, long right) {
            return divDD(PInt.doubleValue(left), right);
        }

        @Specialization
        double divIB(int left, boolean right) {
            return divII(left, PInt.intValue(right));
        }

        @Specialization
        double divII(int x, int y) {
            return divDD(x, y);
        }

        @Specialization
        double divLB(long left, boolean right) {
            return divLL(left, PInt.intValue(right));
        }

        @Specialization
        double divLL(long x, long y) {
            return divDD(x, y);
        }

        @Specialization
        double divDD(double x, double y) {
            if (y == 0) {
                throw raise(PythonErrorType.ZeroDivisionError, "division by zero");
            }
            return x / y;
        }

        @Specialization
        Object doPB(PInt left, boolean right) {
            return doPL(left, PInt.intValue(right));
        }

        @Specialization
        Object doPI(PInt left, int right) {
            if (right == 0) {
                throw raise(PythonErrorType.ZeroDivisionError, "division by zero");
            }
            return doPP(left, factory().createInt(right));
        }

        @Specialization
        Object doPL(PInt left, long right) {
            if (right == 0) {
                throw raise(PythonErrorType.ZeroDivisionError, "division by zero");
            }
            return doPP(left, factory().createInt(right));
        }

        @Specialization
        double doPP(PInt left, PInt right) {
            if (right.isZero()) {
                throw raise(PythonErrorType.ZeroDivisionError, "division by zero");
            }
            return op(left.getValue(), right.getValue());
        }

        /*
         * We must take special care to do double conversion late (if possible), to avoid loss of
         * precision.
         */
        @TruffleBoundary
        private static double op(BigInteger a, BigInteger b) {
            BigInteger[] divideAndRemainder = a.divideAndRemainder(b);
            if (divideAndRemainder[1].equals(BigInteger.ZERO)) {
                return divideAndRemainder[0].doubleValue();
            } else {
                return a.doubleValue() / b.doubleValue();
            }
        }

        @SuppressWarnings("unused")
        @Fallback
        Object doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = SpecialMethodNames.__RTRUEDIV__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class RTrueDivNode extends PythonBinaryBuiltinNode {
        @Specialization
        double divBB(boolean right, boolean left) {
            return divDD(PInt.doubleValue(right), PInt.doubleValue(left));
        }

        @Specialization
        double divBI(boolean right, long left) {
            return divDD(PInt.doubleValue(right), left);
        }

        @Specialization
        double divIB(int right, boolean left) {
            return divDD(right, PInt.doubleValue(left));
        }

        @Specialization
        double divII(int right, int left) {
            return divDD(right, left);
        }

        @Specialization
        double divLB(long right, boolean left) {
            return divDD(right, PInt.doubleValue(left));
        }

        @Specialization
        double divLL(long right, long left) {
            return divDD(right, left);
        }

        @Specialization
        double divDD(double right, double left) {
            if (right == 0) {
                throw raise(PythonErrorType.ZeroDivisionError, "division by zero");
            }
            return left / right;
        }

        @Specialization
        Object div(PInt right, boolean left) {
            return doPP(right, factory().createInt(left));
        }

        @Specialization
        double doPL(PInt right, long left) {
            return doPP(right, factory().createInt(left));
        }

        @Specialization
        double doPP(PInt right, PInt left) {
            if (right.isZero()) {
                throw raise(PythonErrorType.ZeroDivisionError, "division by zero");
            }
            return op(left.getValue(), right.getValue());
        }

        /*
         * We must take special care to do double conversion late (if possible), to avoid loss of
         * precision.
         */
        @TruffleBoundary
        private static double op(BigInteger a, BigInteger b) {
            BigInteger[] divideAndRemainder = a.divideAndRemainder(b);
            if (divideAndRemainder[1].equals(BigInteger.ZERO)) {
                return divideAndRemainder[0].doubleValue();
            } else {
                return a.doubleValue() / b.doubleValue();
            }
        }

        @SuppressWarnings("unused")
        @Fallback
        Object doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = SpecialMethodNames.__FLOORDIV__, fixedNumOfArguments = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class FloorDivNode extends IntBinaryBuiltinNode {
        @Specialization
        int doLL(int left, int right) {
            raiseDivisionByZero(right == 0);
            return Math.floorDiv(left, right);
        }

        @Specialization
        long doLL(long left, long right) {
            raiseDivisionByZero(right == 0);
            return Math.floorDiv(left, right);
        }

        @Specialization
        PInt doLPi(long left, PInt right) {
            raiseDivisionByZero(right.isZero());
            return factory().createInt(op(BigInteger.valueOf(left), right.getValue()));
        }

        @Specialization
        PInt doPiL(PInt left, long right) {
            raiseDivisionByZero(right == 0);
            return factory().createInt(op(left.getValue(), BigInteger.valueOf(right)));
        }

        @Specialization
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
        Object doGeneric(Object right, Object left) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

    }

    @Builtin(name = SpecialMethodNames.__RFLOORDIV__, fixedNumOfArguments = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class RFloorDivNode extends IntBinaryBuiltinNode {
        @Specialization
        int doII(int right, int left) {
            raiseDivisionByZero(right == 0);
            return left / right;
        }

        @Specialization
        long doLL(long right, long left) {
            raiseDivisionByZero(right == 0);
            return left / right;
        }

        @Specialization
        PInt doPiL(PInt right, long left) {
            raiseDivisionByZero(right.isZero());
            return factory().createInt(op(BigInteger.valueOf(left), right.getValue()));
        }

        @Specialization
        PInt doLPi(long right, PInt left) {
            raiseDivisionByZero(right == 0);
            return factory().createInt(op(left.getValue(), BigInteger.valueOf(right)));
        }

        @Specialization
        PInt doPiPi(PInt right, PInt left) {
            raiseDivisionByZero(right.isZero());
            return factory().createInt(op(left.getValue(), right.getValue()));
        }

        @TruffleBoundary
        static BigInteger op(BigInteger left, BigInteger right) {
            return left.divide(right);
        }

        @SuppressWarnings("unused")
        @Fallback
        Object doGeneric(Object right, Object left) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = SpecialMethodNames.__MOD__, fixedNumOfArguments = 2)
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

        @Specialization
        Object doLPi(long left, PInt right) {
            raiseDivisionByZero(right.isZero());
            return factory().createInt(op(BigInteger.valueOf(left), right.getValue()));
        }

        @Specialization(guards = "right >= 0")
        Object doPiL(PInt left, long right) {
            raiseDivisionByZero(right == 0);
            return factory().createInt(op(left.getValue(), BigInteger.valueOf(right)));
        }

        @Specialization(guards = "right.isZeroOrPositive()")
        Object doPiPi(PInt left, PInt right) {
            raiseDivisionByZero(right.isZero());
            return factory().createInt(op(left.getValue(), right.getValue()));
        }

        @Specialization(guards = "right < 0")
        Object doPiLNeg(PInt left, long right) {
            return factory().createInt(opNeg(left.getValue(), BigInteger.valueOf(right)));
        }

        @Specialization(guards = "!right.isZeroOrPositive()")
        Object doPiPiNeg(PInt left, PInt right) {
            return factory().createInt(opNeg(left.getValue(), right.getValue()));
        }

        @TruffleBoundary
        static BigInteger op(BigInteger a, BigInteger b) {
            return a.mod(b);
        }

        @TruffleBoundary
        static BigInteger opNeg(BigInteger a, BigInteger b) {
            if (a.equals(BigInteger.ZERO)) {
                return BigInteger.ZERO;
            }
            return a.mod(b.negate()).subtract(b);
        }

        @SuppressWarnings("unused")
        @Fallback
        Object doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = SpecialMethodNames.__MUL__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class MulNode extends PythonBinaryBuiltinNode {
        @Specialization
        int doBB(boolean x, boolean y) {
            return x ? PInt.intValue(y) : 0;
        }

        @Specialization
        int doBI(boolean x, int y) {
            return x ? y : 0;
        }

        @Specialization
        long doBL(boolean left, long right) {
            return left ? right : 0;
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        int doII(int x, int y) throws ArithmeticException {
            return Math.multiplyExact(x, y);
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        long doLL(long x, long y) {
            return Math.multiplyExact(x, y);
        }

        @Specialization
        Object doLLOvf(long x, long y) {
            try {
                return Math.multiplyExact(x, y);
            } catch (ArithmeticException e) {
                return factory().createInt(op(BigInteger.valueOf(x), BigInteger.valueOf(y)));
            }
        }

        @Specialization
        PInt doPIntPInt(PInt left, PInt right) {
            return factory().createInt(op(left.getValue(), right.getValue()));
        }

        @TruffleBoundary
        BigInteger op(BigInteger a, BigInteger b) {
            return a.multiply(b);
        }

        @Specialization
        Object doPIntLong(PInt left, long right) {
            return factory().createInt(op(left.getValue(), BigInteger.valueOf(right)));
        }

        @Specialization
        int doIB(int left, boolean right) {
            return right ? left : 0;
        }

        @Specialization
        long doLB(long left, boolean right) {
            return right ? left : 0;
        }

        @Specialization
        Object doPIntBoolean(PInt left, boolean right) {
            return right ? left : 0;
        }

        @SuppressWarnings("unused")
        @Fallback
        Object doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = SpecialMethodNames.__RMUL__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class RMulNode extends MulNode {
    }

    @Builtin(name = SpecialMethodNames.__POW__, minNumOfArguments = 2, maxNumOfArguments = 3)
    @GenerateNodeFactory
    abstract static class PowNode extends PythonTernaryBuiltinNode {
        @Specialization(guards = "right >= 0", rewriteOn = ArithmeticException.class)
        int doIntegerFast(int left, int right, @SuppressWarnings("unused") PNone none) {
            int result = 1;
            int exponent = right;
            int base = left;
            while (exponent != 0) {
                if ((exponent & 1) != 0) {
                    result = Math.multiplyExact(result, base);
                }
                exponent >>= 1;
                base = Math.multiplyExact(base, base);
            }
            return result;
        }

        @Specialization(guards = "right >= 0")
        PInt doInteger(int left, int right, @SuppressWarnings("unused") PNone none) {
            return factory().createInt(op(BigInteger.valueOf(left), right));
        }

        @Specialization(guards = "right >= 0", rewriteOn = ArithmeticException.class)
        long doLongFast(long left, int right, PNone none) {
            return doLongFast(left, (long) right, none);
        }

        @Specialization(guards = "right >= 0")
        PInt doLong(long left, int right, PNone none) {
            return doLong(left, (long) right, none);
        }

        @Specialization(guards = "right >= 0", rewriteOn = ArithmeticException.class)
        long doLongFast(int left, long right, PNone none) {
            return doLongFast((long) left, right, none);
        }

        @Specialization(guards = "right >= 0")
        PInt doLong(int left, long right, PNone none) {
            return doLong((long) left, right, none);
        }

        @Specialization(guards = "right >= 0", rewriteOn = ArithmeticException.class)
        long doLongFast(long left, long right, @SuppressWarnings("unused") PNone none) {
            long result = 1;
            long exponent = right;
            long base = left;
            while (exponent != 0) {
                if ((exponent & 1) != 0) {
                    result = Math.multiplyExact(result, base);
                }
                exponent >>= 1;
                base = Math.multiplyExact(base, base);
            }
            return result;
        }

        @Specialization(guards = "right >= 0")
        PInt doLong(long left, long right, @SuppressWarnings("unused") PNone none) {
            return factory().createInt(op(BigInteger.valueOf(left), right));
        }

        @Specialization
        double doInt(long left, long right, @SuppressWarnings("unused") PNone none) {
            return Math.pow(left, right);
        }

        @Specialization
        double doInt(long left, double right, @SuppressWarnings("unused") PNone none) {
            return Math.pow(left, right);
        }

        @Specialization
        PInt doPInt(PInt left, PInt right, @SuppressWarnings("unused") PNone none) {
            try {
                return factory().createInt(op(left.getValue(), right.getValue().longValueExact()));
            } catch (ArithmeticException e) {
                // fall through to normal computation
            }
            double value = Math.pow(left.doubleValue(), right.doubleValue());
            return factory().createInt((long) value);
        }

        @Fallback
        @SuppressWarnings("unused")
        Object doFallback(Object x, Object y, Object z) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        @TruffleBoundary
        private BigInteger op(BigInteger a, long b) {
            try {
                // handle shortcut cases:
                int value = a.intValueExact();
                if (value == 0) {
                    return BigInteger.ZERO;
                } else if (value == 1) {
                    return BigInteger.ONE;
                } else if (value == -1) {
                    return (b & 1) != 0 ? BigInteger.valueOf(-1) : BigInteger.ONE;
                }
            } catch (ArithmeticException e) {
                // fall through to normal computation
            }
            if (b != (int) b) {
                // exponent does not fit in an int, this is likely going to cause out-of-memory
                throw raise(PythonErrorType.ArithmeticError, "exponent too large");
            }
            return a.pow((int) b);
        }
    }

    @Builtin(name = SpecialMethodNames.__ABS__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    abstract static class AbsNode extends PythonUnaryBuiltinNode {
        @Specialization
        boolean pos(boolean arg) {
            return arg;
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        int pos(int arg) {
            int result = Math.abs(arg);
            if (result < 0) {
                throw new ArithmeticException();
            }
            return result;
        }

        @Specialization
        long posOvf(int arg) {
            return Math.abs((long) arg);
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        long pos(long arg) {
            long result = Math.abs(arg);
            if (result < 0) {
                throw new ArithmeticException();
            }
            return result;
        }

        @Specialization
        Object posOvf(long arg) {
            long result = Math.abs(arg);
            if (result < 0) {
                return factory().createInt(op(BigInteger.valueOf(arg)));
            } else {
                return result;
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

    @Builtin(name = SpecialMethodNames.__CEIL__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
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

    @Builtin(name = SpecialMethodNames.__FLOOR__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    abstract static class FloorNode extends PythonUnaryBuiltinNode {
        @Specialization
        int floor(int arg) {
            return arg;
        }

        @Specialization
        int floor(boolean arg) {
            return arg ? 1 : 0;
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

    @Builtin(name = SpecialMethodNames.__POS__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
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

    @Builtin(name = SpecialMethodNames.__NEG__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
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
        Object negOvf(long arg) {
            try {
                return Math.negateExact(arg);
            } catch (ArithmeticException e) {
                return factory().createInt(negate(BigInteger.valueOf(arg)));
            }
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

    @Builtin(name = SpecialMethodNames.__INVERT__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
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

    @Builtin(name = SpecialMethodNames.__LSHIFT__, fixedNumOfArguments = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class LShiftNode extends PythonBinaryBuiltinNode {
        @Specialization(rewriteOn = ArithmeticException.class)
        int doII(int left, int right) {
            raiseNegativeShiftCount(right < 0);
            return ArithmeticUtil.leftShiftExact(left, right);
        }

        @Specialization
        Object doIIOvf(int left, int right) {
            raiseNegativeShiftCount(right < 0);
            try {
                return ArithmeticUtil.leftShiftExact(left, right);
            } catch (ArithmeticException e) {
                return doGuardedBiI(BigInteger.valueOf(left), right);
            }
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        long doLL(long left, long right) {
            raiseNegativeShiftCount(right < 0);
            return ArithmeticUtil.leftShiftExact(left, right);
        }

        @Specialization
        Object doLLOvf(long left, long right) {
            raiseNegativeShiftCount(right < 0);
            try {
                return ArithmeticUtil.leftShiftExact(left, right);
            } catch (ArithmeticException e) {
                int rightI = (int) right;
                if (rightI == right) {
                    return factory().createInt(op(BigInteger.valueOf(left), rightI));
                } else {
                    throw raise(PythonErrorType.OverflowError);
                }
            }
        }

        @Specialization
        PInt doLPi(long left, PInt right) {
            raiseNegativeShiftCount(!right.isZeroOrPositive());
            try {
                return factory().createInt(op(BigInteger.valueOf(left), right.intValue()));
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

        @Specialization
        PInt doPiPi(PInt left, PInt right) {
            raiseNegativeShiftCount(!right.isZeroOrPositive());
            try {
                return factory().createInt(op(left.getValue(), right.intValueExact()));
            } catch (ArithmeticException e) {
                throw raise(PythonErrorType.OverflowError);
            }
        }

        @SuppressWarnings("unused")
        @Fallback
        Object doGeneric(Object a, Object b) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        @TruffleBoundary
        public static BigInteger op(BigInteger left, int right) {
            return left.shiftLeft(right);
        }

        private void raiseNegativeShiftCount(boolean cond) {
            if (cond) {
                throw raise(PythonErrorType.ValueError, "negative shift count");
            }
        }

    }

    @Builtin(name = SpecialMethodNames.__RSHIFT__, fixedNumOfArguments = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class RShiftNode extends PythonBinaryBuiltinNode {
        @Specialization(rewriteOn = ArithmeticException.class)
        int doII(int left, int right) {
            raiseNegativeShiftCount(right < 0);
            return left >> right;
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        long doLL(long left, long right) {
            raiseNegativeShiftCount(right < 0);
            return left >> right;
        }

        @Specialization
        PInt doLPi(long left, PInt right) {
            raiseNegativeShiftCount(!right.isZeroOrPositive());
            return factory().createInt(op(BigInteger.valueOf(left), right.intValue()));
        }

        @Specialization
        PInt doPiI(PInt left, int right) {
            raiseNegativeShiftCount(right < 0);
            return factory().createInt(op(left.getValue(), right));
        }

        @Specialization
        PInt doPiL(PInt left, long right) {
            raiseNegativeShiftCount(right < 0);
            return factory().createInt(op(left.getValue(), (int) right));
        }

        @Specialization
        PInt doPInt(PInt left, PInt right) {
            raiseNegativeShiftCount(!right.isZeroOrPositive());
            return factory().createInt(op(left.getValue(), right.intValue()));
        }

        private void raiseNegativeShiftCount(boolean cond) {
            if (cond) {
                throw raise(PythonErrorType.ValueError, "negative shift count");
            }
        }

        @SuppressWarnings("unused")
        @Fallback
        Object doGeneric(Object a, Object b) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        @TruffleBoundary
        public static BigInteger op(BigInteger left, int right) {
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
            return factory().createInt(op(BigInteger.valueOf(left), right.getValue()));
        }

        @Specialization
        PInt doPInt(PInt left, long right) {
            return factory().createInt(op(left.getValue(), BigInteger.valueOf(right)));
        }

        @Specialization
        PInt doPInt(PInt left, PInt right) {
            return factory().createInt(op(left.getValue(), right.getValue()));
        }

        @SuppressWarnings("unused")
        @Fallback
        Object doGeneric(Object a, Object b) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = SpecialMethodNames.__AND__, fixedNumOfArguments = 2)
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

    @Builtin(name = SpecialMethodNames.__RAND__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class RAndNode extends AndNode {
    }

    @Builtin(name = SpecialMethodNames.__OR__, fixedNumOfArguments = 2)
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

    @Builtin(name = SpecialMethodNames.__ROR__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class ROrNode extends OrNode {
    }

    @Builtin(name = SpecialMethodNames.__XOR__, fixedNumOfArguments = 2)
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

    @Builtin(name = SpecialMethodNames.__RXOR__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class RXorNode extends XorNode {
    }

    @Builtin(name = SpecialMethodNames.__EQ__, fixedNumOfArguments = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
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
            return a.equals(b);
        }

        @SuppressWarnings("unused")
        @Fallback
        Object eq(Object a, Object b) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = SpecialMethodNames.__NE__, fixedNumOfArguments = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class NeNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean eqLL(long a, long b) {
            return a != b;
        }

        @Specialization
        boolean eqPiPi(PInt a, PInt b) {
            return !a.equals(b);
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
        boolean eqPiL(long b, PInt a) {
            return a.longValueExact() != b;
        }

        @Specialization
        boolean eqPiLOvf(long b, PInt a) {
            try {
                return a.longValueExact() != b;
            } catch (ArithmeticException e) {
                return true;
            }
        }

        @SuppressWarnings("unused")
        @Fallback
        Object eq(Object a, Object b) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = SpecialMethodNames.__LT__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class LtNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean doII(int left, int right) {
            return left < right;
        }

        @Specialization
        boolean doIL(int left, long right) {
            return left < right;
        }

        @Specialization
        boolean doLI(long left, int right) {
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
        @TruffleBoundary
        boolean doPP(PInt left, PInt right) {
            return left.getValue().compareTo(right.getValue()) < 0;
        }

        @SuppressWarnings("unused")
        @Fallback
        Object doGeneric(Object a, Object b) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = SpecialMethodNames.__LE__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
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
        @TruffleBoundary
        boolean doPP(PInt left, PInt right) {
            return left.getValue().compareTo(right.getValue()) <= 0;
        }

        @SuppressWarnings("unused")
        @Fallback
        Object doGeneric(Object a, Object b) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = SpecialMethodNames.__GT__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class GtNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean doBB(boolean left, boolean right) {
            return PInt.intValue(left) > PInt.intValue(right);
        }

        @Specialization
        boolean doBI(boolean left, long right) {
            return PInt.intValue(left) > right;
        }

        @Specialization
        boolean doBP(boolean left, PInt right) {
            return doLP(PInt.intValue(left), right);
        }

        @Specialization
        boolean doII(int left, int right) {
            return left > right;
        }

        @Specialization
        boolean doLB(long left, boolean right) {
            return left > PInt.intValue(right);
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
        @TruffleBoundary
        boolean doPP(PInt left, PInt right) {
            return left.getValue().compareTo(right.getValue()) > 0;
        }

        @SuppressWarnings("unused")
        @Fallback
        Object doGeneric(Object a, Object b) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = SpecialMethodNames.__GE__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class GeNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean doBB(boolean left, boolean right) {
            return left ? true : !right;
        }

        @Specialization
        boolean doBI(boolean left, int right) {
            return (left ? 1 : 0) > right;
        }

        @Specialization
        boolean doIB(int left, boolean right) {
            return left > (right ? 1 : 0);
        }

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
        @TruffleBoundary
        boolean doPP(PInt left, PInt right) {
            return left.getValue().compareTo(right.getValue()) >= 0;
        }

        @SuppressWarnings("unused")
        @Fallback
        Object doGeneric(Object a, Object b) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = "from_bytes", fixedNumOfArguments = 2, takesVariableArguments = true, keywordArguments = {"signed"})
    @GenerateNodeFactory
    @SuppressWarnings("unused")
    public abstract static class FromBytesNode extends PythonBuiltinNode {
        private static byte[] littleToBig(byte[] bytes, String byteorder) {
            // PInt uses Java BigInteger which are big-endian
            if (byteorder.equals("big")) {
                return bytes;
            }
            byte[] bigEndianBytes = new byte[bytes.length + 1];
            bigEndianBytes[0] = 0;
            for (int i = 0; i < bytes.length; i++) {
                bigEndianBytes[bytes.length - i] = bytes[i];
            }
            return bytes;
        }

        @Specialization
        @TruffleBoundary
        public Object frombytesSignedOrNot(String str, String byteorder, Object[] args, boolean keywordArg) {
            byte[] bytes = littleToBig(str.getBytes(), byteorder);
            BigInteger integer = new BigInteger(bytes);
            if (keywordArg) {
                return factory().createInt(NegNode.negate(integer));
            } else {
                return factory().createInt(integer);
            }
        }

        @Specialization
        @TruffleBoundary
        public Object frombytes(String str, String byteorder, Object[] args, PNone keywordArg) {
            byte[] bytes = littleToBig(str.getBytes(), byteorder);
            return factory().createInt(new BigInteger(bytes));
        }

        @Specialization
        @TruffleBoundary
        public Object fromPBytes(PBytes str, String byteorder, Object[] args, PNone keywordArg) {
            byte[] bytes = littleToBig(str.getInternalByteArray(), byteorder);
            return factory().createInt(new BigInteger(bytes));
        }
    }

    @Builtin(name = SpecialMethodNames.__BOOL__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class BoolNode extends PythonBuiltinNode {
        @Specialization
        public boolean toBoolean(boolean self) {
            return self;
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

    @Builtin(name = SpecialMethodNames.__STR__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
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
        public Object doPInt(PInt self) {
            return self.toString();
        }
    }

    @Builtin(name = SpecialMethodNames.__REPR__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    abstract static class ReprNode extends StrNode {
    }

    @Builtin(name = SpecialMethodNames.__HASH__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    abstract static class HashNode extends PythonUnaryBuiltinNode {
        @Specialization
        int hash(boolean self) {
            return self ? 1 : 0;
        }

        @Specialization
        int hash(int self) {
            return self;
        }

        @Specialization
        long hash(long self) {
            return self;
        }

        @Specialization
        @TruffleBoundary
        long hash(PInt self) {
            return self.longValue();
        }
    }

    @Builtin(name = "bit_length", fixedNumOfArguments = 1)
    @GenerateNodeFactory
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
    @Builtin(name = "real", fixedNumOfArguments = 1, isGetter = true, doc = "the real part of a complex number")
    static abstract class RealNode extends IntNode {

    }

    @GenerateNodeFactory
    @Builtin(name = "imag", fixedNumOfArguments = 1, isGetter = true, doc = "the imaginary part of a complex number")
    static abstract class ImagNode extends PythonBuiltinNode {
        @Specialization
        int get(@SuppressWarnings("unused") Object self) {
            return 0;
        }
    }

    @GenerateNodeFactory
    @Builtin(name = "numerator", fixedNumOfArguments = 1, isGetter = true, doc = "the numerator of a rational number in lowest terms")
    static abstract class NumeratorNode extends IntNode {

    }

    @GenerateNodeFactory
    @Builtin(name = "conjugate", fixedNumOfArguments = 1, doc = "Returns self, the complex conjugate of any int.")
    static abstract class ConjugateNode extends IntNode {

    }

    @GenerateNodeFactory
    @Builtin(name = "denominator", fixedNumOfArguments = 1, isGetter = true, doc = "the denominator of a rational number in lowest terms")
    static abstract class DenominatorNode extends PythonBuiltinNode {
        @Specialization
        int get(@SuppressWarnings("unused") Object self) {
            return 1;
        }
    }

    @GenerateNodeFactory
    @Builtin(name = SpecialMethodNames.__TRUNC__, fixedNumOfArguments = 1, doc = "Truncating an Integral returns itself.")
    static abstract class TruncNode extends IntNode {

    }

    @Builtin(name = SpecialMethodNames.__INT__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    abstract static class IntNode extends PythonBuiltinNode {
        @Child private GetClassNode getClassNode;

        protected PythonClass getClass(Object value) {
            if (getClassNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getClassNode = insert(GetClassNode.create());
            }
            return getClassNode.execute(value);
        }

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

        @Specialization(guards = "cannotBeOverridden(getClass(self))")
        PInt doPInt(PInt self) {
            return self;
        }

        @Specialization(guards = "!cannotBeOverridden(getClass(self))")
        PInt doPIntOverriden(PInt self) {
            return factory().createInt(self.getValue());
        }
    }

    @Builtin(name = SpecialMethodNames.__INDEX__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    abstract static class IndexNode extends IntNode {
    }

    @Builtin(name = SpecialMethodNames.__FLOAT__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    abstract static class FloatNode extends PythonBuiltinNode {
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
        Object doGeneric(@SuppressWarnings("unused") Object self) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }
}
