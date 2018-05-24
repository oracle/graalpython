/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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
package com.oracle.graal.python.runtime.sequence.storage;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public abstract class SequenceStorage {

    @CompilationFinal private static boolean LOG_GENERALIZATION = false;

    public abstract int length();

    public abstract void setNewLength(int length);

    public abstract SequenceStorage copy();

    public abstract SequenceStorage createEmpty(int newCapacity);

    public abstract Object[] getInternalArray();

    public abstract Object[] getCopyOfInternalArray();

    public abstract Object getItemNormalized(int idx);

    public abstract void setItemNormalized(int idx, Object value) throws SequenceStoreException;

    public abstract void insertItem(int idx, Object value) throws SequenceStoreException;

    public abstract SequenceStorage getSliceInBound(int start, int stop, int step, int length);

    public abstract void setSliceInBound(int start, int stop, int step, SequenceStorage sequence) throws SequenceStoreException;

    public abstract void delSlice(int start, int stop, int step);

    public abstract void delItemInBound(int idx);

    public abstract Object popInBound(int idx);

    public abstract int index(Object value);

    public abstract void append(Object value) throws SequenceStoreException;

    public abstract void extend(SequenceStorage other) throws SequenceStoreException, ArithmeticException;

    public abstract void reverse();

    public abstract void sort();

    public abstract boolean equals(SequenceStorage other);

    public abstract SequenceStorage generalizeFor(Object value);

    public abstract Object getIndicativeValue();

    public abstract void ensureCapacity(int newCapacity);

    public abstract void copyItem(int idxTo, int idxFrom);

    public void clear() {
        this.delSlice(0, length(), 1);
    }

    public int count(Object arg) {
        int count = 0;
        for (Object obj : getInternalArray()) {
            if (obj.equals(arg)) {
                count++;
            }
        }
        return count;
    }

    protected void logGeneralization() {
        // TODO(ls): this is expensive
        if (LOG_GENERALIZATION && PythonOptions.getOption(PythonLanguage.getContext(), PythonOptions.TraceSequenceStorageGeneralization)) {
            printGeneralization("ObjectSequenceStorage");
        }
    }

    protected void logGeneralization(SequenceStorage storage) {
        if (LOG_GENERALIZATION && PythonOptions.getOption(PythonLanguage.getContext(), PythonOptions.TraceSequenceStorageGeneralization)) {
            printGeneralization(storage);
        }
    }

    @TruffleBoundary
    private void printGeneralization(Object storage) {
        System.out.println("[GraalPython]" + this + " generalizing to " + storage);
    }
}
