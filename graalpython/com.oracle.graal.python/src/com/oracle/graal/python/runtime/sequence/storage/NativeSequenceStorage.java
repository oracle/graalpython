/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.ByteOrder;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.InvalidBufferOffsetException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

@ExportLibrary(BufferStorageLibrary.class)
public class NativeSequenceStorage extends SequenceStorage {

    /* native pointer object */
    private Object ptr;

    /* length of contents */
    protected int len;

    /* allocated capacity */
    protected int capacity;

    protected final ListStorageType elementType;

    public NativeSequenceStorage(Object ptr, int length, int capacity, ListStorageType elementType) {
        this.ptr = ptr;
        this.capacity = capacity;
        this.len = length;
        this.elementType = elementType;
    }

    public Object getPtr() {
        return ptr;
    }

    public void setPtr(Object ptr) {
        this.ptr = ptr;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    @Override
    public ListStorageType getElementType() {
        return elementType;
    }

    @Override
    public final int length() {
        return len;
    }

    @Override
    public void setNewLength(int length) {
        assert length <= capacity;
        this.len = length;
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return String.format("<NativeSequenceStorage(type=%s, len=%d, cap=%d) at %s>", elementType, len, capacity, ptr);
    }

    @Override
    public void ensureCapacity(@SuppressWarnings("unused") int newCapacity) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new IllegalStateException("should not reach");
    }

    @Override
    public SequenceStorage copy() {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new IllegalStateException("should not reach");
    }

    @Override
    public SequenceStorage createEmpty(int newCapacity) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new IllegalStateException("should not reach");
    }

    @Override
    public Object[] getInternalArray() {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new IllegalStateException("should not reach");
    }

    @Override
    public Object[] getCopyOfInternalArray() {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new IllegalStateException("should not reach");
    }

    @Override
    public Object getItemNormalized(int idx) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new IllegalStateException("should not reach");
    }

    @Override
    public void setItemNormalized(int idx, Object value) throws SequenceStoreException {
        CompilerAsserts.neverPartOfCompilation();
        throw new IllegalStateException("should not reach");
    }

    @Override
    public void insertItem(int idx, Object value) throws SequenceStoreException {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new IllegalStateException("should not reach");
    }

    @Override
    public SequenceStorage getSliceInBound(int start, int stop, int step, int length) {
        CompilerAsserts.neverPartOfCompilation();
        throw new IllegalStateException("should not reach");
    }

    @Override
    public void reverse() {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new IllegalStateException("should not reach");
    }

    @Override
    public boolean equals(SequenceStorage other) {
        CompilerAsserts.neverPartOfCompilation();
        throw new IllegalStateException("should not reach");
    }

    @Override
    public SequenceStorage generalizeFor(Object value, SequenceStorage other) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new IllegalStateException("should not reach");
    }

    @Override
    public Object getIndicativeValue() {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new IllegalStateException("should not reach");
    }

    @Override
    public void copyItem(int idxTo, int idxFrom) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new IllegalStateException("should not reach");
    }

    @Override
    public Object getInternalArrayObject() {
        return ptr;
    }

    @ExportMessage
    int getBufferLength() {
        return len;
    }

    @ExportMessage
    void copyFrom(int srcOffset, byte[] dest, int destOffset, int length,
                    @Shared("interopLib") @CachedLibrary(limit = "1") InteropLibrary interopLib) {
        assert elementType == ListStorageType.Byte;
        try {
            for (int i = 0; i < length; i++) {
                dest[destOffset + i] = (byte) interopLib.readArrayElement(ptr, srcOffset + i);
            }
        } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
            throw CompilerDirectives.shouldNotReachHere("native storage read failed");
        }
    }

    @ExportMessage
    void copyTo(int destOffset, byte[] src, int srcOffset, int length,
                    @Shared("interopLib") @CachedLibrary(limit = "1") InteropLibrary interopLib) {
        assert elementType == ListStorageType.Byte;
        try {
            for (int i = 0; i < length; i++) {
                interopLib.writeArrayElement(ptr, destOffset + i, src[srcOffset + i]);
            }
        } catch (UnsupportedMessageException | InvalidArrayIndexException | UnsupportedTypeException e) {
            throw CompilerDirectives.shouldNotReachHere("native storage write failed");
        }
    }

    private void checkOffsetForInterop(long byteOffset, int elementLen) throws InvalidBufferOffsetException, UnsupportedMessageException {
        if (elementType != ListStorageType.Byte) {
            throw UnsupportedMessageException.create();
        }
        if (byteOffset < 0 || byteOffset + elementLen - 1 >= len) {
            throw InvalidBufferOffsetException.create(byteOffset, len);
        }
    }

    public static byte readByteFromNative(Object ptr, long byteOffset, InteropLibrary interopLib) throws UnsupportedMessageException {
        try {
            return (byte) interopLib.readArrayElement(ptr, byteOffset);
        } catch (InvalidArrayIndexException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }

    public static void writeByteToNative(Object ptr, long byteOffset, byte value, InteropLibrary interopLib) throws UnsupportedMessageException {
        try {
            interopLib.writeArrayElement(ptr, byteOffset, value);
        } catch (InvalidArrayIndexException | UnsupportedTypeException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }

    public static short readShortFromNative(Object ptr, ByteOrder order, long byteOffset, InteropLibrary interopLib) throws UnsupportedMessageException {
        try {
            byte b1 = (byte) interopLib.readArrayElement(ptr, byteOffset);
            byte b2 = (byte) interopLib.readArrayElement(ptr, byteOffset + 1);
            short res = (short) (((b1 & 0xFF) << Byte.SIZE) | (b2 & 0xFF));
            if (order != ByteOrder.nativeOrder()) {
                res = Short.reverseBytes(res);
            }
            return res;
        } catch (InvalidArrayIndexException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }

    public static void writeShortToNative(Object ptr, ByteOrder order, long byteOffset, short valueIn, InteropLibrary interopLib) throws UnsupportedMessageException {
        short value = valueIn;
        if (order != ByteOrder.nativeOrder()) {
            value = Short.reverseBytes(valueIn);
        }
        try {
            interopLib.writeArrayElement(ptr, byteOffset, (byte) (value >> Byte.SIZE));
            interopLib.writeArrayElement(ptr, byteOffset + 1, (byte) (value));
        } catch (InvalidArrayIndexException | UnsupportedTypeException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }

    public static int readIntFromNative(Object ptr, ByteOrder order, long byteOffset, InteropLibrary interopLib) throws UnsupportedMessageException {
        try {
            byte b1 = (byte) interopLib.readArrayElement(ptr, byteOffset);
            byte b2 = (byte) interopLib.readArrayElement(ptr, byteOffset + 1);
            byte b3 = (byte) interopLib.readArrayElement(ptr, byteOffset + 2);
            byte b4 = (byte) interopLib.readArrayElement(ptr, byteOffset + 3);
            int res = ((b1 & 0xFF) << Byte.SIZE * 3) | ((b2 & 0xFF) << Byte.SIZE * 2) | ((b3 & 0xFF) << Byte.SIZE) | ((b4 & 0xFF));
            if (order != ByteOrder.nativeOrder()) {
                res = Integer.reverseBytes(res);
            }
            return res;
        } catch (InvalidArrayIndexException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }

    public static void writeIntToNative(Object ptr, ByteOrder order, long byteOffset, int valueIn, InteropLibrary interopLib) throws UnsupportedMessageException {
        int value = valueIn;
        if (order != ByteOrder.nativeOrder()) {
            value = Integer.reverseBytes(valueIn);
        }
        try {
            interopLib.writeArrayElement(ptr, byteOffset, (byte) (value >> Byte.SIZE * 3));
            interopLib.writeArrayElement(ptr, byteOffset + 1, (byte) (value >> Byte.SIZE * 2));
            interopLib.writeArrayElement(ptr, byteOffset + 2, (byte) (value >> Byte.SIZE));
            interopLib.writeArrayElement(ptr, byteOffset + 3, (byte) (value));
        } catch (InvalidArrayIndexException | UnsupportedTypeException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }

    public static long readLongFromNative(Object ptr, ByteOrder order, long byteOffset, InteropLibrary interopLib) throws UnsupportedMessageException {
        try {
            byte b1 = (byte) interopLib.readArrayElement(ptr, byteOffset);
            byte b2 = (byte) interopLib.readArrayElement(ptr, byteOffset + 1);
            byte b3 = (byte) interopLib.readArrayElement(ptr, byteOffset + 2);
            byte b4 = (byte) interopLib.readArrayElement(ptr, byteOffset + 3);
            byte b5 = (byte) interopLib.readArrayElement(ptr, byteOffset + 4);
            byte b6 = (byte) interopLib.readArrayElement(ptr, byteOffset + 5);
            byte b7 = (byte) interopLib.readArrayElement(ptr, byteOffset + 6);
            byte b8 = (byte) interopLib.readArrayElement(ptr, byteOffset + 7);
            long res = ((b1 & 0xFFL) << (Byte.SIZE * 7)) | ((b2 & 0xFFL) << (Byte.SIZE * 6)) | ((b3 & 0xFFL) << (Byte.SIZE * 5)) | ((b4 & 0xFFL) << (Byte.SIZE * 4)) |
                            ((b5 & 0xFFL) << (Byte.SIZE * 3)) | ((b6 & 0xFFL) << (Byte.SIZE * 2)) | ((b7 & 0xFFL) << (Byte.SIZE)) | ((b8 & 0xFFL));
            if (order != ByteOrder.nativeOrder()) {
                res = Long.reverseBytes(res);
            }
            return res;
        } catch (InvalidArrayIndexException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }

    public static void writeLongToNative(Object ptr, ByteOrder order, long byteOffset, long valueIn, InteropLibrary interopLib) throws UnsupportedMessageException {
        long value = valueIn;
        if (order != ByteOrder.nativeOrder()) {
            value = Long.reverseBytes(valueIn);
        }
        try {
            interopLib.writeArrayElement(ptr, byteOffset, (byte) (value >> (Byte.SIZE * 7)));
            interopLib.writeArrayElement(ptr, byteOffset + 1, (byte) (value >> (Byte.SIZE * 6)));
            interopLib.writeArrayElement(ptr, byteOffset + 2, (byte) (value >> (Byte.SIZE * 5)));
            interopLib.writeArrayElement(ptr, byteOffset + 3, (byte) (value >> (Byte.SIZE * 4)));
            interopLib.writeArrayElement(ptr, byteOffset + 4, (byte) (value >> (Byte.SIZE * 3)));
            interopLib.writeArrayElement(ptr, byteOffset + 5, (byte) (value >> (Byte.SIZE * 2)));
            interopLib.writeArrayElement(ptr, byteOffset + 6, (byte) (value >> (Byte.SIZE)));
            interopLib.writeArrayElement(ptr, byteOffset + 7, (byte) (value));
        } catch (InvalidArrayIndexException | UnsupportedTypeException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }

    @ExportMessage
    byte readBufferByte(long byteOffset,
                    @Shared("interopLib") @CachedLibrary(limit = "1") InteropLibrary interopLib) throws InvalidBufferOffsetException, UnsupportedMessageException {
        checkOffsetForInterop(byteOffset, 1);
        return readByteFromNative(ptr, byteOffset, interopLib);
    }

    @ExportMessage
    void writeBufferByte(long byteOffset, byte value,
                    @Shared("interopLib") @CachedLibrary(limit = "1") InteropLibrary interopLib) throws InvalidBufferOffsetException, UnsupportedMessageException {
        checkOffsetForInterop(byteOffset, 1);
        writeByteToNative(ptr, byteOffset, value, interopLib);
    }

    @ExportMessage
    short readBufferShort(ByteOrder order, long byteOffset,
                    @Shared("interopLib") @CachedLibrary(limit = "1") InteropLibrary interopLib) throws InvalidBufferOffsetException, UnsupportedMessageException {
        checkOffsetForInterop(byteOffset, 2);
        return readShortFromNative(ptr, order, byteOffset, interopLib);
    }

    @ExportMessage
    void writeBufferShort(ByteOrder order, long byteOffset, short value,
                    @Shared("interopLib") @CachedLibrary(limit = "1") InteropLibrary interopLib) throws InvalidBufferOffsetException, UnsupportedMessageException {
        checkOffsetForInterop(byteOffset, 2);
        writeShortToNative(ptr, order, byteOffset, value, interopLib);
    }

    @ExportMessage
    int readBufferInt(ByteOrder order, long byteOffset,
                    @Shared("interopLib") @CachedLibrary(limit = "1") InteropLibrary interopLib) throws InvalidBufferOffsetException, UnsupportedMessageException {
        checkOffsetForInterop(byteOffset, 4);
        return readIntFromNative(ptr, order, byteOffset, interopLib);
    }

    @ExportMessage
    void writeBufferInt(ByteOrder order, long byteOffset, int value,
                    @Shared("interopLib") @CachedLibrary(limit = "1") InteropLibrary interopLib) throws InvalidBufferOffsetException, UnsupportedMessageException {
        checkOffsetForInterop(byteOffset, 4);
        writeIntToNative(ptr, order, byteOffset, value, interopLib);
    }

    @ExportMessage
    long readBufferLong(ByteOrder order, long byteOffset,
                    @Shared("interopLib") @CachedLibrary(limit = "1") InteropLibrary interopLib) throws InvalidBufferOffsetException, UnsupportedMessageException {
        checkOffsetForInterop(byteOffset, 8);
        return readLongFromNative(ptr, order, byteOffset, interopLib);
    }

    @ExportMessage
    void writeBufferLong(ByteOrder order, long byteOffset, long value,
                    @Shared("interopLib") @CachedLibrary(limit = "1") InteropLibrary interopLib) throws InvalidBufferOffsetException, UnsupportedMessageException {
        checkOffsetForInterop(byteOffset, 8);
        writeLongToNative(ptr, order, byteOffset, value, interopLib);
    }

    @ExportMessage
    float readBufferFloat(ByteOrder order, long byteOffset,
                    @Shared("interopLib") @CachedLibrary(limit = "1") InteropLibrary interopLib) throws InvalidBufferOffsetException, UnsupportedMessageException {
        return Float.intBitsToFloat(readBufferInt(order, byteOffset, interopLib));
    }

    @ExportMessage
    void writeBufferFloat(ByteOrder order, long byteOffset, float value,
                    @Shared("interopLib") @CachedLibrary(limit = "1") InteropLibrary interopLib) throws InvalidBufferOffsetException, UnsupportedMessageException {
        writeBufferInt(order, byteOffset, Float.floatToRawIntBits(value), interopLib);
    }

    @ExportMessage
    double readBufferDouble(ByteOrder order, long byteOffset,
                    @Shared("interopLib") @CachedLibrary(limit = "1") InteropLibrary interopLib) throws InvalidBufferOffsetException, UnsupportedMessageException {
        return Double.longBitsToDouble(readBufferLong(order, byteOffset, interopLib));
    }

    @ExportMessage
    void writeBufferDouble(ByteOrder order, long byteOffset, double value,
                    @Shared("interopLib") @CachedLibrary(limit = "1") InteropLibrary interopLib) throws InvalidBufferOffsetException, UnsupportedMessageException {
        writeBufferLong(order, byteOffset, Double.doubleToRawLongBits(value), interopLib);
    }
}
