/*
 * Copyright (c) 2019, Oracle and/or its affiliates.
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

import com.oracle.graal.python.builtins.objects.type.AbstractPythonClass;
import com.oracle.truffle.api.profiles.ConditionProfile;

public final class MroSequenceStorage extends TypedSequenceStorage {

    private AbstractPythonClass[] values;

    public MroSequenceStorage(AbstractPythonClass[] elements) {
        this.values = elements;
        this.capacity = elements.length;
        this.length = elements.length;
    }

    public MroSequenceStorage(AbstractPythonClass[] elements, int length) {
        this.values = elements;
        this.capacity = elements.length;
        this.length = length;
    }

    public MroSequenceStorage(int capacity) {
        this.values = new AbstractPythonClass[capacity];
        this.capacity = capacity;
        this.length = 0;
    }

    @Override
    public Object getItemNormalized(int idx) {
        return values[idx];
    }

    @Override
    public void setItemNormalized(int idx, Object value) {
        if (value instanceof AbstractPythonClass) {
            setClassItemNormalized(idx, (AbstractPythonClass) value);
        } else {
            throw new SequenceStoreException(value);
        }
    }

    public void setClassItemNormalized(int idx, AbstractPythonClass value) {
        values[idx] = value;
    }

    @Override
    public void insertItem(int idx, Object value) {
        ensureCapacity(length + 1);

        // shifting tail to the right by one slot
        for (int i = values.length - 1; i > idx; i--) {
            values[i] = values[i - 1];
        }

        setItemNormalized(idx, value);
        length++;
    }

    @Override
    public void copyItem(int idxTo, int idxFrom) {
        values[idxTo] = values[idxFrom];
    }

    @Override
    public MroSequenceStorage getSliceInBound(int start, int stop, int step, int sliceLength) {
        AbstractPythonClass[] newArray = new AbstractPythonClass[sliceLength];

        if (step == 1) {
            System.arraycopy(values, start, newArray, 0, sliceLength);
            return new MroSequenceStorage(newArray);
        }

        for (int i = start, j = 0; j < sliceLength; i += step, j++) {
            newArray[j] = values[i];
        }

        return new MroSequenceStorage(newArray);
    }

    public void setObjectSliceInBound(int start, int stop, int step, MroSequenceStorage sequence, ConditionProfile sameLengthProfile) {
        int otherLength = sequence.length();

        // range is the whole sequence?
        if (sameLengthProfile.profile(start == 0 && stop == length && step == 1)) {
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

    @Override
    public SequenceStorage copy() {
        return new MroSequenceStorage(Arrays.copyOf(values, length));
    }

    @Override
    public SequenceStorage createEmpty(int newCapacity) {
        return new MroSequenceStorage(newCapacity);
    }

    @Override
    public Object[] getInternalArray() {
        return values;
    }

    public AbstractPythonClass[] getInternalClassArray() {
        return values;
    }

    @Override
    public void increaseCapacityExactWithCopy(int newCapacity) {
        values = Arrays.copyOf(values, newCapacity);
        capacity = values.length;
    }

    @Override
    public void increaseCapacityExact(int newCapacity) {
        values = new AbstractPythonClass[newCapacity];
        capacity = values.length;
    }

    public Object popObject() {
        Object pop = values[length - 1];
        length--;
        return pop;
    }

    @Override
    public void reverse() {
        if (length > 0) {
            int head = 0;
            int tail = length - 1;
            int middle = (length - 1) / 2;

            for (; head <= middle; head++, tail--) {
                AbstractPythonClass temp = values[head];
                values[head] = values[tail];
                values[tail] = temp;
            }
        }
    }

    @Override
    public Object getIndicativeValue() {
        return null;
    }

    @Override
    public boolean equals(SequenceStorage other) {
        if (other.length() != length()) {
            return false;
        }
        if (this == other) {
            return true;
        }
        int nominalLength = length() <= other.length() ? length() : other.length();
        Object[] otherArray = other.getInternalArray();
        for (int i = 0; i < nominalLength; i++) {
            if (!values[i].equals(otherArray[i])) {
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
        this.values = (AbstractPythonClass[]) arrayObject;
    }

    @Override
    public ListStorageType getElementType() {
        return ListStorageType.Generic;
    }
}
