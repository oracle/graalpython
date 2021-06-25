/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.buffer;

import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.GenerateLibrary.Abstract;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.library.LibraryFactory;

/**
 * A library for accessing buffers obtained using {@link PythonBufferAcquireLibrary}. The buffer
 * should always be released using {@link #release} after the access is finished. It is also
 * permissible to use this library to access the underlying memory of {@link PBytesLike} objects
 * that implement the API directly (i.e. as an equivalent of {@code PyBytes_AsStringAndSize}) - in
 * that case, the buffer doesn't need to be acquired or released.
 * 
 * As in CPython, the caller is responsible for keeping consistency of the underlying memory -
 * implementations are not expected to perform bounds check or check for writability before write
 * operations.
 * 
 * The messages can be categorized into several groups:
 * <ul>
 * <li>Accessing the underlying byte array - {@link #hasInternalByteArray},
 * {@link #getInternalByteArray}), {@link #getInternalOrCopiedByteArray}
 * <li>Doing bulk operations on the contents - {@link #getCopiedByteArray},
 * {@link #readIntoByteArray}, {@link #writeFromByteArray}, {@link #readIntoBuffer}
 * <li>Doing operations on individual elements interpreted as Java primitives - {@link #readByte},
 * {@link #readInt}... {@link #writeByte}, {@link #writeInt}...
 * <li>Querying the buffer metadata - {@link #getBufferLength}, {@link #isReadonly}...
 * </ul>
 */
@GenerateLibrary
public abstract class PythonBufferAccessLibrary extends Library {
    /**
     * Whether this object responds to this buffer API. Marked {@code protected} because the code
     * using buffers must know whether it works with a buffer object or not. Buffers should be
     * obtained using {@link PythonBufferAcquireLibrary}. Use {@link #assertIsBuffer(Object)} for
     * assertions.
     */
    @Abstract
    protected boolean isBuffer(@SuppressWarnings("unused") Object receiver) {
        return false;
    }

    /**
     * Asserts that given object implements this buffer API.
     */
    public static void assertIsBuffer(Object receiver) {
        assert PythonBufferAccessLibrary.getUncached().isBuffer(receiver);
    }

    /**
     * Release the buffer. Equivalent of CPython's {@code PyBuffer_Release}, but must not be called
     * multiple times on the same buffer.
     */
    public void release(@SuppressWarnings("unused") Object receiver) {
    }

    /**
     * Return the buffer length in bytes. Equivalent of CPython's {@code Py_buffer.len}.
     */
    public abstract int getBufferLength(Object receiver);

    /**
     * Returns whether the buffer is considered readonly. Equivalent of CPython's
     * {@code Py_buffer.readonly}.
     */
    @Abstract(ifExported = "writeByte")
    public boolean isReadonly(@SuppressWarnings("unused") Object receiver) {
        return true;
    }

    /**
     * Return whether the object is backed by a Java {@code byte[]} array that can be directly
     * accessed. When it has, the backing array can be accessed using
     * {@link #getInternalByteArray(Object)}.
     */
    public boolean hasInternalByteArray(@SuppressWarnings("unused") Object receiver) {
        return false;
    }

    /**
     * Access the internal {@code byte[]} array of the object. Must call
     * {@link #hasInternalByteArray(Object)} before calling this method (failure to do so results in
     * an {@link AssertionError}). The caller must take into account that the byte array size is
     * different from the buffer size - it is necessary to obtain the buffer size using
     * {@link #getBufferLength(Object)}. If the object is not readonly ({@link #isReadonly(Object)}
     * returns {@code false}), the byte array can be directly written and the changes will affect
     * the object.
     */
    @Abstract(ifExported = "hasInternalByteArray")
    public byte[] getInternalByteArray(@SuppressWarnings("unused") Object receiver) {
        throw CompilerDirectives.shouldNotReachHere("getInternalByteArray");
    }

    /**
     * Read bytes from this buffer into a given byte array. Bounds checks are responsibility of the
     * caller.
     *
     * @param receiver this buffer (source)
     * @param srcOffset offset in this buffer in bytes
     * @param dest destination byte array
     * @param destOffset offset in the destination array in bytes
     * @param length length of the copied segment in bytes
     */
    public void readIntoByteArray(Object receiver, int srcOffset, byte[] dest, int destOffset, int length) {
        if (hasInternalByteArray(receiver)) {
            PythonUtils.arraycopy(getInternalByteArray(receiver), srcOffset, dest, destOffset, length);
        } else {
            for (int i = 0; i < length; i++) {
                dest[destOffset + i] = readByte(receiver, srcOffset + i);
            }
        }
    }

    /**
     * Write bytes into this buffer from given byte array. Bounds checks are responsibility of the
     * caller.
     *
     * @param receiver this buffer (destination)
     * @param destOffset the offset in this buffer in bytes
     * @param src the source byte array
     * @param srcOffset offset in the sources array in bytes
     * @param length length of the copied segment in bytes
     */
    public void writeFromByteArray(Object receiver, int destOffset, byte[] src, int srcOffset, int length) {
        if (hasInternalByteArray(receiver)) {
            PythonUtils.arraycopy(src, srcOffset, getInternalByteArray(receiver), destOffset, length);
        } else {
            for (int i = 0; i < length; i++) {
                writeByte(receiver, destOffset + i, src[srcOffset + i]);
            }
        }
    }

    /**
     * Read data from this buffer and write to another buffer. Bounds checks are responsibility of
     * the caller.
     * 
     * @param receiver this buffer (source)
     * @param srcOffset the offset in this buffer in bytes
     * @param dest other buffer (destination)
     * @param destOffset the offset in the destination buffer in bytes
     * @param length length of the copied segment in bytes
     * @param otherLib the library used to access the other buffer
     */
    public void readIntoBuffer(Object receiver, int srcOffset, Object dest, int destOffset, int length, PythonBufferAccessLibrary otherLib) {
        if (hasInternalByteArray(receiver) && otherLib.hasInternalByteArray(dest)) {
            PythonUtils.arraycopy(getInternalByteArray(receiver), srcOffset, otherLib.getInternalByteArray(dest), destOffset, length);
        } else {
            for (int i = 0; i < length; i++) {
                otherLib.writeByte(dest, destOffset + i, readByte(receiver, srcOffset + i));
            }
        }
    }

    /**
     * Get a copy of the buffer contents as a byte array. The copy is guaranteed to have the same
     * length as the logical length of the buffer.
     */
    public final byte[] getCopiedByteArray(Object receiver) {
        int len = getBufferLength(receiver);
        byte[] bytes = new byte[len];
        readIntoByteArray(receiver, 0, bytes, 0, len);
        return bytes;
    }

    /**
     * Get a byte array representing the buffer contents. Avoid copying it if possible. The caller
     * must take into account that the byte array size is different from the buffer size - it is
     * necessary to obtain the buffer size using {@link #getBufferLength(Object)}. Do not write into
     * the byte array.
     */
    public final byte[] getInternalOrCopiedByteArray(Object receiver) {
        if (hasInternalByteArray(receiver)) {
            return getInternalByteArray(receiver);
        } else {
            return getCopiedByteArray(receiver);
        }
    }

    /**
     * Read a single byte from the buffer. Bounds checks are responsibility of the caller.
     * 
     * @param byteOffset offset in bytes
     */
    public abstract byte readByte(Object receiver, int byteOffset);

    /**
     * Read a single short from the buffer. Bounds checks are responsibility of the caller.
     * 
     * @param byteOffset offset in bytes
     */
    public short readShort(Object receiver, int byteOffset) {
        byte b1 = readByte(receiver, byteOffset);
        byte b2 = readByte(receiver, byteOffset + 1);
        return (short) (((b1 & 0xFF) << Byte.SIZE) | (b2 & 0xFF));
    }

    /**
     * Read a single int from the buffer. Bounds checks are responsibility of the caller.
     * 
     * @param byteOffset offset in bytes
     */
    public int readInt(Object receiver, int byteOffset) {
        byte b1 = readByte(receiver, byteOffset);
        byte b2 = readByte(receiver, byteOffset + 1);
        byte b3 = readByte(receiver, byteOffset + 2);
        byte b4 = readByte(receiver, byteOffset + 3);
        return ((b1 & 0xFF) << Byte.SIZE * 3) | ((b2 & 0xFF) << Byte.SIZE * 2) | ((b3 & 0xFF) << Byte.SIZE) | ((b4 & 0xFF));
    }

    /**
     * Read a single long from the buffer. Bounds checks are responsibility of the caller.
     * 
     * @param byteOffset offset in bytes
     */
    public long readLong(Object receiver, int byteOffset) {
        byte b1 = readByte(receiver, byteOffset);
        byte b2 = readByte(receiver, byteOffset + 1);
        byte b3 = readByte(receiver, byteOffset + 2);
        byte b4 = readByte(receiver, byteOffset + 3);
        byte b5 = readByte(receiver, byteOffset + 4);
        byte b6 = readByte(receiver, byteOffset + 5);
        byte b7 = readByte(receiver, byteOffset + 6);
        byte b8 = readByte(receiver, byteOffset + 7);
        return ((b1 & 0xFFL) << (Byte.SIZE * 7)) | ((b2 & 0xFFL) << (Byte.SIZE * 6)) | ((b3 & 0xFFL) << (Byte.SIZE * 5)) | ((b4 & 0xFFL) << (Byte.SIZE * 4)) |
                        ((b5 & 0xFFL) << (Byte.SIZE * 3)) | ((b6 & 0xFFL) << (Byte.SIZE * 2)) | ((b7 & 0xFFL) << (Byte.SIZE)) | ((b8 & 0xFFL));
    }

    /**
     * Read a single float from the buffer. Bounds checks are responsibility of the caller.
     * 
     * @param byteOffset offset in bytes
     */
    public float readFloat(Object receiver, int byteOffset) {
        return Float.intBitsToFloat(readInt(receiver, byteOffset));
    }

    /**
     * Read a single double from the buffer. Bounds checks are responsibility of the caller.
     * 
     * @param byteOffset offset in bytes
     */
    public double readDouble(Object receiver, int byteOffset) {
        return Double.longBitsToDouble(readLong(receiver, byteOffset));
    }

    /**
     * Write a single byte to the buffer. Bounds checks are responsibility of the caller.
     * 
     * @param byteOffset offset in bytes
     */
    @Abstract(ifExported = "isReadonly")
    @SuppressWarnings("unused")
    public void writeByte(Object receiver, int byteOffset, byte value) {
        throw CompilerDirectives.shouldNotReachHere("writeByte not implemented");
    }

    /**
     * Write a single short to the buffer. Bounds checks are responsibility of the caller.
     * 
     * @param byteOffset offset in bytes
     */
    public void writeShort(Object receiver, int byteOffset, short value) {
        writeByte(receiver, byteOffset, (byte) (value >> Byte.SIZE));
        writeByte(receiver, byteOffset + 1, (byte) (value));
    }

    /**
     * Write a single int to the buffer. Bounds checks are responsibility of the caller.
     * 
     * @param byteOffset offset in bytes
     */
    public void writeInt(Object receiver, int byteOffset, int value) {
        writeByte(receiver, byteOffset, (byte) (value >> Byte.SIZE * 3));
        writeByte(receiver, byteOffset + 1, (byte) (value >> Byte.SIZE * 2));
        writeByte(receiver, byteOffset + 2, (byte) (value >> Byte.SIZE));
        writeByte(receiver, byteOffset + 3, (byte) (value));
    }

    /**
     * Write a single long to the buffer. Bounds checks are responsibility of the caller.
     * 
     * @param byteOffset offset in bytes
     */
    public void writeLong(Object receiver, int byteOffset, long value) {
        writeByte(receiver, byteOffset, (byte) (value >> (Byte.SIZE * 7)));
        writeByte(receiver, byteOffset + 1, (byte) (value >> (Byte.SIZE * 6)));
        writeByte(receiver, byteOffset + 2, (byte) (value >> (Byte.SIZE * 5)));
        writeByte(receiver, byteOffset + 3, (byte) (value >> (Byte.SIZE * 4)));
        writeByte(receiver, byteOffset + 4, (byte) (value >> (Byte.SIZE * 3)));
        writeByte(receiver, byteOffset + 5, (byte) (value >> (Byte.SIZE * 2)));
        writeByte(receiver, byteOffset + 6, (byte) (value >> (Byte.SIZE)));
        writeByte(receiver, byteOffset + 7, (byte) (value));
    }

    /**
     * Write a single float to the buffer. Bounds checks are responsibility of the caller.
     * 
     * @param byteOffset offset in bytes
     */
    public void writeFloat(Object receiver, int byteOffset, float value) {
        writeInt(receiver, byteOffset, Float.floatToIntBits(value));
    }

    /**
     * Write a single double to the buffer. Bounds checks are responsibility of the caller.
     * 
     * @param byteOffset offset in bytes
     */
    public void writeDouble(Object receiver, int byteOffset, double value) {
        writeLong(receiver, byteOffset, Double.doubleToLongBits(value));
    }

    /**
     * Returns the owner object of the buffer. Equivalent of CPython's {@code Py_buffer.obj}. May
     * return {@code null} for native buffers created over raw memory.
     */
    public Object getOwner(Object receiver) {
        return receiver;
    }

    /**
     * Get the byte size of an item for buffers that have typed items. Equivalent of CPython's
     * {@code Py_buffer.format}.
     */
    @Abstract(ifExported = "getFormatString")
    public int getItemSize(@SuppressWarnings("unused") Object receiver) {
        return 1;
    }

    /**
     * Get the format specifier in struct module syntax. Equivalent of CPython's
     * {@code Py_buffer.format}.
     */
    @Abstract(ifExported = "getItemSize")
    public String getFormatString(@SuppressWarnings("unused") Object receiver) {
        return "B";
    }

    static final LibraryFactory<PythonBufferAccessLibrary> FACTORY = LibraryFactory.resolve(PythonBufferAccessLibrary.class);

    public static LibraryFactory<PythonBufferAccessLibrary> getFactory() {
        return FACTORY;
    }

    public static PythonBufferAccessLibrary getUncached() {
        return FACTORY.getUncached();
    }
}
