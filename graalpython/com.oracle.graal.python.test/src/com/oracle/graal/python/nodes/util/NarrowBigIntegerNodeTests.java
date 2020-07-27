/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.python.nodes.util;

import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.test.PythonTests;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;

public class NarrowBigIntegerNodeTests {

    @Before
    public void setUp() {
        PythonTests.enterContext();
    }

    private static NarrowBigIntegerNode narrow = NarrowBigIntegerNodeGen.getUncached();

    @Test
    public void smallInts() {
        expectInt(-2);
        expectInt(-1);
        expectInt(0);
        expectInt(1);
        expectInt(2);
    }

    @Test
    public void intLongBoundary() {
        expectLong(Integer.MIN_VALUE - 1L);
        expectInt(Integer.MIN_VALUE);
        expectInt(Integer.MIN_VALUE + 1);

        expectInt(Integer.MAX_VALUE - 1);
        expectInt(Integer.MAX_VALUE);
        expectLong(Integer.MAX_VALUE + 1L);
    }

    @Test
    public void long32bitBoundary() {
        expectLong(-0x100000001L);
        expectLong(-0x100000000L);
        expectLong(-0xFFFFFFFFL);

        expectLong(0xFFFFFFFFL);
        expectLong(0x100000000L);
        expectLong(0x100000001L);
    }

    @Test
    public void longPIntBoundary() {
        expectPInt(BigInteger.valueOf(Long.MIN_VALUE).subtract(BigInteger.ONE));
        expectLong(Long.MIN_VALUE);
        expectLong(Long.MIN_VALUE + 1L);

        expectLong(Long.MAX_VALUE - 1L);
        expectLong(Long.MAX_VALUE);
        expectPInt(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE));
    }

    private static void expectInt(int expected) {
        Object actual = narrow.execute(BigInteger.valueOf(expected));
        Assert.assertTrue(actual instanceof Integer);
        Assert.assertEquals(expected, (int) actual);
    }

    private static void expectLong(long expected) {
        Object actual = narrow.execute(BigInteger.valueOf(expected));
        Assert.assertTrue(actual instanceof Long);
        Assert.assertEquals(expected, (long) actual);
    }

    private static void expectPInt(BigInteger expected) {
        Object actual = narrow.execute(expected);
        Assert.assertTrue(actual instanceof PInt);
        Assert.assertEquals(expected, ((PInt) actual).getValue());
    }
}
