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
import static com.oracle.graal.python.nodes.ErrorMessages.ARGUMENTS_MUST_BE_ITERATORS;
import static com.oracle.graal.python.nodes.ErrorMessages.IS_NOT_A;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CLASS_GETITEM__;
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
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotIterNext.TpIterNextBuiltin;
import com.oracle.graal.python.lib.IteratorExhausted;
import com.oracle.graal.python.lib.PyIterCheckNode;
import com.oracle.graal.python.lib.PyIterNextNode;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyTupleGetItem;
import com.oracle.graal.python.lib.PyTupleSizeNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedLoopConditionProfile;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PChain})
public final class ChainBuiltins extends PythonBuiltins {

    public static final TpSlots SLOTS = ChainBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ChainBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = "chain", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class ChainNode extends PythonVarargsBuiltinNode {

        @Specialization
        static PChain construct(VirtualFrame frame, Object cls, Object[] args, PKeyword[] keywords,
                        @Bind Node inliningTarget,
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
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.S_TAKES_NO_KEYWORD_ARGS, "chain()");
            }
            PChain self = PFactory.createChain(language, cls, getInstanceShape.execute(cls));
            self.setSource(getIter.execute(frame, inliningTarget, PFactory.createList(language, args)));
            self.setActive(PNone.NONE);
            return self;
        }
    }

    @Slot(value = SlotKind.tp_iter, isComplex = true)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object iter(PChain self) {
            return self;
        }
    }

    @Slot(value = SlotKind.tp_iternext, isComplex = true)
    @GenerateNodeFactory
    public abstract static class NextNode extends TpIterNextBuiltin {
        @Specialization
        static Object next(VirtualFrame frame, PChain self,
                        @Bind Node inliningTarget,
                        @Cached PyObjectGetIter getIter,
                        @Cached PyIterNextNode nextNode,
                        @Cached InlinedBranchProfile nextExceptionProfile,
                        @Cached InlinedLoopConditionProfile loopProfile) {
            while (loopProfile.profile(inliningTarget, self.getSource() != PNone.NONE)) {
                if (self.getActive() == PNone.NONE) {
                    try {
                        Object next;
                        try {
                            next = nextNode.execute(frame, inliningTarget, self.getSource());
                        } catch (IteratorExhausted e) {
                            self.setSource(PNone.NONE);
                            throw e;
                        } catch (PException e) {
                            nextExceptionProfile.enter(inliningTarget);
                            self.setSource(PNone.NONE);
                            throw e;
                        }
                        Object iter = getIter.execute(frame, inliningTarget, next);
                        self.setActive(iter);
                    } catch (PException e) {
                        nextExceptionProfile.enter(inliningTarget);
                        self.setSource(PNone.NONE);
                        throw e;
                    }
                }
                try {
                    return nextNode.execute(frame, inliningTarget, self.getActive());
                } catch (IteratorExhausted e) {
                    self.setActive(PNone.NONE);
                }
            }
            throw iteratorExhausted();
        }
    }

    @Builtin(name = "from_iterable", minNumOfPositionalArgs = 2, isClassmethod = true)
    @GenerateNodeFactory
    public abstract static class FromIterNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object fromIter(VirtualFrame frame, @SuppressWarnings("unused") Object cls, Object arg,
                        @Bind Node inliningTarget,
                        @Cached PyObjectGetIter getIter,
                        @Bind PythonLanguage language) {
            PChain instance = PFactory.createChain(language);
            instance.setSource(getIter.execute(frame, inliningTarget, arg));
            instance.setActive(PNone.NONE);
            return instance;
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends DeprecatedReduceBuiltin {
        @Specialization
        static Object reducePos(PChain self) {
            Object type = GetClassNode.executeUncached(self);
            PythonLanguage language = PythonLanguage.get(null);
            PTuple empty = PFactory.createEmptyTuple(language);
            if (self.getSource() != PNone.NONE) {
                if (self.getActive() != PNone.NONE) {
                    PTuple tuple = PFactory.createTuple(language, new Object[]{self.getSource(), self.getActive()});
                    return PFactory.createTuple(language, new Object[]{type, empty, tuple});
                } else {
                    PTuple tuple = PFactory.createTuple(language, new Object[]{self.getSource()});
                    return PFactory.createTuple(language, new Object[]{type, empty, tuple});
                }
            } else {
                return PFactory.createTuple(language, new Object[]{type, empty});
            }
        }
    }

    @Builtin(name = J___SETSTATE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class SetStateNode extends DeprecatedSetStateBuiltin {
        @Specialization
        static Object setState(PChain self, Object state,
                        @Bind Node node) {
            if (!(state instanceof PTuple)) {
                throw PRaiseNode.raiseStatic(node, TypeError, IS_NOT_A, "state", "a length 1 or 2 tuple");
            }
            int len = PyTupleSizeNode.executeUncached(state);
            if (len < 1 || len > 2) {
                throw PRaiseNode.raiseStatic(node, TypeError, IS_NOT_A, "state", "a length 1 or 2 tuple");
            }
            Object source = PyTupleGetItem.executeUncached(state, 0);
            checkIterator(node, source);
            self.setSource(source);
            if (len == 2) {
                Object active = PyTupleGetItem.executeUncached(state, 1);
                checkIterator(node, active);
                self.setActive(active);
            }
            return PNone.NONE;
        }

        private static void checkIterator(Node node, Object obj) throws PException {
            if (!PyIterCheckNode.executeUncached(obj)) {
                throw PRaiseNode.raiseStatic(node, TypeError, ARGUMENTS_MUST_BE_ITERATORS);
            }
        }
    }

    @Builtin(name = J___CLASS_GETITEM__, minNumOfPositionalArgs = 2, isClassmethod = true)
    @GenerateNodeFactory
    public abstract static class ClassGetItemNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object classGetItem(Object cls, Object key,
                        @Bind PythonLanguage language) {
            return PFactory.createGenericAlias(language, cls, key);
        }
    }
}
