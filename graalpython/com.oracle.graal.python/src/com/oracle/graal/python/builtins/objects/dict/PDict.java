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

import static com.oracle.graal.python.nodes.SpecialMethodNames.__LEN__;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage;
import com.oracle.graal.python.builtins.objects.common.EconomicMapStorage;
import com.oracle.graal.python.builtins.objects.common.EmptyStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.common.KeywordsStorage;
import com.oracle.graal.python.builtins.objects.common.PHashingCollection;
import com.oracle.graal.python.builtins.objects.function.PArguments.ThreadState;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.ConditionProfile;

@ExportLibrary(PythonObjectLibrary.class)
public final class PDict extends PHashingCollection {

    protected HashingStorage dictStorage;

    public PDict() {
        this(PythonBuiltinClassType.PDict, PythonBuiltinClassType.PDict.getInstanceShape());
    }

    public PDict(Object cls, Shape instanceShape, HashingStorage dictStorage) {
        super(cls, instanceShape);
        this.dictStorage = dictStorage;
    }

    public PDict(Object cls, Shape instanceShape) {
        super(cls, instanceShape);
        this.dictStorage = new EmptyStorage();
    }

    public PDict(Object cls, Shape instanceShape, PKeyword[] keywords) {
        super(cls, instanceShape);
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
        static int doBuiltin(PDict self, ThreadState state,
                        @CachedLibrary("self.getDictStorage()") HashingStorageLibrary storageLib,
                        @SuppressWarnings("unused") @Cached IsBuiltinClassProfile profile,
                        @SuppressWarnings("unused") @Cached LookupInheritedAttributeNode.Dynamic lookupSelf,
                        @SuppressWarnings("unused") @Cached LookupAttributeInMRONode.Dynamic lookupDict) {
            return storageLib.lengthWithState(self.dictStorage, state);
        }

        @Specialization(replaces = "doBuiltin")
        static int doSubclassed(PDict self, ThreadState state,
                        @CachedLibrary("self") PythonObjectLibrary plib,
                        @Shared("methodLib") @CachedLibrary(limit = "2") PythonObjectLibrary methodLib,
                        @Shared("gotState") @Cached ConditionProfile gotState,
                        @Exclusive @Cached ConditionProfile hasLen,
                        @Exclusive @Cached ConditionProfile ltZero,
                        @Shared("raise") @Cached PRaiseNode raiseNode,
                        @Exclusive @CachedLibrary(limit = "1") PythonObjectLibrary lib) {
            // call the generic implementation in the superclass
            return self.lengthWithState(state, plib, methodLib, gotState, hasLen, ltZero, raiseNode, lib);
        }
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return "PDict<" + dictStorage.getClass().getSimpleName() + ">";
    }
}
