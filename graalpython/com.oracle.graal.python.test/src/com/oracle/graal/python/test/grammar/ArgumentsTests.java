/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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
package com.oracle.graal.python.test.grammar;

import static com.oracle.graal.python.test.PythonTests.*;

import java.nio.file.*;

import org.junit.*;

public class ArgumentsTests {

    @Test
    public void defaultArg1() {
        String source = "def foo(a, b=3):\n" + //
                        "    print(a)\n" + //
                        "    print(b)\n" + //
                        "foo(1)\n";
        assertPrints("1\n3\n", source);
    }

    @Test
    public void defaultArgWithKeywordArg() {
        String source = "def foo(a, b=3):\n" + //
                        "    print(a)\n" + //
                        "    print(b)\n" + //
                        "foo(a=1)\n";
        assertPrints("1\n3\n", source);
    }

    /**
     * TODO: (zwei) Default args are not behaving correctly. Maybe we want to consider using
     * assumptions.
     */
    // @Test
    public void defaultArgUsingVariable() {
        Path script = Paths.get("function-default-args-test.py");
        assertPrints("do stuff A\ndo stuff B\n", script);
    }

    @Test
    public void VarArgs() {
        String source = "\n" + //
                        "def c(x,y):\n" + //
                        "  print(\"x \",x,\" y \",y)\n" + //

                        "def a(*args):\n" + //
                        "  def b(*args):\n" + //
                        "    c(*args)\n" + //
                        "  return b\n" + //

                        "y = a()\n" + //
                        "y(1,2)\n";

        assertPrints("x  1  y  2\n", source);
    }

    @Test
    public void VarArgs2() {
        String source = "\n" + //
                        "def c(x,y):\n" + //
                        "  print(\"x \",x,\" y \",y)\n" + //

                        "def a(x,*args):\n" + //
                        "  def b(y,*args):\n" + //
                        "    c(*args)\n" + //
                        "  return b(*args)\n" + //

                        "a(3,4,1,2)\n";

        assertPrints("x  1  y  2\n", source);
    }

    @Test
    public void VarArgs3() {
        String source = "\n" + //
                        "def c(x,y):\n" + //
                        "  print(\"x \",x,\" y \",y)\n" + //

                        "def a(x,*args):\n" + //
                        "  def b(y,*args):\n" + //
                        "    c(*args)\n" + //
                        "  return b(*args)\n" + //

                        "a(3,4,1,2)\n" + //
                        "a(3,4,10,20)\n";

        assertPrints("x  1  y  2\nx  10  y  20\n", source);
    }

    @Test
    public void VarArgs4() {
        String source = "\n" + //
                        "def c(x,y):\n" + //
                        "  print(\"x \",x,\" y \",y)\n" + //

                        "def a(*args):\n" + //
                        "  c(*args)\n" + //

                        "a(1,2)\n" + //
                        "a(10,20)\n";

        assertPrints("x  1  y  2\nx  10  y  20\n", source);
    }

    @Test
    public void KwArgs() {
        String source = "\n" + //
                        "def c(x,y):\n" + //
                        "  print(\"x \",x,\" y \",y)\n" + //

                        "def a(z,*args,**kwargs):\n" + //
                        "  for arg in args:\n" + //
                        "    print(\"arg \", arg)\n" + //
                        "  def b(w,**kwargs):\n" + //
                        "    c(**kwargs)\n" + //
                        "  return b(**kwargs)\n" + //

                        "a(3,9,y=2,x=1,w=4)\n";

        assertPrints("arg  9\nx  1  y  2\n", source);
    }

    @Test
    public void KwArgs2() {
        assertPrints("{}\n", "\n" +
                        "def update(E=None, **F):\n" +
                        "  print(F)\n" +
                        "update(42)");
    }
}
