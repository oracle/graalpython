/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates.
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

import static com.oracle.graal.python.nodes.SpecialMethodNames.ITEMS;
import static com.oracle.graal.python.nodes.SpecialMethodNames.KEYS;
import static com.oracle.graal.python.nodes.SpecialMethodNames.VALUES;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LEN__;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage;
import com.oracle.graal.python.builtins.objects.common.EconomicMapStorage;
import com.oracle.graal.python.builtins.objects.common.EmptyStorage;
import com.oracle.graal.python.builtins.objects.common.HashMapStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.common.KeywordsStorage;
import com.oracle.graal.python.builtins.objects.common.PHashingCollection;
import com.oracle.graal.python.builtins.objects.function.PArguments.ThreadState;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.lib.PyNumberIndexNode;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.util.CastToJavaLongLossyNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownKeyException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

@ExportLibrary(InteropLibrary.class)
@ExportLibrary(PythonObjectLibrary.class)
public final class PDict extends PHashingCollection {

    public PDict(PythonLanguage lang) {
        this(PythonBuiltinClassType.PDict, PythonBuiltinClassType.PDict.getInstanceShape(lang));
    }

    public PDict(Object cls, Shape instanceShape, HashingStorage dictStorage) {
        super(cls, instanceShape, dictStorage);
    }

    public PDict(Object cls, Shape instanceShape) {
        super(cls, instanceShape, EmptyStorage.INSTANCE);
    }

    public PDict(Object cls, Shape instanceShape, PKeyword[] keywords) {
        super(cls, instanceShape, (keywords != null) ? KeywordsStorage.create(keywords) : EmptyStorage.INSTANCE);
    }

    public Object getItem(Object key) {
        return HashingStorageLibrary.getUncached().getItem(storage, key);
    }

    public void setItem(Object key, Object value) {
        storage = HashingStorageLibrary.getUncached().setItem(storage, key, value);
    }

    public static HashingStorage createNewStorage(PythonLanguage lang, boolean isStringKey, int expectedSize) {
        HashingStorage newDictStorage;
        if (expectedSize == 0) {
            newDictStorage = EmptyStorage.INSTANCE;
        } else if (isStringKey) {
            if (expectedSize < DynamicObjectStorage.SIZE_THRESHOLD) {
                newDictStorage = new DynamicObjectStorage(lang);
            } else {
                newDictStorage = new HashMapStorage(expectedSize);
            }
        } else {
            newDictStorage = EconomicMapStorage.create(expectedSize);
        }
        return newDictStorage;
    }

    public void update(PDict other) {
        storage = HashingStorageLibrary.getUncached().addAllToOther(other.getDictStorage(), storage);
    }

    @ExportMessage
    static class LengthWithState {

        static boolean isBuiltin(PDict self, IsBuiltinClassProfile p) {
            return p.profileIsAnyBuiltinObject(self);
        }

        static boolean hasBuiltinLen(PDict self, LookupInheritedAttributeNode.Dynamic lookupSelf, LookupAttributeInMRONode.Dynamic lookupDict) {
            return lookupSelf.execute(self, __LEN__) == lookupDict.execute(PythonBuiltinClassType.PDict, __LEN__);
        }

        @Specialization(guards = {
                        "isBuiltin(self, profile) || hasBuiltinLen(self, lookupSelf, lookupDict)"
        }, limit = "1")
        static int doBuiltin(PDict self, @SuppressWarnings("unused") ThreadState state,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary storageLib,
                        @SuppressWarnings("unused") @Cached IsBuiltinClassProfile profile,
                        @SuppressWarnings("unused") @Cached LookupInheritedAttributeNode.Dynamic lookupSelf,
                        @SuppressWarnings("unused") @Cached LookupAttributeInMRONode.Dynamic lookupDict) {
            return storageLib.length(self.storage);
        }

        @Specialization(replaces = "doBuiltin")
        static int doSubclassed(PDict self, ThreadState state,
                        @CachedLibrary("self") PythonObjectLibrary plib,
                        @Shared("methodLib") @CachedLibrary(limit = "2") PythonObjectLibrary methodLib,
                        @Exclusive @Cached ConditionProfile hasLen,
                        @Exclusive @Cached ConditionProfile ltZero,
                        @Shared("raise") @Cached PRaiseNode raiseNode,
                        @Shared("indexNode") @Cached PyNumberIndexNode indexNode,
                        @Shared("gotState") @Cached ConditionProfile gotState,
                        @Exclusive @Cached CastToJavaLongLossyNode toLong,
                        @Exclusive @Cached ConditionProfile ignoreOverflow,
                        @Exclusive @Cached BranchProfile overflow) {
            // call the generic implementation in the superclass
            return self.lengthWithState(state, plib, methodLib, hasLen, ltZero, raiseNode, indexNode, gotState, toLong, ignoreOverflow, overflow);
        }
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
                    @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
        return lib.length(self.getDictStorage());
    }

    @ExportMessage(limit = "2")
    @ExportMessage(name = "isHashEntryModifiable", limit = "2")
    @ExportMessage(name = "isHashEntryRemovable", limit = "2")
    static boolean isHashEntryReadable(PDict self, Object key,
                    @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
        return lib.hasKey(self.getDictStorage(), key);
    }

    @ExportMessage(limit = "2")
    static Object readHashValue(PDict self, Object key,
                    @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) throws UnknownKeyException {
        Object value = lib.getItem(self.getDictStorage(), key);
        if (value == null) {
            throw UnknownKeyException.create(key);
        } else {
            return value;
        }
    }

    @ExportMessage(limit = "3")
    static boolean isHashEntryInsertable(PDict self, Object key,
                    @CachedLibrary("key") PythonObjectLibrary keyLib,
                    @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) {
        if (lib.hasKey(self.getDictStorage(), key)) {
            return false;
        } else {
            // we can only insert hashable types
            try {
                keyLib.hash(key);
            } catch (AbstractTruffleException e) {
                return false;
            }
            return true;
        }
    }

    @ExportMessage(limit = "2")
    static void writeHashEntry(PDict self, Object key, Object value,
                    @Cached IsBuiltinClassProfile errorProfile,
                    @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) throws UnsupportedTypeException {
        try {
            lib.setItem(self.getDictStorage(), key, value);
        } catch (PException e) {
            e.expect(PythonBuiltinClassType.TypeError, errorProfile);
            throw UnsupportedTypeException.create(new Object[] { key }, "keys for Python arrays must be hashable");
        }
    }

    @ExportMessage(limit = "2")
    static void removeHashEntry(PDict self, Object key,
                    @CachedLibrary("self.getDictStorage()") HashingStorageLibrary lib) throws UnknownKeyException {
        if (!isHashEntryReadable(self, key, lib)) {
            throw UnknownKeyException.create(key);
        }
        lib.delItem(self.getDictStorage(), key);
    }

    @ExportMessage
    static Object getHashEntriesIterator(PDict self,
                    @Shared("iterLib") @CachedLibrary(limit = "2") PythonObjectLibrary lib) {
        Object dictItems = lib.lookupAndCallSpecialMethod(self, null, ITEMS);
        return lib.lookupAndCallSpecialMethod(dictItems, null, __ITER__);
    }

    @ExportMessage
    static Object getHashKeysIterator(PDict self,
                    @Shared("iterLib") @CachedLibrary(limit = "2") PythonObjectLibrary lib) {
        Object dictItems = lib.lookupAndCallSpecialMethod(self, null, KEYS);
        return lib.lookupAndCallSpecialMethod(dictItems, null, __ITER__);
    }

    @ExportMessage
    static Object getHashValuesIterator(PDict self,
                    @Shared("iterLib") @CachedLibrary(limit = "2") PythonObjectLibrary lib) {
        Object dictItems = lib.lookupAndCallSpecialMethod(self, null, VALUES);
        return lib.lookupAndCallSpecialMethod(dictItems, null, __ITER__);
    }
}
