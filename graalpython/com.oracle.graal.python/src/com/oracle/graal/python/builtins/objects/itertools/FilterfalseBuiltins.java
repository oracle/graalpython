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
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetObjectSlotsNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotIterNext.CallSlotTpIterNextNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotIterNext.TpIterNextBuiltin;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.profiles.InlinedLoopConditionProfile;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PFilterfalse})
public final class FilterfalseBuiltins extends PythonBuiltins {

    public static final TpSlots SLOTS = FilterfalseBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return FilterfalseBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = "filterfalse", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class FilterFalseNode extends PythonVarargsBuiltinNode {

        @Specialization
        static PFilterfalse construct(VirtualFrame frame, Object cls, Object[] args, PKeyword[] keywords,
                        @Bind("this") Node inliningTarget,
                        @Cached(inline = false /* uncommon path */) TypeNodes.HasObjectInitNode hasObjectInitNode,
                        @Cached PyObjectGetIter getIter,
                        @Cached TypeNodes.IsTypeNode isTypeNode,
                        @Bind PythonLanguage language,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape,
                        @Cached PRaiseNode raiseNode) {
            if (!isTypeNode.execute(inliningTarget, cls)) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
            }
            if (keywords.length > 0 && hasObjectInitNode.executeCached(cls)) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.S_TAKES_NO_KEYWORD_ARGS, "filterfalse()");
            }
            if (args.length != 2) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.S_EXPECTED_D_ARGS, "filterfalse", 2);
            }
            Object func = args[0];
            Object sequence = args[1];
            PFilterfalse self = PFactory.createFilterfalse(language, cls, getInstanceShape.execute(cls));
            self.setFunc(PGuards.isPNone(func) ? null : func);
            self.setSequence(getIter.execute(frame, inliningTarget, sequence));
            return self;
        }
    }

    @Slot(value = SlotKind.tp_iter, isComplex = true)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object iter(PFilterfalse self) {
            return self;
        }
    }

    @Slot(value = SlotKind.tp_iternext, isComplex = true)
    @GenerateNodeFactory
    public abstract static class NextNode extends TpIterNextBuiltin {
        @Specialization
        static Object next(VirtualFrame frame, PFilterfalse self,
                        @Bind("this") Node inliningTarget,
                        @Cached GetObjectSlotsNode getSlots,
                        @Cached CallSlotTpIterNextNode callIterNext,
                        @Cached CallNode callNode,
                        @Cached PyObjectIsTrueNode isTrue,
                        @Cached InlinedConditionProfile hasFuncProfile,
                        @Cached InlinedLoopConditionProfile loopConditionProfile) {
            Object sequence = self.getSequence();
            TpSlot iterNext = getSlots.execute(inliningTarget, sequence).tp_iternext();
            Object func = self.getFunc();
            boolean hasFunc = hasFuncProfile.profile(inliningTarget, func != null);
            Object result;
            boolean cont;
            do {
                result = callIterNext.execute(frame, inliningTarget, iterNext, sequence);
                Object good = result;
                if (hasFunc) {
                    good = callNode.execute(frame, func, result);
                }
                cont = isTrue.execute(frame, good);
            } while (loopConditionProfile.profile(inliningTarget, cont));
            return result;
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object reduce(PFilterfalse self,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedConditionProfile hasNoFuncProfile,
                        @Cached GetClassNode getClassNode,
                        @Bind PythonLanguage language) {
            warnPickleDeprecated();
            Object func = self.getFunc();
            if (hasNoFuncProfile.profile(inliningTarget, func == null)) {
                func = PNone.NONE;
            }
            Object type = getClassNode.execute(inliningTarget, self);
            PTuple tuple = PFactory.createTuple(language, new Object[]{func, self.getSequence()});
            return PFactory.createTuple(language, new Object[]{type, tuple});
        }
    }
}
