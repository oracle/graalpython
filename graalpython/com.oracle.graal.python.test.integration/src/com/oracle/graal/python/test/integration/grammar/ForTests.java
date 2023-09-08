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

public class ForTests {

    @Test
    public void simple() {
        String source = "a = [1, 2, 3]\n" + //
                        "for i in a:\n" + //
                        "  print(i)";
        assertPrints("1\n2\n3\n", source);
    }

    @Test
    public void simple2() {
        String source = "a = [1, 2, 3]\n" + //
                        "c = [0, 0, 0]\n" + //
                        "for i in range(len(a)):\n" + //
                        "  c[i] = a[i]*2\n" + //
                        "print(c)";
        assertPrints("[2, 4, 6]\n", source);
    }

    @Test
    public void simple3() {
        String source = "a = ['a', 'b', 'c']\n" + //
                        "c = [0, 0, 0]\n" + //
                        "for i in range(len(a)-1):\n" + //
                        "  c[i] = a[i]\n" + //
                        "print(c)";
        assertPrints("['a', 'b', 0]\n", source);
    }

    @Test
    public void withUnpacking() {
        String source = "b = [[1,2], [3,4], [5,6]]\n" + //
                        "for x, y in b:\n" + //
                        "  print(x, y)";
        assertPrints("1 2\n3 4\n5 6\n", source);
    }

    @Test
    public void nestedUnpackingInFor() {
        String source = "ll = [([1, 2], [3, 4]), ([5, 6], [7, 8])]\n" + //
                        "for [a, b], [c, d] in ll:\n" + //
                        "    print(a, b, c, d)\n" + //
                        "\n";

        assertPrints("1 2 3 4\n5 6 7 8\n", source);
    }

}
