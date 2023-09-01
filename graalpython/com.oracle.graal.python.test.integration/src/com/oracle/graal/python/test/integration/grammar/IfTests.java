/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates.
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

public class IfTests {

    @Test
    public void basic() {
        String source = "a = 1\n" + //
                        "b = 2\n" + //
                        "if a < b:\n" + //
                        "    print('a < b')\n" + //
                        "if a:\n" + //
                        "    print('a is true')\n" + //
                        "if not b:\n" + //
                        "    print('b is false')\n" + //
                        "if not a > b:\n" + //
                        "    print('not a > b')\n";
        assertPrints("a < b\na is true\nnot a > b\n", source);
    }

    @Test
    public void none() {
        String source = "a = None\n" + //
                        "if a is None:\n" + //
                        "    print('a is', a)\n" + //
                        "else:\n" + //
                        "    print('Error!')\n" + //
                        "if a is not None:\n" + //
                        "    print('Error!')\n" + //
                        "else:\n" + //
                        "    print('a is', a)\n" + //
                        "if a:\n" + //
                        "    print('Error!')\n" + //
                        "else:\n" + //
                        "    print('a is', a)\n" + //
                        "if not a:\n" + //
                        "    print('a is', a)\n" + "else:\n" + //
                        "    print('Error!')\n"; //
        assertPrints("a is None\na is None\na is None\na is None\n", source);
    }

    @Test
    public void testExtendedArgs() {
        StringBuilder source = new StringBuilder();
        StringBuilder expected = new StringBuilder();
        source.append("a = 1\n");
        source.append("if a:\n");
        for (int i = 0; i < 260; i++) {
            source.append(String.format("   print('%d')\n", i));
            expected.append(i).append('\n');
        }
        source.append("else:\n");
        source.append("    print('else')");

        assertPrints(expected.toString(), source.toString());
        assertPrints("else\n", source.toString().replace("a = 1", "a = 0"));
    }
}
