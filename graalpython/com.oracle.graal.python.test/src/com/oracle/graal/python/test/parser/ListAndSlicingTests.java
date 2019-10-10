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

public class ListAndSlicingTests extends ParserTestBase {

    @Test
    public void list01() throws Exception {
        checkTreeResult("[1,2,3,4]");
    }

    @Test
    public void list02() throws Exception {
        checkTreeResult("list = [1,2,3,4]");
    }

    @Test
    public void list03() throws Exception {
        checkTreeResult("[]");
    }

    @Test
    public void list04() throws Exception {
        checkTreeResult("l = []");
    }

    @Test
    public void list05() throws Exception {
        checkTreeResult("[*{2}, 3, *[4]]");
    }

    @Test
    public void slice01() throws Exception {
        checkTreeResult("a[::]");
    }

    @Test
    public void slice02() throws Exception {
        checkTreeResult("a[1::]");
    }

    @Test
    public void slice03() throws Exception {
        checkTreeResult("a[:1:]");
    }

    @Test
    public void slice04() throws Exception {
        checkTreeResult("a[::1]");
    }

    @Test
    public void slice05() throws Exception {
        checkTreeResult("a()[b():c():d()]");
    }

    @Test
    public void starExpr01() throws Exception {
        checkTreeResult("[*[1,2,3]]");
    }

    @Test
    public void starExpr02() throws Exception {
        checkSyntaxErrorMessageContains("*[1,2,3]", "can't use starred expression here");
    }

    @Test
    public void starExpr03() throws Exception {
        checkSyntaxErrorMessageContains("*a = range(5)", "starred assignment target must be in a list or tuple");
    }

    @Test
    public void starExpr04() throws Exception {
        checkTreeResult("*a, = range(5)");
    }

    @Test
    public void starExpr05() throws Exception {
        checkTreeResult("a, *b, c = range(5)");
    }

    @Test
    public void starExpr06() throws Exception {
        checkTreeResult("first, *rest = seq");
    }

    @Test
    public void starExpr07() throws Exception {
        checkTreeResult("[a, *b, c] = seq");
    }

    @Test
    public void starExpr08() throws Exception {
        checkTreeResult("for a, *b in [(1, 2, 3), (4, 5, 6, 7)]:\n" +
                        "    print(b)");
    }

    @Test
    public void starExpr09() throws Exception {
        checkSyntaxErrorMessageContains("b = *a", "can't use starred expression here");
    }

    @Test
    public void starExpr10() throws Exception {
        checkSyntaxErrorMessageContains("[*item for item in l]", "iterable unpacking cannot be used in comprehension");
    }

}
