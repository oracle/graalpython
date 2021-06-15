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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.GenerateLibrary.Abstract;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.library.LibraryFactory;

@GenerateLibrary
public abstract class PythonBufferAccessLibrary extends Library {
    @Abstract
    public boolean isBuffer(@SuppressWarnings("unused") Object receiver) {
        return false;
    }

    public abstract int getBufferLength(Object receiver);

    @Abstract(ifExported = "writeByte")
    public boolean isWritable(@SuppressWarnings("unused") Object receiver) {
        return false;
    }

    @Abstract(ifExported = "getItemSize")
    public String getFormatString(@SuppressWarnings("unused") Object receiver) {
        return "B";
    }

    @Abstract(ifExported = "getFormatString")
    public int getItemSize(@SuppressWarnings("unused") Object receiver) {
        return 1;
    }

    public boolean hasInternalByteArray(@SuppressWarnings("unused") Object receiver) {
        return false;
    }

    @Abstract(ifExported = "hasInternalByteArray")
    public byte[] getInternalByteArray(@SuppressWarnings("unused") Object receiver) {
        throw CompilerDirectives.shouldNotReachHere("getInternalByteArray");
    }

    public abstract void copyFrom(Object receiver, int srcOffset, byte[] dest, int destOffset, int length);

    public abstract void copyTo(Object receiver, int destOffset, byte[] src, int srcOffset, int length);

    public final byte[] getCopiedByteArray(Object receiver) {
        int len = getBufferLength(receiver);
        byte[] bytes = new byte[len];
        copyFrom(receiver, 0, bytes, 0, len);
        return bytes;
    }

    public final byte[] getInternalOrCopiedByteArray(Object receiver) {
        if (hasInternalByteArray(receiver)) {
            return getInternalByteArray(receiver);
        } else {
            return getCopiedByteArray(receiver);
        }
    }

    public abstract byte readByte(Object receiver, int byteOffset);

    public short readShort(Object receiver, int byteOffset) {
        byte b1 = readByte(receiver, byteOffset);
        byte b2 = readByte(receiver, byteOffset + 1);
        return (short) (((b1 & 0xFF) << Byte.SIZE) | (b2 & 0xFF));
    }

    public int readInt(Object receiver, int byteOffset) {
        byte b1 = readByte(receiver, byteOffset);
        byte b2 = readByte(receiver, byteOffset + 1);
        byte b3 = readByte(receiver, byteOffset + 2);
        byte b4 = readByte(receiver, byteOffset + 3);
        return ((b1 & 0xFF) << Byte.SIZE * 3) | ((b2 & 0xFF) << Byte.SIZE * 2) | ((b3 & 0xFF) << Byte.SIZE) | ((b4 & 0xFF));
    }

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

    public float readFloat(Object receiver, int byteOffset) {
        return Float.intBitsToFloat(readInt(receiver, byteOffset));
    }

    public double readDouble(Object receiver, int byteOffset) {
        return Double.longBitsToDouble(readLong(receiver, byteOffset));
    }

    @Abstract(ifExported = "isWritable")
    @SuppressWarnings("unused")
    public void writeByte(Object receiver, int byteOffset, byte value) {
        throw CompilerDirectives.shouldNotReachHere("writeByte not implemented");
    }

    public void writeShort(Object receiver, int byteOffset, short value) {
        writeByte(receiver, byteOffset, (byte) (value >> Byte.SIZE));
        writeByte(receiver, byteOffset + 1, (byte) (value));
    }

    public void writeInt(Object receiver, int byteOffset, int value) {
        writeByte(receiver, byteOffset, (byte) (value >> Byte.SIZE * 3));
        writeByte(receiver, byteOffset + 1, (byte) (value >> Byte.SIZE * 2));
        writeByte(receiver, byteOffset + 2, (byte) (value >> Byte.SIZE));
        writeByte(receiver, byteOffset + 3, (byte) (value));
    }

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

    public void writeFloat(Object receiver, int byteOffset, float value) {
        writeInt(receiver, byteOffset, Float.floatToIntBits(value));
    }

    public void writeDouble(Object receiver, int byteOffset, double value) {
        writeLong(receiver, byteOffset, Double.doubleToLongBits(value));
    }

    public void release(@SuppressWarnings("unused") Object receiver) {
    }

    static final LibraryFactory<PythonBufferAccessLibrary> FACTORY = LibraryFactory.resolve(PythonBufferAccessLibrary.class);

    public static LibraryFactory<PythonBufferAccessLibrary> getFactory() {
        return FACTORY;
    }

    public static PythonBufferAccessLibrary getUncached() {
        return FACTORY.getUncached();
    }
}
