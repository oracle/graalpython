/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

@CoreFunctions(defineModule = "time")
public final class TimeModuleBuiltins extends PythonBuiltins {
    private static final int DELAY_NANOS = 10;

    @Override
    protected List<com.oracle.truffle.api.dsl.NodeFactory<? extends PythonBuiltinNode>> getNodeFactories() {
        return TimeModuleBuiltinsFactory.getFactories();
    }

    @TruffleBoundary
    public static double timeSeconds() {
        return System.currentTimeMillis() / 1000.0;
    }

    @TruffleBoundary
    private static Object[] getTimeStruct(double seconds, boolean local) {
        Object[] timeStruct = new Object[9];
        Instant instant = Instant.ofEpochSecond((long) seconds);

        ZoneId zone = (local) ? ZoneId.systemDefault() : ZoneId.of("GMT");
        ZonedDateTime zonedDateTime = LocalDateTime.ofInstant(instant, zone).atZone(zone);
        timeStruct[0] = zonedDateTime.getYear();
        timeStruct[1] = zonedDateTime.getMonth().getValue();
        timeStruct[2] = zonedDateTime.getDayOfMonth();
        timeStruct[3] = zonedDateTime.getHour();
        timeStruct[4] = zonedDateTime.getMinute();
        timeStruct[5] = zonedDateTime.getSecond();
        timeStruct[6] = zonedDateTime.getDayOfWeek().getValue();
        timeStruct[7] = zonedDateTime.getDayOfYear();
        timeStruct[8] = (zonedDateTime.getZone().getRules().isDaylightSavings(instant)) ? 1 : 0;

        return timeStruct;
    }

    // time.gmtime([seconds])
    @Builtin(name = "gmtime", fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class PythonGMTimeNode extends PythonBuiltinNode {
        @Specialization
        public PTuple gmtime(double seconds) {
            return factory().createTuple(getTimeStruct(seconds, false));
        }
    }

    // time.localtime([seconds])
    @Builtin(name = "localtime", fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class PythonLocalTimeNode extends PythonBuiltinNode {
        @Specialization
        public PTuple localtime(double seconds) {
            return factory().createTuple(getTimeStruct(seconds, true));
        }
    }

    // time.time()
    @Builtin(name = "time", fixedNumOfArguments = 0)
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

    // time.monotonic()
    @Builtin(name = "monotonic", fixedNumOfArguments = 0)
    @GenerateNodeFactory
    public abstract static class PythonMonotonicNode extends PythonBuiltinNode {

        @Specialization
        @TruffleBoundary
        public double time() {
            return System.nanoTime();
        }
    }

    // time.clock()
    @Builtin(name = "clock", fixedNumOfArguments = 0)
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

    @Builtin(name = "sleep", fixedNumOfArguments = 1)
    @GenerateNodeFactory
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
                }
                secs = deadline - timeSeconds();
                if (secs < 0) {
                    break;
                }
            } while (true);

            return PNone.NONE;
        }
    }
}
