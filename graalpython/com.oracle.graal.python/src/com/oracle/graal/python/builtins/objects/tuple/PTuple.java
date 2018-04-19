/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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
package com.oracle.graal.python.builtins.objects.tuple;

import java.util.Arrays;

import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.PImmutableSequence;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.SequenceUtil;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

public final class PTuple extends PImmutableSequence implements Comparable<Object> {

    private final Object[] array;

    public PTuple(PythonClass cls, Object[] elements) {
        super(cls);
        array = elements;
    }

    public Object[] getArray() {
        return array;
    }

    @Override
    public int len() {
        return array.length;
    }

    public boolean isEmpty() {
        return array.length == 0;
    }

    @Override
    public Object getItem(int idx) {
        final int index = SequenceUtil.normalizeIndex(idx, len(), "tuple index out of range");
        return getItemNormalized(index);
    }

    public Object getItemNormalized(int index) {
        return array[index];
    }

    @Override
    public Object getSlice(PythonObjectFactory factory, int start, int stop, int step, int length) {
        Object[] newArray = new Object[length];
        if (step == 1) {
            System.arraycopy(array, start, newArray, 0, stop - start);
            return factory.createTuple(newArray);
        }
        for (int i = start, j = 0; j < length; i += step, j++) {
            newArray[j] = array[i];
        }
        return factory.createTuple(newArray);
    }

    @Override
    public boolean lessThan(PSequence sequence) {
        int i = SequenceUtil.cmp(this, sequence);
        if (i < 0) {
            return i == -1 ? true : false;
        }

        Object element1 = this.getItem(i);
        Object element2 = sequence.getItem(i);

        /**
         * TODO: Can use a better approach instead of instanceof checks
         */
        if (element1 instanceof Integer && element2 instanceof Integer) {
            return (int) element1 < (int) element2;
        }

        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder("(");
        for (int i = 0; i < array.length - 1; i++) {
            buf.append(toString(array[i]));
            buf.append(", ");
        }

        if (array.length > 0) {
            buf.append(toString(array[array.length - 1]));
        }

        if (array.length == 1) {
            buf.append(",");
        }

        buf.append(")");
        return buf.toString();
    }

    @SuppressWarnings({"unused", "static-method"})
    public PTuple __mul__(int value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SequenceStorage getSequenceStorage() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int index(Object value) {
        for (int i = 0; i < array.length; i++) {
            Object val = array[i];

            if (val.equals(value)) {
                return i;
            }
        }

        return -1;
    }

    @Override
    public int compareTo(Object o) {
        return SequenceUtil.cmp(this, (PSequence) o);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof PTuple)) {
            return false;
        }

        PTuple otherTuple = (PTuple) other;
        return Arrays.equals(array, otherTuple.array);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    public static PTuple require(Object value) {
        if (value instanceof PTuple) {
            return (PTuple) value;
        }
        CompilerDirectives.transferToInterpreter();
        throw new AssertionError("PTuple required.");
    }

    public static PTuple expect(Object value) throws UnexpectedResultException {
        if (value instanceof PTuple) {
            return (PTuple) value;
        }
        throw new UnexpectedResultException(value);
    }

}
