/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
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
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotIterNext.TpIterNextBuiltin;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectRichCompareBool;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PSentinelIterator)
public final class SentinelIteratorBuiltins extends PythonBuiltins {

    public static final TpSlots SLOTS = SentinelIteratorBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return SentinelIteratorBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.tp_iternext, isComplex = true)
    @GenerateNodeFactory
    public abstract static class NextNode extends TpIterNextBuiltin {
        @Specialization
        static Object doIterator(VirtualFrame frame, PSentinelIterator iterator,
                        @Bind Node inliningTarget,
                        @Cached CallNode callNode,
                        @Cached IsBuiltinObjectProfile errorProfile,
                        @Cached PyObjectRichCompareBool eqNode) {
            if (iterator.sentinelReached()) {
                throw iteratorExhausted();
            }
            Object nextValue;
            try {
                nextValue = callNode.execute(frame, iterator.getCallable());
            } catch (PException e) {
                e.expectStopIteration(inliningTarget, errorProfile);
                iterator.markSentinelReached();
                throw iteratorExhausted();
            }
            boolean iteratorDone = eqNode.executeEq(frame, inliningTarget, nextValue, iterator.getSentinel());
            if (iteratorDone) {
                iterator.markSentinelReached();
                throw iteratorExhausted();
            }
            return nextValue;
        }
    }

    @Slot(value = SlotKind.tp_iter, isComplex = true)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object doPSentinelIterator(PSentinelIterator self) {
            return self;
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReduceNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object reduce(VirtualFrame frame, PSentinelIterator self,
                        @Bind Node inliningTarget,
                        @Cached PyObjectGetAttr getAttr,
                        @Bind PythonLanguage language) {
            PythonModule builtins = PythonContext.get(inliningTarget).getBuiltins();
            Object iter = getAttr.execute(frame, inliningTarget, builtins, T_ITER);
            Object[] args;
            if (self.sentinelReached()) {
                args = new Object[]{PFactory.createEmptyTuple(language)};
            } else {
                args = new Object[]{self.getCallable(), self.getSentinel()};
            }
            return PFactory.createTuple(language, new Object[]{iter, PFactory.createTuple(language, args)});
        }
    }
}
