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
package com.oracle.graal.python.builtins.objects.foreign;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OverflowError;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___FORMAT__;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.TimeModuleBuiltins;
import com.oracle.graal.python.builtins.modules.datetime.DateNodes;
import com.oracle.graal.python.builtins.modules.datetime.PDate;
import com.oracle.graal.python.builtins.modules.datetime.PTimeDelta;
import com.oracle.graal.python.builtins.modules.datetime.TemporalNodes;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryOp.BinaryOpBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotHashFun.HashBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotRichCompare.RichCmpBuiltinNode;
import com.oracle.graal.python.lib.PyDateCheckNode;
import com.oracle.graal.python.lib.PyDeltaCheckNode;
import com.oracle.graal.python.lib.PyLongAsLongNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectHashNode;
import com.oracle.graal.python.lib.PyObjectStrAsObjectNode;
import com.oracle.graal.python.lib.RichCmpOp;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.ForeignDate)
public final class ForeignDateBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = ForeignDateBuiltinsSlotsGen.SLOTS;

    private static final int MAX_ORDINAL = 3_652_059;
    private static final TruffleString T_ISOCALENDAR = tsLiteral("isocalendar");
    private static final TruffleString T_STRFTIME = tsLiteral("strftime");

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ForeignDateBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.tp_repr, isComplex = true)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        static TruffleString repr(Object selfObj,
                        @Bind Node inliningTarget,
                        @Cached TemporalNodes.ReadDateValueNode readDateValueNode) {
            TemporalNodes.DateValue self = readDateValueNode.execute(inliningTarget, selfObj);
            TruffleString typeName = TypeNodes.GetTpNameNode.executeUncached(GetClassNode.executeUncached(selfObj));
            String string = String.format("%s(%d, %d, %d)", typeName, self.year, self.month, self.day);
            return TruffleString.FromJavaStringNode.getUncached().execute(string, TS_ENCODING);
        }
    }

    @Slot(value = SlotKind.tp_str, isComplex = true)
    @GenerateNodeFactory
    abstract static class StrNode extends PythonUnaryBuiltinNode {
        @Specialization
        static TruffleString str(Object selfObj,
                        @Bind Node inliningTarget,
                        @Cached TemporalNodes.ReadDateValueNode readDateValueNode) {
            return toIsoFormat(readDateValueNode.execute(inliningTarget, selfObj));
        }
    }

    @Slot(value = SlotKind.tp_richcompare, isComplex = true)
    @GenerateNodeFactory
    abstract static class RichCmpNode extends RichCmpBuiltinNode {
        @Specialization
        static Object richCmp(Object selfObj, Object otherObj, RichCmpOp op,
                        @Bind Node inliningTarget,
                        @Cached PyDateCheckNode dateLikeCheckNode,
                        @Cached TemporalNodes.ReadDateValueNode readDateValueNode) {
            if (!dateLikeCheckNode.execute(inliningTarget, otherObj)) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            LocalDate self = readDateValueNode.execute(inliningTarget, selfObj).toLocalDate();
            LocalDate other = readDateValueNode.execute(inliningTarget, otherObj).toLocalDate();
            return op.compareResultToBool(self.compareTo(other));
        }
    }

    @Slot(value = SlotKind.tp_hash, isComplex = true)
    @GenerateNodeFactory
    abstract static class HashNode extends HashBuiltinNode {
        @Specialization
        static long hash(VirtualFrame frame, Object selfObj,
                        @Bind Node inliningTarget,
                        @Cached TemporalNodes.ReadDateValueNode readDateValueNode,
                        @Cached PyObjectHashNode hashNode) {
            return hashNode.execute(frame, inliningTarget, toPythonDate(readDateValueNode.execute(inliningTarget, selfObj)));
        }
    }

    @Slot(value = SlotKind.nb_add, isComplex = true)
    @GenerateNodeFactory
    abstract static class AddNode extends BinaryOpBuiltinNode {
        @Specialization
        @TruffleBoundary
        static Object add(Object left, Object right,
                        @Bind Node inliningTarget,
                        @Cached PyDateCheckNode dateLikeCheckNode,
                        @Cached TemporalNodes.ReadDateValueNode readDateValueNode) {
            Object dateObj;
            Object deltaObj;
            if (dateLikeCheckNode.execute(inliningTarget, left) && PyDeltaCheckNode.executeUncached(right)) {
                dateObj = left;
                deltaObj = right;
            } else if (PyDeltaCheckNode.executeUncached(left) && dateLikeCheckNode.execute(inliningTarget, right)) {
                dateObj = right;
                deltaObj = left;
            } else {
                return PNotImplemented.NOT_IMPLEMENTED;
            }

            LocalDate date = readDateValueNode.execute(inliningTarget, dateObj).toLocalDate();
            TemporalNodes.TimeDeltaValue delta = TemporalNodes.ReadTimeDeltaValueNode.executeUncached(inliningTarget, deltaObj);
            long days = ChronoUnit.DAYS.between(LocalDate.of(1, 1, 1), date) + 1 + delta.days;
            if (days <= 0 || days > MAX_ORDINAL) {
                throw com.oracle.graal.python.nodes.PRaiseNode.raiseStatic(inliningTarget, OverflowError, ErrorMessages.DATE_VALUE_OUT_OF_RANGE);
            }
            return toPythonDate(ChronoUnit.DAYS.addTo(LocalDate.of(1, 1, 1), days - 1));
        }
    }

    @Slot(value = SlotKind.nb_subtract, isComplex = true)
    @GenerateNodeFactory
    abstract static class SubNode extends BinaryOpBuiltinNode {
        @Specialization
        @TruffleBoundary
        static Object sub(Object left, Object right,
                        @Bind Node inliningTarget,
                        @Cached PyDateCheckNode dateLikeCheckNode,
                        @Cached TemporalNodes.ReadDateValueNode readDateValueNode) {
            if (!dateLikeCheckNode.execute(inliningTarget, left)) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }

            LocalDate leftDate = readDateValueNode.execute(inliningTarget, left).toLocalDate();
            LocalDate from = LocalDate.of(1, 1, 1);
            long leftDays = ChronoUnit.DAYS.between(from, leftDate) + 1;
            if (dateLikeCheckNode.execute(inliningTarget, right)) {
                LocalDate rightDate = readDateValueNode.execute(inliningTarget, right).toLocalDate();
                long rightDays = ChronoUnit.DAYS.between(from, rightDate) + 1;
                return new PTimeDelta(PythonBuiltinClassType.PTimeDelta, PythonBuiltinClassType.PTimeDelta.getInstanceShape(PythonLanguage.get(null)), (int) (leftDays - rightDays), 0, 0);
            }
            if (PyDeltaCheckNode.executeUncached(right)) {
                TemporalNodes.TimeDeltaValue delta = TemporalNodes.ReadTimeDeltaValueNode.executeUncached(inliningTarget, right);
                long days = leftDays - delta.days;
                if (days <= 0 || days >= MAX_ORDINAL) {
                    throw com.oracle.graal.python.nodes.PRaiseNode.raiseStatic(inliningTarget, OverflowError, ErrorMessages.DATE_VALUE_OUT_OF_RANGE);
                }
                return toPythonDate(ChronoUnit.DAYS.addTo(from, days - 1));
            }
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = "year", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class YearNode extends PythonUnaryBuiltinNode {
        @Specialization
        static int year(Object selfObj,
                        @Bind Node inliningTarget,
                        @Cached TemporalNodes.ReadDateValueNode readDateValueNode) {
            return readDateValueNode.execute(inliningTarget, selfObj).year;
        }
    }

    @Builtin(name = "month", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class MonthNode extends PythonUnaryBuiltinNode {
        @Specialization
        static int month(Object selfObj,
                        @Bind Node inliningTarget,
                        @Cached TemporalNodes.ReadDateValueNode readDateValueNode) {
            return readDateValueNode.execute(inliningTarget, selfObj).month;
        }
    }

    @Builtin(name = "day", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class DayNode extends PythonUnaryBuiltinNode {
        @Specialization
        static int day(Object selfObj,
                        @Bind Node inliningTarget,
                        @Cached TemporalNodes.ReadDateValueNode readDateValueNode) {
            return readDateValueNode.execute(inliningTarget, selfObj).day;
        }
    }

    @Builtin(name = "replace", minNumOfPositionalArgs = 1, parameterNames = {"self", "year", "month", "day"})
    @GenerateNodeFactory
    abstract static class ReplaceNode extends PythonBuiltinNode {
        @Specialization
        static Object replace(VirtualFrame frame, Object selfObj, Object yearObject, Object monthObject, Object dayObject,
                        @Bind Node inliningTarget,
                        @Cached TemporalNodes.ReadDateValueNode readDateValueNode,
                        @Cached PyLongAsLongNode longAsLongNode,
                        @Cached DateNodes.NewNode newNode) {
            TemporalNodes.DateValue self = readDateValueNode.execute(inliningTarget, selfObj);
            int year = yearObject instanceof PNone ? self.year : (int) longAsLongNode.execute(frame, inliningTarget, yearObject);
            int month = monthObject instanceof PNone ? self.month : (int) longAsLongNode.execute(frame, inliningTarget, monthObject);
            int day = dayObject instanceof PNone ? self.day : (int) longAsLongNode.execute(frame, inliningTarget, dayObject);
            return newNode.execute(inliningTarget, PythonBuiltinClassType.PDate, year, month, day);
        }
    }

    @Builtin(name = "toordinal", minNumOfPositionalArgs = 1, parameterNames = {"self"})
    @GenerateNodeFactory
    abstract static class ToOrdinalNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        static long toOrdinal(Object selfObj,
                        @Bind Node inliningTarget,
                        @Cached TemporalNodes.ReadDateValueNode readDateValueNode) {
            return ChronoUnit.DAYS.between(LocalDate.of(1, 1, 1), readDateValueNode.execute(inliningTarget, selfObj).toLocalDate()) + 1;
        }
    }

    @Builtin(name = "weekday", minNumOfPositionalArgs = 1, parameterNames = {"self"})
    @GenerateNodeFactory
    abstract static class WeekDayNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        static int weekDay(Object selfObj,
                        @Bind Node inliningTarget,
                        @Cached TemporalNodes.ReadDateValueNode readDateValueNode) {
            return readDateValueNode.execute(inliningTarget, selfObj).toLocalDate().getDayOfWeek().getValue() - 1;
        }
    }

    @Builtin(name = "isoweekday", minNumOfPositionalArgs = 1, parameterNames = {"self"})
    @GenerateNodeFactory
    abstract static class IsoWeekDayNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        static int isoWeekDay(Object selfObj,
                        @Bind Node inliningTarget,
                        @Cached TemporalNodes.ReadDateValueNode readDateValueNode) {
            return readDateValueNode.execute(inliningTarget, selfObj).toLocalDate().getDayOfWeek().getValue();
        }
    }

    @Builtin(name = "isocalendar", minNumOfPositionalArgs = 1, parameterNames = {"self"})
    @GenerateNodeFactory
    abstract static class IsoCalendarNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object isoCalendar(Object selfObj,
                        @Bind Node inliningTarget,
                        @Cached TemporalNodes.ReadDateValueNode readDateValueNode) {
            return PyObjectCallMethodObjArgs.executeUncached(toPythonDate(readDateValueNode.execute(inliningTarget, selfObj)), T_ISOCALENDAR);
        }
    }

    @Builtin(name = "isoformat", minNumOfPositionalArgs = 1, parameterNames = {"self"})
    @GenerateNodeFactory
    abstract static class IsoFormatNode extends PythonUnaryBuiltinNode {
        @Specialization
        static TruffleString isoFormat(Object selfObj,
                        @Bind Node inliningTarget,
                        @Cached TemporalNodes.ReadDateValueNode readDateValueNode) {
            return toIsoFormat(readDateValueNode.execute(inliningTarget, selfObj));
        }
    }

    @Builtin(name = "ctime", minNumOfPositionalArgs = 1, parameterNames = {"self"})
    @GenerateNodeFactory
    abstract static class CTimeNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        static TruffleString ctime(Object selfObj,
                        @Bind Node inliningTarget,
                        @Cached TemporalNodes.ReadDateValueNode readDateValueNode) {
            String ctime = readDateValueNode.execute(inliningTarget, selfObj).toLocalDate().format(DateTimeFormatter.ofPattern("EEE LLL ppd 00:00:00 yyyy"));
            return TruffleString.FromJavaStringNode.getUncached().execute(ctime, TS_ENCODING);
        }
    }

    @Builtin(name = "timetuple", minNumOfPositionalArgs = 1, parameterNames = {"self"})
    @GenerateNodeFactory
    abstract static class TimeTupleNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        static PTuple timeTuple(Object selfObj,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached TemporalNodes.ReadDateValueNode readDateValueNode) {
            TemporalNodes.DateValue self = readDateValueNode.execute(inliningTarget, selfObj);
            LocalDate localDate = self.toLocalDate();
            Object[] fields = new Object[]{self.year, self.month, self.day, 0, 0, 0, localDate.getDayOfWeek().getValue() - 1, localDate.getDayOfYear(), -1};
            return PFactory.createStructSeq(language, TimeModuleBuiltins.STRUCT_TIME_DESC, fields);
        }
    }

    @Builtin(name = "strftime", minNumOfPositionalArgs = 2, parameterNames = {"self", "format"})
    @GenerateNodeFactory
    abstract static class StrFTimeNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object strftime(Object selfObj, Object formatObj,
                        @Bind Node inliningTarget,
                        @Cached TemporalNodes.ReadDateValueNode readDateValueNode,
                        @Cached CastToTruffleStringNode castToTruffleStringNode) {
            TruffleString format = castToTruffleStringNode.execute(inliningTarget, formatObj);
            return PyObjectCallMethodObjArgs.executeUncached(toPythonDate(readDateValueNode.execute(inliningTarget, selfObj)), T_STRFTIME, format);
        }
    }

    @Builtin(name = J___FORMAT__, minNumOfPositionalArgs = 2, parameterNames = {"$self", "format"})
    @GenerateNodeFactory
    abstract static class FormatNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object format(VirtualFrame frame, Object selfObj, Object formatObj,
                        @Bind Node inliningTarget,
                        @Cached PyObjectStrAsObjectNode strAsObjectNode,
                        @Cached PyObjectCallMethodObjArgs callMethodObjArgs,
                        @Cached CastToTruffleStringNode castToTruffleStringNode) {
            TruffleString format = castToTruffleStringNode.execute(inliningTarget, formatObj);
            if (format.isEmpty()) {
                return strAsObjectNode.execute(inliningTarget, selfObj);
            }
            return callMethodObjArgs.execute(frame, inliningTarget, selfObj, T_STRFTIME, format);
        }
    }

    private static PDate toPythonDate(TemporalNodes.DateValue date) {
        return (PDate) DateNodes.NewUnsafeNode.executeUncached(PythonBuiltinClassType.PDate, date.year, date.month, date.day);
    }

    private static Object toPythonDate(LocalDate date) {
        return DateNodes.NewUnsafeNode.executeUncached(PythonBuiltinClassType.PDate, date.getYear(), date.getMonthValue(), date.getDayOfMonth());
    }

    private static TruffleString toIsoFormat(TemporalNodes.DateValue date) {
        return TruffleString.FromJavaStringNode.getUncached().execute(date.toLocalDate().toString(), TS_ENCODING);
    }
}
