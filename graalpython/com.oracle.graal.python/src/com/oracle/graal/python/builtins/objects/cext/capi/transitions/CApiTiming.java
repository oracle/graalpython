/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.capi.transitions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

import org.graalvm.nativeimage.ImageInfo;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public final class CApiTiming {

    /**
     * Set this property to a positive reporting interval in milliseconds to enable timing of C API
     * calls (upcalls and downcalls). For a native standalone, set it when building the image; the
     * default value allows the instrumentation to be folded away.
     */
    private static final int PROFILE_CALL_INTERVAL = Integer.getInteger("python.CAPITiming", 0);

    private static final int INITIAL_STACK = 100;
    /**
     * Include results until at least this fraction of total time is included.
     */
    private static final double CUTOFF_TIME = 0.95;
    /**
     * Include results until at least this fraction of total calls is included.
     */
    private static final double CUTOFF_COUNT = 0.95;

    private static final class TimingStack {
        long[] subTimes = new long[INITIAL_STACK + 1];
        long[] startTimes = new long[INITIAL_STACK];
        int sp;
    }

    private static final ThreadLocal<TimingStack> STACK = ThreadLocal.withInitial(TimingStack::new);
    private static final ArrayList<CApiTiming> TIMINGS = new ArrayList<>();
    private static volatile boolean reporterStarted;

    private final String name;
    private final boolean fromJava;
    private final AtomicLong time = new AtomicLong();
    private final AtomicLong count = new AtomicLong();

    private CApiTiming(boolean fromJava, String name) {
        this.fromJava = fromJava;
        this.name = name;
        synchronized (TIMINGS) {
            TIMINGS.add(this);
        }
    }

    public static CApiTiming create(boolean fromJava, Object delegate) {
        return PROFILE_CALL_INTERVAL == 0 ? null : new CApiTiming(fromJava, delegate + (fromJava ? " J->N" : " N->J"));
    }

    private static void ensureReporterStarted() {
        if (reporterStarted) {
            return;
        }
        synchronized (TIMINGS) {
            if (reporterStarted) {
                return;
            }
            // This class and its timing entries may be initialized into the image heap. Install
            // runtime threads lazily, rather than skipping their installation at image build time.
            Thread thread = new Thread(() -> {
                while (true) {
                    try {
                        Thread.sleep(PROFILE_CALL_INTERVAL);
                    } catch (InterruptedException e) {
                        // continue
                    }
                    dumpCallStatistics();
                }
            }, "C API timing reporter");
            thread.setDaemon(true);
            Runtime.getRuntime().addShutdownHook(new Thread(CApiTiming::dumpCallStatistics, "C API timing shutdown"));
            thread.start();
            reporterStarted = true;
        }
    }

    @TruffleBoundary
    private static synchronized void dumpCallStatistics() {
        ArrayList<CApiTiming> sorted;
        synchronized (TIMINGS) {
            sorted = new ArrayList<>(TIMINGS);
        }
        sorted.sort((a, b) -> Boolean.compare(a.fromJava, b.fromJava) * 100 + a.name.compareTo(b.name));
        System.out.println("======================================================================");
        System.out.printf("%70s  %8s %10s\n", "Name:", "Count:", "Time:");
        long[] counts = new long[sorted.size()];
        long[] times = new long[sorted.size()];
        long totalCount = 0;
        long totalTime = 0;
        for (int i = 0; i < sorted.size(); i++) {
            CApiTiming entry = sorted.get(i);
            totalCount += counts[i] = entry.count.get();
            totalTime += times[i] = entry.time.get();
        }
        long cutoffTime = getCutoff(totalTime, times, CUTOFF_TIME);
        long cutoffCount = getCutoff(totalCount, counts, CUTOFF_COUNT);
        long percent = totalTime / 100;
        long visibleCount = 0;
        long visibleTime = 0;
        for (int i = 0; i < sorted.size(); i++) {
            if (times[i] >= cutoffTime || counts[i] >= cutoffCount) {
                System.out.printf("%70s  %8s %8sms %s\n", sorted.get(i).name, counts[i], times[i] / 1000000, stars(percent, times[i]));
                visibleCount += counts[i];
                visibleTime += times[i];
            }
        }
        System.out.printf("%70s  %8s %8sms %s\n", "Others:", (totalCount - visibleCount), (totalTime - visibleTime) / 1000000, stars(percent, totalTime - visibleTime));
        System.out.println("----------------------------------------------------------------------");
        System.out.printf("%70s  %8s %8sms\n", "Total:", totalCount, totalTime / 1000000);
        System.out.printf("%70s  %8s %8sms)\n", "(cutoff:", cutoffCount, cutoffTime / 1000000);
        System.out.println();
    }

    private static long getCutoff(long total, long[] values, double fraction) {
        long[] sorted = Arrays.copyOf(values, values.length);
        Arrays.sort(sorted);
        long accountedTime = 0;
        long cutoffTime = 0;
        for (int i = sorted.length - 1; i >= 0; i--) {
            if (accountedTime <= total * fraction) {
                cutoffTime = sorted[i];
                accountedTime += cutoffTime;
            }
        }
        return cutoffTime;
    }

    private static String stars(long percent, long time) {
        if (percent <= 0 || percent > 100) {
            return "";
        }
        String STARS = "****************************************************************************************************";
        int value = (int) ((time + percent / 2) / percent);
        return String.format("%2d", value) + "% " + STARS.substring(0, value);
    }

    public static void enter() {
        if (PROFILE_CALL_INTERVAL != 0 && !ImageInfo.inImageBuildtimeCode()) {
            enterInternal();
        }
    }

    public static void exit(CApiTiming t) {
        if (PROFILE_CALL_INTERVAL != 0 && !ImageInfo.inImageBuildtimeCode()) {
            exitInternal(t);
        }
    }

    @TruffleBoundary
    private static void enterInternal() {
        ensureReporterStarted();
        TimingStack stack = STACK.get();
        if (stack.sp >= stack.startTimes.length) {
            // grow stack if necessary
            int newSize = stack.startTimes.length * 2;
            stack.subTimes = Arrays.copyOf(stack.subTimes, newSize);
            stack.startTimes = Arrays.copyOf(stack.startTimes, newSize);
        }
        stack.subTimes[stack.sp] = 0;
        stack.startTimes[stack.sp++] = System.nanoTime();
    }

    @TruffleBoundary
    private static void exitInternal(CApiTiming t) {
        TimingStack stack = STACK.get();
        long startTime = stack.startTimes[--stack.sp];
        long delta = System.nanoTime() - startTime;
        t.time.addAndGet(delta - stack.subTimes[stack.sp]);
        if (stack.sp > 0) {
            stack.subTimes[stack.sp - 1] += delta;
        }
        t.count.getAndIncrement();
    }
}
