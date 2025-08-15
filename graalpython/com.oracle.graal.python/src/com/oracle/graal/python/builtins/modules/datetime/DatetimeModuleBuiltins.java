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

import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.tuple.StructSequence;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectReprAsObjectNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.nodes.BuiltinNames.T_TIMEZONE;
import static com.oracle.graal.python.nodes.BuiltinNames.T_UTC;
import static com.oracle.graal.python.nodes.BuiltinNames.T__DATETIME;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

@CoreFunctions(defineModule = "_datetime")
public class DatetimeModuleBuiltins extends PythonBuiltins {

    static final StructSequence.BuiltinTypeDescriptor ISO_CALENDAR_DATE = new StructSequence.BuiltinTypeDescriptor(
                    PythonBuiltinClassType.PIsoCalendarDate,
                    3,
                    new String[]{
                                    "year", "week", "weekday"
                    },
                    null);

    static final int MIN_YEAR = 1;
    static final int MAX_YEAR = 9999;

    public static final TruffleString T_UTCOFFSET = tsLiteral("utcoffset");
    public static final TruffleString T_DST = tsLiteral("dst");

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return new ArrayList<>();
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);

        addBuiltinConstant("MINYEAR", MIN_YEAR);
        addBuiltinConstant("MAXYEAR", MAX_YEAR);

        StructSequence.initType(core, ISO_CALENDAR_DATE);
    }

    @Override
    public void postInitialize(Python3Core core) {
        super.postInitialize(core);

        PythonModule self = core.lookupBuiltinModule(T__DATETIME);
        PythonBuiltinClass timeZoneClass = (PythonBuiltinClass) self.getAttribute(T_TIMEZONE);
        Object utcObject = timeZoneClass.getAttribute(TimeZoneBuiltins.T_UTC_ATTRIBUTE);

        // if by some reason datetime.timezone.utc attribute isn't set yet
        if (!(utcObject instanceof PTimeZone) && !(utcObject == null)) {
            throw CompilerDirectives.shouldNotReachHere();
        }
        PTimeZone utc = (PTimeZone) utcObject;

        self.setAttribute(T_UTC, utc);

        ModuleData moduleDate = new ModuleData();
        moduleDate.utc = utc;
        self.setModuleState(moduleDate);
    }

    // CPython: call_tzinfo_method()
    @TruffleBoundary
    static void validateUtcOffset(PTimeDelta offset, Node inliningTarget) {
        if (offset.days < -1 || (offset.days == -1 && offset.seconds == 0 && offset.microseconds == 0) || offset.days >= 1) {
            // use timedelta.__repr__()
            Object offsetReprObject = PyObjectReprAsObjectNode.executeUncached(offset);
            String offsetRepr = CastToJavaStringNode.getUncached().execute(offsetReprObject);

            throw PRaiseNode.raiseStatic(inliningTarget,
                            ValueError,
                            ErrorMessages.OFFSET_MUST_BE_A_TIMEDELTA_STRICTLY_BETWEEN_NOT_S,
                            offsetRepr);
        }
    }

    @TruffleBoundary
    public static void validateIsoCalendarComponentsAndRaise(Node inliningTarget, long year, long week, long dayOfWeek) {
        if (year < MIN_YEAR || year > MAX_YEAR) {
            throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.YEAR_IS_OUT_OF_RANGE_D, year);
        }

        if (week <= 0 || week >= 53) {
            if (week != 53) {
                throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.INVALID_WEEK_D, week);
            } else {
                // ISO years have 53 weeks in it on years starting with a Thursday
                // and on leap years starting on Wednesday
                var yearStartDate = LocalDate.of((int) year, 1, 1);
                var startsOnThursday = yearStartDate.getDayOfWeek() == DayOfWeek.THURSDAY;
                var startsOnWednesdayAndLeapYear = yearStartDate.getDayOfWeek() == DayOfWeek.WEDNESDAY && yearStartDate.isLeapYear();

                if (!startsOnThursday && !startsOnWednesdayAndLeapYear) {
                    throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.INVALID_WEEK_D, week);
                }
            }
        }

        if (dayOfWeek <= 0 || dayOfWeek >= 8) {
            throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.INVALID_DAY_D_RANGE_IS, dayOfWeek);
        }
    }

    @TruffleBoundary
    public static boolean validateIsoCalendarComponents(int year, int week, int dayOfWeek) {
        if (year < MIN_YEAR || year > MAX_YEAR) {
            return false;
        }

        if (week <= 0 || week >= 53) {
            if (week != 53) {
                return false;
            } else {
                // ISO years have 53 weeks in it on years starting with a Thursday
                // and on leap years starting on Wednesday
                var yearStartDate = LocalDate.of(year, 1, 1);
                var startsOnThursday = yearStartDate.getDayOfWeek() == DayOfWeek.THURSDAY;
                var startsOnWednesdayAndLeapYear = yearStartDate.getDayOfWeek() == DayOfWeek.WEDNESDAY && yearStartDate.isLeapYear();

                if (!startsOnThursday && !startsOnWednesdayAndLeapYear) {
                    return false;
                }
            }
        }

        if (dayOfWeek <= 0 || dayOfWeek >= 8) {
            return false;
        }

        return true;
    }

    // CPython: format_utcoffset()
    @TruffleBoundary
    static String formatUtcOffset(Object tzInfo, Object dateTime, boolean useSeparator, Node inliningTarget) {
        PTimeDelta utcOffset = callUtcOffset(tzInfo, dateTime, inliningTarget);

        if (utcOffset == null) {
            return "";
        }

        long microsecondsTotal = utcOffsetToMicroseconds(utcOffset);
        int sign = Long.signum(microsecondsTotal);
        microsecondsTotal = Math.abs(microsecondsTotal);
        long hours = microsecondsTotal / 1_000_000 / 3_600;
        long minutes = microsecondsTotal / 1_000_000 / 60 % 60;
        long seconds = microsecondsTotal / 1_000_000 % 60;
        long microseconds = microsecondsTotal % 1_000_000;

        var builder = new StringBuilder();

        builder.append((sign >= 0) ? '+' : '-');

        if (useSeparator) {
            builder.append(String.format("%02d:%02d", hours, minutes));
        } else {
            builder.append(String.format("%02d%02d", hours, minutes));
        }

        if (seconds != 0 || microseconds != 0) {
            if (useSeparator) {
                builder.append(String.format(":%02d", seconds));
            } else {
                builder.append(String.format("%02d", seconds));
            }

            if (microseconds != 0) {
                builder.append(String.format(".%06d", microseconds));
            }
        }

        return builder.toString();
    }

    // CPython: call_tzinfo_method()
    public static PTimeDelta callUtcOffset(Object tzInfo, Object dateTime, Node inliningTarget) {
        if (tzInfo == null) {
            return null;
        }

        Object offsetObject = PyObjectCallMethodObjArgs.executeUncached(tzInfo, T_UTCOFFSET, dateTime);
        if (offsetObject instanceof PNone) {
            return null;
        }
        if (!(offsetObject instanceof PTimeDelta offset)) {
            throw PRaiseNode.raiseStatic(inliningTarget,
                            TypeError,
                            ErrorMessages.S_MUST_RETURN_NONE_OR_TIMEDELTA_NOT_P,
                            "tzinfo.utcoffset()",
                            offsetObject);
        }

        DatetimeModuleBuiltins.validateUtcOffset(offset, inliningTarget);
        return offset;
    }

    // CPython: call_tzinfo_method()
    public static PTimeDelta callUtcOffset(Object tzInfo, Object dateTime, VirtualFrame frame, Node inliningTarget, PyObjectCallMethodObjArgs callMethodObjArgs, PRaiseNode raiseNode) {
        if (tzInfo == null) {
            return null;
        }

        Object offsetObject = callMethodObjArgs.execute(frame, inliningTarget, tzInfo, T_UTCOFFSET, dateTime);
        if (offsetObject instanceof PNone) {
            return null;
        }
        if (!(offsetObject instanceof PTimeDelta offset)) {
            throw raiseNode.raise(inliningTarget,
                            TypeError,
                            ErrorMessages.S_MUST_RETURN_NONE_OR_TIMEDELTA_NOT_P,
                            "tzinfo.utcoffset()",
                            offsetObject);
        }

        DatetimeModuleBuiltins.validateUtcOffset(offset, inliningTarget);
        return offset;
    }

    // CPython: call_tzinfo_method()
    public static PTimeDelta callDst(Object tzInfo, Object dateTime, Node inliningTarget) {
        if (tzInfo == null) {
            return null;
        }

        Object dst = PyObjectCallMethodObjArgs.executeUncached(tzInfo, T_DST, dateTime);
        if (dst instanceof PNone) {
            return null;
        }
        if (!(dst instanceof PTimeDelta offset)) {
            throw PRaiseNode.raiseStatic(inliningTarget,
                            TypeError,
                            ErrorMessages.S_MUST_RETURN_NONE_OR_TIMEDELTA_NOT_P,
                            "tzinfo.dst()",
                            dst);
        }

        DatetimeModuleBuiltins.validateUtcOffset(offset, inliningTarget);
        return offset;
    }

    // CPython: call_tzinfo_method()
    public static PTimeDelta callDst(Object tzInfo, Object dateTime, VirtualFrame frame, Node inliningTarget, PyObjectCallMethodObjArgs callMethodObjArgs, PRaiseNode raiseNode) {
        if (tzInfo == null) {
            return null;
        }

        Object dst = callMethodObjArgs.execute(frame, inliningTarget, tzInfo, T_DST, dateTime);
        if (dst instanceof PNone) {
            return null;
        }
        if (!(dst instanceof PTimeDelta offset)) {
            throw raiseNode.raise(inliningTarget,
                            TypeError,
                            ErrorMessages.S_MUST_RETURN_NONE_OR_TIMEDELTA_NOT_P,
                            "tzinfo.dst()",
                            dst);
        }

        DatetimeModuleBuiltins.validateUtcOffset(offset, inliningTarget);
        return offset;
    }

    @TruffleBoundary
    public static Object addOffsetToDateTime(PDateTime dateTime, PTimeDelta offset, DateTimeNodes.SubclassNewNode subclassNewNode, Node inliningTarget) {
        LocalDateTime utc = LocalDateTime.of(dateTime.year, dateTime.month, dateTime.day, dateTime.hour, dateTime.minute, dateTime.second, dateTime.microsecond * 1_000).plusDays(
                        offset.days).plusSeconds(offset.seconds).plusNanos(offset.microseconds * 1_000L);

        return subclassNewNode.execute(inliningTarget,
                        dateTime.getPythonClass(),
                        utc.getYear(),
                        utc.getMonthValue(),
                        utc.getDayOfMonth(),
                        utc.getHour(),
                        utc.getMinute(),
                        utc.getSecond(),
                        utc.getNano() / 1_000,
                        dateTime.tzInfo,
                        0);
    }

    public static long utcOffsetToMicroseconds(PTimeDelta utcOffset) {
        return (long) utcOffset.days * 24 * 3600 * 1_000_000 +
                        (long) utcOffset.seconds * 1_000_000 +
                        (long) utcOffset.microseconds;
    }

    public static PTimeZone getUtcTimeZone(PythonContext context) {
        PythonModule self = context.lookupBuiltinModule(T__DATETIME);
        ModuleData moduleData = self.getModuleState(ModuleData.class);
        return moduleData.utc;
    }

    private static final class ModuleData {
        PTimeZone utc;
    }
}
