/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.math.BigInteger;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.theories.Theory;

import com.oracle.graal.python.util.OverflowException;

public class BigIntegerUtilsTests {
    @Test
    public void smallInts() {
        checkInt(-2);
        checkInt(-2);
        checkInt(-1);
        checkInt(0);
        checkInt(1);
        checkInt(2);
    }

    @Test
    public void intLongBoundary() {
        checkLong(Integer.MIN_VALUE - 1L);
        checkIntOverflow(Integer.MIN_VALUE - 1L);
        checkInt(Integer.MIN_VALUE);
        checkInt(Integer.MIN_VALUE + 1);

        checkInt(Integer.MAX_VALUE - 1);
        checkInt(Integer.MAX_VALUE);
        checkLong(Integer.MAX_VALUE + 1L);
        checkIntOverflow(Integer.MAX_VALUE + 1L);
    }

    @Theory
    @Test
    public void long32bitBoundary() {
        checkLong(-0x100000001L);
        checkIntOverflow(-0x100000001L);
        checkLong(-0x100000000L);
        checkIntOverflow(-0x100000000L);
        checkLong(-0xFFFFFFFFL);
        checkIntOverflow(-0xFFFFFFFFL);

        checkLong(0xFFFFFFFFL);
        checkIntOverflow(0xFFFFFFFFL);
        checkLong(0x100000000L);
        checkIntOverflow(0x100000000L);
        checkLong(0x100000001L);
        checkIntOverflow(0x100000001L);
    }

    @Test
    public void longBoundary() {
        checkLongOverflow(Long.MIN_VALUE, -1);
        checkLong(Long.MIN_VALUE);
        checkLong(Long.MIN_VALUE + 1L);

        checkLong(Long.MAX_VALUE - 1L);
        checkLong(Long.MAX_VALUE);
        checkLongOverflow(Long.MAX_VALUE, 1);

        checkLongOverflow(Long.MAX_VALUE, Long.MAX_VALUE);
        checkLongOverflow(Long.MIN_VALUE, Long.MIN_VALUE);
    }

    private static void checkInt(int value) {
        try {
            Assert.assertEquals(value, BigIntegerUtils.intValueExact(BigInteger.valueOf(value)));
        } catch (OverflowException e) {
            Assert.fail("intValueExact: unexpected overflow");
        }
    }

    private static void checkIntOverflow(long value) {
        try {
            BigIntegerUtils.intValueExact(BigInteger.valueOf(value));
            Assert.fail("intValueExact should overflow for " + value);
        } catch (OverflowException e) {
            // nop
        }
    }

    private static void checkLong(long value) {
        try {
            Assert.assertEquals(value, BigIntegerUtils.longValueExact(BigInteger.valueOf(value)));
        } catch (OverflowException e) {
            Assert.fail("intValueExact: unexpected overflow");
        }
    }

    private static void checkLongOverflow(long value1, long value2) {
        try {
            BigInteger value = BigInteger.valueOf(value1).add(BigInteger.valueOf(value2));
            BigIntegerUtils.longValueExact(value);
            Assert.fail("intValueExact should overflow for " + value);
        } catch (OverflowException e) {
            // nop
        }
    }
}
