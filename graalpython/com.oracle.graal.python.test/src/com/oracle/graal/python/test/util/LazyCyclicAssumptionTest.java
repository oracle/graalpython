/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.test.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.oracle.graal.python.util.LazyCyclicAssumption;
import com.oracle.truffle.api.Assumption;

public class LazyCyclicAssumptionTest {
    @Test
    public void testRenewalAndExhaustion() {
        LazyCyclicAssumption cyclic = new LazyCyclicAssumption("test");
        Assumption first = cyclic.getAssumption();
        assertTrue(first.isValid());
        assertSame(first, cyclic.getAssumption());
        cyclic.invalidate(2);
        assertFalse(first.isValid());
        // Invalidations without an intervening get must not exhaust the remaining budget.
        cyclic.invalidate(2);
        cyclic.invalidate(2);
        Assumption second = cyclic.getAssumption();
        assertTrue(second.isValid());
        assertNotSame(first, second);
        cyclic.invalidate(2);
        assertFalse(second.isValid());
        assertSame(Assumption.NEVER_VALID, cyclic.getAssumption());
        cyclic.invalidate(Integer.MAX_VALUE);
        assertSame(Assumption.NEVER_VALID, cyclic.getAssumption());
    }

    @Test
    public void testInvalidationBeforeFirstGet() {
        LazyCyclicAssumption cyclic = new LazyCyclicAssumption("test");
        cyclic.invalidate(2);
        cyclic.invalidate(2);
        Assumption first = cyclic.getAssumption();
        assertTrue(first.isValid());
        cyclic.invalidate(2);
        assertFalse(first.isValid());
        Assumption second = cyclic.getAssumption();
        assertTrue(second.isValid());
        cyclic.invalidate(2);
        assertFalse(second.isValid());
        assertSame(Assumption.NEVER_VALID, cyclic.getAssumption());
    }

    @Test
    public void testChangingLimit() {
        LazyCyclicAssumption cyclic = new LazyCyclicAssumption("test");
        cyclic.invalidate(10);
        Assumption first = cyclic.getAssumption();
        cyclic.invalidate(3);
        assertFalse(first.isValid());
        assertTrue(cyclic.getAssumption().isValid());
        cyclic.invalidate(2);
        assertSame(Assumption.NEVER_VALID, cyclic.getAssumption());
    }

    @Test
    public void testNonPositiveLimit() {
        for (int limit : new int[]{0, -1, Integer.MIN_VALUE}) {
            LazyCyclicAssumption cyclic = new LazyCyclicAssumption("test");
            Assumption first = cyclic.getAssumption();
            cyclic.invalidate(limit);
            assertFalse(first.isValid());
            assertSame(Assumption.NEVER_VALID, cyclic.getAssumption());
            LazyCyclicAssumption unused = new LazyCyclicAssumption("unused");
            unused.invalidate(limit);
            assertSame(Assumption.NEVER_VALID, unused.getAssumption());
        }
    }

}
