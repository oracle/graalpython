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
package com.oracle.graal.python.builtins.objects.dict;

import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage;
import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage.FastDictStorage;
import com.oracle.graal.python.builtins.objects.common.EconomicMapStorage;
import com.oracle.graal.python.builtins.objects.common.EmptyStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage.DictEntry;
import com.oracle.graal.python.builtins.objects.common.HashingStorage.UnmodifiableStorageException;
import com.oracle.graal.python.builtins.objects.common.KeywordsStorage;
import com.oracle.graal.python.builtins.objects.common.PHashingCollection;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.truffle.api.CompilerAsserts;

public final class PDict extends PHashingCollection {

    private HashingStorage dictStorage;

    public PDict(PythonClass cls, HashingStorage dictStorage) {
        super(cls);
        this.dictStorage = dictStorage;
    }

    public PDict(PythonClass cls) {
        super(cls);
        this.dictStorage = new EmptyStorage();
    }

    public PDict(PythonClass cls, PKeyword[] keywords) {
        super(cls);
        this.dictStorage = (keywords != null) ? KeywordsStorage.create(keywords) : new EmptyStorage();
    }

    public Object getItem(Object key) {
        return dictStorage.getItem(key, HashingStorage.getSlowPathEquivalence(key));
    }

    public void setItem(Object key, Object value) {
        try {
            dictStorage.setItem(key, value, HashingStorage.getSlowPathEquivalence(key));
        } catch (UnmodifiableStorageException e) {
            HashingStorage newDictStorage = createNewStorage(key instanceof String, size() + 1);
            newDictStorage.setItem(key, value, HashingStorage.getSlowPathEquivalence(key));
            dictStorage = newDictStorage;
        }
    }

    public static HashingStorage createNewStorage(boolean isStringKey, int expectedSize) {
        HashingStorage newDictStorage;
        if (expectedSize == 0) {
            newDictStorage = new EmptyStorage();
        } else if (isStringKey && expectedSize < DynamicObjectStorage.SIZE_THRESHOLD) {
            newDictStorage = new FastDictStorage();
        } else {
            newDictStorage = EconomicMapStorage.create(expectedSize, false);
        }
        return newDictStorage;
    }

    public void delItem(Object key) {
        try {
            dictStorage.remove(key, HashingStorage.getSlowPathEquivalence(key));
        } catch (UnmodifiableStorageException e) {
            HashingStorage newDictStorage = createNewStorage(key instanceof String, size() - 1);
            newDictStorage.remove(key, HashingStorage.getSlowPathEquivalence(key));
            dictStorage = newDictStorage;
        }
    }

    public void update(PDict other) {
        for (DictEntry entry : other.entries()) {
            this.setItem(entry.key, entry.value);
        }
    }

    public Iterable<Object> items() {
        return dictStorage.values();
    }

    public Iterable<Object> keys() {
        return dictStorage.keys();
    }

    @Override
    public void setDictStorage(HashingStorage newStorage) {
        dictStorage = newStorage;
    }

    @Override
    public HashingStorage getDictStorage() {
        return dictStorage;
    }

    public boolean hasKey(Object key) {
        return dictStorage.hasKey(key, HashingStorage.getSlowPathEquivalence(key));
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        StringBuilder buf = new StringBuilder("{");
        int length = dictStorage.length();
        int i = 0;

        for (HashingStorage.DictEntry entry : dictStorage.entries()) {
            buf.append(entry.getKey() + ": " + entry.getValue());

            if (i < length - 1) {
                buf.append(", ");
            }

            i++;
        }

        buf.append("}");
        return buf.toString();
    }

    @Override
    public int size() {
        return dictStorage.length();
    }

    public Iterable<DictEntry> entries() {
        return dictStorage.entries();
    }

}
