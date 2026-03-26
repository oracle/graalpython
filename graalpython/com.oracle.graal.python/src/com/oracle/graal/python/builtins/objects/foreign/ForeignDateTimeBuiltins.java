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

import static com.oracle.graal.python.nodes.SpecialMethodNames.J___FORMAT__;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.datetime.DateTimeNodes;
import com.oracle.graal.python.builtins.modules.datetime.DatetimeModuleBuiltins;
import com.oracle.graal.python.builtins.modules.datetime.PDateTime;
import com.oracle.graal.python.builtins.modules.datetime.PTimeDelta;
import com.oracle.graal.python.builtins.modules.datetime.TemporalNodes;
import com.oracle.graal.python.builtins.modules.datetime.TimeDeltaNodes;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryOp.BinaryOpBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotHashFun.HashBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotRichCompare.RichCmpBuiltinNode;
import com.oracle.graal.python.lib.PyDateCheckNode;
import com.oracle.graal.python.lib.PyDateTimeCheckNode;
import com.oracle.graal.python.lib.PyDeltaCheckNode;
import com.oracle.graal.python.lib.PyLongAsLongNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectHashNode;
import com.oracle.graal.python.lib.PyObjectStrAsObjectNode;
import com.oracle.graal.python.lib.RichCmpOp;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.ForeignDateTime)
public final class ForeignDateTimeBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = ForeignDateTimeBuiltinsSlotsGen.SLOTS;

    private static final TruffleString T_ASTIMEZONE = tsLiteral("astimezone");
    private static final TruffleString T_DATE = tsLiteral("date");
    private static final TruffleString T_DST = tsLiteral("dst");
    private static final TruffleString T_ISOFORMAT = tsLiteral("isoformat");
    private static final TruffleString T_STRFTIME = tsLiteral("strftime");
    private static final TruffleString T_TIME = tsLiteral("time");
    private static final TruffleString T_TIMETZ = tsLiteral("timetz");
    private static final TruffleString T_TIMESTAMP = tsLiteral("timestamp");
    private static final TruffleString T_TIMETUPLE = tsLiteral("timetuple");
    private static final TruffleString T_TZNAME = tsLiteral("tzname");
    private static final TruffleString T_UTCOFFSET = tsLiteral("utcoffset");
    private static final TruffleString T_UTCTIMETUPLE = tsLiteral("utctimetuple");

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ForeignDateTimeBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.tp_repr, isComplex = true)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        static TruffleString repr(Object selfObj,
                        @Bind Node inliningTarget,
                        @Cached TemporalNodes.ReadDateTimeValueNode readDateTimeValueNode) {
            TemporalNodes.DateTimeValue self = readDateTimeValueNode.execute(inliningTarget, selfObj);
            TruffleString typeName = TypeNodes.GetTpNameNode.executeUncached(GetClassNode.executeUncached(selfObj));
            String string = String.format("%s(%d, %d, %d, %d, %d, %d, %d)", typeName, self.year, self.month, self.day, self.hour, self.minute, self.second, self.microsecond);
            return TruffleString.FromJavaStringNode.getUncached().execute(string, TS_ENCODING);
        }
    }

    @Slot(value = SlotKind.tp_str, isComplex = true)
    @GenerateNodeFactory
    abstract static class StrNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object str(Object selfObj,
                        @Bind Node inliningTarget,
                        @Cached TemporalNodes.ReadDateTimeValueNode readDateTimeValueNode,
                        @Cached PyObjectStrAsObjectNode strAsObjectNode) {
            return strAsObjectNode.execute(inliningTarget, toPythonDateTime(readDateTimeValueNode.execute(inliningTarget, selfObj), inliningTarget));
        }
    }

    @Slot(value = SlotKind.tp_richcompare, isComplex = true)
    @GenerateNodeFactory
    abstract static class RichCmpNode extends RichCmpBuiltinNode {
        @Specialization
        static Object richCmp(VirtualFrame frame, Object selfObj, Object otherObj, RichCmpOp op,
                        @Bind Node inliningTarget,
                        @Cached PyDateTimeCheckNode dateTimeLikeCheckNode,
                        @Cached PyDateCheckNode dateLikeCheckNode,
                        @Cached TemporalNodes.ReadDateTimeValueNode readDateTimeValueNode,
                        @Cached PyObjectCallMethodObjArgs callMethodObjArgs,
                        @Cached PRaiseNode raiseNode) {
            if (!dateTimeLikeCheckNode.execute(inliningTarget, otherObj)) {
                if (dateLikeCheckNode.execute(inliningTarget, otherObj)) {
                    if (op == RichCmpOp.Py_EQ) {
                        return false;
                    } else if (op == RichCmpOp.Py_NE) {
                        return true;
                    } else {
                        throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.CANT_COMPARE, selfObj, otherObj);
                    }
                }
                return PNotImplemented.NOT_IMPLEMENTED;
            }

            PDateTime self = toPythonDateTime(readDateTimeValueNode.execute(inliningTarget, selfObj), inliningTarget);
            PDateTime other = toPythonDateTime(readDateTimeValueNode.execute(inliningTarget, otherObj), inliningTarget);
            if (self.tzInfo == other.tzInfo) {
                return op.compareResultToBool(compareDateTimeComponents(self, other));
            }

            PTimeDelta selfUtcOffset = DatetimeModuleBuiltins.callUtcOffset(self.tzInfo, self, frame, inliningTarget, callMethodObjArgs, raiseNode);
            PTimeDelta otherUtcOffset = DatetimeModuleBuiltins.callUtcOffset(other.tzInfo, other, frame, inliningTarget, callMethodObjArgs, raiseNode);
            if (Objects.equals(selfUtcOffset, otherUtcOffset)) {
                return op.compareResultToBool(compareDateTimeComponents(self, other));
            }
            if ((selfUtcOffset == null) != (otherUtcOffset == null)) {
                if (op == RichCmpOp.Py_EQ) {
                    return false;
                } else if (op == RichCmpOp.Py_NE) {
                    return true;
                } else {
                    throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.CANT_COMPARE_OFFSET_NAIVE_AND_OFFSET_AWARE_DATETIMES);
                }
            }
            LocalDateTime selfUtc = subtractOffsetFromDateTime(self, selfUtcOffset);
            LocalDateTime otherUtc = subtractOffsetFromDateTime(other, otherUtcOffset);
            return op.compareResultToBool(selfUtc.compareTo(otherUtc));
        }
    }

    @Slot(value = SlotKind.tp_hash, isComplex = true)
    @GenerateNodeFactory
    abstract static class HashNode extends HashBuiltinNode {
        @Specialization
        static long hash(VirtualFrame frame, Object selfObj,
                        @Bind Node inliningTarget,
                        @Cached TemporalNodes.ReadDateTimeValueNode readDateTimeValueNode,
                        @Cached PyObjectHashNode hashNode) {
            return hashNode.execute(frame, inliningTarget, toPythonDateTime(readDateTimeValueNode.execute(inliningTarget, selfObj), inliningTarget));
        }
    }

    @Slot(value = SlotKind.nb_add, isComplex = true)
    @GenerateNodeFactory
    abstract static class AddNode extends BinaryOpBuiltinNode {
        @Specialization
        @TruffleBoundary
        static Object add(Object left, Object right,
                        @Bind Node inliningTarget,
                        @Cached PyDateTimeCheckNode dateTimeLikeCheckNode,
                        @Cached TemporalNodes.ReadDateTimeValueNode readDateTimeValueNode) {
            Object dateTimeObj;
            Object deltaObj;
            if (dateTimeLikeCheckNode.execute(inliningTarget, left) && PyDeltaCheckNode.executeUncached(right)) {
                dateTimeObj = left;
                deltaObj = right;
            } else if (PyDeltaCheckNode.executeUncached(left) && dateTimeLikeCheckNode.execute(inliningTarget, right)) {
                dateTimeObj = right;
                deltaObj = left;
            } else {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            PDateTime date = toPythonDateTime(readDateTimeValueNode.execute(inliningTarget, dateTimeObj), inliningTarget);
            TemporalNodes.TimeDeltaValue delta = TemporalNodes.ReadTimeDeltaValueNode.executeUncached(inliningTarget, deltaObj);
            LocalDateTime adjusted = toLocalDateTime(date).plusDays(delta.days).plusSeconds(delta.seconds).plusNanos(delta.microseconds * 1_000L);
            return toPythonDateTime(adjusted, date.tzInfo, date.fold, inliningTarget);
        }
    }

    @Slot(value = SlotKind.nb_subtract, isComplex = true)
    @GenerateNodeFactory
    abstract static class SubNode extends BinaryOpBuiltinNode {
        @Specialization
        static Object sub(VirtualFrame frame, Object left, Object right,
                        @Bind Node inliningTarget,
                        @Cached PyDateTimeCheckNode dateTimeLikeCheckNode,
                        @Cached TemporalNodes.ReadDateTimeValueNode readDateTimeValueNode,
                        @Cached PyObjectCallMethodObjArgs callMethodObjArgs,
                        @Cached PRaiseNode raiseNode) {
            if (!dateTimeLikeCheckNode.execute(inliningTarget, left)) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            PDateTime self = toPythonDateTime(readDateTimeValueNode.execute(inliningTarget, left), inliningTarget);
            if (dateTimeLikeCheckNode.execute(inliningTarget, right)) {
                PDateTime other = toPythonDateTime(readDateTimeValueNode.execute(inliningTarget, right), inliningTarget);
                PTimeDelta selfOffset = DatetimeModuleBuiltins.callUtcOffset(self.tzInfo, self, frame, inliningTarget, callMethodObjArgs, raiseNode);
                PTimeDelta otherOffset = DatetimeModuleBuiltins.callUtcOffset(other.tzInfo, other, frame, inliningTarget, callMethodObjArgs, raiseNode);
                if ((selfOffset == null) != (otherOffset == null)) {
                    throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.CANNOT_SUBTRACT_OFFSET_NAIVE_AND_OFFSET_AWARE_DATETIMES);
                }
                LocalDateTime selfToCompare = selfOffset != null && self.tzInfo != other.tzInfo ? subtractOffsetFromDateTime(self, selfOffset) : toLocalDateTime(self);
                LocalDateTime otherToCompare = otherOffset != null && self.tzInfo != other.tzInfo ? subtractOffsetFromDateTime(other, otherOffset) : toLocalDateTime(other);
                long selfSeconds = selfToCompare.toEpochSecond(ZoneOffset.UTC);
                long otherSeconds = otherToCompare.toEpochSecond(ZoneOffset.UTC);
                return TimeDeltaNodes.NewNode.getUncached().execute(inliningTarget, PythonBuiltinClassType.PTimeDelta, 0, selfSeconds - otherSeconds, self.microsecond - other.microsecond, 0,
                                0, 0, 0);
            }
            if (PyDeltaCheckNode.executeUncached(right)) {
                TemporalNodes.TimeDeltaValue delta = TemporalNodes.ReadTimeDeltaValueNode.executeUncached(inliningTarget, right);
                LocalDateTime adjusted = toLocalDateTime(self).minusDays(delta.days).minusSeconds(delta.seconds).minusNanos(delta.microseconds * 1_000L);
                return toPythonDateTime(adjusted, self.tzInfo, self.fold, inliningTarget);
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
                        @Cached TemporalNodes.ReadDateTimeValueNode readDateTimeValueNode) {
            return readDateTimeValueNode.execute(inliningTarget, selfObj).year;
        }
    }

    @Builtin(name = "month", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class MonthNode extends PythonUnaryBuiltinNode {
        @Specialization
        static int month(Object selfObj,
                        @Bind Node inliningTarget,
                        @Cached TemporalNodes.ReadDateTimeValueNode readDateTimeValueNode) {
            return readDateTimeValueNode.execute(inliningTarget, selfObj).month;
        }
    }

    @Builtin(name = "day", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class DayNode extends PythonUnaryBuiltinNode {
        @Specialization
        static int day(Object selfObj,
                        @Bind Node inliningTarget,
                        @Cached TemporalNodes.ReadDateTimeValueNode readDateTimeValueNode) {
            return readDateTimeValueNode.execute(inliningTarget, selfObj).day;
        }
    }

    @Builtin(name = "hour", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class HourNode extends PythonUnaryBuiltinNode {
        @Specialization
        static int hour(Object selfObj,
                        @Bind Node inliningTarget,
                        @Cached TemporalNodes.ReadDateTimeValueNode readDateTimeValueNode) {
            return readDateTimeValueNode.execute(inliningTarget, selfObj).hour;
        }
    }

    @Builtin(name = "minute", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class MinuteNode extends PythonUnaryBuiltinNode {
        @Specialization
        static int minute(Object selfObj,
                        @Bind Node inliningTarget,
                        @Cached TemporalNodes.ReadDateTimeValueNode readDateTimeValueNode) {
            return readDateTimeValueNode.execute(inliningTarget, selfObj).minute;
        }
    }

    @Builtin(name = "second", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class SecondNode extends PythonUnaryBuiltinNode {
        @Specialization
        static int second(Object selfObj,
                        @Bind Node inliningTarget,
                        @Cached TemporalNodes.ReadDateTimeValueNode readDateTimeValueNode) {
            return readDateTimeValueNode.execute(inliningTarget, selfObj).second;
        }
    }

    @Builtin(name = "microsecond", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class MicrosecondNode extends PythonUnaryBuiltinNode {
        @Specialization
        static int microsecond(Object selfObj,
                        @Bind Node inliningTarget,
                        @Cached TemporalNodes.ReadDateTimeValueNode readDateTimeValueNode) {
            return readDateTimeValueNode.execute(inliningTarget, selfObj).microsecond;
        }
    }

    @Builtin(name = "tzinfo", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class TzInfoNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object tzinfo(Object selfObj,
                        @Bind Node inliningTarget,
                        @Cached TemporalNodes.ReadDateTimeValueNode readDateTimeValueNode) {
            TemporalNodes.DateTimeValue self = readDateTimeValueNode.execute(inliningTarget, selfObj);
            Object tzInfo = TemporalNodes.toPythonTzInfo(self.tzInfo, self.zoneId, inliningTarget);
            return tzInfo != null ? tzInfo : PNone.NONE;
        }
    }

    @Builtin(name = "fold", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class FoldNode extends PythonUnaryBuiltinNode {
        @Specialization
        static int fold(Object selfObj,
                        @Bind Node inliningTarget,
                        @Cached TemporalNodes.ReadDateTimeValueNode readDateTimeValueNode) {
            return readDateTimeValueNode.execute(inliningTarget, selfObj).fold;
        }
    }

    @Builtin(name = "date", minNumOfPositionalArgs = 1, parameterNames = {"self"})
    @GenerateNodeFactory
    abstract static class DateNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object date(Object selfObj,
                        @Bind Node inliningTarget,
                        @Cached TemporalNodes.ReadDateTimeValueNode readDateTimeValueNode) {
            return PyObjectCallMethodObjArgs.executeUncached(toPythonDateTime(readDateTimeValueNode.execute(inliningTarget, selfObj), inliningTarget), T_DATE);
        }
    }

    @Builtin(name = "time", minNumOfPositionalArgs = 1, parameterNames = {"self"})
    @GenerateNodeFactory
    abstract static class TimeNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object time(Object selfObj,
                        @Bind Node inliningTarget,
                        @Cached TemporalNodes.ReadDateTimeValueNode readDateTimeValueNode) {
            return PyObjectCallMethodObjArgs.executeUncached(toPythonDateTime(readDateTimeValueNode.execute(inliningTarget, selfObj), inliningTarget), T_TIME);
        }
    }

    @Builtin(name = "timetz", minNumOfPositionalArgs = 1, parameterNames = {"self"})
    @GenerateNodeFactory
    abstract static class TimeTzNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object timetz(Object selfObj,
                        @Bind Node inliningTarget,
                        @Cached TemporalNodes.ReadDateTimeValueNode readDateTimeValueNode) {
            return PyObjectCallMethodObjArgs.executeUncached(toPythonDateTime(readDateTimeValueNode.execute(inliningTarget, selfObj), inliningTarget), T_TIMETZ);
        }
    }

    @Builtin(name = "replace", minNumOfPositionalArgs = 1, parameterNames = {"self", "year", "month", "day", "hour", "minute", "second", "microsecond", "tzinfo"}, keywordOnlyNames = {"fold"})
    @GenerateNodeFactory
    abstract static class ReplaceNode extends PythonBuiltinNode {
        @Specialization
        static Object replace(VirtualFrame frame, Object selfObj, Object yearObject, Object monthObject, Object dayObject, Object hourObject, Object minuteObject, Object secondObject,
                        Object microsecondObject, Object tzInfoObject, Object foldObject,
                        @Bind Node inliningTarget,
                        @Cached TemporalNodes.ReadDateTimeValueNode readDateTimeValueNode,
                        @Cached PyLongAsLongNode asLongNode,
                        @Cached DateTimeNodes.NewNode newDateTimeNode) {
            TemporalNodes.DateTimeValue self = readDateTimeValueNode.execute(inliningTarget, selfObj);
            long year = yearObject == PNone.NO_VALUE ? self.year : asLongNode.execute(frame, inliningTarget, yearObject);
            long month = monthObject == PNone.NO_VALUE ? self.month : asLongNode.execute(frame, inliningTarget, monthObject);
            long day = dayObject == PNone.NO_VALUE ? self.day : asLongNode.execute(frame, inliningTarget, dayObject);
            long hour = hourObject == PNone.NO_VALUE ? self.hour : asLongNode.execute(frame, inliningTarget, hourObject);
            long minute = minuteObject == PNone.NO_VALUE ? self.minute : asLongNode.execute(frame, inliningTarget, minuteObject);
            long second = secondObject == PNone.NO_VALUE ? self.second : asLongNode.execute(frame, inliningTarget, secondObject);
            long microsecond = microsecondObject == PNone.NO_VALUE ? self.microsecond : asLongNode.execute(frame, inliningTarget, microsecondObject);
            Object tzInfo;
            if (tzInfoObject == PNone.NO_VALUE) {
                tzInfo = TemporalNodes.toPythonTzInfo(self.tzInfo, self.zoneId, inliningTarget);
            } else if (tzInfoObject == PNone.NONE) {
                tzInfo = null;
            } else {
                tzInfo = tzInfoObject;
            }
            long fold = foldObject == PNone.NO_VALUE ? self.fold : asLongNode.execute(frame, inliningTarget, foldObject);
            return newDateTimeNode.execute(inliningTarget, PythonBuiltinClassType.PDateTime, year, month, day, hour, minute, second, microsecond, tzInfo != null ? tzInfo : PNone.NONE, fold);
        }
    }

    @Builtin(name = "isoformat", minNumOfPositionalArgs = 1, parameterNames = {"self", "sep", "timespec"})
    @GenerateNodeFactory
    abstract static class IsoFormatNode extends PythonBuiltinNode {
        @Specialization
        static Object isoformat(Object selfObj, Object sepObj, Object timespecObj,
                        @Bind Node inliningTarget,
                        @Cached TemporalNodes.ReadDateTimeValueNode readDateTimeValueNode) {
            if (sepObj == PNone.NO_VALUE && timespecObj == PNone.NO_VALUE) {
                return PyObjectCallMethodObjArgs.executeUncached(toPythonDateTime(readDateTimeValueNode.execute(inliningTarget, selfObj), inliningTarget), T_ISOFORMAT);
            }
            if (timespecObj == PNone.NO_VALUE) {
                return PyObjectCallMethodObjArgs.executeUncached(toPythonDateTime(readDateTimeValueNode.execute(inliningTarget, selfObj), inliningTarget), T_ISOFORMAT, sepObj);
            }
            return PyObjectCallMethodObjArgs.executeUncached(toPythonDateTime(readDateTimeValueNode.execute(inliningTarget, selfObj), inliningTarget), T_ISOFORMAT, sepObj, timespecObj);
        }
    }

    @Builtin(name = "utcoffset", minNumOfPositionalArgs = 1, parameterNames = {"$self"})
    @GenerateNodeFactory
    abstract static class UtcOffsetNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object utcoffset(Object selfObj,
                        @Bind Node inliningTarget,
                        @Cached TemporalNodes.ReadDateTimeValueNode readDateTimeValueNode) {
            return PyObjectCallMethodObjArgs.executeUncached(toPythonDateTime(readDateTimeValueNode.execute(inliningTarget, selfObj), inliningTarget), T_UTCOFFSET);
        }
    }

    @Builtin(name = "dst", minNumOfPositionalArgs = 1, parameterNames = {"$self"})
    @GenerateNodeFactory
    abstract static class DstNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object dst(Object selfObj,
                        @Bind Node inliningTarget,
                        @Cached TemporalNodes.ReadDateTimeValueNode readDateTimeValueNode) {
            return PyObjectCallMethodObjArgs.executeUncached(toPythonDateTime(readDateTimeValueNode.execute(inliningTarget, selfObj), inliningTarget), T_DST);
        }
    }

    @Builtin(name = "tzname", minNumOfPositionalArgs = 1, parameterNames = {"$self"})
    @GenerateNodeFactory
    abstract static class TzNameNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object tzname(Object selfObj,
                        @Bind Node inliningTarget,
                        @Cached TemporalNodes.ReadDateTimeValueNode readDateTimeValueNode) {
            return PyObjectCallMethodObjArgs.executeUncached(toPythonDateTime(readDateTimeValueNode.execute(inliningTarget, selfObj), inliningTarget), T_TZNAME);
        }
    }

    @Builtin(name = "timetuple", minNumOfPositionalArgs = 1, parameterNames = {"self"})
    @GenerateNodeFactory
    abstract static class TimeTupleNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object timetuple(Object selfObj,
                        @Bind Node inliningTarget,
                        @Cached TemporalNodes.ReadDateTimeValueNode readDateTimeValueNode) {
            return PyObjectCallMethodObjArgs.executeUncached(toPythonDateTime(readDateTimeValueNode.execute(inliningTarget, selfObj), inliningTarget), T_TIMETUPLE);
        }
    }

    @Builtin(name = "utctimetuple", minNumOfPositionalArgs = 1, parameterNames = {"self"})
    @GenerateNodeFactory
    abstract static class UtcTimeTupleNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object utctimetuple(Object selfObj,
                        @Bind Node inliningTarget,
                        @Cached TemporalNodes.ReadDateTimeValueNode readDateTimeValueNode) {
            return PyObjectCallMethodObjArgs.executeUncached(toPythonDateTime(readDateTimeValueNode.execute(inliningTarget, selfObj), inliningTarget), T_UTCTIMETUPLE);
        }
    }

    @Builtin(name = "timestamp", minNumOfPositionalArgs = 1, parameterNames = {"self"})
    @GenerateNodeFactory
    abstract static class TimestampNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object timestamp(Object selfObj,
                        @Bind Node inliningTarget,
                        @Cached TemporalNodes.ReadDateTimeValueNode readDateTimeValueNode) {
            return PyObjectCallMethodObjArgs.executeUncached(toPythonDateTime(readDateTimeValueNode.execute(inliningTarget, selfObj), inliningTarget), T_TIMESTAMP);
        }
    }

    @Builtin(name = "astimezone", minNumOfPositionalArgs = 1, parameterNames = {"self", "tz"})
    @GenerateNodeFactory
    abstract static class AsTimeZoneNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object astimezone(Object selfObj, Object tzObj,
                        @Bind Node inliningTarget,
                        @Cached TemporalNodes.ReadDateTimeValueNode readDateTimeValueNode) {
            if (tzObj == PNone.NO_VALUE) {
                return PyObjectCallMethodObjArgs.executeUncached(toPythonDateTime(readDateTimeValueNode.execute(inliningTarget, selfObj), inliningTarget), T_ASTIMEZONE);
            }
            return PyObjectCallMethodObjArgs.executeUncached(toPythonDateTime(readDateTimeValueNode.execute(inliningTarget, selfObj), inliningTarget), T_ASTIMEZONE, tzObj);
        }
    }

    @Builtin(name = "strftime", minNumOfPositionalArgs = 2, parameterNames = {"self", "format"})
    @GenerateNodeFactory
    abstract static class StrFTimeNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object strftime(Object selfObj, Object formatObj,
                        @Bind Node inliningTarget,
                        @Cached TemporalNodes.ReadDateTimeValueNode readDateTimeValueNode,
                        @Cached CastToTruffleStringNode castToTruffleStringNode) {
            TruffleString format = castToTruffleStringNode.execute(inliningTarget, formatObj);
            return PyObjectCallMethodObjArgs.executeUncached(toPythonDateTime(readDateTimeValueNode.execute(inliningTarget, selfObj), inliningTarget), T_STRFTIME, format);
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

    @TruffleBoundary
    private static int compareDateTimeComponents(PDateTime self, PDateTime other) {
        return java.util.Arrays.compare(new int[]{self.year, self.month, self.day, self.hour, self.minute, self.second, self.microsecond},
                        new int[]{other.year, other.month, other.day, other.hour, other.minute, other.second, other.microsecond});
    }

    @TruffleBoundary
    private static LocalDateTime subtractOffsetFromDateTime(PDateTime self, PTimeDelta offset) {
        return toLocalDateTime(self).minusDays(offset.days).minusSeconds(offset.seconds).minusNanos(offset.microseconds * 1_000L);
    }

    @TruffleBoundary
    private static LocalDateTime toLocalDateTime(PDateTime self) {
        return LocalDateTime.of(self.year, self.month, self.day, self.hour, self.minute, self.second, self.microsecond * 1_000);
    }

    private static PDateTime toPythonDateTime(TemporalNodes.DateTimeValue value, Node inliningTarget) {
        Object tzInfo = TemporalNodes.toPythonTzInfo(value.tzInfo, value.zoneId, inliningTarget);
        return (PDateTime) DateTimeNodes.NewUnsafeNode.getUncached().execute(inliningTarget, PythonBuiltinClassType.PDateTime, value.year, value.month, value.day, value.hour, value.minute,
                        value.second, value.microsecond, tzInfo != null ? tzInfo : PNone.NONE, value.fold);
    }

    private static Object toPythonDateTime(LocalDateTime local, Object tzInfo, int fold, Node inliningTarget) {
        return DateTimeNodes.NewUnsafeNode.getUncached().execute(inliningTarget, PythonBuiltinClassType.PDateTime, local.getYear(), local.getMonthValue(), local.getDayOfMonth(),
                        local.getHour(), local.getMinute(), local.getSecond(), local.getNano() / 1_000, tzInfo != null ? tzInfo : PNone.NONE, fold);
    }
}
