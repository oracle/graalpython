/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.List;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Context.Builder;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.graal.python.runtime.interop.InteropArray;
import com.oracle.graal.python.test.PythonTests;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

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
            try (Context c = Context.newBuilder().allowExperimentalOptions(true).allowAllAccess(true).option("python.TerminalIsInteractive", "false").build()) {
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
            try (Context c = Context.newBuilder().allowExperimentalOptions(true).allowAllAccess(true).option("python.TerminalIsInteractive", "true").build()) {
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
            try (Context c = Context.newBuilder().allowExperimentalOptions(true).allowAllAccess(true).option("python.TerminalIsInteractive", "false").build()) {
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
            try (Context c = Context.newBuilder().allowExperimentalOptions(true).allowAllAccess(true).option("python.TerminalIsInteractive", "true").build()) {
                c.eval(Source.newBuilder("python", INCOMPLETE_SOURCE, "eval").interactive(true).build());
            } catch (PolyglotException t) {
                assertTrue(t.isSyntaxError());
                assertTrue(t.isIncompleteSource());
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

            Value listConverter = context.eval("python", "list");
            Value libraries = suite.getMember("libraries");
            assertNotNull("libraries found", libraries);
            final List<Object> suiteKeys = Arrays.asList(listConverter.execute(suite.invokeMember("keys")).as(Object[].class));
            assertTrue("Libraries found among keys: " + suiteKeys, suiteKeys.contains("libraries"));

            Value dacapo = null;
            for (Object k : listConverter.execute(libraries.invokeMember("keys")).as(List.class)) {
                System.err.println("k " + k);
                if ("DACAPO".equals(k)) {
                    dacapo = libraries.getMember((String) k);
                }
            }
            assertNotNull("Dacapo found", dacapo);
            assertEquals("'e39957904b7e79caf4fa54f30e8e4ee74d4e9e37'", dacapo.getMember("sha1").toString());
        }

        @ExportLibrary(InteropLibrary.class)
        public static class ForeignObjectWithOOInvoke implements TruffleObject {
            public String getMyName() {
                return getClass().getName();
            }

            @ExportMessage
            boolean hasMembers() {
                return true;
            }

            @ExportMessage
            Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
                return new InteropArray(new Object[]{"getMyName"});
            }

            @ExportMessage
            boolean isMemberInvocable(String member) {
                return member.equals("getMyName");
            }

            @ExportMessage
            Object invokeMember(String member, Object... arguments) throws ArityException, UnknownIdentifierException {
                if (arguments.length != 0) {
                    throw ArityException.create(0, arguments.length);
                } else if (!member.equals("getMyName")) {
                    throw UnknownIdentifierException.create(member);
                } else {
                    return getMyName();
                }
            }
        }

        @ExportLibrary(InteropLibrary.class)
        public static class ForeignObjectWithoutOOInvoke implements TruffleObject {
            public String getMyName() {
                return getClass().getName();
            }

            @ExportMessage
            boolean hasMembers() {
                return true;
            }

            @ExportMessage
            Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
                return new InteropArray(new Object[]{"getMyName"});
            }

            @ExportMessage
            boolean isMemberReadable(String member) {
                return member.equals("getMyName");
            }

            @ExportMessage
            Object readMember(String member) throws UnknownIdentifierException {
                if (member.equals("getMyName")) {
                    return new GetMyNameMethod(this);
                } else {
                    throw UnknownIdentifierException.create(member);
                }
            }
        }

        @ExportLibrary(InteropLibrary.class)
        public static class GetMyNameMethod implements TruffleObject {
            private ForeignObjectWithoutOOInvoke self;

            public GetMyNameMethod(ForeignObjectWithoutOOInvoke object2) {
                this.self = object2;
            }

            @ExportMessage
            boolean isExecutable() {
                return true;
            }

            @ExportMessage
            Object execute(Object... arguments) throws ArityException {
                if (arguments.length != 0) {
                    throw ArityException.create(0, arguments.length);
                } else {
                    return self.getMyName();
                }
            }
        }

        @Test
        public void tryInvokeThenReadExecute() {
            String source = "def foo(a, b):\n" +
                            "    return (a.getMyName(), b.getMyName())\n" +
                            "foo";
            Source script = Source.create("python", source);
            Value foo = context.eval(script);
            Value execute = foo.execute(new ForeignObjectWithOOInvoke(), new ForeignObjectWithoutOOInvoke());
            Object[] result = execute.as(Object[].class);
            assert result[0].equals(ForeignObjectWithOOInvoke.class.getName());
            assert result[1].equals(ForeignObjectWithoutOOInvoke.class.getName());
        }

        public class JavaObject {
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
                            "c <class 'str'>\n", out.toString("UTF-8"));
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
                            "c <class 'str'>\n", out.toString("UTF-8"));
        }

        @Test
        public void writableBindings() {
            context.getBindings("python").putMember("javaObj", 42);
            Value javaObj = context.eval("python", "javaObj");
            assertTrue(javaObj.isNumber());
            assertEquals(javaObj.asInt(), 42);
        }

        @ExportLibrary(InteropLibrary.class)
        static final class WrapString implements TruffleObject {
            private final String str;

            WrapString(String str) {
                this.str = str;
            }

            @ExportMessage
            @SuppressWarnings("static-method")
            boolean isString() {
                return true;
            }

            @ExportMessage
            String asString() {
                return str;
            }
        }

        @ExportLibrary(InteropLibrary.class)
        static final class WrapBoolean implements TruffleObject {
            private final boolean flag;

            WrapBoolean(boolean flag) {
                this.flag = flag;
            }

            @ExportMessage
            @SuppressWarnings("static-method")
            boolean isBoolean() {
                return true;
            }

            @ExportMessage
            boolean asBoolean() {
                return flag;
            }
        }

        @Test
        public void multiplyStrBool() {
            context.getBindings("python").putMember("javaBool", new WrapBoolean(true));
            context.getBindings("python").putMember("javaStr", new WrapString("test"));
            assertEquals(context.eval("python", "javaStr * javaBool").asString(), "test");
            assertEquals(context.eval("python", "javaBool * javaStr").asString(), "test");
        }
    }

    @RunWith(Parameterized.class)
    public static class PythonOptionsExposedInPythonTest extends PythonTests {
        private static Engine engine = Engine.create();

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
