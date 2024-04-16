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

import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.nio.ByteOrder;

import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

@ExportLibrary(PythonBufferAccessLibrary.class)
public final class EmptySequenceStorage extends SequenceStorage {

    public static final EmptySequenceStorage INSTANCE = new EmptySequenceStorage();

    @Override
    public SequenceStorage generalizeFor(Object value, SequenceStorage target) {
        final SequenceStorage generalized;

        if (value instanceof Byte) {
            generalized = new ByteSequenceStorage(16);
        } else if (value instanceof Boolean) {
            generalized = new BoolSequenceStorage(16);
        } else if (value instanceof Integer) {
            if (target instanceof ByteSequenceStorage) {
                generalized = new ByteSequenceStorage(16);
            } else {
                generalized = new IntSequenceStorage();
            }
        } else if (value instanceof Long) {
            if (target instanceof ByteSequenceStorage) {
                generalized = new ByteSequenceStorage(16);
            } else {
                generalized = new LongSequenceStorage();
            }
        } else if (value instanceof Double) {
            generalized = new DoubleSequenceStorage();
        } else {
            generalized = new ObjectSequenceStorage(PythonUtils.EMPTY_OBJECT_ARRAY);
        }

        return generalized;
    }

    @Override
    public Object getIndicativeValue() {
        return null;
    }

    @Override
    public SequenceStorage copy() {
        return this;
    }

    @Override
    public Object[] getInternalArray() {
        return PythonUtils.EMPTY_OBJECT_ARRAY;
    }

    @Override
    public Object[] getCopyOfInternalArray() {
        return PythonUtils.EMPTY_OBJECT_ARRAY;
    }

    @Override
    public SequenceStorage getSliceInBound(int start, int stop, int step, int len) {
        assert start == stop && stop == 0;
        return this;
    }

    @Override
    public boolean equals(SequenceStorage other) {
        return other == EmptySequenceStorage.INSTANCE;
    }

    @Override
    public void ensureCapacity(int newCapacity) {

    }

    @Override
    public Object getInternalArrayObject() {
        return null;
    }

    @Override
    public ListStorageType getElementType() {
        return ListStorageType.Empty;
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return "EmptySequenceStorage[]";
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isBuffer() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    int getBufferLength() {
        return 0;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    byte readByte(@SuppressWarnings("unused") int byteOffset) throws IndexOutOfBoundsException {
        throw new IndexOutOfBoundsException("EmptySequenceStorage is always empty!");
    }

    @ExportMessage
    @SuppressWarnings({"static-method", "unused"})
    short readShortByteOrder(int byteOffset, ByteOrder byteOrder) throws IndexOutOfBoundsException {
        throw new IndexOutOfBoundsException("EmptySequenceStorage is always empty!");
    }

    @ExportMessage
    @SuppressWarnings({"static-method", "unused"})
    int readIntByteOrder(int byteOffset, ByteOrder byteOrder) throws IndexOutOfBoundsException {
        throw new IndexOutOfBoundsException("EmptySequenceStorage is always empty!");
    }

    @ExportMessage
    @SuppressWarnings({"static-method", "unused"})
    long readLongByteOrder(int byteOffset, ByteOrder byteOrder) throws IndexOutOfBoundsException {
        throw new IndexOutOfBoundsException("EmptySequenceStorage is always empty!");
    }

    @ExportMessage
    @SuppressWarnings({"static-method", "unused"})
    float readFloatByteOrder(int byteOffset, ByteOrder byteOrder) throws IndexOutOfBoundsException {
        throw new IndexOutOfBoundsException("EmptySequenceStorage is always empty!");
    }

    @ExportMessage
    @SuppressWarnings({"static-method", "unused"})
    double readDoubleByteOrder(int byteOffset, ByteOrder byteOrder) throws IndexOutOfBoundsException {
        throw new IndexOutOfBoundsException("EmptySequenceStorage is always empty!");
    }
}
