/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.test.PythonTests;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

public class CastToJavaUnsignedLongNodeTests {

    @Before
    public void setUp() {
        PythonTests.enterContext();
    }

    private static CastToJavaUnsignedLongNode castNode = CastToJavaUnsignedLongNode.getUncached();
    private static PythonObjectFactory factory = PythonObjectFactory.getUncached();

    @Test
    public void positiveInt() {
        Assert.assertEquals(0, castNode.execute(0));
        Assert.assertEquals(16, castNode.execute(16));
        Assert.assertEquals(Integer.MAX_VALUE, castNode.execute(Integer.MAX_VALUE));
        Assert.assertEquals(42, castNode.execute(Integer.valueOf(42)));
    }

    @Test
    public void negativeInt() {
        expect(OverflowError, () -> castNode.execute(-1));
        expect(OverflowError, () -> castNode.execute(Integer.MIN_VALUE));
    }

    @Test
    public void positiveLong() {
        Assert.assertEquals(0, castNode.execute(0L));
        Assert.assertEquals(0x80000000L, castNode.execute(0x80000000L));
        Assert.assertEquals(Long.MAX_VALUE, castNode.execute(Long.MAX_VALUE));
        Assert.assertEquals(1234567890123L, castNode.execute(Long.valueOf(1234567890123L)));
    }

    @Test
    public void negativeLong() {
        expect(OverflowError, () -> castNode.execute(-1L));
        expect(OverflowError, () -> castNode.execute(Long.MIN_VALUE));
    }

    @Test
    public void positiveBigInt() {
        Assert.assertEquals(0, castNode.execute(makePInt(0)));
        Assert.assertEquals(1234567890123L, castNode.execute(makePInt(1234567890123L)));
        Assert.assertEquals(Long.MAX_VALUE, castNode.execute(makePInt(Long.MAX_VALUE)));
        Assert.assertEquals(0x8000000000000000L, castNode.execute(makePInt("8000000000000000")));
        Assert.assertEquals(0xFFFFFFFFFFFFFFFFL, castNode.execute(makePInt("FFFFFFFFFFFFFFFF")));
    }

    @Test
    public void negativeBigInt() {
        expect(OverflowError, () -> castNode.execute(makePInt(-1)));
        expect(OverflowError, () -> castNode.execute(makePInt("-10000000000000000")));
    }

    @Test
    public void largeBigInt() {
        expect(OverflowError, () -> castNode.execute(makePInt("10000000000000000")));
        expect(OverflowError, () -> castNode.execute(makePInt("10000000000000001")));
    }

    @Test
    public void nonInteger() {
        expect(TypeError, () -> castNode.execute("123"));
        expect(TypeError, () -> castNode.execute(2.7));
    }

    private static PInt makePInt(long l) {
        return factory.createInt(BigInteger.valueOf(l));
    }

    private static PInt makePInt(String hexString) {
        return factory.createInt(new BigInteger(hexString, 16));
    }

    private static void expect(PythonBuiltinClassType errorType, Runnable test) {
        try {
            test.run();
            Assert.fail("Expected " + errorType.getName());
        } catch (PException e) {
            e.expect(errorType, IsBuiltinClassProfile.getUncached());
        }
    }
}
