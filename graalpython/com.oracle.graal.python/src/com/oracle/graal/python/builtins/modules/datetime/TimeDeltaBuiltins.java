/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryOp.BinaryOpBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotHashFun;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotInquiry;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotRichCompare.RichCmpBuiltinNode;
import com.oracle.graal.python.lib.PyFloatCheckExactNode;
import com.oracle.graal.python.lib.PyLongAsIntNode;
import com.oracle.graal.python.lib.PyLongCheckNode;
import com.oracle.graal.python.lib.PyNumberDivmodNode;
import com.oracle.graal.python.lib.PyNumberMultiplyNode;
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
import com.oracle.graal.python.nodes.util.CastToJavaDoubleNode;
import com.oracle.graal.python.nodes.util.CastToJavaLongExactNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

import java.util.List;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OverflowError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ZeroDivisionError;
import static com.oracle.graal.python.nodes.BuiltinNames.T__DATETIME;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;
import static com.oracle.graal.python.nodes.BuiltinNames.T_MAX;
import static com.oracle.graal.python.nodes.BuiltinNames.T_MIN;
import static com.oracle.graal.python.nodes.BuiltinNames.T_RESOLUTION;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

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

    @Slot(value = Slot.SlotKind.tp_new, isComplex = true)
    @Slot.SlotSignature(name = "datetime.timedelta", minNumOfPositionalArgs = 1, parameterNames = {"$cls", "days", "seconds", "microseconds", "milliseconds", "minutes", "hours", "weeks"})
    @GenerateNodeFactory
    public abstract static class NewNode extends PythonBuiltinNode {

        @Specialization
        static Object newTimeDelta(Object cls, Object days, Object seconds, Object microseconds, Object milliseconds, Object minutes, Object hours, Object weeks,
                        @Bind Node inliningTarget,
                        @Cached TimeDeltaNodes.NewNode newNode) {
            return newNode.execute(inliningTarget, cls, days, seconds, microseconds, milliseconds, minutes, hours, weeks);
        }
    }

    @Slot(Slot.SlotKind.nb_bool)
    @GenerateUncached
    @GenerateNodeFactory
    abstract static class BoolNode extends TpSlotInquiry.NbBoolBuiltinNode {

        @Specialization
        static boolean bool(PTimeDelta self) {
            return self.days != 0 || self.seconds != 0 || self.microseconds != 0;
        }
    }

    @Slot(value = Slot.SlotKind.tp_repr, isComplex = true)
    @GenerateNodeFactory
    public abstract static class ReprNode extends PythonUnaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        static TruffleString repr(PTimeDelta self) {
            var builder = new StringBuilder();

            builder.append("datetime.timedelta(");

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

    @Slot(value = Slot.SlotKind.tp_str, isComplex = true)
    @GenerateNodeFactory
    public abstract static class StrNode extends PythonUnaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        static TruffleString str(PTimeDelta self) {
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
        static Object reduce(PTimeDelta self,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached GetClassNode getClassNode) {
            Object type = getClassNode.execute(inliningTarget, self);
            PTuple arguments = PFactory.createTuple(language, new Object[]{self.days, self.seconds, self.microseconds});
            return PFactory.createTuple(language, new Object[]{type, arguments});
        }
    }

    @Slot(value = Slot.SlotKind.tp_richcompare, isComplex = true)
    @GenerateNodeFactory
    abstract static class RichCmpNode extends RichCmpBuiltinNode {

        @Specialization
        static Object richCmp(PTimeDelta self, PTimeDelta other, RichCmpOp op) {
            int result = self.compareTo(other);
            return op.compareResultToBool(result);
        }

        @Fallback
        static PNotImplemented richCmp(Object self, Object other, RichCmpOp op) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Slot(value = Slot.SlotKind.tp_hash, isComplex = true)
    @GenerateNodeFactory
    abstract static class HashNode extends TpSlotHashFun.HashBuiltinNode {

        @Specialization
        static long hash(VirtualFrame frame, PTimeDelta self,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached PyObjectHashNode hashNode) {
            var content = new int[]{self.days, self.seconds, self.microseconds};
            return hashNode.execute(frame, inliningTarget, PFactory.createTuple(language, content));
        }
    }

    @Slot(value = Slot.SlotKind.nb_add, isComplex = true)
    @GenerateUncached
    @GenerateNodeFactory
    abstract static class AddNode extends BinaryOpBuiltinNode {

        @Specialization
        static Object add(PTimeDelta self, PTimeDelta other,
                        @Bind Node inliningTarget,
                        @Cached TimeDeltaNodes.NewNode newNode) {
            Object type = PythonBuiltinClassType.PTimeDelta;
            return newNode.execute(inliningTarget, type, self.days + other.days, self.seconds + other.seconds, self.microseconds + other.microseconds, 0, 0, 0, 0);
        }

        @Fallback
        Object addObject(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Slot(value = Slot.SlotKind.nb_subtract, isComplex = true)
    @GenerateUncached
    @GenerateNodeFactory
    abstract static class SubNode extends BinaryOpBuiltinNode {

        @Specialization
        static Object sub(PTimeDelta self, PTimeDelta other,
                        @Bind Node inliningTarget,
                        @Cached TimeDeltaNodes.NewNode newNode) {
            Object type = PythonBuiltinClassType.PTimeDelta;
            return newNode.execute(inliningTarget, type, self.days - other.days, self.seconds - other.seconds, self.microseconds - other.microseconds, 0, 0, 0, 0);
        }

        @Fallback
        Object subObject(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Slot(value = Slot.SlotKind.nb_multiply, isComplex = true)
    @GenerateUncached
    @GenerateNodeFactory
    abstract static class MulNode extends BinaryOpBuiltinNode {

        @Specialization
        static Object mul(VirtualFrame frame, PTimeDelta self, Object other,
                        @Bind Node inliningTarget,
                        @Cached @Shared PRaiseNode raiseNode,
                        @Cached @Shared PyLongCheckNode longCheckNode,
                        @Cached @Shared CastToJavaLongExactNode castToJavaLongExactNode,
                        @Cached @Shared PyFloatCheckExactNode floatCheckExactNode,
                        @Cached @Shared CastToJavaDoubleNode castToJavaDoubleNode,
                        @Cached @Shared PyObjectCallMethodObjArgs callMethodObjArgs,
                        @Cached @Shared PyTupleGetItem tupleGetItem,
                        @Cached @Shared PyNumberMultiplyNode numberMultiplyNode,
                        @Cached @Shared PyNumberDivmodNode numberDivmodNode,
                        @Cached @Shared PyLongAsIntNode asIntNode,
                        @Cached @Shared TimeDeltaNodes.NewNode newNode) {
            if (longCheckNode.execute(inliningTarget, other)) {
                long i = castToJavaLongExactNode.execute(inliningTarget, other);

                Object type = PythonBuiltinClassType.PTimeDelta;
                return newNode.execute(inliningTarget, type, self.days * i, self.seconds * i, self.microseconds * i, 0, 0, 0, 0);
            } else if (floatCheckExactNode.execute(inliningTarget, other)) {
                double d = castToJavaDoubleNode.execute(inliningTarget, other);

                if (Double.isNaN(d)) {
                    throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.CANNOT_CONVERT_S_TO_INT_RATIO, "NaN");
                }

                if (Double.isInfinite(d)) {
                    throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.CANNOT_CONVERT_S_TO_INT_RATIO, "Infinity");
                }

                Object type = PythonBuiltinClassType.PTimeDelta;
                return newNode.execute(inliningTarget, type, self.days * d, self.seconds * d, self.microseconds * d, 0, 0, 0, 0);
            } else if (other instanceof PFloat) {
                // it's a float's subclass - so treat it in a generic way

                long selfAsMicroseconds = toMicroseconds(self);

                Object ratioTuple = callMethodObjArgs.execute(frame, inliningTarget, other, T_AS_INTEGER_RATIO);
                validateAsIntegerRationResult(ratioTuple, inliningTarget, raiseNode);

                Object numerator = tupleGetItem.execute(inliningTarget, ratioTuple, 0);
                Object denominator = tupleGetItem.execute(inliningTarget, ratioTuple, 1);

                Object multiplyingResult = numberMultiplyNode.execute(frame, selfAsMicroseconds, numerator);
                Object divmodResult = numberDivmodNode.execute(frame, multiplyingResult, denominator);
                // TODO: use half-even rounding (see divide_nearest)
                Object divResult = tupleGetItem.execute(inliningTarget, divmodResult, 0);

                Object secondsAndMicrosecondsTuple = numberDivmodNode.execute(frame, divResult, 1_000_000);
                validateDivModResult(secondsAndMicrosecondsTuple, inliningTarget, raiseNode);

                Object secondsObject = tupleGetItem.execute(inliningTarget, secondsAndMicrosecondsTuple, 0);
                Object microsecondsObject = tupleGetItem.execute(inliningTarget, secondsAndMicrosecondsTuple, 1);

                int seconds = asIntNode.execute(frame, inliningTarget, secondsObject);
                int microseconds = asIntNode.execute(frame, inliningTarget, microsecondsObject);

                Object type = PythonBuiltinClassType.PTimeDelta;
                return newNode.execute(inliningTarget, type, 0, seconds, microseconds, 0, 0, 0, 0);
            } else {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }

        @Specialization
        static Object rmul(VirtualFrame frame, Object other, PTimeDelta self,
                        @Bind Node inliningTarget,
                        @Cached @Shared PRaiseNode raiseNode,
                        @Cached @Shared PyLongCheckNode longCheckNode,
                        @Cached @Shared CastToJavaLongExactNode castToJavaLongExactNode,
                        @Cached @Shared PyFloatCheckExactNode floatCheckExactNode,
                        @Cached @Shared CastToJavaDoubleNode castToJavaDoubleNode,
                        @Cached @Shared PyObjectCallMethodObjArgs callMethodObjArgs,
                        @Cached @Shared PyTupleGetItem tupleGetItem,
                        @Cached @Shared PyNumberMultiplyNode numberMultiplyNode,
                        @Cached @Shared PyNumberDivmodNode numberDivmodNode,
                        @Cached @Shared PyLongAsIntNode asIntNode,
                        @Cached @Shared TimeDeltaNodes.NewNode newNode) {
            return mul(frame, self, other, inliningTarget, raiseNode, longCheckNode, castToJavaLongExactNode, floatCheckExactNode, castToJavaDoubleNode, callMethodObjArgs, tupleGetItem,
                            numberMultiplyNode, numberDivmodNode, asIntNode, newNode);
        }

        @Fallback
        static Object divObject(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Slot(value = Slot.SlotKind.nb_true_divide, isComplex = true)
    @GenerateUncached
    @GenerateNodeFactory
    abstract static class DivNode extends BinaryOpBuiltinNode {

        @Specialization
        static Object div(VirtualFrame frame, PTimeDelta self, Object other,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode,
                        @Cached PyLongCheckNode longCheckNode,
                        @Cached CastToJavaLongExactNode castToJavaLongExactNode,
                        @Cached PyFloatCheckExactNode floatCheckExactNode,
                        @Cached CastToJavaDoubleNode castToJavaDoubleNode,
                        @Cached PyObjectCallMethodObjArgs callMethodObjArgs,
                        @Cached PyTupleGetItem tupleGetItem,
                        @Cached PyNumberMultiplyNode numberMultiplyNode,
                        @Cached PyNumberDivmodNode numberDivmodNode,
                        @Cached PyLongAsIntNode asIntNode,
                        @Cached TimeDeltaNodes.NewNode newNode) {
            if (other instanceof PTimeDelta otherTimeDelta) {
                long microsecondsSelf = toMicroseconds(self);
                long microsecondsOther = toMicroseconds(otherTimeDelta);

                if (microsecondsOther == 0) {
                    throw raiseNode.raise(inliningTarget, ZeroDivisionError, ErrorMessages.INTEGER_DIVISION_OR_MODULE_BY_ZERO);
                }

                return (double) microsecondsSelf / microsecondsOther;
            } else if (longCheckNode.execute(inliningTarget, other)) {
                long i = castToJavaLongExactNode.execute(inliningTarget, other);

                if (i == 0) {
                    throw raiseNode.raise(inliningTarget, ZeroDivisionError, ErrorMessages.INTEGER_DIVISION_OR_MODULE_BY_ZERO);
                }

                long microseconds = toMicroseconds(self);
                Object type = PythonBuiltinClassType.PTimeDelta;
                return newNode.execute(inliningTarget, type, 0, 0, (double) microseconds / i, 0, 0, 0, 0);
            } else if (floatCheckExactNode.execute(inliningTarget, other)) {
                double d = castToJavaDoubleNode.execute(inliningTarget, other);

                if (Double.isNaN(d)) {
                    throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.CANNOT_CONVERT_S_TO_INT_RATIO, "NaN");
                }

                if (Double.isInfinite(d)) {
                    throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.CANNOT_CONVERT_S_TO_INT_RATIO, "Infinity");
                }

                if (Math.abs(d) < 2 * Double.MIN_VALUE) {
                    // d = 0.0
                    throw raiseNode.raise(inliningTarget, ZeroDivisionError, ErrorMessages.INTEGER_DIVISION_OR_MODULE_BY_ZERO);
                }

                long microseconds = toMicroseconds(self);

                // mimic CPython behavior
                double ratio = (double) microseconds / d;
                if (ratio > Long.MAX_VALUE || ratio < Long.MIN_VALUE) {
                    throw raiseNode.raise(inliningTarget, OverflowError, ErrorMessages.PYTHON_INT_TOO_LARGE_TO_CONVERT_TO_C_INT);
                }

                Object type = PythonBuiltinClassType.PTimeDelta;
                return newNode.execute(inliningTarget, type, 0, 0, ratio, 0, 0, 0, 0);
            } else if (other instanceof PFloat) {
                // it's a float's subclass - so treat it in a generic way

                long selfAsMicroseconds = toMicroseconds(self);

                Object ratioTuple = callMethodObjArgs.execute(frame, inliningTarget, other, T_AS_INTEGER_RATIO);
                validateAsIntegerRationResult(ratioTuple, inliningTarget, raiseNode);

                Object numerator = tupleGetItem.execute(inliningTarget, ratioTuple, 0);
                Object denominator = tupleGetItem.execute(inliningTarget, ratioTuple, 1);

                Object multiplyingResult = numberMultiplyNode.execute(frame, selfAsMicroseconds, denominator);
                Object divmodResult = numberDivmodNode.execute(frame, multiplyingResult, numerator);
                // TODO: use half-even rounding (see divide_nearest)
                Object divResult = tupleGetItem.execute(inliningTarget, divmodResult, 0);

                Object secondsAndMicrosecondsTuple = numberDivmodNode.execute(frame, divResult, 1_000_000);
                validateDivModResult(secondsAndMicrosecondsTuple, inliningTarget, raiseNode);

                Object secondsObject = tupleGetItem.execute(inliningTarget, secondsAndMicrosecondsTuple, 0);
                Object microsecondsObject = tupleGetItem.execute(inliningTarget, secondsAndMicrosecondsTuple, 1);

                int seconds = asIntNode.execute(frame, inliningTarget, secondsObject);
                int microseconds = asIntNode.execute(frame, inliningTarget, microsecondsObject);

                Object type = PythonBuiltinClassType.PTimeDelta;
                return newNode.execute(inliningTarget, type, 0, seconds, microseconds, 0, 0, 0, 0);
            } else {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }

        @Fallback
        static Object divObject(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Slot(value = Slot.SlotKind.nb_floor_divide, isComplex = true)
    @GenerateUncached
    @GenerateNodeFactory
    abstract static class FloorDivNode extends BinaryOpBuiltinNode {

        @Specialization
        static Object div(PTimeDelta self, Object other,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode,
                        @Cached PyLongCheckNode longCheckNode,
                        @Cached TimeDeltaNodes.NewNode newNode,
                        @Cached CastToJavaLongExactNode castToJavaLongExactNode) {
            if (other instanceof PTimeDelta otherTimeDelta) {
                long microsecondsSelf = toMicroseconds(self);
                long microsecondsOther = toMicroseconds(otherTimeDelta);

                if (microsecondsOther == 0) {
                    throw raiseNode.raise(inliningTarget, ZeroDivisionError, ErrorMessages.INTEGER_DIVISION_OR_MODULE_BY_ZERO);
                }

                return microsecondsSelf / microsecondsOther;
            } else if (longCheckNode.execute(inliningTarget, other)) {
                long i = castToJavaLongExactNode.execute(inliningTarget, other);

                if (i == 0) {
                    throw PRaiseNode.raiseStatic(inliningTarget, ZeroDivisionError, ErrorMessages.INTEGER_DIVISION_OR_MODULE_BY_ZERO);
                }

                Object type = PythonBuiltinClassType.PTimeDelta;
                long microseconds = toMicroseconds(self);
                return newNode.execute(inliningTarget, type, 0, 0, microseconds / i, 0, 0, 0, 0);
            } else {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }

        @Fallback
        Object divObject(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Slot(value = Slot.SlotKind.nb_divmod, isComplex = true)
    @GenerateNodeFactory
    abstract static class DivModNode extends BinaryOpBuiltinNode {

        @Specialization
        @TruffleBoundary
        static PTuple divmod(PTimeDelta self, PTimeDelta other,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language) {
            long microsecondsSelf = toMicroseconds(self);
            long microsecondsOther = toMicroseconds(other);

            if (microsecondsOther == 0) {
                throw PRaiseNode.raiseStatic(inliningTarget, ZeroDivisionError, ErrorMessages.INTEGER_DIVISION_OR_MODULE_BY_ZERO);
            }

            long quotient = Math.floorDiv(microsecondsSelf, microsecondsOther);
            long reminder = Math.floorMod(microsecondsSelf, microsecondsOther);
            Object type = PythonBuiltinClassType.PTimeDelta;
            PTimeDelta reminderTimeDelta = TimeDeltaNodes.NewNode.getUncached().execute(inliningTarget, type, 0, 0, reminder, 0, 0, 0, 0);

            Object[] arguments = new Object[]{quotient, reminderTimeDelta};
            return PFactory.createTuple(language, arguments);
        }

        @Fallback
        Object divmodObject(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Slot(value = Slot.SlotKind.nb_remainder, isComplex = true)
    @GenerateNodeFactory
    abstract static class ModNode extends BinaryOpBuiltinNode {

        @Specialization
        @TruffleBoundary
        static PTimeDelta mod(PTimeDelta self, PTimeDelta other,
                        @Bind Node inliningTarget) {
            long microsecondsSelf = toMicroseconds(self);
            long microsecondsOther = toMicroseconds(other);

            if (microsecondsOther == 0) {
                throw PRaiseNode.raiseStatic(inliningTarget, ZeroDivisionError, ErrorMessages.INTEGER_MODULE_BY_ZERO);
            }

            long reminder = Math.floorMod(microsecondsSelf, microsecondsOther);
            Object type = PythonBuiltinClassType.PTimeDelta;
            return TimeDeltaNodes.NewNode.getUncached().execute(inliningTarget, type, 0, 0, reminder, 0, 0, 0, 0);
        }

        @Fallback
        Object modObject(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Slot(value = Slot.SlotKind.nb_absolute, isComplex = true)
    @GenerateUncached
    @GenerateNodeFactory
    abstract static class AbsNode extends PythonUnaryBuiltinNode {

        @Specialization
        static PTimeDelta abs(PTimeDelta self,
                        @Bind Node inliningTarget,
                        @Cached TimeDeltaNodes.NewNode newNode) {
            Object type = PythonBuiltinClassType.PTimeDelta;

            if (self.days >= 0) {
                return newNode.execute(inliningTarget, type, self.days, self.seconds, self.microseconds, 0, 0, 0, 0);
            } else {
                return newNode.execute(inliningTarget, type, -self.days, -self.seconds, -self.microseconds, 0, 0, 0, 0);
            }
        }
    }

    @Slot(value = Slot.SlotKind.nb_positive, isComplex = true)
    @GenerateNodeFactory
    abstract static class PosNode extends PythonUnaryBuiltinNode {

        @Specialization
        static PTimeDelta pos(PTimeDelta self,
                        @Bind Node inliningTarget,
                        @Cached TimeDeltaNodes.NewNode newNode) {
            Object type = PythonBuiltinClassType.PTimeDelta;
            return newNode.execute(inliningTarget, type, self.days, self.seconds, self.microseconds, 0, 0, 0, 0);
        }
    }

    @Slot(value = Slot.SlotKind.nb_negative, isComplex = true)
    @GenerateNodeFactory
    abstract static class NegNode extends PythonUnaryBuiltinNode {

        @Specialization
        static PTimeDelta neg(PTimeDelta self,
                        @Bind Node inliningTarget,
                        @Cached TimeDeltaNodes.NewNode newNode) {
            Object type = PythonBuiltinClassType.PTimeDelta;
            return newNode.execute(inliningTarget, type, -self.days, -self.seconds, -self.microseconds, 0, 0, 0, 0);
        }
    }

    @Builtin(name = "days", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class DaysNode extends PythonUnaryBuiltinNode {

        @Specialization
        static int getDays(PTimeDelta self) {
            return self.days;
        }
    }

    @Builtin(name = "seconds", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class SecondsNode extends PythonUnaryBuiltinNode {

        @Specialization
        static int getSeconds(PTimeDelta self) {
            return self.seconds;
        }
    }

    @Builtin(name = "microseconds", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class MicrosecondsNode extends PythonUnaryBuiltinNode {

        @Specialization
        static int getMicroseconds(PTimeDelta self) {
            return self.microseconds;
        }
    }

    @Builtin(name = "total_seconds", minNumOfPositionalArgs = 1, doc = "Total seconds in the duration.")
    @GenerateNodeFactory
    abstract static class TotalSecondsNode extends PythonUnaryBuiltinNode {

        @Specialization
        static double getTotalSeconds(PTimeDelta self) {
            return ((double) ((long) self.days * 24 * 3600 * 1_000_000 +
                            (long) self.seconds * 1_000_000 +
                            (long) self.microseconds)) / 1_000_000;
        }
    }

    private static long toMicroseconds(PTimeDelta timeDelta) {
        return (long) timeDelta.days * 24 * 3600 * 1_000_000 +
                        (long) timeDelta.seconds * 1_000_000 +
                        (long) timeDelta.microseconds;
    }

    /**
     * Check if float.as_integer_ratio returns correct result (CPython:
     * get_float_as_integer_ratio())
     */
    private static void validateAsIntegerRationResult(Object object, Node inliningTarget, PRaiseNode raiseNode) {
        if (!(object instanceof PTuple)) {
            throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.UNEXPECTED_RETURN_TYPE_FROM_AS_INTEGER_RATION_EXPECTED_TUPLE_GOT_P, object);
        }
        if (PyTupleSizeNode.executeUncached(object) != 2) {
            throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.AS_INTEGER_RATION_MUST_RETURN_A_2_TUPLE, object);
        }
    }

    /**
     * Check whether divmod() returns correct result (CPython: checked_divmod())
     */
    private static void validateDivModResult(Object object, Node inliningTarget, PRaiseNode raiseNode) {
        if (!(object instanceof PTuple)) {
            throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.DIVMOD_RETURNED_NON_TUPLE_P, object);
        }

        int tupleSize = PyTupleSizeNode.executeUncached(object);
        if (tupleSize != 2) {
            throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.DIVMOD_RETURNED_A_TUPLE_OF_SIZE_D, tupleSize);
        }
    }
}
