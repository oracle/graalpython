/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.test.builtin.modules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.oracle.graal.python.builtins.objects.contextvars.Hamt;
import com.oracle.graal.python.builtins.objects.contextvars.HamtIterator;
import com.oracle.graal.python.test.PythonTests;

public class HamtTests {
    @Before
    public void enter() {
        PythonTests.enterContext();
    }

    @After
    public void close() {
        PythonTests.closeContext();
    }

    @Test
    public void testHamtConstructed() {
        Hamt hamt = new Hamt();
        assertEquals("null\n", hamt.dump());
    }

    @Test
    public void testHamtSetLookup() {
        Hamt hamt = new Hamt();
        hamt = hamt.withEntry(new Hamt.Entry(1, 1, 1));
        assertEquals(1, hamt.lookup(1, 1));
        hamt = hamt.withEntry(new Hamt.Entry(0, 0, 0));
        assertEquals(0, hamt.lookup(0, 0));
        assertEquals(1, hamt.lookup(1, 1));
    }

    @Test
    public void testBitmapExtremes() {
        Hamt hamt = new Hamt();
        hamt = hamt.withEntry(new Hamt.Entry(1, 0, 1));
        hamt = hamt.withEntry(new Hamt.Entry(2, 31, 2));
        assertEquals(2, hamt.lookup(2, 31));
        hamt = new Hamt();
        hamt = hamt.withEntry(new Hamt.Entry(1, 31, 1));
        hamt = hamt.withEntry(new Hamt.Entry(2, 0, 2));
        assertEquals(2, hamt.lookup(2, 0));
        assertEquals(1, hamt.lookup(1, 31));
    }

    @Test
    public void testSequentialNumbers() {
        int limit = 700;
        Hamt hamt = new Hamt();
        for (int i = 0; i < limit; ++i) {
            hamt = hamt.withEntry(new Hamt.Entry(i, i, i));
            for (int j = 0; j < limit; ++j) {
                assertEquals(j <= i ? j : null, hamt.lookup(j, j));
            }
        }
    }

    @Test
    public void testLotsOfEntries() {
        Hamt hamt = new Hamt();
        for (int i = 0; i < 100000; ++i) {
            hamt = hamt.withEntry(new Hamt.Entry(i, i * 3, i * 2));
        }
        for (int i = 0; i < 100000; ++i) {
            assertEquals(i * 2, hamt.lookup(i, i * 3));
        }
    }

    @Test
    public void addAndRemoveEntries() {
        int limit = 700;
        Hamt hamt = new Hamt();
        for (int i = 0; i < limit; ++i) {
            hamt = hamt.withEntry(new Hamt.Entry(i, i * 3, i));
            for (int j = 0; j < limit; ++j) {
                assertNull(hamt.without(j, j * 3).lookup(j, j * 3));
            }
        }
    }

    @Test
    public void addAndRemoveAllEntries() {
        int limit = 70000;
        Hamt hamt = new Hamt();
        for (int i = 0; i < limit; ++i) {
            hamt = hamt.withEntry(new Hamt.Entry(i, i * 3, i + 1));
        }
        Hamt fullHamt = hamt;
        for (int i = limit - 1; i >= 0; --i) {
            hamt = hamt.without(i, i * 3);
            if (hamt.lookup(i, i * 3) != null) {
                fail(i + ":" + hamt.dump());
            }
        }
        assertEquals("null\n", hamt.dump());
        hamt = fullHamt;
        for (int i = 0; i < limit; ++i) {
            hamt = hamt.without(i, i * 3);
            if (hamt.lookup(i, i * 3) != null) {
                fail(i + ":" + hamt.dump());
            }
        }
        assertEquals("null\n", hamt.dump());
    }

    @Test
    public void largeHashVariance() {
        int limit = 70000;
        Hamt hamt = new Hamt();
        for (int i = 0; i < limit; ++i) {
            hamt = hamt.withEntry(new Hamt.Entry(i, String.valueOf(i).hashCode(), i + 1));
        }
        for (int i = 0; i < limit; ++i) {
            assertEquals(i + 1, hamt.lookup(i, String.valueOf(i).hashCode()));
        }
        for (int i = 0; i < limit; ++i) {
            hamt = hamt.without(i, String.valueOf(i).hashCode());
        }
        assertEquals("null\n", hamt.dump());
    }

    @Test
    public void testHamtSize() {
        int limit = 100;
        Hamt hamt = new Hamt();
        for (int i = 0; i < limit; ++i) {
            assertEquals(i, hamt.size());
            hamt = hamt.withEntry(new Hamt.Entry(i, i % 2 == 0 ? String.valueOf(i).hashCode() : 0, i + 1));
        }
    }

    @Test
    public void testHamtIterator() {
        int limit = 100;
        boolean[] seen = new boolean[limit];
        Hamt hamt = new Hamt();
        for (int i = 0; i < limit; ++i) {
            hamt = hamt.withEntry(new Hamt.Entry(i, String.valueOf(i).hashCode(), String.valueOf(i)));
        }
        HamtIterator hi = new HamtIterator(hamt);
        Hamt.Entry el = hi.next();
        while (el != null) {
            seen[(int) el.key] = true;
            assertEquals(String.valueOf(el.key), el.value);
            el = hi.next();
        }
        for (int i = 0; i < limit; ++i) {
            assertTrue("failed at " + i, seen[i]);
        }
    }

// @Test
    public void measureTimeForSmallHamtAcceses() {
        Hamt hamt = new Hamt().withEntry(new Hamt.Entry(1, 0, 1)).withEntry(new Hamt.Entry(2, 31, 2));
        long start = System.nanoTime();
        for (int i = 0; i < 1000000; ++i) {
            Hamt requestContext = hamt.withEntry(new Hamt.Entry(2, 31, 1));
            requestContext.lookup(2, 31);
            requestContext.lookup(1, 0);
        }
        System.out.println("Took " + (System.nanoTime() - start) + " nanoseconds");

    }
}
