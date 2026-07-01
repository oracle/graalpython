/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.test.compiler;

import com.oracle.graal.python.test.PythonTests;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SuperInstructionsTests extends PythonTests {
    @Test
    public void testIsNone() {
        assertCompilesAndRuns(
                        "x = None\n" +
                                        "if x is None:\n" +
                                        "    return True\n" +
                                        "return False",
                        "c.IsNone",
                        true);
    }

    @Test
    public void testIsNotNone() {
        assertCompilesAndRuns(
                        "x = object()\n" +
                                        "if x is not None:\n" +
                                        "    return False\n" +
                                        "return True",
                        "c.IsNotNone",
                        false);
    }

    @Test
    public void testChainedComparisonsIsNone() {
        assertCompilesAndRuns(
                        "x = None\n" +
                                        "return (42 == 42) and (x is None) and (1 < 2)",
                        "c.IsNone",
                        true);
    }

    @Test
    public void testChainedComparisonsIsNotNone() {
        assertCompilesAndRuns(
                        "x = object()\n" +
                                        "return (42 == 42) and (x is not None) and (1 < 2)",
                        "c.IsNotNone",
                        true);
    }

    @Test
    public void testIsNoneWithForeignNull() {
        assertCompilesAndRuns(
                        "if x is None:\n" +
                                        "    return True\n" +
                                        "return False",
                        "c.IsNone",
                        true,
                        (Object) null);
    }

    @Test
    public void testIsNotNoneWithForeignNull() {
        assertCompilesAndRuns(
                        "if x is not None:\n" +
                                        "    return False\n" +
                                        "return True",
                        "c.IsNotNone",
                        true,
                        (Object) null);
    }

    private static void assertCompilesAndRuns(String functionBody, String expectedInstruction, boolean expectedResult) {
        assertCompilesAndRuns(functionBody, expectedInstruction, expectedResult, new Object[0]);
    }

    private static void assertCompilesAndRuns(String functionBody, String expectedInstruction, boolean expectedResult, Object... optionalArgValue) {
        assert optionalArgValue.length <= 1;
        String sourceText = "def f(" + (optionalArgValue.length == 0 ? "" : "x") + "):\n" + "    " + functionBody.replace("\n", "\n    ");
        try (Context context = Context.newBuilder("python").allowExperimentalOptions(true).build()) {
            context.eval("python", sourceText);
            Value function = context.getBindings("python").getMember("f");
            Value result = function.execute(optionalArgValue);
            assertEquals(expectedResult, result.asBoolean());
            String disassembly = context.eval("python", "import __graalpython__\n__graalpython__.dis(f)").asString();
            assertTrue(disassembly, disassembly.contains(expectedInstruction));
        }
    }

}
