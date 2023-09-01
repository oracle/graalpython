/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates.
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

public class UnaryOpTests {

    @Test
    public void plus() {
        assertPrints("3\n", "print(+3)");
        assertPrints("37857431053781905\n", "print(+37857431053781905)");
        assertPrints("3.45\n", "print(+3.45)");
    }

    @Test
    public void minus() {
        assertPrints("-129\n", "print(-129)");
        assertPrints("-129547839057329057230\n", "print(-129547839057329057230)");
        assertPrints("-54353.65636\n", "print(-54353.65636)");
    }

    @Test
    public void invert() {
        assertPrints("-346\n", "print(~345)");
        assertPrints("-3455473924052745730\n", "print(~3455473924052745729)");
    }

    @Test
    public void not() {
        assertPrints("False\n", "print(not 45)");
        assertPrints("True\n", "print(not 0)");
        assertPrints("False\n", "print(not 434432432432423423)");
        assertPrints("False\n", "print(not 1.0)");
    }

}
