/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.itertools;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEXT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SETSTATE__;

import com.oracle.graal.python.builtins.Builtin;
import java.util.List;

import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PAccumulate})
public final class AccumulateBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return AccumulateBuiltinsFactory.getFactories();
    }

    @Builtin(name = __ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object iter(PAccumulate self) {
            return self;
        }
    }

    @Builtin(name = __NEXT__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class NextNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object next(VirtualFrame frame, PAccumulate self,
                        @Cached BuiltinFunctions.NextNode nextNode,
                        @Cached BinaryArithmetic.AddNode addNode,
                        @Cached CallNode callNode,
                        @Cached BranchProfile hasInitialProfile,
                        @Cached BranchProfile markerProfile,
                        @Cached ConditionProfile hasFuncProfile) {
            if (self.getInitial() != null) {
                hasInitialProfile.enter();
                self.setTotal(self.getInitial());
                self.setInitial(null);
                return self.getTotal();
            }
            Object value = nextNode.execute(frame, self.getIterable(), PNone.NO_VALUE);
            if (self.getTotal() == null) {
                markerProfile.enter();
                self.setTotal(value);
                return value;
            }
            if (hasFuncProfile.profile(self.getFunc() == null)) {
                self.setTotal(addNode.executeObject(frame, self.getTotal(), value));
            } else {
                self.setTotal(callNode.execute(self.getFunc(), self.getTotal(), value));
            }
            return self.getTotal();
        }
    }

    @Builtin(name = __REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "hasFunc(self)")
        Object reduce(VirtualFrame frame, PAccumulate self,
                        @Cached GetClassNode getClassNode,
                        @Cached BranchProfile hasInitialProfile,
                        @Cached BranchProfile totalNoneProfile,
                        @Cached BranchProfile totalMarkerProfile,
                        @Cached BranchProfile elseProfile,
                        @Cached PyObjectGetIter getIter) {
            return reduce(self, self.getFunc(), hasInitialProfile, getClassNode, totalNoneProfile, getIter, frame, totalMarkerProfile, elseProfile);
        }

        @Specialization(guards = "!hasFunc(self)")
        Object reduceNoFunc(VirtualFrame frame, PAccumulate self,
                        @Cached GetClassNode getClassNode,
                        @Cached BranchProfile hasInitialProfile,
                        @Cached BranchProfile totalNoneProfile,
                        @Cached BranchProfile totalMarkerProfile,
                        @Cached BranchProfile elseProfile,
                        @Cached PyObjectGetIter getIter) {
            return reduce(self, PNone.NONE, hasInitialProfile, getClassNode, totalNoneProfile, getIter, frame, totalMarkerProfile, elseProfile);
        }

        private Object reduce(PAccumulate self, Object func, BranchProfile hasInitialProfile, GetClassNode getClassNode, BranchProfile totalNoneProfile, PyObjectGetIter getIter, VirtualFrame frame,
                        BranchProfile totalMarkerProfile, BranchProfile elseProfile) {
            if (self.getInitial() != null) {
                hasInitialProfile.enter();

                Object type = getClassNode.execute(self);
                PChain chain = factory().createChain(PythonBuiltinClassType.PChain);
                chain.setSource(getIter.execute(frame, factory().createList(new Object[]{self.getIterable()})));
                PTuple initialTuple = factory().createTuple(new Object[]{self.getInitial()});
                chain.setActive(getIter.execute(frame, initialTuple));

                PTuple tuple = factory().createTuple(new Object[]{chain, func});
                return factory().createTuple(new Object[]{type, tuple, PNone.NONE});
            } else if (self.getTotal() == PNone.NONE) {
                totalNoneProfile.enter();

                PChain chain = factory().createChain(PythonBuiltinClassType.PChain);
                PList noneList = factory().createList(new Object[]{PNone.NONE});
                Object noneIter = getIter.execute(frame, noneList);
                chain.setSource(getIter.execute(frame, factory().createList(new Object[]{noneIter, self.getIterable()})));
                chain.setActive(PNone.NONE);
                PAccumulate accumulate = factory().createAccumulate(PythonBuiltinClassType.PAccumulate);
                accumulate.setIterable(chain);
                accumulate.setFunc(func);

                PTuple tuple = factory().createTuple(new Object[]{accumulate, 1, PNone.NONE});
                return factory().createTuple(new Object[]{PythonBuiltinClassType.PIslice, tuple});
            } else if (self.getTotal() != null) {
                totalMarkerProfile.enter();

                Object type = getClassNode.execute(self);
                PTuple tuple = factory().createTuple(new Object[]{self.getIterable(), func});
                return factory().createTuple(new Object[]{type, tuple, self.getTotal()});
            } else {
                elseProfile.enter();

                Object type = getClassNode.execute(self);
                PTuple tuple = factory().createTuple(new Object[]{self.getIterable(), func});
                return factory().createTuple(new Object[]{type, tuple});
            }
        }

        protected static boolean hasFunc(PAccumulate self) {
            return self.getFunc() != null;
        }
    }

    @Builtin(name = __SETSTATE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class SetStateNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object setState(PAccumulate self, Object state) {
            self.setTotal(state);
            return PNone.NONE;
        }
    }

}
