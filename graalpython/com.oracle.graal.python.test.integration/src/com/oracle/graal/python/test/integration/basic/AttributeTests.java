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
package com.oracle.graal.python.test.integration.basic;

import org.junit.Test;

import com.oracle.graal.python.test.integration.PythonTests;

public class AttributeTests {
    @Test
    public void getAttributeDirect() {
        PythonTests.assertPrints("1\n", "class Foo(): pass\n" +
                        "o = Foo()\n" +
                        "o.foo = 1\n" +
                        "print(o.foo)");
    }

    @Test
    public void getAttributeFromClass() {
        PythonTests.assertPrints("1\n", "class Foo(): pass\n" +
                        "Foo.foo = 1\n" +
                        "o = Foo()\n" +
                        "print(o.foo)");
    }

    @Test
    public void getAttributeFromMRO() {
        PythonTests.assertPrints("1\n", "class Foo(): pass\n" +
                        "class Bar(Foo): pass\n" +
                        "Foo.foo = 1\n" +
                        "o = Bar()\n" +
                        "print(o.foo)");
    }

    @Test
    public void getAttributeFromMultiMRO() {
        PythonTests.assertPrints("1\n", "class Foo(): pass\n" +
                        "class Bar(): pass\n" +
                        "class Baz(Foo, Bar): pass\n" +
                        "Foo.foo = 1\n" +
                        "o = Baz()\n" +
                        "print(o.foo)");
    }

    @Test
    public void getAttributeFromMultiMRO2() {
        PythonTests.assertPrints("a\n", "class A(): pass\n" +
                        "class B(): pass\n" +
                        "class C(A, B): pass\n" +
                        "A.foo = 'a'\n" +
                        "B.foo = 'b'\n" +
                        "o = C()\n" +
                        "print(o.foo)");
    }

    @Test
    public void getAttributeFromMultiMRO3() {
        PythonTests.assertPrints("b\n", "class A(): pass\n" +
                        "class B(): pass\n" +
                        "class C(B, A): pass\n" +
                        "A.foo = 'a'\n" +
                        "B.foo = 'b'\n" +
                        "o = C()\n" +
                        "print(o.foo)");
    }

    @Test
    public void getMagicAttribute() {
        PythonTests.assertPrints("{}\n", "class A(): pass\n" +
                        "o = A()\n" +
                        "print(o.__dict__)");
    }

    @Test
    public void setMagicAttribute() {
        PythonTests.assertPrints("__setattr__ called\n" +
                        "1\n",
                        "class A():\n" +
                                        "  def __setattr__(self, key, value):\n" +
                                        "    print('__setattr__ called')\n" +
                                        "    return object.__setattr__(self, key, value)\n" +
                                        "\n" +
                                        "o = A()\n" +
                                        "o.foo = 1\n" +
                                        "print(o.foo)\n");
    }

    @Test
    public void delMagicAttribute() {
        PythonTests.assertPrints("1\n__delattr__ called\nerror\n",
                        "class A():\n" +
                                        "  def __delattr__(self, key):\n" +
                                        "    print('__delattr__ called')\n" +
                                        "    return object.__delattr__(self, key)\n" +
                                        "\n" +
                                        "o = A()\n" +
                                        "o.foo = 1\n" +
                                        "print(o.foo)\n" +
                                        "del o.foo\n" +
                                        "try:\n" +
                                        "  o.foo\n" +
                                        "except AttributeError:\n" +
                                        "  print('error')\n");
    }

    @Test
    public void complexHasDoc() {
        PythonTests.assertPrints("complex(real[, imag]) -> complex number\n\n" +
                        "Create a complex number from a real part and an optional imaginary part.\n" +
                        "This is equivalent to (real + imag*1j) where imag defaults to 0.\n",
                        "print(complex.__doc__)");
    }
}
