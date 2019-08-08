/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDictView.PDictItemsView;
import com.oracle.graal.python.builtins.objects.dict.PDictView.PDictKeysView;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.set.PBaseSet;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.builtins.objects.set.SetNodes;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNode;
import com.oracle.graal.python.nodes.expression.CastToBooleanNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
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
            if (self.getWrappedDict() != null) {
                return factory().createDictKeysIterator(self.getWrappedDict());
            }
            return PNone.NONE;
        }

        @Specialization
        Object getItemsViewIter(PDictItemsView self) {
            if (self.getWrappedDict() != null) {
                return factory().createDictItemsIterator(self.getWrappedDict());
            }
            return PNone.NONE;
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

        @Specialization
        boolean contains(VirtualFrame frame, PDictKeysView self, Object key,
                        @Cached("create()") HashingStorageNodes.ContainsKeyNode containsKeyNode) {
            return containsKeyNode.execute(frame, self.getWrappedDict().getDictStorage(), key);
        }

        @Specialization
        boolean contains(VirtualFrame frame, PDictItemsView self, PTuple key,
                        @Cached("create()") HashingStorageNodes.GetItemNode getDictItemNode,
                        @Cached("createCmpNode()") BinaryComparisonNode callEqNode,
                        @Cached("createIfTrueNode()") CastToBooleanNode castToBoolean,
                        @Cached("createBinaryProfile()") ConditionProfile tupleLenProfile,
                        @Cached("create()") SequenceStorageNodes.LenNode lenNode,
                        @Cached("createNotNormalized()") SequenceStorageNodes.GetItemNode getTupleItemNode) {
            SequenceStorage tupleStorage = key.getSequenceStorage();
            if (tupleLenProfile.profile(lenNode.execute(tupleStorage) != 2)) {
                return false;
            }
            HashingStorage dictStorage = self.getWrappedDict().getDictStorage();
            Object value = getDictItemNode.execute(frame, dictStorage, getTupleItemNode.execute(tupleStorage, 0));
            return value != null && castToBoolean.executeBoolean(frame, callEqNode.executeWith(frame, value, getTupleItemNode.execute(tupleStorage, 1)));
        }

        protected static BinaryComparisonNode createCmpNode() {
            return BinaryComparisonNode.create(SpecialMethodNames.__EQ__, SpecialMethodNames.__EQ__, "==", null, null);
        }
    }

    @Builtin(name = __EQ__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class EqNode extends PythonBinaryBuiltinNode {

        @Specialization
        boolean doKeysView(VirtualFrame frame, PDictKeysView self, PDictKeysView other,
                        @Cached("create()") HashingStorageNodes.KeysEqualsNode equalsNode) {
            return equalsNode.execute(frame, self.getWrappedDict().getDictStorage(), other.getWrappedDict().getDictStorage());
        }

        @Specialization
        boolean doKeysView(VirtualFrame frame, PDictKeysView self, PBaseSet other,
                        @Cached("create()") HashingStorageNodes.KeysEqualsNode equalsNode) {
            return equalsNode.execute(frame, self.getWrappedDict().getDictStorage(), other.getDictStorage());
        }

        @Specialization
        boolean doItemsView(VirtualFrame frame, PDictItemsView self, PDictItemsView other,
                        @Cached("create()") HashingStorageNodes.EqualsNode equalsNode) {
            // the items view stores the original dict with K:V pairs so full K:V equality needs to
            // be tested in this case
            return equalsNode.execute(frame, self.getWrappedDict().getDictStorage(), other.getWrappedDict().getDictStorage());
        }

        @Specialization
        boolean doItemsView(VirtualFrame frame, PDictItemsView self, PBaseSet other,
                        @Cached("create()") SetNodes.ConstructSetNode constructSetNode,
                        @Cached("create()") HashingStorageNodes.KeysEqualsNode equalsNode) {
            PSet selfSet = constructSetNode.executeWith(frame, self);
            return equalsNode.execute(frame, selfSet.getDictStorage(), other.getDictStorage());
        }

        @Fallback
        @SuppressWarnings("unused")
        PNotImplemented doGeneric(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
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
        public boolean notEqual(VirtualFrame frame, PDictView self, PDictView other) {
            return !(Boolean) getEqNode().execute(frame, self, other);
        }

        @Specialization
        public boolean notEqual(VirtualFrame frame, PDictView self, PBaseSet other) {
            return !(Boolean) getEqNode().execute(frame, self, other);
        }

        @Fallback
        @SuppressWarnings("unused")
        PNotImplemented doGeneric(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __SUB__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class SubNode extends PythonBinaryBuiltinNode {
        @Specialization
        PBaseSet doKeysView(VirtualFrame frame, PDictKeysView self, PBaseSet other,
                        @Cached("create()") HashingStorageNodes.DiffNode diffNode) {
            HashingStorage storage = diffNode.execute(frame, self.getWrappedDict().getDictStorage(), other.getDictStorage());
            return factory().createSet(storage);
        }

        @Specialization
        PBaseSet doKeysView(VirtualFrame frame, PDictKeysView self, PDictKeysView other,
                        @Cached("create()") HashingStorageNodes.DiffNode diffNode) {
            HashingStorage storage = diffNode.execute(frame, self.getWrappedDict().getDictStorage(), other.getWrappedDict().getDictStorage());
            return factory().createSet(storage);
        }

        @Specialization
        PBaseSet doItemsView(VirtualFrame frame, PDictItemsView self, PBaseSet other,
                        @Cached("create()") HashingStorageNodes.DiffNode diffNode,
                        @Cached("create()") SetNodes.ConstructSetNode constructSetNode) {
            PSet selfSet = constructSetNode.executeWith(frame, self);
            HashingStorage storage = diffNode.execute(frame, selfSet.getDictStorage(), other.getDictStorage());
            return factory().createSet(storage);
        }

        @Specialization
        PBaseSet doItemsView(VirtualFrame frame, PDictItemsView self, PDictItemsView other,
                        @Cached("create()") HashingStorageNodes.DiffNode diffNode,
                        @Cached("create()") SetNodes.ConstructSetNode constructSetNode) {
            PSet selfSet = constructSetNode.executeWith(frame, self);
            PSet otherSet = constructSetNode.executeWith(frame, other);
            HashingStorage storage = diffNode.execute(frame, selfSet.getDictStorage(), otherSet.getDictStorage());
            return factory().createSet(storage);
        }

        @Specialization
        PBaseSet doItemsView(VirtualFrame frame, PDictKeysView self, PList other,
                        @Cached("create()") HashingStorageNodes.DiffNode diffNode,
                        @Cached("create()") SetNodes.ConstructSetNode constructSetNode) {
            PSet selfSet = constructSetNode.executeWith(frame, self);
            PSet otherSet = constructSetNode.executeWith(frame, other);
            HashingStorage storage = diffNode.execute(frame, selfSet.getDictStorage(), otherSet.getDictStorage());
            return factory().createSet(storage);
        }
    }

    @Builtin(name = __AND__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class AndNode extends PythonBinaryBuiltinNode {
        @Specialization
        PBaseSet doKeysView(VirtualFrame frame, PDictKeysView self, PBaseSet other,
                        @Cached("create()") HashingStorageNodes.IntersectNode intersectNode) {
            HashingStorage intersectedStorage = intersectNode.execute(frame, self.getWrappedDict().getDictStorage(), other.getDictStorage());
            return factory().createSet(intersectedStorage);
        }

        @Specialization
        PBaseSet doKeysView(VirtualFrame frame, PDictKeysView self, PDictKeysView other,
                        @Cached("create()") HashingStorageNodes.IntersectNode intersectNode) {
            HashingStorage intersectedStorage = intersectNode.execute(frame, self.getWrappedDict().getDictStorage(), other.getWrappedDict().getDictStorage());
            return factory().createSet(intersectedStorage);
        }

        @Specialization
        PBaseSet doItemsView(VirtualFrame frame, PDictItemsView self, PBaseSet other,
                        @Cached("create()") HashingStorageNodes.IntersectNode intersectNode,
                        @Cached("create()") SetNodes.ConstructSetNode constructSetNode) {
            PSet selfSet = constructSetNode.executeWith(frame, self);
            HashingStorage intersectedStorage = intersectNode.execute(frame, selfSet.getDictStorage(), other.getDictStorage());
            return factory().createSet(intersectedStorage);
        }

        @Specialization
        PBaseSet doItemsView(VirtualFrame frame, PDictItemsView self, PDictItemsView other,
                        @Cached("create()") HashingStorageNodes.IntersectNode intersectNode,
                        @Cached("create()") SetNodes.ConstructSetNode constructSetNode) {
            PSet selfSet = constructSetNode.executeWith(frame, self);
            PSet otherSet = constructSetNode.executeWith(frame, other);
            HashingStorage intersectedStorage = intersectNode.execute(frame, selfSet.getDictStorage(), otherSet.getDictStorage());
            return factory().createSet(intersectedStorage);
        }
    }

    @Builtin(name = __OR__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class OrNode extends PythonBinaryBuiltinNode {
        @Specialization
        PBaseSet doKeysView(VirtualFrame frame, PDictKeysView self, PBaseSet other,
                        @Cached("create()") HashingStorageNodes.UnionNode unionNode) {
            return factory().createSet(unionNode.execute(frame, self.getWrappedDict().getDictStorage(), other.getDictStorage()));
        }

        @Specialization
        PBaseSet doKeysView(VirtualFrame frame, PDictKeysView self, PDictKeysView other,
                        @Cached("create()") HashingStorageNodes.UnionNode unionNode) {
            return factory().createSet(unionNode.execute(frame, self.getWrappedDict().getDictStorage(), other.getWrappedDict().getDictStorage()));
        }

        @Specialization
        PBaseSet doItemsView(VirtualFrame frame, PDictItemsView self, PBaseSet other,
                        @Cached("create()") HashingStorageNodes.UnionNode unionNode,
                        @Cached("create()") SetNodes.ConstructSetNode constructSetNode) {
            PSet selfSet = constructSetNode.executeWith(frame, self);
            return factory().createSet(unionNode.execute(frame, selfSet.getDictStorage(), other.getDictStorage()));
        }

        @Specialization
        PBaseSet doItemsView(VirtualFrame frame, PDictItemsView self, PDictItemsView other,
                        @Cached("create()") HashingStorageNodes.UnionNode unionNode,
                        @Cached("create()") SetNodes.ConstructSetNode constructSetNode) {
            PSet selfSet = constructSetNode.executeWith(frame, self);
            PSet otherSet = constructSetNode.executeWith(frame, other);
            return factory().createSet(unionNode.execute(frame, selfSet.getDictStorage(), otherSet.getDictStorage()));
        }
    }

    @Builtin(name = __XOR__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class XorNode extends PythonBinaryBuiltinNode {
        @Specialization
        PBaseSet doKeysView(VirtualFrame frame, PDictKeysView self, PBaseSet other,
                        @Cached("create()") HashingStorageNodes.ExclusiveOrNode xorNode) {
            return factory().createSet(xorNode.execute(frame, self.getWrappedDict().getDictStorage(), other.getDictStorage()));
        }

        @Specialization
        PBaseSet doKeysView(VirtualFrame frame, PDictKeysView self, PDictKeysView other,
                        @Cached("create()") HashingStorageNodes.ExclusiveOrNode xorNode) {
            return factory().createSet(xorNode.execute(frame, self.getWrappedDict().getDictStorage(), other.getWrappedDict().getDictStorage()));
        }

        @Specialization
        PBaseSet doItemsView(VirtualFrame frame, PDictItemsView self, PBaseSet other,
                        @Cached("create()") HashingStorageNodes.ExclusiveOrNode xorNode,
                        @Cached("create()") SetNodes.ConstructSetNode constructSetNode) {
            PSet selfSet = constructSetNode.executeWith(frame, self);
            return factory().createSet(xorNode.execute(frame, selfSet.getDictStorage(), other.getDictStorage()));
        }

        @Specialization
        PBaseSet doItemsView(VirtualFrame frame, PDictItemsView self, PDictItemsView other,
                        @Cached("create()") HashingStorageNodes.ExclusiveOrNode xorNode,
                        @Cached("create()") SetNodes.ConstructSetNode constructSetNode) {
            PSet selfSet = constructSetNode.executeWith(frame, self);
            PSet otherSet = constructSetNode.executeWith(frame, other);
            return factory().createSet(xorNode.execute(frame, selfSet.getDictStorage(), otherSet.getDictStorage()));
        }
    }

    @Builtin(name = __LE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class LessEqualNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean lessEqual(VirtualFrame frame, PDictKeysView self, PBaseSet other,
                        @Cached("create()") HashingStorageNodes.KeysIsSubsetNode isSubsetNode) {
            return isSubsetNode.execute(frame, self.getWrappedDict().getDictStorage(), other.getDictStorage());
        }

        @Specialization
        boolean lessEqual(VirtualFrame frame, PDictKeysView self, PDictKeysView other,
                        @Cached("create()") HashingStorageNodes.KeysIsSubsetNode isSubsetNode) {
            return isSubsetNode.execute(frame, self.getWrappedDict().getDictStorage(), other.getWrappedDict().getDictStorage());
        }

        @Specialization
        boolean lessEqual(VirtualFrame frame, PDictItemsView self, PBaseSet other,
                        @Cached("create()") HashingStorageNodes.KeysIsSubsetNode isSubsetNode,
                        @Cached("create()") SetNodes.ConstructSetNode constructSetNode) {
            PSet selfSet = constructSetNode.executeWith(frame, self);
            return isSubsetNode.execute(frame, selfSet.getDictStorage(), other.getDictStorage());
        }

        @Specialization
        boolean lessEqual(VirtualFrame frame, PDictItemsView self, PDictItemsView other,
                        @Cached("create()") HashingStorageNodes.KeysIsSubsetNode isSubsetNode,
                        @Cached("create()") SetNodes.ConstructSetNode constructSetNode) {
            PSet selfSet = constructSetNode.executeWith(frame, self);
            PSet otherSet = constructSetNode.executeWith(frame, other);
            return isSubsetNode.execute(frame, selfSet.getDictStorage(), otherSet.getDictStorage());
        }
    }

    @Builtin(name = __GE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GreaterEqualNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean greaterEqual(VirtualFrame frame, PDictKeysView self, PBaseSet other,
                        @Cached("create()") HashingStorageNodes.KeysIsSupersetNode isSupersetNode) {
            return isSupersetNode.execute(frame, self.getWrappedDict().getDictStorage(), other.getDictStorage());
        }

        @Specialization
        boolean greaterEqual(VirtualFrame frame, PDictKeysView self, PDictKeysView other,
                        @Cached("create()") HashingStorageNodes.KeysIsSupersetNode isSupersetNode) {
            return isSupersetNode.execute(frame, self.getWrappedDict().getDictStorage(), other.getWrappedDict().getDictStorage());
        }

        @Specialization
        boolean greaterEqual(VirtualFrame frame, PDictItemsView self, PBaseSet other,
                        @Cached("create()") HashingStorageNodes.KeysIsSupersetNode isSupersetNode,
                        @Cached("create()") SetNodes.ConstructSetNode constructSetNode) {
            PSet selfSet = constructSetNode.executeWith(frame, self);
            return isSupersetNode.execute(frame, selfSet.getDictStorage(), other.getDictStorage());
        }

        @Specialization
        boolean greaterEqual(VirtualFrame frame, PDictItemsView self, PDictItemsView other,
                        @Cached("create()") HashingStorageNodes.KeysIsSupersetNode isSupersetNode,
                        @Cached("create()") SetNodes.ConstructSetNode constructSetNode) {
            PSet selfSet = constructSetNode.executeWith(frame, self);
            PSet otherSet = constructSetNode.executeWith(frame, other);
            return isSupersetNode.execute(frame, selfSet.getDictStorage(), otherSet.getDictStorage());
        }
    }

    @Builtin(name = __LT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class LessThanNode extends PythonBinaryBuiltinNode {
        @Child LessEqualNode lessEqualNode;

        private LessEqualNode getLessEqualNode() {
            if (lessEqualNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lessEqualNode = insert(DictViewBuiltinsFactory.LessEqualNodeFactory.create());
            }
            return lessEqualNode;
        }

        @Specialization
        boolean isLessThan(VirtualFrame frame, PDictView self, PBaseSet other,
                        @Cached("createBinaryProfile()") ConditionProfile sizeProfile) {
            if (sizeProfile.profile(self.size() >= other.size())) {
                return false;
            }
            return (Boolean) getLessEqualNode().execute(frame, self, other);
        }

        @Specialization
        boolean isLessThan(VirtualFrame frame, PDictView self, PDictView other,
                        @Cached("createBinaryProfile()") ConditionProfile sizeProfile) {
            if (sizeProfile.profile(self.size() >= other.size())) {
                return false;
            }
            return (Boolean) getLessEqualNode().execute(frame, self, other);
        }
    }

    @Builtin(name = __GT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GreaterThanNode extends PythonBinaryBuiltinNode {
        @Child GreaterEqualNode greaterEqualNode;

        private GreaterEqualNode getGreaterEqualNode() {
            if (greaterEqualNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                greaterEqualNode = insert(DictViewBuiltinsFactory.GreaterEqualNodeFactory.create());
            }
            return greaterEqualNode;
        }

        @Specialization
        boolean isGreaterThan(VirtualFrame frame, PDictView self, PBaseSet other,
                        @Cached("createBinaryProfile()") ConditionProfile sizeProfile) {
            if (sizeProfile.profile(self.size() <= other.size())) {
                return false;
            }
            return (Boolean) getGreaterEqualNode().execute(frame, self, other);
        }

        @Specialization
        boolean isGreaterThan(VirtualFrame frame, PDictView self, PDictView other,
                        @Cached("createBinaryProfile()") ConditionProfile sizeProfile) {
            if (sizeProfile.profile(self.size() <= other.size())) {
                return false;
            }
            return (Boolean) getGreaterEqualNode().execute(frame, self, other);
        }
    }
}
