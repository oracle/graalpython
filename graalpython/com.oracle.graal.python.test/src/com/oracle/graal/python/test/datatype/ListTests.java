/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates.
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

import static com.oracle.graal.python.test.PythonTests.assertLastLineError;
import static com.oracle.graal.python.test.PythonTests.assertPrints;

import org.junit.Test;

public class ListTests {

    @Test
    public void simple() {
        String source = "llist = [1,2,3,4]\n" + //
                        "print(llist)\n";

        assertPrints("[1, 2, 3, 4]\n", source);
    }

    @Test
    public void iterate() {
        String source = "llist = [1,2,3,4]\n" + //
                        "for i in llist:\n" + //
                        "    print(i)\n";

        assertPrints("1\n2\n3\n4\n", source);
    }

    @Test
    public void getSlice() {
        String source = "llist = [1,2,3,4]\n" + //
                        "print(llist[1:3])\n";

        assertPrints("[2, 3]\n", source);
    }

    @Test
    public void setSlice() {
        String source = "llist = [1,2,3,4]\n" + //
                        "llist[1:3] = [42,43]\n" + //
                        "print(llist)\n";

        assertPrints("[1, 42, 43, 4]\n", source);
    }

    @Test
    public void concat() {
        String source = "llist = [1,2] + [42, 43]\n" + //
                        "print(llist)\n";

        assertPrints("[1, 2, 42, 43]\n", source);
    }

    @Test
    public void append() {
        String source = "llist = [1,2,3]\n" + //
                        "print(llist.append(4))\n";

        assertPrints("None\n", source);

        source = "llist = [1,2,3]\n" + //
                        "llist.append(4)\n" +
                        "print(llist)\n";

        assertPrints("[1, 2, 3, 4]\n", source);
    }

    @Test
    public void extend() {
        String source = "llist = [1,2]\n" + //
                        "llist.extend([3, 4])\n" +
                        "print(llist)\n";

        assertPrints("[1, 2, 3, 4]\n", source);
    }

    @Test
    public void index() {
        String source = "llist = [1,2,3,4]\n" + //
                        "print(llist.index(3))\n";

        assertPrints("2\n", source);
    }

    @Test
    public void reverse() {
        String source = "llist = [1,2,3,4,5]\n" + //
                        "llist.reverse()\n" + //
                        "print(llist)\n";

        assertPrints("[5, 4, 3, 2, 1]\n", source);
    }

    @Test
    public void mul() {
        String source = "llist = [1,2,3]\n" + //
                        "print(llist * 2)\n";

        assertPrints("[1, 2, 3, 1, 2, 3]\n", source);
    }

    @Test
    public void delItem() {
        String source = "llist = [1,2,3,4]\n" + //
                        "llist.remove(3)\n" + //
                        "print(llist)\n";

        assertPrints("[1, 2, 4]\n", source);
    }

    @Test
    public void delSlice() {
        String source = "llist = [1,2,3,4]\n" + //
                        "del llist[3:]\n" + //
                        "print(llist)\n";

        assertPrints("[1, 2, 3]\n", source);
    }

    @Test
    public void clearList() {
        String source = "llist = [1,2,3,4]\n" + //
                        "del llist[:]\n" + //
                        "print(llist)\n";

        assertPrints("[]\n", source);
    }

    @Test
    public void popItem() {
        String source = "llist = [1,2,3,4]\n" + //
                        "a = llist.pop()\n" + //
                        "print(llist)\n" + //
                        "print(a)\n";

        assertPrints("[1, 2, 3]\n4\n", source);
    }

    @Test
    public void indexOutOfBoundInt() {
        String source = "lst = [1,2,3,4]\n" + //
                        "lst[5]\n";
        assertLastLineError("IndexError: list index out of range\n", source);
    }

    @Test
    public void assignIndexOutOfBoundInt() {
        String source = "lst = [1,2,3,4]\n" + //
                        "lst[5] = 42\n";
        assertLastLineError("IndexError: list assignment index out of range\n", source);
    }

    @Test
    public void indexOutOfBoundDouble() {
        String source = "lst = [1.0,2.0,3.0,4.0]\n" + //
                        "lst[5]\n";
        assertLastLineError("IndexError: list index out of range\n", source);
    }

    @Test
    public void assignIndexOutOfBoundDouble() {
        String source = "lst = [1.0,2.0,3.0,4.0]\n" + //
                        "lst[5] = 4.2\n";
        assertLastLineError("IndexError: list assignment index out of range\n", source);
    }

    @Test
    public void indexOutOfBoundObj() {
        String source = "lst = [None,None,None,None]\n" + //
                        "lst[5]\n";
        assertLastLineError("IndexError: list index out of range\n", source);
    }

    @Test
    public void assignIndexOutOfBoundObj() {
        String source = "lst = [None, None, None, None]\n" + //
                        "lst[5] = None\n";
        assertLastLineError("IndexError: list assignment index out of range\n", source);
    }

    @Test
    public void listLiteralFromIter() {
        String source = "i = iter((1,2,3,4,5,6))\n" + //
                        "lst = [next(i), next(i), next(i)]\n" + //
                        "print(lst)\n";
        assertPrints("[1, 2, 3]\n", source);
    }

    @Test
    public void listLiteralFromIterMismatch() {
        String source = "i = iter((1,2,3,4,5,6))\n" + //
                        "lst = [next(i), 'f']\n" + //
                        "print(lst)\n";
        assertPrints("[1, 'f']\n", source);
    }

    @Test
    public void listLiteral1() {
        String source = "print([1, 2, 3])\n";
        assertPrints("[1, 2, 3]\n", source);
    }

    @Test
    public void listLiteral2() {
        String source = "s1 = [1, 2, 3]\n" +
                        "s2 = [*s1, 4]\n" +
                        "print(s2)\n";
        assertPrints("[1, 2, 3, 4]\n", source);
    }

    @Test
    public void listLiteral3() {
        String source = "s1 = [1, 2, 3]\n" +
                        "s2 = [0, *s1, 4]\n" +
                        "print(s2)\n";
        assertPrints("[0, 1, 2, 3, 4]\n", source);
    }
}
