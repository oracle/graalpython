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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OverflowError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.nodes.ErrorMessages.INVALID_ARGS;
import static com.oracle.graal.python.nodes.ErrorMessages.ISLICE_WRONG_ARGS;
import static com.oracle.graal.python.nodes.ErrorMessages.STEP_FOR_ISLICE_MUST_BE;
import static com.oracle.graal.python.nodes.ErrorMessages.S_FOR_ISLICE_MUST_BE;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NEW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SETSTATE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___SETSTATE__;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.SysModuleBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetObjectSlotsNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotIterNext.CallSlotTpIterNextNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotIterNext.TpIterNextBuiltin;
import com.oracle.graal.python.lib.IteratorExhausted;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaIntLossyNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.profiles.InlinedLoopConditionProfile;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PIslice})
public final class IsliceBuiltins extends PythonBuiltins {

    public static final TpSlots SLOTS = IsliceBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return IsliceBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___NEW__, raiseErrorName = "islice", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PIslice)
    @GenerateNodeFactory
    public abstract static class IsliceNode extends PythonVarargsBuiltinNode {
        @Specialization
        static Object constructOne(VirtualFrame frame, Object cls, Object[] args, PKeyword[] keywords,
                        @Bind("this") Node inliningTarget,
                        @Cached(inline = false /* uncommon path */) TypeNodes.HasObjectInitNode hasObjectInitNode,
                        @Cached PyObjectGetIter getIter,
                        @Cached PyNumberAsSizeNode asIntNode,
                        @Cached InlinedBranchProfile hasStart,
                        @Cached InlinedBranchProfile hasStop,
                        @Cached InlinedBranchProfile hasStep,
                        @Cached InlinedBranchProfile stopNotInt,
                        @Cached InlinedBranchProfile startNotInt,
                        @Cached InlinedBranchProfile stopWrongValue,
                        @Cached InlinedBranchProfile stepWrongValue,
                        @Cached InlinedBranchProfile wrongValue,
                        @Cached InlinedBranchProfile overflowBranch,
                        @Cached InlinedConditionProfile argsLen1,
                        @Cached InlinedConditionProfile argsLen2,
                        @Cached InlinedConditionProfile argsLen3,
                        @Cached InlinedBranchProfile wrongTypeBranch,
                        @Cached InlinedBranchProfile wrongArgsBranch,
                        @Cached TypeNodes.IsTypeNode isTypeNode,
                        @Bind PythonLanguage language,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape,
                        @Cached PRaiseNode raiseNode) {
            if (!isTypeNode.execute(inliningTarget, cls)) {
                wrongTypeBranch.enter(inliningTarget);
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
            }
            if (keywords.length > 0 && hasObjectInitNode.executeCached(cls)) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.S_TAKES_NO_KEYWORD_ARGS, "islice()");
            }
            if (args.length < 2 || args.length > 4) {
                wrongArgsBranch.enter(inliningTarget);
                throw raiseNode.raise(inliningTarget, TypeError, ISLICE_WRONG_ARGS);
            }
            int start = 0;
            int step = 1;
            int stop = -1;
            if (argsLen1.profile(inliningTarget, args.length == 2)) {
                if (args[1] != PNone.NONE) {
                    hasStop.enter(inliningTarget);
                    try {
                        stop = asIntNode.executeExact(frame, inliningTarget, args[1], OverflowError);
                    } catch (PException e) {
                        stopNotInt.enter(inliningTarget);
                        throw raiseNode.raise(inliningTarget, ValueError, S_FOR_ISLICE_MUST_BE, "Indices");
                    }
                }
                if (stop < -1 || stop > SysModuleBuiltins.MAXSIZE) {
                    stopWrongValue.enter(inliningTarget);
                    throw raiseNode.raise(inliningTarget, ValueError, S_FOR_ISLICE_MUST_BE, "Indices");
                }
            } else if (argsLen2.profile(inliningTarget, args.length == 3) || argsLen3.profile(inliningTarget, args.length == 4)) {
                if (args[1] != PNone.NONE) {
                    hasStart.enter(inliningTarget);
                    try {
                        start = asIntNode.executeExact(frame, inliningTarget, args[1], OverflowError);
                    } catch (PException e) {
                        startNotInt.enter(inliningTarget);
                        throw raiseNode.raise(inliningTarget, ValueError, S_FOR_ISLICE_MUST_BE, "Indices");
                    }
                }
                if (args[2] != PNone.NONE) {
                    hasStop.enter(inliningTarget);
                    try {
                        stop = asIntNode.executeExact(frame, inliningTarget, args[2], OverflowError);
                    } catch (PException e) {
                        stopNotInt.enter(inliningTarget);
                        throw raiseNode.raise(inliningTarget, ValueError, S_FOR_ISLICE_MUST_BE, "Stop argument");
                    }
                }
                if (start < 0 || stop < -1 || start > SysModuleBuiltins.MAXSIZE || stop > SysModuleBuiltins.MAXSIZE) {
                    wrongValue.enter(inliningTarget);
                    throw raiseNode.raise(inliningTarget, ValueError, S_FOR_ISLICE_MUST_BE, "Indices");
                }
            }
            if (argsLen3.profile(inliningTarget, args.length == 4)) {
                if (args[3] != PNone.NONE) {
                    hasStep.enter(inliningTarget);
                    try {
                        step = asIntNode.executeExact(frame, inliningTarget, args[3], OverflowError);
                    } catch (PException e) {
                        overflowBranch.enter(inliningTarget);
                        step = -1;
                    }
                }
                if (step < 1) {
                    stepWrongValue.enter(inliningTarget);
                    throw raiseNode.raise(inliningTarget, ValueError, STEP_FOR_ISLICE_MUST_BE);
                }
            }
            Object iterable = args[0];
            PIslice self = PFactory.createIslice(language, cls, getInstanceShape.execute(cls));
            self.setIterable(getIter.execute(frame, inliningTarget, iterable));
            self.setNext(start);
            self.setStop(stop);
            self.setStep(step);
            self.setCnt(0);
            return self;
        }
    }

    @Slot(value = SlotKind.tp_iter, isComplex = true)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object iter(PIslice self) {
            return self;
        }
    }

    @Slot(value = SlotKind.tp_iternext, isComplex = true)
    @GenerateNodeFactory
    public abstract static class NextNode extends TpIterNextBuiltin {
        @Specialization(guards = "isNone(self.getIterable())")
        static Object next(@SuppressWarnings("unused") PIslice self) {
            throw iteratorExhausted();
        }

        @Specialization(guards = "!isNone(self.getIterable())")
        static Object next(VirtualFrame frame, PIslice self,
                        @Bind("this") Node inliningTarget,
                        @Cached GetObjectSlotsNode getSlots,
                        @Cached CallSlotTpIterNextNode callIterNext,
                        @Cached InlinedLoopConditionProfile loopProfile,
                        @Cached InlinedBranchProfile setNextProfile) {
            Object it = self.getIterable();
            TpSlot iterNext = getSlots.execute(inliningTarget, it).tp_iternext();
            int stop = self.getStop();
            Object item;
            try {
                while (loopProfile.profile(inliningTarget, self.getCnt() < self.getNext())) {
                    callIterNext.execute(frame, inliningTarget, iterNext, it);
                    self.setCnt(self.getCnt() + 1);
                }
                if (stop != -1 && self.getCnt() >= stop) {
                    self.setIterable(PNone.NONE);
                    throw iteratorExhausted();
                }
                item = callIterNext.execute(frame, inliningTarget, iterNext, it);
                self.setCnt(self.getCnt() + 1);
                int oldNext = self.getNext();
                self.setNext(self.getNext() + self.getStep());
                if (self.getNext() < oldNext || (stop != -1 && self.getNext() > stop)) {
                    setNextProfile.enter(inliningTarget);
                    self.setNext(stop);
                }
                return item;
            } catch (IteratorExhausted | PException e) {
                self.setIterable(PNone.NONE);
                throw e;
            }
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "isNone(self.getIterable())")
        static Object reduceNoIterable(VirtualFrame frame, PIslice self,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached GetClassNode getClassNode,
                        @Cached PyObjectGetIter getIter,
                        @Bind PythonLanguage language) {
            // return type(self), (iter([]), 0), 0
            Object type = getClassNode.execute(inliningTarget, self);
            PTuple tuple = PFactory.createTuple(language, new Object[]{getIter.execute(frame, inliningTarget, PFactory.createList(language)), 0});
            return PFactory.createTuple(language, new Object[]{type, tuple, 0});
        }

        @Specialization(guards = "!isNone(self.getIterable())")
        static Object reduce(PIslice self,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached GetClassNode getClassNode,
                        @Bind PythonLanguage language) {
            Object type = getClassNode.execute(inliningTarget, self);
            Object stop = (self.getStop() == -1) ? PNone.NONE : self.getStop();
            PTuple tuple = PFactory.createTuple(language, new Object[]{self.getIterable(), self.getNext(), stop, self.getStep()});
            return PFactory.createTuple(language, new Object[]{type, tuple, self.getCnt()});
        }
    }

    @Builtin(name = J___SETSTATE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class SetStateNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object setState(PIslice self, Object state,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToJavaIntLossyNode castInt,
                        @Cached PRaiseNode raiseNode) {
            try {
                self.setCnt(castInt.execute(inliningTarget, state));
            } catch (CannotCastException e) {
                throw raiseNode.raise(inliningTarget, ValueError, INVALID_ARGS, T___SETSTATE__);
            }
            return PNone.NONE;
        }
    }
}
