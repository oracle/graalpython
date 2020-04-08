/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.runtime.sequence.storage;

import java.util.Arrays;

public final class CharSequenceStorage extends TypedSequenceStorage {

    private char[] values;

    public CharSequenceStorage(char[] elements) {
        super(ListStorageType.Char);
        this.values = elements;
        this.capacity = values.length;
        this.length = elements.length;
    }

    public CharSequenceStorage(int capacity) {
        super(ListStorageType.Char);
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
    public void reverse() {
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

    @Override
    public Object getCopyOfInternalArrayObject() {
        return Arrays.copyOf(values, length);
    }

    @Override
    public void setInternalArrayObject(Object arrayObject) {
        this.values = (char[]) arrayObject;
    }

    public void setCharItemNormalized(int idx, char value) {
        values[idx] = value;
    }
}
