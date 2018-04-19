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
package com.oracle.graal.python.builtins.objects.list;

import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.SequenceUtil;
import com.oracle.graal.python.runtime.sequence.storage.EmptySequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStoreException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.ValueProfile;

public final class PList extends PSequence {
    private SequenceStorage store;

    public PList(PythonClass cls, SequenceStorage store) {
        super(cls);
        this.store = store;
    }

    @Override
    public final SequenceStorage getSequenceStorage() {
        return store;
    }

    @Override
    public final Object getItem(int idx) {
        int index = SequenceUtil.normalizeIndex(idx, store.length(), "list index out of range");
        return store.getItemNormalized(index);
    }

    public final void setSequenceStorage(SequenceStorage newStorage) {
        this.store = newStorage;
    }

    @Override
    public final Object getSlice(PythonObjectFactory factory, int start, int stop, int step, int length) {
        return factory.createList(getPythonClass(), store.getSliceInBound(start, stop, step, length));
    }

    @Override
    public final void setSlice(PSlice slice, PSequence value) {
        setSlice(slice.getStart(), slice.getStop(), slice.getStep(), value);
    }

    @Override
    public final void setSlice(int start, int stop, int step, PSequence value) {
        final int normalizedStart = SequenceUtil.normalizeSliceStart(start, step, store.length(), "list assignment index out of range");
        int normalizedStop = SequenceUtil.normalizeSliceStop(stop, step, store.length(), "list assignment index out of range");

        if (normalizedStop < normalizedStart) {
            normalizedStop = normalizedStart;
        }

        try {
            store.setSliceInBound(normalizedStart, normalizedStop, step, value.getSequenceStorage());
        } catch (SequenceStoreException e) {
            store = store.generalizeFor(value.getSequenceStorage().getIndicativeValue());

            try {
                store.setSliceInBound(start, stop, step, value.getSequenceStorage());
            } catch (SequenceStoreException ex) {
                throw new IllegalStateException();
            }
        }
    }

    @Override
    public final void delItem(int idx) {
        int index = SequenceUtil.normalizeIndex(idx, store.length(), "list index out of range");
        store.delItemInBound(index);
    }

    public final void delSlice(PSlice slice) {
        int start = SequenceUtil.normalizeSliceStart(slice, store.length(), "list index out of range");
        final int stop = SequenceUtil.normalizeSliceStop(slice, store.length(), "list index out of range");
        store.delSlice(start, stop);
    }

    @Override
    public final boolean lessThan(PSequence sequence) {
        return false;
    }

    @Override
    public final String toString() {
        StringBuilder buf = new StringBuilder("[");

        for (int i = 0; i < store.length(); i++) {
            Object item = store.getItemNormalized(i);
            buf.append(toString(item));

            if (i < store.length() - 1) {
                buf.append(", ");
            }
        }

        buf.append("]");
        return buf.toString();
    }

    public final void sort() {
        store.sort();
    }

    @Override
    public final int len() {
        return store.length();
    }

    public final PList __mul__(ValueProfile storeProfile, int value) {
        assert value > 0;

        SequenceStorage profiledStore = storeProfile.profile(store);
        SequenceStorage newStore = profiledStore.createEmpty(Math.multiplyExact(value, profiledStore.length()));

        try {
            for (int i = 0; i < value; i++) {
                newStore.extend(profiledStore);
            }
        } catch (SequenceStoreException e) {
            throw new IllegalStateException();
        }

        return new PList(getPythonClass(), newStore);
    }

    public final void reverse() {
        store.reverse();
    }

    public final void append(Object value) {
        if (store instanceof EmptySequenceStorage) {
            store = store.generalizeFor(value);
        }

        try {
            store.append(value);
        } catch (SequenceStoreException e) {
            store = store.generalizeFor(value);

            try {
                store.append(value);
            } catch (SequenceStoreException e1) {
                throw new IllegalStateException();
            }
        }
    }

    public final void extend(PList appendee) {
        SequenceStorage other = appendee.getSequenceStorage();

        try {
            store.extend(other);
        } catch (SequenceStoreException e) {
            store = store.generalizeFor(other.getIndicativeValue());

            try {
                store.extend(other);
            } catch (SequenceStoreException e1) {
                throw new IllegalStateException();
            }
        }
    }

    public final PList __add__(PList other) throws ArithmeticException {
        SequenceStorage otherStore = other.getSequenceStorage();
        SequenceStorage newStore = store.copy();

        try {
            newStore.extend(otherStore);
        } catch (SequenceStoreException e) {
            newStore = newStore.generalizeFor(otherStore.getIndicativeValue());

            try {
                newStore.extend(otherStore);
            } catch (SequenceStoreException e1) {
                throw new IllegalStateException();
            }
        }

        return new PList(getPythonClass(), newStore);
    }

    @Override
    public final int index(Object value) {
        return store.index(value);
    }

    public final void insert(int index, Object value) {
        try {
            store.insertItem(index, value);
        } catch (SequenceStoreException e) {
            store = store.generalizeFor(value);

            try {
                store.insertItem(index, value);
            } catch (SequenceStoreException e1) {
                throw new IllegalStateException();
            }
        }
    }

    @Override
    public final boolean equals(Object other) {
        if (!(other instanceof PList)) {
            return false;
        }

        PList otherList = (PList) other;
        SequenceStorage otherStore = otherList.getSequenceStorage();
        return store.equals(otherStore);
    }

    @Override
    public final int hashCode() {
        return super.hashCode();
    }

    public int count(Object arg) {
        return store.count(arg);
    }

    public static PList require(Object value) {
        if (value instanceof PList) {
            return (PList) value;
        }
        CompilerDirectives.transferToInterpreter();
        throw new AssertionError("PList required.");
    }

    public static PList expect(Object value) throws UnexpectedResultException {
        if (value instanceof PList) {
            return (PList) value;
        }
        throw new UnexpectedResultException(value);
    }

}
