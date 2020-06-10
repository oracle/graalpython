/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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

import static com.oracle.graal.python.test.PythonTests.assertPrints;

import org.junit.Test;

public class DictTests {

    @Test
    public void empty() {
        String source = "dd = {}\n" + //
                        "print(dd)\n";
        assertPrints("{}\n", source);
    }

    @Test
    public void simple() {
        String source = "dd = {1:2}\n" + //
                        "print(dd)\n";
        assertPrints("{1: 2}\n", source);
    }

    @Test
    public void del() {
        String source = "dd = {1:2, 3:4}\n" + //
                        "del dd[1]\n" + //
                        "print(dd)\n";

        assertPrints("{3: 4}\n", source);
    }

    @Test
    public void dict() {
        String source = "print(type({}))\n" +
                        "print(type(iter({1: 'a', 2: 'b'})))\n" +
                        "print({})\n" +
                        "print({1: 'a'})\n" +
                        "print({1: 'a', 2: 'b'})\n";

        assertPrints("<class 'dict'>\n" +
                        "<class 'dict_keyiterator'>\n" +
                        "{}\n" +
                        "{1: 'a'}\n" +
                        "{1: 'a', 2: 'b'}\n", source);

        source = "d = {1: 1}\n" +
                        "d.__setitem__(2, d)\n" +
                        "print(d)\n";
        assertPrints("{1: 1, 2: {...}}\n", source);
    }

    @Test
    public void dictViewKeys() {
        String source = "print(type({}.keys()))\n" +
                        "print(type(iter({1: 'a', 2: 'b'}.keys())))\n" +
                        "print({}.keys())\n" +
                        "print({1: 'a'}.keys())\n" +
                        "print({1: 'a', 2: 'b'}.keys())\n";

        assertPrints("<class 'dict_keys'>\n" +
                        "<class 'dict_keyiterator'>\n" +
                        "dict_keys([])\n" +
                        "dict_keys([1])\n" +
                        "dict_keys([1, 2])\n", source);
    }

    @Test
    public void dictViewValues() {
        String source = "print(type({}.values()))\n" +
                        "print(type(iter({1: 'a', 2: 'b'}.values())))\n" +
                        "print({}.values())\n" +
                        "print({1: 'a'}.values())\n" +
                        "print({1: 'a', 2: 'b'}.values())\n";

        assertPrints("<class 'dict_values'>\n" +
                        "<class 'dict_valueiterator'>\n" +
                        "dict_values([])\n" +
                        "dict_values(['a'])\n" +
                        "dict_values(['a', 'b'])\n", source);

        source = "d = {1: 1}\n" +
                        "d.__setitem__(2, d)\n" +
                        "print(d.values())\n";
        assertPrints("dict_values([1, {1: 1, 2: {...}}])\n", source);
    }

    @Test
    public void dictViewItems() {
        String source = "print(type({}.items()))\n" +
                        "print(type(iter({1: 'a', 2: 'b'}.items())))\n" +
                        "print({}.items())\n" +
                        "print({1: 'a'}.items())\n" +
                        "print({1: 'a', 2: 'b'}.items())\n";

        assertPrints("<class 'dict_items'>\n" +
                        "<class 'dict_itemiterator'>\n" +
                        "dict_items([])\n" +
                        "dict_items([(1, 'a')])\n" +
                        "dict_items([(1, 'a'), (2, 'b')])\n", source);

        source = "d = {1: 1}\n" +
                        "d.__setitem__(2, d)\n" +
                        "print(d.items())\n";
        assertPrints("dict_items([(1, 1), (2, {1: 1, 2: {...}})])\n", source);
    }
}
