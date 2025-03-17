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
import static com.oracle.graal.python.nodes.ErrorMessages.IS_NOT_A;
import static com.oracle.graal.python.nodes.ErrorMessages.STATE_ARGUMENT_D_MUST_BE_A_S;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SETSTATE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___SETSTATE__;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.ToArrayNode;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.tuple.TupleBuiltins.GetItemNode;
import com.oracle.graal.python.builtins.objects.tuple.TupleBuiltins.LenNode;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotIterNext.TpIterNextBuiltin;
import com.oracle.graal.python.lib.IteratorExhausted;
import com.oracle.graal.python.lib.PyIterNextNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PCycle})
public final class CycleBuiltins extends PythonBuiltins {

    public static final TpSlots SLOTS = CycleBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return CycleBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.tp_iter, isComplex = true)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object iter(PCycle self) {
            return self;
        }
    }

    @Slot(value = SlotKind.tp_iternext, isComplex = true)
    @GenerateNodeFactory
    public abstract static class NextNode extends TpIterNextBuiltin {
        @Specialization
        static Object next(VirtualFrame frame, PCycle self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyIterNextNode nextNode,
                        @Cached InlinedBranchProfile iterableProfile,
                        @Cached InlinedBranchProfile firstPassProfile) {
            if (self.getIterable() != null) {
                iterableProfile.enter(inliningTarget);
                try {
                    Object item = nextNode.execute(frame, inliningTarget, self.getIterable());
                    if (!self.isFirstpass()) {
                        firstPassProfile.enter(inliningTarget);
                        add(self.getSaved(), item);
                    }
                    return item;
                } catch (IteratorExhausted e) {
                    self.setIterable(null);
                }
            }
            if (isEmpty(self.getSaved())) {
                throw iteratorExhausted();
            }
            Object item = get(self.getSaved(), self.getIndex());
            self.setIndex(self.getIndex() + 1);
            if (self.getIndex() >= size(self.getSaved())) {
                self.setIndex(0);
            }
            return item;
        }

        @TruffleBoundary
        private static boolean isEmpty(List<Object> l) {
            return l.isEmpty();
        }

        @TruffleBoundary
        private static Object add(List<Object> l, Object item) {
            return l.add(item);
        }

        @TruffleBoundary
        private static Object get(List<Object> l, int idx) {
            return l.get(idx);
        }

        @TruffleBoundary
        private static int size(List<Object> l) {
            return l.size();
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "hasIterable(self)")
        static Object reduce(PCycle self,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached GetClassNode getClass,
                        @Bind PythonLanguage language) {
            Object type = getClass.execute(inliningTarget, self);
            PTuple iterableTuple = PFactory.createTuple(language, new Object[]{self.getIterable()});
            PTuple tuple = PFactory.createTuple(language, new Object[]{getSavedList(self, language), self.isFirstpass()});
            return PFactory.createTuple(language, new Object[]{type, iterableTuple, tuple});
        }

        @Specialization(guards = "!hasIterable(self)")
        static Object reduceNoIterable(VirtualFrame frame, PCycle self,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached GetClassNode getClass,
                        @Cached PyObjectLookupAttr lookupAttrNode,
                        @Cached CallUnaryMethodNode callNode,
                        @Cached PyObjectGetIter getIterNode,
                        @Cached InlinedBranchProfile indexProfile,
                        @Bind PythonLanguage language) {
            Object type = getClass.execute(inliningTarget, self);
            PList savedList = getSavedList(self, language);
            Object it = getIterNode.execute(frame, inliningTarget, savedList);
            if (self.getIndex() > 0) {
                indexProfile.enter(inliningTarget);
                Object setStateCallable = lookupAttrNode.execute(frame, inliningTarget, it, T___SETSTATE__);
                callNode.executeObject(frame, setStateCallable, self.getIndex());
            }
            PTuple iteratorTuple = PFactory.createTuple(language, new Object[]{it});
            PTuple tuple = PFactory.createTuple(language, new Object[]{savedList, true});
            return PFactory.createTuple(language, new Object[]{type, iteratorTuple, tuple});
        }

        static PList getSavedList(PCycle self, PythonLanguage language) {
            return PFactory.createList(language, toArray(self.getSaved()));
        }

        @TruffleBoundary
        private static Object[] toArray(List<Object> l) {
            return l.toArray(new Object[l.size()]);
        }

        protected boolean hasIterable(PCycle self) {
            return self.getIterable() != null;
        }
    }

    @Builtin(name = J___SETSTATE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class SetStateNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object setState(VirtualFrame frame, PCycle self, Object state,
                        @Bind("this") Node inliningTarget,
                        @Cached LenNode lenNode,
                        @Cached GetItemNode getItemNode,
                        @Cached IsBuiltinObjectProfile isTypeErrorProfile,
                        @Cached ToArrayNode toArrayNode,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached PRaiseNode raiseNode) {
            if (!((state instanceof PTuple) && ((int) lenNode.execute(frame, state) == 2))) {
                throw raiseNode.raise(inliningTarget, TypeError, IS_NOT_A, "state", "2-tuple");
            }
            Object obj = getItemNode.execute(frame, state, 0);
            if (!(obj instanceof PList)) {
                throw raiseNode.raise(inliningTarget, TypeError, STATE_ARGUMENT_D_MUST_BE_A_S, 1, "Plist");
            }
            PList saved = (PList) obj;

            boolean firstPass;
            try {
                firstPass = asSizeNode.executeLossy(frame, inliningTarget, getItemNode.execute(frame, state, 1)) != 0;
            } catch (PException e) {
                e.expectTypeError(inliningTarget, isTypeErrorProfile);
                throw raiseNode.raise(inliningTarget, TypeError, STATE_ARGUMENT_D_MUST_BE_A_S, 2, "int");
            }

            Object[] savedArray = toArrayNode.execute(inliningTarget, saved.getSequenceStorage());
            self.setSaved(toList(savedArray));
            self.setFirstpass(firstPass);
            self.setIndex(0);
            return PNone.NONE;
        }

        @TruffleBoundary
        private static ArrayList<Object> toList(Object[] savedArray) {
            return new ArrayList<>(Arrays.asList(savedArray));
        }
    }

}
