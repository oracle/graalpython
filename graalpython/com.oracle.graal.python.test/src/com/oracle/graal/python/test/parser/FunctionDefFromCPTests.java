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

public class FunctionDefFromCPTests extends ParserTestBase {

    @Test
    public void functionDefCPT01() throws Exception {
        checkScopeAndTree("def f(): pass");
    }

    @Test
    public void functionDefCPT02() throws Exception {
        checkScopeAndTree("def f(*args): pass");
    }

    @Test
    public void functionDefCPT03() throws Exception {
        checkScopeAndTree("def f(*args, **kw): pass");
    }

    @Test
    public void functionDefCPT04() throws Exception {
        checkScopeAndTree("def f(**kw): pass");
    }

    @Test
    public void functionDefCPT05() throws Exception {
        checkScopeAndTree("def f(foo=bar): pass");
    }

    @Test
    public void functionDefCPT06() throws Exception {
        checkScopeAndTree("def f(foo=bar, *args): pass");
    }

    @Test
    public void functionDefCPT07() throws Exception {
        checkScopeAndTree("def f(foo=bar, *args, **kw): pass");
    }

    @Test
    public void functionDefCPT08() throws Exception {
        checkScopeAndTree("def f(foo=bar, **kw): pass");
    }

    @Test
    public void functionDefCPT09() throws Exception {
        checkScopeAndTree("def f(a, b): pass");
    }

    @Test
    public void functionDefCPT10() throws Exception {
        checkScopeAndTree("def f(a, b, *args): pass");
    }

    @Test
    public void functionDefCPT11() throws Exception {
        checkScopeAndTree("def f(a, b, *args, **kw): pass");
    }

    @Test
    public void functionDefCPT12() throws Exception {
        checkScopeAndTree("def f(a, b, **kw): pass");
    }

    @Test
    public void functionDefCPT13() throws Exception {
        checkScopeAndTree("def f(a, b, foo=bar): pass");
    }

    @Test
    public void functionDefCPT14() throws Exception {
        checkScopeAndTree("def f(a, b, foo=bar, *args, **kw): pass");
    }

    @Test
    public void functionDefCPT15() throws Exception {
        checkScopeAndTree("def f(a, b, foo=bar, **kw): pass");
    }

    // keyword-only arguments
    @Test
    public void functionDefCPT16() throws Exception {
        checkScopeAndTree("def f(*, a): pass");
    }

    @Test
    public void functionDefCPT17() throws Exception {
        checkScopeAndTree("def f(*, a = 5): pass");
    }

    @Test
    public void functionDefCPT18() throws Exception {
        checkScopeAndTree("def f(*, a = 5, b): pass");
    }

    @Test
    public void functionDefCPT19() throws Exception {
        checkScopeAndTree("def f(*, a, b = 5): pass");
    }

    @Test
    public void functionDefCPT20() throws Exception {
        checkScopeAndTree("def f(*, a, b = 5, **kwds): pass");
    }

    @Test
    public void functionDefCPT21() throws Exception {
        checkScopeAndTree("def f(*args, a): pass");
    }

    @Test
    public void functionDefCPT22() throws Exception {
        checkScopeAndTree("def f(*args, a = 5): pass");
    }

    @Test
    public void functionDefCPT23() throws Exception {
        checkScopeAndTree("def f(*args, a = 5, b): pass");
    }

    @Test
    public void functionDefCPT24() throws Exception {
        checkScopeAndTree("def f(a, b, foo=bar, **kw): pass");
    }

    @Test
    public void functionDefCPT25() throws Exception {
        checkScopeAndTree("def f(a, b, foo=bar, **kw): pass");
    }

    @Test
    public void decoratedFn01() throws Exception {
        checkScopeAndTree(
                        "@staticmethod\n" +
                                        "def f(): pass");
    }

    @Test
    public void decoratedFn02() throws Exception {
        checkScopeAndTree(
                        "@staticmethod\n" +
                                        "@funcattrs(x, y)\n" +
                                        "def f(): pass");
    }

    @Test
    public void decoratedFn03() throws Exception {
        checkScopeAndTree(
                        "@funcattrs()\n" +
                                        "def f(): pass");
    }
}
