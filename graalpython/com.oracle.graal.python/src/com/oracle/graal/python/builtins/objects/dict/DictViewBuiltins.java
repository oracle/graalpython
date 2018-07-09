/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 *     one is included with the Software (each a "Larger Work" to which the
 *     Software is contributed by such licensors),
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
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LEN__;
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
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

@CoreFunctions(extendClasses = {PDictKeysView.class, PDictItemsView.class})
public final class DictViewBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return DictViewBuiltinsFactory.getFactories();
    }

    @Builtin(name = __LEN__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class LenNode extends PythonBuiltinNode {
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
    public abstract static class ContainsNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization(guards = "self.getDict().size() == 0")
        boolean containsEmpty(PDictView self, Object key) {
            return false;
        }

        @Specialization
        boolean contains(PDictView self, Object key,
                        @Cached("create()") HashingStorageNodes.ContainsKeyNode containsKeyNode) {
            return containsKeyNode.execute(self.getDict().getDictStorage(), key);
        }
    }

    @Builtin(name = __EQ__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class EqNode extends PythonBuiltinNode {

        @Specialization
        boolean doKeysView(PDictKeysView self, PDictKeysView other,
                        @Cached("create()") HashingStorageNodes.EqualsNode equalsNode) {
            return equalsNode.execute(self.getDict().getDictStorage(), other.getDict().getDictStorage());
        }

        @Specialization
        boolean doItemsView(PDictItemsView self, PDictItemsView other,
                        @Cached("create()") HashingStorageNodes.EqualsNode equalsNode) {
            return equalsNode.execute(self.getDict().getDictStorage(), other.getDict().getDictStorage());
        }

        @Fallback
        @SuppressWarnings("unused")
        Object doGeneric(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __SUB__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class SubNode extends PythonBinaryBuiltinNode {
        @Specialization
        PBaseSet doItemsView(PDictItemsView left, PDictItemsView right,
                        @Cached("create()") HashingStorageNodes.DiffNode diffNode) {
            HashingStorage storage = diffNode.execute(left.getDict().getDictStorage(), right.getDict().getDictStorage());
            return factory().createSet(storage);
        }

        @Specialization
        PBaseSet doKeysView(PDictKeysView left, PDictKeysView right,
                        @Cached("create()") HashingStorageNodes.DiffNode diffNode) {
            HashingStorage storage = diffNode.execute(left.getDict().getDictStorage(), right.getDict().getDictStorage());
            return factory().createSet(storage);
        }
    }

    @Builtin(name = __AND__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class AndNode extends PythonBinaryBuiltinNode {
        @Specialization
        PBaseSet doItemsView(PDictItemsView left, PDictItemsView right,
                        @Cached("create()") HashingStorageNodes.IntersectNode intersectNode) {
            HashingStorage intersectedStorage = intersectNode.execute(left.getDict().getDictStorage(), right.getDict().getDictStorage());
            return factory().createSet(intersectedStorage);
        }

        @Specialization
        PBaseSet doKeysView(PDictKeysView left, PDictKeysView right,
                        @Cached("create()") HashingStorageNodes.IntersectNode intersectNode) {
            HashingStorage intersectedStorage = intersectNode.execute(left.getDict().getDictStorage(), right.getDict().getDictStorage());
            return factory().createSet(intersectedStorage);
        }
    }

    @Builtin(name = __OR__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class OrNode extends PythonBuiltinNode {
        @Specialization
        PBaseSet doItemsView(PDictItemsView self, PDictItemsView other,
                        @Cached("create()") HashingStorageNodes.UnionNode unionNode) {
            return factory().createSet(unionNode.execute(self.getDict().getDictStorage(), other.getDict().getDictStorage()));
        }

        @Specialization
        PBaseSet doKeysView(PDictKeysView self, PDictKeysView other,
                        @Cached("create()") HashingStorageNodes.UnionNode unionNode) {
            return factory().createSet(unionNode.execute(self.getDict().getDictStorage(), other.getDict().getDictStorage()));
        }
    }

    @Builtin(name = __XOR__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class XorNode extends PythonBuiltinNode {
        @Specialization
        PBaseSet doItemsView(PDictItemsView self, PDictItemsView other,
                             @Cached("create()") HashingStorageNodes.ExclusiveOrNode xorNode) {
            return factory().createSet(xorNode.execute(self.getDict().getDictStorage(), other.getDict().getDictStorage()));
        }

        @Specialization
        PBaseSet doKeysView(PDictKeysView self, PDictKeysView other,
                            @Cached("create()") HashingStorageNodes.ExclusiveOrNode xorNode) {
            return factory().createSet(xorNode.execute(self.getDict().getDictStorage(), other.getDict().getDictStorage()));
        }
    }
}
