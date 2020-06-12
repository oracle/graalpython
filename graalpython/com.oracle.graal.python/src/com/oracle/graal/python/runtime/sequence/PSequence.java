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
package com.oracle.graal.python.runtime.sequence;

import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.object.DynamicObject;

@ExportLibrary(PythonObjectLibrary.class)
@ExportLibrary(InteropLibrary.class)
public abstract class PSequence extends PythonBuiltinObject {

    public PSequence(Object cls, DynamicObject storage) {
        super(cls, storage);
    }

    public abstract SequenceStorage getSequenceStorage();

    /**
     * Note: Sequences are never immutable for us because they can go <it>to native</it>, i.e., the
     * storage will be exchanged and also, native code often allows to modify <it>immutable</it>
     * objects (like {@code _PyTuple_Resize}).
     */
    public abstract void setSequenceStorage(SequenceStorage newStorage);

    public static PSequence require(Object value) {
        if (value instanceof PSequence) {
            return (PSequence) value;
        }
        CompilerDirectives.transferToInterpreter();
        throw new IllegalStateException("PSequence required.");
    }

    public static PSequence expect(Object value) throws UnexpectedResultException {
        if (value instanceof PSequence) {
            return (PSequence) value;
        }
        throw new UnexpectedResultException(value);
    }

    @ExportMessage
    public boolean isIterable() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public boolean isNumber() {
        return false;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public byte asByte() throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public boolean fitsInByte() {
        return false;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public short asShort() throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public boolean fitsInShort() {
        return false;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public int asInt() throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public boolean fitsInInt() {
        return false;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public long asLong() throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public boolean fitsInLong() {
        return false;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public float asFloat() throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public boolean fitsInFloat() {
        return false;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public double asDouble() throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public boolean fitsInDouble() {
        return false;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public boolean isString() {
        return false;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public String asString() throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    public boolean hasArrayElements() {
        return true;
    }

    @ExportMessage
    public long getArraySize(@Exclusive @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                    @Exclusive @Cached SequenceStorageNodes.LenNode lenNode) {
        return lenNode.execute(getSequenceStorageNode.execute(this));
    }

    @ExportMessage
    public Object readArrayElement(long index,
                    @Exclusive @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                    @Cached SequenceStorageNodes.GetItemScalarNode getItem) throws InvalidArrayIndexException {
        try {
            return getItem.execute(getSequenceStorageNode.execute(this), PInt.intValueExact(index));
        } catch (OverflowException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw InvalidArrayIndexException.create(index);
        }
    }

}
