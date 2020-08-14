/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.nodes.SpecialMethodNames.ISDISJOINT;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__AND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__CONTAINS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LEN__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__OR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REVERSED__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__XOR__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.common.PHashingCollection;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDictView.PDictItemsView;
import com.oracle.graal.python.builtins.objects.dict.PDictView.PDictKeysView;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.set.PBaseSet;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.builtins.objects.set.SetNodes;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.control.GetIteratorExpressionNode.GetIteratorNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.expression.CoerceToBooleanNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PDictKeysView, PythonBuiltinClassType.PDictItemsView})
public final class DictViewBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return DictViewBuiltinsFactory.getFactories();
    }

    @Builtin(name = __LEN__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class LenNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object len(PDictView self,
                        @Cached HashingCollectionNodes.LenNode len) {
            return len.execute(self.getWrappedDict());
        }
    }

    @Builtin(name = __ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()")
        Object getKeysViewIter(PDictKeysView self,
                        @Cached HashingCollectionNodes.GetDictStorageNode getStore,
                        @CachedLibrary("getStore.execute(self.getWrappedDict())") HashingStorageLibrary lib) {
            PHashingCollection dict = self.getWrappedDict();
            HashingStorage storage = getStore.execute(dict);
            return factory().createDictKeyIterator(lib.keys(storage).iterator(), storage, lib.length(storage));
        }

        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()")
        Object getItemsViewIter(PDictItemsView self,
                        @Cached HashingCollectionNodes.GetDictStorageNode getStore,
                        @CachedLibrary("getStore.execute(self.getWrappedDict())") HashingStorageLibrary lib) {
            PHashingCollection dict = self.getWrappedDict();
            HashingStorage storage = getStore.execute(dict);
            return factory().createDictItemIterator(lib.entries(storage).iterator(), storage, lib.length(storage));
        }
    }

    @Builtin(name = __REVERSED__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReversedNode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()")
        Object getReversedKeysViewIter(PDictKeysView self,
                        @Cached HashingCollectionNodes.GetDictStorageNode getStore,
                        @CachedLibrary("getStore.execute(self.getWrappedDict())") HashingStorageLibrary lib) {
            PHashingCollection dict = self.getWrappedDict();
            HashingStorage storage = getStore.execute(dict);
            return factory().createDictReverseKeyIterator(lib.reverseKeys(storage).iterator(), storage, lib.length(storage));
        }

        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()")
        Object getReversedItemsViewIter(PDictItemsView self,
                        @Cached HashingCollectionNodes.GetDictStorageNode getStore,
                        @CachedLibrary("getStore.execute(self.getWrappedDict())") HashingStorageLibrary lib) {
            PHashingCollection dict = self.getWrappedDict();
            HashingStorage storage = getStore.execute(dict);
            return factory().createDictReverseItemIterator(lib.reverseEntries(storage).iterator(), storage, lib.length(storage));
        }
    }

    @Builtin(name = __CONTAINS__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ContainsNode extends PythonBinaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization(guards = "len.execute(self.getWrappedDict()) == 0")
        boolean containsEmpty(PDictView self, Object key,
                        @Cached HashingCollectionNodes.LenNode len) {
            return false;
        }

        @Specialization(limit = "1")
        boolean contains(VirtualFrame frame, PDictKeysView self, Object key,
                        @Cached HashingCollectionNodes.GetDictStorageNode getSelfStorage,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @CachedLibrary("getSelfStorage.execute(self.getWrappedDict())") HashingStorageLibrary lib) {
            return lib.hasKeyWithFrame(getSelfStorage.execute(self.getWrappedDict()), key, hasFrame, frame);
        }

        @Specialization(limit = "1")
        boolean contains(VirtualFrame frame, PDictItemsView self, PTuple key,
                        @Cached HashingCollectionNodes.GetDictStorageNode getSelfStorage,
                        @CachedLibrary("getSelfStorage.execute(self.getWrappedDict())") HashingStorageLibrary hlib,
                        @CachedLibrary(limit = "getCallSiteInlineCacheMaxDepth()") PythonObjectLibrary lib,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @Cached("createBinaryProfile()") ConditionProfile tupleLenProfile,
                        @Cached("create()") SequenceStorageNodes.LenNode lenNode,
                        @Cached("createNotNormalized()") SequenceStorageNodes.GetItemNode getTupleItemNode) {
            SequenceStorage tupleStorage = key.getSequenceStorage();
            if (tupleLenProfile.profile(lenNode.execute(tupleStorage) != 2)) {
                return false;
            }
            HashingStorage dictStorage = getSelfStorage.execute(self.getWrappedDict());
            Object value = hlib.getItemWithFrame(dictStorage, getTupleItemNode.execute(frame, tupleStorage, 0), hasFrame, frame);
            if (value != null) {
                return lib.equalsWithFrame(value, getTupleItemNode.execute(frame, tupleStorage, 1), lib, frame);
            } else {
                return false;
            }
        }

        protected static boolean isFallback(Object self, Object key) {
            return !(self instanceof PDictView || self instanceof PDictKeysView || self instanceof PDictItemsView) || (self instanceof PDictItemsView && !(key instanceof PTuple));
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "isFallback(self, key)")
        boolean contains(Object self, Object key) {
            return false;
        }

    }

    @Builtin(name = ISDISJOINT, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class IsDisjointNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = {"self == other"})
        boolean disjointSame(PDictView self, @SuppressWarnings("unused") PDictView other,
                        @Cached HashingCollectionNodes.LenNode len) {
            return len.execute(self.getWrappedDict()) == 0;
        }

        @Specialization(guards = {"self != other"}, limit = "1")
        boolean disjointNotSame(VirtualFrame frame, PDictView self, PDictView other,
                        @Cached HashingCollectionNodes.LenNode len,
                        @Cached ConditionProfile sizeProfile,
                        @CachedLibrary("other") PythonObjectLibrary lib,
                        @Cached("create(false)") ContainedInNode contained) {
            return disjointImpl(frame, self, other, len, sizeProfile, lib, contained);
        }

        @Specialization(limit = "1")
        boolean disjoint(VirtualFrame frame, PDictView self, PBaseSet other,
                        @Cached HashingCollectionNodes.LenNode len,
                        @Cached ConditionProfile sizeProfile,
                        @CachedLibrary("other") PythonObjectLibrary lib,
                        @Cached("create(false)") ContainedInNode contained) {
            return disjointImpl(frame, self, other, len, sizeProfile, lib, contained);
        }

        private static boolean disjointImpl(VirtualFrame frame, PDictView self, Object other, HashingCollectionNodes.LenNode len, ConditionProfile sizeProfile, PythonObjectLibrary lib,
                        ContainedInNode contained) {
            if (sizeProfile.profile(len.execute(self.getWrappedDict()) <= lib.length(other))) {
                return !contained.execute(frame, self, other);
            } else {
                return !contained.execute(frame, other, self);
            }
        }

        @Specialization(guards = {"lib.isIterable(other)", "!isAnySet(other)", "!isDictView(other)"}, limit = "1")
        boolean disjoint(VirtualFrame frame, PDictView self, Object other,
                        @SuppressWarnings("unused") @CachedLibrary("other") PythonObjectLibrary lib,
                        @Cached("create(false)") ContainedInNode contained) {
            return !contained.execute(frame, other, self);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!lib.isIterable(other)", limit = "1")
        boolean disjoint(PDictView self, Object other,
                        @CachedLibrary("other") PythonObjectLibrary lib) {
            throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.OBJ_NOT_ITERABLE, other);
        }
    }

    /**
     * See CPython's dictobject.c all_contained_in and dictviews_isdisjoint. The semantics of dict
     * view comparisons dictates that we need to use iteration to compare them in the general case.
     */
    protected static class ContainedInNode extends PNodeWithContext {
        @Child GetIteratorNode iter = GetIteratorNode.create();
        @Child GetNextNode next;
        @Child LookupAndCallBinaryNode contains;
        @Child CoerceToBooleanNode cast;
        @CompilationFinal IsBuiltinClassProfile stopProfile;
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
                contains = insert(LookupAndCallBinaryNode.create(SpecialMethodNames.__CONTAINS__));
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

        private IsBuiltinClassProfile getStopProfile() {
            if (stopProfile == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                stopProfile = insert(IsBuiltinClassProfile.create());
            }
            return stopProfile;
        }

        public boolean execute(VirtualFrame frame, Object self, Object other) {
            Object iterator = iter.executeWith(frame, self);
            boolean ok = checkAll;
            try {
                while (checkAll && ok || !checkAll && !ok) {
                    Object item = getNext().execute(frame, iterator);
                    ok = getCast().executeBoolean(frame, getContains().executeObject(frame, other, item));
                }
            } catch (PException e) {
                e.expectStopIteration(getStopProfile());
            }
            return ok;
        }

        static ContainedInNode create() {
            return new ContainedInNode(true);
        }

        static ContainedInNode create(boolean all) {
            return new ContainedInNode(all);
        }
    }

    protected abstract static class DictViewRichcompareNode extends PythonBinaryBuiltinNode {
        protected boolean reverse() {
            CompilerAsserts.neverPartOfCompilation();
            throw new IllegalStateException("subclass should have implemented reverse");
        }

        protected boolean lenCompare(@SuppressWarnings("unused") int lenSelf, @SuppressWarnings("unused") int lenOther) {
            CompilerAsserts.neverPartOfCompilation();
            throw new IllegalStateException("subclass should have implemented lenCompare");
        }

        @Specialization(limit = "1")
        boolean doView(VirtualFrame frame, PDictView self, PBaseSet other,
                        @Cached HashingCollectionNodes.GetDictStorageNode getSelfStorage,
                        @CachedLibrary("getSelfStorage.execute(self.getWrappedDict())") HashingStorageLibrary selflib,
                        @CachedLibrary("other.getDictStorage()") HashingStorageLibrary otherlib,
                        @Cached ContainedInNode allContained) {
            int lenSelf = selflib.length(getSelfStorage.execute(self.getWrappedDict()));
            int lenOther = otherlib.length(other.getDictStorage());
            return lenCompare(lenSelf, lenOther) && (reverse() ? allContained.execute(frame, other, self) : allContained.execute(frame, self, other));
        }

        @Specialization(limit = "1")
        boolean doView(VirtualFrame frame, PDictView self, PDictView other,
                        @Cached HashingCollectionNodes.GetDictStorageNode getSelfStorage,
                        @Cached HashingCollectionNodes.GetDictStorageNode getOtherStorage,
                        @CachedLibrary("getSelfStorage.execute(self.getWrappedDict())") HashingStorageLibrary selflib,
                        @CachedLibrary("getOtherStorage.execute(other.getWrappedDict())") HashingStorageLibrary otherlib,
                        @Cached ContainedInNode allContained) {
            int lenSelf = selflib.length(getSelfStorage.execute(self.getWrappedDict()));
            int lenOther = otherlib.length(getOtherStorage.execute(other.getWrappedDict()));
            return lenCompare(lenSelf, lenOther) && (reverse() ? allContained.execute(frame, other, self) : allContained.execute(frame, self, other));
        }

        @Fallback
        PNotImplemented wrongTypes(@SuppressWarnings("unused") Object self, @SuppressWarnings("unused") Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __EQ__, minNumOfPositionalArgs = 2)
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

    @Builtin(name = __NE__, minNumOfPositionalArgs = 2)
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
            Object result = getEqNode().call(frame, self, other);
            if (result == PNotImplemented.NOT_IMPLEMENTED) {
                return result;
            } else {
                assert result instanceof Boolean;
                return !((Boolean) result);
            }
        }
    }

    @Builtin(name = __SUB__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class SubNode extends PythonBinaryBuiltinNode {

        @Specialization(limit = "1")
        PBaseSet doKeysView(@SuppressWarnings("unused") VirtualFrame frame, PDictKeysView self, PBaseSet other,
                        @Cached HashingCollectionNodes.GetDictStorageNode getStorage,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @CachedLibrary("getStorage.execute(self.getWrappedDict())") HashingStorageLibrary lib) {
            HashingStorage storage = lib.diffWithFrame(getStorage.execute(self.getWrappedDict()), other.getDictStorage(), hasFrame, frame);
            return factory().createSet(storage);
        }

        @Specialization(limit = "1")
        PBaseSet doKeysView(@SuppressWarnings("unused") VirtualFrame frame, PDictKeysView self, PDictKeysView other,
                        @Cached HashingCollectionNodes.GetDictStorageNode getSelfStorage,
                        @Cached HashingCollectionNodes.GetDictStorageNode getOtherStorage,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @CachedLibrary("getSelfStorage.execute(self.getWrappedDict())") HashingStorageLibrary lib) {
            HashingStorage storage = lib.diffWithFrame(getSelfStorage.execute(self.getWrappedDict()), getOtherStorage.execute(other.getWrappedDict()), hasFrame, frame);
            return factory().createSet(storage);
        }

        @Specialization(guards = "libOther.isIterable(other)", limit = "1")
        PBaseSet doKeysView(VirtualFrame frame, PDictKeysView self, Object other,
                        @Cached HashingCollectionNodes.GetDictStorageNode getSelfStorage,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @SuppressWarnings("unused") @CachedLibrary("other") PythonObjectLibrary libOther,
                        @Cached("create()") SetNodes.ConstructSetNode constructSetNode,
                        @CachedLibrary("getSelfStorage.execute(self.getWrappedDict())") HashingStorageLibrary lib) {
            HashingStorage left = getSelfStorage.execute(self.getWrappedDict());
            HashingStorage right = constructSetNode.executeWith(frame, other).getDictStorage();
            HashingStorage storage = lib.diffWithFrame(left, right, hasFrame, frame);
            return factory().createSet(storage);
        }

        @Specialization
        PBaseSet doItemsView(VirtualFrame frame, PDictItemsView self, PBaseSet other,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @CachedLibrary(limit = "1") HashingStorageLibrary lib,
                        @Cached("create()") SetNodes.ConstructSetNode constructSetNode) {
            PSet selfSet = constructSetNode.executeWith(frame, self);
            HashingStorage storage = lib.diffWithFrame(selfSet.getDictStorage(), other.getDictStorage(), hasFrame, frame);
            return factory().createSet(storage);
        }

        @Specialization
        PBaseSet doNotIterable(VirtualFrame frame, PDictItemsView self, PDictItemsView other,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @CachedLibrary(limit = "1") HashingStorageLibrary lib,
                        @Cached("create()") SetNodes.ConstructSetNode constructSetNode) {
            PSet selfSet = constructSetNode.executeWith(frame, self);
            PSet otherSet = constructSetNode.executeWith(frame, other);
            HashingStorage storage = lib.diffWithFrame(selfSet.getDictStorage(), otherSet.getDictStorage(), hasFrame, frame);
            return factory().createSet(storage);
        }

        @Specialization(guards = "libOther.isIterable(other)", limit = "1")
        PBaseSet doItemsView(VirtualFrame frame, PDictItemsView self, Object other,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @SuppressWarnings("unused") @CachedLibrary("other") PythonObjectLibrary libOther,
                        @Cached("create()") SetNodes.ConstructSetNode constructSetNode,
                        @CachedLibrary(limit = "1") HashingStorageLibrary lib) {
            HashingStorage left = constructSetNode.executeWith(frame, self).getDictStorage();
            HashingStorage right = constructSetNode.executeWith(frame, other).getDictStorage();
            HashingStorage storage = lib.diffWithFrame(left, right, hasFrame, frame);
            return factory().createSet(storage);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!isDictKeysView(other)", "!isDictItemsView(other)", "!lib.isIterable(other)"}, limit = "1")
        PBaseSet doNotIterable(VirtualFrame frame, PDictView self, Object other,
                        @CachedLibrary("other") PythonObjectLibrary lib) {
            throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.OBJ_NOT_ITERABLE, other);
        }
    }

    @Builtin(name = __AND__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class AndNode extends PythonBinaryBuiltinNode {

        @Specialization(limit = "1")
        PBaseSet doKeysView(VirtualFrame frame, PDictKeysView self, PBaseSet other,
                        @Cached HashingCollectionNodes.GetDictStorageNode getSelfStorage,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @CachedLibrary("getSelfStorage.execute(self.getWrappedDict())") HashingStorageLibrary lib) {
            HashingStorage left = getSelfStorage.execute(self.getWrappedDict());
            HashingStorage right = other.getDictStorage();
            HashingStorage intersectedStorage = lib.intersectWithFrame(left, right, hasFrame, frame);
            return factory().createSet(intersectedStorage);
        }

        @Specialization(limit = "1")
        PBaseSet doKeysView(VirtualFrame frame, PDictKeysView self, PDictKeysView other,
                        @Cached HashingCollectionNodes.GetDictStorageNode getSelfStorage,
                        @Cached HashingCollectionNodes.GetDictStorageNode getOtherStorage,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @CachedLibrary("getSelfStorage.execute(self.getWrappedDict())") HashingStorageLibrary lib) {
            HashingStorage left = getSelfStorage.execute(self.getWrappedDict());
            HashingStorage right = getOtherStorage.execute(other.getWrappedDict());
            HashingStorage intersectedStorage = lib.intersectWithFrame(left, right, hasFrame, frame);
            return factory().createSet(intersectedStorage);
        }

        @Specialization(guards = "libOther.isIterable(other)", limit = "1")
        PBaseSet doKeysView(VirtualFrame frame, PDictKeysView self, Object other,
                        @Cached HashingCollectionNodes.GetDictStorageNode getSelfStorage,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @SuppressWarnings("unused") @CachedLibrary("other") PythonObjectLibrary libOther,
                        @Cached("create()") SetNodes.ConstructSetNode constructSetNode,
                        @CachedLibrary("getSelfStorage.execute(self.getWrappedDict())") HashingStorageLibrary lib) {
            HashingStorage left = getSelfStorage.execute(self.getWrappedDict());
            HashingStorage right = constructSetNode.executeWith(frame, other).getDictStorage();
            HashingStorage intersectedStorage = lib.intersectWithFrame(left, right, hasFrame, frame);
            return factory().createSet(intersectedStorage);
        }

        @Specialization
        PBaseSet doItemsView(VirtualFrame frame, PDictItemsView self, PBaseSet other,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @CachedLibrary(limit = "3") HashingStorageLibrary lib,
                        @Cached("create()") SetNodes.ConstructSetNode constructSetNode) {
            PSet selfSet = constructSetNode.executeWith(frame, self);
            HashingStorage left = selfSet.getDictStorage();
            HashingStorage right = other.getDictStorage();
            HashingStorage intersectedStorage = lib.intersectWithFrame(left, right, hasFrame, frame);
            return factory().createSet(intersectedStorage);
        }

        @Specialization
        PBaseSet doItemsView(VirtualFrame frame, PDictItemsView self, PDictItemsView other,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @CachedLibrary(limit = "3") HashingStorageLibrary lib,
                        @Cached("create()") SetNodes.ConstructSetNode constructSetNode) {
            PSet selfSet = constructSetNode.executeWith(frame, self);
            PSet otherSet = constructSetNode.executeWith(frame, other);
            HashingStorage left = selfSet.getDictStorage();
            HashingStorage right = otherSet.getDictStorage();
            HashingStorage intersectedStorage = lib.intersectWithFrame(left, right, hasFrame, frame);
            return factory().createSet(intersectedStorage);
        }

        @Specialization(guards = "libOther.isIterable(other)", limit = "1")
        PBaseSet doItemsView(VirtualFrame frame, PDictItemsView self, Object other,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @SuppressWarnings("unused") @CachedLibrary("other") PythonObjectLibrary libOther,
                        @Cached("create()") SetNodes.ConstructSetNode constructSetNode,
                        @CachedLibrary(limit = "1") HashingStorageLibrary lib) {
            HashingStorage left = constructSetNode.executeWith(frame, self).getDictStorage();
            HashingStorage right = constructSetNode.executeWith(frame, other).getDictStorage();
            HashingStorage intersectedStorage = lib.intersectWithFrame(left, right, hasFrame, frame);
            return factory().createSet(intersectedStorage);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!isDictKeysView(other)", "!isDictItemsView(other)",
                        "!lib.isIterable(other)"}, limit = "1")
        PBaseSet doNotIterable(VirtualFrame frame, PDictView self, Object other,
                        @CachedLibrary("other") PythonObjectLibrary lib) {
            throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.OBJ_NOT_ITERABLE, other);
        }
    }

    @Builtin(name = __OR__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class OrNode extends PythonBinaryBuiltinNode {

        protected static HashingStorage union(HashingStorageLibrary lib, HashingStorage left, HashingStorage right) {
            return lib.union(left, right);
        }

        @Specialization(limit = "1")
        PBaseSet doKeysView(@SuppressWarnings("unused") VirtualFrame frame, PDictKeysView self, PBaseSet other,
                        @Cached HashingCollectionNodes.GetDictStorageNode getSelfStorage,
                        @CachedLibrary("getSelfStorage.execute(self.getWrappedDict())") HashingStorageLibrary lib) {
            return factory().createSet(union(lib, getSelfStorage.execute(self.getWrappedDict()), other.getDictStorage()));
        }

        @Specialization(limit = "1")
        PBaseSet doKeysView(@SuppressWarnings("unused") VirtualFrame frame, PDictKeysView self, PDictKeysView other,
                        @Cached HashingCollectionNodes.GetDictStorageNode getSelfStorage,
                        @Cached HashingCollectionNodes.GetDictStorageNode getOtherStorage,
                        @CachedLibrary("getSelfStorage.execute(self.getWrappedDict())") HashingStorageLibrary lib) {
            return factory().createSet(union(lib, getSelfStorage.execute(self.getWrappedDict()), getOtherStorage.execute(other.getWrappedDict())));
        }

        @Specialization(guards = "libOther.isIterable(other)", limit = "1")
        PBaseSet doKeysView(@SuppressWarnings("unused") VirtualFrame frame, PDictKeysView self, Object other,
                        @Cached HashingCollectionNodes.GetDictStorageNode getSelfStorage,
                        @Cached("create()") SetNodes.ConstructSetNode constructSetNode,
                        @SuppressWarnings("unused") @CachedLibrary("other") PythonObjectLibrary libOther,
                        @CachedLibrary("getSelfStorage.execute(self.getWrappedDict())") HashingStorageLibrary lib) {
            return factory().createSet(union(lib, getSelfStorage.execute(self.getWrappedDict()), constructSetNode.executeWith(frame, other).getDictStorage()));
        }

        @Specialization
        PBaseSet doItemsView(VirtualFrame frame, PDictItemsView self, PBaseSet other,
                        @CachedLibrary(limit = "1") HashingStorageLibrary lib,
                        @Cached("create()") SetNodes.ConstructSetNode constructSetNode) {
            PSet selfSet = constructSetNode.executeWith(frame, self);
            return factory().createSet(union(lib, selfSet.getDictStorage(), other.getDictStorage()));
        }

        @Specialization
        PBaseSet doItemsView(VirtualFrame frame, PDictItemsView self, PDictItemsView other,
                        @CachedLibrary(limit = "1") HashingStorageLibrary lib,
                        @Cached("create()") SetNodes.ConstructSetNode constructSetNode) {
            PSet selfSet = constructSetNode.executeWith(frame, self);
            PSet otherSet = constructSetNode.executeWith(frame, other);
            return factory().createSet(union(lib, selfSet.getDictStorage(), otherSet.getDictStorage()));
        }

        @Specialization(guards = "libOther.isIterable(other)", limit = "1")
        PBaseSet doItemsView(VirtualFrame frame, PDictItemsView self, Object other,
                        @SuppressWarnings("unused") @CachedLibrary("other") PythonObjectLibrary libOther,
                        @Cached("create()") SetNodes.ConstructSetNode constructSetNode,
                        @CachedLibrary(limit = "1") HashingStorageLibrary lib) {
            return factory().createSet(union(lib, constructSetNode.executeWith(frame, self).getDictStorage(), constructSetNode.executeWith(frame, other).getDictStorage()));
        }

        @Specialization(guards = {"!isDictKeysView(other)", "!isDictItemsView(other)", "!lib.isIterable(other)"}, limit = "1")
        PBaseSet doNotIterable(@SuppressWarnings("unused") VirtualFrame frame, @SuppressWarnings("unused") PDictView self, Object other,
                        @SuppressWarnings("unused") @CachedLibrary("other") PythonObjectLibrary lib) {
            throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.OBJ_NOT_ITERABLE, other);
        }
    }

    @Builtin(name = __XOR__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class XorNode extends PythonBinaryBuiltinNode {

        protected static HashingStorage xor(HashingStorageLibrary lib, HashingStorage left, HashingStorage right) {
            return lib.xor(left, right);
        }

        @Specialization(limit = "1")
        PBaseSet doKeysView(@SuppressWarnings("unused") VirtualFrame frame, PDictKeysView self, PBaseSet other,
                        @Cached HashingCollectionNodes.GetDictStorageNode getSelfStorage,
                        @CachedLibrary("getSelfStorage.execute(self.getWrappedDict())") HashingStorageLibrary lib) {
            return factory().createSet(xor(lib, getSelfStorage.execute(self.getWrappedDict()), other.getDictStorage()));
        }

        @Specialization(limit = "1")
        PBaseSet doKeysView(@SuppressWarnings("unused") VirtualFrame frame, PDictKeysView self, PDictKeysView other,
                        @Cached HashingCollectionNodes.GetDictStorageNode getSelfStorage,
                        @Cached HashingCollectionNodes.GetDictStorageNode getOtherStorage,
                        @CachedLibrary("getSelfStorage.execute(self.getWrappedDict())") HashingStorageLibrary lib) {
            return factory().createSet(xor(lib, getSelfStorage.execute(self.getWrappedDict()), getOtherStorage.execute(other.getWrappedDict())));
        }

        @Specialization(guards = "libOther.isIterable(other)", limit = "1")
        PBaseSet doKeysView(@SuppressWarnings("unused") VirtualFrame frame, PDictKeysView self, Object other,
                        @Cached HashingCollectionNodes.GetDictStorageNode getSelfStorage,
                        @Cached("create()") SetNodes.ConstructSetNode constructSetNode,
                        @SuppressWarnings("unused") @CachedLibrary("other") PythonObjectLibrary libOther,
                        @CachedLibrary("getSelfStorage.execute(self.getWrappedDict())") HashingStorageLibrary lib) {
            return factory().createSet(xor(lib, getSelfStorage.execute(self.getWrappedDict()), constructSetNode.executeWith(frame, other).getDictStorage()));
        }

        @Specialization
        PBaseSet doItemsView(@SuppressWarnings("unused") VirtualFrame frame, PDictItemsView self, PBaseSet other,
                        @CachedLibrary(limit = "1") HashingStorageLibrary lib,
                        @Cached("create()") SetNodes.ConstructSetNode constructSetNode) {
            PSet selfSet = constructSetNode.executeWith(frame, self);
            return factory().createSet(xor(lib, selfSet.getDictStorage(), other.getDictStorage()));
        }

        @Specialization
        PBaseSet doItemsView(@SuppressWarnings("unused") VirtualFrame frame, PDictItemsView self, PDictItemsView other,
                        @CachedLibrary(limit = "1") HashingStorageLibrary lib,
                        @Cached("create()") SetNodes.ConstructSetNode constructSetNode) {
            PSet selfSet = constructSetNode.executeWith(frame, self);
            PSet otherSet = constructSetNode.executeWith(frame, other);
            return factory().createSet(xor(lib, selfSet.getDictStorage(), otherSet.getDictStorage()));
        }

        @Specialization(guards = "libOther.isIterable(other)", limit = "1")
        PBaseSet doItemsView(@SuppressWarnings("unused") VirtualFrame frame, PDictItemsView self, Object other,
                        @SuppressWarnings("unused") @CachedLibrary("other") PythonObjectLibrary libOther,
                        @Cached("create()") SetNodes.ConstructSetNode constructSetNode,
                        @CachedLibrary(limit = "1") HashingStorageLibrary lib) {
            return factory().createSet(xor(lib, constructSetNode.executeWith(frame, self).getDictStorage(), constructSetNode.executeWith(frame, other).getDictStorage()));
        }

        @Specialization(guards = {"!isDictKeysView(other)", "!isDictItemsView(other)", "!lib.isIterable(other)"}, limit = "1")
        PBaseSet doNotIterable(@SuppressWarnings("unused") VirtualFrame frame, @SuppressWarnings("unused") PDictView self, Object other,
                        @SuppressWarnings("unused") @CachedLibrary("other") PythonObjectLibrary lib) {
            throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.OBJ_NOT_ITERABLE, other);
        }
    }

    @Builtin(name = __LE__, minNumOfPositionalArgs = 2)
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

    @Builtin(name = __GE__, minNumOfPositionalArgs = 2)
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

    @Builtin(name = __LT__, minNumOfPositionalArgs = 2)
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

    @Builtin(name = __GT__, minNumOfPositionalArgs = 2)
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
