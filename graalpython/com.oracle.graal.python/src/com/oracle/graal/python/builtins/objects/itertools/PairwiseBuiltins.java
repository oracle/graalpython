/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetObjectSlotsNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotIterNext.CallSlotTpIterNextNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotIterNext.TpIterNextBuiltin;
import com.oracle.graal.python.lib.IteratorExhausted;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PPairwise})
public final class PairwiseBuiltins extends PythonBuiltins {

    public static final TpSlots SLOTS = PairwiseBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PairwiseBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = "pairwise", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class PairwaiseNode extends PythonBinaryBuiltinNode {
        @Specialization
        static PPairwise construct(VirtualFrame frame, Object cls, Object iterable,
                        @Bind Node inliningTarget,
                        @Cached PyObjectGetIter getIter,
                        @Cached TypeNodes.IsTypeNode isTypeNode,
                        @Bind PythonLanguage language,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape,
                        @Cached PRaiseNode raiseNode) {
            if (!isTypeNode.execute(inliningTarget, cls)) {
                // Note: @Fallback or other @Specialization generate data-class
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
            }

            PPairwise self = PFactory.createPairwise(language, cls, getInstanceShape.execute(cls));
            self.setIterable(getIter.execute(frame, inliningTarget, iterable));
            return self;
        }
    }

    @Slot(value = SlotKind.tp_iter, isComplex = true)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object iter(PPairwise self) {
            return self;
        }
    }

    @Slot(value = SlotKind.tp_iternext, isComplex = true)
    @GenerateNodeFactory
    public abstract static class NextNode extends TpIterNextBuiltin {
        @Specialization(guards = "self.getIterable() != null")
        static Object next(VirtualFrame frame, PPairwise self,
                        @Bind Node inliningTarget,
                        @Cached GetObjectSlotsNode getSlots,
                        @Cached CallSlotTpIterNextNode callIterNext,
                        @Bind PythonLanguage language) {
            Object item;
            Object old = self.getOld();
            Object iterable = self.getIterable();
            try {
                if (self.getOld() == null) {
                    old = callIterNext.execute(frame, inliningTarget, getSlots.execute(inliningTarget, iterable).tp_iternext(), iterable);
                    self.setOld(old);
                    iterable = self.getIterable();
                    if (iterable == null) {
                        throw iteratorExhausted();
                    }
                }
                item = callIterNext.execute(frame, inliningTarget, getSlots.execute(inliningTarget, iterable).tp_iternext(), iterable);
                self.setOld(item);
                return PFactory.createTuple(language, new Object[]{old, item});
            } catch (IteratorExhausted | PException e) {
                self.setOld(null);
                self.setIterable(null);
                throw e;
            }
        }

        @Specialization(guards = "self.getIterable() == null")
        static Object next(@SuppressWarnings("unused") PPairwise self) {
            throw iteratorExhausted();
        }
    }
}
