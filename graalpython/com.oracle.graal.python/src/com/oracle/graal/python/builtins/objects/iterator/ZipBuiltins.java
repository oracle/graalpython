/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates.
 * Copyright (c) 2014, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.builtins.objects.iterator;

import static com.oracle.graal.python.nodes.BuiltinNames.J_ZIP;
import static com.oracle.graal.python.nodes.BuiltinNames.T_ZIP;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SETSTATE__;
import static com.oracle.graal.python.nodes.StringLiterals.T_STRICT;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

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
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotIterNext.CallSlotTpIterNextNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotIterNext.TpIterNextBuiltin;
import com.oracle.graal.python.lib.IteratorExhausted;
import com.oracle.graal.python.lib.PyIterNextNode;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PZip)
public final class ZipBuiltins extends PythonBuiltins {

    public static final TpSlots SLOTS = ZipBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ZipBuiltinsFactory.getFactories();
    }

    // zip(*iterables)
    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = J_ZIP, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class ZipNode extends PythonBuiltinNode {
        static boolean isNoneOrEmptyPKeyword(Object value) {
            return PGuards.isPNone(value) || (value instanceof PKeyword[] kw && kw.length == 0);
        }

        @Specialization(guards = "isNoneOrEmptyPKeyword(kw)")
        static PZip zip(VirtualFrame frame, Object cls, Object[] args, @SuppressWarnings("unused") Object kw,
                        @Bind Node inliningTarget,
                        @Cached.Exclusive @Cached PyObjectGetIter getIter,
                        @Cached.Shared @Cached TypeNodes.GetInstanceShape getInstanceShape) {
            return zip(frame, inliningTarget, cls, args, false, getIter, getInstanceShape);
        }

        @Specialization(guards = "kw.length == 1")
        static PZip zip(VirtualFrame frame, Object cls, Object[] args, PKeyword[] kw,
                        @Bind Node inliningTarget,
                        @Cached TruffleString.EqualNode eqNode,
                        @Cached.Exclusive @Cached PyObjectGetIter getIter,
                        @Cached PyObjectIsTrueNode isTrueNode,
                        @Cached InlinedConditionProfile profile,
                        @Cached.Shared @Cached TypeNodes.GetInstanceShape getInstanceShape,
                        @Cached.Exclusive @Cached PRaiseNode raiseNode) {
            if (profile.profile(inliningTarget, eqNode.execute(kw[0].getName(), T_STRICT, TS_ENCODING))) {
                return zip(frame, inliningTarget, cls, args, isTrueNode.execute(frame, kw[0].getValue()), getIter, getInstanceShape);
            }
            throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.S_IS_AN_INVALID_ARG_FOR_S, kw[0].getName(), T_ZIP);
        }

        @Specialization(guards = "kw.length != 1")
        static Object zip(@SuppressWarnings("unused") Object cls, @SuppressWarnings("unused") Object[] args, PKeyword[] kw,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.S_TAKES_AT_MOST_ONE_KEYWORD_ARGUMENT_D_GIVEN, T_ZIP, kw.length);
        }

        private static PZip zip(VirtualFrame frame, Node inliningTarget, Object cls, Object[] args, boolean strict, PyObjectGetIter getIter, TypeNodes.GetInstanceShape getInstanceShape) {
            Object[] iterables = new Object[args.length];
            LoopNode.reportLoopCount(inliningTarget, args.length);
            for (int i = 0; i < args.length; i++) {
                Object item = args[i];
                iterables[i] = getIter.execute(frame, inliningTarget, item);
            }
            return PFactory.createZip(PythonLanguage.get(inliningTarget), cls, getInstanceShape.execute(cls), iterables, strict);
        }
    }

    @Slot(value = SlotKind.tp_iternext, isComplex = true)
    @GenerateNodeFactory
    public abstract static class NextNode extends TpIterNextBuiltin {

        @Specialization(guards = "isEmpty(self.getIterators())")
        static Object doEmpty(@SuppressWarnings("unused") PZip self) {
            throw iteratorExhausted();
        }

        @Specialization(guards = {"!isEmpty(self.getIterators())", "!self.isStrict()"})
        static Object doNext(VirtualFrame frame, PZip self,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached GetObjectSlotsNode getSlots,
                        @Cached CallSlotTpIterNextNode callIterNext) {
            Object[] iterators = self.getIterators();
            Object[] tupleElements = new Object[iterators.length];
            for (int i = 0; i < iterators.length; i++) {
                Object it = iterators[i];
                /*
                 * Not using PyIterNext because the non-strict version should pass through existing
                 * StopIteration
                 */
                tupleElements[i] = callIterNext.execute(frame, inliningTarget, getSlots.execute(inliningTarget, it).tp_iternext(), it);
            }
            return PFactory.createTuple(language, tupleElements);
        }

        @Specialization(guards = {"!isEmpty(self.getIterators())", "self.isStrict()"})
        static Object doNext(VirtualFrame frame, PZip self,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached PyIterNextNode nextNode,
                        @Cached PRaiseNode raiseNode) {
            Object[] iterators = self.getIterators();
            Object[] tupleElements = new Object[iterators.length];
            int i = 0;
            for (; i < iterators.length; i++) {
                try {
                    tupleElements[i] = nextNode.execute(frame, inliningTarget, iterators[i]);
                } catch (IteratorExhausted e) {
                    if (i > 0) {
                        throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.ValueError, ErrorMessages.ZIP_ARG_D_IS_SHORTER_THEN_ARG_SD, i + 1, i == 1 ? " " : "s 1-", i);
                    }
                    for (i = 1; i < iterators.length; i++) {
                        try {
                            nextNode.execute(frame, inliningTarget, iterators[i]);
                        } catch (IteratorExhausted e1) {
                            continue;
                        }
                        throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.ValueError, ErrorMessages.ZIP_ARG_D_IS_LONGER_THEN_ARG_SD, i + 1, i == 1 ? " " : "s 1-", i);
                    }
                    throw e;
                }
            }
            return PFactory.createTuple(language, tupleElements);
        }
    }

    @Slot(value = SlotKind.tp_iter, isComplex = true)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object doPZip(PZip self) {
            return self;
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object reduce(PZip self,
                        @Bind Node inliningTarget,
                        @Cached InlinedConditionProfile strictProfile,
                        @Cached GetClassNode getClass,
                        @Bind PythonLanguage language) {
            Object type = getClass.execute(inliningTarget, self);
            PTuple tuple = PFactory.createTuple(language, self.getIterators());
            Object[] elements = strictProfile.profile(inliningTarget, self.isStrict()) ? new Object[]{type, tuple, true} : new Object[]{type, tuple};
            return PFactory.createTuple(language, elements);
        }
    }

    @Builtin(name = J___SETSTATE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class SetStateNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object doit(VirtualFrame frame, PZip self, Object state,
                        @Cached PyObjectIsTrueNode isTrueNode) {
            self.setStrict(isTrueNode.execute(frame, state));
            return PNone.NONE;
        }
    }
}
