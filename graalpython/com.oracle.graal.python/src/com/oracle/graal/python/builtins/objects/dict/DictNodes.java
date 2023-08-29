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
import static com.oracle.graal.python.builtins.objects.common.HashingStorage.addKeyValuesToStorage;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T_KEYS;

import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage.ObjectToArrayPairNode;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageAddAllToOther;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGuards;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorNext;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageLen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageSetItem;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageTransferItem;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

public abstract class DictNodes {
    @ImportStatic(HashingStorageGuards.class)
    @SuppressWarnings("truffle-inlining")       // footprint reduction 188 -> 170
    public abstract static class UpdateNode extends PNodeWithContext {
        public abstract void execute(Frame frame, PDict self, Object other);

        @SuppressWarnings("unused")
        @Specialization(guards = "isIdentical(self, other)")
        static void updateSelf(VirtualFrame frame, PDict self, Object other) {
        }

        @Specialization(guards = "!mayHaveSideEffectingEq(self)")
        static void updateDictNoSideEffects(PDict self, PDict other,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached HashingStorageAddAllToOther addAllToOther) {
            // The contract is such that we iterate over 'other' and add its elements to 'self'. If
            // 'other' gets mutated during the iteration, we should raise. This can happen via a
            // side effect of '__eq__' of some key in self, we should not run any other arbitrary
            // code here (hashes are reused from the 'other' storage).
            addAllToOther.execute(null, inliningTarget, other.getDictStorage(), self);
        }

        @Specialization(guards = "mayHaveSideEffectingEq(self)")
        static void updateDictGeneric(VirtualFrame frame, PDict self, PDict other,
                        @Bind("this") Node inliningTarget,
                        @Cached HashingStorageTransferItem transferItem,
                        @Cached HashingStorageGetIterator getOtherIter,
                        @Cached HashingStorageIteratorNext iterNext,
                        @Cached HashingStorageLen otherLenNode,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            HashingStorage selfStorage = self.getDictStorage();
            HashingStorage otherStorage = other.getDictStorage();
            int initialSize = otherLenNode.execute(inliningTarget, otherStorage);
            HashingStorageIterator itOther = getOtherIter.execute(inliningTarget, otherStorage);
            while (iterNext.execute(inliningTarget, otherStorage, itOther)) {
                selfStorage = transferItem.execute(frame, inliningTarget, otherStorage, itOther, selfStorage);
                if (initialSize != otherLenNode.execute(inliningTarget, otherStorage)) {
                    throw raiseNode.get(inliningTarget).raise(RuntimeError, ErrorMessages.MUTATED_DURING_UPDATE, "dict");
                }
            }
            self.setDictStorage(selfStorage);
        }

        @Specialization(guards = "!isDict(other)")
        static void updateArg(VirtualFrame frame, PDict self, Object other,
                        @Bind("this") Node inliningTarget,
                        @Cached HashingStorageSetItem setHasihngStorageItem,
                        @Cached PyObjectLookupAttr lookupKeys,
                        @Cached ObjectToArrayPairNode toArrayPair) {
            Object keyAttr = lookupKeys.execute(frame, inliningTarget, other, T_KEYS);
            HashingStorage storage = addKeyValuesToStorage(frame, self, other, keyAttr,
                            inliningTarget, toArrayPair, setHasihngStorageItem);
            self.setDictStorage(storage);
        }

        protected static boolean isIdentical(PDict dict, Object other) {
            return dict == other;
        }

        @NeverDefault
        public static UpdateNode create() {
            return DictNodesFactory.UpdateNodeGen.create();
        }
    }
}
