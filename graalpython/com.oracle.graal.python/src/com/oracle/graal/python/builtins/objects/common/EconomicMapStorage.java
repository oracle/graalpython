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

import static com.oracle.graal.python.nodes.SpecialMethodNames.T___EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___HASH__;
import static com.oracle.graal.python.nodes.truffle.TruffleStringMigrationHelpers.assertNoJavaString;

import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary.ForEachNode;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary.HashingStorageIterable;
import com.oracle.graal.python.builtins.objects.common.ObjectHashMap.DictKey;
import com.oracle.graal.python.builtins.objects.common.ObjectHashMap.MapCursor;
import com.oracle.graal.python.builtins.objects.common.ObjectHashMap.PutNode;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PArguments.ThreadState;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.str.StringNodes.StringMaterializeNode;
import com.oracle.graal.python.lib.PyObjectHashNode;
import com.oracle.graal.python.lib.PyObjectRichCompareBool;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.LoopConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.HashCodeNode;

@ExportLibrary(HashingStorageLibrary.class)
public class EconomicMapStorage extends HashingStorage {

    public static EconomicMapStorage create() {
        return new EconomicMapStorage();
    }

    public static EconomicMapStorage create(int initialCapacity) {
        return new EconomicMapStorage(initialCapacity, false);
    }

    private final ObjectHashMap map;

    private EconomicMapStorage(int initialCapacity, boolean hasSideEffects) {
        this.map = new ObjectHashMap(initialCapacity, hasSideEffects);
    }

    private EconomicMapStorage() {
        this(4, false);
    }

    private EconomicMapStorage(ObjectHashMap original) {
        this.map = original.copy();
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
        static Object getItemTruffleString(EconomicMapStorage self, TruffleString key, ThreadState state,
                        @Shared("tsHash") @Cached TruffleString.HashCodeNode hashCodeNode,
                        @Shared("getNode") @Cached ObjectHashMap.GetNode getNode) {
            return getNode.get(state, self.map, key, PyObjectHashNode.hash(key, hashCodeNode));
        }

        @Specialization(guards = {"isBuiltinString(key, isBuiltinClassProfile)"}, limit = "1")
        static Object getItemPString(EconomicMapStorage self, PString key, ThreadState state,
                        @Shared("stringMaterialize") @Cached StringMaterializeNode stringMaterializeNode,
                        @Shared("tsHash") @Cached TruffleString.HashCodeNode hashCodeNode,
                        @Shared("getNode") @Cached ObjectHashMap.GetNode getNode,
                        @Shared("builtinProfile") @Cached @SuppressWarnings("unused") IsBuiltinClassProfile isBuiltinClassProfile) {
            final TruffleString k = stringMaterializeNode.execute(key);
            return getItemTruffleString(self, k, state, hashCodeNode, getNode);
        }

        @Specialization(replaces = {"getItemTruffleString", "getItemPString"})
        static Object getItemGeneric(EconomicMapStorage self, Object key, ThreadState state,
                        @Shared("hashNode") @Cached PyObjectHashNode hashNode,
                        @Shared("getNode") @Cached ObjectHashMap.GetNode getNode,
                        @Shared("gotState") @Cached ConditionProfile gotState) {
            VirtualFrame frame = gotState.profile(state == null) ? null : PArguments.frameForCall(state);
            return getNode.get(state, self.map, key, hashNode.execute(frame, key));
        }
    }

    @ExportMessage
    protected static boolean hasSideEffect(EconomicMapStorage self) {
        return self.map.hasSideEffect();
    }

    protected static void convertToSideEffectMap(EconomicMapStorage self) {
        self.map.setSideEffectingKeysFlag();
    }

    @SuppressWarnings("unused")
    @ExportMessage
    @ImportStatic(PGuards.class)
    static class SetItemWithState {

        static boolean isBuiltin(PythonObject o, IsBuiltinClassProfile p) {
            return PGuards.isBuiltinObject(o) || p.profileIsAnyBuiltinObject(o);
        }

        static boolean maySideEffect(PythonObject o, LookupInheritedAttributeNode.Dynamic lookup) {
            return !PGuards.isBuiltinFunction(lookup.execute(o, T___EQ__)) || !PGuards.isBuiltinFunction(lookup.execute(o, T___HASH__));
        }

        @Specialization
        static HashingStorage setItemTruffleString(EconomicMapStorage self, TruffleString key, Object value, ThreadState state,
                        @Shared("tsHash") @Cached TruffleString.HashCodeNode hashCodeNode,
                        @Shared("putNode") @Cached ObjectHashMap.PutNode putNode,
                        @Shared("gotState") @Cached ConditionProfile gotState) {
            VirtualFrame frame = gotState.profile(state == null) ? null : PArguments.frameForCall(state);
            putNode.put(state, self.map, key, PyObjectHashNode.hash(key, hashCodeNode), assertNoJavaString(value));
            return self;
        }

        @Specialization(guards = {"isBuiltinString(key, isBuiltinClassProfile)"}, limit = "1")
        static HashingStorage setItemPString(EconomicMapStorage self, PString key, Object value, ThreadState state,
                        @Shared("stringMaterialize") @Cached StringMaterializeNode stringMaterializeNode,
                        @Shared("tsHash") @Cached TruffleString.HashCodeNode hashCodeNode,
                        @Shared("putNode") @Cached ObjectHashMap.PutNode putNode,
                        @Shared("gotState") @Cached ConditionProfile gotState,
                        @Shared("builtinProfile") @Cached IsBuiltinClassProfile isBuiltinClassProfile) {
            final TruffleString k = stringMaterializeNode.execute(key);
            return setItemTruffleString(self, k, value, state, hashCodeNode, putNode, gotState);
        }

        @Specialization(guards = {"!hasSideEffect(self)", "!isBuiltin(key,builtinProfile) || !isBuiltin(value,builtinProfile)",
                        "maySideEffect(key, lookup) || maySideEffect(value, lookup)"}, limit = "1")
        static HashingStorage setItemPythonObjectWithSideEffect(EconomicMapStorage self, PythonObject key, PythonObject value, ThreadState state,
                        @Shared("putNode") @Cached ObjectHashMap.PutNode putNode,
                        @Shared("hashNode") @Cached PyObjectHashNode hashNode,
                        @Shared("lookup") @Cached LookupInheritedAttributeNode.Dynamic lookup,
                        @Shared("builtinProfile") @Cached IsBuiltinClassProfile builtinProfile,
                        @Shared("gotState") @Cached ConditionProfile gotState) {
            convertToSideEffectMap(self);
            return setItemGeneric(self, key, value, state, putNode, hashNode, gotState);
        }

        @Specialization(guards = {"!hasSideEffect(self)", "!isBuiltin(key,builtinProfile)", "maySideEffect(key, lookup)"}, limit = "1")
        static HashingStorage setItemPythonObjectWithSideEffect(EconomicMapStorage self, PythonObject key, Object value, ThreadState state,
                        @Shared("putNode") @Cached ObjectHashMap.PutNode putNode,
                        @Shared("hashNode") @Cached PyObjectHashNode hashNode,
                        @Shared("lookup") @Cached LookupInheritedAttributeNode.Dynamic lookup,
                        @Shared("builtinProfile") @Cached IsBuiltinClassProfile builtinProfile,
                        @Shared("gotState") @Cached ConditionProfile gotState) {
            convertToSideEffectMap(self);
            return setItemGeneric(self, key, value, state, putNode, hashNode, gotState);
        }

        @Specialization(guards = {"!hasSideEffect(self)", "!isBuiltin(value,builtinProfile)", "maySideEffect(value, lookup)"}, limit = "1")
        static HashingStorage setItemPythonObjectWithSideEffect(EconomicMapStorage self, Object key, PythonObject value, ThreadState state,
                        @Shared("putNode") @Cached ObjectHashMap.PutNode putNode,
                        @Shared("hashNode") @Cached PyObjectHashNode hashNode,
                        @Shared("lookup") @Cached LookupInheritedAttributeNode.Dynamic lookup,
                        @Shared("builtinProfile") @Cached IsBuiltinClassProfile builtinProfile,
                        @Shared("gotState") @Cached ConditionProfile gotState) {
            convertToSideEffectMap(self);
            return setItemGeneric(self, key, value, state, putNode, hashNode, gotState);
        }

        @Specialization(replaces = {"setItemPString", "setItemTruffleString"})
        static HashingStorage setItemGeneric(EconomicMapStorage self, Object key, Object value, ThreadState state,
                        @Shared("putNode") @Cached ObjectHashMap.PutNode putNode,
                        @Shared("hashNode") @Cached PyObjectHashNode hashNode,
                        @Shared("gotState") @Cached ConditionProfile gotState) {
            convertToSideEffectMap(self);
            VirtualFrame frame = gotState.profile(state == null) ? null : PArguments.frameForCall(state);
            putNode.put(state, self.map, key, hashNode.execute(frame, key), assertNoJavaString(value));
            return self;
        }
    }

    static boolean advance(MapCursor cursor) {
        return cursor.advance();
    }

    static DictKey getDictKey(MapCursor cursor) {
        return cursor.getKey();
    }

    static Object getKey(MapCursor cursor) {
        return getDictKey(cursor).getValue();
    }

    static Object getValue(MapCursor cursor) {
        return cursor.getValue();
    }

    @ExportMessage
    Object forEachUntyped(ForEachNode<Object> node, Object arg,
                    @Shared("selfEntriesLoop") @Cached LoopConditionProfile loopProfile) {
        return map.forEachUntyped(node, arg, loopProfile);
    }

    @ExportMessage
    public static class AddAllToOther {

        protected static boolean hasSideEffect(EconomicMapStorage self, EconomicMapStorage other) {
            return !other.map.hasSideEffect() && self.map.hasSideEffect();
        }

        @Specialization(guards = "hasSideEffect(self, other)")
        static HashingStorage toSameTypeSideEffect(EconomicMapStorage self, EconomicMapStorage other,
                        @CachedLibrary("self") HashingStorageLibrary thisLib,
                        @Shared("selfEntriesLoop") @Cached LoopConditionProfile loopProfile,
                        @Shared("putNode") @Cached ObjectHashMap.PutNode putNode) {
            convertToSideEffectMap(other);
            return toSameType(self, other, thisLib, loopProfile, putNode);
        }

        @Specialization(guards = "!hasSideEffect(self, other)")
        static HashingStorage toSameType(EconomicMapStorage self, EconomicMapStorage other,
                        @CachedLibrary("self") HashingStorageLibrary thisLib,
                        @Shared("selfEntriesLoop") @Cached LoopConditionProfile loopProfile,
                        @Shared("putNode") @Cached ObjectHashMap.PutNode putNode) {
            MapCursor cursor = self.map.getEntries();
            // get/put may throw, but we ignore that small inaccuracy
            final int size = self.map.size();
            loopProfile.profileCounted(size);
            LoopNode.reportLoopCount(thisLib, size);
            while (loopProfile.inject(advance(cursor))) {
                putNode.put(null, other.map, cursor.getKey().getValue(), cursor.getKey().getPythonHash(), cursor.getValue());
            }
            return other;
        }

        @Specialization
        static HashingStorage generic(EconomicMapStorage self, HashingStorage other,
                        @CachedLibrary("self") HashingStorageLibrary thisLib,
                        @Shared("selfEntriesLoop") @Cached LoopConditionProfile loopProfile,
                        @Shared("otherHLib") @CachedLibrary(limit = "2") HashingStorageLibrary lib) {
            HashingStorage result = other;
            MapCursor cursor = self.map.getEntries();
            // get/put may throw, but we ignore that small inaccuracy
            final int size = self.map.size();
            loopProfile.profileCounted(size);
            LoopNode.reportLoopCount(thisLib, size);
            while (loopProfile.inject(advance(cursor))) {
                result = lib.setItem(result, getKey(cursor), getValue(cursor));
            }
            return result;
        }
    }

    @ExportMessage
    HashingStorage delItemWithState(Object key, ThreadState state,
                    @Cached ObjectHashMap.RemoveNode removeNode,
                    @Shared("hashNode") @Cached PyObjectHashNode hashNode,
                    @Shared("gotState") @Cached ConditionProfile gotState) {
        VirtualFrame frame = gotState.profile(state == null) ? null : PArguments.frameForCall(state);
        removeNode.remove(state, map, key, hashNode.execute(frame, key));
        return this;
    }

    @ExportMessage
    @Override
    HashingStorage clear() {
        map.clear();
        return this;
    }

    @ExportMessage
    @Override
    public HashingStorage copy() {
        return new EconomicMapStorage(this.map);
    }

    @ExportMessage
    public static class EqualsWithState {
        @Specialization
        static boolean equalSameType(EconomicMapStorage self, EconomicMapStorage other, ThreadState state,
                        @CachedLibrary("self") HashingStorageLibrary thisLib,
                        @Shared("getNode") @Cached ObjectHashMap.GetNode getNode,
                        @Shared("eqNode") @Cached PyObjectRichCompareBool.EqNode eqNode,
                        @Shared("selfEntriesLoop") @Cached LoopConditionProfile loopProfile,
                        @Shared("selfEntriesLoopExit") @Cached LoopConditionProfile earlyExitProfile,
                        @Shared("gotState") @Cached ConditionProfile gotState) {
            if (self.map.size() != other.map.size()) {
                return false;
            }
            VirtualFrame frame = gotState.profile(state == null) ? null : PArguments.frameForCall(state);
            MapCursor cursor = self.map.getEntries();
            int counter = 0;
            try {
                while (loopProfile.profile(advance(cursor))) {
                    if (CompilerDirectives.hasNextTier()) {
                        counter++;
                    }
                    Object otherValue = getNode.get(state, other.map, cursor.getKey());
                    if (earlyExitProfile.profile(!(otherValue == null || !eqNode.execute(frame, otherValue, getValue(cursor))))) {
                        // if->continue such that the "true" count of the profile represents the
                        // loop iterations and the "false" count the early exit
                        continue;
                    }
                    return false;
                }
            } finally {
                if (counter != 0) {
                    LoopNode.reportLoopCount(thisLib, counter);
                }
            }
            return true;
        }

        @Specialization
        static boolean equalGeneric(EconomicMapStorage self, HashingStorage other, ThreadState state,
                        @CachedLibrary("self") HashingStorageLibrary thisLib,
                        @Shared("otherHLib") @CachedLibrary(limit = "2") HashingStorageLibrary otherlib,
                        @Shared("selfEntriesLoop") @Cached LoopConditionProfile loopProfile,
                        @Shared("selfEntriesLoopExit") @Cached LoopConditionProfile earlyExitProfile,
                        @Shared("gotState") @Cached ConditionProfile gotState,
                        @Shared("eqNode") @Cached PyObjectRichCompareBool.EqNode eqNode) {
            if (self.map.size() != otherlib.length(other)) {
                return false;
            }
            VirtualFrame frame = gotState.profile(state == null) ? null : PArguments.frameForCall(state);
            MapCursor cursor = self.map.getEntries();
            int counter = 0;
            try {
                while (loopProfile.profile(advance(cursor))) {
                    if (CompilerDirectives.hasNextTier()) {
                        counter++;
                    }
                    Object otherValue = otherlib.getItemWithState(other, getKey(cursor), state);
                    if (earlyExitProfile.profile(!(otherValue == null || !eqNode.execute(frame, otherValue, getValue(cursor))))) {
                        // if->continue such that the "true" count of the profile represents the
                        // loop iterations and the "false" count the early exit
                        continue;
                    }
                    return false;
                }
            } finally {
                if (counter != 0) {
                    LoopNode.reportLoopCount(thisLib, counter);
                }
            }
            return true;
        }
    }

    @ExportMessage
    public static class CompareKeysWithState {
        @Specialization
        static int compareSameType(EconomicMapStorage self, EconomicMapStorage other, ThreadState state,
                        @CachedLibrary("self") HashingStorageLibrary thisLib,
                        @Shared("selfEntriesLoop") @Cached LoopConditionProfile loopProfile,
                        @Shared("selfEntriesLoopExit") @Cached LoopConditionProfile earlyExitProfile,
                        @Shared("getNode") @Cached ObjectHashMap.GetNode getNode) {
            int size = self.map.size();
            int size2 = other.map.size();
            if (size > size2) {
                return 1;
            }
            MapCursor cursor = self.map.getEntries();
            int counter = 0;
            try {
                while (loopProfile.profile(advance(cursor))) {
                    if (CompilerDirectives.hasNextTier()) {
                        counter++;
                    }
                    if (earlyExitProfile.profile(getNode.get(state, other.map, getDictKey(cursor)) != null)) {
                        continue;
                    }
                    return 1;
                }
            } finally {
                if (counter != 0) {
                    LoopNode.reportLoopCount(thisLib, counter);
                }
            }
            if (size == size2) {
                return 0;
            } else {
                return -1;
            }
        }

        @Specialization
        static int compareGeneric(EconomicMapStorage self, HashingStorage other, ThreadState state,
                        @CachedLibrary("self") HashingStorageLibrary thisLib,
                        @Shared("selfEntriesLoop") @Cached LoopConditionProfile loopProfile,
                        @Shared("selfEntriesLoopExit") @Cached LoopConditionProfile earlyExitProfile,
                        @Shared("otherHLib") @CachedLibrary(limit = "2") HashingStorageLibrary lib) {
            int size = self.map.size();
            int length = lib.length(other);
            if (size > length) {
                return 1;
            }
            MapCursor cursor = self.map.getEntries();
            int counter = 0;
            try {
                while (loopProfile.inject(advance(cursor))) {
                    if (CompilerDirectives.hasNextTier()) {
                        counter++;
                    }
                    if (earlyExitProfile.profile(lib.hasKeyWithState(other, getKey(cursor), state))) {
                        continue;
                    }
                    return 1;
                }
            } finally {
                if (counter != 0) {
                    LoopNode.reportLoopCount(thisLib, counter);
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
        @Specialization
        static HashingStorage intersectSameType(EconomicMapStorage self, EconomicMapStorage other, ThreadState state,
                        @CachedLibrary("self") HashingStorageLibrary thisLib,
                        @Shared("putNode") @Cached ObjectHashMap.PutNode putNode,
                        @Shared("getNode") @Cached ObjectHashMap.GetNode getNode,
                        @Shared("selfEntriesLoop") @Cached LoopConditionProfile loopProfile,
                        @Shared("setSideEffect") @Cached BranchProfile setSideEffectFlag) {
            EconomicMapStorage result = EconomicMapStorage.create();
            ObjectHashMap resultMap = result.map;
            ObjectHashMap otherMap = other.map;
            if (self.map.hasSideEffect() || otherMap.hasSideEffect()) {
                setSideEffectFlag.enter();
                resultMap.setSideEffectingKeysFlag();
            }
            MapCursor cursor = self.map.getEntries();
            // get/put may throw, but we ignore that small inaccuracy
            final int size = self.map.size();
            loopProfile.profileCounted(size);
            LoopNode.reportLoopCount(thisLib, size);
            while (loopProfile.inject(advance(cursor))) {
                if (getNode.get(state, otherMap, getDictKey(cursor)) != null) {
                    putNode.put(state, resultMap, getDictKey(cursor), getValue(cursor));
                }
            }
            return result;
        }

        @Specialization
        static HashingStorage intersectGeneric(EconomicMapStorage self, HashingStorage other, @SuppressWarnings("unused") ThreadState state,
                        @CachedLibrary("self") HashingStorageLibrary thisLib,
                        @Shared("putNode") @Cached ObjectHashMap.PutNode putNode,
                        @Shared("selfEntriesLoop") @Cached LoopConditionProfile loopProfile,
                        @Shared("otherHLib") @CachedLibrary(limit = "2") HashingStorageLibrary hlib) {
            EconomicMapStorage result = EconomicMapStorage.create();
            ObjectHashMap resultMap = result.map;
            resultMap.setSideEffectingKeysFlag();
            MapCursor cursor = self.map.getEntries();
            // get/put may throw, but we ignore that small inaccuracy
            final int size = self.map.size();
            loopProfile.profileCounted(size);
            LoopNode.reportLoopCount(thisLib, size);
            while (loopProfile.inject(advance(cursor))) {
                if (hlib.hasKey(other, getKey(cursor))) {
                    putNode.put(state, resultMap, getDictKey(cursor), getValue(cursor));
                }
            }
            return result;
        }
    }

    @ExportMessage
    public static class DiffWithState {
        @Specialization
        static HashingStorage diffSameType(EconomicMapStorage self, EconomicMapStorage other, ThreadState state,
                        @CachedLibrary("self") HashingStorageLibrary thisLib,
                        @Shared("putNode") @Cached ObjectHashMap.PutNode putNode,
                        @Shared("getNode") @Cached ObjectHashMap.GetNode getNode,
                        @Shared("selfEntriesLoop") @Cached LoopConditionProfile loopProfile,
                        @Shared("setSideEffect") @Cached BranchProfile setSideEffectFlag) {
            EconomicMapStorage result = EconomicMapStorage.create();
            ObjectHashMap resultMap = result.map;
            ObjectHashMap otherMap = other.map;
            if (self.map.hasSideEffect() || otherMap.hasSideEffect()) {
                setSideEffectFlag.enter();
                resultMap.setSideEffectingKeysFlag();
            }
            MapCursor cursor = self.map.getEntries();
            // get/put may throw, but we ignore that small inaccuracy
            final int size = self.map.size();
            loopProfile.profileCounted(size);
            LoopNode.reportLoopCount(thisLib, size);
            while (loopProfile.inject(advance(cursor))) {
                if (getNode.get(state, otherMap, getDictKey(cursor)) == null) {
                    putNode.put(state, resultMap, getDictKey(cursor), getValue(cursor));
                }
            }
            return result;
        }

        @Specialization
        static HashingStorage diffGeneric(EconomicMapStorage self, HashingStorage other, @SuppressWarnings("unused") ThreadState state,
                        @CachedLibrary("self") HashingStorageLibrary thisLib,
                        @Shared("putNode") @Cached ObjectHashMap.PutNode putNode,
                        @Shared("selfEntriesLoop") @Cached LoopConditionProfile loopProfile,
                        @Shared("otherHLib") @CachedLibrary(limit = "2") HashingStorageLibrary hlib) {
            EconomicMapStorage result = EconomicMapStorage.create();
            ObjectHashMap resultMap = result.map;
            resultMap.setSideEffectingKeysFlag();
            MapCursor cursor = self.map.getEntries();
            // get/put may throw, but we ignore that small inaccuracy
            final int size = self.map.size();
            loopProfile.profileCounted(size);
            LoopNode.reportLoopCount(thisLib, size);
            while (loopProfile.profile(advance(cursor))) {
                if (!hlib.hasKey(other, getKey(cursor))) {
                    putNode.put(state, resultMap, getDictKey(cursor), getValue(cursor));
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
        return map.keys();
    }

    @ExportMessage
    @Override
    public HashingStorageIterable<Object> reverseKeys() {
        return map.reverseKeys();
    }

    protected void setValueForAllKeys(VirtualFrame frame, Object value, PutNode putNode, ConditionProfile hasFrame, LoopConditionProfile loopProfile) {
        MapCursor cursor = map.getEntries();
        final int size = map.size();
        loopProfile.profileCounted(size);
        LoopNode.reportLoopCount(putNode, size);
        ThreadState state = PArguments.getThreadStateOrNull(frame, hasFrame);
        while (loopProfile.inject(advance(cursor))) {
            putNode.put(state, map, getDictKey(cursor), value);
        }
    }

    @TruffleBoundary
    public void putUncached(TruffleString key, Object value) {
        ObjectHashMapFactory.PutNodeGen.getUncached().put(null, this.map, key, PyObjectHashNode.hash(key, HashCodeNode.getUncached()), value);
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        StringBuilder builder = new StringBuilder();
        builder.append("map(size=").append(length()).append(", {");
        String sep = "";
        MapCursor cursor = map.getEntries();
        int i = 0;
        while (advance(cursor)) {
            i++;
            if (i >= 100) {
                builder.append("...");
                break;
            }
            builder.append(sep);
            builder.append("(").append(getKey(cursor)).append(",").append(getValue(cursor)).append(")");
            sep = ",";
        }
        builder.append("})");
        return builder.toString();
    }
}
