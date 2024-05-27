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

import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;

public final class EmptySequenceStorage extends SequenceStorage {

    public static final EmptySequenceStorage INSTANCE = new EmptySequenceStorage();

    public ArrayBasedSequenceStorage generalizeFor(Object value) {
        final ArrayBasedSequenceStorage generalized;

        if (value instanceof Byte) {
            generalized = new ByteSequenceStorage(16);
        } else if (value instanceof Boolean) {
            generalized = new BoolSequenceStorage(16);
        } else if (value instanceof Integer) {
            generalized = new IntSequenceStorage();
        } else if (value instanceof Long) {
            generalized = new LongSequenceStorage();
        } else if (value instanceof Double) {
            generalized = new DoubleSequenceStorage();
        } else {
            generalized = new ObjectSequenceStorage(PythonUtils.EMPTY_OBJECT_ARRAY);
        }

        return generalized;
    }

    @Override
    public Object getIndicativeValue() {
        return null;
    }

    @Override
    public StorageType getElementType() {
        return StorageType.Empty;
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return "EmptySequenceStorage[]";
    }
}
