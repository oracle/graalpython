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
package com.oracle.graal.python.builtins.objects.tuple;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.ObjectSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.object.Shape;

@ExportLibrary(PythonObjectLibrary.class)
public final class PTuple extends PSequence {

    private SequenceStorage store;
    private long hash = -1;

    public PTuple(Object cls, Shape instanceShape, Object[] elements) {
        super(cls, instanceShape);
        this.store = new ObjectSequenceStorage(elements);
    }

    public PTuple(Object cls, Shape instanceShape, SequenceStorage store) {
        super(cls, instanceShape);
        this.store = store;
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        if (store instanceof ObjectSequenceStorage) {
            StringBuilder buf = new StringBuilder("(");
            Object[] array = store.getInternalArray();
            for (int i = 0; i < array.length - 1; i++) {
                buf.append(array[i]);
                buf.append(", ");
            }

            if (array.length > 0) {
                buf.append(array[array.length - 1]);
            }

            if (array.length == 1) {
                buf.append(",");
            }

            buf.append(")");
            return buf.toString();
        } else {
            return String.format("tuple(%s)", store);
        }
    }

    @Override
    public SequenceStorage getSequenceStorage() {
        return store;
    }

    @Override
    public void setSequenceStorage(SequenceStorage store) {
        this.store = store;
    }

    @ExportMessage.Ignore
    @Override
    public boolean equals(Object other) {
        CompilerAsserts.neverPartOfCompilation();
        if (!(other instanceof PTuple)) {
            return false;
        }

        PTuple otherTuple = (PTuple) other;
        return store.equals(otherTuple.store);
    }

    @Override
    public int hashCode() {
        CompilerAsserts.neverPartOfCompilation();
        return super.hashCode();
    }

    public static PTuple require(Object value) {
        if (value instanceof PTuple) {
            return (PTuple) value;
        }
        CompilerDirectives.transferToInterpreter();
        throw new IllegalStateException("PTuple required.");
    }

    public static PTuple expect(Object value) throws UnexpectedResultException {
        if (value instanceof PTuple) {
            return (PTuple) value;
        }
        throw new UnexpectedResultException(value);
    }

    public long getHash() {
        return hash;
    }

    public void setHash(long hash) {
        this.hash = hash;
    }

    @SuppressWarnings({"static-method", "unused"})
    public static void setItem(int idx, Object value) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        PythonLanguage.getCore().raise(PythonBuiltinClassType.PTuple, ErrorMessages.OBJ_DOES_NOT_SUPPORT_ITEM_ASSIGMENT);
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
}
