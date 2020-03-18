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

import static com.oracle.graal.python.nodes.SpecialMethodNames.__DEL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__HASH__;

import java.util.Iterator;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary.ForEachNode;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary.HashingStorageIterable;
import com.oracle.graal.python.builtins.objects.function.PArguments.ThreadState;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.str.LazyString;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.object.GetLazyClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
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

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;

@ExportLibrary(HashingStorageLibrary.class)
public class EconomicMapStorage extends HashingStorage {

    public static EconomicMapStorage create() {
        return new EconomicMapStorage();
    }

    public static EconomicMapStorage create(int initialCapacity) {
        return new EconomicMapStorage(initialCapacity, false);
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
        public int hashCode() {
            return (int) hash;
        }
    }

    private final PEMap map;

    private EconomicMapStorage(int initialCapacity, boolean hasSideEffects) {
        this.map = PEMap.create(initialCapacity, false, hasSideEffects);
    }

    private EconomicMapStorage() {
        this(4, false);
    }

    private EconomicMapStorage(EconomicMapStorage original) {
        this(original.map.size(), original.map.hasSideEffect());
        this.map.putAll(original.map);
    }

    @TruffleBoundary
    public EconomicMapStorage(EconomicMap<? extends Object, ? extends Object> map) {
        this(map.size(), false);
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
        static Object getItemString(EconomicMapStorage self, String key, @SuppressWarnings("unused") ThreadState state,
                        @Exclusive @Cached("createBinaryProfile()") ConditionProfile findProfile,
                        @CachedLibrary(limit = "2") PythonObjectLibrary lib,
                        @Exclusive @Cached("createBinaryProfile()") ConditionProfile gotState) {
            DictKey newKey = new DictKey(key, key.hashCode());
            return self.map.get(newKey, lib, lib, findProfile, gotState, state);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!isNativeString(key)", "isBuiltinString(key, isBuiltinClassProfile, getClassNode)"})
        static Object getItemPString(EconomicMapStorage self, PString key, @SuppressWarnings("unused") ThreadState state,
                        @Exclusive @Cached("createBinaryProfile()") ConditionProfile findProfile,
                        @Exclusive @Cached("createBinaryProfile()") ConditionProfile gotState,
                        @Exclusive @Cached("createClassProfile()") ValueProfile profile,
                        @Exclusive @Cached IsBuiltinClassProfile isBuiltinClassProfile,
                        @Exclusive @Cached GetLazyClassNode getClassNode,
                        @CachedLibrary(limit = "2") PythonObjectLibrary lib) {
            final String k = EconomicMapStorage.toString(key, profile);
            return getItemString(self, k, state, findProfile, lib, gotState);
        }

        @Specialization(replaces = "getItemString", limit = "3")
        static Object getItemGeneric(EconomicMapStorage self, Object key, ThreadState state,
                        @CachedLibrary("key") PythonObjectLibrary lib,
                        @CachedLibrary(limit = "2") PythonObjectLibrary otherlib,
                        @Exclusive @Cached("createBinaryProfile()") ConditionProfile findProfile,
                        @Exclusive @Cached("createBinaryProfile()") ConditionProfile gotState) {
            final long h = self.getHashWithState(key, lib, state, gotState);
            DictKey newKey = new DictKey(key, h);
            return self.map.get(newKey, lib, otherlib, findProfile, gotState, state);
        }
    }

    protected static boolean hasSideEffect(EconomicMapStorage self) {
        return self.map.hasSideEffect();
    }

    protected static void convertToSideEffectMap(EconomicMapStorage self) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        self.map.setSideEffectFlag();
    }

    @SuppressWarnings("unused")
    @ExportMessage
    @ImportStatic(PGuards.class)
    static class SetItemWithState {

        static boolean isBuiltin(PythonObject o, IsBuiltinClassProfile p) {
            return PGuards.isBuiltinObject(o) || p.profileIsAnyBuiltinObject(o);
        }

        static boolean maySideEffect(PythonObject o, LookupInheritedAttributeNode.Dynamic lookup) {
            boolean se = lookup.execute(o, __DEL__) != PNone.NO_VALUE;
            se = se || !PGuards.isBuiltinFunction(lookup.execute(o, __EQ__));
            se = se || !PGuards.isBuiltinFunction(lookup.execute(o, __HASH__));
            return se;
        }

        @Specialization
        static HashingStorage setItemString(EconomicMapStorage self, String key, Object value, ThreadState state,
                        @Exclusive @Cached("createBinaryProfile()") ConditionProfile findProfile,
                        @CachedLibrary(limit = "2") PythonObjectLibrary lib,
                        @Exclusive @Cached("createBinaryProfile()") ConditionProfile gotState) {
            DictKey newKey = new DictKey(key, key.hashCode());
            self.map.put(newKey, value, lib, lib, findProfile, gotState, state);
            return self;
        }

        @Specialization(guards = {"!isNativeString(key)", "isBuiltinString(key, isBuiltinClassProfile, getClassNode)"})
        static HashingStorage setItemPString(EconomicMapStorage self, PString key, Object value, ThreadState state,
                        @Exclusive @Cached("createClassProfile()") ValueProfile profile,
                        @Exclusive @Cached("createBinaryProfile()") ConditionProfile findProfile,
                        @Exclusive @Cached("createBinaryProfile()") ConditionProfile gotState,
                        @Exclusive @Cached IsBuiltinClassProfile isBuiltinClassProfile,
                        @Exclusive @Cached GetLazyClassNode getClassNode,
                        @CachedLibrary(limit = "2") PythonObjectLibrary lib) {
            final String k = EconomicMapStorage.toString(key, profile);
            return setItemString(self, k, value, state, findProfile, lib, gotState);
        }

        @Specialization(guards = {"!hasSideEffect(self)", "!isBuiltin(key,builtinProfile) || !isBuiltin(value,builtinProfile)", "maySideEffect(key, lookup) || maySideEffect(value, lookup)"})
        static HashingStorage setItemPythonObjectWithSideEffect(EconomicMapStorage self, PythonObject key, PythonObject value, ThreadState state,
                        @CachedLibrary(limit = "2") PythonObjectLibrary lib,
                        @CachedLibrary(limit = "2") PythonObjectLibrary otherlib,
                        @Exclusive @Cached LookupInheritedAttributeNode.Dynamic lookup,
                        @Exclusive @Cached IsBuiltinClassProfile builtinProfile,
                        @Exclusive @Cached("createBinaryProfile()") ConditionProfile findProfile,
                        @Exclusive @Cached("createBinaryProfile()") ConditionProfile gotState,
                        @Exclusive @Cached GetLazyClassNode getClassNode) {
            convertToSideEffectMap(self);
            return setItemGeneric(self, key, value, state, lib, otherlib, findProfile, gotState);
        }

        @Specialization(guards = {"!hasSideEffect(self)", "!isBuiltin(key,builtinProfile)", "maySideEffect(key, lookup)"})
        static HashingStorage setItemPythonObjectWithSideEffect(EconomicMapStorage self, PythonObject key, Object value, ThreadState state,
                        @CachedLibrary(limit = "2") PythonObjectLibrary lib,
                        @CachedLibrary(limit = "2") PythonObjectLibrary otherlib,
                        @Exclusive @Cached LookupInheritedAttributeNode.Dynamic lookup,
                        @Exclusive @Cached IsBuiltinClassProfile builtinProfile,
                        @Exclusive @Cached("createBinaryProfile()") ConditionProfile findProfile,
                        @Exclusive @Cached("createBinaryProfile()") ConditionProfile gotState,
                        @Exclusive @Cached GetLazyClassNode getClassNode) {
            convertToSideEffectMap(self);
            return setItemGeneric(self, key, value, state, lib, otherlib, findProfile, gotState);
        }

        @Specialization(guards = {"!hasSideEffect(self)", "!isBuiltin(value,builtinProfile)", "maySideEffect(value, lookup)"})
        static HashingStorage setItemPythonObjectWithSideEffect(EconomicMapStorage self, Object key, PythonObject value, ThreadState state,
                        @CachedLibrary(limit = "2") PythonObjectLibrary lib,
                        @CachedLibrary(limit = "2") PythonObjectLibrary otherlib,
                        @Exclusive @Cached LookupInheritedAttributeNode.Dynamic lookup,
                        @Exclusive @Cached IsBuiltinClassProfile builtinProfile,
                        @Exclusive @Cached("createBinaryProfile()") ConditionProfile findProfile,
                        @Exclusive @Cached("createBinaryProfile()") ConditionProfile gotState,
                        @Exclusive @Cached GetLazyClassNode getClassNode) {
            convertToSideEffectMap(self);
            return setItemGeneric(self, key, value, state, lib, otherlib, findProfile, gotState);
        }

        @Specialization(replaces = "setItemString", limit = "3")
        static HashingStorage setItemGeneric(EconomicMapStorage self, Object key, Object value, ThreadState state,
                        @CachedLibrary("key") PythonObjectLibrary lib,
                        @CachedLibrary(limit = "2") PythonObjectLibrary otherlib,
                        @Exclusive @Cached("createBinaryProfile()") ConditionProfile findProfile,
                        @Exclusive @Cached("createBinaryProfile()") ConditionProfile gotState) {
            DictKey newKey = new DictKey(key, self.getHashWithState(key, lib, state, gotState));
            self.map.put(newKey, value, lib, otherlib, findProfile, gotState, state);
            return self;
        }
    }

    @Override
    @ExportMessage
    Object forEachUntyped(ForEachNode<Object> node, Object arg) {
        Object result = arg;
        MapCursor<DictKey, Object> cursor = map.getEntries();
        while (cursor.advance()) {
            result = node.execute(cursor.getKey().value, result);
        }
        return result;
    }

    @ExportMessage
    public static class AddAllToOther {

        protected static boolean hasSideEffect(EconomicMapStorage self, EconomicMapStorage other) {
            return !other.map.hasSideEffect() && self.map.hasSideEffect();
        }

        @TruffleBoundary
        @Specialization(guards = "hasSideEffect(self, other)")
        static HashingStorage toSameTypeSideEffect(EconomicMapStorage self, EconomicMapStorage other,
                        @Exclusive @Cached("createBinaryProfile()") ConditionProfile findProfile,
                        @Exclusive @Cached("createBinaryProfile()") ConditionProfile gotState,
                        @CachedLibrary(limit = "2") PythonObjectLibrary lib) {
            convertToSideEffectMap(other);
            return toSameType(self, other, findProfile, gotState, lib);
        }

        @Specialization
        static HashingStorage toSameType(EconomicMapStorage self, EconomicMapStorage other,
                        @Exclusive @Cached("createBinaryProfile()") ConditionProfile findProfile,
                        @Exclusive @Cached("createBinaryProfile()") ConditionProfile gotState,
                        @CachedLibrary(limit = "2") PythonObjectLibrary lib) {
            other.map.putAll(self.map, lib, findProfile, gotState);
            return other;
        }

        @TruffleBoundary
        @Specialization(limit = "2")
        static HashingStorage generic(EconomicMapStorage self, HashingStorage other,
                        @CachedLibrary("other") HashingStorageLibrary lib) {
            HashingStorage result = other;
            MapCursor<DictKey, Object> cursor = self.map.getEntries();
            while (cursor.advance()) {
                result = lib.setItem(other, cursor.getKey().value, cursor.getValue());
            }
            return result;
        }
    }

    protected static boolean hasSideEffect(Object o, LookupInheritedAttributeNode.Dynamic lookup) {
        return o instanceof PythonObject && lookup.execute(o, __DEL__) != PNone.NO_VALUE;
    }

    @ExportMessage
    static class DelItemWithState {

        @Specialization(guards = "!hasSideEffect(self)", limit = "2")
        static HashingStorage delItemWithState(EconomicMapStorage self, Object key, ThreadState state,
                        @CachedLibrary("key") PythonObjectLibrary lib,
                        @CachedLibrary(limit = "2") PythonObjectLibrary otherlib,
                        @Exclusive @Cached("createBinaryProfile()") ConditionProfile gotState) {
            DictKey newKey = new DictKey(key, self.getHashWithState(key, lib, state, gotState));
            self.map.removeKey(newKey, lib, otherlib, gotState, state);
            return self;
        }

        @Specialization(limit = "2")
        static HashingStorage delItemWithStateWithSideEffect(EconomicMapStorage self, Object key, ThreadState state,
                        @CachedLibrary("key") PythonObjectLibrary lib,
                        @CachedLibrary(limit = "2") PythonObjectLibrary otherlib,
                        @Exclusive @Cached LookupInheritedAttributeNode.Dynamic lookup,
                        @Exclusive @Cached CallUnaryMethodNode callNode,
                        @Exclusive @Cached("createBinaryProfile()") ConditionProfile gotState) {
            DictKey newKey = new DictKey(key, self.getHashWithState(key, lib, state, gotState));
            Object value = self.map.removeKey(newKey, lib, otherlib, gotState, state);
            if (hasSideEffect(key, lookup)) {
                callNode.executeObject(lookup.execute(key, __DEL__), key);
            }
            if (hasSideEffect(value, lookup)) {
                callNode.executeObject(lookup.execute(value, __DEL__), value);
            }
            return self;
        }
    }

    @ExportMessage
    static class Clear {

        @Specialization(guards = "!hasSideEffect(self)")
        static HashingStorage clear(EconomicMapStorage self) {
            self.map.clear();
            return self;
        }

        @Specialization
        static HashingStorage clearWithSideEffect(EconomicMapStorage self,
                        @Exclusive @Cached LookupInheritedAttributeNode.Dynamic lookup,
                        @Exclusive @Cached CallUnaryMethodNode callNode) {
            if (self.map.size() == 0) {
                return self;
            }
            Object[] entries = new Object[self.map.size() * 2];
            MapCursor<DictKey, Object> cursor = self.map.getEntries();
            int i = 0;
            while (cursor.advance()) {
                Object key = cursor.getKey().value;
                Object value = cursor.getValue();
                entries[i++] = hasSideEffect(key, lookup) ? key : null;
                entries[i++] = hasSideEffect(value, lookup) ? value : null;
            }
            self.map.clear();
            for (Object o : entries) {
                if (o != null) {
                    callNode.executeObject(lookup.execute(o, __DEL__), o);
                }
            }
            return self;
        }

    }

    @Override
    @ExportMessage
    public HashingStorage copy() {
        return new EconomicMapStorage(this);
    }

    @ExportMessage
    public static class EqualsWithState {
        @TruffleBoundary
        @Specialization
        static boolean equalSameType(EconomicMapStorage self, EconomicMapStorage other, ThreadState state,
                        @Exclusive @Cached("createBinaryProfile()") ConditionProfile findProfile,
                        @Exclusive @Cached("createBinaryProfile()") ConditionProfile gotState,
                        @CachedLibrary(limit = "2") PythonObjectLibrary compareLib1,
                        @CachedLibrary(limit = "2") PythonObjectLibrary compareLib2) {
            if (self.map.size() != other.map.size()) {
                return false;
            }
            MapCursor<DictKey, Object> cursor = self.map.getEntries();
            while (cursor.advance()) {
                Object otherValue = other.map.get(cursor.getKey(), compareLib1, compareLib2, findProfile, gotState, state);
                if (otherValue != null && !compareLib1.equalsWithState(otherValue, cursor.getValue(), compareLib2, state)) {
                    return false;
                }
            }
            return true;
        }

        @TruffleBoundary
        @Specialization
        static boolean equalGeneric(EconomicMapStorage self, HashingStorage other, ThreadState state,
                        @CachedLibrary(limit = "2") HashingStorageLibrary selflib,
                        @CachedLibrary(limit = "2") HashingStorageLibrary otherlib,
                        @CachedLibrary(limit = "2") PythonObjectLibrary compareLib1,
                        @CachedLibrary(limit = "2") PythonObjectLibrary compareLib2) {
            if (self.map.size() != otherlib.lengthWithState(other, state)) {
                return false;
            }
            MapCursor<DictKey, Object> cursor = self.map.getEntries();
            while (cursor.advance()) {
                Object otherValue = selflib.getItemWithState(self, cursor.getKey().value, state);
                if (otherValue != null && !compareLib1.equalsWithState(otherValue, cursor.getValue(), compareLib2, state)) {
                    return false;
                }
            }
            return true;
        }
    }

    @ExportMessage
    public static class CompareKeysWithState {
        @TruffleBoundary
        @Specialization
        static int compareSameType(EconomicMapStorage self, EconomicMapStorage other, ThreadState state,
                        @Exclusive @Cached("createBinaryProfile()") ConditionProfile findProfile,
                        @Exclusive @Cached("createBinaryProfile()") ConditionProfile gotState,
                        @CachedLibrary(limit = "2") PythonObjectLibrary lib) {
            int size = self.map.size();
            int size2 = other.map.size();
            if (size > size2) {
                return 1;
            }
            MapCursor<DictKey, Object> cursor = self.map.getEntries();
            while (cursor.advance()) {
                if (!other.map.containsKey(cursor.getKey(), lib, lib, findProfile, gotState, state)) {
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
        @Specialization(limit = "4")
        static int compareGeneric(EconomicMapStorage self, HashingStorage other, ThreadState state,
                        @CachedLibrary("other") HashingStorageLibrary lib) {
            int size = self.map.size();
            int length = lib.length(other);
            if (size > length) {
                return 1;
            }
            MapCursor<DictKey, Object> cursor = self.map.getEntries();
            while (cursor.advance()) {
                if (!lib.hasKeyWithState(other, cursor.getKey().value, state)) {
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
    public static class IntersectWithState {
        @TruffleBoundary
        @Specialization
        static HashingStorage intersectSameType(EconomicMapStorage self, EconomicMapStorage other, ThreadState state,
                        @Exclusive @Cached("createBinaryProfile()") ConditionProfile findProfile,
                        @Exclusive @Cached("createBinaryProfile()") ConditionProfile gotState,
                        @CachedLibrary(limit = "2") PythonObjectLibrary lib) {
            EconomicMapStorage result = EconomicMapStorage.create();
            MapCursor<DictKey, Object> cursor = self.map.getEntries();
            while (cursor.advance()) {
                if (other.map.containsKey(cursor.getKey(), lib, lib, findProfile, gotState, state)) {
                    result.map.put(cursor.getKey(), cursor.getValue());
                }
            }
            return result;
        }

        @TruffleBoundary
        @Specialization(limit = "4")
        static HashingStorage intersectGeneric(EconomicMapStorage self, HashingStorage other, @SuppressWarnings("unused") ThreadState state,
                        @CachedLibrary("other") HashingStorageLibrary hlib) {
            EconomicMapStorage result = EconomicMapStorage.create();
            MapCursor<DictKey, Object> cursor = self.map.getEntries();
            while (cursor.advance()) {
                if (hlib.hasKey(other, cursor.getKey().value)) {
                    result.map.put(cursor.getKey(), cursor.getValue());
                }
            }
            return result;
        }
    }

    @ExportMessage
    public static class DiffWithState {
        @TruffleBoundary
        @Specialization
        static HashingStorage diffSameType(EconomicMapStorage self, EconomicMapStorage other, ThreadState state,
                        @Exclusive @Cached("createBinaryProfile()") ConditionProfile findProfile,
                        @Exclusive @Cached("createBinaryProfile()") ConditionProfile gotState,
                        @CachedLibrary(limit = "2") PythonObjectLibrary lib) {
            EconomicMapStorage result = EconomicMapStorage.create();
            MapCursor<DictKey, Object> cursor = self.map.getEntries();
            while (cursor.advance()) {
                if (!other.map.containsKey(cursor.getKey(), lib, lib, findProfile, gotState, state)) {
                    result.map.put(cursor.getKey(), cursor.getValue());
                }
            }
            return result;
        }

        @TruffleBoundary
        @Specialization(limit = "4")
        static HashingStorage diffGeneric(EconomicMapStorage self, HashingStorage other, @SuppressWarnings("unused") ThreadState state,
                        @CachedLibrary("other") HashingStorageLibrary hlib) {
            EconomicMapStorage result = EconomicMapStorage.create();
            MapCursor<DictKey, Object> cursor = self.map.getEntries();
            while (cursor.advance()) {
                if (!hlib.hasKey(other, cursor.getKey().value)) {
                    result.map.put(cursor.getKey(), cursor.getValue());
                }
            }
            return result;
        }
    }

    @Override
    @ExportMessage
    public HashingStorageIterable<Object> keys() {
        return new HashingStorageIterable<>(new KeysIterator(map.getKeys().iterator()));
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
