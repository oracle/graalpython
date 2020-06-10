/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.builtins.objects.common;

import java.util.Iterator;
import java.util.NoSuchElementException;

import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary.ForEachNode;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary.HashingStorageIterable;
import com.oracle.graal.python.builtins.objects.function.PArguments.ThreadState;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.profiles.ConditionProfile;

@ExportLibrary(HashingStorageLibrary.class)
public class KeywordsStorage extends HashingStorage {

    private final PKeyword[] keywords;

    protected KeywordsStorage(PKeyword[] keywords) {
        this.keywords = keywords;
    }

    public PKeyword[] getStore() {
        return keywords;
    }

    @Override
    @ExportMessage
    public int length() {
        return keywords.length;
    }

    @ExplodeLoop(kind = LoopExplosionKind.FULL_UNROLL_UNTIL_RETURN)
    protected int findCachedStringKey(String key, int len) {
        for (int i = 0; i < len; i++) {
            if (keywords[i].getName().equals(key)) {
                return i;
            }
        }
        return -1;
    }

    protected int findStringKey(String key) {
        for (int i = 0; i < keywords.length; i++) {
            if (keywords[i].getName().equals(key)) {
                return i;
            }
        }
        return -1;
    }

    @SuppressWarnings("unused")
    @ExportMessage
    @ImportStatic(PGuards.class)
    static class HasKeyWithState {

        @Specialization(guards = {"self.length() == cachedLen", "cachedLen < 6"}, limit = "1")
        static boolean cached(KeywordsStorage self, String key, ThreadState state,
                        @Exclusive @Cached("self.length()") int cachedLen) {
            return self.findCachedStringKey(key, cachedLen) != -1;
        }

        @Specialization(replaces = "cached")
        static boolean string(KeywordsStorage self, String key, ThreadState state) {
            return self.findStringKey(key) != -1;
        }

        @Specialization(guards = "isBuiltinString(key, profile)", limit = "1")
        static boolean pstring(KeywordsStorage self, PString key, ThreadState state,
                        @Exclusive @Cached IsBuiltinClassProfile profile) {
            return string(self, key.getValue(), state);
        }

        @Specialization(guards = "!isBuiltinString(key, profile)")
        static boolean notString(KeywordsStorage self, Object key, ThreadState state,
                        @Exclusive @Cached IsBuiltinClassProfile profile,
                        @CachedLibrary("self") HashingStorageLibrary lib) {
            return lib.getItemWithState(self, key, state) != null;
        }
    }

    @ExportMessage(limit = "1")
    @ImportStatic(PGuards.class)
    public static class GetItemWithState {
        @Specialization(guards = {"self.length() == cachedLen", "cachedLen < 6"}, limit = "1")
        static Object cached(KeywordsStorage self, String key, @SuppressWarnings("unused") ThreadState state,
                        @SuppressWarnings("unused") @Exclusive @Cached("self.length()") int cachedLen) {
            final int idx = self.findCachedStringKey(key, cachedLen);
            return idx != -1 ? self.keywords[idx].getValue() : null;
        }

        @Specialization(replaces = "cached")
        static Object string(KeywordsStorage self, String key, @SuppressWarnings("unused") ThreadState state) {
            final int idx = self.findStringKey(key);
            return idx != -1 ? self.keywords[idx].getValue() : null;
        }

        @Specialization(guards = "isBuiltinString(key, profile)", limit = "1")
        static Object pstring(KeywordsStorage self, PString key, ThreadState state,
                        @SuppressWarnings("unused") @Exclusive @Cached IsBuiltinClassProfile profile) {
            return string(self, key.getValue(), state);
        }

        @Specialization(guards = "!isBuiltinString(key, profile)")
        static Object notString(KeywordsStorage self, Object key, ThreadState state,
                        @SuppressWarnings("unused") @Cached IsBuiltinClassProfile profile,
                        @CachedLibrary(limit = "2") PythonObjectLibrary lib,
                        @Exclusive @Cached("createBinaryProfile()") ConditionProfile gotState) {
            long hash = self.getHashWithState(key, lib, state, gotState);
            for (int i = 0; i < self.keywords.length; i++) {
                String currentKey = self.keywords[i].getName();
                long keyHash;
                if (gotState.profile(state != null)) {
                    keyHash = lib.hashWithState(currentKey, state);
                    if (keyHash == hash && lib.equalsWithState(key, currentKey, lib, state)) {
                        return self.keywords[i].getValue();
                    }
                } else {
                    keyHash = lib.hash(currentKey);
                    if (keyHash == hash && lib.equals(key, currentKey, lib)) {
                        return self.keywords[i].getValue();
                    }
                }
            }
            return null;
        }
    }

    @ExportMessage
    public HashingStorage setItemWithState(Object key, Object value, ThreadState state,
                    @CachedLibrary(limit = "2") HashingStorageLibrary lib,
                    @Exclusive @Cached("createBinaryProfile()") ConditionProfile gotState) {
        HashingStorage newStore = generalize(lib);
        if (gotState.profile(state != null)) {
            return lib.setItemWithState(newStore, key, value, state);
        } else {
            return lib.setItem(newStore, key, value);
        }
    }

    @ExportMessage
    public HashingStorage delItemWithState(Object key, ThreadState state,
                    @CachedLibrary(limit = "1") HashingStorageLibrary lib,
                    @Exclusive @Cached("createBinaryProfile()") ConditionProfile gotState) {
        HashingStorage newStore = generalize(lib);
        if (gotState.profile(state != null)) {
            return lib.delItemWithState(newStore, key, state);
        } else {
            return lib.delItem(newStore, key);
        }
    }

    private HashingStorage generalize(HashingStorageLibrary lib) {
        HashingStorage newStore = EconomicMapStorage.create(length());
        newStore = lib.addAllToOther(this, newStore);
        return newStore;
    }

    @ExportMessage
    static class ForEachUntyped {
        @Specialization(guards = "self.length() == cachedLen", limit = "1")
        @ExplodeLoop
        static Object cached(KeywordsStorage self, ForEachNode<Object> node, Object arg,
                        @Exclusive @Cached("self.length()") int cachedLen) {
            Object result = arg;
            for (int i = 0; i < cachedLen; i++) {
                PKeyword entry = self.keywords[i];
                result = node.execute(entry.getName(), result);
            }
            return result;
        }

        @Specialization(replaces = "cached")
        static Object generic(KeywordsStorage self, ForEachNode<Object> node, Object arg) {
            Object result = arg;
            for (int i = 0; i < self.length(); i++) {
                PKeyword entry = self.keywords[i];
                result = node.execute(entry.getName(), result);
            }
            return result;
        }
    }

    @ExportMessage
    public static class AddAllToOther {
        @Specialization(guards = "self.length() == cachedLen", limit = "1")
        @ExplodeLoop
        static HashingStorage cached(KeywordsStorage self, HashingStorage other,
                        @Exclusive @Cached("self.length()") int cachedLen,
                        @CachedLibrary(limit = "2") HashingStorageLibrary lib) {
            HashingStorage result = other;
            for (int i = 0; i < cachedLen; i++) {
                PKeyword entry = self.keywords[i];
                result = lib.setItem(result, entry.getName(), entry.getValue());
            }
            return result;
        }

        @Specialization(replaces = "cached")
        static HashingStorage generic(KeywordsStorage self, HashingStorage other,
                        @CachedLibrary(limit = "1") HashingStorageLibrary lib) {
            HashingStorage result = other;
            for (int i = 0; i < self.length(); i++) {
                PKeyword entry = self.keywords[i];
                result = lib.setItem(result, entry.getName(), entry.getValue());
            }
            return result;
        }
    }

    @Override
    @ExportMessage
    public HashingStorage clear() {
        return EconomicMapStorage.create();
    }

    @Override
    @ExportMessage
    public HashingStorage copy() {
        // this storage is unmodifiable; just reuse it
        return this;
    }

    @Override
    @ExportMessage
    public HashingStorageIterable<Object> keys() {
        return new HashingStorageIterable<>(new KeysIterator(this));
    }

    @ExportMessage
    @Override
    public HashingStorageIterable<Object> reverseKeys() {
        return new HashingStorageIterable<>(new ReverseKeysIterator(this));
    }

    private abstract static class AbstractKeysIterator implements Iterator<Object> {
        protected final KeywordsStorage storage;
        protected int index;

        public AbstractKeysIterator(KeywordsStorage keywordsStorage, int initialIndex) {
            this.index = initialIndex;
            this.storage = keywordsStorage;
        }

        public abstract void nextIndex();

        public Object next() {
            if (hasNext()) {
                Object result = storage.keywords[index].getName();
                nextIndex();
                return result;
            } else {
                throw new NoSuchElementException();
            }
        }
    }

    private static final class KeysIterator extends AbstractKeysIterator {
        public KeysIterator(KeywordsStorage keywordsStorage) {
            super(keywordsStorage, 0);
        }

        @Override
        public boolean hasNext() {
            return index < storage.length();
        }

        @Override
        public void nextIndex() {
            index += 1;
        }
    }

    private static final class ReverseKeysIterator extends AbstractKeysIterator {
        public ReverseKeysIterator(KeywordsStorage keywordsStorage) {
            super(keywordsStorage, keywordsStorage.keywords.length - 1);
        }

        @Override
        public boolean hasNext() {
            return index >= 0;
        }

        @Override
        public void nextIndex() {
            index -= 1;
        }
    }

    public static KeywordsStorage create(PKeyword[] keywords) {
        return new KeywordsStorage(keywords);
    }
}
