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
import java.util.concurrent.atomic.AtomicLong;

import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.NativeByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.BufferFormat;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.ExportMessage.Ignore;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.strings.TruffleString;

// TODO interop library
@ExportLibrary(PythonBufferAcquireLibrary.class)
@ExportLibrary(PythonBufferAccessLibrary.class)
public final class PArray extends PythonBuiltinObject {
    private final BufferFormat format;
    private final TruffleString formatString;
    private SequenceStorage storage;

    // Count of exports via native buffer interface
    private final AtomicLong exports = new AtomicLong();

    public PArray(Object clazz, Shape instanceShape, TruffleString formatString, BufferFormat format) {
        super(clazz, instanceShape);
        this.formatString = formatString;
        this.format = format;
        this.storage = new ByteSequenceStorage(EMPTY_BYTE_ARRAY);
    }

    public PArray(Object clazz, Shape instanceShape, TruffleString formatString, BufferFormat format, int length) throws OverflowException {
        super(clazz, instanceShape);
        this.formatString = formatString;
        this.format = format;
        this.storage = new ByteSequenceStorage(new byte[PythonUtils.multiplyExact(length, format.bytesize)]);
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

    public Object getBuffer() {
        return storage;
    }

    /*
     * The underlying storage is always bytes regardless of array item type. Don't use nodes for
     * SequenceStorage nodes unless they are agnostic of item type.
     */
    public SequenceStorage getSequenceStorage() {
        return storage;
    }

    public void setSequenceStorage(SequenceStorage storage) {
        assert storage instanceof ByteSequenceStorage || storage instanceof NativeByteSequenceStorage;
        this.storage = storage;
    }

    public int getLength() {
        assert PythonUtils.isDivisible(storage.length(), format.shift);
        return storage.length() >> format.shift;
    }

    public int getBytesLength() {
        return storage.length();
    }

    public AtomicLong getExports() {
        return exports;
    }

    public void checkCanResize(Node inliningTarget, PRaiseNode.Lazy raiseNode) {
        if (exports.get() != 0) {
            throw raiseNode.get(inliningTarget).raise(BufferError, ErrorMessages.EXPORTS_CANNOT_RESIZE);
        }
    }

    public int getItemSizeShift() {
        return format.shift;
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
        return storage.length();
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
    boolean hasInternalByteArray(
                    @Shared @CachedLibrary(limit = "2") PythonBufferAccessLibrary bufferLib) {
        return bufferLib.hasInternalByteArray(storage);
    }

    @ExportMessage
    byte[] getInternalByteArray(
                    @Shared @CachedLibrary(limit = "2") PythonBufferAccessLibrary bufferLib) {
        return bufferLib.getInternalByteArray(storage);
    }

    @ExportMessage
    byte readByte(int byteOffset,
                    @Shared @CachedLibrary(limit = "2") PythonBufferAccessLibrary bufferLib) {
        return bufferLib.readByte(storage, byteOffset);
    }

    @ExportMessage
    void writeByte(int byteOffset, byte value,
                    @Shared @CachedLibrary(limit = "2") PythonBufferAccessLibrary bufferLib) {
        bufferLib.writeByte(storage, byteOffset, value);
    }

    @ExportMessage
    short readShort(int byteOffset,
                    @Shared @CachedLibrary(limit = "2") PythonBufferAccessLibrary bufferLib) {
        return bufferLib.readShort(storage, byteOffset);
    }

    @ExportMessage
    void writeShort(int byteOffset, short value,
                    @Shared @CachedLibrary(limit = "2") PythonBufferAccessLibrary bufferLib) {
        bufferLib.writeShort(storage, byteOffset, value);
    }

    @ExportMessage
    int readInt(int byteOffset,
                    @Shared @CachedLibrary(limit = "2") PythonBufferAccessLibrary bufferLib) {
        return bufferLib.readInt(storage, byteOffset);
    }

    @ExportMessage
    void writeInt(int byteOffset, int value,
                    @Shared @CachedLibrary(limit = "2") PythonBufferAccessLibrary bufferLib) {
        bufferLib.writeInt(storage, byteOffset, value);
    }

    @ExportMessage
    long readLong(int byteOffset,
                    @Shared @CachedLibrary(limit = "2") PythonBufferAccessLibrary bufferLib) {
        return bufferLib.readLong(storage, byteOffset);
    }

    @ExportMessage
    void writeLong(int byteOffset, long value,
                    @Shared @CachedLibrary(limit = "2") PythonBufferAccessLibrary bufferLib) {
        bufferLib.writeLong(storage, byteOffset, value);
    }

    @ExportMessage
    float readFloat(int byteOffset,
                    @Shared @CachedLibrary(limit = "2") PythonBufferAccessLibrary bufferLib) {
        return bufferLib.readFloat(storage, byteOffset);
    }

    @ExportMessage
    void writeFloat(int byteOffset, float value,
                    @Shared @CachedLibrary(limit = "2") PythonBufferAccessLibrary bufferLib) {
        bufferLib.writeFloat(storage, byteOffset, value);
    }

    @ExportMessage
    double readDouble(int byteOffset,
                    @Shared @CachedLibrary(limit = "2") PythonBufferAccessLibrary bufferLib) {
        return bufferLib.readDouble(storage, byteOffset);
    }

    @ExportMessage
    void writeDouble(int byteOffset, double value,
                    @Shared @CachedLibrary(limit = "2") PythonBufferAccessLibrary bufferLib) {
        bufferLib.writeDouble(storage, byteOffset, value);
    }
}
