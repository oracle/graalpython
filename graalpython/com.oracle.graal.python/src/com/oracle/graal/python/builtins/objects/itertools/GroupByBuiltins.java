/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.nodes.ErrorMessages.IS_NOT_A;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NEXT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SETSTATE__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.tuple.TupleBuiltins;
import com.oracle.graal.python.lib.PyObjectRichCompareBool;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.profiles.InlinedLoopConditionProfile;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PGroupBy})
public final class GroupByBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return GroupByBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object iter(PGroupBy self) {
            return self;
        }
    }

    @Builtin(name = J___NEXT__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class NextNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object next(VirtualFrame frame, PGroupBy self,
                        @Bind("this") Node inliningTarget,
                        @Cached BuiltinFunctions.NextNode nextNode,
                        @Cached CallNode callNode,
                        @Cached PyObjectRichCompareBool.EqNode eqNode,
                        @Cached InlinedBranchProfile eqProfile,
                        @Cached InlinedConditionProfile hasFuncProfile,
                        @Cached InlinedLoopConditionProfile loopConditionProfile,
                        @Cached PythonObjectFactory factory) {
            self.setCurrGrouper(null);
            while (loopConditionProfile.profile(inliningTarget, doGroupByStep(frame, inliningTarget, self, eqProfile, eqNode))) {
                self.groupByStep(frame, inliningTarget, nextNode, callNode, hasFuncProfile);
            }
            self.setTgtKey(self.getCurrKey());
            PGrouper grouper = factory.createGrouper(self, self.getTgtKey());
            return factory.createTuple(new Object[]{self.getCurrKey(), grouper});
        }

        private static boolean doGroupByStep(VirtualFrame frame, Node inliningTarget, PGroupBy self, InlinedBranchProfile eqProfile, PyObjectRichCompareBool.EqNode eqNode) {
            if (self.getCurrKey() == null) {
                return true;
            } else if (self.getTgtKey() == null) {
                return false;
            } else {
                eqProfile.enter(inliningTarget);
                if (!eqNode.compare(frame, inliningTarget, self.getTgtKey(), self.getCurrKey())) {
                    return false;
                }
            }
            return true;
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object reduceMarkerNotSet(PGroupBy self,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedConditionProfile noKeyFuncProfile,
                        @Cached InlinedConditionProfile noValuesProfile,
                        @Cached GetClassNode getClassNode,
                        @Cached PythonObjectFactory factory) {
            Object keyFunc = self.getKeyFunc();
            if (noKeyFuncProfile.profile(inliningTarget, keyFunc == null)) {
                keyFunc = PNone.NONE;
            }
            Object type = getClassNode.execute(inliningTarget, self);
            PTuple tuple1 = factory.createTuple(new Object[]{self.getIt(), keyFunc});
            if (noValuesProfile.profile(inliningTarget, !valuesSet(self))) {
                return factory.createTuple(new Object[]{type, tuple1});
            }
            PTuple tuple2 = factory.createTuple(new Object[]{self.getCurrValue(), self.getTgtKey(), self.getCurrKey()});
            return factory.createTuple(new Object[]{type, tuple1, tuple2});
        }

        private static boolean valuesSet(PGroupBy self) {
            return self.getTgtKey() != null && self.getCurrKey() != null && self.getCurrValue() != null;
        }

    }

    @Builtin(name = J___SETSTATE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class SetStateNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object setState(VirtualFrame frame, PGroupBy self, Object state,
                        @Bind("this") Node inliningTarget,
                        @Cached TupleBuiltins.LenNode lenNode,
                        @Cached TupleBuiltins.GetItemNode getItemNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (!(state instanceof PTuple) || (int) lenNode.execute(frame, state) != 3) {
                throw raiseNode.get(inliningTarget).raise(TypeError, IS_NOT_A, "state", "3-tuple");
            }

            Object currValue = getItemNode.execute(frame, state, 0);
            self.setCurrValue(currValue);

            Object tgtKey = getItemNode.execute(frame, state, 1);
            self.setTgtKey(tgtKey);

            Object currKey = getItemNode.execute(frame, state, 2);
            self.setCurrKey(currKey);

            return PNone.NONE;
        }
    }
}
