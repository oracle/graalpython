/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.datetime;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ZeroDivisionError;
import static com.oracle.graal.python.nodes.BuiltinNames.T_MAX;
import static com.oracle.graal.python.nodes.BuiltinNames.T_MIN;
import static com.oracle.graal.python.nodes.BuiltinNames.T_RESOLUTION;
import static com.oracle.graal.python.nodes.BuiltinNames.T__DATETIME;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.math.BigInteger;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryOp.BinaryOpBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotHashFun;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotInquiry;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotRichCompare.RichCmpBuiltinNode;
import com.oracle.graal.python.lib.PyFloatCheckNode;
import com.oracle.graal.python.lib.PyLongCheckNode;
import com.oracle.graal.python.lib.PyNumberAddNode;
import com.oracle.graal.python.lib.PyNumberFloorDivideNode;
import com.oracle.graal.python.lib.PyNumberMultiplyNode;
import com.oracle.graal.python.lib.PyNumberRemainderNode;
import com.oracle.graal.python.lib.PyNumberTrueDivideNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectHashNode;
import com.oracle.graal.python.lib.PyTupleGetItem;
import com.oracle.graal.python.lib.PyTupleSizeNode;
import com.oracle.graal.python.lib.RichCmpOp;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CastToJavaBigIntegerNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.EncapsulatingNodeReference;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PTimeDelta)
public final class TimeDeltaBuiltins extends PythonBuiltins {

    public static final TpSlots SLOTS = TimeDeltaBuiltinsSlotsGen.SLOTS;

    private static final TruffleString T_AS_INTEGER_RATIO = tsLiteral("as_integer_ratio");
    private static final TruffleString T_TIMEDELTA = tsLiteral("timedelta");

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return TimeDeltaBuiltinsFactory.getFactories();
    }

    @Override
    public void postInitialize(Python3Core core) {
        super.postInitialize(core);

        PythonLanguage language = core.getLanguage();

        PythonModule datetimeModule = core.lookupBuiltinModule(T__DATETIME);
        PythonBuiltinClass self = (PythonBuiltinClass) datetimeModule.getAttribute(T_TIMEDELTA);
        final var timeDeltaType = PythonBuiltinClassType.PTimeDelta;
        final var timeDeltaShape = timeDeltaType.getInstanceShape(language);

        final var min = new PTimeDelta(timeDeltaType, timeDeltaShape, -999999999, 0, 0);
        self.setAttribute(T_MIN, min);

        final var max = new PTimeDelta(timeDeltaType, timeDeltaShape, 999999999, 23 * 3600 + 59 * 60 + 59, 999999);
        self.setAttribute(T_MAX, max);

        final var resolution = new PTimeDelta(timeDeltaType, timeDeltaShape, 0, 0, 1);
        self.setAttribute(T_RESOLUTION, resolution);
    }

    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = "datetime.timedelta", minNumOfPositionalArgs = 1, parameterNames = {"$cls", "days", "seconds", "microseconds", "milliseconds", "minutes", "hours", "weeks"})
    @GenerateNodeFactory
    public abstract static class NewNode extends PythonBuiltinNode {

        @Specialization
        static Object newTimeDelta(Object cls, Object days, Object seconds, Object microseconds, Object milliseconds, Object minutes, Object hours, Object weeks,
                        @Bind Node inliningTarget,
                        @Cached TimeDeltaNodes.NewNode newNode) {
            return newNode.execute(inliningTarget, cls, days, seconds, microseconds, milliseconds, minutes, hours, weeks);
        }
    }

    @Slot(SlotKind.nb_bool)
    @GenerateUncached
    @GenerateNodeFactory
    abstract static class BoolNode extends TpSlotInquiry.NbBoolBuiltinNode {

        @Specialization
        static boolean bool(Object selfObj,
                        @Bind Node inliningTarget,
                        @Cached TimeDeltaNodes.AsManagedTimeDeltaNode asManagedTimeDeltaNode) {
            PTimeDelta self = asManagedTimeDeltaNode.execute(inliningTarget, selfObj);
            return self.days != 0 || self.seconds != 0 || self.microseconds != 0;
        }
    }

    @Slot(value = SlotKind.tp_repr, isComplex = true)
    @GenerateNodeFactory
    public abstract static class ReprNode extends PythonUnaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        static TruffleString repr(Object selfObj) {
            PTimeDelta self = TimeDeltaNodes.AsManagedTimeDeltaNode.executeUncached(selfObj);
            var builder = new StringBuilder();

            builder.append(TypeNodes.GetTpNameNode.executeUncached(GetClassNode.executeUncached(self)));

            builder.append("(");

            if (self.days != 0 || self.seconds != 0 || self.microseconds != 0) {
                if (self.days != 0) {
                    builder.append("days=");
                    builder.append(self.days);
                }

                if (self.seconds != 0) {
                    if (self.days != 0) {
                        builder.append(", ");
                    }

                    builder.append("seconds=");
                    builder.append(self.seconds);
                }

                if (self.microseconds != 0) {
                    if (self.days != 0 || self.seconds != 0) {
                        builder.append(", ");
                    }

                    builder.append("microseconds=");
                    builder.append(self.microseconds);
                }
            } else {
                builder.append("0");
            }

            builder.append(")");

            var string = builder.toString();
            return TruffleString.FromJavaStringNode.getUncached().execute(string, TS_ENCODING);
        }
    }

    @Slot(value = SlotKind.tp_str, isComplex = true)
    @GenerateNodeFactory
    public abstract static class StrNode extends PythonUnaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        static TruffleString str(Object selfObj) {
            PTimeDelta self = TimeDeltaNodes.AsManagedTimeDeltaNode.executeUncached(selfObj);
            var builder = new StringBuilder();

            // optional prefix with days, e.g. '1 day' or '5 days'
            if (self.days != 0) {
                builder.append(self.days);

                if (Math.abs(self.days) != 1) {
                    builder.append(" days");
                } else {
                    builder.append(" day");
                }

                builder.append(", ");
            }

            // mandatory section with hours/minutes/seconds, in format HH:dd:ss
            int hours = self.seconds / 3600;
            int minutes = (self.seconds % 3600) / 60;
            int seconds = (self.seconds % 60);

            String timeString = String.format("%d:%02d:%02d", hours, minutes, seconds);
            builder.append(timeString);

            // optional suffix with microseconds, in format '.UUUUUU'
            if (self.microseconds != 0) {
                builder.append(".");
                String microsecondsString = String.format("%06d", self.microseconds);
                builder.append(microsecondsString);
            }

            var string = builder.toString();
            return TruffleString.FromJavaStringNode.getUncached().execute(string, TS_ENCODING);
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object reduce(Object selfObj,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached GetClassNode getClassNode,
                        @Cached TimeDeltaNodes.AsManagedTimeDeltaNode asManagedTimeDeltaNode) {
            PTimeDelta self = asManagedTimeDeltaNode.execute(inliningTarget, selfObj);
            Object type = getClassNode.execute(inliningTarget, selfObj);
            PTuple arguments = PFactory.createTuple(language, new Object[]{self.days, self.seconds, self.microseconds});
            return PFactory.createTuple(language, new Object[]{type, arguments});
        }
    }

    @Slot(value = SlotKind.tp_richcompare, isComplex = true)
    @GenerateNodeFactory
    abstract static class RichCmpNode extends RichCmpBuiltinNode {

        @Specialization
        static Object richCmp(Object left, Object right, RichCmpOp op,
                        @Bind Node inliningTarget,
                        @Cached TimeDeltaNodes.TimeDeltaCheckNode checkNode,
                        @Cached TimeDeltaNodes.AsManagedTimeDeltaNode asManagedTimeDeltaNode) {
            if (!checkNode.execute(inliningTarget, left) || !checkNode.execute(inliningTarget, right)) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            PTimeDelta self = asManagedTimeDeltaNode.execute(inliningTarget, left);
            PTimeDelta other = asManagedTimeDeltaNode.execute(inliningTarget, right);
            int result = self.compareTo(other);
            return op.compareResultToBool(result);
        }
    }

    @Slot(value = SlotKind.tp_hash, isComplex = true)
    @GenerateNodeFactory
    abstract static class HashNode extends TpSlotHashFun.HashBuiltinNode {

        @Specialization
        static long hash(VirtualFrame frame, Object selfObj,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached PyObjectHashNode hashNode,
                        @Cached TimeDeltaNodes.AsManagedTimeDeltaNode asManagedTimeDeltaNode) {
            PTimeDelta self = asManagedTimeDeltaNode.execute(inliningTarget, selfObj);
            var content = new int[]{self.days, self.seconds, self.microseconds};
            return hashNode.execute(frame, inliningTarget, PFactory.createTuple(language, content));
        }
    }

    @Slot(value = SlotKind.nb_add, isComplex = true)
    @GenerateUncached
    @GenerateNodeFactory
    abstract static class AddNode extends BinaryOpBuiltinNode {

        @Specialization
        static Object add(Object left, Object right,
                        @Bind Node inliningTarget,
                        @Cached TimeDeltaNodes.NewNode newNode,
                        @Cached TimeDeltaNodes.TimeDeltaCheckNode checkNode,
                        @Cached TimeDeltaNodes.AsManagedTimeDeltaNode asManagedTimeDeltaNode) {
            if (!checkNode.execute(inliningTarget, left) || !checkNode.execute(inliningTarget, right)) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            PTimeDelta self = asManagedTimeDeltaNode.execute(inliningTarget, left);
            PTimeDelta other = asManagedTimeDeltaNode.execute(inliningTarget, right);
            return newNode.executeBuiltin(inliningTarget, self.days + other.days, self.seconds + other.seconds, self.microseconds + other.microseconds, 0, 0, 0, 0);
        }
    }

    @Slot(value = SlotKind.nb_subtract, isComplex = true)
    @GenerateUncached
    @GenerateNodeFactory
    abstract static class SubNode extends BinaryOpBuiltinNode {

        @Specialization
        static Object sub(Object left, Object rigth,
                        @Bind Node inliningTarget,
                        @Cached TimeDeltaNodes.NewNode newNode,
                        @Cached TimeDeltaNodes.TimeDeltaCheckNode checkNode,
                        @Cached TimeDeltaNodes.AsManagedTimeDeltaNode asManagedTimeDeltaNode) {
            if (!checkNode.execute(inliningTarget, left) || !checkNode.execute(inliningTarget, rigth)) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            PTimeDelta self = asManagedTimeDeltaNode.execute(inliningTarget, left);
            PTimeDelta other = asManagedTimeDeltaNode.execute(inliningTarget, rigth);
            return newNode.executeBuiltin(inliningTarget, self.days - other.days, self.seconds - other.seconds, self.microseconds - other.microseconds, 0, 0, 0, 0);
        }
    }

    @TruffleBoundary
    private static PInt divideNearest(Node node, Object a, Object b) {
        EncapsulatingNodeReference encapsulating = EncapsulatingNodeReference.getCurrent();
        Node encapsulatingNode = encapsulating.set(node);
        try {
            BigInteger dividend = CastToJavaBigIntegerNode.executeUncached(a);
            BigInteger divisor = CastToJavaBigIntegerNode.executeUncached(b);
            if (divisor.equals(BigInteger.ZERO)) {
                throw PRaiseNode.raiseStatic(node, ZeroDivisionError, ErrorMessages.INTEGER_DIVISION_OR_MODULO_BY_ZERO);
            }
            BigInteger[] qr = dividend.divideAndRemainder(divisor);
            BigInteger quotient = qr[0];
            BigInteger remainder = qr[1];

            // Scale to compare (remainder * 2)
            BigInteger doubleRemainder = remainder.abs().multiply(BigInteger.valueOf(2));
            int cmp = doubleRemainder.compareTo(divisor.abs());

            BigInteger result;

            if (cmp < 0) {
                // Remainder < 0.5, round down (do nothing)
                result = quotient;
            } else {
                BigInteger addend = dividend.signum() == divisor.signum() ? BigInteger.ONE : BigInteger.ONE.negate();
                if (cmp > 0) {
                    // Remainder > 0.5, round up
                    result = quotient.add(addend);
                } else {
                    // Exactly halfway
                    // If quotient is even, return quotient
                    // If odd, round to nearest even (add or subtract 1)
                    if (quotient.mod(BigInteger.valueOf(2)).equals(BigInteger.ZERO)) {
                        result = quotient;
                    } else {
                        result = quotient.add(addend);
                    }
                }
            }
            return PFactory.createInt(PythonLanguage.get(null), result);
        } finally {
            encapsulating.set(encapsulatingNode);
        }
    }

    @Slot(value = SlotKind.nb_multiply, isComplex = true)
    @GenerateUncached
    @GenerateNodeFactory
    abstract static class MulNode extends BinaryOpBuiltinNode {

        @Specialization
        static Object mul(VirtualFrame frame, Object left, Object right,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode,
                        @Cached PyLongCheckNode longCheckNode,
                        @Cached PyFloatCheckNode floatCheckNode,
                        @Cached PyObjectCallMethodObjArgs callMethodObjArgs,
                        @Cached PyTupleGetItem tupleGetItem,
                        @Cached PyNumberAddNode addNode,
                        @Cached PyNumberMultiplyNode multiplyNode,
                        @Cached TimeDeltaNodes.NewNode newNode,
                        @Cached TimeDeltaNodes.TimeDeltaCheckNode checkNode,
                        @Cached TimeDeltaNodes.AsManagedTimeDeltaNode asManagedTimeDeltaNode) {
            PTimeDelta date;
            Object other;
            if (checkNode.execute(inliningTarget, left)) {
                date = asManagedTimeDeltaNode.execute(inliningTarget, left);
                other = right;
            } else {
                date = asManagedTimeDeltaNode.execute(inliningTarget, right);
                other = left;
            }
            if (longCheckNode.execute(inliningTarget, other)) {
                Object selfAsMicroseconds = toMicroseconds(date, addNode, multiplyNode);
                Object microseconds = multiplyNode.execute(null, selfAsMicroseconds, other);

                return newNode.executeBuiltin(inliningTarget, 0, 0, microseconds, 0, 0, 0, 0);
            } else if (floatCheckNode.execute(inliningTarget, other)) {
                Object selfAsMicroseconds = toMicroseconds(date, addNode, multiplyNode);

                Object ratioTuple = callMethodObjArgs.execute(frame, inliningTarget, other, T_AS_INTEGER_RATIO);
                validateAsIntegerRatioResult(ratioTuple, inliningTarget, raiseNode);

                Object numerator = tupleGetItem.execute(inliningTarget, ratioTuple, 0);
                Object denominator = tupleGetItem.execute(inliningTarget, ratioTuple, 1);

                Object multiplyResult = multiplyNode.execute(frame, selfAsMicroseconds, numerator);
                PInt microseconds = divideNearest(inliningTarget, multiplyResult, denominator);

                return newNode.executeBuiltin(inliningTarget, 0, 0, microseconds, 0, 0, 0, 0);
            } else {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }
    }

    @Slot(value = SlotKind.nb_true_divide, isComplex = true)
    @GenerateUncached
    @GenerateNodeFactory
    abstract static class DivNode extends BinaryOpBuiltinNode {

        @Specialization
        static Object div(VirtualFrame frame, Object left, Object right,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode,
                        @Cached PyLongCheckNode longCheckNode,
                        @Cached PyFloatCheckNode floatCheckNode,
                        @Cached PyObjectCallMethodObjArgs callMethodObjArgs,
                        @Cached PyTupleGetItem tupleGetItem,
                        @Cached PyNumberAddNode addNode,
                        @Cached PyNumberMultiplyNode multiplyNode,
                        @Cached PyNumberTrueDivideNode trueDivideNode,
                        @Cached TimeDeltaNodes.NewNode newNode,
                        @Cached TimeDeltaNodes.TimeDeltaCheckNode checkLeft,
                        @Cached TimeDeltaNodes.TimeDeltaCheckNode checkRight,
                        @Cached TimeDeltaNodes.AsManagedTimeDeltaNode asManagedTimeDeltaNode) {
            if (!checkLeft.execute(inliningTarget, left)) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            PTimeDelta self = asManagedTimeDeltaNode.execute(inliningTarget, left);
            if (checkRight.execute(inliningTarget, right)) {
                PTimeDelta otherTimeDelta = asManagedTimeDeltaNode.execute(inliningTarget, right);
                Object microsecondsSelf = toMicroseconds(self, addNode, multiplyNode);
                Object microsecondsOther = toMicroseconds(otherTimeDelta, addNode, multiplyNode);
                return trueDivideNode.execute(frame, microsecondsSelf, microsecondsOther);
            } else if (longCheckNode.execute(inliningTarget, right)) {
                Object microseconds = toMicroseconds(self, addNode, multiplyNode);
                microseconds = trueDivideNode.execute(frame, microseconds, right);
                return newNode.executeBuiltin(inliningTarget, 0, 0, microseconds, 0, 0, 0, 0);
            } else if (floatCheckNode.execute(inliningTarget, right)) {
                Object selfAsMicroseconds = toMicroseconds(self, addNode, multiplyNode);

                Object ratioTuple = callMethodObjArgs.execute(frame, inliningTarget, right, T_AS_INTEGER_RATIO);
                validateAsIntegerRatioResult(ratioTuple, inliningTarget, raiseNode);

                Object numerator = tupleGetItem.execute(inliningTarget, ratioTuple, 1);
                Object denominator = tupleGetItem.execute(inliningTarget, ratioTuple, 0);

                Object multiplyResult = multiplyNode.execute(frame, selfAsMicroseconds, numerator);
                PInt microseconds = divideNearest(inliningTarget, multiplyResult, denominator);

                return newNode.executeBuiltin(inliningTarget, 0, 0, microseconds, 0, 0, 0, 0);
            } else {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }
    }

    @Slot(value = SlotKind.nb_floor_divide, isComplex = true)
    @GenerateUncached
    @GenerateNodeFactory
    abstract static class FloorDivNode extends BinaryOpBuiltinNode {

        @Specialization
        static Object div(VirtualFrame frame, Object left, Object right,
                        @Bind Node inliningTarget,
                        @Cached PyLongCheckNode longCheckNode,
                        @Cached TimeDeltaNodes.NewNode newNode,
                        @Cached PyNumberAddNode addNode,
                        @Cached PyNumberMultiplyNode multiplyNode,
                        @Cached PyNumberFloorDivideNode floorDivideNode,
                        @Cached TimeDeltaNodes.TimeDeltaCheckNode checkLeft,
                        @Cached TimeDeltaNodes.TimeDeltaCheckNode checkRight,
                        @Cached TimeDeltaNodes.AsManagedTimeDeltaNode asManagedTimeDeltaNode) {
            if (!checkLeft.execute(inliningTarget, left)) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            PTimeDelta self = asManagedTimeDeltaNode.execute(inliningTarget, left);
            if (checkRight.execute(inliningTarget, right)) {
                PTimeDelta otherTimeDelta = asManagedTimeDeltaNode.execute(inliningTarget, right);
                Object microsecondsSelf = toMicroseconds(self, addNode, multiplyNode);
                Object microsecondsOther = toMicroseconds(otherTimeDelta, addNode, multiplyNode);
                return floorDivideNode.execute(frame, microsecondsSelf, microsecondsOther);
            } else if (longCheckNode.execute(inliningTarget, right)) {
                Object microseconds = toMicroseconds(self, addNode, multiplyNode);
                microseconds = floorDivideNode.execute(frame, microseconds, right);
                return newNode.executeBuiltin(inliningTarget, 0, 0, microseconds, 0, 0, 0, 0);
            } else {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }
    }

    @Slot(value = SlotKind.nb_divmod, isComplex = true)
    @GenerateNodeFactory
    abstract static class DivModNode extends BinaryOpBuiltinNode {

        @Specialization
        @TruffleBoundary
        static Object divmod(Object left, Object right,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language) {
            if (!TimeDeltaNodes.TimeDeltaCheckNode.executeUncached(left) || !TimeDeltaNodes.TimeDeltaCheckNode.executeUncached(right)) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            PTimeDelta self = TimeDeltaNodes.AsManagedTimeDeltaNode.executeUncached(left);
            PTimeDelta other = TimeDeltaNodes.AsManagedTimeDeltaNode.executeUncached(right);

            EncapsulatingNodeReference encapsulating = EncapsulatingNodeReference.getCurrent();
            Node encapsulatingNode = encapsulating.set(inliningTarget);
            try {
                Object microsecondsSelf = toMicrosecondsUncached(self);
                Object microsecondsOther = toMicrosecondsUncached(other);

                Object quotient = PyNumberTrueDivideNode.getUncached().execute(null, microsecondsSelf, microsecondsOther);
                Object remainder = PyNumberRemainderNode.getUncached().execute(null, microsecondsSelf, microsecondsOther);
                PTimeDelta remainderTimeDelta = TimeDeltaNodes.NewNode.getUncached().executeBuiltin(inliningTarget, 0, 0, remainder, 0, 0, 0, 0);
                Object[] arguments = new Object[]{quotient, remainderTimeDelta};
                return PFactory.createTuple(language, arguments);
            } finally {
                encapsulating.set(encapsulatingNode);
            }

        }
    }

    @Slot(value = SlotKind.nb_remainder, isComplex = true)
    @GenerateNodeFactory
    abstract static class ModNode extends BinaryOpBuiltinNode {

        @Specialization
        @TruffleBoundary
        static Object mod(Object left, Object right,
                        @Bind Node inliningTarget) {
            if (!TimeDeltaNodes.TimeDeltaCheckNode.executeUncached(left) || !TimeDeltaNodes.TimeDeltaCheckNode.executeUncached(right)) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            EncapsulatingNodeReference encapsulating = EncapsulatingNodeReference.getCurrent();
            Node encapsulatingNode = encapsulating.set(inliningTarget);
            try {
                PTimeDelta self = TimeDeltaNodes.AsManagedTimeDeltaNode.executeUncached(left);
                PTimeDelta other = TimeDeltaNodes.AsManagedTimeDeltaNode.executeUncached(right);
                Object microsecondsSelf = toMicrosecondsUncached(self);
                Object microsecondsOther = toMicrosecondsUncached(other);
                Object remainder = PyNumberRemainderNode.getUncached().execute(null, microsecondsSelf, microsecondsOther);
                return TimeDeltaNodes.NewNode.getUncached().executeBuiltin(inliningTarget, 0, 0, remainder, 0, 0, 0, 0);
            } finally {
                encapsulating.set(encapsulatingNode);
            }
        }
    }

    @Slot(value = SlotKind.nb_absolute, isComplex = true)
    @GenerateUncached
    @GenerateNodeFactory
    abstract static class AbsNode extends PythonUnaryBuiltinNode {

        @Specialization
        static PTimeDelta abs(PTimeDelta selfObj,
                        @Bind Node inliningTarget,
                        @Cached TimeDeltaNodes.NewNode newNode,
                        @Cached TimeDeltaNodes.AsManagedTimeDeltaNode asManagedTimeDeltaNode) {
            PTimeDelta self = asManagedTimeDeltaNode.execute(inliningTarget, selfObj);
            if (self.days >= 0) {
                return newNode.executeBuiltin(inliningTarget, self.days, self.seconds, self.microseconds, 0, 0, 0, 0);
            } else {
                return newNode.executeBuiltin(inliningTarget, -self.days, -self.seconds, -self.microseconds, 0, 0, 0, 0);
            }
        }
    }

    @Slot(value = SlotKind.nb_positive, isComplex = true)
    @GenerateNodeFactory
    abstract static class PosNode extends PythonUnaryBuiltinNode {

        @Specialization
        static PTimeDelta pos(PTimeDelta selfObj,
                        @Bind Node inliningTarget,
                        @Cached TimeDeltaNodes.NewNode newNode,
                        @Cached TimeDeltaNodes.AsManagedTimeDeltaNode asManagedTimeDeltaNode) {
            PTimeDelta self = asManagedTimeDeltaNode.execute(inliningTarget, selfObj);
            return newNode.executeBuiltin(inliningTarget, self.days, self.seconds, self.microseconds, 0, 0, 0, 0);
        }
    }

    @Slot(value = SlotKind.nb_negative, isComplex = true)
    @GenerateNodeFactory
    abstract static class NegNode extends PythonUnaryBuiltinNode {

        @Specialization
        static PTimeDelta neg(Object selfObj,
                        @Bind Node inliningTarget,
                        @Cached TimeDeltaNodes.NewNode newNode,
                        @Cached TimeDeltaNodes.AsManagedTimeDeltaNode asManagedTimeDeltaNode) {
            PTimeDelta self = asManagedTimeDeltaNode.execute(inliningTarget, selfObj);
            return newNode.executeBuiltin(inliningTarget, -self.days, -self.seconds, -self.microseconds, 0, 0, 0, 0);
        }
    }

    @Builtin(name = "days", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class DaysNode extends PythonUnaryBuiltinNode {

        @Specialization
        static int getDays(PTimeDelta self) {
            return self.days;
        }

        @Specialization
        static int getDays(PythonAbstractNativeObject self,
                        @Cached CStructAccess.ReadI32Node readNode) {
            return TimeDeltaNodes.AsManagedTimeDeltaNode.getDays(self, readNode);
        }
    }

    @Builtin(name = "seconds", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class SecondsNode extends PythonUnaryBuiltinNode {

        @Specialization
        static int getSeconds(PTimeDelta self) {
            return self.seconds;
        }

        @Specialization
        static int getSeconds(PythonAbstractNativeObject self,
                        @Cached CStructAccess.ReadI32Node readNode) {
            return TimeDeltaNodes.AsManagedTimeDeltaNode.getSeconds(self, readNode);
        }
    }

    @Builtin(name = "microseconds", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class MicrosecondsNode extends PythonUnaryBuiltinNode {

        @Specialization
        static int getMicroseconds(PTimeDelta self) {
            return self.microseconds;
        }

        @Specialization
        static int getMicroseconds(PythonAbstractNativeObject self,
                        @Cached CStructAccess.ReadI32Node readNode) {
            return TimeDeltaNodes.AsManagedTimeDeltaNode.getMicroseconds(self, readNode);
        }
    }

    @Builtin(name = "total_seconds", minNumOfPositionalArgs = 1, doc = "Total seconds in the duration.")
    @GenerateNodeFactory
    abstract static class TotalSecondsNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object getTotalSeconds(Object selfObj,
                        @Bind Node inliningTarget,
                        @Cached TimeDeltaNodes.AsManagedTimeDeltaNode asManagedTimeDeltaNode,
                        @Cached PyNumberAddNode addNode,
                        @Cached PyNumberMultiplyNode multiplyNode,
                        @Cached PyNumberTrueDivideNode trueDivideNode) {
            PTimeDelta self = asManagedTimeDeltaNode.execute(inliningTarget, selfObj);
            Object microseconds = toMicroseconds(self, addNode, multiplyNode);
            return trueDivideNode.execute(null, microseconds, 1_000_000);
        }
    }

    private static Object toMicroseconds(PTimeDelta timeDelta, PyNumberAddNode addNode, PyNumberMultiplyNode multiplyNode) {
        Object x = multiplyNode.execute(null, timeDelta.days, 24 * 3600);
        x = addNode.execute(null, x, timeDelta.seconds);
        x = multiplyNode.execute(null, x, 1_000_000);
        return addNode.execute(null, x, timeDelta.microseconds);
    }

    private static Object toMicrosecondsUncached(PTimeDelta timeDelta) {
        return toMicroseconds(timeDelta, PyNumberAddNode.getUncached(), PyNumberMultiplyNode.getUncached());
    }

    /**
     * Check if float.as_integer_ratio returns correct result (CPython:
     * get_float_as_integer_ratio())
     */
    private static void validateAsIntegerRatioResult(Object object, Node inliningTarget, PRaiseNode raiseNode) {
        if (!(object instanceof PTuple)) {
            throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.UNEXPECTED_RETURN_TYPE_FROM_AS_INTEGER_RATIO_EXPECTED_TUPLE_GOT_P, object);
        }
        if (PyTupleSizeNode.executeUncached(object) != 2) {
            throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.AS_INTEGER_RATION_MUST_RETURN_A_2_TUPLE, object);
        }
    }
}
