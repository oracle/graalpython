/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.test.debug;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Context.Builder;
import org.graalvm.polyglot.Source;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.oracle.graal.python.test.PythonTests;
import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.DebugScope;
import com.oracle.truffle.api.debug.DebugStackFrame;
import com.oracle.truffle.api.debug.DebugValue;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.SuspendedCallback;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.tck.DebuggerTester;

public class PythonDebugTest {

    private DebuggerTester tester;

    @Before
    public void before() {
        Builder newBuilder = Context.newBuilder();
        newBuilder.allowExperimentalOptions(true);
        newBuilder.allowAllAccess(true);
        PythonTests.closeContext();
        tester = new DebuggerTester(newBuilder);
    }

    @After
    public void dispose() {
        tester.close();
    }

    @Test
    public void testSteppingAsExpected() throws Throwable {
        // test that various elements step as expected, including generators, statement level atomic
        // expressions, and roots
        final Source source = Source.newBuilder("python", "" +
                        "import sys\n" +
                        "from sys import version\n" +
                        "\n" +
                        "def a():\n" +
                        "  x = [1]\n" +
                        "  x[0]\n" +
                        "  x.append(1)\n" +
                        "  for i in genfunc():\n" +
                        "    return i\n" +
                        "\n" +
                        "def genfunc():\n" +
                        "  yield 1\n" +
                        "  yield 2\n" +
                        "  yield 3\n" +
                        "  return\n" +
                        "\n" +
                        "a()\n" +
                        "a()\n", "test_stepping.py").buildLiteral();

        try (DebuggerSession session = tester.startSession()) {
            session.install(Breakpoint.newBuilder(DebuggerTester.getSourceImpl(source)).lineIs(1).build());
            tester.startEval(source);

            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame frame = event.getTopStackFrame();
                assertEquals(1, frame.getSourceSection().getStartLine());
                event.prepareStepOver(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame frame = event.getTopStackFrame();
                assertEquals(2, frame.getSourceSection().getStartLine());
                event.prepareStepOver(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame frame = event.getTopStackFrame();
                assertEquals(4, frame.getSourceSection().getStartLine());
                event.prepareStepOver(2);
            });
            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame frame = event.getTopStackFrame();
                assertEquals(17, frame.getSourceSection().getStartLine());
                event.prepareStepOver(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame frame = event.getTopStackFrame();
                assertEquals(18, frame.getSourceSection().getStartLine());
                event.prepareStepInto(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame frame = event.getTopStackFrame();
                assertEquals(5, frame.getSourceSection().getStartLine());
                event.prepareStepInto(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame frame = event.getTopStackFrame();
                assertEquals(6, frame.getSourceSection().getStartLine());
                event.prepareStepInto(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame frame = event.getTopStackFrame();
                assertEquals(7, frame.getSourceSection().getStartLine());
                event.prepareStepOver(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame frame = event.getTopStackFrame();
                assertEquals(8, frame.getSourceSection().getStartLine());
                event.prepareStepInto(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame frame = event.getTopStackFrame();
                assertEquals(12, frame.getSourceSection().getStartLine());
                event.prepareStepOver(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame frame = event.getTopStackFrame();
                assertEquals(8, frame.getSourceSection().getStartLine());
                event.prepareStepOut(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame frame = event.getTopStackFrame();
                assertEquals(18, frame.getSourceSection().getStartLine());
                event.prepareStepOver(1);
            });
            assertEquals("1", tester.expectDone());
        }
    }

    @Test
    public void testInlineEvaluation() throws Throwable {
        final Source source = Source.newBuilder("python", "" +
                        "y = 4\n" +
                        "def foo(x):\n" +
                        "  a = 1\n" +
                        "  b = 2\n" +
                        "  def bar():\n" +
                        "    return a + b\n" +
                        "  return bar() + x + y\n" +
                        "foo(3)", "test_inline.py").buildLiteral();

        try (DebuggerSession session = tester.startSession()) {
            session.install(Breakpoint.newBuilder(DebuggerTester.getSourceImpl(source)).lineIs(5).build());
            session.install(Breakpoint.newBuilder(DebuggerTester.getSourceImpl(source)).lineIs(7).build());
            tester.startEval(source);

            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame frame = event.getTopStackFrame();
                assertEquals(5, frame.getSourceSection().getStartLine());
                assertEquals("3", frame.eval("a + b").as(String.class));
                event.prepareContinue();
            });
            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame frame = event.getTopStackFrame();
                assertEquals(7, frame.getSourceSection().getStartLine());
                assertEquals("6", frame.eval("bar() * 2").as(String.class));
                event.prepareContinue();
            });
            assertEquals("10", tester.expectDone());
        }
    }

    @Test
    public void testConditionalBreakpointInFunction() throws Throwable {
        final Source source = Source.newBuilder("python", "" +
                        "def fun():\n" +
                        "  def prod(n):\n" +
                        "    p = 1\n" +
                        "    for i in range(n):\n" +
                        "      p = p * 2\n" +
                        "    return p\n" +
                        "  sum = 0\n" +
                        "  for i in range(0, 10):\n" +
                        "    sum = sum + i\n" +
                        "  return sum\n" +
                        "fun()", "test_cond_break.py").buildLiteral();

        try (DebuggerSession session = tester.startSession()) {
            Breakpoint b = session.install(Breakpoint.newBuilder(DebuggerTester.getSourceImpl(source)).lineIs(9).build());
            b.setCondition("i == 5");
            tester.startEval(source);

            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame frame = event.getTopStackFrame();
                assertEquals(9, frame.getSourceSection().getStartLine());
                assertEquals("10", frame.eval("sum").as(String.class));
                assertEquals("16", frame.eval("prod(4)").as(String.class));
                event.prepareContinue();
            });
            assertEquals("45", tester.expectDone());
        }

        try (DebuggerSession session = tester.startSession()) {
            Breakpoint b = session.install(Breakpoint.newBuilder(DebuggerTester.getSourceImpl(source)).lineIs(9).build());
            b.setCondition("prod(i) == 16");
            tester.startEval(source);

            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame frame = event.getTopStackFrame();
                assertEquals(9, frame.getSourceSection().getStartLine());
                assertEquals("4", frame.eval("i").as(String.class));
                event.prepareContinue();
            });
            assertEquals("45", tester.expectDone());
        }
    }

    @Test
    public void testConditionalBreakpointGlobal() throws Throwable {
        final Source source = Source.newBuilder("python", "" +
                        "values = []\n" +
                        "for i in range(0, 10):\n" +
                        "  values.append(i)\n" +
                        "sum(values)\n", "test_cond_break_global.py").buildLiteral();

        try (DebuggerSession session = tester.startSession()) {
            Breakpoint b = session.install(Breakpoint.newBuilder(DebuggerTester.getSourceImpl(source)).lineIs(3).build());
            b.setCondition("i == 5");
            tester.startEval(source);

            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame frame = event.getTopStackFrame();
                assertEquals(3, frame.getSourceSection().getStartLine());
                assertEquals("10", frame.eval("sum(values)").as(String.class));
                event.prepareContinue();
            });
            assertEquals("45", tester.expectDone());
        }
    }

    @Test
    public void testReenterArgumentsAndValues() throws Throwable {
        // Test that after a re-enter, arguments are kept and variables are cleared.
        final Source source = Source.newBuilder("python", "" +
                        "def main():\n" +
                        "  gi = geni()\n" +
                        "  return fnc(next(gi), 20)\n" +
                        "\n" +
                        "def fnc(n, m):\n" +
                        "  x = n + m\n" +
                        "  n = m - n\n" +
                        "  m = m / 2\n" +
                        "  x = x + n + m\n" +
                        "  return x\n" +
                        "\n" +
                        "def geni():\n" +
                        "  i = 10\n" +
                        "  while (True):\n" +
                        "    i += 1\n" +
                        "    yield i\n" +
                        "\n" +
                        "main()\n", "testReenterArgsAndVals.py").buildLiteral();

        try (DebuggerSession session = tester.startSession()) {
            session.install(Breakpoint.newBuilder(DebuggerTester.getSourceImpl(source)).lineIs(6).build());
            tester.startEval(source);

            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame frame = event.getTopStackFrame();
                assertEquals(6, frame.getSourceSection().getStartLine());
                checkStack(frame, "fnc", "n", "11", "m", "20");
                event.prepareStepOver(4);
            });
            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame frame = event.getTopStackFrame();
                assertEquals(10, frame.getSourceSection().getStartLine());
                checkStack(frame, "fnc", "n", "9", "m", "10.0", "x", "50.0");
                event.prepareUnwindFrame(frame);
            });
            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame frame = event.getTopStackFrame();
                assertEquals(3, frame.getSourceSection().getStartLine());
                assertEquals("main", frame.getName());
            });
            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame frame = event.getTopStackFrame();
                assertEquals(6, frame.getSourceSection().getStartLine());
                checkStack(frame, "fnc", "n", "11", "m", "20");
            });
            assertEquals("50.0", tester.expectDone());
        }
    }

    @Test
    public void testGettersSetters() throws Throwable {
        final Source source = Source.newBuilder("python", "" +
                        "class GetterOnly:\n" +
                        "  def __get__(self):\n" +
                        "    return 42\n" +
                        "\n" +
                        "class P:\n" +
                        "  def __init__(self):\n" +
                        "    self.__x = None\n" +
                        "    self.__y = None\n" +
                        "    self.__nx = 0\n" +
                        "    self.__ny = 0\n" +
                        "\n" +
                        "  @property\n" +
                        "  def x(self):\n" +
                        "    self.__nx += 1\n" +
                        "    return self.__x\n" +
                        "\n" +
                        "  @x.setter\n" +
                        "  def x(self, value):\n" +
                        "    self.__nx += 1\n" +
                        "    self.__x = value\n" +
                        "\n" +
                        "  y = GetterOnly()\n" +
                        "\n" +
                        "p = P()\n" +
                        "str(p)\n" +
                        "\n", "testGettersSetters.py").buildLiteral();
        try (DebuggerSession session = tester.startSession()) {
            session.suspendNextExecution();
            tester.startEval(source);
            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame frame = event.getTopStackFrame();
                assertEquals(1, frame.getSourceSection().getStartLine());
                event.prepareStepOver(2);
            });
            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame frame = event.getTopStackFrame();
                assertEquals("p = P()", frame.getSourceSection().getCharacters().toString());
                event.prepareStepOver(1);
            });
            expectSuspended((SuspendedEvent event) -> {
                DebugValue p = session.getTopScope("python").getDeclaredValue("p");
                DebugValue x = p.getProperty("x");
                assertTrue(x.hasReadSideEffects());
                assertTrue(x.hasWriteSideEffects());
                assertTrue(x.isReadable());
                assertTrue(x.isWritable());
                assertEquals(0, p.getProperty("__nx").as(Number.class).intValue());
                assertEquals("None", x.as(String.class));
                assertEquals(1, p.getProperty("__nx").as(Number.class).intValue());
                x.set(42);
                assertEquals(2, p.getProperty("__nx").as(Number.class).intValue());
                assertEquals("42", x.as(String.class));
                assertEquals(3, p.getProperty("__nx").as(Number.class).intValue());
                DebugValue y = p.getProperty("y");
                assertTrue(y.hasReadSideEffects());
                assertFalse(y.hasWriteSideEffects());
                assertTrue(y.isReadable());
                assertTrue(y.isWritable());
                DebugValue ny = p.getProperty("__ny");
                assertEquals(0, ny.as(Number.class).intValue());
                y.set(24);
                assertEquals("24", y.as(String.class));
            });
        }
    }

    @Test
    public void testInspectJavaArray() throws Throwable {
        final Source source = Source.newBuilder("python", "" +
                        "import java\n" +
                        "a_int = java.type('int[]')(3)\n" +
                        "a_long = java.type('long[]')(3)\n" +
                        "a_short = java.type('short[]')(3)\n" +
                        "a_byte = java.type('byte[]')(3)\n" +
                        "a_float = java.type('float[]')(3)\n" +
                        "a_double = java.type('double[]')(3)\n" +
                        "a_char = java.type('char[]')(3)\n" +
                        "print()\n" +
                        "\n", "testInspectJavaArray.py").buildLiteral();
        try (DebuggerSession session = tester.startSession()) {
            session.install(Breakpoint.newBuilder(DebuggerTester.getSourceImpl(source)).lineIs(9).build());
            tester.startEval(source);
            expectSuspended((SuspendedEvent event) -> {
                DebugScope globalScope = session.getTopScope("python");
                DebugValue intValue = globalScope.getDeclaredValue("a_int").getArray().get(0);
                // It's up to Truffle to decide which language it uses for inspection of primitives,
                // we should be fine as long as this doesn't throw an exception
                intValue.getMetaObject();
                assertEquals("0", intValue.as(String.class));
                DebugValue longValue = globalScope.getDeclaredValue("a_long").getArray().get(0);
                longValue.getMetaObject();
                assertEquals("0", longValue.as(String.class));
                DebugValue shortValue = globalScope.getDeclaredValue("a_short").getArray().get(0);
                shortValue.getMetaObject();
                assertEquals("0", shortValue.as(String.class));
                DebugValue byteValue = globalScope.getDeclaredValue("a_byte").getArray().get(0);
                byteValue.getMetaObject();
                assertEquals("0", byteValue.as(String.class));
                DebugValue floatValue = globalScope.getDeclaredValue("a_float").getArray().get(0);
                floatValue.getMetaObject();
                assertEquals("0.0", floatValue.as(String.class));
                DebugValue doubleValue = globalScope.getDeclaredValue("a_double").getArray().get(0);
                doubleValue.getMetaObject();
                assertEquals("0.0", doubleValue.as(String.class));
                DebugValue charValue = globalScope.getDeclaredValue("a_char").getArray().get(0);
                charValue.getMetaObject();
                assertEquals("\0", charValue.as(String.class));
            });
        }
    }

    @Test
    public void testSourceFileURI() throws Throwable {
        if (System.getProperty("os.name").toLowerCase().contains("mac")) {
            // on the mac slaves we run with symlinked directories and such and it's annoying to
            // cater for that
            return;
        }
        Path tempDir = Files.createTempDirectory("pySourceTest");
        try {
            Path importedFile = tempDir.resolve("imported.py");
            Path importingFile = tempDir.resolve("importing.py");
            Files.write(importedFile, ("def sum(a, b):\n" +
                            "  return a + b\n").getBytes());
            Files.write(importingFile, ("import sys\n" +
                            "sys.path.insert(0, '" + tempDir.toString() + "')\n" +
                            "import imported\n" +
                            "imported.sum(2, 3)\n").getBytes());
            Source source = Source.newBuilder("python", importingFile.toFile()).build();
            try (DebuggerSession session = tester.startSession()) {
                Breakpoint breakpoint = Breakpoint.newBuilder(importingFile.toUri()).lineIs(4).build();
                session.install(breakpoint);
                tester.startEval(source);
                expectSuspended((SuspendedEvent event) -> {
                    assertEquals(importingFile.toUri(), event.getSourceSection().getSource().getURI());
                    DebugStackFrame frame = event.getTopStackFrame();
                    assertEquals(4, frame.getSourceSection().getStartLine());
                    event.prepareStepInto(1);
                });
                expectSuspended((SuspendedEvent event) -> {
                    assertEquals(importedFile.toUri(), event.getSourceSection().getSource().getURI());
                    DebugStackFrame frame = event.getTopStackFrame();
                    assertEquals(2, frame.getSourceSection().getStartLine());
                    event.prepareContinue();
                });
            }
            tester.expectDone();
            // Test that breakpoint on the imported file is hit:
            try (DebuggerSession session = tester.startSession()) {
                Breakpoint breakpoint = Breakpoint.newBuilder(importedFile.toUri()).lineIs(2).build();
                session.install(breakpoint);
                tester.startEval(source);
                expectSuspended((SuspendedEvent event) -> {
                    DebugStackFrame frame = event.getTopStackFrame();
                    assertEquals(2, frame.getSourceSection().getStartLine());
                    checkStack(frame, "sum", "a", "2", "b", "3");
                    event.prepareContinue();
                });
                tester.expectDone();
            }
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private void expectSuspended(SuspendedCallback callback) {
        tester.expectSuspended(callback);
    }

    private static void checkStack(DebugStackFrame frame, String name, String... expectedFrame) {
        assertEquals(name, frame.getName());
        checkDebugValues("variables", frame.getScope().getDeclaredValues(), expectedFrame);
    }

    private static void checkDebugValues(String msg, Iterable<DebugValue> values, String... expectedFrame) {
        Map<String, DebugValue> valMap = new HashMap<>();
        for (DebugValue value : values) {
            valMap.put(value.getName(), value);
        }
        String message = String.format("Frame %s expected %s got %s", msg, Arrays.toString(expectedFrame), values.toString());
        assertEquals(message, expectedFrame.length / 2, valMap.size());
        for (int i = 0; i < expectedFrame.length; i = i + 2) {
            String expectedIdentifier = expectedFrame[i];
            String expectedValue = expectedFrame[i + 1];
            DebugValue value = valMap.get(expectedIdentifier);
            assertNotNull(expectedIdentifier + " not found", value);
            assertEquals(expectedValue, value.as(String.class));
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
