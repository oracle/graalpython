/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
 * Copyright (c) 2014, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.builtins.objects.set;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__AND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__CONTAINS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LEN__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SUB__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.common.EconomicMapStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage.Equivalence;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.PythonEquivalence;
import com.oracle.graal.python.builtins.objects.common.PHashingCollection;
import com.oracle.graal.python.builtins.objects.set.FrozenSetBuiltinsFactory.BinaryUnionNodeGen;
import com.oracle.graal.python.nodes.PBaseNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.control.GetIteratorNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PFrozenSet, PythonBuiltinClassType.PSet})
public final class FrozenSetBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return FrozenSetBuiltinsFactory.getFactories();
    }

    @Builtin(name = __ITER__, fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        public Object iter(PBaseSet self) {
            return factory().createBaseSetIterator(self);
        }
    }

    @Builtin(name = __LEN__, fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class LenNode extends PythonUnaryBuiltinNode {
        @Specialization
        public int len(PBaseSet self) {
            return self.size();
        }
    }

    @Builtin(name = __REDUCE__, fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReduceNode extends PythonUnaryBuiltinNode {
        @Specialization
        public Object iter(PBaseSet self) {
            Object[] reduceTuple = new Object[]{PNone.NONE, PNone.NONE, PNone.NONE};
            reduceTuple[0] = self.getPythonClass();
            reduceTuple[1] = factory().createTuple(new Object[]{factory().createList(self.getDictStorage().keysAsArray())});
            return factory().createTuple(reduceTuple);
        }
    }

    @Builtin(name = __EQ__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class EqNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean doSetSameType(PBaseSet self, PBaseSet other,
                        @Cached("create()") HashingStorageNodes.KeysEqualsNode equalsNode) {
            return equalsNode.execute(self.getDictStorage(), other.getDictStorage());
        }

        @Fallback
        @SuppressWarnings("unused")
        PNotImplemented doGeneric(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __LE__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class LeNode extends PythonBinaryBuiltinNode {
        @Child private HashingStorageNodes.ContainsKeyNode containsKeyNode = HashingStorageNodes.ContainsKeyNode.create();

        @Specialization
        Object run(PBaseSet self, PBaseSet other) {
            if (self.size() > other.size()) {
                return false;
            }

            for (Object value : self.values()) {
                if (!containsKeyNode.execute(other.getDictStorage(), value)) {
                    return false;
                }
            }

            return true;
        }
    }

    @Builtin(name = __AND__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class AndNode extends PythonBinaryBuiltinNode {
        @Child private HashingStorageNodes.IntersectNode intersectNode;
        @Child private HashingStorageNodes.SetItemNode setItemNode;

        private HashingStorageNodes.SetItemNode getSetItemNode() {
            if (setItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                setItemNode = insert(HashingStorageNodes.SetItemNode.create());
            }
            return setItemNode;
        }

        @TruffleBoundary
        private HashingStorage getStringAsHashingStorage(String str) {
            HashingStorage storage = EconomicMapStorage.create(str.length(), true);
            for (int i = 0; i < str.length(); i++) {
                String key = String.valueOf(str.charAt(i));
                getSetItemNode().execute(storage, key, PNone.NO_VALUE);
            }
            return storage;
        }

        @Specialization
        PBaseSet doPBaseSet(PSet left, String right) {
            return factory().createSet(getIntersectNode().execute(left.getDictStorage(), getStringAsHashingStorage(right)));
        }

        @Specialization
        PBaseSet doPBaseSet(PFrozenSet left, String right) {
            return factory().createFrozenSet(getIntersectNode().execute(left.getDictStorage(), getStringAsHashingStorage(right)));
        }

        private HashingStorageNodes.IntersectNode getIntersectNode() {
            if (intersectNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                intersectNode = insert(HashingStorageNodes.IntersectNode.create());
            }
            return intersectNode;
        }

        @Specialization
        PBaseSet doPBaseSet(PSet left, PBaseSet right) {
            HashingStorage intersectedStorage = getIntersectNode().execute(left.getDictStorage(), right.getDictStorage());
            return factory().createSet(intersectedStorage);
        }

        @Specialization
        PBaseSet doPBaseSet(PFrozenSet left, PBaseSet right) {
            HashingStorage intersectedStorage = getIntersectNode().execute(left.getDictStorage(), right.getDictStorage());
            return factory().createFrozenSet(intersectedStorage);
        }

        @Fallback
        Object doAnd(Object self, Object other) {
            throw raise(PythonErrorType.TypeError, "unsupported operand type(s) for &=: '%p' and '%p'", self, other);
        }
    }

    @Builtin(name = __SUB__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class SubNode extends PythonBinaryBuiltinNode {
        @Child private HashingStorageNodes.DiffNode diffNode;

        private HashingStorageNodes.DiffNode getDiffNode() {
            if (diffNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                diffNode = insert(HashingStorageNodes.DiffNode.create());
            }
            return diffNode;
        }

        @Specialization
        PBaseSet doPBaseSet(PSet left, PBaseSet right) {
            HashingStorage storage = getDiffNode().execute(left.getDictStorage(), right.getDictStorage());
            return factory().createSet(storage);
        }

        @Specialization
        PBaseSet doPBaseSet(PFrozenSet left, PBaseSet right) {
            HashingStorage storage = getDiffNode().execute(left.getDictStorage(), right.getDictStorage());
            return factory().createSet(storage);
        }

        @Fallback
        Object doSub(Object self, Object other) {
            throw raise(PythonErrorType.TypeError, "unsupported operand type(s) for -: %p and %p", self, other);
        }
    }

    @Builtin(name = __CONTAINS__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class ContainsNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean contains(PBaseSet self, Object key,
                        @Cached("create()") HashingStorageNodes.ContainsKeyNode containsKeyNode) {
            return containsKeyNode.execute(self.getDictStorage(), key);
        }
    }

    @Builtin(name = "union", minNumOfPositionalArgs = 1, takesVarArgs = true)
    @GenerateNodeFactory
    abstract static class UnionNode extends PythonBuiltinNode {

        @Child private BinaryUnionNode binaryUnionNode;

        @CompilationFinal private ValueProfile setTypeProfile;

        private BinaryUnionNode getBinaryUnionNode() {
            if (binaryUnionNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                binaryUnionNode = insert(BinaryUnionNode.create());
            }
            return binaryUnionNode;
        }

        private ValueProfile getSetTypeProfile() {
            if (setTypeProfile == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                setTypeProfile = ValueProfile.createClassProfile();
            }
            return setTypeProfile;
        }

        @Specialization(guards = {"args.length == len", "args.length < 32"}, limit = "3")
        PBaseSet doCached(PBaseSet self, Object[] args,
                        @Cached("args.length") int len,
                        @Cached("create()") HashingStorageNodes.CopyNode copyNode) {
            PBaseSet result = create(self, copyNode.execute(self.getDictStorage()));
            for (int i = 0; i < len; i++) {
                getBinaryUnionNode().execute(result, result.getDictStorage(), args[i]);
            }
            return result;
        }

        @Specialization(replaces = "doCached")
        PBaseSet doGeneric(PBaseSet self, Object[] args,
                        @Cached("create()") HashingStorageNodes.CopyNode copyNode) {
            PBaseSet result = create(self, copyNode.execute(self.getDictStorage()));
            for (int i = 0; i < args.length; i++) {
                getBinaryUnionNode().execute(result, result.getDictStorage(), args[i]);
            }
            return result;
        }

        private PBaseSet create(PBaseSet left, HashingStorage storage) {
            if (getSetTypeProfile().profile(left) instanceof PFrozenSet) {
                return factory().createFrozenSet(storage);
            }
            return factory().createSet(storage);
        }
    }

    abstract static class BinaryUnionNode extends PBaseNode {
        @Child private Equivalence equivalenceNode;

        public abstract PBaseSet execute(PBaseSet container, HashingStorage left, Object right);

        protected Equivalence getEquivalence() {
            if (equivalenceNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                equivalenceNode = insert(new PythonEquivalence());
            }
            return equivalenceNode;
        }

        @Specialization
        PBaseSet doHashingCollection(PBaseSet container, EconomicMapStorage selfStorage, PHashingCollection other) {
            for (Object key : other.getDictStorage().keys()) {
                selfStorage.setItem(key, PNone.NO_VALUE, getEquivalence());
            }
            return container;
        }

        @Specialization
        PBaseSet doIterable(PBaseSet container, HashingStorage dictStorage, Object iterable,
                        @Cached("create()") GetIteratorNode getIteratorNode,
                        @Cached("create()") GetNextNode next,
                        @Cached("createBinaryProfile()") ConditionProfile errorProfile,
                        @Cached("create()") HashingStorageNodes.SetItemNode setItemNode) {

            HashingStorage curStorage = dictStorage;
            Object iterator = getIteratorNode.executeWith(iterable);
            while (true) {
                Object value;
                try {
                    value = next.execute(iterator);
                } catch (PException e) {
                    e.expectStopIteration(getCore(), errorProfile);
                    container.setDictStorage(curStorage);
                    return container;
                }
                curStorage = setItemNode.execute(curStorage, value, PNone.NO_VALUE);
            }
        }

        public static BinaryUnionNode create() {
            return BinaryUnionNodeGen.create();
        }
    }

    @Builtin(name = "issubset", fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class IsSubsetNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean isSubSet(PBaseSet self, PBaseSet other,
                        @Cached("create()") HashingStorageNodes.KeysIsSubsetNode isSubsetNode) {
            return isSubsetNode.execute(self.getDictStorage(), other.getDictStorage());
        }

        @Specialization
        boolean isSubSet(PBaseSet self, String other,
                        @Cached("create()") SetNodes.ConstructSetNode constructSetNode,
                        @Cached("create()") HashingStorageNodes.KeysIsSubsetNode isSubsetNode) {
            PSet otherSet = constructSetNode.executeWith(other);
            return isSubsetNode.execute(self.getDictStorage(), otherSet.getDictStorage());
        }
    }

    @Builtin(name = "issuperset", fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class IsSupersetNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean isSuperSet(PBaseSet self, PBaseSet other,
                        @Cached("create()") HashingStorageNodes.KeysIsSupersetNode isSupersetNode) {
            return isSupersetNode.execute(self.getDictStorage(), other.getDictStorage());
        }

        @Specialization
        boolean isSuperSet(PBaseSet self, String other,
                        @Cached("create()") SetNodes.ConstructSetNode constructSetNode,
                        @Cached("create()") HashingStorageNodes.KeysIsSupersetNode isSupersetNode) {
            PSet otherSet = constructSetNode.executeWith(other);
            return isSupersetNode.execute(self.getDictStorage(), otherSet.getDictStorage());
        }
    }

    @Builtin(name = __LE__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class LessEqualNode extends IsSubsetNode {
        @Specialization
        Object isLessEqual(PBaseSet self, Object other,
                        @Cached("create(__GE__)") LookupAndCallBinaryNode lookupAndCallBinaryNode) {
            Object result = lookupAndCallBinaryNode.executeObject(other, self);
            if (result != PNone.NO_VALUE) {
                return result;
            }
            throw raise(PythonErrorType.TypeError, "unorderable types: %p <= %p", self, other);
        }
    }

    @Builtin(name = __GE__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GreaterEqualNode extends IsSupersetNode {
        @Specialization
        Object isGreaterEqual(PBaseSet self, Object other,
                        @Cached("create(__LE__)") LookupAndCallBinaryNode lookupAndCallBinaryNode) {
            Object result = lookupAndCallBinaryNode.executeObject(other, self);
            if (result != PNone.NO_VALUE) {
                return result;
            }
            throw raise(PythonErrorType.TypeError, "unorderable types: %p >= %p", self, other);
        }
    }

    @Builtin(name = __LT__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class LessThanNode extends PythonBinaryBuiltinNode {
        @Child LessEqualNode lessEqualNode;

        private LessEqualNode getLessEqualNode() {
            if (lessEqualNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lessEqualNode = insert(FrozenSetBuiltinsFactory.LessEqualNodeFactory.create());
            }
            return lessEqualNode;
        }

        @Specialization
        boolean isLessThan(PBaseSet self, PBaseSet other,
                        @Cached("createBinaryProfile()") ConditionProfile sizeProfile) {
            if (sizeProfile.profile(self.size() >= other.size())) {
                return false;
            }
            return (Boolean) getLessEqualNode().execute(self, other);
        }

        @Specialization
        boolean isLessThan(PBaseSet self, String other,
                        @Cached("createBinaryProfile()") ConditionProfile sizeProfile) {
            if (sizeProfile.profile(self.size() >= other.length())) {
                return false;
            }
            return (Boolean) getLessEqualNode().execute(self, other);
        }

        @Specialization
        Object isLessThan(PBaseSet self, Object other,
                        @Cached("create(__GT__)") LookupAndCallBinaryNode lookupAndCallBinaryNode) {
            Object result = lookupAndCallBinaryNode.executeObject(other, self);
            if (result != PNone.NO_VALUE) {
                return result;
            }
            throw raise(PythonErrorType.TypeError, "unorderable types: %p < %p", self, other);
        }
    }

    @Builtin(name = __GT__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GreaterThanNode extends PythonBinaryBuiltinNode {
        @Child GreaterEqualNode greaterEqualNode;

        private GreaterEqualNode getGreaterEqualNode() {
            if (greaterEqualNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                greaterEqualNode = insert(FrozenSetBuiltinsFactory.GreaterEqualNodeFactory.create());
            }
            return greaterEqualNode;
        }

        @Specialization
        boolean isGreaterThan(PBaseSet self, PBaseSet other,
                        @Cached("createBinaryProfile()") ConditionProfile sizeProfile) {
            if (sizeProfile.profile(self.size() <= other.size())) {
                return false;
            }
            return (Boolean) getGreaterEqualNode().execute(self, other);
        }

        @Specialization
        boolean isGreaterThan(PBaseSet self, String other,
                        @Cached("createBinaryProfile()") ConditionProfile sizeProfile) {
            if (sizeProfile.profile(self.size() <= other.length())) {
                return false;
            }
            return (Boolean) getGreaterEqualNode().execute(self, other);
        }

        @Specialization
        Object isLessThan(PBaseSet self, Object other,
                        @Cached("create(__LT__)") LookupAndCallBinaryNode lookupAndCallBinaryNode) {
            Object result = lookupAndCallBinaryNode.executeObject(other, self);
            if (result != PNone.NO_VALUE) {
                return result;
            }
            throw raise(PythonErrorType.TypeError, "unorderable types: %p > %p", self, other);
        }
    }
}
