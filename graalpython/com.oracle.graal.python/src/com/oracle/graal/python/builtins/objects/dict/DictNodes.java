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
package com.oracle.graal.python.builtins.objects.dict;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.RuntimeError;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.EconomicMapStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageAddAllToOther;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGuards;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorNext;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageLen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageSetItem;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageTransferItem;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.lib.GetNextNode;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.builtins.ListNodes;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

public abstract class DictNodes {
    @ImportStatic(HashingStorageGuards.class)
    public abstract static class UpdateNode extends PNodeWithContext {
        public abstract void execute(Frame frame, PDict self, Object other);

        @SuppressWarnings("unused")
        @Specialization(guards = "isIdentical(self, other)")
        static void updateSelf(VirtualFrame frame, PDict self, Object other) {
        }

        @Specialization(guards = "!mayHaveSideEffectingEq(self)")
        static void updateDictNoSideEffects(PDict self, PDict other,
                        @Cached HashingStorageAddAllToOther addAllToOther) {
            // The contract is such that we iterate over 'other' and add its elements to 'self'. If
            // 'other' gets mutated during the iteration, we should raise. This can happen via a
            // side effect of '__eq__' of some key in self, we should not run any other arbitrary
            // code here (hashes are reused from the 'other' storage).
            addAllToOther.execute(null, other.getDictStorage(), self);
        }

        @Specialization(guards = "mayHaveSideEffectingEq(self)")
        static void updateDictGeneric(VirtualFrame frame, PDict self, PDict other,
                        @Cached HashingStorageTransferItem transferItem,
                        @Cached HashingStorageGetIterator getOtherIter,
                        @Cached HashingStorageIteratorNext iterNext,
                        @Cached HashingStorageLen otherLenNode,
                        @Cached PRaiseNode raiseNode) {
            HashingStorage selfStorage = self.getDictStorage();
            HashingStorage otherStorage = other.getDictStorage();
            int initialSize = otherLenNode.execute(otherStorage);
            HashingStorageIterator itOther = getOtherIter.execute(otherStorage);
            while (iterNext.execute(otherStorage, itOther)) {
                selfStorage = transferItem.execute(frame, null, otherStorage, itOther, selfStorage);
                if (initialSize != otherLenNode.execute(otherStorage)) {
                    throw raiseNode.raise(RuntimeError, ErrorMessages.MUTATED_DURING_UPDATE, "dict");
                }
            }
            self.setDictStorage(selfStorage);
        }

        @Specialization(guards = {"!isDict(other)", "hasKeysAttr(frame, other, lookupKeys)"})
        static void updateMapping(VirtualFrame frame, PDict self, Object other,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Shared("lookupKeys") @Cached PyObjectLookupAttr lookupKeys,
                        @Shared("setStorageItem") @Cached HashingStorageSetItem setHasihngStorageItem,
                        @Shared("addAllToOther") @Cached HashingStorageAddAllToOther addAllToOther,
                        @Shared("getIter") @Cached PyObjectGetIter getIter,
                        @Cached("create(T_KEYS)") LookupAndCallUnaryNode callKeysNode,
                        @Cached PyObjectGetItem getItem,
                        @Cached GetNextNode nextNode,
                        @Cached IsBuiltinObjectProfile errorProfile) {
            HashingStorage storage = HashingStorage.copyToStorage(frame, inliningTarget, other, PKeyword.EMPTY_KEYWORDS, self.getDictStorage(),
                            callKeysNode, getItem, getIter, nextNode, errorProfile, setHasihngStorageItem, addAllToOther);
            self.setDictStorage(storage);
        }

        @Specialization(guards = {"!isDict(other)", "!hasKeysAttr(frame, other, lookupKeys)"})
        static void updateSequence(VirtualFrame frame, PDict self, Object other,
                        @Bind("this") Node inliningTarget,
                        @Shared("setStorageItem") @Cached HashingStorageSetItem setHasihngStorageItem,
                        @SuppressWarnings("unused") @Shared("lookupKeys") @Cached PyObjectLookupAttr lookupKeys,
                        @Shared("addAllToOther") @Cached HashingStorageAddAllToOther addAllToOther,
                        @Shared("getIter") @Cached PyObjectGetIter getIter,
                        @Cached PRaiseNode raise,
                        @Cached GetNextNode nextNode,
                        @Cached ListNodes.FastConstructListNode createListNode,
                        @Cached PyObjectGetItem getItem,
                        @Cached SequenceNodes.LenNode seqLenNode,
                        @Cached InlinedConditionProfile lengthTwoProfile,
                        @Cached IsBuiltinObjectProfile errorProfile,
                        @Cached IsBuiltinObjectProfile isTypeErrorProfile) {
            HashingStorage.StorageSupplier storageSupplier = (int length) -> self.getDictStorage();
            HashingStorage storage = HashingStorage.addSequenceToStorage(frame, inliningTarget, other, PKeyword.EMPTY_KEYWORDS, storageSupplier,
                            getIter, nextNode, createListNode, seqLenNode, lengthTwoProfile, raise, getItem, isTypeErrorProfile,
                            errorProfile, setHasihngStorageItem, addAllToOther);
            self.setDictStorage(storage);
        }

        protected static boolean isIdentical(PDict dict, Object other) {
            return dict == other;
        }

        protected static boolean isDictEconomicMap(Object other) {
            return other instanceof PDict && ((PDict) other).getDictStorage() instanceof EconomicMapStorage;
        }

        protected static boolean hasKeysAttr(VirtualFrame frame, Object other, PyObjectLookupAttr lookupKeys) {
            return lookupKeys.execute(frame, other, SpecialMethodNames.T_KEYS) != PNone.NO_VALUE;
        }

        @NeverDefault
        public static UpdateNode create() {
            return DictNodesFactory.UpdateNodeGen.create();
        }
    }
}
