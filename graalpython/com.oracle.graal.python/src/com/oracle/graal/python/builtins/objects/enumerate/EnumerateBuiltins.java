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
package com.oracle.graal.python.builtins.objects.enumerate;

import static com.oracle.graal.python.nodes.BuiltinNames.J_ENUMERATE;
import static com.oracle.graal.python.nodes.PGuards.isInteger;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CLASS_GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetObjectSlotsNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotIterNext.CallSlotTpIterNextNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotIterNext.TpIterNextBuiltin;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PEnumerate)
public final class EnumerateBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = EnumerateBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return EnumerateBuiltinsFactory.getFactories();
    }

    // enumerate(iterable, start=0)
    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = J_ENUMERATE, minNumOfPositionalArgs = 2, parameterNames = {"cls", "iterable", "start"})
    @GenerateNodeFactory
    public abstract static class EnumerateNode extends PythonBuiltinNode {

        @Specialization
        static PEnumerate doNone(VirtualFrame frame, Object cls, Object iterable, @SuppressWarnings("unused") PNone keywordArg,
                        @Bind Node inliningTarget,
                        @Shared("getIter") @Cached PyObjectGetIter getIter,
                        @Shared @Cached TypeNodes.GetInstanceShape getInstanceShape) {
            return PFactory.createEnumerate(cls, getInstanceShape.execute(cls), getIter.execute(frame, inliningTarget, iterable), 0);
        }

        @Specialization
        static PEnumerate doInt(VirtualFrame frame, Object cls, Object iterable, int start,
                        @Bind Node inliningTarget,
                        @Shared("getIter") @Cached PyObjectGetIter getIter,
                        @Shared @Cached TypeNodes.GetInstanceShape getInstanceShape) {
            return PFactory.createEnumerate(cls, getInstanceShape.execute(cls), getIter.execute(frame, inliningTarget, iterable), start);
        }

        @Specialization
        static PEnumerate doLong(VirtualFrame frame, Object cls, Object iterable, long start,
                        @Bind Node inliningTarget,
                        @Shared("getIter") @Cached PyObjectGetIter getIter,
                        @Shared @Cached TypeNodes.GetInstanceShape getInstanceShape) {
            return PFactory.createEnumerate(cls, getInstanceShape.execute(cls), getIter.execute(frame, inliningTarget, iterable), start);
        }

        @Specialization
        static PEnumerate doPInt(VirtualFrame frame, Object cls, Object iterable, PInt start,
                        @Bind Node inliningTarget,
                        @Shared("getIter") @Cached PyObjectGetIter getIter,
                        @Shared @Cached TypeNodes.GetInstanceShape getInstanceShape) {
            return PFactory.createEnumerate(cls, getInstanceShape.execute(cls), getIter.execute(frame, inliningTarget, iterable), start);
        }

        static boolean isIntegerIndex(Object idx) {
            return isInteger(idx) || idx instanceof PInt;
        }

        @Specialization(guards = "!isIntegerIndex(start)")
        static void enumerate(@SuppressWarnings("unused") Object cls, @SuppressWarnings("unused") Object iterable, Object start,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER, start);
        }
    }

    @Slot(value = SlotKind.tp_iternext, isComplex = true)
    @GenerateNodeFactory
    public abstract static class NextNode extends TpIterNextBuiltin {

        @Specialization
        static Object doNext(VirtualFrame frame, PEnumerate self,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached InlinedConditionProfile bigIntIndexProfile,
                        @Cached GetObjectSlotsNode getSlots,
                        @Cached CallSlotTpIterNextNode callIterNext) {
            Object index = self.getAndIncrementIndex(inliningTarget, language, bigIntIndexProfile);
            Object it = self.getDecoratedIterator();
            Object nextValue = callIterNext.execute(frame, inliningTarget, getSlots.execute(inliningTarget, it).tp_iternext(), it);
            return PFactory.createTuple(language, (new Object[]{index, nextValue}));
        }
    }

    @Slot(value = SlotKind.tp_iter, isComplex = true)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object doGeneric(PEnumerate self) {
            return self;
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object reduce(PEnumerate self,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached InlinedConditionProfile bigIntIndexProfile,
                        @Cached GetClassNode getClassNode) {
            Object iterator = self.getDecoratedIterator();
            Object index = self.getIndex(inliningTarget, bigIntIndexProfile);
            PTuple contents = PFactory.createTuple(language, new Object[]{iterator, index});
            return PFactory.createTuple(language, new Object[]{getClassNode.execute(inliningTarget, self), contents});
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
