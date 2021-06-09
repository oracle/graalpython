/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
