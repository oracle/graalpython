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
package com.oracle.graal.python.test.parser;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.source.Source;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class ParseWithArgumentsTests extends ParserTestBase {

    @Test
    public void testSimpple01() throws Exception {
        Source source = createSource("arg1");
        CallTarget target = context.getEnv().parsePublic(source, "arg1");
        assertEquals(66, target.call(66));
        assertEquals(false, target.call(false));
        assertEquals("Ahoj", target.call("Ahoj"));
    }

    @Test
    public void testSimpple02() throws Exception {
        Source source = createSource("arg1 + arg2");
        CallTarget target = context.getEnv().parsePublic(source, "arg1", "arg2");
        assertEquals(11, target.call(5, 6));
        assertEquals("AhojHello", target.call("Ahoj", "Hello"));
    }

    @Test
    public void testMoreStatements() throws Exception {
        Source source = createSource("tmp = arg1 + arg2\n" + "2 * tmp");
        CallTarget target = context.getEnv().parsePublic(source, "arg1", "arg2");
        assertEquals(22, target.call(5, 6));
        assertEquals("AhojHelloAhojHello", target.call("Ahoj", "Hello"));
    }

    @Test
    public void testReturnStatement() throws Exception {
        Source source = createSource("tmp = arg1 + arg2\n" + "return 2 * tmp");
        CallTarget target = context.getEnv().parsePublic(source, "arg1", "arg2");
        assertEquals(22, target.call(5, 6));
        assertEquals("AhojHelloAhojHello", target.call("Ahoj", "Hello"));
    }

    @Test
    public void testWitouthReturn() throws Exception {
        Source source = createSource("tmp = arg1 + arg2\n");
        CallTarget target = context.getEnv().parsePublic(source, "arg1", "arg2");
        assertEquals(PNone.NONE, target.call(5, 6));
        assertEquals(PNone.NONE, target.call("Ahoj", "Hello"));
    }

    @Test
    public void testCompareWithAndWithouthArguments() throws Exception {
        Source source = createSource("22");
        CallTarget targetWithout = context.getEnv().parsePublic(source);
        CallTarget targetWith = context.getEnv().parsePublic(source, "arg1");
        assertEquals(22, targetWithout.call());
        assertEquals(22, targetWith.call("Hello"));
    }

    @Test
    public void testPrintBuiltin() throws Exception {
        Source source = createSource("print('Ahoj')");
        CallTarget target = context.getEnv().parsePublic(source, "arg1");
        assertEquals(PNone.NONE, target.call(5));
    }

    @Test
    public void testObjectMethods() throws Exception {
        Source source = createSource("arg1.upper()");
        CallTarget target = context.getEnv().parsePublic(source, "arg1");
        assertEquals("AHOJ", target.call("ahoj"));
    }

    @Test
    public void testAbsGlobal() throws Exception {
        Source source = createSource("abs(arg1)");
        CallTarget target = context.getEnv().parsePublic(source, "arg1");
        assertEquals(10, target.call(-10));
    }

    private static Source createSource(String code) {
        return Source.newBuilder(PythonLanguage.ID, code, "test.py").build();
    }
}
