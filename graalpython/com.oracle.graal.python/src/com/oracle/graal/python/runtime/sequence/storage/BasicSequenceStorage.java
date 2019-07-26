/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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

public abstract class BasicSequenceStorage extends SequenceStorage {

    // nominated storage length
    protected int length;

    // physical storage length
    protected int capacity;

    @Override
    public final int length() {
        return length;
    }

    @Override
    public void setNewLength(int length) {
        this.length = length;
    }

    public abstract Object getCopyOfInternalArrayObject();

    public abstract void setInternalArrayObject(Object arrayObject);

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
    @Override
    public void ensureCapacity(int newCapacity) throws ArithmeticException {
        if (newCapacity > capacity) {
            increaseCapacityExactWithCopy(capacityFor(newCapacity));
        }
    }

    protected abstract void increaseCapacityExactWithCopy(int newCapacity);

    protected abstract void increaseCapacityExact(int newCapacity);

    public void minimizeCapacity() {
        capacity = length;
    }
}
