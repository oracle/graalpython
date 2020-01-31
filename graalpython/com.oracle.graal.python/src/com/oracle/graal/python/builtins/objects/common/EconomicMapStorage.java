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

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.MapCursor;

import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary.InjectIntoNode;
import com.oracle.graal.python.builtins.objects.function.PArguments.ThreadState;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.str.LazyString;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.object.GetLazyClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;

@ExportLibrary(HashingStorageLibrary.class)
public class EconomicMapStorage extends HashingStorage {

    public static final Equivalence DEFAULT_EQIVALENCE = new Equivalence() {

        @Override
        public int hashCode(Object o) {
            return Long.hashCode(o.hashCode());
        }

        @Override
        public boolean equals(Object a, Object b) {
            return (a == b) || (a != null && objectEquals(a, b));
        }

        @TruffleBoundary
        private boolean objectEquals(Object a, Object b) {
            return a.equals(b);
        }
    };

    public static EconomicMapStorage create() {
        return new EconomicMapStorage();
    }

    public static EconomicMapStorage create(int initialCapacity) {
        return new EconomicMapStorage(initialCapacity);
    }

    public static EconomicMapStorage create(EconomicMap<? extends Object, Object> map) {
        return new EconomicMapStorage(map);
    }

    static final class DictKey {
        final Object value;
        final long hash;

        DictKey(Object value, long hash) {
            this.value = value;
            this.hash = hash;
        }

        @Override
        public boolean equals(Object obj) {
            // Comparison as per CPython's dictobject.c#lookdict function. First
            // check if the keys are identical, then check if the hashes are the
            // same, and only if they are, also call the comparison function.
            if (obj instanceof DictKey) {
                DictKey other = (DictKey) obj;
                if (value == other.value) {
                    return true;
                } else if (hash == other.hash) {
                    final PythonObjectLibrary lib = PythonObjectLibrary.getUncached();
                    return lib.equals(value, other.value, lib);
                } else {
                    return false;
                }
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return (int) hash;
        }
    }

    private final PEMap<DictKey, Object> map;

    private EconomicMapStorage() {
        this.map = new PEMap<>();
    }

    private EconomicMapStorage(int initialCapacity) {
        this.map = new PEMap<>(initialCapacity);
    }

    private EconomicMapStorage(EconomicMapStorage original) {
        this.map = new PEMap<>(original.map.size());
        this.map.putAll(original.map);
    }

    @TruffleBoundary
    public EconomicMapStorage(EconomicMap<? extends Object, ? extends Object> map) {
        this.map = new PEMap<>(map.size());
        MapCursor<? extends Object, ? extends Object> c = map.getEntries();
        while (c.advance()) {
            Object key = c.getKey();
            assert key instanceof Integer || key instanceof String;
            this.map.put(new DictKey(key, key.hashCode()), c.getValue());
        }
    }

    @Override
    @ExportMessage
    public int length() {
        return map.size();
    }

    public static String toString(PString key, ValueProfile profile) {
        CharSequence profiled = profile.profile(key.getCharSequence());
        if (profiled instanceof String) {
            return (String) profiled;
        } else if (profiled instanceof LazyString) {
            return ((LazyString) profiled).toString();
        }
        return generic(profiled);
    }

    @TruffleBoundary
    private static String generic(CharSequence profiled) {
        return profiled.toString();
    }

    @ExportMessage
    @ImportStatic(PGuards.class)
    static class GetItemWithState {

        @Specialization
        static Object getItemString(EconomicMapStorage self, String key, @SuppressWarnings("unused") ThreadState state) {
            DictKey newKey = new DictKey(key, key.hashCode());
            return self.map.get(newKey);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!isNativeString(key)", "isBuiltinString(key, isBuiltinClassProfile, getClassNode)"})
        static Object getItemPString(EconomicMapStorage self, PString key, @SuppressWarnings("unused") ThreadState state,
                        @Exclusive @Cached("createClassProfile()") ValueProfile profile,
                        @Exclusive @Cached IsBuiltinClassProfile isBuiltinClassProfile,
                        @Exclusive @Cached GetLazyClassNode getClassNode) {
            final String k = EconomicMapStorage.toString(key, profile);
            DictKey newKey = new DictKey(k, k.hashCode());
            return self.map.get(newKey);
        }

        @Specialization(replaces = "getItemString", limit = "3")
        static Object getItemGeneric(EconomicMapStorage self, Object key, ThreadState state,
                        @CachedLibrary("key") PythonObjectLibrary lib,
                        @Exclusive @Cached("createBinaryProfile()") ConditionProfile gotState) {
            DictKey newKey = new DictKey(key, self.getHashWithState(key, lib, state, gotState));
            return self.map.get(newKey);
        }
    }

    @SuppressWarnings("unused")
    @ExportMessage
    @ImportStatic(PGuards.class)
    static class SetItemWithState {

        @Specialization
        static HashingStorage setItemString(EconomicMapStorage self, String key, Object value, ThreadState state) {
            DictKey newKey = new DictKey(key, key.hashCode());
            self.map.put(newKey, value);
            return self;
        }

        @Specialization(guards = {"!isNativeString(key)", "isBuiltinString(key, isBuiltinClassProfile, getClassNode)"})
        static HashingStorage setItemPString(EconomicMapStorage self, PString key, Object value, ThreadState state,
                        @Exclusive @Cached("createClassProfile()") ValueProfile profile,
                        @Exclusive @Cached IsBuiltinClassProfile isBuiltinClassProfile,
                        @Exclusive @Cached GetLazyClassNode getClassNode) {
            final String k = EconomicMapStorage.toString(key, profile);
            DictKey newKey = new DictKey(k, k.hashCode());
            self.map.put(newKey, value);
            return self;
        }

        @Specialization(replaces = "setItemString", limit = "3")
        static HashingStorage setItemGeneric(EconomicMapStorage self, Object key, Object value, ThreadState state,
                        @CachedLibrary("key") PythonObjectLibrary lib,
                        @Exclusive @Cached("createBinaryProfile()") ConditionProfile gotState) {
            DictKey newKey = new DictKey(key, self.getHashWithState(key, lib, state, gotState));
            self.map.put(newKey, value);
            return self;
        }
    }

    @ExportMessage(limit = "3")
    public HashingStorage delItemWithState(Object key, ThreadState state,
                    @CachedLibrary("key") PythonObjectLibrary lib,
                    @Exclusive @Cached("createBinaryProfile()") ConditionProfile gotState) {
        DictKey newKey = new DictKey(key, getHashWithState(key, lib, state, gotState));
        map.removeKey(newKey);
        return this;
    }

    @Override
    @ExportMessage
    @TruffleBoundary
    public HashingStorage[] injectInto(HashingStorage[] firstValue, InjectIntoNode node) {
        HashingStorage[] result = firstValue;
        MapCursor<DictKey, Object> cursor = map.getEntries();
        while (cursor.advance()) {
            result = node.execute(result, cursor.getKey().value);
        }
        return result;
    }

    @ExportMessage
    public static class AddAllToOther {
        @Specialization
        static HashingStorage toSameType(EconomicMapStorage self, EconomicMapStorage other) {
            other.map.putAll(self.map);
            return other;
        }

        @Specialization
        @TruffleBoundary
        static HashingStorage generic(EconomicMapStorage self, HashingStorage other,
                        @CachedLibrary(limit = "1") HashingStorageLibrary lib) {
            HashingStorage result = other;
            MapCursor<DictKey, Object> cursor = self.map.getEntries();
            while (cursor.advance()) {
                result = lib.setItem(other, cursor.getKey().value, cursor.getValue());
            }
            return result;
        }
    }

    @Override
    @ExportMessage
    public HashingStorage clear() {
        map.clear();
        return this;
    }

    @Override
    @ExportMessage
    public HashingStorage copy() {
        return new EconomicMapStorage(this);
    }

    @ExportMessage
    public static class EqualsWithState {
        @Specialization
        @TruffleBoundary
        static boolean toSameType(EconomicMapStorage self, EconomicMapStorage other, ThreadState state,
                        @CachedLibrary(limit = "3") PythonObjectLibrary compareLib) {
            if (self.map.size() != other.map.size()) {
                return false;
            }
            MapCursor<DictKey, Object> cursor = self.map.getEntries();
            while (cursor.advance()) {
                Object otherValue = other.map.get(cursor.getKey());
                if (otherValue != null && !compareLib.equalsWithState(otherValue, cursor.getValue(), compareLib, state)) {
                    return false;
                }
            }
            return true;
        }

        @Specialization
        @TruffleBoundary
        static boolean generic(EconomicMapStorage self, HashingStorage other, ThreadState state,
                        @CachedLibrary(limit = "1") HashingStorageLibrary lib,
                        @CachedLibrary(limit = "3") PythonObjectLibrary compareLib) {
            if (self.map.size() != lib.lengthWithState(other, state)) {
                return false;
            }
            MapCursor<DictKey, Object> cursor = self.map.getEntries();
            while (cursor.advance()) {
                Object otherValue = lib.getItemWithState(self, cursor.getKey().value, state);
                if (otherValue != null && !compareLib.equalsWithState(otherValue, cursor.getValue(), compareLib, state)) {
                    return false;
                }
            }
            return true;
        }
    }

    @ExportMessage
    public static class CompareKeys {
        @TruffleBoundary
        @Specialization
        static int toSameType(EconomicMapStorage self, EconomicMapStorage other) {
            int size = self.map.size();
            int size2 = other.map.size();
            if (size > size2) {
                return 1;
            }
            MapCursor<DictKey, Object> cursor = self.map.getEntries();
            while (cursor.advance()) {
                if (!other.map.containsKey(cursor.getKey())) {
                    return 1;
                }
            }
            if (size == size2) {
                return 0;
            } else {
                return -1;
            }
        }

        @TruffleBoundary
        @Specialization(limit = "1")
        static int generic(EconomicMapStorage self, HashingStorage other,
                        @CachedLibrary("other") HashingStorageLibrary lib) {
            int size = self.map.size();
            int length = lib.length(other);
            if (size > length) {
                return 1;
            }
            MapCursor<DictKey, Object> cursor = self.map.getEntries();
            while (cursor.advance()) {
                if (!lib.hasKey(other, cursor.getKey().value)) {
                    return 1;
                }
            }
            if (size == length) {
                return 0;
            } else {
                return -1;
            }
        }
    }

    @ExportMessage
    public static class Intersect {
        @Specialization
        @TruffleBoundary
        static HashingStorage toSameType(EconomicMapStorage self, EconomicMapStorage other) {
            EconomicMapStorage result = EconomicMapStorage.create();
            MapCursor<DictKey, Object> cursor = self.map.getEntries();
            while (cursor.advance()) {
                if (other.map.containsKey(cursor.getKey())) {
                    result.map.put(cursor.getKey(), cursor.getValue());
                }
            }
            return result;
        }

        @Specialization
        @TruffleBoundary
        static HashingStorage generic(EconomicMapStorage self, HashingStorage other,
                        @CachedLibrary(limit = "1") HashingStorageLibrary lib) {
            EconomicMapStorage result = EconomicMapStorage.create();
            MapCursor<DictKey, Object> cursor = self.map.getEntries();
            while (cursor.advance()) {
                if (lib.hasKey(other, cursor.getKey().value)) {
                    result.map.put(cursor.getKey(), cursor.getValue());
                }
            }
            return result;
        }
    }

    @ExportMessage
    public static class Diff {
        @Specialization
        @TruffleBoundary
        static HashingStorage toSameType(EconomicMapStorage self, EconomicMapStorage other) {
            EconomicMapStorage result = EconomicMapStorage.create();
            MapCursor<DictKey, Object> cursor = self.map.getEntries();
            while (cursor.advance()) {
                if (!other.map.containsKey(cursor.getKey())) {
                    result.map.put(cursor.getKey(), cursor.getValue());
                }
            }
            return result;
        }

        @Specialization
        @TruffleBoundary
        static HashingStorage generic(EconomicMapStorage self, HashingStorage other,
                        @CachedLibrary(limit = "1") HashingStorageLibrary lib) {
            EconomicMapStorage result = EconomicMapStorage.create();
            MapCursor<DictKey, Object> cursor = self.map.getEntries();
            while (cursor.advance()) {
                if (!lib.hasKey(other, cursor.getKey().value)) {
                    result.map.put(cursor.getKey(), cursor.getValue());
                }
            }
            return result;
        }
    }

    @Override
    @ExportMessage
    public Iterator<Object> keys() {
        return new KeysIterator(map.getKeys().iterator());
    }

    private static final class KeysIterator implements Iterator<Object> {
        private final Iterator<DictKey> keysIterator;

        KeysIterator(Iterator<DictKey> iter) {
            this.keysIterator = iter;
        }

        public boolean hasNext() {
            return keysIterator.hasNext();
        }

        public Object next() {
            return keysIterator.next().value;
        }
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        StringBuilder builder = new StringBuilder();
        builder.append("map(size=").append(length()).append(", {");
        String sep = "";
        MapCursor<DictKey, Object> cursor = map.getEntries();
        while (cursor.advance()) {
            builder.append(sep);
            builder.append("(").append(cursor.getKey().value).append(",").append(cursor.getValue()).append(")");
            sep = ",";
        }
        builder.append("})");
        return builder.toString();
    }
}

final class PEMap<K, V> {
    private final EconomicMap<K, V> map;

    @TruffleBoundary
    PEMap(int initialCapacity) {
        this.map = EconomicMap.create(EconomicMapStorage.DEFAULT_EQIVALENCE, initialCapacity);
    }

    PEMap() {
        this(4);
    }

    @TruffleBoundary
    public V get(K key) {
        return map.get(key);
    }

    @TruffleBoundary
    public boolean containsKey(K key) {
        return map.containsKey(key);
    }

    @TruffleBoundary
    public int size() {
        return map.size();
    }

    @TruffleBoundary
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @TruffleBoundary
    public Iterable<K> getKeys() {
        return new KeysIterable<>(map.getKeys());
    }

    private static final class KeysIterable<K> implements Iterable<K> {
        private final Iterable<K> keysIterable;

        private KeysIterable(Iterable<K> keysIterable) {
            this.keysIterable = keysIterable;
        }

        @TruffleBoundary
        public Iterator<K> iterator() {
            return new KeysIterator<>(keysIterable.iterator());
        }
    }

    private static final class KeysIterator<K> implements Iterator<K> {
        private final Iterator<K> keysIterator;

        KeysIterator(Iterator<K> iter) {
            this.keysIterator = iter;
        }

        @TruffleBoundary
        public boolean hasNext() {
            return keysIterator.hasNext();
        }

        @TruffleBoundary
        public K next() {
            return keysIterator.next();
        }
    }

    @TruffleBoundary
    public V put(K key, V value) {
        return map.put(key, value);
    }

    @TruffleBoundary
    public void clear() {
        map.clear();
    }

    @TruffleBoundary
    public V removeKey(K key) {
        return map.removeKey(key);
    }

    @TruffleBoundary
    public MapCursor<K, V> getEntries() {
        return new EntriesMapCursor<>(map.getEntries());
    }

    @TruffleBoundary
    public void putAll(PEMap<K, V> other) {
        MapCursor<K, V> e = other.getEntries();
        while (e.advance()) {
            put(e.getKey(), e.getValue());
        }
    }

    private static final class EntriesMapCursor<K, V> implements MapCursor<K, V> {
        private final MapCursor<K, V> mapCursor;

        public EntriesMapCursor(MapCursor<K, V> mapCursor) {
            this.mapCursor = mapCursor;
        }

        @TruffleBoundary
        public boolean advance() {
            return mapCursor.advance();
        }

        @TruffleBoundary
        public K getKey() {
            return mapCursor.getKey();
        }

        @TruffleBoundary
        public V getValue() {
            return mapCursor.getValue();
        }

        @TruffleBoundary
        public void remove() {
            mapCursor.remove();
        }
    }
}
