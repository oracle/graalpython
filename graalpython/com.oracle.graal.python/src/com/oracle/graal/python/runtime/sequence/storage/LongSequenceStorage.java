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
package com.oracle.graal.python.runtime.sequence.storage;

import java.math.BigInteger;
import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives;

public final class LongSequenceStorage extends ArrayBasedSequenceStorage {

    private long[] values;

    public LongSequenceStorage() {
        values = new long[]{};
    }

    public LongSequenceStorage(long[] elements) {
        this.values = elements;
        this.capacity = values.length;
        this.length = elements.length;
    }

    public LongSequenceStorage(long[] elements, int length) {
        this.values = elements;
        this.capacity = values.length;
        this.length = length;
    }

    public LongSequenceStorage(int capacity) {
        this.values = new long[capacity];
        this.capacity = capacity;
        this.length = 0;
    }

    private void increaseCapacityExactWithCopy(int newCapacity) {
        values = Arrays.copyOf(values, newCapacity);
        capacity = values.length;
    }

    public void ensureCapacity(int newCapacity) throws ArithmeticException {
        if (CompilerDirectives.injectBranchProbability(CompilerDirectives.UNLIKELY_PROBABILITY, newCapacity > capacity)) {
            increaseCapacityExactWithCopy(capacityFor(newCapacity));
        }
    }

    @Override
    public LongSequenceStorage createEmpty(int newCapacity) {
        return new LongSequenceStorage(newCapacity);
    }

    public void reverse() {
        if (length > 0) {
            int head = 0;
            int tail = length - 1;
            int middle = (length - 1) / 2;

            for (; head <= middle; head++, tail--) {
                long temp = values[head];
                values[head] = values[tail];
                values[tail] = temp;
            }
        }
    }

    public long[] getInternalLongArray() {
        return values;
    }

    @Override
    public Object getItemNormalized(int idx) {
        return getLongItemNormalized(idx);
    }

    public long getLongItemNormalized(int idx) {
        return values[idx];
    }

    @Override
    public void setItemNormalized(int idx, Object val) throws SequenceStoreException {
        Object value = (val instanceof Integer) ? BigInteger.valueOf((int) val).longValue() : val;
        value = (val instanceof BigInteger) ? ((BigInteger) val).longValue() : value;
        if (value instanceof Long) {
            setLongItemNormalized(idx, (long) value);
        } else {
            throw new SequenceStoreException(value);
        }
    }

    public void setLongItemNormalized(int idx, long value) {
        values[idx] = value;
    }

    public void insertLongItem(int idx, long value) {
        ensureCapacity(length + 1);

        // shifting tail to the right by one slot
        for (int i = values.length - 1; i > idx; i--) {
            values[i] = values[i - 1];
        }

        values[idx] = value;
        incLength();
    }

    public int indexOfLong(long value) {
        for (int i = 0; i < length; i++) {
            if (values[i] == value) {
                return i;
            }
        }

        return -1;
    }

    @Override
    public Object getIndicativeValue() {
        return 0;
    }

    @Override
    public Object getInternalArrayObject() {
        return values;
    }

    @Override
    public Object getCopyOfInternalArrayObject() {
        return Arrays.copyOf(values, length);
    }

    public Object[] getCopyOfInternalArray() {
        /*
         * Have to box and copy.
         */
        Object[] boxed = new Object[length];

        for (int i = 0; i < length; i++) {
            boxed[i] = values[i];
        }

        return boxed;
    }

    @Override
    public void setInternalArrayObject(Object arrayObject) {
        this.values = (long[]) arrayObject;
    }

    @Override
    public StorageType getElementType() {
        return StorageType.Long;
    }
}
