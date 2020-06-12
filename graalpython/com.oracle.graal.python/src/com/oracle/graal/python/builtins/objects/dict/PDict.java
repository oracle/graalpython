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
package com.oracle.graal.python.builtins.objects.dict;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage;
import com.oracle.graal.python.builtins.objects.common.EconomicMapStorage;
import com.oracle.graal.python.builtins.objects.common.EmptyStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.common.KeywordsStorage;
import com.oracle.graal.python.builtins.objects.common.PHashingCollection;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.object.DynamicObject;

public final class PDict extends PHashingCollection {

    private HashingStorage dictStorage;

    public PDict() {
        this(PythonBuiltinClassType.PDict, PythonBuiltinClassType.PDict.newInstance());
    }

    public PDict(Object cls, DynamicObject storage, HashingStorage dictStorage) {
        super(cls, storage);
        this.dictStorage = dictStorage;
    }

    public PDict(Object cls, DynamicObject storage) {
        super(cls, storage);
        this.dictStorage = new EmptyStorage();
    }

    public PDict(Object cls, DynamicObject storage, PKeyword[] keywords) {
        super(cls, storage);
        this.dictStorage = (keywords != null) ? KeywordsStorage.create(keywords) : new EmptyStorage();
    }

    public Object getItem(Object key) {
        return HashingStorageLibrary.getUncached().getItem(dictStorage, key);
    }

    public void setItem(Object key, Object value) {
        dictStorage = HashingStorageLibrary.getUncached().setItem(dictStorage, key, value);
    }

    public static HashingStorage createNewStorage(boolean isStringKey, int expectedSize) {
        HashingStorage newDictStorage;
        if (expectedSize == 0) {
            newDictStorage = new EmptyStorage();
        } else if (isStringKey && expectedSize < DynamicObjectStorage.SIZE_THRESHOLD) {
            newDictStorage = new DynamicObjectStorage();
        } else {
            newDictStorage = EconomicMapStorage.create(expectedSize);
        }
        return newDictStorage;
    }

    public void update(PDict other) {
        dictStorage = HashingStorageLibrary.getUncached().addAllToOther(other.getDictStorage(), dictStorage);
    }

    @Override
    public void setDictStorage(HashingStorage newStorage) {
        dictStorage = newStorage;
    }

    @Override
    public HashingStorage getDictStorage() {
        return dictStorage;
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return "PDict<" + dictStorage.getClass().getSimpleName() + ">";
    }

    @Override
    public int size() {
        return HashingStorageLibrary.getUncached().length(dictStorage);
    }
}
