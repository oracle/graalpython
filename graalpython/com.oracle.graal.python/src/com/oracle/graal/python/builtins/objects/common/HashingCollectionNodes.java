/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodesFactory.SetItemNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageCopy;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorKey;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorNext;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageSetItem;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.dict.PDictView;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.lib.GetNextNode;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.LoopConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringIterator;

public abstract class HashingCollectionNodes {

    @GenerateUncached
    @ImportStatic(PGuards.class)
    public abstract static class SetItemNode extends PNodeWithContext {
        public abstract void execute(Frame frame, PHashingCollection c, Object key, Object value);

        @Specialization
        static void doSetItem(Frame frame, PHashingCollection c, Object key, Object value,
                        @Cached HashingStorageSetItem setItem) {
            HashingStorage storage = c.getDictStorage();
            storage = setItem.execute(frame, storage, key, value);
            c.setDictStorage(storage);
        }

        @NeverDefault
        public static SetItemNode create() {
            return SetItemNodeGen.create();
        }

        public static SetItemNode getUncached() {
            return SetItemNodeGen.getUncached();
        }
    }

    @ImportStatic({PGuards.class})
    abstract static class SetValueHashingStorageNode extends PNodeWithContext {
        abstract HashingStorage execute(VirtualFrame frame, HashingStorage iterator, Object value);

        @Specialization
        HashingStorage doEconomicStorage(VirtualFrame frame, EconomicMapStorage map, Object value,
                        @Cached ObjectHashMap.PutNode putNode,
                        @Cached ConditionProfile hasFrameProfile,
                        @Cached LoopConditionProfile loopProfile) {
            // We want to avoid calling __hash__() during map.put
            map.setValueForAllKeys(frame, value, putNode, hasFrameProfile, loopProfile);
            return map;
        }

        @Specialization(guards = "!isEconomicMapStorage(map)")
        static HashingStorage doGeneric(VirtualFrame frame, HashingStorage map, Object value,
                        @Cached HashingStorageSetItem setItem,
                        @Cached HashingStorageGetIterator getIterator,
                        @Cached HashingStorageIteratorNext itNext,
                        @Cached HashingStorageIteratorKey itKey) {
            HashingStorageIterator it = getIterator.execute(map);
            HashingStorage storage = map;
            while (itNext.execute(map, it)) {
                Object key = itKey.execute(storage, it);
                storage = setItem.execute(frame, storage, key, value);
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
    @ImportStatic({PGuards.class, PythonOptions.class})
    public abstract static class GetClonedHashingStorageNode extends PNodeWithContext {
        public abstract HashingStorage execute(VirtualFrame frame, Object iterator, Object value);

        public final HashingStorage doNoValue(VirtualFrame frame, Object iterator) {
            return execute(frame, iterator, PNone.NO_VALUE);
        }

        @Specialization(guards = "isNoValue(value)")
        static HashingStorage doHashingCollectionNoValue(PHashingCollection other, @SuppressWarnings("unused") Object value,
                        @Shared("copyNode") @Cached HashingStorageCopy copyNode) {
            return copyNode.execute(other.getDictStorage());
        }

        @Specialization(guards = "isNoValue(value)")
        static HashingStorage doPDictKeyViewNoValue(PDictView.PDictKeysView other, Object value,
                        @Shared("copyNode") @Cached HashingStorageCopy copyNode) {
            return doHashingCollectionNoValue(other.getWrappedDict(), value, copyNode);
        }

        @Specialization(guards = "!isNoValue(value)")
        static HashingStorage doHashingCollection(VirtualFrame frame, PHashingCollection other, Object value,
                        @Cached SetValueHashingStorageNode setValue,
                        @Shared("copyNode") @Cached HashingStorageCopy copyNode) {
            HashingStorage storage = copyNode.execute(other.getDictStorage());
            storage = setValue.execute(frame, storage, value);
            return storage;
        }

        @Specialization(guards = "!isNoValue(value)")
        static HashingStorage doPDictView(VirtualFrame frame, PDictView.PDictKeysView other, Object value,
                        @Cached SetValueHashingStorageNode setValue,
                        @Shared("copyNode") @Cached HashingStorageCopy copyNode) {
            return doHashingCollection(frame, other.getWrappedDict(), value, setValue, copyNode);
        }

        @Specialization
        static HashingStorage doString(TruffleString str, Object value,
                        @Shared("setStorageItem") @Cached HashingStorageSetItem setStorageItem,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.CreateCodePointIteratorNode createCodePointIteratorNode,
                        @Cached TruffleStringIterator.NextNode nextNode,
                        @Cached TruffleString.FromCodePointNode fromCodePointNode) {
            HashingStorage storage = PDict.createNewStorage(codePointLengthNode.execute(str, TS_ENCODING));
            Object val = value == PNone.NO_VALUE ? PNone.NONE : value;
            TruffleStringIterator it = createCodePointIteratorNode.execute(str, TS_ENCODING);
            while (it.hasNext()) {
                // TODO: GR-37219: use SubstringNode with lazy=true?
                int codePoint = nextNode.execute(it);
                TruffleString key = fromCodePointNode.execute(codePoint, TS_ENCODING, true);
                storage = setStorageItem.execute(storage, key, val);
            }
            return storage;
        }

        @Specialization
        static HashingStorage doString(PString pstr, Object value,
                        @Shared("setStorageItem") @Cached HashingStorageSetItem setStorageItem,
                        @Cached CastToTruffleStringNode castToStringNode,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.CreateCodePointIteratorNode createCodePointIteratorNode,
                        @Cached TruffleStringIterator.NextNode nextNode,
                        @Cached TruffleString.FromCodePointNode fromCodePointNode) {
            return doString(castToStringNode.execute(pstr), value, setStorageItem, codePointLengthNode, createCodePointIteratorNode, nextNode, fromCodePointNode);
        }

        @Specialization(guards = {"!isPHashingCollection(other)", "!isDictKeysView(other)", "!isString(other)"})
        static HashingStorage doIterable(VirtualFrame frame, Object other, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectGetIter getIter,
                        @Cached GetNextNode nextNode,
                        @Cached IsBuiltinObjectProfile errorProfile,
                        @Shared("setStorageItem") @Cached HashingStorageSetItem setStorageItem) {
            HashingStorage curStorage = EmptyStorage.INSTANCE;
            Object iterator = getIter.execute(frame, other);
            Object val = value == PNone.NO_VALUE ? PNone.NONE : value;
            while (true) {
                Object key;
                try {
                    key = nextNode.execute(frame, iterator);
                } catch (PException e) {
                    e.expectStopIteration(inliningTarget, errorProfile);
                    return curStorage;
                }
                curStorage = setStorageItem.execute(frame, curStorage, key, val);
            }
        }

        @Fallback
        HashingStorage fail(Object other, @SuppressWarnings("unused") Object value,
                        @Cached PRaiseNode raise) {
            throw raise.raise(TypeError, ErrorMessages.OBJ_NOT_ITERABLE, other);
        }
    }

    /**
     * Returns {@link HashingStorage} with the same keys as the given iterator. There is no
     * guarantee about the values!
     */
    @ImportStatic({SpecialMethodNames.class, PGuards.class})
    public abstract static class GetHashingStorageNode extends PNodeWithContext {

        public abstract HashingStorage execute(VirtualFrame frame, Object iterator);

        @Specialization
        static HashingStorage doHashingCollection(PHashingCollection other) {
            return other.getDictStorage();
        }

        @Specialization
        static HashingStorage doPDictView(PDictView.PDictKeysView other) {
            return other.getWrappedDict().getDictStorage();
        }

        @Specialization(guards = {"!isPHashingCollection(other)", "!isDictKeysView(other)"})
        static HashingStorage doGeneric(VirtualFrame frame, Object other,
                        @Cached GetClonedHashingStorageNode getHashingStorageNode) {
            return getHashingStorageNode.doNoValue(frame, other);
        }
    }
}
