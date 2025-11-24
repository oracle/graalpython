/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nfi2;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

import com.oracle.truffle.api.CompilerDirectives;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import sun.misc.Unsafe;

public final class NativeMemory {

    public static final long NULLPTR = 0L;
    public static final long POINTER_SIZE = Long.BYTES;

    static final Unsafe UNSAFE = initUnsafe();

    private NativeMemory() {
    }

    @TruffleBoundary
    public static long malloc(long size) {
        assert size > 0;
        return UNSAFE.allocateMemory(size);
    }

    @TruffleBoundary
    public static long calloc(long size) {
        long ptr = malloc(size);
        memset(ptr, (byte) 0, size);
        return ptr;
    }

    public static void free(long ptr) {
        UNSAFE.freeMemory(ptr);
    }

    public static void memcpy(long dst, long src, long size) {
        UNSAFE.copyMemory(null, src, null, dst, size);
    }

    public static void memset(long dst, byte value, long count) {
        UNSAFE.setMemory(dst, count, value);
    }

    public static long mallocByteArray(long count) {
        assert count > 0;
        return malloc(count);
    }

    public static long callocByteArray(long count) {
        assert count > 0;
        return calloc(count);
    }

    public static long mallocShortArray(long count) {
        assert count > 0;
        assert canMultiplyWithoutOverflow(count, Short.BYTES);
        return malloc(count * Short.BYTES);
    }

    public static long callocShortArray(long count) {
        assert count > 0;
        assert canMultiplyWithoutOverflow(count, Short.BYTES);
        return calloc(count * Short.BYTES);
    }

    public static long mallocIntArray(long count) {
        assert count > 0;
        assert canMultiplyWithoutOverflow(count, Integer.BYTES);
        return malloc(count * Integer.BYTES);
    }

    public static long callocIntArray(long count) {
        assert count > 0;
        assert canMultiplyWithoutOverflow(count, Integer.BYTES);
        return calloc(count * Integer.BYTES);
    }

    public static long mallocLongArray(long count) {
        assert count > 0;
        assert canMultiplyWithoutOverflow(count, Long.BYTES);
        return malloc(count * Long.BYTES);
    }

    public static long callocLongArray(long count) {
        assert count > 0;
        assert canMultiplyWithoutOverflow(count, Long.BYTES);
        return calloc(count * Long.BYTES);
    }

    public static long mallocPtrArray(long count) {
        assert count > 0;
        assert canMultiplyWithoutOverflow(count, (int) POINTER_SIZE);
        return malloc(count * POINTER_SIZE);
    }

    public static long callocPtrArray(long count) {
        assert count > 0;
        assert canMultiplyWithoutOverflow(count, (int) POINTER_SIZE);
        return calloc(count * POINTER_SIZE);
    }

    public static long getFieldPtr(long basePtr, long offset) {
        assert basePtr >= 0 && offset >= 0 && basePtr + offset >= 0;
        return basePtr + offset;
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////

    public static byte readByte(long pointer) {
        return UNSAFE.getByte(pointer);
    }

    public static void writeByte(long pointer, byte value) {
        UNSAFE.putByte(pointer, value);
    }

    public static byte readByteArrayElement(long arrayPtr, long index) {
        return readByte(arrayPtr + index);
    }

    public static void writeByteArrayElement(long arrayPtr, long index, byte value) {
        writeByte(arrayPtr + index, value);
    }

    public static byte[] readByteArrayElements(long arrayPtr, long srcIndex, int count) {
        byte[] result = new byte[count];
        readByteArrayElements(arrayPtr, srcIndex, result, 0, count);
        return result;
    }

    public static void readByteArrayElements(long arrayPtr, long srcIndex, byte[] dst, int dstIndex, int count) {
        UNSAFE.copyMemory(null, arrayPtr + srcIndex, dst, Unsafe.ARRAY_BYTE_BASE_OFFSET + (long) dstIndex, count);
    }

    public static void writeByteArrayElements(long arrayPtr, long dstIndex, byte[] src, int offset, int count) {
        UNSAFE.copyMemory(src, Unsafe.ARRAY_BYTE_BASE_OFFSET + (long) offset, null, arrayPtr + dstIndex, count);
    }

    public static void copyByteArray(long dstArray, long dstIndex, long srcArray, long srcIndex, long count) {
        memcpy(dstArray + dstIndex, srcArray + srcIndex, count);
    }

    public static short readShort(long pointer) {
        return UNSAFE.getShort(pointer);
    }

    public static void writeShort(long pointer, short value) {
        UNSAFE.putShort(pointer, value);
    }

    public static short readShortArrayElement(long arrayPtr, long index) {
        assert canMultiplyWithoutOverflow(index, Short.BYTES);
        return readShort(arrayPtr + index * Short.BYTES);
    }

    public static void writeShortArrayElement(long arrayPtr, long index, short value) {
        assert canMultiplyWithoutOverflow(index, Short.BYTES);
        writeShort(arrayPtr + index * Short.BYTES, value);
    }

    public static int readInt(long pointer) {
        return UNSAFE.getInt(pointer);
    }

    public static void writeInt(long pointer, int value) {
        UNSAFE.putInt(pointer, value);
    }

    public static int readIntArrayElement(long arrayPtr, long index) {
        assert canMultiplyWithoutOverflow(index, Integer.BYTES);
        return readInt(arrayPtr + index * Integer.BYTES);
    }

    public static void writeIntArrayElement(long arrayPtr, long index, int value) {
        assert canMultiplyWithoutOverflow(index, Integer.BYTES);
        writeInt(arrayPtr + index * Integer.BYTES, value);
    }

    public static long readLong(long pointer) {
        return UNSAFE.getLong(pointer);
    }

    public static void writeLong(long pointer, long value) {
        UNSAFE.putLong(pointer, value);
    }

    public static long readLongArrayElement(long arrayPtr, long index) {
        assert canMultiplyWithoutOverflow(index, Long.BYTES);
        return readLong(arrayPtr + index * Long.BYTES);
    }

    public static void writeLongArrayElement(long arrayPtr, long index, long value) {
        assert canMultiplyWithoutOverflow(index, Long.BYTES);
        writeLong(arrayPtr + index * Long.BYTES, value);
    }

    public static long[] readLongArrayElements(long arrayPtr, long srcIndex, int count) {
        long[] result = new long[count];
        readLongArrayElements(arrayPtr, srcIndex, result, 0, count);
        return result;
    }

    public static void readLongArrayElements(long arrayPtr, long srcIndex, long[] dst, int dstIndex, int count) {
        assert canMultiplyWithoutOverflow(srcIndex, Long.BYTES);
        UNSAFE.copyMemory(null, arrayPtr + srcIndex * Long.BYTES, dst, Unsafe.ARRAY_LONG_BASE_OFFSET + (long) dstIndex * Long.BYTES, (long) count * Long.BYTES);
    }

    public static long readPtr(long pointer) {
        return UNSAFE.getLong(pointer);
    }

    public static void writePtr(long pointer, long value) {
        UNSAFE.putLong(pointer, value);
    }

    public static long readPtrArrayElement(long arrayPtr, long index) {
        assert canMultiplyWithoutOverflow(index, (int) POINTER_SIZE);
        return readPtr(arrayPtr + index * POINTER_SIZE);
    }

    public static void writePtrArrayElement(long arrayPtr, long index, long value) {
        assert canMultiplyWithoutOverflow(index, (int) POINTER_SIZE);
        writePtr(arrayPtr + index * POINTER_SIZE, value);
    }

    public static void writePtrArrayElements(long arrayPtr, long dstIndex, long[] src, int offset, int count) {
        assert canMultiplyWithoutOverflow(dstIndex, (int) POINTER_SIZE);
        UNSAFE.copyMemory(src, Unsafe.ARRAY_LONG_BASE_OFFSET + (long) offset * POINTER_SIZE, null, arrayPtr + dstIndex * POINTER_SIZE, (long) count * POINTER_SIZE);
    }

    public static void copyPtrArray(long dstArray, long dstIndex, long srcArray, long srcIndex, long count) {
        assert canMultiplyWithoutOverflow(dstIndex, (int) POINTER_SIZE);
        assert canMultiplyWithoutOverflow(srcIndex, (int) POINTER_SIZE);
        assert canMultiplyWithoutOverflow(count, (int) POINTER_SIZE);
        memcpy(dstArray + dstIndex * POINTER_SIZE, srcArray + srcIndex * POINTER_SIZE, count * POINTER_SIZE);
    }

    public static float readFloat(long pointer) {
        return UNSAFE.getFloat(pointer);
    }

    public static void writeFloat(long pointer, float value) {
        UNSAFE.putFloat(pointer, value);
    }

    public static double readDouble(long pointer) {
        return UNSAFE.getDouble(pointer);
    }

    public static void writeDouble(long pointer, double value) {
        UNSAFE.putDouble(pointer, value);
    }

    static long javaStringToNativeUtf8(String s) {
        byte[] utf8 = s.getBytes(StandardCharsets.UTF_8);
        long ptr = NativeMemory.malloc(utf8.length + 1);
        UNSAFE.copyMemory(utf8, UNSAFE.arrayBaseOffset(byte[].class), null, ptr, utf8.length);
        UNSAFE.putByte(ptr + utf8.length, (byte) 0);
        return ptr;
    }

    private static boolean canMultiplyWithoutOverflow(long value, int stride) {
        assert value >= 0 : "Value must be non-negative";
        assert stride > 0 && (stride & (stride - 1)) == 0 : "Stride must be a power of two";
        int bitsNeeded = Integer.numberOfTrailingZeros(stride);
        return (value >>> (63 - bitsNeeded)) == 0;
    }

    private static Unsafe initUnsafe() {
        try {
            // Fast path when we are trusted.
            return Unsafe.getUnsafe();
        } catch (SecurityException se) {
            // Slow path when we are not trusted.
            try {
                Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
                theUnsafe.setAccessible(true);
                return (Unsafe) theUnsafe.get(Unsafe.class);
            } catch (Exception e) {
                throw CompilerDirectives.shouldNotReachHere("exception while trying to get Unsafe", e);
            }
        }
    }
}
