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
package com.oracle.graal.python.runtime.sequence.storage;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;

public abstract class BasicSequenceStorage extends SequenceStorage {

    public abstract Object getItemNormalized(int idx);

    public abstract void setItemNormalized(int idx, Object value) throws SequenceStoreException;

    public abstract void insertItem(int idx, Object value) throws SequenceStoreException;

    public abstract void reverse();

    public abstract SequenceStorage copy();

    public abstract Object getCopyOfInternalArrayObject();

    public abstract void setInternalArrayObject(Object arrayObject);

    public final void setNewLength(int length) {
        this.length = length;
    }

    protected final void incLength() {
        this.length++;
    }

    public abstract BasicSequenceStorage createEmpty(int newCapacity);

    /**
     * The capacity we should allocate for a given length.
     */
    private static int capacityFor(int length) throws ArithmeticException {
        return Math.max(16, Math.multiplyExact(length, 2));
    }

    /**
     * Ensure that the current capacity is big enough. If not, we increase capacity to the next
     * designated size (not necessarily the requested one).
     */
    public final void ensureCapacity(int newCapacity) throws ArithmeticException {
        if (CompilerDirectives.injectBranchProbability(CompilerDirectives.UNLIKELY_PROBABILITY, newCapacity > capacity)) {
            increaseCapacityExactWithCopy(capacityFor(newCapacity));
        }
    }

    protected abstract void increaseCapacityExactWithCopy(int newCapacity);

    public void minimizeCapacity() {
        capacity = length;
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        StringBuilder str = new StringBuilder(getClass().getSimpleName()).append('[');
        int len = length > 10 ? 10 : length;
        for (int i = 0; i < len; i++) {
            str.append(i == 0 ? "" : ", ");
            str.append(getItemNormalized(i));
        }
        if (length > 10) {
            str.append("...").append('(').append(length).append(')');
        }
        return str.append(']').toString();
    }
}
