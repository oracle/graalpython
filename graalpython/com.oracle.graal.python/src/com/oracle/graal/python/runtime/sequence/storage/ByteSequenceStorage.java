/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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

import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.nio.ByteBuffer;
import java.util.Arrays;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.nodes.util.CastToByteNode;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public final class ByteSequenceStorage extends TypedSequenceStorage {

    private byte[] values;

    public ByteSequenceStorage(byte[] elements) {
        this(elements, elements.length);
    }

    public ByteSequenceStorage(byte[] elements, int length) {
        this.values = elements;
        this.capacity = values.length;
        this.length = length;
    }

    public ByteSequenceStorage(int capacity) {
        this.values = new byte[capacity];
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
        values = new byte[newCapacity];
        capacity = values.length;
    }

    @Override
    public SequenceStorage copy() {
        return new ByteSequenceStorage(Arrays.copyOf(values, length));
    }

    @Override
    public SequenceStorage createEmpty(int newCapacity) {
        return new ByteSequenceStorage(newCapacity);
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

    @TruffleBoundary(allowInlining = true)
    public byte[] getInternalByteArray() {
        if (length != values.length) {
            assert length < values.length;
            return Arrays.copyOf(values, length);
        }
        return values;
    }

    @TruffleBoundary(allowInlining = true, transferToInterpreterOnException = false)
    public ByteBuffer getBufferView() {
        ByteBuffer view = ByteBuffer.wrap(values);
        view.limit(values.length);
        return view;
    }

    @Override
    public Object getItemNormalized(int idx) {
        return getIntItemNormalized(idx);
    }

    public final byte getByteItemNormalized(int idx) {
        return values[idx];
    }

    public int getIntItemNormalized(int idx) {
        return values[idx] & 0xFF;
    }

    @Override
    public void setItemNormalized(int idx, Object value) throws SequenceStoreException {
        if (value instanceof Byte) {
            setByteItemNormalized(idx, (byte) value);
        } else if (value instanceof Integer) {
            if ((int) value < 0 || (int) value >= 256) {
                throw PythonLanguage.getCore().raise(ValueError, CastToByteNode.INVALID_BYTE_VALUE);
            }
            setByteItemNormalized(idx, ((Integer) value).byteValue());
        } else {
            throw PythonLanguage.getCore().raise(TypeError, "an integer is required");
        }
    }

    public void setByteItemNormalized(int idx, byte value) {
        values[idx] = value;
    }

    @Override
    public void insertItem(int idx, Object value) throws SequenceStoreException {
        if (value instanceof Byte) {
            insertByteItem(idx, (byte) value);
        } else if (value instanceof Integer) {
            insertByteItem(idx, ((Integer) value).byteValue());
        } else {
            throw new SequenceStoreException(value);
        }
    }

    public void insertByteItem(int idx, byte value) {
        ensureCapacity(length + 1);

        // shifting tail to the right by one slot
        for (int i = values.length - 1; i > idx; i--) {
            values[i] = values[i - 1];
        }

        values[idx] = value;
        length++;
    }

    @Override
    public void copyItem(int idxTo, int idxFrom) {
        values[idxTo] = values[idxFrom];
    }

    @Override
    public ByteSequenceStorage getSliceInBound(int start, int stop, int step, int sliceLength) {
        byte[] newArray = new byte[sliceLength];

        if (step == 1) {
            System.arraycopy(values, start, newArray, 0, sliceLength);
            return new ByteSequenceStorage(newArray);
        }

        for (int i = start, j = 0; j < sliceLength; i += step, j++) {
            newArray[j] = values[i];
        }

        return new ByteSequenceStorage(newArray);
    }

    @TruffleBoundary
    public void setByteSliceInBound(int start, int stop, int step, IntSequenceStorage sequence) {
        int otherLength = sequence.length();
        int[] seqValues = sequence.getInternalIntArray();

        // (stop - start) = bytes to be replaced; otherLength = bytes to be written
        int newLength = length - (stop - start - otherLength);

        ensureCapacity(newLength);

        // if enlarging, we need to move the suffix first
        if (stop - start < otherLength) {
            assert length < newLength;
            for (int j = length - 1, k = newLength - 1; j >= stop; j--, k--) {
                values[k] = values[j];
            }
        }

        int i = start;
        for (int j = 0; j < otherLength; i += step, j++) {
            if (seqValues[j] < Byte.MIN_VALUE || seqValues[j] > Byte.MAX_VALUE) {
                throw PythonLanguage.getCore().raise(ValueError, CastToByteNode.INVALID_BYTE_VALUE);
            }
            values[i] = (byte) seqValues[j];
        }

        // if shrinking, move the suffix afterwards
        if (stop - start > otherLength) {
            assert stop >= 0;
            for (int j = i, k = 0; stop + k < values.length; j++, k++) {
                values[j] = values[stop + k];
            }
        }

        // for security
        Arrays.fill(values, newLength, values.length, (byte) 0);

        length = newLength;
    }

    @TruffleBoundary
    public void setByteSliceInBound(int start, int stop, int step, ByteSequenceStorage sequence) {
        int otherLength = sequence.length();

        // range is the whole sequence?
        if (start == 0 && stop == length) {
            values = Arrays.copyOf(sequence.values, otherLength);
            length = otherLength;
            minimizeCapacity();
            return;
        }

        // (stop - start) = bytes to be replaced; otherLength = bytes to be written
        int newLength = length - (stop - start - otherLength);

        ensureCapacity(newLength);

        // if enlarging, we need to move the suffix first
        if (stop - start < otherLength) {
            assert length < newLength;
            for (int j = length - 1, k = newLength - 1; j >= stop; j--, k--) {
                values[k] = values[j];
            }
        }

        int i = start;
        for (int j = 0; j < otherLength; i += step, j++) {
            values[i] = sequence.values[j];
        }

        // if shrinking, move the suffix afterwards
        if (stop - start > otherLength) {
            assert stop >= 0;
            for (int j = i, k = 0; stop + k < values.length; j++, k++) {
                values[j] = values[stop + k];
            }
        }

        // for security
        Arrays.fill(values, newLength, values.length, (byte) 0);

        length = newLength;
    }

    public int popInt() {
        int pop = values[capacity - 1] & 0xFF;
        length--;
        return pop;
    }

    public int indexOfByte(byte value) {
        for (int i = 0; i < length; i++) {
            if (values[i] == value) {
                return i;
            }
        }

        return -1;
    }

    public int indexOfInt(int value) {
        for (int i = 0; i < length; i++) {
            if ((values[i] & 0xFF) == value) {
                return i;
            }
        }

        return -1;
    }

    public void appendLong(long value) {
        if (value < 0 || value >= 256) {
            throw new SequenceStoreException(value);
        }
        ensureCapacity(length + 1);
        values[length] = (byte) value;
        length++;
    }

    public void appendInt(int value) {
        if (value < 0 || value >= 256) {
            throw new SequenceStoreException(value);
        }
        ensureCapacity(length + 1);
        values[length] = (byte) value;
        length++;
    }

    public void appendByte(byte value) {
        ensureCapacity(length + 1);
        values[length] = value;
        length++;
    }

    @Override
    public void reverse() {
        if (length > 0) {
            int head = 0;
            int tail = length - 1;
            int middle = (length - 1) / 2;

            for (; head <= middle; head++, tail--) {
                byte temp = values[head];
                values[head] = values[tail];
                values[tail] = temp;
            }
        }
    }

    @Override
    public Object getIndicativeValue() {
        return 0;
    }

    @Override
    public boolean equals(SequenceStorage other) {
        if (other.length() != length() || !(other instanceof ByteSequenceStorage)) {
            return false;
        }

        byte[] otherArray = ((ByteSequenceStorage) other).getInternalByteArray();
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
        this.values = (byte[]) arrayObject;
    }

    @Override
    public ListStorageType getElementType() {
        return ListStorageType.Byte;
    }
}
