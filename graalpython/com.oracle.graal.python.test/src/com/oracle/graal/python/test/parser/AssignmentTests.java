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

import org.junit.Test;

public class AssignmentTests extends ParserTestBase {

    @Test
    public void assignment01() throws Exception {
        checkTreeResult("a = 1");
    }

    @Test
    public void assignment02() throws Exception {
        checkTreeResult("a = b = 1");
    }

    @Test
    public void assignment03() throws Exception {
        checkTreeResult("a = 0\n" + "b = a\n" + "c = a + a + b");
    }

    @Test
    public void assignment04() throws Exception {
        checkTreeResult("a = b = c = d = e");
    }

    @Test
    public void assignment05() throws Exception {
        checkTreeResult("a, b, c = 1, 2, 3");
    }

    @Test
    public void assignment06() throws Exception {
        checkScopeAndTree("def fn():\n  a = b = c = d = e");
    }

    @Test
    public void assignment07() throws Exception {
        checkScopeAndTree("def fn():\n  a, b, c = 1, 2, 3");
    }

    @Test
    public void assignment08() throws Exception {
        checkTreeResult("a.b = 1");
    }

    @Test
    public void assignment09() throws Exception {
        checkTreeResult("f().b = 1");
    }

    @Test
    public void assignment10() throws Exception {
        checkTreeResult("i, j, k = x = a");
    }

    @Test
    public void augassign01() throws Exception {
        checkTreeResult("a += b");
    }

    @Test
    public void augassign02() throws Exception {
        checkTreeResult("a -= b");
    }

    @Test
    public void augassign03() throws Exception {
        checkTreeResult("a *= b");
    }

    @Test
    public void augassign04() throws Exception {
        checkTreeResult("a /= b");
    }

    @Test
    public void augassign05() throws Exception {
        checkTreeResult("a //= b");
    }

    @Test
    public void augassign06() throws Exception {
        checkTreeResult("a %= b");
    }

    @Test
    public void augassign07() throws Exception {
        checkTreeResult("a &= b");
    }

    @Test
    public void augassign08() throws Exception {
        checkTreeResult("a |= b");
    }

    @Test
    public void augassign09() throws Exception {
        checkTreeResult("a ^= b");
    }

    @Test
    public void augassign10() throws Exception {
        checkTreeResult("a <<= b");
    }

    @Test
    public void augassign11() throws Exception {
        checkTreeResult("a >>= b");
    }

    @Test
    public void augassign12() throws Exception {
        checkTreeResult("a **= b");
    }

    @Test
    public void augassign13() throws Exception {
        checkScopeAndTree("def fn (): x += 3");
    }

    @Test
    public void augassign14() throws Exception {
        checkScopeAndTree(
                        "def _method(*args, **keywords):\n" +
                                        "    cls_or_self, *rest = args");
    }

    @Test
    public void nonLocal01() throws Exception {
        checkSyntaxErrorMessage(
                        "def outer():\n" +
                                        "  x = 'local in outer'\n" +
                                        "  def inner():\n" +
                                        "    x = 10\n" +
                                        "    nonlocal x\n" +
                                        "  inner()\n",
                        "SyntaxError: name 'x' is assigned to before nonlocal declaration");
    }

    @Test
    public void annotationType01() throws Exception {
        checkTreeResult("j: int");
    }

    @Test
    public void annotationType02() throws Exception {
        checkScopeAndTree("def fn():\n" + "  index : int = 0\n");
    }

    @Test
    public void annotationType03() throws Exception {
        checkTreeResult("j = 1\n" + "ahoj.__annotations__['j'] = float");
    }
}
