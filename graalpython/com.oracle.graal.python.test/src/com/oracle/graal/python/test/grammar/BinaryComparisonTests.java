/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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

import static com.oracle.graal.python.test.PythonTests.assertPrints;

import org.junit.Test;

public class BinaryComparisonTests {

    @Test
    public void chainedComparisons() {
        String source = "print(2 < 5 > 3)";
        assertPrints("True\n", source);
    }

    @Test
    public void chainedEquals() {
        String source = "print(11 == 11 == 12 == 11 == 11)";
        assertPrints("False\n", source);
    }

    @Test
    public void moreComplexChainedEquals() {
        String source = "a = 11\n" + //
                        "print(11 == a == 11)";
        assertPrints("True\n", source);
    }

    @Test
    public void equalAndEqual() {
        String source = "a = 11;b = 3; print(3 == b == 3 and 11 == 11 == a)";
        assertPrints("True\n", source);
    }

    @Test
    public void notInList() {
        String source = "print(1 not in [])\n" + //
                        "print(1 not in [1,2,3])\n" + //
                        "print(0.1 not in [0.1,0.2,0.3])\n" + //
                        "print(None not in [None, None])";
        assertPrints("True\nFalse\nFalse\nFalse\n", source);
    }

    @Test
    public void inEvaluationOrder() {
        String source = "print('a') in [print('b')]";
        assertPrints("a\nb\n", source);
    }

}
