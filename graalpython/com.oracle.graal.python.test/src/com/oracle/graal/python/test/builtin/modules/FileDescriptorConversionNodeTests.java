/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.runtime.PosixConstants.AT_FDCWD;

import java.math.BigInteger;

import org.graalvm.polyglot.Context;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oracle.graal.python.builtins.modules.PosixModuleBuiltinsFactory;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.test.PythonTests;

public class FileDescriptorConversionNodeTests extends ConversionNodeTests {

    @Before
    public void setUp() {
        PythonTests.enterContext();
    }

    @After
    public void tearDown() {
        PythonTests.closeContext();
    }

    @Test
    public void none() {
        expectPythonMessage("TypeError: argument must be an int, or have a fileno() method.", () -> {
            call(PNone.NONE);
            Assert.assertEquals(AT_FDCWD.value, call(PNone.NO_VALUE));
        });
    }

    @Test
    public void fdBool() {
        Assert.assertEquals(0, call(false));
        Assert.assertEquals(1, call(true));
    }

    @Test
    public void fdInt() {
        Assert.assertEquals(42, call(42));
    }

    @Test
    public void longFitsInt() {
        Assert.assertEquals(42, call(42L));
    }

    @Test
    public void longTooBig() {
        expectPythonMessage("OverflowError: Python int too large to convert to Java int", () -> {
            call(1L << 40);
        });
    }

    @Test
    public void longTooSmall() {
        expectPythonMessage("OverflowError: Python int too large to convert to Java int", () -> {
            call(-1L << 40);
        });
    }

    @Test
    public void pintFitsInt() {
        Assert.assertEquals(42, call(factory().createInt(BigInteger.valueOf(42))));
    }

    @Test
    public void pintTooBig() {
        expectPythonMessage("OverflowError: Python int too large to convert to Java int", () -> {
            call(factory().createInt(BigInteger.ONE.shiftLeft(100)));
        });
    }

    @Test
    public void pintTooSmall() {
        expectPythonMessage("OverflowError: Python int too large to convert to Java int", () -> {
            call(factory().createInt(BigInteger.ONE.shiftLeft(100).negate()));
        });
    }

    @Test
    public void indexNotUsed() {
        expectPythonMessage("TypeError: argument must be an int, or have a fileno() method.", () -> {
            call(evalValue("class C:\n  def __index__(self):\n    return 42\nC()"));
        });
    }

    @Test
    public void filenoOk() {
        Assert.assertEquals(42, call(evalValue("class C:\n  def fileno(self):\n    return 42\nC()")));
    }

    @Test
    public void filenoWrongType() {
        expectPythonMessage("TypeError: fileno() returned a non-integer", () -> {
            call(evalValue("class C:\n  def fileno(self):\n    return 3.14\nC()"));
        });
    }

    @Test
    public void filenoTooBig() {
        expectPythonMessage("OverflowError: Python int too large to convert to Java int", () -> {
            call(evalValue("class C:\n  def fileno(self):\n    return 1 << 40\nC()"));
        });
    }

    @Test
    public void unsupportedType1() {
        expectPythonMessage("TypeError: argument must be an int, or have a fileno() method.", () -> {
            call(3.14);
        });
    }

    protected static int call(Object arg) {
        Object result = call(arg, PosixModuleBuiltinsFactory.FileDescriptorConversionNodeGen.create());
        MatcherAssert.assertThat(result, CoreMatchers.instanceOf(Integer.class));
        return (int) result;
    }

    private static PythonObjectFactory factory() {
        return PythonObjectFactory.getUncached();
    }

    private static Object evalValue(String source) {
        return PythonContext.get(null).getEnv().asGuestValue(Context.getCurrent().eval("python", source));
    }
}
