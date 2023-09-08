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

public class WithTests {

    @Test
    public void withWithException() {
        String source = "\n" + //
                        "a = 5\n" + //
                        "class Sample:\n" + //
                        "    def __enter__(self):\n" + //
                        "        print(\"In __enter__()\")\n" + //
                        "        return self\n" + //

                        "    def __exit__(self, type, value, trace):\n" + //
                        "         print(\"In __exit__()\");\n" + //
                        "         return True;\n" + //

                        "    def do_something(self, x):\n" + //
                        "         raise KeyboardInterrupt\n" + //
                        "         return \"Foo\"\n" + //

                        "with Sample() as sample:\n" + //
                        "    print(\"sample:\", sample.do_something(a))\n" + //
                        "    a = 1;\n" + //
                        "#print(\"sample:\", sample.do_something(a))\n" + //

                        "print (a);\n" + //
                        "\n";

        assertPrints("In __enter__()\nIn __exit__()\n5\n", source);
    }

    @Test
    public void withByFuncCall() {
        String source = "\n" + //
                        "a = 5\n" + //
                        "class Sample:\n" + //
                        "    def __enter__(self):\n" + //
                        "        print(\"In __enter__()\")\n" + //
                        "        return self\n" + //

                        "    def __exit__(self, type, value, trace):\n" + //
                        "         print(\"In __exit__()\");\n" + //
                        "         return 5;\n" + //

                        "    def do_something(self, x):\n" + //
                        "         return \"Foo\"\n" + //

                        "def get_sample():\n" + //
                        "    return Sample()\n" + //

                        "with get_sample() as sample:\n" + //
                        "    print(\"sample:\", sample.do_something(a))\n" + //
                        "    a = 1;\n" + //
                        "print(\"sample:\", sample.do_something(a))\n" + //

                        "print (a);\n" + //

                        "\n";

        assertPrints("In __enter__()\nsample: Foo\nIn __exit__()\nsample: Foo\n1\n", source);
    }

    @Test
    public void withNoAsName() {
        String source = "\n" + //
                        "a = 5\n" + //
                        "class Sample:\n" + //
                        "    def __enter__(self):\n" + //
                        "        print(\"In __enter__()\")\n" + //
                        "        return self\n" + //

                        "    def __exit__(self, type, value, trace):\n" + //
                        "         print(\"In __exit__()\");\n" + //
                        "         return 5;\n" + //

                        "    def do_something(self, x):\n" + //
                        "         return \"Foo\"\n" + //

                        "def get_sample():\n" + //
                        "    return Sample()\n" + //

                        "with get_sample():\n" + //
                        "    print(\"Execute without asName\")\n" + //
                        "    a = 1;\n" + //

                        "print (a);\n" + //

                        "\n";

        assertPrints("In __enter__()\nExecute without asName\nIn __exit__()\n1\n", source);
    }

    @Test
    public void withExceptionInfo() {
        String source = "import sys\n" +
                        "class CM:\n" +
                        "    def __enter__(self):\n" +
                        "        return self\n" +
                        "    def __exit__(self, et, e, tb):\n" +
                        "        print(repr(sys.exc_info()[1]))\n" +
                        "        return True\n" +
                        "with CM():\n" +
                        "    raise NameError\n" +
                        "print(repr(sys.exc_info()[1]))\n";

        assertPrints("NameError()\nNone\n", source);
    }

    @Test
    public void withBreakExecutesExit() {
        String source = "class CM:\n" +
                        "    def __enter__(self):\n" +
                        "        print('enter')\n" +
                        "        return self\n" +
                        "    def __exit__(self, et, e, tb):\n" +
                        "        print('exit')\n" +
                        "        return True\n" +
                        "for i in range(1):\n" +
                        "    with CM():\n" +
                        "        print('inner')\n" +
                        "        break\n";

        assertPrints("enter\ninner\nexit\n", source);
    }
}
