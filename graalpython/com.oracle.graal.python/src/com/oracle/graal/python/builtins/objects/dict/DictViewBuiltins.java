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
package com.oracle.graal.python.builtins.objects.dict;

import static com.oracle.graal.python.nodes.SpecialMethodNames.J_ISDISJOINT;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___AND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CONTAINS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___LEN__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___LE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___LT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___OR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RAND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REVERSED__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ROR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RSUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RXOR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___XOR__;

import java.util.List;

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
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.lib.GetNextNode;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectRichCompareBool;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.expression.CoerceToBooleanNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.profiles.InlinedLoopConditionProfile;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PDictKeysView, PythonBuiltinClassType.PDictItemsView})
public final class DictViewBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return DictViewBuiltinsFactory.getFactories();
    }

    @Builtin(name = "mapping", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class MappingNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object mapping(PDictView self,
                        @Cached PythonObjectFactory factory) {
            return factory.createMappingproxy(self.getWrappedDict());
        }
    }

    @Builtin(name = J___LEN__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class LenNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object len(PDictView self,
                        @Bind("this") Node inliningTarget,
                        @Cached HashingStorageLen len) {
            return len.execute(inliningTarget, self.getWrappedDict().getDictStorage());
        }
    }

    @Builtin(name = J___ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object getKeysViewIter(@SuppressWarnings("unused") PDictKeysView self,
                        @Bind("this") Node inliningTarget,
                        @Shared("len") @Cached HashingStorageLen lenNode,
                        @Shared("getit") @Cached HashingStorageGetIterator getIterator,
                        @Shared @Cached PythonObjectFactory factory) {
            HashingStorage storage = self.getWrappedDict().getDictStorage();
            return factory.createDictKeyIterator(getIterator.execute(inliningTarget, storage), storage, lenNode.execute(inliningTarget, storage));
        }

        @Specialization
        static Object getItemsViewIter(PDictItemsView self,
                        @Bind("this") Node inliningTarget,
                        @Shared("len") @Cached HashingStorageLen lenNode,
                        @Shared("getit") @Cached HashingStorageGetIterator getIterator,
                        @Shared @Cached PythonObjectFactory factory) {
            HashingStorage storage = self.getWrappedDict().getDictStorage();
            return factory.createDictItemIterator(getIterator.execute(inliningTarget, storage), storage, lenNode.execute(inliningTarget, storage));
        }
    }

    @Builtin(name = J___REVERSED__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @SuppressWarnings("truffle-static-method")
    public abstract static class ReversedNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object getReversedKeysViewIter(PDictKeysView self,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached HashingStorageLen lenNode,
                        @Shared @Cached HashingStorageGetReverseIterator getReverseIterator,
                        @Shared @Cached PythonObjectFactory factory) {
            HashingStorage storage = self.getWrappedDict().getDictStorage();
            return factory.createDictKeyIterator(getReverseIterator.execute(inliningTarget, storage), storage, lenNode.execute(inliningTarget, storage));
        }

        @Specialization
        static Object getReversedItemsViewIter(PDictItemsView self,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached HashingStorageLen lenNode,
                        @Shared @Cached HashingStorageGetReverseIterator getReverseIterator,
                        @Shared @Cached PythonObjectFactory factory) {
            HashingStorage storage = self.getWrappedDict().getDictStorage();
            return factory.createDictItemIterator(getReverseIterator.execute(inliningTarget, storage), storage, lenNode.execute(inliningTarget, storage));
        }
    }

    @Builtin(name = J___CONTAINS__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ContainsNode extends PythonBinaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization(guards = "len.execute(inliningTarget, self.getWrappedDict().getDictStorage()) == 0", limit = "1")
        static boolean containsEmpty(PDictView self, Object key,
                        @Bind("this") Node inliningTarget,
                        @Cached HashingStorageLen len) {
            return false;
        }

        @Specialization
        static boolean contains(VirtualFrame frame, PDictKeysView self, Object key,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached HashingStorageGetItem getItem) {
            return getItem.hasKey(frame, inliningTarget, self.getWrappedDict().getDictStorage(), key);
        }

        @Specialization
        static boolean contains(VirtualFrame frame, PDictItemsView self, PTuple key,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached HashingStorageGetItem getItem,
                        @Cached PyObjectRichCompareBool.EqNode eqNode,
                        @Cached InlinedConditionProfile tupleLenProfile,
                        @Cached("createNotNormalized()") SequenceStorageNodes.GetItemNode getTupleItemNode) {
            SequenceStorage tupleStorage = key.getSequenceStorage();
            if (tupleLenProfile.profile(inliningTarget, tupleStorage.length() != 2)) {
                return false;
            }
            HashingStorage dictStorage = self.getWrappedDict().getDictStorage();
            Object value = getItem.execute(frame, inliningTarget, dictStorage, getTupleItemNode.execute(tupleStorage, 0));
            if (value != null) {
                return eqNode.compare(frame, inliningTarget, value, getTupleItemNode.execute(tupleStorage, 1));
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
                        @Bind("this") Node inliningTarget,
                        @Cached @Shared HashingStorageLen len) {
            return len.execute(inliningTarget, self.getWrappedDict().getDictStorage()) == 0;
        }

        @Specialization(guards = {"self != other"})
        static boolean disjointNotSame(VirtualFrame frame, PDictView self, PDictView other,
                        @Bind("this") Node inliningTarget,
                        @Cached @Shared HashingStorageLen len,
                        @Cached @Shared InlinedConditionProfile sizeProfile,
                        @Cached @Shared PyObjectSizeNode sizeNode,
                        @Cached("create(false)") @Shared ContainedInNode contained) {
            return disjointImpl(frame, inliningTarget, self, other, len, sizeProfile, sizeNode, contained);
        }

        @Specialization
        static boolean disjoint(VirtualFrame frame, PDictView self, PBaseSet other,
                        @Bind("this") Node inliningTarget,
                        @Cached @Shared HashingStorageLen len,
                        @Cached @Shared InlinedConditionProfile sizeProfile,
                        @Cached @Shared PyObjectSizeNode sizeNode,
                        @Cached("create(false)") @Shared ContainedInNode contained) {
            return disjointImpl(frame, inliningTarget, self, other, len, sizeProfile, sizeNode, contained);
        }

        private static boolean disjointImpl(VirtualFrame frame, Node inliningTarget, PDictView self, Object other, HashingStorageLen len, InlinedConditionProfile sizeProfile,
                        PyObjectSizeNode sizeNode, ContainedInNode contained) {
            if (sizeProfile.profile(inliningTarget, len.execute(inliningTarget, self.getWrappedDict().getDictStorage()) <= sizeNode.execute(frame, inliningTarget, other))) {
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
        @Child private GetNextNode next;
        @Child private LookupAndCallBinaryNode contains;
        @Child private CoerceToBooleanNode cast;
        private final boolean checkAll;

        public ContainedInNode(boolean checkAll) {
            this.checkAll = checkAll;
        }

        private GetNextNode getNext() {
            if (next == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                next = insert(GetNextNode.create());
            }
            return next;
        }

        private LookupAndCallBinaryNode getContains() {
            if (contains == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                contains = insert(LookupAndCallBinaryNode.create(SpecialMethodSlot.Contains));
            }
            return contains;
        }

        private CoerceToBooleanNode getCast() {
            if (cast == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                cast = insert(CoerceToBooleanNode.createIfTrueNode());
            }
            return cast;
        }

        public abstract boolean execute(VirtualFrame frame, Object self, Object other);

        @Specialization
        public boolean doIt(VirtualFrame frame, Object self, Object other,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedLoopConditionProfile loopConditionProfile,
                        @Cached PyObjectGetIter getIterNode,
                        @Cached IsBuiltinObjectProfile stopProfile) {
            Object iterator = getIterNode.execute(frame, inliningTarget, self);
            boolean ok = checkAll;
            int i = 0;
            try {
                while (loopConditionProfile.profile(inliningTarget, checkAll && ok || !checkAll && !ok)) {
                    Object item = getNext().execute(frame, iterator);
                    ok = getCast().executeBooleanCached(frame, getContains().executeObject(frame, other, item));
                    i++;
                }
            } catch (PException e) {
                e.expectStopIteration(inliningTarget, stopProfile);
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

    @SuppressWarnings("truffle-static-method")
    protected abstract static class DictViewRichcompareNode extends PythonBinaryBuiltinNode {
        protected boolean reverse() {
            CompilerAsserts.neverPartOfCompilation();
            throw new IllegalStateException("subclass should have implemented reverse");
        }

        protected boolean lenCompare(@SuppressWarnings("unused") int lenSelf, @SuppressWarnings("unused") int lenOther) {
            CompilerAsserts.neverPartOfCompilation();
            throw new IllegalStateException("subclass should have implemented lenCompare");
        }

        @Specialization
        boolean doView(VirtualFrame frame, PDictView self, PBaseSet other,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached HashingStorageLen selfLenNode,
                        @Shared @Cached HashingStorageLen otherLenNode,
                        @Shared @Cached ContainedInNode allContained) {
            int lenSelf = selfLenNode.execute(inliningTarget, self.getWrappedDict().getDictStorage());
            int lenOther = otherLenNode.execute(inliningTarget, other.getDictStorage());
            return lenCompare(lenSelf, lenOther) && (reverse() ? allContained.execute(frame, other, self) : allContained.execute(frame, self, other));
        }

        @Specialization
        boolean doView(VirtualFrame frame, PDictView self, PDictView other,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached HashingStorageLen selfLenNode,
                        @Shared @Cached HashingStorageLen otherLenNode,
                        @Shared @Cached ContainedInNode allContained) {
            int lenSelf = selfLenNode.execute(inliningTarget, self.getWrappedDict().getDictStorage());
            int lenOther = otherLenNode.execute(inliningTarget, other.getWrappedDict().getDictStorage());
            return lenCompare(lenSelf, lenOther) && (reverse() ? allContained.execute(frame, other, self) : allContained.execute(frame, self, other));
        }

        @Fallback
        static PNotImplemented wrongTypes(@SuppressWarnings("unused") Object self, @SuppressWarnings("unused") Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = J___EQ__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class EqNode extends DictViewRichcompareNode {
        @Override
        protected boolean reverse() {
            return false;
        }

        @Override
        protected boolean lenCompare(int lenSelf, int lenOther) {
            return lenSelf == lenOther;
        }
    }

    @Builtin(name = J___NE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class NeNode extends PythonBinaryBuiltinNode {
        @Child EqNode eqNode;

        private EqNode getEqNode() {
            if (eqNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                eqNode = insert(DictViewBuiltinsFactory.EqNodeFactory.create());
            }
            return eqNode;
        }

        @Specialization
        public Object notEqual(VirtualFrame frame, Object self, Object other) {
            Object result = getEqNode().execute(frame, self, other);
            if (result == PNotImplemented.NOT_IMPLEMENTED) {
                return result;
            } else {
                assert result instanceof Boolean;
                return !((Boolean) result);
            }
        }
    }

    @Builtin(name = J___SUB__, minNumOfPositionalArgs = 2)
    @Builtin(name = J___RSUB__, minNumOfPositionalArgs = 2, reverseOperation = true)
    @GenerateNodeFactory
    abstract static class SubNode extends PythonBinaryBuiltinNode {

        @Specialization
        static PBaseSet doKeysView(VirtualFrame frame, PDictKeysView self, PBaseSet other,
                        @Bind("this") Node inliningTarget,
                        @Shared("diff") @Cached HashingStorageDiff diffNode,
                        @Shared @Cached PythonObjectFactory factory) {
            HashingStorage storage = diffNode.execute(frame, inliningTarget, self.getWrappedDict().getDictStorage(), other.getDictStorage());
            return factory.createSet(storage);
        }

        @Specialization
        static PBaseSet doKeysView(VirtualFrame frame, PDictKeysView self, PDictKeysView other,
                        @Bind("this") Node inliningTarget,
                        @Shared("diff") @Cached HashingStorageDiff diffNode,
                        @Shared @Cached PythonObjectFactory factory) {
            HashingStorage storage = diffNode.execute(frame, inliningTarget, self.getWrappedDict().getDictStorage(), other.getWrappedDict().getDictStorage());
            return factory.createSet(storage);
        }

        @Specialization
        static PBaseSet doKeysView(VirtualFrame frame, PDictKeysView self, Object other,
                        @Bind("this") Node inliningTarget,
                        @Shared("constrSet") @Cached SetNodes.ConstructSetNode constructSetNode,
                        @Shared("diff") @Cached HashingStorageDiff diffNode,
                        @Shared @Cached PythonObjectFactory factory) {
            HashingStorage left = self.getWrappedDict().getDictStorage();
            HashingStorage right = constructSetNode.executeWith(frame, other).getDictStorage();
            HashingStorage storage = diffNode.execute(frame, inliningTarget, left, right);
            return factory.createSet(storage);
        }

        @Specialization
        static PBaseSet doItemsView(VirtualFrame frame, PDictItemsView self, PBaseSet other,
                        @Bind("this") Node inliningTarget,
                        @Shared("constrSet") @Cached SetNodes.ConstructSetNode constructSetNode,
                        @Shared("diff") @Cached HashingStorageDiff diffNode,
                        @Shared @Cached PythonObjectFactory factory) {
            PSet selfSet = constructSetNode.executeWith(frame, self);
            HashingStorage storage = diffNode.execute(frame, inliningTarget, selfSet.getDictStorage(), other.getDictStorage());
            return factory.createSet(storage);
        }

        @Specialization
        static PBaseSet doGeneric(VirtualFrame frame, Object self, Object other,
                        @Bind("this") Node inliningTarget,
                        @Shared("constrSet") @Cached SetNodes.ConstructSetNode constructSetNode,
                        @Shared("diff") @Cached HashingStorageDiff diffNode,
                        @Shared @Cached PythonObjectFactory factory) {
            HashingStorage left = constructSetNode.executeWith(frame, self).getDictStorage();
            HashingStorage right = constructSetNode.executeWith(frame, other).getDictStorage();
            HashingStorage storage = diffNode.execute(frame, inliningTarget, left, right);
            return factory.createSet(storage);
        }
    }

    @Builtin(name = J___AND__, minNumOfPositionalArgs = 2)
    @Builtin(name = J___RAND__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class AndNode extends PythonBinaryBuiltinNode {

        @Specialization
        static PBaseSet doKeysView(VirtualFrame frame, PDictKeysView self, PBaseSet other,
                        @Bind("this") Node inliningTarget,
                        @Shared("intersect") @Cached HashingStorageIntersect intersectNode,
                        @Shared @Cached PythonObjectFactory factory) {
            HashingStorage left = self.getWrappedDict().getDictStorage();
            HashingStorage right = other.getDictStorage();
            HashingStorage intersectedStorage = intersectNode.execute(frame, inliningTarget, left, right);
            return factory.createSet(intersectedStorage);
        }

        @Specialization
        static PBaseSet doKeysView(VirtualFrame frame, PDictKeysView self, PDictKeysView other,
                        @Bind("this") Node inliningTarget,
                        @Shared("intersect") @Cached HashingStorageIntersect intersectNode,
                        @Shared @Cached PythonObjectFactory factory) {
            HashingStorage left = self.getWrappedDict().getDictStorage();
            HashingStorage right = other.getWrappedDict().getDictStorage();
            HashingStorage intersectedStorage = intersectNode.execute(frame, inliningTarget, left, right);
            return factory.createSet(intersectedStorage);
        }

        @Specialization
        static PBaseSet doKeysView(VirtualFrame frame, PDictKeysView self, Object other,
                        @Bind("this") Node inliningTarget,
                        @Shared("constrSet") @Cached SetNodes.ConstructSetNode constructSetNode,
                        @Shared("intersect") @Cached HashingStorageIntersect intersectNode,
                        @Shared @Cached PythonObjectFactory factory) {
            HashingStorage left = self.getWrappedDict().getDictStorage();
            HashingStorage right = constructSetNode.executeWith(frame, other).getDictStorage();
            HashingStorage intersectedStorage = intersectNode.execute(frame, inliningTarget, left, right);
            return factory.createSet(intersectedStorage);
        }

        @Specialization
        static PBaseSet doItemsView(VirtualFrame frame, PDictItemsView self, PBaseSet other,
                        @Bind("this") Node inliningTarget,
                        @Shared("intersect") @Cached HashingStorageIntersect intersectNode,
                        @Shared("constrSet") @Cached SetNodes.ConstructSetNode constructSetNode,
                        @Shared @Cached PythonObjectFactory factory) {
            PSet selfSet = constructSetNode.executeWith(frame, self);
            HashingStorage left = selfSet.getDictStorage();
            HashingStorage right = other.getDictStorage();
            HashingStorage intersectedStorage = intersectNode.execute(frame, inliningTarget, left, right);
            return factory.createSet(intersectedStorage);
        }

        @Specialization
        static PBaseSet doItemsView(VirtualFrame frame, PDictItemsView self, PDictItemsView other,
                        @Bind("this") Node inliningTarget,
                        @Shared("intersect") @Cached HashingStorageIntersect intersectNode,
                        @Shared("constrSet") @Cached SetNodes.ConstructSetNode constructSetNode,
                        @Shared @Cached PythonObjectFactory factory) {
            PSet selfSet = constructSetNode.executeWith(frame, self);
            PSet otherSet = constructSetNode.executeWith(frame, other);
            HashingStorage left = selfSet.getDictStorage();
            HashingStorage right = otherSet.getDictStorage();
            HashingStorage intersectedStorage = intersectNode.execute(frame, inliningTarget, left, right);
            return factory.createSet(intersectedStorage);
        }

        @Specialization
        static PBaseSet doItemsView(VirtualFrame frame, PDictItemsView self, Object other,
                        @Bind("this") Node inliningTarget,
                        @Shared("constrSet") @Cached SetNodes.ConstructSetNode constructSetNode,
                        @Shared("intersect") @Cached HashingStorageIntersect intersectNode,
                        @Shared @Cached PythonObjectFactory factory) {
            HashingStorage left = constructSetNode.executeWith(frame, self).getDictStorage();
            HashingStorage right = constructSetNode.executeWith(frame, other).getDictStorage();
            HashingStorage intersectedStorage = intersectNode.execute(frame, inliningTarget, left, right);
            return factory.createSet(intersectedStorage);
        }
    }

    @Builtin(name = J___OR__, minNumOfPositionalArgs = 2)
    @Builtin(name = J___ROR__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class OrNode extends PythonBinaryBuiltinNode {

        protected static HashingStorage union(Node inliningTarget, HashingStorageCopy copyNode, HashingStorageAddAllToOther addAllToOther, HashingStorage left, HashingStorage right) {
            return left.union(inliningTarget, right, copyNode, addAllToOther);
        }

        @Specialization
        static PBaseSet doKeysView(@SuppressWarnings("unused") VirtualFrame frame, PDictKeysView self, PBaseSet other,
                        @Bind("this") Node inliningTarget,
                        @Shared("copy") @Cached HashingStorageCopy copyNode,
                        @Shared("addAll") @Cached HashingStorageAddAllToOther addAllToOther,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createSet(union(inliningTarget, copyNode, addAllToOther, self.getWrappedDict().getDictStorage(), other.getDictStorage()));
        }

        @Specialization
        static PBaseSet doKeysView(@SuppressWarnings("unused") VirtualFrame frame, PDictKeysView self, PDictKeysView other,
                        @Bind("this") Node inliningTarget,
                        @Shared("copy") @Cached HashingStorageCopy copyNode,
                        @Shared("addAll") @Cached HashingStorageAddAllToOther addAllToOther,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createSet(union(inliningTarget, copyNode, addAllToOther, self.getWrappedDict().getDictStorage(), other.getWrappedDict().getDictStorage()));
        }

        @Specialization
        static PBaseSet doKeysView(@SuppressWarnings("unused") VirtualFrame frame, PDictKeysView self, Object other,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached SetNodes.ConstructSetNode constructSetNode,
                        @Shared("copy") @Cached HashingStorageCopy copyNode,
                        @Shared("addAll") @Cached HashingStorageAddAllToOther addAllToOther,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createSet(union(inliningTarget, copyNode, addAllToOther, self.getWrappedDict().getDictStorage(), constructSetNode.executeWith(frame, other).getDictStorage()));
        }

        @Specialization
        static PBaseSet doItemsView(VirtualFrame frame, PDictItemsView self, PBaseSet other,
                        @Bind("this") Node inliningTarget,
                        @Shared("copy") @Cached HashingStorageCopy copyNode,
                        @Shared("addAll") @Cached HashingStorageAddAllToOther addAllToOther,
                        @Shared @Cached SetNodes.ConstructSetNode constructSetNode,
                        @Shared @Cached PythonObjectFactory factory) {
            PSet selfSet = constructSetNode.executeWith(frame, self);
            return factory.createSet(union(inliningTarget, copyNode, addAllToOther, selfSet.getDictStorage(), other.getDictStorage()));
        }

        @Specialization
        static PBaseSet doItemsView(VirtualFrame frame, PDictItemsView self, PDictItemsView other,
                        @Bind("this") Node inliningTarget,
                        @Shared("copy") @Cached HashingStorageCopy copyNode,
                        @Shared("addAll") @Cached HashingStorageAddAllToOther addAllToOther,
                        @Shared @Cached SetNodes.ConstructSetNode constructSetNode,
                        @Shared @Cached PythonObjectFactory factory) {
            PSet selfSet = constructSetNode.executeWith(frame, self);
            PSet otherSet = constructSetNode.executeWith(frame, other);
            return factory.createSet(union(inliningTarget, copyNode, addAllToOther, selfSet.getDictStorage(), otherSet.getDictStorage()));
        }

        @Specialization
        static PBaseSet doItemsView(VirtualFrame frame, PDictItemsView self, Object other,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached SetNodes.ConstructSetNode constructSetNode,
                        @Shared("copy") @Cached HashingStorageCopy copyNode,
                        @Shared("addAll") @Cached HashingStorageAddAllToOther addAllToOther,
                        @Shared @Cached PythonObjectFactory factory) {
            HashingStorage selfStorage = constructSetNode.executeWith(frame, self).getDictStorage();
            HashingStorage otherStorage = constructSetNode.executeWith(frame, other).getDictStorage();
            return factory.createSet(union(inliningTarget, copyNode, addAllToOther, selfStorage, otherStorage));
        }
    }

    @Builtin(name = J___XOR__, minNumOfPositionalArgs = 2)
    @Builtin(name = J___RXOR__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class XorNode extends PythonBinaryBuiltinNode {

        protected static HashingStorage xor(VirtualFrame frame, Node inliningTarget, HashingStorageXor xorNode, HashingStorage left, HashingStorage right) {
            return xorNode.execute(frame, inliningTarget, left, right);
        }

        @Specialization
        static PBaseSet doKeysView(VirtualFrame frame, PDictKeysView self, PBaseSet other,
                        @Bind("this") Node inliningTarget,
                        @Shared("xorNode") @Cached HashingStorageXor xorNode,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createSet(xor(frame, inliningTarget, xorNode, self.getWrappedDict().getDictStorage(), other.getDictStorage()));
        }

        @Specialization
        static PBaseSet doKeysView(VirtualFrame frame, PDictKeysView self, PDictKeysView other,
                        @Bind("this") Node inliningTarget,
                        @Shared("xorNode") @Cached HashingStorageXor xorNode,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createSet(xor(frame, inliningTarget, xorNode, self.getWrappedDict().getDictStorage(), other.getWrappedDict().getDictStorage()));
        }

        @Specialization
        static PBaseSet doKeysView(VirtualFrame frame, PDictKeysView self, Object other,
                        @Bind("this") Node inliningTarget,
                        @Shared("constructSet") @Cached SetNodes.ConstructSetNode constructSetNode,
                        @Shared("xorNode") @Cached HashingStorageXor xorNode,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createSet(xor(frame, inliningTarget, xorNode, self.getWrappedDict().getDictStorage(), constructSetNode.executeWith(frame, other).getDictStorage()));
        }

        @Specialization
        static PBaseSet doItemsView(VirtualFrame frame, PDictItemsView self, PBaseSet other,
                        @Bind("this") Node inliningTarget,
                        @Shared("xorNode") @Cached HashingStorageXor xorNode,
                        @Shared("constructSet") @Cached SetNodes.ConstructSetNode constructSetNode,
                        @Shared @Cached PythonObjectFactory factory) {
            PSet selfSet = constructSetNode.executeWith(frame, self);
            return factory.createSet(xor(frame, inliningTarget, xorNode, selfSet.getDictStorage(), other.getDictStorage()));
        }

        @Specialization
        static PBaseSet doItemsView(@SuppressWarnings("unused") VirtualFrame frame, PDictItemsView self, PDictItemsView other,
                        @Bind("this") Node inliningTarget,
                        @Shared("xorNode") @Cached HashingStorageXor xorNode,
                        @Shared("constructSet") @Cached SetNodes.ConstructSetNode constructSetNode,
                        @Shared @Cached PythonObjectFactory factory) {
            PSet selfSet = constructSetNode.executeWith(frame, self);
            PSet otherSet = constructSetNode.executeWith(frame, other);
            return factory.createSet(xor(frame, inliningTarget, xorNode, selfSet.getDictStorage(), otherSet.getDictStorage()));
        }

        @Specialization
        static PBaseSet doItemsView(@SuppressWarnings("unused") VirtualFrame frame, PDictItemsView self, Object other,
                        @Bind("this") Node inliningTarget,
                        @Shared("constructSet") @Cached SetNodes.ConstructSetNode constructSetNode,
                        @Shared("xorNode") @Cached HashingStorageXor xorNode,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createSet(xor(frame, inliningTarget, xorNode, constructSetNode.executeWith(frame, self).getDictStorage(), constructSetNode.executeWith(frame, other).getDictStorage()));
        }
    }

    @Builtin(name = J___LE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class LessEqualNode extends DictViewRichcompareNode {
        @Override
        protected boolean reverse() {
            return false;
        }

        @Override
        protected boolean lenCompare(int lenSelf, int lenOther) {
            return lenSelf <= lenOther;
        }
    }

    @Builtin(name = J___GE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GreaterEqualNode extends DictViewRichcompareNode {
        @Override
        protected boolean reverse() {
            return true;
        }

        @Override
        protected boolean lenCompare(int lenSelf, int lenOther) {
            return lenSelf >= lenOther;
        }
    }

    @Builtin(name = J___LT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class LessThanNode extends DictViewRichcompareNode {
        @Override
        protected boolean reverse() {
            return false;
        }

        @Override
        protected boolean lenCompare(int lenSelf, int lenOther) {
            return lenSelf < lenOther;
        }
    }

    @Builtin(name = J___GT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GreaterThanNode extends DictViewRichcompareNode {
        @Override
        protected boolean reverse() {
            return true;
        }

        @Override
        protected boolean lenCompare(int lenSelf, int lenOther) {
            return lenSelf > lenOther;
        }
    }
}
