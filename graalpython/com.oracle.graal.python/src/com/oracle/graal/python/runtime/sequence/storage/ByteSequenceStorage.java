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

import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.util.Arrays;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.runtime.sequence.SequenceUtil;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

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

    @TruffleBoundary(allowInlining = true, transferToInterpreterOnException = false)
    public byte[] getInternalByteArray() {
        if (length != values.length) {
            assert length < values.length;
            return Arrays.copyOf(values, length);
        }
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

    @Override
    public void setSliceInBound(int start, int stop, int step, SequenceStorage sequence) throws SequenceStoreException {
        if (sequence instanceof ByteSequenceStorage) {
            setByteSliceInBound(start, stop, step, (ByteSequenceStorage) sequence);
        } else if (sequence instanceof IntSequenceStorage) {
            setByteSliceInBound(start, stop, step, (IntSequenceStorage) sequence);
        } else if (sequence instanceof EmptySequenceStorage) {
            setByteSliceInBound(start, stop, step, new IntSequenceStorage(0));
        } else {
            throw new SequenceStoreException();
        }
    }

    @TruffleBoundary(allowInlining = true, transferToInterpreterOnException = false)
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
                throw PythonLanguage.getCore().raise(ValueError, "byte must be in range(0, 256)");
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

    @TruffleBoundary(allowInlining = true, transferToInterpreterOnException = false)
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

    @Override
    public void delSlice(int startParam, int stopParam, int stepParam) {
        int start = startParam;
        int stop = stopParam;
        int step = stepParam;
        if ((stop == SequenceUtil.MISSING_INDEX || stop >= length) && step == 1) {
            length = start;
        } else if ((start == 0 && stop >= length) && step == 1) {
            length = 0;
        } else {
            int decraseLen; // how much will be the result array shorter
            int index;  // index of the "old" array
            if (step < 0) {
                // For the simplicity of algorithm, then start and stop are swapped.
                // The start index has to recalculated according the step, because
                // the algorithm bellow removes the start itema and then start + step ....
                step = Math.abs(step);
                stop++;
                int tmpStart = stop + ((start - stop) % step);
                stop = start + 1;
                start = tmpStart;
            }
            int arrayIndex = start; // pointer to the "new" form of array
            if (step == 1) {
                // this is easy, just remove the part of array
                decraseLen = stop - start;
                index = start + decraseLen;
            } else {
                int nextStep = index = start; // nextStep is a pointer to the next removed item
                decraseLen = (stop - start - 1) / step + 1;
                for (; index < stop && nextStep < stop; index++) {
                    if (nextStep == index) {
                        nextStep += step;
                    } else {
                        values[arrayIndex++] = values[index];
                    }
                }
            }
            if (decraseLen > 0) {
                // shift all other items in array behind the last change
                for (; index < length; arrayIndex++, index++) {
                    values[arrayIndex] = values[index];
                }
                // change the result length
                // TODO Shouldn't we realocate the array, if the chane is big?
                // Then unnecessary big array is kept in the memory.
                length = length - decraseLen;
            }
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

    @Override
    public void append(Object value) throws SequenceStoreException {
        if (value instanceof Integer) {
            appendInt((int) value);
        } else if (value instanceof Long) {
            appendLong((long) value);
        } else if (value instanceof PInt) {
            try {
                appendInt(((PInt) value).intValueExact());
            } catch (ArithmeticException e) {
                throw SequenceStoreException.INSTANCE;
            }
        } else if (value instanceof Byte) {
            appendByte((byte) value);
        } else {
            throw SequenceStoreException.INSTANCE;
        }
    }

    public void appendLong(long value) {
        if (value < 0 || value >= 256) {
            throw SequenceStoreException.INSTANCE;
        }
        ensureCapacity(length + 1);
        values[length] = (byte) value;
        length++;
    }

    public void appendInt(int value) {
        if (value < 0 || value >= 256) {
            throw SequenceStoreException.INSTANCE;
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
    public void extend(SequenceStorage other) throws SequenceStoreException {
        if (other instanceof ByteSequenceStorage) {
            extendWithByteStorage((ByteSequenceStorage) other);
        } else if (other instanceof IntSequenceStorage) {
            extendWithIntStorage((IntSequenceStorage) other);
        } else if (other instanceof ObjectSequenceStorage) {
            extendWithObjectStorage((ObjectSequenceStorage) other);
        } else {
            throw SequenceStoreException.INSTANCE;
        }
    }

    private void extendWithByteStorage(ByteSequenceStorage other) {
        int extendedLength = length + other.length();
        ensureCapacity(extendedLength);
        byte[] otherValues = other.values;

        for (int i = length, j = 0; i < extendedLength; i++, j++) {
            values[i] = otherValues[j];
        }

        length = extendedLength;
    }

    private void extendWithIntStorage(IntSequenceStorage other) {
        int extendedLength = length + other.length();
        ensureCapacity(extendedLength);
        int[] otherValues = other.getInternalIntArray();

        for (int i = length, j = 0; i < extendedLength; i++, j++) {
            int otherValue = otherValues[j];
            if (otherValue < 0 || otherValue >= 256) {
                throw SequenceStoreException.INSTANCE;
            }
            values[i] = (byte) otherValue;
        }

        length = extendedLength;
    }

    private void extendWithObjectStorage(ObjectSequenceStorage other) {
        int extendedLength = length + other.length();
        ensureCapacity(extendedLength);
        Object[] otherValues = other.getInternalArray();

        for (int i = length, j = 0; i < extendedLength; i++, j++) {
            Object otherValue = otherValues[j];
            long value = 0;
            if (otherValue instanceof Integer) {
                value = (int) otherValue;
            } else if (otherValue instanceof Long) {
                value = (long) otherValue;
            } else if (otherValue instanceof PInt) {
                try {
                    value = ((PInt) otherValue).intValueExact();
                } catch (ArithmeticException e) {
                    throw SequenceStoreException.INSTANCE;
                }
            }
            if (value < 0 || value >= 256) {
                throw SequenceStoreException.INSTANCE;
            }
            values[i] = (byte) value;
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
