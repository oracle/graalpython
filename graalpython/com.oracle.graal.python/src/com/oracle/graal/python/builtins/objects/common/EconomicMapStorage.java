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

import java.util.LinkedHashMap;
import java.util.Map.Entry;

import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary.ForEachNode;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary.HashingStorageIterable;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageSetItem;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.SpecializedSetStringKey;
import com.oracle.graal.python.builtins.objects.common.ObjectHashMap.DictKey;
import com.oracle.graal.python.builtins.objects.common.ObjectHashMap.MapCursor;
import com.oracle.graal.python.builtins.objects.common.ObjectHashMap.PutNode;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PArguments.ThreadState;
import com.oracle.graal.python.lib.PyObjectHashNode;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
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

    public static EconomicMapStorage createWithSideEffects() {
        return new EconomicMapStorage(4, true);
    }

    public static EconomicMapStorage create(int initialCapacity) {
        return new EconomicMapStorage(initialCapacity, false);
    }

    final ObjectHashMap map;

    private EconomicMapStorage(int initialCapacity, boolean hasSideEffects) {
        this.map = new ObjectHashMap(initialCapacity, hasSideEffects);
    }

    private EconomicMapStorage() {
        this(4, false);
    }

    private EconomicMapStorage(ObjectHashMap original) {
        this.map = original.copy();
    }

    public static EconomicMapStorage create(LinkedHashMap<String, Object> map) {
        EconomicMapStorage result = new EconomicMapStorage(map.size(), false);
        putAllUncached(map, result);
        return result;
    }

    @ExportMessage
    @Override
    public int length() {
        return map.size();
    }

    @ExportMessage
    protected static boolean hasSideEffect(EconomicMapStorage self) {
        return self.map.hasSideEffect();
    }

    protected static void convertToSideEffectMap(EconomicMapStorage self) {
        self.map.setSideEffectingKeysFlag();
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
                    @CachedLibrary("this") HashingStorageLibrary thisLib,
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
                        @Exclusive @Cached HashingStorageSetItem setItem) {
            HashingStorage result = other;
            MapCursor cursor = self.map.getEntries();
            // get/put may throw, but we ignore that small inaccuracy
            final int size = self.map.size();
            loopProfile.profileCounted(size);
            LoopNode.reportLoopCount(thisLib, size);
            while (loopProfile.inject(advance(cursor))) {
                result = setItem.execute(null, result, getKey(cursor), getValue(cursor));
            }
            return result;
        }
    }

    @ExportMessage
    HashingStorage delItemWithState(Object key, ThreadState state,
                    @Cached ObjectHashMap.RemoveNode removeNode,
                    @Cached PyObjectHashNode hashNode,
                    @Cached ConditionProfile gotState) {
        VirtualFrame frame = gotState.profile(state == null) ? null : PArguments.frameForCall(state);
        removeNode.remove(frame, map, key, hashNode.execute(frame, key));
        return this;
    }

    void clear() {
        map.clear();
    }

    public HashingStorage copy() {
        return new EconomicMapStorage(this.map);
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
            VirtualFrame frame = state == null ? null : PArguments.frameForCall(state);
            while (loopProfile.inject(advance(cursor))) {
                if (getNode.get(frame, otherMap, getDictKey(cursor)) != null) {
                    putNode.put(frame, resultMap, getDictKey(cursor), getValue(cursor));
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
            VirtualFrame frame = state == null ? null : PArguments.frameForCall(state);
            while (loopProfile.inject(advance(cursor))) {
                if (hlib.hasKey(other, getKey(cursor))) {
                    putNode.put(frame, resultMap, getDictKey(cursor), getValue(cursor));
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
            VirtualFrame frame = state == null ? null : PArguments.frameForCall(state);
            while (loopProfile.inject(advance(cursor))) {
                if (getNode.get(frame, otherMap, getDictKey(cursor)) == null) {
                    putNode.put(frame, resultMap, getDictKey(cursor), getValue(cursor));
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
            VirtualFrame frame = state == null ? null : PArguments.frameForCall(state);
            while (loopProfile.profile(advance(cursor))) {
                if (!hlib.hasKey(other, getKey(cursor))) {
                    putNode.put(frame, resultMap, getDictKey(cursor), getValue(cursor));
                }
            }
            return result;
        }
    }

    @ExportMessage
    @Override
    public HashingStorageIterable<Object> keys() {
        return map.keys();
    }

    protected void setValueForAllKeys(VirtualFrame frame, Object value, PutNode putNode, ConditionProfile hasFrame, LoopConditionProfile loopProfile) {
        MapCursor cursor = map.getEntries();
        final int size = map.size();
        loopProfile.profileCounted(size);
        LoopNode.reportLoopCount(putNode, size);
        while (loopProfile.inject(advance(cursor))) {
            putNode.put(frame, map, getDictKey(cursor), value);
        }
    }

    @TruffleBoundary
    public void putUncached(TruffleString key, Object value) {
        ObjectHashMapFactory.PutNodeGen.getUncached().put(null, this.map, key, PyObjectHashNode.hash(key, HashCodeNode.getUncached()), value);
    }

    @TruffleBoundary
    private static void putAllUncached(LinkedHashMap<String, Object> map, EconomicMapStorage result) {
        for (Entry<String, Object> entry : map.entrySet()) {
            result.putUncached(TruffleString.fromJavaStringUncached(entry.getKey(), TS_ENCODING), entry.getValue());
        }
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

    @GenerateUncached
    public static abstract class EconomicMapSetStringKey extends SpecializedSetStringKey {
        @Specialization
        static void doIt(HashingStorage self, TruffleString key, Object value,
                        @Cached PyObjectHashNode hashNode,
                        @Cached PutNode putNode) {
            putNode.put(null, ((EconomicMapStorage) self).map, key, hashNode.execute(null, key), value);
        }
    }
}
