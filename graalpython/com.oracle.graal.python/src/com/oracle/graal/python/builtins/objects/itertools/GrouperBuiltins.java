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
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.nodes.BuiltinNames.T_ITER;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotIterNext.TpIterNextBuiltin;
import com.oracle.graal.python.lib.PyIterNextNode;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectRichCompareBool;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
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

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PGrouper})
public final class GrouperBuiltins extends PythonBuiltins {

    public static final TpSlots SLOTS = GrouperBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return GrouperBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = "_grouper", minNumOfPositionalArgs = 2, parameterNames = {"$self", "parent", "tgtkey"})
    @GenerateNodeFactory
    public abstract static class GrouperNode extends PythonTernaryBuiltinNode {
        @Specialization
        static PGrouper construct(Object cls, Object parent, Object tgtKey,
                        @Bind Node inliningTarget,
                        @Cached InlinedConditionProfile wrongTypeProfile,
                        @Cached InlinedConditionProfile isPGroupByProfile,
                        @Cached TypeNodes.IsTypeNode isTypeNode,
                        @Bind PythonLanguage language,
                        @Cached PRaiseNode raiseNode) {
            if (!wrongTypeProfile.profile(inliningTarget, isTypeNode.execute(inliningTarget, cls))) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
            }
            if (!isPGroupByProfile.profile(inliningTarget, parent instanceof PGroupBy)) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.INCORRECT_USAGE_OF_INTERNAL_GROUPER);
            }
            return PFactory.createGrouper(language, (PGroupBy) parent, tgtKey);
        }
    }

    @Slot(value = SlotKind.tp_iter, isComplex = true)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object iter(PGrouper self) {
            return self;
        }
    }

    @Slot(value = SlotKind.tp_iternext, isComplex = true)
    @GenerateNodeFactory
    public abstract static class NextNode extends TpIterNextBuiltin {
        @Specialization
        static Object next(VirtualFrame frame, PGrouper self,
                        @Bind Node inliningTarget,
                        @Cached PyIterNextNode nextNode,
                        @Cached CallNode callNode,
                        @Cached PyObjectRichCompareBool eqNode,
                        @Cached InlinedBranchProfile currGrouperProfile,
                        @Cached InlinedBranchProfile currValueMarkerProfile,
                        @Cached InlinedBranchProfile currValueTgtProfile,
                        @Cached InlinedConditionProfile hasFuncProfile) {
            PGroupBy gbo = self.getParent();
            if (gbo.getCurrGrouper() != self) {
                currGrouperProfile.enter(inliningTarget);
                throw iteratorExhausted();
            }
            if (gbo.getCurrValue() == null) {
                currValueMarkerProfile.enter(inliningTarget);
                gbo.groupByStep(frame, inliningTarget, nextNode, callNode, hasFuncProfile);
            }
            if (!eqNode.executeEq(frame, inliningTarget, self.getTgtKey(), gbo.getCurrKey())) {
                currValueTgtProfile.enter(inliningTarget);
                throw iteratorExhausted();
            }
            Object r = gbo.getCurrValue();
            gbo.setCurrValue(null);
            return r;
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "currValueIsSelf(self)")
        static Object reduce(PGrouper self,
                        @Bind Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Bind PythonLanguage language) {
            warnPickleDeprecated();
            Object type = getClassNode.execute(inliningTarget, self);
            PTuple tuple = PFactory.createTuple(language, new Object[]{self.getParent(), self.getTgtKey()});
            return PFactory.createTuple(language, new Object[]{type, tuple});
        }

        @Specialization(guards = "!currValueIsSelf(self)")
        Object reduceCurrNotSelf(VirtualFrame frame, @SuppressWarnings("unused") PGrouper self,
                        @Bind Node inliningTarget,
                        @Cached PyObjectGetAttr getAttrNode,
                        @Bind PythonLanguage language) {
            PythonModule builtins = getContext().getCore().lookupBuiltinModule(BuiltinNames.T_BUILTINS);
            Object iterCallable = getAttrNode.execute(frame, inliningTarget, builtins, T_ITER);
            // return Py_BuildValue("N(())", _PyEval_GetBuiltinId(&PyId_iter));
            return PFactory.createTuple(language, new Object[]{iterCallable, PFactory.createTuple(language, new Object[]{PFactory.createEmptyTuple(language)})});
        }

        protected boolean currValueIsSelf(PGrouper self) {
            return self.getParent().getCurrGrouper() == self;
        }
    }
}
