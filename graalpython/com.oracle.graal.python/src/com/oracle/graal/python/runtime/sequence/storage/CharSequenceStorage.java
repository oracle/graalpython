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
package com.oracle.graal.python.runtime.sequence.storage;

import java.util.Arrays;

public final class CharSequenceStorage extends TypedSequenceStorage {

    private char[] values;

    public CharSequenceStorage(char[] elements) {
        this.values = elements;
        this.capacity = values.length;
        this.length = elements.length;
    }

    public CharSequenceStorage(int capacity) {
        this.values = new char[capacity];
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
        values = new char[newCapacity];
        capacity = values.length;
    }

    @Override
    public SequenceStorage copy() {
        return new CharSequenceStorage(Arrays.copyOf(values, length));
    }

    @Override
    public SequenceStorage createEmpty(int newCapacity) {
        return new CharSequenceStorage(newCapacity);
    }

    @Override
    public Object[] getInternalArray() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object getItemNormalized(int idx) {
        return getCharItemNormalized(idx);
    }

    public final char getCharItemNormalized(int idx) {
        return values[idx];
    }

    @Override
    public void setItemNormalized(int idx, Object value) throws SequenceStoreException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void insertItem(int idx, Object value) throws SequenceStoreException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void copyItem(int idxTo, int idxFrom) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CharSequenceStorage getSliceInBound(int start, int stop, int step, int sliceLength) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setSliceInBound(int start, int stop, int step, SequenceStorage sequence) throws SequenceStoreException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void delSlice(int startParam, int stopParam, int stepParam) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void delItemInBound(int idx) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object popInBound(int idx) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int index(Object value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void append(Object value) throws SequenceStoreException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void extend(SequenceStorage other) throws SequenceStoreException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void reverse() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void sort() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object getIndicativeValue() {
        return '\0';
    }

    @Override
    public boolean equals(SequenceStorage other) {
        if (other.length() != length() || !(other instanceof CharSequenceStorage)) {
            return false;
        }

        char[] otherArray = ((CharSequenceStorage) other).values;
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
}
