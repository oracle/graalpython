/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.builtins.modules;

import java.text.DateFormatSymbols;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes.GetObjectArrayNode;
import com.oracle.graal.python.builtins.objects.common.SequenceNodesFactory.GetObjectArrayNodeGen;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PArguments.ThreadState;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CastToIntegerFromIntNode;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(defineModule = "time")
public final class TimeModuleBuiltins extends PythonBuiltins {
    private static final int DELAY_NANOS = 10;
    private static final long PERF_COUNTER_START = TruffleOptions.AOT ? 0 : System.nanoTime();

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return TimeModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(PythonCore core) {
        super.initialize(core);
        TimeZone defaultTimeZone = TimeZone.getTimeZone(core.getContext().getEnv().getTimeZone());
        String noDaylightSavingZone = defaultTimeZone.getDisplayName(false, TimeZone.SHORT);
        String daylightSavingZone = defaultTimeZone.getDisplayName(true, TimeZone.SHORT);

        boolean hasDaylightSaving = !noDaylightSavingZone.equals(daylightSavingZone);
        if (hasDaylightSaving) {
            builtinConstants.put("tzname", core.factory().createTuple(new Object[]{noDaylightSavingZone, daylightSavingZone}));
        } else {
            builtinConstants.put("tzname", core.factory().createTuple(new Object[]{noDaylightSavingZone}));
        }

        builtinConstants.put("daylight", PInt.intValue(hasDaylightSaving));
    }

    @TruffleBoundary
    public static double timeSeconds() {
        return System.currentTimeMillis() / 1000.0;
    }

    @TruffleBoundary
    private static Object[] getTimeStruct(double seconds, boolean local) {
        Object[] timeStruct = new Object[11];
        Instant instant = Instant.ofEpochSecond((long) seconds);
        ZoneId zone = (local) ? PythonLanguage.getContext().getEnv().getTimeZone() : ZoneId.of("GMT");
        ZonedDateTime zonedDateTime = LocalDateTime.ofInstant(instant, zone).atZone(zone);
        timeStruct[0] = zonedDateTime.getYear();
        timeStruct[1] = zonedDateTime.getMonth().getValue();
        timeStruct[2] = zonedDateTime.getDayOfMonth();
        timeStruct[3] = zonedDateTime.getHour();
        timeStruct[4] = zonedDateTime.getMinute();
        timeStruct[5] = zonedDateTime.getSecond();
        timeStruct[6] = zonedDateTime.getDayOfWeek().getValue() - 1;
        timeStruct[7] = zonedDateTime.getDayOfYear();
        timeStruct[8] = (zonedDateTime.getZone().getRules().isDaylightSavings(instant)) ? 1 : 0;
        timeStruct[9] = zone.getId();
        timeStruct[10] = zonedDateTime.getOffset().getTotalSeconds();

        return timeStruct;
    }

    // time.gmtime([seconds])
    @Builtin(name = "__truffle_gmtime_tuple__", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class PythonGMTimeNode extends PythonBuiltinNode {

        @Specialization
        public PTuple gmtime(@SuppressWarnings("unused") PNone seconds) {
            return factory().createTuple(getTimeStruct(timeSeconds(), false));
        }

        @Specialization
        public PTuple gmtime(long seconds) {
            return factory().createTuple(getTimeStruct(seconds, false));
        }

        @Specialization
        public PTuple gmtime(double seconds) {
            return factory().createTuple(getTimeStruct(seconds, false));
        }
    }

    // time.localtime([seconds])
    @Builtin(name = "__truffle_localtime_tuple__", minNumOfPositionalArgs = 0, maxNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class PythonLocalTimeNode extends PythonBuiltinNode {

        @Specialization
        public PTuple localtime(@SuppressWarnings("unused") PNone seconds) {
            return factory().createTuple(getTimeStruct(timeSeconds(), true));
        }

        @Specialization
        public PTuple localtime(long seconds) {
            return factory().createTuple(getTimeStruct(seconds, true));
        }

        @Specialization
        public PTuple localtime(double seconds) {
            return factory().createTuple(getTimeStruct(seconds, true));
        }
    }

    // time.time()
    @Builtin(name = "time", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class PythonTimeNode extends PythonBuiltinNode {

        /**
         * The logic is borrowed from Jython.
         *
         * @return current system millisecond time in second
         */

        @Specialization
        @TruffleBoundary
        public double time() {
            return timeSeconds();
        }
    }

    // time.time_ns()
    @Builtin(name = "time_ns", minNumOfPositionalArgs = 0, doc = "Similar to time() but returns time as an integer number of nanoseconds since the epoch.")
    @GenerateNodeFactory
    public abstract static class PythonTimeNsNode extends PythonBuiltinNode {

        /**
         * The maximum date, which are systems able to handle is 2262 04 11. This corresponds to the
         * 64 bit long.
         */
        @Specialization
        public long time() {
            return timeNanoSeconds();
        }

        @TruffleBoundary
        private static long timeNanoSeconds() {
            Instant now = Instant.now();
            // From java we are not able to obtain the nano seconds resolution. It depends on the
            // jdk
            // JKD 1.8 the resolution is usually miliseconds (for example 1576081173486000000)
            // From JDK 9 including JDK 11 the resolution is usually microseconds (for example
            // 1576082578022393000)
            // To obtain really nanosecond resulution we have to fake the nanoseconds
            return now.getEpochSecond() * 1000000000L + now.getNano();
        }
    }

    // time.monotonic()
    @Builtin(name = "monotonic", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class PythonMonotonicNode extends PythonBuiltinNode {

        @Specialization
        @TruffleBoundary
        public double time() {
            return System.nanoTime() / 1000000000D;
        }
    }

    // time.monotonic_ns()
    @Builtin(name = "monotonic_ns", minNumOfPositionalArgs = 0, doc = "Similar to monotonic(), but return time as nanoseconds.")
    @GenerateNodeFactory
    public abstract static class PythonMonotonicNsNode extends PythonBuiltinNode {

        @Specialization
        @TruffleBoundary
        public long time() {
            return System.nanoTime();
        }
    }

    @Builtin(name = "perf_counter", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class PythonPerfCounterNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        public double counter() {
            return (System.nanoTime() - PERF_COUNTER_START) / 1000_000_000.0;
        }
    }

    @Builtin(name = "perf_counter_ns", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class PythonPerfCounterNsNode extends PythonBuiltinNode {

        @Specialization
        @TruffleBoundary
        public long counter() {
            return System.nanoTime() - PERF_COUNTER_START;
        }
    }

    // TODO time.clock in 3.8 is removed in 3.5 is deprecated
    // time.clock()
    @Builtin(name = "clock", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class PythonClockNode extends PythonBuiltinNode {
        /**
         * Python's time.clock() returns a positive number, which {@link System#nanoTime()} does not
         * guarantee. Also, we cannot statically assign the first value of nanoTime in AOT, because
         * that would freeze a completely useless constant.
         */
        protected static final boolean isAOT = TruffleOptions.AOT;
        @CompilationFinal protected static long start = isAOT ? 0 : System.nanoTime();

        @Specialization(guards = {"isAOT", "start == 0"})
        @TruffleBoundary
        double firstClock() {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            start = System.nanoTime();
            return clock();
        }

        @Specialization(replaces = "firstClock")
        @TruffleBoundary
        double clock() {
            return (System.nanoTime() - start) / 1000_000_000.0;
        }
    }

    @Builtin(name = "sleep", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class SleepNode extends PythonBuiltinNode {
        // see: https://github.com/python/cpython/blob/master/Modules/timemodule.c#L1741

        @Specialization
        @TruffleBoundary
        Object sleep(long seconds) {
            long secs = seconds;

            long deadline = (long) timeSeconds() + secs;
            do {
                try {
                    Thread.sleep(seconds * 1000);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }

                secs = deadline - (long) timeSeconds();
                if (secs < 0) {
                    break;
                }
            } while (true);

            return PNone.NONE;
        }

        @Specialization
        @TruffleBoundary
        Object sleep(double seconds) {
            double secs = seconds;

            double deadline = timeSeconds() + secs;
            do {
                double milliseconds = secs * 1000;
                long millis = Math.round(Math.floor(milliseconds));
                int nanos = ((Long) Math.round((milliseconds - millis) * 1000)).intValue();
                nanos = (millis == 0 && nanos == 0) ? DELAY_NANOS : nanos;
                try {
                    Thread.sleep(millis, nanos);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                secs = deadline - timeSeconds();
                if (secs < 0) {
                    break;
                }
            } while (true);

            return PNone.NONE;
        }
    }

    // time.strftime(format[, t])
    @Builtin(name = "strftime", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class StrfTimeNode extends PythonBuiltinNode {
        private static final int IMPOSSIBLE = -2;
        @Child private CastToIntegerFromIntNode castIntNode;

        @CompilationFinal private ConditionProfile outOfRangeProfile;

        private CastToIntegerFromIntNode getCastIntNode() {
            if (castIntNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castIntNode = insert(CastToIntegerFromIntNode.create(val -> {
                    throw raise(PythonBuiltinClassType.TypeError, "an integer is required (got type %p)", val);
                }));
            }
            return castIntNode;
        }

        private ConditionProfile getOutOfRangeProfile() {
            if (outOfRangeProfile == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                outOfRangeProfile = ConditionProfile.createBinaryProfile();
            }
            return outOfRangeProfile;
        }

        private int getIntValue(Object oValue, int min, int max, String errorMessage) {
            Object iValue = getCastIntNode().execute(oValue);
            long value = IMPOSSIBLE;
            if (iValue instanceof Long) {
                value = (long) iValue;
            } else if (iValue instanceof Integer) {
                value = (int) iValue;
            } else if (iValue instanceof PInt) {
                value = ((PInt) iValue).longValueExact();
            }
            if (getOutOfRangeProfile().profile(min > value || value > max)) {
                throw raise(PythonBuiltinClassType.ValueError, errorMessage);
            }
            return (int) value;
        }

        private static String padInt(int i, int target, char addChar) {
            String s = Integer.toString(i);
            int sz = s.length();
            if (target <= sz) { // no truncation
                return s;
            }
            if (target == sz + 1) {
                return addChar + s;
            }
            if (target == sz + 2) {
                return new String(new char[]{addChar, addChar}) + s;
            } else {
                char[] c = new char[target - sz];
                Arrays.fill(c, addChar);
                return new String(c) + s;
            }
        }

        private static String twoDigit(int i) {
            return padInt(i, 2, '0');
        }

        public int[] checkStructtime(PTuple time) {
            CompilerAsserts.neverPartOfCompilation();
            Object[] date = GetObjectArrayNodeGen.getUncached().execute(time);
            if (date.length < 9) {
                throw raise(PythonBuiltinClassType.TypeError, "function takes at least 9 arguments (%d given)", date.length);
            }
            int year = getIntValue(getCastIntNode().execute(date[0]), Integer.MIN_VALUE, Integer.MAX_VALUE, "year");
            int mon = getIntValue(getCastIntNode().execute(date[1]), 0, 12, "month out of range");
            if (mon == 0) {
                mon = 1;
            }
            int day = getIntValue(getCastIntNode().execute(date[2]), 0, 31, "day of month out of range");
            if (day == 0) {
                day = 1;
            }
            int hour = getIntValue(getCastIntNode().execute(date[3]), 0, 23, "hour out of range");
            int min = getIntValue(getCastIntNode().execute(date[4]), 0, 59, "minute out of range");
            int sec = getIntValue(getCastIntNode().execute(date[5]), 0, 61, "seconds out of range");
            int wday = getIntValue(getCastIntNode().execute(date[6]), -1, Integer.MAX_VALUE,
                            "1 when daylight savings time is in effect, and 0 when it is not. A value of -1 indicates that this is not known");
            if (wday == -1) {
                wday = 6;
            } else if (wday > 6) {
                wday = wday % 7;
            }
            int yday = getIntValue(getCastIntNode().execute(date[7]), 0, 366, "day of year out of range");
            if (yday == 0) {
                yday = 1;
            }
            int isdst = getIntValue(getCastIntNode().execute(date[7]), Integer.MIN_VALUE, Integer.MAX_VALUE, "daylight savings time out of range");
            return new int[]{year, mon, day, hour, min, sec, wday, yday, isdst};
        }

        protected static DateFormatSymbols datesyms = new DateFormatSymbols();

        @TruffleBoundary
        private static String getDayShortName(int day) {
            String[] names = datesyms.getShortWeekdays();
            return names[day == 6 ? 1 : day + 2];
        }

        @TruffleBoundary
        private static String getDayLongName(int day) {
            String[] names = datesyms.getWeekdays();
            return names[day == 6 ? 1 : day + 2];
        }

        @TruffleBoundary
        private static String getMonthShortName(int month) {
            String[] names = datesyms.getShortMonths();
            return names[month == 0 ? 0 : month - 1];
        }

        @TruffleBoundary
        private static String getMonthLongName(int month) {
            String[] names = datesyms.getMonths();
            return names[month == 0 ? 0 : month - 1];
        }

        @TruffleBoundary
        private static String truncYear(int year) {
            String yearstr = padInt(year, 4, '0');
            return yearstr.substring(yearstr.length() - 2, yearstr.length());
        }

        public static String localeAsctime(int[] time) {
            int day = time[6];
            int mon = time[1];
            return getDayShortName(day) + " " + getMonthShortName(mon) + " " + padInt(time[2], 2, ' ') + " " + twoDigit(time[3]) + ":" + twoDigit(time[4]) + ":" + twoDigit(time[5]) + " " + time[0];
        }

        private static GregorianCalendar getCalendar(int[] time) {
            return new GregorianCalendar(time[0], time[1], time[2], time[3], time[4], time[5]);
        }

        // This taken from JPython + some switches were corrected to provide the
        // same result as CPython
        @TruffleBoundary
        private String format(String format, PTuple date) {

            int[] items = checkStructtime(date);

            String s = "";
            int lastc = 0;
            int j;
            String[] syms;
            GregorianCalendar cal = null;
            while (lastc < format.length()) {
                int i = format.indexOf("%", lastc);
                if (i < 0) {
                    // the end of the format string
                    s = s + format.substring(lastc);
                    break;
                }
                if (i == format.length() - 1) {
                    // there's a bare % at the end of the string. Python lets
                    // this go by just sticking a % at the end of the result
                    // string
                    s = s + "%";
                    break;
                }
                s = s + format.substring(lastc, i);
                i++;
                switch (format.charAt(i)) {
                    case 'a':
                        // abbrev weekday
                        j = items[6];
                        s = s + getDayShortName(j);
                        break;
                    case 'A':
                        // full weekday
                        j = items[6];
                        s = s + getDayLongName(j);
                        break;
                    case 'b':
                        // abbrev month
                        j = items[1];
                        s = s + getMonthShortName(j);
                        break;
                    case 'B':
                        // full month
                        j = items[1];
                        s = s + getMonthLongName(j);
                        break;
                    case 'c':
                        s = s + localeAsctime(items);
                        break;
                    case 'd':
                        // day of month (01-31)
                        s = s + twoDigit(items[2]);
                        break;
                    case 'H':
                        // hour (00-23)
                        s = s + twoDigit(items[3]);
                        break;
                    case 'I':
                        // hour (01-12)
                        j = items[3] % 12;
                        if (j == 0) {
                            j = 12;                  // midnight or noon
                        }
                        s = s + twoDigit(j);
                        break;
                    case 'j':
                        // day of year (001-366)
                        s = s + padInt(items[7], 3, '0');
                        break;
                    case 'm':
                        // month (01-12)
                        s = s + twoDigit(items[1]);
                        break;
                    case 'M':
                        // minute (00-59)
                        s = s + twoDigit(items[4]);
                        break;
                    case 'p':
                        // AM/PM
                        j = items[3];
                        syms = datesyms.getAmPmStrings();
                        if (0 <= j && j < 12) {
                            s = s + syms[0];
                        } else if (12 <= j && j < 24) {
                            s = s + syms[1];
                        }
                        break;
                    case 'S':
                        // seconds (00-61)
                        s = s + twoDigit(items[5]);
                        break;
                    case 'U':
                        // week of year (sunday is first day) (00-53). all days in
                        // new year preceding first sunday are considered to be in
                        // week 0

                        // TODO this is not correct, CPython counts the week of year
                        // from day of year item [8]
                        if (cal == null) {
                            cal = getCalendar(items);
                        }

                        cal.setFirstDayOfWeek(Calendar.SUNDAY);
                        cal.setMinimalDaysInFirstWeek(7);
                        j = cal.get(Calendar.WEEK_OF_YEAR);
                        if (cal.get(Calendar.MONTH) == Calendar.JANUARY && j >= 52) {
                            j = 0;
                        }
                        s = s + twoDigit(j);
                        break;
                    case 'w':
                        // weekday as decimal (0=Sunday-6)
                        j = (items[6] + 1) % 7;
                        s = s + j;
                        break;
                    case 'W':
                        // week of year (monday is first day) (00-53). all days in
                        // new year preceding first sunday are considered to be in
                        // week 0

                        // TODO this is not correct, CPython counts the week of year
                        // from day of year item [8]

                        if (cal == null) {
                            cal = getCalendar(items);
                        }
                        cal.setFirstDayOfWeek(Calendar.MONDAY);
                        cal.setMinimalDaysInFirstWeek(7);
                        j = cal.get(Calendar.WEEK_OF_YEAR);

                        if (cal.get(Calendar.MONTH) == Calendar.JANUARY && j >= 52) {
                            j = 0;
                        }
                        s = s + twoDigit(j);
                        break;
                    case 'x':
                        // TBD: A note about %x and %X. Python's time.strftime()
                        // by default uses the "C" locale, which is changed by
                        // using the setlocale() function. In Java, the default
                        // locale is set by user.language and user.region
                        // properties and is "en_US" by default, at least around
                        // here! Locale "en_US" differs from locale "C" in the way
                        // it represents dates and times. Eventually we might want
                        // to craft a "C" locale for Java and set Jython to use
                        // this by default, but that's too much work right now.
                        //
                        // For now, we hard code %x and %X to return values
                        // formatted in the "C" locale, i.e. the default way
                        // CPython does it. E.g.:
                        // %x == mm/dd/yy
                        // %X == HH:mm:SS
                        //
                        s = s + twoDigit(items[1] + 1) + "/" +
                                        twoDigit(items[2]) + "/" +
                                        truncYear(items[0]);
                        break;
                    case 'X':
                        // See comment for %x above
                        s = s + twoDigit(items[3]) + ":" +
                                        twoDigit(items[4]) + ":" +
                                        twoDigit(items[5]);
                        break;
                    case 'Y':
                        // year w/ century
                        s = s + items[0];
                        break;
                    case 'y':
                        // year w/o century (00-99)
                        s = s + truncYear(items[0]);
                        break;
                    case 'Z':
                        // timezone name
                        if (cal == null) {
                            cal = getCalendar(items);
                        }
                        // If items[8] == 1, we're in daylight savings time.
                        // -1 means the information was not available; treat this as if not in dst.
                        s = s + cal.getTimeZone().getDisplayName(items[8] > 0, 0);
                        break;
                    case '%':
                        // %
                        s = s + "%";
                        break;
                    default:
                        // TBD: should this raise a ValueError?
                        s = s + "%" + format.charAt(i);
                        i++;
                        break;
                }
                lastc = i + 1;
            }
            return s;
        }

        @Specialization
        public String formatTime(String format, @SuppressWarnings("unused") PNone time) {
            return format(format, factory().createTuple(getTimeStruct(timeSeconds(), true)));
        }

        @Specialization
        public String formatTime(String format, PTuple time) {
            return format(format, time);
        }

        @Specialization
        public String formatTime(@SuppressWarnings("unused") String format, @SuppressWarnings("unused") Object time) {
            throw raise(PythonBuiltinClassType.TypeError, "Tuple or struct_time argument required");
        }

        @Fallback
        public String formatTime(Object format, @SuppressWarnings("unused") Object time) {
            throw raise(PythonBuiltinClassType.TypeError, "strftime() argument 1 must be str, not %p", format);
        }
    }

    @Builtin(name = "mktime", minNumOfPositionalArgs = 1, doc = "mktime(tuple) -> floating point number\n\n" +
                    "Convert a time tuple in local time to seconds since the Epoch.\n" +
                    "Note that mktime(gmtime(0)) will not generally return zero for most\n" +
                    "time zones; instead the returned value will either be equal to that\n" +
                    "of the timezone or altzone attributes on the time module.")
    @GenerateNodeFactory
    abstract static class MkTimeNode extends PythonUnaryBuiltinNode {
        private static final int ELEMENT_COUNT = 9;

        @Specialization
        @ExplodeLoop
        double mktime(VirtualFrame frame, PTuple tuple,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @CachedLibrary(limit = "getCallSiteInlineCacheMaxDepth()") PythonObjectLibrary lib,
                        @Cached GetObjectArrayNode getObjectArrayNode) {
            Object[] items = getObjectArrayNode.execute(tuple);
            if (items.length != 9) {
                throw raise(PythonBuiltinClassType.TypeError, "function takes exactly 9 arguments (%d given)", items.length);
            }
            ThreadState threadState = null;
            if (hasFrame.profile(frame != null)) {
                threadState = PArguments.getThreadState(frame);
            }
            int[] integers = new int[9];
            for (int i = 0; i < ELEMENT_COUNT; i++) {
                if (hasFrame.profile(frame != null)) {
                    integers[i] = lib.asSizeWithState(items[i], threadState);
                } else {
                    integers[i] = lib.asSize(items[i]);
                }
            }
            return op(integers);
        }

        @TruffleBoundary
        private static long op(int[] integers) {
            LocalDateTime localtime = LocalDateTime.of(integers[0], integers[1], integers[2], integers[3], integers[4], integers[5]);
            ZoneId timeZone = PythonLanguage.getContext().getEnv().getTimeZone();
            return localtime.toEpochSecond(timeZone.getRules().getOffset(localtime));
        }
    }
}
