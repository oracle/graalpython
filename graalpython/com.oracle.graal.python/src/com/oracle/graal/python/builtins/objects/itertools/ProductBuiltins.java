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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.nodes.ErrorMessages.ARG_CANNOT_BE_NEGATIVE;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SETSTATE__;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.ItertoolsModuleBuiltins.DeprecatedReduceBuiltin;
import com.oracle.graal.python.builtins.modules.ItertoolsModuleBuiltins.DeprecatedSetStateBuiltin;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.iterator.IteratorNodes;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotIterNext.TpIterNextBuiltin;
import com.oracle.graal.python.lib.PyLongAsIntNode;
import com.oracle.graal.python.lib.PyTupleGetItem;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.profiles.InlinedLoopConditionProfile;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PProduct})
public final class ProductBuiltins extends PythonBuiltins {

    public static final TpSlots SLOTS = ProductBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ProductBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = "product", minNumOfPositionalArgs = 1, takesVarArgs = true, keywordOnlyNames = {"repeat"})
    @GenerateNodeFactory
    public abstract static class ProductNode extends PythonBuiltinNode {

        @Specialization(guards = "isTypeNode.execute(inliningTarget, cls)")
        static Object constructNoneRepeat(VirtualFrame frame, Object cls, Object[] iterables, @SuppressWarnings("unused") PNone repeat,
                        @SuppressWarnings("unused") @Bind Node inliningTarget,
                        @Cached.Shared @Cached IteratorNodes.ToArrayNode toArrayNode,
                        @SuppressWarnings("unused") @Cached.Shared("typeNode") @Cached TypeNodes.IsTypeNode isTypeNode,
                        @Bind PythonLanguage language,
                        @Cached.Shared @Cached TypeNodes.GetInstanceShape getInstanceShape) {
            PProduct self = PFactory.createProduct(language, cls, getInstanceShape.execute(cls));
            constructOneRepeat(frame, self, iterables, toArrayNode);
            return self;
        }

        @Specialization(guards = {"isTypeNode.execute(inliningTarget, cls)", "repeat == 1"}, limit = "1")
        static Object constructOneRepeat(VirtualFrame frame, Object cls, Object[] iterables, @SuppressWarnings("unused") int repeat,
                        @SuppressWarnings("unused") @Bind Node inliningTarget,
                        @Cached.Shared @Cached IteratorNodes.ToArrayNode toArrayNode,
                        @SuppressWarnings("unused") @Exclusive @Cached TypeNodes.IsTypeNode isTypeNode,
                        @Bind PythonLanguage language,
                        @Cached.Shared @Cached TypeNodes.GetInstanceShape getInstanceShape) {
            PProduct self = PFactory.createProduct(language, cls, getInstanceShape.execute(cls));
            constructOneRepeat(frame, self, iterables, toArrayNode);
            return self;
        }

        @Specialization(guards = {"isTypeNode.execute(inliningTarget, cls)", "repeat > 1"}, limit = "1")
        static Object construct(VirtualFrame frame, Object cls, Object[] iterables, int repeat,
                        @Bind Node inliningTarget,
                        @Cached.Shared @Cached IteratorNodes.ToArrayNode toArrayNode,
                        @Cached InlinedLoopConditionProfile loopProfile,
                        @SuppressWarnings("unused") @Exclusive @Cached TypeNodes.IsTypeNode isTypeNode,
                        @Bind PythonLanguage language,
                        @Cached.Shared @Cached TypeNodes.GetInstanceShape getInstanceShape) {
            Object[][] lists = unpackIterables(frame, iterables, toArrayNode);
            Object[][] gears = new Object[lists.length * repeat][];
            loopProfile.profileCounted(inliningTarget, repeat);
            LoopNode.reportLoopCount(inliningTarget, repeat);
            for (int i = 0; loopProfile.inject(inliningTarget, i < repeat); i++) {
                PythonUtils.arraycopy(lists, 0, gears, i * lists.length, lists.length);
            }
            PProduct self = PFactory.createProduct(language, cls, getInstanceShape.execute(cls));
            construct(self, gears);
            return self;
        }

        @Specialization(guards = {"isTypeNode.execute(inliningTarget, cls)", "repeat == 0"}, limit = "1")
        static Object constructNoRepeat(Object cls, @SuppressWarnings("unused") Object[] iterables, @SuppressWarnings("unused") int repeat,
                        @SuppressWarnings("unused") @Bind Node inliningTarget,
                        @SuppressWarnings("unused") @Exclusive @Cached TypeNodes.IsTypeNode isTypeNode,
                        @Bind PythonLanguage language,
                        @Cached.Shared @Cached TypeNodes.GetInstanceShape getInstanceShape) {
            PProduct self = PFactory.createProduct(language, cls, getInstanceShape.execute(cls));
            self.setGears(new Object[0][]);
            self.setIndices(new int[0]);
            self.setLst(null);
            self.setStopped(false);
            return self;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"isTypeNode.execute(inliningTarget, cls)", "repeat < 0"}, limit = "1")
        static Object constructNeg(Object cls, Object[] iterables, int repeat,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached TypeNodes.IsTypeNode isTypeNode) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ARG_CANNOT_BE_NEGATIVE, "repeat");
        }

        private static void constructOneRepeat(VirtualFrame frame, PProduct self, Object[] iterables, IteratorNodes.ToArrayNode toArrayNode) {
            Object[][] gears = unpackIterables(frame, iterables, toArrayNode);
            construct(self, gears);
        }

        private static void construct(PProduct self, Object[][] gears) {
            self.setGears(gears);
            for (int i = 0; i < gears.length; i++) {
                if (gears[i].length == 0) {
                    self.setIndices(null);
                    self.setLst(null);
                    self.setStopped(true);
                    return;
                }
            }
            self.setIndices(new int[gears.length]);
            self.setLst(null);
            self.setStopped(false);
        }

        private static Object[][] unpackIterables(VirtualFrame frame, Object[] iterables, IteratorNodes.ToArrayNode toArrayNode) {
            Object[][] lists = new Object[iterables.length][];
            for (int i = 0; i < lists.length; i++) {
                lists[i] = toArrayNode.execute(frame, iterables[i]);
            }
            return lists;
        }

        @Specialization(guards = "!isTypeNode.execute(inliningTarget, cls)")
        @SuppressWarnings("unused")
        static Object construct(Object cls, Object iterables, Object repeat,
                        @Bind Node inliningTarget,
                        @Cached.Shared("typeNode") @Cached TypeNodes.IsTypeNode isTypeNode) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
        }
    }

    @Slot(value = SlotKind.tp_iter, isComplex = true)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object iter(PProduct self) {
            return self;
        }
    }

    @Slot(value = SlotKind.tp_iternext, isComplex = true)
    @GenerateNodeFactory
    public abstract static class NextNode extends TpIterNextBuiltin {

        @Specialization(guards = {"!self.isStopped()", "!hasLst(self)"})
        static Object next(PProduct self,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached InlinedLoopConditionProfile loopProfile,
                        @Bind PythonLanguage language) {
            Object[] lst = new Object[self.getGears().length];
            loopProfile.profileCounted(inliningTarget, lst.length);
            for (int i = 0; loopProfile.inject(inliningTarget, i < lst.length); i++) {
                lst[i] = self.getGears()[i][0];
            }
            self.setLst(lst);
            return PFactory.createTuple(language, lst);
        }

        @Specialization(guards = {"!self.isStopped()", "hasLst(self)"})
        static Object next(PProduct self,
                        @Bind Node inliningTarget,
                        @Cached InlinedConditionProfile gearsProfile,
                        @Cached InlinedConditionProfile indexProfile,
                        @Cached InlinedBranchProfile wasStoppedProfile,
                        @Exclusive @Cached InlinedLoopConditionProfile loopProfile,
                        @Cached InlinedBranchProfile doneProfile,
                        @Bind PythonLanguage language) {
            Object[][] gears = self.getGears();
            int x = gears.length - 1;
            if (gearsProfile.profile(inliningTarget, x >= 0)) {
                Object[] gear = gears[x];
                int[] indices = self.getIndices();
                int index = indices[x] + 1;
                if (indexProfile.profile(inliningTarget, index < gear.length)) {
                    // no carry: done
                    self.getLst()[x] = gear[index];
                    indices[x] = index;
                } else {
                    rotatePreviousGear(inliningTarget, self, loopProfile, doneProfile);
                }
            } else {
                self.setStopped(true);
            }

            if (self.isStopped()) {
                wasStoppedProfile.enter(inliningTarget);
                throw iteratorExhausted();
            }

            // the existing lst array can be changed in a following next call
            Object[] ret = new Object[self.getLst().length];
            PythonUtils.arraycopy(self.getLst(), 0, ret, 0, ret.length);
            return PFactory.createTuple(language, ret);
        }

        @Specialization(guards = "self.isStopped()")
        static Object nextStopped(@SuppressWarnings("unused") PProduct self) {
            throw iteratorExhausted();
        }

        private static void rotatePreviousGear(Node inliningTarget, PProduct self, InlinedLoopConditionProfile loopProfile, InlinedBranchProfile doneProfile) {
            Object[] lst = self.getLst();
            Object[][] gears = self.getGears();
            int x = gears.length - 1;
            lst[x] = gears[x][0];
            int[] indices = self.getIndices();
            indices[x] = 0;
            x = x - 1;
            // the outer loop runs as long as we have a carry
            while (loopProfile.profile(inliningTarget, x >= 0)) {
                Object[] gear = gears[x];
                int index = indices[x] + 1;
                if (index < gear.length) {
                    // no carry: done
                    doneProfile.enter(inliningTarget);
                    lst[x] = gear[index];
                    indices[x] = index;
                    return;
                }
                lst[x] = gear[0];
                indices[x] = 0;
                x = x - 1;
            }
            self.setLst(null);
            self.setStopped(true);
        }

        protected static boolean hasLst(PProduct self) {
            return self.getLst() != null;
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends DeprecatedReduceBuiltin {

        @Specialization
        static Object reduce(PProduct self) {
            PythonLanguage language = PythonLanguage.get(null);
            Object type = GetClassNode.executeUncached(self);
            if (self.isStopped()) {
                PTuple empty = PFactory.createEmptyTuple(language);
                return PFactory.createTuple(language, new Object[]{type, PFactory.createTuple(language, new Object[]{empty})});
            }
            PTuple gearTuples = createGearTuple(self, language);
            if (self.getLst() == null) {
                return PFactory.createTuple(language, new Object[]{type, gearTuples});
            }
            PTuple indicesTuple = PFactory.createTuple(language, PythonUtils.arrayCopyOf(self.getIndices(), self.getIndices().length));
            return PFactory.createTuple(language, new Object[]{type, gearTuples, indicesTuple});
        }

        private static PTuple createGearTuple(PProduct self, PythonLanguage language) {
            PList[] lists = new PList[self.getGears().length];
            for (int i = 0; i < lists.length; i++) {
                lists[i] = PFactory.createList(language, self.getGears()[i]);
            }
            return PFactory.createTuple(language, lists);
        }
    }

    @Builtin(name = J___SETSTATE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class SetStateNode extends DeprecatedSetStateBuiltin {
        @Specialization
        static Object setState(PProduct self, Object state) {
            Object[][] gears = self.getGears();
            Object[] lst = new Object[gears.length];
            int[] indices = self.getIndices();
            for (int i = 0; i < gears.length; i++) {
                Object o = PyTupleGetItem.executeUncached(state, i);
                int index = PyLongAsIntNode.executeUncached(o);
                int gearSize = gears[i].length;
                if (indices == null || gearSize == 0) {
                    self.setStopped(true);
                    return PNone.NONE;
                }
                if (index < 0) {
                    index = 0;
                } else if (index > gearSize - 1) {
                    index = gearSize - 1;
                }
                indices[i] = index;
                lst[i] = gears[i][index];
            }
            self.setLst(lst);
            return PNone.NONE;
        }
    }
}
