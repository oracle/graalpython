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
import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.range.PRange;
import com.oracle.graal.python.nodes.control.GetIteratorExpressionNode.GetIteratorNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.test.PythonTests;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

public class PRangeTests {
    @Before
    public void setup() {
        PythonTests.enterContext();
    }

    static class TestRoot extends RootNode {
        protected TestRoot(PythonLanguage language) {
            super(language);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return null;
        }

        public void doInsert(Node child) {
            insert(child);
        }
    }

    @Test
    public void loopWithOnlyStop() throws UnexpectedResultException {
        PRange range = PythonObjectFactory.getUncached().createRange(10);
        int index = 0;
        TestRoot testRoot = new TestRoot(PythonLanguage.getCurrent());
        GetIteratorNode getIter = GetIteratorNode.create();
        testRoot.doInsert(getIter);
        Object iter = getIter.executeWith(null, range);
        GetNextNode next = GetNextNode.create();
        testRoot.doInsert(next);
        IsBuiltinClassProfile errorProfile = IsBuiltinClassProfile.getUncached();

        while (true) {
            try {
                int item = next.executeInt(null, iter);
                assertEquals(index, item);
            } catch (PException e) {
                e.expectStopIteration(errorProfile);
                break;
            }
            index++;
        }
    }

    @Test
    public void loopWithStep() throws UnexpectedResultException {
        PRange range = PythonObjectFactory.getUncached().createRange(0, 10, 2);
        int index = 0;
        TestRoot testRoot = new TestRoot(PythonLanguage.getCurrent());
        GetIteratorNode getIter = GetIteratorNode.create();
        testRoot.doInsert(getIter);
        Object iter = getIter.executeWith(null, range);
        GetNextNode next = GetNextNode.create();
        testRoot.doInsert(next);
        IsBuiltinClassProfile errorProfile = IsBuiltinClassProfile.getUncached();

        while (true) {
            try {
                int item = next.executeInt(null, iter);
                assertEquals(index, item);
            } catch (PException e) {
                e.expectStopIteration(errorProfile);
                break;
            }
            index += 2;
        }
    }

    @Test
    public void getItem() {
        PRange range = PythonObjectFactory.getUncached().createRange(10);
        assertEquals(3, range.getItemNormalized(3));
    }

    @Test
    public void getItemNegative() {
        String source = "print(range(10)[-3])\n";
        assertPrints("7\n", source);
    }

    @Test
    public void forRangeLoop() {
        String source = "alist = []\n" + //
                        "for i in range(10):\n" + //
                        "  alist.append(i)\n" + //
                        "print(alist)\n";
        assertPrints("[0, 1, 2, 3, 4, 5, 6, 7, 8, 9]\n", source);
    }

    @Test
    public void slice() {
        String source = "r3 = range(3)\n" + //
                        "print(r3[:1])\n" + //
                        "print(r3[1:])\n" + //
                        "r3 = range(1, 3)\n" + //
                        "print(r3[1:])\n";
        assertPrints("range(0, 1)\nrange(1, 3)\nrange(2, 3)\n", source);
    }
}
