/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.Assert;
import org.junit.Test;

public class SharedEngineMultithreadingSimpleLambdaTest extends SharedEngineMultithreadingTestBase {
    private static final int RUNS_COUNT = 100 * RUNS_COUNT_FACTOR;

    @Test
    public void testLambdaInParallelCtxCreatedInMainThread() throws InterruptedException {
        for (int runIndex = 0; runIndex < RUNS_COUNT; runIndex++) {
            Source code = Source.create("python", "lambda: 42");
            Engine engine = Engine.create();
            Thread[] threads = new Thread[Runtime.getRuntime().availableProcessors()];
            for (int i = 0; i < threads.length; i++) {
                InitializedContext ctx = initContext(engine, new String[0]);
                threads[i] = new Thread(() -> {
                    try {
                        Value result = ctx.context.eval(code).execute();
                        Assert.assertEquals(42, result.asInt());
                        StdStreams out = ctx.getStreamsOutput();
                        Assert.assertEquals("", out.out);
                        Assert.assertEquals("", out.err);
                    } finally {
                        ctx.close();
                    }
                });
            }
            startAndJoinThreadsAssertNoErrors(threads);
        }
    }

    @Test
    public void testLambdaInParallelCtxCreatedInWorkerThread() throws InterruptedException {
        for (int runIndex = 0; runIndex < RUNS_COUNT; runIndex++) {
            Source code = Source.create("python", "lambda: 42");
            Engine engine = Engine.create();
            Thread[] threads = new Thread[Runtime.getRuntime().availableProcessors()];
            for (int i = 0; i < threads.length; i++) {
                threads[i] = new Thread(() -> {
                    try (InitializedContext ctx = initContext(engine, new String[0])) {
                        Value result = ctx.context.eval(code).execute();
                        Assert.assertEquals(42, result.asInt());
                        StdStreams out = ctx.getStreamsOutput();
                        Assert.assertEquals("", out.out);
                        Assert.assertEquals("", out.err);
                    }
                });
            }
            startAndJoinThreadsAssertNoErrors(threads);
        }
    }

    @Test
    public void testLambdaInParallelSharedContexts() throws InterruptedException {
        for (int runIndex = 0; runIndex < RUNS_COUNT; runIndex++) {
            Source code = Source.create("python", "lambda: 42");
            Engine engine = Engine.create();
            Thread[] threads = new Thread[Runtime.getRuntime().availableProcessors()];
            InitializedContext[] contexts = new InitializedContext[]{
                    initContext(engine, new String[0]),
                    initContext(engine, new String[0]),
                    initContext(engine, new String[0])
            };
            try {
                for (int i = 0; i < threads.length; i++) {
                    final int contextIdx = i % contexts.length;
                    threads[i] = new Thread(() -> {
                        InitializedContext ctx = contexts[contextIdx];
                        Value result = ctx.context.eval(code).execute();
                        Assert.assertEquals(42, result.asInt());
                        StdStreams out = ctx.getStreamsOutput();
                        Assert.assertEquals("", out.out);
                        Assert.assertEquals("", out.err);
                    });
                }
                startAndJoinThreadsAssertNoErrors(threads);
            } finally {
                for (InitializedContext ctx : contexts) {
                    ctx.close();
                }
            }
        }
    }
}
