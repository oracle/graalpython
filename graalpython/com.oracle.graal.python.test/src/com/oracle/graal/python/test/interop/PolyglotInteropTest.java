/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertNotNull;

import java.io.IOException;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Context.Builder;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

import com.oracle.graal.python.test.PythonTests;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.strings.TruffleString;

@RunWith(Enclosed.class)
public class PolyglotInteropTest {
    public static class GeneralInterop extends PythonTests {
        private Context context;

        @Before
        public void setUpTest() {
            Builder builder = Context.newBuilder();
            builder.allowExperimentalOptions(true);
            builder.allowAllAccess(true);
            context = builder.build();
        }

        @After
        public void tearDown() {
            context.close();
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
            for (Object k : libraries.getHashKeysIterator().as(Iterable.class)) {
                if (k instanceof TruffleString) {
                    k = ((TruffleString) k).toJavaStringUncached();
                }
                System.err.println("k " + k);
                if ("DACAPO".equals(k)) {
                    dacapo = libraries.getHashValue(k);
                }
            }
            assertNotNull("Dacapo found", dacapo);
            assertEquals("'e39957904b7e79caf4fa54f30e8e4ee74d4e9e37'", dacapo.getHashValue("sha1").toString());
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
                    throw ArityException.create(0, 0, arguments.length);
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
                    throw ArityException.create(0, 0, arguments.length);
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

    @ExportLibrary(InteropLibrary.class)
    public static final class InteropArray implements TruffleObject {
        @CompilationFinal(dimensions = 1) private final Object[] array;

        public InteropArray(Object[] array) {
            this.array = array;
        }

        @ExportMessage(name = "readArrayElement")
        Object get(long idx) {
            return array[(int) idx];
        }

        @ExportMessage(name = "getArraySize")
        int size() {
            return array.length;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        boolean isArrayElementReadable(long idx) {
            return idx < array.length;
        }
    }
}
