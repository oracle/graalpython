/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.CachedHashingStorageGetItemNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.HashingStorageAddAllToOtherNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.HashingStorageCopyNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.HashingStorageDelItemNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.HashingStorageForEachNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.HashingStorageGetItemNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.HashingStorageGetItemWithHashNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.HashingStorageGetIteratorNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.HashingStorageGetReverseIteratorNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodesFactory.HashingStorageIteratorKeyHashNodeGen;
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
import com.oracle.graal.python.lib.PyUnicodeCheckExactNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.util.CastBuiltinStringToTruffleStringNode;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
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
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
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
    @GenerateInline
    @GenerateCached(false)
    public abstract static class HashingStorageGetItemWithHash extends Node {

        public static Object getItemWithHash(HashingStorage self, Object key, long keyHash) {
            return HashingStorageGetItemWithHashNodeGen.getUncached().execute(null, null, self, key, keyHash);
        }

        public abstract Object execute(Frame frame, Node inliningTarget, HashingStorage self, Object key, long keyHash);

        @Specialization
        static Object economicMap(Frame frame, Node inliningTarget, EconomicMapStorage self, Object key, long keyHash,
                        @Cached ObjectHashMap.GetNode getNode) {
            return getNode.execute(frame, inliningTarget, self.map, key, keyHash);
        }

        @Specialization
        static Object dom(Frame frame, Node inliningTarget, DynamicObjectStorage self, Object key, long keyHash,
                        @Cached DynamicObjectStorage.GetItemNode getNode) {
            return getNode.execute(frame, inliningTarget, self, key, keyHash);
        }

        @Specialization
        @SuppressWarnings("unused")
        static Object empty(Frame frame, EmptyStorage self, Object key, long keyHash) {
            return null;
        }

        @Specialization
        @InliningCutoff
        static Object keywords(Frame frame, Node inliningTarget, KeywordsStorage self, Object key, long keyHash,
                        @Cached GetKeywordsStorageItemNode getNode) {
            return getNode.execute(frame, inliningTarget, self, key, keyHash);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    @ImportStatic(PGuards.class)
    public abstract static class HashingStorageGetItem extends Node {
        public static boolean hasKeyUncached(HashingStorage storage, Object key) {
            return HashingStorageGetItemNodeGen.getUncached().execute(null, null, storage, key) != null;
        }

        public final boolean hasKey(Node inliningTarget, HashingStorage self, TruffleString key) {
            return execute(null, inliningTarget, self, key) != null;
        }

        public final boolean hasKey(Frame frame, Node inliningTarget, HashingStorage self, Object key) {
            return execute(frame, inliningTarget, self, key) != null;
        }

        public static Object executeUncached(HashingStorage storage, Object key) {
            return HashingStorageGetItemNodeGen.getUncached().execute(null, null, storage, key);
        }

        public final Object execute(Node inliningTarget, HashingStorage self, TruffleString key) {
            // Shortcut for frequent usage with TruffleString. We do not need a frame in such case,
            // because the string's __hash__ does not need it. Some fast-paths avoid even invoking
            // __hash__ for string keys
            return execute(null, inliningTarget, self, key);
        }

        public abstract Object execute(Frame frame, Node inliningTarget, HashingStorage self, Object key);

        @Specialization(guards = "isEconomicMapOrEmpty(self)")
        static Object economicMap(Frame frame, Node inliningTarget, HashingStorage self, Object key,
                        @Cached PyObjectHashNode hashNode,
                        @Cached InlinedConditionProfile isEconomicMapProfile,
                        @Cached ObjectHashMap.GetNode getNode) {
            // We must not omit the potentially side-effecting call to __hash__
            long hash = hashNode.execute(frame, inliningTarget, key);
            if (isEconomicMapProfile.profile(inliningTarget, self instanceof EconomicMapStorage)) {
                return getNode.execute(frame, inliningTarget, ((EconomicMapStorage) self).map, key, hash);
            } else {
                return null;
            }
        }

        @Specialization
        static Object dom(Frame frame, Node inliningTarget, DynamicObjectStorage self, Object key,
                        @Cached DynamicObjectStorage.GetItemNode getNode) {
            return getNode.execute(frame, inliningTarget, self, key, -1);
        }

        @Specialization
        @InliningCutoff
        static Object keywords(Frame frame, Node inliningTarget, KeywordsStorage self, Object key,
                        @Cached GetKeywordsStorageItemNode getNode) {
            return getNode.execute(frame, inliningTarget, self, key, -1);
        }
    }

    @GenerateInline(false)
    public abstract static class CachedHashingStorageGetItem extends Node {
        public abstract Object execute(Frame frame, HashingStorage storage, Object key);

        @Specialization
        Object doIt(Frame frame, HashingStorage s, Object k,
                        @Cached HashingStorageGetItem getItem) {
            return getItem.execute(frame, this, s, k);
        }

        @NeverDefault
        public static CachedHashingStorageGetItem create() {
            return CachedHashingStorageGetItemNodeGen.create();
        }
    }

    abstract static class SpecializedSetStringKey extends Node {
        public abstract void execute(Node inliningTarget, HashingStorage self, TruffleString key, Object value);
    }

    static EconomicMapStorage dynamicObjectStorageToEconomicMap(Node inliningTarget, DynamicObjectStorage s, DynamicObjectLibrary dylib, PyObjectHashNode hashNode, PutNode putNode) {
        // TODO: shouldn't we invalidate all MRO assumptions in this case?
        DynamicObject store = s.store;
        EconomicMapStorage result = EconomicMapStorage.create(dylib.getShape(store).getPropertyCount());
        ObjectHashMap resultMap = result.map;
        Object[] keys = dylib.getKeyArray(store);
        for (Object k : keys) {
            if (k instanceof TruffleString) {
                Object v = dylib.getOrDefault(store, k, PNone.NO_VALUE);
                if (v != PNone.NO_VALUE) {
                    putNode.execute(null, inliningTarget, resultMap, k, hashNode.execute(null, inliningTarget, k), v);
                }
            }
        }
        return result;
    }

    @GenerateUncached
    @GenerateInline(inlineByDefault = true)
    @ImportStatic(PGuards.class)
    public abstract static class HashingStorageSetItemWithHash extends Node {

        @NeverDefault
        public static HashingStorageSetItemWithHash create() {
            return HashingStorageSetItemWithHashNodeGen.create();
        }

        public abstract HashingStorage execute(Frame frame, Node inliningTarget, HashingStorage self, Object key, long keyHash, Object value);

        public final HashingStorage executeCached(Frame frame, HashingStorage self, Object key, long keyHash, Object value) {
            return execute(frame, this, self, key, keyHash, value);
        }

        @Specialization
        static HashingStorage economicMap(Frame frame, Node inliningTarget, EconomicMapStorage self, Object key, long keyHash, Object value,
                        @Exclusive @Cached PyUnicodeCheckExactNode isBuiltinString,
                        @Exclusive @Cached ObjectHashMap.PutNode putNode) {
            putNode.execute(frame, inliningTarget, self.map, key, keyHash, value);
            if (!self.map.hasSideEffect() && !isBuiltinString.execute(inliningTarget, key)) {
                self.map.setSideEffectingKeysFlag();
            }
            return self;
        }

        @Specialization
        static HashingStorage empty(Frame frame, Node inliningTarget, @SuppressWarnings("unused") EmptyStorage self, Object key, long keyHash, Object value,
                        @Exclusive @Cached PyUnicodeCheckExactNode isBuiltinString,
                        @Exclusive @Cached ObjectHashMap.PutNode putNode) {
            EconomicMapStorage storage = EconomicMapStorage.create(1);
            putNode.execute(frame, inliningTarget, storage.map, key, keyHash, value);
            if (!isBuiltinString.execute(inliningTarget, key)) {
                storage.map.setSideEffectingKeysFlag();
            }
            return storage;
        }

        @Specialization(guards = "!self.shouldTransitionOnPut()")
        static HashingStorage domStringKey(Node inliningTarget, DynamicObjectStorage self, TruffleString key, long keyHash, Object value,
                        @Cached InlinedBranchProfile invalidateMroProfile,
                        @Shared @CachedLibrary(limit = "3") DynamicObjectLibrary dylib) {
            self.setStringKey(key, value, dylib, inliningTarget, invalidateMroProfile);
            return self;
        }

        @Specialization(replaces = "domStringKey")
        @InliningCutoff
        static HashingStorage dom(Frame frame, Node inliningTarget, DynamicObjectStorage self, Object key, long keyHash, Object value,
                        @Cached InlinedConditionProfile shouldTransitionProfile,
                        @Exclusive @Cached PyUnicodeCheckExactNode isBuiltinString,
                        @Shared @CachedLibrary(limit = "3") DynamicObjectLibrary dylib,
                        @Cached DOMStorageSetItemWithHash domNode) {
            boolean transition = true;
            if (shouldTransitionProfile.profile(inliningTarget, !self.shouldTransitionOnPut())) {
                if (isBuiltinString.execute(inliningTarget, key)) {
                    transition = false;
                }
            }
            return domNode.execute(frame, inliningTarget, self, key, keyHash, value, transition, dylib);
        }

        @Specialization
        @InliningCutoff
        static HashingStorage keywords(Frame frame, Node inliningTarget, KeywordsStorage self, Object key, long keyHash, Object value,
                        @Exclusive @Cached PyUnicodeCheckExactNode isBuiltinString,
                        @Exclusive @Cached ObjectHashMap.PutNode putNode,
                        @Cached EconomicMapSetStringKey specializedPutNode) {
            // TODO: do we want to try DynamicObjectStorage if the key is a string?
            EconomicMapStorage result = EconomicMapStorage.create(self.length());
            self.addAllTo(inliningTarget, result, specializedPutNode);
            return economicMap(frame, inliningTarget, result, key, keyHash, value, isBuiltinString, putNode);
        }

        @GenerateUncached
        @GenerateInline
        @GenerateCached(false)
        @ImportStatic(PGuards.class)
        abstract static class DOMStorageSetItemWithHash extends Node {
            public abstract HashingStorage execute(Frame frame, Node inliningTarget, DynamicObjectStorage self, Object key, long keyHash, Object value,
                            boolean transition, DynamicObjectLibrary dylib);

            @Specialization(guards = {"!transition", "isBuiltinString.execute(inliningTarget, key)"}, limit = "1")
            static HashingStorage domStringKey(Node inliningTarget, DynamicObjectStorage self, Object key, @SuppressWarnings("unused") long keyHash, Object value,
                            @SuppressWarnings("unused") boolean transition, DynamicObjectLibrary dylib,
                            @SuppressWarnings("unused") @Cached PyUnicodeCheckExactNode isBuiltinString,
                            @Cached CastBuiltinStringToTruffleStringNode castStr,
                            @Cached InlinedBranchProfile invalidateMroProfile) {
                self.setStringKey(castStr.execute(inliningTarget, key), value, dylib, inliningTarget, invalidateMroProfile);
                return self;
            }

            @Fallback
            static HashingStorage domTransition(Frame frame, Node inliningTarget, DynamicObjectStorage self, Object key, @SuppressWarnings("unused") long keyHash, Object value,
                            @SuppressWarnings("unused") boolean transition, DynamicObjectLibrary dylib,
                            @Cached PyObjectHashNode hashNode,
                            @Cached ObjectHashMap.PutNode putNode) {
                EconomicMapStorage result = dynamicObjectStorageToEconomicMap(inliningTarget, self, dylib, hashNode, putNode);
                putNode.execute(frame, inliningTarget, result.map, key, keyHash, value);
                return result;
            }
        }
    }

    /**
     * This unfortunately duplicates most of the logic in {@link HashingStorageSetItemWithHash}, but
     * here we want to avoid computing the hash if we happen to be setting an item into a storage
     * that does not need the Python hash at all.
     */
    @GenerateUncached
    @GenerateInline(inlineByDefault = true)
    @ImportStatic(PGuards.class)
    public abstract static class HashingStorageSetItem extends Node {

        @NeverDefault
        public static HashingStorageSetItem create() {
            return HashingStorageSetItemNodeGen.create();
        }

        public static HashingStorage executeUncached(HashingStorage storage, Object key, Object value) {
            return HashingStorageSetItemNodeGen.getUncached().execute(null, null, storage, key, value);
        }

        public final HashingStorage executeCached(Frame frame, HashingStorage storage, Object key, Object value) {
            return execute(frame, this, storage, key, value);
        }

        public final HashingStorage execute(Node inliningTarget, HashingStorage self, TruffleString key, Object value) {
            // Shortcut for frequent usage with TruffleString. We do not need a frame in such case,
            // because the string's __hash__ does not need it. Some fast-paths avoid even invoking
            // __hash__ for string keys
            return execute(null, inliningTarget, self, key, value);
        }

        public abstract HashingStorage execute(Frame frame, Node inliningTarget, HashingStorage self, Object key, Object value);

        @Specialization
        static HashingStorage economicMap(Frame frame, Node inliningTarget, EconomicMapStorage self, Object key, Object value,
                        @Exclusive @Cached PyUnicodeCheckExactNode isBuiltinString,
                        @Exclusive @Cached PyObjectHashNode hashNode,
                        @Exclusive @Cached ObjectHashMap.PutNode putNode) {
            putNode.execute(frame, inliningTarget, self.map, key, hashNode.execute(frame, inliningTarget, key), value);
            if (!self.map.hasSideEffect() && !isBuiltinString.execute(inliningTarget, key)) {
                self.map.setSideEffectingKeysFlag();
            }
            return self;
        }

        @Specialization
        static HashingStorage empty(Frame frame, Node inliningTarget, @SuppressWarnings("unused") EmptyStorage self, Object key, Object value,
                        @Exclusive @Cached PyUnicodeCheckExactNode isBuiltinString,
                        @Exclusive @Cached PyObjectHashNode hashNode,
                        @Exclusive @Cached ObjectHashMap.PutNode putNode) {
            // The ObjectHashMap.PutNode is @Exclusive because profiles for a put into a freshly new
            // allocated map can be quite different to profiles in the other situations when we are
            // putting into a map that already has or will have some more items in it
            // It is also @Cached(inline = false) because inlining it triggers GR-44836
            // TODO: do we want to try DynamicObjectStorage if the key is a string?
            return economicMap(frame, inliningTarget, EconomicMapStorage.create(1), key, value, isBuiltinString, hashNode, putNode);
        }

        @Specialization(guards = "!self.shouldTransitionOnPut()")
        static HashingStorage domStringKey(Node inliningTarget, DynamicObjectStorage self, TruffleString key, Object value,
                        @Cached InlinedBranchProfile invalidateMroProfile,
                        @Shared @CachedLibrary(limit = "3") DynamicObjectLibrary dylib) {
            self.setStringKey(key, value, dylib, inliningTarget, invalidateMroProfile);
            return self;
        }

        @Specialization(replaces = "domStringKey")
        @InliningCutoff
        static HashingStorage dom(Frame frame, Node inliningTarget, DynamicObjectStorage self, Object key, Object value,
                        @Cached InlinedConditionProfile shouldTransitionProfile,
                        @Exclusive @Cached PyUnicodeCheckExactNode isBuiltinString,
                        @Shared @CachedLibrary(limit = "3") DynamicObjectLibrary dylib,
                        @Cached DOMStorageSetItem domNode) {
            boolean transition = true;
            if (shouldTransitionProfile.profile(inliningTarget, !self.shouldTransitionOnPut())) {
                if (isBuiltinString.execute(inliningTarget, key)) {
                    transition = false;
                }
            }
            return domNode.execute(frame, inliningTarget, self, key, value, transition, dylib);
        }

        @Specialization
        @InliningCutoff
        static HashingStorage keywords(Frame frame, Node inliningTarget, KeywordsStorage self, Object key, Object value,
                        @Exclusive @Cached PyObjectHashNode hashNode,
                        @Exclusive @Cached PyUnicodeCheckExactNode isBuiltinString,
                        @Exclusive @Cached ObjectHashMap.PutNode putNode,
                        @Cached EconomicMapSetStringKey specializedPutNode) {
            // TODO: do we want to try DynamicObjectStorage if the key is a string?
            EconomicMapStorage result = EconomicMapStorage.create(self.length());
            self.addAllTo(inliningTarget, result, specializedPutNode);
            return economicMap(frame, inliningTarget, result, key, value, isBuiltinString, hashNode, putNode);
        }

        @GenerateUncached
        @GenerateInline
        @GenerateCached(false)
        @ImportStatic(PGuards.class)
        abstract static class DOMStorageSetItem extends Node {
            public abstract HashingStorage execute(Frame frame, Node inliningTarget, DynamicObjectStorage self, Object key, Object value,
                            boolean transition, DynamicObjectLibrary dylib);

            @Specialization(guards = {"!transition", "isBuiltinString.execute(inliningTarget, key)"}, limit = "1")
            static HashingStorage domStringKey(Node inliningTarget, DynamicObjectStorage self, Object key, Object value,
                            @SuppressWarnings("unused") boolean transition, DynamicObjectLibrary dylib,
                            @SuppressWarnings("unused") @Cached PyUnicodeCheckExactNode isBuiltinString,
                            @Cached CastBuiltinStringToTruffleStringNode castStr,
                            @Cached InlinedBranchProfile invalidateMroProfile) {
                self.setStringKey(castStr.execute(inliningTarget, key), value, dylib, inliningTarget, invalidateMroProfile);
                return self;
            }

            @Fallback
            static HashingStorage domTransition(Frame frame, Node inliningTarget, DynamicObjectStorage self, Object key, Object value,
                            @SuppressWarnings("unused") boolean transition, DynamicObjectLibrary dylib,
                            @Cached PyObjectHashNode hashNode,
                            @Cached ObjectHashMap.PutNode putNode) {
                EconomicMapStorage result = dynamicObjectStorageToEconomicMap(inliningTarget, self, dylib, hashNode, putNode);
                putNode.execute(frame, inliningTarget, result.map, key, hashNode.execute(frame, inliningTarget, key), value);
                return result;
            }
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    @ImportStatic({PGuards.class})
    public abstract static class HashingStorageDelItem extends Node {
        public static void executeUncached(HashingStorage self, Object key, PHashingCollection toUpdate) {
            HashingStorageDelItemNodeGen.getUncached().executeWithAsserts(null, null, self, key, false, toUpdate);
        }

        public static void executeUncachedWithHash(EconomicMapStorage storage, Object key, long hash) {
            ObjectHashMapFactory.RemoveNodeGen.getUncached().execute(null, null, storage.map, key, hash);
        }

        public final void execute(Node inliningTarget, HashingStorage self, TruffleString key, PHashingCollection toUpdate) {
            // Shortcut for frequent usage with TruffleString. We do not need a frame in such case,
            // because the string's __hash__ does not need it. Some fast-paths avoid even invoking
            // __hash__ for string keys
            executeWithAsserts(null, inliningTarget, self, key, false, toUpdate);
        }

        public final void execute(Frame frame, Node inliningTarget, HashingStorage self, Object key, PHashingCollection toUpdate) {
            executeWithAsserts(frame, inliningTarget, self, key, false, toUpdate);
        }

        public final Object executePop(Node inliningTarget, HashingStorage self, TruffleString key, PHashingCollection toUpdate) {
            return executeWithAsserts(null, inliningTarget, self, key, true, toUpdate);
        }

        public final Object executePop(Frame frame, Node inliningTarget, HashingStorage self, Object key, PHashingCollection toUpdate) {
            return executeWithAsserts(frame, inliningTarget, self, key, true, toUpdate);
        }

        final Object executeWithAsserts(Frame frame, Node inliningTarget, HashingStorage self, Object key, boolean isPop, PHashingCollection toUpdate) {
            assert toUpdate != null;
            CompilerAsserts.partialEvaluationConstant(isPop);
            return executeImpl(frame, inliningTarget, self, key, isPop, toUpdate);
        }

        abstract Object executeImpl(Frame frame, Node inliningTarget, HashingStorage self, Object key, boolean isPop, PHashingCollection toUpdate);

        @Specialization(guards = "isEconomicMapOrEmpty(self)")
        static Object economicMap(Frame frame, Node inliningTarget, HashingStorage self, Object key, boolean isPop, @SuppressWarnings("unused") PHashingCollection toUpdate,
                        @Exclusive @Cached InlinedBranchProfile isEconomicMapProfile,
                        @Exclusive @Cached PyObjectHashNode hashNode,
                        @Exclusive @Cached ObjectHashMap.RemoveNode removeNode) {
            // We must not omit the potentially side-effecting call to __hash__
            long hash = hashNode.execute(frame, inliningTarget, key);
            if (self instanceof EconomicMapStorage economicMap) {
                isEconomicMapProfile.enter(inliningTarget);
                Object result = removeNode.execute(frame, inliningTarget, economicMap.map, key, hash);
                return isPop ? result : null;
            }
            return null;
        }

        @Specialization
        @InliningCutoff
        static Object domStringKey(Frame frame, Node inliningTarget, DynamicObjectStorage self, Object keyObj, boolean isPop, @SuppressWarnings("unused") PHashingCollection toUpdate,
                        @Cached PyUnicodeCheckExactNode isBuiltinString,
                        @Cached CastBuiltinStringToTruffleStringNode castStr,
                        @Exclusive @Cached PyObjectHashNode hashNode,
                        @Exclusive @Cached InlinedBranchProfile invalidateMroProfile,
                        @CachedLibrary(limit = "3") DynamicObjectLibrary dylib) {
            if (!isBuiltinString.execute(inliningTarget, keyObj)) {
                // Just for the potential side effects
                hashNode.execute(frame, inliningTarget, keyObj);
                return null;
            }
            TruffleString key = castStr.execute(inliningTarget, keyObj);
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

        @Specialization
        @InliningCutoff
        static Object keywords(Frame frame, Node inliningTarget, KeywordsStorage self, Object key, boolean isPop, PHashingCollection toUpdate,
                        @Exclusive @Cached PyObjectHashNode hashNode,
                        @Exclusive @Cached ObjectHashMap.RemoveNode removeNode,
                        @Cached EconomicMapSetStringKey specializedPutNode) {
            EconomicMapStorage newStorage = EconomicMapStorage.create(self.length());
            self.addAllTo(inliningTarget, newStorage, specializedPutNode);
            toUpdate.setDictStorage(newStorage);
            Object result = removeNode.execute(frame, inliningTarget, newStorage.map, key, hashNode.execute(frame, inliningTarget, key));
            return isPop ? result : null;
        }
    }

    @GenerateUncached
    @GenerateInline(inlineByDefault = true)
    public abstract static class HashingStorageLen extends Node {
        public static int executeUncached(HashingStorage dictStorage) {
            return HashingStorageLenNodeGen.getUncached().execute(null, dictStorage);
        }

        public final int executeCached(HashingStorage storage) {
            return execute(this, storage);
        }

        @NeverDefault
        public static HashingStorageLen create() {
            return HashingStorageLenNodeGen.create();
        }

        public abstract int execute(Node inliningTarget, HashingStorage storage);

        @Specialization
        static int economicMap(EconomicMapStorage self) {
            return self.length();
        }

        @Specialization
        @InliningCutoff
        static int dom(Node inliningTarget, DynamicObjectStorage self,
                        @Cached(inline = false) DynamicObjectStorage.LengthNode lengthNode) {
            return lengthNode.execute(self);
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
    @GenerateInline
    @GenerateCached(false)
    public abstract static class HashingStorageClear extends Node {
        public abstract HashingStorage execute(Node inliningTarget, HashingStorage storage);

        @Specialization
        static HashingStorage economicMap(EconomicMapStorage self) {
            self.clear();
            return self;
        }

        @Specialization
        @InliningCutoff
        static HashingStorage dom(Node inliningTarget, DynamicObjectStorage self,
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
    @GenerateInline(inlineByDefault = true)
    public abstract static class HashingStorageCopy extends Node {

        @NeverDefault
        public static HashingStorageCopy create() {
            return HashingStorageCopyNodeGen.create();
        }

        public static HashingStorage executeUncached(HashingStorage source) {
            return HashingStorageCopyNodeGen.getUncached().execute(null, source);
        }

        public final HashingStorage executeCached(HashingStorage source) {
            return execute(this, source);
        }

        public abstract HashingStorage execute(Node inliningTarget, HashingStorage source);

        @Specialization
        static HashingStorage economic(EconomicMapStorage map) {
            return map.copy();
        }

        @Specialization
        static EmptyStorage empty(@SuppressWarnings("unused") EmptyStorage map) {
            return EmptyStorage.INSTANCE;
        }

        @Specialization
        @InliningCutoff
        static DynamicObjectStorage dom(Node inliningTarget, DynamicObjectStorage dom,
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
    @GenerateInline(inlineByDefault = true)
    @ImportStatic({PGuards.class})
    public abstract static class HashingStorageGetIterator extends Node {
        public static HashingStorageIterator executeUncached(HashingStorage storage) {
            return HashingStorageGetIteratorNodeGen.getUncached().execute(null, storage);
        }

        @NeverDefault
        public static HashingStorageGetIterator create() {
            return HashingStorageGetIteratorNodeGen.create();
        }

        public final HashingStorageIterator executeCached(HashingStorage storage) {
            return execute(this, storage);
        }

        public final HashingStorageIterator execute(Node node, HashingStorage storage) {
            HashingStorageIterator result = executeImpl(node, storage);
            assert !result.isReverse;
            return result;
        }

        public abstract HashingStorageIterator executeImpl(Node node, HashingStorage storage);

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
    @GenerateInline
    @GenerateCached(false)
    @ImportStatic({PGuards.class})
    public abstract static class HashingStorageGetReverseIterator extends Node {
        public static HashingStorageIterator executeUncached(HashingStorage storage) {
            return HashingStorageGetReverseIteratorNodeGen.getUncached().execute(null, storage);
        }

        public final HashingStorageIterator execute(Node node, HashingStorage storage) {
            HashingStorageIterator result = executeImpl(node, storage);
            assert result.isReverse;
            return result;
        }

        abstract HashingStorageIterator executeImpl(Node node, HashingStorage storage);

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
    @GenerateInline(inlineByDefault = true)
    @ImportStatic({PGuards.class})
    public abstract static class HashingStorageIteratorNext extends Node {
        public static boolean executeUncached(HashingStorage storage, HashingStorageIterator it) {
            return HashingStorageIteratorNextNodeGen.getUncached().execute(null, storage, it);
        }

        public final boolean executeCached(HashingStorage storage, HashingStorageIterator it) {
            return execute(this, storage, it);
        }

        @NeverDefault
        public static HashingStorageIteratorNext create() {
            return HashingStorageIteratorNextNodeGen.create();
        }

        /**
         * Returns {@code true} if the iterator has next value. Use nodes to get the current value,
         * key, and hash of the current key.
         */
        public abstract boolean execute(Node node, HashingStorage storage, HashingStorageIterator it);

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
    @GenerateInline(inlineByDefault = true)
    @ImportStatic({PGuards.class})
    public abstract static class HashingStorageIteratorValue extends Node {
        public static Object executeUncached(HashingStorage storage, HashingStorageIterator it) {
            return HashingStorageIteratorValueNodeGen.getUncached().execute(null, storage, it);
        }

        public final Object executeCached(HashingStorage storage, HashingStorageIterator it) {
            return execute(this, storage, it);
        }

        @NeverDefault
        public static HashingStorageIteratorValue create() {
            return HashingStorageIteratorValueNodeGen.create();
        }

        public abstract Object execute(Node node, HashingStorage storage, HashingStorageIterator it);

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

    @GenerateUncached
    @GenerateInline(inlineByDefault = true)
    @ImportStatic({PGuards.class})
    public abstract static class HashingStorageIteratorKey extends Node {
        public static Object executeUncached(HashingStorage storage, HashingStorageIterator it) {
            return HashingStorageIteratorKeyNodeGen.getUncached().execute(null, storage, it);
        }

        public final Object executeCached(HashingStorage storage, HashingStorageIterator it) {
            return execute(this, storage, it);
        }

        @NeverDefault
        public static HashingStorageIteratorKey create() {
            return HashingStorageIteratorKeyNodeGen.create();
        }

        public abstract Object execute(Node node, HashingStorage storage, HashingStorageIterator it);

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
    @GenerateInline
    @GenerateCached(false)
    @ImportStatic({PGuards.class})
    public abstract static class HashingStorageIteratorKeyHash extends Node {

        public static long executeUncached(HashingStorage storage, HashingStorageIterator it) {
            return HashingStorageIteratorKeyHashNodeGen.getUncached().execute(null, storage, it);
        }

        public abstract long execute(Node node, HashingStorage storage, HashingStorageIterator it);

        @Specialization
        static long economicMap(EconomicMapStorage self, HashingStorageIterator it) {
            return self.map.hashes[it.index];
        }

        @Specialization
        static long dom(@SuppressWarnings("unused") DynamicObjectStorage self, HashingStorageIterator it,
                        @Shared("hash") @Cached(inline = false) TruffleString.HashCodeNode hashNode) {
            return PyObjectHashNode.hash((TruffleString) it.domKeys[it.index], hashNode);
        }

        @Specialization
        @SuppressWarnings("unused")
        static long empty(EmptyStorage self, HashingStorageIterator it) {
            throw CompilerDirectives.shouldNotReachHere("empty in HashingStorageIteratorKey");
        }

        @Specialization
        static long keywords(KeywordsStorage self, HashingStorageIterator it,
                        @Shared("hash") @Cached(inline = false) TruffleString.HashCodeNode hashNode) {
            return PyObjectHashNode.hash(self.keywords[it.index].getName(), hashNode);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class HashingStoragePop extends Node {
        /**
         * Returns {@code null} if there is nothing to pop, otherwise popped [key, value].
         */
        public abstract Object[] execute(Node inliningTarget, HashingStorage storage, PHashingCollection toUpdate);

        @Specialization
        static Object[] economicMap(Node inliningTarget, EconomicMapStorage self, @SuppressWarnings("unused") PHashingCollection toUpdate,
                        @Cached ObjectHashMap.PopNode popNode) {
            return popNode.execute(inliningTarget, self.map);
        }

        // Other storages should not have any side effects, it's OK if they call __eq__
        @Fallback
        static Object[] others(Node inliningTarget, HashingStorage storage, PHashingCollection toUpdate,
                        @Cached HashingStorageDelItem delItem,
                        @Cached HashingStorageGetReverseIterator getReverseIterator,
                        @Cached HashingStorageIteratorNext iterNext,
                        @Cached HashingStorageIteratorKey iterKey,
                        @Cached HashingStorageIteratorValue iterValue) {
            HashingStorageIterator it = getReverseIterator.execute(inliningTarget, storage);
            if (iterNext.execute(inliningTarget, storage, it)) {
                Object key = iterKey.execute(inliningTarget, storage, it);
                var result = new Object[]{key, iterValue.execute(inliningTarget, storage, it)};
                delItem.execute(null, inliningTarget, storage, key, toUpdate);
                return result;
            }
            return null;
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    @ImportStatic({PGuards.class})
    public abstract static class HashingStorageEq extends Node {
        public abstract boolean execute(Frame frame, Node inliningTarget, HashingStorage a, HashingStorage b);

        @Specialization
        static boolean doIt(Frame frame, Node inliningTarget, HashingStorage aStorage, HashingStorage bStorage,
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
            if (lenANode.execute(inliningTarget, aStorage) != lenBNode.execute(inliningTarget, bStorage)) {
                return false;
            }
            int index = 0;
            try {
                HashingStorageIterator aIter = getAIter.execute(inliningTarget, aStorage);
                while (loopProfile.profile(inliningTarget, aIterNext.execute(inliningTarget, aStorage, aIter))) {
                    if (CompilerDirectives.hasNextTier()) {
                        index++;
                    }

                    Object aKey = aIterKey.execute(inliningTarget, aStorage, aIter);
                    long aHash = aIterHash.execute(inliningTarget, aStorage, aIter);
                    Object bValue = getBNode.execute(frame, inliningTarget, bStorage, aKey, aHash);
                    Object aValue = aIterValue.execute(inliningTarget, aStorage, aIter);
                    if (earlyExitProfile.profile(inliningTarget, !(bValue == null || !eqNode.compare(frame, inliningTarget, bValue, aValue)))) {
                        // if->continue such that the "true" count of the profile represents the
                        // loop iterations and the "false" count the early exit
                        continue;
                    }
                    return false;
                }
            } finally {
                if (index != 0) {
                    LoopNode.reportLoopCount(inliningTarget, index);
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
    @GenerateInline
    @GenerateCached(false)
    @ImportStatic({PGuards.class})
    public abstract static class HashingStorageForEach extends Node {
        @SuppressWarnings("unchecked")
        public static <T> T executeUncached(HashingStorage storage, HashingStorageForEachCallback<T> callback, T accumulator) {
            return (T) HashingStorageForEachNodeGen.getUncached().executeUntyped(null, null, storage, (HashingStorageForEachCallback<Object>) callback, accumulator);
        }

        @SuppressWarnings("unchecked")
        public final <T> T execute(Frame frame, Node inliningTarget, HashingStorage storage, HashingStorageForEachCallback<T> callback, T accumulator) {
            CompilerAsserts.partialEvaluationConstant(callback);
            return (T) executeUntyped(frame, inliningTarget, storage, (HashingStorageForEachCallback<Object>) callback, accumulator);
        }

        abstract Object executeUntyped(Frame frame, Node inliningTarget, HashingStorage storage, HashingStorageForEachCallback<Object> callback, Object accumulator);

        @Specialization
        static Object doIt(Frame frame, Node callbackInliningTarget, HashingStorage storage, HashingStorageForEachCallback<Object> callback, Object accumulatorIn,
                        @Bind("this") Node inliningTarget,
                        @Cached HashingStorageGetIterator getIter,
                        @Cached HashingStorageIteratorNext iterNext,
                        @Cached InlinedLoopConditionProfile loopProfile) {
            int index = 0;
            Object accumulator = accumulatorIn;
            try {
                HashingStorageIterator aIter = getIter.execute(inliningTarget, storage);
                while (loopProfile.profile(inliningTarget, iterNext.execute(inliningTarget, storage, aIter))) {
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
        static ResultAndOther doGeneric(Frame frame, Node inliningTarget, HashingStorage storage, HashingStorageIterator it, ResultAndOther acc,
                        @Cached ObjectHashMap.PutNode putResultNode,
                        @Cached HashingStorageGetItemWithHash getFromOther,
                        @Cached HashingStorageIteratorKey iterKey,
                        @Cached HashingStorageIteratorValue iterValue,
                        @Cached HashingStorageIteratorKeyHash iterHash) {
            Object key = iterKey.execute(inliningTarget, storage, it);
            long hash = iterHash.execute(inliningTarget, storage, it);
            Object otherValue = getFromOther.execute(frame, inliningTarget, acc.other, key, hash);
            if (otherValue == null) {
                putResultNode.put(frame, inliningTarget, acc.result, key, hash, iterValue.execute(inliningTarget, storage, it));
            }
            return acc;
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    @ImportStatic({PGuards.class})
    public abstract static class HashingStorageXor extends Node {
        public abstract HashingStorage execute(Frame frame, Node inliningTarget, HashingStorage a, HashingStorage b);

        @Specialization
        static HashingStorage doIt(Frame frame, Node inliningTarget, HashingStorage aStorage, HashingStorage bStorage,
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
        static ResultAndOther doGeneric(Frame frame, Node inliningTarget, HashingStorage storage, HashingStorageIterator it, ResultAndOther acc,
                        @Cached ObjectHashMap.PutNode putResultNode,
                        @Cached HashingStorageGetItemWithHash getFromOther,
                        @Cached HashingStorageIteratorKey iterKey,
                        @Cached HashingStorageIteratorKeyHash iterHash) {
            Object key = iterKey.execute(inliningTarget, storage, it);
            long hash = iterHash.execute(inliningTarget, storage, it);
            Object otherValue = getFromOther.execute(frame, inliningTarget, acc.other, key, hash);
            if (otherValue != null) {
                putResultNode.put(frame, inliningTarget, acc.result, key, hash, otherValue);
            }
            return acc;
        }
    }

    /**
     * In case the key is in both, this keeps the value from {@code b}.
     */
    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    @ImportStatic({PGuards.class})
    public abstract static class HashingStorageIntersect extends Node {
        public abstract HashingStorage execute(Frame frame, Node inliningTarget, HashingStorage a, HashingStorage b);

        @Specialization
        static HashingStorage doIt(Frame frame, Node inliningTarget, HashingStorage aStorage, HashingStorage bStorage,
                        @Cached HashingStorageForEach forEachA,
                        @Cached HashingStorageIntersectCallback callback) {
            final EconomicMapStorage result = EconomicMapStorage.createWithSideEffects();
            ResultAndOther acc = new ResultAndOther(result.map, bStorage);
            forEachA.execute(frame, inliningTarget, aStorage, callback, acc);
            return result;
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    @ImportStatic({PGuards.class})
    public abstract static class HashingStorageDiffCallback extends HashingStorageForEachCallback<ResultAndOther> {

        @Override
        public abstract ResultAndOther execute(Frame frame, Node inliningTarget, HashingStorage storage, HashingStorageIterator it, ResultAndOther accumulator);

        @Specialization
        static ResultAndOther doGeneric(Frame frame, Node inliningTarget, HashingStorage storage, HashingStorageIterator it, ResultAndOther acc,
                        @Cached ObjectHashMap.PutNode putResultNode,
                        @Cached HashingStorageGetItemWithHash getFromOther,
                        @Cached HashingStorageIteratorKey iterKey,
                        @Cached HashingStorageIteratorKeyHash iterHash,
                        @Cached HashingStorageIteratorValue iterValue) {
            Object key = iterKey.execute(inliningTarget, storage, it);
            long hash = iterHash.execute(inliningTarget, storage, it);
            Object otherValue = getFromOther.execute(frame, inliningTarget, acc.other, key, hash);
            if (otherValue == null) {
                putResultNode.put(frame, inliningTarget, acc.result, key, hash, iterValue.execute(inliningTarget, storage, it));
            }
            return acc;
        }
    }

    /**
     * {@code a-b}
     */
    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    @ImportStatic({PGuards.class})
    public abstract static class HashingStorageDiff extends Node {
        public abstract HashingStorage execute(Frame frame, Node inliningTarget, HashingStorage a, HashingStorage b);

        @Specialization
        static HashingStorage doIt(Frame frame, Node inliningTarget, HashingStorage aStorage, HashingStorage bStorage,
                        @Cached HashingStorageForEach forEachA,
                        @Cached HashingStorageDiffCallback callback) {
            final EconomicMapStorage result = EconomicMapStorage.createWithSideEffects();
            ResultAndOther acc = new ResultAndOther(result.map, bStorage);
            forEachA.execute(frame, inliningTarget, aStorage, callback, acc);
            return result;
        }
    }

    /**
     * Throws {@link AbortIteration} if a key that's missing in {@code bStorage} is found.
     */
    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    @ImportStatic({PGuards.class})
    public abstract static class HashingStorageCompareKeysCallback extends HashingStorageForEachCallback<HashingStorage> {

        @Override
        public abstract HashingStorage execute(Frame frame, Node inliningTarget, HashingStorage aStorage, HashingStorageIterator it, HashingStorage bStorage);

        @Specialization
        static HashingStorage doGeneric(Frame frame, Node inliningTarget, HashingStorage aStorage, HashingStorageIterator it, HashingStorage bStorage,
                        @Cached HashingStorageGetItemWithHash getFromOther,
                        @Cached HashingStorageIteratorKey iterKey,
                        @Cached HashingStorageIteratorKeyHash iterHash) {
            Object key = iterKey.execute(inliningTarget, aStorage, it);
            long hash = iterHash.execute(inliningTarget, aStorage, it);
            Object otherValue = getFromOther.execute(frame, inliningTarget, bStorage, key, hash);
            if (otherValue == null) {
                throw AbortIteration.INSTANCE;
            }
            return bStorage;
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    @ImportStatic({PGuards.class})
    public abstract static class HashingStorageCompareKeys extends Node {
        public abstract int execute(Frame frame, Node inliningTarget, HashingStorage a, HashingStorage b);

        @Specialization(guards = "aStorage == bStorage")
        @SuppressWarnings("unused")
        static int doSame(HashingStorage aStorage, HashingStorage bStorage) {
            return 0;
        }

        @Specialization(guards = "aStorage != bStorage")
        static int doGeneric(Frame frame, Node inliningTarget, HashingStorage aStorage, HashingStorage bStorage,
                        @Cached HashingStorageLen aLenNode,
                        @Cached HashingStorageLen bLenNode,
                        @Cached HashingStorageForEach forEachA,
                        @Cached HashingStorageCompareKeysCallback callback) {
            int aLen = aLenNode.execute(inliningTarget, aStorage);
            int bLen = bLenNode.execute(inliningTarget, bStorage);
            if (aLen > bLen) {
                return 1;
            }
            try {
                forEachA.execute(frame, inliningTarget, aStorage, callback, bStorage);
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
    @GenerateInline
    @GenerateCached(false)
    @ImportStatic({PGuards.class})
    public abstract static class HashingStorageAreDisjointCallback extends HashingStorageForEachCallback<HashingStorage> {

        @Override
        public abstract HashingStorage execute(Frame frame, Node inliningTarget, HashingStorage aStorage, HashingStorageIterator it, HashingStorage bStorage);

        @Specialization
        static HashingStorage doGeneric(Frame frame, Node inliningTarget, HashingStorage aStorage, HashingStorageIterator it, HashingStorage bStorage,
                        @Cached HashingStorageGetItemWithHash getFromOther,
                        @Cached HashingStorageIteratorKey iterKey,
                        @Cached HashingStorageIteratorKeyHash iterHash) {
            Object key = iterKey.execute(inliningTarget, aStorage, it);
            long hash = iterHash.execute(inliningTarget, aStorage, it);
            Object otherValue = getFromOther.execute(frame, inliningTarget, bStorage, key, hash);
            if (otherValue != null) {
                throw AbortIteration.INSTANCE;
            }
            return bStorage;
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    @ImportStatic({PGuards.class})
    public abstract static class HashingStorageAreDisjoint extends Node {
        public abstract boolean execute(Frame frame, Node inliningTarget, HashingStorage a, HashingStorage b);

        @Specialization
        static boolean doGeneric(Frame frame, Node inliningTarget, HashingStorage aStorage, HashingStorage bStorage,
                        @Cached HashingStorageLen aLenNode,
                        @Cached HashingStorageLen bLenNode,
                        @Cached HashingStorageForEach forEach,
                        @Cached HashingStorageAreDisjointCallback callback) {
            int aLen = aLenNode.execute(inliningTarget, aStorage);
            int bLen = bLenNode.execute(inliningTarget, bStorage);
            try {
                if (aLen > bLen) {
                    forEach.execute(frame, inliningTarget, bStorage, callback, aStorage);
                } else {
                    forEach.execute(frame, inliningTarget, aStorage, callback, bStorage);
                }
                return true;
            } catch (AbortIteration ignore) {
                return false;
            }
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class HashingStorageTransferItem extends HashingStorageForEachCallback<HashingStorage> {
        @Override
        public abstract HashingStorage execute(Frame frame, Node inliningTarget, HashingStorage src, HashingStorageIterator it, HashingStorage destStorage);

        @Specialization
        static EconomicMapStorage economic2Economic(Frame frame, Node inliningTarget, EconomicMapStorage src, HashingStorageIterator it, EconomicMapStorage destStorage,
                        @Cached PutNode putNode) {
            ObjectHashMap srcMap = src.map;
            putNode.put(frame, inliningTarget, destStorage.map, srcMap.getKey(it.index), srcMap.hashes[it.index], srcMap.getValue(it.index));
            return destStorage;
        }

        @Specialization(replaces = "economic2Economic")
        @InliningCutoff
        static HashingStorage economic2Generic(Frame frame, Node inliningTarget, EconomicMapStorage src, HashingStorageIterator it, HashingStorage destStorage,
                        @Cached HashingStorageSetItemWithHash setItemWithHash) {
            // Note that the point is to avoid side-effecting __hash__ call. Since the source is
            // economic map, the key may be an arbitrary object.
            ObjectHashMap srcMap = src.map;
            return setItemWithHash.execute(frame, inliningTarget, destStorage, srcMap.getKey(it.index), srcMap.hashes[it.index], srcMap.getValue(it.index));
        }

        @Fallback
        @InliningCutoff
        static HashingStorage generic2Generic(Frame frame, Node inliningTarget, HashingStorage src, HashingStorageIterator it, HashingStorage destStorage,
                        @Cached HashingStorageIteratorKey iterKey,
                        @Cached HashingStorageIteratorValue iterValue,
                        @Cached HashingStorageSetItem setItem) {
            // We know that for all other storages the key hash must be side effect free, so we can
            // just insert it leaving it up to the HashingStorageSetItem whether we need to compute
            // hash or not. Since the src is not EconomicMapStorage, we do not know the hash anyway.
            // We still pass the frame, because the insertion may trigger __eq__
            return setItem.execute(frame, inliningTarget, destStorage, iterKey.execute(inliningTarget, src, it), iterValue.execute(inliningTarget, src, it));
        }
    }

    @GenerateUncached
    @GenerateInline(inlineByDefault = true)
    public abstract static class HashingStorageAddAllToOther extends Node {
        public static void executeUncached(HashingStorage source, PHashingCollection dest) {
            HashingStorageAddAllToOtherNodeGen.getUncached().execute(null, null, source, dest);
        }

        @NeverDefault
        public static HashingStorageAddAllToOther create() {
            return HashingStorageAddAllToOtherNodeGen.create();
        }

        public final void execute(Frame frame, Node inliningTarget, HashingStorage source, PHashingCollection dest) {
            dest.setDictStorage(execute(frame, inliningTarget, source, dest.getDictStorage()));
        }

        public final HashingStorage executeCached(Frame frame, HashingStorage source, HashingStorage dest) {
            return execute(frame, this, source, dest);
        }

        public abstract HashingStorage execute(Frame frame, Node inliningTarget, HashingStorage source, HashingStorage dest);

        @Specialization(guards = "source == dest")
        @SuppressWarnings("unused")
        static HashingStorage doIdentical(Frame frame, HashingStorage source, HashingStorage dest) {
            return dest;
        }

        @Specialization(guards = "source != dest")
        static HashingStorage doIt(Frame frame, Node inliningTarget, HashingStorage source, HashingStorage dest,
                        @Cached HashingStorageForEach forEach,
                        @Cached HashingStorageTransferItem transferItem) {
            return forEach.execute(frame, inliningTarget, source, transferItem, dest);
        }
    }
}
