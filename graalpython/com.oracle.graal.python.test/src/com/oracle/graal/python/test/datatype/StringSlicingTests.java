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

import static com.oracle.graal.python.test.PythonTests.*;

import org.junit.*;

public class StringSlicingTests {

    @Test
    public void slice0() {
        String source = "alphabet = \"abcdefghij\"\n" + //
                        "print(alphabet[1:3])\n";
        assertPrints("bc\n", source);
    }

    @Test
    public void slice1() {
        String source = "alphabet = \"abcdefghij\"\n" + //
                        "print(alphabet[:3])\n";
        assertPrints("abc\n", source);
    }

    @Test
    public void slice2() {
        String source = "alphabet = \"abcdefghij\"\n" + //
                        "print(alphabet[1:])\n";
        assertPrints("bcdefghij\n", source);
    }

    @Test
    public void slice3() {
        String source = "alphabet = \"abcdefghij\"\n" + //
                        "print(alphabet[:])\n";
        assertPrints("abcdefghij\n", source);
    }

    @Test
    public void slice4() {
        String source = "alphabet = \"abcdefghij\"\n" + //
                        "print(alphabet[-1:])\n";
        assertPrints("j\n", source);
    }

    @Test
    public void slice5() {
        String source = "alphabet = \"abcdefghij\"\n" + //
                        "print(alphabet[:-1])\n";
        assertPrints("abcdefghi\n", source);
    }

}
