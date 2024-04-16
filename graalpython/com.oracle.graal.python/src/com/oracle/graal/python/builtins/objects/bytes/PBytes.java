/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates.
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

import com.oracle.graal.python.builtins.objects.buffer.BufferFlags;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.object.Shape;

@SuppressWarnings("truffle-abstract-export")
@ExportLibrary(InteropLibrary.class)
@ExportLibrary(PythonBufferAcquireLibrary.class)
public final class PBytes extends PBytesLike {

    public PBytes(Object cls, Shape instanceShape, byte[] bytes) {
        super(cls, instanceShape, bytes);
    }

    public PBytes(Object cls, Shape instanceShape, SequenceStorage store) {
        super(cls, instanceShape, store);
    }

    @Override
    public String toString() {
        // TODO(fa) really required ?
        CompilerAsserts.neverPartOfCompilation();
        if (store instanceof ByteSequenceStorage) {
            return BytesUtils.bytesRepr(((ByteSequenceStorage) store).getInternalByteArray(), store.length());
        } else {
            return store.toString();
        }
    }

    @ExportMessage
    @SuppressWarnings("unused")
    public static boolean isArrayElementModifiable(PBytes self, long index) {
        return false;
    }

    @ExportMessage
    @SuppressWarnings("unused")
    public static boolean isArrayElementInsertable(PBytes self, long index) {
        return false;
    }

    @ExportMessage
    @SuppressWarnings("unused")
    public static boolean isArrayElementRemovable(PBytes self, long index) {
        return false;
    }

    @ExportMessage
    @SuppressWarnings("unused")
    public static void writeArrayElement(PBytes self, long key, Object value) throws UnsupportedMessageException, InvalidArrayIndexException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    @SuppressWarnings("unused")
    public static void removeArrayElement(PBytes self, long key) throws UnsupportedMessageException, InvalidArrayIndexException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    Object acquire(int flags,
                    @Cached PRaiseNode raiseNode) {
        if ((flags & BufferFlags.PyBUF_WRITABLE) != 0) {
            throw raiseNode.raise(BufferError, ErrorMessages.OBJ_IS_NOT_WRITABLE);
        }
        return this;
    }
}
