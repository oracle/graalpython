/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates.
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
package com.oracle.graal.python.test.integration.grammar;

import static com.oracle.graal.python.test.integration.PythonTests.assertPrints;

import org.junit.Test;

public class BinaryBitwiseTests {

    @Test
    public void bitwiseShifts() {
        assertPrints("8\n", "print(1 << 3)");
        assertPrints("2\n", "print(8 >> 2)");
        assertPrints("680564733841876926926749214863536422912\n", "print(1 << 129)");
        assertPrints("0\n", "print(8 >> 20)");
        assertPrints("-256\n", "print(-1 << 8)");
        assertPrints("-1\n", "print(-20 >> 12)");
    }

    @Test
    public void bitwiseAnd() {
        assertPrints("0\n", "print(32 & 8)");
        assertPrints("0\n", "print(32 & 8484324820482048)");
    }

    @Test
    public void bitwiseOr() {
        assertPrints("441\n", "print(432 | 9)");
        assertPrints("943824320482304948\n", "print(432 | 943824320482304820)");
    }

    @Test
    public void bitwiseXor() {
        assertPrints("415\n", "print(425 ^ 54)");
        assertPrints("544382094820482034324155\n", "print(425 ^ 544382094820482034324242)");
    }

}
