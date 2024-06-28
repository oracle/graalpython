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

public abstract class SequenceStorage {

    public enum StorageType {
        Uninitialized,
        Empty,
        Boolean,
        Byte,
        Int,
        Long,
        Double,
        Generic;

        public boolean generalizesFrom(StorageType other) {
            return switch (this) {
                case Uninitialized, Empty -> false;
                case Boolean, Byte, Double, Int -> other == Uninitialized || other == Empty || other == Byte;
                case Long -> other == Uninitialized || other == Empty || other == Byte || other == Int;
                default -> true;
            };
        }
    }

    // nominated storage length
    protected int length;

    // physical storage length
    protected int capacity;

    protected SequenceStorage() {
    }

    protected SequenceStorage(int length, int capacity) {
        this.length = length;
        this.capacity = capacity;
    }

    public void setNewLength(int length) {
        this.length = length;
    }

    public final void incLength() {
        this.length++;
    }

    public final int length() {
        return length;
    }

    public final int getCapacity() {
        return capacity;
    }

    public abstract StorageType getElementType();

    public abstract Object getIndicativeValue();
}
