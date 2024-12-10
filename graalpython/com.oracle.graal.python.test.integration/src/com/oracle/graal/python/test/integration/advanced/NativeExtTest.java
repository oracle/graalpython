/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.test.integration.advanced;

import java.util.concurrent.CountDownLatch;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.junit.Assert;
import org.junit.Test;

/**
 * This should be the only test using native extensions. We must test everything in one long test,
 * because we cannot create multiple contexts that would load native extensions.
 */
public class NativeExtTest {
    @Test
    public void testSharingErrorWithCpythonSre() throws InterruptedException {
        // The first context is the one that will use native extensions
        Engine engine = Engine.newBuilder().build();
        Context cextContext = newContext(engine);
        try {
            cextContext.eval("python", "import _cpython_sre\nassert _cpython_sre.ascii_tolower(88) == 120\n");

            // Check that second context that tries to load native extension fails
            try (Context secondCtx = newContext(engine)) {
                try {
                    secondCtx.eval("python", "import _cpython_sre\nassert _cpython_sre.ascii_tolower(88) == 120\n");
                } catch (PolyglotException ex) {
                    Assert.assertTrue(ex.getMessage(), ex.getMessage().contains("Option python.NativeModules is set to 'true' and a second GraalPy context attempted"));
                }
            }

            // To test cancellation we are going to spawn some Python threads...
            ShutdownTest.asyncStartPythonThreadsThatSleep(cextContext);

            // A java thread that attaches to GraalPy and finishes before the cancellation
            CountDownLatch latch = new CountDownLatch(1);
            new Thread(() -> {
                cextContext.eval("python", "import _cpython_sre\nassert _cpython_sre.ascii_tolower(88) == 120\n");
                latch.countDown();
            }).start();
            latch.await();

            // A java thread that attaches to GraalPy and sleeps forever in Java
            CountDownLatch latch2 = new CountDownLatch(1);
            new Thread(() -> {
                cextContext.eval("python", "import _cpython_sre\nassert _cpython_sre.ascii_tolower(88) == 120\n");
                latch2.countDown();
                try {
                    Thread.sleep(100000000);
                } catch (InterruptedException e) {
                    // expected
                }
            }).start();
            latch2.await();

        } finally {
            cextContext.close(true);
        }
    }

    private static Context newContext(Engine engine) {
        return Context.newBuilder().allowExperimentalOptions(true).allowAllAccess(true).engine(engine).build();
    }
}
