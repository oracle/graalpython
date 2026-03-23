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

import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.datetime.DatetimeModuleBuiltins;
import com.oracle.graal.python.builtins.modules.datetime.PTime;
import com.oracle.graal.python.builtins.modules.datetime.PTimeDelta;
import com.oracle.graal.python.builtins.modules.datetime.TemporalNodes;
import com.oracle.graal.python.builtins.modules.datetime.TimeDeltaNodes;
import com.oracle.graal.python.builtins.modules.datetime.TimeNodes;
import com.oracle.graal.python.builtins.modules.datetime.TimeZoneNodes;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotHashFun.HashBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotRichCompare.RichCmpBuiltinNode;
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
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.ForeignTime)
public final class ForeignTimeBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = ForeignTimeBuiltinsSlotsGen.SLOTS;

    private static final TruffleString T_DST = tsLiteral("dst");
    private static final TruffleString T_ISOFORMAT = tsLiteral("isoformat");
    private static final TruffleString T_STRFTIME = tsLiteral("strftime");
    private static final TruffleString T_TZNAME = tsLiteral("tzname");
    private static final TruffleString T_UTCOFFSET = tsLiteral("utcoffset");

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ForeignTimeBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.tp_repr, isComplex = true)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        static TruffleString repr(Object selfObj,
                        @Bind Node inliningTarget,
                        @Cached TemporalNodes.ReadTimeValueNode readTimeValueNode) {
            TemporalNodes.TimeValue self = readTimeValueNode.execute(inliningTarget, selfObj);
            TruffleString typeName = TypeNodes.GetTpNameNode.executeUncached(GetClassNode.executeUncached(selfObj));
            String value = self.microsecond == 0
                            ? String.format("%s(%d, %d, %d)", typeName, self.hour, self.minute, self.second)
                            : String.format("%s(%d, %d, %d, %d)", typeName, self.hour, self.minute, self.second, self.microsecond);
            return TruffleString.FromJavaStringNode.getUncached().execute(value, TS_ENCODING);
        }
    }

    @Slot(value = SlotKind.tp_str, isComplex = true)
    @GenerateNodeFactory
    abstract static class StrNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object str(Object selfObj,
                        @Bind Node inliningTarget,
                        @Cached TemporalNodes.ReadTimeValueNode readTimeValueNode,
                        @Cached PyObjectStrAsObjectNode strAsObjectNode) {
            return strAsObjectNode.execute(inliningTarget, toPythonTime(readTimeValueNode.execute(inliningTarget, selfObj), inliningTarget));
        }
    }

    @Slot(value = SlotKind.tp_richcompare, isComplex = true)
    @GenerateNodeFactory
    abstract static class RichCmpNode extends RichCmpBuiltinNode {
        @Specialization
        static Object richCmp(VirtualFrame frame, Object selfObj, Object otherObj, RichCmpOp op,
                        @Bind Node inliningTarget,
                        @Cached TemporalNodes.TimeLikeCheckNode timeLikeCheckNode,
                        @Cached TemporalNodes.ReadTimeValueNode readTimeValueNode,
                        @Cached PyObjectCallMethodObjArgs callMethodObjArgs,
                        @Cached PRaiseNode raiseNode) {
            if (!timeLikeCheckNode.execute(inliningTarget, otherObj)) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            PTime self = toPythonTime(readTimeValueNode.execute(inliningTarget, selfObj), inliningTarget);
            PTime other = toPythonTime(readTimeValueNode.execute(inliningTarget, otherObj), inliningTarget);
            if (self.tzInfo == other.tzInfo) {
                return compareTimeComponents(self, other, op);
            }

            PTimeDelta selfUtcOffset = DatetimeModuleBuiltins.callUtcOffset(self.tzInfo, PNone.NONE, frame, inliningTarget, callMethodObjArgs, raiseNode);
            PTimeDelta otherUtcOffset = DatetimeModuleBuiltins.callUtcOffset(other.tzInfo, PNone.NONE, frame, inliningTarget, callMethodObjArgs, raiseNode);
            if (Objects.equals(selfUtcOffset, otherUtcOffset)) {
                return compareTimeComponents(self, other, op);
            }
            if ((selfUtcOffset == null) != (otherUtcOffset == null)) {
                if (op == RichCmpOp.Py_EQ) {
                    return false;
                } else if (op == RichCmpOp.Py_NE) {
                    return true;
                } else {
                    throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.CANT_COMPARE_OFFSET_NAIVE_AND_OFFSET_AWARE_TIMES);
                }
            }
            return op.compareResultToBool(Long.compare(toMicroseconds(self, selfUtcOffset), toMicroseconds(other, otherUtcOffset)));
        }

        private static boolean compareTimeComponents(PTime self, PTime other, RichCmpOp op) {
            int[] selfComponents = new int[]{self.hour, self.minute, self.second, self.microsecond};
            int[] otherComponents = new int[]{other.hour, other.minute, other.second, other.microsecond};
            return op.compareResultToBool(Arrays.compare(selfComponents, otherComponents));
        }
    }

    @Slot(value = SlotKind.tp_hash, isComplex = true)
    @GenerateNodeFactory
    abstract static class HashNode extends HashBuiltinNode {
        @Specialization
        static long hash(VirtualFrame frame, Object selfObj,
                        @Bind Node inliningTarget,
                        @Cached TemporalNodes.ReadTimeValueNode readTimeValueNode,
                        @Cached PyObjectCallMethodObjArgs callMethodObjArgs,
                        @Cached PRaiseNode raiseNode,
                        @Cached PyObjectHashNode hashNode) {
            PTime self = toPythonTime(readTimeValueNode.execute(inliningTarget, selfObj), inliningTarget);
            PTimeDelta utcOffset = DatetimeModuleBuiltins.callUtcOffset(self.tzInfo, PNone.NONE, frame, inliningTarget, callMethodObjArgs, raiseNode);
            if (utcOffset == null) {
                return hashNode.execute(frame, inliningTarget, self);
            }
            return hashNode.execute(frame, inliningTarget, toMicroseconds(self, utcOffset));
        }
    }

    @Builtin(name = "hour", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class HourNode extends PythonUnaryBuiltinNode {
        @Specialization
        static int hour(Object selfObj,
                        @Bind Node inliningTarget,
                        @Cached TemporalNodes.ReadTimeValueNode readTimeValueNode) {
            return readTimeValueNode.execute(inliningTarget, selfObj).hour;
        }
    }

    @Builtin(name = "minute", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class MinuteNode extends PythonUnaryBuiltinNode {
        @Specialization
        static int minute(Object selfObj,
                        @Bind Node inliningTarget,
                        @Cached TemporalNodes.ReadTimeValueNode readTimeValueNode) {
            return readTimeValueNode.execute(inliningTarget, selfObj).minute;
        }
    }

    @Builtin(name = "second", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class SecondNode extends PythonUnaryBuiltinNode {
        @Specialization
        static int second(Object selfObj,
                        @Bind Node inliningTarget,
                        @Cached TemporalNodes.ReadTimeValueNode readTimeValueNode) {
            return readTimeValueNode.execute(inliningTarget, selfObj).second;
        }
    }

    @Builtin(name = "microsecond", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class MicrosecondNode extends PythonUnaryBuiltinNode {
        @Specialization
        static int microsecond(Object selfObj,
                        @Bind Node inliningTarget,
                        @Cached TemporalNodes.ReadTimeValueNode readTimeValueNode) {
            return readTimeValueNode.execute(inliningTarget, selfObj).microsecond;
        }
    }

    @Builtin(name = "tzinfo", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class TzInfoNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object tzinfo(Object selfObj,
                        @Bind Node inliningTarget,
                        @Cached TemporalNodes.ReadTimeValueNode readTimeValueNode) {
            Object tzInfo = toPythonTzInfo(readTimeValueNode.execute(inliningTarget, selfObj), inliningTarget);
            return tzInfo != null ? tzInfo : PNone.NONE;
        }
    }

    @Builtin(name = "fold", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class FoldNode extends PythonUnaryBuiltinNode {
        @Specialization
        static int fold(Object selfObj,
                        @Bind Node inliningTarget,
                        @Cached TemporalNodes.ReadTimeValueNode readTimeValueNode) {
            return readTimeValueNode.execute(inliningTarget, selfObj).fold;
        }
    }

    @Builtin(name = "replace", minNumOfPositionalArgs = 1, parameterNames = {"self", "hour", "minute", "second", "microsecond", "tzinfo"}, keywordOnlyNames = {"fold"})
    @GenerateNodeFactory
    abstract static class ReplaceNode extends PythonBuiltinNode {
        @Specialization
        static Object replace(VirtualFrame frame, Object selfObj, Object hourObject, Object minuteObject, Object secondObject, Object microsecondObject, Object tzInfoObject, Object foldObject,
                        @Bind Node inliningTarget,
                        @Cached TemporalNodes.ReadTimeValueNode readTimeValueNode,
                        @Cached PyLongAsLongNode asLongNode,
                        @Cached TimeNodes.NewNode newTimeNode) {
            TemporalNodes.TimeValue self = readTimeValueNode.execute(inliningTarget, selfObj);
            long hour = hourObject == PNone.NO_VALUE ? self.hour : asLongNode.execute(frame, inliningTarget, hourObject);
            long minute = minuteObject == PNone.NO_VALUE ? self.minute : asLongNode.execute(frame, inliningTarget, minuteObject);
            long second = secondObject == PNone.NO_VALUE ? self.second : asLongNode.execute(frame, inliningTarget, secondObject);
            long microsecond = microsecondObject == PNone.NO_VALUE ? self.microsecond : asLongNode.execute(frame, inliningTarget, microsecondObject);
            Object tzInfo;
            if (tzInfoObject == PNone.NO_VALUE) {
                tzInfo = toPythonTzInfo(self, inliningTarget);
            } else if (tzInfoObject == PNone.NONE) {
                tzInfo = null;
            } else {
                tzInfo = tzInfoObject;
            }
            long fold = foldObject == PNone.NO_VALUE ? self.fold : asLongNode.execute(frame, inliningTarget, foldObject);
            return newTimeNode.execute(inliningTarget, PythonBuiltinClassType.PTime, hour, minute, second, microsecond, tzInfo != null ? tzInfo : PNone.NONE, fold);
        }
    }

    @Builtin(name = "isoformat", minNumOfPositionalArgs = 1, parameterNames = {"self", "timespec"})
    @GenerateNodeFactory
    abstract static class IsoFormatNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object isoformat(Object selfObj, Object timespecObj,
                        @Bind Node inliningTarget,
                        @Cached TemporalNodes.ReadTimeValueNode readTimeValueNode) {
            Object timespec = timespecObj == PNone.NO_VALUE ? PNone.NO_VALUE : timespecObj;
            return PyObjectCallMethodObjArgs.executeUncached(toPythonTime(readTimeValueNode.execute(inliningTarget, selfObj), inliningTarget), T_ISOFORMAT, timespec);
        }
    }

    @Builtin(name = "utcoffset", minNumOfPositionalArgs = 1, parameterNames = {"$self"})
    @GenerateNodeFactory
    abstract static class UtcOffsetNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object utcoffset(Object selfObj,
                        @Bind Node inliningTarget,
                        @Cached TemporalNodes.ReadTimeValueNode readTimeValueNode) {
            return PyObjectCallMethodObjArgs.executeUncached(toPythonTime(readTimeValueNode.execute(inliningTarget, selfObj), inliningTarget), T_UTCOFFSET);
        }
    }

    @Builtin(name = "dst", minNumOfPositionalArgs = 1, parameterNames = {"$self"})
    @GenerateNodeFactory
    abstract static class DstNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object dst(Object selfObj,
                        @Bind Node inliningTarget,
                        @Cached TemporalNodes.ReadTimeValueNode readTimeValueNode) {
            return PyObjectCallMethodObjArgs.executeUncached(toPythonTime(readTimeValueNode.execute(inliningTarget, selfObj), inliningTarget), T_DST);
        }
    }

    @Builtin(name = "tzname", minNumOfPositionalArgs = 1, parameterNames = {"$self"})
    @GenerateNodeFactory
    abstract static class TzNameNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object tzname(Object selfObj,
                        @Bind Node inliningTarget,
                        @Cached TemporalNodes.ReadTimeValueNode readTimeValueNode) {
            return PyObjectCallMethodObjArgs.executeUncached(toPythonTime(readTimeValueNode.execute(inliningTarget, selfObj), inliningTarget), T_TZNAME);
        }
    }

    @Builtin(name = "strftime", minNumOfPositionalArgs = 2, parameterNames = {"self", "format"})
    @GenerateNodeFactory
    abstract static class StrFTimeNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object strftime(Object selfObj, Object formatObj,
                        @Bind Node inliningTarget,
                        @Cached TemporalNodes.ReadTimeValueNode readTimeValueNode,
                        @Cached CastToTruffleStringNode castToTruffleStringNode) {
            TruffleString format = castToTruffleStringNode.execute(inliningTarget, formatObj);
            return PyObjectCallMethodObjArgs.executeUncached(toPythonTime(readTimeValueNode.execute(inliningTarget, selfObj), inliningTarget), T_STRFTIME, format);
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

    private static long toMicroseconds(PTime self, PTimeDelta utcOffset) {
        return (long) self.hour * 3600 * 1_000_000 +
                        (long) self.minute * 60 * 1_000_000 +
                        (long) self.second * 1_000_000 +
                        (long) self.microsecond -
                        (long) utcOffset.days * 24 * 3600 * 1_000_000 -
                        (long) utcOffset.seconds * 1_000_000 -
                        (long) utcOffset.microseconds;
    }

    private static PTime toPythonTime(TemporalNodes.TimeValue time, Node inliningTarget) {
        Object tzInfo = toPythonTzInfo(time, inliningTarget);
        return (PTime) TimeNodes.NewNode.newTimeUnchecked(PythonBuiltinClassType.PTime, time.hour, time.minute, time.second, time.microsecond, tzInfo != null ? tzInfo : PNone.NONE, time.fold);
    }

    private static Object toPythonTzInfo(TemporalNodes.TimeValue time, Node inliningTarget) {
        if (time.tzInfo != null) {
            return time.tzInfo;
        }
        if (time.zoneId instanceof ZoneOffset zoneOffset) {
            PTimeDelta offset = TimeDeltaNodes.NewNode.getUncached().executeBuiltin(inliningTarget, 0, zoneOffset.getTotalSeconds(), 0, 0, 0, 0, 0);
            return TimeZoneNodes.NewNode.getUncached().execute(inliningTarget, PythonContext.get(inliningTarget), PythonBuiltinClassType.PTimezone, offset, PNone.NO_VALUE);
        }
        if (time.zoneId != null && time.zoneId.getRules().isFixedOffset()) {
            ZoneOffset offset = time.zoneId.getRules().getOffset(java.time.Instant.EPOCH);
            PTimeDelta delta = TimeDeltaNodes.NewNode.getUncached().executeBuiltin(inliningTarget, 0, offset.getTotalSeconds(), 0, 0, 0, 0, 0);
            return TimeZoneNodes.NewNode.getUncached().execute(inliningTarget, PythonContext.get(inliningTarget), PythonBuiltinClassType.PTimezone, delta, PNone.NO_VALUE);
        }
        return null;
    }
}
