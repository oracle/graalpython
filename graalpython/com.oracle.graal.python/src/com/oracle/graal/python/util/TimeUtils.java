package com.oracle.graal.python.util;

import com.oracle.graal.python.runtime.PosixSupportLibrary.Timeval;

public abstract class TimeUtils {
    public static final long SEC_TO_MS = 1000L;
    public static final long MS_TO_US = 1000L;
    public static final long SEC_TO_US = SEC_TO_MS * MS_TO_US;
    public static final long US_TO_NS = 1000L;
    public static final long MS_TO_NS = MS_TO_US * US_TO_NS;
    public static final long SEC_TO_NS = SEC_TO_MS * MS_TO_NS;

    /**
     * Equivalent of CPython's {@code _PyTime_AsTimeval}
     */
    public static Timeval pyTimeAsTimeval(long t) {
        long secs = t / SEC_TO_NS;
        long ns = t % SEC_TO_NS;
        // Note: we cannot really have secs == Long.MIN_VALUE or Long.MAX_VALUE like it is possible
        // in CPython if the C types of 't' and 'secs' do not match
        long usec = pyTimeDivide(ns, US_TO_NS);
        if (usec < 0) {
            usec += SEC_TO_US;
            secs -= 1;
        } else if (usec >= SEC_TO_US) {
            usec -= SEC_TO_US;
            secs += 1;
        }
        assert 0 <= usec && usec < SEC_TO_US;
        return new Timeval(secs, usec);
    }

    /**
     * Equivalent of CPython's {@code _PyTime_divide}.
     */
    public static long pyTimeDivide(long t, long k) {
        // _PyTime_Divide, for now hard-coded mode HALF_UP
        assert k > 1;
        if (t >= 0) {
            return (t + k - 1) / k;
        } else {
            return (t - (k - 1)) / k;
        }
    }

    /**
     * Equivalent of CPython's {@code _PyTime_AsSecondsDouble}.
     */
    public static double pyTimeAsSecondsDouble(long t) {
        double d;
        if (t % SEC_TO_NS == 0) {
            /*
             * Divide using integers to avoid rounding issues on the integer part. 1e-9 cannot be
             * stored exactly in IEEE 64-bit.
             */
            long secs = t / SEC_TO_NS;
            d = secs;
        } else {
            d = t;
            d /= 1e9;
        }
        return d;
    }
}
