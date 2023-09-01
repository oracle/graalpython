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
package com.oracle.graal.python.test.integration.builtin;

import static com.oracle.graal.python.test.integration.PythonTests.assertPrintContains;
import static com.oracle.graal.python.test.integration.PythonTests.assertPrints;

import org.junit.Test;

public class ImportTests {
    @Test
    public void importStandardLib() {
        String source = "import bisect\n" + //
                        "bisect.foo = 42\n" + //
                        "print(bisect.foo)\n";
        assertPrints("42\n", source);
    }

    @Test
    public void module__file__() {
        String source = "import bisect\n" + //
                        "print(bisect.__file__)\n";
        assertPrintContains("bisect.py\n", source);
    }

    @Test
    public void module__file__1() {
        String source = "import __future__\n" + //
                        "print(__future__.__file__)\n";
        assertPrintContains("__future__.py\n", source);
    }

    @Test
    public void testFromImport() {
        String souce = "from math import sqrt, sin as sine\n" +
                        "print(sqrt.__name__, sine.__name__)\n";
        assertPrints("sqrt sin\n", souce);
    }
}
