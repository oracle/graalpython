/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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

import static com.oracle.graal.python.builtins.objects.bytes.BytesUtils.__repr__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.util.Arrays;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.array.PArray;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.SequenceUtil;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.EmptySequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStoreException;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;

public final class PByteArray extends PArray implements PIBytesLike {

    private SequenceStorage store;

    public PByteArray(PythonClass cls, byte[] bytes) {
        super(cls);
        store = new ByteSequenceStorage(bytes);
    }

    public PByteArray(PythonClass cls, SequenceStorage store) {
        super(cls);
        this.store = store;
    }

    @Override
    public Object getItem(int idx) {
        return getItemNormalized(SequenceUtil.normalizeIndex(idx, store.length(), "array index out of range"));
    }

    @Override
    public Object getItemNormalized(int idx) {
        return store.getItemNormalized(idx);
    }

    public void setItem(int idx, Object value) {
        setItemNormalized(SequenceUtil.normalizeIndex(idx, store.length(), "array index out of range"), value);
    }

    public void setItemNormalized(int index, Object value) {
        try {
            store.setItemNormalized(index, value);
        } catch (SequenceStoreException e) {
            store = store.generalizeFor(value, null);

            try {
                store.setItemNormalized(index, value);
            } catch (SequenceStoreException ex) {
                throw new IllegalStateException();
            }
        }
    }

    @Override
    public Object getSlice(PythonObjectFactory factory, int start, int stop, int step, int length) {
        return factory.createByteArray(this.getPythonClass(), store.getSliceInBound(start, stop, step, length));
    }

    @Override
    public void setSlice(int start, int stop, int step, PSequence value) {
        final int normalizedStart = SequenceUtil.normalizeSliceStart(start, step, store.length());
        int normalizedStop = SequenceUtil.normalizeSliceStop(stop, step, store.length());

        if (normalizedStop < normalizedStart) {
            normalizedStop = normalizedStart;
        }

        SequenceStorage other = value.getSequenceStorage();
        try {
            store.setSliceInBound(normalizedStart, normalizedStop, step, other);
        } catch (SequenceStoreException e) {
            throw PythonLanguage.getCore().raise(TypeError, "an integer is required");
        }
    }

    @Override
    public void setSlice(PSlice slice, PSequence value) {
        PSlice.SliceInfo sliceInfo = slice.computeActualIndices(len());
        setSlice(sliceInfo.start, sliceInfo.stop, sliceInfo.step, value);
    }

    @Override
    public void delItem(int idx) {
        int index = SequenceUtil.normalizeIndex(idx, store.length(), "array index out of range");
        store.delItemInBound(index);
    }

    @Override
    public int index(Object value) {
        int index = store.index(value);

        if (index != -1) {
            return index;
        }

        CompilerDirectives.transferToInterpreter();
        throw PythonLanguage.getCore().raise(ValueError, "%s is not in bytes literal", value);
    }

    @Override
    public SequenceStorage getSequenceStorage() {
        return store;
    }

    @Override
    public final void setSequenceStorage(SequenceStorage newStorage) {
        this.store = newStorage;
    }

    @Override
    public boolean lessThan(PSequence sequence) {
        return false;
    }

    @Override
    public int len() {
        return store.length();
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return String.format("bytearray(%s)", __repr__(getInternalByteArray(), store.length()));
    }

    @Override
    public final boolean equals(Object other) {
        if (!(other instanceof PSequence)) {
            return false;
        } else {
            return equals((PSequence) other);
        }
    }

    public final boolean equals(PSequence other) {
        if (len() == 0 && other.len() == 0) {
            return true;
        }
        SequenceStorage otherStore = other.getSequenceStorage();
        return store.equals(otherStore);
    }

    @Override
    public final int hashCode() {
        return Arrays.hashCode(store.getInternalArray());
    }

    public final void reverse() {
        store.reverse();
    }

    public final void clear() {
        store.clear();
    }

    public final void append(Object value) {
        if (store instanceof EmptySequenceStorage) {
            store = new ByteSequenceStorage(1);
        }
        store.append(value);
    }

    public PByteArray copy() {
        return new PByteArray(this.getPythonClass(), store.copy());
    }

    public final void delSlice(PSlice slice) {
        int start = Math.max(0, SequenceUtil.normalizeSliceStart(slice, store.length()));
        final int stop = Math.min(store.length(), SequenceUtil.normalizeSliceStop(slice, store.length()));
        final int step = SequenceUtil.normalizeSliceStep(slice);
        store.delSlice(start, stop, step);
    }

    public int count(Object arg) {
        return this.store.count(arg);
    }

    @Override
    public byte[] getInternalByteArray() {
        if (store instanceof ByteSequenceStorage) {
            return ((ByteSequenceStorage) store).getInternalByteArray();
        } else {
            throw new UnsupportedOperationException("this case is not yet supported!");
        }
    }

    @Override
    public PIBytesLike createFromBytes(PythonObjectFactory factory, byte[] bytes) {
        return factory.createByteArray(bytes);
    }
}
