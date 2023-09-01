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
package com.oracle.graal.python.test.integration.engine;

import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.Assert;
import org.junit.Test;

public class SharedEngineMultithreadingShapeTransitionsTest extends SharedEngineMultithreadingTestBase {
    private static final int RUNS_COUNT = RUNS_COUNT_FACTOR;
    private static final int PROPERTIES_COUNT = 20;
    private static final int SHARED_PREFIX_COUNT = PROPERTIES_COUNT - 10;

    @Test
    public void testShapeTransitionsInParallel() throws InterruptedException, ExecutionException {
        ExecutorService executorService = createExecutorService();
        String[] names = IntStream.range(0, RUNS_COUNT * PROPERTIES_COUNT).mapToObj(x -> "prop" + x).toArray(String[]::new);
        String[] sharedPrefixNames = IntStream.range(0, SHARED_PREFIX_COUNT).mapToObj(x -> "prefix" + x).toArray(String[]::new);
        InitializedContext[] contexts = new InitializedContext[Runtime.getRuntime().availableProcessors()];

        for (int runIndex = 0; runIndex < RUNS_COUNT; runIndex++) {
            log("Running %d iteration of testLambdaInParallelCtxCreatedInMainThread", runIndex);
            try (Engine engine = Engine.create()) {
                for (int i = 0; i < contexts.length; i++) {
                    contexts[i] = initContext(engine, new String[0]);
                }
                Source createClass = Source.create("python", "class MySharedShape:\n" +
                                "  def __init__(self): self.start = 42;");

                Task[] tasks = new Task[contexts.length];
                for (int taskIdx = 0; taskIdx < tasks.length; taskIdx++) {
                    int index = taskIdx;
                    int runNumber = runIndex;
                    tasks[taskIdx] = () -> {
                        Context ctx = contexts[index].context;
                        ctx.eval(createClass);

                        /*
                         * Here we generate Python code that looks like:
                         *
                         * obj=MySharedShape() obj.someProp = 0 assert obj.someProp == 0
                         * obj.otherProp = 1 assert obj.otherProp == 1 ... obj.start # the result
                         * returned to the embedded
                         */

                        // start with few names that are going to be the same in all threads and
                        // then choose some pseudo random names possibly shared possibly not
                        String[] testNames = Arrays.copyOf(sharedPrefixNames, PROPERTIES_COUNT);
                        for (int j = testNames.length - sharedPrefixNames.length; j < testNames.length; j++) {
                            testNames[j] = names[(runNumber ^ index ^ j) % names.length];
                        }

                        // set all of them to some value and assert that they were set correctly
                        // (still inside Python), check in Java that the initial property is there
                        // and set to 42
                        String code = "obj=MySharedShape()\n" +
                                        IntStream.range(0, testNames.length).mapToObj(i -> String.format("obj.%s=%d", testNames[i], i)).collect(Collectors.joining("\n")) + "\n" +
                                        IntStream.range(0, testNames.length).mapToObj(i -> String.format("assert obj.%s==%d", testNames[i], i)).collect(Collectors.joining("\n")) + "\n" +
                                        "obj.start";
                        Value result = ctx.eval("python", code);
                        Assert.assertEquals(42, result.asInt());

                        // Sanity check of the outputs
                        StdStreams out = contexts[index].getStreamsOutput();
                        logOutput(index, out);
                        Assert.assertEquals("", out.out);
                        Assert.assertEquals("", out.err);
                        return null;
                    };
                }
                submitAndWaitAll(executorService, tasks);
            } finally {
                for (InitializedContext ctx : contexts) {
                    if (ctx != null) {
                        ctx.close();
                    }
                }
            }
        }
    }
}
