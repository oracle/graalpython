/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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
package com.oracle.graal.python.test.grammar;

import static com.oracle.graal.python.test.PythonTests.*;

import org.junit.*;

public class BinaryArithTests {

    @Test
    public void binaryOps() {
        String source = "a = 1 + 2\n" + //
                        "b = 4 - 2\n" + //
                        "c = 6 * 2\n" + //
                        "d = 7 / 2\n" + //
                        "e = 7 // 2\n" + //
                        "print(a,b,c,d,e)\n";
        assertPrints("3 2 12 3.5 3\n", source);
    }

    @Test
    public void bigIntegerDiv() {
        String source = "print(22222222222222222222 / 2)\n";
        assertPrints("1.111111111111111e+19\n", source);
    }

    @Test
    public void simpleIntegerDiv() {
        String source = "print(7 / 2)\n";
        assertPrints("3.5\n", source);
    }

    @Test
    public void additionAndMultiply() {
        String source = "print(345606 + 364 * 2)\n";
        assertPrints("346334\n", source);
    }

    @Test
    public void shouldBeInteger() {
        String source = "print(64 * 64 / (16 * 16))\n";
        assertPrints("16.0\n", source);
    }

    @Test
    public void intOverflow() {
        String source = "print((30020 + 2**31) % 2**32 - 2**31)\n";
        assertPrints("30020\n", source);
    }

    @Test
    public void intOverflowPow() {
        String source = "x = 2**31 + 3; print(x)\n";
        assertPrints("2147483651\n", source);
    }

    @Test
    public void divisionAndMinus() {
        String source = "print(1101010101 / 356 - 2002)\n";
        assertPrints("3090723.0028089886\n", source);
    }

    @Test
    public void minusAndMultiply() {
        String source = "print(42 - 99999 * 543858438584385)\n";
        assertPrints("-54385299999999915573\n", source);
    }

    @Test
    public void divisonAndMultiply() {
        String source = "print(1 / 356 * 2.0)\n";
        assertPrints("0.0056179775280898875\n", source);
    }

    @Test
    public void floorDivisionWithDouble() {
        String source = "print(3 // 5.0)\n";
        assertPrints("0.0\n", source);
    }

    @Test
    public void complexBinaryArith2() {
        String source = "a = 46372573068954628579 / 432\n" + //
                        "b = 46372573068954628579 / 43.2\n" + //
                        "c = (3.56 - 5278948673290672067) // 6427069\n" + //
                        "d = 2 ** 4\n" + //
                        "e = 2.5 ** 3.0\n" + //
                        "print(a,b,c,d,e)\n";
        assertPrints("1.0734391914109867e+17 1.0734391914109868e+18 -821361754992.0 16 15.625\n", source);
    }

    @Test
    public void modulo() {
        assertPrints("4\n", "print(14 % 5)");
        assertPrints("20\n", "print(54528840284285205820 % 52)");
        assertPrints("2.7440432148750915e-09\n", "print(43253252 % 0.7)");
    }

    @Test
    public void int64() {
        String source = "BB_ALL = 0b1111111111111111111111111111111111111111111111111111111111111111\n" + //
                        "print(BB_ALL)\n";
        assertPrints("18446744073709551615\n", source);
    }

    @Test
    public void NonePlusInt() {
        String source = "a = None\n" + //
                        "a += 1\n";
        assertLastLineError("TypeError: unsupported operand type(s) for +=: 'NoneType' and 'int'\n", source);
    }

}
