/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.EconomicMapStorage.EconomicMapSetStringKey;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.HashingStorageAddAllToOtherNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.HashingStorageCopyNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.HashingStorageDelItemNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.HashingStorageGetItemNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.HashingStorageGetIteratorNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.HashingStorageIteratorKeyNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.HashingStorageIteratorNextNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.HashingStorageIteratorValueNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.HashingStorageLenNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.HashingStorageSetItemNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.HashingStorageSetItemWithHashNodeGen;
import com.oracle.graal.python.builtins.objects.common.KeywordsStorage.GetKeywordsStorageItemNode;
import com.oracle.graal.python.builtins.objects.common.ObjectHashMap.PutNode;
import com.oracle.graal.python.lib.PyObjectHashNode;
import com.oracle.graal.python.lib.PyObjectRichCompareBool;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedLoopConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

public class HashingStorageNodes {

    public abstract static class HashingStorageGuards {
        private HashingStorageGuards() {
        }

        /**
         * If the storage may contain keys that may have side-effecting {@code __eq__}
         * implementation.
         */
        public static boolean mayHaveSideEffectingEq(PHashingCollection wrapper) {
            return wrapper.getDictStorage() instanceof EconomicMapStorage;
        }

        public static boolean mayHaveSideEffects(PHashingCollection wrapper) {
            HashingStorage s = wrapper.getDictStorage();
            return !(s instanceof EconomicMapStorage && ((EconomicMapStorage) s).map.hasSideEffect());
        }
    }

    @GenerateUncached
    public abstract static class HashingStorageGetItemWithHash extends Node {
        public abstract Object execute(Frame frame, HashingStorage self, Object key, long keyHash);

        @Specialization
        static Object economicMap(Frame frame, EconomicMapStorage self, Object key, long keyHash,
                        @Cached ObjectHashMap.GetNode getNode) {
            return getNode.execute(frame, self.map, key, keyHash);
        }

        @Specialization
        static Object dom(Frame frame, DynamicObjectStorage self, Object key, long keyHash,
                        @Bind("this") Node inliningTarget,
                        @Cached DynamicObjectStorage.GetItemNode getNode) {
            return getNode.execute(frame, inliningTarget, self, key, keyHash);
        }

        @Specialization
        @SuppressWarnings("unused")
        static Object empty(Frame frame, EmptyStorage self, Object key, long keyHash) {
            return null;
        }

        @Specialization
        static Object keywords(Frame frame, KeywordsStorage self, Object key, long keyHash,
                        @Bind("this") Node inliningTarget,
                        @Cached GetKeywordsStorageItemNode getNode) {
            return getNode.execute(frame, inliningTarget, self, key, keyHash);
        }
    }

    @GenerateUncached
    public abstract static class HashingStorageGetItem extends Node {
        public static boolean hasKeyUncached(HashingStorage storage, Object key) {
            return HashingStorageGetItemNodeGen.getUncached().execute(null, storage, key) != null;
        }

        @NeverDefault
        public static HashingStorageGetItem create() {
            return HashingStorageGetItemNodeGen.create();
        }

        public final boolean hasKey(HashingStorage self, TruffleString key) {
            return execute(null, self, key) != null;
        }

        public final boolean hasKey(Frame frame, HashingStorage self, Object key) {
            return execute(frame, self, key) != null;
        }

        public static Object executeUncached(HashingStorage storage, Object key) {
            return HashingStorageGetItemNodeGen.getUncached().execute(null, storage, key);
        }

        public final Object execute(HashingStorage self, TruffleString key) {
            // Shortcut for frequent usage with TruffleString. We do not need a frame in such case,
            // because the string's __hash__ does not need it. Some fast-paths avoid even invoking
            // __hash__ for string keys
            return execute(null, self, key);
        }

        public abstract Object execute(Frame frame, HashingStorage self, Object key);

        @Specialization
        static Object economicMap(Frame frame, EconomicMapStorage self, Object key,
                        @Shared("hash") @Cached PyObjectHashNode hashNode,
                        @Cached ObjectHashMap.GetNode getNode) {
            return getNode.execute(frame, self.map, key, hashNode.execute(frame, key));
        }

        @Specialization
        static Object dom(Frame frame, DynamicObjectStorage self, Object key,
                        @Bind("this") Node inliningTarget,
                        @Cached DynamicObjectStorage.GetItemNode getNode) {
            return getNode.execute(frame, inliningTarget, self, key, -1);
        }

        @Specialization
        static Object empty(Frame frame, @SuppressWarnings("unused") EmptyStorage self, Object key,
                        @Shared("hash") @Cached PyObjectHashNode hashNode) {
            // We must not omit the potentially side-effecting call to __hash__
            hashNode.execute(frame, key);
            return null;
        }

        @Specialization
        static Object keywords(Frame frame, KeywordsStorage self, Object key,
                        @Bind("this") Node inliningTarget,
                        @Cached GetKeywordsStorageItemNode getNode) {
            return getNode.execute(frame, inliningTarget, self, key, -1);
        }
    }

    abstract static class SpecializedSetStringKey extends Node {
        public abstract void execute(HashingStorage self, TruffleString key, Object value);
    }

    @GenerateUncached
    abstract static class HashingStorageToEconomicMap extends Node {
        abstract EconomicMapStorage execute(HashingStorage storage);

        @Specialization
        static EconomicMapStorage doEconomicMap(EconomicMapStorage s) {
            return s;
        }

        @Specialization
        static EconomicMapStorage doEmptyStorage(@SuppressWarnings("unused") EmptyStorage s) {
            return EconomicMapStorage.create();
        }

        @Specialization
        static EconomicMapStorage doDynamicObjectStorage(DynamicObjectStorage s,
                        @CachedLibrary(limit = "3") DynamicObjectLibrary dylib,
                        @Cached PyObjectHashNode hashNode,
                        @Cached ObjectHashMap.PutNode putNode) {
            // TODO: shouldn't we invalidate all MRO assumptions in this case?
            DynamicObject store = s.store;
            EconomicMapStorage result = EconomicMapStorage.create(dylib.getShape(store).getPropertyCount());
            ObjectHashMap resultMap = result.map;
            Object[] keys = dylib.getKeyArray(store);
            for (Object k : keys) {
                if (k instanceof TruffleString) {
                    Object v = dylib.getOrDefault(store, k, PNone.NO_VALUE);
                    if (v != PNone.NO_VALUE) {
                        putNode.put(null, resultMap, k, hashNode.execute(null, k), v);
                    }
                }
            }
            return result;
        }

        @Specialization
        static EconomicMapStorage doKeywords(KeywordsStorage s,
                        @Cached EconomicMapSetStringKey specializedPutNode) {
            EconomicMapStorage result = EconomicMapStorage.create(s.length());
            s.addAllTo(result, specializedPutNode);
            return result;
        }
    }

    @GenerateUncached
    @ImportStatic(PGuards.class)
    public abstract static class HashingStorageSetItemWithHash extends Node {

        @NeverDefault
        public static HashingStorageSetItemWithHash create() {
            return HashingStorageSetItemWithHashNodeGen.create();
        }

        public abstract HashingStorage execute(Frame frame, HashingStorage self, Object key, long keyHash, Object value);

        @Specialization
        static HashingStorage economicMap(Frame frame, EconomicMapStorage self, Object key, long keyHash, Object value,
                        @Bind("this") Node inliningTarget,
                        @Shared("isBuiltin") @Cached IsBuiltinObjectProfile profile,
                        @Shared("economicPut") @Cached ObjectHashMap.PutNode putNode) {
            putNode.execute(frame, self.map, key, keyHash, value);
            if (!self.map.hasSideEffect() && !PGuards.isBuiltinString(inliningTarget, key, profile)) {
                self.map.setSideEffectingKeysFlag();
            }
            return self;
        }

        @Specialization
        static HashingStorage empty(Frame frame, @SuppressWarnings("unused") EmptyStorage self, Object key, long keyHash, Object value,
                        @Bind("this") Node inliningTarget,
                        @Shared("isBuiltin") @Cached IsBuiltinObjectProfile profile,
                        @Exclusive @Cached ObjectHashMap.PutNode putNode) {
            // The ObjectHashMap.PutNode is @Exclusive because profiles for a put into a freshly new
            // allocated map can be quite different to profiles in the other situations when we are
            // putting into a map that already has or will have some more items in it
            // TODO: do we want to try DynamicObjectStorage if the key is a string?
            return economicMap(frame, EconomicMapStorage.create(1), key, keyHash, value, inliningTarget, profile, putNode);
        }

        @Specialization(guards = "!self.shouldTransitionOnPut()")
        static HashingStorage domStringKey(DynamicObjectStorage self, TruffleString key, @SuppressWarnings("unused") long keyHash, Object value,
                        @Bind("this") Node inliningTarget,
                        @Shared("invalidateMro") @Cached InlinedBranchProfile invalidateMroProfile,
                        @Shared("dylib") @CachedLibrary(limit = "3") DynamicObjectLibrary dylib) {
            self.setStringKey(key, value, dylib, inliningTarget, invalidateMroProfile);
            return self;
        }

        @Specialization(guards = {"!self.shouldTransitionOnPut()", "isBuiltinString(inliningTarget, key, profile)"})
        static HashingStorage domPStringKey(DynamicObjectStorage self, Object key, @SuppressWarnings("unused") long keyHash, Object value,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared("isBuiltin") @Cached IsBuiltinObjectProfile profile,
                        @Cached CastToTruffleStringNode castStr,
                        @Shared("invalidateMro") @Cached InlinedBranchProfile invalidateMroProfile,
                        @Shared("dylib") @CachedLibrary(limit = "3") DynamicObjectLibrary dylib) {
            self.setStringKey(castStr.execute(key), value, dylib, inliningTarget, invalidateMroProfile);
            return self;
        }

        @Specialization(guards = {"self.shouldTransitionOnPut() || !isBuiltinString(inliningTarget, key, profile)"})
        static HashingStorage domTransition(Frame frame, DynamicObjectStorage self, Object key, @SuppressWarnings("unused") long keyHash, Object value,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared("isBuiltin") @Cached IsBuiltinObjectProfile profile,
                        @Shared("dylib") @CachedLibrary(limit = "3") DynamicObjectLibrary dylib,
                        @Cached PyObjectHashNode hashNode,
                        @Shared("economicPut") @Cached ObjectHashMap.PutNode putNode) {
            EconomicMapStorage result = HashingStorageToEconomicMap.doDynamicObjectStorage(self, dylib, hashNode, putNode);
            putNode.put(frame, result.map, key, hashNode.execute(frame, key), value);
            return result;
        }

        @Specialization
        static HashingStorage keywords(Frame frame, KeywordsStorage self, Object key, long keyHash, Object value,
                        @Bind("this") Node inliningTarget,
                        @Shared("isBuiltin") @Cached IsBuiltinObjectProfile profile,
                        @Shared("economicPut") @Cached ObjectHashMap.PutNode putNode,
                        @Cached EconomicMapSetStringKey specializedPutNode) {
            // TODO: do we want to try DynamicObjectStorage if the key is a string?
            EconomicMapStorage result = EconomicMapStorage.create(self.length());
            self.addAllTo(result, specializedPutNode);
            return economicMap(frame, result, key, keyHash, value, inliningTarget, profile, putNode);
        }
    }

    /**
     * This unfortunately duplicates most of the logic in {@link HashingStorageSetItemWithHash}, but
     * here we want to avoid computing the hash if we happen to be setting an item into a storage
     * that does not need the Python hash at all.
     */
    @GenerateUncached
    @ImportStatic(PGuards.class)
    public abstract static class HashingStorageSetItem extends Node {

        @NeverDefault
        public static HashingStorageSetItem create() {
            return HashingStorageSetItemNodeGen.create();
        }

        public static HashingStorage executeUncached(HashingStorage storage, Object key, Object value) {
            return HashingStorageSetItemNodeGen.getUncached().execute(null, storage, key, value);
        }

        public final HashingStorage execute(HashingStorage self, TruffleString key, Object value) {
            // Shortcut for frequent usage with TruffleString. We do not need a frame in such case,
            // because the string's __hash__ does not need it. Some fast-paths avoid even invoking
            // __hash__ for string keys
            return execute(null, self, key, value);
        }

        public abstract HashingStorage execute(Frame frame, HashingStorage self, Object key, Object value);

        @Specialization
        static HashingStorage economicMap(Frame frame, EconomicMapStorage self, Object key, Object value,
                        @Bind("this") Node inliningTarget,
                        @Shared("isBuiltin") @Cached IsBuiltinObjectProfile profile,
                        @Shared("hash") @Cached PyObjectHashNode hashNode,
                        @Shared("economicPut") @Cached ObjectHashMap.PutNode putNode) {
            putNode.execute(frame, self.map, key, hashNode.execute(frame, key), value);
            if (!self.map.hasSideEffect() && !PGuards.isBuiltinString(inliningTarget, key, profile)) {
                self.map.setSideEffectingKeysFlag();
            }
            return self;
        }

        @Specialization
        static HashingStorage empty(Frame frame, @SuppressWarnings("unused") EmptyStorage self, Object key, Object value,
                        @Bind("this") Node inliningTarget,
                        @Shared("isBuiltin") @Cached IsBuiltinObjectProfile profile,
                        @Shared("hash") @Cached PyObjectHashNode hashNode,
                        @Exclusive @Cached ObjectHashMap.PutNode putNode) {
            // The ObjectHashMap.PutNode is @Exclusive because profiles for a put into a freshly new
            // allocated map can be quite different to profiles in the other situations when we are
            // putting into a map that already has or will have some more items in it
            // TODO: do we want to try DynamicObjectStorage if the key is a string?
            return economicMap(frame, EconomicMapStorage.create(1), key, value, inliningTarget, profile, hashNode, putNode);
        }

        @Specialization(guards = "!self.shouldTransitionOnPut()")
        static HashingStorage domStringKey(DynamicObjectStorage self, TruffleString key, Object value,
                        @Bind("this") Node inliningTarget,
                        @Shared("invalidateMro") @Cached InlinedBranchProfile invalidateMroProfile,
                        @Shared("dylib") @CachedLibrary(limit = "3") DynamicObjectLibrary dylib) {
            self.setStringKey(key, value, dylib, inliningTarget, invalidateMroProfile);
            return self;
        }

        @Specialization(guards = {"!self.shouldTransitionOnPut()", "isBuiltinString(inliningTarget, key, profile)"})
        static HashingStorage domPStringKey(DynamicObjectStorage self, Object key, Object value,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared("isBuiltin") @Cached IsBuiltinObjectProfile profile,
                        @Cached CastToTruffleStringNode castStr,
                        @Shared("invalidateMro") @Cached InlinedBranchProfile invalidateMroProfile,
                        @Shared("dylib") @CachedLibrary(limit = "3") DynamicObjectLibrary dylib) {
            self.setStringKey(castStr.execute(key), value, dylib, inliningTarget, invalidateMroProfile);
            return self;
        }

        @Specialization(guards = {"self.shouldTransitionOnPut() || !isBuiltinString(inliningTarget, key, profile)"})
        static HashingStorage domTransition(Frame frame, DynamicObjectStorage self, Object key, Object value,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared("isBuiltin") @Cached IsBuiltinObjectProfile profile,
                        @Shared("dylib") @CachedLibrary(limit = "3") DynamicObjectLibrary dylib,
                        @Shared("hash") @Cached PyObjectHashNode hashNode,
                        @Shared("economicPut") @Cached ObjectHashMap.PutNode putNode) {
            EconomicMapStorage result = HashingStorageToEconomicMap.doDynamicObjectStorage(self, dylib, hashNode, putNode);
            putNode.put(frame, result.map, key, hashNode.execute(frame, key), value);
            return result;
        }

        @Specialization
        static HashingStorage keywords(Frame frame, KeywordsStorage self, Object key, Object value,
                        @Bind("this") Node inliningTarget,
                        @Shared("hash") @Cached PyObjectHashNode hashNode,
                        @Shared("isBuiltin") @Cached IsBuiltinObjectProfile profile,
                        @Shared("economicPut") @Cached ObjectHashMap.PutNode putNode,
                        @Cached EconomicMapSetStringKey specializedPutNode) {
            // TODO: do we want to try DynamicObjectStorage if the key is a string?
            EconomicMapStorage result = EconomicMapStorage.create(self.length());
            self.addAllTo(result, specializedPutNode);
            return economicMap(frame, result, key, value, inliningTarget, profile, hashNode, putNode);
        }
    }

    @GenerateUncached
    @ImportStatic({PGuards.class})
    public abstract static class HashingStorageDelItem extends Node {
        public static void executeUncached(HashingStorage self, Object key, PHashingCollection toUpdate) {
            HashingStorageDelItemNodeGen.getUncached().executeWithAsserts(null, self, key, false, toUpdate);
        }

        public final void execute(HashingStorage self, TruffleString key, PHashingCollection toUpdate) {
            // Shortcut for frequent usage with TruffleString. We do not need a frame in such case,
            // because the string's __hash__ does not need it. Some fast-paths avoid even invoking
            // __hash__ for string keys
            executeWithAsserts(null, self, key, false, toUpdate);
        }

        public final void execute(Frame frame, HashingStorage self, Object key, PHashingCollection toUpdate) {
            executeWithAsserts(frame, self, key, false, toUpdate);
        }

        public final Object executePop(HashingStorage self, TruffleString key, PHashingCollection toUpdate) {
            return executeWithAsserts(null, self, key, true, toUpdate);
        }

        public final Object executePop(Frame frame, HashingStorage self, Object key, PHashingCollection toUpdate) {
            return executeWithAsserts(frame, self, key, true, toUpdate);
        }

        final Object executeWithAsserts(Frame frame, HashingStorage self, Object key, boolean isPop, PHashingCollection toUpdate) {
            assert toUpdate != null;
            CompilerAsserts.partialEvaluationConstant(isPop);
            return executeImpl(frame, self, key, isPop, toUpdate);
        }

        abstract Object executeImpl(Frame frame, HashingStorage self, Object key, boolean isPop, PHashingCollection toUpdate);

        @Specialization
        static Object economicMap(Frame frame, EconomicMapStorage self, Object key, boolean isPop, @SuppressWarnings("unused") PHashingCollection toUpdate,
                        @Shared("hash") @Cached PyObjectHashNode hashNode,
                        @Shared("economicRemove") @Cached ObjectHashMap.RemoveNode removeNode) {
            Object result = removeNode.execute(frame, self.map, key, hashNode.execute(frame, key));
            return isPop ? result : null;
        }

        @Specialization
        static Object domStringKey(DynamicObjectStorage self, TruffleString key, boolean isPop, @SuppressWarnings("unused") PHashingCollection toUpdate,
                        @Bind("this") Node inliningTarget,
                        @Shared("invalidateMro") @Cached InlinedBranchProfile invalidateMroProfile,
                        @Shared("dylib") @CachedLibrary(limit = "3") DynamicObjectLibrary dylib) {
            DynamicObject store = self.store;
            if (isPop) {
                Object val = dylib.getOrDefault(store, key, PNone.NO_VALUE);
                if (val == PNone.NO_VALUE) {
                    return null;
                } else {
                    dylib.put(store, key, PNone.NO_VALUE);
                    self.invalidateAttributeInMROFinalAssumption(key, inliningTarget, invalidateMroProfile);
                    return val;
                }
            } else {
                if (dylib.putIfPresent(store, key, PNone.NO_VALUE)) {
                    self.invalidateAttributeInMROFinalAssumption(key, inliningTarget, invalidateMroProfile);
                }
                return null;
            }
        }

        @Specialization(guards = "isBuiltinString(inliningTarget, key, profile)")
        static Object domPStringKey(DynamicObjectStorage self, Object key, boolean isPop, @SuppressWarnings("unused") PHashingCollection toUpdate,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared("isBuiltin") @Cached IsBuiltinObjectProfile profile,
                        @Cached CastToTruffleStringNode castStr,
                        @Shared("invalidateMro") @Cached InlinedBranchProfile invalidateMroProfile,
                        @Shared("dylib") @CachedLibrary(limit = "3") DynamicObjectLibrary dylib) {
            return domStringKey(self, castStr.execute(key), isPop, toUpdate, inliningTarget, invalidateMroProfile, dylib);
        }

        @Specialization(guards = "!isBuiltinString(inliningTarget, key, profile)")
        static Object domOther(Frame frame, @SuppressWarnings("unused") DynamicObjectStorage self, Object key, @SuppressWarnings("unused") boolean isPop,
                        @SuppressWarnings("unused") PHashingCollection toUpdate,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared("isBuiltin") @Cached IsBuiltinObjectProfile profile,
                        @Shared("hash") @Cached PyObjectHashNode hashNode) {
            hashNode.execute(frame, key); // Just for the potential side effects
            return null;
        }

        @Specialization
        static Object empty(Frame frame, @SuppressWarnings("unused") EmptyStorage self, Object key, @SuppressWarnings("unused") boolean isPop, @SuppressWarnings("unused") PHashingCollection toUpdate,
                        @Shared("hash") @Cached PyObjectHashNode hashNode) {
            // We must not omit the potentially side-effecting call to __hash__
            hashNode.execute(frame, key);
            return null;
        }

        @Specialization
        static Object keywords(Frame frame, KeywordsStorage self, Object key, boolean isPop, PHashingCollection toUpdate,
                        @Shared("hash") @Cached PyObjectHashNode hashNode,
                        @Shared("economicRemove") @Cached ObjectHashMap.RemoveNode removeNode,
                        @Cached EconomicMapSetStringKey specializedPutNode) {
            EconomicMapStorage newStorage = EconomicMapStorage.create(self.length());
            self.addAllTo(newStorage, specializedPutNode);
            toUpdate.setDictStorage(newStorage);
            return economicMap(frame, newStorage, key, isPop, toUpdate, hashNode, removeNode);
        }
    }

    @GenerateUncached
    public abstract static class HashingStorageLen extends Node {
        public static int executeUncached(HashingStorage dictStorage) {
            return HashingStorageLenNodeGen.getUncached().execute(dictStorage);
        }

        @NeverDefault
        public static HashingStorageLen create() {
            return HashingStorageLenNodeGen.create();
        }

        public abstract int execute(HashingStorage storage);

        @Specialization
        static int economicMap(EconomicMapStorage self) {
            return self.length();
        }

        @Specialization
        static int dom(DynamicObjectStorage self,
                        @Bind("this") Node inliningTarget,
                        @Cached DynamicObjectStorage.LengthNode lengthNode) {
            return lengthNode.execute(inliningTarget, self);
        }

        @Specialization
        @SuppressWarnings("unused")
        static int empty(EmptyStorage self) {
            return 0;
        }

        @Specialization
        static int keywords(KeywordsStorage self) {
            return self.length();
        }
    }

    @GenerateUncached
    public abstract static class HashingStorageClear extends Node {
        public abstract HashingStorage execute(HashingStorage storage);

        @Specialization
        static HashingStorage economicMap(EconomicMapStorage self) {
            self.clear();
            return self;
        }

        @Specialization
        static HashingStorage dom(DynamicObjectStorage self,
                        @Bind("this") Node inliningTarget,
                        @Cached DynamicObjectStorage.ClearNode clearNode) {
            clearNode.execute(inliningTarget, self);
            return self;
        }

        @Fallback
        static HashingStorage empty(@SuppressWarnings("unused") HashingStorage self) {
            return EmptyStorage.INSTANCE;
        }
    }

    @GenerateUncached
    public abstract static class HashingStorageCopy extends Node {
        public static HashingStorageCopy getUncached() {
            return HashingStorageCopyNodeGen.getUncached();
        }

        @NeverDefault
        public static HashingStorageCopy create() {
            return HashingStorageCopyNodeGen.create();
        }

        public abstract HashingStorage execute(HashingStorage source);

        @Specialization
        static HashingStorage economic(EconomicMapStorage map) {
            return map.copy();
        }

        @Specialization
        static EmptyStorage empty(@SuppressWarnings("unused") EmptyStorage map) {
            return EmptyStorage.INSTANCE;
        }

        @Specialization
        static DynamicObjectStorage dom(DynamicObjectStorage dom,
                        @Bind("this") Node inliningTarget,
                        @Cached DynamicObjectStorage.Copy copyNode) {
            return copyNode.execute(inliningTarget, dom);
        }

        @Specialization
        static HashingStorage keywords(KeywordsStorage self) {
            return self.copy();
        }
    }

    @ValueType
    public static final class HashingStorageIterator {
        int index = -1;
        Object currentValue;
        final Object[] domKeys;
        final DynamicObjectLibrary dylib;
        final boolean isReverse;

        public HashingStorageIterator() {
            domKeys = null;
            dylib = null;
            isReverse = false;
        }

        public HashingStorageIterator(boolean isReverse) {
            domKeys = null;
            dylib = null;
            this.isReverse = isReverse;
        }

        public HashingStorageIterator(Object[] domKeys, DynamicObjectLibrary dylib, boolean isReverse) {
            this.domKeys = domKeys;
            this.dylib = dylib;
            this.isReverse = isReverse;
        }

        /**
         * Captures internal state of the iterator such that it can be iterated and then restored
         * back to that state and eventually iterated again.
         */
        public int getState() {
            return index;
        }

        public void setState(int state) {
            index = state;
        }
    }

    @GenerateUncached
    @ImportStatic({PGuards.class})
    public abstract static class HashingStorageGetIterator extends Node {
        public static HashingStorageIterator executeUncached(HashingStorage storage) {
            return HashingStorageGetIteratorNodeGen.getUncached().execute(storage);
        }

        @NeverDefault
        public static HashingStorageGetIterator create() {
            return HashingStorageGetIteratorNodeGen.create();
        }

        public final HashingStorageIterator execute(HashingStorage storage) {
            HashingStorageIterator result = executeImpl(storage);
            assert !result.isReverse;
            return result;
        }

        public abstract HashingStorageIterator executeImpl(HashingStorage storage);

        @Specialization
        static HashingStorageIterator economicMap(@SuppressWarnings("unused") EconomicMapStorage self) {
            return new HashingStorageIterator();
        }

        @Specialization
        static HashingStorageIterator dom(DynamicObjectStorage self,
                        @CachedLibrary(limit = "3") DynamicObjectLibrary dylib) {
            return new HashingStorageIterator(dylib.getKeyArray(self.store), dylib, false);
        }

        @Specialization
        static HashingStorageIterator empty(@SuppressWarnings("unused") EmptyStorage self) {
            return new HashingStorageIterator();
        }

        @Specialization
        static HashingStorageIterator keywords(@SuppressWarnings("unused") KeywordsStorage self) {
            return new HashingStorageIterator();
        }
    }

    @GenerateUncached
    @ImportStatic({PGuards.class})
    public abstract static class HashingStorageGetReverseIterator extends Node {
        public final HashingStorageIterator execute(HashingStorage storage) {
            HashingStorageIterator result = executeImpl(storage);
            assert result.isReverse;
            return result;
        }

        abstract HashingStorageIterator executeImpl(HashingStorage storage);

        @Specialization
        static HashingStorageIterator economicMap(@SuppressWarnings("unused") EconomicMapStorage self) {
            HashingStorageIterator it = new HashingStorageIterator(true);
            it.index = self.map.usedHashes;
            return it;
        }

        @Specialization
        static HashingStorageIterator dom(DynamicObjectStorage self,
                        @CachedLibrary(limit = "3") DynamicObjectLibrary dylib) {
            HashingStorageIterator it = new HashingStorageIterator(dylib.getKeyArray(self.store), dylib, true);
            it.index = it.domKeys.length;
            return it;
        }

        @Specialization
        static HashingStorageIterator empty(@SuppressWarnings("unused") EmptyStorage self) {
            return new HashingStorageIterator(true);
        }

        @Specialization
        static HashingStorageIterator keywords(@SuppressWarnings("unused") KeywordsStorage self) {
            HashingStorageIterator it = new HashingStorageIterator(true);
            it.index = self.length();
            return it;
        }
    }

    @GenerateUncached
    @ImportStatic({PGuards.class})
    public abstract static class HashingStorageIteratorNext extends Node {
        public static boolean executeUncached(HashingStorage storage, HashingStorageIterator it) {
            return HashingStorageIteratorNextNodeGen.getUncached().execute(storage, it);
        }

        @NeverDefault
        public static HashingStorageIteratorNext create() {
            return HashingStorageIteratorNextNodeGen.create();
        }

        /**
         * Returns {@code true} if the iterator has next value. Use nodes to get the current value,
         * key, and hash of the current key.
         */
        public abstract boolean execute(HashingStorage storage, HashingStorageIterator it);

        @Specialization(guards = "!it.isReverse")
        static boolean economicMap(EconomicMapStorage self, HashingStorageIterator it) {
            ObjectHashMap map = self.map;
            it.index++;
            while (it.index < map.usedHashes) {
                Object val = map.getValue(it.index);
                if (val != null) {
                    it.currentValue = val;
                    return true;
                }
                it.index++;
            }
            assert (it.currentValue = null) == null;
            return false;
        }

        @Specialization(guards = "it.isReverse")
        static boolean economicMapReverse(EconomicMapStorage self, HashingStorageIterator it) {
            ObjectHashMap map = self.map;
            it.index--;
            while (it.index >= 0) {
                Object val = map.getValue(it.index);
                if (val != null) {
                    it.currentValue = val;
                    return true;
                }
                it.index--;
            }
            assert (it.currentValue = null) == null;
            return false;
        }

        @Specialization(guards = "!it.isReverse")
        static boolean dom(DynamicObjectStorage self, HashingStorageIterator it) {
            it.index++;
            while (it.index < it.domKeys.length) {
                if (it.domKeys[it.index] instanceof TruffleString) {
                    Object val = it.dylib.getOrDefault(self.store, it.domKeys[it.index], PNone.NO_VALUE);
                    if (val != PNone.NO_VALUE) {
                        it.currentValue = val;
                        return true;
                    }
                }
                it.index++;
            }
            assert (it.currentValue = null) == null;
            return false;
        }

        @Specialization(guards = "it.isReverse")
        static boolean domReverse(DynamicObjectStorage self, HashingStorageIterator it) {
            it.index--;
            while (it.index >= 0) {
                if (it.domKeys[it.index] instanceof TruffleString) {
                    Object val = it.dylib.getOrDefault(self.store, it.domKeys[it.index], PNone.NO_VALUE);
                    if (val != PNone.NO_VALUE) {
                        it.currentValue = val;
                        return true;
                    }
                }
                it.index--;
            }
            assert (it.currentValue = null) == null;
            return false;
        }

        @Specialization
        @SuppressWarnings("unused")
        static boolean empty(EmptyStorage self, HashingStorageIterator it) {
            return false;
        }

        @Specialization(guards = "!it.isReverse")
        static boolean keywords(KeywordsStorage self, HashingStorageIterator it) {
            return ++it.index < self.length();
        }

        @Specialization(guards = "it.isReverse")
        static boolean keywordsReverse(@SuppressWarnings("unused") KeywordsStorage self, HashingStorageIterator it) {
            return --it.index >= 0;
        }
    }

    @GenerateUncached
    @ImportStatic({PGuards.class})
    public abstract static class HashingStorageIteratorValue extends Node {
        public static Object executeUncached(HashingStorage storage, HashingStorageIterator it) {
            return HashingStorageIteratorValueNodeGen.getUncached().execute(storage, it);
        }

        @NeverDefault
        public static HashingStorageIteratorValue create() {
            return HashingStorageIteratorValueNodeGen.create();
        }

        public abstract Object execute(HashingStorage storage, HashingStorageIterator it);

        @Specialization
        static Object economicMap(@SuppressWarnings("unused") EconomicMapStorage self, HashingStorageIterator it) {
            return it.currentValue;
        }

        @Specialization
        static Object dom(@SuppressWarnings("unused") DynamicObjectStorage self, HashingStorageIterator it) {
            return it.currentValue;
        }

        @Specialization
        @SuppressWarnings("unused")
        static boolean empty(EmptyStorage self, HashingStorageIterator it) {
            throw CompilerDirectives.shouldNotReachHere("empty in HashingStorageIteratorValue");
        }

        @Specialization
        static Object keywords(KeywordsStorage self, HashingStorageIterator it) {
            return self.keywords[it.index].getValue();
        }
    }

    // TODO: DSL inlining: inline this other nodes in this file
    @GenerateUncached
    @ImportStatic({PGuards.class})
    public abstract static class HashingStorageIteratorKey extends Node {
        public static Object executeUncached(HashingStorage storage, HashingStorageIterator it) {
            return HashingStorageIteratorKeyNodeGen.getUncached().execute(storage, it);
        }

        @NeverDefault
        public static HashingStorageIteratorKey create() {
            return HashingStorageIteratorKeyNodeGen.create();
        }

        public abstract Object execute(HashingStorage storage, HashingStorageIterator it);

        @Specialization
        static Object economicMap(EconomicMapStorage self, HashingStorageIterator it) {
            return self.map.getKey(it.index);
        }

        @Specialization
        static TruffleString dom(@SuppressWarnings("unused") DynamicObjectStorage self, HashingStorageIterator it) {
            return (TruffleString) it.domKeys[it.index];
        }

        @Specialization
        @SuppressWarnings("unused")
        static boolean empty(EmptyStorage self, HashingStorageIterator it) {
            throw CompilerDirectives.shouldNotReachHere("empty in HashingStorageIteratorKey");
        }

        @Specialization
        static Object keywords(KeywordsStorage self, HashingStorageIterator it) {
            return self.keywords[it.index].getName();
        }
    }

    @GenerateUncached
    @ImportStatic({PGuards.class})
    public abstract static class HashingStorageIteratorKeyHash extends Node {
        public abstract long execute(HashingStorage storage, HashingStorageIterator it);

        @Specialization
        static long economicMap(EconomicMapStorage self, HashingStorageIterator it) {
            return self.map.hashes[it.index];
        }

        @Specialization
        static long dom(@SuppressWarnings("unused") DynamicObjectStorage self, HashingStorageIterator it,
                        @Shared("hash") @Cached TruffleString.HashCodeNode hashNode) {
            return PyObjectHashNode.hash((TruffleString) it.domKeys[it.index], hashNode);
        }

        @Specialization
        @SuppressWarnings("unused")
        static long empty(EmptyStorage self, HashingStorageIterator it) {
            throw CompilerDirectives.shouldNotReachHere("empty in HashingStorageIteratorKey");
        }

        @Specialization
        static long keywords(KeywordsStorage self, HashingStorageIterator it,
                        @Shared("hash") @Cached TruffleString.HashCodeNode hashNode) {
            return PyObjectHashNode.hash(self.keywords[it.index].getName(), hashNode);
        }
    }

    @GenerateUncached
    @ImportStatic({PGuards.class})
    public abstract static class HashingStorageEq extends Node {
        public abstract boolean execute(Frame frame, HashingStorage a, HashingStorage b);

        @Specialization
        boolean doIt(Frame frame, HashingStorage aStorage, HashingStorage bStorage,
                        @Bind("this") Node inliningTarget,
                        @Cached HashingStorageGetItemWithHash getBNode,
                        @Cached HashingStorageLen lenANode,
                        @Cached HashingStorageLen lenBNode,
                        @Cached HashingStorageGetIterator getAIter,
                        @Cached HashingStorageIteratorNext aIterNext,
                        @Cached HashingStorageIteratorKey aIterKey,
                        @Cached HashingStorageIteratorValue aIterValue,
                        @Cached HashingStorageIteratorKeyHash aIterHash,
                        @Cached PyObjectRichCompareBool.EqNode eqNode,
                        @Cached InlinedLoopConditionProfile loopProfile,
                        @Cached InlinedLoopConditionProfile earlyExitProfile) {
            if (lenANode.execute(aStorage) != lenBNode.execute(bStorage)) {
                return false;
            }
            int index = 0;
            try {
                HashingStorageIterator aIter = getAIter.execute(aStorage);
                while (loopProfile.profile(inliningTarget, aIterNext.execute(aStorage, aIter))) {
                    if (CompilerDirectives.hasNextTier()) {
                        index++;
                    }

                    Object aKey = aIterKey.execute(aStorage, aIter);
                    long aHash = aIterHash.execute(aStorage, aIter);
                    Object bValue = getBNode.execute(frame, bStorage, aKey, aHash);
                    Object aValue = aIterValue.execute(aStorage, aIter);
                    if (earlyExitProfile.profile(inliningTarget, !(bValue == null || !eqNode.execute(frame, bValue, aValue)))) {
                        // if->continue such that the "true" count of the profile represents the
                        // loop iterations and the "false" count the early exit
                        continue;
                    }
                    return false;
                }
            } finally {
                if (index != 0) {
                    LoopNode.reportLoopCount(this, index);
                }
            }
            return true;
        }
    }

    private static final class AbortIteration extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private static final AbortIteration INSTANCE = new AbortIteration();

        public AbortIteration() {
            super(null, null);
        }

        @SuppressWarnings("sync-override")
        @Override
        public Throwable fillInStackTrace() {
            return this;
        }
    }

    public abstract static class HashingStorageForEachCallback<T> extends Node {
        public abstract T execute(Frame frame, Node inliningTarget, HashingStorage storage, HashingStorageIterator it, T accumulator);
    }

    @GenerateUncached
    @ImportStatic({PGuards.class})
    public abstract static class HashingStorageForEach extends Node {
        @SuppressWarnings("unchecked")
        public final <T> T execute(Frame frame, HashingStorage storage, HashingStorageForEachCallback<T> callback, T accumulator) {
            CompilerAsserts.partialEvaluationConstant(callback);
            return (T) executeUntyped(frame, null, storage, (HashingStorageForEachCallback<Object>) callback, accumulator);
        }

        @SuppressWarnings("unchecked")
        public final <T> T execute(Frame frame, Node callbackInliningTarget, HashingStorage storage, HashingStorageForEachCallback<T> callback, T accumulator) {
            CompilerAsserts.partialEvaluationConstant(callback);
            return (T) executeUntyped(frame, callbackInliningTarget, storage, (HashingStorageForEachCallback<Object>) callback, accumulator);
        }

        abstract Object executeUntyped(Frame frame, Node callbackInliningTarget, HashingStorage storage, HashingStorageForEachCallback<Object> callback, Object accumulator);

        @Specialization
        static Object doIt(Frame frame, Node callbackInliningTarget, HashingStorage storage, HashingStorageForEachCallback<Object> callback, Object accumulatorIn,
                        @Bind("this") Node inliningTarget,
                        @Cached HashingStorageGetIterator getIter,
                        @Cached HashingStorageIteratorNext iterNext,
                        @Cached InlinedLoopConditionProfile loopProfile) {
            int index = 0;
            Object accumulator = accumulatorIn;
            try {
                HashingStorageIterator aIter = getIter.execute(storage);
                while (loopProfile.profile(inliningTarget, iterNext.execute(storage, aIter))) {
                    if (CompilerDirectives.hasNextTier()) {
                        index++;
                    }
                    accumulator = callback.execute(frame, callbackInliningTarget, storage, aIter, accumulator);
                }
            } finally {
                if (index != 0) {
                    LoopNode.reportLoopCount(getIter, index);
                }
            }
            return accumulator;
        }
    }

    @ValueType
    public static final class ResultAndOther {
        final ObjectHashMap result;
        final HashingStorage other;

        public ResultAndOther(ObjectHashMap result, HashingStorage other) {
            this.result = result;
            this.other = other;
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    @ImportStatic({PGuards.class})
    public abstract static class HashingStorageXorCallback extends HashingStorageForEachCallback<ResultAndOther> {

        @Override
        public abstract ResultAndOther execute(Frame frame, Node inliningTarget, HashingStorage storage, HashingStorageIterator it, ResultAndOther accumulator);

        @Specialization
        static ResultAndOther doGeneric(Frame frame, @SuppressWarnings("unused") Node inliningTarget, HashingStorage storage, HashingStorageIterator it, ResultAndOther acc,
                        @Cached(inline = false) ObjectHashMap.PutNode putResultNode,
                        @Cached(inline = false) HashingStorageGetItemWithHash getFromOther,
                        @Cached(inline = false) HashingStorageIteratorKey iterKey,
                        @Cached(inline = false) HashingStorageIteratorValue iterValue,
                        @Cached(inline = false) HashingStorageIteratorKeyHash iterHash) {
            Object key = iterKey.execute(storage, it);
            long hash = iterHash.execute(storage, it);
            Object otherValue = getFromOther.execute(frame, acc.other, key, hash);
            if (otherValue == null) {
                putResultNode.put(frame, acc.result, key, hash, iterValue.execute(storage, it));
            }
            return acc;
        }
    }

    @GenerateUncached
    @ImportStatic({PGuards.class})
    public abstract static class HashingStorageXor extends Node {
        public abstract HashingStorage execute(Frame frame, HashingStorage a, HashingStorage b);

        @Specialization
        static HashingStorage doIt(Frame frame, HashingStorage aStorage, HashingStorage bStorage,
                        @Bind("this") Node inliningTarget,
                        @Cached HashingStorageForEach forEachA,
                        @Cached HashingStorageForEach forEachB,
                        @Cached HashingStorageXorCallback callbackA,
                        @Cached HashingStorageXorCallback callbackB) {
            final EconomicMapStorage result = EconomicMapStorage.createWithSideEffects();
            ObjectHashMap resultMap = result.map;

            ResultAndOther accA = new ResultAndOther(resultMap, bStorage);
            forEachA.execute(frame, inliningTarget, aStorage, callbackA, accA);

            ResultAndOther accB = new ResultAndOther(resultMap, aStorage);
            forEachB.execute(frame, inliningTarget, bStorage, callbackB, accB);

            return result;
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    @ImportStatic({PGuards.class})
    public abstract static class HashingStorageIntersectCallback extends HashingStorageForEachCallback<ResultAndOther> {

        @Override
        public abstract ResultAndOther execute(Frame frame, Node inliningTarget, HashingStorage storage, HashingStorageIterator it, ResultAndOther accumulator);

        @Specialization
        static ResultAndOther doGeneric(Frame frame, HashingStorage storage, HashingStorageIterator it, ResultAndOther acc,
                        @Cached(inline = false) ObjectHashMap.PutNode putResultNode,
                        @Cached(inline = false) HashingStorageGetItemWithHash getFromOther,
                        @Cached(inline = false) HashingStorageIteratorKey iterKey,
                        @Cached(inline = false) HashingStorageIteratorKeyHash iterHash) {
            Object key = iterKey.execute(storage, it);
            long hash = iterHash.execute(storage, it);
            Object otherValue = getFromOther.execute(frame, acc.other, key, hash);
            if (otherValue != null) {
                putResultNode.put(frame, acc.result, key, hash, otherValue);
            }
            return acc;
        }
    }

    /**
     * In case the key is in both, this keeps the value from {@code b}.
     */
    @GenerateUncached
    @ImportStatic({PGuards.class})
    public abstract static class HashingStorageIntersect extends Node {
        public abstract HashingStorage execute(Frame frame, HashingStorage a, HashingStorage b);

        @Specialization
        static HashingStorage doIt(Frame frame, HashingStorage aStorage, HashingStorage bStorage,
                        @Bind("this") Node inliningTarget,
                        @Cached HashingStorageForEach forEachA,
                        @Cached HashingStorageIntersectCallback callback) {
            final EconomicMapStorage result = EconomicMapStorage.createWithSideEffects();
            ResultAndOther acc = new ResultAndOther(result.map, bStorage);
            forEachA.execute(frame, inliningTarget, aStorage, callback, acc);
            return result;
        }
    }

    @GenerateUncached
    @ImportStatic({PGuards.class})
    public abstract static class HashingStorageDiffCallback extends HashingStorageForEachCallback<ResultAndOther> {

        @Override
        public abstract ResultAndOther execute(Frame frame, Node inliningTarget, HashingStorage storage, HashingStorageIterator it, ResultAndOther accumulator);

        @Specialization
        static ResultAndOther doGeneric(Frame frame, @SuppressWarnings("unused") Node inliningTarget, HashingStorage storage, HashingStorageIterator it, ResultAndOther acc,
                        @Cached ObjectHashMap.PutNode putResultNode,
                        @Cached HashingStorageGetItemWithHash getFromOther,
                        @Cached HashingStorageIteratorKey iterKey,
                        @Cached HashingStorageIteratorKeyHash iterHash,
                        @Cached HashingStorageIteratorValue iterValue) {
            Object key = iterKey.execute(storage, it);
            long hash = iterHash.execute(storage, it);
            Object otherValue = getFromOther.execute(frame, acc.other, key, hash);
            if (otherValue == null) {
                putResultNode.put(frame, acc.result, key, hash, iterValue.execute(storage, it));
            }
            return acc;
        }
    }

    /**
     * {@code a-b}
     */
    @GenerateUncached
    @ImportStatic({PGuards.class})
    public abstract static class HashingStorageDiff extends Node {
        public abstract HashingStorage execute(Frame frame, HashingStorage a, HashingStorage b);

        @Specialization
        static HashingStorage doIt(Frame frame, HashingStorage aStorage, HashingStorage bStorage,
                        @Cached HashingStorageForEach forEachA,
                        @Cached HashingStorageDiffCallback callback) {
            final EconomicMapStorage result = EconomicMapStorage.createWithSideEffects();
            ResultAndOther acc = new ResultAndOther(result.map, bStorage);
            forEachA.execute(frame, aStorage, callback, acc);
            return result;
        }
    }

    /**
     * Throws {@link AbortIteration} if a key that's missing in {@code bStorage} is found.
     */
    @GenerateUncached
    @ImportStatic({PGuards.class})
    public abstract static class HashingStorageCompareKeysCallback extends HashingStorageForEachCallback<HashingStorage> {

        @Override
        public abstract HashingStorage execute(Frame frame, Node inliningTarget, HashingStorage aStorage, HashingStorageIterator it, HashingStorage bStorage);

        @Specialization
        static HashingStorage doGeneric(Frame frame, @SuppressWarnings("unused") Node inliningTarget, HashingStorage aStorage, HashingStorageIterator it, HashingStorage bStorage,
                        @Cached HashingStorageGetItemWithHash getFromOther,
                        @Cached HashingStorageIteratorKey iterKey,
                        @Cached HashingStorageIteratorKeyHash iterHash) {
            Object key = iterKey.execute(aStorage, it);
            long hash = iterHash.execute(aStorage, it);
            Object otherValue = getFromOther.execute(frame, bStorage, key, hash);
            if (otherValue == null) {
                throw AbortIteration.INSTANCE;
            }
            return bStorage;
        }
    }

    @GenerateUncached
    @ImportStatic({PGuards.class})
    public abstract static class HashingStorageCompareKeys extends Node {
        public abstract int execute(Frame frame, HashingStorage a, HashingStorage b);

        @Specialization(guards = "aStorage == bStorage")
        @SuppressWarnings("unused")
        static int doSame(HashingStorage aStorage, HashingStorage bStorage) {
            return 0;
        }

        @Specialization(guards = "aStorage != bStorage")
        static int doGeneric(Frame frame, HashingStorage aStorage, HashingStorage bStorage,
                        @Cached HashingStorageLen aLenNode,
                        @Cached HashingStorageLen bLenNode,
                        @Cached HashingStorageForEach forEachA,
                        @Cached HashingStorageCompareKeysCallback callback) {
            int aLen = aLenNode.execute(aStorage);
            int bLen = bLenNode.execute(bStorage);
            if (aLen > bLen) {
                return 1;
            }
            try {
                forEachA.execute(frame, aStorage, callback, bStorage);
            } catch (AbortIteration ignored) {
                return 1;
            }
            if (aLen == bLen) {
                return 0;
            } else {
                return -1;
            }
        }
    }

    /**
     * Throws {@link AbortIteration} if a key that's in both storages is found.
     */
    @GenerateUncached
    @ImportStatic({PGuards.class})
    public abstract static class HashingStorageAreDisjointCallback extends HashingStorageForEachCallback<HashingStorage> {

        @Override
        public abstract HashingStorage execute(Frame frame, Node inliningTarget, HashingStorage aStorage, HashingStorageIterator it, HashingStorage bStorage);

        @Specialization
        static HashingStorage doGeneric(Frame frame, @SuppressWarnings("unused") Node inliningTarget, HashingStorage aStorage, HashingStorageIterator it, HashingStorage bStorage,
                        @Cached HashingStorageGetItemWithHash getFromOther,
                        @Cached HashingStorageIteratorKey iterKey,
                        @Cached HashingStorageIteratorKeyHash iterHash) {
            Object key = iterKey.execute(aStorage, it);
            long hash = iterHash.execute(aStorage, it);
            Object otherValue = getFromOther.execute(frame, bStorage, key, hash);
            if (otherValue != null) {
                throw AbortIteration.INSTANCE;
            }
            return bStorage;
        }
    }

    @GenerateUncached
    @ImportStatic({PGuards.class})
    public abstract static class HashingStorageAreDisjoint extends Node {
        public abstract boolean execute(Frame frame, HashingStorage a, HashingStorage b);

        @Specialization
        static boolean doGeneric(Frame frame, HashingStorage aStorage, HashingStorage bStorage,
                        @Cached HashingStorageLen aLenNode,
                        @Cached HashingStorageLen bLenNode,
                        @Cached HashingStorageForEach forEach,
                        @Cached HashingStorageAreDisjointCallback callback) {
            int aLen = aLenNode.execute(aStorage);
            int bLen = bLenNode.execute(bStorage);
            try {
                if (aLen > bLen) {
                    forEach.execute(frame, bStorage, callback, aStorage);
                } else {
                    forEach.execute(frame, aStorage, callback, bStorage);
                }
                return true;
            } catch (AbortIteration ignore) {
                return false;
            }
        }
    }

    @GenerateUncached
    public abstract static class HashingStorageTransferItem extends HashingStorageForEachCallback<HashingStorage> {
        @Override
        public abstract HashingStorage execute(Frame frame, Node inliningTarget, HashingStorage src, HashingStorageIterator it, HashingStorage destStorage);

        @Specialization
        static EconomicMapStorage economic2Economic(Frame frame, @SuppressWarnings("unused") Node inliningTarget, EconomicMapStorage src, HashingStorageIterator it, EconomicMapStorage destStorage,
                        @Cached PutNode putNode) {
            ObjectHashMap srcMap = src.map;
            putNode.put(frame, destStorage.map, srcMap.getKey(it.index), srcMap.hashes[it.index], srcMap.getValue(it.index));
            return destStorage;
        }

        @Specialization(replaces = "economic2Economic")
        static HashingStorage economic2Generic(Frame frame, @SuppressWarnings("unused") Node inliningTarget, EconomicMapStorage src, HashingStorageIterator it, HashingStorage destStorage,
                        @Cached HashingStorageSetItemWithHash setItemWithHash) {
            // Note that the point is to avoid side-effecting __hash__ call. Since the source is
            // economic map, the key may be an arbitrary object.
            ObjectHashMap srcMap = src.map;
            return setItemWithHash.execute(frame, destStorage, srcMap.getKey(it.index), srcMap.hashes[it.index], srcMap.getValue(it.index));
        }

        @Fallback
        static HashingStorage generic2Generic(Frame frame, @SuppressWarnings("unused") Node inliningTarget, HashingStorage src, HashingStorageIterator it, HashingStorage destStorage,
                        @Cached HashingStorageIteratorKey iterKey,
                        @Cached HashingStorageIteratorValue iterValue,
                        @Cached HashingStorageSetItem setItem) {
            // We know that for all other storages the key hash must be side effect free, so we can
            // just insert it leaving it up to the HashingStorageSetItem whether we need to compute
            // hash or not. Since the src is not EconomicMapStorage, we do not know the hash anyway.
            // We still pass the frame, because the insertion may trigger __eq__
            return setItem.execute(frame, destStorage, iterKey.execute(src, it), iterValue.execute(src, it));
        }
    }

    @GenerateUncached
    public abstract static class HashingStorageAddAllToOther extends Node {
        public static HashingStorageAddAllToOther getUncached() {
            return HashingStorageAddAllToOtherNodeGen.getUncached();
        }

        @NeverDefault
        public static HashingStorageAddAllToOther create() {
            return HashingStorageAddAllToOtherNodeGen.create();
        }

        public final void execute(Frame frame, HashingStorage source, PHashingCollection dest) {
            dest.setDictStorage(execute(frame, source, dest.getDictStorage()));
        }

        public abstract HashingStorage execute(Frame frame, HashingStorage source, HashingStorage dest);

        @Specialization(guards = "source == dest")
        @SuppressWarnings("unused")
        static HashingStorage doIdentical(Frame frame, HashingStorage source, HashingStorage dest) {
            return dest;
        }

        @Specialization(guards = "source != dest")
        static HashingStorage doIt(Frame frame, HashingStorage source, HashingStorage dest,
                        @Cached HashingStorageForEach forEach,
                        @Cached HashingStorageTransferItem transferItem) {
            return forEach.execute(frame, source, transferItem, dest);
        }
    }
}
