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

public class NumberTests extends ParserTestBase {

    @Test
    public void int01() throws Exception {
        checkTreeResult("1");
    }

    @Test
    public void int01_1() throws Exception {
        checkTreeResult("+1");
    }

    @Test
    public void int01_2() throws Exception {
        checkTreeResult("+   1");
    }

    @Test
    public void int02() throws Exception {
        checkTreeResult("-1");
    }

    @Test
    public void int02_1() throws Exception {
        checkTreeResult("-   1");
    }

    @Test
    public void int03() throws Exception {
        checkTreeResult("-0");
    }

    @Test
    public void int04() throws Exception {
        checkTreeResult("h == -1");
    }

    @Test
    public void int05() throws Exception {
        checkTreeResult("--2");
    }

    @Test
    public void int06() throws Exception {
        checkTreeResult("---2");
    }

    @Test
    public void int07() throws Exception {
        checkTreeResult("----2");
    }

    @Test
    public void maxint() throws Exception {
        checkTreeResult("2147483647");
    }

    @Test
    public void minint() throws Exception {
        checkTreeResult("-2147483648");
    }

    @Test
    public void minlong() throws Exception {
        checkTreeResult("-9223372036854775808");
    }

    @Test
    public void maxNegLong() throws Exception {
        checkTreeResult("-2147483649");
    }

    @Test
    public void minPosLong() throws Exception {
        checkTreeResult("2147483648");
    }

    @Test
    public void maxlong() throws Exception {
        checkTreeResult("9223372036854775807");
    }

    @Test
    public void minPosPInt() throws Exception {
        checkTreeResult("9223372036854775808");
    }

    @Test
    public void maxNegPInt() throws Exception {
        checkTreeResult("-9223372036854775809");
    }

    @Test
    public void someFloat1() throws Exception {
        checkTreeResult("12.0");
    }

    @Test
    public void someFloat2() throws Exception {
        checkTreeResult("12.");
    }

    @Test
    public void someFloat3() throws Exception {
        checkTreeResult(".3");
    }

    @Test
    public void someFloat4() throws Exception {
        checkTreeResult("12.0e1");
    }

    @Test
    public void someFloat5() throws Exception {
        checkTreeResult("12.0E4");
    }

    @Test
    public void someComplex1() throws Exception {
        checkTreeResult("12.0j");
    }

    @Test
    public void someComplex2() throws Exception {
        checkTreeResult("12.j");
    }

    @Test
    public void someComplex3() throws Exception {
        checkTreeResult(".3j");
    }

    @Test
    public void someComplex4() throws Exception {
        checkTreeResult("12.0e1j");
    }

    @Test
    public void someComplex5() throws Exception {
        checkTreeResult("12.0E4j");
    }

    @Test
    public void someComplex6() throws Exception {
        checkTreeResult("12.0J");
    }

    @Test
    public void someComplex7() throws Exception {
        checkTreeResult("12.J");
    }

    @Test
    public void someComplex8() throws Exception {
        checkTreeResult(".3J");
    }

    @Test
    public void someComplex9() throws Exception {
        checkTreeResult("12.0e1J");
    }

    @Test
    public void someComplex10() throws Exception {
        checkTreeResult("12.0E4J");
    }

    @Test
    public void someComplex11() throws Exception {
        checkTreeResult("12J");
    }

    @Test
    public void someComplex12() throws Exception {
        checkTreeResult("12j");
    }
}
