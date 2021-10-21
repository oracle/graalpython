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
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SETSTATE__;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.tuple.TupleBuiltins;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectRichCompareBool;
import static com.oracle.graal.python.nodes.ErrorMessages.IS_NOT_A;
import static com.oracle.graal.python.nodes.ErrorMessages.STATE_ARGUMENT_D_MUST_BE_A_S;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.LoopConditionProfile;
import java.util.List;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PGroupBy})
public final class GroupByBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return GroupByBuiltinsFactory.getFactories();
    }

    @Builtin(name = __INIT__, minNumOfPositionalArgs = 2, parameterNames = {"$self", "iterable", "key"})
    @ArgumentClinic(name = "key", defaultValue = "PNone.NONE")
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonTernaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return GroupByBuiltinsClinicProviders.InitNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = "isNone(key)")
        Object initNoKey(VirtualFrame frame, PGroupBy self, Object iterable, @SuppressWarnings("unused") PNone key,
                        @Cached PyObjectGetIter getIter) {
            return init(frame, self, iterable, null, getIter);
        }

        @Specialization(guards = "!isNone(key)")
        Object init(VirtualFrame frame, PGroupBy self, Object iterable, Object key,
                        @Cached PyObjectGetIter getIter) {
            Object marker = factory().createPythonObject(PythonBuiltinClassType.PythonObject);
            self.setMarker(marker);
            self.setTgtKey(marker);
            self.setCurrKey(marker);
            self.setCurrValue(marker);
            self.setKeyFunc(key);
            self.setIt(getIter.execute(frame, iterable));
            return PNone.NONE;
        }
    }

    @Builtin(name = __ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object iter(PGroupBy self) {
            return self;
        }
    }

    @Builtin(name = __NEXT__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class NextNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object next(VirtualFrame frame, PGroupBy self,
                        @Cached BuiltinFunctions.NextNode nextNode,
                        @Cached CallNode callNode,
                        @Cached PyObjectRichCompareBool.EqNode eqNode,
                        @Cached ConditionProfile hasFuncProfile,
                        @Cached LoopConditionProfile loopConditionProfile) {
            self.setCurrGrouper(null);
            Object marker = self.getMarker();
            while (loopConditionProfile.profile(!(self.getCurrKey() != marker && (self.getTgtKey() == marker || !eqNode.execute(frame, self.getTgtKey(), self.getCurrKey()))))) {
                self.groupByStep(frame, nextNode, callNode, hasFuncProfile);
            }
            self.setTgtKey(self.getCurrKey());
            PGrouper grouper = factory().createGrouper(self, self.getTgtKey());
            return factory().createTuple(new Object[]{self.getCurrKey(), grouper});
        }
    }

    @Builtin(name = __REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "isMarkerStillSet(self)")
        Object reduce(PGroupBy self,
                        @Cached GetClassNode getClassNode) {

            Object type = getClassNode.execute(self);
            Object keyFunc = self.getKeyFunc() == null ? PNone.NONE : self.getKeyFunc();

            PTuple tuple = factory().createTuple(new Object[]{self.getIt(), keyFunc});
            return factory().createTuple(new Object[]{type, tuple});
        }

        @Specialization(guards = "!isMarkerStillSet(self)")
        Object reduceMarkerNotSet(PGroupBy self,
                        @Cached GetClassNode getClassNode) {

            Object type = getClassNode.execute(self);
            Object keyFunc = self.getKeyFunc() == null ? PNone.NONE : self.getKeyFunc();

            Object currValue = self.getCurrValue();
            Object tgtKey = self.getTgtKey();
            Object currKey = self.getCurrKey();
            Object currGrouper = self.getCurrGrouper() == null ? PNone.NONE : self.getCurrGrouper();
            Object marker = self.getMarker();

            PTuple tuple = factory().createTuple(new Object[]{self.getIt(), keyFunc, currValue, tgtKey, currKey, currGrouper, marker});
            PTuple emptyTuple = factory().createTuple(new Object[]{factory().createEmptyTuple()});
            return factory().createTuple(new Object[]{type, emptyTuple, tuple});
        }

        protected boolean isMarkerStillSet(PGroupBy self) {
            return self.getCurrValue() == self.getMarker() || self.getTgtKey() == self.getMarker() || self.getCurrKey() == self.getMarker();
        }

    }

    @Builtin(name = __SETSTATE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class SetStateNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object setState(VirtualFrame frame, PGroupBy self, Object state,
                        @Cached TupleBuiltins.LenNode lenNode,
                        @Cached TupleBuiltins.GetItemNode getItemNode,
                        @Cached BranchProfile isNotTupleProfile,
                        @Cached BranchProfile isNotGroupByProfile) {
            if (!(state instanceof PTuple) || (int) lenNode.execute(frame, state) != 7) {
                isNotTupleProfile.enter();
                throw raise(TypeError, IS_NOT_A, "state", "7-tuple");
            }
            Object iterable = getItemNode.execute(frame, state, 0);
            self.setIt(iterable);

            Object keyFunc = getItemNode.execute(frame, state, 1);
            self.setKeyFunc(keyFunc instanceof PNone ? null : keyFunc);

            Object currValue = getItemNode.execute(frame, state, 2);
            self.setCurrValue(currValue);

            Object tgtKey = getItemNode.execute(frame, state, 3);
            self.setTgtKey(tgtKey);

            Object currKey = getItemNode.execute(frame, state, 4);
            self.setCurrKey(currKey);

            Object currGrouper = getItemNode.execute(frame, state, 5);
            if (currGrouper instanceof PNone) {
                self.setCurrGrouper(null);
            } else {
                if (!(currGrouper instanceof PGrouper)) {
                    isNotGroupByProfile.enter();
                    throw raise(TypeError, STATE_ARGUMENT_D_MUST_BE_A_S, 6, "PGrouper");
                }
                self.setCurrGrouper((PGrouper) currGrouper);
            }

            Object marker = getItemNode.execute(frame, state, 6);
            self.setMarker(marker);

            return PNone.NONE;
        }
    }
}
