/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.ordereddict;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.KeyError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.RuntimeError;
import static com.oracle.graal.python.nodes.BuiltinNames.T_ITER;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotIterNext.TpIterNextBuiltin;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.builtins.ListNodes;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

@CoreFunctions(extendClasses = PythonBuiltinClassType.POrderedDictIterator)
public class OrderedDictIteratorBuiltins extends PythonBuiltins {

    public static final TpSlots SLOTS = OrderedDictIteratorBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return OrderedDictIteratorBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.tp_iter, isComplex = true)
    @GenerateNodeFactory
    abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object iter(POrderedDictIterator self) {
            return self;
        }
    }

    @Slot(value = SlotKind.tp_iternext, isComplex = true)
    @GenerateNodeFactory
    abstract static class NextNode extends TpIterNextBuiltin {
        @Specialization
        static Object next(VirtualFrame frame, POrderedDictIterator self,
                        @Bind("this") Node inliningTarget,
                        @Cached PRaiseNode raiseNode,
                        @Cached HashingStorageNodes.HashingStorageGetItemWithHash getItem) {
            if (self.current == null) {
                throw iteratorExhausted();
            }
            if (self.size != self.dict.nodes.size()) {
                throw raiseNode.raise(inliningTarget, RuntimeError, ErrorMessages.CHANGED_SIZE_DURING_ITERATION, "OrderedDict");
            }
            Object result;
            Object key = self.current.key;
            if (self.type == POrderedDictIterator.IteratorType.KEYS) {
                result = key;
            } else {
                Object value = getItem.execute(frame, inliningTarget, self.dict.getDictStorage(), key, self.current.hash);
                if (value == null) {
                    throw raiseNode.raise(inliningTarget, KeyError, new Object[]{key});
                }
                if (self.type == POrderedDictIterator.IteratorType.VALUES) {
                    result = value;
                } else {
                    result = PFactory.createTuple(PythonLanguage.get(inliningTarget), new Object[]{key, value});
                }
            }
            if (!self.reversed) {
                self.current = self.current.next;
            } else {
                self.current = self.current.prev;
            }
            return result;
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReduceNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object reduce(VirtualFrame frame, POrderedDictIterator self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectGetAttr getAttr,
                        @Bind PythonLanguage language,
                        @Cached ListNodes.ConstructListNode constructListNode) {
            PythonContext context = PythonContext.get(inliningTarget);
            Object iterBuiltin = getAttr.execute(frame, inliningTarget, context.getBuiltins(), T_ITER);
            POrderedDictIterator copy = PFactory.createOrderedDictIterator(language, self.dict, self.type, self.reversed);
            copy.current = self.current;
            /* iterate the temporary into a list */
            PList list = constructListNode.execute(frame, copy);
            PTuple args = PFactory.createTuple(language, new Object[]{list});
            return PFactory.createTuple(language, new Object[]{iterBuiltin, args});
        }
    }
}
