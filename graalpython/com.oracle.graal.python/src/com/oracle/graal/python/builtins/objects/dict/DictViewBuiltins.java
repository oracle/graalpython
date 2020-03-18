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
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDictView.PDictItemsView;
import com.oracle.graal.python.builtins.objects.dict.PDictView.PDictKeysView;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.set.PBaseSet;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.builtins.objects.set.SetNodes;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
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
        Object len(PDictView self) {
            return self.getWrappedDict().size();
        }
    }

    @Builtin(name = __ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object getKeysViewIter(PDictKeysView self) {
            return factory().createDictKeysIterator(self.getWrappedDict());
        }

        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()")
        Object getItemsViewIter(PDictItemsView self,
                        @Cached HashingCollectionNodes.GetDictStorageNode getStore,
                        @CachedLibrary("getStore.execute(self.getWrappedDict())") HashingStorageLibrary lib) {
            return factory().createDictItemsIterator(lib.entries(getStore.execute(self.getWrappedDict())).iterator());
        }
    }

    @Builtin(name = __CONTAINS__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ContainsNode extends PythonBinaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization(guards = "self.getWrappedDict().size() == 0")
        boolean containsEmpty(PDictView self, Object key) {
            return false;
        }

        @Specialization(limit = "1")
        boolean contains(VirtualFrame frame, PDictKeysView self, Object key,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @CachedLibrary("self.getWrappedDict().getDictStorage()") HashingStorageLibrary lib) {
            if (hasFrame.profile(frame != null)) {
                return lib.hasKeyWithState(self.getWrappedDict().getDictStorage(), key, PArguments.getThreadState(frame));
            } else {
                return lib.hasKey(self.getWrappedDict().getDictStorage(), key);
            }
        }

        @Specialization(limit = "1")
        boolean contains(VirtualFrame frame, PDictItemsView self, PTuple key,
                        @CachedLibrary("self.getWrappedDict().getDictStorage()") HashingStorageLibrary hlib,
                        @CachedLibrary(limit = "getCallSiteInlineCacheMaxDepth()") PythonObjectLibrary lib,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @Cached("createBinaryProfile()") ConditionProfile tupleLenProfile,
                        @Cached("create()") SequenceStorageNodes.LenNode lenNode,
                        @Cached("createNotNormalized()") SequenceStorageNodes.GetItemNode getTupleItemNode) {
            SequenceStorage tupleStorage = key.getSequenceStorage();
            if (tupleLenProfile.profile(lenNode.execute(tupleStorage) != 2)) {
                return false;
            }
            HashingStorage dictStorage = self.getWrappedDict().getDictStorage();
            Object value = hlib.getItemWithState(dictStorage, getTupleItemNode.execute(frame, tupleStorage, 0), PArguments.getThreadState(frame));
            if (value != null) {
                if (hasFrame.profile(frame != null)) {
                    return lib.equalsWithState(value, getTupleItemNode.execute(frame, tupleStorage, 1), lib, PArguments.getThreadState(frame));
                } else {
                    return lib.equals(value, getTupleItemNode.execute(frame, tupleStorage, 1), lib);
                }
            } else {
                return false;
            }
        }
    }

    /**
     * See CPython's dictobject.c all_contained_in. The semantics of dict view comparisons dictates
     * that we need to use iteration to compare them in the general case.
     */
    protected static class AllContainedInNode extends PNodeWithContext {
        @Child GetIteratorNode iter = GetIteratorNode.create();
        @Child GetNextNode next;
        @Child LookupAndCallBinaryNode contains;
        @Child CoerceToBooleanNode cast;
        @CompilationFinal IsBuiltinClassProfile stopProfile;

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
                stopProfile = IsBuiltinClassProfile.create();
            }
            return stopProfile;
        }

        public boolean execute(VirtualFrame frame, Object self, Object other) {
            Object iterator = iter.executeWith(frame, self);
            boolean ok = true;
            try {
                while (ok) {
                    Object item = getNext().execute(frame, iterator);
                    ok = getCast().executeBoolean(frame, getContains().executeObject(frame, other, item));
                }
            } catch (PException e) {
                e.expectStopIteration(getStopProfile());
            }
            return ok;
        }

        public static AllContainedInNode create() {
            return new AllContainedInNode();
        }
    }

    protected abstract static class DictViewRichcompareNode extends PythonBinaryBuiltinNode {
        protected boolean reverse() {
            throw new IllegalStateException("subclass should have implemented reverse");
        }

        protected boolean lenCompare(@SuppressWarnings("unused") int lenSelf, @SuppressWarnings("unused") int lenOther) {
            throw new IllegalStateException("subclass should have implemented lenCompare");
        }

        @Specialization(limit = "1")
        boolean doView(VirtualFrame frame, PDictView self, PBaseSet other,
                        @CachedLibrary("self.getWrappedDict().getDictStorage()") HashingStorageLibrary selflib,
                        @CachedLibrary("other.getDictStorage()") HashingStorageLibrary otherlib,
                        @Cached AllContainedInNode allContained) {
            int lenSelf = selflib.length(self.getWrappedDict().getDictStorage());
            int lenOther = otherlib.length(other.getDictStorage());
            return lenCompare(lenSelf, lenOther) && (reverse() ? allContained.execute(frame, other, self) : allContained.execute(frame, self, other));
        }

        @Specialization(limit = "1")
        boolean doView(VirtualFrame frame, PDictView self, PDictView other,
                        @CachedLibrary("self.getWrappedDict().getDictStorage()") HashingStorageLibrary selflib,
                        @CachedLibrary("other.getWrappedDict().getDictStorage()") HashingStorageLibrary otherlib,
                        @Cached AllContainedInNode allContained) {
            int lenSelf = selflib.length(self.getWrappedDict().getDictStorage());
            int lenOther = otherlib.length(other.getWrappedDict().getDictStorage());
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
            Object result = getEqNode().execute(frame, self, other);
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

        protected static HashingStorage diff(HashingStorageLibrary lib, HashingStorage left, HashingStorage right, VirtualFrame frame, ConditionProfile hasFrame) {
            if (hasFrame.profile(frame != null)) {
                return lib.diffWithState(left, right, PArguments.getThreadState(frame));
            } else {
                return lib.diff(left, right);
            }
        }

        @Specialization(limit = "1")
        PBaseSet doKeysView(@SuppressWarnings("unused") VirtualFrame frame, PDictKeysView self, PBaseSet other,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @CachedLibrary("self.getWrappedDict().getDictStorage()") HashingStorageLibrary lib) {
            HashingStorage storage = diff(lib, self.getWrappedDict().getDictStorage(), other.getDictStorage(), frame, hasFrame);
            return factory().createSet(storage);
        }

        @Specialization(limit = "1")
        PBaseSet doKeysView(@SuppressWarnings("unused") VirtualFrame frame, PDictKeysView self, PDictKeysView other,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @CachedLibrary("self.getWrappedDict().getDictStorage()") HashingStorageLibrary lib) {
            HashingStorage storage = diff(lib, self.getWrappedDict().getDictStorage(), other.getWrappedDict().getDictStorage(), frame, hasFrame);
            return factory().createSet(storage);
        }

        @Specialization
        PBaseSet doItemsView(VirtualFrame frame, PDictItemsView self, PBaseSet other,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @CachedLibrary(limit = "1") HashingStorageLibrary lib,
                        @Cached("create()") SetNodes.ConstructSetNode constructSetNode) {
            PSet selfSet = constructSetNode.executeWith(frame, self);
            HashingStorage storage = diff(lib, selfSet.getDictStorage(), other.getDictStorage(), frame, hasFrame);
            return factory().createSet(storage);
        }

        @Specialization
        PBaseSet doItemsView(VirtualFrame frame, PDictItemsView self, PDictItemsView other,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @CachedLibrary(limit = "1") HashingStorageLibrary lib,
                        @Cached("create()") SetNodes.ConstructSetNode constructSetNode) {
            PSet selfSet = constructSetNode.executeWith(frame, self);
            PSet otherSet = constructSetNode.executeWith(frame, other);
            HashingStorage storage = diff(lib, selfSet.getDictStorage(), otherSet.getDictStorage(), frame, hasFrame);
            return factory().createSet(storage);
        }

        @Specialization
        PBaseSet doItemsView(VirtualFrame frame, PDictKeysView self, PList other,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @CachedLibrary(limit = "1") HashingStorageLibrary lib,
                        @Cached("create()") SetNodes.ConstructSetNode constructSetNode) {
            PSet selfSet = constructSetNode.executeWith(frame, self);
            PSet otherSet = constructSetNode.executeWith(frame, other);
            HashingStorage storage = diff(lib, selfSet.getDictStorage(), otherSet.getDictStorage(), frame, hasFrame);
            return factory().createSet(storage);
        }
    }

    @Builtin(name = __AND__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class AndNode extends PythonBinaryBuiltinNode {

        protected static HashingStorage intersect(HashingStorageLibrary lib, HashingStorage left, HashingStorage right, VirtualFrame frame, ConditionProfile hasFrame) {
            if (hasFrame.profile(frame != null)) {
                return lib.intersectWithState(left, right, PArguments.getThreadState(frame));
            } else {
                return lib.intersect(left, right);
            }
        }

        @Specialization(limit = "1")
        PBaseSet doKeysView(VirtualFrame frame, PDictKeysView self, PBaseSet other,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @CachedLibrary("self.getWrappedDict().getDictStorage()") HashingStorageLibrary lib) {
            HashingStorage left = self.getWrappedDict().getDictStorage();
            HashingStorage right = other.getDictStorage();
            HashingStorage intersectedStorage = intersect(lib, left, right, frame, hasFrame);
            return factory().createSet(intersectedStorage);
        }

        @Specialization(limit = "1")
        PBaseSet doKeysView(VirtualFrame frame, PDictKeysView self, PDictKeysView other,
                        @Cached("createBinaryProfile()") ConditionProfile hasFrame,
                        @CachedLibrary("self.getWrappedDict().getDictStorage()") HashingStorageLibrary lib) {
            HashingStorage left = self.getWrappedDict().getDictStorage();
            HashingStorage right = other.getWrappedDict().getDictStorage();
            HashingStorage intersectedStorage = intersect(lib, left, right, frame, hasFrame);
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
            HashingStorage intersectedStorage = intersect(lib, left, right, frame, hasFrame);
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
            HashingStorage intersectedStorage = intersect(lib, left, right, frame, hasFrame);
            return factory().createSet(intersectedStorage);
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
                        @CachedLibrary("self.getWrappedDict().getDictStorage()") HashingStorageLibrary lib) {
            return factory().createSet(union(lib, self.getWrappedDict().getDictStorage(), other.getDictStorage()));
        }

        @Specialization(limit = "1")
        PBaseSet doKeysView(@SuppressWarnings("unused") VirtualFrame frame, PDictKeysView self, PDictKeysView other,
                        @CachedLibrary("self.getWrappedDict().getDictStorage()") HashingStorageLibrary lib) {
            return factory().createSet(union(lib, self.getWrappedDict().getDictStorage(), other.getWrappedDict().getDictStorage()));
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
    }

    @Builtin(name = __XOR__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class XorNode extends PythonBinaryBuiltinNode {

        protected static HashingStorage xor(HashingStorageLibrary lib, HashingStorage left, HashingStorage right) {
            return lib.xor(left, right);
        }

        @Specialization(limit = "1")
        PBaseSet doKeysView(@SuppressWarnings("unused") VirtualFrame frame, PDictKeysView self, PBaseSet other,
                        @CachedLibrary("self.getWrappedDict().getDictStorage()") HashingStorageLibrary lib) {
            return factory().createSet(xor(lib, self.getWrappedDict().getDictStorage(), other.getDictStorage()));
        }

        @Specialization(limit = "1")
        PBaseSet doKeysView(@SuppressWarnings("unused") VirtualFrame frame, PDictKeysView self, PDictKeysView other,
                        @CachedLibrary("self.getWrappedDict().getDictStorage()") HashingStorageLibrary lib) {
            return factory().createSet(xor(lib, self.getWrappedDict().getDictStorage(), other.getWrappedDict().getDictStorage()));
        }

        @Specialization
        PBaseSet doItemsView(VirtualFrame frame, PDictItemsView self, PBaseSet other,
                        @CachedLibrary(limit = "1") HashingStorageLibrary lib,
                        @Cached("create()") SetNodes.ConstructSetNode constructSetNode) {
            PSet selfSet = constructSetNode.executeWith(frame, self);
            return factory().createSet(xor(lib, selfSet.getDictStorage(), other.getDictStorage()));
        }

        @Specialization
        PBaseSet doItemsView(VirtualFrame frame, PDictItemsView self, PDictItemsView other,
                        @CachedLibrary(limit = "1") HashingStorageLibrary lib,
                        @Cached("create()") SetNodes.ConstructSetNode constructSetNode) {
            PSet selfSet = constructSetNode.executeWith(frame, self);
            PSet otherSet = constructSetNode.executeWith(frame, other);
            return factory().createSet(xor(lib, selfSet.getDictStorage(), otherSet.getDictStorage()));
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
