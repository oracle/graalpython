/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates.
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
package com.oracle.graal.python.test.integration.grammar;

import static com.oracle.graal.python.test.integration.PythonTests.assertLastLineError;
import static com.oracle.graal.python.test.integration.PythonTests.assertLastLineErrorContains;
import static com.oracle.graal.python.test.integration.PythonTests.assertPrints;

import org.junit.Test;

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

    @Test
    public void kwargsMerge() {
        assertPrints("{'a': 1, 'b': 2, 'c': 3, 'd': 4, 'e': 5}\n", "\n" +
                        "def foo(**kwargs):\n" +
                        "  print(kwargs)\n" +
                        "foo(a=1, **{'b': 2, 'c': 3}, d=4, **{'e': 5})\n");

        assertPrints("32\n0\n31\n", "\n" +
                        "def f(**kw): \n" +
                        "  print(len(kw)) == 32\n" +
                        "  print(kw[\"a0\"])\n" +
                        "  print(kw[\"a31\"])  \n" +
                        "f(a0=0, a1=1, a2=2, a3=3, a4=4, a5=5, a6=6, a7=7, a8=8, a9=9, a10=10, a11=11, a12=12, a13=13, a14=14, a15=15, a16=16, a17=17, a18=18, a19=19, a20=20, a21=21, a22=22, a23=23, a24=24, a25=25, a26=26, a27=27, a28=28, a29=29, a30=30, a31=31)\n");
    }

    @Test
    public void kwargsDuplicate() {
        assertLastLineErrorContains("TypeError: __main__.foo() got multiple values for keyword argument 'd'", "\n" +
                        "def foo(**kwargs):\n" +
                        "  print(kwargs)\n" +
                        "foo(a=1, **{'b': 2, 'c': 3}, d=4, **{'d': 5})\n");
    }

    private static String call(String source, String args) {
        return String.format("%s\nf(%s)", source, args);
    }

    private static String mkEmptyFunc(String argsDef) {
        return String.format("def f(%s): pass", argsDef);
    }

    @Test
    public void f0() {
        String source = mkEmptyFunc("");
        assertPrints("", call(source, ""));
        assertLastLineErrorContains("TypeError", call(source, "1, 2, 3"));
        assertLastLineErrorContains("TypeError", call(source, "a=1, b=1"));
        assertLastLineErrorContains("TypeError", call(source, "1,2,3,4, a=1, b=1"));
    }

    @Test
    public void f1() {
        String source = mkEmptyFunc("*args");
        assertPrints("", call(source, ""));
        assertPrints("", call(source, "1, 2, 3"));
        assertLastLineErrorContains("TypeError", call(source, "a=1"));
    }

    @Test
    public void f2() {
        String source = mkEmptyFunc("*args, **kw");
        assertPrints("", call(source, ""));
        assertPrints("", call(source, "1, 2, 3"));
        assertPrints("", call(source, "1, 2, 3, a=1, b=2"));
        assertPrints("", call(source, "a=1, b=2"));
    }

    @Test
    public void f3() {
        String source = mkEmptyFunc("**kw");
        assertPrints("", call(source, ""));
        assertLastLineErrorContains("TypeError", call(source, "1, 2, 3"));
        assertLastLineErrorContains("TypeError", call(source, "1, 2, 3, a=1, b=2"));
        assertPrints("", call(source, "a=1, b=2"));
    }

    @Test
    public void f4() {
        String source = mkEmptyFunc("foo=10");
        assertPrints("", call(source, ""));
        assertPrints("", call(source, "20"));
        assertPrints("", call(source, "foo=20"));
        assertLastLineErrorContains("TypeError", call(source, "a=10"));
    }

    @Test
    public void f5() {
        String source = mkEmptyFunc("foo=10, *args");
        assertLastLineErrorContains("SyntaxError", call(source, "foo=1, 2, 3"));
        assertPrints("", call(source, "foo=20"));
        assertLastLineErrorContains("TypeError", call(source, "a=10"));
        assertPrints("", call(source, "1, 2, 3"));
    }

    @Test
    public void f6() {
        String source = mkEmptyFunc("foo=10, *args, **kw");
        assertPrints("", call(source, "foo=20"));
        assertPrints("", call(source, "foo=20, a=2"));
        assertLastLineErrorContains("SyntaxError", call(source, "foo=1, 2, 3, a=2"));
    }

    @Test
    public void f7() {
        String source = mkEmptyFunc("foo=10, **kw");
        assertLastLineErrorContains("TypeError", call(source, "1, 2"));
        assertPrints("", call(source, ""));
        assertPrints("", call(source, "1"));
        assertPrints("", call(source, "1, a=2"));
        assertPrints("", call(source, "foo=1, a=2"));
    }

    @Test
    public void f8() {
        String source = mkEmptyFunc("a, b");
        assertLastLineErrorContains("TypeError", call(source, ""));
        assertLastLineErrorContains("TypeError", call(source, "1"));
        assertPrints("", call(source, "1, 2"));
        assertLastLineErrorContains("TypeError", call(source, "1, 2, 3"));
        assertLastLineErrorContains("TypeError", call(source, "c=1"));
    }

    @Test
    public void f9() {
        String source = mkEmptyFunc("a, b, *args");
        assertLastLineErrorContains("TypeError", call(source, ""));
        assertPrints("", call(source, "1, 2, 3"));
    }

    @Test
    public void f10() {
        String source = mkEmptyFunc("a, b, *args, **kw");
        assertPrints("", call(source, "1,2,3,4,c=1"));
        assertLastLineErrorContains("TypeError", call(source, "1,2,3,4,a=1"));
    }

    @Test
    public void f11() {
        String source = mkEmptyFunc("a, b, **kw");
        assertPrints("", call(source, "a=1,b=2"));
        assertPrints("", call(source, "a=1,b=2,c=3"));
        assertLastLineError("SyntaxError: keyword argument repeated: a", call(source, "a=1, b=2, a=3"));
        assertLastLineErrorContains("TypeError", call(source, "1, b=2, a=3"));
    }

    @Test
    public void f12() {
        String source = mkEmptyFunc("a, b, foo=10");
        assertPrints("", call(source, "1,2"));
        assertPrints("", call(source, "1,2,3"));
        assertLastLineErrorContains("TypeError", call(source, "a=1"));
        assertPrints("", call(source, "1,2,foo=3"));
        assertPrints("", call(source, "a=1,b=2,foo=3"));
        assertLastLineErrorContains("SyntaxError", call(source, "a=1, 2, foo=3"));
    }

    @Test
    public void f13() {
        String source = mkEmptyFunc("a, b, foo=10, *args");
        assertPrints("", call(source, "1,2,3,4"));
        assertLastLineErrorContains("TypeError", call(source, "1, 2, foo=3, c=4"));
    }

    @Test
    public void f14() {
        String source = mkEmptyFunc("a, b, foo=10, *args, **kw");
        assertPrints("", call(source, "1, 2, foo=3, c=4"));
        assertPrints("", call(source, "a=1, b=2, foo=3, c=4"));
        assertPrints("", call(source, "a=1, b=2, foo=3"));
        assertPrints("", call(source, "1, 2, 3, c=4"));
        assertPrints("", call(source, "1, 2, 3, 4, 5, 6, 7, d=1"));
        assertLastLineErrorContains("TypeError", call(source, "1, 2, 3, a=4"));
    }

    @Test
    public void f15() {
        String source = mkEmptyFunc("a, b, foo=10, **kw");
        assertPrints("", call(source, "1, 2, foo=3, c=4"));
        assertPrints("", call(source, "a=1, b=2, foo=3, c=4"));
        assertPrints("", call(source, "a=1, b=2, foo=3"));
        assertPrints("", call(source, "1, 2, 3, c=4"));
        assertLastLineErrorContains("TypeError", call(source, "1, 2, 3, 4, 5, 6, 7, d=1"));
    }

    @Test
    public void f16() {
        String source = mkEmptyFunc("*, a");
        assertLastLineErrorContains("TypeError", call(source, ""));
        assertLastLineErrorContains("TypeError", call(source, "1"));
        assertPrints("", call(source, "a=1"));
        assertLastLineErrorContains("TypeError", call(source, "a=1, b=1"));
    }

    @Test
    public void f17() {
        String source = mkEmptyFunc("*, a=5");
        assertPrints("", call(source, ""));
        assertPrints("", call(source, "a=1"));
        assertLastLineErrorContains("TypeError", call(source, "b=1"));
        assertLastLineErrorContains("TypeError", call(source, "1"));
    }

    @Test
    public void f18() {
        String source = mkEmptyFunc("*, a=5, b");
        assertLastLineErrorContains("TypeError", call(source, "1, 2"));
        assertLastLineErrorContains("SyntaxError", call(source, "a=1, 2"));
        assertPrints("", call(source, "a=1, b=2"));
        assertLastLineErrorContains("TypeError", call(source, "a=1,c=2"));
        assertLastLineErrorContains("TypeError", call(source, "1,b=2"));
    }

    @Test
    public void f19() {
        String source = mkEmptyFunc("*, a, b=5");
        assertPrints("", call(source, "a=1"));
        assertPrints("", call(source, "a=1, b=2"));
        assertLastLineErrorContains("TypeError", call(source, "1,b=2"));
        assertLastLineErrorContains("TypeError", call(source, "1"));
    }

    @Test
    public void f20() {
        String source = mkEmptyFunc("*, a, b=5, **kw");
        assertPrints("", call(source, "a=1"));
        assertPrints("", call(source, "a=1, b=2"));
        assertPrints("", call(source, "a=1, b=2, c=3"));
        assertLastLineError("SyntaxError: keyword argument repeated: a", call(source, "a=1,b=2,a=3"));
        assertLastLineErrorContains("TypeError", call(source, "1, b=2"));
        assertLastLineErrorContains("TypeError", call(source, "1"));
    }

    @Test
    public void f21() {
        String source = mkEmptyFunc("*args, a");
        assertLastLineErrorContains("TypeError", call(source, "1,2,3"));
        assertPrints("", call(source, "1,2,a=3"));
        assertPrints("", call(source, "a=3"));
    }

    @Test
    public void f22() {
        String source = mkEmptyFunc("*args, a=5");
        assertPrints("", call(source, ""));
        assertPrints("", call(source, "a=3"));
        assertPrints("", call(source, "1,2,a=3"));
        assertLastLineErrorContains("TypeError", call(source, "a=2, b=3"));
    }

    @Test
    public void f23() {
        String source = mkEmptyFunc("*args, a=5, b");
        assertLastLineErrorContains("TypeError", call(source, "1,2,3"));
        assertPrints("", call(source, "1,2,3,b=4"));
        assertPrints("", call(source, "1,2,a=3,b=4"));
        assertPrints("", call(source, "1,2,b=4,a=3"));
    }

    @Test
    public void f24() {
        String source = mkEmptyFunc("*args, a, b=5");
        assertLastLineErrorContains("TypeError", call(source, "1,2,3"));
        assertPrints("", call(source, "1,2,a=3"));
        assertPrints("", call(source, "1,2,a=3,b=4"));
        assertLastLineErrorContains("TypeError", call(source, "1,2, a=3, b=4, c=5"));
        assertPrints("", call(source, "a=1"));
        assertLastLineErrorContains("TypeError", call(source, "1"));
        assertPrints("", call(source, "a=1, b=2"));
        assertLastLineErrorContains("SyntaxError", call(source, "a=1, 2"));
    }

    @Test
    public void f25() {
        String source = mkEmptyFunc("*args, a, b=5, **kw");
        assertLastLineErrorContains("TypeError", call(source, ""));
        assertLastLineErrorContains("TypeError", call(source, "1,2,3"));
        assertPrints("", call(source, "1,2,3,a=4"));
        assertPrints("", call(source, "1,2,3,a=4,b=5"));
        assertLastLineErrorContains("SyntaxError", call(source, "1,2,3,a=4,5"));
        assertPrints("", call(source, "1,2,3,a=4,b=5,c=6"));
        assertPrints("", call(source, "1,2,3,a=4,c=6"));
        assertLastLineErrorContains("TypeError", call(source, "1,2,3,c=6"));
        assertPrints("", call(source, "a=4,c=6"));
        assertPrints("", call(source, "a=4"));
    }
}
