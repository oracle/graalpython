/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates.
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
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.Shape;

@SuppressWarnings("truffle-abstract-export")
@ExportLibrary(InteropLibrary.class)
public abstract class PSequence extends PythonBuiltinObject {

    public PSequence(Object cls, Shape instanceShape) {
        super(cls, instanceShape);
    }

    public abstract SequenceStorage getSequenceStorage();

    /**
     * Note: Sequences are never immutable for us because they can go <it>to native</it>, i.e., the
     * storage will be exchanged and also, native code often allows to modify <it>immutable</it>
     * objects (like {@code _PyTuple_Resize}).
     */
    public abstract void setSequenceStorage(SequenceStorage newStorage);

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
    public long getArraySize(@Bind("$node") Node inliningTarget,
                    @Exclusive @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            return getSequenceStorageNode.execute(inliningTarget, this).length();
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    public Object readArrayElement(long index,
                    @Bind("$node") Node inliningTarget,
                    @Exclusive @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                    @Exclusive @Cached SequenceStorageNodes.GetItemScalarNode getItem,
                    @Exclusive @Cached GilNode gil) throws InvalidArrayIndexException {
        boolean mustRelease = gil.acquire();
        try {
            try {
                return getItem.execute(inliningTarget, getSequenceStorageNode.execute(inliningTarget, this), PInt.intValueExact(index));
            } catch (OverflowException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw InvalidArrayIndexException.create(index);
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    public void writeArrayElement(long index, Object value,
                    @Bind("$node") Node inliningTarget,
                    @Exclusive @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                    @Exclusive @Cached SequenceStorageNodes.SetItemScalarNode setItem,
                    @Exclusive @Cached GilNode gil) throws InvalidArrayIndexException {
        boolean mustRelease = gil.acquire();
        try {
            try {
                setItem.execute(inliningTarget, getSequenceStorageNode.execute(inliningTarget, this), PInt.intValueExact(index), value);
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
                    @Bind("$node") Node inliningTarget,
                    @Exclusive @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                    @Exclusive @Cached SequenceStorageNodes.DeleteItemNode delItem,
                    @Exclusive @Cached GilNode gil) throws InvalidArrayIndexException {
        boolean mustRelease = gil.acquire();
        try {
            try {
                delItem.execute(inliningTarget, getSequenceStorageNode.execute(inliningTarget, this), PInt.intValueExact(index));
            } catch (OverflowException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw InvalidArrayIndexException.create(index);
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    public boolean isArrayElementReadable(long idx,
                    @Bind("$node") Node inliningTarget,
                    @Exclusive @Cached SequenceNodes.IsInBoundsNode isInBoundsNode,
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            return isInBoundsNode.execute(inliningTarget, this, idx);
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    public boolean isArrayElementModifiable(long idx,
                    @Bind("$node") Node inliningTarget,
                    @Exclusive @Cached SequenceNodes.IsInBoundsNode isInBoundsNode,
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            return isInBoundsNode.execute(inliningTarget, this, idx);
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    public boolean isArrayElementInsertable(long idx,
                    @Bind("$node") Node inliningTarget,
                    @Exclusive @Cached SequenceNodes.IsInBoundsNode isInBoundsNode,
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            return !isInBoundsNode.execute(inliningTarget, this, idx);
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    public boolean isArrayElementRemovable(long idx,
                    @Bind("$node") Node inliningTarget,
                    @Exclusive @Cached SequenceNodes.IsInBoundsNode isInBoundsNode,
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            return isInBoundsNode.execute(inliningTarget, this, idx);
        } finally {
            gil.release(mustRelease);
        }
    }
}
