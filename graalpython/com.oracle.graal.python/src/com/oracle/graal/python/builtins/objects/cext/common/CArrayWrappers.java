/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_GET_BYTE_ARRAY_TYPE_ID;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext.LLVMType;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.GetLLVMType;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.IsPointerNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.capi.DynamicObjectNativeWrapper.PythonObjectNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.InvalidateNativeObjectsAllManagedNode;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapperLibrary;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;

import sun.misc.Unsafe;

/**
 * Native wrappers for managed objects such that they can be used as a C array by native code. The
 * major difference to other native wrappers is that they are copied to native memory if it receives
 * {@code toNative}. This is primarily necessary for C primitive array like {@code char* arr}. The
 * {@code toNative} transformation directly uses {@code Unsafe} to save unnecessary round trips
 * between Python and Sulong.
 */
public abstract class CArrayWrappers {
    public static final Unsafe UNSAFE = getUnsafe();
    private static final long SIZEOF_INT64 = 8;

    private static Unsafe getUnsafe() {
        CompilerAsserts.neverPartOfCompilation();
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            return (Unsafe) theUnsafe.get(null);
        } catch (Exception e) {
            throw new AssertionError();
        }
    }

    /**
     * Uses {@code Unsafe} to allocate enough off-heap memory for the provided {@code byte[]} and
     * the copies the contents to the native memory.
     */
    @TruffleBoundary
    public static long byteArrayToNativeInt8(byte[] data, boolean writeNullTerminator) {
        int size = (data.length + (writeNullTerminator ? 1 : 0)) * Byte.BYTES;
        long ptr = UNSAFE.allocateMemory(size);
        UNSAFE.copyMemory(data, Unsafe.ARRAY_BYTE_BASE_OFFSET, null, ptr, size);
        if (writeNullTerminator) {
            UNSAFE.putByte(ptr + data.length * Byte.BYTES, (byte) 0);
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
     * Encodes the provided String as UTF-8 bytes and copies the bytes (and an additional NUL char)
     * to a freshly allocated off-heap {@code int8*} (using {@code Unsafe}).
     */
    @TruffleBoundary
    public static long stringToNativeUtf8Bytes(String string) {
        ByteBuffer encoded = StandardCharsets.UTF_8.encode(string);
        byte[] data = new byte[encoded.remaining() + 1];
        encoded.get(data, 0, data.length - 1);
        return byteArrayToNativeInt8(data, true);
    }

    @TruffleBoundary
    private static long allocateBoundary(long size) {
        return UNSAFE.allocateMemory(size);
    }

    @ExportLibrary(InteropLibrary.class)
    public abstract static class CArrayWrapper extends PythonNativeWrapper {

        public CArrayWrapper(Object delegate) {
            super(delegate);
        }

        @ExportMessage
        boolean isPointer(
                        @Exclusive @Cached IsPointerNode pIsPointerNode) {
            return pIsPointerNode.execute(this);
        }

        @ExportMessage
        long asPointer(
                        @CachedLibrary("this") PythonNativeWrapperLibrary lib,
                        @CachedLibrary(limit = "1") InteropLibrary interopLibrary) throws UnsupportedMessageException {
            Object nativePointer = lib.getNativePointer(this);
            if (nativePointer instanceof Long) {
                return (long) nativePointer;
            }
            return interopLibrary.asPointer(nativePointer);
        }
    }

    /**
     * Unlike a {@link PythonObjectNativeWrapper} object that wraps a Python unicode object, this
     * wrapper let's a Java String look like a {@code char*}.
     */
    @ExportLibrary(InteropLibrary.class)
    @ExportLibrary(value = NativeTypeLibrary.class, useForAOT = false)
    public static final class CStringWrapper extends CArrayWrapper {

        public CStringWrapper(String delegate) {
            super(delegate);
        }

        public String getString(PythonNativeWrapperLibrary lib) {
            return ((String) lib.getDelegate(this));
        }

        @ExportMessage
        long getArraySize(
                        @CachedLibrary("this") PythonNativeWrapperLibrary lib) {
            return ((String) lib.getDelegate(this)).length();
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        byte readArrayElement(long index,
                        @CachedLibrary("this") PythonNativeWrapperLibrary lib) throws InvalidArrayIndexException {
            try {
                int idx = PInt.intValueExact(index);
                String s = (String) lib.getDelegate(this);
                if (idx >= 0 && idx < s.length()) {
                    return (byte) s.charAt(idx);
                } else if (idx == s.length()) {
                    return 0;
                }
            } catch (OverflowException e) {
                // fall through
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw InvalidArrayIndexException.create(index);
        }

        @ExportMessage
        boolean isArrayElementReadable(long identifier,
                        @CachedLibrary("this") PythonNativeWrapperLibrary lib) {
            return 0 <= identifier && identifier < getArraySize(lib);
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasNativeType() {
            return true;
        }

        @ExportMessage
        Object getNativeType(
                        @CachedLibrary("this") PythonNativeWrapperLibrary lib,
                        @Exclusive @Cached PCallCapiFunction callByteArrayTypeIdNode) {
            return callByteArrayTypeIdNode.call(FUN_GET_BYTE_ARRAY_TYPE_ID, ((String) lib.getDelegate(this)).length());
        }

        @ExportMessage
        void toNative(
                        @CachedLibrary("this") PythonNativeWrapperLibrary lib,
                        @Exclusive @Cached InvalidateNativeObjectsAllManagedNode invalidateNode) {
            if (!PythonContext.get(lib).isNativeAccessAllowed()) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new RuntimeException(ErrorMessages.NATIVE_ACCESS_NOT_ALLOWED);
            }
            invalidateNode.execute();
            if (!lib.isNative(this)) {
                setNativePointer(stringToNativeUtf8Bytes(getString(lib)));
            }
        }
    }

    /**
     * A native wrapper for arbitrary byte arrays (i.e. the store of a Python Bytes object) to be
     * used like a {@code char*} pointer.
     */
    @ExportLibrary(InteropLibrary.class)
    @ExportLibrary(value = NativeTypeLibrary.class, useForAOT = false)
    public static final class CByteArrayWrapper extends CArrayWrapper {

        public CByteArrayWrapper(byte[] delegate) {
            super(delegate);
        }

        public byte[] getByteArray(PythonNativeWrapperLibrary lib) {
            return ((byte[]) lib.getDelegate(this));
        }

        @ExportMessage
        long getArraySize(@CachedLibrary("this") PythonNativeWrapperLibrary lib) {
            return getByteArray(lib).length;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        Object readArrayElement(long index,
                        @CachedLibrary("this") PythonNativeWrapperLibrary lib,
                        @Exclusive @Cached GilNode gil) throws InvalidArrayIndexException {
            boolean mustRelease = gil.acquire();
            try {
                try {
                    int idx = PInt.intValueExact(index);
                    byte[] arr = getByteArray(lib);
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
        boolean isArrayElementReadable(long identifier,
                        @CachedLibrary("this") PythonNativeWrapperLibrary lib) {
            return 0 <= identifier && identifier < getArraySize(lib);
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasNativeType() {
            return true;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        Object getNativeType(
                        @Cached GetLLVMType getLLVMType) {
            return getLLVMType.execute(LLVMType.int8_ptr_t);
        }

        @ExportMessage
        void toNative(
                        @CachedLibrary("this") PythonNativeWrapperLibrary lib,
                        @Exclusive @Cached InvalidateNativeObjectsAllManagedNode invalidateNode) {
            if (!PythonContext.get(lib).isNativeAccessAllowed()) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new RuntimeException(ErrorMessages.NATIVE_ACCESS_NOT_ALLOWED);
            }
            invalidateNode.execute();
            if (!lib.isNative(this)) {
                setNativePointer(byteArrayToNativeInt8(getByteArray(lib), true));
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

        public int[] getIntArray(PythonNativeWrapperLibrary lib) {
            return ((int[]) lib.getDelegate(this));
        }

        @ExportMessage
        long getArraySize(@CachedLibrary("this") PythonNativeWrapperLibrary lib) {
            return getIntArray(lib).length;
        }

        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        Object readArrayElement(long index,
                        @CachedLibrary("this") PythonNativeWrapperLibrary lib,
                        @Exclusive @Cached GilNode gil) throws InvalidArrayIndexException {
            boolean mustRelease = gil.acquire();
            try {
                int idx = PInt.intValueExact(index);
                int[] arr = getIntArray(lib);
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
        boolean isArrayElementReadable(long identifier,
                        @CachedLibrary("this") PythonNativeWrapperLibrary lib) {
            return 0 <= identifier && identifier < getArraySize(lib);
        }

        @ExportMessage
        void toNative(
                        @CachedLibrary("this") PythonNativeWrapperLibrary lib,
                        @Exclusive @Cached InvalidateNativeObjectsAllManagedNode invalidateNode) {
            if (!PythonContext.get(lib).isNativeAccessAllowed()) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new RuntimeException(ErrorMessages.NATIVE_ACCESS_NOT_ALLOWED);
            }
            invalidateNode.execute();
            if (!lib.isNative(this)) {
                int[] data = getIntArray(lib);
                setNativePointer(intArrayToNativeInt32(data));
            }
        }
    }
}
