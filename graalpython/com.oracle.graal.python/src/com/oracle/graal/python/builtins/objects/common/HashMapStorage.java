/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.nodes.truffle.TruffleStringMigrationPythonTypes.assertNoJavaString;
import static com.oracle.graal.python.nodes.truffle.TruffleStringMigrationPythonTypes.ensureNoJavaString;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary.ForEachNode;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary.HashingStorageIterable;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PArguments.ThreadState;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.lib.PyObjectHashNode;
import com.oracle.graal.python.lib.PyObjectRichCompareBool;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Hashing storage that can be used for any type of key that does not override {@code __hash__} and
 * {@code __equals__} and uses the default Java hash code and equality. This may be extended by
 * using wrapper that would delegate to GraalPython's internal hash code and equality
 * implementations as long as those have no visible side effects. For now we use this strategy only
 * for string keys.
 */
@ExportLibrary(HashingStorageLibrary.class)
public class HashMapStorage extends HashingStorage {
    public static final int SIZE_THRESHOLD = 100;

    private final LinkedHashMap<Object, Object> values;

    public HashMapStorage(int capacity) {
        this.values = newHashMap(capacity);
    }

    public HashMapStorage() {
        this.values = newHashMap();
    }

    public HashMapStorage(LinkedHashMap<Object, Object> map) {
        assert hasStringKeys(map) : "keys in HashMapStorage have to be java.lang.String";
        values = map;
    }

    private static boolean hasStringKeys(LinkedHashMap<Object, Object> map) {
        for (Object k : map.keySet()) {
            if (!(k instanceof String)) {
                return false;
            }
        }
        return true;
    }

    @TruffleBoundary
    private static LinkedHashMap<Object, Object> newHashMap() {
        return new LinkedHashMap<>();
    }

    @TruffleBoundary
    private static LinkedHashMap<Object, Object> newHashMap(int capacity) {
        return new LinkedHashMap<>(capacity);
    }

    @TruffleBoundary
    private static LinkedHashMap<Object, Object> newHashMap(Map<?, ?> map) {
        return new LinkedHashMap<>(map);
    }

    @TruffleBoundary
    private static LinkedHashMap<Object, Object> newHashMap(LinkedHashMap<Object, Object> map) {
        return new LinkedHashMap<>(map);
    }

    static boolean isSupportedKey(Object obj, IsBuiltinClassProfile isBuiltinClassProfile) {
        return PGuards.isBuiltinString(obj, isBuiltinClassProfile);
    }

    private static final class CustomKey {
        private final Object value;
        private final int hash;

        private CustomKey(Object value, int hash) {
            this.value = value;
            this.hash = hash;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (other == null) {
                return false;
            }
            Object otherValue = other;
            if (other instanceof CustomKey) {
                otherValue = ((CustomKey) other).value;
                if (hash != ((CustomKey) other).hash) {
                    return false;
                }
            } else if (hash != other.hashCode()) {
                return false;
            }
            // Hopefully it will be uncommon that the object we search for will have the same hash
            // as some of the items in the storage (it may even equal to some of those items), so
            // the uncached equals call does not hurt that much here
            return PyObjectRichCompareBool.EqNode.getUncached().execute(null, value, ensureNoJavaString(otherValue));
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    @ExportMessage
    @Override
    public int length() {
        return size(values);
    }

    @TruffleBoundary
    private static int size(LinkedHashMap<Object, Object> map) {
        return map.size();
    }

    @ExportMessage
    static class GetItemWithState {
        @Specialization
        static Object getItemString(HashMapStorage self, String key, @SuppressWarnings("unused") ThreadState state) {
            return get(self.values, key);
        }

        @Specialization(replaces = "getItemString", guards = "isSupportedKey(key, profile)")
        static Object getItem(HashMapStorage self, Object key, @SuppressWarnings("unused") ThreadState state,
                        @Cached CastToJavaStringNode castNode,
                        @SuppressWarnings("unused") @Cached IsBuiltinClassProfile profile) {
            return get(self.values, castNode.execute(key));
        }

        @TruffleBoundary
        private static Object get(LinkedHashMap<Object, Object> values, Object key) {
            return values.get(key);
        }

        @Specialization(guards = "!isSupportedKey(key, profile)", limit = "1")
        static Object getItemNotSupportedKey(@SuppressWarnings("unused") HashMapStorage self, Object key, @SuppressWarnings("unused") ThreadState state,
                        @Shared("classProfile") @SuppressWarnings("unused") @Cached IsBuiltinClassProfile profile,
                        @Shared("hashNode") @Cached PyObjectHashNode hashNode,
                        @Shared("gotState") @Cached ConditionProfile gotState) {
            // we must still search the map for items that may have the same hash and that may
            // return true from key.__eq__, we use artificial object with overridden Java level
            // equals and hashCode methods to perform this search
            VirtualFrame frame = gotState.profile(state == null) ? null : PArguments.frameForCall(state);
            long hash = hashNode.execute(frame, key);
            if (PInt.isIntRange(hash)) {
                CustomKey keyObj = new CustomKey(key, (int) hash);
                return get(self.values, keyObj);
            }
            // else the hashes cannot possibly match
            return null;
        }
    }

    @ExportMessage
    static class SetItemWithState {
        @Specialization
        static HashingStorage setItemString(HashMapStorage self, String key, Object value, @SuppressWarnings("unused") ThreadState state) {
            put(self.values, key, assertNoJavaString(value));
            return self;
        }

        @Specialization(replaces = "setItemString", guards = "isSupportedKey(key, profile)")
        static HashingStorage setItem(HashMapStorage self, Object key, Object value, @SuppressWarnings("unused") ThreadState state,
                        @Cached CastToJavaStringNode castNode,
                        @SuppressWarnings("unused") @Cached IsBuiltinClassProfile profile) {
            put(self.values, castNode.execute(key), assertNoJavaString(value));
            return self;
        }

        @Specialization(guards = "!isSupportedKey(key, profile)", limit = "3")
        static HashingStorage setItemNotSupportedKey(HashMapStorage self, Object key, Object value, @SuppressWarnings("unused") ThreadState state,
                        @SuppressWarnings("unused") @Cached IsBuiltinClassProfile profile,
                        @CachedLibrary("self") HashingStorageLibrary thisLib,
                        @CachedLibrary(limit = "1") HashingStorageLibrary newLib) {
            HashingStorage newStore = EconomicMapStorage.create(self.length());
            thisLib.addAllToOther(self, newStore);
            newLib.setItem(newStore, key, value);
            return newStore;
        }
    }

    @ExportMessage
    static class DelItemWithState {
        @Specialization
        static HashingStorage delItemString(HashMapStorage self, String key, @SuppressWarnings("unused") ThreadState state) {
            remove(self.values, key);
            return self;
        }

        @Specialization(replaces = "delItemString", guards = "isSupportedKey(key, profile)")
        static HashingStorage delItem(HashMapStorage self, Object key, @SuppressWarnings("unused") ThreadState state,
                        @Cached CastToJavaStringNode castNode,
                        @SuppressWarnings("unused") @Cached IsBuiltinClassProfile profile) {
            remove(self.values, castNode.execute(key));
            return self;
        }

        @TruffleBoundary
        private static void remove(LinkedHashMap<Object, Object> values, Object key) {
            values.remove(key);
        }

        @Specialization(guards = "!isSupportedKey(key, profile)", limit = "1")
        static HashingStorage delItemNonSupportedKey(HashMapStorage self, @SuppressWarnings("unused") Object key, @SuppressWarnings("unused") ThreadState state,
                        @Shared("classProfile") @SuppressWarnings("unused") @Cached IsBuiltinClassProfile profile,
                        @Shared("hashNode") @Cached PyObjectHashNode hashNode,
                        @Shared("gotState") @Cached ConditionProfile gotState) {
            // we must still search the map for items that may have the same hash and that may
            // return true from key.__eq__, we use artificial object with overridden Java level
            // equals and hashCode methods to perform this search
            VirtualFrame frame = gotState.profile(state == null) ? null : PArguments.frameForCall(state);
            long hash = hashNode.execute(frame, key);
            if (PInt.isIntRange(hash)) {
                CustomKey keyObj = new CustomKey(key, (int) hash);
                remove(self.values, keyObj);
            }
            // else the hashes cannot possibly match
            return self;
        }
    }

    @ExportMessage
    @Override
    Object forEachUntyped(ForEachNode<Object> node, Object argIn) {
        Object arg = argIn;
        for (Object key : keys()) {
            arg = node.execute(key, arg);
        }
        return arg;
    }

    @ExportMessage
    @Override
    public HashingStorage clear() {
        clearMap(values);
        return this;
    }

    @TruffleBoundary
    private static void clearMap(LinkedHashMap<Object, Object> map) {
        map.clear();
    }

    @ExportMessage
    @Override
    public HashingStorage copy() {
        return new HashMapStorage(newHashMap(values));
    }

    @ExportMessage
    @Override
    public HashingStorageIterable<Object> keys() {
        return new HashingStorageIterable<>(getKeysIterator(values));
    }

    private static Iterator<Object> getKeysIterator(LinkedHashMap<Object, Object> map) {
        return fixStrings(map.keySet().iterator());
    }

    private static Iterator<Object> fixStrings(Iterator<Object> it) {
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public Object next() {
                Object o = it.next();
                if (o instanceof String) {
                    o = toTruffleStringUncached((String) o);
                }
                return o;
            }
        };
    }

    @ExportMessage
    @Override
    public HashingStorageIterable<Object> reverseKeys() {
        return new HashingStorageIterable<>(getReverseIterator(values));
    }

    @TruffleBoundary
    private static Iterator<Object> getReverseIterator(LinkedHashMap<Object, Object> values) {
        ArrayList<Object> keys = new ArrayList<>(values.keySet());
        Collections.reverse(keys);
        return fixStrings(keys.iterator());
    }

    public void put(TruffleString key, Object value) {
        put(values, key.toJavaStringUncached(), value);
    }

    @TruffleBoundary
    private static void put(LinkedHashMap<Object, Object> values, Object key, Object value) {
        values.put(key, value);
    }

}
