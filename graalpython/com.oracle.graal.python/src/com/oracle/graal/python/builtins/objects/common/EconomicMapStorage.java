/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.collections.MapCursor;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary.ForEachNode;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary.HashingStorageIterable;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PArguments.ThreadState;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.str.StringNodes.StringMaterializeNode;
import com.oracle.graal.python.lib.PyObjectHashNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.profiles.ConditionProfile;

@ExportLibrary(HashingStorageLibrary.class)
public class EconomicMapStorage extends HashingStorage {

    public static EconomicMapStorage create() {
        return new EconomicMapStorage();
    }

    public static EconomicMapStorage create(int initialCapacity) {
        return new EconomicMapStorage(initialCapacity, false);
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

    @ExportMessage
    @Override
    public int length() {
        return map.size();
    }

    @ExportMessage
    @ImportStatic(PGuards.class)
    static class GetItemWithState {

        @Specialization
        static Object getItemString(EconomicMapStorage self, String key, @SuppressWarnings("unused") ThreadState state,
                        @Shared("findProfile") @Cached ConditionProfile findProfile,
                        @Shared("plib") @CachedLibrary(limit = "3") PythonObjectLibrary lib,
                        @Shared("gotState") @Cached ConditionProfile gotState) {
            DictKey newKey = new DictKey(key, key.hashCode());
            return self.map.get(newKey, lib, lib, findProfile, gotState, state);
        }

        @Specialization(guards = {"isBuiltinString(key, isBuiltinClassProfile)"}, limit = "1")
        static Object getItemPString(EconomicMapStorage self, PString key, @SuppressWarnings("unused") ThreadState state,
                        @Shared("stringMaterialize") @Cached StringMaterializeNode stringMaterializeNode,
                        @Shared("findProfile") @Cached ConditionProfile findProfile,
                        @Shared("gotState") @Cached ConditionProfile gotState,
                        @Shared("builtinProfile") @Cached @SuppressWarnings("unused") IsBuiltinClassProfile isBuiltinClassProfile,
                        @Shared("plib") @CachedLibrary(limit = "3") PythonObjectLibrary lib) {
            final String k = stringMaterializeNode.execute(key);
            return getItemString(self, k, state, findProfile, lib, gotState);
        }

        @Specialization(replaces = {"getItemString", "getItemPString"})
        static Object getItemGeneric(EconomicMapStorage self, Object key, ThreadState state,
                        @Shared("plib") @CachedLibrary(limit = "3") PythonObjectLibrary lib,
                        @Shared("hashNode") @Cached PyObjectHashNode hashNode,
                        @Shared("findProfile") @Cached ConditionProfile findProfile,
                        @Shared("gotState") @Cached ConditionProfile gotState) {
            VirtualFrame frame = gotState.profile(state == null) ? null : PArguments.frameForCall(state);
            DictKey newKey = new DictKey(key, hashNode.execute(frame, key));
            return self.map.get(newKey, lib, lib, findProfile, gotState, state);
        }
    }

    @ExportMessage
    protected static boolean hasSideEffect(EconomicMapStorage self) {
        return self.map.hasSideEffect();
    }

    protected static void convertToSideEffectMap(EconomicMapStorage self) {
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
                        @Shared("findProfile") @Cached ConditionProfile findProfile,
                        @Shared("plib") @CachedLibrary(limit = "3") PythonObjectLibrary lib,
                        @Shared("gotState") @Cached ConditionProfile gotState) {
            DictKey newKey = new DictKey(key, key.hashCode());
            self.map.put(newKey, value, lib, lib, findProfile, gotState, state);
            return self;
        }

        @Specialization(guards = {"isBuiltinString(key, isBuiltinClassProfile)"}, limit = "1")
        static HashingStorage setItemPString(EconomicMapStorage self, PString key, Object value, ThreadState state,
                        @Shared("stringMaterialize") @Cached StringMaterializeNode stringMaterializeNode,
                        @Shared("findProfile") @Cached ConditionProfile findProfile,
                        @Shared("gotState") @Cached ConditionProfile gotState,
                        @Shared("builtinProfile") @Cached IsBuiltinClassProfile isBuiltinClassProfile,
                        @Shared("plib") @CachedLibrary(limit = "3") PythonObjectLibrary lib) {
            final String k = stringMaterializeNode.execute(key);
            return setItemString(self, k, value, state, findProfile, lib, gotState);
        }

        @Specialization(guards = {"!hasSideEffect(self)", "!isBuiltin(key,builtinProfile) || !isBuiltin(value,builtinProfile)",
                        "maySideEffect(key, lookup) || maySideEffect(value, lookup)"}, limit = "1")
        static HashingStorage setItemPythonObjectWithSideEffect(EconomicMapStorage self, PythonObject key, PythonObject value, ThreadState state,
                        @Shared("plib") @CachedLibrary(limit = "3") PythonObjectLibrary lib,
                        @Shared("hashNode") @Cached PyObjectHashNode hashNode,
                        @Shared("lookup") @Cached LookupInheritedAttributeNode.Dynamic lookup,
                        @Shared("builtinProfile") @Cached IsBuiltinClassProfile builtinProfile,
                        @Shared("findProfile") @Cached ConditionProfile findProfile,
                        @Shared("gotState") @Cached ConditionProfile gotState) {
            convertToSideEffectMap(self);
            return setItemGeneric(self, key, value, state, lib, hashNode, findProfile, gotState);
        }

        @Specialization(guards = {"!hasSideEffect(self)", "!isBuiltin(key,builtinProfile)", "maySideEffect(key, lookup)"}, limit = "1")
        static HashingStorage setItemPythonObjectWithSideEffect(EconomicMapStorage self, PythonObject key, Object value, ThreadState state,
                        @Shared("plib") @CachedLibrary(limit = "3") PythonObjectLibrary lib,
                        @Shared("hashNode") @Cached PyObjectHashNode hashNode,
                        @Shared("lookup") @Cached LookupInheritedAttributeNode.Dynamic lookup,
                        @Shared("builtinProfile") @Cached IsBuiltinClassProfile builtinProfile,
                        @Shared("findProfile") @Cached ConditionProfile findProfile,
                        @Shared("gotState") @Cached ConditionProfile gotState) {
            convertToSideEffectMap(self);
            return setItemGeneric(self, key, value, state, lib, hashNode, findProfile, gotState);
        }

        @Specialization(guards = {"!hasSideEffect(self)", "!isBuiltin(value,builtinProfile)", "maySideEffect(value, lookup)"}, limit = "1")
        static HashingStorage setItemPythonObjectWithSideEffect(EconomicMapStorage self, Object key, PythonObject value, ThreadState state,
                        @Shared("plib") @CachedLibrary(limit = "3") PythonObjectLibrary lib,
                        @Shared("hashNode") @Cached PyObjectHashNode hashNode,
                        @Shared("lookup") @Cached LookupInheritedAttributeNode.Dynamic lookup,
                        @Shared("builtinProfile") @Cached IsBuiltinClassProfile builtinProfile,
                        @Shared("findProfile") @Cached ConditionProfile findProfile,
                        @Shared("gotState") @Cached ConditionProfile gotState) {
            convertToSideEffectMap(self);
            return setItemGeneric(self, key, value, state, lib, hashNode, findProfile, gotState);
        }

        @Specialization(replaces = "setItemString")
        static HashingStorage setItemGeneric(EconomicMapStorage self, Object key, Object value, ThreadState state,
                        @Shared("plib") @CachedLibrary(limit = "3") PythonObjectLibrary lib,
                        @Shared("hashNode") @Cached PyObjectHashNode hashNode,
                        @Shared("findProfile") @Cached ConditionProfile findProfile,
                        @Shared("gotState") @Cached ConditionProfile gotState) {
            VirtualFrame frame = gotState.profile(state == null) ? null : PArguments.frameForCall(state);
            DictKey newKey = new DictKey(key, hashNode.execute(frame, key));
            self.map.put(newKey, value, lib, lib, findProfile, gotState, state);
            return self;
        }
    }

    @TruffleBoundary
    static boolean advance(MapCursor<DictKey, Object> cursor) {
        return cursor.advance();
    }

    @TruffleBoundary
    static DictKey getDictKey(MapCursor<DictKey, Object> cursor) {
        return cursor.getKey();
    }

    static Object getKey(MapCursor<DictKey, Object> cursor) {
        return getDictKey(cursor).value;
    }

    @TruffleBoundary
    static Object getValue(MapCursor<DictKey, Object> cursor) {
        return cursor.getValue();
    }

    @ExportMessage
    @Override
    Object forEachUntyped(ForEachNode<Object> node, Object arg) {
        Object result = arg;
        MapCursor<DictKey, Object> cursor = map.getEntries();
        while (advance(cursor)) {
            result = node.execute(getKey(cursor), result);
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
                        @Shared("findProfile") @Cached ConditionProfile findProfile,
                        @Shared("gotState") @Cached ConditionProfile gotState,
                        @Shared("plib") @CachedLibrary(limit = "3") PythonObjectLibrary lib) {
            convertToSideEffectMap(other);
            return toSameType(self, other, findProfile, gotState, lib);
        }

        @Specialization(guards = "!hasSideEffect(self, other)")
        static HashingStorage toSameType(EconomicMapStorage self, EconomicMapStorage other,
                        @Shared("findProfile") @Cached ConditionProfile findProfile,
                        @Shared("gotState") @Cached ConditionProfile gotState,
                        @Shared("plib") @CachedLibrary(limit = "3") PythonObjectLibrary lib) {
            other.map.putAll(self.map, lib, findProfile, gotState);
            return other;
        }

        @TruffleBoundary
        @Specialization
        static HashingStorage generic(EconomicMapStorage self, HashingStorage other,
                        @Shared("otherHLib") @CachedLibrary(limit = "2") HashingStorageLibrary lib) {
            HashingStorage result = other;
            MapCursor<DictKey, Object> cursor = self.map.getEntries();
            while (advance(cursor)) {
                result = lib.setItem(result, getKey(cursor), getValue(cursor));
            }
            return result;
        }
    }

    private static boolean hasDELSideEffect(Object o, LookupInheritedAttributeNode.Dynamic lookup) {
        return o instanceof PythonObject && lookup.execute(o, __DEL__) != PNone.NO_VALUE;
    }

    @ExportMessage
    static class DelItemWithState {

        @Specialization(guards = "!hasSideEffect(self)")
        static HashingStorage delItemWithState(EconomicMapStorage self, Object key, ThreadState state,
                        @Shared("plib") @CachedLibrary(limit = "3") PythonObjectLibrary lib,
                        @Shared("hashNode") @Cached PyObjectHashNode hashNode,
                        @Shared("gotState") @Cached ConditionProfile gotState) {
            VirtualFrame frame = gotState.profile(state == null) ? null : PArguments.frameForCall(state);
            DictKey newKey = new DictKey(key, hashNode.execute(frame, key));
            self.map.removeKey(newKey, lib, lib, gotState, state);
            return self;
        }

        @Specialization
        static HashingStorage delItemWithStateWithSideEffect(EconomicMapStorage self, Object key, ThreadState state,
                        @Shared("plib") @CachedLibrary(limit = "3") PythonObjectLibrary lib,
                        @Shared("hashNode") @Cached PyObjectHashNode hashNode,
                        @Shared("lookupDel") @Cached LookupInheritedAttributeNode.Dynamic lookup,
                        @Shared("callDel") @Cached CallUnaryMethodNode callNode,
                        @Shared("gotState") @Cached ConditionProfile gotState) {
            VirtualFrame frame = gotState.profile(state == null) ? null : PArguments.frameForCall(state);
            DictKey newKey = new DictKey(key, hashNode.execute(frame, key));
            Object value = self.map.removeKey(newKey, lib, lib, gotState, state);
            if (hasDELSideEffect(key, lookup)) {
                callNode.executeObject(frame, lookup.execute(key, __DEL__), key);
            }
            if (hasDELSideEffect(value, lookup)) {
                callNode.executeObject(frame, lookup.execute(value, __DEL__), value);
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
                        @Shared("lookupDel") @Cached LookupInheritedAttributeNode.Dynamic lookup,
                        @Shared("callDel") @Cached CallUnaryMethodNode callNode) {
            if (self.map.size() == 0) {
                return self;
            }
            Object[] entries = new Object[self.map.size() * 2];
            MapCursor<DictKey, Object> cursor = self.map.getEntries();
            int i = 0;
            while (advance(cursor)) {
                Object key = getKey(cursor);
                Object value = getValue(cursor);
                entries[i++] = hasDELSideEffect(key, lookup) ? key : null;
                entries[i++] = hasDELSideEffect(value, lookup) ? value : null;
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

    @ExportMessage
    @Override
    public HashingStorage copy() {
        return new EconomicMapStorage(this);
    }

    @ExportMessage
    public static class EqualsWithState {
        @TruffleBoundary
        @Specialization
        static boolean equalSameType(EconomicMapStorage self, EconomicMapStorage other, ThreadState state,
                        @Shared("findProfile") @Cached ConditionProfile findProfile,
                        @Shared("gotState") @Cached ConditionProfile gotState,
                        @Shared("plib") @CachedLibrary(limit = "3") PythonObjectLibrary lib) {
            if (self.map.size() != other.map.size()) {
                return false;
            }
            MapCursor<DictKey, Object> cursor = self.map.getEntries();
            while (advance(cursor)) {
                Object otherValue = other.map.get(getDictKey(cursor), lib, lib, findProfile, gotState, state);
                if (otherValue != null && !lib.equalsWithState(otherValue, getValue(cursor), lib, state)) {
                    return false;
                }
            }
            return true;
        }

        @TruffleBoundary
        @Specialization
        static boolean equalGeneric(EconomicMapStorage self, HashingStorage other, ThreadState state,
                        @CachedLibrary("self") HashingStorageLibrary selflib,
                        @Shared("otherHLib") @CachedLibrary(limit = "2") HashingStorageLibrary otherlib,
                        @Shared("plib") @CachedLibrary(limit = "3") PythonObjectLibrary lib) {
            if (self.map.size() != otherlib.length(other)) {
                return false;
            }
            MapCursor<DictKey, Object> cursor = self.map.getEntries();
            while (advance(cursor)) {
                Object otherValue = selflib.getItemWithState(self, getKey(cursor), state);
                if (otherValue != null && !lib.equalsWithState(otherValue, getValue(cursor), lib, state)) {
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
                        @Shared("findProfile") @Cached ConditionProfile findProfile,
                        @Shared("gotState") @Cached ConditionProfile gotState,
                        @Shared("plib") @CachedLibrary(limit = "3") PythonObjectLibrary lib) {
            int size = self.map.size();
            int size2 = other.map.size();
            if (size > size2) {
                return 1;
            }
            MapCursor<DictKey, Object> cursor = self.map.getEntries();
            while (advance(cursor)) {
                if (!other.map.containsKey(getDictKey(cursor), lib, lib, findProfile, gotState, state)) {
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
        @Specialization
        static int compareGeneric(EconomicMapStorage self, HashingStorage other, ThreadState state,
                        @Shared("otherHLib") @CachedLibrary(limit = "2") HashingStorageLibrary lib) {
            int size = self.map.size();
            int length = lib.length(other);
            if (size > length) {
                return 1;
            }
            MapCursor<DictKey, Object> cursor = self.map.getEntries();
            while (advance(cursor)) {
                if (!lib.hasKeyWithState(other, getKey(cursor), state)) {
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
                        @Shared("findProfile") @Cached ConditionProfile findProfile,
                        @Shared("gotState") @Cached ConditionProfile gotState,
                        @Shared("plib") @CachedLibrary(limit = "3") PythonObjectLibrary lib) {
            EconomicMapStorage result = EconomicMapStorage.create();
            MapCursor<DictKey, Object> cursor = self.map.getEntries();
            while (advance(cursor)) {
                if (other.map.containsKey(getDictKey(cursor), lib, lib, findProfile, gotState, state)) {
                    result.map.put(getDictKey(cursor), getValue(cursor));
                }
            }
            return result;
        }

        @TruffleBoundary
        @Specialization
        static HashingStorage intersectGeneric(EconomicMapStorage self, HashingStorage other, @SuppressWarnings("unused") ThreadState state,
                        @Shared("otherHLib") @CachedLibrary(limit = "2") HashingStorageLibrary hlib) {
            EconomicMapStorage result = EconomicMapStorage.create();
            MapCursor<DictKey, Object> cursor = self.map.getEntries();
            while (advance(cursor)) {
                if (hlib.hasKey(other, getKey(cursor))) {
                    result.map.put(getDictKey(cursor), getValue(cursor));
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
                        @Shared("findProfile") @Cached ConditionProfile findProfile,
                        @Shared("gotState") @Cached ConditionProfile gotState,
                        @Shared("plib") @CachedLibrary(limit = "3") PythonObjectLibrary lib) {
            EconomicMapStorage result = EconomicMapStorage.create();
            MapCursor<DictKey, Object> cursor = self.map.getEntries();
            while (advance(cursor)) {
                if (!other.map.containsKey(getDictKey(cursor), lib, lib, findProfile, gotState, state)) {
                    result.map.put(getDictKey(cursor), getValue(cursor));
                }
            }
            return result;
        }

        @TruffleBoundary
        @Specialization
        static HashingStorage diffGeneric(EconomicMapStorage self, HashingStorage other, @SuppressWarnings("unused") ThreadState state,
                        @Shared("otherHLib") @CachedLibrary(limit = "2") HashingStorageLibrary hlib) {
            EconomicMapStorage result = EconomicMapStorage.create();
            MapCursor<DictKey, Object> cursor = self.map.getEntries();
            while (advance(cursor)) {
                if (!hlib.hasKey(other, getKey(cursor))) {
                    result.map.put(getDictKey(cursor), getValue(cursor));
                }
            }
            return result;
        }
    }

    @ExportMessage
    public HashingStorage xor(HashingStorage other,
                    @CachedLibrary("this") HashingStorageLibrary selfLib,
                    @Shared("otherHLib") @CachedLibrary(limit = "2") HashingStorageLibrary otherLib) {
        HashingStorage a = selfLib.diff(this, other);
        HashingStorage b = otherLib.diff(other, this);
        return selfLib.union(a, b);
    }

    @ExportMessage
    @Override
    public HashingStorageIterable<Object> keys() {
        return new HashingStorageIterable<>(new KeysIterator(map.getKeys().iterator()));
    }

    @ExportMessage
    @Override
    public HashingStorageIterable<Object> reverseKeys() {
        return new HashingStorageIterable<>(new KeysIterator(map.reverseKeyIterator()));
    }

    static final class KeysIterator implements Iterator<Object> {
        private final Iterator<DictKey> keysIterator;

        KeysIterator(Iterator<DictKey> iter) {
            this.keysIterator = iter;
        }

        public Iterator<DictKey> getKeysIterator() {
            return keysIterator;
        }

        @Override
        public boolean hasNext() {
            return keysIterator.hasNext();
        }

        @Override
        public Object next() {
            return keysIterator.next().value;
        }
    }

    protected void setValue(DictKey key, Object value, PythonObjectLibrary lib, ConditionProfile findProfile, ConditionProfile gotState, ThreadState state) {
        this.map.put(key, value, lib, lib, findProfile, gotState, state);
    }

    protected HashingStorageIterable<DictKey> dictKeys() {
        return new HashingStorageIterable<>(new DictKeysIterator(map.getKeys().iterator()));
    }

    static final class DictKeysIterator implements Iterator<DictKey> {
        private final Iterator<DictKey> keysIterator;

        DictKeysIterator(Iterator<DictKey> iter) {
            this.keysIterator = iter;
        }

        public Iterator<DictKey> getKeysIterator() {
            return keysIterator;
        }

        @TruffleBoundary
        @Override
        public boolean hasNext() {
            return keysIterator.hasNext();
        }

        @TruffleBoundary
        @Override
        public DictKey next() {
            return keysIterator.next();
        }
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        StringBuilder builder = new StringBuilder();
        builder.append("map(size=").append(length()).append(", {");
        String sep = "";
        MapCursor<DictKey, Object> cursor = map.getEntries();
        while (advance(cursor)) {
            builder.append(sep);
            builder.append("(").append(getKey(cursor)).append(",").append(getValue(cursor)).append(")");
            sep = ",";
        }
        builder.append("})");
        return builder.toString();
    }
}
