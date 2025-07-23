/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageCopy;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorKey;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorNext;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageSetItem;
import com.oracle.graal.python.builtins.objects.common.ObjectHashMap.PutNode;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodesFactory.GetClonedHashingStorageNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodesFactory.SetItemNodeGen;
import com.oracle.graal.python.builtins.objects.dict.DictNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.dict.PDictView;
import com.oracle.graal.python.builtins.objects.set.PBaseSet;
import com.oracle.graal.python.lib.IteratorExhausted;
import com.oracle.graal.python.lib.PyIterNextNode;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedLoopConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringIterator;

public abstract class HashingCollectionNodes {

    @GenerateUncached
    @GenerateInline(inlineByDefault = true)
    @GenerateCached
    @ImportStatic(PGuards.class)
    public abstract static class SetItemNode extends PNodeWithContext {
        public abstract void execute(Frame frame, Node inliningTarget, Object c, Object key, Object value);

        public final void executeCached(Frame frame, Object c, Object key, Object value) {
            execute(frame, this, c, key, value);
        }

        @Specialization
        static void doSetItem(Frame frame, Node inliningTarget, Object c, Object key, Object value,
                        @Cached DictNodes.GetDictStorageNode getStorageNode,
                        @Cached DictNodes.UpdateDictStorageNode updateStorageNode,
                        @Cached HashingStorageSetItem setItem) {
            var storage = getStorageNode.execute(inliningTarget, c);
            var newStorage = setItem.execute(frame, inliningTarget, storage, key, value);
            updateStorageNode.execute(inliningTarget, c, storage, newStorage);
        }

        @NeverDefault
        public static SetItemNode create() {
            return SetItemNodeGen.create();
        }

        @NeverDefault
        public static SetItemNode getUncached() {
            return SetItemNodeGen.getUncached();
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @ImportStatic({PGuards.class})
    abstract static class SetValueHashingStorageNode extends PNodeWithContext {
        abstract HashingStorage execute(VirtualFrame frame, Node inliningTarget, HashingStorage iterator, Object value);

        @Specialization
        static HashingStorage doEconomicStorage(VirtualFrame frame, Node inliningTarget, EconomicMapStorage map, Object value,
                        @Exclusive @Cached ObjectHashMap.PutNode putNode,
                        @Exclusive @Cached InlinedLoopConditionProfile loopProfile) {
            // We want to avoid calling __hash__() during map.put
            map.setValueForAllKeys(frame, inliningTarget, value, putNode, loopProfile);
            return map;
        }

        // @Exclusive for truffle-interpreted-performance
        @Specialization(guards = "!isEconomicMapStorage(map)")
        @InliningCutoff
        static HashingStorage doGeneric(VirtualFrame frame, Node inliningTarget, HashingStorage map, Object value,
                        @Cached HashingStorageSetItem setItem,
                        @Cached HashingStorageGetIterator getIterator,
                        @Cached HashingStorageIteratorNext itNext,
                        @Cached HashingStorageIteratorKey itKey,
                        @Exclusive @Cached PutNode putNode,
                        @Exclusive @Cached InlinedLoopConditionProfile loopProfile) {
            HashingStorageIterator it = getIterator.execute(inliningTarget, map);
            while (itNext.execute(inliningTarget, map, it)) {
                Object key = itKey.execute(inliningTarget, map, it);
                HashingStorage newStorage = setItem.execute(frame, inliningTarget, map, key, value);
                if (newStorage != map) {
                    // when the storage changes, the iterator state is not a reliable cursor
                    // anymore and we need to restart.
                    if (newStorage instanceof EconomicMapStorage mapStorage) {
                        mapStorage.setValueForAllKeys(frame, inliningTarget, value, putNode, loopProfile);
                        return mapStorage;
                    } else {
                        throw CompilerDirectives.shouldNotReachHere("We only generalize to EconomicMapStorage");
                    }
                }
            }
            return map;
        }

        protected static boolean isEconomicMapStorage(Object o) {
            return o instanceof EconomicMapStorage;
        }
    }

    /**
     * Gets clone of the keys of the storage with all values set to given value or (when used to
     * create a set or frozenset) to NO_VALUE.
     */
    @GenerateInline(inlineByDefault = true)
    public abstract static class GetClonedHashingStorageNode extends PNodeWithContext {
        protected abstract HashingStorage execute(VirtualFrame frame, Node inliningTarget, Object iterator, Object value);

        /**
         * Gets clone of the keys of the storage with all values either set to given value or, if
         * that is PNone.NO_VALUE, all values set to PNone.NONE. Use this method to clone into a
         * dict or other object where the values may be accessible from Python to avoid a)
         * PNone.NO_VALUE leaking to Python.
         */
        public final HashingStorage getForDictionaries(VirtualFrame frame, Node inliningTarget, Object iterator, Object value) {
            return execute(frame, inliningTarget, iterator, value == PNone.NO_VALUE ? PNone.NONE : value);
        }

        /**
         * Gets a clone of the keys of the storage with all values set to NO_VALUE. This must be
         * used *only* to create new storages for use in sets and frozensets where the values cannot
         * be accessed from user code.
         */
        public final HashingStorage getForSets(VirtualFrame frame, Node inliningTarget, Object iterator) {
            return execute(frame, inliningTarget, iterator, PNone.NO_VALUE);
        }

        /**
         * IMPORTANT: Only for sets and frozensets.
         *
         * @see #getForSets(VirtualFrame, Node, Object)
         */
        public final HashingStorage getForSetsCached(VirtualFrame frame, Object iterator) {
            return execute(frame, null, iterator, PNone.NO_VALUE);
        }

        // This for cloning sets (we come here from doNoValue or doNoValueCached). If we clone from
        // some other PHashingCollection, we would hold on to keys in the sets, and if we were to
        // clone for some other PHashingCollection (not PBaseSet), we might leak NO_VALUE into user
        // code.
        @Specialization(guards = "isNoValue(givenValue)")
        static HashingStorage doSet(Node inliningTarget, PBaseSet other, @SuppressWarnings("unused") Object givenValue,
                        @Cached HashingStorageCopy copyNode) {
            return copyNode.execute(inliningTarget, other.getDictStorage());
        }

        @Specialization(replaces = "doSet")
        static HashingStorage doHashingCollection(VirtualFrame frame, PHashingCollection other, Object givenValue,
                        @Shared @Cached(inline = false) GetClonedHashingCollectionNode hashingCollectionNode) {
            Object value = givenValue == PNone.NO_VALUE ? PNone.NONE : givenValue;
            return hashingCollectionNode.execute(frame, other.getDictStorage(), value);
        }

        @Specialization
        static HashingStorage doPDictView(VirtualFrame frame, PDictView.PDictKeysView other, Object givenValue,
                        @Shared @Cached(inline = false) GetClonedHashingCollectionNode hashingCollectionNode) {
            Object value = givenValue == PNone.NO_VALUE ? PNone.NONE : givenValue;
            return hashingCollectionNode.execute(frame, other.getWrappedStorage(), value);
        }

        @Specialization(guards = "isString(strObj)")
        @InliningCutoff
        static HashingStorage doString(Node inliningTarget, Object strObj, Object value,
                        @Cached CastToTruffleStringNode castToStringNode,
                        @Exclusive @Cached HashingStorageSetItem setStorageItem,
                        @Cached(inline = false) TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached(inline = false) TruffleString.CreateCodePointIteratorNode createCodePointIteratorNode,
                        @Cached(inline = false) TruffleStringIterator.NextNode nextNode,
                        @Cached(inline = false) TruffleString.FromCodePointNode fromCodePointNode) {
            TruffleString str = castToStringNode.execute(inliningTarget, strObj);
            HashingStorage storage = PDict.createNewStorage(codePointLengthNode.execute(str, TS_ENCODING));
            Object val = value == PNone.NO_VALUE ? PNone.NONE : value;
            TruffleStringIterator it = createCodePointIteratorNode.execute(str, TS_ENCODING);
            while (it.hasNext()) {
                // TODO: GR-37219: use SubstringNode with lazy=true?
                int codePoint = nextNode.execute(it, TS_ENCODING);
                TruffleString key = fromCodePointNode.execute(codePoint, TS_ENCODING, true);
                storage = setStorageItem.execute(inliningTarget, storage, key, val);
            }
            return storage;
        }

        @Specialization(guards = {"!isPHashingCollection(other)", "!isDictKeysView(other)", "!isString(other)"})
        @InliningCutoff
        static HashingStorage doIterable(VirtualFrame frame, Node inliningTarget, Object other, Object value,
                        @Cached PyObjectGetIter getIter,
                        @Cached PyIterNextNode nextNode,
                        @Exclusive @Cached HashingStorageSetItem setStorageItem) {
            HashingStorage curStorage = EmptyStorage.INSTANCE;
            Object iterator = getIter.execute(frame, inliningTarget, other);
            Object val = value == PNone.NO_VALUE ? PNone.NONE : value;
            while (true) {
                Object key;
                try {
                    key = nextNode.execute(frame, inliningTarget, iterator);
                } catch (IteratorExhausted e) {
                    return curStorage;
                }
                curStorage = setStorageItem.execute(frame, inliningTarget, curStorage, key, val);
            }
        }

        @NeverDefault
        public static GetClonedHashingStorageNode create() {
            return GetClonedHashingStorageNodeGen.create();
        }

        @GenerateInline(false) // Intentionally lazy
        abstract static class GetClonedHashingCollectionNode extends Node {
            abstract HashingStorage execute(VirtualFrame frame, HashingStorage other, Object value);

            @Specialization
            static HashingStorage doHashingCollection(VirtualFrame frame, HashingStorage other, Object value,
                            @Bind Node inliningTarget,
                            @Cached SetValueHashingStorageNode setValue,
                            @Cached HashingStorageCopy copyNode) {
                assert !PGuards.isNoValue(value);
                HashingStorage storage = copyNode.execute(inliningTarget, other);
                storage = setValue.execute(frame, inliningTarget, storage, value);
                return storage;
            }
        }
    }

    /**
     * Returns {@link HashingStorage} with the same keys as the given iterator. There is no
     * guarantee about the values!
     *
     * @see com.oracle.graal.python.builtins.objects.dict.DictNodes.GetDictStorageNode
     */
    @GenerateInline(inlineByDefault = true)
    public abstract static class GetSetStorageNode extends PNodeWithContext {

        public abstract HashingStorage execute(VirtualFrame frame, Node inliningTarget, Object iterator);

        public final HashingStorage executeCached(VirtualFrame frame, Object iterator) {
            return execute(frame, this, iterator);
        }

        @Specialization
        static HashingStorage doHashingCollection(PHashingCollection other) {
            return other.getDictStorage();
        }

        @Specialization
        static HashingStorage doPDictView(PDictView.PDictKeysView other) {
            return other.getWrappedStorage();
        }

        @Specialization(guards = {"!isPHashingCollection(other)", "!isDictKeysView(other)"})
        @InliningCutoff
        static HashingStorage doGeneric(VirtualFrame frame, Node inliningTarget, Object other,
                        @Cached GetClonedHashingStorageNode getHashingStorageNode) {
            return getHashingStorageNode.getForSets(frame, inliningTarget, other);
        }
    }
}
