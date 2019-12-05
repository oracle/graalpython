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

package com.oracle.graal.python.test.parser;

import java.io.File;
import org.junit.Test;

public class FuncDefTests extends ParserTestBase {

    @Test
    public void args01() throws Exception {
        checkScopeAndTree("def fn(a, b=0, *arg, k1, k2=0): return a + b + k1 + k2 + sum(arg)");
    }
    
    @Test
    public void args02() throws Exception {
        checkSyntaxErrorMessage("def f(a, a): pass", "SyntaxError: duplicate argument 'a' in function definition");
    }

    @Test
    public void functionDoc01() throws Exception {
        checkScopeAndTree();
    }

    @Test
    public void functionDef02() throws Exception {
        checkScopeAndTree("def foo(): \n" + "  return 10\n");
    }

    @Test
    public void functionDef03() throws Exception {
        checkScopeAndTree();
    }

    @Test
    public void functionDef04() throws Exception {
        checkTreeResult("def foo(a, b): \n" + "  return a + b\n");
    }

    @Test
    public void functionDef05() throws Exception {
        checkTreeResult("def foo(par1 = 10): \n" + "  return par1");
    }

    @Test
    public void functionDef06() throws Exception {
        checkTreeResult("def foo(par1, par2 = 22): \n" + "  return par1 * par2");
    }

    @Test
    public void functionDef07() throws Exception {
        checkScopeAndTree();
    }

    @Test
    public void functionDef08() throws Exception {
        checkSyntaxError("def foo8(par1='ahoj', par2): \n" + "  return par1 * par2");
    }

    @Test
    public void functionDef09() throws Exception {
        checkTreeResult("def foo(*args): \n" + "  pass");
    }

    @Test
    public void functionDef10() throws Exception {
        checkTreeResult("def foo(*args): \n" + "  print(args)");
    }

    @Test
    public void functionDef11() throws Exception {
        checkScopeAndTree();
    }

    @Test
    public void functionDef12() throws Exception {
        checkScopeAndTree();
    }

    @Test
    public void functionDef13() throws Exception {
        checkScopeAndTree();
    }

    @Test
    public void functionDef14() throws Exception {
        checkScopeAndTree(
                        "def bla():\n" +
                                        "  install(extra_opts=[1])\n" +
                                        "def install(extra_opts=[]):\n" +
                                        "  pass");
    }

    @Test
    public void functionDef15() throws Exception {
        checkScopeAndTree(
                        "SOMETHING = NONE\n" +
                                        "def setup():\n" +
                                        "  global SOMETHING\n" +
                                        "  if True : SOMETHING = True\n" +
                                        "def install():\n" +
                                        "  if SOMETHING : pass");
    }

    @Test
    public void functionDef16() throws Exception {
        checkScopeAndTree(
                        "def test():\n" +
                                        "  def inner (end):\n" +
                                        "    def inner_inner():\n" +
                                        "      print(\"inner_inner\", end=end)\n" +
                                        "    inner_inner()\n" +
                                        "  inner(\" baf\\n\")\n" +
                                        "test()");
    }

    @Test
    public void functionDef17() throws Exception {
        checkScopeAndTree(
                        "def test():\n" +
                                        "  def inner (end):\n" +
                                        "    def inner_inner():\n" +
                                        "      print(\"inner_inner\", end=\" haha\\n\")\n" +
                                        "      print(end)\n" +
                                        "    inner_inner()\n" +
                                        "  inner(\" baf\\n\")\n" +
                                        "test()");
    }

    @Test
    public void functionDef18() throws Exception {
        checkScopeAndTree(
                        "def __new__(_cls, hits, misses, maxsize, currsize):\n" +
                                        "  return _tuple_new(_cls, (hits, misses, maxsize, currsize))");
    }

    @Test
    public void functionDef19() throws Exception {
        checkScopeAndTree("def __build_class__(func, name, *bases, metaclass=None, **kwargs): pass");
    }

    @Test
    public void functionDef20() throws Exception {
        checkScopeAndTree("def __init__(self, max_size=0, *, ctx, pending_work_items): pass");
    }

    @Test
    public void decorator01() throws Exception {
        checkScopeAndTree();
    }

    @Test
    public void decorator02() throws Exception {
        checkScopeAndTree();
    }

    @Test
    public void decorator03() throws Exception {
        checkScopeAndTree("@some.path.to.decorator\n" + "def fn(): pass");
    }

    @Test
    public void decorator04() throws Exception {
        checkScopeAndTree(
                        "def outer():\n" +
                                        "  @decorator1\n" +
                                        "  def inner(): pass");
    }

    @Test
    public void decorator05() throws Exception {
        checkScopeAndTree(
                        "def outer():\n" +
                                        "  def decorator1(fn):\n" +
                                        "    pass\n" +
                                        "  @decorator1\n" +
                                        "  def inner(): pass");
    }

    @Test
    public void recursion01() throws Exception {
        checkScopeAndTree(
                        "def test(self, msg, offset=0):\n" +
                                        "    return test(msg, self.string, 1)");
    }

    @Test
    public void recursion02() throws Exception {
        checkScopeAndTree(
                        "def outer():\n" +
                                        "  def recursionInner(arg):\n" +
                                        "    return recursionInner(arg + 1)\n" +
                                        "  recursionInner(0)");
    }

    @Test
    public void positionalOnlyArg01() throws Exception {
        checkTreeResult("def name(p1, p2, /, p_or_kw, *, kw): pass");
    }
    
    @Test
    public void positionalOnlyArg02() throws Exception {
        checkTreeResult("def name(p1, p2=None, /, p_or_kw=None, *, kw): pass");
    }
    
    @Test
    public void positionalOnlyArg03() throws Exception {
        checkTreeResult("def name(p1, p2=None, /, *, kw): pass");
    }
    
    @Test
    public void positionalOnlyArg04() throws Exception {
        checkTreeResult("def name(p1, p2=None, /): pass");
    }
    
    @Test
    public void positionalOnlyArg05() throws Exception {
        checkTreeResult("def name(p1, p2, /, p_or_kw): pass");
    }
    
    @Test
    public void positionalOnlyArg06() throws Exception {
        checkTreeResult("def name(p1, p2, /): pass");
    }
    
    @Test
    public void positionalOnlyArg07() throws Exception {
        checkSyntaxErrorMessage("def name(p1, p2=None, /, p_or_kw, *, kw): pass", "SyntaxError: non-default argument follows default argument");
    }
    
    @Test
    public void positionalOnlyArg08() throws Exception {
        checkSyntaxErrorMessage("def name(p1=None, p2, /, p_or_kw=None, *, kw): pass", "SyntaxError: non-default argument follows default argument");
    }
    
    @Test
    public void positionalOnlyArg09() throws Exception {
        checkSyntaxErrorMessage("def name(p1=None, p2, /): pass", "SyntaxError: non-default argument follows default argument");
    }
    
    @Test
    public void positionalOnlyArg10() throws Exception {
        checkSyntaxErrorMessage("def f(a, b = 5, /, c): pass", "SyntaxError: non-default argument follows default argument");
    }

    @Test
    public void positionalOnlyArg11() throws Exception {
        checkSyntaxErrorMessage("def f(a = 5, b, /, c): pass", "SyntaxError: non-default argument follows default argument");
    }

    @Test
    public void positionalOnlyArg12() throws Exception {
        checkSyntaxErrorMessage("def f(a = 5, b=1, /, c, *, d=2): pass", "SyntaxError: non-default argument follows default argument");
    }

    @Test
    public void positionalOnlyArg13() throws Exception {
        checkSyntaxErrorMessage("def f(a = 5, b, /): pass", "SyntaxError: non-default argument follows default argument");
    }
    
    @Test
    public void positionalOnlyArg14() throws Exception {
        checkSyntaxError("def f(*args, /): pass");
    }

    @Test
    public void positionalOnlyArg15() throws Exception {
        checkSyntaxError("def f(*args, a, /): pass");
    }
        
    @Test
    public void positionalOnlyArg16() throws Exception {
        checkSyntaxError("def f(**kwargs, /): pass");
    }

    @Test
    public void positionalOnlyArg17() throws Exception {
        checkSyntaxError("def f(/, a = 1): pass");
    }

    @Test
    public void positionalOnlyArg18() throws Exception {
        checkSyntaxError("def f(/, a): pass");
    }

    @Test
    public void positionalOnlyArg19() throws Exception {
        checkSyntaxError("def f(/): pass");
    }

    @Test
    public void positionalOnlyArg20() throws Exception {
        checkSyntaxError("def f(*, a, /): pass");
    }

    @Test
    public void positionalOnlyArg21() throws Exception {
        checkSyntaxError("def f(*, /, a): pass");
    }

    @Test
    public void positionalOnlyArg22() throws Exception {
        checkSyntaxErrorMessage("def f(a, /, a): pass", "SyntaxError: duplicate argument 'a' in function definition");
    }

    @Test
    public void positionalOnlyArg23() throws Exception {
        checkSyntaxErrorMessage("def f(a, /, *, a): pass", "SyntaxError: duplicate argument 'a' in function definition");
    }

    @Test
    public void positionalOnlyArg24() throws Exception {
        checkSyntaxError("def f(a, b/2, c): pass");
    }

    @Test
    public void positionalOnlyArg25() throws Exception {
        checkSyntaxError("def f(a, /, c, /): pass");
    }

    @Test
    public void positionalOnlyArg26() throws Exception {
        checkSyntaxError("def f(a, /, c, /, d): pass");
    }

    @Test
    public void positionalOnlyArg27() throws Exception {
        checkSyntaxError("def f(a, /, c, /, d, *, e): pass");
    }

    @Test
    public void positionalOnlyArg28() throws Exception {
        checkSyntaxError("def f(a, *, c, /, d, e): pass");
    }

    @Test
    public void positionalOnlyArg29() throws Exception {
        checkSyntaxErrorMessage("async def f(a, b = 5, /, c): pass", "SyntaxError: non-default argument follows default argument");
    }

    @Test
    public void positionalOnlyArg30() throws Exception {    
        checkSyntaxErrorMessage("async def f(a = 5, b, /, c): pass", "SyntaxError: non-default argument follows default argument");
    }

    @Test
    public void positionalOnlyArg31() throws Exception {        
        checkSyntaxErrorMessage("async def f(a = 5, b=1, /, c, d=2): pass", "SyntaxError: non-default argument follows default argument");
    }

    @Test
    public void positionalOnlyArg32() throws Exception {
        checkSyntaxErrorMessage("async def f(a = 5, b, /): pass", "SyntaxError: non-default argument follows default argument");
    }

    @Test
    public void positionalOnlyArg33() throws Exception {
        checkSyntaxError("async def f(*args, /): pass");
    }

    @Test
    public void positionalOnlyArg34() throws Exception {
        checkSyntaxError("async def f(*args, a, /): pass");
    }

    @Test
    public void positionalOnlyArg35() throws Exception {
        checkSyntaxError("async def f(**kwargs, /): pass");
    }

    @Test
    public void positionalOnlyArg36() throws Exception {
        checkSyntaxError("async def f(/, a = 1): pass");
    }

    @Test
    public void positionalOnlyArg37() throws Exception {
        checkSyntaxError("async def f(/, a): pass");
    }

    @Test
    public void positionalOnlyArg38() throws Exception {
        checkSyntaxError("async def f(/): pass");
    }

    @Test
    public void positionalOnlyArg39() throws Exception {
        checkSyntaxError("async def f(*, a, /): pass");
    }

    @Test
    public void positionalOnlyArg40() throws Exception {
        checkSyntaxError("async def f(*, /, a): pass");
    }

    @Test
    public void positionalOnlyArg41() throws Exception {
        checkSyntaxErrorMessage("async def f(a, /, a): pass", "SyntaxError: duplicate argument 'a' in function definition");
    }

    @Test
    public void positionalOnlyArg42() throws Exception {
        checkSyntaxErrorMessage("async def f(a, /, *, a): pass", "SyntaxError: duplicate argument 'a' in function definition");
    }

    @Test
    public void positionalOnlyArg43() throws Exception {
        checkSyntaxError("async def f(a, b/2, c): pass");
    }

    @Test
    public void positionalOnlyArg44() throws Exception {
        checkSyntaxError("async def f(a, /, c, /): pass");
    }

    @Test
    public void positionalOnlyArg45() throws Exception {
        checkSyntaxError("async def f(a, /, c, /, d): pass");
    }

    @Test
    public void positionalOnlyArg46() throws Exception {
        checkSyntaxError("async def f(a, /, c, /, d, *, e): pass");
    }

    @Test
    public void positionalOnlyArg47() throws Exception {
        checkSyntaxError("async def f(a, *, c, /, d, e): pass");
    }
    
    private void checkScopeAndTree() throws Exception {
        File testFile = getTestFileFromTestAndTestMethod();
        checkScopeFromFile(testFile, true);
        checkTreeFromFile(testFile, true);
    }
}
