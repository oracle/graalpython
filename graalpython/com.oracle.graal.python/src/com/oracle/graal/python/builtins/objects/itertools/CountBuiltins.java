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
import static com.oracle.graal.python.nodes.ErrorMessages.NUMBER_IS_REQUIRED;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___NAME__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.itertools.CountBuiltinsClinicProviders.CountNodeClinicProviderGen;
import com.oracle.graal.python.builtins.objects.str.StringUtils.SimpleTruffleStringFormatNode;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotIterNext.TpIterNextBuiltin;
import com.oracle.graal.python.lib.PyNumberAddNode;
import com.oracle.graal.python.lib.PyNumberCheckNode;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectReprAsObjectNode;
import com.oracle.graal.python.lib.PyObjectTypeCheck;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CastToJavaLongExactNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
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
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PCount})
public final class CountBuiltins extends PythonBuiltins {

    public static final TpSlots SLOTS = CountBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return CountBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = "count", minNumOfPositionalArgs = 1, parameterNames = {"cls", "start", "step"})
    @ArgumentClinic(name = "start", defaultValue = "0", useDefaultForNone = true)
    @ArgumentClinic(name = "step", defaultValue = "1", useDefaultForNone = true)
    @GenerateNodeFactory
    public abstract static class CountNode extends PythonTernaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return CountNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object construct(Object cls, Object start, Object step,
                        @Bind Node inliningTarget,
                        @Cached PyNumberCheckNode checkNode,
                        @Cached TypeNodes.IsTypeNode isTypeNode,
                        @Bind PythonLanguage language,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape,
                        @Cached PRaiseNode raiseNode) {
            if (!isTypeNode.execute(inliningTarget, cls)) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
            }
            if (!checkNode.execute(inliningTarget, start)) {
                throw raiseNode.raise(inliningTarget, TypeError, NUMBER_IS_REQUIRED);
            }
            if (!checkNode.execute(inliningTarget, step)) {
                throw raiseNode.raise(inliningTarget, TypeError, NUMBER_IS_REQUIRED);
            }
            PCount self = PFactory.createCount(language, cls, getInstanceShape.execute(cls));
            self.setCnt(start);
            self.setStep(step);
            return self;
        }
    }

    @Slot(value = SlotKind.tp_iter, isComplex = true)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object iter(PCount self) {
            return self;
        }
    }

    @Slot(value = SlotKind.tp_iternext, isComplex = true)
    @GenerateNodeFactory
    public abstract static class NextNode extends TpIterNextBuiltin {
        @Specialization
        static Object next(VirtualFrame frame, PCount self,
                        @Cached PyNumberAddNode addNode) {
            Object cnt = self.getCnt();
            self.setCnt(addNode.execute(frame, self.getCnt(), self.getStep()));
            return cnt;
        }
    }

    @Slot(value = SlotKind.tp_repr, isComplex = true)
    @GenerateNodeFactory
    public abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        static TruffleString reprPos(VirtualFrame frame, PCount self,
                        @Bind Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached PyObjectGetAttr getAttrNode,
                        @Cached PyObjectReprAsObjectNode reprNode,
                        @Cached CastToTruffleStringNode castStringNode,
                        @Cached CastToJavaLongExactNode castLongNode,
                        @Cached PyObjectTypeCheck typeCheckNode,
                        @Cached InlinedBranchProfile hasDefaultStep,
                        @Cached SimpleTruffleStringFormatNode simpleTruffleStringFormatNode) {
            Object type = getClassNode.execute(inliningTarget, self);
            TruffleString name = castStringNode.execute(inliningTarget, getAttrNode.execute(frame, inliningTarget, type, T___NAME__));
            TruffleString cntRepr = castStringNode.execute(inliningTarget, reprNode.execute(frame, inliningTarget, self.getCnt()));
            if (!typeCheckNode.execute(inliningTarget, self.getStep(), PythonBuiltinClassType.PInt) || castLongNode.execute(inliningTarget, self.getStep()) != 1) {
                hasDefaultStep.enter(inliningTarget);
                return simpleTruffleStringFormatNode.format("%s(%s, %s)", name, cntRepr, castStringNode.execute(inliningTarget, reprNode.execute(frame, inliningTarget, self.getStep())));
            }
            return simpleTruffleStringFormatNode.format("%s(%s)", name, cntRepr);
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object reducePos(PCount self,
                        @Bind Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached CastToJavaLongExactNode castLongNode,
                        @Cached PyObjectTypeCheck typeCheckNode,
                        @Cached InlinedConditionProfile hasDefaultStep,
                        @Bind PythonLanguage language) {
            warnPickleDeprecated();
            Object type = getClassNode.execute(inliningTarget, self);
            PTuple tuple;
            if (hasDefaultStep.profile(inliningTarget,
                            !typeCheckNode.execute(inliningTarget, self.getStep(), PythonBuiltinClassType.PInt) || castLongNode.execute(inliningTarget, self.getStep()) != 1)) {
                tuple = PFactory.createTuple(language, new Object[]{self.getCnt(), self.getStep()});
            } else {
                tuple = PFactory.createTuple(language, new Object[]{self.getCnt()});
            }
            return PFactory.createTuple(language, new Object[]{type, tuple});
        }
    }
}
