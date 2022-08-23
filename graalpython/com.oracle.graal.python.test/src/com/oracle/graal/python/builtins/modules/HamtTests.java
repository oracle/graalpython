package com.oracle.graal.python.builtins.modules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.oracle.graal.python.builtins.objects.contextvars.Hamt;
import com.oracle.graal.python.test.PythonTests;

import junit.framework.TestCase;

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
        new Hamt();
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
    public void measureTimeForSmallHamtAcceses() {
        Hamt hamt = new Hamt().withEntry(new Hamt.Entry(1, 0, 1)).withEntry(new Hamt.Entry(2, 31, 2));
        long start = System.nanoTime();
        for(int i = 0; i < 1000000; ++i) {
            Hamt requestContext = hamt.withEntry(new Hamt.Entry(2, 31, 1));
            requestContext.lookup(2, 31);
            requestContext.lookup(1, 0);
        }
        System.out.println("Took " + (System.nanoTime() - start) + " nanoseconds");

    }
}
