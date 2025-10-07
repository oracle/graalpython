/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates.
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

import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;

public final class IntSequenceStorage extends ArrayBasedSequenceStorage {

    private int[] values;

    public IntSequenceStorage() {
        this.values = new int[DEFAULT_CAPACITY];
        this.capacity = values.length;
    }

    public IntSequenceStorage(int[] elements) {
        this.values = elements;
        this.capacity = values.length;
        this.length = elements.length;
    }

    public IntSequenceStorage(int[] elements, int length) {
        this.values = elements;
        this.capacity = values.length;
        this.length = length;
    }

    public IntSequenceStorage(int capacity) {
        this.values = new int[capacity];
        this.capacity = capacity;
        this.length = 0;
    }

    private void increaseCapacityExactWithCopy(int newCapacity) {
        values = PythonUtils.arrayCopyOf(values, newCapacity);
        capacity = values.length;
    }

    public void ensureCapacity(int newCapacity) throws ArithmeticException {
        if (CompilerDirectives.injectBranchProbability(CompilerDirectives.UNLIKELY_PROBABILITY, newCapacity > capacity)) {
            increaseCapacityExactWithCopy(capacityFor(newCapacity));
        }
    }

    @Override
    public IntSequenceStorage createEmpty(int newCapacity) {
        return new IntSequenceStorage(newCapacity);
    }

    public void reverse() {
        if (length > 0) {
            int head = 0;
            int tail = length - 1;
            int middle = (length - 1) / 2;

            for (; head <= middle; head++, tail--) {
                int temp = values[head];
                values[head] = values[tail];
                values[tail] = temp;
            }
        }
    }

    public int[] getInternalIntArray() {
        return values;
    }

    public int getIntItemNormalized(int idx) {
        return values[idx];
    }

    public void setIntItemNormalized(int idx, int value) {
        values[idx] = value;
    }

    public void insertIntItem(int idx, int value) {
        ensureCapacity(length + 1);

        // shifting tail to the right by one slot
        for (int i = values.length - 1; i > idx; i--) {
            values[i] = values[i - 1];
        }

        values[idx] = value;
        incLength();
    }

    public int indexOfInt(int value) {
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
        this.values = (int[]) arrayObject;
    }

    @Override
    public StorageType getElementType() {
        return StorageType.Int;
    }
}
