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
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.TimeModuleBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotHashFun.HashBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotInquiry.NbBoolBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotRichCompare.RichCmpBuiltinNode;
import com.oracle.graal.python.lib.PyLongAsLongNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectHashNode;
import com.oracle.graal.python.lib.PyObjectReprAsObjectNode;
import com.oracle.graal.python.lib.PyObjectStrAsObjectNode;
import com.oracle.graal.python.lib.PyUnicodeCheckNode;
import com.oracle.graal.python.lib.RichCmpOp;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.ExecutionContext;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.FromJavaStringNode;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.nodes.BuiltinNames.T_MIN;
import static com.oracle.graal.python.nodes.BuiltinNames.T_TIME;
import static com.oracle.graal.python.nodes.BuiltinNames.T__DATETIME;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___FORMAT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE_EX__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;
import static com.oracle.graal.python.nodes.BuiltinNames.T_MAX;
import static com.oracle.graal.python.nodes.BuiltinNames.T_RESOLUTION;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PTime)
public final class TimeBuiltins extends PythonBuiltins {

    public static final TpSlots SLOTS = TimeBuiltinsSlotsGen.SLOTS;

    private static final TruffleString T_ISOFORMAT = tsLiteral("isoformat");
    private static final TruffleString T_STRFTIME = tsLiteral("strftime");
    private static final TruffleString T_TZNAME = tsLiteral("tzname");

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return TimeBuiltinsFactory.getFactories();
    }

    @Override
    public void postInitialize(Python3Core core) {
        super.postInitialize(core);

        PythonLanguage language = core.getLanguage();

        PythonModule datetimeModule = core.lookupBuiltinModule(T__DATETIME);
        PythonBuiltinClass self = (PythonBuiltinClass) datetimeModule.getAttribute(T_TIME);
        final var timeType = PythonBuiltinClassType.PTime;
        final var timeShape = timeType.getInstanceShape(language);

        final var min = new PTime(timeType, timeShape, 0, 0, 0, 0, null, 0);
        self.setAttribute(T_MIN, min);

        final var max = new PTime(timeType, timeShape, 23, 59, 59, 999_999, null, 0);
        self.setAttribute(T_MAX, max);

        final var timeDeltaType = PythonBuiltinClassType.PTimeDelta;
        final var timeDeltaShape = timeDeltaType.getInstanceShape(language);

        final var resolution = new PTimeDelta(timeDeltaType, timeDeltaShape, 0, 0, 1);
        self.setAttribute(T_RESOLUTION, resolution);
    }

    @Slot(value = Slot.SlotKind.tp_new, isComplex = true)
    @Slot.SlotSignature(name = "datetime.time", minNumOfPositionalArgs = 1, parameterNames = {"$cls", "hour", "minute", "second", "microsecond", "tzinfo"}, keywordOnlyNames = {"fold"})
    @GenerateNodeFactory
    public abstract static class NewNode extends PythonBuiltinNode {

        @Specialization
        static PTime newTime(Object cls, Object hourObject, Object minuteObject, Object secondObject, Object microsecondObject, Object tzInfoObject, Object foldObject,
                        @Bind Node inliningTarget,
                        @Cached BytesNodes.ToBytesNode toBytesNode,
                        @Cached TimeNodes.NewNode newTimeNode) {
            // load Time serialized with pickle when given only bytes/string and optional tzinfo
            if (secondObject == PNone.NO_VALUE && microsecondObject == PNone.NO_VALUE && tzInfoObject == PNone.NO_VALUE && foldObject == PNone.NO_VALUE) {
                PTime time = tryToDeserializeTime(cls, hourObject, minuteObject, inliningTarget, toBytesNode);

                if (time != null) {
                    return time;
                }
            }

            // ordinal constructor call
            return newTimeNode.execute(inliningTarget, cls, hourObject, minuteObject, secondObject, microsecondObject, tzInfoObject, foldObject);
        }

        @TruffleBoundary
        private static PTime tryToDeserializeTime(Object cls, Object bytesObject, Object tzInfo, Node inliningTarget, BytesNodes.ToBytesNode toBytesNode) {
            final byte[] bytes;

            if (bytesObject instanceof PBytesLike) {
                // serialized Time into bytes is passed as the first parameter
                bytes = toBytesNode.execute((PBytesLike) bytesObject);
            } else if (PyUnicodeCheckNode.executeUncached(bytesObject)) {
                // CPython: PyUnicode_AsLatin1String()
                TruffleString string = CastToTruffleStringNode.getUncached().execute(inliningTarget, bytesObject);

                if (!string.isCompatibleToUncached(TruffleString.Encoding.ISO_8859_1)) {
                    throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.FAILED_TO_ENCODE_LATIN1_STRING_WHEN_UNPICKLING_A_TIME_OBJECT);
                }

                TruffleString stringLatin1 = TruffleString.SwitchEncodingNode.getUncached().execute(string, TruffleString.Encoding.ISO_8859_1);
                bytes = TruffleString.CopyToByteArrayNode.getUncached().execute(stringLatin1, TruffleString.Encoding.ISO_8859_1);
            } else {
                return null;
            }

            if (naiveBytesCheck(bytes)) {
                // slightly different error message
                if (tzInfo != PNone.NO_VALUE && !(tzInfo instanceof PTzInfo)) {
                    throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.BAD_TZINFO_STATE_ARG);
                }

                return deserializeTime(bytes, tzInfo, inliningTarget, cls);
            }

            return null;
        }

        /**
         * Construct a Date instance from a pickle serialized representation. Time is serialized in
         * the following format: ( bytes(hours, minutes, seconds, microseconds 1st byte,
         * microseconds 2nd byte, microseconds 3d byte), <optional tzInfo> ) fold is encoded into
         * the first bit of the first byte.
         */
        private static PTime deserializeTime(byte[] bytes, Object tzInfo, Node inliningTarget, Object cls) {
            int fold = Byte.toUnsignedInt(bytes[0]) >> 7; // get the 1st bit
            int hours = Byte.toUnsignedInt(bytes[0]) & 0x7F; // ignore the 1st bit
            int minutes = Byte.toUnsignedInt(bytes[1]);
            int seconds = Byte.toUnsignedInt(bytes[2]);

            int microseconds = (Byte.toUnsignedInt(bytes[3]) << 16) +
                            (Byte.toUnsignedInt(bytes[4]) << 8) +
                            Byte.toUnsignedInt(bytes[5]);

            return TimeNodes.NewUnsafeNode.getUncached().execute(inliningTarget, cls, hours, minutes, seconds, microseconds, tzInfo, fold);
        }

        /**
         * Ensure bytes have correct format - correct length and hours component has correct value.
         */
        private static boolean naiveBytesCheck(byte[] bytes) {
            return bytes.length == 6 && (Byte.toUnsignedInt(bytes[0]) & 0x7F) < 24;
        }
    }

    @Slot(value = Slot.SlotKind.tp_str, isComplex = true)
    @GenerateNodeFactory
    public abstract static class StrNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object str(VirtualFrame frame, PTime self,
                        @Bind Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethodObjArgs) {
            return callMethodObjArgs.execute(frame, inliningTarget, self, T_ISOFORMAT);
        }
    }

    @Slot(value = Slot.SlotKind.tp_repr, isComplex = true)
    @GenerateNodeFactory
    public abstract static class ReprNode extends PythonUnaryBuiltinNode {

        @Specialization
        static TruffleString repr(VirtualFrame frame, PTime self,
                        @Cached("createFor($node)") IndirectCallData.BoundaryCallData boundaryCallData) {
            Object saved = ExecutionContext.BoundaryCallContext.enter(frame, boundaryCallData);
            try {
                return reprBoundary(self);
            } finally {
                // A Python method call (using PyObjectReprAsObjectNode) should be
                // connected to a current node.
                ExecutionContext.BoundaryCallContext.exit(frame, boundaryCallData, saved);
            }
        }

        @TruffleBoundary
        private static TruffleString reprBoundary(PTime self) {
            var builder = new StringBuilder();

            builder.append(PythonUtils.formatJString("datetime.time(%d, %d", self.hour, self.minute));

            if (self.microsecond != 0) {
                builder.append(PythonUtils.formatJString(", %d, %d", self.second, self.microsecond));
            } else if (self.second != 0) {
                builder.append(PythonUtils.formatJString(", %d", self.second));
            }

            if (self.tzInfo != null) {
                builder.append(", tzinfo=");

                Object tzinfoReprObject = PyObjectReprAsObjectNode.executeUncached(self.tzInfo);
                String tzinfoRepr = CastToJavaStringNode.getUncached().execute(tzinfoReprObject);
                builder.append(tzinfoRepr);
            }

            if (self.fold != 0) {
                builder.append(", fold=1");
            }

            builder.append(")");

            return FromJavaStringNode.getUncached().execute(builder.toString(), TS_ENCODING);
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object reduce(PTime self,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached GetClassNode getClassNode) {
            // Time is serialized in the following format:
            // (
            // bytes(hours, minutes, seconds, microseconds 1st byte, microseconds 2nd byte,
            // microseconds 3d byte),
            // <optional tzInfo>
            // )
            // fold isn't serialized
            byte[] baseStateBytes = new byte[6];
            baseStateBytes[0] = (byte) self.hour;
            baseStateBytes[1] = (byte) self.minute;
            baseStateBytes[2] = (byte) self.second;
            baseStateBytes[3] = (byte) (self.microsecond >> 16);
            baseStateBytes[4] = (byte) ((self.microsecond >> 8) & 0xFF);
            baseStateBytes[5] = (byte) (self.microsecond & 0xFF);

            PBytes baseState = PFactory.createBytes(language, baseStateBytes);

            final PTuple arguments;
            if (self.tzInfo != null) {
                arguments = PFactory.createTuple(language, new Object[]{baseState, self.tzInfo});
            } else {
                arguments = PFactory.createTuple(language, new Object[]{baseState});
            }

            Object type = getClassNode.execute(inliningTarget, self);
            return PFactory.createTuple(language, new Object[]{type, arguments});
        }
    }

    @Builtin(name = J___REDUCE_EX__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ReduceExNode extends PythonBinaryBuiltinNode {

        @Specialization
        static Object reduceEx(PTime self, int protocol,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached GetClassNode getClassNode) {
            byte[] baseStateBytes = new byte[6];
            baseStateBytes[0] = (byte) self.hour;
            baseStateBytes[1] = (byte) self.minute;
            baseStateBytes[2] = (byte) self.second;
            baseStateBytes[3] = (byte) (self.microsecond >> 16);
            baseStateBytes[4] = (byte) ((self.microsecond >> 8) & 0xFF);
            baseStateBytes[5] = (byte) (self.microsecond & 0xFF);

            if (protocol > 3 && self.fold != 0) {
                baseStateBytes[0] |= (byte) (1 << 7);
            }

            PBytes baseState = PFactory.createBytes(language, baseStateBytes);

            final PTuple arguments;
            if (self.tzInfo != null) {
                arguments = PFactory.createTuple(language, new Object[]{baseState, self.tzInfo});
            } else {
                arguments = PFactory.createTuple(language, new Object[]{baseState});
            }

            Object type = getClassNode.execute(inliningTarget, self);
            return PFactory.createTuple(language, new Object[]{type, arguments});
        }
    }

    @Slot(value = Slot.SlotKind.tp_richcompare, isComplex = true)
    @GenerateNodeFactory
    abstract static class RichCmpNode extends RichCmpBuiltinNode {

        @Specialization
        static Object richCmp(VirtualFrame frame, PTime self, PTime other, RichCmpOp op,
                        @Bind Node inliningTarget,
                        @Cached("createFor($node)") IndirectCallData.BoundaryCallData boundaryCallData) {
            Object saved = ExecutionContext.BoundaryCallContext.enter(frame, boundaryCallData);
            try {
                return richCmpBoundary(self, other, op, inliningTarget);
            } finally {
                // A Python method call (using DatetimeModuleBuiltins.callUtcOffset)
                // should be connected to a current node.
                ExecutionContext.BoundaryCallContext.exit(frame, boundaryCallData, saved);
            }
        }

        @TruffleBoundary
        private static Object richCmpBoundary(PTime self, PTime other, RichCmpOp op, Node inliningTarget) {
            // either naive times (without timezone) or timezones are exactly the same objects
            if (self.tzInfo == other.tzInfo) {
                return compareTimeComponents(self, other, op);
            }

            PTimeDelta selfUtcOffset = DatetimeModuleBuiltins.callUtcOffset(self.tzInfo, PNone.NONE, inliningTarget);
            PTimeDelta otherUtcOffset = DatetimeModuleBuiltins.callUtcOffset(other.tzInfo, PNone.NONE, inliningTarget);

            if (Objects.equals(selfUtcOffset, otherUtcOffset)) {
                return compareTimeComponents(self, other, op);
            }

            if ((selfUtcOffset == null) != (otherUtcOffset == null)) {
                if (op == RichCmpOp.Py_EQ) {
                    return false;
                } else if (op == RichCmpOp.Py_NE) {
                    return true;
                } else {
                    throw PRaiseNode.raiseStatic(inliningTarget,
                                    TypeError,
                                    ErrorMessages.CANT_COMPARE_OFFSET_NAIVE_AND_OFFSET_AWARE_TIMES);
                }
            }

            // both times are aware, so take into account their utc offsets
            long selfMicrosecondsTotal = toMicroseconds(self, selfUtcOffset);
            long otherMicrosecondsTotal = toMicroseconds(other, otherUtcOffset);

            int result = Long.compare(selfMicrosecondsTotal, otherMicrosecondsTotal);
            return op.compareResultToBool(result);
        }

        @Fallback
        static PNotImplemented doGeneric(Object self, Object other, RichCmpOp op) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        private static boolean compareTimeComponents(PTime self, PTime other, RichCmpOp op) {
            // compare only hours, minutes, ... and ignore fold
            int[] selfComponents = new int[]{self.hour, self.minute, self.second, self.microsecond};
            int[] otherComponents = new int[]{other.hour, other.minute, other.second, other.microsecond};

            int result = Arrays.compare(selfComponents, otherComponents);

            return op.compareResultToBool(result);
        }
    }

    @Slot(value = Slot.SlotKind.tp_hash, isComplex = true)
    @GenerateNodeFactory
    abstract static class HashNode extends HashBuiltinNode {

        @Specialization
        static long hash(VirtualFrame frame, PTime self,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached PyObjectCallMethodObjArgs callMethodObjArgs,
                        @Cached PRaiseNode raiseNode,
                        @Cached PyObjectHashNode hashNode) {
            PTimeDelta utcOffset = DatetimeModuleBuiltins.callUtcOffset(self.tzInfo, PNone.NONE, frame, inliningTarget, callMethodObjArgs, raiseNode);

            if (utcOffset == null) {
                var content = new int[]{self.hour, self.minute, self.second, self.microsecond};
                return hashNode.execute(frame, inliningTarget, PFactory.createTuple(language, content));
            } else {
                DatetimeModuleBuiltins.validateUtcOffset(utcOffset, inliningTarget);

                long microsecondsTotal = toMicroseconds(self, utcOffset);
                return hashNode.execute(frame, inliningTarget, microsecondsTotal);
            }
        }
    }

    @Slot(value = Slot.SlotKind.nb_bool)
    @GenerateUncached
    @GenerateNodeFactory
    public abstract static class BoolNode extends NbBoolBuiltinNode {

        @Specialization
        static boolean bool(PTime self) {
            return true;
        }
    }

    @Builtin(name = "hour", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class HourNode extends PythonUnaryBuiltinNode {

        @Specialization
        static int getHour(VirtualFrame frame, PTime self) {
            return self.hour;
        }
    }

    @Builtin(name = "minute", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class MinuteNode extends PythonUnaryBuiltinNode {

        @Specialization
        static int getMinute(VirtualFrame frame, PTime self) {
            return self.minute;
        }
    }

    @Builtin(name = "second", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class SecondNode extends PythonUnaryBuiltinNode {

        @Specialization
        static int getSecond(VirtualFrame frame, PTime self) {
            return self.second;
        }
    }

    @Builtin(name = "microsecond", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class MicrosecondNode extends PythonUnaryBuiltinNode {

        @Specialization
        static int getMicrosecond(VirtualFrame frame, PTime self) {
            return self.microsecond;
        }
    }

    @Builtin(name = "tzinfo", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class TzInfoNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object getTzInfo(VirtualFrame frame, PTime self) {
            if (self.tzInfo == null) {
                return PNone.NONE;
            }

            return self.tzInfo;
        }
    }

    @Builtin(name = "fold", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class FoldNode extends PythonUnaryBuiltinNode {

        @Specialization
        static int getFold(VirtualFrame frame, PTime self) {
            return self.fold;
        }
    }

    @Builtin(name = "fromisoformat", minNumOfPositionalArgs = 2, isClassmethod = true, parameterNames = {"self", "time_string"})
    @GenerateNodeFactory
    public abstract static class FromIsoFormatNode extends PythonBuiltinNode {
        static class TimeBuilder {
            record UtcOffset(int sign, int hours, int minutes, int seconds, int microseconds) {

                public boolean isUtc() {
                    return hours == 0 && minutes == 0 && seconds == 0 && microseconds == 0;
                }
            }

            private final Object cls;
            private final Node inliningTarget;

            private int hours;
            private int minutes;
            private int seconds;
            private int microseconds;
            private UtcOffset utcOffset;

            TimeBuilder(Object cls, Node inliningTarget) {
                this.cls = cls;
                this.inliningTarget = inliningTarget;
            }

            public void setHours(int hours) {
                this.hours = hours;
            }

            public void setMinutes(int minutes) {
                this.minutes = minutes;
            }

            public void setSeconds(int seconds) {
                this.seconds = seconds;
            }

            public void setMicroseconds(int microseconds) {
                this.microseconds = microseconds;
            }

            public void setUtcOffset(int sign, int hours, int minutes, int seconds, int microseconds) {
                this.utcOffset = new UtcOffset(sign, hours, minutes, seconds, microseconds);
            }

            public Object toTime() {
                final PTimeZone timezone;
                if (utcOffset == null) {
                    timezone = null;
                } else if (utcOffset.isUtc()) {
                    timezone = DatetimeModuleBuiltins.getUtcTimeZone(getContext(inliningTarget));
                } else {
                    Object timeDeltaType = PythonBuiltinClassType.PTimeDelta;

                    final PTimeDelta timeDelta;
                    if (utcOffset.sign >= 0) {
                        timeDelta = TimeDeltaNodes.NewNode.getUncached().execute(inliningTarget, timeDeltaType, 0, utcOffset.seconds, utcOffset.microseconds, 0, utcOffset.minutes, utcOffset.hours, 0);
                    } else {
                        timeDelta = TimeDeltaNodes.NewNode.getUncached().execute(inliningTarget, timeDeltaType, 0, -utcOffset.seconds, -utcOffset.microseconds, 0, -utcOffset.minutes, -utcOffset.hours,
                                        0);
                    }

                    DatetimeModuleBuiltins.validateUtcOffset(timeDelta, inliningTarget);

                    Object timeZoneType = PythonBuiltinClassType.PTimezone;
                    timezone = TimeZoneNodes.NewNode.getUncached().execute(inliningTarget, getContext(inliningTarget), timeZoneType, timeDelta, PNone.NO_VALUE);
                }

                return TimeNodes.SubclassNewNode.getUncached().execute(inliningTarget, cls, hours, minutes, seconds, microseconds, timezone, 0);
            }
        }

        @Specialization
        static Object fromIsoFormat(VirtualFrame frame, Object cls, Object sourceObject,
                        @Bind Node inliningTarget,
                        @Cached("createFor($node)") IndirectCallData.BoundaryCallData boundaryCallData) {
            Object saved = ExecutionContext.BoundaryCallContext.enter(frame, boundaryCallData);
            try {
                return fromIsoFormatBoundary(cls, sourceObject, inliningTarget);
            } finally {
                // A Python method call (using PyObjectReprAsObjectNode) should be
                // connected to a current node.
                ExecutionContext.BoundaryCallContext.exit(frame, boundaryCallData, saved);
            }
        }

        @TruffleBoundary
        private static Object fromIsoFormatBoundary(Object cls, Object sourceObject, Node inliningTarget) {
            if (!PyUnicodeCheckNode.executeUncached(sourceObject)) {
                throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.FROMISOFORMAT_ARGUMENT_MUST_BE_STR);
            }

            final String source;
            try {
                source = CastToJavaStringNode.getUncached().execute(sourceObject);
            } catch (CannotCastException ex) {
                throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.FROMISOFORMAT_ARGUMENT_MUST_BE_STR);
            }

            Object time = parseIsoTimeFormat(source, cls, inliningTarget);
            if (time == null) {
                Object sourceReprObject = PyObjectReprAsObjectNode.executeUncached(sourceObject);
                String sourceRepr = CastToJavaStringNode.getUncached().execute(sourceReprObject);

                throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.INVALID_ISOFORMAT_STRING_S, sourceRepr);
            }

            return time;
        }

        // CPython: parse_isoformat_date()
        @TruffleBoundary
        private static Object parseIsoTimeFormat(String source, Object cls, Node inliningTarget) {
            try {
                TimeBuilder builder = new TimeBuilder(cls, inliningTarget);

                final Integer hours;
                Integer minutes, seconds, microseconds; // optional values
                boolean usesSeparator;

                int pos = 0;

                // leading 'T' is optional
                if (source.charAt(pos) == 'T') {
                    pos += 1;
                }

                // mandatory hours
                hours = parseDigits(source, pos, 2);
                if (hours == null) {
                    return null;
                }
                pos += 2;
                builder.setHours(hours);

                if (pos >= source.length()) {
                    return builder.toTime();
                }

                // there are optional minutes
                if (source.charAt(pos) == ':' || isDigit(source.charAt(pos))) {
                    usesSeparator = source.charAt(pos) == ':';
                    if (usesSeparator) {
                        pos += 1;
                    }

                    // expect minutes to be present
                    minutes = parseDigits(source, pos, 2);
                    if (minutes == null) {
                        return null;
                    }
                    pos += 2;
                    builder.setMinutes(minutes);

                    if (pos >= source.length()) {
                        return builder.toTime();
                    }

                    // there are optional seconds
                    if (source.charAt(pos) == ':' || isDigit(source.charAt(pos))) {
                        // check both cases - separator is required and is missing and separator is
                        // prohibited but is present
                        if (usesSeparator != (source.charAt(pos) == ':')) {
                            return null;
                        }

                        if (usesSeparator) {
                            pos += 1;
                        }

                        // expect seconds to be present
                        seconds = parseDigits(source, pos, 2);
                        if (seconds == null) {
                            return null;
                        }
                        pos += 2;
                        builder.setSeconds(seconds);

                        if (pos >= source.length()) {
                            return builder.toTime();
                        }

                        // there are optional microseconds
                        if (source.charAt(pos) == '.' || source.charAt(pos) == ',') {
                            pos += 1;

                            int endPos = lookDigitsAhead(source, pos);

                            // no digits found
                            if (endPos == pos) {
                                return null;
                            }

                            int length = Math.min(endPos - pos, 6); // take only first 6 digits and
                                                                    // ignore others
                            microseconds = parseDigits(source, pos, length);

                            // it shouldn't happen but keep it for safety
                            if (microseconds == null) {
                                return null;
                            }

                            // normalize microseconds to 6 digits
                            if (length < 6) {
                                for (int i = 1; i <= 6 - length; i++) {
                                    microseconds *= 10;
                                }
                            }

                            pos = endPos;
                            builder.setMicroseconds(microseconds);

                            if (pos >= source.length()) {
                                return builder.toTime();
                            }
                        }
                    }
                }

                if (source.charAt(pos) != 'Z' && source.charAt(pos) != '+' && source.charAt(pos) != '-') {
                    return null;
                }

                // there is optional utc offset (either 'Z' or '[+|-]dd(:dd(:dd(.dddddd)))')

                final Integer offsetHours;
                Integer offsetMinutes, offsetSeconds, offsetMicroseconds;
                boolean offsetUsesSeparator;
                final int sign;

                if (source.charAt(pos) == 'Z') {
                    builder.setUtcOffset(1, 0, 0, 0, 0);

                    pos++;

                    if (pos >= source.length()) {
                        return builder.toTime();
                    } else {
                        return null;
                    }
                }

                sign = source.charAt(pos) == '+' ? 1 : -1;
                pos++;

                // mandatory hours
                offsetHours = parseDigits(source, pos, 2);
                if (offsetHours == null) {
                    return null;
                }
                pos += 2;

                if (pos >= source.length()) {
                    builder.setUtcOffset(sign, offsetHours, 0, 0, 0);
                    return builder.toTime();
                }

                // there are optional minutes
                if (source.charAt(pos) == ':' || isDigit(source.charAt(pos))) {
                    offsetUsesSeparator = source.charAt(pos) == ':';
                    if (offsetUsesSeparator) {
                        pos += 1;
                    }

                    // expect minutes to be present
                    offsetMinutes = parseDigits(source, pos, 2);
                    if (offsetMinutes == null) {
                        return null;
                    }
                    pos += 2;

                    if (pos >= source.length()) {
                        builder.setUtcOffset(sign, offsetHours, offsetMinutes, 0, 0);
                        return builder.toTime();
                    }

                    // there are optional seconds
                    if (source.charAt(pos) == ':' || isDigit(source.charAt(pos))) {
                        // check both cases - separator is required and is missing and separator is
                        // prohibited but is present
                        if (offsetUsesSeparator != (source.charAt(pos) == ':')) {
                            return null;
                        }

                        if (offsetUsesSeparator) {
                            pos += 1;
                        }

                        // expect seconds to be present
                        offsetSeconds = parseDigits(source, pos, 2);
                        if (offsetSeconds == null) {
                            return null;
                        }
                        pos += 2;

                        if (pos >= source.length()) {
                            builder.setUtcOffset(sign, offsetHours, offsetMinutes, offsetSeconds, 0);
                            return builder.toTime();
                        }

                        // there are optional microseconds
                        if (source.charAt(pos) == '.' || source.charAt(pos) == ',') {
                            pos += 1;

                            int endPos = lookDigitsAhead(source, pos);

                            // no digits found
                            if (endPos == pos) {
                                return null;
                            }

                            int length = Math.min(endPos - pos, 6); // take only first 6 digits and
                                                                    // ignore others
                            offsetMicroseconds = parseDigits(source, pos, length);

                            // it shouldn't happen but keep it for safety
                            if (offsetMicroseconds == null) {
                                return null;
                            }

                            // normalize microseconds to 6 digits
                            if (length < 6) {
                                for (int i = 1; i <= 6 - length; i++) {
                                    offsetMicroseconds *= 10;
                                }
                            }

                            pos = endPos;

                            if (pos >= source.length()) {
                                builder.setUtcOffset(sign, offsetHours, offsetMinutes, offsetSeconds, offsetMicroseconds);
                                return builder.toTime();
                            }
                        }
                    }
                }

                return null;
            } catch (IndexOutOfBoundsException e) {
                return null;
            }
        }

        private static boolean isDigit(char codepoint) {
            return codepoint >= '0' && codepoint <= '9';
        }

        @TruffleBoundary
        private static Integer parseDigits(String source, int from, int digitsCount) {
            int number = 0;

            for (int i = 0; i < digitsCount; i++) {
                char c = source.charAt(from + i);

                if (!isDigit(c)) {
                    return null;
                }

                int digit = c - '0';
                number = number * 10 + digit;
            }

            return number;
        }

        @TruffleBoundary
        private static int lookDigitsAhead(String source, int from) {
            int i = from;

            while (i < source.length() && isDigit(source.charAt(i))) {
                i++;
            }

            return i;
        }
    }

    @Builtin(name = "replace", minNumOfPositionalArgs = 1, parameterNames = {"self", "hour", "minute", "second", "microsecond", "tzinfo"}, keywordOnlyNames = {"fold"})
    @GenerateNodeFactory
    public abstract static class ReplaceNode extends PythonBuiltinNode {

        @Specialization
        static Object replace(VirtualFrame frame, PTime self, Object hourObject, Object minuteObject, Object secondObject, Object microsecondObject, Object tzInfoObject, Object foldObject,
                        @Bind Node inliningTarget,
                        @Cached PyLongAsLongNode asLongNode,
                        @Cached TimeNodes.NewNode newTimeNode) {
            final long hour, minute, second, microsecond, fold;
            final Object tzInfo;

            if (hourObject == PNone.NO_VALUE) {
                hour = self.hour;
            } else {
                hour = asLongNode.execute(frame, inliningTarget, hourObject);
            }

            if (minuteObject == PNone.NO_VALUE) {
                minute = self.minute;
            } else {
                minute = asLongNode.execute(frame, inliningTarget, minuteObject);
            }

            if (secondObject == PNone.NO_VALUE) {
                second = self.second;
            } else {
                second = asLongNode.execute(frame, inliningTarget, secondObject);
            }

            if (microsecondObject == PNone.NO_VALUE) {
                microsecond = self.microsecond;
            } else {
                microsecond = asLongNode.execute(frame, inliningTarget, microsecondObject);
            }

            if (tzInfoObject == PNone.NO_VALUE) {
                tzInfo = self.tzInfo;
            } else if (tzInfoObject == PNone.NONE) {
                tzInfo = null;
            } else {
                tzInfo = tzInfoObject;
            }

            if (foldObject == PNone.NO_VALUE) {
                fold = self.fold;
            } else {
                fold = asLongNode.execute(frame, inliningTarget, foldObject);
            }

            Object type = self.getPythonClass();
            return newTimeNode.execute(inliningTarget, type, hour, minute, second, microsecond, tzInfo, fold);
        }
    }

    @Builtin(name = "isoformat", minNumOfPositionalArgs = 1, parameterNames = {"self", "timespec"})
    @GenerateNodeFactory
    public abstract static class IsoFormatNode extends PythonBinaryBuiltinNode {

        @Specialization
        static TruffleString isoFormat(VirtualFrame frame, PTime self, Object timespecObject,
                        @Bind Node inliningTarget,
                        @Cached("createFor($node)") IndirectCallData.BoundaryCallData boundaryCallData) {
            Object saved = ExecutionContext.BoundaryCallContext.enter(frame, boundaryCallData);
            try {
                return isoFormatBoundary(self, timespecObject, inliningTarget);
            } finally {
                // A Python method call (using PyObjectCallMethodObjArgs and
                // DatetimeModuleBuiltins.callUtcOffset) should be connected to a
                // current node.
                ExecutionContext.BoundaryCallContext.exit(frame, boundaryCallData, saved);
            }
        }

        @TruffleBoundary
        private static TruffleString isoFormatBoundary(PTime self, Object timespecObject, Node inliningTarget) {
            var builder = new StringBuilder();

            final String timespec;
            if (timespecObject == PNone.NO_VALUE) {
                timespec = "auto";
            } else {
                try {
                    timespec = CastToJavaStringNode.getUncached().execute(timespecObject);
                } catch (CannotCastException e) {
                    throw PRaiseNode.raiseStatic(inliningTarget,
                                    TypeError,
                                    ErrorMessages.ARG_D_MUST_BE_S_NOT_P,
                                    "isoformat()",
                                    2,
                                    "str",
                                    timespecObject);
                }
            }

            switch (timespec) {
                case "auto" -> {
                    if (self.microsecond == 0) {
                        String string = PythonUtils.formatJString("%02d:%02d:%02d", self.hour, self.minute, self.second);
                        builder.append(string);
                    } else {
                        String string = PythonUtils.formatJString("%02d:%02d:%02d.%06d", self.hour, self.minute, self.second, self.microsecond);
                        builder.append(string);
                    }
                }
                case "hours" -> {
                    String string = PythonUtils.formatJString("%02d", self.hour);
                    builder.append(string);
                }
                case "minutes" -> {
                    String string = PythonUtils.formatJString("%02d:%02d", self.hour, self.minute);
                    builder.append(string);
                }
                case "seconds" -> {
                    String string = PythonUtils.formatJString("%02d:%02d:%02d", self.hour, self.minute, self.second);
                    builder.append(string);
                }
                case "milliseconds" -> {
                    int milliseconds = self.microsecond / 1000;
                    String string = PythonUtils.formatJString("%02d:%02d:%02d.%03d", self.hour, self.minute, self.second, milliseconds);
                    builder.append(string);
                }
                case "microseconds" -> {
                    String string = PythonUtils.formatJString("%02d:%02d:%02d.%06d", self.hour, self.minute, self.second, self.microsecond);
                    builder.append(string);
                }
                default ->
                    throw PRaiseNode.raiseStatic(inliningTarget,
                                    ValueError,
                                    ErrorMessages.UNKNOWN_TIMESPEC_VALUE);
            }

            Object utcOffsetString = DatetimeModuleBuiltins.formatUtcOffset(self.tzInfo, PNone.NONE, true, inliningTarget);
            builder.append(utcOffsetString);

            return FromJavaStringNode.getUncached().execute(builder.toString(), TS_ENCODING);
        }
    }

    @Builtin(name = "utcoffset", minNumOfPositionalArgs = 1, parameterNames = {"$self"})
    @GenerateNodeFactory
    abstract static class UtcOffsetNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object getUtcOffset(VirtualFrame frame, PTime self,
                        @Bind Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethodObjArgs,
                        @Cached PRaiseNode raiseNode) {
            PTimeDelta offset = DatetimeModuleBuiltins.callUtcOffset(self.tzInfo, PNone.NONE, frame, inliningTarget, callMethodObjArgs, raiseNode);

            if (offset == null) {
                return PNone.NONE;
            }

            return offset;
        }
    }

    @Builtin(name = "dst", minNumOfPositionalArgs = 1, parameterNames = {"$self"})
    @GenerateNodeFactory
    abstract static class DstNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object getDst(VirtualFrame frame, PTime self,
                        @Bind Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethodObjArgs,
                        @Cached PRaiseNode raiseNode) {
            PTimeDelta dst = DatetimeModuleBuiltins.callDst(self.tzInfo, PNone.NONE, frame, inliningTarget, callMethodObjArgs, raiseNode);

            if (dst == null) {
                return PNone.NONE;
            }

            return dst;
        }
    }

    @Builtin(name = "tzname", minNumOfPositionalArgs = 1, parameterNames = {"$self"})
    @GenerateNodeFactory
    abstract static class TzNameNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object getTzName(VirtualFrame frame, PTime self,
                        @Bind Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethodObjArgs,
                        @Cached PyUnicodeCheckNode unicodeCheckNode,
                        @Cached PRaiseNode raiseNode) {
            if (self.tzInfo == null) {
                return PNone.NONE;
            }

            Object tzName = callMethodObjArgs.execute(frame, inliningTarget, self.tzInfo, T_TZNAME, PNone.NONE);

            if (tzName instanceof PNone) {
                return PNone.NONE;
            }

            if (!unicodeCheckNode.execute(inliningTarget, tzName)) {
                throw raiseNode.raise(inliningTarget,
                                TypeError,
                                ErrorMessages.TZINFO_TZNAME_MUST_RETURN_NONE_OR_A_STRING_NOT_P,
                                tzName);
            }

            return tzName;
        }
    }

    @Builtin(name = "strftime", minNumOfPositionalArgs = 2, parameterNames = {"self", "format"})
    @ArgumentClinic(name = "format", conversion = ArgumentClinic.ClinicConversion.TString)
    @GenerateNodeFactory
    public abstract static class StrFTimeNode extends PythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return DateBuiltinsClinicProviders.StrFTimeNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static TruffleString strftime(VirtualFrame frame, PTime self, TruffleString format,
                        @Bind Node inliningTarget,
                        @Cached("createFor($node)") IndirectCallData.BoundaryCallData boundaryCallData) {
            Object saved = ExecutionContext.BoundaryCallContext.enter(frame, boundaryCallData);
            try {
                return strftimeBoundary(self, format, inliningTarget);
            } finally {
                // A Python method call (using PyObjectCallMethodObjArgs and
                // DatetimeModuleBuiltins.formatUtcOffset) should be connected to a
                // current node.
                ExecutionContext.BoundaryCallContext.exit(frame, boundaryCallData, saved);
            }
        }

        @TruffleBoundary
        private static TruffleString strftimeBoundary(PTime self, TruffleString format, Node inliningTarget) {
            // Reuse time.strftime(format, time_tuple) method.
            int[] timeTuple = new int[]{1900, 1, 1, self.hour, self.minute, self.second, 0, 1, -1};
            String formatPreprocessed = preprocessFormat(format, self, inliningTarget);
            return TimeModuleBuiltins.StrfTimeNode.format(formatPreprocessed, timeTuple, TruffleString.FromJavaStringNode.getUncached());
        }

        // The datetime.time.strftime() method supports some extra formatters - %f, %z, %:z, and
        // %Z so handle them here.
        // CPython: wrap_strftime()
        @TruffleBoundary
        private static String preprocessFormat(TruffleString tsformat, PTime self, Node inliningTarget) {
            String format = tsformat.toString();
            StringBuilder builder = new StringBuilder();
            int i = 0;

            while (i != format.length()) {
                int p = format.indexOf('%', i);
                if (p == -1) {
                    builder.append(format, i, format.length());
                    break;
                } else {
                    builder.append(format, i, p); // append a fragment till the found '%' character
                }

                // '%' at the end of the string
                if (p + 1 == format.length()) {
                    builder.append('%');
                    break;
                }

                char c = format.charAt(p + 1);

                if (c == 'z') {
                    Object utcOffsetString = DatetimeModuleBuiltins.formatUtcOffset(self.tzInfo, PNone.NONE, false, inliningTarget);
                    builder.append(utcOffsetString);
                    i = p + 2;
                } else if (c == 'Z') {
                    if (self.tzInfo != null) {
                        // call tzname()
                        Object tzNameObject = PyObjectCallMethodObjArgs.executeUncached(self.tzInfo, T_TZNAME, PNone.NONE);

                        // ignore None value
                        if (tzNameObject != PNone.NONE) {
                            if (PyUnicodeCheckNode.executeUncached(tzNameObject)) {
                                // escape %-sequences to prevent their further interpolation
                                String tzName = CastToJavaStringNode.getUncached().execute(tzNameObject);
                                tzName = tzName.replace("%", "%%");
                                builder.append(tzName);
                            } else {
                                throw PRaiseNode.raiseStatic(inliningTarget,
                                                TypeError,
                                                ErrorMessages.TZINFO_TZNAME_MUST_RETURN_NONE_OR_A_STRING_NOT_P,
                                                tzNameObject);
                            }
                        }
                    }

                    i = p + 2;
                } else if (c == 'f') {
                    String microseconds = String.format("%06d", self.microsecond);
                    builder.append(microseconds);
                    i = p + 2;
                } else if (c == ':') {
                    if (p + 2 == format.length()) {
                        builder.append(':');
                        break;
                    }

                    char d = format.charAt(p + 2);
                    if (d == 'z') {
                        Object utcOffsetString = DatetimeModuleBuiltins.formatUtcOffset(self.tzInfo, PNone.NONE, true, inliningTarget);
                        builder.append(utcOffsetString);

                        i = p + 3;
                    }
                } else {
                    builder.append(format, p, p + 2); // unknown formatter - ignore it and add as is
                    i = p + 2;
                }
            }

            return builder.toString();
        }
    }

    @Builtin(name = J___FORMAT__, minNumOfPositionalArgs = 2, parameterNames = {"$self", "format"})
    @ArgumentClinic(name = "format", conversion = ArgumentClinic.ClinicConversion.TString)
    @GenerateNodeFactory
    public abstract static class FormatNode extends PythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return DateBuiltinsClinicProviders.FormatNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object format(VirtualFrame frame, PTime self, TruffleString format,
                        @Bind Node inliningTarget,
                        @Cached PyObjectStrAsObjectNode strAsObjectNode,
                        @Cached PyObjectCallMethodObjArgs callMethodObjArgs) {
            if (format.isEmpty()) {
                // str(self)
                return strAsObjectNode.execute(inliningTarget, self);
            }

            // call self.strftime(format)
            return callMethodObjArgs.execute(frame, inliningTarget, self, T_STRFTIME, format);
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
}
