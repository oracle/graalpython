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
package com.oracle.graal.python.builtins.modules.datetime;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.object.IsForeignObjectNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;

public final class TemporalNodes {
    private TemporalNodes() {
    }

    @ValueType
    public static final class DateValue {
        public final int year;
        public final int month;
        public final int day;

        public DateValue(int year, int month, int day) {
            this.year = year;
            this.month = month;
            this.day = day;
        }

        public static DateValue of(PDate date) {
            return new DateValue(date.year, date.month, date.day);
        }

        public LocalDate toLocalDate() {
            return LocalDate.of(year, month, day);
        }
    }

    @ValueType
    public static class TimeValue {
        public final int hour;
        public final int minute;
        public final int second;
        public final int microsecond;
        public final Object tzInfo;
        public final ZoneId zoneId;
        public final int fold;

        public TimeValue(int hour, int minute, int second, int microsecond, Object tzInfo, ZoneId zoneId, int fold) {
            this.hour = hour;
            this.minute = minute;
            this.second = second;
            this.microsecond = microsecond;
            this.tzInfo = tzInfo;
            this.zoneId = zoneId;
            this.fold = fold;
        }

        public static TimeValue of(PTime time) {
            return new TimeValue(time.hour, time.minute, time.second, time.microsecond, time.tzInfo, null, time.fold);
        }

        public LocalTime toLocalTime() {
            return LocalTime.of(hour, minute, second, microsecond * 1_000);
        }

        public boolean hasTimeZone() {
            return tzInfo != null || zoneId != null;
        }
    }

    @ValueType
    public static final class DateTimeValue extends TimeValue {
        public final int year;
        public final int month;
        public final int day;

        public DateTimeValue(int year, int month, int day, int hour, int minute, int second, int microsecond, Object tzInfo, ZoneId zoneId, int fold) {
            super(hour, minute, second, microsecond, tzInfo, zoneId, fold);
            this.year = year;
            this.month = month;
            this.day = day;
        }

        public static DateTimeValue of(PDateTime dateTime) {
            return new DateTimeValue(dateTime.year, dateTime.month, dateTime.day, dateTime.hour, dateTime.minute, dateTime.second, dateTime.microsecond, dateTime.tzInfo, null,
                            dateTime.fold);
        }

        public DateValue getDateValue() {
            return new DateValue(year, month, day);
        }

        public LocalDateTime toLocalDateTime() {
            return LocalDateTime.of(year, month, day, hour, minute, second, microsecond * 1_000);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class ReadDateValueNode extends Node {
        public abstract DateValue execute(Node inliningTarget, Object obj);

        @Specialization
        static DateValue doManaged(PDate value) {
            return DateValue.of(value);
        }

        @Specialization(guards = "checkNode.execute(inliningTarget, value)", limit = "1")
        static DateValue doNative(@SuppressWarnings("unused") Node inliningTarget, PythonAbstractNativeObject value,
                        @SuppressWarnings("unused") @Cached DateNodes.DateCheckNode checkNode,
                        @Cached CStructAccess.ReadByteNode readNode) {
            return new DateValue(DateNodes.AsManagedDateNode.getYear(value, readNode), DateNodes.AsManagedDateNode.getMonth(value, readNode), DateNodes.AsManagedDateNode.getDay(value, readNode));
        }

        @Specialization(guards = {"isForeignObjectNode.execute(inliningTarget, value)", "interop.isDate(value)"}, limit = "1")
        static DateValue doForeign(Node inliningTarget, Object value,
                        @SuppressWarnings("unused") @Cached IsForeignObjectNode isForeignObjectNode,
                        @CachedLibrary("value") InteropLibrary interop) {
            try {
                LocalDate date = interop.asDate(value);
                return new DateValue(date.getYear(), date.getMonthValue(), date.getDayOfMonth());
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        @Fallback
        static DateValue error(Object obj,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.S_EXPECTED_GOT_P, "date", obj);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class ReadTimeValueNode extends Node {
        public abstract TimeValue execute(Node inliningTarget, Object obj);

        @Specialization
        static TimeValue doManaged(PTime value) {
            return TimeValue.of(value);
        }

        @Specialization(guards = "checkNode.execute(inliningTarget, value)", limit = "1")
        static TimeValue doNative(@SuppressWarnings("unused") Node inliningTarget, PythonAbstractNativeObject value,
                        @SuppressWarnings("unused") @Cached TimeNodes.TimeCheckNode checkNode,
                        @Cached CStructAccess.ReadByteNode readByteNode,
                        @Cached CStructAccess.ReadObjectNode readObjectNode) {
            return new TimeValue(TimeNodes.AsManagedTimeNode.getHour(value, readByteNode), TimeNodes.AsManagedTimeNode.getMinute(value, readByteNode),
                            TimeNodes.AsManagedTimeNode.getSecond(value, readByteNode), TimeNodes.AsManagedTimeNode.getMicrosecond(value, readByteNode),
                            TimeNodes.AsManagedTimeNode.getTzInfo(value, readByteNode, readObjectNode), null, TimeNodes.AsManagedTimeNode.getFold(value, readByteNode));
        }

        @Specialization(guards = {"isForeignObjectNode.execute(inliningTarget, value)", "interop.isTime(value)"}, limit = "1")
        static TimeValue doForeign(Node inliningTarget, Object value,
                        @SuppressWarnings("unused") @Cached IsForeignObjectNode isForeignObjectNode,
                        @CachedLibrary("value") InteropLibrary interop) {
            try {
                LocalTime time = interop.asTime(value);
                ZoneId zoneId = interop.isTimeZone(value) ? interop.asTimeZone(value) : null;
                return new TimeValue(time.getHour(), time.getMinute(), time.getSecond(), time.getNano() / 1_000, null, zoneId, 0);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        @Fallback
        static TimeValue error(Object obj,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.S_EXPECTED_GOT_P, "time", obj);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class ReadDateTimeValueNode extends Node {
        public abstract DateTimeValue execute(Node inliningTarget, Object obj);

        @Specialization
        static DateTimeValue doManaged(PDateTime value) {
            return DateTimeValue.of(value);
        }

        @Specialization(guards = "checkNode.execute(inliningTarget, value)", limit = "1")
        static DateTimeValue doNative(@SuppressWarnings("unused") Node inliningTarget, PythonAbstractNativeObject value,
                        @SuppressWarnings("unused") @Cached DateTimeNodes.DateTimeCheckNode checkNode,
                        @Cached CStructAccess.ReadByteNode readByteNode,
                        @Cached CStructAccess.ReadObjectNode readObjectNode) {
            return new DateTimeValue(DateTimeNodes.AsManagedDateTimeNode.getYear(value, readByteNode), DateTimeNodes.AsManagedDateTimeNode.getMonth(value, readByteNode),
                            DateTimeNodes.AsManagedDateTimeNode.getDay(value, readByteNode), DateTimeNodes.AsManagedDateTimeNode.getHour(value, readByteNode),
                            DateTimeNodes.AsManagedDateTimeNode.getMinute(value, readByteNode), DateTimeNodes.AsManagedDateTimeNode.getSecond(value, readByteNode),
                            DateTimeNodes.AsManagedDateTimeNode.getMicrosecond(value, readByteNode), DateTimeNodes.AsManagedDateTimeNode.getTzInfo(value, readByteNode, readObjectNode), null,
                            DateTimeNodes.AsManagedDateTimeNode.getFold(value, readByteNode));
        }

        @Specialization(guards = {"isForeignObjectNode.execute(inliningTarget, value)", "interop.isDate(value)", "interop.isTime(value)"}, limit = "1")
        static DateTimeValue doForeign(Node inliningTarget, Object value,
                        @SuppressWarnings("unused") @Cached IsForeignObjectNode isForeignObjectNode,
                        @CachedLibrary("value") InteropLibrary interop) {
            try {
                LocalDate date = interop.asDate(value);
                LocalTime time = interop.asTime(value);
                ZoneId zoneId = interop.isTimeZone(value) ? interop.asTimeZone(value) : null;
                return new DateTimeValue(date.getYear(), date.getMonthValue(), date.getDayOfMonth(), time.getHour(), time.getMinute(), time.getSecond(), time.getNano() / 1_000, null, zoneId,
                                0);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        @Fallback
        static DateTimeValue error(Object obj,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.S_EXPECTED_GOT_P, "datetime", obj);
        }
    }
}
