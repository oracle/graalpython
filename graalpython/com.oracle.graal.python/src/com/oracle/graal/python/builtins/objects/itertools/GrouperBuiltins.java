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

import static com.oracle.graal.python.nodes.SpecialMethodNames.__INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEXT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REDUCE__;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.StopIteration;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.tuple.TupleBuiltins;
import com.oracle.graal.python.lib.PyObjectRichCompareBool;
import static com.oracle.graal.python.nodes.ErrorMessages.IS_NOT_A;
import static com.oracle.graal.python.nodes.ErrorMessages.STATE_ARGUMENT_D_MUST_BE_A_S;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SETSTATE__;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import java.util.List;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PGrouper})
public final class GrouperBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return GrouperBuiltinsFactory.getFactories();
    }

    @Builtin(name = __INIT__, minNumOfPositionalArgs = 2, parameterNames = {"$self", "parent", "tgtkey"})
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonTernaryBuiltinNode {
        @Specialization
        Object init(PGrouper self, PGroupBy parent, Object tgtKey) {
            parent.setCurrGrouper(self);
            self.setParent(parent);
            self.setTgtKey(tgtKey);
            self.setMarker(parent.getMarker());
            return PNone.NONE;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isGroupBy(parent)")
        Object init(Object self, Object parent, Object tgtky) {
            // TODO
// throw raise(TypeError, "incorrect usage of internal _grouper " + parent + " " + tgtky);
            return PNone.NONE;
        }

        protected boolean isGroupBy(Object obj) {
            return obj instanceof PGroupBy;
        }
    }

    @Builtin(name = __ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object iter(PGrouper self) {
            return self;
        }
    }

    @Builtin(name = __NEXT__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class NextNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object next(VirtualFrame frame, PGrouper self,
                        @Cached BuiltinFunctions.NextNode nextNode,
                        @Cached CallNode callNode,
                        @Cached PyObjectRichCompareBool.EqNode eqNode,
                        @Cached BranchProfile currGrouperProfile,
                        @Cached BranchProfile currValueMarkerProfile,
                        @Cached BranchProfile currValueTgtProfile,
                        @Cached ConditionProfile hasFuncProfile) {
            PGroupBy gbo = self.getParent();
            if (gbo.getCurrGrouper() != self) {
                currGrouperProfile.enter();
                throw raise(StopIteration);
            }
            if (gbo.getCurrValue() == self.getMarker()) {
                currValueMarkerProfile.enter();
                gbo.groupByStep(frame, nextNode, callNode, hasFuncProfile);
            }
            if (!eqNode.execute(frame, self.getTgtKey(), gbo.getCurrKey())) {
                currValueTgtProfile.enter();
                throw raise(StopIteration);
            }
            Object r = gbo.getCurrValue();
            gbo.setCurrValue(self.getMarker());
            return r;
        }
    }

    @Builtin(name = __REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object reduce(PGrouper self,
                        @Cached GetClassNode getClassNode) {
            Object type = getClassNode.execute(self);
            PTuple tuple = factory().createTuple(new Object[]{self.getParent(), self.getTgtKey(), self.getMarker()});
            PTuple emptyTuple = factory().createTuple(new Object[]{factory().createEmptyTuple()});
            // TODO
            return factory().createTuple(new Object[]{type, emptyTuple, tuple});
        }
    }

    @Builtin(name = __SETSTATE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class SetStateNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object setState(VirtualFrame frame, PGrouper self, Object state,
                        @Cached TupleBuiltins.LenNode lenNode,
                        @Cached TupleBuiltins.GetItemNode getItemNode,
                        @Cached BranchProfile isNotTupleProfile,
                        @Cached BranchProfile isNotGrouperProfile) {

            if (!(state instanceof PTuple) || (int) lenNode.execute(frame, state) != 3) {
                isNotTupleProfile.enter();
                throw raise(TypeError, IS_NOT_A, "state", "3-tuple");
            }
            Object parent = getItemNode.execute(frame, state, 0);
            if (!(parent instanceof PGroupBy)) {
                isNotGrouperProfile.enter();
                throw raise(TypeError, STATE_ARGUMENT_D_MUST_BE_A_S, 1, "PGroupBy");
            }
            self.setParent((PGroupBy) parent);

            Object tgtKey = getItemNode.execute(frame, state, 1);
            self.setTgtKey(tgtKey);

            Object marker = getItemNode.execute(frame, state, 2);
            self.setMarker(marker);

            return PNone.NONE;
        }
    }
}
