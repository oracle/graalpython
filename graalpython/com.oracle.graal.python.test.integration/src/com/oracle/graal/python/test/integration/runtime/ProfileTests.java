/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.test.integration.runtime;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.graal.python.test.integration.PythonTests;

public class ProfileTests {

    @Test
    public void profileYield() {
        String source = "import sys\n" +
                        "def f(frame, event, arg): print(frame, event, arg)\n" +
                        "def fg(): yield 42\n" +
                        "g = fg()\n" +
                        "sys.setprofile(f)\n" +
                        "for i in g: pass";
        assertPrints("fg> call None\n" +
                        "fg> return 42\n" +
                        "fg> call None\n" +
                        "fg> return None\n" +
                        "<module>> return None\n", source);
    }

    @Test
    public void profileException() {
        String source = "import sys\n" +
                        "def f(frame, event, arg): print(frame, event, arg)\n" +
                        "sys.setprofile(f)\n" +
                        "try:\n" +
                        "  max(0)\n" +
                        "except Exception:\n" +
                        "  pass\n";
        assertPrints("<module>> c_call <built-in function max>\n" +
                        "<module>> c_exception <built-in function max>\n" +
                        "<module>> return None\n", source);
    }

    private static void assertPrints(String expected, String code) {
        final ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
        final PrintStream printStream = new PrintStream(byteArray);
        PythonTests.runScript(new String[0], code, printStream, System.err);
        String result = byteArray.toString().replaceAll("\r\n", "\n");
        Assert.assertEquals(expected, result.replaceAll(".*code ", ""));
    }
}
