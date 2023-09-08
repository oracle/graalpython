/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

public class NamedExprTest extends ParserTestBase {

    @Test
    public void testAssignment01() throws Exception {
        checkTreeResult("(a := 10)");
    }

    @Test
    public void testAssignment02() throws Exception {
        checkTreeResult("a = 20\n(a := a)");
    }

    @Test
    public void testAssignment03() throws Exception {
        checkTreeResult("(total := 1 + 2)");
    }

    @Test
    public void testAssignment04() throws Exception {
        checkTreeResult("(info := (1, 2, 3))");
    }

    @Test
    public void testAssignment05() throws Exception {
        checkTreeResult("(x := 1, 2)");
    }

    @Test
    public void testAssignment06() throws Exception {
        checkTreeResult("(z := (y := (x := 0)))");
    }

    @Test
    public void testAssignment07() throws Exception {
        checkTreeResult("(loc := (1, 2))");
    }

    @Test
    public void testAssignment08() throws Exception {
        checkTreeResult("if spam := \"eggs\": pass\n");
    }

    @Test
    public void testAssignment09() throws Exception {
        checkTreeResult("if True and (spam := True): pass");
    }

    @Test
    public void testAssignment10() throws Exception {
        checkTreeResult("if (match := 10) == 10: pass");
    }

    @Test
    public void testAssignment11() throws Exception {
        checkTreeResult("res = [(x, y, x/y) for x in input_data if (y := spam(x)) > 0]");
    }

    @Test
    public void testAssignment12() throws Exception {
        checkTreeResult("res = [[y := spam(x), x/y] for x in range(1, 5)]");
    }

    @Test
    public void testAssignment13() throws Exception {
        checkTreeResult("length = len(lines := [1, 2])");
    }

    @Test
    public void testAssignment14() throws Exception {
        checkTreeResult(
                        "while a > (d := x // a**(n-1)):\n" +
                                        "   a = ((n-1)*a + d) // n");
    }

    @Test
    public void testAssignment15() throws Exception {
        checkTreeResult("while a := False: pass");
    }

    @Test
    public void testAssignment16() throws Exception {
        checkTreeResult("fib = {(c := a): (a := b) + (b := a + c) - b for __ in range(6)}");
    }

    @Test
    public void testAssignment17() throws Exception {
        checkTreeResult("element = a[b:=0]");
    }

    @Test
    public void testAssignment18() throws Exception {
        checkTreeResult("element = a[b:=0, c:=0]");
    }

}
