/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Value;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

public class SharedEngineMultithreadingOSRAndQuickeningTest extends SharedEngineMultithreadingTestBase {
    private static final int RUNS_COUNT = 1;

    private static final String CODE = """
                    def get_byte(buf, i):
                        return buf.__getitem__(i)

                    def get_long():
                        return 33**2

                    from dataclasses import dataclass
                    @dataclass
                    class Result:
                        min: int
                        max: int
                        sum: int
                        name: bytes
                        count: int

                    def parseLoop(buf):
                        ORD_SEMICOLON = ord(';')
                        ORD_ZERO = ord('0')
                        ORD_MINUS = ord('-')
                        ORD_DOT = ord('.')
                        scanPtr = 0
                        cities = dict()
                        while scanPtr < len(buf):
                            nameAddress = scanPtr

                            scanPtr += 1
                            while True:
                                if get_byte(buf, scanPtr) == ORD_SEMICOLON:
                                    break
                                scanPtr += 1

                            nameLength = scanPtr - nameAddress
                            scanPtr += 1

                            # parse number
                            number = 0
                            sign_or_first_digit = get_byte(buf, scanPtr)
                            if sign_or_first_digit == ORD_MINUS:
                                scanPtr += 1
                                number = get_byte(buf, scanPtr) - ORD_ZERO
                                scanPtr += 1
                                if get_byte(buf, scanPtr) != ORD_DOT:
                                    number = number * 10 + (get_byte(buf, scanPtr) - ORD_ZERO)
                                    scanPtr += 1
                                scanPtr += 1
                                number = number * 10 + (get_byte(buf, scanPtr) - ORD_ZERO)
                                number = -number
                            else:
                                number = sign_or_first_digit - ORD_ZERO
                                scanPtr += 1
                                if get_byte(buf, scanPtr) != ORD_DOT:
                                    number = number * 10 + (get_byte(buf, scanPtr) - ORD_ZERO)
                                    scanPtr += 1
                                scanPtr += 1
                                number = number * 10 + (get_byte(buf, scanPtr) - ORD_ZERO)
                            scanPtr += 1

                            name = buf[nameAddress:(nameAddress+nameLength)]
                            existing = cities.get(name)
                            if existing:
                                existing.sum += number
                                existing.min = min(existing.min, number)
                                existing.max = max(existing.max, number)
                                existing.count += 1
                            else:
                                if name == b'Brno':
                                    scanPtr += get_long()  # int -> long
                                cities[name] = Result(number, number, number, name, count=1)
                                if number == 999:
                                    scanPtr -= get_long()

                            scanPtr += 1
                        return cities

                    def get_buffer():
                        return b'Prague;10.8\\nPrague;33.5\\n' * 500 + b'Brno;99.9\\n' + b'Prague;10.8\\nPrague;33.5\\n' * 500
                    """;

    @Test
    public void testOSRAndQuickenInParallel() throws InterruptedException, ExecutionException {
        ExecutorService executorService = createExecutorService();
        InitializedContext[] contexts = new InitializedContext[Runtime.getRuntime().availableProcessors()];
        try (Engine e = Engine.create()) {
            // No point in this test if compiler is enabled and OSR cannot be configured
            Assume.assumeNotNull(e.getOptions().get("engine.OSRCompilationThreshold"));
        }
        for (int runIndex = 0; runIndex < RUNS_COUNT; runIndex++) {
            try (Engine engine = Engine.newBuilder().allowExperimentalOptions(true).option("engine.OSRCompilationThreshold", "100").build()) {
                for (int i = 0; i < contexts.length; i++) {
                    contexts[i] = initContext(engine, new String[0]);
                }
                final Value[] buffers = new Value[contexts.length];
                final Value[] funs = new Value[contexts.length];
                for (int i = 0; i < contexts.length; i++) {
                    Context ctx = contexts[i].context;
                    ctx.eval("python", CODE);
                    Value getBuffer = ctx.getBindings("python").getMember("get_buffer");
                    buffers[i] = getBuffer.execute();
                    funs[i] = ctx.getBindings("python").getMember("parseLoop");
                }

                Task[] tasks = new Task[contexts.length];
                for (int i = 0; i < tasks.length; i++) {
                    int taskIndex = i;
                    tasks[i] = () -> {
                        Value result = funs[taskIndex].execute(buffers[taskIndex]);
                        Assert.assertTrue(result.hasHashEntries());
                        Assert.assertEquals(2, result.getHashSize());
                        Value key = contexts[taskIndex].context.eval("python", "b'Prague'");
                        Value city = result.getHashValue(key);
                        Assert.assertEquals(108, city.getMember("min").asInt());
                        Assert.assertEquals(335, city.getMember("max").asInt());
                        return null;
                    };
                }
                submitAndWaitAll(executorService, tasks);
            }
        }
    }
}
