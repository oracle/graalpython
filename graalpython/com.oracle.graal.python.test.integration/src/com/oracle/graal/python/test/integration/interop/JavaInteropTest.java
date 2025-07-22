/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Context.Builder;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyHashMap;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.graal.python.test.integration.PythonTests;

@RunWith(Enclosed.class)
public class JavaInteropTest {
    public static class GeneralInterop extends PythonTests {
        private static final String INCOMPLETE_SOURCE = "class A:";
        private ByteArrayOutputStream out;
        private Context context;
        private ByteArrayOutputStream err;

        @Before
        public void setUpTest() {
            out = new ByteArrayOutputStream();
            err = new ByteArrayOutputStream();
            Builder builder = Context.newBuilder();
            builder.engine(Engine.newBuilder("python").option("engine.WarnInterpreterOnly", "false").build());
            builder.allowExperimentalOptions(true);
            builder.allowAllAccess(true);
            builder.out(out);
            builder.err(err);
            context = builder.build();
        }

        @After
        public void tearDown() {
            context.close();
            context.getEngine().close();
        }

        @Test
        public void evalFailsOnError() {
            boolean didFail = false;
            try {
                context.eval(Source.create("python", "invalid code"));
            } catch (Throwable t) {
                didFail = true;
            }
            assertTrue(didFail);
        }

        @Test
        public void evalNonInteractiveThrowsSyntaxError() throws IOException {
            try (Engine engine = Engine.create("python");
                            Context c = Context.newBuilder().engine(engine).allowExperimentalOptions(true).allowAllAccess(true).option("python.TerminalIsInteractive", "false").build()) {
                c.eval(Source.newBuilder("python", INCOMPLETE_SOURCE, "eval").interactive(false).build());
            } catch (PolyglotException t) {
                assertTrue(t.isSyntaxError());
                assertFalse(t.isIncompleteSource());
                return;
            }
            fail();
        }

        @Test
        public void evalNonInteractiveInInteractiveTerminalThrowsSyntaxError() throws IOException {
            try (Engine engine = Engine.create("python");
                            Context c = Context.newBuilder().engine(engine).allowExperimentalOptions(true).allowAllAccess(true).option("python.TerminalIsInteractive", "true").build()) {
                c.eval(Source.newBuilder("python", INCOMPLETE_SOURCE, "eval").interactive(false).build());
            } catch (PolyglotException t) {
                assertTrue(t.isSyntaxError());
                assertFalse(t.isIncompleteSource());
                return;
            }
            fail();
        }

        @Test
        public void evalInteractiveInNonInteractiveTerminalThrowsSyntaxError() throws IOException {
            try (Engine engine = Engine.create("python");
                            Context c = Context.newBuilder().engine(engine).allowExperimentalOptions(true).allowAllAccess(true).option("python.TerminalIsInteractive", "false").build()) {
                c.eval(Source.newBuilder("python", INCOMPLETE_SOURCE, "eval").interactive(true).build());
            } catch (PolyglotException t) {
                assertTrue(t.isSyntaxError());
                assertTrue(t.isIncompleteSource());
                return;
            }
            fail();
        }

        @Test
        public void evalInteractiveInInteractiveTerminalThrowsSyntaxError() throws IOException {
            try (Engine engine = Engine.create("python");
                            Context c = Context.newBuilder().engine(engine).allowExperimentalOptions(true).allowAllAccess(true).option("python.TerminalIsInteractive", "true").build()) {
                c.eval(Source.newBuilder("python", INCOMPLETE_SOURCE, "eval").interactive(true).build());
            } catch (PolyglotException t) {
                assertTrue(t.isSyntaxError());
                assertTrue(t.isIncompleteSource());
                return;
            }

            fail();
        }

        @Test
        public void importingJavaLangStringConvertsEagerly() {
            try (Engine engine = Engine.create("python"); Context c = Context.newBuilder().engine(engine).allowExperimentalOptions(true).allowAllAccess(true).build()) {
                c.getPolyglotBindings().putMember("b", "hello world");
                c.eval("python", "import polyglot; xyz = polyglot.import_value('b'); assert isinstance(xyz, str)");
                // should not fail
            }
        }

        @Test
        public void evalWithSyntaxErrorThrows() {
            try (Engine engine = Engine.create("python"); Context c = Context.newBuilder().engine(engine).build()) {
                c.eval("python", "var d=5/0");
            } catch (PolyglotException t) {
                assertTrue(t.isSyntaxError());
                assertFalse(t.isIncompleteSource());
                return;
            }

            fail();
        }

        @Test
        public void truffleMethodExport() {
            String source = "import polyglot\n" +
                            "@polyglot.export_value\n" +
                            "def foo():\n" +
                            "    print('Called')\n\n";
            Source script = Source.create("python", source);
            context.eval(script);
            Value main = context.getPolyglotBindings().getMember("foo");
            assertTrue(main.canExecute());
        }

        // From https://github.com/oracle/graalpython/issues/298
        @Test
        public void testHostException() {
            try {
                context.eval(createSource("import java; java.math.BigInteger.ONE.divide(java.math.BigInteger.ZERO)"));
                fail();
            } catch (PolyglotException e) {
                Assert.assertTrue(e.isHostException());
                Assert.assertTrue(e.asHostException() instanceof ArithmeticException);
                Assert.assertTrue(e.getMessage(), e.getMessage().contains("divide by zero"));
            }

            String outString = out.toString(StandardCharsets.UTF_8);
            String errString = err.toString(StandardCharsets.UTF_8);
            Assert.assertTrue(outString, outString.isEmpty());
            Assert.assertTrue(errString, errString.isEmpty());
        }

        @Test
        public void javaArraySet() {
            String source = "import java\n" +
                            "array = java.type(\"int[]\")(4)\n" +
                            "array[2] = 42\n" +
                            "print(array[2])\n\n";
            assertPrints("42\n", source);
        }

        @Test
        public void javaArrayBytes() {
            String source = "import java\n" +
                            "array = java.type(\"short[]\")(4)\n" +
                            "array[0] = 1\n" +
                            "array[1] = 2\n" +
                            "array[2] = 3\n" +
                            "array[3] = 4\n" +
                            "print(bytes(array))\n\n";
            assertPrints("b'\\x01\\x02\\x03\\x04'\n", source);
        }

        @Test
        public void javaNullIsNone() {
            String source = """
                            def is_none(x):
                                return x is None
                            is_none
                            """;
            Source script = Source.create("python", source);
            Value isNone = context.eval(script);
            assertTrue(isNone.execute((Object) null).asBoolean());
        }

        @Test
        public void testPassingFloats() throws UnsupportedEncodingException {
            String source = "import polyglot\n" +
                            "@polyglot.export_value\n" +
                            "def foo(x, y):\n" +
                            "    print(x * y)\n\n";
            Source script = Source.create("python", source);
            context.eval(script);
            Value main = context.getPolyglotBindings().getMember("foo");
            main.execute((float) 1.0, (float) 2.0);
            assertEquals("2.0\n", out.toString("UTF-8"));
        }

        @Test
        public void testPassingBigIntegers() throws UnsupportedEncodingException {
            String source = "import polyglot\n" +
                            "@polyglot.export_value\n" +
                            "def foo(x, y):\n" +
                            "    print(int(x) * int(y))\n\n";
            Source script = Source.create("python", source);
            context.eval(script);
            Value main = context.getPolyglotBindings().getMember("foo");
            main.execute(BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO), BigInteger.valueOf(7));
            assertEquals("129127208515966861298\n", out.toString("UTF-8"));
            out.reset();
            main.execute(Long.MAX_VALUE, 14);
            assertEquals("129127208515966861298\n", out.toString("UTF-8"));
            out.reset();
            main.execute(6, 7);
            assertEquals("42\n", out.toString("UTF-8"));
            out.reset();
            main.execute(true, true);
            assertEquals("1\n", out.toString("UTF-8"));
        }

        @Test
        public void testBigIntegersAdd() throws UnsupportedEncodingException {
            String source = "import polyglot\n" +
                            "@polyglot.export_value\n" +
                            "def foo(x, y):\n" +
                            "    print(int(x) + int(y))\n\n";
            Source script = Source.create("python", source);
            context.eval(script);
            Value main = context.getPolyglotBindings().getMember("foo");
            main.execute(BigInteger.valueOf(24).shiftLeft(101), BigInteger.valueOf(7));
            assertEquals("60847228810955011271841753858055\n", out.toString("UTF-8"));
            out.reset();
            main.execute(BigInteger.valueOf(24).shiftLeft(101), BigInteger.valueOf(24).shiftLeft(101));
            assertEquals("121694457621910022543683507716096\n", out.toString("UTF-8"));
            out.reset();
            main.execute(6, 7);
            assertEquals("13\n", out.toString("UTF-8"));
            out.reset();
            main.execute(true, true);
            assertEquals("2\n", out.toString("UTF-8"));
        }

        @Test
        public void testBigIntegersEg() throws UnsupportedEncodingException {
            String source = "import polyglot\n" +
                            "@polyglot.export_value\n" +
                            "def foo(x, y):\n" +
                            "    print(int(x) == int(y))\n\n";
            Source script = Source.create("python", source);
            context.eval(script);
            Value main = context.getPolyglotBindings().getMember("foo");
            main.execute(BigInteger.valueOf(24).shiftLeft(101), BigInteger.valueOf(7));
            assertEquals("False\n", out.toString("UTF-8"));
            out.reset();
            main.execute(BigInteger.valueOf(24).shiftLeft(101), BigInteger.valueOf(24).shiftLeft(101));
            assertEquals("True\n", out.toString("UTF-8"));
            out.reset();
            main.execute(6, 7);
            assertEquals("False\n", out.toString("UTF-8"));
            out.reset();
            main.execute(6, 6);
            assertEquals("True\n", out.toString("UTF-8"));
            out.reset();
            main.execute(true, BigInteger.ONE);
            assertEquals("True\n", out.toString("UTF-8"));
            out.reset();
            main.execute(true, BigInteger.ZERO);
            assertEquals("False\n", out.toString("UTF-8"));
        }

        @Test
        public void testAsFunction() throws UnsupportedEncodingException {
            String source = "import polyglot\n" +
                            "@polyglot.export_value\n" +
                            "def foo():\n" +
                            "    print('Called')\n\n";
            Source script = Source.create("python", source);
            context.eval(script);
            Value main = context.getPolyglotBindings().getMember("foo");
            main.execute();
            assertEquals("Called\n", out.toString("UTF-8"));
        }

        @Test
        public void testAsFunctionVarArgs() throws UnsupportedEncodingException {
            String source = "import polyglot\n" +
                            "@polyglot.export_value\n" +
                            "def foo(a, b):\n" +
                            "    print(a, b)\n\n";
            Source script = Source.create("python", source);
            context.eval(script);
            Value main = context.getPolyglotBindings().getMember("foo");
            main.execute("Hello", "World");
            assertEquals("Hello World\n", out.toString("UTF-8"));
        }

        @Test
        public void mainFunctionsAreImplicitlyImporteable() throws UnsupportedEncodingException {
            String source = "def foo(a, b):\n" +
                            "    print(a, b)\n\n";
            Source script = Source.create("python", source);
            context.eval(script);
            Value main = context.getBindings("python").getMember("foo");
            main.execute("Hello", "World");
            assertEquals("Hello World\n", out.toString("UTF-8"));
        }

        @Test
        public void builtinFunctionsAreImporteable() throws UnsupportedEncodingException {
            String source = "pass";
            Source script = Source.create("python", source);
            context.eval(script);
            Value main = context.getBindings("python").getMember("__builtins__").getMember("print");
            main.execute("Hello", "World");
            assertEquals("Hello World\n", out.toString("UTF-8"));
        }

        @Test
        public void enumeratingMainBindingsWorks() throws Exception {
            assertTrue(context.getBindings("python").getMemberKeys().contains("__builtins__"));
        }

        @Test
        public void testMultipleInvocationsAreInSameScope() throws UnsupportedEncodingException {
            String source = "def foo(a, b):\n" +
                            "    print(a, b)\n" +
                            "foo";
            Source script = Source.create("python", source);
            Value foo = context.eval(script);
            foo.execute("Hello", "World");
            assertEquals("Hello World\n", out.toString("UTF-8"));

            source = "def bar(a, b):\n" +
                            "    foo(a, b)\n" +
                            "bar";
            script = Source.create("python", source);
            Value bar = context.eval(script);
            bar.execute("Hello", "World");
            assertEquals("Hello World\nHello World\n", out.toString("UTF-8"));

            source = "invalid syntax";
            script = Source.create("python", source);
            try {
                context.eval(script);
            } catch (Throwable t) {
            }
            bar.execute("Hello", "World");
            assertEquals("Hello World\nHello World\nHello World\n", out.toString("UTF-8"));
            foo.execute("Hello", "World");
            assertEquals("Hello World\nHello World\nHello World\nHello World\n", out.toString("UTF-8"));
        }

        @Test
        public void accessSuitePy() throws IOException {
            Source suitePy = Source.newBuilder("python", "{ \"libraries\" : {\n" +
                            "\n" +
                            "    # ------------- Libraries -------------\n" +
                            "\n" +
                            "    \"DACAPO\" : {\n" +
                            "      \"urls\" : [\n" +
                            "        \"https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/dacapo-9.12-bach-patched.jar\",\n" +
                            "      ],\n" +
                            "      \"sha1\" : \"e39957904b7e79caf4fa54f30e8e4ee74d4e9e37\",\n" +
                            "    }," +
                            "  }" +
                            "}",
                            "suite.py").build();
            Value suite = context.eval(suitePy);
            Value libraries = suite.getHashValue("libraries");
            assertNotNull("libraries found", libraries);
            Value dacapo = null;
            Value hashKeysIterator = libraries.getHashKeysIterator();
            while (hashKeysIterator.hasIteratorNextElement()) {
                String k = hashKeysIterator.getIteratorNextElement().asString();
                System.err.println("k " + k);
                if ("DACAPO".equals(k)) {
                    dacapo = libraries.getHashValue(k);
                }
            }
            assertNotNull("Dacapo found", dacapo);
            assertEquals("e39957904b7e79caf4fa54f30e8e4ee74d4e9e37", dacapo.getHashValue("sha1").asString());
        }

        public class AForeignExecutable implements ProxyExecutable {
            @Override
            public Object execute(Value... arguments) {
                throw new UnsupportedOperationException("wrong arity");
            }
        }

        @Ignore // blocked by GR-46281
        @Test
        public void runAForeignExecutable() throws IOException {
            Source suitePy = Source.newBuilder("python",
                            """
                                            def foo(obj):
                                              try:
                                                 obj()
                                              except TypeError as e:
                                                 pass
                                              else:
                                                 assert False
                                            foo
                                            """,
                            "suite.py").build();
            Value foo = context.eval(suitePy);
            foo.execute(new AForeignExecutable());
        }

        @Ignore // blocked by GR-46281
        @Test
        public void invokeAForeignMember() throws IOException {
            Source suitePy = Source.newBuilder("python",
                            """
                                            def foo(obj):
                                              try:
                                                 obj.fun()
                                              except TypeError as e:
                                                 pass
                                              else:
                                                 assert False
                                            foo
                                            """,
                            "suite.py").build();
            Map<String, Object> m = new HashMap<>();
            m.put("fun", new AForeignExecutable());
            Value foo = context.eval(suitePy);
            foo.execute(ProxyObject.fromMap(m));
        }

        public static class JavaObject {
            public byte byteValue = 1;
            public short shortValue = 2;
            public int intValue = 3;
            public long longValue = 4;
            public float floatValue = 5;
            public double doubleValue = 6;
            public boolean booleanValue = true;
            public char charValue = 'c';

            public byte getByteValue() {
                return byteValue;
            }

            public short getShortValue() {
                return shortValue;
            }

            public int getIntValue() {
                return intValue;
            }

            public long getLongValue() {
                return longValue;
            }

            public float getFloatValue() {
                return floatValue;
            }

            public double getDoubleValue() {
                return doubleValue;
            }

            public boolean getBooleanValue() {
                return booleanValue;
            }

            public char getCharValue() {
                return charValue;
            }
        }

        @Test
        public void accessJavaObjectFields() throws IOException {
            Source suitePy = Source.newBuilder("python", "" +
                            "def foo(obj):\n" +
                            "  print(obj.byteValue, type(obj.byteValue))\n" +
                            "  print(obj.shortValue, type(obj.shortValue))\n" +
                            "  print(obj.intValue, type(obj.intValue))\n" +
                            "  print(obj.longValue, type(obj.longValue))\n" +
                            "  print(obj.floatValue, type(obj.floatValue))\n" +
                            "  print(obj.doubleValue, type(obj.doubleValue))\n" +
                            "  print(obj.booleanValue, type(obj.booleanValue))\n" +
                            "  print(obj.charValue, type(obj.charValue))\n" +
                            "foo",
                            "suite.py").build();
            Value foo = context.eval(suitePy);
            foo.execute(new JavaObject());
            assertEquals("" +
                            "1 <class 'int'>\n" +
                            "2 <class 'int'>\n" +
                            "3 <class 'int'>\n" +
                            "4 <class 'int'>\n" +
                            "5.0 <class 'float'>\n" +
                            "6.0 <class 'float'>\n" +
                            "True <class 'bool'>\n" +
                            "c <class 'polyglot.ForeignString'>\n", out.toString("UTF-8"));
        }

        @Test
        public void accessJavaObjectGetters() throws IOException {
            Source suitePy = Source.newBuilder("python", "" +
                            "def foo(obj):\n" +
                            "  print(obj.getByteValue(), type(obj.getByteValue()))\n" +
                            "  print(obj.getShortValue(), type(obj.getShortValue()))\n" +
                            "  print(obj.getIntValue(), type(obj.getIntValue()))\n" +
                            "  print(obj.getLongValue(), type(obj.getLongValue()))\n" +
                            "  print(obj.getFloatValue(), type(obj.getFloatValue()))\n" +
                            "  print(obj.getDoubleValue(), type(obj.getDoubleValue()))\n" +
                            "  print(obj.getBooleanValue(), type(obj.getBooleanValue()))\n" +
                            "  print(obj.getCharValue(), type(obj.getCharValue()))\n" +
                            "foo",
                            "suite.py").build();
            Value foo = context.eval(suitePy);
            foo.execute(new JavaObject());
            assertEquals("" +
                            "1 <class 'int'>\n" +
                            "2 <class 'int'>\n" +
                            "3 <class 'int'>\n" +
                            "4 <class 'int'>\n" +
                            "5.0 <class 'float'>\n" +
                            "6.0 <class 'float'>\n" +
                            "True <class 'bool'>\n" +
                            "c <class 'polyglot.ForeignString'>\n", out.toString("UTF-8"));
        }

        @Test
        public void javaStringsAndPythonStrings() throws IOException {
            Source workflowScript = Source.newBuilder("python", "" +
                            "def enrich(mymap):\n" +
                            "    print(type(mymap))\n" +
                            "    print(mymap['source'] == 'foo')\n" +
                            "    print(mymap.get('source') == 'foo')\n",
                            "workflowScript.py").build();
            context.eval(workflowScript);
            Map<String, String> source = Map.of("source", "foo");
            context.getBindings("python").getMember("enrich").execute(source);
            assertEquals("" +
                            "<class 'polyglot.ForeignDict'>\n" +
                            "True\n" +
                            "True\n", out.toString("UTF-8"));
        }

        @Test
        public void writableBindings() {
            context.getBindings("python").putMember("javaObj", 42);
            Value javaObj = context.eval("python", "javaObj");
            assertTrue(javaObj.isNumber());
            assertEquals(javaObj.asInt(), 42);
        }

        @Test
        public void testDictTypeConversion() {
            Value bindings = context.getBindings("python");
            bindings.putMember("foo", "32");
            bindings.putMember("bar", (short) 32);
            Value intValue = context.eval("python", "int(foo) + int(bar)");
            assertEquals(intValue.asInt(), 64);
        }

        @Test
        public void testListTypeConversions() {
            Value intConversion = context.eval("python", "int");
            Value list = context.eval("python", "[1]");
            list.setArrayElement(0, "32");
            assertEquals(intConversion.execute(list.getArrayElement(0)).asInt(), 32);
            list.setArrayElement(0, (short) 3);
            assertEquals(intConversion.execute(list.getArrayElement(0)).asInt(), 3);
        }

        public class UnsupportedProxyHashMap implements ProxyHashMap {
            @Override
            public long getHashSize() {
                return 0;
            }

            @Override
            public boolean hasHashEntry(Value key) {
                return key.asString().endsWith("_exists");
            }

            @Override
            public Object getHashValue(Value key) {
                throw new UnsupportedOperationException("map is not readable");
            }

            @Override
            public void putHashEntry(Value key, Value value) {
                throw new UnsupportedOperationException("map is read only");
            }

            @Override
            public boolean removeHashEntry(Value key) {
                throw new UnsupportedOperationException("map is read only");
            }

            @Override
            public Object getHashEntriesIterator() {
                return null;
            }
        }

        public class UnsupportedProxyArray implements ProxyArray {

            @Override
            public Object get(long index) {
                throw new UnsupportedOperationException("array is not readable");
            }

            @Override
            public void set(long index, Value value) {
                throw new UnsupportedOperationException("array is read only");
            }

            @Override
            public boolean remove(long index) {
                throw new UnsupportedOperationException("array is read only");
            }

            @Override
            public long getSize() {
                return 1;
            }
        }

        @Test
        public void modifyUnsupportedProxyHashMap() throws IOException {
            Source suitePy = Source.newBuilder("python", "" +
                            "def foo(obj):\n" +
                            "  try:\n" +
                            "     obj['foo_exists']=42\n" +
                            "  except AttributeError as e:\n" +
                            "    if(not str(e).endswith('not writable')):" +
                            "      raise e\n" +
                            "foo",
                            "suite.py").build();
            Value foo = context.eval(suitePy);
            foo.execute(new UnsupportedProxyHashMap());
        }

        @Test
        public void putUnsupportedProxyHashMap() throws IOException {
            Source suitePy = Source.newBuilder("python", "" +
                            "def foo(obj):\n" +
                            "  try:\n" +
                            "     obj['foo']=42\n" +
                            "  except AttributeError as e:\n" +
                            "    if(not str(e).endswith('not insertable')):" +
                            "      raise e\n" +
                            "foo",
                            "suite.py").build();
            Value foo = context.eval(suitePy);
            foo.execute(new UnsupportedProxyHashMap());
        }

        @Test
        public void removeUnsupportedProxyHashMap() throws IOException {
            Source suitePy = Source.newBuilder("python", "" +
                            "def foo(obj):\n" +
                            "  try:\n" +
                            "     del obj['foo']\n" +
                            "  except KeyError as e:\n" +
                            "      pass\n" +
                            "foo",
                            "suite.py").build();
            Value foo = context.eval(suitePy);
            foo.execute(new UnsupportedProxyHashMap());
        }

        @Test
        public void removeExistingUnsupportedProxyHashMap() throws IOException {
            Source suitePy = Source.newBuilder("python", "" +
                            "def foo(obj):\n" +
                            "  try:\n" +
                            "    del obj['foo_exists']\n" +
                            "  except AttributeError as e:\n" +
                            "    if(not str(e).endswith('not removable')):" +
                            "      raise e\n" +
                            "foo",
                            "suite.py").build();
            Value foo = context.eval(suitePy);
            foo.execute(new UnsupportedProxyHashMap());
        }

        @Test
        public void getUnsupportedArrayElement() throws IOException {
            Source suitePy = Source.newBuilder("python", "" +
                            "def foo(obj):\n" +
                            "  try:\n" +
                            "    return obj[0]\n" +
                            "  except IndexError as e:\n" +
                            "    if(not str(e).endswith('not readable')):" +
                            "      raise e\n" +
                            "foo",
                            "suite.py").build();
            Value foo = context.eval(suitePy);
            foo.execute(new UnsupportedProxyArray());
        }

        @Test
        public void setUnsupportedArrayElement() throws IOException {
            Source suitePy = Source.newBuilder("python", "" +
                            "def foo(obj):\n" +
                            "  try:\n" +
                            "    obj[0] = 42\n" +
                            "  except IndexError as e:\n" +
                            "    if(not str(e).endswith('not writable')):" +
                            "      raise e\n" +
                            "foo",
                            "suite.py").build();
            Value foo = context.eval(suitePy);
            foo.execute(new UnsupportedProxyArray());
        }

        @Test
        public void removeUnsupportedArrayElement() throws IOException {
            Source suitePy = Source.newBuilder("python", "" +
                            "def foo(obj):\n" +
                            "  try:\n" +
                            "    del obj[0]\n" +
                            "  except IndexError as e:\n" +
                            "    if(not str(e).endswith('not removable')):" +
                            "      raise e\n" +
                            "foo",
                            "suite.py").build();
            Value foo = context.eval(suitePy);
            foo.execute(new UnsupportedProxyArray());
        }

        @Test
        public void recursiveJavaListRepr() throws IOException {
            Source source = Source.newBuilder("python", """
                            def foo(obj):
                                return repr(obj)
                            foo
                            """, "input").build();
            Value foo = context.eval(source);
            List<Object> recursiveList = new ArrayList<>();
            recursiveList.add(1);
            recursiveList.add(recursiveList);
            Value result = foo.execute(recursiveList);
            assertEquals(result.as(String.class), "[1, [...]]");
        }

        @Test
        public void testMetaParents() throws IOException {
            Source source = Source.newBuilder("python", """
                            class Foo:
                                pass
                            class Bar(Foo):
                                pass
                            Bar
                            """, "input").build();
            Value bar = context.eval(source);
            assertTrue(bar.isMetaObject());
            assertEquals(bar.getMetaSimpleName(), "Bar");
            assertTrue(bar.hasMetaParents());
            Value barParents = bar.getMetaParents();
            assertTrue(barParents.hasArrayElements());
            assertEquals(barParents.getArraySize(), 1);
            Value foo = barParents.getArrayElement(0);
            assertTrue(foo.isMetaObject());
            assertEquals(foo.getMetaSimpleName(), "Foo");
            assertTrue(foo.hasMetaParents());
            Value fooParents = foo.getMetaParents();
            assertTrue(fooParents.hasArrayElements());
            assertEquals(fooParents.getArraySize(), 1);
            Value object = fooParents.getArrayElement(0);
            assertTrue(object.isMetaObject());
            assertEquals(object.getMetaSimpleName(), "object");
            assertFalse(object.hasMetaParents());
        }

        public static class MyFunction implements java.util.function.Function<String, String> {
            public String __text_signature__ = "(string) -> str";

            @Override
            public String apply(String s) {
                return s;
            }
        }

        public static class MyFunctionWithCustomName implements java.util.function.Function<String, String> {
            public String __text_signature__ = "(string) -> str";
            public String __name__ = "myfunc";

            @Override
            public String apply(String s) {
                return s;
            }
        }

        public static class MyFunctionWithIncorrectSignature implements java.util.function.Function<String, String> {
            public String __text_signature__ = "[I;java.lang.String;";

            @Override
            public String apply(String s) {
                return s;
            }
        }

        @Test
        public void javaExecutablesAsPythonFunctions() {
            Value inspectSignature = context.eval("python", "import inspect; inspect.signature");
            assertEquals("<Signature (string)>", inspectSignature.execute(new MyFunction()).toString());

            Value signature = context.eval("python", "lambda f: f.__text_signature__");
            assertEquals(new MyFunctionWithIncorrectSignature().__text_signature__, signature.execute(new MyFunctionWithIncorrectSignature()).asString());

            Value name = context.eval("python", "lambda f: f.__name__");
            assertEquals(new MyFunctionWithCustomName().__name__, name.execute(new MyFunctionWithCustomName()).asString());
        }
    }

    @RunWith(Parameterized.class)
    public static class PythonOptionsExposedInPythonTest extends PythonTests {
        private static Engine engine = Engine.create("python");

        static class OptionsChecker {
            private String option;
            private Source source;
            private String[] values;
            private Builder builder;

            OptionsChecker(String option, String code, String... values) {
                this.builder = Context.newBuilder("python").engine(engine).allowExperimentalOptions(true).allowAllAccess(true);
                this.option = "python." + option;
                this.source = Source.create("python", code);
                this.values = values;
            }

            @Override
            public String toString() {
                return option + "->" + source.getCharacters();
            }
        }

        @Parameters(name = "{0}")
        public static OptionsChecker[] input() {
            return new OptionsChecker[]{
                            new OptionsChecker("InspectFlag", "import sys; sys.flags.inspect", "true", "false"),
                            new OptionsChecker("QuietFlag", "import sys; sys.flags.quiet", "true", "false"),
                            new OptionsChecker("VerboseFlag", "import sys; sys.flags.verbose", "true", "false"),
                            new OptionsChecker("NoSiteFlag", "import sys; sys.flags.no_site", "true", "false"),
                            new OptionsChecker("NoUserSiteFlag", "import sys; sys.flags.no_user_site", "true", "false"),
                            new OptionsChecker("IgnoreEnvironmentFlag", "import sys; sys.flags.ignore_environment", "true", "false"),
                            new OptionsChecker("PythonPath", "import sys; sys.path", "/pathA", "/pathB"),
                            new OptionsChecker("PythonOptimizeFlag", "import sys; sys.flags.debug", "true", "false"),
                            new OptionsChecker("PythonOptimizeFlag", "import sys; sys.flags.optimize", "true", "false"),
                            new OptionsChecker("PythonOptimizeFlag", "__debug__", "true", "false"),
                            new OptionsChecker("Executable", "import sys; sys.executable", "graalpython", "python3"),
                            new OptionsChecker("IsolateFlag", "import sys; sys.flags.isolated", "true", "false")
            };
        }

        @Parameter public OptionsChecker check;

        @Test
        public void testSysFlagsReflectContext() {
            assertEquals(check.values.length, Arrays.stream(check.values).map(value -> {
                Value result = check.builder.option(check.option, value).build().eval(check.source);
                return result.toString();
            }).distinct().count());
        }
    }
}
