/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.test.grammar;

import org.junit.Test;

import com.oracle.graal.python.test.PythonTests;

public class PositionalOnlyArgTests {

    @Test
    public void optionalPositionalOnly01() {
        String source = "def f(a, b=10, /, c=100):\n" +
                        "  return a + b + c\n" +
                        "print(f(1, 2, 3))";
        PythonTests.assertPrints("6\n", source);
    }

    @Test
    public void optionalPositionalOnly02() {
        String source = "def f(a, b=10, /, c=100):\n" +
                        "  return a + b + c\n" +
                        "print(f(1, 2, c=3))";
        PythonTests.assertPrints("6\n", source);
    }

    @Test
    public void optionalPositionalOnly03() {
        String source = "def f(a, b=10, /, c=100):\n" +
                        "  return a + b + c\n" +
                        "print(f(1, b=2, c=100))";
        PythonTests.assertLastLineError("TypeError: f() got some positional-only arguments passed as keyword arguments: 'b'", source);
    }

    @Test
    public void optionalPositionalOnly04() {
        String source = "def f(a, b=10, /, c=100):\n" +
                        "  return a + b + c\n" +
                        "print(f(1, b=2))";
        PythonTests.assertLastLineError("TypeError: f() got some positional-only arguments passed as keyword arguments: 'b'", source);
    }

    @Test
    public void optionalPositionalOnly05() {
        String source = "def f(a, b=10, /, c=100):\n" +
                        "  return a + b + c\n" +
                        "print(f(1, c=2))";
        PythonTests.assertPrints("13\n", source);
    }

    @Test
    public void posOnlyDefinition01() {
        String source = "def f(a, b, c, /, d, e=1, *, f, g=2):\n" +
                        " pass\n" +
                        "print(f.__code__.co_argcount)\n" +
                        "print(f.__code__.co_posonlyargcount)\n" +
                        "print(f.__defaults__)";
        PythonTests.assertPrints("5\n3\n(1,)\n", source);
    }

    @Test
    public void posOnlyDefinition02() {
        String source = "def f(a, b, c=1, /, d=2, e=3, *, f, g=4):\n" +
                        "  pass\n" +
                        "print(f.__code__.co_argcount)\n" +
                        "print(f.__code__.co_posonlyargcount)\n" +
                        "print(f.__defaults__)";
        PythonTests.assertPrints("5\n3\n(1, 2, 3)\n", source);
    }

    @Test
    public void invalidCall01() {
        String source = "def f(a, b, /, c):\n" +
                        "  pass\n" +
                        "f(1, 2)";
        PythonTests.assertLastLineError("TypeError: f() missing 1 required positional argument: 'c'", source);
    }

    @Test
    public void invalidCall02() {
        String source = "def f(a, b, /, c):\n" +
                        "  pass\n" +
                        "f(1)";
        PythonTests.assertLastLineError("TypeError: f() missing 2 required positional arguments: 'b' and 'c'", source);
    }

    @Test
    public void invalidCall03() {
        String source = "def f(a, b, /, c):\n" +
                        "  pass\n" +
                        "f()";
        PythonTests.assertLastLineError("TypeError: f() missing 3 required positional arguments: 'a', 'b', and 'c'", source);
    }

    @Test
    public void invalidCall04() {
        String source = "def f(a, b, /, c):\n" +
                        "  pass\n" +
                        "f(1,2,3,4)";
        PythonTests.assertLastLineError("TypeError: f() takes 3 positional arguments but 4 were given", source);
    }

    @Test
    public void invalidCall05() {
        String source = "def f(a, b, /, c=3):" +
                        "  pass\n" +
                        "f(1)";
        PythonTests.assertLastLineError("TypeError: f() missing 1 required positional argument: 'b'", source);
    }

    @Test
    public void invalidCall06() {
        String source = "def f(a, b, /, c=3):" +
                        "  pass\n" +
                        "f()";
        PythonTests.assertLastLineError("TypeError: f() missing 2 required positional arguments: 'a' and 'b'", source);
    }

    @Test
    public void invalidCall07() {
        String source = "def f(a, b, /, c=3):" +
                        "  pass\n" +
                        "f(1,2,3,4)";
        PythonTests.assertLastLineError("TypeError: f() takes from 2 to 3 positional arguments but 4 were given", source);
    }

    @Test
    public void invalidCall08() {
        String source = "def f(a, b, /, c, *, d, e):" +
                        "  pass\n" +
                        "f(1, 2, 3, e=2)";
        PythonTests.assertLastLineError("TypeError: f() missing 1 required keyword-only argument: 'd'", source);
    }

    @Test
    public void invalidCall09() {
        String source = "def f(a, b, /, c, *, d, e):" +
                        "  pass\n" +
                        "f(1, 2, 3)";
        PythonTests.assertLastLineError("TypeError: f() missing 2 required keyword-only arguments: 'd' and 'e'", source);
    }

    @Test
    public void invalidCall10() {
        String source = "def f(a, b, /, c, *, d, e):" +
                        "  pass\n" +
                        "f(1, 2)";
        PythonTests.assertLastLineError("TypeError: f() missing 1 required positional argument: 'c'", source);
    }

    @Test
    public void invalidCall11() {
        String source = "def f(a, b, /, c, *, d, e):" +
                        "  pass\n" +
                        "f(1)";
        PythonTests.assertLastLineError("TypeError: f() missing 2 required positional arguments: 'b' and 'c'", source);
    }

    @Test
    public void invalidCall12() {
        String source = "def f(a, b, /, c, *, d, e):" +
                        "  pass\n" +
                        "f()";
        PythonTests.assertLastLineError("TypeError: f() missing 3 required positional arguments: 'a', 'b', and 'c'", source);
    }

    @Test
    public void invalidCall13() {
        String source = "def f(a, b, /, c, *, d, e):" +
                        "  pass\n" +
                        "f(1, 2, 3, 4, 5, 6, d=7, e=8)";
        PythonTests.assertLastLineError("TypeError: f() takes 3 positional arguments but 6 positional arguments (and 2 keyword-only arguments) were given", source);
    }

    @Test
    public void invalidCall14() {
        String source = "def f(a, b, /, c, *, d, e):" +
                        "  pass\n" +
                        "f(1, 2, 3, d=1, e=4, f=56)";
        PythonTests.assertLastLineError("TypeError: f() got an unexpected keyword argument 'f'", source);
    }

    @Test
    public void lambda01() {
        String source = "x = lambda a, /, b: a + b\n" +
                        "print(x(1,2))\n" +
                        "print(x(1,b=2))";
        PythonTests.assertPrints("3\n3\n", source);
    }

    @Test
    public void lambda02() {
        String source = "x = lambda a, /, b=2: a + b\n" +
                        "print(x(1))";
        PythonTests.assertPrints("3\n", source);
    }

    @Test
    public void lambda03() {
        String source = "x = lambda a, b, /: a + b\n" +
                        "print(x(1, 2))";
        PythonTests.assertPrints("3\n", source);
    }

    @Test
    public void lambda04() {
        String source = "x = lambda a, b, /, : a + b\n" +
                        "print(x(1, 2))";
        PythonTests.assertPrints("3\n", source);
    }

    @Test
    public void postionalOnlyOverlapsWithKwargs() {
        String source = "def f(a, b, /, **kwargs):" +
                        "  print(a, b, kwargs.get('a'))\n" +
                        "f(1, 2, a=3)";
        PythonTests.assertPrints("1 2 3\n", source);
    }
}
