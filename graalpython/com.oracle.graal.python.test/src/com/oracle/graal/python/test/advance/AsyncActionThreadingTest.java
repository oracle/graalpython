/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.test.advance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graalvm.polyglot.Context;
import org.junit.Test;

import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.test.PythonTests;

import sun.misc.Unsafe;

public class AsyncActionThreadingTest extends PythonTests {
    long pythonThreadCount() {
        return pythonThreads().count();
    }

    String threadNames() {
        return pythonThreads().map((t) -> t.getName()).collect(Collectors.joining(","));
    }

    Stream<Thread> pythonThreads() {
        int c;
        Thread[] threads;
        do {
            c = Thread.activeCount() + 1;
            threads = new Thread[c];
        } while (Thread.enumerate(threads) >= c);
        return Arrays.stream(threads).filter((t) -> t != null && t.getName().startsWith("python"));
    }

    @Test
    public void testNewThreadsByDefault() {
        long threadCount = pythonThreadCount();
        Context c = PythonTests.enterContext();
        try {
            // importlib creates weakref callbacks, struct is native, so we should have
            // async action threads for cleanup now
            c.eval("python", "import re, itertools, functools, _struct; _struct.pack('i', 1)");
            long collectionActionsThreadCount = pythonThreadCount();
            assertTrue("automatic async actions use threads to trigger " + threadNames(), threadCount < collectionActionsThreadCount);
        } finally {
            PythonTests.closeContext();
        }
        assertEquals("python cleans up its threads: " + threadNames(), threadCount, pythonThreadCount());

        // now flip the option and create a new context that does not have multiple threads
        Unsafe unsafe = null;
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            unsafe = (Unsafe) theUnsafe.get(Unsafe.class);
        } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e1) {
            fail("Cannot get unsafe");
        }
        Field field;
        boolean fieldWasTrue = PythonOptions.AUTOMATIC_ASYNC_ACTIONS;
        try {
            field = PythonOptions.class.getField("AUTOMATIC_ASYNC_ACTIONS");
            assertFalse("AUTOMATIC_ASYNC_ACTIONS should be final", (field.getModifiers() & Modifier.FINAL) == 0);
            unsafe.putBoolean(unsafe.staticFieldBase(field), unsafe.staticFieldOffset(field), false);
            try {
                c = PythonTests.enterContext();
                try {
                    c.eval("python", "import re, itertools, functools, _struct; _struct.pack('i', 1)");
                    assertTrue("manual async actions create no threads " + threadNames(), threadCount == pythonThreadCount());
                    c.eval("python", "import threading; t = threading.Thread(target=lambda: print(1)); t.start(); t.join()");
                    assertTrue("manual async actions create no thread for gil release " + threadNames(), threadCount == pythonThreadCount());
                } finally {
                    PythonTests.closeContext();
                }
            } finally {
                unsafe.putBoolean(unsafe.staticFieldBase(field), unsafe.staticFieldOffset(field), fieldWasTrue);
            }
        } catch (NoSuchFieldException e) {
            fail("PythonOptions.AUTOMATIC_ASYNC_ACTIONS should exist");
        }
    }
}
