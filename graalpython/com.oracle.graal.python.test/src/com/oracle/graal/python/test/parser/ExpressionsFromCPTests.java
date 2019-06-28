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

public class ExpressionsFromCPTests extends ParserTestBase {

    @Test
    public void expression01() throws Exception {
        checkTreeResult("def f(): pass");
    }
    
    @Test
    public void expression02() throws Exception {
        checkTreeResult("foo(1)");
    }
    
    @Test
    public void expression03() throws Exception {
        checkTreeResult("[1, 2, 3]");
    }
    
    @Test
    public void expression04() throws Exception {
        checkTreeResult("...");
    }

    @Test
    public void expression05() throws Exception {
        checkTreeResult("a[...]");
    }

    @Test
    public void generator01() throws Exception {
        checkScopeAndTree("[x**3 for x in range(20)]");
    }
    
    @Test
    public void generator02() throws Exception {
        checkScopeAndTree("[x**3 for x in range(20) if x % 3]");
    }

    @Test
    public void generator03() throws Exception {
        checkScopeAndTree("[x**3 for x in range(20) if x % 2 if x % 3]");
    }

    @Test
    public void generator04() throws Exception {
        checkScopeAndTree("list(x**3 for x in range(20))");
    }

    @Test
    public void generator05() throws Exception {
        checkScopeAndTree("list(x**3 for x in range(20) if x % 3)");
    }
    
    @Test
    public void generator06() throws Exception {
        checkScopeAndTree("list(x**3 for x in range(20) if x % 2 if x % 3)");
    }

    @Test
    public void generator07() throws Exception {
        checkScopeAndTree("(x for x in range(10))");
    }
    
    @Test
    public void generator08() throws Exception {
        checkScopeAndTree("foo(x for x in range(10))");
    }

    @Test
    public void fnCall01() throws Exception {
        checkTreeResult("foo(*args)");
    }

    @Test
    public void fnCall02() throws Exception {
        checkTreeResult("foo(*args, **kw)");
    }

    @Test
    public void fnCall03() throws Exception {
        checkTreeResult("foo(**kw)");
    }

    @Test
    public void fnCall04() throws Exception {
        checkTreeResult("foo(key=value)");
    }
    
    @Test
    public void fnCall05() throws Exception {
        checkTreeResult("foo(key=value, *args)");
    }
    
    @Test
    public void fnCall06() throws Exception {
        checkTreeResult("foo(key=value, *args, **kw)");
    }
    
    @Test
    public void fnCall07() throws Exception {
        checkTreeResult("foo(key=value, **kw)");
    }
    
    @Test
    public void fnCall08() throws Exception {
        checkTreeResult("foo(a, b, c, *args)");
    }
    
    @Test
    public void fnCall09() throws Exception {
        checkTreeResult("foo(a, b, c, *args, **kw)");
    }
    
    @Test
    public void fnCall10() throws Exception {
        checkTreeResult("foo(a, b, c, **kw)");
    }
    
    @Test
    public void fnCall11() throws Exception {
        checkTreeResult("foo(a, *args, keyword=23)");
    }
    
    @Test
    public void binOp01() throws Exception {
        checkTreeResult("foo + bar");
    }

    @Test
    public void binOp02() throws Exception {
        checkTreeResult("foo - bar");
    }

    @Test
    public void binOp03() throws Exception {
        checkTreeResult("foo * bar");
    }

    @Test
    public void binOp04() throws Exception {
        checkTreeResult("foo / bar");
    }

    @Test
    public void binOp05() throws Exception {
        checkTreeResult("foo // bar");
    }

    @Test
    public void lambda01() throws Exception {
        checkScopeAndTree("lambda: 0");
    }
    
    @Test
    public void lambda02() throws Exception {
        checkScopeAndTree("lambda x: 0");
    }
    
    @Test
    public void lambda03() throws Exception {
        checkScopeAndTree("lambda *y: 0");
    }
    
    @Test
    public void lambda04() throws Exception {
        checkScopeAndTree("lambda *y, **z: 0");
    }

    @Test
    public void lambda05() throws Exception {
        checkScopeAndTree("lambda **z: 0");
    }

    @Test
    public void lambda06() throws Exception {
        checkScopeAndTree("lambda x, y: 0");
    }

    @Test
    public void lambda07() throws Exception {
        checkScopeAndTree("lambda foo=bar: 0");
    }

    @Test
    public void lambda08() throws Exception {
        checkScopeAndTree("lambda foo=bar, spaz=nifty+spit: 0");
    }

    @Test
    public void lambda09() throws Exception {
        checkScopeAndTree("lambda foo=bar, **z: 0");
    }

    @Test
    public void lambda10() throws Exception {
        checkScopeAndTree("lambda foo=bar, blaz=blat+2, **z: 0");
    }

    @Test
    public void lambda11() throws Exception {
        checkScopeAndTree("lambda foo=bar, blaz=blat+2, *y, **z: 0");
    }

    @Test
    public void lambda12() throws Exception {
        checkScopeAndTree("lambda x, *y, **z: 0");
    }
}
