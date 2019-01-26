/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.test.datatype;

import static com.oracle.graal.python.test.PythonTests.*;

import org.junit.*;

public class StringTests {

    @Test
    public void simple() {
        String source = "a = 'combine'\n" + //
                        "b = 'x' + 'y'\n" + //
                        "print(a, b)\n";
        assertPrints("combine xy\n", source);
    }

    @Test
    public void staticMakeTrans() {
        String source = "t = str.maketrans('abc', '123')\n" + //
                        "print(t)\n";
        assertPrints("{97: 49, 98: 50, 99: 51}\n", source);
    }

    @Test
    public void translate() {
        String source = "table = {98 : 50, 99 : 51, 97 : 49}\n" + //
                        "s = 'c b a'\n" + //
                        "print(s.translate(table))\n";
        assertPrints("3 2 1\n", source);
    }

    @Test
    public void ord() {
        String source = "print(ord('a'))\n";
        assertPrints("97\n", source);
    }

    @Test
    public void join0() {
        String source = "val = \"-\".join(\"12345\")\n" + //
                        "print(val)\n";

        assertPrints("1-2-3-4-5\n", source);
    }

    @Test
    public void join1() {
        String source = "s = set(str(i) for i in range(3))\n" + //
                        "print(''.join(s))\n";
        assertPrints("012\n", source);
    }

    @Test
    public void stringToTuple() {
        String source = "s = \"0123456789\"\n" + //
                        "print(tuple(s))\n";
        assertPrints("('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')\n", source);
    }

    @Test
    public void stringRSplitNoLimit() {
        String source = "print(' a b c '.rsplit())\n";
        assertPrints("['a', 'b', 'c']\n", source);
    }

    @Test
    public void stringRSplitSepLimit() {
        String source = "print('----a---b--c-'.rsplit('-', 5))\n";
        assertPrints("['----a-', '', 'b', '', 'c', '']\n", source);
    }

    @Test
    public void bytesCreateTest() {
        String source = "x=b'1234'; print(x); print(type(x));\n";
        assertPrints("b'1234'\n" +
                        "<class 'bytes'>\n", source);
    }

    @Test
    public void rawStringTest() {
        String source = "y=r'34\\344'; print(y); print(type(y));\n";
        assertPrints("34\\344\n" +
                        "<class 'str'>\n", source);
    }

    @Test
    public void regularStringTest() {
        String source = "y='34\\344'; print(y); print(type(y));\n";
        assertPrints("34ä\n" +
                        "<class 'str'>\n", source);
    }
}
