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
package com.oracle.graal.python.test.integration.runtime;

import static com.oracle.graal.python.test.integration.PythonTests.assertPrints;

import org.junit.Test;

public class GetAttributeDispatchTests {
    @Test
    public void cachedModuleAttr() {
        String source = "import time\n" + //
                        "for i in range(2):\n" + //
                        "  time.foo = 42\n" + //
                        "  print(time.foo)\n";
        assertPrints("42\n42\n", source);
    }

    @Test
    public void booleanAttr() {
        String source = "class A:\n" + //
                        "    def __init__(self, bool):" + //
                        "        self.bool = bool" + //
                        "\n" + //
                        "a = A(True)\n" + //
                        "print(a.bool)\n";
        assertPrints("True\n", source);
    }

    @Test
    public void inObjectAttributeStore() {
        String source = "class A:\n" + //
                        "    pass\n" + //
                        "a = A()\n" + //
                        "for i in range(3):\n" + //
                        "    a.attr = int\n" + //
                        "    print(a.attr)\n";
        assertPrints("<class 'int'>\n<class 'int'>\n<class 'int'>\n", source);
    }

    @Test
    public void unboxedAttributePolymorphic() {
        String source = "l = [1, 3.0, 'oo']\n" + //
                        "for i in l:\n" + //
                        "    print(i.__str__())\n";
        assertPrints("1\n3.0\noo\n", source);
    }

    @Test
    public void unboxedAttributeMegamorphic() {
        String source = "l = [1, 3.0, 'oo', []]\n" + //
                        "for i in l:\n" + //
                        "    print(i.__str__())\n";
        assertPrints("1\n3.0\noo\n[]\n", source);
    }

}
