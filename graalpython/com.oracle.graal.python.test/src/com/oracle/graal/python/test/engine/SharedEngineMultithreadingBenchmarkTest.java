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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.junit.Test;

import com.oracle.graal.python.test.PythonTests;

public class SharedEngineMultithreadingBenchmarkTest extends SharedEngineMultithreadingTestBase {
    @Test
    public void testRichardsInParallelInMultipleContexts() throws Throwable {
        try (Engine engine = Engine.newBuilder().allowExperimentalOptions(true).option("python.NativeModules", "false").build()) {
            File richards = PythonTests.getBenchFile(Paths.get("meso", "richards3.py"));
            Source richardsSource = getSource(richards);
            Task[] tasks = new Task[THREADS_COUNT];
            for (int taskIdx = 0; taskIdx < tasks.length; taskIdx++) {
                final int id = taskIdx;
                tasks[taskIdx] = () -> {
                    log("Running %s in thread %d", richards, id);
                    StdStreams result = run(id, engine, new String[]{richards.toString(), "2"}, ctx -> ctx.eval(richardsSource));
                    assertEquals("", result.err);
                    assertTrue("unexpected output" + result.out, result.out.matches("finished\\.\\s+[a-zA-Z0-9/.\\-]*\\s+took\\s+[0-9.]*\\s+s\\s+"));
                    return null;
                };
            }
            submitAndWaitAll(createExecutorService(), tasks);
        }
    }

    private static Source getSource(File file) {
        try {
            return Source.newBuilder("python", file).build();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
