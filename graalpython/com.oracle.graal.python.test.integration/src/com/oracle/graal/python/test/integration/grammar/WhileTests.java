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

public class WhileTests {

    @Test
    public void test0() {
        String source = "a = 1\n" + //
                        "while a:\n" + //
                        "  print(a)\n" + //
                        "  a = a - 1\n";
        assertPrints("1\n", source);
    }

    @Test
    public void test1() {
        String source = "a = 0\n" + //
                        "b = 5\n" + //
                        "while a < b:\n" + //
                        "  print(a, \" < \", b)\n" + //
                        "  b = b - 1\n";
        assertPrints("0  <  5\n0  <  4\n0  <  3\n0  <  2\n0  <  1\n", source);
    }

    @Test
    public void test2() {
        String source = "a = 1\n" + //
                        "while not a:\n" + //
                        "  print(a)\n" + //
                        "  a = a - 1\n";
        assertPrints("", source);
    }

    @Test
    public void test3() {
        String source = "a = 1\n" + //
                        "b = 5\n" + //
                        "while not a > b:\n" + //
                        "  print(a, \" > \", b)\n" + //
                        "  b = b - 1\n";
        assertPrints("1  >  5\n1  >  4\n1  >  3\n1  >  2\n1  >  1\n", source);
    }

}
