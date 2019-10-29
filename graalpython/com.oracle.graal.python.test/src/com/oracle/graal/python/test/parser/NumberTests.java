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

public class NumberTests extends ParserTestBase {

    @Test
    public void int01() throws Exception {
        checkTreeResult("1");
    }

    @Test
    public void int02() throws Exception {
        checkTreeResult("-1");
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
    public void bin01() throws Exception {
        checkTreeResult("0b101");
    }

    @Test
    public void bin02() throws Exception {
        checkTreeResult("-0b101");
    }

    @Test
    public void bin03() throws Exception {
        checkTreeResult("0b1111111111111111111111111111111111111111111111111111111111111111");
    }

    @Test
    public void complex01() throws Exception {
        checkTreeResult("0+1j");
    }

    @Test
    public void underscore01() throws Exception {
        checkTreeResult("1_0");
    }

    @Test
    public void underscore02() throws Exception {
        checkTreeResult("1_0_6");
    }

    @Test
    public void underscore03() throws Exception {
        checkTreeResult("0b1_1");
    }

    @Test
    public void underscore04() throws Exception {
        checkTreeResult("0o1_7");
    }

    @Test
    public void underscore05() throws Exception {
        checkTreeResult("0x1_f");
    }

    @Test
    public void underscore06() throws Exception {
        checkTreeResult("0_0_0");
    }

    @Test
    public void underscore07() throws Exception {
        checkTreeResult("1_00_00.5");
    }

    @Test
    public void underscore08() throws Exception {
        checkTreeResult("1_00_00.5e5");
    }

    @Test
    public void underscore09() throws Exception {
        checkTreeResult("1_00_00e5_1");
    }

    @Test
    public void underscore10() throws Exception {
        checkTreeResult("1e1_0");
    }

    @Test
    public void underscore11() throws Exception {
        checkTreeResult(".1_4");
    }

    @Test
    public void underscore12() throws Exception {
        checkTreeResult(".1_4e1");
    }

    @Test
    public void underscore13() throws Exception {
        checkTreeResult("1_00_00j");
    }

    @Test
    public void underscore14() throws Exception {
        checkTreeResult("1_00_00.5j");
    }

    @Test
    public void underscore15() throws Exception {
        checkTreeResult("1_00_00e5_1j");
    }

    @Test
    public void underscore16() throws Exception {
        checkTreeResult(".1_4j");
    }

    @Test
    public void underscore17() throws Exception {
        checkTreeResult("(1_2.5+3_3j)");
    }

    @Test
    public void underscore18() throws Exception {
        checkTreeResult("(.5_6j)");
    }

    @Test
    public void underscore19() throws Exception {
        checkSyntaxError("42_");
    }

    @Test
    public void underscore20() throws Exception {
        checkSyntaxError("0x_");
    }

    @Test
    public void underscore21() throws Exception {
        checkSyntaxError("0b1_");
    }

    @Test
    public void underscore22() throws Exception {
        checkSyntaxError("0xf_");
    }

    @Test
    public void underscore23() throws Exception {
        checkSyntaxError("0o5_");
    }

    @Test
    public void underscore24() throws Exception {
        checkSyntaxError("4_______2");
    }

    @Test
    public void underscore25() throws Exception {
        checkSyntaxError("0b1001__0100");
    }

    @Test
    public void underscore26() throws Exception {
        checkSyntaxError("0xffff__ffff");
    }

    @Test
    public void underscore27() throws Exception {
        checkSyntaxError("0x___");
    }

    @Test
    public void underscore28() throws Exception {
        checkSyntaxError("0o5__77");
    }

}
