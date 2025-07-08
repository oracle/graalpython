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
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.builtins.modules.ItertoolsModuleBuiltins.warnPickleDeprecated;
import static com.oracle.graal.python.builtins.objects.itertools.TeeDataObjectBuiltins.LINKCELLS;
import static com.oracle.graal.python.nodes.ErrorMessages.INDEX_OUT_OF_RANGE;
import static com.oracle.graal.python.nodes.ErrorMessages.INTEGER_REQUIRED_GOT;
import static com.oracle.graal.python.nodes.ErrorMessages.IS_NOT_A;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___COPY__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SETSTATE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___COPY__;

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
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.tuple.TupleBuiltins;
import com.oracle.graal.python.builtins.objects.tuple.TupleBuiltins.LenNode;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotIterNext.TpIterNextBuiltin;
import com.oracle.graal.python.lib.PyIterNextNode;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaIntLossyNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PTee})
public final class TeeBuiltins extends PythonBuiltins {

    public static final TpSlots SLOTS = TeeBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return TeeBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class NewNode extends PythonBinaryBuiltinNode {
        @Specialization
        public static PTee teeFromIterable(VirtualFrame frame, @SuppressWarnings("unused") Object cls, Object iterable,
                        @Bind Node inliningTarget,
                        @Cached PyObjectGetIter getIter,
                        @Cached InlinedConditionProfile isTeeInstanceProfile,
                        @Bind PythonLanguage language) {
            Object it = getIter.execute(frame, inliningTarget, iterable);
            if (isTeeInstanceProfile.profile(inliningTarget, it instanceof PTee)) {
                return CopyNode.copy((PTee) it, language);
            } else {
                PTeeDataObject dataObj = PFactory.createTeeDataObject(language, it);
                return PFactory.createTee(language, dataObj, 0);
            }
        }

        @NeverDefault
        protected LookupAndCallUnaryNode createCopyNode() {
            return LookupAndCallUnaryNode.create(T___COPY__);
        }
    }

    @Builtin(name = J___COPY__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class CopyNode extends PythonUnaryBuiltinNode {
        @Specialization
        public static PTee copy(PTee self,
                        @Bind PythonLanguage language) {
            return PFactory.createTee(language, self.getDataobj(), self.getIndex());
        }
    }

    @Slot(value = SlotKind.tp_iter, isComplex = true)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object iter(PTee self) {
            return self;
        }
    }

    @Slot(value = SlotKind.tp_iternext, isComplex = true)
    @ImportStatic(TeeDataObjectBuiltins.class)
    @GenerateNodeFactory
    public abstract static class NextNode extends TpIterNextBuiltin {
        @Specialization
        static Object doIt(VirtualFrame frame, PTee self,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached InlinedConditionProfile indexConditionProfile,
                        @Cached PyIterNextNode nextNode,
                        @Cached PRaiseNode raiseNode) {
            if (indexConditionProfile.profile(inliningTarget, self.getIndex() >= LINKCELLS)) {
                self.setDataObj(self.getDataobj().jumplink(language));
                self.setIndex(0);
            }
            Object value = self.getDataobj().getItem(frame, inliningTarget, self.getIndex(), nextNode, raiseNode);
            self.setIndex(self.getIndex() + 1);
            return value;
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object reduce(PTee self,
                        @Bind Node inliningTarget,
                        @Cached GetClassNode getClass,
                        @Bind PythonLanguage language) {
            warnPickleDeprecated();
            // return type(self), ((),), (self.dataobj, self.index)
            Object type = getClass.execute(inliningTarget, self);
            PTuple tuple1 = PFactory.createTuple(language, new Object[]{PFactory.createEmptyTuple(language)});
            PTuple tuple2 = PFactory.createTuple(language, new Object[]{self.getDataobj(), self.getIndex()});
            return PFactory.createTuple(language, new Object[]{type, tuple1, tuple2});
        }
    }

    @Builtin(name = J___SETSTATE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class SetStateNode extends PythonBinaryBuiltinNode {

        @Specialization
        static Object setState(VirtualFrame frame, PTee self, Object state,
                        @Bind Node inliningTarget,
                        @Cached LenNode lenNode,
                        @Cached TupleBuiltins.GetItemNode getItemNode,
                        @Cached CastToJavaIntLossyNode castToIntNode,
                        @Cached PRaiseNode raiseNode) {

            warnPickleDeprecated();
            if (!(state instanceof PTuple) || (int) lenNode.execute(frame, state) != 2) {
                throw raiseNode.raise(inliningTarget, TypeError, IS_NOT_A, "state", "2-tuple");
            }
            Object dataObject = getItemNode.execute(frame, state, 0);
            if (!(dataObject instanceof PTeeDataObject)) {
                throw raiseNode.raise(inliningTarget, TypeError, IS_NOT_A, "state", "_tee_dataobject");
            }
            self.setDataObj((PTeeDataObject) dataObject);
            Object secondElement = getItemNode.execute(frame, state, 1);
            int index = 0;
            try {
                index = castToIntNode.execute(inliningTarget, secondElement);
            } catch (CannotCastException e) {
                throw raiseNode.raise(inliningTarget, TypeError, INTEGER_REQUIRED_GOT, secondElement);
            }
            if (index < 0 || index > LINKCELLS) {
                throw raiseNode.raise(inliningTarget, ValueError, INDEX_OUT_OF_RANGE);
            }
            self.setIndex(index);
            return PNone.NONE;
        }
    }
}
