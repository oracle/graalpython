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

public class AwaitAndAsyncTests extends ParserTestBase {

    @Test
    public void await01() throws Exception {
        checkScopeAndTree("async def f():\n await smth()");
    }

    @Test
    public void await02() throws Exception {
        checkScopeAndTree("async def f():\n foo = await smth()");
    }

    @Test
    public void await03() throws Exception {
        checkScopeAndTree("async def f():\n foo, bar = await smth()");
    }

    @Test
    public void await04() throws Exception {
        checkScopeAndTree("async def f():\n (await smth())");
    }

    @Test
    public void await05() throws Exception {
        checkScopeAndTree("async def f():\n foo((await smth()))");
    }

    @Test
    public void await06() throws Exception {
        checkScopeAndTree("async def f():\n await foo(); return 42");
    }

    @Test
    public void asyncWith01() throws Exception {
        checkScopeAndTree("async def f():\n async with 1: pass");
    }

    @Test
    public void asyncWith02() throws Exception {
        checkScopeAndTree("async def f():\n async with a as b, c as d: pass");
    }

    @Test
    public void asyncFor01() throws Exception {
        checkScopeAndTree("async def f():\n async for i in (): pass");
    }

    @Test
    public void asyncFor02() throws Exception {
        checkScopeAndTree("async def f():\n async for i, b in (): pass");
    }
}
