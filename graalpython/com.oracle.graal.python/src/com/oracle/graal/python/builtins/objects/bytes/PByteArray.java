/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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

import java.util.Arrays;

import com.oracle.graal.python.builtins.objects.common.IndexNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.ExportMessage.Ignore;
import com.oracle.truffle.api.object.Shape;

@ExportLibrary(PythonObjectLibrary.class)
public final class PByteArray extends PBytesLike {

    public PByteArray(Object cls, Shape instanceShape, byte[] bytes) {
        super(cls, instanceShape, bytes);
    }

    public PByteArray(Object cls, Shape instanceShape, SequenceStorage store) {
        super(cls, instanceShape, store);
    }

    @Override
    @TruffleBoundary
    public String toString() {
        return formatByteArray("bytearray");
    }

    @TruffleBoundary
    public String formatByteArray(String typeName) {
        if (getSequenceStorage() instanceof ByteSequenceStorage) {
            byte[] barr = ((ByteSequenceStorage) getSequenceStorage()).getInternalByteArray();
            return String.format("%s(%s)", typeName, BytesUtils.bytesRepr(barr, barr.length));
        } else {
            return String.format("%s(%s)", typeName, getSequenceStorage());
        }
    }

    @Ignore
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof PByteArray)) {
            return false;
        }
        if (this == obj) {
            return true;
        }
        PByteArray other = (PByteArray) obj;
        return Arrays.equals(store.getInternalArray(), other.store.getInternalArray());
    }

    @Override
    public final int hashCode() {
        return Arrays.hashCode(store.getInternalArray());
    }

    public final void reverse() {
        store.reverse();
    }

    @ExportMessage
    public boolean isArrayElementModifiable(long index,
                    @Cached.Exclusive @Cached SequenceStorageNodes.LenNode lenNode,
                    @Cached.Exclusive @Cached IndexNodes.NormalizeIndexCustomMessageNode normalize) {
        final int len = lenNode.execute(store);
        try {
            normalize.execute(index, len, ErrorMessages.INDEX_OUT_OF_RANGE);
        } catch (PException e) {
            return false;
        }
        return true;
    }

    @ExportMessage
    public boolean isArrayElementInsertable(long index,
                    @Cached.Exclusive @Cached SequenceStorageNodes.LenNode lenNode) {
        final int len = lenNode.execute(store);
        return index == len;
    }

    @ExportMessage
    public boolean isArrayElementRemovable(long index,
                    @Cached.Exclusive @Cached SequenceStorageNodes.LenNode lenNode,
                    @Cached.Exclusive @Cached IndexNodes.NormalizeIndexCustomMessageNode normalize) {
        final int len = lenNode.execute(store);
        try {
            normalize.execute(index, len, ErrorMessages.INDEX_OUT_OF_RANGE);
        } catch (PException e) {
            return false;
        }
        return true;
    }

    @ExportMessage
    public void writeArrayElement(long index, Object value,
                    @Cached.Exclusive @Cached SequenceStorageNodes.SetItemScalarNode setItem) throws InvalidArrayIndexException {
        try {
            setItem.execute(store, PInt.intValueExact(index), value);
        } catch (OverflowException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw InvalidArrayIndexException.create(index);
        }
    }

    @ExportMessage
    public void removeArrayElement(long index,
                    @Cached.Exclusive @Cached SequenceStorageNodes.DeleteItemNode delItem) throws InvalidArrayIndexException {
        try {
            delItem.execute(store, PInt.intValueExact(index));
        } catch (OverflowException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw InvalidArrayIndexException.create(index);
        }
    }
}
