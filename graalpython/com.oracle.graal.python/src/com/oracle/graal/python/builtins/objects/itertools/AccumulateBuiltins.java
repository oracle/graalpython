/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.modules.ItertoolsModuleBuiltins.warnPickleDeprecated;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SETSTATE__;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetObjectSlotsNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotIterNext.CallSlotTpIterNextNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotIterNext.TpIterNextBuiltin;
import com.oracle.graal.python.lib.PyNumberAddNode;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PAccumulate})
public final class AccumulateBuiltins extends PythonBuiltins {

    public static final TpSlots SLOTS = AccumulateBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return AccumulateBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.tp_iter, isComplex = true)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object iter(PAccumulate self) {
            return self;
        }
    }

    @Slot(value = SlotKind.tp_iternext, isComplex = true)
    @GenerateNodeFactory
    public abstract static class NextNode extends TpIterNextBuiltin {
        @Specialization
        static Object next(VirtualFrame frame, PAccumulate self,
                        @Bind("this") Node inliningTarget,
                        @Cached GetObjectSlotsNode getSlots,
                        @Cached CallSlotTpIterNextNode callIterNext,
                        @Cached PyNumberAddNode addNode,
                        @Cached CallNode callNode,
                        @Cached InlinedBranchProfile hasInitialProfile,
                        @Cached InlinedBranchProfile markerProfile,
                        @Cached InlinedConditionProfile hasFuncProfile) {
            if (self.getInitial() != null) {
                hasInitialProfile.enter(inliningTarget);
                self.setTotal(self.getInitial());
                self.setInitial(null);
                return self.getTotal();
            }
            Object it = self.getIterable();
            Object value = callIterNext.execute(frame, inliningTarget, getSlots.execute(inliningTarget, it).tp_iternext(), it);
            if (self.getTotal() == null) {
                markerProfile.enter(inliningTarget);
                self.setTotal(value);
                return value;
            }
            if (hasFuncProfile.profile(inliningTarget, self.getFunc() == null)) {
                self.setTotal(addNode.execute(frame, self.getTotal(), value));
            } else {
                self.setTotal(callNode.execute(frame, self.getFunc(), self.getTotal(), value));
            }
            return self.getTotal();
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object reduceNoFunc(VirtualFrame frame, PAccumulate self,
                        @Bind("this") Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached InlinedBranchProfile hasInitialProfile,
                        @Cached InlinedBranchProfile totalNoneProfile,
                        @Cached InlinedBranchProfile totalMarkerProfile,
                        @Cached InlinedBranchProfile elseProfile,
                        @Cached PyObjectGetIter getIter,
                        @Bind PythonLanguage language) {
            warnPickleDeprecated();
            Object func = self.getFunc();
            if (func == null) {
                func = PNone.NONE;
            }
            if (self.getInitial() != null) {
                hasInitialProfile.enter(inliningTarget);

                Object type = getClassNode.execute(inliningTarget, self);
                PChain chain = PFactory.createChain(language);
                chain.setSource(getIter.execute(frame, inliningTarget, PFactory.createList(language, new Object[]{self.getIterable()})));
                PTuple initialTuple = PFactory.createTuple(language, new Object[]{self.getInitial()});
                chain.setActive(getIter.execute(frame, inliningTarget, initialTuple));

                PTuple tuple = PFactory.createTuple(language, new Object[]{chain, func});
                return PFactory.createTuple(language, new Object[]{type, tuple, PNone.NONE});
            } else if (self.getTotal() == PNone.NONE) {
                totalNoneProfile.enter(inliningTarget);

                PChain chain = PFactory.createChain(language);
                PList noneList = PFactory.createList(language, new Object[]{PNone.NONE});
                Object noneIter = getIter.execute(frame, inliningTarget, noneList);
                chain.setSource(getIter.execute(frame, inliningTarget, PFactory.createList(language, new Object[]{noneIter, self.getIterable()})));
                chain.setActive(PNone.NONE);
                PAccumulate accumulate = PFactory.createAccumulate(language);
                accumulate.setIterable(chain);
                accumulate.setFunc(func);

                PTuple tuple = PFactory.createTuple(language, new Object[]{accumulate, 1, PNone.NONE});
                return PFactory.createTuple(language, new Object[]{PythonBuiltinClassType.PIslice, tuple});
            } else if (self.getTotal() != null) {
                totalMarkerProfile.enter(inliningTarget);

                Object type = getClassNode.execute(inliningTarget, self);
                PTuple tuple = PFactory.createTuple(language, new Object[]{self.getIterable(), func});
                return PFactory.createTuple(language, new Object[]{type, tuple, self.getTotal()});
            } else {
                elseProfile.enter(inliningTarget);

                Object type = getClassNode.execute(inliningTarget, self);
                PTuple tuple = PFactory.createTuple(language, new Object[]{self.getIterable(), func});
                return PFactory.createTuple(language, new Object[]{type, tuple});
            }
        }

    }

    @Builtin(name = J___SETSTATE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class SetStateNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object setState(PAccumulate self, Object state) {
            warnPickleDeprecated();
            self.setTotal(state);
            return PNone.NONE;
        }
    }

}
