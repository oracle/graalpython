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

public class LoopTests {

    @Test
    public void forWithContinue() {
        String source = "for i in range(10):\n" + //
                        "  if i % 3 != 0:\n" + //
                        "    continue\n" + //
                        "  print(i)";
        assertPrints("0\n3\n6\n9\n", source);
    }

    @Test
    public void whileWithContinue() {
        String source = "num = 11\n" + //
                        "while num:\n" + //
                        "    num = num - 1\n" + //
                        "    if num % 3 != 0:\n" + //
                        "        continue\n" + //
                        "    print(num)\n" + //
                        "\n";

        assertPrints("9\n6\n3\n0\n", source);
    }

    @Test
    public void forWithBreak() {
        String source = "for i in range(1, 10):\n" + //
                        "    if i % 3 == 0:\n" + //
                        "        break\n" + //
                        "    print(i)\n" + //
                        "\n";

        assertPrints("1\n2\n", source);
    }

    @Test
    public void whileWithBreakAndElse() {
        String source = "num = 11\n" + //
                        "while num:\n" + //
                        "    num = num - 1\n" + //
                        "    if num % 7 == 0:\n" + //
                        "        break\n" + //
                        "    print(num)\n" + //
                        "else:\n" + //
                        "    print(\"else\")\n" + //
                        "\n";

        assertPrints("10\n9\n8\n", source);
    }

    @Test
    public void forWithElse() {
        String source = "num = 0\n" + //
                        "for i in range(5):\n" + //
                        "    num += 1\n" + //
                        "else:\n" + //
                        "    print(num)\n" + //
                        "\n";
        assertPrints("5\n", source);
    }

    @Test
    public void forWithContinueAndElse() {
        String source = "num = 0\n" + //
                        "for i in range(5):\n" + //
                        "    if i % 3 == 0:\n" + //
                        "       continue\n" + //
                        "    num += 1\n" + //
                        "else:\n" + //
                        "    print(num)\n" + //
                        "\n";
        assertPrints("3\n", source);
    }

    @Test
    public void forWithBreakAndElse() {
        String source = "num = 0\n" + //
                        "for i in range(5):\n" + //
                        "    if i % 3 == 0:\n" + //
                        "       break\n" + //
                        "    num += 1\n" + //
                        "else:\n" + //
                        "    print(num)\n" + //
                        "\n";
        assertPrints("", source);
    }

    @Test
    public void whileWithContinueAndElse() {
        String source = "num = 11\n" + //
                        "while num:\n" + //
                        "    num = num - 1\n" + //
                        "    if num % 3 != 0:\n" + //
                        "        continue\n" + //
                        "    print(num)\n" + //
                        "else:\n" + //
                        "    print(\"else\")" + //
                        "\n";

        assertPrints("9\n6\n3\n0\nelse\n", source);
    }

    @Test
    public void forRangeWithStep() {
        String source = "for i in range(1, 10, 2):\n" + //
                        "    print(i)\n" + //
                        "\n";

        assertPrints("1\n3\n5\n7\n9\n", source);
    }

}
