/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.iterator;

import java.math.BigInteger;

import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.object.DynamicObject;

public final class PBigRangeIterator extends PBuiltinIterator {
    private final PInt start;
    private final PInt step;
    private final PInt len;

    private BigInteger longIndex;

    public PBigRangeIterator(Object clazz, DynamicObject storage, PInt start, PInt step, PInt len) {
        super(clazz, storage);
        this.start = start;
        this.step = step;
        this.len = len;

        this.longIndex = BigInteger.ZERO;
    }

    @TruffleBoundary
    public BigInteger getLength() {
        return this.len.subtract(this.longIndex);
    }

    @TruffleBoundary
    public BigInteger nextBigInt() {
        BigInteger nextVal = start.add(longIndex.multiply(step.getValue()));
        this.longIndex = longIndex.add(BigInteger.ONE);
        return nextVal;
    }

    @TruffleBoundary
    public boolean hasNextBigInt() {
        return longIndex.compareTo(len.getValue()) < 0;
    }

    public BigInteger next() {
        return nextBigInt();
    }

    public boolean hasNext() {
        return hasNextBigInt();
    }

    public PInt getStart() {
        return start;
    }

    public PInt getLen() {
        return len;
    }

    public PInt getStep() {
        return step;
    }

    public PInt getReduceStart() {
        return start;
    }

    public PInt getReduceStop(PythonObjectFactory factory) {
        return factory.createInt(start.add(len.multiply(step)));
    }

    public PInt getReduceStep() {
        return step;
    }

    public PInt getLongIndex(PythonObjectFactory factory) {
        return factory.createInt(longIndex);
    }

    public void setLongIndex(BigInteger idx) {
        this.longIndex = idx;
    }
}
