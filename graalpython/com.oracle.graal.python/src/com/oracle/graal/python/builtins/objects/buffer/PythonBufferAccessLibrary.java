/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.util.BufferFormat.T_UINT_8_TYPE_CODE;

import java.nio.ByteOrder;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.nodes.IndirectCallNode;
import com.oracle.graal.python.nodes.PNodeWithIndirectCall;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.GenerateLibrary.Abstract;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.library.LibraryFactory;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

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
     * multiple times on the same buffer. If the caller has access to a VirtualFrame
     * {@link #release(Object, VirtualFrame, PNodeWithIndirectCall)} or
     * {@link #release(Object, VirtualFrame, PythonContext, PythonLanguage, IndirectCallNode)}
     * should be used. If the caller doesn't have access to a VirtualFrame it must be ensured that
     * an IndirectCallContext was already created in the call path.
     */
    public void release(@SuppressWarnings("unused") Object receiver) {
    }

    /**
     * Release the buffer. Equivalent of CPython's {@code PyBuffer_Release}, but must not be called
     * multiple times on the same buffer.
     */
    public final <T extends Node & IndirectCallNode> void release(Object receiver, VirtualFrame frame, T callNode) {
        Object savedState = IndirectCallContext.enter(frame, callNode);
        try {
            release(receiver);
        } finally {
            IndirectCallContext.exit(frame, callNode, savedState);
        }
    }

    /**
     * Release the buffer. Equivalent of CPython's {@code PyBuffer_Release}, but must not be called
     * multiple times on the same buffer.
     */
    public final void release(Object receiver, VirtualFrame frame, PythonContext context, PythonLanguage language, IndirectCallNode node) {
        Object savedState = IndirectCallContext.enter(frame, language, context, node);
        try {
            release(receiver);
        } finally {
            IndirectCallContext.exit(frame, language, context, savedState);
        }
    }

    /**
     * Return the buffer length in bytes. Equivalent of CPython's {@code Py_buffer.len}.
     */

    @Abstract
    @SuppressWarnings("unused")
    public int getBufferLength(Object receiver) {
        throw CompilerDirectives.shouldNotReachHere("getBufferLength");
    }

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
     * Get a {@code byte[]} copy of a range of the buffer contents.
     */
    public final byte[] getCopyOfRange(Object receiver, int from, int to) {
        int len = to - from;
        assert len >= 0;
        byte[] newBuf = new byte[len];
        readIntoByteArray(receiver, from, newBuf, 0, len);
        return newBuf;
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
    @Abstract
    @SuppressWarnings("unused")
    public byte readByte(Object receiver, int byteOffset) {
        throw CompilerDirectives.shouldNotReachHere("readByte");
    }

    /**
     * Read a single short from the buffer. Bounds checks are responsibility of the caller.
     *
     * @param byteOffset offset in bytes
     */
    public short readShort(Object receiver, int byteOffset) {
        byte b1 = readByte(receiver, byteOffset);
        byte b2 = readByte(receiver, byteOffset + 1);
        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            return (short) (((b2 & 0xFF) << 8) | (b1 & 0xFF));
        } else {
            return (short) (((b1 & 0xFF) << 8) | (b2 & 0xFF));
        }
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
        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            return ((b4 & 0xFF) << 8 * 3) | ((b3 & 0xFF) << 8 * 2) | ((b2 & 0xFF) << 8) | ((b1 & 0xFF));
        } else {
            return ((b1 & 0xFF) << 8 * 3) | ((b2 & 0xFF) << 8 * 2) | ((b3 & 0xFF) << 8) | ((b4 & 0xFF));
        }
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
        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            return ((b8 & 0xFFL) << (8 * 7)) | ((b7 & 0xFFL) << (8 * 6)) | ((b6 & 0xFFL) << (8 * 5)) | ((b5 & 0xFFL) << (8 * 4)) |
                            ((b4 & 0xFFL) << (8 * 3)) | ((b3 & 0xFFL) << (8 * 2)) | ((b2 & 0xFFL) << 8) | ((b1 & 0xFFL));
        } else {
            return ((b1 & 0xFFL) << (8 * 7)) | ((b2 & 0xFFL) << (8 * 6)) | ((b3 & 0xFFL) << (8 * 5)) | ((b4 & 0xFFL) << (8 * 4)) |
                            ((b5 & 0xFFL) << (8 * 3)) | ((b6 & 0xFFL) << (8 * 2)) | ((b7 & 0xFFL) << 8) | ((b8 & 0xFFL));
        }
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
        byte b1 = (byte) (value >> 8);
        byte b2 = (byte) value;
        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            writeByte(receiver, byteOffset, b2);
            writeByte(receiver, byteOffset + 1, b1);
        } else {
            writeByte(receiver, byteOffset, b1);
            writeByte(receiver, byteOffset + 1, b2);
        }
    }

    /**
     * Write a single int to the buffer. Bounds checks are responsibility of the caller.
     *
     * @param byteOffset offset in bytes
     */
    public void writeInt(Object receiver, int byteOffset, int value) {
        byte b1 = (byte) (value >> 8 * 3);
        byte b2 = (byte) (value >> 8 * 2);
        byte b3 = (byte) (value >> 8);
        byte b4 = (byte) value;
        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            writeByte(receiver, byteOffset, b4);
            writeByte(receiver, byteOffset + 1, b3);
            writeByte(receiver, byteOffset + 2, b2);
            writeByte(receiver, byteOffset + 3, b1);
        } else {
            writeByte(receiver, byteOffset, b1);
            writeByte(receiver, byteOffset + 1, b2);
            writeByte(receiver, byteOffset + 2, b3);
            writeByte(receiver, byteOffset + 3, b4);
        }
    }

    /**
     * Write a single long to the buffer. Bounds checks are responsibility of the caller.
     *
     * @param byteOffset offset in bytes
     */
    public void writeLong(Object receiver, int byteOffset, long value) {
        byte b1 = (byte) (value >> (8 * 7));
        byte b2 = (byte) (value >> (8 * 6));
        byte b3 = (byte) (value >> (8 * 5));
        byte b4 = (byte) (value >> (8 * 4));
        byte b5 = (byte) (value >> (8 * 3));
        byte b6 = (byte) (value >> (8 * 2));
        byte b7 = (byte) (value >> 8);
        byte b8 = (byte) value;
        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            writeByte(receiver, byteOffset, b8);
            writeByte(receiver, byteOffset + 1, b7);
            writeByte(receiver, byteOffset + 2, b6);
            writeByte(receiver, byteOffset + 3, b5);
            writeByte(receiver, byteOffset + 4, b4);
            writeByte(receiver, byteOffset + 5, b3);
            writeByte(receiver, byteOffset + 6, b2);
            writeByte(receiver, byteOffset + 7, b1);
        } else {
            writeByte(receiver, byteOffset, b1);
            writeByte(receiver, byteOffset + 1, b2);
            writeByte(receiver, byteOffset + 2, b3);
            writeByte(receiver, byteOffset + 3, b4);
            writeByte(receiver, byteOffset + 4, b5);
            writeByte(receiver, byteOffset + 5, b6);
            writeByte(receiver, byteOffset + 6, b7);
            writeByte(receiver, byteOffset + 7, b8);
        }
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
    public TruffleString getFormatString(@SuppressWarnings("unused") Object receiver) {
        return T_UINT_8_TYPE_CODE;
    }

    static final LibraryFactory<PythonBufferAccessLibrary> FACTORY = LibraryFactory.resolve(PythonBufferAccessLibrary.class);

    public static LibraryFactory<PythonBufferAccessLibrary> getFactory() {
        return FACTORY;
    }

    public static PythonBufferAccessLibrary getUncached() {
        return FACTORY.getUncached();
    }
}
