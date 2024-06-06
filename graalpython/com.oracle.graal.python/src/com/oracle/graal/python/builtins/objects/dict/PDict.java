/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates.
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
import static com.oracle.graal.python.util.PythonUtils.builtinClassToType;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.common.EconomicMapStorage;
import com.oracle.graal.python.builtins.objects.common.EmptyStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageAddAllToOther;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageDelItem;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetItem;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageLen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageSetItem;
import com.oracle.graal.python.builtins.objects.common.KeywordsStorage;
import com.oracle.graal.python.builtins.objects.common.PHashingCollection;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectHashNode;
import com.oracle.graal.python.nodes.interop.PForeignToPTypeNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownKeyException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.Shape;

@ExportLibrary(InteropLibrary.class)
public class PDict extends PHashingCollection {

    public PDict(Object cls, Shape instanceShape, HashingStorage dictStorage) {
        super(builtinClassToType(cls), instanceShape, dictStorage);
    }

    public PDict(Object cls, Shape instanceShape) {
        this(cls, instanceShape, EmptyStorage.INSTANCE);
    }

    public PDict(Object cls, Shape instanceShape, PKeyword[] keywords) {
        this(cls, instanceShape, (keywords != null) ? KeywordsStorage.create(keywords) : EmptyStorage.INSTANCE);
    }

    public Object getItem(Object key) {
        return HashingStorageGetItem.executeUncached(storage, key);
    }

    public void setItem(Object key, Object value) {
        storage = HashingStorageSetItem.executeUncached(storage, key, value);
    }

    public void delItem(Object key) {
        HashingStorageDelItem.executeUncached(storage, key, this);
    }

    public static HashingStorage createNewStorage(int expectedSize) {
        HashingStorage newDictStorage;
        if (expectedSize == 0) {
            newDictStorage = EmptyStorage.INSTANCE;
        } else {
            newDictStorage = EconomicMapStorage.create(expectedSize);
        }
        return newDictStorage;
    }

    public void update(PDict other) {
        HashingStorageAddAllToOther.executeUncached(other.getDictStorage(), this);
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

    @ExportMessage
    static long getHashSize(PDict self,
                    @Bind("$node") Node inliningTarget,
                    @Exclusive @Cached GilNode gil,
                    @Cached HashingStorageLen lenNode) {
        boolean mustRelease = gil.acquire();
        try {
            return lenNode.execute(inliningTarget, self.getDictStorage());
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @ExportMessage(name = "isHashEntryModifiable")
    @ExportMessage(name = "isHashEntryRemovable")
    static boolean isHashEntryReadable(PDict self, Object key,
                    @Bind("$node") Node inliningTarget,
                    @Exclusive @Cached GilNode gil,
                    @Shared("getItem") @Cached HashingStorageGetItem getItem,
                    @Exclusive @Cached PForeignToPTypeNode convertNode) {
        boolean mustRelease = gil.acquire();
        try {
            return getItem.hasKey(null, inliningTarget, self.getDictStorage(), convertNode.executeConvert(key));
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    static Object readHashValue(PDict self, Object key,
                    @Bind("$node") Node inliningTarget,
                    @Exclusive @Cached GilNode gil,
                    @Exclusive @Cached HashingStorageGetItem getItem,
                    @Exclusive @Cached PForeignToPTypeNode convertNode) throws UnknownKeyException {
        Object value = null;
        boolean mustRelease = gil.acquire();
        try {
            value = getItem.execute(null, inliningTarget, self.getDictStorage(), convertNode.executeConvert(key));
        } finally {
            gil.release(mustRelease);
        }
        if (value == null) {
            throw UnknownKeyException.create(key);
        } else {
            return value;
        }
    }

    @ExportMessage
    static boolean isHashEntryInsertable(PDict self, Object key,
                    @Bind("$node") Node inliningTarget,
                    @Exclusive @Cached GilNode gil,
                    @Cached PyObjectHashNode hashNode,
                    @Exclusive @Cached HashingStorageGetItem getItem,
                    @Exclusive @Cached PForeignToPTypeNode convertNode) {
        boolean mustRelease = gil.acquire();
        try {
            Object pKey = convertNode.executeConvert(key);
            if (getItem.hasKey(null, inliningTarget, self.getDictStorage(), pKey)) {
                return false;
            } else {
                // we can only insert hashable types
                try {
                    hashNode.execute(null, inliningTarget, pKey);
                } catch (AbstractTruffleException e) {
                    return false;
                }
                return true;
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    static void writeHashEntry(PDict self, Object key, Object value,
                    @Bind("this") Node inliningTarget,
                    @Exclusive @Cached GilNode gil,
                    @Cached IsBuiltinObjectProfile errorProfile,
                    @Cached HashingStorageSetItem setItem,
                    @Exclusive @Cached PForeignToPTypeNode convertNodeKey,
                    @Exclusive @Cached PForeignToPTypeNode convertNodeValue) throws UnsupportedTypeException {
        boolean mustRelease = gil.acquire();
        Object pKey = convertNodeKey.executeConvert(key);
        try {
            HashingStorage newStorage = setItem.execute(null, inliningTarget, self.getDictStorage(), pKey, convertNodeValue.executeConvert(value));
            self.setDictStorage(newStorage);
        } catch (PException e) {
            e.expect(inliningTarget, PythonBuiltinClassType.TypeError, errorProfile);
            throw UnsupportedTypeException.create(new Object[]{pKey}, "keys for Python arrays must be hashable");
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    static void removeHashEntry(PDict self, Object key,
                    @Bind("$node") Node inliningTarget,
                    @Exclusive @Cached GilNode gil,
                    @Cached HashingStorageDelItem delItem,
                    @Exclusive @Cached PForeignToPTypeNode convertNode) throws UnknownKeyException {
        boolean mustRelease = gil.acquire();
        try {
            Object pKey = convertNode.executeConvert(key);
            Object found = delItem.executePop(null, inliningTarget, self.getDictStorage(), pKey, self);
            if (found == null) {
                throw UnknownKeyException.create(key);
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    static Object getHashEntriesIterator(PDict self,
                    @Bind("$node") Node inliningTarget,
                    @Exclusive @Cached GilNode gil,
                    @Shared("getIter") @Cached PyObjectGetIter getIter,
                    @Shared("callMethod") @Cached PyObjectCallMethodObjArgs callMethod) {
        boolean mustRelease = gil.acquire();
        try {
            Object dictItems = callMethod.execute(null, inliningTarget, self, T_ITEMS);
            return getIter.execute(null, inliningTarget, dictItems);
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    static Object getHashKeysIterator(PDict self,
                    @Bind("$node") Node inliningTarget,
                    @Exclusive @Cached GilNode gil,
                    @Shared("getIter") @Cached PyObjectGetIter getIter,
                    @Shared("callMethod") @Cached PyObjectCallMethodObjArgs callMethod) {
        boolean mustRelease = gil.acquire();
        try {
            Object dictKeys = callMethod.execute(null, inliningTarget, self, T_KEYS);
            return getIter.execute(null, inliningTarget, dictKeys);
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    static Object getHashValuesIterator(PDict self,
                    @Bind("$node") Node inliningTarget,
                    @Exclusive @Cached GilNode gil,
                    @Shared("getIter") @Cached PyObjectGetIter getIter,
                    @Shared("callMethod") @Cached PyObjectCallMethodObjArgs callMethod) {
        boolean mustRelease = gil.acquire();
        try {
            Object dictValues = callMethod.execute(null, inliningTarget, self, T_VALUES);
            return getIter.execute(null, inliningTarget, dictValues);
        } finally {
            gil.release(mustRelease);
        }
    }
}
