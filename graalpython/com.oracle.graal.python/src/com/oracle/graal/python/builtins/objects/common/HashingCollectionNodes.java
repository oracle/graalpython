/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodesFactory.GetClonedHashingStorageNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodesFactory.SetItemNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageCopy;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorKey;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorNext;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageSetItem;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.dict.PDictView;
import com.oracle.graal.python.lib.GetNextNode;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.exception.PException;
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
        public abstract void execute(Frame frame, Node inliningTarget, PHashingCollection c, Object key, Object value);

        public final void executeCached(Frame frame, PHashingCollection c, Object key, Object value) {
            execute(frame, this, c, key, value);
        }

        @Specialization
        static void doSetItem(Frame frame, Node inliningTarget, PHashingCollection c, Object key, Object value,
                        @Cached HashingStorageSetItem setItem) {
            HashingStorage storage = c.getDictStorage();
            storage = setItem.execute(frame, inliningTarget, storage, key, value);
            c.setDictStorage(storage);
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
                        @Cached ObjectHashMap.PutNode putNode,
                        @Cached InlinedLoopConditionProfile loopProfile) {
            // We want to avoid calling __hash__() during map.put
            map.setValueForAllKeys(frame, inliningTarget, value, putNode, loopProfile);
            return map;
        }

        @Specialization(guards = "!isEconomicMapStorage(map)")
        @InliningCutoff
        static HashingStorage doGeneric(VirtualFrame frame, Node inliningTarget, HashingStorage map, Object value,
                        @Cached HashingStorageSetItem setItem,
                        @Cached HashingStorageGetIterator getIterator,
                        @Cached HashingStorageIteratorNext itNext,
                        @Cached HashingStorageIteratorKey itKey) {
            HashingStorageIterator it = getIterator.execute(inliningTarget, map);
            HashingStorage storage = map;
            while (itNext.execute(inliningTarget, map, it)) {
                Object key = itKey.execute(inliningTarget, storage, it);
                storage = setItem.execute(frame, inliningTarget, storage, key, value);
            }
            return storage;
        }

        protected static boolean isEconomicMapStorage(Object o) {
            return o instanceof EconomicMapStorage;
        }
    }

    /**
     * Gets clone of the keys of the storage with all values either set to given value or with no
     * guarantees about the values if {@link PNone#NO_VALUE} is passed as {@code value}.
     */
    @GenerateInline(inlineByDefault = true)
    public abstract static class GetClonedHashingStorageNode extends PNodeWithContext {
        public abstract HashingStorage execute(VirtualFrame frame, Node inliningTarget, Object iterator, Object value);

        public final HashingStorage doNoValue(VirtualFrame frame, Node inliningTarget, Object iterator) {
            return execute(frame, inliningTarget, iterator, PNone.NO_VALUE);
        }

        public final HashingStorage doNoValueCached(VirtualFrame frame, Object iterator) {
            return execute(frame, null, iterator, PNone.NO_VALUE);
        }

        @Specialization(guards = "isNoValue(value)")
        static HashingStorage doHashingCollectionNoValue(Node inliningTarget, PHashingCollection other, @SuppressWarnings("unused") Object value,
                        @Shared("copyNode") @Cached HashingStorageCopy copyNode) {
            return copyNode.execute(inliningTarget, other.getDictStorage());
        }

        @Specialization(guards = "isNoValue(value)")
        static HashingStorage doPDictKeyViewNoValue(Node inliningTarget, PDictView.PDictKeysView other, Object value,
                        @Shared("copyNode") @Cached HashingStorageCopy copyNode) {
            return doHashingCollectionNoValue(inliningTarget, other.getWrappedDict(), value, copyNode);
        }

        @Specialization(guards = "!isNoValue(value)")
        static HashingStorage doHashingCollection(VirtualFrame frame, PHashingCollection other, Object value,
                        @Shared @Cached(inline = false) GetClonedHashingCollectionNode hashingCollectionNode) {
            return hashingCollectionNode.execute(frame, other, value);
        }

        @Specialization(guards = "!isNoValue(value)")
        static HashingStorage doPDictView(VirtualFrame frame, PDictView.PDictKeysView other, Object value,
                        @Shared @Cached(inline = false) GetClonedHashingCollectionNode hashingCollectionNode) {
            return hashingCollectionNode.execute(frame, other.getWrappedDict(), value);
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
                int codePoint = nextNode.execute(it);
                TruffleString key = fromCodePointNode.execute(codePoint, TS_ENCODING, true);
                storage = setStorageItem.execute(inliningTarget, storage, key, val);
            }
            return storage;
        }

        @Specialization(guards = {"!isPHashingCollection(other)", "!isDictKeysView(other)", "!isString(other)"})
        @InliningCutoff
        static HashingStorage doIterable(VirtualFrame frame, Node inliningTarget, Object other, Object value,
                        @Cached PyObjectGetIter getIter,
                        @Cached(inline = false) GetNextNode nextNode,
                        @Cached IsBuiltinObjectProfile errorProfile,
                        @Exclusive @Cached HashingStorageSetItem setStorageItem) {
            HashingStorage curStorage = EmptyStorage.INSTANCE;
            Object iterator = getIter.execute(frame, inliningTarget, other);
            Object val = value == PNone.NO_VALUE ? PNone.NONE : value;
            while (true) {
                Object key;
                try {
                    key = nextNode.execute(frame, iterator);
                } catch (PException e) {
                    e.expectStopIteration(inliningTarget, errorProfile);
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
            abstract HashingStorage execute(VirtualFrame frame, PHashingCollection other, Object value);

            @Specialization
            static HashingStorage doHashingCollection(VirtualFrame frame, PHashingCollection other, Object value,
                            @Bind("this") Node inliningTarget,
                            @Cached SetValueHashingStorageNode setValue,
                            @Cached HashingStorageCopy copyNode) {
                assert !PGuards.isNoValue(value);
                HashingStorage storage = copyNode.execute(inliningTarget, other.getDictStorage());
                storage = setValue.execute(frame, inliningTarget, storage, value);
                return storage;
            }
        }
    }

    /**
     * Returns {@link HashingStorage} with the same keys as the given iterator. There is no
     * guarantee about the values!
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
            return other.getWrappedDict().getDictStorage();
        }

        @Specialization(guards = {"!isPHashingCollection(other)", "!isDictKeysView(other)"})
        @InliningCutoff
        static HashingStorage doGeneric(VirtualFrame frame, Node inliningTarget, Object other,
                        @Cached GetClonedHashingStorageNode getHashingStorageNode) {
            return getHashingStorageNode.doNoValue(frame, inliningTarget, other);
        }
    }
}
