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
package com.oracle.graal.python.builtins.objects.floats;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__ABS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__BOOL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__FLOAT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__FLOORDIV__;
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
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RFLOORDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RMOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ROUND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RSUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RTRUEDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__STR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__TRUEDIV__;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteOrder;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.nodes.call.special.LookupAndCallVarargsNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.runtime.JavaTypeConversions;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;

@CoreFunctions(extendClasses = PFloat.class)
public final class FloatBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinNode>> getNodeFactories() {
        return FloatBuiltinsFactory.getFactories();
    }

    public static double asDouble(boolean right) {
        return right ? 1.0 : 0.0;
    }

    @Builtin(name = __STR__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    abstract static class StrNode extends PythonUnaryBuiltinNode {
        @Specialization
        String str(double self) {
            return JavaTypeConversions.doubleToString(self);
        }

        @Specialization
        String str(PFloat self) {
            return str(self.getValue());
        }
    }

    @Builtin(name = __ABS__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    abstract static class AbsNode extends PythonUnaryBuiltinNode {

        @Specialization
        double abs(double arg) {
            return Math.abs(arg);
        }

        @Specialization
        double abs(PFloat arg) {
            return Math.abs(arg.getValue());
        }

    }

    @Builtin(name = __REPR__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    abstract static class ReprNode extends StrNode {
    }

    @Builtin(name = __BOOL__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    abstract static class BoolNode extends PythonUnaryBuiltinNode {
        @Specialization
        boolean bool(double self) {
            return self != 0.0;
        }
    }

    @Builtin(name = __INT__, fixedNumOfArguments = 1)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class IntNode extends PythonUnaryBuiltinNode {
        protected boolean isIntRange(double self) {
            return self >= Integer.MIN_VALUE && self <= Integer.MAX_VALUE;
        }

        protected boolean isLongRange(double self) {
            return self >= Long.MIN_VALUE && self <= Long.MAX_VALUE;
        }

        @Specialization(guards = "isIntRange(self)")
        int doIntRange(double self) {
            return (int) self;
        }

        @Specialization(guards = "isLongRange(self)")
        long doLongRange(double self) {
            return (long) self;
        }

        @Specialization(guards = "!isLongRange(self)")
        @TruffleBoundary
        PInt doGeneric(double self) {
            return factory().createInt(BigDecimal.valueOf(self).toBigInteger());
        }
    }

    @Builtin(name = __FLOAT__, fixedNumOfArguments = 1)
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
    }

    @Builtin(name = __ADD__, fixedNumOfArguments = 2)
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
            return left + right.doubleValue();
        }

        @SuppressWarnings("unused")
        @Fallback
        Object doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __RADD__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class RAddNode extends AddNode {
    }

    @Builtin(name = __SUB__, fixedNumOfArguments = 2)
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
            return left - right.doubleValue();
        }

        @SuppressWarnings("unused")
        @Fallback
        Object doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __RSUB__, fixedNumOfArguments = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class RSubNode extends PythonBinaryBuiltinNode {
        @Specialization
        double doDD(double right, double left) {
            return left - right;
        }

        @Specialization
        double doDL(double right, long left) {
            return left - right;
        }

        @Specialization
        double doDPi(double right, PInt left) {
            return left.doubleValue() - right;
        }

        @SuppressWarnings("unused")
        @Fallback
        Object doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __MUL__, fixedNumOfArguments = 2)
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
            return left * right.doubleValue();
        }

        @SuppressWarnings("unused")
        @Fallback
        Object doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __RMUL__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class RMulNode extends MulNode {
    }

    @Builtin(name = __POW__, fixedNumOfArguments = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class PowerNode extends PythonBinaryBuiltinNode {
        @Specialization
        double doDL(double left, long right) {
            return Math.pow(left, right);
        }

        @Specialization
        double doDPi(double left, PInt right) {
            return Math.pow(left, right.doubleValue());
        }

        @Specialization
        double doDD(double left, double right) {
            return Math.pow(left, right);
        }

        @SuppressWarnings("unused")
        @Fallback
        Object doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __FLOORDIV__, fixedNumOfArguments = 2)
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
            return Math.floor(left / right.doubleValue());
        }

        @Specialization
        double doDD(double left, double right) {
            raiseDivisionByZero(right == 0.0);
            return Math.floor(left / right);
        }

        @SuppressWarnings("unused")
        @Fallback
        Object doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = "fromhex", fixedNumOfArguments = 2)
    @GenerateNodeFactory
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
                    throw raise(PythonErrorType.OverflowError, "hexadecimal value too large to represent as a float");
                }

                return result;
            } catch (NumberFormatException ex) {
                throw raise(PythonErrorType.ValueError, INVALID_STRING);
            }
        }

        @Specialization(guards = "isPythonBuiltinClass(cl)")
        @SuppressWarnings("unused")
        public double fromhexFloat(PythonClass cl, String arg) {
            return fromHex(arg);
        }

        @Specialization(guards = "!isPythonBuiltinClass(cl)")
        public Object fromhexO(PythonClass cl, String arg,
                        @Cached("create(__CALL__)") LookupAndCallVarargsNode constr) {
            double value = fromHex(arg);
            Object result = constr.execute(cl, new Object[]{cl, value});

            return result;
        }

        @Fallback
        @SuppressWarnings("unused")
        public double fromhex(Object object, Object arg) {
            throw raise(PythonErrorType.TypeError, "bad argument type for built-in operation");
        }
    }

    @Builtin(name = "hex", fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class HexNode extends PythonBuiltinNode {

        @TruffleBoundary
        private static String makeHexNumber(double value) {
            String result = Double.toHexString(value);
            String lresult = result.toLowerCase();
            if (lresult.equals("nan")) {
                return lresult;
            } else if (lresult.equals("infinity")) {
                return "inf";
            } else if (lresult.equals("-infinity")) {
                return "-inf";
            } else if (lresult.equals("0x0.0p0")) {
                return "0x0.0p+0";
            } else if (lresult.equals("-0x0.0p0")) {
                return "-0x0.0p+0";
            }

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

        @Specialization
        public String hexPF(PFloat value) {
            return makeHexNumber(value.getValue());
        }
    }

    @Builtin(name = __RFLOORDIV__, fixedNumOfArguments = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class RFloorDivNode extends FloatBinaryBuiltinNode {
        @Specialization
        Object doDL(double right, long left) {
            raiseDivisionByZero(right == 0.0);
            return Math.floor(left / right);
        }

        @Specialization
        Object doDPi(double right, PInt left) {
            raiseDivisionByZero(right == 0.0);
            return Math.floor(left.doubleValue() / right);
        }

        @Specialization
        double doDD(double left, double right) {
            // Cannot be reached via standard dispatch but it can be called directly.
            raiseDivisionByZero(right == 0.0);
            return Math.floor(left / right);
        }

        @SuppressWarnings("unused")
        @Fallback
        Object doGeneric(Object right, Object left) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __MOD__, fixedNumOfArguments = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class ModNode extends FloatBinaryBuiltinNode {
        @Specialization
        double doDL(double left, long right) {
            raiseDivisionByZero(right == 0);
            return left % right;
        }

        @Specialization
        double doDL(double left, PInt right) {
            raiseDivisionByZero(right.isZero());
            return left % right.doubleValue();
        }

        @Specialization
        double doDD(double left, double right) {
            raiseDivisionByZero(right == 0.0);
            return left % right;
        }

        @SuppressWarnings("unused")
        @Fallback
        Object doGeneric(Object right, Object left) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __RMOD__, fixedNumOfArguments = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class RModNode extends FloatBinaryBuiltinNode {
        @Specialization
        Object doDL(double right, long left) {
            raiseDivisionByZero(right == 0.0);
            return left % right;
        }

        @Specialization
        Object doGeneric(double right, PInt left) {
            raiseDivisionByZero(right == 0.0);
            return left.doubleValue() % right;
        }

        @Specialization
        double doDD(double left, double right) {
            raiseDivisionByZero(right == 0.0);
            return left % right;
        }

        @SuppressWarnings("unused")
        @Fallback
        Object doGeneric(Object right, Object left) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __TRUEDIV__, fixedNumOfArguments = 2)
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
            return left / right.doubleValue();
        }

        @SuppressWarnings("unused")
        @Fallback
        Object doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __RTRUEDIV__, fixedNumOfArguments = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class RDivNode extends PythonBinaryBuiltinNode {
        @Specialization
        double div(double right, double left) {
            return left / right;
        }

        @Specialization
        double div(double right, long left) {
            return left / right;
        }

        @Specialization
        double div(double right, PInt left) {
            return left.doubleValue() / right;
        }

        @SuppressWarnings("unused")
        @Fallback
        Object doGeneric(Object right, Object left) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __ROUND__, minNumOfArguments = 1, maxNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class RoundNode extends PythonBinaryBuiltinNode {
        /**
         * The logic is borrowed from Jython.
         */
        @TruffleBoundary
        @Specialization
        double round(double x, int n) {
            if (Double.isNaN(x) || Double.isInfinite(x) || x == 0.0) {
                // nans, infinities and zeros round to themselves
                return x;
            } else {
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
                    BigDecimal rr = xx.setScale(n, RoundingMode.HALF_UP);
                    return rr.doubleValue();
                }
            }
        }

        @Specialization
        long round(double x, @SuppressWarnings("unused") PNone none) {
            return ((Double) round(x, 0)).longValue();
        }

        @Specialization(guards = {"!isInteger(n)", "!isPNone(n)"})
        Object round(@SuppressWarnings("unused") double x, Object n) {
            throw raise(PythonErrorType.TypeError, "'%p' object cannot be interpreted as an integer", n);
        }
    }

    @Builtin(name = __EQ__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class EqNode extends PythonBinaryBuiltinNode {

        @Specialization
        boolean eqDbDb(double a, double b) {
            return a == b;
        }

        @Specialization
        boolean eqDbDb(double a, boolean b) {
            return a == asDouble(b);
        }

        @Specialization
        boolean eqDbLn(double a, long b) {
            return a == b;
        }

        @Specialization
        boolean eqDbPI(double a, PInt b) {
            return a == b.doubleValue();
        }

        @Specialization
        boolean eqPFDb(PFloat self, double other) {
            return self.getValue() == other;
        }

        @Specialization
        boolean eqDbPF(double a, PFloat other) {
            return a == other.getValue();
        }

        @Specialization
        boolean eqPFPF(PFloat self, PFloat other) {
            return self.equals(other);
        }

        @Fallback
        @SuppressWarnings("unused")
        Object eq(Object a, Object b) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __NE__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class NeNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean eqDbDb(double a, double b) {
            return a != b;
        }

        @Specialization
        boolean eqDbLn(double a, long b) {
            return a != b;
        }

        @Specialization
        boolean eqDbPI(double a, PInt b) {
            return a != b.doubleValue();
        }

        @Specialization
        boolean eqPFDb(PFloat self, double other) {
            return self.getValue() != other;
        }

        @Specialization
        boolean eqPFPF(PFloat self, PFloat other) {
            return !self.equals(other);
        }

        @Fallback
        @SuppressWarnings("unused")
        Object eq(Object a, Object b) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __LT__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class LtNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean doDD(double x, double y) {
            return x < y;
        }

        @Specialization
        boolean doDB(double x, boolean y) {
            return x < asDouble(y);
        }

        @Specialization
        boolean doDL(double x, long y) {
            return x < y;
        }

        @Fallback
        @SuppressWarnings("unused")
        Object doGeneric(Object a, Object b) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __LE__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class LeNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean doDD(double x, double y) {
            return x <= y;
        }

        @Specialization
        boolean doDB(double x, boolean y) {
            return x <= asDouble(y);
        }

        @Specialization
        boolean doDL(double x, long y) {
            return x <= y;
        }

        @Fallback
        @SuppressWarnings("unused")
        Object doGeneric(Object a, Object b) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __GT__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class GtNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean doDD(double x, double y) {
            return x > y;
        }

        @Specialization
        boolean doDB(double x, boolean y) {
            return x > asDouble(y);
        }

        @Specialization
        boolean doDL(double x, long y) {
            return x > y;
        }

        @Fallback
        @SuppressWarnings("unused")
        Object doGeneric(Object a, Object b) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __GE__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class GeNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean doDD(double x, double y) {
            return x >= y;
        }

        @Specialization
        boolean doDB(double x, boolean y) {
            return x >= asDouble(y);
        }

        @Specialization
        boolean doDL(double x, long y) {
            return x >= y;
        }

        @Fallback
        @SuppressWarnings("unused")
        Object doGeneric(Object a, Object b) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __POS__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    abstract static class PosNode extends PythonUnaryBuiltinNode {
        @Specialization
        double pos(double arg) {
            return arg;
        }

        @Specialization
        double pos(PFloat arg) {
            return arg.getValue();
        }
    }

    @Builtin(name = __NEG__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    abstract static class NegNode extends PythonUnaryBuiltinNode {
        @Specialization
        double neg(double arg) {
            return -arg;
        }

        @Specialization
        double neg(PFloat operand) {
            return -operand.getValue();
        }
    }

    @Builtin(name = __GETFORMAT__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class GetFormatNode extends PythonUnaryBuiltinNode {
        private String getDetectedEndianess() {
            try {
                ByteOrder byteOrder = ByteOrder.nativeOrder();
                if (byteOrder.equals(ByteOrder.BIG_ENDIAN)) {
                    return "IEEE, big-endian";
                } else if (byteOrder.equals(ByteOrder.LITTLE_ENDIAN)) {
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
        String getFormat(@SuppressWarnings("unused") PythonClass cls, @SuppressWarnings("unused") String typeStr) {
            return getDetectedEndianess();
        }

        @Fallback
        String getFormat(@SuppressWarnings("unused") Object cls, @SuppressWarnings("unused") Object typeStr) {
            throw raise(PythonErrorType.ValueError, "__getformat__() argument 1 must be 'double' or 'float'");
        }
    }

    private abstract static class FloatBinaryBuiltinNode extends PythonBinaryBuiltinNode {
        protected void raiseDivisionByZero(boolean cond) {
            if (cond) {
                throw raise(PythonErrorType.ZeroDivisionError, "division by zero");
            }
        }
    }
}
