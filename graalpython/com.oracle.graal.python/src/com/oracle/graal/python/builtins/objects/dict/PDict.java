/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates.
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

import static com.oracle.graal.python.nodes.SpecialMethodNames.T_ITEMS;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T_KEYS;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T_VALUES;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.common.EconomicMapStorage;
import com.oracle.graal.python.builtins.objects.common.EmptyStorage;
import com.oracle.graal.python.builtins.objects.common.HashMapStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.common.KeywordsStorage;
import com.oracle.graal.python.builtins.objects.common.PHashingCollection;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectHashNode;
import com.oracle.graal.python.nodes.interop.PForeignToPTypeNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownKeyException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.object.Shape;

@ExportLibrary(InteropLibrary.class)
public class PDict extends PHashingCollection {

    public PDict(PythonLanguage lang) {
        this(PythonBuiltinClassType.PDict, PythonBuiltinClassType.PDict.getInstanceShape(lang));
    }

    public PDict(Object cls, Shape instanceShape, HashingStorage dictStorage) {
        super(ensurePBCT(cls), instanceShape, dictStorage);
    }

    private static Object ensurePBCT(Object cls) {
        if (cls instanceof PythonBuiltinClass && ((PythonBuiltinClass) cls).getType() == PythonBuiltinClassType.PDict) {
            return PythonBuiltinClassType.PDict;
        }
        return cls;
    }

    public PDict(Object cls, Shape instanceShape) {
        this(cls, instanceShape, EmptyStorage.INSTANCE);
    }

    public PDict(Object cls, Shape instanceShape, PKeyword[] keywords) {
        this(cls, instanceShape, (keywords != null) ? KeywordsStorage.create(keywords) : EmptyStorage.INSTANCE);
    }

    public Object getItem(Object key) {
        return HashingStorageLibrary.getUncached().getItem(storage, key);
    }

    public void setItem(Object key, Object value) {
        storage = HashingStorageLibrary.getUncached().setItem(storage, key, value);
    }

    public void delItem(Object key) {
        storage = HashingStorageLibrary.getUncached().delItem(storage, key);
    }

    public static HashingStorage createNewStorage(boolean isStringKey, int expectedSize) {
        HashingStorage newDictStorage;
        if (expectedSize == 0) {
            newDictStorage = EmptyStorage.INSTANCE;
        } else if (isStringKey) {
            newDictStorage = new HashMapStorage(expectedSize);
        } else {
            newDictStorage = EconomicMapStorage.create(expectedSize);
        }
        return newDictStorage;
    }

    public void update(PDict other) {
        storage = HashingStorageLibrary.getUncached().addAllToOther(other.getDictStorage(), storage);
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return "PDict<" + storage.getClass().getSimpleName() + ">";
    }

    @ExportMessage
    static boolean hasHashEntries(@SuppressWarnings("unused") PDict self) {
        return true;
    }

    @ExportMessage(limit = "2")
    static long getHashSize(PDict self,
                    @Exclusive @Cached GilNode gil,
                    @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
        boolean mustRelease = gil.acquire();
        try {
            return lib.length(self.getDictStorage());
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage(limit = "2")
    @ExportMessage(name = "isHashEntryModifiable", limit = "2")
    @ExportMessage(name = "isHashEntryRemovable", limit = "2")
    static boolean isHashEntryReadable(PDict self, Object key,
                    @Exclusive @Cached GilNode gil,
                    @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib,
                    @Exclusive @Cached PForeignToPTypeNode convertNode) {
        boolean mustRelease = gil.acquire();
        try {
            return lib.hasKey(self.getDictStorage(), convertNode.executeConvert(key));
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage(limit = "2")
    static Object readHashValue(PDict self, Object key,
                    @Exclusive @Cached GilNode gil,
                    @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib,
                    @Exclusive @Cached PForeignToPTypeNode convertNode) throws UnknownKeyException {
        Object value = null;
        boolean mustRelease = gil.acquire();
        try {
            value = lib.getItem(self.getDictStorage(), convertNode.executeConvert(key));
        } finally {
            gil.release(mustRelease);
        }
        if (value == null) {
            throw UnknownKeyException.create(key);
        } else {
            return value;
        }
    }

    @ExportMessage(limit = "3")
    static boolean isHashEntryInsertable(PDict self, Object key,
                    @Exclusive @Cached GilNode gil,
                    @Cached PyObjectHashNode hashNode,
                    @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib,
                    @Exclusive @Cached PForeignToPTypeNode convertNode) {
        boolean mustRelease = gil.acquire();
        try {
            Object pKey = convertNode.executeConvert(key);
            if (lib.hasKey(self.getDictStorage(), pKey)) {
                return false;
            } else {
                // we can only insert hashable types
                try {
                    hashNode.execute(null, pKey);
                } catch (AbstractTruffleException e) {
                    return false;
                }
                return true;
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage(limit = "2")
    static void writeHashEntry(PDict self, Object key, Object value,
                    @Exclusive @Cached GilNode gil,
                    @Cached IsBuiltinClassProfile errorProfile,
                    @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib,
                    @Exclusive @Cached PForeignToPTypeNode convertNodeKey,
                    @Exclusive @Cached PForeignToPTypeNode convertNodeValue) throws UnsupportedTypeException {
        boolean mustRelease = gil.acquire();
        Object pKey = convertNodeKey.executeConvert(key);
        try {
            lib.setItem(self.getDictStorage(), pKey, convertNodeValue.executeConvert(value));
        } catch (PException e) {
            e.expect(PythonBuiltinClassType.TypeError, errorProfile);
            throw UnsupportedTypeException.create(new Object[]{pKey}, "keys for Python arrays must be hashable");
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage(limit = "2")
    static void removeHashEntry(PDict self, Object key,
                    @Exclusive @Cached GilNode gil,
                    @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib,
                    @Exclusive @Cached PForeignToPTypeNode convertNode) throws UnknownKeyException {
        boolean mustRelease = gil.acquire();
        try {
            Object pKey = convertNode.executeConvert(key);
            if (!lib.hasKey(self.getDictStorage(), pKey)) {
                throw UnknownKeyException.create(key);
            }
            lib.delItem(self.getDictStorage(), pKey);
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    static Object getHashEntriesIterator(PDict self,
                    @Exclusive @Cached GilNode gil,
                    @Shared("getIter") @Cached PyObjectGetIter getIter,
                    @Shared("callMethod") @Cached PyObjectCallMethodObjArgs callMethod) {
        boolean mustRelease = gil.acquire();
        try {
            Object dictItems = callMethod.execute(null, self, T_ITEMS);
            return getIter.execute(null, dictItems);
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    static Object getHashKeysIterator(PDict self,
                    @Exclusive @Cached GilNode gil,
                    @Shared("getIter") @Cached PyObjectGetIter getIter,
                    @Shared("callMethod") @Cached PyObjectCallMethodObjArgs callMethod) {
        boolean mustRelease = gil.acquire();
        try {
            Object dictKeys = callMethod.execute(null, self, T_KEYS);
            return getIter.execute(null, dictKeys);
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    static Object getHashValuesIterator(PDict self,
                    @Exclusive @Cached GilNode gil,
                    @Shared("getIter") @Cached PyObjectGetIter getIter,
                    @Shared("callMethod") @Cached PyObjectCallMethodObjArgs callMethod) {
        boolean mustRelease = gil.acquire();
        try {
            Object dictValues = callMethod.execute(null, self, T_VALUES);
            return getIter.execute(null, dictValues);
        } finally {
            gil.release(mustRelease);
        }
    }
}
