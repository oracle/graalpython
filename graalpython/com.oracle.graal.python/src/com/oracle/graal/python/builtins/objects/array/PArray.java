/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates.
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
package com.oracle.graal.python.builtins.objects.array;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.BufferError;
import static com.oracle.graal.python.util.BufferFormat.T_UNICODE_TYPE_CODE_U;
import static com.oracle.graal.python.util.BufferFormat.T_UNICODE_TYPE_CODE_W;
import static com.oracle.graal.python.util.PythonUtils.EMPTY_BYTE_ARRAY;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.nio.ByteOrder;

import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.runtime.sequence.storage.BufferStorage;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.util.BufferFormat;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.ExportMessage.Ignore;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.strings.TruffleString;

// TODO interop library
@ExportLibrary(PythonBufferAcquireLibrary.class)
@ExportLibrary(PythonBufferAccessLibrary.class)
public final class PArray extends PythonBuiltinObject {
    private final BufferFormat format;
    private final TruffleString formatString;
    private int length;
    private byte[] bytes;
    private BufferStorage storage;
    private volatile int exports;

    public PArray(Object clazz, Shape instanceShape, TruffleString formatString, BufferFormat format) {
        super(clazz, instanceShape);
        this.formatString = formatString;
        this.format = format;
        this.length = 0;
        this.bytes = EMPTY_BYTE_ARRAY;
        this.storage = new ByteSequenceStorage(bytes);
    }

    public PArray(Object clazz, Shape instanceShape, TruffleString formatString, BufferFormat format, int length) throws OverflowException {
        super(clazz, instanceShape);
        this.formatString = formatString;
        this.format = format;
        this.length = length;
        this.bytes = new byte[PythonUtils.multiplyExact(length, format.bytesize)];
        this.storage = new ByteSequenceStorage(bytes);
    }

    public BufferFormat getFormat() {
        return format;
    }

    @Ignore
    public TruffleString getFormatString() {
        return formatString;
    }

    @ExportMessage(name = "getFormatString")
    public TruffleString getFormatStringForBuffer() {
        if (T_UNICODE_TYPE_CODE_U.equalsUncached(formatString, TS_ENCODING)) {
            return T_UNICODE_TYPE_CODE_W;
        }
        return formatString;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public Object getBuffer() {
        return storage;
    }

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        assert length >= 0;
        this.length = length;
    }

    public int getExports() {
        return exports;
    }

    public void setExports(int exports) {
        this.exports = exports;
    }

    public void checkCanResize(PythonBuiltinBaseNode node) {
        if (exports != 0) {
            throw node.raise(BufferError, ErrorMessages.EXPORTS_CANNOT_RESIZE);
        }
    }

    private int computeNewSize(int newLength, int itemsize) throws OverflowException {
        int newSize = computeNewSizeNoOverflowCheck(newLength, itemsize);
        if (newSize / itemsize < newLength) {
            throw OverflowException.INSTANCE;
        }
        return newSize;
    }

    private int computeNewSizeNoOverflowCheck(int newLength, int itemsize) {
        if (newLength == 0) {
            return 0;
        }
        // Overallocation using the same formula as CPython
        return ((newLength >> 4) + (length < 8 ? 3 : 7) + newLength) * itemsize;
    }

    public void resizeStorage(int newLength) throws OverflowException {
        assert newLength >= 0;
        int itemsize = format.bytesize;
        if (bytes.length / itemsize < newLength || length + 16 >= newLength) {
            byte[] newBuffer = new byte[computeNewSize(newLength, itemsize)];
            PythonUtils.arraycopy(bytes, 0, newBuffer, 0, Math.min(bytes.length, newBuffer.length));
            bytes = newBuffer;
        }
    }

    public void resize(int newLength) throws OverflowException {
        resizeStorage(newLength);
        length = newLength;
    }

    public void shift(int from, int by) throws OverflowException {
        assert from >= 0 && from <= length;
        assert by >= 0;
        int newLength = PythonUtils.addExact(length, by);
        int itemsize = format.bytesize;
        if (bytes.length / itemsize < newLength) {
            byte[] newBuffer = new byte[computeNewSize(newLength, itemsize)];
            PythonUtils.arraycopy(bytes, 0, newBuffer, 0, from * itemsize);
            PythonUtils.arraycopy(bytes, from * itemsize, newBuffer, (from + by) * itemsize, (length - from) * itemsize);
            bytes = newBuffer;
        } else {
            PythonUtils.arraycopy(bytes, from * itemsize, bytes, (from + by) * itemsize, (length - from) * itemsize);
        }
        length = newLength;
    }

    public void delSlice(int at, int count) {
        assert count >= 0;
        assert at + count <= length;
        int newLength = length - count;
        assert newLength >= 0;
        int itemsize = format.bytesize;
        if (length + 16 >= newLength) {
            byte[] newBuffer = new byte[computeNewSizeNoOverflowCheck(newLength, itemsize)];
            PythonUtils.arraycopy(bytes, 0, newBuffer, 0, at * itemsize);
            PythonUtils.arraycopy(bytes, (at + count) * itemsize, newBuffer, at * itemsize, (length - at - count) * itemsize);
            bytes = newBuffer;
        } else {
            PythonUtils.arraycopy(bytes, (at + count) * itemsize, bytes, at * itemsize, (length - at - count) * itemsize);
        }
        length = newLength;
    }

    public enum MachineFormat {
        UNSIGNED_INT8(0, BufferFormat.UINT_8, null),
        SIGNED_INT8(1, BufferFormat.INT_8, null),
        UNSIGNED_INT16_LE(2, BufferFormat.UINT_16, ByteOrder.LITTLE_ENDIAN),
        UNSIGNED_INT16_BE(3, BufferFormat.UINT_16, ByteOrder.BIG_ENDIAN),
        SIGNED_INT16_LE(4, BufferFormat.INT_16, ByteOrder.LITTLE_ENDIAN),
        SIGNED_INT16_BE(5, BufferFormat.INT_16, ByteOrder.BIG_ENDIAN),
        UNSIGNED_INT32_LE(6, BufferFormat.UINT_32, ByteOrder.LITTLE_ENDIAN),
        UNSIGNED_INT32_BE(7, BufferFormat.UINT_32, ByteOrder.BIG_ENDIAN),
        SIGNED_INT32_LE(8, BufferFormat.INT_32, ByteOrder.LITTLE_ENDIAN),
        SIGNED_INT32_BE(9, BufferFormat.INT_32, ByteOrder.BIG_ENDIAN),
        UNSIGNED_INT64_LE(10, BufferFormat.UINT_64, ByteOrder.LITTLE_ENDIAN),
        UNSIGNED_INT64_BE(11, BufferFormat.UINT_64, ByteOrder.BIG_ENDIAN),
        SIGNED_INT64_LE(12, BufferFormat.INT_64, ByteOrder.LITTLE_ENDIAN),
        SIGNED_INT64_BE(13, BufferFormat.INT_64, ByteOrder.BIG_ENDIAN),
        IEEE_754_FLOAT_LE(14, BufferFormat.FLOAT, ByteOrder.LITTLE_ENDIAN),
        IEEE_754_FLOAT_BE(15, BufferFormat.FLOAT, ByteOrder.BIG_ENDIAN),
        IEEE_754_DOUBLE_LE(16, BufferFormat.DOUBLE, ByteOrder.LITTLE_ENDIAN),
        IEEE_754_DOUBLE_BE(17, BufferFormat.DOUBLE, ByteOrder.BIG_ENDIAN),
        UTF32_LE(20, BufferFormat.UNICODE, ByteOrder.LITTLE_ENDIAN, tsLiteral("utf-32-le")),
        UTF32_BE(21, BufferFormat.UNICODE, ByteOrder.BIG_ENDIAN, tsLiteral("utf-32-be")),
        // These two need to come after UTF32, so that forFormat doesn't pick them for UNICODE
        UTF16_LE(18, BufferFormat.UNICODE, ByteOrder.LITTLE_ENDIAN, tsLiteral("utf-16-le")),
        UTF16_BE(19, BufferFormat.UNICODE, ByteOrder.BIG_ENDIAN, tsLiteral("utf-16-be"));

        public final int code;
        public final BufferFormat format;
        public final ByteOrder order;
        public final TruffleString unicodeEncoding;

        MachineFormat(int code, BufferFormat format, ByteOrder order) {
            this(code, format, order, null);
        }

        MachineFormat(int code, BufferFormat format, ByteOrder order, TruffleString unicodeEncoding) {
            this.code = code;
            this.format = format;
            this.order = order;
            this.unicodeEncoding = unicodeEncoding;
        }

        @CompilationFinal(dimensions = 1) private static final MachineFormat[] BY_BUFFER_FORMAT = new MachineFormat[BufferFormat.values().length];
        @CompilationFinal(dimensions = 1) private static final MachineFormat[] BY_CODE = new MachineFormat[values().length];

        static {
            for (var machineFormat : values()) {
                BufferFormat bufferFormat = machineFormat.format;
                if (BY_BUFFER_FORMAT[bufferFormat.ordinal()] == null && (machineFormat.order == null || machineFormat.order == ByteOrder.nativeOrder())) {
                    BY_BUFFER_FORMAT[bufferFormat.ordinal()] = machineFormat;
                }
            }
            for (var machineFormat : values()) {
                assert BY_CODE[machineFormat.code] == null;
                BY_CODE[machineFormat.code] = machineFormat;
            }
        }

        public static MachineFormat forFormat(BufferFormat format) {
            return BY_BUFFER_FORMAT[format.ordinal()];
        }

        public static MachineFormat fromCode(int code) {
            return code >= 0 && code < BY_CODE.length ? BY_CODE[code] : null;
        }
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasBuffer() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isBuffer() {
        return true;
    }

    @ExportMessage
    int getBufferLength() {
        return length * format.bytesize;
    }

    @ExportMessage
    Object acquire(@SuppressWarnings("unused") int flags) {
        return this;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isReadonly() {
        return false;
    }

    @ExportMessage
    int getItemSize() {
        return format.bytesize;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasInternalByteArray() {
        return true;
    }

    @ExportMessage
    byte[] getInternalByteArray() {
        return bytes;
    }

    @ExportMessage
    byte readByte(int byteOffset) {
        return bytes[byteOffset];
    }

    @ExportMessage
    void writeByte(int byteOffset, byte value) {
        bytes[byteOffset] = value;
    }

    @ExportMessage
    short readShort(int byteOffset) {
        return PythonUtils.ARRAY_ACCESSOR.getShort(bytes, byteOffset);
    }

    @ExportMessage
    void writeShort(int byteOffset, short value) {
        PythonUtils.ARRAY_ACCESSOR.putShort(bytes, byteOffset, value);
    }

    @ExportMessage
    int readInt(int byteOffset) {
        return PythonUtils.ARRAY_ACCESSOR.getInt(bytes, byteOffset);
    }

    @ExportMessage
    void writeInt(int byteOffset, int value) {
        PythonUtils.ARRAY_ACCESSOR.putInt(bytes, byteOffset, value);
    }

    @ExportMessage
    long readLong(int byteOffset) {
        return PythonUtils.ARRAY_ACCESSOR.getLong(bytes, byteOffset);
    }

    @ExportMessage
    void writeLong(int byteOffset, long value) {
        PythonUtils.ARRAY_ACCESSOR.putLong(bytes, byteOffset, value);
    }

    @ExportMessage
    float readFloat(int byteOffset) {
        return PythonUtils.ARRAY_ACCESSOR.getFloat(bytes, byteOffset);
    }

    @ExportMessage
    void writeFloat(int byteOffset, float value) {
        PythonUtils.ARRAY_ACCESSOR.putFloat(bytes, byteOffset, value);
    }

    @ExportMessage
    double readDouble(int byteOffset) {
        return PythonUtils.ARRAY_ACCESSOR.getDouble(bytes, byteOffset);
    }

    @ExportMessage
    void writeDouble(int byteOffset, double value) {
        PythonUtils.ARRAY_ACCESSOR.putDouble(bytes, byteOffset, value);
    }
}
