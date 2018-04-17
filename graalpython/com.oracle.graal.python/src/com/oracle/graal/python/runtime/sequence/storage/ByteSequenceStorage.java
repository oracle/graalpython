/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
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

import static com.oracle.graal.python.runtime.exception.PythonErrorType.NotImplementedError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.util.Arrays;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.runtime.sequence.SequenceUtil;
import com.oracle.truffle.api.nodes.ExplodeLoop;

public final class ByteSequenceStorage extends TypedSequenceStorage {

    private byte[] values;

    public ByteSequenceStorage(byte[] elements) {
        this.values = elements;
        this.capacity = values.length;
        this.length = elements.length;
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

    public byte[] getInternalByteArray() {
        return values;
    }

    @Override
    public Object getItemNormalized(int idx) {
        return getIntItemNormalized(idx);
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
                throw PythonLanguage.getCore().raise(ValueError, "byte must be in range(0, 256)");
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
            throw SequenceStoreException.INSTANCE;
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

    @Override
    public void setSliceInBound(int start, int stop, int step, SequenceStorage sequence) throws SequenceStoreException {
        if (sequence instanceof ByteSequenceStorage) {
            setByteSliceInBound(start, stop, step, (ByteSequenceStorage) sequence);
        } else if (sequence instanceof IntSequenceStorage) {
            setByteSliceInBound(start, stop, step, (IntSequenceStorage) sequence);
        } else {
            throw new SequenceStoreException();
        }
    }

    public void setByteSliceInBound(int start, int stop, int step, IntSequenceStorage sequence) {
        ensureCapacity(stop);

        int[] seqValues = sequence.getInternalIntArray();
        for (int i = start, j = 0; i < stop; i += step, j++) {
            values[i] = ((Integer) seqValues[j]).byteValue();
        }

        length = length > stop ? length : stop;
    }

    public void setByteSliceInBound(int start, int stop, int step, ByteSequenceStorage sequence) {
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

    @Override
    public void delSlice(int start, int stop) {
        if (stop == SequenceUtil.MISSING_INDEX || stop >= length) {
            length = start;
        } else if (start == 0 && stop >= length) {
            length = 0;
        } else {
            throw PythonLanguage.getCore().raise(NotImplementedError, "delete slice not yet supported in this case: [%s: %s]", start, stop);
        }
    }

    @Override
    public void delItemInBound(int idx) {
        if (values.length - 1 == idx) {
            popInt();
        } else {
            popInBound(idx);
        }
    }

    @Override
    public Object popInBound(int idx) {
        int pop = values[idx] & 0xFF;

        for (int i = idx; i < values.length - 1; i++) {
            values[i] = values[i + 1];
        }

        length--;
        return pop;
    }

    public int popInt() {
        int pop = values[capacity - 1] & 0xFF;
        length--;
        return pop;
    }

    @Override
    public int index(Object value) {
        if (value instanceof Byte) {
            return indexOfByte((byte) value);
        } else if (value instanceof Integer) {
            return indexOfInt((int) value);
        } else {
            return super.index(value);
        }

    }

    @ExplodeLoop
    public int indexOfByte(byte value) {
        for (int i = 0; i < length; i++) {
            if (values[i] == value) {
                return i;
            }
        }

        return -1;
    }

    @ExplodeLoop
    public int indexOfInt(int value) {
        for (int i = 0; i < length; i++) {
            if ((values[i] & 0xFF) == value) {
                return i;
            }
        }

        return -1;
    }

    @Override
    public void append(Object value) throws SequenceStoreException {
        if (value instanceof Integer) {
            appendInt((int) value);
        } else if (value instanceof Byte) {
            appendByte((byte) value);
        } else {
            throw SequenceStoreException.INSTANCE;
        }
    }

    public void appendInt(int value) {
        ensureCapacity(length + 1);
        values[length] = ((Integer) value).byteValue();
        length++;
    }

    public void appendByte(byte value) {
        ensureCapacity(length + 1);
        values[length] = value;
        length++;
    }

    @Override
    public void extend(SequenceStorage other) throws SequenceStoreException {
        if (other instanceof ByteSequenceStorage) {
            extendWithByteStorage((ByteSequenceStorage) other);
        } else {
            throw SequenceStoreException.INSTANCE;
        }
    }

    // @ExplodeLoop
    public void extendWithByteStorage(ByteSequenceStorage other) {
        int extendedLength = length + other.length();
        ensureCapacity(extendedLength);
        byte[] otherValues = other.values;

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
                byte temp = values[head];
                values[head] = values[tail];
                values[tail] = temp;
            }
        }
    }

    @Override
    public void sort() {
        byte[] copy = Arrays.copyOf(values, length);
        Arrays.sort(copy);
        values = copy;
        minimizeCapacity();
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
}
