/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.test.integration.grammar;

import static com.oracle.graal.python.test.integration.PythonTests.assertLastLineError;
import static com.oracle.graal.python.test.integration.PythonTests.assertPrints;

import org.junit.Test;

public class LocalFrameTests {

    @Test
    public void int2Double() {
        String source = "def foo():\n" + //
                        "    a = 42\n" + //
                        "    print(a)\n" + //
                        "    a = 4.2 / a\n" + //
                        "    print(a)\n" + //
                        "\n" + //
                        "foo()\n";

        assertPrints("42\n0.1\n", source);
    }

    @Test
    public void int2BigInteger() {
        String source = "def foo():\n" + //
                        "    a = 42\n" + //
                        "    print(a)\n" + //
                        "    a = 4294929942949294929429\n" + //
                        "    print(a)\n" + //
                        "\n" + //
                        "foo()\n";

        assertPrints("42\n4294929942949294929429\n", source);
    }

    @Test
    public void int2BigInteger2Double() {
        String source = "def foo():\n" + //
                        "    a = 42\n" + //
                        "    print(a)\n" + //
                        "    a = 4294929942949294929429\n" + //
                        "    print(a)\n" + //
                        "    a = a / 4.2\n" + //
                        "    print(a)\n" + //
                        "\n" + //
                        "foo()\n";

        assertPrints("42\n4294929942949294929429\n1.0226023673688797e+21\n", source);
    }

    @Test
    public void bool2Int() {
        String source = "def foo():\n" + //
                        "    a = True\n" + //
                        "    print(a)\n" + //
                        "    a = 42 + True\n" + //
                        "    print(a)\n" + //
                        "\n" + //
                        "foo()\n";

        assertPrints("True\n43\n", source);
    }

    @Test
    public void bool2Float() {
        String source = "def foo():\n" + //
                        "    a = True\n" + //
                        "    print(a)\n" + //
                        "    a = 4.2 + True\n" + //
                        "    print(a)\n" + //
                        "\n" + //
                        "foo()\n";

        assertPrints("True\n5.2\n", source);
    }

    @Test
    public void bool2Complex() {
        String source = "def foo():\n" + //
                        "    a = True\n" + //
                        "    print(a)\n" + //
                        "    a = complex(1, 0) + True\n" + //
                        "    print(a)\n" + //
                        "\n" + //
                        "foo()\n";

        assertPrints("True\n(2+0j)\n", source);
    }

    @Test
    public void unboundLocalError() {
        String source = "def foo(y):\n" + //
                        "    if y > .5: x = 'big'\n" + //
                        "    else: pass\n" + //
                        "    return x\n" + //
                        "\n" + //
                        "foo(0)\n";

        assertLastLineError("UnboundLocalError: local variable 'x' referenced before assignment\n", source);
    }

    @Test
    public void unboundLocalError2() {
        String source = "def test(a):\n" + //
                        "  for i in range(42):\n" + //
                        "    if a == 2 and foo == None:\n" + //
                        "       foo = i\n" + //
                        "    else:\n" + //
                        "       foo = i\n" + //
                        "  return foo\n" + //
                        "test(1)\n" + //
                        "test(2)\n";
        assertLastLineError("UnboundLocalError: local variable 'foo' referenced before assignment\n", source);
    }

    @Test
    public void initToNone() {
        String source = "def foo():\n" + //
                        "    hi = None\n" + //
                        "    hi = 2\n" + //
                        "    list1 = [1,2,3]\n" + //
                        "    print(list1[hi])\n" + //
                        "\n" + //
                        "foo()\n";

        assertPrints("3\n", source);
    }

    @Test
    public void intToComplex() {
        String source = "def foo():\n" + //
                        "    y = 0\n" + //
                        "    for i in range(2):\n" + //
                        "        x = 0\n" + //
                        "        for j in range(2):\n" + //
                        "            x = x + 5j\n" + //
                        "        y = y + x\n" + //
                        "    print(y)\n" + //
                        "\n" + //
                        "foo()\n";

        assertPrints("20j\n", source);
    }
}
