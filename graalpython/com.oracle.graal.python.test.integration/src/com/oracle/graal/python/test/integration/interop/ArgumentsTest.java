/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.test.integration.interop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import com.oracle.graal.python.test.integration.PythonTests;
import java.io.ByteArrayOutputStream;

import org.graalvm.python.embedding.KeywordArguments;
import org.graalvm.python.embedding.PositionalArguments;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ArgumentsTest extends PythonTests {

    private ByteArrayOutputStream out;
    private Context context;
    private ByteArrayOutputStream err;

    @Before
    public void setUpTest() {
        out = new ByteArrayOutputStream();
        err = new ByteArrayOutputStream();
        Context.Builder builder = Context.newBuilder();
        builder.allowExperimentalOptions(true);
        builder.allowAllAccess(true);
        builder.out(out);
        builder.err(err);
        context = builder.build();
    }

    @After
    public void tearDown() {
        context.close();
    }

    public interface TestPositionalArgsLong {
        long fn(PositionalArguments args);
    }

    @Test
    public void testPositionalArgsLength() {
        String source = """
                        def fn (*args)->int:
                            return len(args)
                        """;

        TestPositionalArgsLong module = context.eval(Source.create("python", source)).as(TestPositionalArgsLong.class);
        assertEquals(1, module.fn(PositionalArguments.of(22)));
        assertEquals(2, module.fn(PositionalArguments.of(1, null)));
        assertEquals(2, module.fn(PositionalArguments.of(null, 2)));
        assertEquals(5, module.fn(PositionalArguments.of(1, 2, 3, 4, 5)));
        assertEquals(0, module.fn(PositionalArguments.of()));

        assertEquals(1, module.fn(PositionalArguments.from(List.of(2))));
        assertEquals(2, module.fn(PositionalArguments.from(List.of(2, 3))));

        assertEquals(3, module.fn(PositionalArguments.of(new Object[]{2, 3, 4})));
        assertEquals(1, module.fn(PositionalArguments.of(new Object[]{null})));
    }

    @Test
    public void testPositionalArgsWithNoneValue() {
        String source = """
                        def fn (*args):
                            result = 0;
                            for arg in args:
                                if arg is None:
                                    result = result + 1;
                            return result
                        """;

        TestPositionalArgsLong module = context.eval(Source.create("python", source)).as(TestPositionalArgsLong.class);
        assertEquals(0, module.fn(PositionalArguments.of(22)));
        assertEquals(1, module.fn(PositionalArguments.of(1, null)));
        assertEquals(1, module.fn(PositionalArguments.of(null, 2)));
        assertEquals(3, module.fn(PositionalArguments.of(null, null, null)));
        assertEquals(1, module.fn(PositionalArguments.of(new Object[]{null})));

        Value none = context.eval(Source.create("python", "a = None")).getMember("a");
        assertEquals(1, module.fn(PositionalArguments.of(none)));
        assertEquals(3, module.fn(PositionalArguments.of(none, null, none)));
        assertEquals(2, module.fn(PositionalArguments.of(new Object[]{none, null})));
    }

    public interface TestPositionalArgs01 {
        String fn(Object a, Object b, PositionalArguments args);
    }

    @Test
    public void testPositionalArgs01() {
        String source = """
                        def fn (a, b, *args):
                            result = str(a) + str(b);
                            for arg in args:
                                result = result + str(arg);
                            return result
                        """;
        TestPositionalArgs01 module = context.eval(Source.create("python", source)).as(TestPositionalArgs01.class);
        assertEquals("12", module.fn(1, 2, PositionalArguments.of()));
        assertEquals("123", module.fn(1, 2, PositionalArguments.of(3)));
        assertEquals("123Ahoj", module.fn(1, 2, PositionalArguments.of(3, "Ahoj")));
        assertEquals("123AhojTrue", module.fn(1, 2, PositionalArguments.of(3, "Ahoj", true)));
        assertEquals("123AhojTrueNone", module.fn(1, 2, PositionalArguments.of(3, "Ahoj", true, null)));
    }

    public interface TestPositionalArgs02 {
        String fn(Object a);

        String fn(Object a, PositionalArguments args);

        String fn(PositionalArguments args);

        String fn(Object a, Object b);

        String fn(Object a, Object b, PositionalArguments args);
    }

    @Test
    public void testPositionalArgs02() {
        String source = """
                        def fn (a, b="correct", *args):
                            result = str(a) + str(b)
                            for arg in args:
                                result = result + str(arg)
                            return result
                        """;
        TestPositionalArgs02 module = context.eval(Source.create("python", source)).as(TestPositionalArgs02.class);
        assertEquals("1correct", module.fn(1));
        assertEquals("12", module.fn(1, 2));
        assertEquals("1only one", module.fn(1, PositionalArguments.of("only one")));
        assertEquals("1only oneand two", module.fn(1, PositionalArguments.of("only one", "and two")));
        assertEquals("12", module.fn(1, 2, PositionalArguments.of()));
        assertEquals("123", module.fn(1, 2, PositionalArguments.of(3)));
        assertEquals("123Ahoj", module.fn(1, 2, PositionalArguments.of(3, "Ahoj")));
        assertEquals("123AhojTrue", module.fn(1, 2, PositionalArguments.of(3, "Ahoj", true)));
        assertEquals("123AhojTrueNone", module.fn(1, 2, PositionalArguments.of(3, "Ahoj", true, null)));
    }

    public interface TestKeywordArgs01 {
        String fn();

        String fn(KeywordArguments kwArgs);
    }

    @Test
    public void testKeywordArgs01() {
        String source = """
                        def fn (**kwArgs):
                            result = ''
                            for key, value in kwArgs.items():
                                result = result + f'[{key}:{str(value)}],'
                            return result
                        """;
        TestKeywordArgs01 module = context.eval(Source.create("python", source)).as(TestKeywordArgs01.class);
        assertEquals("", module.fn());
        assertEquals("", module.fn(KeywordArguments.from(new HashMap<>())));
        assertEquals("[jedna:1],", module.fn(KeywordArguments.of("jedna", 1)));

        Value none = context.eval(Source.create("python", "a = None")).getMember("a");
        Map<String, Object> keyArgs = new HashMap<>();
        keyArgs.put("jedna", 1);
        keyArgs.put("true", true);
        keyArgs.put("null", none);

        String result = module.fn(KeywordArguments.from(keyArgs));
        assertTrue(result.contains("[true:True],"));
        assertTrue(result.contains("[jedna:1],"));
        assertTrue(result.contains("[null:None],"));
    }
}
