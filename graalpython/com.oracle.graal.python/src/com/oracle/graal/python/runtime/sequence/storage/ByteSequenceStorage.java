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

import java.nio.ByteOrder;
import java.util.Arrays;

import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.ArrayUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

@ExportLibrary(PythonBufferAccessLibrary.class)
public final class ByteSequenceStorage extends ArrayBasedSequenceStorage {

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
    public ByteSequenceStorage createEmpty(int newCapacity) {
        return new ByteSequenceStorage(newCapacity);
    }

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

    public byte getByteItemNormalized(int idx) {
        return values[idx];
    }

    public int getIntItemNormalized(int idx) {
        return values[idx] & 0xFF;
    }

    public void setByteItemNormalized(int idx, byte value) {
        values[idx] = value;
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

    public int indexOfByte(byte value) {
        return ArrayUtils.indexOf(values, 0, length, value);
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
        this.values = (byte[]) arrayObject;
    }

    @Override
    public StorageType getElementType() {
        return StorageType.Byte;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isBuffer() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isReadonly() {
        return false;
    }

    @ExportMessage
    int getBufferLength() {
        return length;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasInternalByteArray() {
        return true;
    }

    @ExportMessage
    public byte[] getInternalByteArray() {
        return values;
    }

    @ExportMessage
    byte readByte(int byteOffset) {
        return values[byteOffset];
    }

    @ExportMessage
    void writeByte(int byteOffset, byte value) {
        values[byteOffset] = value;
    }

    @ExportMessage
    short readShortByteOrder(int byteOffset, ByteOrder byteOrder) {
        return PythonUtils.byteArraySupport(byteOrder).getShort(values, byteOffset);
    }

    @ExportMessage
    void writeShortByteOrder(int byteOffset, short value, ByteOrder byteOrder) {
        PythonUtils.byteArraySupport(byteOrder).putShort(values, byteOffset, value);
    }

    @ExportMessage
    int readIntByteOrder(int byteOffset, ByteOrder byteOrder) {
        return PythonUtils.byteArraySupport(byteOrder).getInt(values, byteOffset);
    }

    @ExportMessage
    void writeIntByteOrder(int byteOffset, int value, ByteOrder byteOrder) {
        PythonUtils.byteArraySupport(byteOrder).putInt(values, byteOffset, value);
    }

    @ExportMessage
    long readLongByteOrder(int byteOffset, ByteOrder byteOrder) {
        return PythonUtils.byteArraySupport(byteOrder).getLong(values, byteOffset);
    }

    @ExportMessage
    void writeLongByteOrder(int byteOffset, long value, ByteOrder byteOrder) {
        PythonUtils.byteArraySupport(byteOrder).putLong(values, byteOffset, value);
    }

    @ExportMessage
    float readFloatByteOrder(int byteOffset, ByteOrder byteOrder) {
        return PythonUtils.byteArraySupport(byteOrder).getFloat(values, byteOffset);
    }

    @ExportMessage
    void writeFloatByteOrder(int byteOffset, float value, ByteOrder byteOrder) {
        PythonUtils.byteArraySupport(byteOrder).putFloat(values, byteOffset, value);
    }

    @ExportMessage
    double readDoubleByteOrder(int byteOffset, ByteOrder byteOrder) {
        return PythonUtils.byteArraySupport(byteOrder).getDouble(values, byteOffset);
    }

    @ExportMessage
    void writeDoubleByteOrder(int byteOffset, double value, ByteOrder byteOrder) {
        PythonUtils.byteArraySupport(byteOrder).putDouble(values, byteOffset, value);
    }
}
