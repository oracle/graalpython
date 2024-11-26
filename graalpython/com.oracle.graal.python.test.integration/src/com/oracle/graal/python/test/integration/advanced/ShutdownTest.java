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

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.graal.python.test.integration.PythonTests;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class ShutdownTest extends PythonTests {
    @Test
    public void testCloseWithBackgroundThreadsRunningSucceeds() {
        Context context = Context.newBuilder().allowExperimentalOptions(true).allowAllAccess(true).build();
        try {
            loadNativeExtension(context);
            asyncStartPythonThreadsThatSleep(context);
        } finally {
            context.close(true);
        }
    }

    @Test
    public void testCloseFromAnotherThreadThrowsCancelledEx() {
        Context context = Context.newBuilder().allowExperimentalOptions(true).allowAllAccess(true).build();
        PolyglotException thrownEx = null;
        try {
            loadNativeExtension(context);
            asyncStartPythonThreadsThatSleep(context);
            CountDownLatch latch = new CountDownLatch(1);
            new Thread(() -> {
                try {
                    latch.await();
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                // close from another thread
                context.close(true);
            }).start();
            countDownLatchAndSleepInPython(context, latch);
        } catch (PolyglotException ex) {
            thrownEx = ex;
        } finally {
            context.close(true);
            Assert.assertNotNull("PolyglotException was not thrown upon cancellation ", thrownEx);
            Assert.assertTrue(thrownEx.toString(), thrownEx.isCancelled());
        }
    }

    @Test
    public void testJavaThreadGetsCancelledException() throws InterruptedException {
        Context context = Context.newBuilder().allowExperimentalOptions(true).allowAllAccess(true).build();
        AtomicReference<PolyglotException> thrownEx = new AtomicReference<>();
        CountDownLatch gotException = new CountDownLatch(1);
        try {
            asyncStartPythonThreadsThatSleep(context);
            CountDownLatch latch = new CountDownLatch(1);
            new Thread(() -> {
                try {
                    // this thread will get stuck sleeping in python
                    countDownLatchAndSleepInPython(context, latch);
                } catch (PolyglotException ex) {
                    thrownEx.set(ex);
                    gotException.countDown();
                }
            }).start();
            try {
                loadNativeExtension(context);
                latch.await();
                Thread.sleep(10); // make sure the other thread really starts sleeping
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        } finally {
            context.close(true);
        }
        Assert.assertTrue("other thread did not get any exception in time limit", gotException.await(2, TimeUnit.SECONDS));
        Assert.assertNotNull("PolyglotException was not thrown upon cancellation ", thrownEx.get());
        Assert.assertTrue(thrownEx.toString(), thrownEx.get().isCancelled());
    }

    @Test
    public void testJavaThreadNotExecutingPythonAnymore() throws InterruptedException {
        Context context = Context.newBuilder().allowExperimentalOptions(true).allowAllAccess(true).build();
        var javaThreadDone = new CountDownLatch(1);
        var javaThreadCanEnd = new CountDownLatch(1);
        var javaThread = new Thread(() -> {
            Assert.assertEquals(42, context.eval("python", "42").asInt());
            javaThreadDone.countDown();
            try {
                javaThreadCanEnd.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        AtomicReference<Throwable> uncaughtEx = new AtomicReference<>();
        javaThread.setUncaughtExceptionHandler((t, e) -> uncaughtEx.set(e));
        try {
            javaThread.start();
            loadNativeExtension(context);
            javaThreadDone.await();
        } finally {
            // we can close although the Java thread is still running
            context.close(true);
        }
        javaThreadCanEnd.countDown();
        javaThread.join();
        Assert.assertNull(uncaughtEx.get());
    }

    private static void loadNativeExtension(Context context) {
        context.eval("python", "import _cpython_sre\nassert _cpython_sre.ascii_tolower(88) == 120\n");
    }

    private static void asyncStartPythonThreadsThatSleep(Context context) {
        for (int i = 0; i < 10; i++) {
            context.eval("python", "import threading; import time; threading.Thread(target=lambda: time.sleep(10000)).start()");
        }
    }

    private static void countDownLatchAndSleepInPython(Context context, CountDownLatch latch) {
        context.eval("python", "def run(latch): import time; latch.countDown(); time.sleep(100000)");
        context.getBindings("python").getMember("run").execute(latch);
    }
}
