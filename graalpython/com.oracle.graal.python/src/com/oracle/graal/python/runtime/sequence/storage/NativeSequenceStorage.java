/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeStorageReference;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

@ExportLibrary(PythonBufferAccessLibrary.class)
public final class NativeSequenceStorage extends SequenceStorage {

    /* native pointer object */
    private Object ptr;

    private final ListStorageType elementType;

    private NativeStorageReference reference;

    private NativeSequenceStorage(Object ptr, int length, int capacity, ListStorageType elementType) {
        super(length, capacity);
        this.ptr = ptr;
        this.elementType = elementType;
    }

    /**
     * @param ownsMemory whether the memory should be freed when this object dies. Should be true
     *            when actually used as a sequence storage
     */
    public static NativeSequenceStorage create(Object ptr, int length, int capacity, ListStorageType elementType, boolean ownsMemory) {
        NativeSequenceStorage storage = new NativeSequenceStorage(ptr, length, capacity, elementType);
        if (ownsMemory) {
            CApiTransitions.registerNativeSequenceStorage(storage);
        }
        return storage;
    }

    public Object getPtr() {
        return ptr;
    }

    public void setPtr(Object ptr) {
        if (reference != null) {
            reference.setPtr(ptr);
        }
        this.ptr = ptr;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public void setReference(NativeStorageReference reference) {
        this.reference = reference;
    }

    @Override
    public ListStorageType getElementType() {
        return elementType;
    }

    @Override
    public void setNewLength(int length) {
        assert length <= capacity;
        this.length = length;
        if (reference != null) {
            reference.setSize(length);
        }
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return String.format("<NativeSequenceStorage(type=%s, len=%d, cap=%d) at %s>", elementType, length, capacity, ptr);
    }

    @Override
    public void ensureCapacity(int newCapacity) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    public SequenceStorage copy() {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    public SequenceStorage createEmpty(int newCapacity) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    public Object[] getInternalArray() {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    public Object[] getCopyOfInternalArray() {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    public Object getItemNormalized(int idx) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    public void setItemNormalized(int idx, Object value) throws SequenceStoreException {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    public void insertItem(int idx, Object value) throws SequenceStoreException {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    public SequenceStorage getSliceInBound(int start, int stop, int step, int len) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    public void reverse() {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    public boolean equals(SequenceStorage other) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    public SequenceStorage generalizeFor(Object value, SequenceStorage other) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    public Object getIndicativeValue() {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    public void copyItem(int idxTo, int idxFrom) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    public Object getInternalArrayObject() {
        return ptr;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isBuffer() {
        return elementType == ListStorageType.Byte;
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
    byte readByte(int byteOffset,
                    @Shared("interopLib") @CachedLibrary(limit = "1") InteropLibrary interopLib) {
        try {
            return (byte) interopLib.readArrayElement(ptr, byteOffset);
        } catch (InvalidArrayIndexException | UnsupportedMessageException e) {
            throw CompilerDirectives.shouldNotReachHere("native storage read failed");
        }
    }

    @ExportMessage
    void writeByte(int byteOffset, byte value,
                    @Shared("interopLib") @CachedLibrary(limit = "1") InteropLibrary interopLib) {
        try {
            interopLib.writeArrayElement(ptr, byteOffset, value);
        } catch (InvalidArrayIndexException | UnsupportedMessageException | UnsupportedTypeException e) {
            throw CompilerDirectives.shouldNotReachHere("native storage write failed");
        }
    }
}
