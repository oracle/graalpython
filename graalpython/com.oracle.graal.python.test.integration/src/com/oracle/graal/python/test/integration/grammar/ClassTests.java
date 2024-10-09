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

import static com.oracle.graal.python.test.integration.PythonTests.assertPrintContains;
import static com.oracle.graal.python.test.integration.PythonTests.assertPrints;

import org.junit.Test;

public class ClassTests {

    @Test
    public void emptyClass() {
        String source = "class Foo:\n" + //
                        "    pass\n";

        assertPrints("", source);
    }

    @Test
    public void simpleClass() {
        String source = "class Foo:\n" + //
                        "    def __init__(self, num):\n" + //
                        "        self.num = num\n" + //
                        "\n";

        assertPrints("", source);
    }

    @Test
    public void classInstantiate() {
        String source = "class Foo:\n" + //
                        "    def __init__(self, num):\n" + //
                        "        self.num = num\n" + //
                        "\n" + //
                        "Foo(42)\n";

        assertPrints("", source);
    }

    @Test
    public void instanceAttributes() {
        String source = "class Foo:\n" + //
                        "    def __init__(self, num):\n" + //
                        "        self.num = num\n" + //
                        "\n" + //
                        "foo = Foo(42)\n" + //
                        "print(foo.num)\n";

        assertPrints("42\n", source);
    }

    @Test
    public void classAttribute1() {
        String source = "class Foo:\n" + //
                        "    class_attr = 2\n" + //
                        "    def __init__(self, num):\n" + //
                        "       self.num = num\n" + //
                        "\n" + //
                        "foo = Foo(42)\n" + //
                        "print(foo.num)\n" + //
                        "print(Foo.class_attr)\n" + //
                        "print(foo.class_attr)\n";

        assertPrints("42\n2\n2\n", source);
    }

    @Test
    public void classAttribute2() {
        String source = "class Foo:\n" + //
                        "    class_attr = AssertionError\n" + //
                        "\n" + //
                        "print(Foo.class_attr)\n";
        assertPrints("<class 'AssertionError'>\n", source);
    }

    @Test
    public void classAttribute3() {
        String source = "class Foo:\n" + //
                        "    def assertEqual():\n" + //
                        "        pass\n" + //
                        "    class_attr = assertEqual\n" + //
                        "\n" + //
                        "print(Foo.class_attr)\n";
        assertPrintContains("<function Foo.assertEqual", source);
    }

    @Test
    public void userClassInheritance() {
        String source = "class ClassA(object):\n" + //
                        "    pass\n" + //
                        "\n" + //
                        "class ClassB(ClassA):\n" + //
                        "    pass\n" + //
                        "";

        assertPrints("", source);
    }

    @Test
    public void scriptClassTest() {
        String source = "class Foo:\n" + //
                        "  def __init__(self, num):\n" + //
                        "    self.num = num\n" + //
                        "\n" + //
                        "  def showNum(self):\n" + //
                        "    print(self.num)\n" + //
                        "foo = Foo(42)\n" + //
                        "foo.showNum()";
        assertPrints("42\n", source);
    }

    @Test
    public void defaultArgInMethod() {
        String source = "class TestSuite():\n" + //
                        "    def assertTrue(self, arg, msg=None):\n" + //
                        "        print(\"arg\", arg)\n" + //
                        "        print(\"msg\", msg)\n" + //
                        "testSuite = TestSuite()\n" + //
                        "testSuite.assertTrue(1 < 2, \"1 is not less than 2\")\n";

        assertPrints("arg True\nmsg 1 is not less than 2\n", source);
    }

    @Test
    public void keywordArgInMethod() {
        String source = "class TestSuite():\n" + //
                        "    def assertTrue(self, arg, msg=None):\n" + //
                        "        print(\"arg\", arg)\n" + //
                        "        print(\"msg\", msg)\n" + //
                        "testSuite = TestSuite()\n" + //
                        "testSuite.assertTrue(1 < 2, msg=\"1 is not less than 2\")\n";

        assertPrints("arg True\nmsg 1 is not less than 2\n", source);
    }

    @Test
    public void classAttributeAsArgTest() {
        String source = "class Foo:\n" + //
                        "  def __init__(self, num):\n" + //
                        "    self.num = num\n" + //
                        "  def showNum(self,num):\n" + //
                        "    bar = [100,200]\n" + //
                        "    print(bar[num])\n" + //
                        "  def boo(self):\n" + //
                        "    self.showNum(self.num)\n" + //
                        "\n" + //
                        "foo = Foo(1)\n" + //
                        "foo.boo()\n";
        assertPrints("200\n", source);
    }

    @Test
    public void chainedAssignmentWithObject() {
        String source = "class Foo:\n" + //
                        "  def __init__(self, num):\n" + //
                        "    self.num = num\n" + //
                        "    self.child = None\n" + //
                        "a = Foo(1)\n" + //
                        "b = a.child = Foo(2)\n" + //
                        "b.num = 4\n" + //
                        "print(a.child.num)\n";
        assertPrints("4\n", source);
    }

    /**
     * zwei: Disabled before MRO is wired in for attribute access look up.
     */
    // @Test
    public void multipleInheritance() {
        String source = "class common:\n" + //
                        "    def __repr__(self):\n" + //
                        "        return 'common'\n" + //
                        "class labeling:\n" + //
                        "    pass\n" + //
                        "class basegraph:\n" + //
                        "    pass\n" + //
                        "class graph(basegraph, common, labeling):\n" + //
                        "    def __init__(self):\n" + //
                        "        pass\n" + //
                        "print(repr(graph()))\n";
        assertPrints("common\n", source);
    }

    @Test
    public void classDecorator() {
        String source = "def wrapper(cls):\n" + //
                        "  orig_init = cls.__init__\n" + //
                        "  def new_init(self):\n" + //
                        "    print('wrapper')\n" + //
                        "    orig_init(self)\n" + //
                        "  cls.__init__ = new_init\n" + //
                        "  return cls\n" + //
                        "@wrapper\n" + //
                        "class Foo:\n" + //
                        "  def __init__(self):\n" + //
                        "    print('Foo')\n" + //
                        "Foo()\n";
        assertPrints("wrapper\nFoo\n", source);
    }

}
