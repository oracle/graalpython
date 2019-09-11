/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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
package com.oracle.graal.python.test.builtin;

import static com.oracle.graal.python.test.PythonTests.assertPrintContains;
import static com.oracle.graal.python.test.PythonTests.assertPrints;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.graalvm.polyglot.Value;
import org.junit.Test;

import com.oracle.graal.python.test.PythonTests;

public class BuiltinFunctionTests {

    @Test
    public void builtinLookup() {
        String source = "for i in range(5):\n" + //
                        "    print(abs)\n";

        assertPrints("<built-in function abs>\n<built-in function abs>\n<built-in function abs>\n<built-in function abs>\n<built-in function abs>\n", source);
    }

    @Test
    public void allTest() {
        String source = "x = all([10, 0, 30])\n" + "print(x)";
        assertPrints("False\n", source);
    }

    @Test
    public void anyTest() {
        String source = "x = any([0, 10, 30])\n" + "print(x)";
        assertPrints("True\n", source);
    }

    @Test
    public void roundTest0() {
        String source = "x = round(2.5)\n" + "print(x)";
        assertPrints("3\n", source);
    }

    @Test
    public void roundTest1() {
        String source = "x = round(2.4)\n" + "print(x)";
        assertPrints("2\n", source);
    }

    @Test
    public void roundTest2() {
        String source = "x = round(2.56,1)\n" + "print(x)";
        assertPrints("2.6\n", source);
    }

    @Test
    public void absTest() {
        String source = "x = abs(10)\n" + "print(x)\n" +

                        "x = abs(10.25)\n" + "print(x)\n" +

                        "x = abs(1 + 2j)\n" + "print(x)\n";

        assertPrints("10\n10.25\n2.23606797749979\n", source);
    }

    @Test
    public void absNegative() {
        String source = "val = abs(-42)\n" + //
                        "print(val)\n";

        assertPrints("42\n", source);
    }

    @Test
    public void boolTest() {
        String source = "x = bool(10)\n" + "print(x)\n" +

                        "x = bool(0.0)\n" + "print(x)\n" +

                        "x = bool()\n" + "print(x)\n";

        assertPrints("True\nFalse\nFalse\n", source);
    }

    @Test
    public void chrTest() {
        String source = "x = chr(65)\n" + "print(x)";
        assertPrints("A\n", source);
    }

    @Test
    public void complexTest() {
        String source = "x = complex(2, 3)\n" + //
                        "print(x)\n" + //

                        "x = complex(3.4, 4.9)\n" + //
                        "print(x)\n" + //

                        "x = complex(2)\n" + //
                        "print(x)\n" + //

                        "x = complex()\n" + //
                        "print(x)\n";

        assertPrints("(2+3j)\n(3.4+4.9j)\n(2+0j)\n0j\n", source);
    }

    @Test
    public void enumerateTest() {
        String source = "list1 = [1000, 2000, 3000]\n" + "for s in enumerate(list1):\n" + "\tprint(s)\n";
        assertPrints("(0, 1000)\n(1, 2000)\n(2, 3000)\n", source);
    }

    @Test
    public void floatTest() {
        String source = "x = float(2)\n" + "print(x)\n" +

                        "x = float('+1.23')\n" + "print(x)\n" +

                        "x = float('   -12345')\n" + "print(x)\n" +

                        "x = float('1e-003')\n" + "print(x)\n" +

                        "x = float('+1E6')\n" + "print(x)\n" +

                        "x = float()\n" + "print(x)\n";

        assertPrints("2.0\n1.23\n-12345.0\n0.001\n1000000.0\n0.0\n", source);
    }

    @Test
    public void intTest() {
        String source = "x = int(3)\n" + "print(x)\n" +

                        "x = int(2.9)\n" + "print(x)\n" +

                        "x = int(\"4\")\n" + "print(x)\n" +

                        "x = int(2147483648)\n" + "print(x)\n" +

                        "x = int()\n" + "print(x)\n";

        assertPrints("3\n2\n4\n2147483648\n0\n", source);
    }

    @Test
    public void isinstanceTest() {
        String source = "class Student:\n\t" +

                        "def __init__(self, id):\n\t\t" + "self.id = id\n" +

                        "student = Student(10)\n" +

                        "x = isinstance(student, Student)\n" +

                        "print(x)\n";

        assertPrints("True\n", source);
    }

    @Test
    public void iterTest() {
        String source = "for element in iter(\"hello\"):\n\t" +

                        "print(element)\n" +

                        "for element in iter([10, 20, 30]):\n\t" +

                        "print(element)\n";

        assertPrints("h\ne\nl\nl\no\n10\n20\n30\n", source);
    }

    @Test
    public void lenTest() {
        String source = "value = \"hello\"\n" + //
                        "print(len(value))\n" + //

                        "value = (100, 200, 300)\n" + //
                        "print(len(value))\n" + //

                        "value = ['a', 'b', 'c', 'd']\n" + //
                        "print(len(value))\n" + //

                        "value = {'id' : 17, 'name' : \"gulfem\"}\n" + //
                        "print(len(value))\n";

        assertPrints("5\n3\n4\n2\n", source);
    }

    @Test
    public void maxTest() {
        String source = "x = max(10, 20)\n" + "print(x)\n" +

                        "x = max(20.8, 10.3)\n" + "print(x)";

        assertPrints("20\n20.8\n", source);
    }

    @Test
    public void nextTest() {
        String source = "x = iter([10, 20, 30])\n" +

                        "print(next(x))\n" +

                        "print(next(x))\n" +

                        "print(next(x))\n";

        assertPrints("10\n20\n30\n", source);
    }

    @Test
    public void rangeTest() {
        String source = "print(list(range(10)))\n" +

                        "print(list(range(1, 11)))\n" +

                        "print(list(range(0, 30, 5)))\n";

        assertPrints("[0, 1, 2, 3, 4, 5, 6, 7, 8, 9]\n[1, 2, 3, 4, 5, 6, 7, 8, 9, 10]\n[0, 5, 10, 15, 20, 25]\n", source);
    }

    @Test
    public void zipTest() {
        String source = "for s in zip('ABC', '123'):\n" + //
                        "    print(s)\n";
        assertPrints("('A', '1')\n('B', '2')\n('C', '3')\n", source);
    }

    /**
     * extracted from meteor3.py.
     */
    @Test
    public void frozenSetFromRangeLen() {
        String source = "board = [1,2,3,4,5]\n" + //
                        "free = frozenset(range(len(board)))\n" + //
                        "print(free)";
        assertPrints("frozenset({0, 1, 2, 3, 4})\n", source);
    }

    @Test
    public void sum() {
        String source = "print(sum(x for x in range(10)))";
        assertPrints("45\n", source);
    }

    @Test
    public void divmodInt() {
        String source = "print(divmod(42, 3))";
        assertPrints("(14, 0)\n", source);
    }

    @Test
    public void printE() {
        String source = "x=0.0000000012;print(x);print(str(0.000000000012))";
        assertPrints("1.2e-09\n1.2e-11\n", source);
    }

    @Test
    public void printE2() {
        String source = "x=[0.0001000002, 0.0000000012, 0.000000000012];print(x);";
        assertPrints("[0.0001000002, 1.2e-09, 1.2e-11]\n", source);
    }

    @Test
    public void divmodDouble() {
        String source = "print(divmod(4.2, 3.1))";
        assertPrints("(1.0, 1.1)\n", source);
    }

    @Test
    public void reverseRange() {
        String source = "for i in reversed(range(3)):" + //
                        "    print(i)";
        assertPrints("2\n1\n0\n", source);
    }

    @Test
    public void reverseSequence() {
        String source = "for i in reversed([1,2,3]):" + //
                        "    print(i)";
        assertPrints("3\n2\n1\n", source);
    }

    @Test
    public void repr0() {
        String source = "print(repr(object()))";
        assertPrintContains("<object object at", source);
    }

    @Test
    public void repr1() {
        String source = "class Foo:\n" + //
                        "  def __repr__(self):\n" + //
                        "    return 'foo is a fool'\n" + //
                        "print(repr(Foo()))";
        assertPrints("foo is a fool\n", source);
    }

    @Test
    public void repr2() {
        String source = "class Foo:\n" + //
                        "  def __repr__(self):\n" + //
                        "    return 'foo is a fool'\n" + //
                        "print(Foo())";
        assertPrints("foo is a fool\n", source);
    }

    @Test
    public void str() {
        String source = "class Foo:\n" + //
                        "  def __str__(self):\n" + //
                        "    return 'foo is a fool'\n" + //
                        "print(str(Foo()))";
        assertPrints("foo is a fool\n", source);
    }

    @Test
    public void printTest() {
        String source = "a = 1;print('a=',a)";
        assertPrintContains("a= 1", source);
    }

    @Test
    public void isinstance() {
        String source = "print(isinstance([], bytes))\n";
        assertPrints("False\n", source);
    }

    @Test
    public void id() {
        String source = "id1 = id([])\n" + //
                        "id2 = id([])\n" + //
                        "print(id1 == id2)";
        assertPrints("False\n", source);
    }

    @Test
    public void cellType() {

        String source = "def foo(n):\n" +
                        "    m = n + 1\n" +
                        "    def bar():\n" +
                        "        return m + 1\n" +
                        "    return bar\n" +
                        "\n" +
                        "foo(3).__closure__[0]";
        Value eval = PythonTests.eval(source);
        assertTrue(eval.getMetaObject().hasMember("__name__"));
        Value type = eval.getMetaObject().getMember("__name__");
        assertTrue(type.isString());
        assertEquals("cell", type.asString());
    }

}
