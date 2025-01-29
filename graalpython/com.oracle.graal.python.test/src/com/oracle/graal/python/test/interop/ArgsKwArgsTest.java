/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.test.interop;

import com.oracle.graal.python.test.PythonTests;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.python.embedding.KeywordArguments;
import org.graalvm.python.embedding.PositionalArguments;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ArgsKwArgsTest extends PythonTests {
    private Context context;

    @Before
    public void setUpTest() {
        Context.Builder builder = Context.newBuilder();
        builder.allowExperimentalOptions(true);
        builder.allowAllAccess(true);
        context = builder.build();
    }

    @After
    public void tearDown() {
        context.close();
    }

    private Value run(String evalString) {
        return context.eval("python", evalString);
    }

    @Test
    public void testPositionalArgs01() {
        // @formatter:off
        Value module = run("""
                def sum(*args):
                    result = 0
                    for arg in args:
                        result = result + arg
                    return result;
                """
        );

        assertEquals(0, module.invokeMember("sum").asInt());
        assertEquals(22, module.invokeMember("sum", 22).asInt());
        assertEquals(60, module.invokeMember("sum", 10, 20, 30).asInt());
        assertEquals(6, module.invokeMember("sum", PositionalArguments.of(1, 2, 3)).asInt());

        assertEquals(0, module.invokeMember("sum", PositionalArguments.of()).asInt());
        assertEquals(0, module.invokeMember("sum", KeywordArguments.from(Map.of())).asInt());

        PolyglotException pe = assertThrows(PolyglotException.class, () -> {
            assertEquals(0, module.invokeMember("sum", KeywordArguments.of("one", 1)).asInt());
        });
        assertEquals("TypeError: sum() got an unexpected keyword argument 'one'", pe.getMessage());

    }

    @Test
    public void testPositionalArgs02() {
        // @formatter:off
        Value module = run("""
                def text(a, *args):
                    result = f'{a=},'
                    index = 0
                    for arg in args:
                        result = result + f'args[{index}]={arg},'
                        index += 1
                    return result;
                """
        );

        assertEquals("a=0,", module.invokeMember("text", 0).asString());
        assertEquals("a=22,args[0]=33,", module.invokeMember("text", 22, 33).asString());
        assertEquals("a='hello',args[0]=ahoj,args[1]=cau,", module.invokeMember("text", "hello", "ahoj", "cau").asString());
        assertEquals("a='6',args[0]=1,args[1]=2,args[2]=3,", module.invokeMember("text", "6",  PositionalArguments.of(1, 2, 3)).asString());
        assertEquals("a=1,args[0]=2,args[1]=3,", module.invokeMember("text", PositionalArguments.of(1, 2, 3)).asString());
        assertEquals("a=1,", module.invokeMember("text", PositionalArguments.of(1)).asString());

        assertEquals("a=1,", module.invokeMember("text", KeywordArguments.of("a", 1)).asString());

        PolyglotException pe = assertThrows(PolyglotException.class, () -> {
            module.invokeMember("text", KeywordArguments.of("a", 1, "b", 2));
        });
        assertEquals("TypeError: text() got an unexpected keyword argument 'b'", pe.getMessage());
    }

    @Test
    public void testPositionalArgs03() {
        // @formatter:off
        Value module = run("""
                def text(a,b=44, *args):
                    result = f'{a=},{b=},'
                    index = 0
                    for arg in args:
                        result = result + f'args[{index}]={arg},'
                        index += 1
                    return result;
                """
        );

        assertEquals("a=0,b=44,", module.invokeMember("text", 0).asString());
        assertEquals("a=22,b=33,", module.invokeMember("text", 22, 33).asString());
        assertEquals("a='hello',b='ahoj',args[0]=cau,", module.invokeMember("text", "hello", "ahoj", "cau").asString());
        assertEquals("a='6',b=1,args[0]=2,args[1]=3,", module.invokeMember("text", "6",  PositionalArguments.of(1, 2, 3)).asString());
        assertEquals("a=1,b=44,", module.invokeMember("text", PositionalArguments.of(1)).asString());
        assertEquals("a=1,b=2,", module.invokeMember("text", PositionalArguments.of(1, 2)).asString());
        assertEquals("a=1,b=2,args[0]=3,", module.invokeMember("text", PositionalArguments.of(1, 2, 3)).asString());
        assertEquals("a='a',b='b',args[0]=1,args[1]=2,args[2]=3,", module.invokeMember("text", "a", "b", PositionalArguments.of(1, 2, 3)).asString());
    }

    private static String assertAllKeysInText(String text, Map<String, Object> kwArgs) {
        String rest = text;
        for (Map.Entry<String, Object> entry : kwArgs.entrySet()) {
            String key = entry.getKey();
            Object val = entry.getValue();
            String keyVal = "[" + key + ":" + val.toString() + "],";
            assertTrue("The string \"" + keyVal + "\" was not found in \"" + text + "\"", text.contains(keyVal));
            rest = rest.replace(keyVal, "");
        }
        return rest;
    }

    @Test
    public void testKwArgs01() {
        // @formatter:off
        Value module = run("""
                def text(**kwArgs):
                    result = ''
                    for key, value in kwArgs.items():
                       result = result + f'[{key}:{str(value)}],'
                    return result
                """
        );

        assertEquals("", module.invokeMember("text").asString());
        assertEquals("", module.invokeMember("text", KeywordArguments.from(Map.of())).asString());

        Map<String, Object> kwargsMap = Map.of("key1", 1);
        String remaining = assertAllKeysInText(module.invokeMember("text", KeywordArguments.from(kwargsMap)).asString(), kwargsMap);
        assertTrue(remaining.isEmpty());

        kwargsMap = Map.of("key1", 1, "key2", 22);
        remaining = assertAllKeysInText(module.invokeMember("text", KeywordArguments.from(kwargsMap)).asString(), kwargsMap);
        assertTrue(remaining.isEmpty());

        assertTrue(module.invokeMember("text", PositionalArguments.of()).asString().isEmpty());

        PolyglotException pe = assertThrows(PolyglotException.class, () -> {
            module.invokeMember("text", PositionalArguments.of(44)).asString();
        });
        assertEquals("TypeError: text() takes 0 positional arguments but 1 was given", pe.getMessage());
    }

    @Test
    public void testKwArgs02() {
        // @formatter:off
        Value module = run("""
                def text(*,named1, **kwArgs):
                    result = f'[named1:{str(named1)}],'
                    for key, value in kwArgs.items():
                       result = result + f'[{key}:{str(value)}],'
                    return result
                """
        );

        Map<String, Object> kwargsMap = Map.of("named1", 1);
        String remaining = assertAllKeysInText(module.invokeMember("text", KeywordArguments.from(kwargsMap)).asString(), kwargsMap);
        assertTrue(remaining.isEmpty());

        kwargsMap = Map.of("named1", 1, "named2", 2);
        remaining = assertAllKeysInText(module.invokeMember("text", KeywordArguments.from(kwargsMap)).asString(), kwargsMap);
        assertTrue(remaining.isEmpty());

        PolyglotException pe = assertThrows(PolyglotException.class, () -> {
            module.invokeMember("text").asString();
        });
        assertEquals("TypeError: text() missing 1 required keyword-only argument: 'named1'", pe.getMessage());

        pe = assertThrows(PolyglotException.class, () -> {
            module.invokeMember("text", KeywordArguments.from(Map.of())).asString();
        });
        assertEquals("TypeError: text() missing 1 required keyword-only argument: 'named1'", pe.getMessage());

        pe = assertThrows(PolyglotException.class, () -> {
            module.invokeMember("text", KeywordArguments.of("named2", 10)).asString();
        });
        assertEquals("TypeError: text() missing 1 required keyword-only argument: 'named1'", pe.getMessage());

        pe = assertThrows(PolyglotException.class, () -> {
            module.invokeMember("text", PositionalArguments.of()).asString();
        });
        assertEquals("TypeError: text() missing 1 required keyword-only argument: 'named1'", pe.getMessage());

        pe = assertThrows(PolyglotException.class, () -> {
            module.invokeMember("text", PositionalArguments.of(10)).asString();
        });
        assertEquals("TypeError: text() takes 0 positional arguments but 1 was given", pe.getMessage());
    }

    @Test
    public void testKwArgs03() {
        // @formatter:off
        Value module = run("""
                def text(*,named1, named2=44,  **kwArgs):
                    result = f'[named1:{str(named1)}],[named2:{str(named2)}],'
                    for key, value in kwArgs.items():
                       result = result + f'[{key}:{str(value)}],'
                    return result
                """
        );

        Map<String, Object> kwargsMap = Map.of("named1", 1);
        String  remaining = assertAllKeysInText(module.invokeMember("text", KeywordArguments.from(kwargsMap)).asString(), kwargsMap);
        assertEquals("[named2:44],", remaining);

        kwargsMap = Map.of("named1", 1, "named2", 2);
        remaining = assertAllKeysInText(module.invokeMember("text", KeywordArguments.from(kwargsMap)).asString(), kwargsMap);
        assertTrue(remaining.isEmpty());
    }

    @Test
    public void testKwArgs04() {
        // @formatter:off
        Value module = run("""
                def text(*,named1, named2=44):
                    result = f'[named1:{str(named1)}],[named2:{str(named2)}],'
                    return result
                """
        );

        Map<String, Object> kwargsMap = Map.of("named1", 1);
        String  remaining = module.invokeMember("text", KeywordArguments.from(kwargsMap)).asString();
        assertEquals("[named1:1],[named2:44],", remaining);

        kwargsMap = Map.of("named1", 1, "named2", 2);
        remaining = module.invokeMember("text", KeywordArguments.from(kwargsMap)).asString();
        assertEquals("[named1:1],[named2:2],", remaining);

        PolyglotException pe = assertThrows(PolyglotException.class, () -> {
            module.invokeMember("text",
                module.invokeMember("text", KeywordArguments.of("named1", 1, "named2", 2, "named3", 3))).asString();
        });
        assertEquals("TypeError: text() got an unexpected keyword argument 'named3'", pe.getMessage());
    }
}
