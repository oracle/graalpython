/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates.
 * Copyright (c) 2014, Regents of the University of California
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
package com.oracle.graal.python.test.integration.builtin;

import static com.oracle.graal.python.test.integration.PythonTests.assertPrints;

import org.junit.Test;

public class SpecialMethodTests {

    @Test
    public void __add__0() {
        String source = "class Num:\n" + //
                        "  def __init__(self, n):\n" + //
                        "    self.n = n\n" + //
                        "  def __add__(self, other):\n" + //
                        "    return Num(self.n + other.n)\n" + //
                        "  def __repr__(self):\n" + //
                        "    return str(self.n)\n" + //
                        "" + //
                        "n0 = Num(42)\n" + //
                        "n1 = Num(1)\n" + //
                        "print(n0 + n1)\n";
        assertPrints("43\n", source);
    }

    @Test
    public void __add__1() {
        String source = "class Num:\n" + //
                        "  def __init__(self, n):\n" + //
                        "    self.n = n\n" + //
                        "  def __add__(self, other):\n" + //
                        "    return Num(self.n + other)\n" + //
                        "  def __repr__(self):\n" + //
                        "    return str(self.n)\n" + //
                        "" + //
                        "n0 = Num(42)\n" + //
                        "print(n0 + 1)\n";
        assertPrints("43\n", source);
    }

    @Test
    public void __add__2() {
        String source = "class Num:\n" + //
                        "  def __init__(self, n):\n" + //
                        "    self.n = n\n" + //
                        "  def __radd__(self, other):\n" + //
                        "    return Num(self.n + other)\n" + //
                        "  def __repr__(self):\n" + //
                        "    return str(self.n)\n" + //
                        "" + //
                        "n0 = Num(42)\n" + //
                        "print(1 + n0)\n";
        assertPrints("43\n", source);
    }

    @Test
    public void __add__And__rand__Polymorphic() {
        String source = "class Num:\n" + //
                        "  def __init__(self, n):\n" + //
                        "    self.n = n\n" + //
                        "  def __add__(self, other):\n" + //
                        "    return Num(self.n + other)\n" + //
                        "  def __radd__(self, other):\n" + //
                        "    return Num(self.n + other)\n" + //
                        "  def __repr__(self):\n" + //
                        "    return str(self.n)\n" + //
                        "" + //
                        "def doAdd(left, right):\n" + //
                        "  return left + right\n" + //
                        "print(doAdd(Num(42), 1))\n" + //
                        "print(doAdd(1, Num(42)))\n";
        assertPrints("43\n43\n", source);
    }

    @Test
    public void __sub__() {
        String source = "class Num:\n" + //
                        "  def __init__(self, n):\n" + //
                        "    self.n = n\n" + //
                        "  def __sub__(self, other):\n" + //
                        "    return Num(self.n - other.n)\n" + //
                        "  def __repr__(self):\n" + //
                        "    return str(self.n)\n" + //
                        "" + //
                        "n0 = Num(42)\n" + //
                        "n1 = Num(1)\n" + //
                        "print(n0 - n1)\n";
        assertPrints("41\n", source);
    }

    @Test
    public void __eq__() {
        String source = "class Num:\n" + //
                        "  def __init__(self, n):\n" + //
                        "    self.n = n\n" + //
                        "  def __eq__(self, other):\n" + //
                        "    return type(self) == type(other) and self.n == other.n\n" + //
                        "  def __repr__(self):\n" + //
                        "    return str(self.n)\n" + //
                        "" + //
                        "n0 = Num(1)\n" + //
                        "n1 = Num(1)\n" + //
                        "print(n0 == n1)\n" + //
                        "print(object() == object())\n";
        assertPrints("True\nFalse\n", source);
    }

    @Test
    public void __len__() {
        String source = "class Num:\n" + //
                        "  def __init__(self, n):\n" + //
                        "    self.n = n\n" + //
                        "  def __len__(self):\n" + //
                        "    return self.n\n" + //
                        "" + //
                        "n0 = Num(42)\n" + //
                        "print(len(n0))\n";
        assertPrints("42\n", source);
    }

    @Test
    public void __call__global() {
        String source = "class Num:\n" + //
                        "  def __init__(self, n):\n" + //
                        "    self.n = n\n" + //
                        "  def __call__(self):\n" + //
                        "    print(self.n)\n" + //
                        "" + //
                        "n0 = Num(42)\n" + //
                        "n0()\n";
        assertPrints("42\n", source);
    }

    @Test
    public void __call__local() {
        String source = "class Num:\n" + //
                        "  def __init__(self, n):\n" + //
                        "    self.n = n\n" + //
                        "  def __call__(self):\n" + //
                        "    print(self.n)\n" + //
                        "\n" + //
                        "def docall(num):\n" + //
                        "  num()\n" + //
                        "docall(Num(42))\n";
        assertPrints("42\n", source);
    }

    @Test
    public void __len__instance() {
        String source = "class C: pass\n" +
                        "c = C()\n" +
                        "c.__len__ = lambda x: 5\n" +
                        "try:\n" +
                        "  len(c)\n" +
                        "except TypeError:\n" +
                        "  print('caught')\n";
        assertPrints("caught\n", source);
    }

    @Test
    public void __bool__anything() {
        assertPrints("true\n", "print('true' if object() else 'false')");
    }

    @Test
    public void __getattribute__special() {
        String source = "class C:\n" +
                        "  def __len__(self):\n" +
                        "    return 10\n" +
                        "  def __getattribute__(self, name):\n" +
                        "    print('Class getattribute invoked')\n" +
                        "    return object.__getattribute__(self, name)\n" +
                        "c = C()\n" +
                        "print(c.__len__())\n" +
                        "print(len(c))\n";
        assertPrints("Class getattribute invoked\n10\n10\n", source);
    }

    @Test
    public void __neg__() {
        assertPrints("(-0-1j)\n", "print(-(1j))");
    }

    @Test
    public void __pos__special() {
        assertPrints("pos\n", "" +
                        "class C:\n" +
                        "    def __pos__(self):\n" +
                        "        return 'pos'\n" +
                        "print(+C())");
    }
}
