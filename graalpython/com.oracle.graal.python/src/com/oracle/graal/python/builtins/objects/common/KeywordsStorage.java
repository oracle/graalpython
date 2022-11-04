/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.util.Iterator;
import java.util.NoSuchElementException;

import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary.ForEachNode;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary.HashingStorageIterable;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageSetItem;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.SpecializedSetStringKey;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PArguments.ThreadState;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.lib.PyObjectHashNode;
import com.oracle.graal.python.lib.PyObjectRichCompareBool;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@ExportLibrary(HashingStorageLibrary.class)
public class KeywordsStorage extends HashingStorage {

    final PKeyword[] keywords;

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
    protected int findCachedStringKey(TruffleString key, int len, TruffleString.EqualNode equalNode) {
        for (int i = 0; i < len; i++) {
            if (equalNode.execute(keywords[i].getName(), key, TS_ENCODING)) {
                return i;
            }
        }
        return -1;
    }

    protected int findStringKey(TruffleString key, TruffleString.EqualNode equalNode) {
        return findStringKey(keywords, key, equalNode);
    }

    public static int findStringKey(PKeyword[] keywords, TruffleString key, TruffleString.EqualNode equalNode) {
        for (int i = 0; i < keywords.length; i++) {
            if (equalNode.execute(keywords[i].getName(), key, TS_ENCODING)) {
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
        static boolean cached(KeywordsStorage self, TruffleString key, ThreadState state,
                        @Exclusive @Cached("self.length()") int cachedLen,
                        @Shared("tsEqual") @Cached TruffleString.EqualNode equalNode) {
            return self.findCachedStringKey(key, cachedLen, equalNode) != -1;
        }

        @Specialization(replaces = "cached")
        static boolean string(KeywordsStorage self, TruffleString key, ThreadState state,
                        @Shared("tsEqual") @Cached TruffleString.EqualNode equalNode) {
            return self.findStringKey(key, equalNode) != -1;
        }

        @Specialization(guards = "isBuiltinString(key, profile)", limit = "1")
        static boolean pstring(KeywordsStorage self, PString key, ThreadState state,
                        @Cached CastToTruffleStringNode castToTruffleStringNode,
                        @Shared("builtinProfile") @Cached IsBuiltinClassProfile profile,
                        @Shared("tsEqual") @Cached TruffleString.EqualNode equalNode) {
            return string(self, castToTruffleStringNode.execute(key), state, equalNode);
        }

        @Specialization(guards = "!isBuiltinString(key, profile)", limit = "1")
        static boolean notString(KeywordsStorage self, Object key, ThreadState state,
                        @Shared("builtinProfile") @Cached IsBuiltinClassProfile profile,
                        @CachedLibrary("self") HashingStorageLibrary lib) {
            return lib.getItemWithState(self, key, state) != null;
        }
    }

    @ImportStatic(PGuards.class)
    @GenerateUncached
    public abstract static class GetItemNode extends Node {
        /**
         * For builtin strings the {@code keyHash} value is ignored and can be garbage. If the
         * {@code keyHash} is equal to {@code -1} it will be computed for non-string keys.
         */
        public abstract Object execute(Frame frame, KeywordsStorage self, Object key, long hash);

        @Specialization(guards = {"self.length() == cachedLen", "cachedLen < 6"}, limit = "1")
        static Object cached(KeywordsStorage self, TruffleString key, @SuppressWarnings("unused") long hash,
                        @SuppressWarnings("unused") @Exclusive @Cached("self.length()") int cachedLen,
                        @Shared("tsEqual") @Cached TruffleString.EqualNode equalNode) {
            final int idx = self.findCachedStringKey(key, cachedLen, equalNode);
            return idx != -1 ? self.keywords[idx].getValue() : null;
        }

        @Specialization(replaces = "cached")
        static Object string(KeywordsStorage self, TruffleString key, @SuppressWarnings("unused") long hash,
                        @Shared("tsEqual") @Cached TruffleString.EqualNode equalNode) {
            final int idx = self.findStringKey(key, equalNode);
            return idx != -1 ? self.keywords[idx].getValue() : null;
        }

        @Specialization(guards = "isBuiltinString(key, profile)", limit = "1")
        static Object pstring(KeywordsStorage self, PString key, @SuppressWarnings("unused") long hash,
                        @Cached CastToTruffleStringNode castToTruffleStringNode,
                        @SuppressWarnings("unused") @Shared("builtinProfile") @Cached IsBuiltinClassProfile profile,
                        @Shared("tsEqual") @Cached TruffleString.EqualNode equalNode) {
            return string(self, castToTruffleStringNode.execute(key), -1, equalNode);
        }

        @Specialization(guards = "!isBuiltinString(key, profile)", limit = "1")
        static Object notString(Frame frame, KeywordsStorage self, Object key, long hashIn,
                        @SuppressWarnings("unused") @Shared("builtinProfile") @Cached IsBuiltinClassProfile profile,
                        @Cached PyObjectHashNode hashNode,
                        @Cached PyObjectRichCompareBool.EqNode eqNode) {
            long hash = hashIn == -1 ? hashNode.execute(frame, key) : hashIn;
            for (int i = 0; i < self.keywords.length; i++) {
                TruffleString currentKey = self.keywords[i].getName();
                long keyHash = hashNode.execute(frame, currentKey);
                if (keyHash == hash && eqNode.execute(frame, key, currentKey)) {
                    return self.keywords[i].getValue();
                }
            }
            return null;
        }
    }

    @ExportMessage
    public HashingStorage delItemWithState(Object key, ThreadState state,
                    @CachedLibrary(limit = "2") HashingStorageLibrary lib,
                    @Cached ConditionProfile gotState) {
        HashingStorage newStore = generalize(lib, true, length() - 1);
        if (gotState.profile(state != null)) {
            return lib.delItemWithState(newStore, key, state);
        } else {
            return lib.delItem(newStore, key);
        }
    }

    private HashingStorage generalize(HashingStorageLibrary lib, boolean isStringKey, int expectedLength) {
        HashingStorage newStore = PDict.createNewStorage(isStringKey, expectedLength);
        newStore = lib.addAllToOther(this, newStore);
        return newStore;
    }

    void addAllTo(HashingStorage storage, SpecializedSetStringKey putNode) {
        for (PKeyword entry : keywords) {
            putNode.execute(storage, entry.getName(), entry.getValue());
        }
    }

    @ExportMessage
    static class ForEachUntyped {
        @Specialization(guards = {"self.length() == cachedLen", "cachedLen <= 32"}, limit = "1")
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
        @Specialization(guards = {"self.length() == cachedLen", "cachedLen <= 32"}, limit = "1")
        @ExplodeLoop
        static HashingStorage cached(KeywordsStorage self, HashingStorage other,
                        @Exclusive @Cached("self.length()") int cachedLen,
                        @Exclusive @Cached HashingStorageSetItem setItem) {
            HashingStorage result = other;
            for (int i = 0; i < cachedLen; i++) {
                PKeyword entry = self.keywords[i];
                result = setItem.execute(null, result, entry.getName(), entry.getValue());
            }
            return result;
        }

        @Specialization(replaces = "cached")
        static HashingStorage generic(KeywordsStorage self, HashingStorage other,
                        @Exclusive @Cached HashingStorageSetItem setItem) {
            HashingStorage result = other;
            for (int i = 0; i < self.length(); i++) {
                PKeyword entry = self.keywords[i];
                result = setItem.execute(null, result, entry.getName(), entry.getValue());
            }
            return result;
        }
    }

    public HashingStorage copy() {
        // this storage is unmodifiable; just reuse it
        return this;
    }

    @ExportMessage
    @Override
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

        @Override
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
