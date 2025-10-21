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
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.ToArrayNode;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotIterNext.TpIterNextBuiltin;
import com.oracle.graal.python.lib.IteratorExhausted;
import com.oracle.graal.python.lib.PyIterNextNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyTupleGetItem;
import com.oracle.graal.python.lib.PyTupleSizeNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
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

    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = "cycle", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class CycleNode extends PythonVarargsBuiltinNode {

        @Specialization
        static PCycle construct(VirtualFrame frame, Object cls, Object[] args, PKeyword[] keywords,
                        @Bind Node inliningTarget,
                        @Cached(inline = false /* uncommon path */) TypeNodes.HasObjectInitNode hasObjectInitNode,
                        @Cached PyObjectGetIter getIter,
                        @Cached TypeNodes.IsTypeNode isTypeNode,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape,
                        @Cached PRaiseNode raiseNode) {
            if (!isTypeNode.execute(inliningTarget, cls)) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
            }
            if (keywords.length > 0 && hasObjectInitNode.executeCached(cls)) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.S_TAKES_NO_KEYWORD_ARGS, "cycle()");
            }
            if (args.length != 1) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.S_EXPECTED_D_ARGS, "cycle", 1);
            }
            Object iterable = args[0];
            PCycle self = PFactory.createCycle(cls, getInstanceShape.execute(cls));
            self.setSaved(new ArrayList<>());
            self.setIterable(getIter.execute(frame, inliningTarget, iterable));
            self.setIndex(0);
            self.setFirstpass(false);
            return self;
        }
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
                        @Bind Node inliningTarget,
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
    public abstract static class ReduceNode extends DeprecatedReduceBuiltin {
        @Specialization(guards = "hasIterable(self)")
        static Object reduce(PCycle self) {
            PythonLanguage language = PythonLanguage.get(null);
            Object type = GetClassNode.executeUncached(self);
            PTuple iterableTuple = PFactory.createTuple(language, new Object[]{self.getIterable()});
            PTuple tuple = PFactory.createTuple(language, new Object[]{getSavedList(self, language), self.isFirstpass()});
            return PFactory.createTuple(language, new Object[]{type, iterableTuple, tuple});
        }

        @Specialization(guards = "!hasIterable(self)")
        static Object reduceNoIterable(PCycle self) {
            PythonLanguage language = PythonLanguage.get(null);
            Object type = GetClassNode.executeUncached(self);
            PList savedList = getSavedList(self, language);
            Object it = PyObjectGetIter.executeUncached(savedList);
            if (self.getIndex() > 0) {
                Object setStateCallable = PyObjectLookupAttr.executeUncached(it, T___SETSTATE__);
                CallUnaryMethodNode.getUncached().executeObject(setStateCallable, self.getIndex());
            }
            PTuple iteratorTuple = PFactory.createTuple(language, new Object[]{it});
            PTuple tuple = PFactory.createTuple(language, new Object[]{savedList, true});
            return PFactory.createTuple(language, new Object[]{type, iteratorTuple, tuple});
        }

        static PList getSavedList(PCycle self, PythonLanguage language) {
            List<Object> l = self.getSaved();
            return PFactory.createList(language, l.toArray(new Object[l.size()]));
        }

        protected boolean hasIterable(PCycle self) {
            return self.getIterable() != null;
        }
    }

    @Builtin(name = J___SETSTATE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class SetStateNode extends DeprecatedSetStateBuiltin {
        @Specialization
        static Object setState(PCycle self, Object state,
                        @Bind Node inliningTarget) {
            if (!((state instanceof PTuple) && (PyTupleSizeNode.executeUncached(state) == 2))) {
                throw PRaiseNode.raiseStatic(inliningTarget, TypeError, IS_NOT_A, "state", "2-tuple");
            }
            Object obj = PyTupleGetItem.executeUncached(state, 0);
            if (!(obj instanceof PList)) {
                throw PRaiseNode.raiseStatic(inliningTarget, TypeError, STATE_ARGUMENT_D_MUST_BE_A_S, 1, "Plist");
            }
            PList saved = (PList) obj;

            boolean firstPass;
            try {
                firstPass = PyNumberAsSizeNode.getUncached().executeLossy(null, null, PyTupleGetItem.executeUncached(state, 1)) != 0;
            } catch (PException e) {
                throw PRaiseNode.raiseStatic(inliningTarget, TypeError, STATE_ARGUMENT_D_MUST_BE_A_S, 2, "int");
            }

            Object[] savedArray = ToArrayNode.executeUncached(saved.getSequenceStorage());
            self.setSaved(new ArrayList<>(Arrays.asList(savedArray)));
            self.setFirstpass(firstPass);
            self.setIndex(0);
            return PNone.NONE;
        }
    }
}
