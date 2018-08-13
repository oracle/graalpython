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

import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.PImmutableSequence;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.SequenceUtil;
import com.oracle.graal.python.runtime.sequence.storage.ObjectSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

public final class PTuple extends PImmutableSequence implements Comparable<Object> {

    private SequenceStorage store;

    public PTuple(PythonClass cls, Object[] elements) {
        super(cls);
        this.store = new ObjectSequenceStorage(elements);
    }

    public PTuple(PythonClass cls, SequenceStorage store) {
        super(cls);
        this.store = store;
    }

    @Deprecated
    public Object[] getArray() {
        // TODO disallow direct array access
        if (store instanceof ObjectSequenceStorage) {
            return store.getInternalArray();
        }
        throw new UnsupportedOperationException();
    }

    @Override
    public int len() {
        return store.length();
    }

    public boolean isEmpty() {
        return len() == 0;
    }

    @Deprecated
    public Object getItemNormalized(int index) {
        return store.getItemNormalized(index);
    }

    @Override
    public Object getSlice(PythonObjectFactory factory, int start, int stop, int step, int length) {
        return factory.createTuple(store.getSliceInBound(start, stop, step, length));
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        if (store instanceof ObjectSequenceStorage) {
            StringBuilder buf = new StringBuilder("(");
            Object[] array = store.getInternalArray();
            for (int i = 0; i < array.length - 1; i++) {
                buf.append(array[i]);
                buf.append(", ");
            }

            if (array.length > 0) {
                buf.append(array[array.length - 1]);
            }

            if (array.length == 1) {
                buf.append(",");
            }

            buf.append(")");
            return buf.toString();
        } else {
            return String.format("tuple(%s)", store);
        }
    }

    @SuppressWarnings({"unused", "static-method"})
    public PTuple __mul__(int value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SequenceStorage getSequenceStorage() {
        return store;
    }

    @Override
    public void setSequenceStorage(SequenceStorage store) {
        this.store = store;
    }

    @Override
    public int index(Object value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int compareTo(Object o) {
        return SequenceUtil.cmp(this, (PSequence) o);
    }

    @Override
    public boolean equals(Object other) {
        CompilerAsserts.neverPartOfCompilation();
        if (!(other instanceof PTuple)) {
            return false;
        }

        PTuple otherTuple = (PTuple) other;
        return store.equals(otherTuple.store);
    }

    @Override
    public int hashCode() {
        CompilerAsserts.neverPartOfCompilation();
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
