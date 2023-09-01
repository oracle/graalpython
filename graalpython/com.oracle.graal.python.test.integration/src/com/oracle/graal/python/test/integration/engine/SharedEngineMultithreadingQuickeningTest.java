/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.test.integration.engine;

import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.junit.Assert;
import org.junit.Test;

public class SharedEngineMultithreadingQuickeningTest extends SharedEngineMultithreadingTestBase {
    private static final int RUNS_COUNT = RUNS_COUNT_FACTOR;
    private static final int FUNCTIONS = 6;
    private static final String[] VALUES = new String[]{"0", "1", "1.0", "0.0", "False", Integer.toString(Integer.MAX_VALUE)};

    @Test
    public void testQuickenInParallel() throws InterruptedException, ExecutionException {
        ExecutorService executorService = createExecutorService();
        InitializedContext[] contexts = new InitializedContext[Runtime.getRuntime().availableProcessors()];
        Random random = new Random();
        for (int runIndex = 0; runIndex < RUNS_COUNT; runIndex++) {
            try (Engine engine = Engine.create()) {
                for (int i = 0; i < contexts.length; i++) {
                    contexts[i] = initContext(engine, new String[0]);
                }
                StringBuilder functions = new StringBuilder();
                for (int i = 0; i < FUNCTIONS; i++) {
                    String fn = String.format("def fn%d(a):\n", i) +
                                    " b = a + a\n" +
                                    " c = -a\n" +
                                    " if not c:\n" +
                                    "  c = None\n";
                    functions.append(fn);
                }
                Source fns = Source.create("python", functions.toString());
                Task[] tasks = new Task[contexts.length];
                for (int i = 0; i < tasks.length; i++) {
                    int taskIndex = i;
                    tasks[i] = () -> {
                        InitializedContext ctx = contexts[taskIndex];
                        ctx.context.eval(fns);
                        StringBuilder calls = new StringBuilder();
                        for (int j = 0; j < FUNCTIONS; j++) {
                            calls.append(String.format("fn%d(%s)\n", j, VALUES[random.nextInt(VALUES.length)]));
                        }
                        ctx.context.eval("python", calls.toString());
                        StdStreams out = ctx.getStreamsOutput();
                        Assert.assertEquals("", out.out);
                        Assert.assertEquals("", out.err);
                        return null;
                    };
                }
                submitAndWaitAll(executorService, tasks);
            }
        }
    }
}
