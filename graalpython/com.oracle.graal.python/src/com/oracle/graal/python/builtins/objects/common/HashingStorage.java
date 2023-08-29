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

import static com.oracle.graal.python.builtins.objects.function.PKeyword.EMPTY_KEYWORDS;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T_KEYS;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.EMPTY_OBJECT_ARRAY;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingStorageFactory.InitNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageAddAllToOther;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageCopy;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageSetItem;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes.LenNode;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.lib.GetNextNode;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.IndirectCallNode;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.LookupCallableSlotInMRONode;
import com.oracle.graal.python.nodes.builtins.ListNodes.FastConstructListNode;
import com.oracle.graal.python.nodes.call.special.CallVarargsMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.util.ArrayBuilder;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

public abstract class HashingStorage {
    public abstract static class InitNode extends PNodeWithContext implements IndirectCallNode {

        private final Assumption dontNeedExceptionState = Truffle.getRuntime().createAssumption();
        private final Assumption dontNeedCallerFrame = Truffle.getRuntime().createAssumption();

        @Override
        public Assumption needNotPassFrameAssumption() {
            return dontNeedCallerFrame;
        }

        @Override
        public Assumption needNotPassExceptionAssumption() {
            return dontNeedExceptionState;
        }

        public abstract HashingStorage execute(VirtualFrame frame, Object mapping, PKeyword[] kwargs);

        protected boolean isEmpty(PKeyword[] kwargs) {
            return kwargs.length == 0;
        }

        @Specialization(guards = {"isNoValue(iterable)", "isEmpty(kwargs)"})
        static HashingStorage doEmpty(@SuppressWarnings("unused") PNone iterable, @SuppressWarnings("unused") PKeyword[] kwargs) {
            return EmptyStorage.INSTANCE;
        }

        @Specialization(guards = {"isNoValue(iterable)", "!isEmpty(kwargs)"})
        static HashingStorage doKeywords(@SuppressWarnings("unused") PNone iterable, PKeyword[] kwargs) {
            return new KeywordsStorage(kwargs);
        }

        protected static boolean isPDict(Object o) {
            return o instanceof PHashingCollection;
        }

        @Specialization(guards = {"isEmpty(kwargs)", "!hasIterAttrButNotBuiltin(inliningTarget, dictLike, getClassNode, lookupIter)"}, limit = "1")
        static HashingStorage doPDict(PHashingCollection dictLike, @SuppressWarnings("unused") PKeyword[] kwargs,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Exclusive @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Exclusive @Cached(parameters = "Iter") LookupCallableSlotInMRONode lookupIter,
                        @Exclusive @Cached HashingStorageCopy copyNode) {
            return copyNode.execute(inliningTarget, dictLike.getDictStorage());
        }

        @Specialization(guards = {"!isEmpty(kwargs)", "!hasIterAttrButNotBuiltin(inliningTarget, iterable, getClassNode, lookupIter)"}, limit = "1")
        @SuppressWarnings("truffle-static-method")
        HashingStorage doPDictKwargs(VirtualFrame frame, PHashingCollection iterable, PKeyword[] kwargs,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Exclusive @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Exclusive @Cached(parameters = "Iter") LookupCallableSlotInMRONode lookupIter,
                        @Exclusive @Cached HashingStorageCopy copyNode,
                        @Exclusive @Cached HashingStorageAddAllToOther addAllToOther) {
            PythonContext contextRef = PythonContext.get(this);
            PythonLanguage language = PythonLanguage.get(this);
            Object state = IndirectCallContext.enter(frame, language, contextRef, this);
            try {
                HashingStorage iterableDictStorage = iterable.getDictStorage();
                HashingStorage dictStorage = copyNode.execute(inliningTarget, iterableDictStorage);
                return addAllToOther.execute(frame, inliningTarget, new KeywordsStorage(kwargs), dictStorage);
            } finally {
                IndirectCallContext.exit(frame, language, contextRef, state);
            }
        }

        @Specialization(guards = "hasIterAttrButNotBuiltin(inliningTarget, col, getClassNode, lookupIter)", limit = "1")
        static HashingStorage doNoBuiltinKeysAttr(VirtualFrame frame, PHashingCollection col, @SuppressWarnings("unused") PKeyword[] kwargs,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Exclusive @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Exclusive @Cached(parameters = "Iter") LookupCallableSlotInMRONode lookupIter,
                        @Exclusive @Cached PyObjectGetIter getIter,
                        @Exclusive @Cached HashingStorageSetItem setHashingStorageItem,
                        @Exclusive @Cached HashingStorageAddAllToOther addAllToOther,
                        @Exclusive @Cached("create(T_KEYS)") LookupAndCallUnaryNode callKeysNode,
                        @Exclusive @Cached PyObjectGetItem getItemNode,
                        @Exclusive @Cached GetNextNode nextNode,
                        @Exclusive @Cached IsBuiltinObjectProfile errorProfile) {
            HashingStorage curStorage = PDict.createNewStorage(0);
            Object keysIterable = callKeysNode.executeObject(frame, col);
            return copyToStorage(frame, col, kwargs, curStorage, inliningTarget, keysIterable, getItemNode, getIter, nextNode, errorProfile, setHashingStorageItem, addAllToOther);
        }

        protected static boolean hasIterAttrButNotBuiltin(Node inliningTarget, PHashingCollection col, GetClassNode getClassNode, LookupCallableSlotInMRONode lookupIter) {
            Object attr = lookupIter.execute(getClassNode.execute(inliningTarget, col));
            return attr != PNone.NO_VALUE && !(attr instanceof PBuiltinMethod || attr instanceof PBuiltinFunction);
        }

        @Specialization(guards = {"!isNoValue(arg)", "!isPDict(arg)"})
        static HashingStorage updateArg(VirtualFrame frame, Object arg, PKeyword[] kwargs,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectLookupAttr lookupKeysAttributeNode,
                        @Cached CallVarargsMethodNode callKeysMethod,
                        @Exclusive @Cached HashingStorageSetItem setHasihngStorageItem,
                        @Exclusive @Cached HashingStorageAddAllToOther addAllToOther,
                        @Exclusive @Cached PyObjectGetIter getIter,
                        @Cached PRaiseNode.Lazy raise,
                        @Exclusive @Cached GetNextNode nextNode,
                        @Cached FastConstructListNode createListNode,
                        @Exclusive @Cached PyObjectGetItem getItemNode,
                        @Cached SequenceNodes.LenNode seqLenNode,
                        @Cached InlinedConditionProfile lengthTwoProfile,
                        @Cached InlinedConditionProfile hasKeyProfile,
                        @Exclusive @Cached IsBuiltinObjectProfile errorProfile,
                        @Exclusive @Cached IsBuiltinObjectProfile isTypeErrorProfile) {
            Object keyAttr = lookupKeysAttributeNode.execute(frame, inliningTarget, arg, T_KEYS);
            if (hasKeyProfile.profile(inliningTarget, keyAttr != PNone.NO_VALUE)) {
                HashingStorage curStorage = PDict.createNewStorage(0);
                // We don't need to pass self as the attribute object has it already.
                Object keysIterable = callKeysMethod.execute(frame, keyAttr, EMPTY_OBJECT_ARRAY, EMPTY_KEYWORDS);
                return copyToStorage(frame, arg, kwargs, curStorage,
                                inliningTarget, keysIterable, getItemNode, getIter, nextNode,
                                errorProfile, setHasihngStorageItem, addAllToOther);
            } else {
                return addSequenceToStorage(frame, arg, kwargs,
                                inliningTarget, PDict::createNewStorage, getIter, nextNode, createListNode,
                                seqLenNode, lengthTwoProfile, raise, getItemNode, isTypeErrorProfile,
                                errorProfile, setHasihngStorageItem, addAllToOther);
            }
        }

        @NeverDefault
        public static InitNode create() {
            return InitNodeGen.create();
        }
    }

    public final HashingStorage union(Node inliningTarget, HashingStorage other, HashingStorageCopy copyNode, HashingStorageAddAllToOther addAllToOther) {
        HashingStorage newStore = copyNode.execute(inliningTarget, this);
        return addAllToOther.execute(null, inliningTarget, other, newStore);
    }

    /**
     * Callers must make sure that {@code copyNode} and {@code addAllToOther} are cached nodes (not
     * inlined).
     */
    public final HashingStorage unionCached(HashingStorage other, HashingStorageCopy copyNode, HashingStorageAddAllToOther addAllToOther) {
        HashingStorage newStore = copyNode.executeCached(this);
        return addAllToOther.executeCached(null, other, newStore);
    }

    /**
     * Adds all items from the given mapping object to storage. It is the caller responsibility to
     * ensure, that mapping has the 'keys' attribute.
     */
    public static HashingStorage copyToStorage(VirtualFrame frame, Object mapping, PKeyword[] kwargs, HashingStorage storage,
                    Node inliningTarget,
                    Object keysIterable,
                    PyObjectGetItem callGetItemNode,
                    PyObjectGetIter getIter,
                    GetNextNode nextNode,
                    IsBuiltinObjectProfile errorProfile,
                    HashingStorageSetItem setHashingStorageItem,
                    HashingStorageAddAllToOther addAllToOtherNode) {
        Object keysIt = getIter.execute(frame, inliningTarget, keysIterable);
        HashingStorage curStorage = storage;
        while (true) {
            try {
                Object keyObj = nextNode.execute(frame, keysIt);
                Object valueObj = callGetItemNode.execute(frame, inliningTarget, mapping, keyObj);

                curStorage = setHashingStorageItem.execute(frame, inliningTarget, curStorage, keyObj, valueObj);
            } catch (PException e) {
                e.expectStopIteration(inliningTarget, errorProfile);
                break;
            }
        }
        if (kwargs.length > 0) {
            curStorage = addAllToOtherNode.execute(frame, inliningTarget, new KeywordsStorage(kwargs), curStorage);
        }
        return curStorage;
    }

    @FunctionalInterface
    public interface StorageSupplier {
        HashingStorage get(int length);
    }

    public static HashingStorage addSequenceToStorage(VirtualFrame frame, Object iterable, PKeyword[] kwargs, Node inliningTarget,
                    StorageSupplier storageSupplier,
                    PyObjectGetIter getIter,
                    GetNextNode nextNode,
                    FastConstructListNode createListNode,
                    LenNode seqLenNode,
                    InlinedConditionProfile lengthTwoProfile,
                    PRaiseNode.Lazy raise,
                    PyObjectGetItem getItemNode,
                    IsBuiltinObjectProfile isTypeErrorProfile,
                    IsBuiltinObjectProfile errorProfile,
                    HashingStorageSetItem setHashingStorageItem,
                    HashingStorageAddAllToOther addAllToOther) throws PException {
        Object it = getIter.execute(frame, inliningTarget, iterable);
        ArrayBuilder<PSequence> elements = new ArrayBuilder<>();
        try {
            while (true) {
                Object next = nextNode.execute(frame, it);
                PSequence element = createListNode.execute(frame, inliningTarget, next);
                assert element != null;
                // This constructs a new list using the builtin type. So, the object cannot
                // be subclassed and we can directly call 'len()'.
                int len = seqLenNode.execute(inliningTarget, element);

                if (lengthTwoProfile.profile(inliningTarget, len != 2)) {
                    throw raise.get(inliningTarget).raise(ValueError, ErrorMessages.DICT_UPDATE_SEQ_ELEM_HAS_LENGTH_2_REQUIRED, elements.size(), len);
                }

                elements.add(element);
            }
        } catch (PException e) {
            if (isTypeErrorProfile.profileException(inliningTarget, e, TypeError)) {
                throw raise.get(inliningTarget).raise(TypeError, ErrorMessages.CANNOT_CONVERT_DICT_UPDATE_SEQ, elements.size());
            } else {
                e.expectStopIteration(inliningTarget, errorProfile);
            }
        }
        HashingStorage storage = storageSupplier.get(elements.size() + kwargs.length);
        for (int j = 0; j < elements.size(); j++) {
            PSequence element = elements.get(j);
            Object key = getItemNode.execute(frame, inliningTarget, element, 0);
            Object value = getItemNode.execute(frame, inliningTarget, element, 1);
            storage = setHashingStorageItem.execute(frame, inliningTarget, storage, key, value);
        }
        if (kwargs.length > 0) {
            storage = addAllToOther.execute(frame, inliningTarget, new KeywordsStorage(kwargs), storage);
        }
        return storage;
    }
}
