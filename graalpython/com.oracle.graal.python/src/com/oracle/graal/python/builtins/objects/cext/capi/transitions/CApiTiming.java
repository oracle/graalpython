/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static java.util.stream.Collectors.summingLong;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import org.graalvm.nativeimage.ImageInfo;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public final class CApiTiming {

    /**
     * Set this property to non-zero to enable timing of C API calls (upcalls and downcalls).
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

    static {
        if (PROFILE_CALL_INTERVAL != 0 && !ImageInfo.inImageBuildtimeCode()) {
            Thread thread = new Thread() {
                @Override
                public void run() {
                    while (true) {
                        try {
                            Thread.sleep(PROFILE_CALL_INTERVAL);
                        } catch (InterruptedException e) {
                            // continue
                        }
                        synchronized (TIMINGS) {
                            dumpCallStatistics();
                        }
                    }
                }

            };
            thread.setDaemon(true);
            thread.start();
            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    dumpCallStatistics();
                }
            });
        }
    }

    private static void dumpCallStatistics() {
        ArrayList<CApiTiming> sorted = new ArrayList<>(TIMINGS);
        sorted.sort((a, b) -> Boolean.compare(a.fromJava, b.fromJava) * 100 + a.name.compareTo(b.name));
        System.out.println("======================================================================");
        System.out.printf("%70s  %8s %10s\n", "Name:", "Count:", "Time:");
        long totalCount = sorted.stream().collect(summingLong(e -> e.count.get()));
        long totalTime = sorted.stream().collect(summingLong(e -> e.time.get()));
        long cutoffTime = getCutoff(totalTime, sorted.stream().map(e -> e.time.get()));
        long cutoffCount = getCutoff(totalCount, sorted.stream().map(e -> e.count.get()));
        long percent = totalTime / 100;
        long visibleCount = 0;
        long visibleTime = 0;
        for (var e : sorted) {
            if (e.time.get() >= cutoffTime || e.count.get() >= cutoffCount) {
                System.out.printf("%70s  %8s %8sms %s\n", e.name, e.count, e.time.get() / 1000000, stars(percent, e.time.get()));
                visibleCount += e.count.get();
                visibleTime += e.time.get();
            }
        }
        System.out.printf("%70s  %8s %8sms %s\n", "Others:", (totalCount - visibleCount), (totalTime - visibleTime) / 1000000, stars(percent, totalTime - visibleTime));
        System.out.println("----------------------------------------------------------------------");
        System.out.printf("%70s  %8s %8sms\n", "Total:", totalCount, totalTime / 1000000);
        System.out.printf("%70s  %8s %8sms)\n", "(cutoff:", cutoffCount, cutoffTime / 1000000);
        System.out.println();
    }

    private static long getCutoff(long total, Stream<Long> values) {
        ArrayList<Long> sorted = new ArrayList<>(values.toList());
        sorted.sort(Long::compare);
        long accountedTime = 0;
        long cutoffTime = 0;
        for (int i = sorted.size() - 1; i >= 0; i--) {
            if (accountedTime <= total * CUTOFF_TIME) {
                cutoffTime = sorted.get(i);
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
        if (PROFILE_CALL_INTERVAL != 0) {
            enterInternal();
        }
    }

    public static void exit(CApiTiming t) {
        if (PROFILE_CALL_INTERVAL != 0) {
            exitInternal(t);
        }
    }

    @TruffleBoundary
    private static void enterInternal() {
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
