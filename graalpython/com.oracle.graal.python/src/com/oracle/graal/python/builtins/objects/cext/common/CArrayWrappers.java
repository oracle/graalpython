/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.common;

import static com.oracle.graal.python.util.PythonUtils.byteArraySupport;

import java.nio.ByteOrder;

import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapper.PythonStructNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonObjectNativeWrapper;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.InvalidBufferOffsetException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.Encoding;

import sun.misc.Unsafe;

/**
 * Native wrappers for managed objects such that they can be used as a C array by native code. The
 * major difference to other native wrappers is that they are copied to native memory if it receives
 * {@code toNative}. This is primarily necessary for C primitive array like {@code char* arr}. The
 * {@code toNative} transformation directly uses {@code Unsafe} to save unnecessary round trips
 * between Python and Sulong.
 */
public abstract class CArrayWrappers {
    public static final Unsafe UNSAFE = PythonUtils.initUnsafe();
    private static final long SIZEOF_INT64 = 8;

    /**
     * Uses {@code Unsafe} to allocate enough off-heap memory for the provided {@code byte[]} and
     * the copies the contents to the native memory.
     */
    @TruffleBoundary
    public static long byteArrayToNativeInt8(byte[] data, boolean writeNullTerminator) {
        int size = data.length * Byte.BYTES;
        long ptr = UNSAFE.allocateMemory(size + (writeNullTerminator ? Byte.BYTES : 0));
        UNSAFE.copyMemory(data, Unsafe.ARRAY_BYTE_BASE_OFFSET, null, ptr, size);
        if (writeNullTerminator) {
            UNSAFE.putByte(ptr + size, (byte) 0);
        }
        return ptr;
    }

    /**
     * Uses {@code Unsafe} to allocate enough off-heap memory for the provided {@code int[]} and the
     * copies the contents to the native memory.
     */
    @TruffleBoundary
    public static long intArrayToNativeInt32(int[] data) {
        int size = data.length * Integer.BYTES;
        long ptr = UNSAFE.allocateMemory(size);
        UNSAFE.copyMemory(data, Unsafe.ARRAY_INT_BASE_OFFSET, null, ptr, size);
        return ptr;
    }

    /**
     * Copies a Java {@code int[]} to a native {@code int64_t *}. For this, the native memory is
     * allocated off-heap using {@code Unsafe}.
     */
    public static long intArrayToNativeInt64(int[] data) {
        long size = data.length * SIZEOF_INT64;
        long ptr = allocateBoundary(size);
        // we need to copy element-wise because the int needs to be converted to a long
        for (int i = 0; i < data.length; i++) {
            UNSAFE.putLong(ptr + i * SIZEOF_INT64, data[i]);
        }
        return ptr;
    }

    /**
     * Encodes the provided TruffleString as UTF-8 bytes and copies the bytes (and an additional NUL
     * char) to a freshly allocated off-heap {@code int8*} (using {@code Unsafe}).
     */
    public static long stringToNativeUtf8Bytes(TruffleString string, TruffleString.SwitchEncodingNode switchEncodingNode, TruffleString.CopyToByteArrayNode copyToByteArrayNode) {
        // TODO GR-37216: use CopyToNative
        TruffleString utf8 = switchEncodingNode.execute(string, Encoding.UTF_8);
        byte[] data = new byte[utf8.byteLength(Encoding.UTF_8)];
        copyToByteArrayNode.execute(utf8, 0, data, 0, data.length, Encoding.UTF_8);
        return byteArrayToNativeInt8(data, true);
    }

    @TruffleBoundary
    private static long allocateBoundary(long size) {
        return UNSAFE.allocateMemory(size);
    }

    @TruffleBoundary
    private static void freeBoundary(long address) {
        UNSAFE.freeMemory(address);
    }

    @ExportLibrary(InteropLibrary.class)
    public abstract static class CArrayWrapper extends PythonStructNativeWrapper {

        public CArrayWrapper(Object delegate) {
            super(delegate);
        }

        @ExportMessage
        boolean isPointer() {
            return isNative();
        }

        @ExportMessage
        long asPointer() {
            return getNativePointer();
        }

        public void free() {
            if (isNative()) {
                freeBoundary(getNativePointer());
            }
        }
    }

    /**
     * Unlike a {@link PythonObjectNativeWrapper} object that wraps a Python unicode object, this
     * wrapper let's a TruffleString look like a {@code char*}.
     */
    @ExportLibrary(InteropLibrary.class)
    public static final class CStringWrapper extends CArrayWrapper {
        private TruffleString.Encoding encoding;

        public CStringWrapper(TruffleString delegate, TruffleString.Encoding encoding) {
            super(delegate);
            this.encoding = encoding;
            assert delegate.isValidUncached(encoding);
        }

        public TruffleString getString() {
            return (TruffleString) getDelegate();
        }

        @ExportMessage
        long getArraySize() {
            return getString().byteLength(encoding) + 1;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        byte readArrayElement(long index,
                        @CachedLibrary("this") InteropLibrary thisLib) throws InvalidArrayIndexException, UnsupportedMessageException {
            try {
                return thisLib.readBufferByte(this, index);
            } catch (InvalidBufferOffsetException e) {
                throw InvalidArrayIndexException.create(index);
            }
        }

        @ExportMessage
        boolean isArrayElementReadable(long index) {
            return 0 <= index && index <= getString().byteLength(encoding);
        }

        @ExportMessage
        void toNative(
                        @Cached TruffleString.SwitchEncodingNode switchEncodingNode,
                        @Cached TruffleString.CopyToByteArrayNode copyToByteArrayNode) {
            if (!PythonContext.get(switchEncodingNode).isNativeAccessAllowed()) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new RuntimeException(ErrorMessages.NATIVE_ACCESS_NOT_ALLOWED.toJavaStringUncached());
            }
            if (!isNative()) {
                setNativePointer(stringToNativeUtf8Bytes(getString(), switchEncodingNode, copyToByteArrayNode));
            }
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasBufferElements() {
            return true;
        }

        @ExportMessage
        long getBufferSize() {
            return getString().byteLength(encoding) + 1;
        }

        @ExportMessage
        byte readBufferByte(long byteOffset,
                        @Cached TruffleString.ReadByteNode readByteNode) throws InvalidBufferOffsetException {
            TruffleString s = getString();
            int len = s.byteLength(encoding);
            if (byteOffset >= 0 && byteOffset < len) {
                return (byte) readByteNode.execute(s, (int) byteOffset, encoding);
            } else if (byteOffset == len) {
                return 0;
            } else {
                throw InvalidBufferOffsetException.create(byteOffset, len);
            }
        }

        @ExportMessage
        short readBufferShort(ByteOrder byteOrder, long byteOffset,
                        @CachedLibrary("this") InteropLibrary thisLib) throws UnsupportedMessageException, InvalidBufferOffsetException {
            byte b1 = thisLib.readBufferByte(this, byteOffset);
            byte b2 = thisLib.readBufferByte(this, byteOffset + 1);
            if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
                return (short) (((b2 & 0xFF) << 8) | (b1 & 0xFF));
            } else {
                return (short) (((b1 & 0xFF) << 8) | (b2 & 0xFF));
            }
        }

        @ExportMessage
        int readBufferInt(ByteOrder byteOrder, long byteOffset,
                        @CachedLibrary("this") InteropLibrary thisLib) throws UnsupportedMessageException, InvalidBufferOffsetException {
            byte b1 = thisLib.readBufferByte(this, byteOffset);
            byte b2 = thisLib.readBufferByte(this, byteOffset + 1);
            byte b3 = thisLib.readBufferByte(this, byteOffset + 2);
            byte b4 = thisLib.readBufferByte(this, byteOffset + 3);
            if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
                return ((b4 & 0xFF) << 8 * 3) | ((b3 & 0xFF) << 8 * 2) | ((b2 & 0xFF) << 8) | ((b1 & 0xFF));
            } else {
                return ((b1 & 0xFF) << 8 * 3) | ((b2 & 0xFF) << 8 * 2) | ((b3 & 0xFF) << 8) | ((b4 & 0xFF));
            }
        }

        @ExportMessage
        long readBufferLong(ByteOrder byteOrder, long byteOffset,
                        @CachedLibrary("this") InteropLibrary thisLib) throws UnsupportedMessageException, InvalidBufferOffsetException {
            byte b1 = thisLib.readBufferByte(this, byteOffset);
            byte b2 = thisLib.readBufferByte(this, byteOffset + 1);
            byte b3 = thisLib.readBufferByte(this, byteOffset + 2);
            byte b4 = thisLib.readBufferByte(this, byteOffset + 3);
            byte b5 = thisLib.readBufferByte(this, byteOffset + 4);
            byte b6 = thisLib.readBufferByte(this, byteOffset + 5);
            byte b7 = thisLib.readBufferByte(this, byteOffset + 6);
            byte b8 = thisLib.readBufferByte(this, byteOffset + 7);
            if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
                return ((b8 & 0xFFL) << (8 * 7)) | ((b7 & 0xFFL) << (8 * 6)) | ((b6 & 0xFFL) << (8 * 5)) | ((b5 & 0xFFL) << (8 * 4)) |
                                ((b4 & 0xFFL) << (8 * 3)) | ((b3 & 0xFFL) << (8 * 2)) | ((b2 & 0xFFL) << 8) | ((b1 & 0xFFL));
            } else {
                return ((b1 & 0xFFL) << (8 * 7)) | ((b2 & 0xFFL) << (8 * 6)) | ((b3 & 0xFFL) << (8 * 5)) | ((b4 & 0xFFL) << (8 * 4)) |
                                ((b5 & 0xFFL) << (8 * 3)) | ((b6 & 0xFFL) << (8 * 2)) | ((b7 & 0xFFL) << 8) | ((b8 & 0xFFL));
            }
        }

        @ExportMessage
        float readBufferFloat(ByteOrder byteOrder, long byteOffset,
                        @CachedLibrary("this") InteropLibrary thisLib) throws UnsupportedMessageException, InvalidBufferOffsetException {
            return Float.intBitsToFloat(thisLib.readBufferInt(this, byteOrder, byteOffset));
        }

        @ExportMessage
        double readBufferDouble(ByteOrder byteOrder, long byteOffset,
                        @CachedLibrary("this") InteropLibrary thisLib) throws UnsupportedMessageException, InvalidBufferOffsetException {
            return Double.longBitsToDouble(thisLib.readBufferLong(this, byteOrder, byteOffset));
        }

        @ExportMessage
        void readBuffer(long byteOffset, byte[] destination, int destinationOffset, int length,
                        @CachedLibrary("this") InteropLibrary thisLib) throws UnsupportedMessageException, InvalidBufferOffsetException {
            if (length < 0) {
                throw InvalidBufferOffsetException.create(byteOffset, length);
            }
            for (int i = 0; i < length; i++) {
                destination[destinationOffset + i] = thisLib.readBufferByte(this, byteOffset + i);
            }
        }
    }

    /**
     * A native wrapper for arbitrary byte arrays (i.e. the store of a Python Bytes object) to be
     * used like a {@code char*} pointer.
     */
    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings("truffle-abstract-export")
    public static final class CByteArrayWrapper extends CArrayWrapper {

        public CByteArrayWrapper(byte[] delegate) {
            super(delegate);
        }

        public byte[] getByteArray() {
            return ((byte[]) getDelegate());
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasBufferElements() {
            return true;
        }

        @ExportMessage
        @ExportMessage(name = "getArraySize")
        long getBufferSize() {
            return getByteArray().length + 1;
        }

        @ExportMessage
        byte readBufferByte(long byteOffset) throws InvalidBufferOffsetException {
            byte[] bytes = getByteArray();
            /*
             * FIXME we only allow reading the NULL byte when reading by bytes, we should also allow
             * that when reading ints etc.
             */
            if (byteOffset == bytes.length) {
                return 0;
            }
            try {
                return bytes[(int) byteOffset];
            } catch (ArrayIndexOutOfBoundsException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw InvalidBufferOffsetException.create(byteOffset, bytes.length);
            }
        }

        @ExportMessage
        short readBufferShort(ByteOrder order, long byteOffset) throws InvalidBufferOffsetException {
            try {
                return byteArraySupport(order).getShort(getByteArray(), byteOffset);
            } catch (IndexOutOfBoundsException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw InvalidBufferOffsetException.create(byteOffset, getByteArray().length);
            }
        }

        @ExportMessage
        int readBufferInt(ByteOrder order, long byteOffset) throws InvalidBufferOffsetException {
            try {
                return byteArraySupport(order).getInt(getByteArray(), byteOffset);
            } catch (IndexOutOfBoundsException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw InvalidBufferOffsetException.create(byteOffset, getByteArray().length);
            }
        }

        @ExportMessage
        long readBufferLong(ByteOrder order, long byteOffset) throws InvalidBufferOffsetException {
            try {
                return byteArraySupport(order).getLong(getByteArray(), byteOffset);
            } catch (IndexOutOfBoundsException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw InvalidBufferOffsetException.create(byteOffset, getByteArray().length);
            }
        }

        @ExportMessage
        float readBufferFloat(ByteOrder order, long byteOffset) throws InvalidBufferOffsetException {
            return Float.intBitsToFloat(readBufferInt(order, byteOffset));
        }

        @ExportMessage
        double readBufferDouble(ByteOrder order, long byteOffset) throws InvalidBufferOffsetException {
            return Double.longBitsToDouble(readBufferLong(order, byteOffset));
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        Object readArrayElement(long index,
                        @Exclusive @Cached GilNode gil) throws InvalidArrayIndexException {
            boolean mustRelease = gil.acquire();
            try {
                try {
                    int idx = PInt.intValueExact(index);
                    byte[] arr = getByteArray();
                    if (idx >= 0 && idx < arr.length) {
                        return arr[idx];
                    } else if (idx == arr.length) {
                        return (byte) 0;
                    }
                } catch (OverflowException e) {
                    // fall through
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw InvalidArrayIndexException.create(index);
            } finally {
                gil.release(mustRelease);
            }
        }

        @ExportMessage
        boolean isArrayElementReadable(long index) {
            return 0 <= index && index < getBufferSize();
        }

        @ExportMessage
        void toNative(
                        @Bind Node node) {
            if (!PythonContext.get(node).isNativeAccessAllowed()) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new RuntimeException(ErrorMessages.NATIVE_ACCESS_NOT_ALLOWED.toJavaStringUncached());
            }
            if (!isNative()) {
                setNativePointer(byteArrayToNativeInt8(getByteArray(), true));
            }
        }
    }

    /**
     * A native wrapper for arbitrary {@code int} arrays to be used like a {@code int *} pointer.
     */
    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings("static-method")
    public static final class CIntArrayWrapper extends CArrayWrapper {

        public CIntArrayWrapper(int[] delegate) {
            super(delegate);
        }

        public int[] getIntArray() {
            return ((int[]) getDelegate());
        }

        @ExportMessage
        long getArraySize() {
            return getIntArray().length;
        }

        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        Object readArrayElement(long index,
                        @Exclusive @Cached GilNode gil) throws InvalidArrayIndexException {
            boolean mustRelease = gil.acquire();
            try {
                int idx = PInt.intValueExact(index);
                int[] arr = getIntArray();
                if (idx >= 0 && idx < arr.length) {
                    return arr[idx];
                }
            } catch (OverflowException e) {
                // fall through
            } finally {
                gil.release(mustRelease);
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw InvalidArrayIndexException.create(index);
        }

        @ExportMessage
        boolean isArrayElementReadable(long identifier) {
            return 0 <= identifier && identifier < getArraySize();
        }

        @ExportMessage
        void toNative(
                        @Bind Node node) {
            if (!PythonContext.get(node).isNativeAccessAllowed()) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new RuntimeException(ErrorMessages.NATIVE_ACCESS_NOT_ALLOWED.toJavaStringUncached());
            }
            if (!isNative()) {
                setNativePointer(intArrayToNativeInt32(getIntArray()));
            }
        }
    }
}
