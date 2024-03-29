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
package com.oracle.graal.python.test.integration.decorator;

import static com.oracle.graal.python.test.integration.PythonTests.assertPrints;

import org.junit.Test;

public class SingleDecoratorTest {

    @Test
    public void decorator_test_wrapper() {
        String source = "\n" + //
                        "def a(x):\n" + //
                        "  def c():\n" + //
                        "    print(\"a\")\n" + //
                        "    x()\n" + //
                        "  return c\n" + //
                        "@a\n" + //
                        "def b():\n" + //
                        "  print(\"b\")\n" + //
                        "b()\n";
        assertPrints("a\nb\n", source);
    }

    @Test
    public void decorator_test_wrapper_arg() {
        String source = "\n" + //
                        "def a(x):\n" + //
                        "  def c(*args):\n" + //
                        "    print(\"a\")\n" + //
                        "    x(*args)\n" + //
                        "  return c\n" + //
                        "@a\n" + //
                        "def b(y):\n" + //
                        "  print(y)\n" + //
                        "b(\"b\")\n";
        assertPrints("a\nb\n", source);
    }

    @Test
    public void decorator_test_wrapper_arg2() {
        String source = "\n" + //
                        "def a(x):\n" + //
                        "  def c(*args):\n" + //
                        "    print(\"a\")\n" + //
                        "    x(*args)\n" + //
                        "  return c\n" + //
                        "@a\n" + //
                        "def b(y):\n" + //
                        "  print(y)\n" + //
                        "b(\"b\")\n" + //
                        "b(\"c\")\n";
        assertPrints("a\nb\na\nc\n", source);
    }

    @Test
    public void decorator_test_wrapper_dec_arg_a() {
        String source = "\n" + //
                        "def a(z):\n" + //
                        "  def c(x):\n" + //
                        "    print(z)\n" + //
                        "    def d(*args):\n" + //
                        "      x(*args)\n" + //
                        "    return d\n" + //
                        "  return c\n" + //
                        "@a(\"a\")\n" + //
                        "def b(y):\n" + //
                        "  print(y)\n" + //
                        "\n";
        assertPrints("a\n", source);
    }

    @Test
    public void decorator_test_wrapper_dec_arg_ab() {
        String source = "\n" + //
                        "def a(z):\n" + //
                        "  def c(x):\n" + //
                        "    def d(*args):\n" + //
                        "      print(z)\n" + //
                        "      x(*args)\n" + //
                        "    return d\n" + //
                        "  return c\n" + //
                        "@a(\"a\")\n" + //
                        "def b(y):\n" + //
                        "  print(y)\n" + //
                        "b(\"b\")\n";
        assertPrints("a\nb\n", source);
    }

    @Test
    public void decorator_test_wrapper_dec_arg_nothing() {
        String source = "\n" + //
                        "def a(z):\n" + //
                        "  def c(x):\n" + //
                        "    def d(*args):\n" + //
                        "      print(z)\n" + //
                        "      x(*args)\n" + //
                        "    return d\n" + //
                        "  return c\n" + //
                        "@a(\"a\")\n" + //
                        "def b(y):\n" + //
                        "  print(y)\n" + //
                        "\n";
        assertPrints("", source);
    }

}
