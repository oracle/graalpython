/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.lib;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.RuntimeError;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T_KEYS;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorNext;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageLen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageTransferItem;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.DictNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetCachedTpSlotsNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.builtins.ListNodes;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.GetClassNode.GetPythonObjectClassNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedLoopConditionProfile;

/** Equivalent to {@code PyDict_Merge(target, mapping, 1)}. */
@GenerateInline
@GenerateCached(false)
@GenerateUncached
public abstract class PyDictMerge extends PNodeWithContext {
    public abstract void execute(VirtualFrame frame, Node inliningTarget, Object target, Object mapping);

    @Specialization(guards = "isBuiltinDict(mapping) || hasBuiltinDictIter(inliningTarget, mapping, getClassNode, getSlots)", limit = "1")
    static void doBuiltinDict(VirtualFrame frame, Node inliningTarget, Object target, PDict mapping,
                    @SuppressWarnings("unused") @Cached GetPythonObjectClassNode getClassNode,
                    @SuppressWarnings("unused") @Cached GetCachedTpSlotsNode getSlots,
                    @Cached BuiltinDictNode merge) {
        merge.execute(frame, inliningTarget, target, mapping);
    }

    @Fallback
    static void doMapping(VirtualFrame frame, Node inliningTarget, Object target, Object mapping,
                    @Cached PyObjectGetAttr getAttr,
                    @Cached MappingNode merge) {
        Object keysMethod = getAttr.execute(frame, inliningTarget, mapping, T_KEYS);
        merge.execute(frame, inliningTarget, target, mapping, keysMethod);
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class BuiltinDictNode extends PNodeWithContext {
        public abstract void execute(VirtualFrame frame, Node inliningTarget, Object target, PDict mapping);

        @Specialization
        static void doMerge(VirtualFrame frame, Node inliningTarget, Object target, PDict mapping,
                        @Cached DictNodes.GetDictStorageNode getStorageNode,
                        @Cached DictNodes.UpdateDictStorageNode updateStorageNode,
                        @Cached HashingStorageTransferItem transferItem,
                        @Cached HashingStorageGetIterator getMappingIter,
                        @Cached HashingStorageIteratorNext iterNext,
                        @Cached HashingStorageLen mappingLenNode,
                        @Cached PRaiseNode raiseNode) {
            if (target == mapping) {
                return;
            }
            HashingStorage targetStorage = getStorageNode.execute(inliningTarget, target);
            HashingStorage mappingStorage = mapping.getDictStorage();
            int initialSize = mappingLenNode.execute(inliningTarget, mappingStorage);
            HashingStorageIterator iterator = getMappingIter.execute(inliningTarget, mappingStorage);
            HashingStorage newStorage = targetStorage;
            while (iterNext.execute(inliningTarget, mappingStorage, iterator)) {
                newStorage = transferItem.execute(frame, inliningTarget, mappingStorage, iterator, newStorage);
                if (initialSize != mappingLenNode.execute(inliningTarget, mappingStorage)) {
                    throw raiseNode.raise(inliningTarget, RuntimeError, ErrorMessages.MUTATED_DURING_UPDATE, "dict");
                }
            }
            updateStorageNode.execute(inliningTarget, target, targetStorage, newStorage);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class MappingNode extends PNodeWithContext {
        public abstract void execute(VirtualFrame frame, Node inliningTarget, Object target, Object mapping, Object keysMethod);

        @Specialization
        static void doMerge(VirtualFrame frame, Node inliningTarget, Object target, Object mapping, Object keysMethod,
                        @Cached CallNode callKeys,
                        @Cached ListNodes.FastConstructListNode materializeKeys,
                        @Cached SequenceStorageNodes.GetItemScalarNode getKey,
                        @Cached PyObjectGetItem getItem,
                        @Cached PyObjectSetItem setItem,
                        @Cached InlinedLoopConditionProfile loopProfile) {
            PList keys = materializeKeys.execute(frame, inliningTarget, callKeys.execute(frame, keysMethod));
            SequenceStorage keysStorage = keys.getSequenceStorage();
            int keysLen = keysStorage.length();
            loopProfile.profileCounted(inliningTarget, keysLen);
            for (int i = 0; loopProfile.inject(inliningTarget, i < keysLen); i++) {
                Object key = getKey.execute(inliningTarget, keysStorage, i);
                Object value = getItem.execute(frame, inliningTarget, mapping, key);
                setItem.execute(frame, inliningTarget, target, key, value);
            }
        }
    }

    /** Equivalent to {@code PyDict_MergeFromSeq2(target, iterable, 1)}. */
    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class FromSeq2Node extends PNodeWithContext {
        public abstract void execute(VirtualFrame frame, Node inliningTarget, Object target, Object iterable);

        @Specialization
        static void doIterable(VirtualFrame frame, Node inliningTarget, Object target, Object iterable,
                        @Cached PyObjectGetIter getIter,
                        @Cached PyIterNextNode next,
                        @Cached SetItemFromSequenceNode setItemFromSequence) {
            Object iterator = getIter.execute(frame, inliningTarget, iterable);
            int index = 0;
            while (true) {
                Object element;
                try {
                    element = next.execute(frame, inliningTarget, iterator);
                } catch (IteratorExhausted e) {
                    break;
                }
                setItemFromSequence.execute(frame, inliningTarget, target, element, index++);
            }
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    abstract static class SetItemFromSequenceNode extends PNodeWithContext {
        abstract void execute(VirtualFrame frame, Node inliningTarget, Object target, Object element, int index);

        @Specialization
        static void doGeneric(VirtualFrame frame, Node inliningTarget, Object target, Object element, int index,
                        @Cached ListNodes.ConstructListNode createList,
                        @Cached SequenceStorageNodes.GetItemScalarNode getItem,
                        @Cached PyObjectSetItem setItem,
                        @Cached IsBuiltinObjectProfile isTypeErrorProfile,
                        @Cached InlinedBranchProfile tupleProfile,
                        @Cached InlinedBranchProfile listProfile,
                        @Cached InlinedBranchProfile genericProfile,
                        @Cached PRaiseNode raiseInvalidLength) {
            SequenceStorage storage;
            if (element instanceof PTuple tuple && PGuards.isBuiltinTuple(tuple)) {
                tupleProfile.enter(inliningTarget);
                storage = tuple.getSequenceStorage();
            } else if (element instanceof PList list && PGuards.isBuiltinList(list)) {
                listProfile.enter(inliningTarget);
                storage = list.getSequenceStorage();
            } else {
                genericProfile.enter(inliningTarget);
                PList genericList;
                try {
                    genericList = createList.execute(frame, element);
                } catch (PException e) {
                    if (isTypeErrorProfile.profileException(inliningTarget, e, TypeError)) {
                        throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.CANNOT_CONVERT_DICT_UPDATE_SEQ, index);
                    }
                    throw e;
                }
                storage = genericList.getSequenceStorage();
            }
            int length = storage.length();
            if (length != 2) {
                throw raiseInvalidLength.raise(inliningTarget, ValueError, ErrorMessages.DICT_UPDATE_SEQ_ELEM_HAS_LENGTH_2_REQUIRED, index, length);
            }
            Object key = getItem.execute(inliningTarget, storage, 0);
            Object value = getItem.execute(inliningTarget, storage, 1);
            setItem.execute(frame, inliningTarget, target, key, value);
        }
    }
}
