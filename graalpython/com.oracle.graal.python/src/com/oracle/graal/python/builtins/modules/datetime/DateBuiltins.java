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
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
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
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryOp.BinaryOpBuiltinNode;
import com.oracle.graal.python.lib.PyFloatAsDoubleNode;
import com.oracle.graal.python.lib.PyFloatCheckNode;
import com.oracle.graal.python.lib.PyLongAsLongNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectHashNode;
import com.oracle.graal.python.lib.PyObjectStrAsObjectNode;
import com.oracle.graal.python.lib.PyUnicodeCheckNode;
import com.oracle.graal.python.lib.RichCmpOp;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.ExecutionContext;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.EncapsulatingNodeReference;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.util.List;
import java.util.TimeZone;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OverflowError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.builtins.modules.datetime.DatetimeModuleBuiltins.ISO_CALENDAR_DATE;
import static com.oracle.graal.python.builtins.modules.datetime.DatetimeModuleBuiltins.MAX_YEAR;
import static com.oracle.graal.python.builtins.modules.datetime.DatetimeModuleBuiltins.MIN_YEAR;
import static com.oracle.graal.python.builtins.objects.type.slots.TpSlotHashFun.*;
import static com.oracle.graal.python.builtins.objects.type.slots.TpSlotRichCompare.*;
import static com.oracle.graal.python.nodes.BuiltinNames.T__DATETIME;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___FORMAT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;
import static com.oracle.graal.python.nodes.StringLiterals.T_DATE;
import static com.oracle.graal.python.nodes.BuiltinNames.T_MAX;
import static com.oracle.graal.python.nodes.BuiltinNames.T_MIN;
import static com.oracle.graal.python.nodes.BuiltinNames.T_RESOLUTION;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PDate, PythonBuiltinClassType.PDateTime})
public final class DateBuiltins extends PythonBuiltins {

    public static final TpSlots SLOTS = DateBuiltinsSlotsGen.SLOTS;

    private static final int MAX_ORDINAL = 3_652_059; // date(9999,12,31).toordinal()

    private static final TruffleString T_STRFTIME = tsLiteral("strftime");

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return DateBuiltinsFactory.getFactories();
    }

    @Override
    public void postInitialize(Python3Core core) {
        super.postInitialize(core);

        PythonLanguage language = core.getLanguage();

        PythonModule datetimeModule = core.lookupBuiltinModule(T__DATETIME);
        PythonBuiltinClass self = (PythonBuiltinClass) datetimeModule.getAttribute(T_DATE);
        final var dateType = PythonBuiltinClassType.PDate;
        final var dateShape = dateType.getInstanceShape(language);

        final var minDate = new PDate(dateType, dateShape, MIN_YEAR, 1, 1);
        self.setAttribute(T_MIN, minDate);

        final var maxDate = new PDate(dateType, dateShape, MAX_YEAR, 12, 31);
        self.setAttribute(T_MAX, maxDate);

        final var timeDeltaType = PythonBuiltinClassType.PTimeDelta;
        final var timeDeltaShape = timeDeltaType.getInstanceShape(language);
        final var resolution = new PTimeDelta(timeDeltaType, timeDeltaShape, 1, 0, 0);
        self.setAttribute(T_RESOLUTION, resolution);
    }

    @Slot(value = Slot.SlotKind.tp_new, isComplex = true)
    @Slot.SlotSignature(name = "datetime.date", minNumOfPositionalArgs = 2, parameterNames = {"$cls", "year", "month", "day"})
    @GenerateNodeFactory
    public abstract static class NewNode extends PythonBuiltinNode {

        @Specialization
        static Object newDate(Object cls, Object yearObject, Object monthObject, Object dayObject,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raiseNode,
                        @Cached BytesNodes.ToBytesNode toBytesNode,
                        @Cached PyUnicodeCheckNode unicodeCheckNode,
                        @Cached CastToTruffleStringNode castToTruffleStringNode,
                        @Cached TruffleString.SwitchEncodingNode switchEncodingNode,
                        @Cached TruffleString.CopyToByteArrayNode copyToByteArrayNode,
                        @Cached DateNodes.NewUnsafeNode newUnsafeNode,
                        @Cached DateNodes.NewNode newNode) {
            // load Date serialized with pickle when given only bytes/string
            if (monthObject == PNone.NO_VALUE && dayObject == PNone.NO_VALUE) {
                if (yearObject instanceof PBytesLike bytesLike) {
                    // serialized Date into bytes is passed as the first parameter
                    // toBytesNode.execute acts as branch profile
                    byte[] bytes = toBytesNode.execute(bytesLike);

                    if (naiveBytesCheck(bytes)) {
                        return deserializeDate(bytes, inliningTarget, cls, newUnsafeNode);
                    }
                }

                if (unicodeCheckNode.execute(inliningTarget, yearObject)) {
                    // CPython: PyUnicode_AsLatin1String()
                    TruffleString string = castToTruffleStringNode.execute(inliningTarget, yearObject);

                    if (!string.isCompatibleToUncached(TruffleString.Encoding.ISO_8859_1)) {
                        throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.FAILED_TO_ENCODE_LATIN1_STRING_WHEN_UNPICKLING_A_DATE_OBJECT);
                    }

                    TruffleString stringLatin1 = switchEncodingNode.execute(string, TruffleString.Encoding.ISO_8859_1);
                    byte[] bytes = copyToByteArrayNode.execute(stringLatin1, TruffleString.Encoding.ISO_8859_1);

                    if (naiveBytesCheck(bytes)) {
                        return deserializeDate(bytes, inliningTarget, cls, newUnsafeNode);
                    }
                }
            }

            return newNode.execute(inliningTarget, cls, yearObject, monthObject, dayObject);
        }

        /**
         * Ensure bytes have correct format - correct length and month component has correct value.
         */
        private static boolean naiveBytesCheck(byte[] bytes) {
            if (bytes.length != 4) {
                return false;
            }

            int month = Byte.toUnsignedInt(bytes[2]);
            return month >= 1 && month <= 12;
        }

        /**
         * Construct a Date instance from a pickle serialized representation. Date is serialized as
         * bytes - (year / 256, year % 256, month, day).
         */
        private static PDate deserializeDate(byte[] bytes, Node inliningTarget, Object cls, DateNodes.NewUnsafeNode newNode) {
            int year = Byte.toUnsignedInt(bytes[0]) * 256 + Byte.toUnsignedInt(bytes[1]);
            int month = Byte.toUnsignedInt(bytes[2]);
            int day = Byte.toUnsignedInt(bytes[3]);

            return newNode.execute(inliningTarget, cls, year, month, day);
        }
    }

    @Slot(value = Slot.SlotKind.tp_repr, isComplex = true)
    @GenerateNodeFactory
    public abstract static class ReprNode extends PythonUnaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        static TruffleString repr(PDate self) {
            var string = String.format("datetime.date(%d, %d, %d)", self.year, self.month, self.day);
            return TruffleString.FromJavaStringNode.getUncached().execute(string, TS_ENCODING);
        }
    }

    @Slot(value = Slot.SlotKind.tp_str, isComplex = true)
    @GenerateNodeFactory
    public abstract static class StrNode extends PythonUnaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        static TruffleString str(PDate self) {
            var string = String.format("%04d-%02d-%02d", self.year, self.month, self.day);
            return TruffleString.FromJavaStringNode.getUncached().execute(string, TS_ENCODING);
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object reduce(PDate self,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached GetClassNode getClassNode) {
            byte[] bytes = new byte[]{(byte) (self.year / 256), (byte) (self.year % 256), (byte) self.month, (byte) self.day};
            PBytes string = PFactory.createBytes(language, bytes);

            PTuple arguments = PFactory.createTuple(language, new Object[]{string});
            Object type = getClassNode.execute(inliningTarget, self);
            return PFactory.createTuple(language, new Object[]{type, arguments});
        }
    }

    @Slot(value = Slot.SlotKind.tp_richcompare, isComplex = true)
    @GenerateNodeFactory
    abstract static class RichCmpNode extends RichCmpBuiltinNode {

        @Specialization
        static Object richCmp(PDate self, PDate other, RichCmpOp op) {
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
    abstract static class HashNode extends HashBuiltinNode {

        @Specialization
        static long hash(VirtualFrame frame, PDate self,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached PyObjectHashNode hashNode) {
            var content = new int[]{self.year, self.month, self.day};
            return hashNode.execute(frame, inliningTarget, PFactory.createTuple(language, content));
        }
    }

    @Slot(value = Slot.SlotKind.nb_add, isComplex = true)
    @GenerateNodeFactory
    abstract static class AddNode extends BinaryOpBuiltinNode {

        @Specialization
        static Object add(VirtualFrame frame, PDate self, PTimeDelta timeDelta,
                        @Bind Node inliningTarget,
                        @Cached("createFor($node)") @Shared IndirectCallData.BoundaryCallData boundaryCallData) {
            Object saved = ExecutionContext.BoundaryCallContext.enter(frame, boundaryCallData);
            try {
                return addBoundary(self, timeDelta, inliningTarget);
            } finally {
                // A Python method call (using DateNodes.SubclassNewNode) should be
                // connected to a current node.
                ExecutionContext.BoundaryCallContext.exit(frame, boundaryCallData, saved);
            }
        }

        @TruffleBoundary
        private static Object addBoundary(PDate self, PTimeDelta timeDelta, Node inliningTarget) {
            LocalDate from = LocalDate.of(1, 1, 1);
            LocalDate to = LocalDate.of(self.year, self.month, self.day);
            long days = ChronoUnit.DAYS.between(from, to) + 1;

            days += timeDelta.days;

            if (days <= 0) {
                throw PRaiseNode.raiseStatic(inliningTarget, OverflowError, ErrorMessages.DATE_VALUE_OUT_OF_RANGE);
            }
            if (days > MAX_ORDINAL) {
                throw PRaiseNode.raiseStatic(inliningTarget, OverflowError, ErrorMessages.DATE_VALUE_OUT_OF_RANGE);
            }

            LocalDate localDate = ChronoUnit.DAYS.addTo(from, days - 1);
            return DateNodes.SubclassNewNode.getUncached().execute(inliningTarget,
                            self.getPythonClass(),
                            localDate.getYear(),
                            localDate.getMonthValue(),
                            localDate.getDayOfMonth());
        }

        @Specialization
        static Object radd(VirtualFrame frame, PTimeDelta timeDelta, PDate self,
                        @Bind Node inliningTarget,
                        @Cached("createFor($node)") @Shared IndirectCallData.BoundaryCallData boundaryCallData) {
            return add(frame, self, timeDelta, inliningTarget, boundaryCallData);
        }

        @Fallback
        Object addObject(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Slot(value = Slot.SlotKind.nb_subtract, isComplex = true)
    @GenerateNodeFactory
    abstract static class SubNode extends BinaryOpBuiltinNode {

        @Specialization
        @TruffleBoundary
        static PTimeDelta sub(PDate self, PDate other,
                        @Bind PythonLanguage language) {
            LocalDate from = LocalDate.of(1, 1, 1);
            LocalDate toSelf = LocalDate.of(self.year, self.month, self.day);
            long daysSelf = ChronoUnit.DAYS.between(from, toSelf) + 1;

            LocalDate toOther = LocalDate.of(other.year, other.month, other.day);
            long daysOther = ChronoUnit.DAYS.between(from, toOther) + 1;

            long days = daysSelf - daysOther;
            return new PTimeDelta(
                            PythonBuiltinClassType.PTimeDelta,
                            PythonBuiltinClassType.PTimeDelta.getInstanceShape(language),
                            (int) days,
                            0,
                            0);
        }

        @Specialization
        static Object subTimeDelta(VirtualFrame frame, PDate self, PTimeDelta timeDelta,
                        @Bind Node inliningTarget,
                        @Cached("createFor($node)") IndirectCallData.BoundaryCallData boundaryCallData) {
            Object saved = ExecutionContext.BoundaryCallContext.enter(frame, boundaryCallData);
            try {
                return subTimeDeltaBoundary(self, timeDelta, inliningTarget);
            } finally {
                // A Python method call (using DateNodes.SubclassNewNode) should be
                // connected to a current node.
                ExecutionContext.BoundaryCallContext.exit(frame, boundaryCallData, saved);
            }
        }

        @TruffleBoundary
        private static Object subTimeDeltaBoundary(PDate self, PTimeDelta timeDelta, Node inliningTarget) {
            LocalDate from = LocalDate.of(1, 1, 1);
            LocalDate to = LocalDate.of(self.year, self.month, self.day);
            long days = ChronoUnit.DAYS.between(from, to) + 1;

            days -= timeDelta.days;

            if (days <= 0) {
                throw PRaiseNode.raiseStatic(inliningTarget, OverflowError, ErrorMessages.DATE_VALUE_OUT_OF_RANGE);
            }
            if (days >= MAX_ORDINAL) {
                throw PRaiseNode.raiseStatic(inliningTarget, OverflowError, ErrorMessages.DATE_VALUE_OUT_OF_RANGE);
            }

            LocalDate localDate = ChronoUnit.DAYS.addTo(from, days - 1);
            return DateNodes.SubclassNewNode.getUncached().execute(inliningTarget,
                            self.getPythonClass(),
                            localDate.getYear(),
                            localDate.getMonthValue(),
                            localDate.getDayOfMonth());
        }

        @Fallback
        Object subObject(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = "year", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class YearNode extends PythonUnaryBuiltinNode {

        @Specialization
        static int getYear(PDate self) {
            return self.year;
        }
    }

    @Builtin(name = "month", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class MonthNode extends PythonUnaryBuiltinNode {

        @Specialization
        static int getMonth(PDate self) {
            return self.month;
        }
    }

    @Builtin(name = "day", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class DayNode extends PythonUnaryBuiltinNode {

        @Specialization
        static int getDay(PDate self) {
            return self.day;
        }
    }

    @Builtin(name = "today", minNumOfPositionalArgs = 1, isClassmethod = true, parameterNames = {"self"})
    @GenerateNodeFactory
    public abstract static class TodayNode extends PythonBuiltinNode {

        @Specialization
        static Object today(VirtualFrame frame, Object cls,
                        @Bind Node inliningTarget,
                        @Cached("createFor($node)") IndirectCallData.BoundaryCallData boundaryCallData) {
            Object saved = ExecutionContext.BoundaryCallContext.enter(frame, boundaryCallData);
            try {
                return todayBoundary(cls, inliningTarget);
            } finally {
                // A Python method call (using DateNodes.SubclassNewNode) should be
                // connected to a current node.
                ExecutionContext.BoundaryCallContext.exit(frame, boundaryCallData, saved);
            }
        }

        @TruffleBoundary
        private static Object todayBoundary(Object cls, Node inliningTarget) {
            var localDate = LocalDate.now();
            return DateNodes.SubclassNewNode.getUncached().execute(inliningTarget, cls, localDate.getYear(), localDate.getMonthValue(), localDate.getDayOfMonth());
        }
    }

    @Builtin(name = "fromtimestamp", minNumOfPositionalArgs = 2, isClassmethod = true, parameterNames = {"self", "timestamp"})
    @GenerateNodeFactory
    public abstract static class FromTimestampNode extends PythonBinaryBuiltinNode {

        @Specialization
        static Object fromTimestamp(Object cls, Object timestampObject,
                        @Bind Node inliningTarget) {
            EncapsulatingNodeReference encapsulating = EncapsulatingNodeReference.getCurrent();
            Node encapsulatingNode = encapsulating.set(inliningTarget);
            try {
                return fromTimestampBoundary(cls, timestampObject, inliningTarget);
            } finally {
                // Some uncached nodes (e.g. PyFloatAsDoubleNode and PyLongAsLongNode)
                // may raise exceptions that are not connected to a current node. Set
                // the current node manually.
                encapsulating.set(encapsulatingNode);
            }
        }

        @TruffleBoundary
        private static Object fromTimestampBoundary(Object cls, Object timestampObject, Node inliningTarget) {
            // CPython: _PyTime_ObjectToTime_t
            final long timestamp;
            if (PyFloatCheckNode.executeUncached(timestampObject)) {
                timestamp = (long) PyFloatAsDoubleNode.executeUncached(timestampObject);
            } else {
                timestamp = PyLongAsLongNode.executeUncached(timestampObject);
            }

            final Instant instant;
            try {
                instant = Instant.ofEpochSecond(timestamp);
            } catch (DateTimeException e) {
                throw PRaiseNode.raiseStatic(inliningTarget, OverflowError, ErrorMessages.TIMESTAMP_OUT_OF_RANGE);
            }

            TimeZone timeZone = TimeModuleBuiltins.getGlobalTimeZone(getContext(inliningTarget));
            ZoneId zoneId = timeZone.toZoneId();
            LocalDate localDate = LocalDate.ofInstant(instant, zoneId);
            return DateNodes.SubclassNewNode.getUncached().execute(inliningTarget, cls, localDate.getYear(), localDate.getMonthValue(), localDate.getDayOfMonth());
        }
    }

    @Builtin(name = "fromisocalendar", minNumOfPositionalArgs = 4, isClassmethod = true, parameterNames = {"self", "year", "week", "day"})
    @ArgumentClinic(name = "year", conversion = ArgumentClinic.ClinicConversion.Long)
    @ArgumentClinic(name = "week", conversion = ArgumentClinic.ClinicConversion.Long)
    @ArgumentClinic(name = "day", conversion = ArgumentClinic.ClinicConversion.Long)
    @GenerateNodeFactory
    public abstract static class FromIsoCalendarNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return DateBuiltinsClinicProviders.FromIsoCalendarNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object fromIsoCalendar(VirtualFrame frame, Object cls, long year, long week, long dayOfWeek,
                        @Bind Node inliningTarget,
                        @Cached("createFor($node)") IndirectCallData.BoundaryCallData boundaryCallData) {
            Object saved = ExecutionContext.BoundaryCallContext.enter(frame, boundaryCallData);
            try {
                return fromIsoCalendarBoundary(cls, year, week, dayOfWeek, inliningTarget);
            } finally {
                // A Python method call (using DateNodes.SubclassNewNode) should be
                // connected to a current node.
                ExecutionContext.BoundaryCallContext.exit(frame, boundaryCallData, saved);
            }
        }

        @TruffleBoundary
        private static Object fromIsoCalendarBoundary(Object cls, long year, long week, long dayOfWeek, Node inliningTarget) {
            DatetimeModuleBuiltins.validateIsoCalendarComponentsAndRaise(inliningTarget, year, week, dayOfWeek);
            LocalDate localDate = LocalDate.now().with(IsoFields.WEEK_BASED_YEAR, year).with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, week).with(ChronoField.DAY_OF_WEEK, dayOfWeek);
            return DateNodes.SubclassNewNode.getUncached().execute(inliningTarget, cls, localDate.getYear(), localDate.getMonthValue(), localDate.getDayOfMonth());
        }
    }

    @Builtin(name = "fromisoformat", minNumOfPositionalArgs = 2, isClassmethod = true, parameterNames = {"self", "date_string"})
    @GenerateNodeFactory
    public abstract static class FromIsoFormatNode extends PythonBuiltinNode {

        @Specialization
        static Object fromIsoFormat(VirtualFrame frame, Object cls, Object object,
                        @Bind Node inliningTarget,
                        @Cached PyUnicodeCheckNode pyUnicodeCheckNode,
                        @Cached CastToJavaStringNode castToJavaStringNode,
                        @Cached("createFor($node)") IndirectCallData.BoundaryCallData boundaryCallData) {
            if (!pyUnicodeCheckNode.execute(inliningTarget, object)) {
                throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.FROMISOFORMAT_ARGUMENT_MUST_BE_STR);
            }

            final String source;
            try {
                source = castToJavaStringNode.execute(object);
            } catch (CannotCastException ex) {
                throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.FROMISOFORMAT_ARGUMENT_MUST_BE_STR);
            }

            if (source.length() != 7 && source.length() != 8 && source.length() != 10) {
                throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.INVALID_ISOFORMAT_STRING_S, object);
            }

            Object date;
            Object saved = ExecutionContext.BoundaryCallContext.enter(frame, boundaryCallData);
            try {
                date = parseIsoDateFormat(source, inliningTarget, cls);
            } finally {
                // A Python method call (using DateNodes.SubclassNewNode) should be
                // connected to a current node.
                ExecutionContext.BoundaryCallContext.exit(frame, boundaryCallData, saved);
            }

            if (date == null) {
                throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.INVALID_ISOFORMAT_STRING_S, object);
            }

            return date;
        }

        // CPython: parse_isoformat_date()
        @TruffleBoundary
        private static Object parseIsoDateFormat(String source, Node inliningTarget, Object cls) {
            try {
                int pos = 0;

                Integer year = parseDigits(source, pos, 4);
                if (year == null) {
                    return null;
                }
                pos += 4;

                boolean usesSeparator = source.charAt(pos) == '-';
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
                        if (usesSeparator && source.charAt(pos++) != '-') {
                            return null;
                        }

                        dayOfWeek = parseDigits(source, pos, 1);
                        if (dayOfWeek == null) {
                            return null;
                        }
                    } else {
                        dayOfWeek = 1;
                    }

                    // don't raise specific errors for invalid component values
                    if (!DatetimeModuleBuiltins.validateIsoCalendarComponents(year, week, dayOfWeek)) {
                        return null;
                    }

                    LocalDate localDate = LocalDate.now().with(IsoFields.WEEK_BASED_YEAR, year).with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, week).with(ChronoField.DAY_OF_WEEK, dayOfWeek);
                    return DateNodes.SubclassNewNode.getUncached().execute(inliningTarget,
                                    cls,
                                    localDate.getYear(),
                                    localDate.getMonthValue(),
                                    localDate.getDayOfMonth());
                }

                Integer month = parseDigits(source, pos, 2);
                if (month == null) {
                    return null;
                }
                pos += 2;

                if (usesSeparator && source.charAt(pos++) != '-') {
                    return null;
                }

                Integer day = parseDigits(source, pos, 2);
                if (day == null) {
                    return null;
                }

                return DateNodes.SubclassNewNode.getUncached().execute(inliningTarget, cls, year, month, day);
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
    }

    @Builtin(name = "replace", minNumOfPositionalArgs = 1, parameterNames = {"self", "year", "month", "day"})
    @GenerateNodeFactory
    public abstract static class ReplaceNode extends PythonBuiltinNode {

        @Specialization
        static PDate replace(VirtualFrame frame, PDate self, Object yearObject, Object monthObject, Object dayObject,
                        @Bind Node inliningTarget,
                        @Cached PyLongAsLongNode longAsLongNode,
                        @Cached DateNodes.NewNode newNode) {
            final int year, month, day;

            if (yearObject instanceof PNone) {
                year = self.year;
            } else {
                year = (int) longAsLongNode.execute(frame, inliningTarget, yearObject);
            }

            if (monthObject instanceof PNone) {
                month = self.month;
            } else {
                month = (int) longAsLongNode.execute(frame, inliningTarget, monthObject);
            }

            if (dayObject instanceof PNone) {
                day = self.day;
            } else {
                day = (int) longAsLongNode.execute(frame, inliningTarget, dayObject);
            }

            return newNode.execute(inliningTarget, self.getPythonClass(), year, month, day);
        }
    }

    @Builtin(name = "toordinal", minNumOfPositionalArgs = 1, parameterNames = {"self"})
    @GenerateNodeFactory
    public abstract static class ToOrdinalNode extends PythonUnaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        static long toOrdinal(PDate self) {
            LocalDate from = LocalDate.of(1, 1, 1);
            LocalDate to = LocalDate.of(self.year, self.month, self.day);
            return ChronoUnit.DAYS.between(from, to) + 1;
        }
    }

    @Builtin(name = "fromordinal", minNumOfPositionalArgs = 1, isClassmethod = true, parameterNames = {"self", "ordinal"})
    @ArgumentClinic(name = "ordinal", conversion = ArgumentClinic.ClinicConversion.Long)
    @GenerateNodeFactory
    public abstract static class FromOrdinalNode extends PythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return DateBuiltinsClinicProviders.FromOrdinalNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object fromOrdinal(VirtualFrame frame, Object cls, long days,
                        @Bind Node inliningTarget,
                        @Cached("createFor($node)") IndirectCallData.BoundaryCallData boundaryCallData) {
            Object saved = ExecutionContext.BoundaryCallContext.enter(frame, boundaryCallData);
            try {
                return fromOrdinalBoundary(cls, days, inliningTarget);
            } finally {
                // A Python method call (using DateNodes.SubclassNewNode) should be
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

            return DateNodes.SubclassNewNode.getUncached().execute(inliningTarget, cls, localDate.getYear(), localDate.getMonthValue(), localDate.getDayOfMonth());
        }
    }

    @Builtin(name = "weekday", minNumOfPositionalArgs = 1, parameterNames = {"self"})
    @GenerateNodeFactory
    public abstract static class WeekDayNode extends PythonUnaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        static int weekDay(PDate self) {
            LocalDate localDate = LocalDate.of(self.year, self.month, self.day);
            DayOfWeek dayOfWeek = localDate.getDayOfWeek();

            // adjust 1-7 range of Java's DayOfWeek to Python's week day range 0-6
            return dayOfWeek.getValue() - 1;
        }
    }

    @Builtin(name = "isoweekday", minNumOfPositionalArgs = 1, parameterNames = {"self"})
    @GenerateNodeFactory
    public abstract static class IsoWeekDayNode extends PythonUnaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        static int weekDay(PDate self) {
            LocalDate localDate = LocalDate.of(self.year, self.month, self.day);
            DayOfWeek dayOfWeek = localDate.getDayOfWeek();
            return dayOfWeek.getValue();
        }
    }

    @Builtin(name = "isocalendar", minNumOfPositionalArgs = 1, parameterNames = {"self"})
    @GenerateNodeFactory
    public abstract static class IsoCalendarNode extends PythonUnaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        static PTuple isoCalendar(PDate self,
                        @Bind PythonLanguage language) {
            LocalDate localDate = LocalDate.of(self.year, self.month, self.day);

            // use week based year ISO-8601 calendar
            int year = localDate.get(IsoFields.WEEK_BASED_YEAR);
            int weekOfYear = localDate.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            int dayOfWeek = localDate.getDayOfWeek().getValue();

            return PFactory.createStructSeq(language, ISO_CALENDAR_DATE, year, weekOfYear, dayOfWeek);
        }
    }

    @Builtin(name = "isoformat", minNumOfPositionalArgs = 1, parameterNames = {"self"})
    @GenerateNodeFactory
    public abstract static class IsoFormatNode extends PythonUnaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        static TruffleString isoFormat(PDate self) {
            LocalDate locaDate = LocalDate.of(self.year, self.month, self.day);
            var isoString = locaDate.toString();
            return TruffleString.FromJavaStringNode.getUncached().execute(isoString, TS_ENCODING);
        }
    }

    @Builtin(name = "ctime", minNumOfPositionalArgs = 1, parameterNames = {"self"})
    @GenerateNodeFactory
    public abstract static class CTimeNode extends PythonUnaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        static TruffleString cTime(PDate self) {
            LocalDate localDate = LocalDate.of(self.year, self.month, self.day);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEE LLL ppd 00:00:00 yyyy");
            String ctime = localDate.format(formatter);
            return TruffleString.FromJavaStringNode.getUncached().execute(ctime, TS_ENCODING);
        }
    }

    @Builtin(name = "timetuple", minNumOfPositionalArgs = 1, parameterNames = {"self"})
    @GenerateNodeFactory
    public abstract static class TimeTupleNode extends PythonUnaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        static PTuple timeTuple(PDate self,
                        @Bind PythonLanguage language) {
            LocalDate localDate = LocalDate.of(self.year, self.month, self.day);

            // Python's day of week is in range 0-6
            int dayOfWeek = localDate.getDayOfWeek().getValue() - 1;
            int dayOfYear = localDate.getDayOfYear();

            Object[] fields = new Object[]{self.year, self.month, self.day, 0, 0, 0, dayOfWeek, dayOfYear, -1};
            return PFactory.createStructSeq(language, TimeModuleBuiltins.STRUCT_TIME_DESC, fields);
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
        @TruffleBoundary
        static TruffleString strftime(PDate self, TruffleString format) {
            // Reuse time.strftime(format, time_tuple) method.

            // construct time_tuple
            LocalDate localDate = LocalDate.of(self.year, self.month, self.day);
            int dayOfWeek = localDate.getDayOfWeek().getValue() - 1;
            int dayOfYear = localDate.getDayOfYear();
            int[] timeTuple = new int[]{self.year, self.month, self.day, 0, 0, 0, dayOfWeek, dayOfYear, -1};

            String formatPreprocessed = preprocessFormat(format);

            return TimeModuleBuiltins.StrfTimeNode.format(formatPreprocessed, timeTuple, TruffleString.FromJavaStringNode.getUncached());
        }

        // The datetime.date.strftime() method supports some extra formatters - %f, %z, %:z, and
        // %Z so handle them here.
        // CPython: wrap_strftime()
        private static String preprocessFormat(TruffleString tsformat) {
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
                    i = p + 2; // replace %z with ""
                } else if (c == 'Z') {
                    i = p + 2; // replace %Z with ""
                } else if (c == 'f') {
                    i = p + 2;
                    builder.append("000000");
                } else if (c == ':') {
                    if (p + 2 == format.length()) {
                        builder.append(':');
                        break;
                    }

                    char d = format.charAt(p + 2);
                    if (d == 'z') {
                        i = p + 3; // replace %:z with ""
                    }
                } else {
                    i = p + 2;
                    builder.append(format, p, p + 2); // append %<formatter> without change
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
        static Object format(VirtualFrame frame, PDate self, TruffleString format,
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
}
