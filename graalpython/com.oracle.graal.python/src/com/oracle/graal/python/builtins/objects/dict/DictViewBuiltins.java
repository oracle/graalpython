/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDictView.PDictItemsView;
import com.oracle.graal.python.builtins.objects.dict.PDictView.PDictKeysView;
import com.oracle.graal.python.builtins.objects.set.PBaseSet;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.builtins.objects.set.SetNodes;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = {PDictKeysView.class, PDictItemsView.class})
public final class DictViewBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return DictViewBuiltinsFactory.getFactories();
    }

    @Builtin(name = __LEN__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class LenNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object len(PDictView self) {
            return self.getDict().size();
        }
    }

    @Builtin(name = __ITER__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object getKeysViewIter(PDictKeysView self) {
            if (self.getDict() != null) {
                return factory().createDictKeysIterator(self.getDict());
            }
            return PNone.NONE;
        }

        @Specialization
        Object getItemsViewIter(PDictItemsView self) {
            if (self.getDict() != null) {
                return factory().createDictItemsIterator(self.getDict());
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = __CONTAINS__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class ContainsNode extends PythonBinaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization(guards = "self.getDict().size() == 0")
        boolean containsEmpty(PDictView self, Object key) {
            return false;
        }

        @Specialization
        boolean contains(PDictKeysView self, Object key,
                        @Cached("create()") HashingStorageNodes.ContainsKeyNode containsKeyNode) {
            return containsKeyNode.execute(self.getDict().getDictStorage(), key);
        }

        @Specialization
        boolean contains(PDictItemsView self, PTuple key,
                        @Cached("create()") HashingStorageNodes.GetItemNode getItemNode,
                        @Cached("create()") HashingStorageNodes.PythonEquivalence equivalenceNode,
                        @Cached("createBinaryProfile()") ConditionProfile tupleLenProfile) {
            if (tupleLenProfile.profile(key.len() != 2)) {
                return false;
            }
            HashingStorage dictStorage = self.getDict().getDictStorage();
            Object value = getItemNode.execute(dictStorage, key.getItem(0));
            return value != null && equivalenceNode.equals(value, key.getItem(1));
        }
    }

    @Builtin(name = __EQ__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class EqNode extends PythonBinaryBuiltinNode {

        @Specialization
        boolean doKeysView(PDictKeysView self, PDictKeysView other,
                        @Cached("create()") HashingStorageNodes.KeysEqualsNode equalsNode) {
            return equalsNode.execute(self.getDict().getDictStorage(), other.getDict().getDictStorage());
        }

        @Specialization
        boolean doKeysView(PDictKeysView self, PBaseSet other,
                        @Cached("create()") HashingStorageNodes.KeysEqualsNode equalsNode) {
            return equalsNode.execute(self.getDict().getDictStorage(), other.getDictStorage());
        }

        @Specialization
        boolean doItemsView(PDictItemsView self, PDictItemsView other,
                        @Cached("create()") HashingStorageNodes.EqualsNode equalsNode) {
            // the items view stores the original dict with K:V pairs so full K:V equality needs to
            // be tested in this case
            return equalsNode.execute(self.getDict().getDictStorage(), other.getDict().getDictStorage());
        }

        @Specialization
        boolean doItemsView(PDictItemsView self, PBaseSet other,
                        @Cached("create()") SetNodes.ConstructSetNode constructSetNode,
                        @Cached("create()") HashingStorageNodes.KeysEqualsNode equalsNode) {
            PSet selfSet = constructSetNode.executeWith(self);
            return equalsNode.execute(selfSet.getDictStorage(), other.getDictStorage());
        }

        @Fallback
        @SuppressWarnings("unused")
        PNotImplemented doGeneric(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __NE__, fixedNumOfArguments = 2)
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
        public boolean notEqual(PDictView self, PDictView other) {
            return !(Boolean) getEqNode().execute(self, other);
        }

        @Specialization
        public boolean notEqual(PDictView self, PBaseSet other) {
            return !(Boolean) getEqNode().execute(self, other);
        }

        @Fallback
        @SuppressWarnings("unused")
        PNotImplemented doGeneric(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __SUB__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class SubNode extends PythonBinaryBuiltinNode {
        @Specialization
        PBaseSet doKeysView(PDictKeysView self, PBaseSet other,
                        @Cached("create()") HashingStorageNodes.DiffNode diffNode) {
            HashingStorage storage = diffNode.execute(self.getDict().getDictStorage(), other.getDictStorage());
            return factory().createSet(storage);
        }

        @Specialization
        PBaseSet doKeysView(PDictKeysView self, PDictKeysView other,
                        @Cached("create()") HashingStorageNodes.DiffNode diffNode) {
            HashingStorage storage = diffNode.execute(self.getDict().getDictStorage(), other.getDict().getDictStorage());
            return factory().createSet(storage);
        }

        @Specialization
        PBaseSet doItemsView(PDictItemsView self, PBaseSet other,
                        @Cached("create()") HashingStorageNodes.DiffNode diffNode,
                        @Cached("create()") SetNodes.ConstructSetNode constructSetNode) {
            PSet selfSet = constructSetNode.executeWith(self);
            HashingStorage storage = diffNode.execute(selfSet.getDictStorage(), other.getDictStorage());
            return factory().createSet(storage);
        }

        @Specialization
        PBaseSet doItemsView(PDictItemsView self, PDictItemsView other,
                        @Cached("create()") HashingStorageNodes.DiffNode diffNode,
                        @Cached("create()") SetNodes.ConstructSetNode constructSetNode) {
            PSet selfSet = constructSetNode.executeWith(self);
            PSet otherSet = constructSetNode.executeWith(other);
            HashingStorage storage = diffNode.execute(selfSet.getDictStorage(), otherSet.getDictStorage());
            return factory().createSet(storage);
        }
    }

    @Builtin(name = __AND__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class AndNode extends PythonBinaryBuiltinNode {
        @Specialization
        PBaseSet doKeysView(PDictKeysView self, PBaseSet other,
                        @Cached("create()") HashingStorageNodes.IntersectNode intersectNode) {
            HashingStorage intersectedStorage = intersectNode.execute(self.getDict().getDictStorage(), other.getDictStorage());
            return factory().createSet(intersectedStorage);
        }

        @Specialization
        PBaseSet doKeysView(PDictKeysView self, PDictKeysView other,
                        @Cached("create()") HashingStorageNodes.IntersectNode intersectNode) {
            HashingStorage intersectedStorage = intersectNode.execute(self.getDict().getDictStorage(), other.getDict().getDictStorage());
            return factory().createSet(intersectedStorage);
        }

        @Specialization
        PBaseSet doItemsView(PDictItemsView self, PBaseSet other,
                        @Cached("create()") HashingStorageNodes.IntersectNode intersectNode,
                        @Cached("create()") SetNodes.ConstructSetNode constructSetNode) {
            PSet selfSet = constructSetNode.executeWith(self);
            HashingStorage intersectedStorage = intersectNode.execute(selfSet.getDictStorage(), other.getDictStorage());
            return factory().createSet(intersectedStorage);
        }

        @Specialization
        PBaseSet doItemsView(PDictItemsView self, PDictItemsView other,
                        @Cached("create()") HashingStorageNodes.IntersectNode intersectNode,
                        @Cached("create()") SetNodes.ConstructSetNode constructSetNode) {
            PSet selfSet = constructSetNode.executeWith(self);
            PSet otherSet = constructSetNode.executeWith(other);
            HashingStorage intersectedStorage = intersectNode.execute(selfSet.getDictStorage(), otherSet.getDictStorage());
            return factory().createSet(intersectedStorage);
        }
    }

    @Builtin(name = __OR__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class OrNode extends PythonBinaryBuiltinNode {
        @Specialization
        PBaseSet doKeysView(PDictKeysView self, PBaseSet other,
                        @Cached("create()") HashingStorageNodes.UnionNode unionNode) {
            return factory().createSet(unionNode.execute(self.getDict().getDictStorage(), other.getDictStorage()));
        }

        @Specialization
        PBaseSet doKeysView(PDictKeysView self, PDictKeysView other,
                        @Cached("create()") HashingStorageNodes.UnionNode unionNode) {
            return factory().createSet(unionNode.execute(self.getDict().getDictStorage(), other.getDict().getDictStorage()));
        }

        @Specialization
        PBaseSet doItemsView(PDictItemsView self, PBaseSet other,
                        @Cached("create()") HashingStorageNodes.UnionNode unionNode,
                        @Cached("create()") SetNodes.ConstructSetNode constructSetNode) {
            PSet selfSet = constructSetNode.executeWith(self);
            return factory().createSet(unionNode.execute(selfSet.getDictStorage(), other.getDictStorage()));
        }

        @Specialization
        PBaseSet doItemsView(PDictItemsView self, PDictItemsView other,
                        @Cached("create()") HashingStorageNodes.UnionNode unionNode,
                        @Cached("create()") SetNodes.ConstructSetNode constructSetNode) {
            PSet selfSet = constructSetNode.executeWith(self);
            PSet otherSet = constructSetNode.executeWith(other);
            return factory().createSet(unionNode.execute(selfSet.getDictStorage(), otherSet.getDictStorage()));
        }
    }

    @Builtin(name = __XOR__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class XorNode extends PythonBinaryBuiltinNode {
        @Specialization
        PBaseSet doKeysView(PDictKeysView self, PBaseSet other,
                        @Cached("create()") HashingStorageNodes.ExclusiveOrNode xorNode) {
            return factory().createSet(xorNode.execute(self.getDict().getDictStorage(), other.getDictStorage()));
        }

        @Specialization
        PBaseSet doKeysView(PDictKeysView self, PDictKeysView other,
                        @Cached("create()") HashingStorageNodes.ExclusiveOrNode xorNode) {
            return factory().createSet(xorNode.execute(self.getDict().getDictStorage(), other.getDict().getDictStorage()));
        }

        @Specialization
        PBaseSet doItemsView(PDictItemsView self, PBaseSet other,
                        @Cached("create()") HashingStorageNodes.ExclusiveOrNode xorNode,
                        @Cached("create()") SetNodes.ConstructSetNode constructSetNode) {
            PSet selfSet = constructSetNode.executeWith(self);
            return factory().createSet(xorNode.execute(selfSet.getDictStorage(), other.getDictStorage()));
        }

        @Specialization
        PBaseSet doItemsView(PDictItemsView self, PDictItemsView other,
                        @Cached("create()") HashingStorageNodes.ExclusiveOrNode xorNode,
                        @Cached("create()") SetNodes.ConstructSetNode constructSetNode) {
            PSet selfSet = constructSetNode.executeWith(self);
            PSet otherSet = constructSetNode.executeWith(other);
            return factory().createSet(xorNode.execute(selfSet.getDictStorage(), otherSet.getDictStorage()));
        }
    }

    @Builtin(name = __LE__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class LessEqualNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean lessEqual(PDictKeysView self, PBaseSet other,
                        @Cached("create()") HashingStorageNodes.KeysIsSubsetNode isSubsetNode) {
            return isSubsetNode.execute(self.getDict().getDictStorage(), other.getDictStorage());
        }

        @Specialization
        boolean lessEqual(PDictKeysView self, PDictKeysView other,
                        @Cached("create()") HashingStorageNodes.KeysIsSubsetNode isSubsetNode) {
            return isSubsetNode.execute(self.getDict().getDictStorage(), other.getDict().getDictStorage());
        }

        @Specialization
        boolean lessEqual(PDictItemsView self, PBaseSet other,
                        @Cached("create()") HashingStorageNodes.KeysIsSubsetNode isSubsetNode,
                        @Cached("create()") SetNodes.ConstructSetNode constructSetNode) {
            PSet selfSet = constructSetNode.executeWith(self);
            return isSubsetNode.execute(selfSet.getDictStorage(), other.getDictStorage());
        }

        @Specialization
        boolean lessEqual(PDictItemsView self, PDictItemsView other,
                        @Cached("create()") HashingStorageNodes.KeysIsSubsetNode isSubsetNode,
                        @Cached("create()") SetNodes.ConstructSetNode constructSetNode) {
            PSet selfSet = constructSetNode.executeWith(self);
            PSet otherSet = constructSetNode.executeWith(other);
            return isSubsetNode.execute(selfSet.getDictStorage(), otherSet.getDictStorage());
        }
    }

    @Builtin(name = __GE__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class GreaterEqualNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean greaterEqual(PDictKeysView self, PBaseSet other,
                        @Cached("create()") HashingStorageNodes.KeysIsSupersetNode isSupersetNode) {
            return isSupersetNode.execute(self.getDict().getDictStorage(), other.getDictStorage());
        }

        @Specialization
        boolean greaterEqual(PDictKeysView self, PDictKeysView other,
                        @Cached("create()") HashingStorageNodes.KeysIsSupersetNode isSupersetNode) {
            return isSupersetNode.execute(self.getDict().getDictStorage(), other.getDict().getDictStorage());
        }

        @Specialization
        boolean greaterEqual(PDictItemsView self, PBaseSet other,
                        @Cached("create()") HashingStorageNodes.KeysIsSupersetNode isSupersetNode,
                        @Cached("create()") SetNodes.ConstructSetNode constructSetNode) {
            PSet selfSet = constructSetNode.executeWith(self);
            return isSupersetNode.execute(selfSet.getDictStorage(), other.getDictStorage());
        }

        @Specialization
        boolean greaterEqual(PDictItemsView self, PDictItemsView other,
                        @Cached("create()") HashingStorageNodes.KeysIsSupersetNode isSupersetNode,
                        @Cached("create()") SetNodes.ConstructSetNode constructSetNode) {
            PSet selfSet = constructSetNode.executeWith(self);
            PSet otherSet = constructSetNode.executeWith(other);
            return isSupersetNode.execute(selfSet.getDictStorage(), otherSet.getDictStorage());
        }
    }

    @Builtin(name = __LT__, fixedNumOfArguments = 2)
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
        boolean isLessThan(PDictView self, PBaseSet other,
                        @Cached("createBinaryProfile()") ConditionProfile sizeProfile) {
            if (sizeProfile.profile(self.size() >= other.size())) {
                return false;
            }
            return (Boolean) getLessEqualNode().execute(self, other);
        }

        @Specialization
        boolean isLessThan(PDictView self, PDictView other,
                        @Cached("createBinaryProfile()") ConditionProfile sizeProfile) {
            if (sizeProfile.profile(self.size() >= other.size())) {
                return false;
            }
            return (Boolean) getLessEqualNode().execute(self, other);
        }
    }

    @Builtin(name = __GT__, fixedNumOfArguments = 2)
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
        boolean isGreaterThan(PDictView self, PBaseSet other,
                        @Cached("createBinaryProfile()") ConditionProfile sizeProfile) {
            if (sizeProfile.profile(self.size() <= other.size())) {
                return false;
            }
            return (Boolean) getGreaterEqualNode().execute(self, other);
        }

        @Specialization
        boolean isGreaterThan(PDictView self, PDictView other,
                        @Cached("createBinaryProfile()") ConditionProfile sizeProfile) {
            if (sizeProfile.profile(self.size() <= other.size())) {
                return false;
            }
            return (Boolean) getGreaterEqualNode().execute(self, other);
        }
    }
}
