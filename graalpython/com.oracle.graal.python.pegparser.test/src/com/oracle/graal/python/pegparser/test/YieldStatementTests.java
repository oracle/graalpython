/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.python.pegparser.test;

import org.junit.Test;

public class YieldStatementTests extends ParserTestBase {

    @Test
    public void yield01() throws Exception {
        checkScopeAndTree("def f(): yield 1");
    }

    @Test
    public void yield02() throws Exception {
        checkScopeAndTree("def f(): yield");
    }

    @Test
    public void yield03() throws Exception {
        checkScopeAndTree("def f(): x += yield");
    }

    @Test
    public void yield04() throws Exception {
        checkScopeAndTree("def f(): x = yield 1");
    }

    @Test
    public void yield05() throws Exception {
        checkScopeAndTree("def f(): x = y = yield 1");
    }

    @Test
    public void yield06() throws Exception {
        checkScopeAndTree("def f(): x = yield");
    }

    @Test
    public void yield07() throws Exception {
        checkScopeAndTree("def f(): x = y = yield");
    }

    @Test
    public void yield08() throws Exception {
        checkScopeAndTree("def f(): 1 + (yield)*2");
    }

    @Test
    public void yield09() throws Exception {
        checkScopeAndTree("def f(): (yield 1)*2");
    }

    @Test
    public void yield10() throws Exception {
        checkScopeAndTree("def f(): return; yield 1");
    }

    @Test
    public void yield11() throws Exception {
        checkScopeAndTree("def f(): yield 1; return");
    }

    @Test
    public void yield12() throws Exception {
        checkScopeAndTree("def f(): yield from 1");
    }

    @Test
    public void yield13() throws Exception {
        checkScopeAndTree("def f(): f((yield from 1))");
    }

    @Test
    public void yield14() throws Exception {
        checkScopeAndTree("def f(): yield 1; return 1");
    }

    @Test
    public void yield15() throws Exception {
        checkScopeAndTree(
                        "def f():\n" +
                                        "    for x in range(30):\n" +
                                        "        yield x\n");
    }

    @Test
    public void yield16() throws Exception {
        checkScopeAndTree(
                        "def f():\n" +
                                        "    if (yield):\n" +
                                        "        yield x\n");
    }

    @Test
    public void customIter01() throws Exception {
        checkScopeAndTree(
                        "def fn():\n" +
                                        "    class MyIter:\n" +
                                        "        def __iter__(self):\n" +
                                        "            return self\n" +
                                        "        def __next__(self):\n" +
                                        "            raise StopIteration(42)\n" +
                                        "    def gen():\n" +
                                        "        nonlocal ret\n" +
                                        "        ret = yield from MyIter()\n" +
                                        "    ret = None\n" +
                                        "    list(gen())");
    }

    @Test
    public void yield17() throws Exception {
        checkScopeAndTree("generator = type((lambda: (yield))())");
    }

    @Test
    public void with01() throws Exception {
        checkScopeAndTree(
                        "def gen():\n" +
                                        "  with fn():\n" +
                                        "    yield 12\n" +
                                        "    yield 13");
    }

    @Test
    public void with02() throws Exception {
        checkScopeAndTree(
                        "def gen(a):\n" +
                                        "  with a:\n" +
                                        "    bla(p1, p2, p3)\n" +
                                        "  with fn():\n" +
                                        "    yield 12\n" +
                                        "    yield 13");
    }

    @Test
    public void with03() throws Exception {
        checkScopeAndTree(
                        "def gen(a):\n" +
                                        "  with a:\n" +
                                        "    yield 12\n" +
                                        "    yield 13");
    }

    @Test
    public void with04() throws Exception {
        checkScopeAndTree(
                        "def gen():\n" +
                                        "  with A() as a, B() as b:\n" +
                                        "    yield a");
    }

    @Test
    public void if01() throws Exception {
        checkScopeAndTree(
                        "def gen():\n" +
                                        "  if b:\n" +
                                        "    yield 12\n" +
                                        "  else:\n" +
                                        "    yield 13");
    }

    @Test
    public void if02() throws Exception {
        checkScopeAndTree(
                        "def gen(c, b):\n" +
                                        "  if c:\n" +
                                        "    b=1\n" +
                                        "  if b:\n" +
                                        "    yield 12\n" +
                                        "  else:\n" +
                                        "    yield 13");
    }

    @Test
    public void if03() throws Exception {
        checkScopeAndTree(
                        "def gen(c, b):\n" +
                                        "  if c:\n" +
                                        "    b=1\n" +
                                        "  else:\n" +
                                        "    yield 9\n" +
                                        "  if b:\n" +
                                        "    yield 12\n" +
                                        "  else:\n" +
                                        "    yield 13");
    }

    @Test
    public void while01() throws Exception {
        checkScopeAndTree(
                        "def gen(x):\n" +
                                        "   while x:\n" +
                                        "     x = x-1\n" +
                                        "     yield x");
    }

    @Test
    public void while02() throws Exception {
        checkScopeAndTree(
                        "def gen(x):\n" +
                                        "   while x:\n" +
                                        "     pass\n" +
                                        "   while x:\n" +
                                        "     x = x-1\n" +
                                        "     yield x");
    }

    @Test
    public void while03() throws Exception {
        checkScopeAndTree(
                        "def gen(x):\n" +
                                        "   while x:\n" +
                                        "     if x == 1:\n" +
                                        "       continue\n" +
                                        "     x = x-1\n" +
                                        "     yield x");
    }

    @Test
    public void while04() throws Exception {
        checkScopeAndTree(
                        "def gen(x):\n" +
                                        "   while x:\n" +
                                        "     if x == 1:\n" +
                                        "       yield -1\n" +
                                        "     x = x-1\n" +
                                        "     yield x");
    }

    @Test
    public void while05() throws Exception {
        checkScopeAndTree(
                        "def gen(x):\n" +
                                        "   while x:\n" +
                                        "     if x == 1:\n" +
                                        "       break\n" +
                                        "     x = x-1\n" +
                                        "     yield x");
    }

    @Test
    public void while06() throws Exception {
        checkScopeAndTree(
                        "def gen(x):\n" +
                                        "   while x:\n" +
                                        "     if x == 1:\n" +
                                        "       break\n" +
                                        "     if x == 2:\n" +
                                        "       continue\n" +
                                        "     x = x-1\n" +
                                        "     yield x");
    }

    @Test
    public void while07() throws Exception {
        checkScopeAndTree(
                        "def gen(x):\n" +
                                        "   while x:\n" +
                                        "     if x == 1:\n" +
                                        "       break\n" +
                                        "     x = x-1\n" +
                                        "     yield x\n" +
                                        "   else:\n" +
                                        "     yield 10");
    }

    @Test
    public void try01() throws Exception {
        checkScopeAndTree(
                        "def gen(x):\n" +
                                        "  try:\n" +
                                        "    pass\n" +
                                        "  except ValueError:\n" +
                                        "    yield 3");
    }

    @Test
    public void try02() throws Exception {
        checkScopeAndTree(
                        "def gen(x):\n" +
                                        "  try:\n" +
                                        "    yield 3\n" +
                                        "  except ValueError:\n" +
                                        "    pass");
    }

    @Test
    public void try03() throws Exception {
        checkScopeAndTree(
                        "def gen():\n" +
                                        "  try:\n" +
                                        "    pass\n" +
                                        "  except ValueError:\n" +
                                        "    pass\n" +
                                        "  try:\n" +
                                        "    yield 3\n" +
                                        "  except ValueError:\n" +
                                        "    pass");
    }

    @Test
    public void syntaxEror02() throws Exception {
        checkSyntaxErrorMessageContains("yield from", "invalid syntax");
    }

    // These tests pass CPython 3.8. In CPython 3.7 the bulk is deprecated, in CPython 3.6 the
    // behaviour is different.
    // According CPython 3.8 the yielded expression cannot be used outside function and in
    // comprehension.
    @Test
    public void yieldSyntaxError02() throws Exception {
        checkSyntaxErrorMessageContains("[(yield x) for x in range(3)]", "'yield' inside list comprehension");
    }

    @Test
    public void yieldSyntaxError03() throws Exception {
        checkSyntaxErrorMessageContains("def g(): [(yield x) for x in ()]", "'yield' inside list comprehension");
    }

    @Test
    public void yieldSyntaxError04() throws Exception {
        checkSyntaxErrorMessageContains("def g(): [x for x in () if not (yield x)]", "'yield' inside list comprehension");
    }

    @Test
    public void yieldSyntaxError05() throws Exception {
        checkSyntaxErrorMessageContains("def g(): [y for x in () for y in [(yield x)]]", "'yield' inside list comprehension");
    }

    @Test
    public void yieldSyntaxError06() throws Exception {
        checkSyntaxErrorMessageContains("def g(): {(yield x) for x in ()}", "'yield' inside set comprehension");
    }

    @Test
    public void yieldSyntaxError07() throws Exception {
        checkSyntaxErrorMessageContains("def g(): {(yield x): x for x in ()}", "'yield' inside dict comprehension");
    }

    @Test
    public void yieldSyntaxError08() throws Exception {
        checkSyntaxErrorMessageContains("def g(): {x: (yield x) for x in ()}", "'yield' inside dict comprehension");
    }

    @Test
    public void yieldSyntaxError09() throws Exception {
        checkSyntaxErrorMessageContains("def g(): ((yield x) for x in ())", "'yield' inside generator expression");
    }

    @Test
    public void yieldSyntaxError10() throws Exception {
        checkSyntaxErrorMessageContains("def g(): [(yield from x) for x in ()]", "'yield' inside list comprehension");
    }

    @Test
    public void yieldSyntaxError11() throws Exception {
        checkSyntaxErrorMessageContains("class C: [(yield x) for x in ()]", "'yield' inside list comprehension");
    }

    @Test
    public void yieldSyntaxError12() throws Exception {
        checkSyntaxErrorMessageContains("[(yield x) for x in ()]", "'yield' inside list comprehension");
    }
}
