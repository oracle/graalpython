/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.test.interop;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Context.Builder;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;

import com.oracle.graal.python.test.PythonTests;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class InteropLibraryTest extends PythonTests {
    private Context context;

    @Before
    public void setUpTest() {
        Builder builder = Context.newBuilder();
        builder.allowExperimentalOptions(true);
        builder.allowAllAccess(true);
        context = builder.build();
    }

    @After
    public void tearDown() {
        context.close();
    }

    private Value v(String evalString) {
        return context.eval("python", evalString);
    }

    @Test
    public void testStringUnbox() {
        Value somePStr = context.eval("python", "" +
                        "class X(str):\n" +
                        "    pass\n" +
                        "X('123')");
        assertTrue(somePStr.isString());
        assertEquals(somePStr.asString(), "123");

        assertFalse(v("123").isString());
        assertFalse(v("12.3").isString());
        assertFalse(v("{}").isString());
    }

    @Test
    public void testDoubleUnbox() {
        Value somePStr = context.eval("python", "" +
                        "class X(float):\n" +
                        "    pass\n" +
                        "X(123.0)");
        // assertTrue(somePStr.fitsInFloat());
        assertTrue(somePStr.fitsInDouble());
        assertEquals(somePStr.asDouble(), 123.0, 0);
    }

    @Test
    public void testLongUnbox() {
        Value somePStr = context.eval("python", "2**64");
        assertFalse(somePStr.fitsInLong());
        somePStr = context.eval("python", "2**63 - 1");
        assertTrue(somePStr.fitsInLong());
        assertFalse(somePStr.fitsInInt());
    }

    @Test
    public void testBooleanUnbox() {
        Value somePStr = context.eval("python", "True");
        assertTrue(somePStr.isBoolean());
        assertTrue(somePStr.asBoolean());

        somePStr = context.eval("python", "False");
        assertTrue(somePStr.isBoolean());
        assertFalse(somePStr.asBoolean());

        somePStr = context.eval("python", "1");
        assertFalse(somePStr.isBoolean());
    }

    @Test
    public void testPListInsertable() {
        org.graalvm.polyglot.Source source = org.graalvm.polyglot.Source.create("python", "import polyglot\nmutableObj = [1,2,3,4]\nprint(polyglot.__element_info__(mutableObj, 0, \"insertable\"))");
        assertPrints("False\n", source);
    }

    @Test
    public void testPListRemovable() {
        org.graalvm.polyglot.Source source = org.graalvm.polyglot.Source.create("python", "import polyglot\nmutableObj = [1,2,3,4]\nprint(polyglot.__element_info__(mutableObj, 0, \"removable\"))");
        assertPrints("True\n", source);
    }

    @Test
    public void testIsNull() {
        assertTrue(v("None").isNull());
    }

    @Test
    public void testForItemInLazyArray() {
        // @formatter:off
        Value collect = context.eval("python", ""
                + "def iter (arr):\n"
                + "\tcollect = []\n"
                + "\tfor item in arr:\n"
                + "\t\tcollect.append(item)\n"
                + "\t\tcollect.append(item)\n"
                + "\t\tcollect.append(item)\n"
                + "\treturn collect\n"
                + "iter\n"
        );
        // @formatter:on

        final List<Integer> list = Arrays.asList(5, 7, 11, 13, 17, 23);

        Value tripples = collect.execute(new LazyArray(list.iterator()));
        assertTrue("Array returned", tripples.hasArrayElements());
        assertEquals(list.size() * 3, tripples.getArraySize());
    }

    private static final class LazyArray implements ProxyArray {

        private final Iterator<?> it;
        private long at;

        LazyArray(Iterator<?> it) {
            this.it = it;
            this.at = 0;
        }

        @Override
        public Object get(long index) {
            if (index == at) {
                at++;
                return it.next();
            }
            return null;
        }

        @Override
        public void set(long index, Value value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean remove(long index) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getSize() {
            return it.hasNext() ? at + 1 : at;
        }
    }

}
