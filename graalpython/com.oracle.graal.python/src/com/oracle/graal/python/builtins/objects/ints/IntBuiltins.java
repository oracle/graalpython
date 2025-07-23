/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.DeprecationWarning;
import static com.oracle.graal.python.nodes.ErrorMessages.BITWISE_INVERSION_OF_THE_UNDERLYING_INT;
import static com.oracle.graal.python.nodes.BuiltinNames.J_INT;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CEIL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___FLOOR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___FORMAT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GETNEWARGS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ROUND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___TRUNC__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___BYTES__;
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

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ArgumentClinic.ClinicConversion;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.MathGuards;
import com.oracle.graal.python.builtins.modules.WarningsModuleBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes.BytesFromObject;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeVoidPtr;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.FromNativeSubclassNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PointerCompareNode;
import com.oracle.graal.python.builtins.objects.common.FormatNodeBase;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.ints.IntBuiltinsClinicProviders.FormatNodeClinicProviderGen;
import com.oracle.graal.python.builtins.objects.ints.IntBuiltinsClinicProviders.FromBytesNodeClinicProviderGen;
import com.oracle.graal.python.builtins.objects.ints.IntBuiltinsClinicProviders.ToBytesNodeClinicProviderGen;
import com.oracle.graal.python.builtins.objects.ints.IntBuiltinsFactory.AddNodeFactory;
import com.oracle.graal.python.builtins.objects.ints.IntBuiltinsFactory.AndNodeFactory;
import com.oracle.graal.python.builtins.objects.ints.IntBuiltinsFactory.FloorDivNodeFactory;
import com.oracle.graal.python.builtins.objects.ints.IntBuiltinsFactory.LShiftNodeFactory;
import com.oracle.graal.python.builtins.objects.ints.IntBuiltinsFactory.ModNodeFactory;
import com.oracle.graal.python.builtins.objects.ints.IntBuiltinsFactory.MulNodeFactory;
import com.oracle.graal.python.builtins.objects.ints.IntBuiltinsFactory.NegNodeFactory;
import com.oracle.graal.python.builtins.objects.ints.IntBuiltinsFactory.OrNodeFactory;
import com.oracle.graal.python.builtins.objects.ints.IntBuiltinsFactory.PowNodeFactory;
import com.oracle.graal.python.builtins.objects.ints.IntBuiltinsFactory.RShiftNodeFactory;
import com.oracle.graal.python.builtins.objects.ints.IntBuiltinsFactory.SubNodeFactory;
import com.oracle.graal.python.builtins.objects.ints.IntBuiltinsFactory.TrueDivNodeFactory;
import com.oracle.graal.python.builtins.objects.ints.IntBuiltinsFactory.XorNodeFactory;
import com.oracle.graal.python.builtins.objects.ints.IntNodes.PyLongFromByteArray;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryOp.BinaryOpBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotHashFun.HashBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotInquiry.NbBoolBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotRichCompare.RichCmpBuiltinNode;
import com.oracle.graal.python.lib.PyLongCheckNode;
import com.oracle.graal.python.lib.PyLongCopy;
import com.oracle.graal.python.lib.PyLongFromUnicodeObject;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyNumberFloatNode;
import com.oracle.graal.python.lib.PyNumberLongNode;
import com.oracle.graal.python.lib.PyObjectHashNode;
import com.oracle.graal.python.lib.PyUnicodeCheckNode;
import com.oracle.graal.python.lib.RichCmpOp;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinClassExactProfile;
import com.oracle.graal.python.nodes.object.GetClassNode.GetPythonObjectClassNode;
import com.oracle.graal.python.nodes.truffle.PythonIntegerTypes;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.formatting.FloatFormatter;
import com.oracle.graal.python.runtime.formatting.IntegerFormatter;
import com.oracle.graal.python.runtime.formatting.InternalFormat;
import com.oracle.graal.python.runtime.formatting.InternalFormat.Spec;
import com.oracle.graal.python.runtime.object.PFactory;
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
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
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
import com.oracle.truffle.api.strings.TruffleString.EqualNode;
import com.oracle.truffle.api.strings.TruffleString.FromJavaStringNode;
import com.oracle.truffle.api.strings.TruffleString.FromLongNode;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PInt)
public final class IntBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = IntBuiltinsSlotsGen.SLOTS;

    private static void raiseDivisionByZero(Node inliningTarget, boolean cond, PRaiseNode raiseNode) {
        if (cond) {
            raiseDivisionByZero(inliningTarget, raiseNode);
        }
    }

    @InliningCutoff
    private static void raiseDivisionByZero(Node inliningTarget, PRaiseNode raiseNode) {
        throw raiseNode.raise(inliningTarget, PythonErrorType.ZeroDivisionError, ErrorMessages.S_DIVISION_OR_MODULO_BY_ZERO, "integer");
    }

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return IntBuiltinsFactory.getFactories();
    }

    // int(x=0)
    // int(x, base=10)
    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = J_INT, minNumOfPositionalArgs = 1, parameterNames = {"cls", "x", "base"}, numOfPositionalOnlyArgs = 2)
    @GenerateNodeFactory
    public abstract static class IntNewNode extends PythonTernaryBuiltinNode {

        @Specialization
        static Object doGeneric(VirtualFrame frame, Object cls, Object x, Object baseObj,
                        @Bind Node inliningTarget,
                        @Cached IntNodeInnerNode innerNode,
                        @Cached IsBuiltinClassExactProfile isPrimitiveIntProfile,
                        @Cached CreateIntSubclassNode createIntSubclassNode) {
            Object result = innerNode.execute(frame, inliningTarget, x, baseObj);
            if (isPrimitiveIntProfile.profileClass(inliningTarget, cls, PythonBuiltinClassType.PInt)) {
                return result;
            } else {
                return createIntSubclassNode.execute(inliningTarget, cls, result);
            }
        }

        @GenerateInline
        @GenerateCached(false)
        abstract static class CreateIntSubclassNode extends Node {
            public abstract Object execute(Node inliningTarget, Object cls, Object intObj);

            @Specialization
            static Object doSubclass(Object cls, int value,
                            @Bind PythonLanguage language,
                            @Shared @Cached TypeNodes.GetInstanceShape getInstanceShape) {
                return PFactory.createInt(language, cls, getInstanceShape.execute(cls), value);
            }

            @Specialization
            static Object doSubclass(Object cls, long value,
                            @Bind PythonLanguage language,
                            @Shared @Cached TypeNodes.GetInstanceShape getInstanceShape) {
                return PFactory.createInt(language, cls, getInstanceShape.execute(cls), value);
            }

            @Specialization
            static Object doSubclass(Object cls, boolean value,
                            @Bind PythonLanguage language,
                            @Shared @Cached TypeNodes.GetInstanceShape getInstanceShape) {
                return PFactory.createInt(language, cls, getInstanceShape.execute(cls), PInt.intValue(value));
            }

            @Specialization
            static Object doSubclass(Object cls, PInt value,
                            @Bind PythonLanguage language,
                            @Shared @Cached TypeNodes.GetInstanceShape getInstanceShape) {
                return PFactory.createInt(language, cls, getInstanceShape.execute(cls), value.getValue());
            }
        }

        @GenerateInline
        @GenerateCached(false)
        @ImportStatic(PGuards.class)
        abstract static class IntNodeInnerNode extends Node {
            public abstract Object execute(VirtualFrame frame, Node inliningTarget, Object x, Object base);

            @Specialization(guards = "isNoValue(baseObj)")
            static Object doNoBase(VirtualFrame frame, Node inliningTarget, Object x, @SuppressWarnings("unused") Object baseObj,
                            @Cached @Exclusive InlinedBranchProfile noX,
                            @Cached PyNumberLongNode pyNumberLongNode) {
                if (x == PNone.NO_VALUE) {
                    noX.enter(inliningTarget);
                    return 0;
                } else {
                    return pyNumberLongNode.execute(frame, inliningTarget, x);
                }
            }

            @Fallback
            @InliningCutoff
            static Object doWithBase(VirtualFrame frame, Node inliningTarget, Object x, Object baseObj,
                            @Cached @Exclusive InlinedBranchProfile missingArgument,
                            @Cached @Exclusive InlinedBranchProfile wrongBase,
                            @Cached @Exclusive InlinedBranchProfile cannotConvert,
                            @Cached PyNumberAsSizeNode asSizeNode,
                            @Cached PyUnicodeCheckNode unicodeCheckNode,
                            @Cached PyLongFromUnicodeObject longFromUnicode,
                            @Cached BytesNodes.BytesLikeCheck bytesLikeCheck,
                            @Cached PyNumberLongNode.LongFromBufferNode fromBufferNode,
                            @Cached PRaiseNode raiseNode) {
                if (x == PNone.NO_VALUE) {
                    missingArgument.enter(inliningTarget);
                    throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.INT_MISSING_STRING_ARGUMENT);
                }
                int base = asSizeNode.executeLossy(frame, inliningTarget, baseObj);
                if ((base != 0 && base < 2) || base > 36) {
                    wrongBase.enter(inliningTarget);
                    throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.INT_BASE_MUST_BE_2_AND_36_OR_0);
                }
                if (unicodeCheckNode.execute(inliningTarget, x)) {
                    return longFromUnicode.execute(inliningTarget, x, base);
                } else if (bytesLikeCheck.execute(inliningTarget, x)) {
                    return fromBufferNode.execute(frame, x, base);
                }
                cannotConvert.enter(inliningTarget);
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.INT_CANT_CONVERT_STRING_WITH_EXPL_BASE);
            }
        }
    }

    @Builtin(name = J___ROUND__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @ImportStatic(MathGuards.class)
    @TypeSystemReference(PythonIntegerTypes.class)
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
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language) {
            return PFactory.createInt(language, arg.getValue());
        }

        @Specialization
        static Object roundLongInt(long arg, int n,
                        @Bind Node inliningTarget,
                        @Shared("intOvf") @Cached InlinedBranchProfile intOverflow,
                        @Shared("longOvf") @Cached InlinedBranchProfile longOverflow) {
            if (n >= 0) {
                return arg;
            }
            return makeInt(inliningTarget, op(arg, n), intOverflow, longOverflow);
        }

        @Specialization
        static Object roundPIntInt(PInt arg, int n,
                        @Bind Node inliningTarget,
                        @Shared("intOvf") @Cached InlinedBranchProfile intOverflow,
                        @Shared("longOvf") @Cached InlinedBranchProfile longOverflow) {
            if (n >= 0) {
                return arg;
            }
            return makeInt(inliningTarget, op(arg.getValue(), n), intOverflow, longOverflow);
        }

        @Specialization
        static Object roundLongLong(long arg, long n,
                        @Bind Node inliningTarget,
                        @Shared("intOvf") @Cached InlinedBranchProfile intOverflow,
                        @Shared("longOvf") @Cached InlinedBranchProfile longOverflow) {
            if (n >= 0) {
                return arg;
            }
            if (n < Integer.MIN_VALUE) {
                return 0;
            }
            return makeInt(inliningTarget, op(arg, (int) n), intOverflow, longOverflow);
        }

        @Specialization
        static Object roundPIntLong(PInt arg, long n,
                        @Bind Node inliningTarget,
                        @Shared("intOvf") @Cached InlinedBranchProfile intOverflow,
                        @Shared("longOvf") @Cached InlinedBranchProfile longOverflow) {
            if (n >= 0) {
                return arg;
            }
            if (n < Integer.MIN_VALUE) {
                return 0;
            }
            return makeInt(inliningTarget, op(arg.getValue(), (int) n), intOverflow, longOverflow);
        }

        @Specialization
        static Object roundPIntLong(long arg, PInt n,
                        @Bind Node inliningTarget,
                        @Shared("intOvf") @Cached InlinedBranchProfile intOverflow,
                        @Shared("longOvf") @Cached InlinedBranchProfile longOverflow) {
            if (n.isZeroOrPositive()) {
                return arg;
            }
            try {
                return makeInt(inliningTarget, op(arg, n.intValueExact()), intOverflow, longOverflow);
            } catch (OverflowException e) {
                // n is < -2^31, max. number of base-10 digits in BigInteger is 2^31 * log10(2)
                return 0;
            }
        }

        @Specialization
        static Object roundPIntPInt(PInt arg, PInt n,
                        @Bind Node inliningTarget,
                        @Shared("intOvf") @Cached InlinedBranchProfile intOverflow,
                        @Shared("longOvf") @Cached InlinedBranchProfile longOverflow) {
            if (n.isZeroOrPositive()) {
                return arg;
            }
            try {
                return makeInt(inliningTarget, op(arg.getValue(), n.intValueExact()), intOverflow, longOverflow);
            } catch (OverflowException e) {
                // n is < -2^31, max. number of base-10 digits in BigInteger is 2^31 * log10(2)
                return 0;
            }
        }

        @Specialization(guards = {"!isInteger(n)"})
        @SuppressWarnings("unused")
        static Object roundPIntPInt(Object arg, Object n,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, PythonErrorType.TypeError, ErrorMessages.OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER, n);
        }

        private static Object makeInt(Node inliningTarget, BigDecimal d, InlinedBranchProfile intOverflow, InlinedBranchProfile longOverflow) {
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
                longOverflow.enter(inliningTarget);
            }
            try {
                return PFactory.createInt(PythonLanguage.get(inliningTarget), toBigIntegerExact(d));
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

    @Slot(value = SlotKind.nb_add, isComplex = true)
    @GenerateNodeFactory
    @TypeSystemReference(PythonIntegerTypes.class)
    public abstract static class AddNode extends BinaryOpBuiltinNode {
        public abstract Object execute(int left, int right);

        @Specialization(rewriteOn = ArithmeticException.class)
        static int doII(int left, int right) {
            return Math.addExact(left, right);
        }

        @Specialization(replaces = "doII", rewriteOn = ArithmeticException.class)
        static long doLL(long left, long right) {
            return Math.addExact(left, right);
        }

        @Specialization(replaces = "doLL")
        static Object doLLOvf(long x, long y,
                        @Bind PythonLanguage language) {
            /* Inlined version of Math.addExact(x, y) with BigInteger fallback. */
            long r = x + y;
            // HD 2-12 Overflow iff both arguments have the opposite sign of the result
            if (((x ^ r) & (y ^ r)) < 0) {
                return PFactory.createInt(language, add(PInt.longToBigInteger(x), PInt.longToBigInteger(y)));
            }
            return r;
        }

        @Specialization(rewriteOn = OverflowException.class)
        static Object doPLNarrow(PInt left, long right) throws OverflowException {
            return PInt.longValueExact(add(left.getValue(), PInt.longToBigInteger(right)));
        }

        @Specialization(replaces = "doPLNarrow")
        static Object doPL(PInt left, long right,
                        @Bind PythonLanguage language) {
            return PFactory.createInt(language, add(left.getValue(), PInt.longToBigInteger(right)));
        }

        @Specialization(rewriteOn = OverflowException.class)
        static Object doLPNarrow(long left, PInt right) throws OverflowException {
            return PInt.longValueExact(add(PInt.longToBigInteger(left), right.getValue()));
        }

        @Specialization(replaces = "doLPNarrow")
        static Object doLP(long left, PInt right,
                        @Bind PythonLanguage language) {
            return PFactory.createInt(language, add(PInt.longToBigInteger(left), right.getValue()));
        }

        @Specialization(rewriteOn = OverflowException.class)
        static Object doPPNarrow(PInt left, PInt right) throws OverflowException {
            return PInt.longValueExact(add(left.getValue(), right.getValue()));
        }

        @Specialization(replaces = "doPPNarrow")
        static Object doPP(PInt left, PInt right,
                        @Bind PythonLanguage language) {
            return PFactory.createInt(language, add(left.getValue(), right.getValue()));
        }

        @TruffleBoundary
        public static BigInteger add(BigInteger left, BigInteger right) {
            return left.add(right);
        }

        static boolean isNotImplemented(Object x) {
            return !(x instanceof Long || x instanceof Integer || x instanceof Boolean || x instanceof PInt);
        }

        // There is a Truffle bug (GR-57305) that constructs a wrong fallback guard in the presence
        // of implicit casts, so we cannot use @Fallback for now
        @SuppressWarnings("unused")
        @Specialization(guards = {"isNotImplemented(left) || isNotImplemented(right)"})
        static PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        @NeverDefault
        public static AddNode create() {
            return AddNodeFactory.create();
        }
    }

    @Slot(value = SlotKind.nb_subtract, isComplex = true)
    @GenerateNodeFactory
    @TypeSystemReference(PythonIntegerTypes.class)
    public abstract static class SubNode extends BinaryOpBuiltinNode {
        public abstract Object execute(int left, int right);

        @Specialization(rewriteOn = ArithmeticException.class)
        static int doII(int x, int y) throws ArithmeticException {
            return Math.subtractExact(x, y);
        }

        @Specialization(replaces = "doII")
        static long doIIOvf(int x, int y) {
            return (long) x - (long) y;
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        static long doLL(long x, long y) throws ArithmeticException {
            return Math.subtractExact(x, y);
        }

        @Specialization(replaces = "doLL")
        static Object doLongWithOverflow(long x, long y,
                        @Bind Node inliningTarget) {
            /* Inlined version of Math.subtractExact(x, y) with BigInteger fallback. */
            long r = x - y;
            // HD 2-12 Overflow iff the arguments have different signs and
            // the sign of the result is different than the sign of x
            if (((x ^ y) & (x ^ r)) < 0) {
                return PFactory.createInt(PythonLanguage.get(inliningTarget), sub(PInt.longToBigInteger(x), PInt.longToBigInteger(y)));
            }
            return r;
        }

        @Specialization(rewriteOn = OverflowException.class)
        static long doPIntLongAndNarrow(PInt left, long right) throws OverflowException {
            return PInt.longValueExact(sub(left.getValue(), PInt.longToBigInteger(right)));
        }

        @Specialization(replaces = "doPIntLongAndNarrow")
        static PInt doPIntLong(PInt left, long right,
                        @Bind PythonLanguage language) {
            return PFactory.createInt(language, sub(left.getValue(), PInt.longToBigInteger(right)));
        }

        @Specialization(rewriteOn = OverflowException.class)
        static long doLongPIntAndNarrow(long left, PInt right) throws OverflowException {
            return PInt.longValueExact(sub(PInt.longToBigInteger(left), right.getValue()));
        }

        @Specialization(replaces = "doLongPIntAndNarrow")
        static PInt doLongPInt(long left, PInt right,
                        @Bind PythonLanguage language) {
            return PFactory.createInt(language, sub(PInt.longToBigInteger(left), right.getValue()));
        }

        @Specialization(rewriteOn = OverflowException.class)
        static long doPIntPIntAndNarrow(PInt left, PInt right) throws OverflowException {
            return PInt.longValueExact(sub(left.getValue(), right.getValue()));
        }

        @Specialization(replaces = "doPIntPIntAndNarrow")
        static PInt doPIntPInt(PInt left, PInt right,
                        @Bind PythonLanguage language) {
            return PFactory.createInt(language, sub(left.getValue(), right.getValue()));
        }

        @TruffleBoundary
        public static BigInteger sub(BigInteger left, BigInteger right) {
            return left.subtract(right);
        }

        @Fallback
        @SuppressWarnings("unused")
        static PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        @NeverDefault
        public static SubNode create() {
            return SubNodeFactory.create();
        }
    }

    @Slot(value = SlotKind.nb_true_divide, isComplex = true)
    @GenerateNodeFactory
    @TypeSystemReference(PythonIntegerTypes.class)
    public abstract static class TrueDivNode extends BinaryOpBuiltinNode {
        public abstract Object execute(int left, int right);

        @Specialization
        static double divII(int x, int y,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode raiseNode) {
            return divDD(x, y, inliningTarget, raiseNode);
        }

        @Specialization(guards = {"fitsIntoDouble(x)", "fitsIntoDouble(y)"})
        static double divLL(long x, long y,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode raiseNode) {
            return divDD(x, y, inliningTarget, raiseNode);
        }

        @Specialization(guards = {"!fitsIntoDouble(x) || !fitsIntoDouble(y)"})
        static double divLLLarge(long x, long y,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode raiseNode) {
            if (y == 0) {
                throw raiseNode.raise(inliningTarget, PythonErrorType.ZeroDivisionError, ErrorMessages.DIVISION_BY_ZERO);
            }
            return op(inliningTarget, PInt.longToBigInteger(x), PInt.longToBigInteger(y));
        }

        static double divDD(double x, double y,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode raiseNode) {
            if (y == 0) {
                throw raiseNode.raise(inliningTarget, PythonErrorType.ZeroDivisionError, ErrorMessages.DIVISION_BY_ZERO);
            }
            return x / y;
        }

        @Specialization
        static double doPI(long left, PInt right,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode raiseNode) {
            if (right.isZero()) {
                throw raiseNode.raise(inliningTarget, PythonErrorType.ZeroDivisionError, ErrorMessages.DIVISION_BY_ZERO);
            }
            return op(inliningTarget, PInt.longToBigInteger(left), right.getValue());
        }

        @Specialization
        static double doPL(PInt left, long right,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode raiseNode) {
            if (right == 0) {
                throw raiseNode.raise(inliningTarget, PythonErrorType.ZeroDivisionError, ErrorMessages.DIVISION_BY_ZERO);
            }
            return op(inliningTarget, left.getValue(), PInt.longToBigInteger(right));
        }

        @Specialization
        static double doPP(PInt left, PInt right,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode raiseNode) {
            if (right.isZero()) {
                throw raiseNode.raise(inliningTarget, PythonErrorType.ZeroDivisionError, ErrorMessages.DIVISION_BY_ZERO);
            }
            return op(inliningTarget, left.getValue(), right.getValue());
        }

        /*
         * We must take special care to do double conversion late (if possible), to avoid loss of
         * precision.
         */
        @TruffleBoundary
        private static double op(Node raisingNode, BigInteger a, BigInteger b) {
            final int precisionOfDouble = 18;
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
                throw PRaiseNode.raiseStatic(raisingNode, OverflowError, ErrorMessages.INTEGER_DIVISION_RESULT_TOO_LARGE);
            }
            return d;
        }

        public static boolean fitsIntoDouble(long x) {
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
            return TrueDivNodeFactory.create();
        }
    }

    @Slot(value = SlotKind.nb_floor_divide, isComplex = true)
    @TypeSystemReference(PythonIntegerTypes.class)
    @GenerateNodeFactory
    public abstract static class FloorDivNode extends BinaryOpBuiltinNode {
        public abstract Object execute(int left, int right);

        private static final long INT_OVERFLOW_VALUE = (long) Integer.MAX_VALUE + 1;
        private static final BigInteger LONG_OVERFLOW_VALUE = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);

        @Specialization
        static Object doII(int left, int right,
                        @Bind Node inliningTarget,
                        @Shared @Cached InlinedBranchProfile overflowValueProfile,
                        @Shared @Cached PRaiseNode raiseNode) {
            raiseDivisionByZero(inliningTarget, right == 0, raiseNode);
            if (left == Integer.MIN_VALUE && right == -1) {
                overflowValueProfile.enter(inliningTarget);
                return INT_OVERFLOW_VALUE;
            }
            return Math.floorDiv(left, right);
        }

        @Specialization
        static Object doLL(long left, long right,
                        @Bind Node inliningTarget,
                        @Shared @Cached InlinedBranchProfile overflowValueProfile,
                        @Shared @Cached PRaiseNode raiseNode) {
            raiseDivisionByZero(inliningTarget, right == 0, raiseNode);
            if (left == Long.MIN_VALUE && right == -1) {
                overflowValueProfile.enter(inliningTarget);
                return PFactory.createInt(PythonLanguage.get(inliningTarget), LONG_OVERFLOW_VALUE);
            }
            return Math.floorDiv(left, right);
        }

        @Specialization
        static Object doIPi(int left, PInt right,
                        @Bind Node inliningTarget,
                        @Shared @Cached InlinedBranchProfile overflowValueProfile,
                        @Shared @Cached PRaiseNode raiseNode) {
            try {
                int rightValue = right.intValueExact();
                raiseDivisionByZero(inliningTarget, rightValue == 0, raiseNode);
                if (left == Integer.MIN_VALUE && rightValue == -1) {
                    overflowValueProfile.enter(inliningTarget);
                    return INT_OVERFLOW_VALUE;
                }
                return Math.floorDiv(left, rightValue);
            } catch (OverflowException e) {
                return left < 0 == right.isNegative() ? 0 : -1;
            }
        }

        @Specialization
        static Object doLPi(long left, PInt right,
                        @Bind Node inliningTarget,
                        @Shared @Cached InlinedBranchProfile overflowValueProfile,
                        @Shared @Cached PRaiseNode raiseNode) {
            try {
                long rightValue = right.longValueExact();
                raiseDivisionByZero(inliningTarget, rightValue == 0, raiseNode);
                if (left == Long.MIN_VALUE && rightValue == -1) {
                    overflowValueProfile.enter(inliningTarget);
                    return PFactory.createInt(PythonLanguage.get(inliningTarget), LONG_OVERFLOW_VALUE);
                }
                return Math.floorDiv(left, rightValue);
            } catch (OverflowException e) {
                return left < 0 == right.isNegative() ? 0 : -1;
            }
        }

        @Specialization(rewriteOn = OverflowException.class)
        static long doPiIAndNarrow(PInt left, int right,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode raiseNode) throws OverflowException {
            raiseDivisionByZero(inliningTarget, right == 0, raiseNode);
            return PInt.longValueExact(op(left.getValue(), PInt.longToBigInteger(right)));
        }

        @Specialization(replaces = "doPiIAndNarrow")
        static PInt doPiI(PInt left, int right,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Shared @Cached PRaiseNode raiseNode) {
            raiseDivisionByZero(inliningTarget, right == 0, raiseNode);
            return PFactory.createInt(language, op(left.getValue(), PInt.longToBigInteger(right)));
        }

        @Specialization(rewriteOn = OverflowException.class)
        static long doPiLAndNarrow(PInt left, long right,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode raiseNode) throws OverflowException {
            raiseDivisionByZero(inliningTarget, right == 0, raiseNode);
            return PInt.longValueExact(op(left.getValue(), PInt.longToBigInteger(right)));
        }

        @Specialization(replaces = "doPiLAndNarrow")
        static PInt doPiL(PInt left, long right,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Shared @Cached PRaiseNode raiseNode) {
            raiseDivisionByZero(inliningTarget, right == 0, raiseNode);
            return PFactory.createInt(language, op(left.getValue(), PInt.longToBigInteger(right)));
        }

        @Specialization(rewriteOn = OverflowException.class)
        static long doPiPiAndNarrow(PInt left, PInt right,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode raiseNode) throws OverflowException {
            raiseDivisionByZero(inliningTarget, right.isZero(), raiseNode);
            return PInt.longValueExact(op(left.getValue(), right.getValue()));
        }

        @Specialization(replaces = "doPiPiAndNarrow")
        static PInt doPiPi(PInt left, PInt right,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Shared @Cached PRaiseNode raiseNode) {
            raiseDivisionByZero(inliningTarget, right.isZero(), raiseNode);
            return PFactory.createInt(language, op(left.getValue(), right.getValue()));
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
            return FloorDivNodeFactory.create();
        }
    }

    @Slot(value = SlotKind.nb_divmod, isComplex = true)
    @GenerateNodeFactory
    abstract static class DivModNode extends BinaryOpBuiltinNode {

        @Specialization
        static Object doGeneric(VirtualFrame frame, Object left, Object right,
                        @Bind Node inliningTarget,
                        @Cached FloorDivNode floorDivNode,
                        @Cached ModNode modNode) {
            Object div = floorDivNode.execute(frame, left, right);
            if (div == PNotImplemented.NOT_IMPLEMENTED) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            Object mod = modNode.execute(frame, left, right);
            return PFactory.createTuple(PythonLanguage.get(inliningTarget), new Object[]{div, mod});
        }
    }

    @Slot(value = SlotKind.nb_remainder, isComplex = true)
    @TypeSystemReference(PythonIntegerTypes.class)
    @GenerateNodeFactory
    public abstract static class ModNode extends BinaryOpBuiltinNode {
        public abstract int executeInt(int left, int right) throws UnexpectedResultException;

        public abstract Object execute(int left, int right);

        @Specialization
        static int doII(int left, int right,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode raiseNode) {
            raiseDivisionByZero(inliningTarget, right == 0, raiseNode);
            return Math.floorMod(left, right);
        }

        @Specialization
        static long doLL(long left, long right,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode raiseNode) {
            raiseDivisionByZero(inliningTarget, right == 0, raiseNode);
            return Math.floorMod(left, right);
        }

        @Specialization(guards = "right.isZeroOrPositive()", rewriteOn = OverflowException.class)
        static long doLPiAndNarrow(long left, PInt right,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode raiseNode) throws OverflowException {
            raiseDivisionByZero(inliningTarget, right.isZero(), raiseNode);
            return PInt.longValueExact(op(PInt.longToBigInteger(left), right.getValue()));
        }

        @Specialization(guards = "right.isZeroOrPositive()", replaces = "doLPiAndNarrow")
        static PInt doLPi(long left, PInt right,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Shared @Cached PRaiseNode raiseNode) {
            raiseDivisionByZero(inliningTarget, right.isZero(), raiseNode);
            return PFactory.createInt(language, op(PInt.longToBigInteger(left), right.getValue()));
        }

        @Specialization(guards = "!right.isZeroOrPositive()", rewriteOn = OverflowException.class)
        static long doLPiNegativeAndNarrow(long left, PInt right,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode raiseNode) throws OverflowException {
            raiseDivisionByZero(inliningTarget, right.isZero(), raiseNode);
            return PInt.longValueExact(opNeg(PInt.longToBigInteger(left), right.getValue()));
        }

        @Specialization(guards = "!right.isZeroOrPositive()", replaces = "doLPiNegativeAndNarrow")
        static PInt doLPiNegative(long left, PInt right,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Shared @Cached PRaiseNode raiseNode) {
            raiseDivisionByZero(inliningTarget, right.isZero(), raiseNode);
            return PFactory.createInt(language, opNeg(PInt.longToBigInteger(left), right.getValue()));
        }

        @Specialization(guards = "right >= 0", rewriteOn = OverflowException.class)
        static long doPiLAndNarrow(PInt left, long right,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode raiseNode) throws OverflowException {
            raiseDivisionByZero(inliningTarget, right == 0, raiseNode);
            return PInt.longValueExact(op(left.getValue(), PInt.longToBigInteger(right)));
        }

        @Specialization(guards = "right >= 0", replaces = "doPiLAndNarrow")
        static PInt doPiL(PInt left, long right,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Shared @Cached PRaiseNode raiseNode) {
            raiseDivisionByZero(inliningTarget, right == 0, raiseNode);
            return PFactory.createInt(language, op(left.getValue(), PInt.longToBigInteger(right)));
        }

        @Specialization(guards = "right < 0", rewriteOn = OverflowException.class)
        static long doPiLNegAndNarrow(PInt left, long right,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode raiseNode) throws OverflowException {
            raiseDivisionByZero(inliningTarget, right == 0, raiseNode);
            return PInt.longValueExact(opNeg(left.getValue(), PInt.longToBigInteger(right)));
        }

        @Specialization(guards = "right < 0", replaces = "doPiLNegAndNarrow")
        static PInt doPiLNeg(PInt left, long right,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Shared @Cached PRaiseNode raiseNode) {
            raiseDivisionByZero(inliningTarget, right == 0, raiseNode);
            return PFactory.createInt(language, opNeg(left.getValue(), PInt.longToBigInteger(right)));
        }

        @Specialization(guards = "right.isZeroOrPositive()", rewriteOn = OverflowException.class)
        static long doPiPiAndNarrow(PInt left, PInt right,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode raiseNode) throws OverflowException {
            raiseDivisionByZero(inliningTarget, right.isZero(), raiseNode);
            return PInt.longValueExact(op(left.getValue(), right.getValue()));
        }

        @Specialization(guards = "right.isZeroOrPositive()", replaces = "doPiPiAndNarrow")
        static PInt doPiPi(PInt left, PInt right,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Shared @Cached PRaiseNode raiseNode) {
            raiseDivisionByZero(inliningTarget, right.isZero(), raiseNode);
            return PFactory.createInt(language, op(left.getValue(), right.getValue()));
        }

        @Specialization(guards = "!right.isZeroOrPositive()", rewriteOn = OverflowException.class)
        static long doPiPiNegAndNarrow(PInt left, PInt right) throws OverflowException {
            return PInt.longValueExact(opNeg(left.getValue(), right.getValue()));
        }

        @Specialization(guards = "!right.isZeroOrPositive()", replaces = "doPiPiNegAndNarrow")
        static PInt doPiPiNeg(PInt left, PInt right,
                        @Bind PythonLanguage language) {
            return PFactory.createInt(language, opNeg(left.getValue(), right.getValue()));
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
            return ModNodeFactory.create();
        }
    }

    @Slot(value = SlotKind.nb_multiply, isComplex = true)
    @GenerateNodeFactory
    @TypeSystemReference(PythonIntegerTypes.class)
    public abstract static class MulNode extends BinaryOpBuiltinNode {
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

        @Specialization(replaces = "doLL")
        static Object doLongWithOverflow(long x, long y,
                        @Bind Node inliningTarget) {
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
                    return PFactory.createInt(PythonLanguage.get(inliningTarget), mul(PInt.longToBigInteger(x), PInt.longToBigInteger(y)));
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
                        @Bind PythonLanguage language) {
            // we must return a new object with the same value
            return PFactory.createInt(language, left.getValue());
        }

        @Specialization(guards = "left == 1")
        PInt doPIntLongOne(@SuppressWarnings("unused") long left, PInt right,
                        @Bind PythonLanguage language) {
            return PFactory.createInt(language, right.getValue());
        }

        @Specialization(guards = {"right != 0", "right != 1"})
        static PInt doPIntLong(PInt left, long right,
                        @Bind PythonLanguage language) {
            return PFactory.createInt(language, mul(left.getValue(), PInt.longToBigInteger(right)));
        }

        @Specialization(guards = {"left != 0", "left != 1"})
        PInt doPIntLong(long left, PInt right,
                        @Bind PythonLanguage language) {
            return PFactory.createInt(language, mul(PInt.longToBigInteger(left), right.getValue()));
        }

        @Specialization
        static PInt doPIntPInt(PInt left, PInt right,
                        @Bind PythonLanguage language) {
            return PFactory.createInt(language, mul(left.getValue(), right.getValue()));
        }

        @TruffleBoundary
        public static BigInteger mul(BigInteger a, BigInteger b) {
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
            return MulNodeFactory.create();
        }
    }

    @Slot(value = SlotKind.nb_power, isComplex = true)
    @GenerateNodeFactory
    @TypeSystemReference(PythonIntegerTypes.class)
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
                        @Bind PythonLanguage language) {
            return PFactory.createInt(language, op(PInt.longToBigInteger(left), right));
        }

        @Specialization(guards = "right < 0")
        @InliningCutoff
        double doLLNeg(long left, long right, @SuppressWarnings("unused") PNone none,
                        @Bind Node inliningTarget,
                        @Shared("leftIsZero") @Cached InlinedConditionProfile leftIsZero,
                        @Shared @Cached PRaiseNode raiseNode) {
            if (leftIsZero.profile(inliningTarget, left == 0)) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.ZeroDivisionError, ErrorMessages.POW_ZERO_CANNOT_RAISE_TO_NEGATIVE_POWER);
            }
            return Math.pow(left, right);
        }

        @Specialization(rewriteOn = {OverflowException.class, ArithmeticException.class})
        @InliningCutoff
        Object doLPNarrow(long left, PInt right, @SuppressWarnings("unused") PNone none,
                        @Bind Node inliningTarget,
                        @Shared("leftIsZero") @Cached InlinedConditionProfile leftIsZero,
                        @Shared @Cached PRaiseNode raiseNode) throws OverflowException {
            long lright = right.longValueExact();
            if (lright >= 0) {
                return doLLFast(left, lright, none);
            }
            return doLLNeg(left, lright, none, inliningTarget, leftIsZero, raiseNode);
        }

        @Specialization(replaces = "doLPNarrow")
        @InliningCutoff
        Object doLP(long left, PInt right, @SuppressWarnings("unused") PNone none) {
            Object result = op(PInt.longToBigInteger(left), right.getValue());
            if (result instanceof BigInteger) {
                return PFactory.createInt(PythonLanguage.get(this), (BigInteger) result);
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
                        @Bind PythonLanguage language) {
            return PFactory.createInt(language, op(left.getValue(), right));
        }

        @Specialization(guards = "right < 0")
        @InliningCutoff
        double doPLNeg(PInt left, long right, @SuppressWarnings("unused") PNone none,
                        @Bind Node inliningTarget,
                        @Shared("leftIsZero") @Cached InlinedConditionProfile leftIsZero,
                        @Shared @Cached PRaiseNode raiseNode) {
            double leftDouble = PInt.doubleValueWithOverflow(this, left.getValue());
            if (leftIsZero.profile(inliningTarget, leftDouble == 0.0)) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.ZeroDivisionError, ErrorMessages.POW_ZERO_CANNOT_RAISE_TO_NEGATIVE_POWER);
            }
            return Math.pow(leftDouble, right);
        }

        @Specialization
        @InliningCutoff
        Object doPP(PInt left, PInt right, @SuppressWarnings("unused") PNone none) {
            Object result = op(left.getValue(), right.getValue());
            if (result instanceof BigInteger) {
                return PFactory.createInt(PythonLanguage.get(this), (BigInteger) result);
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
                        @Bind Node inliningTarget,
                        @Exclusive @Cached InlinedConditionProfile modNegativeProfile,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            if (mod == 0) {
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.POW_THIRD_ARG_CANNOT_BE_ZERO);
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
                return PFactory.createInt(PythonLanguage.get(this), (BigInteger) result);
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
                throw PRaiseNode.raiseStatic(this, ValueError, ErrorMessages.POW_THIRD_ARG_CANNOT_BE_ZERO);
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
                    throw PRaiseNode.raiseStatic(this, ValueError, ErrorMessages.POW_BASE_NOT_INVERTIBLE);
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
            } else {
                double leftDouble = PInt.doubleValueWithOverflow(this, left);
                double rightDouble = PInt.doubleValueWithOverflow(this, right);
                if (leftDouble == 0.0) {
                    throw PRaiseNode.raiseStatic(this, PythonBuiltinClassType.ZeroDivisionError, ErrorMessages.POW_ZERO_CANNOT_RAISE_TO_NEGATIVE_POWER);
                }
                return Math.pow(leftDouble, rightDouble);
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
                throw PRaiseNode.raiseStatic(this, PythonErrorType.ArithmeticError, ErrorMessages.EXPONENT_TOO_LARGE);
            }
            return a.pow((int) b);
        }

        @NeverDefault
        public static PowNode create() {
            return PowNodeFactory.create();
        }
    }

    @Slot(value = SlotKind.nb_absolute, isComplex = true)
    @GenerateNodeFactory
    abstract static class AbsNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object absInt(int arg) {
            return PInt.abs(arg);
        }

        @Specialization
        static Object absLong(long arg,
                        @Bind Node inliningTarget) {
            return PInt.abs(inliningTarget, arg);
        }

        @Specialization
        static PInt absPInt(PInt arg,
                        @Bind PythonLanguage language) {
            return PFactory.createInt(language, arg.abs());
        }

        @Specialization
        static int absBoolean(boolean arg) {
            return arg ? 1 : 0;
        }
    }

    @Builtin(name = J___CEIL__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonIntegerTypes.class)
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
    @TypeSystemReference(PythonIntegerTypes.class)
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
                        @Bind PythonLanguage language) {
            return PFactory.createInt(language, arg.getValue());
        }
    }

    @Slot(value = SlotKind.nb_positive, isComplex = true)
    @GenerateNodeFactory
    abstract static class PosNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Integer pos(Integer arg) {
            return arg;
        }

        @Specialization
        static Long pos(Long arg) {
            return arg;
        }

        @Specialization
        static PInt pos(PInt arg,
                        @Bind PythonLanguage language) {
            return PFactory.createInt(language, arg.getValue());
        }

        @Specialization
        static int pos(boolean arg) {
            return PInt.intValue(arg);
        }
    }

    @Slot(value = SlotKind.nb_negative, isComplex = true)
    @GenerateNodeFactory
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
                        @Bind PythonLanguage language) {
            BigInteger value = arg == Long.MIN_VALUE ? negate(PInt.longToBigInteger(arg)) : PInt.longToBigInteger(-arg);
            return PFactory.createInt(language, value);
        }

        @Specialization
        static PInt doPInt(PInt operand,
                        @Bind PythonLanguage language) {
            return PFactory.createInt(language, negate(operand.getValue()));
        }

        @Specialization
        static int doBoolean(boolean arg) {
            return -PInt.intValue(arg);
        }

        @TruffleBoundary
        static BigInteger negate(BigInteger value) {
            return value.negate();
        }

        @NeverDefault
        public static NegNode create() {
            return NegNodeFactory.create();
        }
    }

    @Slot(value = SlotKind.nb_invert, isComplex = true)
    @GenerateNodeFactory
    abstract static class InvertNode extends PythonUnaryBuiltinNode {

        @TruffleBoundary
        private static void warnBoolInvert() {
            WarningsModuleBuiltins.WarnNode.getUncached().warnEx(null, DeprecationWarning, BITWISE_INVERSION_OF_THE_UNDERLYING_INT, 1);
        }

        @Specialization
        static int neg(boolean arg) {
            warnBoolInvert();
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
                        @Bind PythonLanguage language) {
            return PFactory.createInt(language, not(operand.getValue()));
        }

        @TruffleBoundary
        static BigInteger not(BigInteger value) {
            return value.not();
        }
    }

    @Slot(value = SlotKind.nb_lshift, isComplex = true)
    @GenerateNodeFactory
    @TypeSystemReference(PythonIntegerTypes.class)
    public abstract static class LShiftNode extends BinaryOpBuiltinNode {
        public abstract int executeInt(int left, int right) throws UnexpectedResultException;

        public abstract Object execute(int left, int right);

        private static long leftShiftExact(Node inliningTarget, long left, long right, PRaiseNode raiseNode) throws OverflowException {
            if (right >= Long.SIZE || right < 0) {
                shiftError(inliningTarget, right, raiseNode);
            }

            long result = left << right;

            if (left != result >> right) {
                throw OverflowException.INSTANCE;
            }

            return result;
        }

        private static int leftShiftExact(Node inliningTarget, int left, int right, PRaiseNode raiseNode) throws OverflowException {
            if (right >= Integer.SIZE || right < 0) {
                shiftError(inliningTarget, right, raiseNode);
            }

            int result = left << right;

            if (left != result >> right) {
                throw OverflowException.INSTANCE;
            }

            return result;
        }

        private static void shiftError(Node inliningTarget, long shiftCount, PRaiseNode raiseNode) throws OverflowException {
            if (shiftCount >= Integer.SIZE) {
                throw OverflowException.INSTANCE;
            } else if (shiftCount < 0) {
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.NEGATIVE_SHIFT_COUNT);
            }
        }

        @Specialization(rewriteOn = OverflowException.class)
        static int doII(int left, int right,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode raiseNode) throws OverflowException {
            raiseNegativeShiftCount(inliningTarget, right < 0, raiseNode);
            return leftShiftExact(inliningTarget, left, right, raiseNode);
        }

        @Specialization
        static Object doIIOvf(int left, int right,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode raiseNode) {
            raiseNegativeShiftCount(inliningTarget, right < 0, raiseNode);
            try {
                return leftShiftExact(inliningTarget, left, right, raiseNode);
            } catch (OverflowException e) {
                return doGuardedBiI(inliningTarget, PInt.longToBigInteger(left), right, raiseNode);
            }
        }

        @Specialization(rewriteOn = OverflowException.class)
        static long doLL(long left, long right,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode raiseNode) throws OverflowException {
            raiseNegativeShiftCount(inliningTarget, right < 0, raiseNode);
            return leftShiftExact(inliningTarget, left, right, raiseNode);
        }

        @Specialization
        static Object doILOvf(int left, long right,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode raiseNode) {
            return doLLOvf(left, right, inliningTarget, raiseNode);
        }

        @Specialization
        static Object doLIOvf(long left, int right,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode raiseNode) {
            return doLLOvf(left, right, inliningTarget, raiseNode);
        }

        @Specialization
        static Object doLLOvf(long left, long right,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode raiseNode) {
            raiseNegativeShiftCount(inliningTarget, right < 0, raiseNode);
            try {
                return leftShiftExact(inliningTarget, left, right, raiseNode);
            } catch (OverflowException e) {
                int rightI = (int) right;
                if (rightI == right) {
                    try {
                        return PFactory.createInt(PythonLanguage.get(inliningTarget), op(PInt.longToBigInteger(left), rightI));
                    } catch (OverflowException ex) {
                        // fallback to the raise of overflow error
                    }
                }
                throw raiseNode.raise(inliningTarget, PythonErrorType.OverflowError);
            }
        }

        @Specialization(guards = {"left == 0", "right.isZeroOrPositive()"})
        static int doIPiZero(@SuppressWarnings("unused") int left, @SuppressWarnings("unused") PInt right) {
            return 0;
        }

        @Specialization(replaces = "doIPiZero")
        static PInt doIPi(int left, PInt right,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Shared @Cached PRaiseNode raiseNode) {
            raiseNegativeShiftCount(inliningTarget, !right.isZeroOrPositive(), raiseNode);
            if (left == 0) {
                return PFactory.createInt(language, BigInteger.ZERO);
            }
            try {
                int iright = right.intValueExact();
                return PFactory.createInt(language, op(PInt.longToBigInteger(left), iright));
            } catch (OverflowException e) {
                throw raiseNode.raise(inliningTarget, PythonErrorType.OverflowError);
            }
        }

        @Specialization(guards = {"left == 0", "right.isZeroOrPositive()"})
        static int doLPiZero(@SuppressWarnings("unused") long left, @SuppressWarnings("unused") PInt right) {
            return 0;
        }

        // there is some duplication in here because of GR-44123

        @Specialization(replaces = "doLPiZero")
        static PInt doLPi(long left, PInt right,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode raiseNode) {
            raiseNegativeShiftCount(inliningTarget, !right.isZeroOrPositive(), raiseNode);
            PythonLanguage language = PythonLanguage.get(inliningTarget);
            if (left == 0) {
                return PFactory.createInt(language, BigInteger.ZERO);
            }
            try {
                int iright = right.intValueExact();
                return PFactory.createInt(language, op(PInt.longToBigInteger(left), iright));
            } catch (OverflowException e) {
                throw raiseNode.raise(inliningTarget, PythonErrorType.OverflowError);
            }
        }

        @Specialization
        static PInt doPiI(PInt left, int right,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode raiseNode) {
            raiseNegativeShiftCount(inliningTarget, right < 0, raiseNode);
            return doGuardedBiI(inliningTarget, left.getValue(), right, raiseNode);
        }

        static PInt doGuardedBiI(Node inliningTarget, BigInteger left, int right, PRaiseNode raiseNode) {
            try {
                return PFactory.createInt(PythonLanguage.get(inliningTarget), op(left, right));
            } catch (OverflowException e) {
                throw raiseNode.raise(inliningTarget, PythonErrorType.OverflowError);
            }
        }

        @Specialization
        static PInt doPiL(PInt left, long right,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode raiseNode) {
            raiseNegativeShiftCount(inliningTarget, right < 0, raiseNode);
            int rightI = (int) right;
            if (rightI == right) {
                return doGuardedBiI(inliningTarget, left.getValue(), rightI, raiseNode);
            } else {
                throw raiseNode.raise(inliningTarget, PythonErrorType.OverflowError);
            }
        }

        @Specialization(guards = {"left.isZero()", "right.isZeroOrPositive()"})
        static int doPiPiZero(@SuppressWarnings("unused") PInt left, @SuppressWarnings("unused") PInt right) {
            return 0;
        }

        @Specialization(replaces = "doPiPiZero")
        static PInt doPiPi(PInt left, PInt right,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode raiseNode) {
            raiseNegativeShiftCount(inliningTarget, !right.isZeroOrPositive(), raiseNode);
            PythonLanguage language = PythonLanguage.get(inliningTarget);
            if (left.isZero()) {
                return PFactory.createInt(language, BigInteger.ZERO);
            }
            try {
                return PFactory.createInt(language, op(left.getValue(), right.intValueExact()));
            } catch (OverflowException e) {
                throw raiseNode.raise(inliningTarget, PythonErrorType.OverflowError);
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

        private static void raiseNegativeShiftCount(Node inliningTarget, boolean cond, PRaiseNode raiseNode) {
            if (cond) {
                throw raiseNode.raise(inliningTarget, PythonErrorType.ValueError, ErrorMessages.NEGATIVE_SHIFT_COUNT);
            }
        }

        @NeverDefault
        public static LShiftNode create() {
            return LShiftNodeFactory.create();
        }
    }

    @Slot(value = SlotKind.nb_rshift, isComplex = true)
    @TypeSystemReference(PythonIntegerTypes.class)
    @GenerateNodeFactory
    public abstract static class RShiftNode extends BinaryOpBuiltinNode {
        public abstract int executeInt(int left, int right) throws UnexpectedResultException;

        public abstract Object execute(int left, int right);

        @Specialization(guards = "right < 32")
        static int doIISmall(int left, int right,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode raiseNode) {
            raiseNegativeShiftCount(inliningTarget, right < 0, raiseNode);
            return left >> right;
        }

        @Specialization(replaces = "doIISmall")
        static int doII(int left, int right,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode raiseNode) {
            raiseNegativeShiftCount(inliningTarget, right < 0, raiseNode);
            // Note: according to JLS, if 'left' is an int, then only the 5 LSBs of 'right' are
            // considered. However, Python would consider more bits, so do the max possible shift.
            return left >> (right >= 32 ? 31 : right);
        }

        @Specialization(guards = "right < 64")
        static long doLLSmall(long left, long right,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode raiseNode) {
            raiseNegativeShiftCount(inliningTarget, right < 0, raiseNode);
            return left >> right;
        }

        @Specialization(replaces = "doLLSmall")
        static long doLL(long left, long right,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode raiseNode) {
            raiseNegativeShiftCount(inliningTarget, right < 0, raiseNode);
            // for explanation, see 'doII'
            return left >> (right >= 64 ? 63 : right);
        }

        @Specialization
        static Object doIPi(int left, PInt right,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode raiseNode) {
            return doHugeShift(inliningTarget, PInt.longToBigInteger(left), right, raiseNode);
        }

        @Specialization
        static Object doLPi(long left, PInt right,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode raiseNode) {
            return doHugeShift(inliningTarget, PInt.longToBigInteger(left), right, raiseNode);
        }

        @Specialization
        static PInt doPiI(PInt left, int right,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Shared @Cached PRaiseNode raiseNode) {
            raiseNegativeShiftCount(inliningTarget, right < 0, raiseNode);
            return PFactory.createInt(language, op(left.getValue(), right));
        }

        @Specialization
        static Object doPiL(PInt left, long right,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Shared @Cached PRaiseNode raiseNode) {
            raiseNegativeShiftCount(inliningTarget, right < 0, raiseNode);
            int rightI = (int) right;
            if (rightI == right) {
                return PFactory.createInt(language, op(left.getValue(), rightI));
            }
            // right is >= 2**31, BigInteger's bitLength is at most 2**31-1
            // therefore the result of shifting right is just the sign bit
            return left.isNegative() ? -1 : 0;
        }

        @Specialization
        static Object doPInt(PInt left, PInt right,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode raiseNode) {
            return doHugeShift(inliningTarget, left.getValue(), right, raiseNode);
        }

        private static void raiseNegativeShiftCount(Node inliningTarget, boolean cond, PRaiseNode raiseNode) {
            if (cond) {
                throw raiseNode.raise(inliningTarget, PythonErrorType.ValueError, ErrorMessages.NEGATIVE_SHIFT_COUNT);
            }
        }

        @SuppressWarnings("unused")
        @Fallback
        static PNotImplemented doGeneric(Object a, Object b) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        private static Object doHugeShift(Node inliningTarget, BigInteger left, PInt right, PRaiseNode raiseNode) {
            raiseNegativeShiftCount(inliningTarget, !right.isZeroOrPositive(), raiseNode);
            try {
                return PFactory.createInt(PythonLanguage.get(inliningTarget), op(left, right.intValueExact()));
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
            return RShiftNodeFactory.create();
        }
    }

    @GenerateCached(false)
    abstract static class BinaryBitwiseNode extends BinaryOpBuiltinNode {

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
                        @Bind Node inliningTarget,
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
                        @Bind Node inliningTarget,
                        @Shared("h") @Cached PyObjectHashNode hashNode) {
            long ptrVal = a.getNativePointer();
            // pointers are considered unsigned
            return op(ptrVal, hashNode.execute(frame, inliningTarget, b));
        }

        @Specialization(guards = {"!a.isNativePointer()", "b.isNativePointer()"})
        long voidPtrsBNative(VirtualFrame frame, PythonNativeVoidPtr a, PythonNativeVoidPtr b,
                        @Bind Node inliningTarget,
                        @Shared("h") @Cached PyObjectHashNode hashNode) {
            long ptrVal = b.getNativePointer();
            // pointers are considered unsigned
            return op(ptrVal, hashNode.execute(frame, inliningTarget, a));
        }

        @Specialization(guards = {"!a.isNativePointer()", "!b.isNativePointer()"})
        long voidPtrsManaged(VirtualFrame frame, PythonNativeVoidPtr a, PythonNativeVoidPtr b,
                        @Bind Node inliningTarget,
                        @Shared("h") @Cached PyObjectHashNode hashNode) {
            return op(hashNode.execute(frame, inliningTarget, a), hashNode.execute(frame, inliningTarget, b));
        }

        @Specialization
        PInt doPInt(long left, PInt right,
                        @Bind PythonLanguage language) {
            return PFactory.createInt(language, op(PInt.longToBigInteger(left), right.getValue()));
        }

        @Specialization
        PInt doPInt(PInt left, long right,
                        @Bind PythonLanguage language) {
            return PFactory.createInt(language, op(left.getValue(), PInt.longToBigInteger(right)));
        }

        @Specialization
        PInt doPInt(PInt left, PInt right,
                        @Bind PythonLanguage language) {
            return PFactory.createInt(language, op(left.getValue(), right.getValue()));
        }

        @SuppressWarnings("unused")
        @Fallback
        static PNotImplemented doGeneric(Object a, Object b) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Slot(value = SlotKind.nb_and, isComplex = true)
    @TypeSystemReference(PythonIntegerTypes.class)
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
            return AndNodeFactory.create();
        }
    }

    @Slot(value = SlotKind.nb_or, isComplex = true)
    @TypeSystemReference(PythonIntegerTypes.class)
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
            return OrNodeFactory.create();
        }
    }

    @Slot(value = SlotKind.nb_xor, isComplex = true)
    @TypeSystemReference(PythonIntegerTypes.class)
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
            return XorNodeFactory.create();
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @TypeSystemReference(PythonIntegerTypes.class)
    @ImportStatic(FromNativeSubclassNode.class)
    abstract static class RichCompareHelperNode extends Node {

        abstract Object execute(VirtualFrame frame, Node inliningTarget, Object left, Object right, RichCmpOp op);

        @Specialization
        static boolean doII(int left, int right, RichCmpOp op) {
            return op.compareResultToBool(Integer.compare(left, right));
        }

        @Specialization
        static boolean doLL(long left, long right, RichCmpOp op) {
            return op.compareResultToBool(Long.compare(left, right));
        }

        @Specialization(rewriteOn = OverflowException.class)
        static boolean doLPExact(long left, PInt right, RichCmpOp op) throws OverflowException {
            return op.compareResultToBool(Long.compare(left, right.longValueExact()));
        }

        @Specialization(rewriteOn = OverflowException.class)
        static boolean doPLExact(PInt left, long right, RichCmpOp op) throws OverflowException {
            return op.compareResultToBool(Long.compare(left.longValueExact(), right));
        }

        @Specialization(replaces = "doLPExact")
        static boolean doLP(long left, PInt right, RichCmpOp op) {
            return op.compareResultToBool(PInt.compareTo(left, right));
        }

        @Specialization(replaces = "doPLExact")
        static boolean doPL(PInt left, long right, RichCmpOp op) {
            return op.compareResultToBool(left.compareTo(right));
        }

        @Specialization
        static boolean doPP(PInt left, PInt right, RichCmpOp op) {
            return op.compareResultToBool(left.compareTo(right));
        }

        @Specialization(guards = "isFloatSubtype(inliningTarget, y, getClass, isSubtype)")
        @InliningCutoff
        static boolean doDN(VirtualFrame frame, Node inliningTarget, long x, PythonAbstractNativeObject y, RichCmpOp op,
                        @SuppressWarnings("unused") @Shared @Cached GetPythonObjectClassNode getClass,
                        @SuppressWarnings("unused") @Shared @Cached IsSubtypeNode isSubtype,
                        @Shared @Cached FromNativeSubclassNode nativeRight) {
            return op.compareResultToBool(PFloat.compare(x, nativeRight.execute(frame, y)));
        }

        @Specialization(guards = {
                        "isFloatSubtype(inliningTarget, x, getClass, isSubtype)",
                        "isFloatSubtype(inliningTarget, y, getClass, isSubtype)"})
        @InliningCutoff
        static boolean doDN(VirtualFrame frame, Node inliningTarget, PythonAbstractNativeObject x, PythonAbstractNativeObject y, RichCmpOp op,
                        @SuppressWarnings("unused") @Shared @Cached GetPythonObjectClassNode getClass,
                        @SuppressWarnings("unused") @Shared @Cached IsSubtypeNode isSubtype,
                        @Shared @Cached FromNativeSubclassNode nativeLeft,
                        @Shared @Cached FromNativeSubclassNode nativeRight) {
            return op.compareResultToBool(PFloat.compare(nativeLeft.execute(frame, x), nativeRight.execute(frame, y)));
        }

        @Specialization(guards = "isFloatSubtype(inliningTarget, x, getClass, isSubtype)")
        @InliningCutoff
        static boolean doDN(VirtualFrame frame, Node inliningTarget, PythonAbstractNativeObject x, double y, RichCmpOp op,
                        @SuppressWarnings("unused") @Shared @Cached GetPythonObjectClassNode getClass,
                        @SuppressWarnings("unused") @Shared @Cached IsSubtypeNode isSubtype,
                        @Shared @Cached FromNativeSubclassNode nativeLeft) {
            return op.compareResultToBool(PFloat.compare(nativeLeft.execute(frame, x), y));
        }

        // Note: native int subclasses are still represented as Java PInt, just with a different
        // Python level class

        static boolean someIsNativePtr(Object a, Object b) {
            return a instanceof PythonNativeVoidPtr || b instanceof PythonNativeVoidPtr;
        }

        @Specialization(guards = "someIsNativePtr(x, y)")
        @InliningCutoff
        static Object doVoidPtr(VirtualFrame frame, Node inliningTarget, Object x, Object y, RichCmpOp op,
                        @Cached PointerCompareNode pointerCompareNode,
                        @Cached EqNodeNativePtr pointerEqNode) {
            if (op.isEqOrNe()) {
                Object result = pointerEqNode.execute(frame, x, y);
                if (result == PNotImplemented.NOT_IMPLEMENTED) {
                    return result;
                }
                return ((boolean) result) == op.isEq();
            }
            return pointerCompareNode.execute(inliningTarget, op, x, y);
        }

        @GenerateInline(false)       // footprint reduction 32 -> 15
        @TypeSystemReference(PythonIntegerTypes.class)
        abstract static class EqNodeNativePtr extends PNodeWithContext {

            abstract Object execute(VirtualFrame frame, Object a, Object b);

            @Specialization
            static boolean eqLongVoidPtr(VirtualFrame frame, long a, PythonNativeVoidPtr b,
                            @Bind Node inliningTarget,
                            @Shared("h") @Cached PyObjectHashNode hashNode) {
                return eqVoidPtrLong(frame, b, a, inliningTarget, hashNode);
            }

            @Specialization
            static boolean eqPIntVoidPtr(PInt a, PythonNativeVoidPtr b) {
                return eqVoidPtrPInt(b, a);
            }

            @Specialization
            static boolean eqVoidPtrLong(VirtualFrame frame, PythonNativeVoidPtr a, long b,
                            @Bind Node inliningTarget,
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
                            @Bind Node inliningTarget,
                            @Shared("h") @Cached PyObjectHashNode hashNode) {
                long ptrVal = a.getNativePointer();
                // pointers are considered unsigned
                return ptrVal == hashNode.execute(frame, inliningTarget, b);
            }

            @Specialization(guards = {"!a.isNativePointer()", "b.isNativePointer()"})
            static boolean voidPtrsBNative(VirtualFrame frame, PythonNativeVoidPtr a, PythonNativeVoidPtr b,
                            @Bind Node inliningTarget,
                            @Shared("h") @Cached PyObjectHashNode hashNode) {
                long ptrVal = b.getNativePointer();
                // pointers are considered unsigned
                return ptrVal == hashNode.execute(frame, inliningTarget, a);
            }

            @Specialization(guards = {"!a.isNativePointer()", "!b.isNativePointer()"})
            static boolean voidPtrsManaged(VirtualFrame frame, PythonNativeVoidPtr a, PythonNativeVoidPtr b,
                            @Bind Node inliningTarget,
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

            @SuppressWarnings("unused")
            @Fallback
            static PNotImplemented doGeneric(Object a, Object b) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }

        @SuppressWarnings("unused")
        @Fallback
        static PNotImplemented doGeneric(Object a, Object b, RichCmpOp op) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Slot(value = SlotKind.tp_richcompare, isComplex = true)
    @GenerateNodeFactory
    abstract static class RichCompareNode extends RichCmpBuiltinNode {
        @Specialization(guards = {"opCode == cachedOp"}, limit = "6")
        static Object doCached(VirtualFrame frame, Object left, Object right, @SuppressWarnings("unused") RichCmpOp opCode,
                        @Bind Node inliningTarget,
                        @Cached PyLongCheckNode checkLeft,
                        @Cached PyLongCheckNode checkRight,
                        @SuppressWarnings("unused") @Cached("opCode") RichCmpOp cachedOp,
                        @Cached RichCompareHelperNode cmpNode) {
            if (!checkLeft.execute(inliningTarget, left) || !checkRight.execute(inliningTarget, right)) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            return cmpNode.execute(frame, inliningTarget, left, right, cachedOp);
        }
    }

    // to_bytes
    @Builtin(name = "to_bytes", minNumOfPositionalArgs = 1, parameterNames = {"$self", "length", "byteorder"}, keywordOnlyNames = {"signed"})
    @ArgumentClinic(name = "length", conversion = ClinicConversion.Index, defaultValue = "1")
    @ArgumentClinic(name = "byteorder", conversion = ClinicConversion.TString, defaultValue = "T_BIG")
    @ArgumentClinic(name = "signed", conversion = ClinicConversion.Boolean, defaultValue = "false")
    @GenerateNodeFactory
    @SuppressWarnings("unused")
    @TypeSystemReference(PythonIntegerTypes.class)
    public abstract static class ToBytesNode extends PythonClinicBuiltinNode {

        @TruffleBoundary
        private static boolean isBigEndian(Node raisingNode, TruffleString order) {
            if (order.equalsUncached(T_BIG, TS_ENCODING)) {
                return true;
            }
            if (order.equalsUncached(T_LITTLE, TS_ENCODING)) {
                return false;
            }
            throw PRaiseNode.raiseStatic(raisingNode, ValueError, ErrorMessages.BYTEORDER_MUST_BE_LITTLE_OR_BIG);
        }

        @Specialization
        static PBytes fromLong(long self, int byteCount, TruffleString byteorder, boolean signed,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Exclusive @Cached InlinedConditionProfile negativeByteCountProfile,
                        @Exclusive @Cached InlinedConditionProfile negativeNumberProfile,
                        @Exclusive @Cached InlinedConditionProfile overflowProfile,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            if (negativeByteCountProfile.profile(inliningTarget, byteCount < 0)) {
                throw raiseNode.raise(inliningTarget, PythonErrorType.ValueError, ErrorMessages.MESSAGE_LENGTH_ARGUMENT);
            }
            if (self < 0) {
                if (negativeNumberProfile.profile(inliningTarget, !signed)) {
                    throw raiseNode.raise(inliningTarget, PythonErrorType.OverflowError, ErrorMessages.MESSAGE_CONVERT_NEGATIVE);
                }
            }
            return PFactory.createBytes(language, fromLong(self, byteCount, isBigEndian(inliningTarget, byteorder), signed,
                            inliningTarget, overflowProfile, raiseNode));
        }

        public static byte[] fromLong(long self, int byteCount, boolean isBigEndian, boolean signed,
                        Node inliningTarget,
                        InlinedConditionProfile overflowProfile,
                        PRaiseNode raiseNode) {
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

            if (overflowProfile.profile(inliningTarget, !signed && number != 0 || (signed && bytes.length == 1 && bytes[0] != self) || (byteCount == 0 && self != 0 && self != -1))) {
                throw raiseNode.raise(inliningTarget, PythonErrorType.OverflowError, ErrorMessages.MESSAGE_INT_TO_BIG);
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
                    throw PRaiseNode.raiseStatic(raisingNode, OverflowError, ErrorMessages.MESSAGE_CONVERT_NEGATIVE);
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
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Exclusive @Cached InlinedConditionProfile negativeByteCountProfile,
                        @Exclusive @Cached InlinedConditionProfile overflowProfile,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            if (negativeByteCountProfile.profile(inliningTarget, byteCount < 0)) {
                throw raiseNode.raise(inliningTarget, PythonErrorType.ValueError, ErrorMessages.MESSAGE_LENGTH_ARGUMENT);
            }
            return PFactory.createBytes(language, fromBigInteger(self, byteCount, isBigEndian(inliningTarget, byteorder), signed,
                            inliningTarget, overflowProfile, raiseNode));
        }

        public static byte[] fromBigInteger(PInt self, int byteCount, boolean isBigEndian, boolean signed,
                        Node inliningTarget,
                        InlinedConditionProfile overflowProfile,
                        PRaiseNode raiseNode) {
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
                    throw raiseNode.raise(inliningTarget, PythonErrorType.OverflowError, ErrorMessages.MESSAGE_INT_TO_BIG);
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
            return ToBytesNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "from_bytes", minNumOfPositionalArgs = 2, parameterNames = {"cls", "bytes", "byteorder"}, keywordOnlyNames = {"signed"}, isClassmethod = true)
    @ArgumentClinic(name = "byteorder", conversion = ClinicConversion.TString, defaultValue = "T_BIG")
    @ArgumentClinic(name = "signed", conversion = ClinicConversion.Boolean, defaultValue = "false")
    @ImportStatic(SpecialMethodNames.class)
    @GenerateNodeFactory
    public abstract static class FromBytesNode extends PythonClinicBuiltinNode {

        @Specialization
        static Object fromObject(VirtualFrame frame, Object cl, Object object, TruffleString byteorder, boolean signed,
                        @Bind Node inliningTarget,
                        @Cached("create(T___BYTES__)") LookupAndCallUnaryNode callBytes,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib,
                        @Cached IsBuiltinClassExactProfile isBuiltinIntProfile,
                        @Cached InlinedBranchProfile hasBytesProfile,
                        @Cached EqualNode equalNode,
                        @Cached BytesFromObject bytesFromObject,
                        @Cached PyLongFromByteArray fromByteArray,
                        @Cached CallNode callCtor,
                        @Cached PRaiseNode raiseNode) {
            boolean littleEndian;
            if (equalNode.execute(byteorder, T_BIG, TS_ENCODING)) {
                littleEndian = false;
            } else if (equalNode.execute(byteorder, T_LITTLE, TS_ENCODING)) {
                littleEndian = true;
            } else {
                throw raiseNode.raise(inliningTarget, PythonErrorType.ValueError, ErrorMessages.BYTEORDER_MUST_BE_LITTLE_OR_BIG);
            }
            byte[] bytes;
            Object bytesObj = callBytes.executeObject(frame, object);
            if (bytesObj != PNone.NO_VALUE) {
                hasBytesProfile.enter(inliningTarget);
                if (!(bytesObj instanceof PBytes)) {
                    throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.RETURNED_NONBYTES, T___BYTES__);
                }
                bytes = bufferLib.getCopiedByteArray(bytesObj);
            } else {
                bytes = bytesFromObject.execute(frame, object);
            }
            Object result = fromByteArray.execute(inliningTarget, bytes, littleEndian, signed);
            if (isBuiltinIntProfile.profileClass(inliningTarget, cl, PythonBuiltinClassType.PInt)) {
                return result;
            } else {
                return callCtor.execute(frame, cl, result);
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return FromBytesNodeClinicProviderGen.INSTANCE;
        }
    }

    @Slot(SlotKind.nb_bool)
    @GenerateUncached
    @GenerateNodeFactory
    public abstract static class BoolNode extends NbBoolBuiltinNode {
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

    @Slot(value = SlotKind.tp_str, isComplex = true)
    @GenerateNodeFactory
    @TypeSystemReference(PythonIntegerTypes.class)
    abstract static class StrNode extends PythonUnaryBuiltinNode {

        @Specialization
        static TruffleString doL(long self,
                        @Shared("fromLong") @Cached FromLongNode fromLongNode) {
            return fromLongNode.execute(self, TS_ENCODING, false);
        }

        @Specialization
        static TruffleString doPInt(PInt self,
                        @Bind Node inliningTarget,
                        @Cached FromJavaStringNode fromJavaStringNode,
                        @Cached InlinedIntValueProfile maxDigitsProfile,
                        @Cached InlinedIntValueProfile maxDigitsBitLengthProfile,
                        @Cached PRaiseNode raiseNode) {
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
                    throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.EXCEEDS_THE_LIMIT_FOR_INTEGER_STRING_CONVERSION, intMaxStrDigits);
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
                    throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.EXCEEDS_THE_LIMIT_FOR_INTEGER_STRING_CONVERSION);
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
                        @Bind Node inliningTarget,
                        @Cached PyObjectHashNode hashNode,
                        @Shared("fromLong") @Cached FromLongNode fromLongNode) {
            return doL(hashNode.execute(frame, inliningTarget, self), fromLongNode);
        }
    }

    @Slot(value = SlotKind.tp_repr, isComplex = true)
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

        // We cannot use PythonIntegerTypes, because for empty format string we need to call the
        // boolean's __str__ and not int's __str__ (that specialization is inherited)
        @Specialization(guards = "!formatString.isEmpty()")
        static TruffleString formatB(boolean self, TruffleString formatString,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode raiseNode) {
            return formatI(self ? 1 : 0, formatString, inliningTarget, raiseNode);
        }

        @Specialization(guards = "!formatString.isEmpty()")
        static TruffleString formatI(int self, TruffleString formatString,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode raiseNode) {
            Spec spec = getSpec(formatString, inliningTarget);
            if (isDoubleSpec(spec)) {
                return formatDouble(spec, self, inliningTarget);
            }
            validateIntegerSpec(inliningTarget, raiseNode, spec);
            return formatInt(self, spec, inliningTarget);
        }

        @Specialization(guards = "!formatString.isEmpty()")
        static TruffleString formatL(VirtualFrame frame, long self, TruffleString formatString,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Shared @Cached PyNumberFloatNode floatNode,
                        @Shared @Cached PRaiseNode raiseNode) {
            return formatPI(frame, PFactory.createInt(language, self), formatString, inliningTarget, floatNode, raiseNode);
        }

        @Specialization(guards = "!formatString.isEmpty()")
        static TruffleString formatPI(VirtualFrame frame, PInt self, TruffleString formatString,
                        @Bind Node inliningTarget,
                        @Shared @Cached PyNumberFloatNode floatNode,
                        @Shared @Cached PRaiseNode raiseNode) {
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

        private static void validateIntegerSpec(Node inliningTarget, PRaiseNode raiseNode, Spec spec) {
            if (Spec.specified(spec.precision)) {
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.PRECISION_NOT_ALLOWED_FOR_INT);
            }
            if (spec.type == 'c') {
                if (Spec.specified(spec.sign)) {
                    throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.SIGN_NOT_ALLOWED_WITH_C_FOR_INT);
                } else if (spec.alternate) {
                    throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.ALTERNATE_NOT_ALLOWED_WITH_C_FOR_INT);
                }
            }
        }
    }

    @Slot(value = SlotKind.tp_hash, isComplex = true)
    @GenerateNodeFactory
    @TypeSystemReference(PythonIntegerTypes.class)
    @GenerateUncached
    public abstract static class HashNode extends HashBuiltinNode {
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
    @TypeSystemReference(PythonIntegerTypes.class)
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
    @TypeSystemReference(PythonIntegerTypes.class)
    public abstract static class BitLengthNode extends PythonUnaryBuiltinNode {
        public abstract int execute(Object argument);

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

    @Builtin(name = "is_integer", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonIntegerTypes.class)
    abstract static class IsIntegerNode extends PythonUnaryBuiltinNode {
        @Specialization
        static boolean doLong(long argument) {
            return true;
        }

        @Specialization
        @TruffleBoundary
        static boolean doPInt(PInt argument) {
            return true;
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
                        @Bind PythonLanguage language) {
            return PFactory.createTuple(language, new Object[]{intNode.execute(frame, self), 1});
        }
    }

    @GenerateNodeFactory
    @Builtin(name = J___TRUNC__, minNumOfPositionalArgs = 1, doc = "Truncating an Integral returns itself.")
    abstract static class TruncNode extends IntNode {

    }

    @Slot(value = SlotKind.nb_int, isComplex = true)
    @Slot(value = SlotKind.nb_index, isComplex = true)
    @GenerateNodeFactory
    abstract static class IntNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object doCopy(Object self,
                        @Bind Node inliningTarget,
                        @Cached PyLongCopy copy) {
            return copy.execute(inliningTarget, self);
        }
    }

    @Builtin(name = J___GETNEWARGS__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class GetNewArgsNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doI(Object self,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached PyLongCopy copy) {
            return PFactory.createTuple(language, new Object[]{copy.execute(inliningTarget, self)});
        }
    }

    @Slot(value = SlotKind.nb_float, isComplex = true)
    @GenerateNodeFactory
    @TypeSystemReference(PythonIntegerTypes.class)
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
