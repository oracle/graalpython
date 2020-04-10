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
package com.oracle.graal.python.runtime.sequence.storage;

import java.util.Arrays;

import com.oracle.graal.python.builtins.objects.tuple.PTuple;

public final class TupleSequenceStorage extends TypedSequenceStorage {

    private PTuple[] values;

    public TupleSequenceStorage() {
        values = new PTuple[]{};
    }

    public TupleSequenceStorage(PTuple[] elements) {
        this.values = elements;
        this.capacity = values.length;
        this.length = elements.length;
    }

    public TupleSequenceStorage(PTuple[] elements, int length) {
        this.values = elements;
        this.capacity = values.length;
        this.length = length;
    }

    public TupleSequenceStorage(int capacity) {
        this.values = new PTuple[capacity];
        this.capacity = capacity;
        this.length = 0;
    }

    @Override
    protected void increaseCapacityExactWithCopy(int newCapacity) {
        values = Arrays.copyOf(values, newCapacity);
        capacity = values.length;
    }

    @Override
    protected void increaseCapacityExact(int newCapacity) {
        values = new PTuple[newCapacity];
        capacity = values.length;
    }

    @Override
    public SequenceStorage copy() {
        return new TupleSequenceStorage(Arrays.copyOf(values, length));
    }

    @Override
    public SequenceStorage createEmpty(int newCapacity) {
        return new TupleSequenceStorage(newCapacity);
    }

    @Override
    public Object[] getInternalArray() {
        /**
         * Have to box and copy.
         */
        Object[] boxed = new Object[length];

        for (int i = 0; i < length; i++) {
            boxed[i] = values[i];
        }

        return boxed;
    }

    public PTuple[] getInternalPTupleArray() {
        return values;
    }

    @Override
    public Object getItemNormalized(int idx) {
        return getPTupleItemNormalized(idx);
    }

    public PTuple getPTupleItemNormalized(int idx) {
        return values[idx];
    }

    @Override
    public void setItemNormalized(int idx, Object value) throws SequenceStoreException {
        if (value instanceof PTuple) {
            setPTupleItemNormalized(idx, (PTuple) value);
        } else {
            throw new SequenceStoreException(value);
        }
    }

    public void setPTupleItemNormalized(int idx, PTuple value) {
        values[idx] = value;
    }

    @Override
    public void insertItem(int idx, Object value) throws SequenceStoreException {
        if (value instanceof PTuple) {
            insertPTupleItem(idx, (PTuple) value);
        } else {
            throw new SequenceStoreException(value);
        }
    }

    @Override
    public void copyItem(int idxTo, int idxFrom) {
        values[idxTo] = values[idxFrom];
    }

    public void insertPTupleItem(int idx, PTuple value) {
        ensureCapacity(length + 1);

        // shifting tail to the right by one slot
        for (int i = values.length - 1; i > idx; i--) {
            values[i] = values[i - 1];
        }

        values[idx] = value;
        length++;
    }

    @Override
    public SequenceStorage getSliceInBound(int start, int stop, int step, int sliceLength) {
        PTuple[] newArray = new PTuple[sliceLength];

        if (step == 1) {
            System.arraycopy(values, start, newArray, 0, sliceLength);
            return new TupleSequenceStorage(newArray);
        }

        for (int i = start, j = 0; j < sliceLength; i += step, j++) {
            newArray[j] = values[i];
        }

        return new TupleSequenceStorage(newArray);
    }

    public void setPTupleSliceInBound(int start, int stop, int step, TupleSequenceStorage sequence) {
        int otherLength = sequence.length();

        // range is the whole sequence?
        if (start == 0 && stop == length) {
            values = Arrays.copyOf(sequence.values, otherLength);
            length = otherLength;
            minimizeCapacity();
            return;
        }

        ensureCapacity(stop);

        for (int i = start, j = 0; i < stop; i += step, j++) {
            values[i] = sequence.values[j];
        }

        length = length > stop ? length : stop;
    }

    public PTuple popPTuple() {
        PTuple pop = values[capacity - 1];
        length--;
        return pop;
    }

    public int indexOfPTuple(PTuple value) {
        for (int i = 0; i < length; i++) {
            if (values[i] == value) {
                return i;
            }
        }

        return -1;
    }

    public void appendPTuple(PTuple value) {
        ensureCapacity(length + 1);
        values[length] = value;
        length++;
    }

    public void extendWithPTupleStorage(TupleSequenceStorage other) {
        int extendedLength = length + other.length();
        ensureCapacity(extendedLength);
        PTuple[] otherValues = other.values;

        for (int i = length, j = 0; i < extendedLength; i++, j++) {
            values[i] = otherValues[j];
        }

        length = extendedLength;
    }

    @Override
    public void reverse() {
        if (length > 0) {
            int head = 0;
            int tail = length - 1;
            int middle = (length - 1) / 2;

            for (; head <= middle; head++, tail--) {
                PTuple temp = values[head];
                values[head] = values[tail];
                values[tail] = temp;
            }
        }
    }

    @Override
    public Object getIndicativeValue() {
        return length > 0 ? values[0] : null;
    }

    @Override
    public boolean equals(SequenceStorage other) {
        if (!(other instanceof TupleSequenceStorage)) {
            return false;
        }
        TupleSequenceStorage otherTuple = (TupleSequenceStorage) other;
        if (otherTuple.length() != length()) {
            return false;
        }

        PTuple[] otherArray = otherTuple.getInternalPTupleArray();
        for (int i = 0; i < length(); i++) {
            if (values[i] != otherArray[i]) {
                return false;
            }
        }

        return true;
    }

    @Override
    public Object getInternalArrayObject() {
        return values;
    }

    @Override
    public Object getCopyOfInternalArrayObject() {
        return Arrays.copyOf(values, length);
    }

    @Override
    public void setInternalArrayObject(Object arrayObject) {
        this.values = (PTuple[]) arrayObject;
    }

    @Override
    public ListStorageType getElementType() {
        return ListStorageType.Tuple;
    }
}
