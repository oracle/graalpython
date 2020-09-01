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

import java.io.UnsupportedEncodingException;
import java.util.Arrays;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.function.PArguments.ThreadState;
import com.oracle.graal.python.builtins.objects.iterator.PSequenceIterator;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.NativeSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage.ListStorageType;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.ExportMessage.Ignore;
import com.oracle.truffle.api.object.Shape;

@ExportLibrary(PythonObjectLibrary.class)
public final class PBytes extends PBytesLike {

    public PBytes(Object cls, Shape instanceShape, byte[] bytes) {
        super(cls, instanceShape, bytes);
    }

    public PBytes(Object cls, Shape instanceShape, SequenceStorage store) {
        super(cls, instanceShape, store);
    }

    @Override
    public void setSequenceStorage(SequenceStorage store) {
        assert store instanceof ByteSequenceStorage || store instanceof NativeSequenceStorage && ((NativeSequenceStorage) store).getElementType() == ListStorageType.Byte;
        this.store = store;
    }

    public Object getItemNormalized(int index) {
        return store.getItemNormalized(index);
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

    @Ignore
    @Override
    public final boolean equals(Object other) {
        // TODO(fa) really required ?
        if (!(other instanceof PSequence)) {
            return false;
        } else {
            return equals((PSequence) other);
        }
    }

    @Ignore
    public final boolean equals(PSequence other) {
        CompilerAsserts.neverPartOfCompilation();
        PSequence otherSeq = other;
        SequenceStorage otherStore = otherSeq.getSequenceStorage();
        return store.equals(otherStore);
    }

    @Override
    public final int hashCode() {
        // TODO(fa) really required ?
        if (store instanceof ByteSequenceStorage) {
            return Arrays.hashCode(((ByteSequenceStorage) store).getInternalByteArray());
        }
        return store.hashCode();
    }

    @ExportMessage
    public String asPathWithState(@SuppressWarnings("unused") ThreadState state,
                    @Cached PRaiseNode raise,
                    @Cached SequenceStorageNodes.ToByteArrayNode toBytes) {
        return newString(raise, toBytes.execute(getSequenceStorage()));
    }

    @CompilerDirectives.TruffleBoundary
    public static String newString(PRaiseNode raise, byte[] ary) {
        try {
            return new String(ary, "ascii");
        } catch (UnsupportedEncodingException e) {
            throw raise.raise(PythonBuiltinClassType.UnicodeDecodeError, e);
        }
    }

    @SuppressWarnings({"static-method", "unused"})
    public static void setItem(int idx, Object value) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        PythonLanguage.getCore().raise(PythonBuiltinClassType.PBytes, ErrorMessages.OBJ_DOES_NOT_SUPPORT_ITEM_ASSIGMENT);
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
    PSequenceIterator getIteratorWithState(@SuppressWarnings("unused") ThreadState threadState,
                    @Cached PythonObjectFactory factory) {
        return factory.createSequenceIterator(this);
    }
}
