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
package com.oracle.graal.python.test.datatype;

import static com.oracle.graal.python.test.PythonTests.assertLastLineError;
import static com.oracle.graal.python.test.PythonTests.assertPrints;

import org.junit.Test;

public class TupleTest {

    @Test
    public void simple() {
        String source = "t = ('a', 123, 'b', 49324324242949949)\n" + //
                        "print(t)";
        assertPrints("('a', 123, 'b', 49324324242949949)\n", source);
    }

    @Test
    public void greaterThan() {
        String source = "print((3,4) >= (3,3))\n";
        assertPrints("True\n", source);
    }

    @Test
    public void tupleLiteral1() {
        String source = "print((1, 2, 3))\n";
        assertPrints("(1, 2, 3)\n", source);
    }

    @Test
    public void tupleLiteral2() {
        String source = "s1 = (1, 2, 3)\n" +
                        "s2 = (*s1, 4)\n" +
                        "print(s2)\n";
        assertPrints("(1, 2, 3, 4)\n", source);
    }

    @Test
    public void tupleLiteral3() {
        String source = "s1 = 1, 2, 3\n" +
                        "s2 = 0, *s1, 4\n" +
                        "print(s2)\n";
        assertPrints("(0, 1, 2, 3, 4)\n", source);
    }

    @Test
    public void indexOutOfBound() {
        String source = "tup = (1,2,3,4)\n" + //
                        "tup[5]\n";
        assertLastLineError("IndexError: tuple index out of range\n", source);
    }

}
