/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.cext.test;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.graalvm.polyglot.Context;
import org.junit.Assert;

public class MultithreadedImportTestBase {
    private static final int ITERATIONS_LIMIT = 10;

    // This test should be executed in its own process. It tests that there are no deadlocks. We do
    // not want to wait for the gate job timeout and timeout the test ourselves after shorter period
    // of time, but for that we must not call ExecutorService#close, which would be waiting for the
    // threads to finish
    @SuppressWarnings("resource")
    static void multithreadedImportTest(int numberOfThreads, Context context) {
        try {
            ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
            var tasks = new ArrayList<Future<?>>();
            for (String pkg : PACKAGES.trim().split("\n")) {
                log("Starting import: %s", pkg);
                tasks.add(executor.submit(() -> {
                    log("Importing %s on thread %s", pkg, Thread.currentThread());
                    context.eval("python", "import " + pkg);
                }));
            }

            int iteration = 0;
            while (!tasks.isEmpty() && iteration++ < ITERATIONS_LIMIT) {
                log("Iteration %s, looping over remaining %d unfinished tasks", iteration, tasks.size());
                var finishedTasks = tasks.stream().filter(task -> {
                    try {
                        task.get(1000, TimeUnit.MILLISECONDS);
                        return true;
                    } catch (TimeoutException timeoutEx) {
                        return false;
                    } catch (Exception ex) {
                        log("Caught exception: %s", ex);
                        throw new RuntimeException(ex);
                    }
                }).collect(Collectors.toCollection(ArrayList::new));
                tasks.removeAll(finishedTasks);
            }

            if (tasks.isEmpty()) {
                executor.shutdown();
            } else {
                // otherwise do not wait for the threads to finish, just dump them and continue to
                // fail the assertion below
                try {
                    System.out.println("There are unfinished tasks. This failure is inherently transient. " +
                                    "Please report any failure. Thread dump is below if available:");
                    ThreadMXBean threadMxBean = ManagementFactory.getThreadMXBean();
                    for (ThreadInfo threadInfo : threadMxBean.dumpAllThreads(true, true, 20)) {
                        System.out.print(threadInfo.toString());
                    }
                } catch (UnsupportedOperationException ignored) {
                }
            }

            Assert.assertTrue("Unfinished tasks", tasks.isEmpty());
            log("DONE: %s", MultithreadedImportTestBase.class.getSimpleName());
        } finally {
            context.close(true);
        }
    }

    private static void log(String fmt, Object... args) {
        System.out.printf(fmt + "%n", args);
    }

    private static final String PACKAGES = """
                    csv
                    configparser
                    tomllib
                    hashlib
                    os
                    _testcapi
                    io
                    time
                    logging
                    ctypes
                    argparse
                    _sqlite3
                    _cpython_sre
                    threading
                    multiprocessing
                    sched
                    contextvars
                    json
                    pyexpat
                    base64
                    html
                    locale
                    shlex
                    venv
                    ast
                    re
                    difflib
                    zlib
                    """;
}
