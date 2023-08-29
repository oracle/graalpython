/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.test.engine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.junit.Rule;

import com.oracle.graal.python.test.CleanupRule;
import com.oracle.graal.python.test.PythonTests;
import com.oracle.graal.python.util.Consumer;

/**
 * Base class for tests that run in multiple context with a shared engine and in parallel.
 */
public class SharedEngineMultithreadingTestBase extends PythonTests {
    // To increase the chances of hitting concurrency issues, we run each test repeatedly.
    protected static final int RUNS_COUNT_FACTOR = Integer.getInteger("com.oracle.graal.python.test.SharedEngineMultithreadingRunCountFactor", 4);
    protected static final int THREADS_COUNT = Runtime.getRuntime().availableProcessors();
    private static final boolean LOG = false;

    @Rule public CleanupRule cleanup = new CleanupRule();

    protected static void log(String fmt, Object... args) {
        if (LOG) {
            System.out.printf(fmt + "\n", args);
        }
    }

    public static void logOutput(int workerId, StdStreams result) {
        log("Thread %d out:%n%s%n---", workerId, result.out);
        log("Thread %d err:%n%s%n---", workerId, result.err);
    }

    protected ExecutorService createExecutorService() {
        ExecutorService executorService = Executors.newFixedThreadPool(THREADS_COUNT, new ThreadFactory() {
            private int index;

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "tests-thread-pool-thread-" + index++);
            }
        });
        cleanup.add(() -> {
            executorService.shutdown();
            executorService.awaitTermination(10000, TimeUnit.MILLISECONDS);
        });
        return executorService;
    }

    protected static void submitAndWaitAll(ExecutorService service, Task[] tasks) throws InterruptedException, ExecutionException {
        CountDownLatch latch = new CountDownLatch(tasks.length);
        Future<?>[] futures = new Future<?>[tasks.length];
        for (int i = 0; i < futures.length; i++) {
            final int index = i;
            futures[i] = service.submit(() -> {
                try {
                    latch.countDown();
                    latch.await();
                    tasks[index].call();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
        for (Future<?> future : futures) {
            future.get();
        }
        log("All %d futures finished...", tasks.length);
    }

    InitializedContext initContext(Engine engine, String[] args) {
        ByteArrayOutputStream errArray = new ByteArrayOutputStream();
        ByteArrayOutputStream outArray = new ByteArrayOutputStream();
        PrintStream errStream = new PrintStream(errArray);
        PrintStream outStream = new PrintStream(outArray);

        final Context context = Context.newBuilder().engine(engine).out(outStream).err(errStream).//
                        option("python.Executable", executable).//
                        allowExperimentalOptions(true).allowAllAccess(true).//
                        arguments("python", args).build();
        return new InitializedContext(context, outArray, errArray);
    }

    StdStreams run(int workerId, Engine engine, String[] args, Consumer<Context> action) {
        StdStreams result;
        try (InitializedContext ctx = initContext(engine, args)) {
            ctx.context.initialize("python");
            ctx.context.enter();
            action.accept(ctx.context);
            result = ctx.getStreamsOutput();
        }

        logOutput(workerId, result);
        return result;
    }

    protected static final class InitializedContext implements AutoCloseable {
        final Context context;
        final ByteArrayOutputStream errArray;
        final ByteArrayOutputStream outArray;

        private InitializedContext(Context context, ByteArrayOutputStream outArray, ByteArrayOutputStream errArray) {
            this.context = context;
            this.outArray = outArray;
            this.errArray = errArray;
        }

        public StdStreams getStreamsOutput() {
            String err = errArray.toString().replaceAll("\r\n", "\n");
            String result = outArray.toString().replaceAll("\r\n", "\n");
            return new StdStreams(result, err);
        }

        @Override
        public void close() {
            context.close();
        }
    }

    protected static final class StdStreams {
        final String out;
        final String err;

        public StdStreams(String out, String err) {
            this.out = out;
            this.err = err;
        }
    }

    @FunctionalInterface
    protected interface Task extends Callable<Void> {
    }
}
