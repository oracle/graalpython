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

import static com.oracle.graal.python.nodes.truffle.TruffleStringMigrationHelpers.assertContainsNoJavaString;
import static com.oracle.graal.python.nodes.truffle.TruffleStringMigrationHelpers.assertNoJavaString;

import java.util.Arrays;

import com.oracle.graal.python.util.PythonUtils;

public final class ObjectSequenceStorage extends ArrayBasedSequenceStorage {

    private Object[] values;

    public ObjectSequenceStorage(Object[] elements) {
        this.values = elements;
        this.capacity = elements.length;
        this.length = elements.length;
        assertContainsNoJavaString(elements);
    }

    public ObjectSequenceStorage(Object[] elements, int length) {
        this.values = elements;
        this.capacity = elements.length;
        this.length = length;
        assertContainsNoJavaString(elements);
    }

    public ObjectSequenceStorage(int capacity) {
        this.values = new Object[capacity];
        this.capacity = capacity;
        this.length = 0;
    }

    @Override
    public Object getItemNormalized(int idx) {
        return values[idx];
    }

    @Override
    public void setItemNormalized(int idx, Object value) {
        values[idx] = assertNoJavaString(value);
    }

    @Override
    public void insertItem(int idx, Object value) {
        ensureCapacity(length + 1);

        // shifting tail to the right by one slot
        for (int i = values.length - 1; i > idx; i--) {
            values[i] = values[i - 1];
        }

        values[idx] = assertNoJavaString(value);
        incLength();
    }

    @Override
    public SequenceStorage copy() {
        return new ObjectSequenceStorage(getCopyOfInternalArray());
    }

    @Override
    public ArrayBasedSequenceStorage createEmpty(int newCapacity) {
        return new ObjectSequenceStorage(newCapacity);
    }

    @Override
    public Object[] getInternalArray() {
        return values;
    }

    @Override
    public Object[] getCopyOfInternalArray() {
        return PythonUtils.arrayCopyOf(values, length);
    }

    @Override
    public void increaseCapacityExactWithCopy(int newCapacity) {
        values = PythonUtils.arrayCopyOf(values, newCapacity);
        capacity = values.length;
    }

    @Override
    public ObjectSequenceStorage generalizeFor(Object value, SequenceStorage other) {
        return this;
    }

    @Override
    public Object getIndicativeValue() {
        return null;
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
        this.values = (Object[]) arrayObject;
        this.capacity = values.length;
        assertContainsNoJavaString(values);
    }

    @Override
    public StorageType getElementType() {
        return StorageType.Generic;
    }
}
