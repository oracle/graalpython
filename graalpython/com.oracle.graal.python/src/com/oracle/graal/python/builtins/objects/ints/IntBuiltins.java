/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ABS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___AND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___BOOL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CEIL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___DIVMOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___FLOAT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___FLOORDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___FLOOR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___FORMAT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GETNEWARGS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___HASH__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INDEX__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INVERT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___LE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___LSHIFT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___LT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___MOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___MUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NEG__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___OR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___POS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___POW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RAND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RDIVMOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RFLOORDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RLSHIFT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ROR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ROUND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RPOW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RRSHIFT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RSHIFT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RSUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RTRUEDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RXOR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___STR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___TRUEDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___TRUNC__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___XOR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___BYTES__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___LT__;
import static com.oracle.graal.python.nodes.StringLiterals.T_BIG;
import static com.oracle.graal.python.nodes.StringLiterals.T_LITTLE;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
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
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeVoidPtr;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.FromNativeSubclassNode;
import com.oracle.graal.python.builtins.objects.common.FormatNodeBase;
import com.oracle.graal.python.builtins.objects.ints.IntBuiltinsClinicProviders.FormatNodeClinicProviderGen;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.lib.PyNumberFloatNode;
import com.oracle.graal.python.lib.PyObjectHashNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
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
import com.oracle.graal.python.nodes.object.GetClassNode.GetPythonObjectClassNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.formatting.FloatFormatter;
import com.oracle.graal.python.runtime.formatting.IntegerFormatter;
import com.oracle.graal.python.runtime.formatting.InternalFormat;
import com.oracle.graal.python.runtime.formatting.InternalFormat.Spec;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.profiles.InlinedIntValueProfile;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PInt)
public final class IntBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return IntBuiltinsFactory.getFactories();
    }

    private abstract static class IntBinaryBuiltinNode extends PythonBinaryBuiltinNode {
        protected static void raiseDivisionByZero(Node inliningTarget, boolean cond, InlinedBranchProfile divisionByZeroProfile, PRaiseNode.Lazy raiseNode) {
            if (cond) {
                raiseDivByZero(inliningTarget, divisionByZeroProfile, raiseNode.get(inliningTarget));
            }
        }

        @InliningCutoff
        private static void raiseDivByZero(Node inliningTarget, InlinedBranchProfile divisionByZeroProfile, PRaiseNode raiseNode) {
            divisionByZeroProfile.enter(inliningTarget);
            throw raiseNode.raise(PythonErrorType.ZeroDivisionError, ErrorMessages.S_DIVISION_OR_MODULO_BY_ZERO, "integer");
        }
    }

    @Builtin(name = J___ROUND__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @ImportStatic(MathGuards.class)
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class RoundNode extends PythonBinaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        static int roundIntNone(int arg, PNone n) {
            return arg;
        }

        @SuppressWarnings("unused")
        @Specialization
        static long roundLongNone(long arg, PNone n) {
            return arg;
        }

        @SuppressWarnings("unused")
        @Specialization
        static PInt roundPIntNone(PInt arg, PNone n,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PythonObjectFactory.Lazy factory) {
            return factory.get(inliningTarget).createInt(arg.getValue());
        }

        @Specialization
        static Object roundLongInt(long arg, int n,
                        @Bind("this") Node inliningTarget,
                        @Shared("intOvf") @Cached InlinedBranchProfile intOverflow,
                        @Shared @Cached PythonObjectFactory.Lazy factory) {
            if (n >= 0) {
                return arg;
            }
            return makeInt(inliningTarget, op(arg, n), intOverflow, factory);
        }

        @Specialization
        static Object roundPIntInt(PInt arg, int n,
                        @Bind("this") Node inliningTarget,
                        @Shared("intOvf") @Cached InlinedBranchProfile intOverflow,
                        @Shared @Cached PythonObjectFactory.Lazy factory) {
            if (n >= 0) {
                return arg;
            }
            return makeInt(inliningTarget, op(arg.getValue(), n), intOverflow, factory);
        }

        @Specialization
        static Object roundLongLong(long arg, long n,
                        @Bind("this") Node inliningTarget,
                        @Shared("intOvf") @Cached InlinedBranchProfile intOverflow,
                        @Shared @Cached PythonObjectFactory.Lazy factory) {
            if (n >= 0) {
                return arg;
            }
            if (n < Integer.MIN_VALUE) {
                return 0;
            }
            return makeInt(inliningTarget, op(arg, (int) n), intOverflow, factory);
        }

        @Specialization
        static Object roundPIntLong(PInt arg, long n,
                        @Bind("this") Node inliningTarget,
                        @Shared("intOvf") @Cached InlinedBranchProfile intOverflow,
                        @Shared @Cached PythonObjectFactory.Lazy factory) {
            if (n >= 0) {
                return arg;
            }
            if (n < Integer.MIN_VALUE) {
                return 0;
            }
            return makeInt(inliningTarget, op(arg.getValue(), (int) n), intOverflow, factory);
        }

        @Specialization
        static Object roundPIntLong(long arg, PInt n,
                        @Bind("this") Node inliningTarget,
                        @Shared("intOvf") @Cached InlinedBranchProfile intOverflow,
                        @Shared @Cached PythonObjectFactory.Lazy factory) {
            if (n.isZeroOrPositive()) {
                return arg;
            }
            try {
                return makeInt(inliningTarget, op(arg, n.intValueExact()), intOverflow, factory);
            } catch (OverflowException e) {
                // n is < -2^31, max. number of base-10 digits in BigInteger is 2^31 * log10(2)
                return 0;
            }
        }

        @Specialization
        static Object roundPIntPInt(PInt arg, PInt n,
                        @Bind("this") Node inliningTarget,
                        @Shared("intOvf") @Cached InlinedBranchProfile intOverflow,
                        @Shared @Cached PythonObjectFactory.Lazy factory) {
            if (n.isZeroOrPositive()) {
                return arg;
            }
            try {
                return makeInt(inliningTarget, op(arg.getValue(), n.intValueExact()), intOverflow, factory);
            } catch (OverflowException e) {
                // n is < -2^31, max. number of base-10 digits in BigInteger is 2^31 * log10(2)
                return 0;
            }
        }

        @Specialization(guards = {"!isInteger(n)"})
        @SuppressWarnings("unused")
        static Object roundPIntPInt(Object arg, Object n,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(PythonErrorType.TypeError, ErrorMessages.OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER, n);
        }

        private static Object makeInt(Node inliningTarget, BigDecimal d, InlinedBranchProfile intOverflow, PythonObjectFactory.Lazy factory) {
            try {
                return intValueExact(d);
            } catch (OverflowException e) {
                // does not fit int, so try long
                intOverflow.enter(inliningTarget);
            }
            try {
                return longValueExact(d);
            } catch (OverflowException e) {
                // does not fit long, try BigInteger
            }
            try {
                // lazy factory initialization should serve as branch profile
                return factory.get(inliningTarget).createInt(toBigIntegerExact(d));
            } catch (OverflowException e) {
                // has non-zero fractional part, which should not happen
                throw CompilerDirectives.shouldNotReachHere("non-integer produced after rounding an integer", e);
            }
        }

        @TruffleBoundary
        private static BigInteger toBigIntegerExact(BigDecimal d) throws OverflowException {
            try {
                return d.toBigIntegerExact();
            } catch (ArithmeticException ex) {
                throw OverflowException.INSTANCE;
            }
        }

        @TruffleBoundary
        private static int intValueExact(BigDecimal d) throws OverflowException {
            try {
                return d.intValueExact();
            } catch (ArithmeticException ex) {
                throw OverflowException.INSTANCE;
            }
        }

        @TruffleBoundary
        private static long longValueExact(BigDecimal d) throws OverflowException {
            try {
                return d.longValueExact();
            } catch (ArithmeticException ex) {
                throw OverflowException.INSTANCE;
            }
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

    @Builtin(name = J___RADD__, minNumOfPositionalArgs = 2)
    @Builtin(name = J___ADD__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class AddNode extends PythonBinaryBuiltinNode {
        public abstract Object execute(int left, int right);

        @Specialization(rewriteOn = ArithmeticException.class)
        static int add(int left, int right) {
            return Math.addExact(left, right);
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        static long addLong(long left, long right) {
            return Math.addExact(left, right);
        }

        @Specialization
        static Object addLongWithOverflow(long x, long y,
                        @Shared @Cached PythonObjectFactory factory) {
            /* Inlined version of Math.addExact(x, y) with BigInteger fallback. */
            long r = x + y;
            // HD 2-12 Overflow iff both arguments have the opposite sign of the result
            if (((x ^ r) & (y ^ r)) < 0) {
                return factory.createInt(op(PInt.longToBigInteger(x), PInt.longToBigInteger(y)));
            }
            return r;
        }

        @Specialization(rewriteOn = OverflowException.class)
        static Object addPIntLongAndNarrow(PInt left, long right) throws OverflowException {
            return PInt.longValueExact(op(left.getValue(), PInt.longToBigInteger(right)));
        }

        @Specialization(replaces = "addPIntLongAndNarrow")
        static Object addPIntLong(PInt left, long right,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createInt(op(left.getValue(), PInt.longToBigInteger(right)));
        }

        @Specialization(rewriteOn = OverflowException.class)
        static Object addLongPIntAndNarrow(long left, PInt right) throws OverflowException {
            return PInt.longValueExact(op(PInt.longToBigInteger(left), right.getValue()));
        }

        @Specialization(replaces = "addLongPIntAndNarrow")
        static Object addLongPInt(long left, PInt right,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createInt(op(PInt.longToBigInteger(left), right.getValue()));
        }

        @Specialization(rewriteOn = OverflowException.class)
        static Object addPIntPIntAndNarrow(PInt left, PInt right) throws OverflowException {
            return PInt.longValueExact(op(left.getValue(), right.getValue()));
        }

        @Specialization(replaces = "addPIntPIntAndNarrow")
        static Object addPIntPInt(PInt left, PInt right,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createInt(op(left.getValue(), right.getValue()));
        }

        @TruffleBoundary
        static BigInteger op(BigInteger left, BigInteger right) {
            return left.add(right);
        }

        @SuppressWarnings("unused")
        @Fallback
        static PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        @NeverDefault
        public static AddNode create() {
            return IntBuiltinsFactory.AddNodeFactory.create();
        }
    }

    @Builtin(name = J___RSUB__, minNumOfPositionalArgs = 2, reverseOperation = true)
    @Builtin(name = J___SUB__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class SubNode extends PythonBinaryBuiltinNode {
        public abstract Object execute(int left, int right);

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
        static Object doLongWithOverflow(long x, long y,
                        @Shared @Cached PythonObjectFactory factory) {
            /* Inlined version of Math.subtractExact(x, y) with BigInteger fallback. */
            long r = x - y;
            // HD 2-12 Overflow iff the arguments have different signs and
            // the sign of the result is different than the sign of x
            if (((x ^ y) & (x ^ r)) < 0) {
                return factory.createInt(op(PInt.longToBigInteger(x), PInt.longToBigInteger(y)));
            }
            return r;
        }

        @Specialization(rewriteOn = OverflowException.class)
        static long doPIntLongAndNarrow(PInt left, long right) throws OverflowException {
            return PInt.longValueExact(op(left.getValue(), PInt.longToBigInteger(right)));
        }

        @Specialization(replaces = "doPIntLongAndNarrow")
        static PInt doPIntLong(PInt left, long right,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createInt(op(left.getValue(), PInt.longToBigInteger(right)));
        }

        @Specialization(rewriteOn = OverflowException.class)
        static long doLongPIntAndNarrow(long left, PInt right) throws OverflowException {
            return PInt.longValueExact(op(PInt.longToBigInteger(left), right.getValue()));
        }

        @Specialization(replaces = "doLongPIntAndNarrow")
        static PInt doLongPInt(long left, PInt right,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createInt(op(PInt.longToBigInteger(left), right.getValue()));
        }

        @Specialization(rewriteOn = OverflowException.class)
        static long doPIntPIntAndNarrow(PInt left, PInt right) throws OverflowException {
            return PInt.longValueExact(op(left.getValue(), right.getValue()));
        }

        @Specialization(replaces = "doPIntPIntAndNarrow")
        static PInt doPIntPInt(PInt left, PInt right,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createInt(op(left.getValue(), right.getValue()));
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

        @NeverDefault
        public static SubNode create() {
            return IntBuiltinsFactory.SubNodeFactory.create();
        }
    }

    @Builtin(name = J___RTRUEDIV__, minNumOfPositionalArgs = 2, reverseOperation = true)
    @Builtin(name = J___TRUEDIV__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class TrueDivNode extends PythonBinaryBuiltinNode {
        public abstract Object execute(int left, int right);

        @Specialization
        static double divII(int x, int y,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            return divDD(x, y, inliningTarget, raiseNode);
        }

        @Specialization(guards = {"fitsIntoDouble(x)", "fitsIntoDouble(y)"})
        static double divLL(long x, long y,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            return divDD(x, y, inliningTarget, raiseNode);
        }

        @Specialization(guards = {"!fitsIntoDouble(x) || !fitsIntoDouble(y)"})
        static double divLLLarge(long x, long y,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            if (y == 0) {
                throw raiseNode.get(inliningTarget).raise(PythonErrorType.ZeroDivisionError, ErrorMessages.DIVISION_BY_ZERO);
            }
            return op(inliningTarget, PInt.longToBigInteger(x), PInt.longToBigInteger(y));
        }

        static double divDD(double x, double y,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            if (y == 0) {
                throw raiseNode.get(inliningTarget).raise(PythonErrorType.ZeroDivisionError, ErrorMessages.DIVISION_BY_ZERO);
            }
            return x / y;
        }

        @Specialization
        static double doPI(long left, PInt right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            if (right.isZero()) {
                throw raiseNode.get(inliningTarget).raise(PythonErrorType.ZeroDivisionError, ErrorMessages.DIVISION_BY_ZERO);
            }
            return op(inliningTarget, PInt.longToBigInteger(left), right.getValue());
        }

        @Specialization
        static double doPL(PInt left, long right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            if (right == 0) {
                throw raiseNode.get(inliningTarget).raise(PythonErrorType.ZeroDivisionError, ErrorMessages.DIVISION_BY_ZERO);
            }
            return op(inliningTarget, left.getValue(), PInt.longToBigInteger(right));
        }

        @Specialization
        static double doPP(PInt left, PInt right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            if (right.isZero()) {
                throw raiseNode.get(inliningTarget).raise(PythonErrorType.ZeroDivisionError, ErrorMessages.DIVISION_BY_ZERO);
            }
            return op(inliningTarget, left.getValue(), right.getValue());
        }

        /*
         * We must take special care to do double conversion late (if possible), to avoid loss of
         * precision.
         */
        @TruffleBoundary
        private static double op(Node raisingNode, BigInteger a, BigInteger b) {
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
                throw PRaiseNode.raiseUncached(raisingNode, OverflowError, ErrorMessages.INTEGER_DIVISION_RESULT_TOO_LARGE);
            }
            return d;
        }

        protected static boolean fitsIntoDouble(long x) {
            return x < (1L << 52) && x > -(1L << 52);
        }

        private static boolean fitsIntoDouble(BigInteger x) {
            return x.bitLength() < 53;
        }

        @SuppressWarnings("unused")
        @Fallback
        static PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        @NeverDefault
        public static TrueDivNode create() {
            return IntBuiltinsFactory.TrueDivNodeFactory.create();
        }
    }

    @Builtin(name = J___RFLOORDIV__, minNumOfPositionalArgs = 2, reverseOperation = true)
    @Builtin(name = J___FLOORDIV__, minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class FloorDivNode extends IntBinaryBuiltinNode {
        public abstract Object execute(int left, int right);

        @Specialization
        static int doII(int left, int right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached InlinedBranchProfile divisionByZeroProfile,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            raiseDivisionByZero(inliningTarget, right == 0, divisionByZeroProfile, raiseNode);
            return Math.floorDiv(left, right);
        }

        @Specialization(rewriteOn = OverflowException.class)
        static long doLL(long left, long right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached InlinedBranchProfile divisionByZeroProfile,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) throws OverflowException {
            if (left == Long.MIN_VALUE && right == -1) {
                throw OverflowException.INSTANCE;
            }
            raiseDivisionByZero(inliningTarget, right == 0, divisionByZeroProfile, raiseNode);
            return Math.floorDiv(left, right);
        }

        @Specialization(replaces = "doLL")
        static PInt doLLOverflow(long left, long right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached InlinedBranchProfile divisionByZeroProfile,
                        @Shared @Cached PythonObjectFactory factory,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            return doPiPi(factory.createInt(left), factory.createInt(right), inliningTarget, divisionByZeroProfile, factory, raiseNode);
        }

        @Specialization(rewriteOn = OverflowException.class)
        static int doIPi(int left, PInt right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached InlinedBranchProfile divisionByZeroProfile,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) throws OverflowException {
            raiseDivisionByZero(inliningTarget, right.isZero(), divisionByZeroProfile, raiseNode);
            return Math.floorDiv(left, right.intValueExact());
        }

        @Specialization(replaces = "doIPi")
        static int doIPiOvf(int left, PInt right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached InlinedBranchProfile divisionByZeroProfile,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            raiseDivisionByZero(inliningTarget, right.isZero(), divisionByZeroProfile, raiseNode);
            try {
                return Math.floorDiv(left, right.intValueExact());
            } catch (OverflowException e) {
                return left < 0 == right.isNegative() ? 0 : -1;
            }
        }

        @Specialization(rewriteOn = OverflowException.class)
        static long doLPi(long left, PInt right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached InlinedBranchProfile divisionByZeroProfile,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) throws OverflowException {
            raiseDivisionByZero(inliningTarget, right.isZero(), divisionByZeroProfile, raiseNode);
            return Math.floorDiv(left, right.longValueExact());
        }

        @Specialization(replaces = "doLPi")
        static long doLPiOvf(long left, PInt right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached InlinedBranchProfile divisionByZeroProfile,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            raiseDivisionByZero(inliningTarget, right.isZero(), divisionByZeroProfile, raiseNode);
            try {
                return Math.floorDiv(left, right.longValueExact());
            } catch (OverflowException e) {
                return left < 0 == right.isNegative() ? 0 : -1;
            }
        }

        @Specialization(rewriteOn = OverflowException.class)
        static long doPiIAndNarrow(PInt left, int right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached InlinedBranchProfile divisionByZeroProfile,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) throws OverflowException {
            raiseDivisionByZero(inliningTarget, right == 0, divisionByZeroProfile, raiseNode);
            return PInt.longValueExact(op(left.getValue(), PInt.longToBigInteger(right)));
        }

        @Specialization(replaces = "doPiIAndNarrow")
        static PInt doPiI(PInt left, int right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached InlinedBranchProfile divisionByZeroProfile,
                        @Shared @Cached PythonObjectFactory factory,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            raiseDivisionByZero(inliningTarget, right == 0, divisionByZeroProfile, raiseNode);
            return factory.createInt(op(left.getValue(), PInt.longToBigInteger(right)));
        }

        @Specialization(rewriteOn = OverflowException.class)
        static long doPiLAndNarrow(PInt left, long right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached InlinedBranchProfile divisionByZeroProfile,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) throws OverflowException {
            raiseDivisionByZero(inliningTarget, right == 0, divisionByZeroProfile, raiseNode);
            return PInt.longValueExact(op(left.getValue(), PInt.longToBigInteger(right)));
        }

        @Specialization(replaces = "doPiLAndNarrow")
        static PInt doPiL(PInt left, long right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached InlinedBranchProfile divisionByZeroProfile,
                        @Shared @Cached PythonObjectFactory factory,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            raiseDivisionByZero(inliningTarget, right == 0, divisionByZeroProfile, raiseNode);
            return factory.createInt(op(left.getValue(), PInt.longToBigInteger(right)));
        }

        @Specialization(rewriteOn = OverflowException.class)
        static long doPiPiAndNarrow(PInt left, PInt right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached InlinedBranchProfile divisionByZeroProfile,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) throws OverflowException {
            raiseDivisionByZero(inliningTarget, right.isZero(), divisionByZeroProfile, raiseNode);
            return PInt.longValueExact(op(left.getValue(), right.getValue()));
        }

        @Specialization(replaces = "doPiPiAndNarrow")
        static PInt doPiPi(PInt left, PInt right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached InlinedBranchProfile divisionByZeroProfile,
                        @Shared @Cached PythonObjectFactory factory,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            raiseDivisionByZero(inliningTarget, right.isZero(), divisionByZeroProfile, raiseNode);
            return factory.createInt(op(left.getValue(), right.getValue()));
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
        static PNotImplemented doGeneric(Object right, Object left) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        @NeverDefault
        public static FloorDivNode create() {
            return IntBuiltinsFactory.FloorDivNodeFactory.create();
        }
    }

    @Builtin(name = J___RDIVMOD__, minNumOfPositionalArgs = 2, reverseOperation = true)
    @Builtin(name = J___DIVMOD__, minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class DivModNode extends IntBinaryBuiltinNode {
        @Specialization
        static PTuple doLL(int left, int right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached InlinedBranchProfile divisionByZeroProfile,
                        @Shared @Cached PythonObjectFactory factory,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            raiseDivisionByZero(inliningTarget, right == 0, divisionByZeroProfile, raiseNode);
            return factory.createTuple(new Object[]{Math.floorDiv(left, right), Math.floorMod(left, right)});
        }

        @Specialization
        static PTuple doLL(long left, long right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached InlinedBranchProfile divisionByZeroProfile,
                        @Shared @Cached PythonObjectFactory factory,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            raiseDivisionByZero(inliningTarget, right == 0, divisionByZeroProfile, raiseNode);
            return factory.createTuple(new Object[]{Math.floorDiv(left, right), Math.floorMod(left, right)});
        }

        @Specialization(guards = {"accepts(left)", "accepts(right)"})
        static PTuple doGenericInt(VirtualFrame frame, Object left, Object right,
                        @Cached FloorDivNode floorDivNode,
                        @Cached ModNode modNode,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createTuple(new Object[]{floorDivNode.execute(frame, left, right), modNode.execute(frame, left, right)});
        }

        @SuppressWarnings("unused")
        @Fallback
        static PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        protected static boolean accepts(Object obj) {
            return obj instanceof Integer || obj instanceof Long || obj instanceof PInt;
        }
    }

    @Builtin(name = J___MOD__, minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class ModNode extends IntBinaryBuiltinNode {
        public abstract int executeInt(int left, int right) throws UnexpectedResultException;

        public abstract Object execute(int left, int right);

        @Specialization
        static int doII(int left, int right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached InlinedBranchProfile divisionByZeroProfile,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            raiseDivisionByZero(inliningTarget, right == 0, divisionByZeroProfile, raiseNode);
            return Math.floorMod(left, right);
        }

        @Specialization
        static long doLL(long left, long right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached InlinedBranchProfile divisionByZeroProfile,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            raiseDivisionByZero(inliningTarget, right == 0, divisionByZeroProfile, raiseNode);
            return Math.floorMod(left, right);
        }

        @Specialization(guards = "right.isZeroOrPositive()", rewriteOn = OverflowException.class)
        static long doLPiAndNarrow(long left, PInt right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached InlinedBranchProfile divisionByZeroProfile,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) throws OverflowException {
            raiseDivisionByZero(inliningTarget, right.isZero(), divisionByZeroProfile, raiseNode);
            return PInt.longValueExact(op(PInt.longToBigInteger(left), right.getValue()));
        }

        @Specialization(guards = "right.isZeroOrPositive()", replaces = "doLPiAndNarrow")
        static PInt doLPi(long left, PInt right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached InlinedBranchProfile divisionByZeroProfile,
                        @Shared @Cached PythonObjectFactory factory,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            raiseDivisionByZero(inliningTarget, right.isZero(), divisionByZeroProfile, raiseNode);
            return factory.createInt(op(PInt.longToBigInteger(left), right.getValue()));
        }

        @Specialization(guards = "!right.isZeroOrPositive()", rewriteOn = OverflowException.class)
        static long doLPiNegativeAndNarrow(long left, PInt right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached InlinedBranchProfile divisionByZeroProfile,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) throws OverflowException {
            raiseDivisionByZero(inliningTarget, right.isZero(), divisionByZeroProfile, raiseNode);
            return PInt.longValueExact(opNeg(PInt.longToBigInteger(left), right.getValue()));
        }

        @Specialization(guards = "!right.isZeroOrPositive()", replaces = "doLPiNegativeAndNarrow")
        static PInt doLPiNegative(long left, PInt right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached InlinedBranchProfile divisionByZeroProfile,
                        @Shared @Cached PythonObjectFactory factory,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            raiseDivisionByZero(inliningTarget, right.isZero(), divisionByZeroProfile, raiseNode);
            return factory.createInt(opNeg(PInt.longToBigInteger(left), right.getValue()));
        }

        @Specialization(guards = "right >= 0", rewriteOn = OverflowException.class)
        static long doPiLAndNarrow(PInt left, long right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached InlinedBranchProfile divisionByZeroProfile,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) throws OverflowException {
            raiseDivisionByZero(inliningTarget, right == 0, divisionByZeroProfile, raiseNode);
            return PInt.longValueExact(op(left.getValue(), PInt.longToBigInteger(right)));
        }

        @Specialization(guards = "right >= 0", replaces = "doPiLAndNarrow")
        static PInt doPiL(PInt left, long right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached InlinedBranchProfile divisionByZeroProfile,
                        @Shared @Cached PythonObjectFactory factory,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            raiseDivisionByZero(inliningTarget, right == 0, divisionByZeroProfile, raiseNode);
            return factory.createInt(op(left.getValue(), PInt.longToBigInteger(right)));
        }

        @Specialization(guards = "right < 0", rewriteOn = OverflowException.class)
        static long doPiLNegAndNarrow(PInt left, long right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached InlinedBranchProfile divisionByZeroProfile,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) throws OverflowException {
            raiseDivisionByZero(inliningTarget, right == 0, divisionByZeroProfile, raiseNode);
            return PInt.longValueExact(opNeg(left.getValue(), PInt.longToBigInteger(right)));
        }

        @Specialization(guards = "right < 0", replaces = "doPiLNegAndNarrow")
        static PInt doPiLNeg(PInt left, long right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached InlinedBranchProfile divisionByZeroProfile,
                        @Shared @Cached PythonObjectFactory factory,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            raiseDivisionByZero(inliningTarget, right == 0, divisionByZeroProfile, raiseNode);
            return factory.createInt(opNeg(left.getValue(), PInt.longToBigInteger(right)));
        }

        @Specialization(guards = "right.isZeroOrPositive()", rewriteOn = OverflowException.class)
        static long doPiPiAndNarrow(PInt left, PInt right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached InlinedBranchProfile divisionByZeroProfile,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) throws OverflowException {
            raiseDivisionByZero(inliningTarget, right.isZero(), divisionByZeroProfile, raiseNode);
            return PInt.longValueExact(op(left.getValue(), right.getValue()));
        }

        @Specialization(guards = "right.isZeroOrPositive()", replaces = "doPiPiAndNarrow")
        static PInt doPiPi(PInt left, PInt right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached InlinedBranchProfile divisionByZeroProfile,
                        @Shared @Cached PythonObjectFactory factory,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            raiseDivisionByZero(inliningTarget, right.isZero(), divisionByZeroProfile, raiseNode);
            return factory.createInt(op(left.getValue(), right.getValue()));
        }

        @Specialization(guards = "!right.isZeroOrPositive()", rewriteOn = OverflowException.class)
        static long doPiPiNegAndNarrow(PInt left, PInt right) throws OverflowException {
            return PInt.longValueExact(opNeg(left.getValue(), right.getValue()));
        }

        @Specialization(guards = "!right.isZeroOrPositive()", replaces = "doPiPiNegAndNarrow")
        static PInt doPiPiNeg(PInt left, PInt right,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createInt(opNeg(left.getValue(), right.getValue()));
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
        static PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        @NeverDefault
        public static ModNode create() {
            return IntBuiltinsFactory.ModNodeFactory.create();
        }
    }

    @Builtin(name = J___RMUL__, minNumOfPositionalArgs = 2)
    @Builtin(name = J___MUL__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class MulNode extends PythonBinaryBuiltinNode {
        public abstract Object execute(int left, int right);

        @Specialization(rewriteOn = ArithmeticException.class)
        static int doII(int x, int y) throws ArithmeticException {
            return Math.multiplyExact(x, y);
        }

        @Specialization(replaces = "doII")
        static long doIIL(int x, int y) {
            return x * (long) y;
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        static long doLL(long x, long y) {
            return Math.multiplyExact(x, y);
        }

        @Specialization
        static Object doLongWithOverflow(long x, long y,
                        @Shared @Cached PythonObjectFactory factory) {
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
                    return factory.createInt(mul(PInt.longToBigInteger(x), PInt.longToBigInteger(y)));
                }
            }
            return r;
        }

        @Specialization(guards = "right == 0")
        static int doPIntLongZero(@SuppressWarnings("unused") PInt left, @SuppressWarnings("unused") long right) {
            return 0;
        }

        @Specialization(guards = "left == 0")
        static int doPIntLongZero(@SuppressWarnings("unused") long left, @SuppressWarnings("unused") PInt right) {
            return 0;
        }

        @Specialization(guards = "right == 1")
        static PInt doPIntLongOne(PInt left, @SuppressWarnings("unused") long right,
                        @Shared @Cached PythonObjectFactory factory) {
            // we must return a new object with the same value
            return factory.createInt(left.getValue());
        }

        @Specialization(guards = "left == 1")
        PInt doPIntLongOne(@SuppressWarnings("unused") long left, PInt right,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createInt(right.getValue());
        }

        @Specialization(guards = {"right != 0", "right != 1"})
        static PInt doPIntLong(PInt left, long right,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createInt(mul(left.getValue(), PInt.longToBigInteger(right)));
        }

        @Specialization(guards = {"left != 0", "left != 1"})
        PInt doPIntLong(long left, PInt right,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createInt(mul(PInt.longToBigInteger(left), right.getValue()));
        }

        @Specialization
        static PInt doPIntPInt(PInt left, PInt right,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createInt(mul(left.getValue(), right.getValue()));
        }

        @TruffleBoundary
        static BigInteger mul(BigInteger a, BigInteger b) {
            if (!BigInteger.ZERO.equals(b) && b.and(b.subtract(BigInteger.ONE)).equals(BigInteger.ZERO)) {
                return bigIntegerShift(a, b.getLowestSetBit());
            } else {
                return bigIntegerMul(a, b);
            }
        }

        @TruffleBoundary
        static BigInteger bigIntegerMul(BigInteger a, BigInteger b) {
            return a.multiply(b);
        }

        @TruffleBoundary
        static BigInteger bigIntegerShift(BigInteger a, int n) {
            return a.shiftLeft(n);
        }

        @SuppressWarnings("unused")
        @Fallback
        static PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        @NeverDefault
        public static MulNode create() {
            return IntBuiltinsFactory.MulNodeFactory.create();
        }
    }

    @Builtin(name = J___RPOW__, minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3, reverseOperation = true)
    @Builtin(name = J___POW__, minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(MathGuards.class)
    @ReportPolymorphism
    public abstract static class PowNode extends PythonTernaryBuiltinNode {
        protected abstract int executeInt(int left, int right, PNone none) throws UnexpectedResultException;

        protected abstract Object execute(int left, int right, PNone none);

        public final int executeInt(int left, int right) throws UnexpectedResultException {
            return executeInt(left, right, PNone.NO_VALUE);
        }

        public final Object execute(int left, int right) {
            return execute(left, right, PNone.NO_VALUE);
        }

        @Specialization(guards = "right >= 0", rewriteOn = ArithmeticException.class)
        static int doIIFast(int left, int right, @SuppressWarnings("unused") PNone none) {
            int result = 1;
            int exponent = right;
            int base = left;
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

        @Specialization(guards = "right >= 0", rewriteOn = ArithmeticException.class)
        @InliningCutoff
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
        @InliningCutoff
        PInt doLLPos(long left, long right, @SuppressWarnings("unused") PNone none,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createInt(op(PInt.longToBigInteger(left), right));
        }

        @Specialization(guards = "right < 0")
        @InliningCutoff
        double doLLNeg(long left, long right, @SuppressWarnings("unused") PNone none,
                        @Bind("this") Node inliningTarget,
                        @Shared("leftIsZero") @Cached InlinedConditionProfile leftIsZero,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            if (leftIsZero.profile(inliningTarget, left == 0)) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.ZeroDivisionError, ErrorMessages.POW_ZERO_CANNOT_RAISE_TO_NEGATIVE_POWER);
            }
            return Math.pow(left, right);
        }

        @Specialization(rewriteOn = {OverflowException.class, ArithmeticException.class})
        @InliningCutoff
        Object doLPNarrow(long left, PInt right, @SuppressWarnings("unused") PNone none,
                        @Bind("this") Node inliningTarget,
                        @Shared("leftIsZero") @Cached InlinedConditionProfile leftIsZero,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) throws OverflowException {
            long lright = right.longValueExact();
            if (lright >= 0) {
                return doLLFast(left, lright, none);
            }
            return doLLNeg(left, lright, none, inliningTarget, leftIsZero, raiseNode);
        }

        @Specialization(replaces = "doLPNarrow")
        @InliningCutoff
        Object doLP(long left, PInt right, @SuppressWarnings("unused") PNone none,
                        @Shared @Cached PythonObjectFactory factory) {
            Object result = op(PInt.longToBigInteger(left), right.getValue());
            if (result instanceof BigInteger) {
                return factory.createInt((BigInteger) result);
            } else {
                return result;
            }
        }

        @Specialization(guards = "right >= 0", rewriteOn = OverflowException.class)
        @InliningCutoff
        long doPLNarrow(PInt left, long right, @SuppressWarnings("unused") PNone none) throws OverflowException {
            return PInt.longValueExact(op(left.getValue(), right));
        }

        @Specialization(guards = "right >= 0", replaces = "doPLNarrow")
        @InliningCutoff
        PInt doPLPos(PInt left, long right, @SuppressWarnings("unused") PNone none,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createInt(op(left.getValue(), right));
        }

        @Specialization(guards = "right < 0")
        @InliningCutoff
        double doPLNeg(PInt left, long right, @SuppressWarnings("unused") PNone none,
                        @Bind("this") Node inliningTarget,
                        @Shared("leftIsZero") @Cached InlinedConditionProfile leftIsZero,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            if (leftIsZero.profile(inliningTarget, left.isZero())) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.ZeroDivisionError, ErrorMessages.POW_ZERO_CANNOT_RAISE_TO_NEGATIVE_POWER);
            }
            return TrueDivNode.op(this, BigInteger.ONE, op(left.getValue(), -right));
        }

        @Specialization
        @InliningCutoff
        Object doPP(PInt left, PInt right, @SuppressWarnings("unused") PNone none,
                        @Shared @Cached PythonObjectFactory factory) {
            Object result = op(left.getValue(), right.getValue());
            if (result instanceof BigInteger) {
                return factory.createInt((BigInteger) result);
            } else {
                return result;
            }
        }

        @Specialization(guards = {"right >= 0", "mod > 0"})
        @InliningCutoff
        static long doLLPosLPos(long left, long right, long mod) {
            try {
                return PInt.longValueExact(op(left, right, mod));
            } catch (OverflowException e) {
                // cannot happen since we took modulo long AND 'mod > 0'
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException();
            }
        }

        @Specialization(guards = "right >= 0", replaces = "doLLPosLPos")
        @InliningCutoff
        static long doLLPosLGeneric(long left, long right, long mod,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached InlinedConditionProfile modNegativeProfile,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            if (mod == 0) {
                throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.POW_THIRD_ARG_CANNOT_BE_ZERO);
            }
            try {
                if (modNegativeProfile.profile(inliningTarget, mod < 0)) {
                    return PInt.longValueExact(opNeg(left, right, mod));
                }
                return PInt.longValueExact(op(left, right, mod));
            } catch (OverflowException e) {
                // cannot happen since we took modulo long AND 'mod != 0'
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException();
            }
        }

        // see cpython://Objects/longobject.c#long_pow
        @Specialization(replaces = "doPP")
        @InliningCutoff
        Object powModulo(Object x, Object y, Object z,
                        @Shared @Cached PythonObjectFactory factory) {
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
                return factory.createInt((BigInteger) result);
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
                throw PRaiseNode.raiseUncached(this, ValueError, ErrorMessages.POW_THIRD_ARG_CANNOT_BE_ZERO);
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
                    throw PRaiseNode.raiseUncached(this, ValueError, ErrorMessages.POW_BASE_NOT_INVERTIBLE);
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
                throw CompilerDirectives.shouldNotReachHere("never reached");
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
                throw PRaiseNode.raiseUncached(this, PythonBuiltinClassType.ZeroDivisionError, ErrorMessages.POW_ZERO_CANNOT_RAISE_TO_NEGATIVE_POWER);
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
                throw PRaiseNode.raiseUncached(this, PythonErrorType.ArithmeticError, ErrorMessages.EXPONENT_TOO_LARGE);
            }
            return a.pow((int) b);
        }

        @NeverDefault
        public static PowNode create() {
            return IntBuiltinsFactory.PowNodeFactory.create();
        }
    }

    @Builtin(name = J___ABS__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class AbsNode extends PythonUnaryBuiltinNode {
        @Specialization
        static int absBoolean(boolean arg) {
            return arg ? 1 : 0;
        }

        @Specialization(rewriteOn = {ArithmeticException.class, OverflowException.class})
        static int absInt(int arg) throws OverflowException {
            int result = Math.abs(arg);
            if (result < 0) {
                throw OverflowException.INSTANCE;
            }
            return result;
        }

        @Specialization(replaces = "absInt")
        static long absIntOvf(int arg) {
            // Math.abs(Integer#MIN_VALUE) returns Integer#MIN_VALUE
            return Math.abs((long) arg);
        }

        @Specialization(rewriteOn = {ArithmeticException.class, OverflowException.class})
        static long absLong(long arg) throws OverflowException {
            long result = Math.abs(arg);
            if (result < 0) {
                throw OverflowException.INSTANCE;
            }
            return result;
        }

        @Specialization(replaces = "absLong")
        static PInt absLongOvf(long arg,
                        @Shared @Cached PythonObjectFactory factory) {
            long result = Math.abs(arg);
            if (result < 0) {
                return factory.createInt(absBigInteger(PInt.longToBigInteger(arg)));
            } else {
                return factory.createInt(PInt.longToBigInteger(arg));
            }
        }

        @Specialization
        static PInt absPInt(PInt arg,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createInt(absBigInteger(arg.getValue()));
        }

        @TruffleBoundary
        static BigInteger absBigInteger(BigInteger value) {
            return value.abs();
        }
    }

    @Builtin(name = J___CEIL__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class CeilNode extends PythonUnaryBuiltinNode {
        @Specialization
        static int ceil(int arg) {
            return arg;
        }

        @Specialization
        static long ceil(long arg) {
            return arg;
        }

        @Specialization
        static PInt ceil(PInt arg) {
            return arg;
        }
    }

    @Builtin(name = J___FLOOR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class FloorNode extends PythonUnaryBuiltinNode {
        @Specialization
        static int floor(int arg) {
            return arg;
        }

        @Specialization
        static long floor(long arg) {
            return arg;
        }

        @Specialization
        static PInt floor(PInt arg,
                        @Cached PythonObjectFactory factory) {
            return factory.createInt(arg.getValue());
        }
    }

    @Builtin(name = J___POS__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class PosNode extends PythonUnaryBuiltinNode {
        @Specialization
        static int pos(int arg) {
            return arg;
        }

        @Specialization
        static long pos(long arg) {
            return arg;
        }

        @Specialization
        static PInt pos(PInt arg,
                        @Cached PythonObjectFactory factory) {
            return factory.createInt(arg.getValue());
        }
    }

    @Builtin(name = J___NEG__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class NegNode extends PythonUnaryBuiltinNode {
        public abstract Object execute(int value);

        @Specialization(rewriteOn = ArithmeticException.class)
        static int neg(int arg) {
            return Math.negateExact(arg);
        }

        @Specialization
        static long negOvf(int arg) {
            return -((long) arg);
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        static long neg(long arg) {
            return Math.negateExact(arg);
        }

        @Specialization
        static PInt negOvf(long arg,
                        @Shared @Cached PythonObjectFactory factory) {
            BigInteger value = arg == Long.MIN_VALUE ? negate(PInt.longToBigInteger(arg)) : PInt.longToBigInteger(-arg);
            return factory.createInt(value);
        }

        @Specialization
        static PInt doPInt(PInt operand,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createInt(negate(operand.getValue()));
        }

        @TruffleBoundary
        static BigInteger negate(BigInteger value) {
            return value.negate();
        }

        @NeverDefault
        public static NegNode create() {
            return IntBuiltinsFactory.NegNodeFactory.create();
        }
    }

    @Builtin(name = J___INVERT__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class InvertNode extends PythonUnaryBuiltinNode {
        @Specialization
        static int neg(boolean arg) {
            return ~(arg ? 1 : 0);
        }

        @Specialization
        static int neg(int arg) {
            return ~arg;
        }

        @Specialization
        static long neg(long arg) {
            return ~arg;
        }

        @Specialization
        static PInt doPInt(PInt operand,
                        @Cached PythonObjectFactory factory) {
            return factory.createInt(not(operand.getValue()));
        }

        @TruffleBoundary
        static BigInteger not(BigInteger value) {
            return value.not();
        }
    }

    @Builtin(name = J___LSHIFT__, minNumOfPositionalArgs = 2)
    @Builtin(name = J___RLSHIFT__, minNumOfPositionalArgs = 2, reverseOperation = true)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class LShiftNode extends PythonBinaryBuiltinNode {
        public abstract int executeInt(int left, int right) throws UnexpectedResultException;

        public abstract Object execute(int left, int right);

        private static long leftShiftExact(Node inliningTarget, long left, long right, PRaiseNode.Lazy raiseNode) throws OverflowException {
            if (right >= Long.SIZE || right < 0) {
                shiftError(inliningTarget, right, raiseNode);
            }

            long result = left << right;

            if (left != result >> right) {
                throw OverflowException.INSTANCE;
            }

            return result;
        }

        private static int leftShiftExact(Node inliningTarget, int left, int right, PRaiseNode.Lazy raiseNode) throws OverflowException {
            if (right >= Integer.SIZE || right < 0) {
                shiftError(inliningTarget, right, raiseNode);
            }

            int result = left << right;

            if (left != result >> right) {
                throw OverflowException.INSTANCE;
            }

            return result;
        }

        private static void shiftError(Node inliningTarget, long shiftCount, PRaiseNode.Lazy raiseNode) throws OverflowException {
            if (shiftCount >= Integer.SIZE) {
                throw OverflowException.INSTANCE;
            } else if (shiftCount < 0) {
                throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.NEGATIVE_SHIFT_COUNT);
            }
        }

        @Specialization(rewriteOn = OverflowException.class)
        static int doII(int left, int right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) throws OverflowException {
            raiseNegativeShiftCount(inliningTarget, right < 0, raiseNode);
            return leftShiftExact(inliningTarget, left, right, raiseNode);
        }

        @Specialization
        static Object doIIOvf(int left, int right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PythonObjectFactory factory,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            raiseNegativeShiftCount(inliningTarget, right < 0, raiseNode);
            try {
                return leftShiftExact(inliningTarget, left, right, raiseNode);
            } catch (OverflowException e) {
                return doGuardedBiI(inliningTarget, PInt.longToBigInteger(left), right, factory, raiseNode);
            }
        }

        @Specialization(rewriteOn = OverflowException.class)
        static long doLL(long left, long right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) throws OverflowException {
            raiseNegativeShiftCount(inliningTarget, right < 0, raiseNode);
            return leftShiftExact(inliningTarget, left, right, raiseNode);
        }

        @Specialization
        static Object doILOvf(int left, long right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PythonObjectFactory factory,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            return doLLOvf(left, right, inliningTarget, factory, raiseNode);
        }

        @Specialization
        static Object doLIOvf(long left, int right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PythonObjectFactory factory,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            return doLLOvf(left, right, inliningTarget, factory, raiseNode);
        }

        @Specialization
        static Object doLLOvf(long left, long right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PythonObjectFactory factory,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            raiseNegativeShiftCount(inliningTarget, right < 0, raiseNode);
            try {
                return leftShiftExact(inliningTarget, left, right, raiseNode);
            } catch (OverflowException e) {
                int rightI = (int) right;
                if (rightI == right) {
                    try {
                        return factory.createInt(op(PInt.longToBigInteger(left), rightI));
                    } catch (OverflowException ex) {
                        // fallback to the raise of overflow error
                    }
                }
                throw raiseNode.get(inliningTarget).raise(PythonErrorType.OverflowError);
            }
        }

        @Specialization(guards = {"left == 0", "right.isZeroOrPositive()"})
        static int doIPiZero(@SuppressWarnings("unused") int left, @SuppressWarnings("unused") PInt right) {
            return 0;
        }

        @Specialization(replaces = "doIPiZero")
        static PInt doIPi(int left, PInt right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PythonObjectFactory factory,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            raiseNegativeShiftCount(inliningTarget, !right.isZeroOrPositive(), raiseNode);
            if (left == 0) {
                return factory.createInt(BigInteger.ZERO);
            }
            try {
                int iright = right.intValueExact();
                return factory.createInt(op(PInt.longToBigInteger(left), iright));
            } catch (OverflowException e) {
                throw raiseNode.get(inliningTarget).raise(PythonErrorType.OverflowError);
            }
        }

        @Specialization(guards = {"left == 0", "right.isZeroOrPositive()"})
        static int doLPiZero(@SuppressWarnings("unused") long left, @SuppressWarnings("unused") PInt right) {
            return 0;
        }

        // there is some duplication in here because of GR-44123

        @Specialization(replaces = "doLPiZero")
        static PInt doLPi(long left, PInt right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PythonObjectFactory factory,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            raiseNegativeShiftCount(inliningTarget, !right.isZeroOrPositive(), raiseNode);
            if (left == 0) {
                return factory.createInt(BigInteger.ZERO);
            }
            try {
                int iright = right.intValueExact();
                return factory.createInt(op(PInt.longToBigInteger(left), iright));
            } catch (OverflowException e) {
                throw raiseNode.get(inliningTarget).raise(PythonErrorType.OverflowError);
            }
        }

        @Specialization
        static PInt doPiI(PInt left, int right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PythonObjectFactory factory,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            raiseNegativeShiftCount(inliningTarget, right < 0, raiseNode);
            return doGuardedBiI(inliningTarget, left.getValue(), right, factory, raiseNode);
        }

        static PInt doGuardedBiI(Node inliningTarget, BigInteger left, int right, PythonObjectFactory factory, PRaiseNode.Lazy raiseNode) {
            try {
                return factory.createInt(op(left, right));
            } catch (OverflowException e) {
                throw raiseNode.get(inliningTarget).raise(PythonErrorType.OverflowError);
            }
        }

        @Specialization
        static PInt doPiL(PInt left, long right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PythonObjectFactory factory,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            int rightI = (int) right;
            if (rightI == right) {
                return doPiI(left, rightI, inliningTarget, factory, raiseNode);
            } else {
                throw raiseNode.get(inliningTarget).raise(PythonErrorType.OverflowError);
            }
        }

        @Specialization(guards = {"left.isZero()", "right.isZeroOrPositive()"})
        static int doPiPiZero(@SuppressWarnings("unused") PInt left, @SuppressWarnings("unused") PInt right) {
            return 0;
        }

        @Specialization(replaces = "doPiPiZero")
        static PInt doPiPi(PInt left, PInt right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PythonObjectFactory factory,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            raiseNegativeShiftCount(inliningTarget, !right.isZeroOrPositive(), raiseNode);
            if (left.isZero()) {
                return factory.createInt(BigInteger.ZERO);
            }
            try {
                return factory.createInt(op(left.getValue(), right.intValueExact()));
            } catch (OverflowException e) {
                throw raiseNode.get(inliningTarget).raise(PythonErrorType.OverflowError);
            }
        }

        @SuppressWarnings("unused")
        @Fallback
        static PNotImplemented doGeneric(Object a, Object b) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        @TruffleBoundary
        static BigInteger op(BigInteger left, int right) throws OverflowException {
            try {
                return left.shiftLeft(right);
            } catch (ArithmeticException ex) {
                throw OverflowException.INSTANCE;
            }
        }

        private static void raiseNegativeShiftCount(Node inliningTarget, boolean cond, PRaiseNode.Lazy raiseNode) {
            if (cond) {
                throw raiseNode.get(inliningTarget).raise(PythonErrorType.ValueError, ErrorMessages.NEGATIVE_SHIFT_COUNT);
            }
        }

        @NeverDefault
        public static LShiftNode create() {
            return IntBuiltinsFactory.LShiftNodeFactory.create();
        }
    }

    @Builtin(name = J___RSHIFT__, minNumOfPositionalArgs = 2)
    @Builtin(name = J___RRSHIFT__, minNumOfPositionalArgs = 2, reverseOperation = true)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class RShiftNode extends PythonBinaryBuiltinNode {
        public abstract int executeInt(int left, int right) throws UnexpectedResultException;

        public abstract Object execute(int left, int right);

        @Specialization(guards = "right < 32")
        static int doIISmall(int left, int right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            raiseNegativeShiftCount(inliningTarget, right < 0, raiseNode);
            return left >> right;
        }

        @Specialization(replaces = "doIISmall")
        static int doII(int left, int right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            raiseNegativeShiftCount(inliningTarget, right < 0, raiseNode);
            // Note: according to JLS, if 'left' is an int, then only the 5 LSBs of 'right' are
            // considered. However, Python would consider more bits, so do the max possible shift.
            return left >> (right >= 32 ? 31 : right);
        }

        @Specialization(guards = "right < 64")
        static long doLLSmall(long left, long right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            raiseNegativeShiftCount(inliningTarget, right < 0, raiseNode);
            return left >> right;
        }

        @Specialization(replaces = "doLLSmall")
        static long doLL(long left, long right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            raiseNegativeShiftCount(inliningTarget, right < 0, raiseNode);
            // for explanation, see 'doII'
            return left >> (right >= 64 ? 63 : right);
        }

        @Specialization
        static Object doIPi(int left, PInt right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PythonObjectFactory factory,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            return doHugeShift(inliningTarget, PInt.longToBigInteger(left), right, factory, raiseNode);
        }

        @Specialization
        static Object doLPi(long left, PInt right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PythonObjectFactory factory,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            return doHugeShift(inliningTarget, PInt.longToBigInteger(left), right, factory, raiseNode);
        }

        @Specialization
        static PInt doPiI(PInt left, int right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PythonObjectFactory factory,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            raiseNegativeShiftCount(inliningTarget, right < 0, raiseNode);
            return factory.createInt(op(left.getValue(), right));
        }

        @Specialization
        static Object doPiL(PInt left, long right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PythonObjectFactory factory,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            raiseNegativeShiftCount(inliningTarget, right < 0, raiseNode);
            int rightI = (int) right;
            if (rightI == right) {
                return factory.createInt(op(left.getValue(), rightI));
            }
            // right is >= 2**31, BigInteger's bitLength is at most 2**31-1
            // therefore the result of shifting right is just the sign bit
            return left.isNegative() ? -1 : 0;
        }

        @Specialization
        static Object doPInt(PInt left, PInt right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PythonObjectFactory factory,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            return doHugeShift(inliningTarget, left.getValue(), right, factory, raiseNode);
        }

        private static void raiseNegativeShiftCount(Node inliningTarget, boolean cond, PRaiseNode.Lazy raiseNode) {
            if (cond) {
                throw raiseNode.get(inliningTarget).raise(PythonErrorType.ValueError, ErrorMessages.NEGATIVE_SHIFT_COUNT);
            }
        }

        @SuppressWarnings("unused")
        @Fallback
        static PNotImplemented doGeneric(Object a, Object b) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        private static Object doHugeShift(Node inliningTarget, BigInteger left, PInt right, PythonObjectFactory factory, PRaiseNode.Lazy raiseNode) {
            raiseNegativeShiftCount(inliningTarget, !right.isZeroOrPositive(), raiseNode);
            try {
                return factory.createInt(op(left, right.intValueExact()));
            } catch (OverflowException e) {
                // right is >= 2**31, BigInteger's bitLength is at most 2**31-1
                // therefore the result of shifting right is just the sign bit
                return left.signum() < 0 ? -1 : 0;
            }
        }

        @TruffleBoundary
        private static BigInteger op(BigInteger left, int right) {
            return left.shiftRight(right);
        }

        @NeverDefault
        public static RShiftNode create() {
            return IntBuiltinsFactory.RShiftNodeFactory.create();
        }
    }

    abstract static class BinaryBitwiseNode extends PythonBinaryBuiltinNode {

        @SuppressWarnings("unused")
        protected int op(int left, int right) {
            throw CompilerDirectives.shouldNotReachHere("should not reach here");
        }

        @SuppressWarnings("unused")
        protected long op(long left, long right) {
            throw CompilerDirectives.shouldNotReachHere("should not reach here");
        }

        @SuppressWarnings("unused")
        protected BigInteger op(BigInteger left, BigInteger right) {
            throw CompilerDirectives.shouldNotReachHere("should not reach here");
        }

        @Specialization
        int doInteger(int left, int right) {
            return op(left, right);
        }

        @Specialization
        long doInteger(long left, long right) {
            return op(left, right);
        }

        @Specialization(guards = "a.isNativePointer()")
        Object opVoidNativePtrLong(PythonNativeVoidPtr a, long b) {
            if (a.isNativePointer()) {
                return op(a.getNativePointer(), b);
            }
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        @Specialization(guards = "!a.isNativePointer()")
        Object opVoidPtrLong(VirtualFrame frame, PythonNativeVoidPtr a, long b,
                        @Bind("this") Node inliningTarget,
                        @Shared("h") @Cached PyObjectHashNode hashNode) {
            return op(hashNode.execute(frame, inliningTarget, a), b);
        }

        @Specialization(guards = {"a.isNativePointer()", "b.isNativePointer()"})
        long voidPtrsNative(PythonNativeVoidPtr a, PythonNativeVoidPtr b) {
            long ptrVal = a.getNativePointer();
            // pointers are considered unsigned
            return op(ptrVal, b.getNativePointer());
        }

        @Specialization(guards = {"a.isNativePointer()", "!b.isNativePointer()"})
        long voidPtrsANative(VirtualFrame frame, PythonNativeVoidPtr a, PythonNativeVoidPtr b,
                        @Bind("this") Node inliningTarget,
                        @Shared("h") @Cached PyObjectHashNode hashNode) {
            long ptrVal = a.getNativePointer();
            // pointers are considered unsigned
            return op(ptrVal, hashNode.execute(frame, inliningTarget, b));
        }

        @Specialization(guards = {"!a.isNativePointer()", "b.isNativePointer()"})
        long voidPtrsBNative(VirtualFrame frame, PythonNativeVoidPtr a, PythonNativeVoidPtr b,
                        @Bind("this") Node inliningTarget,
                        @Shared("h") @Cached PyObjectHashNode hashNode) {
            long ptrVal = b.getNativePointer();
            // pointers are considered unsigned
            return op(ptrVal, hashNode.execute(frame, inliningTarget, a));
        }

        @Specialization(guards = {"!a.isNativePointer()", "!b.isNativePointer()"})
        long voidPtrsManaged(VirtualFrame frame, PythonNativeVoidPtr a, PythonNativeVoidPtr b,
                        @Bind("this") Node inliningTarget,
                        @Shared("h") @Cached PyObjectHashNode hashNode) {
            return op(hashNode.execute(frame, inliningTarget, a), hashNode.execute(frame, inliningTarget, b));
        }

        @Specialization
        PInt doPInt(long left, PInt right,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createInt(op(PInt.longToBigInteger(left), right.getValue()));
        }

        @Specialization
        PInt doPInt(PInt left, long right,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createInt(op(left.getValue(), PInt.longToBigInteger(right)));
        }

        @Specialization
        PInt doPInt(PInt left, PInt right,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createInt(op(left.getValue(), right.getValue()));
        }

        @SuppressWarnings("unused")
        @Fallback
        static PNotImplemented doGeneric(Object a, Object b) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = J___RAND__, minNumOfPositionalArgs = 2)
    @Builtin(name = J___AND__, minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class AndNode extends BinaryBitwiseNode {

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
        protected final BigInteger op(BigInteger left, BigInteger right) {
            return left.and(right);
        }

        @NeverDefault
        public static AndNode create() {
            return IntBuiltinsFactory.AndNodeFactory.create();
        }
    }

    @Builtin(name = J___ROR__, minNumOfPositionalArgs = 2)
    @Builtin(name = J___OR__, minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class OrNode extends BinaryBitwiseNode {

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
        public final BigInteger op(BigInteger left, BigInteger right) {
            return left.or(right);
        }

        @NeverDefault
        public static OrNode create() {
            return IntBuiltinsFactory.OrNodeFactory.create();
        }
    }

    @Builtin(name = J___RXOR__, minNumOfPositionalArgs = 2)
    @Builtin(name = J___XOR__, minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class XorNode extends BinaryBitwiseNode {
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

        @NeverDefault
        public static XorNode create() {
            return IntBuiltinsFactory.XorNodeFactory.create();
        }
    }

    @Builtin(name = J___EQ__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class EqNode extends PythonBinaryBuiltinNode {
        @Specialization
        static boolean eqLL(long a, long b) {
            return a == b;
        }

        @Specialization
        static boolean eqPIntBoolean(PInt a, boolean b) {
            return b ? a.isOne() : a.isZero();
        }

        @Specialization
        static boolean eqBooleanPInt(boolean a, PInt b) {
            return a ? b.isOne() : b.isZero();
        }

        @Specialization(rewriteOn = OverflowException.class)
        static boolean eqPiL(PInt a, long b) throws OverflowException {
            return a.longValueExact() == b;
        }

        @Specialization
        static boolean eqPiLOvf(PInt a, long b) {
            try {
                return a.longValueExact() == b;
            } catch (OverflowException e) {
                return false;
            }
        }

        @Specialization(rewriteOn = OverflowException.class)
        static boolean eqLPi(long b, PInt a) throws OverflowException {
            return a.longValueExact() == b;
        }

        @Specialization
        static boolean eqPiLOvf(long b, PInt a) {
            try {
                return a.longValueExact() == b;
            } catch (OverflowException e) {
                return false;
            }
        }

        @Specialization
        static boolean eqPiPi(PInt a, PInt b) {
            return a.compareTo(b) == 0;
        }

        // left: PythonNativeVoidPtr

        @Specialization
        static boolean eqLongVoidPtr(VirtualFrame frame, long a, PythonNativeVoidPtr b,
                        @Bind("this") Node inliningTarget,
                        @Shared("h") @Cached PyObjectHashNode hashNode) {
            return eqVoidPtrLong(frame, b, a, inliningTarget, hashNode);
        }

        @Specialization
        static boolean eqPIntVoidPtr(PInt a, PythonNativeVoidPtr b) {
            return eqVoidPtrPInt(b, a);
        }

        @Specialization
        static boolean eqVoidPtrLong(VirtualFrame frame, PythonNativeVoidPtr a, long b,
                        @Bind("this") Node inliningTarget,
                        @Shared("h") @Cached PyObjectHashNode hashNode) {
            if (a.isNativePointer()) {
                long ptrVal = a.getNativePointer();
                // pointers are considered unsigned
                return ptrVal == b;
            }
            return hashNode.execute(frame, inliningTarget, a) == b;
        }

        @Specialization(guards = {"a.isNativePointer()", "b.isNativePointer()"})
        static boolean voidPtrsNative(PythonNativeVoidPtr a, PythonNativeVoidPtr b) {
            long ptrVal = a.getNativePointer();
            // pointers are considered unsigned
            return ptrVal == b.getNativePointer();
        }

        @Specialization(guards = {"a.isNativePointer()", "!b.isNativePointer()"})
        static boolean voidPtrsANative(VirtualFrame frame, PythonNativeVoidPtr a, PythonNativeVoidPtr b,
                        @Bind("this") Node inliningTarget,
                        @Shared("h") @Cached PyObjectHashNode hashNode) {
            long ptrVal = a.getNativePointer();
            // pointers are considered unsigned
            return ptrVal == hashNode.execute(frame, inliningTarget, b);
        }

        @Specialization(guards = {"!a.isNativePointer()", "b.isNativePointer()"})
        static boolean voidPtrsBNative(VirtualFrame frame, PythonNativeVoidPtr a, PythonNativeVoidPtr b,
                        @Bind("this") Node inliningTarget,
                        @Shared("h") @Cached PyObjectHashNode hashNode) {
            long ptrVal = b.getNativePointer();
            // pointers are considered unsigned
            return ptrVal == hashNode.execute(frame, inliningTarget, a);
        }

        @Specialization(guards = {"!a.isNativePointer()", "!b.isNativePointer()"})
        static boolean voidPtrsManaged(VirtualFrame frame, PythonNativeVoidPtr a, PythonNativeVoidPtr b,
                        @Bind("this") Node inliningTarget,
                        @Shared("h") @Cached PyObjectHashNode hashNode) {
            return hashNode.execute(frame, inliningTarget, a) == hashNode.execute(frame, inliningTarget, b);
        }

        @Specialization
        @TruffleBoundary
        static boolean eqVoidPtrPInt(PythonNativeVoidPtr a, PInt b) {
            if (a.isNativePointer()) {
                long ptrVal = a.getNativePointer();
                if (ptrVal < 0) {
                    // pointers are considered unsigned
                    BigInteger bi = PInt.longToBigInteger(ptrVal).add(BigInteger.ONE.shiftLeft(64));
                    return bi.equals(b.getValue());
                }
                return PInt.longToBigInteger(ptrVal).equals(b.getValue());
            }
            try {
                return PyObjectHashNode.executeUncached(a) == b.longValueExact();
            } catch (OverflowException e) {
                return false;
            }
        }

        @Fallback
        @SuppressWarnings("unused")
        static PNotImplemented eq(Object a, Object b) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = J___NE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class NeNode extends PythonBinaryBuiltinNode {
        @Specialization
        static boolean eqLL(long a, long b) {
            return a != b;
        }

        @Specialization(rewriteOn = OverflowException.class)
        static boolean eqPiL(PInt a, long b) throws OverflowException {
            return a.longValueExact() != b;
        }

        @Specialization(replaces = "eqPiL")
        static boolean eqPiLOvf(PInt a, long b) {
            try {
                return a.longValueExact() != b;
            } catch (OverflowException e) {
                return true;
            }
        }

        @Specialization(rewriteOn = OverflowException.class)
        static boolean eqLPi(long b, PInt a) throws OverflowException {
            return a.longValueExact() != b;
        }

        @Specialization(replaces = "eqLPi")
        static boolean eqLPiOvf(long b, PInt a) {
            try {
                return a.longValueExact() != b;
            } catch (OverflowException e) {
                return true;
            }
        }

        @Specialization
        static boolean eqPiPi(PInt a, PInt b) {
            return a.compareTo(b) != 0;
        }

        @SuppressWarnings("unused")
        @Fallback
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
        static boolean doII(int left, int right) {
            return left < right;
        }

        @Specialization
        static boolean doLL(long left, long right) {
            return left < right;
        }

        @Specialization
        static boolean doLP(long left, PInt right) {
            try {
                return left < right.longValueExact();
            } catch (OverflowException e) {
                return right.doubleValue() > 0;
            }
        }

        @Specialization
        static boolean doPL(PInt left, long right) {
            try {
                return left.longValueExact() < right;
            } catch (OverflowException e) {
                return left.doubleValue() < 0;
            }
        }

        @Specialization
        static boolean doPP(PInt left, PInt right) {
            return left.compareTo(right) < 0;
        }

        @Specialization(guards = "isFloatSubtype(frame, inliningTarget, y, getClass, isSubtype)")
        static boolean doDN(VirtualFrame frame, long x, PythonAbstractNativeObject y,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared @Cached GetPythonObjectClassNode getClass,
                        @SuppressWarnings("unused") @Shared @Cached IsSubtypeNode isSubtype,
                        @Shared @Cached FromNativeSubclassNode nativeRight) {
            return x < nativeRight.execute(frame, y);
        }

        @Specialization(guards = {
                        "isFloatSubtype(frame, inliningTarget, x, getClass, isSubtype)",
                        "isFloatSubtype(frame, inliningTarget, y, getClass, isSubtype)"})
        static boolean doDN(VirtualFrame frame, PythonAbstractNativeObject x, PythonAbstractNativeObject y,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared @Cached GetPythonObjectClassNode getClass,
                        @SuppressWarnings("unused") @Shared @Cached IsSubtypeNode isSubtype,
                        @Shared @Cached FromNativeSubclassNode nativeLeft,
                        @Shared @Cached FromNativeSubclassNode nativeRight) {
            return nativeLeft.execute(frame, x) < nativeRight.execute(frame, y);
        }

        @Specialization(guards = "isFloatSubtype(frame, inliningTarget, x, getClass, isSubtype)")
        static boolean doDN(VirtualFrame frame, PythonAbstractNativeObject x, double y,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared @Cached GetPythonObjectClassNode getClass,
                        @SuppressWarnings("unused") @Shared @Cached IsSubtypeNode isSubtype,
                        @Shared @Cached FromNativeSubclassNode nativeLeft) {
            return nativeLeft.execute(frame, x) < y;
        }

        @Specialization
        static boolean doVoidPtr(PythonNativeVoidPtr x, long y,
                        @Cached CExtNodes.PointerCompareNode ltNode) {
            return ltNode.execute(T___LT__, x, y);
        }

        @SuppressWarnings("unused")
        @Fallback
        static PNotImplemented doGeneric(Object a, Object b) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = J___LE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class LeNode extends PythonBinaryBuiltinNode {
        @Specialization
        static boolean doII(int left, int right) {
            return left <= right;
        }

        @Specialization
        static boolean doLL(long left, long right) {
            return left <= right;
        }

        @Specialization
        static boolean doLP(long left, PInt right) {
            try {
                return left <= right.longValueExact();
            } catch (OverflowException e) {
                return right.doubleValue() > 0;
            }
        }

        @Specialization
        static boolean doPL(PInt left, long right) {
            try {
                return left.longValueExact() <= right;
            } catch (OverflowException e) {
                return left.doubleValue() < 0;
            }
        }

        @Specialization
        static boolean doPP(PInt left, PInt right) {
            return left.compareTo(right) <= 0;
        }

        @SuppressWarnings("unused")
        @Fallback
        static PNotImplemented doGeneric(Object a, Object b) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = J___GT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class GtNode extends PythonBinaryBuiltinNode {

        @Specialization
        static boolean doII(int left, int right) {
            return left > right;
        }

        @Specialization
        static boolean doLL(long left, long right) {
            return left > right;
        }

        @Specialization
        static boolean doLP(long left, PInt right) {
            try {
                return left > right.longValueExact();
            } catch (OverflowException e) {
                return right.doubleValue() < 0;
            }
        }

        @Specialization
        static boolean doPL(PInt left, long right) {
            try {
                return left.longValueExact() > right;
            } catch (OverflowException e) {
                return left.doubleValue() > 0;
            }
        }

        @Specialization
        static boolean doPP(PInt left, PInt right) {
            return left.compareTo(right) > 0;
        }

        @SuppressWarnings("unused")
        @Fallback
        static PNotImplemented doGeneric(Object a, Object b) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = J___GE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class GeNode extends PythonBinaryBuiltinNode {

        @Specialization
        static boolean doII(int left, int right) {
            return left >= right;
        }

        @Specialization
        static boolean doLL(long left, long right) {
            return left >= right;
        }

        @Specialization
        static boolean doLP(long left, PInt right) {
            try {
                return left >= right.longValueExact();
            } catch (OverflowException e) {
                return right.doubleValue() < 0;
            }
        }

        @Specialization
        static boolean doPL(PInt left, long right) {
            try {
                return left.longValueExact() >= right;
            } catch (OverflowException e) {
                return left.doubleValue() > 0;
            }
        }

        @Specialization
        static boolean doPP(PInt left, PInt right) {
            return left.compareTo(right) >= 0;
        }

        @SuppressWarnings("unused")
        @Fallback
        static PNotImplemented doGeneric(Object a, Object b) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    // to_bytes
    @Builtin(name = "to_bytes", minNumOfPositionalArgs = 3, parameterNames = {"$self", "length", "byteorder"}, keywordOnlyNames = {"signed"})
    @ArgumentClinic(name = "length", conversion = ClinicConversion.Index)
    @ArgumentClinic(name = "byteorder", conversion = ClinicConversion.TString)
    @ArgumentClinic(name = "signed", conversion = ClinicConversion.Boolean, defaultValue = "false")
    @GenerateNodeFactory
    @SuppressWarnings("unused")
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class ToBytesNode extends PythonClinicBuiltinNode {

        @TruffleBoundary
        private static boolean isBigEndian(Node raisingNode, TruffleString order) {
            if (order.equalsUncached(T_BIG, TS_ENCODING)) {
                return true;
            }
            if (order.equalsUncached(T_LITTLE, TS_ENCODING)) {
                return false;
            }
            throw PRaiseNode.raiseUncached(raisingNode, PythonErrorType.ValueError, ErrorMessages.BYTEORDER_MUST_BE_LITTLE_OR_BIG);
        }

        @Specialization
        static PBytes fromLong(long self, int byteCount, TruffleString byteorder, boolean signed,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached InlinedConditionProfile negativeByteCountProfile,
                        @Exclusive @Cached InlinedConditionProfile negativeNumberProfile,
                        @Exclusive @Cached InlinedConditionProfile overflowProfile,
                        @Shared @Cached PythonObjectFactory factory,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            if (negativeByteCountProfile.profile(inliningTarget, byteCount < 0)) {
                throw raiseNode.get(inliningTarget).raise(PythonErrorType.ValueError, ErrorMessages.MESSAGE_LENGTH_ARGUMENT);
            }
            if (self < 0) {
                if (negativeNumberProfile.profile(inliningTarget, !signed)) {
                    throw raiseNode.get(inliningTarget).raise(PythonErrorType.OverflowError, ErrorMessages.MESSAGE_CONVERT_NEGATIVE);
                }
            }
            return factory.createBytes(fromLong(self, byteCount, isBigEndian(inliningTarget, byteorder), signed,
                            inliningTarget, overflowProfile, raiseNode));
        }

        public static byte[] fromLong(long self, int byteCount, boolean isBigEndian, boolean signed,
                        Node inliningTarget,
                        InlinedConditionProfile overflowProfile,
                        PRaiseNode.Lazy raiseNode) {
            byte signByte = 0;
            if (self < 0) {
                assert signed : ErrorMessages.MESSAGE_CONVERT_NEGATIVE;
                signByte = -1;
            }
            int index;
            int delta;
            if (isBigEndian) {
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

            if (overflowProfile.profile(inliningTarget, !signed && number != 0 || (signed && bytes.length == 1 && bytes[0] != self) || (byteCount == 0 && self != 0))) {
                throw raiseNode.get(inliningTarget).raise(PythonErrorType.OverflowError, ErrorMessages.MESSAGE_INT_TO_BIG);
            }

            if (signed) {
                while (0 <= index && index <= (byteCount - 1)) {
                    bytes[index] = signByte;
                    index += delta;
                }
            }
            return bytes;
        }

        @TruffleBoundary
        private static byte getSignByte(BigInteger value, boolean signed, Node raisingNode) {
            if (value.compareTo(BigInteger.ZERO) < 0) {
                if (!signed) {
                    throw PRaiseNode.raiseUncached(raisingNode, PythonErrorType.OverflowError, ErrorMessages.MESSAGE_CONVERT_NEGATIVE);
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
        static PBytes fromPIntInt(PInt self, int byteCount, TruffleString byteorder, boolean signed,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached InlinedConditionProfile negativeByteCountProfile,
                        @Exclusive @Cached InlinedConditionProfile overflowProfile,
                        @Shared @Cached PythonObjectFactory factory,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            if (negativeByteCountProfile.profile(inliningTarget, byteCount < 0)) {
                throw raiseNode.get(inliningTarget).raise(PythonErrorType.ValueError, ErrorMessages.MESSAGE_LENGTH_ARGUMENT);
            }
            return factory.createBytes(fromBigInteger(self, byteCount, isBigEndian(inliningTarget, byteorder), signed,
                            inliningTarget, overflowProfile, raiseNode));
        }

        public static byte[] fromBigInteger(PInt self, int byteCount, boolean isBigEndian, boolean signed,
                        Node inliningTarget,
                        InlinedConditionProfile overflowProfile,
                        PRaiseNode.Lazy raiseNode) {
            BigInteger value = self.getValue();
            byte signByte = getSignByte(value, signed, inliningTarget);
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
                if (overflowProfile.profile(inliningTarget, len > byteCount)) {
                    // the corrected len is still bigger then we need.
                    throw raiseNode.get(inliningTarget).raise(PythonErrorType.OverflowError, ErrorMessages.MESSAGE_INT_TO_BIG);
                }
                // the array starts with sign bytes and has to be truncated to the requested
                // size
                byte[] tmp = bytes;
                bytes = new byte[len];
                PythonUtils.arraycopy(tmp, startIndex, bytes, 0, len);
            }

            if (isBigEndian) {
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
                    return resultBytes;
                } else {
                    return bytes;
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
                return resultBytes;
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return IntBuiltinsClinicProviders.ToBytesNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "from_bytes", minNumOfPositionalArgs = 3, parameterNames = {"cls", "bytes", "byteorder"}, varArgsMarker = true, keywordOnlyNames = {"signed"}, isClassmethod = true)
    @ArgumentClinic(name = "byteorder", conversion = ClinicConversion.TString)
    @ArgumentClinic(name = "signed", conversion = ClinicConversion.Boolean, defaultValue = "false")
    @ImportStatic(SpecialMethodNames.class)
    @GenerateNodeFactory
    public abstract static class FromBytesNode extends PythonClinicBuiltinNode {

        @Child private LookupAndCallVarargsNode constructNode;

        private static byte[] littleToBig(byte[] bytes) {
            // PInt uses Java BigInteger which are big-endian
            byte[] bigEndianBytes = new byte[bytes.length];
            for (int i = 0; i < bytes.length; i++) {
                bigEndianBytes[bytes.length - i - 1] = bytes[i];
            }
            return bigEndianBytes;
        }

        @TruffleBoundary
        public static BigInteger createBigInteger(byte[] bytes, boolean isBigEndian, boolean signed) {
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
        private static boolean isBigEndian(Node raisingNode, TruffleString order) {
            if (order.equalsUncached(T_BIG, TS_ENCODING)) {
                return true;
            }
            if (order.equalsUncached(T_LITTLE, TS_ENCODING)) {
                return false;
            }
            throw PRaiseNode.raiseUncached(raisingNode, PythonErrorType.ValueError, ErrorMessages.BYTEORDER_MUST_BE_LITTLE_OR_BIG);
        }

        private Object createIntObject(Object cl, BigInteger number, PythonObjectFactory factory) {
            PythonBuiltinClassType type = null;
            if (cl instanceof PythonBuiltinClass) {
                type = ((PythonBuiltinClass) cl).getType();
            } else if (cl instanceof PythonBuiltinClassType) {
                type = (PythonBuiltinClassType) cl;
            }
            if (type == PythonBuiltinClassType.PInt) {
                return factory.createInt(number);
            }
            if (constructNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                constructNode = insert(LookupAndCallVarargsNode.create(SpecialMethodNames.T___CALL__));
            }
            return constructNode.execute(null, cl, new Object[]{cl, factory.createInt(number)});
        }

        private Object compute(Object cl, byte[] bytes, TruffleString byteorder, boolean signed, PythonObjectFactory factory) {
            BigInteger bi = createBigInteger(bytes, isBigEndian(this, byteorder), signed);
            return createIntObject(cl, bi, factory);
        }

        @Specialization
        Object fromObject(VirtualFrame frame, Object cl, Object object, TruffleString byteorder, boolean signed,
                        @Bind("this") Node inliningTarget,
                        @Cached("create(Bytes)") LookupAndCallUnaryNode callBytes,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib,
                        @Cached BytesNodes.BytesFromObject bytesFromObject,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            byte[] bytes;
            Object bytesObj = callBytes.executeObject(frame, object);
            if (bytesObj != PNone.NO_VALUE) {
                if (!(bytesObj instanceof PBytes)) {
                    throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.RETURNED_NONBYTES, T___BYTES__);
                }
                bytes = bufferLib.getCopiedByteArray(bytesObj);
            } else {
                bytes = bytesFromObject.execute(frame, object);
            }
            return compute(cl, bytes, byteorder, signed, factory);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return IntBuiltinsClinicProviders.FromBytesNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = J___BOOL__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class BoolNode extends PythonBuiltinNode {
        @Specialization
        static boolean toBoolean(boolean self) {
            return self;
        }

        @Specialization
        static boolean toBoolean(int self) {
            return self != 0;
        }

        @Specialization
        static boolean toBoolean(long self) {
            return self != 0;
        }

        @Specialization
        static boolean toBoolean(PInt self) {
            return !self.isZero();
        }

        @Specialization
        static boolean toBoolean(PythonNativeVoidPtr self,
                        @CachedLibrary(limit = "1") InteropLibrary lib) {
            return !lib.isNull(self.getPointerObject());
        }
    }

    @Builtin(name = J___STR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class StrNode extends PythonBuiltinNode {

        @Specialization
        static TruffleString doL(long self,
                        @Shared("fromLong") @Cached TruffleString.FromLongNode fromLongNode) {
            return fromLongNode.execute(self, TS_ENCODING, false);
        }

        @Specialization
        static TruffleString doPInt(PInt self,
                        @Bind("this") Node inliningTarget,
                        @Cached TruffleString.FromJavaStringNode fromJavaStringNode,
                        @Cached InlinedIntValueProfile maxDigitsProfile,
                        @Cached InlinedIntValueProfile maxDigitsBitLengthProfile,
                        @Cached PRaiseNode.Lazy raiseNode) {
            PythonContext context = PythonContext.get(inliningTarget);
            int intMaxStrDigits = maxDigitsProfile.profile(inliningTarget, context.getIntMaxStrDigits());
            /*
             * Approximate pre-check for the number of digits. It's done as a prevention for DoS
             * attacks, because CPython's conversion algorithm has bad complexity. Java's is
             * probably better, but we need to be compatible. CPython has a different pre-check
             * based on internals of their representation.
             *
             * Their check in longobject.c:long_to_decimal_string_internal uses their internal
             * length, which is the number of 30 bit chunks, in a special formula.
             *
             * Our check compares the bit length reported by BigInteger against a precomputed bit
             * length of a smallest positive number that is over the limit.
             */
            if (intMaxStrDigits > 0) {
                int bitLength = positiveBitLength(self);
                if (bitLength >= maxDigitsBitLengthProfile.profile(inliningTarget, context.getMinIntBitLengthOverLimit())) {
                    throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.EXCEEDS_THE_LIMIT_FOR_INTEGER_STRING_CONVERSION, intMaxStrDigits);
                }
            }
            String value = self.toString();
            /*
             * Post check for the exact value. It does nothing for DoS prevention anymore, it's just
             * for consistency.
             */
            if (intMaxStrDigits > 0) {
                int digits = self.isNegative() ? value.length() - 1 : value.length();
                if (digits > intMaxStrDigits) {
                    throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.EXCEEDS_THE_LIMIT_FOR_INTEGER_STRING_CONVERSION);
                }
            }
            return fromJavaStringNode.execute(value, TS_ENCODING);
        }

        @TruffleBoundary
        private static int positiveBitLength(PInt self) {
            return self.abs().bitLength();
        }

        @Specialization
        static TruffleString doNativeVoidPtr(VirtualFrame frame, PythonNativeVoidPtr self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectHashNode hashNode,
                        @Shared("fromLong") @Cached TruffleString.FromLongNode fromLongNode) {
            return doL(hashNode.execute(frame, inliningTarget, self), fromLongNode);
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

        // We cannot use PythonArithmeticTypes, because for empty format string we need to call the
        // boolean's __str__ and not int's __str__ (that specialization is inherited)
        @Specialization(guards = "!formatString.isEmpty()")
        static TruffleString formatB(boolean self, TruffleString formatString,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            return formatI(self ? 1 : 0, formatString, inliningTarget, raiseNode);
        }

        @Specialization(guards = "!formatString.isEmpty()")
        static TruffleString formatI(int self, TruffleString formatString,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            Spec spec = getSpec(formatString, inliningTarget);
            if (isDoubleSpec(spec)) {
                return formatDouble(spec, self, inliningTarget);
            }
            validateIntegerSpec(inliningTarget, raiseNode, spec);
            return formatInt(self, spec, inliningTarget);
        }

        @Specialization(guards = "!formatString.isEmpty()")
        static TruffleString formatL(VirtualFrame frame, long self, TruffleString formatString,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PyNumberFloatNode floatNode,
                        @Cached PythonObjectFactory factory,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            return formatPI(frame, factory.createInt(self), formatString, inliningTarget, floatNode, raiseNode);
        }

        @Specialization(guards = "!formatString.isEmpty()")
        static TruffleString formatPI(VirtualFrame frame, PInt self, TruffleString formatString,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PyNumberFloatNode floatNode,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            Spec spec = getSpec(formatString, inliningTarget);
            if (isDoubleSpec(spec)) {
                // lazy init of floatNode serves as branch profile
                double doubleVal = asDouble(frame, inliningTarget, floatNode, self);
                return formatDouble(spec, doubleVal, inliningTarget);
            }
            validateIntegerSpec(inliningTarget, raiseNode, spec);
            return formatPInt(self, spec, inliningTarget);
        }

        private static double asDouble(VirtualFrame frame, Node inliningTarget, PyNumberFloatNode floatNode, Object self) {
            // This should have the semantics of PyNumber_Float
            return floatNode.execute(frame, inliningTarget, self);
        }

        private static Spec getSpec(TruffleString formatString, Node raisingNode) {
            return InternalFormat.fromText(formatString, 'd', '>', raisingNode);
        }

        private static boolean isDoubleSpec(Spec spec) {
            return spec.type == 'e' || spec.type == 'E' || spec.type == 'f' || //
                            spec.type == 'F' || spec.type == 'g' || //
                            spec.type == 'G' || spec.type == '%';
        }

        @TruffleBoundary
        private static TruffleString formatDouble(Spec spec, double value, Node raisingNode) {
            FloatFormatter formatter = new FloatFormatter(spec, raisingNode);
            formatter.format(value);
            return formatter.pad().getResult();
        }

        @TruffleBoundary
        private static TruffleString formatInt(int self, Spec spec, Node raisingNode) {
            IntegerFormatter formatter = new IntegerFormatter(spec, raisingNode);
            formatter.format(self);
            return formatter.pad().getResult();
        }

        @TruffleBoundary
        private static TruffleString formatPInt(PInt self, Spec spec, Node raisingNode) {
            IntegerFormatter formatter = new IntegerFormatter(spec, raisingNode);
            formatter.format(self.getValue());
            return formatter.pad().getResult();
        }

        private static void validateIntegerSpec(Node inliningTarget, PRaiseNode.Lazy raiseNode, Spec spec) {
            if (Spec.specified(spec.precision)) {
                throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.PRECISION_NOT_ALLOWED_FOR_INT);
            }
            if (spec.type == 'c') {
                if (Spec.specified(spec.sign)) {
                    throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.SIGN_NOT_ALLOWED_WITH_C_FOR_INT);
                } else if (spec.alternate) {
                    throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.ALTERNATE_NOT_ALLOWED_WITH_C_FOR_INT);
                }
            }
        }
    }

    @Builtin(name = J___HASH__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class HashNode extends PythonUnaryBuiltinNode {

        @Specialization
        static long hash(int self) {
            return PyObjectHashNode.hash(self);
        }

        @Specialization
        static long hash(long self) {
            return PyObjectHashNode.hash(self);
        }

        @Specialization
        static long hash(PInt self) {
            return self.hash();
        }

        @Specialization(limit = "1")
        static long hash(PythonNativeVoidPtr self,
                        @CachedLibrary("self.getPointerObject()") InteropLibrary lib) {
            Object object = self.getPointerObject();
            if (lib.hasIdentity(object)) {
                try {
                    return lib.identityHashCode(object);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
            }
            return hashCodeBoundary(object);
        }

        @TruffleBoundary
        private static long hashCodeBoundary(Object object) {
            return object.hashCode();
        }
    }

    @Builtin(name = "bit_count", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class BitCountNode extends PythonBuiltinNode {
        @Specialization
        static int bitCount(int i) {
            return Integer.bitCount(Math.abs(i));
        }

        @Specialization
        static int bitCount(long l) {
            return Long.bitCount(Math.abs(l));
        }

        @Specialization
        @TruffleBoundary
        static int bitCount(PInt i) {
            return i.getValue().abs().bitCount();
        }
    }

    @Builtin(name = "bit_length", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class BitLengthNode extends PythonBuiltinNode {
        @Specialization
        static int bitLength(int argument) {
            return Integer.SIZE - Integer.numberOfLeadingZeros(Math.abs(argument));
        }

        @Specialization
        static int bitLength(long argument) {
            return Long.SIZE - Long.numberOfLeadingZeros(Math.abs(argument));
        }

        @Specialization
        @TruffleBoundary
        static int bitLength(PInt argument) {
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
        static int get(@SuppressWarnings("unused") Object self) {
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
        static int get(@SuppressWarnings("unused") Object self) {
            return 1;
        }
    }

    @GenerateNodeFactory
    @Builtin(name = "as_integer_ratio", minNumOfPositionalArgs = 1, doc = "Return integer ratio.")
    abstract static class AsIntegerRatioNode extends PythonBuiltinNode {
        @Specialization
        static Object get(VirtualFrame frame, Object self, @Cached IntNode intNode,
                        @Cached PythonObjectFactory factory) {
            return factory.createTuple(new Object[]{intNode.execute(frame, self), 1});
        }
    }

    @GenerateNodeFactory
    @Builtin(name = J___TRUNC__, minNumOfPositionalArgs = 1, doc = "Truncating an Integral returns itself.")
    abstract static class TruncNode extends IntNode {

    }

    @Builtin(name = J___INT__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class IntNode extends PythonUnaryBuiltinNode {

        @Specialization
        static int doB(boolean self) {
            return self ? 1 : 0;
        }

        @Specialization
        static int doI(int self) {
            return self;
        }

        @Specialization
        static long doL(long self) {
            return self;
        }

        @Specialization(guards = "cannotBeOverridden(self, inliningTarget, getClassNode)")
        static PInt doPInt(PInt self,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared @Cached GetClassNode getClassNode) {
            return self;
        }

        @Specialization(guards = "!cannotBeOverridden(self, inliningTarget, getClassNode)", rewriteOn = OverflowException.class)
        static int doPIntOverridenNarrowInt(PInt self,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared @Cached GetClassNode getClassNode) throws OverflowException {
            return self.intValueExact();
        }

        @Specialization(guards = "!cannotBeOverridden(self, inliningTarget, getClassNode)", replaces = "doPIntOverridenNarrowInt", rewriteOn = OverflowException.class)
        static long doPIntOverridenNarrowLong(PInt self,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared @Cached GetClassNode getClassNode) throws OverflowException {
            return self.longValueExact();
        }

        @Specialization(guards = "!cannotBeOverridden(self, inliningTarget, getClassNode)", replaces = "doPIntOverridenNarrowLong")
        static PInt doPIntOverriden(PInt self,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared @Cached GetClassNode getClassNode,
                        @Cached PythonObjectFactory factory) {
            return factory.createInt(self.getValue());
        }

        @Specialization
        static PythonNativeVoidPtr doL(PythonNativeVoidPtr self) {
            return self;
        }
    }

    @Builtin(name = J___INDEX__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IndexNode extends IntNode {
    }

    @Builtin(name = J___GETNEWARGS__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class GetNewArgsNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doI(int self,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createTuple(new Object[]{factory.createInt(self)});
        }

        @Specialization
        static Object doL(long self,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createTuple(new Object[]{factory.createInt(self)});
        }

        @Specialization
        static Object getPI(PInt self,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createTuple(new Object[]{factory.createInt(self.getValue())});
        }
    }

    @Builtin(name = J___FLOAT__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class FloatNode extends PythonUnaryBuiltinNode {
        @Specialization
        static double doBoolean(boolean self) {
            return self ? 1.0 : 0.0;
        }

        @Specialization
        static double doInt(int self) {
            return self;
        }

        @Specialization
        static double doLong(long self) {
            return self;
        }

        @Specialization
        double doPInt(PInt self) {
            return self.doubleValueWithOverflow(this);
        }

        @Fallback
        static PNotImplemented doGeneric(@SuppressWarnings("unused") Object self) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }
}
