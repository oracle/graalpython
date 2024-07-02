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
package com.oracle.graal.python.builtins.objects.dict;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.RuntimeError;
import static com.oracle.graal.python.nodes.ErrorMessages.DESCRIPTOR_REQUIRES_S_OBJ_RECEIVED_P;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T_KEYS;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.common.ForeignHashingStorage;
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
import com.oracle.graal.python.builtins.objects.common.PHashingCollection;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles;
import com.oracle.graal.python.nodes.object.IsForeignObjectNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

public abstract class DictNodes {

    @GenerateUncached
    @GenerateInline(inlineByDefault = true)
    public abstract static class GetDictStorageNode extends PNodeWithContext {

        public abstract HashingStorage execute(Node inliningTarget, Object object);

        @Specialization
        static HashingStorage doPHashingCollection(PHashingCollection dict) {
            return dict.getDictStorage();
        }

        @Specialization(guards = {"isForeignObjectNode.execute(inliningTarget, dict)", "interop.hasHashEntries(dict)"}, limit = "1")
        static HashingStorage doForeign(Node inliningTarget, Object dict,
                        @Cached IsForeignObjectNode isForeignObjectNode,
                        @CachedLibrary(limit = "getCallSiteInlineCacheMaxDepth()") InteropLibrary interop) {
            return new ForeignHashingStorage(dict);
        }

        @Fallback
        static HashingStorage doFallback(Node inliningTarget, Object object,
                        @Cached PRaiseNode.Lazy raiseNode) {
            throw raiseNode.get(inliningTarget).raise(TypeError, DESCRIPTOR_REQUIRES_S_OBJ_RECEIVED_P, "dict", object);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class UpdateDictStorageNode extends PNodeWithContext {

        public abstract void execute(Node inliningTarget, Object dict, HashingStorage oldStorage, HashingStorage newStorage);

        @Specialization
        static void doPHashingCollection(Node inliningTarget, PHashingCollection dict, HashingStorage oldStorage, HashingStorage newStorage,
                        @Exclusive @Cached InlinedConditionProfile generalizedProfile) {
            if (generalizedProfile.profile(inliningTarget, oldStorage != newStorage)) {
                dict.setDictStorage(newStorage);
            }
        }

        @Fallback
        static void doForeign(Object dict, HashingStorage oldStorage, HashingStorage newStorage) {
            if (oldStorage != newStorage) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw CompilerDirectives.shouldNotReachHere("foreign dict storage should never need to be replaced: " + dict);
            }
        }

    }

    @SuppressWarnings("truffle-inlining")       // footprint reduction 52 -> 36
    public abstract static class UpdateNode extends PNodeWithContext {
        public abstract void execute(Frame frame, Object self, Object other);

        @Specialization
        static void updateDictGeneric(VirtualFrame frame, Object self, Object other,
                        @Bind("this") Node inliningTarget,
                        @Cached BuiltinClassProfiles.IsBuiltinObjectProfile isDictNode,
                        @Cached DictNodes.GetDictStorageNode getStorageNode,
                        @Cached UpdateInnerNode updateInnerNode) {
            if (self != other) {
                var selfStorage = getStorageNode.execute(inliningTarget, self);
                boolean isDict = isDictNode.profileObject(inliningTarget, other, PythonBuiltinClassType.PDict);
                HashingStorage otherStorage = isDict ? getStorageNode.execute(inliningTarget, other) : null;
                updateInnerNode.execute(frame, inliningTarget, self, selfStorage, other, otherStorage);
            }
        }

        @NeverDefault
        public static UpdateNode create() {
            return DictNodesFactory.UpdateNodeGen.create();
        }
    }

    @ImportStatic(HashingStorageGuards.class)
    @GenerateInline
    @GenerateCached(false)
    public abstract static class UpdateInnerNode extends PNodeWithContext {
        public abstract void execute(Frame frame, Node inliningTarget, Object self, HashingStorage selfStorage, Object other, Object otherStorage);

        @Specialization(guards = "!mayHaveSideEffectingEq(selfStorage)")
        public static void updateDictNoSideEffects(Node inliningTarget, Object self, HashingStorage selfStorage, Object other, HashingStorage otherStorage,
                        @Exclusive @Cached HashingStorageAddAllToOther addAllToOther,
                        @Exclusive @Cached DictNodes.UpdateDictStorageNode updateStorageNode) {
            // The contract is such that we iterate over 'other' and add its elements to 'self'. If
            // 'other' gets mutated during the iteration, we should raise. This can happen via a
            // side effect of '__eq__' of some key in self, we should not run any other arbitrary
            // code here (hashes are reused from the 'other' storage).
            var newStorage = addAllToOther.execute(null, inliningTarget, otherStorage, selfStorage);
            updateStorageNode.execute(inliningTarget, self, selfStorage, newStorage);
        }

        @Specialization(guards = "mayHaveSideEffectingEq(selfStorage)")
        public static void updateDictGeneric(VirtualFrame frame, Node inliningTarget, Object self, HashingStorage selfStorage, Object other, HashingStorage otherStorage,
                        @Exclusive @Cached DictNodes.UpdateDictStorageNode updateStorageNode,
                        @Cached HashingStorageTransferItem transferItem,
                        @Cached HashingStorageGetIterator getOtherIter,
                        @Cached HashingStorageIteratorNext iterNext,
                        @Cached HashingStorageLen otherLenNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            int initialSize = otherLenNode.execute(inliningTarget, otherStorage);
            HashingStorageIterator itOther = getOtherIter.execute(inliningTarget, otherStorage);
            var newStorage = selfStorage;
            while (iterNext.execute(inliningTarget, otherStorage, itOther)) {
                newStorage = transferItem.execute(frame, inliningTarget, otherStorage, itOther, newStorage);
                if (initialSize != otherLenNode.execute(inliningTarget, otherStorage)) {
                    throw raiseNode.get(inliningTarget).raise(RuntimeError, ErrorMessages.MUTATED_DURING_UPDATE, "dict");
                }
            }
            updateStorageNode.execute(inliningTarget, self, selfStorage, newStorage);
        }

        @Specialization(guards = "otherStorage == null")
        public static void updateArg(VirtualFrame frame, Node inliningTarget, Object self, HashingStorage selfStorage, Object other, Object otherStorage,
                        @Exclusive @Cached DictNodes.UpdateDictStorageNode updateStorageNode,
                        @Cached HashingStorageSetItem setItem,
                        @Cached PyObjectLookupAttr lookupKeys,
                        @Cached(inline = false) ObjectToArrayPairNode toArrayPair) {
            Object keyAttr = lookupKeys.execute(frame, inliningTarget, other, T_KEYS);
            HashingStorage newStorage = HashingStorage.addKeyValuesToStorage(frame, selfStorage, other, keyAttr,
                            inliningTarget, toArrayPair, setItem);
            updateStorageNode.execute(inliningTarget, self, selfStorage, newStorage);
        }
    }
}
