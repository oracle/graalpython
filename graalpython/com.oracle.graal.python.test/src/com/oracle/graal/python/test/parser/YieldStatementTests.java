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

public class YieldStatementTests extends ParserTestBase {
   
    @Test
    public void yeild01() throws Exception {
        checkScopeAndTree("def f(): yield 1");
    }
    
    @Test
    public void yeild02() throws Exception {
        checkScopeAndTree("def f(): yield");
    }
    
    @Test
    public void yeild03() throws Exception {
        checkScopeAndTree("def f(): x += yield");
    }

    @Test
    public void yeild04() throws Exception {
        checkScopeAndTree("def f(): x = yield 1");
    }

    @Test
    public void yeild05() throws Exception {
        checkScopeAndTree("def f(): x = y = yield 1");
    }
    
    @Test
    public void yeild06() throws Exception {
        checkScopeAndTree("def f(): x = yield");
    }

    
    @Test
    public void yeild07() throws Exception {
        checkScopeAndTree("def f(): x = y = yield");
    }

    
    @Test
    public void yeild08() throws Exception {
        checkScopeAndTree("def f(): 1 + (yield)*2");
    }
    
    @Test
    public void yeild09() throws Exception {
        checkScopeAndTree("def f(): (yield 1)*2");
    }

    @Test
    public void yeild10() throws Exception {
        checkScopeAndTree("def f(): return; yield 1");
    }

    @Test
    public void yeild11() throws Exception {
        checkScopeAndTree("def f(): yield 1; return");
    }
    
    @Test
    public void yeild12() throws Exception {
        checkScopeAndTree("def f(): yield from 1");
    }
    
    @Test
    public void yeild13() throws Exception {
        checkScopeAndTree("def f(): f((yield from 1))");
    }

    @Test
    public void yeild14() throws Exception {
        checkScopeAndTree("def f(): yield 1; return 1");
    }

    @Test
    public void yeild15() throws Exception {
        checkScopeAndTree(
                "def f():\n" +
                "    for x in range(30):\n"+
                "        yield x\n");
    }
    
    @Test
    public void yeild16() throws Exception {
        checkScopeAndTree(
                "def f():\n" +
                "    if (yield):\n" +
                "        yield x\n");
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

}
