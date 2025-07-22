/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates.
 * Copyright (c) 2014, Regents of the University of California
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
package com.oracle.graal.python.builtins.objects.bytes;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.BufferError;
import static com.oracle.graal.python.util.PythonUtils.builtinClassToType;

import java.nio.ByteOrder;

import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.common.IndexNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.interop.PForeignToPTypeNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.Shape;

@SuppressWarnings("truffle-abstract-export")
@ExportLibrary(InteropLibrary.class)
@ExportLibrary(PythonBufferAccessLibrary.class)
public final class PByteArray extends PBytesLike {

    private volatile long exports;

    public PByteArray(Object cls, Shape instanceShape, byte[] bytes) {
        super(builtinClassToType(cls), instanceShape, bytes);
    }

    public PByteArray(Object cls, Shape instanceShape, SequenceStorage store) {
        super(builtinClassToType(cls), instanceShape, store);
    }

    @Override
    @TruffleBoundary
    public String toString() {
        return formatByteArray("bytearray");
    }

    @TruffleBoundary
    public String formatByteArray(String typeName) {
        if (getSequenceStorage() instanceof ByteSequenceStorage byteSequenceStorage) {
            return String.format("%s(%s)", typeName, BytesUtils.bytesRepr(byteSequenceStorage.getInternalByteArray(), byteSequenceStorage.length()));
        } else {
            return String.format("%s(%s)", typeName, getSequenceStorage());
        }
    }

    public long getExports() {
        return exports;
    }

    public void setExports(long exports) {
        this.exports = exports;
    }

    public void checkCanResize(Node inliningTarget, PRaiseNode raiseNode) {
        if (exports != 0) {
            throw raiseNode.raise(inliningTarget, BufferError, ErrorMessages.EXPORTS_CANNOT_RESIZE);
        }
    }

    @ExportMessage
    public boolean isArrayElementModifiable(long index,
                    @Exclusive @Cached IndexNodes.NormalizeIndexCustomMessageNode normalize,
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            final int len = store.length();
            try {
                normalize.execute(index, len, ErrorMessages.INDEX_OUT_OF_RANGE);
            } catch (PException e) {
                return false;
            }
            return true;
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    public boolean isArrayElementInsertable(long index,
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            final int len = store.length();
            return index == len;
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    public boolean isArrayElementRemovable(long index,
                    @Exclusive @Cached IndexNodes.NormalizeIndexCustomMessageNode normalize,
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            final int len = store.length();
            try {
                normalize.execute(index, len, ErrorMessages.INDEX_OUT_OF_RANGE);
            } catch (PException e) {
                return false;
            }
            return true;
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    public void writeArrayElement(long index, Object value,
                    @Bind Node inliningTarget,
                    @Exclusive @Cached SequenceStorageNodes.SetItemScalarNode setItem,
                    @Exclusive @Cached PForeignToPTypeNode convert,
                    @Exclusive @Cached GilNode gil) throws InvalidArrayIndexException {
        boolean mustRelease = gil.acquire();
        try {
            try {
                setItem.execute(inliningTarget, store, PInt.intValueExact(index), convert.executeConvert(value));
            } catch (OverflowException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw InvalidArrayIndexException.create(index);
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    public void removeArrayElement(long index,
                    @Bind Node inliningTarget,
                    @Exclusive @Cached SequenceStorageNodes.DeleteItemNode delItem,
                    @Exclusive @Cached GilNode gil) throws InvalidArrayIndexException {
        boolean mustRelease = gil.acquire();
        try {
            try {
                delItem.execute(inliningTarget, store, PInt.intValueExact(index));
            } catch (OverflowException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw InvalidArrayIndexException.create(index);
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isReadonly() {
        return false;
    }

    @ExportMessage
    void writeByte(int byteOffset, byte value,
                    @Shared("bufferLib") @CachedLibrary(limit = "2") PythonBufferAccessLibrary bufferLib) {
        bufferLib.writeByte(store, byteOffset, value);
    }

    @ExportMessage
    void writeShortByteOrder(int byteOffset, short value, ByteOrder byteOrder,
                    @Shared("bufferLib") @CachedLibrary(limit = "2") PythonBufferAccessLibrary bufferLib) {
        bufferLib.writeShortByteOrder(store, byteOffset, value, byteOrder);
    }

    @ExportMessage
    void writeIntByteOrder(int byteOffset, int value, ByteOrder byteOrder,
                    @Shared("bufferLib") @CachedLibrary(limit = "2") PythonBufferAccessLibrary bufferLib) {
        bufferLib.writeIntByteOrder(store, byteOffset, value, byteOrder);
    }

    @ExportMessage
    void writeLongByteOrder(int byteOffset, long value, ByteOrder byteOrder,
                    @Shared("bufferLib") @CachedLibrary(limit = "2") PythonBufferAccessLibrary bufferLib) {
        bufferLib.writeLongByteOrder(store, byteOffset, value, byteOrder);
    }

    @ExportMessage
    void writeFloatByteOrder(int byteOffset, float value, ByteOrder byteOrder,
                    @Shared("bufferLib") @CachedLibrary(limit = "2") PythonBufferAccessLibrary bufferLib) {
        bufferLib.writeFloatByteOrder(store, byteOffset, value, byteOrder);
    }

    @ExportMessage
    void writeDoubleByteOrder(int byteOffset, double value, ByteOrder byteOrder,
                    @Shared("bufferLib") @CachedLibrary(limit = "2") PythonBufferAccessLibrary bufferLib) {
        bufferLib.writeDoubleByteOrder(store, byteOffset, value, byteOrder);
    }
}
