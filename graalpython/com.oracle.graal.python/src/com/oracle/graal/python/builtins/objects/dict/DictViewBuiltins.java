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
package com.oracle.graal.python.builtins.objects.dict;

import static com.oracle.graal.python.nodes.SpecialMethodNames.J_ISDISJOINT;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REVERSED__;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageAddAllToOther;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageCopy;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageDiff;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetItem;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetReverseIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIntersect;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageLen;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageXor;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.DictViewBuiltinsFactory.ContainedInNodeGen;
import com.oracle.graal.python.builtins.objects.dict.PDictView.PDictItemsView;
import com.oracle.graal.python.builtins.objects.dict.PDictView.PDictKeysView;
import com.oracle.graal.python.builtins.objects.set.PBaseSet;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.builtins.objects.set.SetNodes;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryOp.BinaryOpBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotLen.LenBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotRichCompare;
import com.oracle.graal.python.lib.RichCmpOp;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSqContains.SqContainsBuiltinNode;
import com.oracle.graal.python.lib.IteratorExhausted;
import com.oracle.graal.python.lib.PyIterNextNode;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.lib.PyObjectRichCompareBool;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.lib.PySequenceContainsNode;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.profiles.InlinedLoopConditionProfile;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PDictKeysView, PythonBuiltinClassType.PDictItemsView})
public final class DictViewBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = DictViewBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return DictViewBuiltinsFactory.getFactories();
    }

    @Builtin(name = "mapping", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class MappingNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object mapping(PDictView self,
                        @Bind PythonLanguage language) {
            return PFactory.createMappingproxy(language, self.getWrappedDict());
        }
    }

    @Slot(SlotKind.sq_length)
    @GenerateUncached
    @GenerateNodeFactory
    public abstract static class LenNode extends LenBuiltinNode {
        @Specialization
        static int len(PDictView self,
                        @Bind Node inliningTarget,
                        @Cached HashingStorageLen len) {
            return len.execute(inliningTarget, self.getWrappedStorage());
        }
    }

    @Slot(value = SlotKind.tp_iter, isComplex = true)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object getKeysViewIter(@SuppressWarnings("unused") PDictKeysView self,
                        @Bind Node inliningTarget,
                        @Shared("len") @Cached HashingStorageLen lenNode,
                        @Shared("getit") @Cached HashingStorageGetIterator getIterator,
                        @Bind PythonLanguage language) {
            HashingStorage storage = self.getWrappedStorage();
            return PFactory.createDictKeyIterator(language, getIterator.execute(inliningTarget, storage), storage, lenNode.execute(inliningTarget, storage));
        }

        @Specialization
        static Object getItemsViewIter(PDictItemsView self,
                        @Bind Node inliningTarget,
                        @Shared("len") @Cached HashingStorageLen lenNode,
                        @Shared("getit") @Cached HashingStorageGetIterator getIterator,
                        @Bind PythonLanguage language) {
            HashingStorage storage = self.getWrappedStorage();
            return PFactory.createDictItemIterator(language, getIterator.execute(inliningTarget, storage), storage, lenNode.execute(inliningTarget, storage));
        }
    }

    @Builtin(name = J___REVERSED__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReversedNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object getReversedKeysViewIter(PDictKeysView self,
                        @Bind Node inliningTarget,
                        @Shared @Cached HashingStorageLen lenNode,
                        @Shared @Cached HashingStorageGetReverseIterator getReverseIterator,
                        @Bind PythonLanguage language) {
            HashingStorage storage = self.getWrappedStorage();
            return PFactory.createDictKeyIterator(language, getReverseIterator.execute(inliningTarget, storage), storage, lenNode.execute(inliningTarget, storage));
        }

        @Specialization
        static Object getReversedItemsViewIter(PDictItemsView self,
                        @Bind Node inliningTarget,
                        @Shared @Cached HashingStorageLen lenNode,
                        @Shared @Cached HashingStorageGetReverseIterator getReverseIterator,
                        @Bind PythonLanguage language) {
            HashingStorage storage = self.getWrappedStorage();
            return PFactory.createDictItemIterator(language, getReverseIterator.execute(inliningTarget, storage), storage, lenNode.execute(inliningTarget, storage));
        }
    }

    @Slot(value = SlotKind.sq_contains, isComplex = true)
    @GenerateNodeFactory
    public abstract static class ContainsNode extends SqContainsBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization(guards = "len.execute(inliningTarget, self.getWrappedStorage()) == 0", limit = "1")
        static boolean containsEmpty(PDictView self, Object key,
                        @Bind Node inliningTarget,
                        @Cached HashingStorageLen len) {
            return false;
        }

        @Specialization
        static boolean contains(VirtualFrame frame, PDictKeysView self, Object key,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached HashingStorageGetItem getItem) {
            return getItem.hasKey(frame, inliningTarget, self.getWrappedStorage(), key);
        }

        @Specialization
        static boolean contains(VirtualFrame frame, PDictItemsView self, PTuple key,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached HashingStorageGetItem getItem,
                        @Cached PyObjectRichCompareBool eqNode,
                        @Cached InlinedConditionProfile tupleLenProfile,
                        @Cached("createNotNormalized()") SequenceStorageNodes.GetItemNode getTupleItemNode) {
            SequenceStorage tupleStorage = key.getSequenceStorage();
            if (tupleLenProfile.profile(inliningTarget, tupleStorage.length() != 2)) {
                return false;
            }
            HashingStorage dictStorage = self.getWrappedStorage();
            Object value = getItem.execute(frame, inliningTarget, dictStorage, getTupleItemNode.execute(tupleStorage, 0));
            if (value != null) {
                return eqNode.execute(frame, inliningTarget, value, getTupleItemNode.execute(tupleStorage, 1), RichCmpOp.Py_EQ);
            } else {
                return false;
            }
        }

        protected static boolean isFallback(Object self, Object key) {
            return !(self instanceof PDictView) || self instanceof PDictItemsView && !(key instanceof PTuple);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "isFallback(self, key)")
        static boolean contains(Object self, Object key) {
            return false;
        }

    }

    @Builtin(name = J_ISDISJOINT, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class IsDisjointNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = {"self == other"})
        static boolean disjointSame(PDictView self, @SuppressWarnings("unused") PDictView other,
                        @Bind Node inliningTarget,
                        @Cached @Shared HashingStorageLen len) {
            return len.execute(inliningTarget, self.getWrappedStorage()) == 0;
        }

        @Specialization(guards = {"self != other"})
        static boolean disjointNotSame(VirtualFrame frame, PDictView self, PDictView other,
                        @Bind Node inliningTarget,
                        @Cached @Shared HashingStorageLen len,
                        @Cached @Shared InlinedConditionProfile sizeProfile,
                        @Cached @Shared PyObjectSizeNode sizeNode,
                        @Cached("create(false)") @Shared ContainedInNode contained) {
            return disjointImpl(frame, inliningTarget, self, other, len, sizeProfile, sizeNode, contained);
        }

        @Specialization
        static boolean disjoint(VirtualFrame frame, PDictView self, PBaseSet other,
                        @Bind Node inliningTarget,
                        @Cached @Shared HashingStorageLen len,
                        @Cached @Shared InlinedConditionProfile sizeProfile,
                        @Cached @Shared PyObjectSizeNode sizeNode,
                        @Cached("create(false)") @Shared ContainedInNode contained) {
            return disjointImpl(frame, inliningTarget, self, other, len, sizeProfile, sizeNode, contained);
        }

        private static boolean disjointImpl(VirtualFrame frame, Node inliningTarget, PDictView self, Object other, HashingStorageLen len, InlinedConditionProfile sizeProfile,
                        PyObjectSizeNode sizeNode, ContainedInNode contained) {
            if (sizeProfile.profile(inliningTarget, len.execute(inliningTarget, self.getWrappedStorage()) <= sizeNode.execute(frame, inliningTarget, other))) {
                return !contained.execute(frame, self, other);
            } else {
                return !contained.execute(frame, other, self);
            }
        }

        @Specialization(guards = {"!isAnySet(other)", "!isDictView(other)"})
        static boolean disjoint(VirtualFrame frame, PDictView self, Object other,
                        @Cached("create(false)") @Shared ContainedInNode contained) {
            return !contained.execute(frame, other, self);
        }
    }

    /**
     * See CPython's dictobject.c all_contained_in and dictviews_isdisjoint. The semantics of dict
     * view comparisons dictates that we need to use iteration to compare them in the general case.
     */
    protected abstract static class ContainedInNode extends PNodeWithContext {
        private final boolean checkAll;

        public ContainedInNode(boolean checkAll) {
            this.checkAll = checkAll;
        }

        public abstract boolean execute(VirtualFrame frame, Object self, Object other);

        @Specialization
        public boolean doIt(VirtualFrame frame, Object self, Object other,
                        @Bind Node inliningTarget,
                        @Cached InlinedLoopConditionProfile loopConditionProfile,
                        @Cached PyObjectGetIter getIterNode,
                        @Cached PyIterNextNode nextNode,
                        @Cached PySequenceContainsNode containsNode,
                        @Cached PyObjectIsTrueNode isTrueNode) {
            Object iterator = getIterNode.execute(frame, inliningTarget, self);
            boolean ok = checkAll;
            int i = 0;
            try {
                while (loopConditionProfile.profile(inliningTarget, checkAll && ok || !checkAll && !ok)) {
                    Object item;
                    try {
                        item = nextNode.execute(frame, inliningTarget, iterator);
                    } catch (IteratorExhausted e) {
                        break;
                    }
                    ok = isTrueNode.execute(frame, containsNode.execute(frame, inliningTarget, other, item));
                    i++;
                }
            } finally {
                LoopNode.reportLoopCount(this, i < 0 ? Integer.MAX_VALUE : i);
            }
            return ok;
        }

        @NeverDefault
        static ContainedInNode create() {
            return ContainedInNodeGen.create(true);
        }

        @NeverDefault
        static ContainedInNode create(boolean all) {
            return ContainedInNodeGen.create(all);
        }
    }

    @Slot(value = SlotKind.tp_richcompare, isComplex = true)
    @GenerateNodeFactory
    abstract static class DictViewRichcompareHelperNode extends TpSlotRichCompare.RichCmpBuiltinNode {

        protected static boolean reverse(RichCmpOp op) {
            return op == RichCmpOp.Py_GE || op == RichCmpOp.Py_GT;
        }

        static boolean isDictViewOrSet(Object o) {
            return o instanceof PDictView || o instanceof PBaseSet;
        }

        @Specialization(guards = "isDictViewOrSet(other)")
        static boolean doIt(VirtualFrame frame, PDictView self, Object other, RichCmpOp originalOp,
                        @Bind Node inliningTarget,
                        @Cached InlinedConditionProfile isSetProfile,
                        @Cached InlinedConditionProfile lenCheckProfile,
                        @Cached InlinedConditionProfile reverseProfile,
                        @Cached HashingStorageLen selfLenNode,
                        @Cached HashingStorageLen otherLenNode,
                        @Cached(inline = false) ContainedInNode allContained) {
            // Note: more compact (to help hosted inlining) implementation, but should be in the end
            // the same as CPython dictview_richcompare
            RichCmpOp op = originalOp != RichCmpOp.Py_NE ? originalOp : RichCmpOp.Py_EQ;

            int lenSelf = selfLenNode.execute(inliningTarget, self.getWrappedStorage());
            HashingStorage otherStorage;
            if (isSetProfile.profile(inliningTarget, other instanceof PBaseSet)) {
                otherStorage = ((PBaseSet) other).getDictStorage();
            } else {
                otherStorage = ((PDictView) other).getWrappedStorage();
            }

            int lenOther = otherLenNode.execute(inliningTarget, otherStorage);
            if (lenCheckProfile.profile(inliningTarget, !op.compareResultToBool(lenSelf - lenOther))) {
                return originalOp == RichCmpOp.Py_NE;
            }
            Object left = self;
            Object right = other;
            if (reverseProfile.profile(inliningTarget, reverse(op))) {
                left = other;
                right = self;
            }
            boolean result = allContained.execute(frame, left, right);
            if (originalOp == RichCmpOp.Py_NE) {
                result = !result;
            }
            return result;
        }

        @Fallback
        @SuppressWarnings("unused")
        static PNotImplemented wrongTypes(Object self, Object other, RichCmpOp op) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Slot(value = SlotKind.nb_subtract, isComplex = true)
    @GenerateNodeFactory
    abstract static class SubNode extends BinaryOpBuiltinNode {

        @Specialization
        static PBaseSet doKeysView(VirtualFrame frame, PDictKeysView self, PBaseSet other,
                        @Bind Node inliningTarget,
                        @Shared("diff") @Cached HashingStorageDiff diffNode,
                        @Bind PythonLanguage language) {
            HashingStorage storage = diffNode.execute(frame, inliningTarget, self.getWrappedStorage(), other.getDictStorage());
            return PFactory.createSet(language, storage);
        }

        @Specialization
        static PBaseSet doKeysView(VirtualFrame frame, PDictKeysView self, PDictKeysView other,
                        @Bind Node inliningTarget,
                        @Shared("diff") @Cached HashingStorageDiff diffNode,
                        @Bind PythonLanguage language) {
            HashingStorage storage = diffNode.execute(frame, inliningTarget, self.getWrappedStorage(), other.getWrappedStorage());
            return PFactory.createSet(language, storage);
        }

        @Specialization
        static PBaseSet doKeysView(VirtualFrame frame, PDictKeysView self, Object other,
                        @Bind Node inliningTarget,
                        @Shared("constrSet") @Cached SetNodes.ConstructSetNode constructSetNode,
                        @Shared("diff") @Cached HashingStorageDiff diffNode,
                        @Bind PythonLanguage language) {
            HashingStorage left = self.getWrappedStorage();
            HashingStorage right = constructSetNode.execute(frame, other).getDictStorage();
            HashingStorage storage = diffNode.execute(frame, inliningTarget, left, right);
            return PFactory.createSet(language, storage);
        }

        @Specialization
        static PBaseSet doItemsView(VirtualFrame frame, PDictItemsView self, PBaseSet other,
                        @Bind Node inliningTarget,
                        @Shared("constrSet") @Cached SetNodes.ConstructSetNode constructSetNode,
                        @Shared("diff") @Cached HashingStorageDiff diffNode,
                        @Bind PythonLanguage language) {
            PSet selfSet = constructSetNode.execute(frame, self);
            HashingStorage storage = diffNode.execute(frame, inliningTarget, selfSet.getDictStorage(), other.getDictStorage());
            return PFactory.createSet(language, storage);
        }

        @Specialization
        static PBaseSet doGeneric(VirtualFrame frame, Object self, Object other,
                        @Bind Node inliningTarget,
                        @Shared("constrSet") @Cached SetNodes.ConstructSetNode constructSetNode,
                        @Shared("diff") @Cached HashingStorageDiff diffNode,
                        @Bind PythonLanguage language) {
            HashingStorage left = constructSetNode.execute(frame, self).getDictStorage();
            HashingStorage right = constructSetNode.execute(frame, other).getDictStorage();
            HashingStorage storage = diffNode.execute(frame, inliningTarget, left, right);
            return PFactory.createSet(language, storage);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    abstract static class GetStorageForBinopNode extends Node {
        public abstract HashingStorage execute(Frame frame, Node inliningTarget, Object obj);

        @Specialization
        static HashingStorage doView(PDictKeysView obj) {
            return obj.getWrappedStorage();
        }

        @Specialization
        static HashingStorage doSet(PBaseSet obj) {
            return obj.getDictStorage();
        }

        @Fallback
        static HashingStorage doOther(VirtualFrame frame, Object obj,
                        @Cached SetNodes.ConstructSetNode constructSetNode) {
            return constructSetNode.execute(frame, obj).getDictStorage();
        }
    }

    @GenerateInline
    @GenerateCached(false)
    abstract static class GetCopiedStorageForBinopNode extends Node {
        public abstract HashingStorage execute(Frame frame, Node inliningTarget, Object obj);

        @Specialization
        static HashingStorage doView(Node inliningTarget, PDictKeysView obj,
                        @Shared @Cached HashingStorageCopy copyNode) {
            return copyNode.execute(inliningTarget, obj.getWrappedStorage());
        }

        @Specialization
        static HashingStorage doSet(Node inliningTarget, PBaseSet obj,
                        @Shared @Cached HashingStorageCopy copyNode) {
            return copyNode.execute(inliningTarget, obj.getDictStorage());
        }

        @Fallback
        static HashingStorage doOther(VirtualFrame frame, Object obj,
                        @Cached SetNodes.ConstructSetNode constructSetNode) {
            return constructSetNode.execute(frame, obj).getDictStorage();
        }
    }

    @Slot(value = SlotKind.nb_and, isComplex = true)
    @GenerateNodeFactory
    abstract static class AndNode extends BinaryOpBuiltinNode {

        @Specialization
        static PBaseSet doGeneric(VirtualFrame frame, Object self, Object other,
                        @Bind Node inliningTarget,
                        @Cached GetStorageForBinopNode getStorage,
                        @Cached HashingStorageIntersect intersectNode,
                        @Bind PythonLanguage language) {
            HashingStorage left = getStorage.execute(frame, inliningTarget, self);
            HashingStorage right = getStorage.execute(frame, inliningTarget, other);
            return PFactory.createSet(language, intersectNode.execute(frame, inliningTarget, left, right));
        }
    }

    @Slot(value = SlotKind.nb_or, isComplex = true)
    @GenerateNodeFactory
    public abstract static class OrNode extends BinaryOpBuiltinNode {

        @Specialization
        static PBaseSet doGeneric(VirtualFrame frame, Object self, Object other,
                        @Bind Node inliningTarget,
                        @Cached GetCopiedStorageForBinopNode getStorage,
                        @Cached HashingStorageAddAllToOther addAllToOther,
                        @Bind PythonLanguage language) {
            HashingStorage left = getStorage.execute(frame, inliningTarget, self);
            HashingStorage right = getStorage.execute(frame, inliningTarget, other);
            return PFactory.createSet(language, addAllToOther.execute(frame, inliningTarget, left, right));
        }
    }

    @Slot(value = SlotKind.nb_xor, isComplex = true)
    @GenerateNodeFactory
    public abstract static class XorNode extends BinaryOpBuiltinNode {

        @Specialization
        static PBaseSet doGeneric(VirtualFrame frame, Object self, Object other,
                        @Bind Node inliningTarget,
                        @Cached GetStorageForBinopNode getStorage,
                        @Cached HashingStorageXor xor,
                        @Bind PythonLanguage language) {
            HashingStorage left = getStorage.execute(frame, inliningTarget, self);
            HashingStorage right = getStorage.execute(frame, inliningTarget, other);
            return PFactory.createSet(language, xor.execute(frame, inliningTarget, left, right));
        }
    }
}
