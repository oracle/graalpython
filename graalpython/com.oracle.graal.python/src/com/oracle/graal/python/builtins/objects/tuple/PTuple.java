/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates.
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
package com.oracle.graal.python.builtins.objects.tuple;

import static com.oracle.graal.python.util.PythonUtils.builtinClassToType;

import com.oracle.graal.python.runtime.sequence.PSequenceWithStorage;
import com.oracle.graal.python.runtime.sequence.storage.ObjectSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.object.Shape;

@SuppressWarnings("truffle-abstract-export")
@ExportLibrary(InteropLibrary.class)
public final class PTuple extends PSequenceWithStorage {

    private long hash = -1;

    public PTuple(Object cls, Shape instanceShape, Object[] elements) {
        super(builtinClassToType(cls), instanceShape);
        this.store = new ObjectSequenceStorage(elements);
    }

    public PTuple(Object cls, Shape instanceShape, SequenceStorage store) {
        super(builtinClassToType(cls), instanceShape);
        this.store = store;
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return String.format("tuple(%s)", store);
    }

    public long getHash() {
        return hash;
    }

    public void setHash(long hash) {
        this.hash = hash;
    }

    @ExportMessage
    @SuppressWarnings("unused")
    public static boolean isArrayElementModifiable(PTuple self, long index) {
        return false;
    }

    @ExportMessage
    @SuppressWarnings("unused")
    public static boolean isArrayElementInsertable(PTuple self, long index) {
        return false;
    }

    @ExportMessage
    @SuppressWarnings("unused")
    public static boolean isArrayElementRemovable(PTuple self, long index) {
        return false;
    }

    @ExportMessage
    @SuppressWarnings("unused")
    public static void writeArrayElement(PTuple self, long key, Object value) throws UnsupportedMessageException, InvalidArrayIndexException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    @SuppressWarnings("unused")
    public static void removeArrayElement(PTuple self, long key) throws UnsupportedMessageException, InvalidArrayIndexException {
        throw UnsupportedMessageException.create();
    }
}
