/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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

import com.oracle.graal.python.builtins.objects.common.IndexNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.object.Shape;

@ExportLibrary(PythonObjectLibrary.class)
public class PArray extends PSequence {

    private SequenceStorage store;

    public PArray(Object clazz, Shape instanceShape) {
        super(clazz, instanceShape);
    }

    public PArray(Object clazz, Shape instanceShape, SequenceStorage store) {
        super(clazz, instanceShape);
        this.store = store;
    }

    @Override
    public SequenceStorage getSequenceStorage() {
        return store;
    }

    @Override
    public void setSequenceStorage(SequenceStorage store) {
        this.store = store;
    }

    public int len() {
        return store.length();
    }

    @ExportMessage
    static boolean isBuffer(@SuppressWarnings("unused") PArray self) {
        return true;
    }

    @ExportMessage
    byte[] getBufferBytes(
                    @Cached SequenceStorageNodes.ToByteArrayNode toByteArrayNode) {
        // TODO Implement access to the actual bytes which represent the array in memory.
        // This implementation only works for ByteSequenceStorage.
        return toByteArrayNode.execute(store);
    }

    @ExportMessage
    int getBufferLength(
                    @Cached SequenceStorageNodes.LenNode lenNode) {
        // TODO This only works for ByteSequenceStorage since its itemsize is 1.
        return lenNode.execute(store);
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
