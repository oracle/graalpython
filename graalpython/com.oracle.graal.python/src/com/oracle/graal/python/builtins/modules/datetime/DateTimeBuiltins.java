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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.DeprecationWarning;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OverflowError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.builtins.modules.datetime.DatetimeModuleBuiltins.MAX_YEAR;
import static com.oracle.graal.python.builtins.modules.datetime.DatetimeModuleBuiltins.MIN_YEAR;
import static com.oracle.graal.python.nodes.BuiltinNames.T_MAX;
import static com.oracle.graal.python.nodes.BuiltinNames.T_MIN;
import static com.oracle.graal.python.nodes.BuiltinNames.T_RESOLUTION;
import static com.oracle.graal.python.nodes.BuiltinNames.T__DATETIME;
import static com.oracle.graal.python.nodes.ErrorMessages.OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER;
import static com.oracle.graal.python.nodes.ErrorMessages.WARN_DEPRECATED_UTCFROMTIMESTAMP;
import static com.oracle.graal.python.nodes.ErrorMessages.WARN_DEPRECATED_UTCNOW;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE_EX__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;
import static com.oracle.graal.python.nodes.StringLiterals.T_DATETIME;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.text.ParsePosition;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.chrono.IsoChronology;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.WeekFields;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.TimeModuleBuiltins;
import com.oracle.graal.python.builtins.modules.WarningsModuleBuiltins.WarnNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryOp.BinaryOpBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotHashFun.HashBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotRichCompare.RichCmpBuiltinNode;
import com.oracle.graal.python.lib.PyFloatAsDoubleNode;
import com.oracle.graal.python.lib.PyFloatCheckNode;
import com.oracle.graal.python.lib.PyLongAsLongNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectReprAsObjectNode;
import com.oracle.graal.python.lib.PyUnicodeCheckNode;
import com.oracle.graal.python.lib.RichCmpOp;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaDoubleNode;
import com.oracle.graal.python.nodes.util.CastToJavaLongExactNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.ExecutionContext;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.EncapsulatingNodeReference;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PDateTime)
public final class DateTimeBuiltins extends PythonBuiltins {

    public static final TpSlots SLOTS = DateTimeBuiltinsSlotsGen.SLOTS;

    private static final TruffleString T_FROMUTC = tsLiteral("fromutc");
    private static final TruffleString T_ISOFORMAT = tsLiteral("isoformat");
    private static final TruffleString T_TZNAME = tsLiteral("tzname");
    private static final TruffleString T_T = tsLiteral("T");
    private static final TruffleString T_WHITESPACE = tsLiteral(" ");

    // As of version 2015f max fold in IANA database is 23 hours at 1969-09-30 13:00:00 in
    // Kwajalein. Let's probe 24 hours in the past to detect a transition.
    private static final int MAX_FOLD_SECONDS = 24 * 3600;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return DateTimeBuiltinsFactory.getFactories();
    }

    @Override
    public void postInitialize(Python3Core core) {
        super.postInitialize(core);

        PythonLanguage language = core.getLanguage();

        PythonModule datetimeModule = core.lookupBuiltinModule(T__DATETIME);
        PythonBuiltinClass self = (PythonBuiltinClass) datetimeModule.getAttribute(T_DATETIME);
        final var dateTimeType = PythonBuiltinClassType.PDateTime;
        final var dateTimeShape = dateTimeType.getInstanceShape(language);

        final var min = new PDateTime(dateTimeType, dateTimeShape, MIN_YEAR, 1, 1, 0, 0, 0, 0, null, 0);
        self.setAttribute(T_MIN, min);

        final var max = new PDateTime(dateTimeType, dateTimeShape, MAX_YEAR, 12, 31, 23, 59, 59, 999_999, null, 0);
        self.setAttribute(T_MAX, max);

        final var timeDeltaType = PythonBuiltinClassType.PTimeDelta;
        final var timeDeltaShape = timeDeltaType.getInstanceShape(language);

        final var resolution = new PTimeDelta(timeDeltaType, timeDeltaShape, 0, 0, 1);
        self.setAttribute(T_RESOLUTION, resolution);
    }

    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = "datetime.datetime", minNumOfPositionalArgs = 1, parameterNames = {"$cls", "year", "month", "day", "hour", "minute", "second", "microsecond",
                    "tzinfo"}, keywordOnlyNames = {"fold"})
    @GenerateNodeFactory
    public abstract static class NewNode extends PythonBuiltinNode {

        @Specialization
        static Object newDateTime(Object cls, Object yearObject, Object monthObject, Object dayObject, Object hourObject, Object minuteObject, Object secondObject, Object microsecondObject,
                        Object tzInfoObject, Object foldObject,
                        @Bind Node inliningTarget,
                        @Cached BytesNodes.ToBytesNode toBytesNode,
                        @Cached DateTimeNodes.NewNode newNode) {
            // load DateTime serialized with pickle when given only bytes/string and optional tzinfo
            if (dayObject == PNone.NO_VALUE && hourObject == PNone.NO_VALUE && minuteObject == PNone.NO_VALUE && secondObject == PNone.NO_VALUE && microsecondObject == PNone.NO_VALUE &&
                            tzInfoObject == PNone.NO_VALUE && foldObject == PNone.NO_VALUE) {
                Object dateTime = tryToDeserializeDateTime(cls, yearObject, monthObject, inliningTarget, toBytesNode);

                if (dateTime != null) {
                    return dateTime;
                }
            }

            return newNode.execute(inliningTarget, cls, yearObject, monthObject, dayObject, hourObject, minuteObject, secondObject, microsecondObject, tzInfoObject, foldObject);
        }

        @TruffleBoundary
        private static Object tryToDeserializeDateTime(Object cls, Object bytesObject, Object tzInfo, Node inliningTarget, BytesNodes.ToBytesNode toBytesNode) {
            final byte[] bytes;

            if (bytesObject instanceof PBytesLike) {
                // serialized DateTime into bytes is passed as the first parameter
                bytes = toBytesNode.execute((PBytesLike) bytesObject);
            } else if (PyUnicodeCheckNode.executeUncached(bytesObject)) {
                // CPython: PyUnicode_AsLatin1String()
                TruffleString string = CastToTruffleStringNode.getUncached().execute(inliningTarget, bytesObject);

                if (!string.isCompatibleToUncached(TruffleString.Encoding.ISO_8859_1)) {
                    throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.FAILED_TO_ENCODE_LATIN1_STRING_WHEN_UNPICKLING_A_DATETIME_OBJECT);
                }

                TruffleString stringLatin1 = TruffleString.SwitchEncodingNode.getUncached().execute(string, TruffleString.Encoding.ISO_8859_1);
                bytes = TruffleString.CopyToByteArrayNode.getUncached().execute(stringLatin1, TruffleString.Encoding.ISO_8859_1);
            } else {
                return null;
            }

            if (naiveBytesCheck(bytes)) {
                if (tzInfo != PNone.NO_VALUE && !(tzInfo instanceof PTzInfo)) {
                    throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.BAD_TZINFO_STATE_ARG);
                }

                return deserializeDateTime(bytes, tzInfo, inliningTarget, cls);
            }

            return null;
        }

        /**
         * Ensure bytes have correct format - correct length and month component has correct value.
         */
        private static boolean naiveBytesCheck(byte[] bytes) {
            if (bytes.length != 10) {
                return false;
            }

            int month = Byte.toUnsignedInt(bytes[2]) & 0x7F;
            return month >= 1 && month <= 12;
        }

        /**
         * Construct a Date instance from a pickle serialized representation. DateTime is serialized
         * in the following format: ( bytes(year 1st byte, year 2nd byte, month, day, hours,
         * minutes, seconds, microseconds 1st byte, microseconds 2nd byte, microseconds 3d byte),
         * <optional tzInfo> ) fold is encoded into the 1st bit of the 3d byte.
         */
        private static Object deserializeDateTime(byte[] bytes, Object tzInfo, Node inliningTarget, Object cls) {
            int year = Byte.toUnsignedInt(bytes[0]) * 256 + Byte.toUnsignedInt(bytes[1]);
            int month = Byte.toUnsignedInt(bytes[2]) & 0x7F;
            int day = Byte.toUnsignedInt(bytes[3]);

            int hours = Byte.toUnsignedInt(bytes[4]); // ignore the 1st bit
            int minutes = Byte.toUnsignedInt(bytes[5]);
            int seconds = Byte.toUnsignedInt(bytes[6]);
            int microseconds = (Byte.toUnsignedInt(bytes[7]) << 16) +
                            (Byte.toUnsignedInt(bytes[8]) << 8) +
                            Byte.toUnsignedInt(bytes[9]);

            int fold = Byte.toUnsignedInt(bytes[2]) >> 7; // get the 1st bit

            return DateTimeNodes.NewUnsafeNode.getUncached().execute(inliningTarget, cls, year, month, day, hours, minutes, seconds, microseconds, tzInfo, fold);
        }
    }

    @Builtin(name = "today", minNumOfPositionalArgs = 1, parameterNames = {"$cls"}, isClassmethod = true)
    @GenerateNodeFactory
    abstract static class TodayNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object today(VirtualFrame frame, Object cls,
                        @Bind Node inliningTarget,
                        @Cached("createFor($node)") IndirectCallData.BoundaryCallData boundaryCallData) {
            Object saved = ExecutionContext.BoundaryCallContext.enter(frame, boundaryCallData);
            try {
                return todayBoundary(cls, inliningTarget);
            } finally {
                // A Python method call (using DateTimeNodes.SubclassNewNode) should be
                // connected to a current node.
                ExecutionContext.BoundaryCallContext.exit(frame, boundaryCallData, saved);
            }
        }

        @TruffleBoundary
        private static Object todayBoundary(Object cls, Node inliningTarget) {
            var local = LocalDateTime.now();
            return DateTimeNodes.SubclassNewNode.getUncached().execute(inliningTarget,
                            cls,
                            local.getYear(),
                            local.getMonthValue(),
                            local.getDayOfMonth(),
                            local.getHour(),
                            local.getMinute(),
                            local.getSecond(),
                            local.getNano() / 1_000,
                            PNone.NONE,
                            0);
        }
    }

    @Builtin(name = "now", minNumOfPositionalArgs = 1, parameterNames = {"$cls", "tz"}, isClassmethod = true)
    @GenerateNodeFactory
    abstract static class NowNode extends PythonBinaryBuiltinNode {

        @Specialization
        static Object nowInTimeZone(VirtualFrame frame, Object cls, PTzInfo tzInfo,
                        @Bind Node inliningTarget,
                        @Cached("createFor($node)") IndirectCallData.BoundaryCallData boundaryCallData) {
            Object saved = ExecutionContext.BoundaryCallContext.enter(frame, boundaryCallData);
            try {
                return nowInTimeZoneBoundary(cls, tzInfo, inliningTarget);
            } finally {
                // A Python method call (using PyObjectCallMethodObjArgs and
                // DateTimeNodes.SubclassNewNode) should be connected to a current node.
                ExecutionContext.BoundaryCallContext.exit(frame, boundaryCallData, saved);
            }
        }

        @TruffleBoundary
        private static Object nowInTimeZoneBoundary(Object cls, PTzInfo tzInfo, Node inliningTarget) {
            // convert current time in UTC to the given time zone with tzinfo.fromutc()
            LocalDateTime utc = LocalDateTime.now(ZoneOffset.UTC);

            Object self = DateTimeNodes.SubclassNewNode.getUncached().execute(inliningTarget,
                            cls,
                            utc.getYear(),
                            utc.getMonthValue(),
                            utc.getDayOfMonth(),
                            utc.getHour(),
                            utc.getMinute(),
                            utc.getSecond(),
                            utc.getNano() / 1_000,
                            tzInfo, // set the final value beforehand - it's required by
                                    // #fromutc()
                            0);

            return PyObjectCallMethodObjArgs.executeUncached(tzInfo, T_FROMUTC, self);
        }

        @Specialization
        @TruffleBoundary
        static Object nowNaive(Object cls, PNone tzInfo,
                        @Bind Node inliningTarget) {
            var local = LocalDateTime.now();
            return DateTimeNodes.SubclassNewNode.getUncached().execute(inliningTarget,
                            cls,
                            local.getYear(),
                            local.getMonthValue(),
                            local.getDayOfMonth(),
                            local.getHour(),
                            local.getMinute(),
                            local.getSecond(),
                            local.getNano() / 1_000,
                            PNone.NONE,
                            0);
        }

        @Fallback
        static void doGeneric(Object cls, Object tzInfo,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.TZINFO_ARGUMENT_MUST_BE_NONE_OR_OF_A_TZINFO_SUBCLASS_NOT_TYPE_P, tzInfo);
        }
    }

    @Slot(value = SlotKind.tp_str, isComplex = true)
    @GenerateNodeFactory
    public abstract static class StrNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object str(VirtualFrame frame, Object self,
                        @Bind Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethodObjArgs) {
            return callMethodObjArgs.execute(frame, inliningTarget, self, T_ISOFORMAT, T_WHITESPACE);
        }
    }

    @Slot(value = SlotKind.tp_repr, isComplex = true)
    @GenerateNodeFactory
    public abstract static class ReprNode extends PythonUnaryBuiltinNode {

        @Specialization
        static TruffleString repr(Object selfObj,
                        @Bind Node inliningTarget) {
            EncapsulatingNodeReference encapsulating = EncapsulatingNodeReference.getCurrent();
            Node encapsulatingNode = encapsulating.set(inliningTarget);
            try {
                return reprBoundary(selfObj);
            } finally {
                // Some uncached nodes (e.g. PyFloatAsDoubleNode, PyLongAsLongNode,
                // PyObjectReprAsObjectNode) may raise exceptions that are not
                // connected to a current node. Set the current node manually.
                encapsulating.set(encapsulatingNode);
            }
        }

        @TruffleBoundary
        private static TruffleString reprBoundary(Object selfObj) {
            PDateTime self = DateTimeNodes.AsManagedDateTimeNode.executeUncached(selfObj);
            var builder = new StringBuilder();

            TruffleString typeName = TypeNodes.GetTpNameNode.executeUncached(GetClassNode.executeUncached(selfObj));
            builder.append(PythonUtils.formatJString("%s(%d, %d, %d, %d, %d", typeName, self.year, self.month, self.day, self.hour, self.minute));

            if (self.microsecond != 0) {
                builder.append(PythonUtils.formatJString(", %d, %d", self.second, self.microsecond));
            } else if (self.second != 0) {
                builder.append(PythonUtils.formatJString(", %d", self.second));
            }

            if (self.fold != 0) {
                builder.append(", fold=1");
            }

            if (self.tzInfo != null) {
                builder.append(", tzinfo=");

                Object tzinfoReprObject = PyObjectReprAsObjectNode.executeUncached(self.tzInfo);
                String tzinfoRepr = CastToJavaStringNode.getUncached().execute(tzinfoReprObject);
                builder.append(tzinfoRepr);
            }

            builder.append(")");

            return TruffleString.FromJavaStringNode.getUncached().execute(builder.toString(), TS_ENCODING);
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object reduce(Object selfObj,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached GetClassNode getClassNode) {
            PDateTime self = DateTimeNodes.AsManagedDateTimeNode.executeUncached(selfObj);
            // DateTime is serialized in the following format:
            // (
            // bytes(year 1st byte, year 2nd byte, month, day, hours, minutes, seconds, microseconds
            // 1st byte, microseconds 2nd byte, microseconds 3d byte),
            // <optional tzInfo>
            // )
            // fold isn't serialized
            byte[] baseStateBytes = new byte[10];

            baseStateBytes[0] = (byte) (self.year / 256);
            baseStateBytes[1] = (byte) (self.year % 256);
            baseStateBytes[2] = (byte) self.month;
            baseStateBytes[3] = (byte) self.day;
            baseStateBytes[4] = (byte) self.hour;
            baseStateBytes[5] = (byte) self.minute;
            baseStateBytes[6] = (byte) self.second;
            baseStateBytes[7] = (byte) (self.microsecond >> 16);
            baseStateBytes[8] = (byte) ((self.microsecond >> 8) & 0xFF);
            baseStateBytes[9] = (byte) (self.microsecond & 0xFF);

            PBytes baseState = PFactory.createBytes(language, baseStateBytes);

            final PTuple arguments;
            if (self.tzInfo != null) {
                arguments = PFactory.createTuple(language, new Object[]{baseState, self.tzInfo});
            } else {
                arguments = PFactory.createTuple(language, new Object[]{baseState});
            }

            Object type = getClassNode.execute(inliningTarget, selfObj);
            return PFactory.createTuple(language, new Object[]{type, arguments});
        }
    }

    @Builtin(name = J___REDUCE_EX__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ReduceExNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object reduceEx(Object selfObj, int protocol,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached GetClassNode getClassNode) {
            PDateTime self = DateTimeNodes.AsManagedDateTimeNode.executeUncached(selfObj);
            byte[] baseStateBytes = new byte[10];
            baseStateBytes[0] = (byte) (self.year / 256);
            baseStateBytes[1] = (byte) (self.year % 256);
            baseStateBytes[2] = (byte) self.month;
            baseStateBytes[3] = (byte) self.day;
            baseStateBytes[4] = (byte) self.hour;
            baseStateBytes[5] = (byte) self.minute;
            baseStateBytes[6] = (byte) self.second;
            baseStateBytes[7] = (byte) (self.microsecond >> 16);
            baseStateBytes[8] = (byte) ((self.microsecond >> 8) & 0xFF);
            baseStateBytes[9] = (byte) (self.microsecond & 0xFF);

            if (protocol > 3 && self.fold != 0) {
                baseStateBytes[2] |= (byte) (1 << 7);
            }

            PBytes baseState = PFactory.createBytes(language, baseStateBytes);

            final PTuple arguments;
            if (self.tzInfo != null) {
                arguments = PFactory.createTuple(language, new Object[]{baseState, self.tzInfo});
            } else {
                arguments = PFactory.createTuple(language, new Object[]{baseState});
            }

            Object type = getClassNode.execute(inliningTarget, selfObj);
            return PFactory.createTuple(language, new Object[]{type, arguments});
        }
    }

    @Slot(value = SlotKind.tp_richcompare, isComplex = true)
    @GenerateNodeFactory
    abstract static class RichCmpNode extends RichCmpBuiltinNode {

        @Specialization
        static Object richCmp(VirtualFrame frame, Object self, Object other, RichCmpOp op,
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
        private static Object richCmpBoundary(Object selfObj, Object otherObj, RichCmpOp op, Node inliningTarget) {
            if (!DateTimeNodes.DateTimeCheckNode.executeUncached(otherObj)) {
                /*
                 * Prevent invocation of date_richcompare. We want to return NotImplemented here to
                 * give the other object a chance. But since DateTime is a subclass of Date, if the
                 * other object is a Date, it would compute an ordering based on the date part
                 * alone, and we don't want that. So force unequal or uncomparable here in that
                 * case.
                 */
                if (DateNodes.DateCheckNode.executeUncached(otherObj)) {
                    if (op == RichCmpOp.Py_EQ) {
                        return false;
                    } else if (op == RichCmpOp.Py_NE) {
                        return true;
                    } else {
                        throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.CANT_COMPARE, selfObj, otherObj);
                    }
                }
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            PDateTime self = DateTimeNodes.AsManagedDateTimeNode.executeUncached(selfObj);
            PDateTime other = DateTimeNodes.AsManagedDateTimeNode.executeUncached(otherObj);
            // either naive datetimes (without timezone) or timezones are exactly the same objects
            if (self.tzInfo == other.tzInfo) {
                int result = compareDateTimeComponents(self, other);
                return op.compareResultToBool(result);
            }

            PTimeDelta selfUtcOffset = DatetimeModuleBuiltins.callUtcOffset(self.tzInfo, self, inliningTarget);
            PTimeDelta otherUtcOffset = DatetimeModuleBuiltins.callUtcOffset(other.tzInfo, other, inliningTarget);

            if (Objects.equals(selfUtcOffset, otherUtcOffset)) {
                int result = compareDateTimeComponents(self, other);

                if (result == 0 && (op == RichCmpOp.Py_EQ || op == RichCmpOp.Py_NE) && selfUtcOffset != null) {
                    // if any utc offset is affected by a fold value - return false
                    if (isExceptionInPep495(selfObj, self, selfUtcOffset, other, otherUtcOffset, inliningTarget)) {
                        result = 1;
                    }
                }

                return op.compareResultToBool(result);
            }

            if ((selfUtcOffset == null) != (otherUtcOffset == null)) {
                if (op == RichCmpOp.Py_EQ) {
                    return false;
                } else if (op == RichCmpOp.Py_NE) {
                    return true;
                } else {
                    throw PRaiseNode.raiseStatic(inliningTarget,
                                    TypeError,
                                    ErrorMessages.CANT_COMPARE_OFFSET_NAIVE_AND_OFFSET_AWARE_DATETIMES);
                }
            }

            // Both times are timezone aware, so take into account their utc offsets.
            // Avoid instantiating new DateTime to prevent raising OverflowError.
            LocalDateTime selfLocalDateTimeInUtc = subtractOffsetFromDateTime(self, selfUtcOffset);
            LocalDateTime otherLocalDateTimeInUtc = subtractOffsetFromDateTime(other, otherUtcOffset);

            int result = selfLocalDateTimeInUtc.compareTo(otherLocalDateTimeInUtc);

            if (result == 0 && (op == RichCmpOp.Py_EQ || op == RichCmpOp.Py_NE)) {
                // if any utc offset is affected by a fold value - return false
                if (isExceptionInPep495(selfObj, self, selfUtcOffset, other, otherUtcOffset, inliningTarget)) {
                    result = 1;
                }
            }

            return op.compareResultToBool(result);
        }

        @TruffleBoundary
        private static int compareDateTimeComponents(PDateTime self, PDateTime other) {
            // compare only year, month, day, hours, minutes, ... and ignore fold
            int[] selfComponents = new int[]{self.year, self.month, self.day, self.hour, self.minute, self.second, self.microsecond};
            int[] otherComponents = new int[]{other.year, other.month, other.day, other.hour, other.minute, other.second, other.microsecond};

            return Arrays.compare(selfComponents, otherComponents);
        }

        /**
         * Check that self's and other's utc offset doesn't depend on a fold value. Based on "PEP
         * 495 – Local Time Disambiguation". See <a href="https://peps.python.org/pep-0495/">PEP 495
         * – Local Time Disambiguation</a>
         */
        private static boolean isExceptionInPep495(Object selfObj, PDateTime self, PTimeDelta selfUtcOffset, PDateTime other, PTimeDelta otherUtcOffset, Node inliningTarget) {
            return isExceptionInPep495(selfObj, self, selfUtcOffset, inliningTarget) || isExceptionInPep495(selfObj, other, otherUtcOffset, inliningTarget);
        }

        @TruffleBoundary
        private static boolean isExceptionInPep495(Object dateTimeObj, PDateTime dateTime, PTimeDelta utcOffset, Node inliningTarget) {
            Object cls = GetClassNode.executeUncached(dateTimeObj);
            Shape shape = TypeNodes.GetInstanceShape.getUncached().execute(cls);
            int fold = dateTime.fold == 1 ? 0 : 1;

            PDateTime newDateTime = new PDateTime(cls, shape, dateTime.year, dateTime.month, dateTime.day, dateTime.hour, dateTime.minute, dateTime.second,
                            dateTime.microsecond, dateTime.tzInfo, fold);
            PTimeDelta newUtcOffset = DatetimeModuleBuiltins.callUtcOffset(newDateTime.tzInfo, newDateTime, inliningTarget);

            return !utcOffset.equals(newUtcOffset);
        }
    }

    @Slot(value = SlotKind.tp_hash, isComplex = true)
    @GenerateNodeFactory
    abstract static class HashNode extends HashBuiltinNode {

        @Specialization
        static long hash(VirtualFrame frame, Object selfObj,
                        @Bind Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethodObjArgs,
                        @Cached PRaiseNode raiseNode,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape) {
            PDateTime self = DateTimeNodes.AsManagedDateTimeNode.executeUncached(selfObj);
            final PTimeDelta offset;
            if (self.tzInfo == null) {
                offset = null;
            } else {
                // ignore fold in calculating utc offset
                final PDateTime getUtcOffsetFrom;
                if (self.fold == 1) {
                    // reset fold
                    Object cls = GetClassNode.executeUncached(selfObj);
                    Shape shape = getInstanceShape.execute(cls);
                    getUtcOffsetFrom = new PDateTime(cls, shape, self.year, self.month, self.day, self.hour, self.minute, self.second, self.microsecond, self.tzInfo, 0);
                } else {
                    getUtcOffsetFrom = self;
                }

                offset = DatetimeModuleBuiltins.callUtcOffset(self.tzInfo, getUtcOffsetFrom, frame, inliningTarget, callMethodObjArgs, raiseNode);
            }

            if (offset == null) {
                return getHashForDateTime(self);
            } else {
                return getHashForDateTimeWithOffset(self, offset);
            }
        }

        @TruffleBoundary
        private static long getHashForDateTime(PDateTime self) {
            return Objects.hash(self.year, self.month, self.day, self.hour, self.minute, self.second, self.microsecond);
        }

        @TruffleBoundary
        private static long getHashForDateTimeWithOffset(PDateTime self, PTimeDelta offset) {
            LocalDateTime utc = subtractOffsetFromDateTime(self, offset);
            return Objects.hash(utc.getYear(), utc.getMonthValue(), utc.getDayOfMonth(), utc.getHour(), utc.getMinute(), utc.getSecond(), utc.getNano() / 1_000);
        }
    }

    @Slot(value = SlotKind.nb_add, isComplex = true)
    @GenerateNodeFactory
    abstract static class AddNode extends BinaryOpBuiltinNode {

        @Specialization
        static Object add(VirtualFrame frame, Object left, Object right,
                        @Bind Node inliningTarget,
                        @Cached("createFor($node)") IndirectCallData.BoundaryCallData boundaryCallData) {
            Object saved = ExecutionContext.BoundaryCallContext.enter(frame, boundaryCallData);
            try {
                return addBoundary(left, right, inliningTarget);
            } finally {
                // A Python method call (using DateTimeNodes.SubclassNewNode) should be
                // connected to a current node.
                ExecutionContext.BoundaryCallContext.exit(frame, boundaryCallData, saved);
            }
        }

        @TruffleBoundary
        private static Object addBoundary(Object left, Object right, Node inliningTarget) {
            Object dateTimeObj, deltaObj;
            if (DateTimeNodes.DateTimeCheckNode.executeUncached(left)) {
                if (TimeDeltaNodes.TimeDeltaCheckNode.executeUncached(right)) {
                    dateTimeObj = left;
                    deltaObj = right;
                } else {
                    return PNotImplemented.NOT_IMPLEMENTED;
                }
            } else if (TimeDeltaNodes.TimeDeltaCheckNode.executeUncached(left)) {
                dateTimeObj = right;
                deltaObj = left;
            } else {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            PDateTime date = DateTimeNodes.AsManagedDateTimeNode.executeUncached(dateTimeObj);
            PTimeDelta delta = TimeDeltaNodes.AsManagedTimeDeltaNode.executeUncached(deltaObj);

            LocalDateTime local = toLocalDateTime(date);
            LocalDateTime localAdjusted = local.plusDays(delta.days).plusSeconds(delta.seconds).plusNanos(delta.microseconds * 1_000L);

            if (localAdjusted.getYear() < MIN_YEAR || localAdjusted.getYear() > MAX_YEAR) {
                throw PRaiseNode.raiseStatic(inliningTarget, OverflowError, ErrorMessages.DATE_VALUE_OUT_OF_RANGE);
            }

            return toPDateTime(localAdjusted, date.tzInfo, date.fold, inliningTarget, GetClassNode.executeUncached(dateTimeObj));
        }
    }

    @Slot(value = SlotKind.nb_subtract, isComplex = true)
    @GenerateNodeFactory
    abstract static class SubNode extends BinaryOpBuiltinNode {

        @Specialization
        static Object sub(VirtualFrame frame, Object left, Object right,
                        @Bind Node inliningTarget,
                        @Cached("createFor($node)") IndirectCallData.BoundaryCallData boundaryCallData) {
            Object saved = ExecutionContext.BoundaryCallContext.enter(frame, boundaryCallData);
            try {
                return subBoundary(left, right, inliningTarget);
            } finally {
                // A Python method call (using DatetimeModuleBuiltins.callUtcOffset)
                // should be connected to a current node.
                ExecutionContext.BoundaryCallContext.exit(frame, boundaryCallData, saved);
            }
        }

        @TruffleBoundary
        private static Object subBoundary(Object left, Object right, Node inliningTarget) {
            if (!DateTimeNodes.DateTimeCheckNode.executeUncached(left)) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            PDateTime self = DateTimeNodes.AsManagedDateTimeNode.executeUncached(left);
            if (DateTimeNodes.DateTimeCheckNode.executeUncached(right)) {
                PDateTime other = DateTimeNodes.AsManagedDateTimeNode.executeUncached(right);

                final PTimeDelta selfOffset;
                final PTimeDelta otherOffset;

                selfOffset = DatetimeModuleBuiltins.callUtcOffset(self.tzInfo, self, inliningTarget);
                otherOffset = DatetimeModuleBuiltins.callUtcOffset(other.tzInfo, other, inliningTarget);

                if ((selfOffset == null) != (otherOffset == null)) {
                    throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.CANNOT_SUBTRACT_OFFSET_NAIVE_AND_OFFSET_AWARE_DATETIMES);
                }

                final LocalDateTime selfToCompare;
                final LocalDateTime otherToCompare;

                if (selfOffset != null && self.tzInfo != other.tzInfo) {
                    selfToCompare = subtractOffsetFromDateTime(self, selfOffset);
                    otherToCompare = subtractOffsetFromDateTime(other, otherOffset);
                } else {
                    selfToCompare = toLocalDateTime(self);
                    otherToCompare = toLocalDateTime(other);
                }

                long selfSeconds = selfToCompare.toEpochSecond(ZoneOffset.UTC);
                long otherSeconds = otherToCompare.toEpochSecond(ZoneOffset.UTC);

                return TimeDeltaNodes.NewNode.getUncached().execute(inliningTarget,
                                PythonBuiltinClassType.PTimeDelta,
                                0,
                                selfSeconds - otherSeconds,
                                self.microsecond - other.microsecond,
                                0,
                                0,
                                0,
                                0);
            } else if (TimeDeltaNodes.TimeDeltaCheckNode.executeUncached(right)) {
                PTimeDelta timeDelta = TimeDeltaNodes.AsManagedTimeDeltaNode.executeUncached(right);
                LocalDateTime local = toLocalDateTime(self);
                LocalDateTime localAdjusted = local.minusDays(timeDelta.days).minusSeconds(timeDelta.seconds).minusNanos(timeDelta.microseconds * 1_000L);

                if (localAdjusted.getYear() < MIN_YEAR || localAdjusted.getYear() > MAX_YEAR) {
                    throw PRaiseNode.raiseStatic(inliningTarget, OverflowError, ErrorMessages.DATE_VALUE_OUT_OF_RANGE);
                }

                return toPDateTime(localAdjusted, self.tzInfo, self.fold, inliningTarget, GetClassNode.executeUncached(left));
            } else {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }
    }

    @Builtin(name = "hour", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class HourNode extends PythonUnaryBuiltinNode {
        @Specialization
        static int getHour(PDateTime self) {
            return self.hour;
        }

        @Specialization
        static int getHour(PythonAbstractNativeObject self,
                        @Cached CStructAccess.ReadByteNode readByteNode) {
            return readByteNode.readFromObjUnsigned(self, CFields.PyDateTime_DateTime__data, 4);
        }
    }

    @Builtin(name = "minute", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class MinuteNode extends PythonUnaryBuiltinNode {

        @Specialization
        static int getMinute(PDateTime self) {
            return self.minute;
        }

        @Specialization
        static int getMinute(PythonAbstractNativeObject self,
                        @Cached CStructAccess.ReadByteNode readNode) {
            return readNode.readFromObjUnsigned(self, CFields.PyDateTime_DateTime__data, 5);
        }
    }

    @Builtin(name = "second", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class SecondNode extends PythonUnaryBuiltinNode {

        @Specialization
        static int getSecond(PDateTime self) {
            return self.second;
        }

        @Specialization
        static int getSecond(PythonAbstractNativeObject self,
                        @Cached CStructAccess.ReadByteNode readNode) {
            return readNode.readFromObjUnsigned(self, CFields.PyDateTime_DateTime__data, 6);
        }
    }

    @Builtin(name = "microsecond", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class MicrosecondNode extends PythonUnaryBuiltinNode {

        @Specialization
        static int getMicrosecond(PDateTime self) {
            return self.microsecond;
        }

        @Specialization
        static int getMicrosecond(PythonAbstractNativeObject self,
                        @Cached CStructAccess.ReadByteNode readNode) {
            int b3 = readNode.readFromObjUnsigned(self, CFields.PyDateTime_DateTime__data, 7);
            int b4 = readNode.readFromObjUnsigned(self, CFields.PyDateTime_DateTime__data, 8);
            int b5 = readNode.readFromObjUnsigned(self, CFields.PyDateTime_DateTime__data, 9);
            return (b3 << 16) | (b4 << 8) | b5;
        }
    }

    @Builtin(name = "tzinfo", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class TzInfoNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object getTzInfo(Object self,
                        @Bind Node inliningTarget,
                        @Cached DateTimeNodes.TzInfoNode tzInfoNode) {
            Object tzinfo = tzInfoNode.execute(inliningTarget, self);
            return tzinfo != null ? tzinfo : PNone.NONE;
        }
    }

    @Builtin(name = "fold", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class FoldNode extends PythonUnaryBuiltinNode {

        @Specialization
        static int getFold(PDateTime self) {
            return self.fold;
        }

        @Specialization
        static int getFold(PythonAbstractNativeObject self,
                        @Cached CStructAccess.ReadByteNode readNode) {
            return readNode.readFromObjUnsigned(self, CFields.PyDateTime_DateTime__fold);
        }
    }

    @Builtin(name = "utcnow", minNumOfPositionalArgs = 1, isClassmethod = true, parameterNames = {"$cls"})
    @GenerateNodeFactory
    public abstract static class UtcNowNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object utcNow(VirtualFrame frame, Object cls,
                        @Bind Node inliningTarget,
                        @Cached WarnNode warnNode,
                        @Cached("createFor($node)") IndirectCallData.BoundaryCallData boundaryCallData) {
            warnNode.warnFormat(frame, DeprecationWarning, WARN_DEPRECATED_UTCNOW);

            Object saved = ExecutionContext.BoundaryCallContext.enter(frame, boundaryCallData);
            try {
                return buildDateTimeInUtc(cls, inliningTarget);
            } finally {
                // A Python method call (DateTimeNodes.SubclassNewNode) should be
                // connected to a current node.
                ExecutionContext.BoundaryCallContext.exit(frame, boundaryCallData, saved);
            }
        }

        @TruffleBoundary
        private static Object buildDateTimeInUtc(Object cls, Node inliningTarget) {
            LocalDateTime utc = LocalDateTime.now(ZoneOffset.UTC);
            return toPDateTime(utc, PNone.NONE, 0, inliningTarget, cls);
        }
    }

    @Builtin(name = "fromtimestamp", minNumOfPositionalArgs = 2, isClassmethod = true, parameterNames = {"$cls", "timestamp", "tz"})
    @GenerateNodeFactory
    public abstract static class FromTimestampNode extends PythonTernaryBuiltinNode {

        @Specialization
        static Object fromTimestamp(VirtualFrame frame, Object cls, Object timestampObject, Object tzInfoObject,
                        @Bind Node inliningTarget,
                        @Cached("createFor($node)") IndirectCallData.BoundaryCallData boundaryCallData) {
            Object saved = ExecutionContext.BoundaryCallContext.enter(frame, boundaryCallData);
            try {
                return fromTimestampBoundary(cls, timestampObject, tzInfoObject, inliningTarget);
            } finally {
                // A Python method call (using PyObjectCallMethodObjArgs and
                // DateTimeNodes.SubclassNewNode) should be connected to a current node.
                ExecutionContext.BoundaryCallContext.exit(frame, boundaryCallData, saved);
            }
        }

        @TruffleBoundary
        private static Object fromTimestampBoundary(Object cls, Object timestampObject, Object tzInfoObject, Node inliningTarget) {
            final Object tzInfo;
            if (tzInfoObject instanceof PNone) {
                tzInfo = null;
            } else {
                if (!(tzInfoObject instanceof PTzInfo)) {
                    throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.TZINFO_ARGUMENT_MUST_BE_NONE_OR_OF_A_TZINFO_SUBCLASS_NOT_TYPE_P, tzInfoObject);
                }

                tzInfo = tzInfoObject;
            }

            // CPython: _PyTime_ObjectToTimeval
            final long seconds;
            final long microseconds;
            if (PyFloatCheckNode.executeUncached(timestampObject)) { // TODO: check for NaN/Infinity
                double timestamp = PyFloatAsDoubleNode.executeUncached(timestampObject);
                seconds = (long) timestamp;
                microseconds = (long) Math.rint((timestamp - (long) timestamp) * 1_000_000);
            } else {
                long timestamp = PyLongAsLongNode.executeUncached(timestampObject);
                seconds = timestamp;
                microseconds = 0;
            }

            final Instant instant;
            try {
                instant = Instant.ofEpochSecond(seconds);
            } catch (DateTimeException e) {
                throw PRaiseNode.raiseStatic(inliningTarget, OverflowError, ErrorMessages.TIMESTAMP_OUT_OF_RANGE);
            }

            if (tzInfo == null) {
                // convert from UTC to system timezone
                TimeZone timeZone = TimeModuleBuiltins.getGlobalTimeZone(getContext(inliningTarget));
                ZoneId zoneId = timeZone.toZoneId();
                ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant(instant, zoneId).plusNanos(microseconds * 1_000);

                final int fold;
                if (isBackwardTransitionDetected(instant, getContext(inliningTarget))) {
                    fold = 1;
                } else {
                    fold = 0;
                }

                return toPDateTime(zonedDateTime, PNone.NONE, fold, inliningTarget, cls);
            } else {
                // convert from UTC to the given timezone
                LocalDateTime utc = LocalDateTime.ofInstant(instant, ZoneOffset.UTC).plusNanos(microseconds * 1_000);

                // convert current time in UTC to the given time zone with tzinfo.fromutc()
                Object self = DateTimeNodes.SubclassNewNode.getUncached().execute(inliningTarget,
                                cls,
                                utc.getYear(),
                                utc.getMonthValue(),
                                utc.getDayOfMonth(),
                                utc.getHour(),
                                utc.getMinute(),
                                utc.getSecond(),
                                utc.getNano() / 1_000,
                                tzInfo, // set the final value beforehand - it's required by
                                        // #fromutc()
                                0);

                return PyObjectCallMethodObjArgs.executeUncached(tzInfo, T_FROMUTC, self);
            }
        }
    }

    @Builtin(name = "utcfromtimestamp", minNumOfPositionalArgs = 1, isClassmethod = true, parameterNames = {"$cls", "timestamp"})
    @GenerateNodeFactory
    public abstract static class UtcFromTimestampNode extends PythonBinaryBuiltinNode {

        @Specialization
        static Object fromTimestamp(VirtualFrame frame, Object cls, Object timestampObject,
                        @Bind Node inliningTarget,
                        @Cached WarnNode warnNode,
                        @Cached CastToJavaLongExactNode castToLongNode,
                        @Cached CastToJavaDoubleNode castToDoubleNode,
                        @Cached DateTimeNodes.SubclassNewNode newNode,
                        @Cached("createFor($node)") IndirectCallData.BoundaryCallData boundaryCallData) {
            warnNode.warnFormat(frame, DeprecationWarning, WARN_DEPRECATED_UTCFROMTIMESTAMP);

            Object saved = ExecutionContext.BoundaryCallContext.enter(frame, boundaryCallData);
            try {
                return buildDateTimeInUtcFromTimestamp(cls, timestampObject, inliningTarget, castToLongNode, castToDoubleNode, newNode);
            } finally {
                // A Python method call (using DateTimeNodes.SubclassNewNode) should be
                // connected to a current node.
                ExecutionContext.BoundaryCallContext.exit(frame, boundaryCallData, saved);
            }
        }

        @TruffleBoundary
        private static Object buildDateTimeInUtcFromTimestamp(Object cls, Object timestampObject, Node inliningTarget, CastToJavaLongExactNode castToLongNode, CastToJavaDoubleNode castToDoubleNode,
                        DateTimeNodes.SubclassNewNode newNode) {
            long seconds, microseconds;

            try {
                long timestamp = castToLongNode.execute(inliningTarget, timestampObject);
                seconds = timestamp;
                microseconds = 0;
            } catch (CannotCastException e) {
                try {
                    double timestamp = castToDoubleNode.execute(inliningTarget, timestampObject);
                    seconds = (long) timestamp;
                    microseconds = (long) Math.rint((timestamp - (long) timestamp) * 1_000_000);
                } catch (CannotCastException ex) {
                    throw PRaiseNode.raiseStatic(inliningTarget, TypeError, OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER);
                }
            }

            LocalDateTime epoch = LocalDateTime.of(1970, 1, 1, 0, 0, 0, 0);

            try {
                LocalDateTime utc = epoch.plusSeconds(seconds).plusNanos(microseconds * 1_000);

                final int fold;
                Instant instant = Instant.ofEpochSecond(seconds);
                if (isBackwardTransitionDetected(instant, getContext(inliningTarget))) {
                    fold = 1;
                } else {
                    fold = 0;
                }

                return toPDateTime(utc, PNone.NONE, fold, newNode, inliningTarget, cls);
            } catch (DateTimeException e) {
                throw PRaiseNode.raiseStatic(inliningTarget, OverflowError, ErrorMessages.TIMESTAMP_OUT_OF_RANGE);
            }
        }
    }

    @Builtin(name = "fromordinal", minNumOfPositionalArgs = 1, isClassmethod = true, parameterNames = {"$cls", "ordinal"})
    @ArgumentClinic(name = "ordinal", conversion = ArgumentClinic.ClinicConversion.Long)
    @GenerateNodeFactory
    public abstract static class FromOrdinalNode extends PythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return DateTimeBuiltinsClinicProviders.FromOrdinalNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object fromOrdinal(VirtualFrame frame, Object cls, long days,
                        @Bind Node inliningTarget,
                        @Cached("createFor($node)") IndirectCallData.BoundaryCallData boundaryCallData) {
            Object saved = ExecutionContext.BoundaryCallContext.enter(frame, boundaryCallData);
            try {
                return fromOrdinalBoundary(cls, days, inliningTarget);
            } finally {
                // A Python method call (using DateTimeNodes.SubclassNewNode) should be
                // connected to a current node.
                ExecutionContext.BoundaryCallContext.exit(frame, boundaryCallData, saved);
            }
        }

        @TruffleBoundary
        private static Object fromOrdinalBoundary(Object cls, long days, Node inliningTarget) {
            if (days <= 0) {
                throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.ORDINAL_MUST_BE_GREATER_THAN_ONE);
            }

            LocalDate baseLocalDate = LocalDate.of(1, 1, 1);
            LocalDate localDate = ChronoUnit.DAYS.addTo(baseLocalDate, days - 1);

            return DateTimeNodes.SubclassNewNode.getUncached().execute(inliningTarget, cls, localDate.getYear(), localDate.getMonthValue(), localDate.getDayOfMonth(), PNone.NO_VALUE, PNone.NO_VALUE,
                            PNone.NO_VALUE, PNone.NO_VALUE, PNone.NO_VALUE, PNone.NO_VALUE);
        }
    }

    @Builtin(name = "combine", minNumOfPositionalArgs = 3, isClassmethod = true, parameterNames = {"$cls", "date", "time", "tzinfo"})
    @GenerateNodeFactory
    public abstract static class CombineNode extends PythonBuiltinNode {

        @Specialization
        static Object combine(Object cls, Object dateObject, Object timeObject, Object tzInfoObject,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode,
                        @Cached DateTimeNodes.SubclassNewNode newNode) {
            if (!DateNodes.DateCheckNode.executeUncached(dateObject)) {
                throw raiseNode.raise(inliningTarget,
                                TypeError,
                                ErrorMessages.ARG_D_MUST_BE_S_NOT_P,
                                "combine()",
                                1,
                                "datetime.date",
                                dateObject);
            }

            if (!TimeNodes.TimeCheckNode.executeUncached(timeObject)) {
                throw raiseNode.raise(inliningTarget,
                                TypeError,
                                ErrorMessages.ARG_D_MUST_BE_S_NOT_P,
                                "combine()",
                                1,
                                "datetime.time",
                                timeObject);
            }

            PDate date = DateNodes.AsManagedDateNode.executeUncached(dateObject);
            PTime time = TimeNodes.AsManagedTimeNode.executeUncached(timeObject);

            final Object tzInfo;
            if (tzInfoObject instanceof PNone) {
                tzInfo = time.tzInfo;
            } else {
                tzInfo = tzInfoObject;
            }

            if (tzInfo != null && !(tzInfo instanceof PTzInfo)) {
                throw raiseNode.raise(inliningTarget,
                                TypeError,
                                ErrorMessages.TZINFO_ARGUMENT_MUST_BE_NONE_OR_OF_A_TZINFO_SUBCLASS_NOT_TYPE_P,
                                tzInfo);
            }

            return newNode.execute(inliningTarget, cls, date.year, date.month, date.day, time.hour, time.minute, time.second, time.microsecond, tzInfo, time.fold);
        }
    }

    @Builtin(name = "fromisoformat", minNumOfPositionalArgs = 2, isClassmethod = true, parameterNames = {"self", "date_string"})
    @GenerateNodeFactory
    public abstract static class FromIsoFormatNode extends PythonBuiltinNode {

        static class DateTimeBuilder {
            record UtcOffset(int sign, int hours, int minutes, int seconds, int microseconds) {

                public boolean isUtc() {
                    return hours == 0 && minutes == 0 && seconds == 0 && microseconds == 0;
                }
            }

            private final Object cls;
            private final Node inliningTarget;

            private int year;
            private int month;
            private int day;
            private int hours;
            private int minutes;
            private int seconds;
            private int microseconds;
            private UtcOffset utcOffset;

            DateTimeBuilder(Object cls, Node inliningTarget) {
                this.cls = cls;
                this.inliningTarget = inliningTarget;
            }

            public void setYear(int year) {
                this.year = year;
            }

            public void setMonth(int month) {
                this.month = month;
            }

            public void setDay(int day) {
                this.day = day;
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

            public void setWeekAndDayOfWeek(int week, int dayOfWeek) {
                LocalDate localDate = LocalDate.now().with(IsoFields.WEEK_BASED_YEAR, year).with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, week).with(ChronoField.DAY_OF_WEEK, dayOfWeek);

                year = localDate.getYear();
                month = localDate.getMonthValue();
                day = localDate.getDayOfMonth();
            }

            public void setUtcOffset(int sign, int hours, int minutes, int seconds, int microseconds) {
                this.utcOffset = new UtcOffset(sign, hours, minutes, seconds, microseconds);
            }

            public Object toDateTime() {
                final PTimeZone timezone;
                if (utcOffset == null) {
                    timezone = null;
                } else if (utcOffset.isUtc()) {
                    timezone = DatetimeModuleBuiltins.getUtcTimeZone(getContext(inliningTarget));
                } else {
                    final PTimeDelta timeDelta;
                    if (utcOffset.sign >= 0) {
                        timeDelta = TimeDeltaNodes.NewNode.getUncached().executeBuiltin(inliningTarget,
                                        0, utcOffset.seconds, utcOffset.microseconds, 0, utcOffset.minutes, utcOffset.hours, 0);
                    } else {
                        timeDelta = TimeDeltaNodes.NewNode.getUncached().executeBuiltin(inliningTarget,
                                        0, -utcOffset.seconds, -utcOffset.microseconds, 0, -utcOffset.minutes, -utcOffset.hours, 0);
                    }

                    DatetimeModuleBuiltins.validateUtcOffset(timeDelta, inliningTarget);

                    Object timeZoneType = PythonBuiltinClassType.PTimezone;
                    timezone = TimeZoneNodes.NewNode.getUncached().execute(inliningTarget, getContext(inliningTarget), timeZoneType, timeDelta, PNone.NO_VALUE);
                }

                return DateTimeNodes.SubclassNewNode.getUncached().execute(inliningTarget, cls, year, month, day, hours, minutes, seconds, microseconds, timezone, 0);
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
                // A Python method call (using PyObjectReprAsObjectNode and
                // DateTimeNodes.SubclassNewNode) should be connected to a current node.
                ExecutionContext.BoundaryCallContext.exit(frame, boundaryCallData, saved);
            }
        }

        @TruffleBoundary
        private static Object fromIsoFormatBoundary(Object cls, Object sourceObject, Node inliningTarget) {
            if (!PyUnicodeCheckNode.executeUncached(sourceObject)) {
                throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.FROMISOFORMAT_ARGUMENT_MUST_BE_STR);
            }

            String source;
            try {
                source = CastToJavaStringNode.getUncached().execute(sourceObject);
            } catch (CannotCastException ex) {
                throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.FROMISOFORMAT_ARGUMENT_MUST_BE_STR);
            }

            // Replace supplementary characters (that are represented with 2 chars) with 'T'.
            // Supplementary characters are only allowed in the separator position between the date
            // and time parts. Replace it with any character (e.g. 'T') to simplify resolving
            // ambiguity during parsing.
            if (source.codePointCount(0, source.length()) != source.length()) {
                StringBuilder builder = new StringBuilder(source.length());
                for (int i = 0; i < source.length();) {
                    int codePoint = source.codePointAt(i);

                    if (Character.isSupplementaryCodePoint(codePoint)) {
                        builder.append('T');
                    } else {
                        builder.appendCodePoint(codePoint);
                    }

                    i += Character.charCount(codePoint);
                }

                source = builder.toString();
            }

            Object dateTime = parseIsoFormat(source, cls, inliningTarget);
            if (dateTime == null) {
                Object sourceReprObject = PyObjectReprAsObjectNode.executeUncached(sourceObject);
                String sourceRepr = CastToJavaStringNode.getUncached().execute(sourceReprObject);
                throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.INVALID_ISOFORMAT_STRING_S, sourceRepr);
            }

            return dateTime;
        }

        @TruffleBoundary
        private static Object parseIsoFormat(String source, Object cls, Node inliningTarget) {
            DateTimeBuilder builder = new DateTimeBuilder(cls, inliningTarget);
            Integer year, month, day;
            boolean usesSeparator;

            try {
                // parse date part
                int pos = 0;

                year = parseDigits(source, pos, 4);
                if (year == null) {
                    return null;
                }
                pos += 4;
                builder.setYear(year);

                usesSeparator = source.charAt(pos) == '-';
                if (usesSeparator) {
                    pos += 1;
                }

                if (source.charAt(pos) == 'W') {
                    // This is an isocalendar-style date string (YYYY-<W>WW-D)
                    pos += 1;

                    // week
                    Integer week = parseDigits(source, pos, 2);
                    if (week == null) {
                        return null;
                    }
                    pos += 2;

                    // day of week
                    final Integer dayOfWeek;
                    if (pos < source.length()) {
                        // As far as a week day is optional and a separator between date and time
                        // components can be any character (not only 'T') there is ambiguity, e.g.
                        // 2020-W01-0000 could be YYYY-Www-D0HH or YYYY-Www-HHMM.
                        //
                        // Choose the second option.
                        //
                        // The logic is to check if there is even number of digits after a potential
                        // separator character (that's either HH or HHMM or HHMMSS).

                        final boolean found; // whether the next character is a separator between
                                             // date and time parts
                        boolean isCorrectDatePartContinuation = (usesSeparator && source.charAt(pos) == '-') || (!usesSeparator && isDigit(source.charAt(pos)));

                        if (isCorrectDatePartContinuation) {
                            if (pos + 1 < source.length()) {
                                // potential ambiguity
                                int from = pos + 1;
                                int stoppedAt = lookDigitsAhead(source, from);
                                int length = stoppedAt - from;

                                found = (length != 0) && (length % 2 == 0);
                            } else {
                                // there is no time part and a separator character
                                found = false; // e.g. '2025W014'
                            }
                        } else {
                            // it's definitely a separator
                            found = true;
                        }

                        if (found) {
                            dayOfWeek = 1; // use default value if day is missing
                        } else {
                            if (usesSeparator && source.charAt(pos++) != '-') {
                                return null;
                            }

                            dayOfWeek = parseDigits(source, pos, 1);
                            if (dayOfWeek == null) {
                                return null;
                            }
                            pos += 1;
                        }
                    } else {
                        dayOfWeek = 1; // use default value if day is missing
                    }

                    // don't raise specific errors for invalid component values
                    if (!DatetimeModuleBuiltins.validateIsoCalendarComponents(year, week, dayOfWeek)) {
                        return null;
                    }

                    builder.setWeekAndDayOfWeek(week, dayOfWeek);
                } else {
                    month = parseDigits(source, pos, 2);
                    if (month == null) {
                        return null;
                    }
                    pos += 2;
                    builder.setMonth(month);

                    if (usesSeparator && source.charAt(pos++) != '-') {
                        return null;
                    }

                    day = parseDigits(source, pos, 2);
                    if (day == null) {
                        return null;
                    }
                    pos += 2;
                    builder.setDay(day);
                }

                if (pos >= source.length()) {
                    return builder.toDateTime();
                }

                // parse time part

                final Integer hours;
                Integer minutes = 0, seconds = 0, microseconds = 0; // optional values

                // mandatory separator ('T' or any other character)
                pos += 1;

                // mandatory hours
                hours = parseDigits(source, pos, 2);
                if (hours == null) {
                    return null;
                }
                pos += 2;
                builder.setHours(hours);

                if (pos >= source.length()) {
                    return builder.toDateTime();
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
                        return builder.toDateTime();
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
                            return builder.toDateTime();
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
                                return builder.toDateTime();
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
                        return builder.toDateTime();
                    } else {
                        return null;
                    }
                }

                sign = source.charAt(pos) == '+' ? 1 : -1;
                pos++;

                // TODO: remove duplication
                // mandatory hours
                offsetHours = parseDigits(source, pos, 2);
                if (offsetHours == null) {
                    return null;
                }
                pos += 2;

                if (pos >= source.length()) {
                    builder.setUtcOffset(sign, offsetHours, 0, 0, 0);
                    return builder.toDateTime();
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
                        return builder.toDateTime();
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
                            return builder.toDateTime();
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
                                return builder.toDateTime();
                            }
                        }
                    }
                }

                return null;
            } catch (IndexOutOfBoundsException e) {
                return null;
            }
        }

        @TruffleBoundary
        private static Integer parseDigits(String source, int from, int digitsCount) {
            int result = 0;

            for (int i = 0; i < digitsCount; i++) {
                int tmp = source.charAt(from + i) - '0';
                if (tmp < 0 || tmp > 9) {
                    return null;
                }
                result = result * 10 + tmp;
            }

            return result;
        }

        @TruffleBoundary
        private static int lookDigitsAhead(String source, int from) {
            int i = from;

            while (i < source.length() && isDigit(source.charAt(i))) {
                i++;
            }

            return i;
        }

        private static boolean isDigit(char codepoint) {
            return codepoint >= '0' && codepoint <= '9';
        }
    }

    @Builtin(name = "fromisocalendar", minNumOfPositionalArgs = 4, isClassmethod = true, parameterNames = {"$cls", "year", "week", "day"})
    @ArgumentClinic(name = "year", conversion = ArgumentClinic.ClinicConversion.Long)
    @ArgumentClinic(name = "week", conversion = ArgumentClinic.ClinicConversion.Long)
    @ArgumentClinic(name = "day", conversion = ArgumentClinic.ClinicConversion.Long)
    @GenerateNodeFactory
    public abstract static class FromIsoCalendarNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return DateTimeBuiltinsClinicProviders.FromIsoCalendarNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object fromIsoCalendar(VirtualFrame frame, Object cls, long year, long week, long dayOfWeek,
                        @Bind Node inliningTarget,
                        @Cached("createFor($node)") IndirectCallData.BoundaryCallData boundaryCallData) {
            Object saved = ExecutionContext.BoundaryCallContext.enter(frame, boundaryCallData);
            try {
                return fromIsoCalendarBoundary(cls, year, week, dayOfWeek, inliningTarget);
            } finally {
                // A Python method call (using DateTimeNodes.SubclassNewNode) should be
                // connected to a current node.
                ExecutionContext.BoundaryCallContext.exit(frame, boundaryCallData, saved);
            }
        }

        @TruffleBoundary
        private static Object fromIsoCalendarBoundary(Object cls, long year, long week, long dayOfWeek, Node inliningTarget) {
            DatetimeModuleBuiltins.validateIsoCalendarComponentsAndRaise(inliningTarget, year, week, dayOfWeek);
            LocalDate localDate = LocalDate.now().with(IsoFields.WEEK_BASED_YEAR, year).with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, week).with(ChronoField.DAY_OF_WEEK, dayOfWeek);
            return DateTimeNodes.SubclassNewNode.getUncached().execute(inliningTarget, cls, localDate.getYear(), localDate.getMonthValue(), localDate.getDayOfMonth(), PNone.NO_VALUE, PNone.NO_VALUE,
                            PNone.NO_VALUE, PNone.NO_VALUE, PNone.NO_VALUE, PNone.NO_VALUE);
        }
    }

    @Builtin(name = "strptime", minNumOfPositionalArgs = 3, isClassmethod = true, parameterNames = {"$cls", "date_string", "format"})
    @ArgumentClinic(name = "date_string", conversion = ArgumentClinic.ClinicConversion.TString)
    @ArgumentClinic(name = "format", conversion = ArgumentClinic.ClinicConversion.TString)
    @GenerateNodeFactory
    public abstract static class StrPTimeNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return DateTimeBuiltinsClinicProviders.StrPTimeNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object strptime(VirtualFrame frame, Object cls, TruffleString stringTs, TruffleString formatTs,
                        @Bind Node inliningTarget,
                        @Cached TruffleString.ToJavaStringNode toJavaStringNode,
                        @Cached("createFor($node)") IndirectCallData.BoundaryCallData boundaryCallData) {
            String string = toJavaStringNode.execute(stringTs);
            String format = toJavaStringNode.execute(formatTs);

            Object saved = ExecutionContext.BoundaryCallContext.enter(frame, boundaryCallData);
            try {
                return parse(string, format, getContext(inliningTarget), cls, inliningTarget);
            } finally {
                // A Python method call (using DateTimeNodes.SubclassNewNode) should be
                // connected to a current node.
                ExecutionContext.BoundaryCallContext.exit(frame, boundaryCallData, saved);
            }
        }

        static class DateTimeBuilder {
            private final Node inliningTarget;

            private Integer year;
            private int month = 1;
            private int day = 1;
            private Integer hours;
            private int minutes = 0;
            private int seconds = 0;
            private int microseconds = 0;

            private boolean is12HourClock;
            private boolean isAm = true;

            private Integer dayOfYear;
            private Integer week;
            private Integer weekStartsOn;
            private Integer dayOfWeek;

            private Integer yearIso8601;
            private Integer weekIso8601;

            private String timeZoneName;
            private Integer timeZoneUtcOffsetAsSeconds;
            private Integer timeZoneUtcOffsetMicroseconds;

            DateTimeBuilder(Node inliningTarget) {
                this.inliningTarget = inliningTarget;
            }

            public void setYear(int year) {
                this.year = year;
            }

            public void setYearWithoutCentury(int year) {
                if (year <= 68) {
                    this.year = 2000 + year;
                } else {
                    this.year = 1900 + year;
                }
            }

            public void setMonth(int month) {
                this.month = month;
            }

            public void setDay(int day) {
                this.day = day;
            }

            public void setHours(int hours) {
                this.hours = hours;
                this.is12HourClock = false;
            }

            public void set12HourClockHours(int hours) {
                this.hours = hours;
                this.is12HourClock = true;
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

            public void setIsAm(boolean isAm) {
                this.isAm = isAm;
            }

            public void setDayOfYear(int dayOfYear) {
                this.dayOfYear = dayOfYear;
            }

            public void setWeekStartingOnSunday(int week) {
                this.week = week;
                this.weekStartsOn = 6;
            }

            public void setWeekStartingOnMonday(int week) {
                this.week = week;
                this.weekStartsOn = 0;
            }

            public void setDayOfWeek(int dayOfWeek) {
                // given parameter is in range 0-6 starting from Sunday
                if (dayOfWeek == 0) {
                    this.dayOfWeek = 6;
                } else {
                    this.dayOfWeek = dayOfWeek - 1;
                }
            }

            public void setDayOfWeekShortName(int dayOfWeek) {
                this.dayOfWeek = dayOfWeek;
            }

            public void setDayOfWeekFullName(int dayOfWeek) {
                this.dayOfWeek = dayOfWeek;
            }

            public void setYearIso8601(int yearIso8601) {
                this.yearIso8601 = yearIso8601;
            }

            public void setWeekIso8601(int weekIso8601) {
                this.weekIso8601 = weekIso8601;
            }

            public void setDayOfWeekIso8601(int dayOfWeekIso8601) {
                // given parameter is in range 1-7 starting from Monday
                this.dayOfWeek = dayOfWeekIso8601 - 1;
            }

            public void setDateTime(LocalDateTime dateTime) {
                year = dateTime.getYear();
                month = dateTime.getMonthValue();
                day = dateTime.getDayOfMonth();
                hours = dateTime.getHour();
                minutes = dateTime.getMinute();
                seconds = dateTime.getSecond();
            }

            public void setDate(LocalDate date) {
                year = date.getYear();
                month = date.getMonthValue();
                day = date.getDayOfMonth();
            }

            public void setTime(LocalTime time) {
                hours = time.getHour();
                minutes = time.getMinute();
                seconds = time.getSecond();
            }

            public void setTimeZoneName(String name) {
                this.timeZoneName = name;
            }

            public void setTimezoneUtcOffset(int seconds) {
                this.timeZoneUtcOffsetAsSeconds = seconds;
            }

            public void setTimezoneUtcOffset(int seconds, int microseconds) {
                this.timeZoneUtcOffsetAsSeconds = seconds;
                this.timeZoneUtcOffsetMicroseconds = microseconds;
            }

            public LocalDateTime getLocalDateTime() {
                // check whether there are some ambiguities
                if (yearIso8601 != null) {
                    if (dayOfYear != null) {
                        throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.DAY_OF_THE_YEAR_DIRECTIVE_IS_NOT_COMPATIBLE_WITH);
                    } else if (weekIso8601 == null || dayOfWeek == null) {
                        throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.ISO_YEAR_DIRECTIVE_MUST_BE_USED_WITH);
                    }
                } else if (weekIso8601 != null) {
                    if (year == null || dayOfWeek == null) {
                        throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.ISO_WEEK_DIRECTIVE_MUST_BE_USED_WITH);
                    } else {
                        throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.ISO_WEEK_DIRECTIVE_IS_INCOMPATIBLE_WITH);
                    }
                }

                // handle 'Feb 29' when year isn't given so default year 1900 becomes incorrect
                int year;
                boolean leadYearFix = false;
                if (this.year == null) {
                    if (this.month == 2 && this.day == 29) {
                        year = 1904; // 1904 is first leap year in the 20th century
                        leadYearFix = true;
                    } else {
                        year = 1900;
                    }
                } else {
                    year = this.year;
                }

                // Calculate month and day from day of year.
                // If day of year is given or can be calculated - it takes precedence over
                // month/day.
                int month = this.month;
                int day = this.day;
                if (dayOfYear != null || (dayOfWeek != null && week != null) || (dayOfWeek != null && weekIso8601 != null)) {
                    final LocalDate date;

                    if (this.dayOfYear != null) {
                        date = LocalDate.ofYearDay(year, dayOfYear);
                    } else if (week != null) {
                        final WeekFields weekFields;
                        int dayOfWeek;

                        assert weekStartsOn == 0 || weekStartsOn == 6; // either Monday or Sunday

                        if (weekStartsOn == 6) {
                            weekFields = WeekFields.of(DayOfWeek.SUNDAY, 7);

                            // convert Monday-based day of week to Sunday-based one
                            if (this.dayOfWeek == 6) {
                                dayOfWeek = 0;
                            } else {
                                dayOfWeek = this.dayOfWeek + 1;
                            }
                            dayOfWeek = dayOfWeek + 1; // convert from range 0..6 to 1..7
                        } else {
                            weekFields = WeekFields.of(DayOfWeek.MONDAY, 7);
                            dayOfWeek = this.dayOfWeek + 1; // convert from range 0..6 to 1..7
                        }

                        date = LocalDate.of(year, 1, 1).with(weekFields.weekOfYear(), week).with(weekFields.dayOfWeek(), dayOfWeek);
                    } else {
                        int dayOfWeek = this.dayOfWeek + 1; // convert from range 0..6 to 1..7
                        date = LocalDate.now().with(IsoFields.WEEK_BASED_YEAR, this.yearIso8601).with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, this.weekIso8601).with(ChronoField.DAY_OF_WEEK, dayOfWeek);
                    }

                    year = date.getYear();
                    month = date.getMonthValue();
                    day = date.getDayOfMonth();
                }

                if (leadYearFix) {
                    // year wasn't given but the date is Feb 29th. We couldn't use the default of
                    // 1900 for computations so changed it and set it back now.
                    year = 1900;
                }

                // calculate hours
                final int hours;
                if (this.hours == null) {
                    hours = 0;
                } else if (!this.is12HourClock) {
                    hours = this.hours;
                } else {
                    if (isAm) {
                        if (this.hours == 12) {
                            hours = 0; // 12 AM == midnight == hour 0
                        } else {
                            hours = this.hours;
                        }
                    } else {
                        if (this.hours == 12) {
                            hours = 12; // 12 PM == midday == hour 12
                        } else {
                            hours = this.hours + 12;
                        }
                    }
                }

                return LocalDateTime.of(year, month, day, hours, this.minutes, this.seconds, this.microseconds * 1_000);
            }

            String getTimeZoneName() {
                return timeZoneName;
            }

            Integer getTimeZoneUtcOffsetAsSeconds() {
                return timeZoneUtcOffsetAsSeconds;
            }

            Integer getTimeZoneUtcOffsetMicroseconds() {
                return timeZoneUtcOffsetMicroseconds;
            }
        }

        @TruffleBoundary
        private static Object parse(String string, String format, PythonContext context, Object cls, Node inliningTarget) {
            try {
                var builder = new DateTimeBuilder(inliningTarget);
                int i = 0, j = 0;

                while (i < string.length() && j < format.length()) {
                    if (format.charAt(j) != '%') {
                        if (string.charAt(i) != format.charAt(j)) {
                            throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.TIME_DATA_S_DOES_NOT_MATCH_FORMAT_S, string, format);
                        }

                        i++;
                        j++;
                    } else {
                        j++; // move from '%' to the format code character

                        switch (format.charAt(j)) {
                            case 'a' -> {
                                String pattern = "EEE"; // short form
                                var position = new ParsePosition(i);
                                TemporalAccessor accessor = parseLocalizedComponent(string, pattern, position);
                                DayOfWeek dayOfWeek = DayOfWeek.from(accessor);

                                int a = dayOfWeek.getValue() - 1; // DayOfWeek numeric values is
                                                                  // 1-based, so 1 is Monday etc
                                builder.setDayOfWeekShortName(a);

                                i = position.getIndex();
                            }
                            case 'A' -> {
                                String pattern = "EEEE"; // full form
                                var position = new ParsePosition(i);
                                TemporalAccessor accessor = parseLocalizedComponent(string, pattern, position);
                                DayOfWeek dayOfWeek = DayOfWeek.from(accessor);

                                int a = dayOfWeek.getValue() - 1; // DayOfWeek numeric values is
                                                                  // 1-based, so 1 is Monday etc
                                builder.setDayOfWeekFullName(a);

                                i = position.getIndex();
                            }
                            case 'w' -> {
                                Integer w = parseDigits(string, i, 1);

                                if (w == null) {
                                    throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.TIME_DATA_S_DOES_NOT_MATCH_FORMAT_S, string, format);
                                }

                                builder.setDayOfWeek(w);
                                i += 1;
                            }
                            case 'd' -> {
                                var pos = new ParsePosition(i);
                                Integer d = parseDigitsUpTo(string, pos, 2);

                                if (d == null) {
                                    throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.TIME_DATA_S_DOES_NOT_MATCH_FORMAT_S, string, format);
                                }

                                builder.setDay(d);
                                i = pos.getIndex();
                            }
                            case 'b' -> {
                                String pattern = "LLL"; // short form
                                var position = new ParsePosition(i);
                                TemporalAccessor accessor = parseLocalizedComponent(string, pattern, position);
                                Month month = Month.from(accessor);
                                int b = month.getValue();

                                builder.setMonth(b);
                                i = position.getIndex();
                            }
                            case 'B' -> {
                                String pattern = "LLLL"; // full form
                                var position = new ParsePosition(i);
                                TemporalAccessor accessor = parseLocalizedComponent(string, pattern, position);
                                Month month = Month.from(accessor);
                                int b = month.getValue();

                                builder.setMonth(b);
                                i = position.getIndex();
                            }
                            case 'm' -> {
                                var pos = new ParsePosition(i);
                                Integer m = parseDigitsUpTo(string, pos, 2);

                                if (m == null) {
                                    throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.TIME_DATA_S_DOES_NOT_MATCH_FORMAT_S, string, format);
                                }

                                builder.setMonth(m);
                                i = pos.getIndex();
                            }
                            case 'y' -> {
                                Integer y = parseDigits(string, i, 2);

                                if (y == null) {
                                    throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.TIME_DATA_S_DOES_NOT_MATCH_FORMAT_S, string, format);
                                }

                                builder.setYearWithoutCentury(y);
                                i += 2;
                            }
                            case 'Y' -> {
                                Integer y = parseDigits(string, i, 4);

                                if (y == null) {
                                    throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.TIME_DATA_S_DOES_NOT_MATCH_FORMAT_S, string, format);
                                }

                                builder.setYear(y);
                                i += 4;
                            }
                            case 'H' -> {
                                var pos = new ParsePosition(i);
                                Integer h = parseDigitsUpTo(string, pos, 2);

                                if (h == null) {
                                    throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.TIME_DATA_S_DOES_NOT_MATCH_FORMAT_S, string, format);
                                }

                                builder.setHours(h);
                                i = pos.getIndex();
                            }
                            case 'I' -> {
                                var pos = new ParsePosition(i);
                                Integer h = parseDigitsUpTo(string, pos, 2);

                                if (h == null) {
                                    throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.TIME_DATA_S_DOES_NOT_MATCH_FORMAT_S, string, format);
                                }

                                builder.set12HourClockHours(h);
                                i = pos.getIndex();
                            }
                            case 'p' -> {
                                // TODO: localize it
                                String p = string.substring(i, i + 2);

                                if (!p.equalsIgnoreCase("am") && !p.equalsIgnoreCase("pm")) {
                                    throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.TIME_DATA_S_DOES_NOT_MATCH_FORMAT_S, string, format);
                                }

                                boolean isAm = p.equalsIgnoreCase("am");
                                builder.setIsAm(isAm);
                                i += 2;
                            }
                            case 'M' -> {
                                var pos = new ParsePosition(i);
                                Integer m = parseDigitsUpTo(string, pos, 2);

                                if (m == null) {
                                    throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.TIME_DATA_S_DOES_NOT_MATCH_FORMAT_S, string, format);
                                }

                                builder.setMinutes(m);
                                i = pos.getIndex();
                            }
                            case 'S' -> {
                                var pos = new ParsePosition(i);
                                Integer s = parseDigitsUpTo(string, pos, 2);

                                if (s == null) {
                                    throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.TIME_DATA_S_DOES_NOT_MATCH_FORMAT_S, string, format);
                                }

                                builder.setSeconds(s);
                                i = pos.getIndex();
                            }
                            case 'f' -> {
                                var pos = new ParsePosition(i);
                                Integer f = parseDigitsUpTo(string, pos, 6);

                                if (f == null) {
                                    throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.TIME_DATA_S_DOES_NOT_MATCH_FORMAT_S, string, format);
                                }

                                // complete microseconds up to 6 digits
                                int length = pos.getIndex() - i;
                                if (length < 6) {
                                    for (int k = 1; k <= 6 - length; k++) {
                                        f *= 10;
                                    }
                                }

                                builder.setMicroseconds(f);
                                i = pos.getIndex();
                            }
                            case 'z' -> {
                                if (string.charAt(i) == 'Z') {
                                    builder.setTimezoneUtcOffset(0);
                                    i += 1;
                                } else {
                                    String regex = "\\A[+-]\\d\\d:?[0-5]\\d(:?[0-5]\\d(\\.\\d{1,6})?)?";
                                    Pattern pattern = Pattern.compile(regex);
                                    Matcher matcher = pattern.matcher(string);
                                    matcher.region(i, string.length());

                                    if (matcher.lookingAt()) {
                                        int pos = i;
                                        boolean hasSeparator = false;

                                        int sign = string.charAt(pos) == '+' ? 1 : -1;
                                        pos += 1;

                                        Integer hours = parseDigits(string, pos, 2);
                                        pos += 2;

                                        if (string.charAt(pos) == ':') {
                                            hasSeparator = true;
                                            pos += 1;
                                        }

                                        Integer minutes = parseDigits(string, pos, 2);
                                        pos += 2;

                                        if (pos == matcher.end()) {
                                            // [+-]HH:MM
                                            int secondsTotal = sign * (hours * 3600 + minutes * 60);
                                            builder.setTimezoneUtcOffset(secondsTotal);
                                        } else {
                                            // [+-]HH:SS:MM and optional microseconds
                                            if (hasSeparator != (string.charAt(pos) == ':')) {
                                                throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.INCONSISTENT_USE_OF_COLON_IN_S, string.substring(i, matcher.end()));
                                            }

                                            if (string.charAt(pos) == ':') {
                                                pos += 1;
                                            }

                                            Integer seconds = parseDigits(string, pos, 2);
                                            pos += 2;

                                            if (pos == matcher.end()) {
                                                int secondsTotal = sign * (hours * 3600 + minutes * 60 + seconds);
                                                builder.setTimezoneUtcOffset(secondsTotal);
                                            } else {
                                                pos += 1; // skip '.'

                                                int length = matcher.end() - pos;
                                                Integer microseconds = parseDigits(string, pos, length);

                                                // complete microseconds up to 6 digits
                                                if (length < 6) {
                                                    for (int k = 1; k <= 6 - length; k++) {
                                                        microseconds *= 10;
                                                    }
                                                }

                                                int secondsTotal = sign * (hours * 3600 + minutes * 60 + seconds);
                                                builder.setTimezoneUtcOffset(secondsTotal, sign * microseconds);
                                            }
                                        }

                                        i = matcher.end();
                                    } else {
                                        throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.TIME_DATA_S_DOES_NOT_MATCH_FORMAT_S, string, format);
                                    }
                                }
                            }
                            case 'Z' -> {
                                TimeZone timeZone = TimeModuleBuiltins.getGlobalTimeZone(context);
                                String zoneName = timeZone.getDisplayName(false, TimeZone.SHORT);
                                String zoneNameDaylightSaving = timeZone.getDisplayName(true, TimeZone.SHORT);

                                if (string.startsWith("UTC", i)) {
                                    builder.setTimeZoneName("UTC");
                                    i += 3;
                                } else if (string.startsWith("GMT", i)) {
                                    builder.setTimeZoneName("GMT");
                                    i += 3;
                                } else if (string.startsWith(zoneName, i)) {
                                    builder.setTimeZoneName(zoneName);
                                    i += zoneName.length();
                                } else if (string.startsWith(zoneNameDaylightSaving, i)) {
                                    builder.setTimeZoneName(zoneNameDaylightSaving);
                                    i += zoneNameDaylightSaving.length();
                                } else {
                                    throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.TIME_DATA_S_DOES_NOT_MATCH_FORMAT_S, string, format);
                                }
                            }
                            case 'j' -> {
                                var pos = new ParsePosition(i);
                                Integer jj = parseDigitsUpTo(string, pos, 3);

                                if (jj == null) {
                                    throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.TIME_DATA_S_DOES_NOT_MATCH_FORMAT_S, string, format);
                                }

                                builder.setDayOfYear(jj);
                                i = pos.getIndex();
                            }
                            case 'U' -> {
                                Integer u = parseDigits(string, i, 2);

                                if (u == null) {
                                    throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.TIME_DATA_S_DOES_NOT_MATCH_FORMAT_S, string, format);
                                }

                                builder.setWeekStartingOnSunday(u);
                                i += 2;
                            }
                            case 'W' -> {
                                var pos = new ParsePosition(i);
                                Integer w = parseDigitsUpTo(string, pos, 2);

                                if (w == null) {
                                    throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.TIME_DATA_S_DOES_NOT_MATCH_FORMAT_S, string, format);
                                }

                                builder.setWeekStartingOnMonday(w);
                                i = pos.getIndex();
                            }
                            case 'c' -> {
                                // TODO: don't hardcore format and use a localized one
                                String pattern = "E M d HH:mm:ss y";
                                var position = new ParsePosition(i);
                                TemporalAccessor accessor = parseLocalizedComponent(string, pattern, position);
                                LocalDateTime localDateTime = LocalDateTime.from(accessor);

                                builder.setDateTime(localDateTime);
                                i = position.getIndex();
                            }
                            case 'x' -> {
                                var locale = Locale.getDefault();
                                String pattern = DateTimeFormatterBuilder.getLocalizedDateTimePattern(FormatStyle.SHORT, null, IsoChronology.INSTANCE, locale);
                                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern, locale);
                                var position = new ParsePosition(i);
                                TemporalAccessor accessor = formatter.parse(string, position);
                                LocalDate localDate = LocalDate.from(accessor);

                                builder.setDate(localDate);
                                i = position.getIndex();
                            }
                            case 'X' -> {
                                // TODO: don't hardcore format and use a localized one
                                String pattern = "HH:mm:ss";
                                var position = new ParsePosition(i);
                                TemporalAccessor accessor = parseLocalizedComponent(string, pattern, position);
                                LocalTime localTime = LocalTime.from(accessor);

                                builder.setTime(localTime);
                                i = position.getIndex();
                            }
                            case '%' -> {
                                // just do nothing, it's escaped '%'
                            }
                            case 'G' -> {
                                Integer g = parseDigits(string, i, 4);

                                if (g == null) {
                                    throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.TIME_DATA_S_DOES_NOT_MATCH_FORMAT_S, string, format);
                                }

                                builder.setYearIso8601(g);
                                i += 4;
                            }
                            case 'u' -> {
                                Integer u = parseDigits(string, i, 1);

                                if (u == null) {
                                    throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.TIME_DATA_S_DOES_NOT_MATCH_FORMAT_S, string, format);
                                }

                                builder.setDayOfWeekIso8601(u);
                                i += 1;
                            }
                            case 'V' -> {
                                var pos = new ParsePosition(i);
                                Integer v = parseDigitsUpTo(string, pos, 2);

                                if (v == null) {
                                    throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.TIME_DATA_S_DOES_NOT_MATCH_FORMAT_S, string, format);
                                }

                                builder.setWeekIso8601(v);
                                i = pos.getIndex();
                            }
                            default ->
                                throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.S_IS_A_BAD_DIRECTIVE_IN_FORMAT_S, String.valueOf(format.charAt(j)), format);
                        }

                        j++; // move to the next character after the format code
                    }

                }

                // extra characters in the source string
                if (i < string.length()) {
                    throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.UNCONVERTED_DATA_REMAINS_S, string.substring(i));
                }

                // extra characters in the format string
                if (j < format.length()) {
                    throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.TIME_DATA_S_DOES_NOT_MATCH_FORMAT_S, string, format);
                }

                LocalDateTime localDateTime = builder.getLocalDateTime();

                final Object tzInfo;
                if (builder.getTimeZoneUtcOffsetAsSeconds() != null) {
                    final PTimeDelta utcOffset;
                    if (builder.getTimeZoneUtcOffsetMicroseconds() == null) {
                        utcOffset = TimeDeltaNodes.NewNode.getUncached().executeBuiltin(inliningTarget,
                                        0, builder.getTimeZoneUtcOffsetAsSeconds(), 0, 0, 0, 0, 0);
                    } else {
                        utcOffset = TimeDeltaNodes.NewNode.getUncached().executeBuiltin(inliningTarget,
                                        0, builder.getTimeZoneUtcOffsetAsSeconds(), builder.getTimeZoneUtcOffsetMicroseconds(), 0, 0, 0, 0);
                    }

                    if (builder.getTimeZoneName() == null) {
                        tzInfo = TimeZoneNodes.NewNode.getUncached().execute(inliningTarget, getContext(inliningTarget), PythonBuiltinClassType.PTimezone, utcOffset, PNone.NO_VALUE);
                    } else {
                        TruffleString name = TruffleString.FromJavaStringNode.getUncached().execute(builder.getTimeZoneName(), TS_ENCODING);
                        tzInfo = TimeZoneNodes.NewNode.getUncached().execute(inliningTarget, getContext(inliningTarget), PythonBuiltinClassType.PTimezone, utcOffset, name);
                    }
                } else {
                    tzInfo = PNone.NONE;
                }

                return toPDateTime(localDateTime, tzInfo, 0, inliningTarget, cls);
            } catch (IndexOutOfBoundsException | DateTimeParseException e) {
                throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.TIME_DATA_S_DOES_NOT_MATCH_FORMAT_S, string, format);
            }
        }

        @TruffleBoundary
        private static Integer parseDigits(String source, int from, int digitsCount) {
            int result = 0;

            for (int i = 0; i < digitsCount; i++) {
                int n = source.charAt(from + i) - '0';
                if (n < 0 || n > 9) {
                    return null;
                }
                result = result * 10 + n;
            }

            return result;
        }

        @TruffleBoundary
        private static Integer parseDigitsUpTo(String source, ParsePosition from, int maxDigitsCount) {
            int result = 0;
            int limit = Math.min(maxDigitsCount, source.length() - from.getIndex());

            for (int i = 0; i < limit; i++) {
                int n = source.charAt(from.getIndex() + i) - '0';
                if (n < 0 || n > 9) {
                    if (i == 0) {
                        return null;
                    } else {
                        from.setIndex(from.getIndex() + i);
                        return result;
                    }
                }
                result = result * 10 + n;
            }

            from.setIndex(from.getIndex() + limit);
            return result;
        }

        @TruffleBoundary
        private static TemporalAccessor parseLocalizedComponent(String source, String pattern, ParsePosition pos) {
            var locale = Locale.getDefault();
            var formatter = DateTimeFormatter.ofPattern(pattern, locale);
            return formatter.parse(source, pos);
        }
    }

    @Builtin(name = "date", minNumOfPositionalArgs = 1, parameterNames = {"$self"})
    @GenerateNodeFactory
    abstract static class DateNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object getDate(Object selfObj,
                        @Bind Node inliningTarget,
                        @Cached DateNodes.NewNode newDateNode) {
            PDateTime self = DateTimeNodes.AsManagedDateTimeNode.executeUncached(selfObj);
            return newDateNode.execute(inliningTarget,
                            PythonBuiltinClassType.PDate,
                            self.year,
                            self.month,
                            self.day);
        }
    }

    @Builtin(name = "time", minNumOfPositionalArgs = 1, parameterNames = {"$self"})
    @GenerateNodeFactory
    abstract static class TimeNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object getTime(Object selfObj,
                        @Bind Node inliningTarget,
                        @Cached TimeNodes.NewNode newTimeNode) {
            PDateTime self = DateTimeNodes.AsManagedDateTimeNode.executeUncached(selfObj);
            return newTimeNode.execute(inliningTarget,
                            PythonBuiltinClassType.PTime,
                            self.hour,
                            self.minute,
                            self.second,
                            self.microsecond,
                            PNone.NONE,
                            self.fold);
        }
    }

    @Builtin(name = "timetz", minNumOfPositionalArgs = 1, parameterNames = {"$self"})
    @GenerateNodeFactory
    abstract static class TimeTzNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object getTime(Object selfObj,
                        @Bind Node inliningTarget,
                        @Cached TimeNodes.NewNode newTimeNode) {
            PDateTime self = DateTimeNodes.AsManagedDateTimeNode.executeUncached(selfObj);
            return newTimeNode.execute(inliningTarget,
                            PythonBuiltinClassType.PTime,
                            self.hour,
                            self.minute,
                            self.second,
                            self.microsecond,
                            self.tzInfo,
                            self.fold);
        }
    }

    @Builtin(name = "replace", minNumOfPositionalArgs = 1, parameterNames = {"self", "year", "month", "day", "hour", "minute", "second", "microsecond", "tzinfo"}, keywordOnlyNames = {"fold"})
    @GenerateNodeFactory
    public abstract static class ReplaceNode extends PythonBuiltinNode {

        @Specialization
        static Object replace(VirtualFrame frame, Object selfObj, Object yearObject, Object monthObject, Object dayObject, Object hourObject, Object minuteObject, Object secondObject,
                        Object microsecondObject, Object tzInfoObject, Object foldObject,
                        @Bind Node inliningTarget,
                        @Cached PyLongAsLongNode asLongNode,
                        @Cached GetClassNode getClassNode,
                        @Cached DateTimeNodes.NewNode newDateTimeNode) {
            PDateTime self = DateTimeNodes.AsManagedDateTimeNode.executeUncached(selfObj);
            final long year, month, day;

            if (yearObject instanceof PNone) {
                year = self.year;
            } else {
                year = asLongNode.execute(frame, inliningTarget, yearObject);
            }

            if (monthObject instanceof PNone) {
                month = self.month;
            } else {
                month = asLongNode.execute(frame, inliningTarget, monthObject);
            }

            if (dayObject instanceof PNone) {
                day = self.day;
            } else {
                day = asLongNode.execute(frame, inliningTarget, dayObject);
            }

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

            Object type = getClassNode.execute(inliningTarget, selfObj);
            return newDateTimeNode.execute(inliningTarget, type, year, month, day, hour, minute, second, microsecond, tzInfo, fold);
        }
    }

    @Builtin(name = "astimezone", minNumOfPositionalArgs = 1, parameterNames = {"self", "tz"})
    @GenerateNodeFactory
    abstract static class AsTimeZoneNode extends PythonBinaryBuiltinNode {

        @Specialization
        static Object inTimeZone(VirtualFrame frame, Object self, Object tzInfo,
                        @Bind Node inliningTarget,
                        @Cached("createFor($node)") IndirectCallData.BoundaryCallData boundaryCallData) {
            Object saved = ExecutionContext.BoundaryCallContext.enter(frame, boundaryCallData);
            try {
                return inTimeZoneBoundary(self, tzInfo, inliningTarget);
            } finally {
                // A Python method call (using DatetimeModuleBuiltins.callUtcOffset
                // and PyObjectCallMethodObjArgs) should be connected to a current node.
                ExecutionContext.BoundaryCallContext.exit(frame, boundaryCallData, saved);
            }
        }

        @TruffleBoundary
        private static Object inTimeZoneBoundary(Object selfObj, Object tzInfo, Node inliningTarget) {
            PDateTime self = DateTimeNodes.AsManagedDateTimeNode.executeUncached(selfObj);
            if (tzInfo == self.tzInfo) {
                return self;
            }

            Object sourceTimeZone;
            if (self.tzInfo != null) {
                sourceTimeZone = self.tzInfo;
            } else {
                sourceTimeZone = getSystemTimeZoneAt(toLocalDateTime(self), self.fold, inliningTarget);
            }

            PTimeDelta sourceOffset = DatetimeModuleBuiltins.callUtcOffset(sourceTimeZone, self, inliningTarget);

            if (sourceOffset == null) {
                sourceTimeZone = getSystemTimeZoneAt(toLocalDateTime(self), self.fold, inliningTarget);
                sourceOffset = DatetimeModuleBuiltins.callUtcOffset(sourceTimeZone, self, inliningTarget);
            }

            LocalDateTime selfAsLocalDateTimeInUtc = subtractOffsetFromDateTime(self, sourceOffset);
            if (selfAsLocalDateTimeInUtc.getYear() < MIN_YEAR || selfAsLocalDateTimeInUtc.getYear() > MAX_YEAR) {
                throw PRaiseNode.raiseStatic(inliningTarget, OverflowError, ErrorMessages.DATE_VALUE_OUT_OF_RANGE);
            }

            final Object targetTimeZone;
            if (tzInfo instanceof PNone) {
                targetTimeZone = getSystemTimeZoneAt(toLocalDateTime(self), self.fold, inliningTarget);
            } else if (!(tzInfo instanceof PTzInfo)) {
                throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.TZINFO_ARGUMENT_MUST_BE_NONE_OR_OF_A_TZINFO_SUBCLASS_NOT_TYPE_P, tzInfo);
            } else {
                targetTimeZone = tzInfo;
            }

            Object selfInUtc = toPDateTime(selfAsLocalDateTimeInUtc, targetTimeZone, 0, inliningTarget, GetClassNode.executeUncached(selfObj));
            return PyObjectCallMethodObjArgs.executeUncached(targetTimeZone, T_FROMUTC, selfInUtc);
        }

        // CPython: local_timezone_from_local()
        private static PTimeZone getSystemTimeZoneAt(LocalDateTime localDateTime, int fold, Node inliningTarget) {
            TimeZone timeZone = TimeModuleBuiltins.getGlobalTimeZone(getContext(inliningTarget));
            ZoneId zoneId = timeZone.toZoneId();
            ZonedDateTime zonedDateTime = ZonedDateTime.of(localDateTime, zoneId);

            final long timestamp;
            if (localDateTime.equals(zonedDateTime.toLocalDateTime())) {
                final ZonedDateTime withFoldApplied;
                if (fold == 1) {
                    withFoldApplied = zonedDateTime.withLaterOffsetAtOverlap();
                } else {
                    withFoldApplied = zonedDateTime.withEarlierOffsetAtOverlap();
                }

                timestamp = withFoldApplied.toEpochSecond();
            } else {
                // There is a daylight saving time forward transition and the given datetime is in
                // the gap that's doesn't exist. Java's ZonedDateTime jumps forward in this case,
                // but Python's datetime in this case jumps forward only when fold is 1 and jumps
                // backward otherwise.

                if (fold == 0) {
                    long transitionSeconds = ChronoUnit.SECONDS.between(localDateTime, zonedDateTime.toLocalDateTime());
                    timestamp = zonedDateTime.toEpochSecond() - transitionSeconds;
                } else {
                    timestamp = zonedDateTime.toEpochSecond();
                }
            }

            String timeZoneName = timeZone.getDisplayName(); // TODO: get name localized
            long timestampMillis = timestamp * 1_000;
            int offsetMilliseconds = timeZone.getOffset(timestampMillis);

            Object timeDeltaType = PythonBuiltinClassType.PTimeDelta;
            Object offset = TimeDeltaNodes.NewNode.getUncached().execute(inliningTarget, timeDeltaType, 0, 0, 0, offsetMilliseconds, 0, 0, 0);

            Object timeZoneType = PythonBuiltinClassType.PTimezone;
            TruffleString timeZoneNameTS = TruffleString.FromJavaStringNode.getUncached().execute(timeZoneName, TS_ENCODING);

            return TimeZoneNodes.NewNode.getUncached().execute(inliningTarget, getContext(inliningTarget), timeZoneType, offset, timeZoneNameTS);
        }
    }

    @Builtin(name = "utcoffset", minNumOfPositionalArgs = 1, parameterNames = {"$self"})
    @GenerateNodeFactory
    abstract static class UtcOffsetNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object getUtcOffset(VirtualFrame frame, Object selfObj,
                        @Bind Node inliningTarget,
                        @Cached DateTimeNodes.TzInfoNode tzInfoNode,
                        @Cached PyObjectCallMethodObjArgs callMethodObjArgs,
                        @Cached PRaiseNode raiseNode) {
            Object tzInfo = tzInfoNode.execute(inliningTarget, selfObj);
            PTimeDelta offset = DatetimeModuleBuiltins.callUtcOffset(tzInfo, selfObj, frame, inliningTarget, callMethodObjArgs, raiseNode);

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
        static Object getDst(VirtualFrame frame, Object selfObj,
                        @Bind Node inliningTarget,
                        @Cached DateTimeNodes.TzInfoNode tzInfoNode,
                        @Cached PyObjectCallMethodObjArgs callMethodObjArgs,
                        @Cached PRaiseNode raiseNode) {
            Object tzInfo = tzInfoNode.execute(inliningTarget, selfObj);
            PTimeDelta offset = DatetimeModuleBuiltins.callDst(tzInfo, selfObj, frame, inliningTarget, callMethodObjArgs, raiseNode);

            if (offset == null) {
                return PNone.NONE;
            }

            return offset;
        }
    }

    @Builtin(name = "tzname", minNumOfPositionalArgs = 1, parameterNames = {"$self"})
    @GenerateNodeFactory
    abstract static class TzNameNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object getTzName(VirtualFrame frame, Object selfObj,
                        @Bind Node inliningTarget,
                        @Cached DateTimeNodes.TzInfoNode tzInfoNode,
                        @Cached PyObjectCallMethodObjArgs callMethodObjArgs,
                        @Cached PRaiseNode raiseNode,
                        @Cached PyUnicodeCheckNode unicodeCheckNode) {
            Object tzInfo = tzInfoNode.execute(inliningTarget, selfObj);
            if (tzInfo == null) {
                return PNone.NONE;
            }

            Object tzName = callMethodObjArgs.execute(frame, inliningTarget, tzInfo, T_TZNAME, selfObj);

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

    @Builtin(name = "timetuple", minNumOfPositionalArgs = 1, parameterNames = {"self"})
    @GenerateNodeFactory
    public abstract static class TimeTupleNode extends PythonUnaryBuiltinNode {

        @Specialization
        static PTuple composeTimeTuple(VirtualFrame frame, Object self,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached("createFor($node)") IndirectCallData.BoundaryCallData boundaryCallData) {
            Object saved = ExecutionContext.BoundaryCallContext.enter(frame, boundaryCallData);
            try {
                return composeTimeTupleBoundary(self, inliningTarget, language);
            } finally {
                // A Python method call (using DatetimeModuleBuiltins.callDst) should
                // be connected to a current node.
                ExecutionContext.BoundaryCallContext.exit(frame, boundaryCallData, saved);
            }
        }

        @TruffleBoundary
        private static PTuple composeTimeTupleBoundary(Object selfObj, Node inliningTarget, PythonLanguage language) {
            PDateTime self = DateTimeNodes.AsManagedDateTimeNode.executeUncached(selfObj);
            LocalDate localDate = LocalDate.of(self.year, self.month, self.day);
            int dayOfWeek = localDate.getDayOfWeek().getValue() - 1; // Python's day of week range
                                                                     // is 0-6
            int dayOfYear = localDate.getDayOfYear();
            int isDst = getIsDst(self, inliningTarget);

            Object[] fields = new Object[]{self.year, self.month, self.day, self.hour, self.minute, self.second, dayOfWeek, dayOfYear, isDst};
            return PFactory.createStructSeq(language, TimeModuleBuiltins.STRUCT_TIME_DESC, fields);
        }

        private static int getIsDst(PDateTime self, Node inliningTarget) {
            int isDst;
            PTimeDelta offset = DatetimeModuleBuiltins.callDst(self.tzInfo, self, inliningTarget);

            if (offset == null) {
                isDst = -1;
            } else {
                if (offset.days != 0 || offset.seconds != 0 || offset.microseconds != 0) {
                    isDst = 1;
                } else {
                    isDst = 0;
                }
            }

            return isDst;
        }
    }

    @Builtin(name = "utctimetuple", minNumOfPositionalArgs = 1, parameterNames = {"self"})
    @GenerateNodeFactory
    public abstract static class UtcTimeTupleNode extends PythonUnaryBuiltinNode {

        @Specialization
        static PTuple composeTimeTuple(VirtualFrame frame, Object self,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached("createFor($node)") IndirectCallData.BoundaryCallData boundaryCallData) {
            Object saved = ExecutionContext.BoundaryCallContext.enter(frame, boundaryCallData);
            try {
                return composeTimeTupleBoundary(self, inliningTarget, language);
            } finally {
                // A Python method call (using DatetimeModuleBuiltins.callUtcOffset
                // and PyObjectCallMethodObjArgs) should be connected to a current node.
                ExecutionContext.BoundaryCallContext.exit(frame, boundaryCallData, saved);
            }
        }

        @TruffleBoundary
        private static PTuple composeTimeTupleBoundary(Object selfObj, Node inliningTarget, PythonLanguage language) {
            PDateTime self = DateTimeNodes.AsManagedDateTimeNode.executeUncached(selfObj);
            final LocalDateTime localDateTime;
            PTimeDelta offset = DatetimeModuleBuiltins.callUtcOffset(self.tzInfo, self, inliningTarget);

            if (offset == null) {
                localDateTime = toLocalDateTime(self);
            } else {
                // convert self to UTC
                localDateTime = subtractOffsetFromDateTime(self, offset);

                if (localDateTime.getYear() < MIN_YEAR || localDateTime.getYear() > MAX_YEAR) {
                    throw PRaiseNode.raiseStatic(inliningTarget, OverflowError, ErrorMessages.DATE_VALUE_OUT_OF_RANGE);
                }
            }

            // calculate day-of-week and day-of-year
            int dayOfWeek = localDateTime.getDayOfWeek().getValue() - 1; // Python's day of week
                                                                         // range is 0-6
            int dayOfYear = localDateTime.getDayOfYear();

            Object[] fields = new Object[]{localDateTime.getYear(), localDateTime.getMonthValue(), localDateTime.getDayOfMonth(), localDateTime.getHour(), localDateTime.getMinute(),
                            localDateTime.getSecond(), dayOfWeek, dayOfYear, 0};
            return PFactory.createStructSeq(language, TimeModuleBuiltins.STRUCT_TIME_DESC, fields);
        }
    }

    @Builtin(name = "toordinal", minNumOfPositionalArgs = 1, parameterNames = {"self"})
    @GenerateNodeFactory
    public abstract static class ToOrdinalNode extends PythonUnaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        static long toOrdinal(Object selfObj) {
            PDateTime self = DateTimeNodes.AsManagedDateTimeNode.executeUncached(selfObj);
            LocalDate from = LocalDate.of(1, 1, 1);
            LocalDate to = LocalDate.of(self.year, self.month, self.day);
            return ChronoUnit.DAYS.between(from, to) + 1;
        }
    }

    @Builtin(name = "timestamp", minNumOfPositionalArgs = 1, parameterNames = {"self"})
    @GenerateNodeFactory
    public abstract static class TimestampNode extends PythonUnaryBuiltinNode {

        @Specialization
        static double toTimestamp(VirtualFrame frame, Object self,
                        @Bind Node inliningTarget,
                        @Cached("createFor($node)") IndirectCallData.BoundaryCallData boundaryCallData) {
            Object saved = ExecutionContext.BoundaryCallContext.enter(frame, boundaryCallData);
            try {
                return toTimestampBoundary(self, inliningTarget);
            } finally {
                // A Python method call (using DatetimeModuleBuiltins.callUtcOffset)
                // should be connected to a current node.
                ExecutionContext.BoundaryCallContext.exit(frame, boundaryCallData, saved);
            }
        }

        @TruffleBoundary
        private static double toTimestampBoundary(Object selfObj, Node inliningTarget) {
            PDateTime self = DateTimeNodes.AsManagedDateTimeNode.executeUncached(selfObj);
            if (self.tzInfo == null) {
                // CPython: local_to_seconds()
                TimeZone timeZone = TimeModuleBuiltins.getGlobalTimeZone(getContext(inliningTarget));
                ZoneId zoneId = timeZone.toZoneId();

                LocalDateTime localDateTime = toLocalDateTime(self);
                ZonedDateTime zonedDateTime = ZonedDateTime.of(localDateTime, zoneId);

                if (localDateTime.equals(zonedDateTime.toLocalDateTime())) {
                    final ZonedDateTime withFoldApplied;
                    if (self.fold == 1) {
                        withFoldApplied = zonedDateTime.withLaterOffsetAtOverlap();
                    } else {
                        withFoldApplied = zonedDateTime.withEarlierOffsetAtOverlap();
                    }

                    return withFoldApplied.toEpochSecond() + self.microsecond / 1_000_000.0;
                } else {
                    // There is a daylight saving time forward transition and the given datetime is
                    // in the gap that's doesn't exist. Java's ZonedDateTime jumps forward in this
                    // case, but Python's datetime jumps forward only when fold is 0 and jumps
                    // backward otherwise.

                    if (self.fold == 1) {
                        long transitionSeconds = ChronoUnit.SECONDS.between(localDateTime, zonedDateTime.toLocalDateTime());
                        return zonedDateTime.toEpochSecond() - transitionSeconds + self.microsecond / 1_000_000.0;
                    } else {
                        return zonedDateTime.toEpochSecond() + self.microsecond / 1_000_000.0;
                    }
                }
            } else {
                final LocalDateTime localDateTime;
                PTimeDelta offset = DatetimeModuleBuiltins.callUtcOffset(self.tzInfo, self, inliningTarget);

                if (offset == null) {
                    localDateTime = toLocalDateTime(self);
                } else {
                    // convert self to UTC
                    localDateTime = subtractOffsetFromDateTime(self, offset);
                }

                LocalDateTime epoch = LocalDateTime.of(1970, 1, 1, 0, 0, 0, 0);
                long seconds = ChronoUnit.SECONDS.between(epoch, localDateTime);

                return seconds + localDateTime.getNano() / 1_000_000_000.0;
            }
        }
    }

    @Builtin(name = "isoformat", minNumOfPositionalArgs = 1, parameterNames = {"self", "sep", "timespec"})
    @GenerateNodeFactory
    public abstract static class IsoFormatNode extends PythonTernaryBuiltinNode {

        @Specialization
        static TruffleString isoFormat(VirtualFrame frame, Object self, Object separatorObject, Object timespecObject,
                        @Bind Node inliningTarget,
                        @Cached("createFor($node)") IndirectCallData.BoundaryCallData boundaryCallData) {
            Object saved = ExecutionContext.BoundaryCallContext.enter(frame, boundaryCallData);
            try {
                return isoFormatBoundary(self, separatorObject, timespecObject, inliningTarget);
            } finally {
                // A Python method call (using DatetimeModuleBuiltins.callUtcOffset)
                // should be connected to a current node.
                ExecutionContext.BoundaryCallContext.exit(frame, boundaryCallData, saved);
            }
        }

        @TruffleBoundary
        private static TruffleString isoFormatBoundary(Object selfObj, Object separatorObject, Object timespecObject, Node inliningTarget) {
            PDateTime self = DateTimeNodes.AsManagedDateTimeNode.executeUncached(selfObj);
            var builder = new StringBuilder();

            String dateSection = PythonUtils.formatJString("%04d-%02d-%02d", self.year, self.month, self.day);
            builder.append(dateSection);

            if (separatorObject instanceof PNone) {
                builder.append(T_T);
            } else {
                String separator;
                try {
                    separator = CastToJavaStringNode.getUncached().execute(separatorObject);
                } catch (CannotCastException e) {
                    throw PRaiseNode.raiseStatic(inliningTarget,
                                    TypeError,
                                    ErrorMessages.ARG_D_MUST_BE_S_NOT_P,
                                    "isoformat()",
                                    1,
                                    "a unicode character",
                                    separatorObject);
                }

                // don't use String#length() because it returns 2 chars for supplementary characters
                if (separator.codePointCount(0, separator.length()) != 1) {
                    throw PRaiseNode.raiseStatic(inliningTarget,
                                    TypeError,
                                    ErrorMessages.ARG_D_MUST_BE_S_NOT_P,
                                    "isoformat()",
                                    1,
                                    "a unicode character",
                                    separatorObject);
                }

                builder.append(separator);
            }

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
                    int milliseconds = self.microsecond / 1_000;
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

            Object utcOffsetString = DatetimeModuleBuiltins.formatUtcOffset(self.tzInfo, self, true, inliningTarget);
            builder.append(utcOffsetString);

            return TruffleString.FromJavaStringNode.getUncached().execute(builder.toString(), TS_ENCODING);
        }
    }

    @Builtin(name = "ctime", minNumOfPositionalArgs = 1, parameterNames = {"self"})
    @GenerateNodeFactory
    public abstract static class CTimeNode extends PythonUnaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        static TruffleString cTime(Object selfObj) {
            PDateTime self = DateTimeNodes.AsManagedDateTimeNode.executeUncached(selfObj);
            LocalDateTime localDateTime = LocalDateTime.of(self.year, self.month, self.day, self.hour, self.minute, self.second);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEE LLL ppd HH:mm:ss yyyy");
            String ctime = localDateTime.format(formatter);
            return TruffleString.FromJavaStringNode.getUncached().execute(ctime, TS_ENCODING);
        }
    }

    @Builtin(name = "strftime", minNumOfPositionalArgs = 2, parameterNames = {"self", "format"})
    @ArgumentClinic(name = "format", conversion = ArgumentClinic.ClinicConversion.TString)
    @GenerateNodeFactory
    public abstract static class StrFTimeNode extends PythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return DateTimeBuiltinsClinicProviders.StrFTimeNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static TruffleString strftime(VirtualFrame frame, Object self, TruffleString format,
                        @Bind Node inliningTarget,
                        @Cached("createFor($node)") IndirectCallData.BoundaryCallData boundaryCallData) {
            Object saved = ExecutionContext.BoundaryCallContext.enter(frame, boundaryCallData);
            try {
                return strftimeBoundary(self, format, inliningTarget);
            } finally {
                // A Python method call (using PyObjectCallMethodObjArgs and
                // DatetimeModuleBuiltins.callUtcOffset) should be connected to a
                // current node.
                ExecutionContext.BoundaryCallContext.exit(frame, boundaryCallData, saved);
            }
        }

        @TruffleBoundary
        private static TruffleString strftimeBoundary(Object selfObj, TruffleString format, Node inliningTarget) {
            PDateTime self = DateTimeNodes.AsManagedDateTimeNode.executeUncached(selfObj);
            // Reuse time.strftime(format, time_tuple) method.

            // construct time_tuple
            LocalDate localDate = LocalDate.of(self.year, self.month, self.day);
            int dayOfWeek = localDate.getDayOfWeek().getValue() - 1;
            int dayOfYear = localDate.getDayOfYear();
            int[] timeTuple = new int[]{self.year, self.month, self.day, self.hour, self.minute, self.second, dayOfWeek, dayOfYear, -1};

            String formatPreprocessed = preprocessFormat(format, self, inliningTarget);

            return TimeModuleBuiltins.StrfTimeNode.format(formatPreprocessed, timeTuple, TruffleString.FromJavaStringNode.getUncached());
        }

        // The datetime.datetime.strftime() method supports some extra formatters - %f, %z, %:z,
        // and %Z so handle them here.
        // CPython: wrap_strftime()
        private static String preprocessFormat(TruffleString tsformat, PDateTime self, Node inliningTarget) {
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
                    Object utcOffsetString = DatetimeModuleBuiltins.formatUtcOffset(self.tzInfo, self, false, inliningTarget);
                    builder.append(utcOffsetString);
                    i = p + 2;
                } else if (c == 'Z') {
                    if (self.tzInfo != null) {
                        // call tzname()
                        Object tzNameObject = PyObjectCallMethodObjArgs.executeUncached(self.tzInfo, T_TZNAME, self);

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
                        Object utcOffsetString = DatetimeModuleBuiltins.formatUtcOffset(self.tzInfo, self, true, inliningTarget);
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

    @TruffleBoundary
    private static LocalDateTime subtractOffsetFromDateTime(PDateTime self, PTimeDelta offset) {
        return toLocalDateTime(self).minusDays(offset.days).minusSeconds(offset.seconds).minusNanos(offset.microseconds * 1_000L);
    }

    @TruffleBoundary
    private static LocalDateTime toLocalDateTime(PDateTime dateTime) {
        return LocalDateTime.of(dateTime.year, dateTime.month, dateTime.day, dateTime.hour, dateTime.minute, dateTime.second, dateTime.microsecond * 1_000);
    }

    private static Object toPDateTime(LocalDateTime local, Object tzInfo, int fold, Node inliningTarget, Object cls) {
        var newNode = DateTimeNodes.SubclassNewNode.getUncached();
        return toPDateTime(local, tzInfo, fold, newNode, inliningTarget, cls);
    }

    private static Object toPDateTime(LocalDateTime local, Object tzInfo, int fold, DateTimeNodes.SubclassNewNode newNode, Node inliningTarget, Object cls) {
        return newNode.execute(inliningTarget,
                        cls,
                        local.getYear(),
                        local.getMonthValue(),
                        local.getDayOfMonth(),
                        local.getHour(),
                        local.getMinute(),
                        local.getSecond(),
                        local.getNano() / 1_000,
                        tzInfo,
                        fold);
    }

    private static Object toPDateTime(ZonedDateTime local, Object tzInfo, int fold, Node inliningTarget, Object cls) {
        return DateTimeNodes.SubclassNewNode.getUncached().execute(inliningTarget,
                        cls,
                        local.getYear(),
                        local.getMonthValue(),
                        local.getDayOfMonth(),
                        local.getHour(),
                        local.getMinute(),
                        local.getSecond(),
                        local.getNano() / 1_000,
                        tzInfo,
                        fold);
    }

    /**
     * Check whether there was setting clocks back due to daylight saving time transition. CPython:
     * datetime_from_timet_and_us()
     */
    @TruffleBoundary
    private static boolean isBackwardTransitionDetected(Instant instant, PythonContext context) {
        TimeZone timeZone = TimeModuleBuiltins.getGlobalTimeZone(context);
        int offsetMillis = timeZone.getOffset(instant.toEpochMilli());

        Instant probe = instant.minusSeconds(MAX_FOLD_SECONDS);
        int probeOffsetMillis = timeZone.getOffset(probe.toEpochMilli());

        if (probeOffsetMillis > offsetMillis) {
            int offsetChangeMillis = offsetMillis - probeOffsetMillis;
            probe = instant.plusMillis(offsetChangeMillis);
            probeOffsetMillis = timeZone.getOffset(probe.toEpochMilli());

            return probeOffsetMillis != offsetMillis;
        } else {
            return false;
        }
    }
}
