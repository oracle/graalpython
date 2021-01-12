/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.test.util;

import static org.junit.Assert.assertEquals;

import java.math.BigDecimal;

import org.junit.Test;

import com.oracle.graal.python.util.ArrayBuilder;
import com.oracle.graal.python.util.IntArrayBuilder;

public class ArrayBuilderTest {
    @Test
    public void testObjectBasics() {
        ArrayBuilder<BigDecimal> builder = new ArrayBuilder<>();
        builder.add(BigDecimal.ONE);
        for (int i = 0; i < 10; i++) {
            assertEquals(i + 1, builder.size());
            builder.add(builder.get(i).add(BigDecimal.ONE));
        }
        BigDecimal[] result = builder.toArray(new BigDecimal[0]);
        assertEquals(11, result.length);
        for (int i = 0; i < 11; i++) {
            assertEquals(BigDecimal.valueOf(i + 1), builder.get(i));
            assertEquals(BigDecimal.valueOf(i + 1), result[i]);
        }
    }

    @Test
    public void testIntBasics() {
        IntArrayBuilder builder = new IntArrayBuilder();
        builder.add(1);
        for (int i = 0; i < 10; i++) {
            assertEquals(i + 1, builder.size());
            builder.add(builder.get(i) + 1);
        }
        int[] result = builder.toArray();
        assertEquals(11, result.length);
        for (int i = 0; i < 11; i++) {
            assertEquals(i + 1, builder.get(i));
            assertEquals(i + 1, result[i]);
        }
    }
}
