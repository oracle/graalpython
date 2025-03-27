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
package com.oracle.graal.python.builtins.objects.reversed;

import static com.oracle.graal.python.nodes.BuiltinNames.J_REVERSED;
import static com.oracle.graal.python.nodes.ErrorMessages.OBJ_HAS_NO_LEN;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___LENGTH_HINT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NEW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SETSTATE__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.addExact;
import static com.oracle.graal.python.util.PythonUtils.multiplyExact;
import static com.oracle.graal.python.util.PythonUtils.negateExact;
import static com.oracle.graal.python.util.PythonUtils.subtractExact;

import java.math.BigInteger;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.iterator.PBigRangeIterator;
import com.oracle.graal.python.builtins.objects.iterator.PBuiltinIterator;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.range.PBigRange;
import com.oracle.graal.python.builtins.objects.range.PIntRange;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotIterNext.TpIterNextBuiltin;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.lib.PySequenceCheckNode;
import com.oracle.graal.python.lib.PySequenceGetItemNode;
import com.oracle.graal.python.lib.PySequenceSizeNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.call.special.LookupSpecialMethodSlotNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PReverseIterator)
public final class ReversedBuiltins extends PythonBuiltins {

    /*
     * "extendClasses" only needs one of the set of Java classes that are mapped to the Python
     * class.
     */

    public static final TpSlots SLOTS = ReversedBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ReversedBuiltinsFactory.getFactories();
    }

    // reversed(seq)
    @Builtin(name = J___NEW__, raiseErrorName = J_REVERSED, minNumOfPositionalArgs = 2, constructsClass = PythonBuiltinClassType.PReverseIterator)
    @GenerateNodeFactory
    @ImportStatic(SpecialMethodSlot.class)
    public abstract static class ReversedNode extends PythonBuiltinNode {

        @Specialization
        static PythonObject reversed(@SuppressWarnings("unused") Object cls, PIntRange range,
                        @Bind("this") Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached InlinedBranchProfile overflowProfile) {
            int lstart = range.getIntStart();
            int lstep = range.getIntStep();
            int ulen = range.getIntLength();
            try {
                int new_stop = subtractExact(lstart, lstep);
                int new_start = addExact(new_stop, multiplyExact(ulen, lstep));
                return PFactory.createIntRangeIterator(language, new_start, new_stop, negateExact(lstep), ulen);
            } catch (OverflowException e) {
                overflowProfile.enter(inliningTarget);
                return handleOverflow(language, lstart, lstep, ulen);
            }
        }

        @CompilerDirectives.TruffleBoundary
        private static PBigRangeIterator handleOverflow(PythonLanguage language, int lstart, int lstep, int ulen) {
            BigInteger bstart = BigInteger.valueOf(lstart);
            BigInteger bstep = BigInteger.valueOf(lstep);
            BigInteger blen = BigInteger.valueOf(ulen);
            BigInteger new_stop = bstart.subtract(bstep);
            BigInteger new_start = new_stop.add(blen.multiply(bstep));

            return PFactory.createBigRangeIterator(language, new_start, new_stop, bstep.negate(), blen);
        }

        @Specialization
        @CompilerDirectives.TruffleBoundary
        static PythonObject reversed(@SuppressWarnings("unused") Object cls, PBigRange range) {
            BigInteger lstart = range.getBigIntegerStart();
            BigInteger lstep = range.getBigIntegerStep();
            BigInteger ulen = range.getBigIntegerLength();

            BigInteger new_stop = lstart.subtract(lstep);
            BigInteger new_start = new_stop.add(ulen.multiply(lstep));

            return PFactory.createBigRangeIterator(PythonLanguage.get(null), new_start, new_stop, lstep.negate(), ulen);
        }

        @Specialization
        static PythonObject reversed(Object cls, PString value,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringNode castToStringNode,
                        @Bind PythonLanguage language,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape) {
            return PFactory.createStringReverseIterator(language, cls, getInstanceShape.execute(cls), castToStringNode.execute(inliningTarget, value));
        }

        @Specialization
        static PythonObject reversed(Object cls, TruffleString value,
                        @Bind PythonLanguage language,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape) {
            return PFactory.createStringReverseIterator(language, cls, getInstanceShape.execute(cls), value);
        }

        @Specialization(guards = {"!isString(sequence)", "!isPRange(sequence)"})
        static Object reversed(VirtualFrame frame, Object cls, Object sequence,
                        @Bind("this") Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached("create(Reversed)") LookupSpecialMethodSlotNode lookupReversed,
                        @Cached CallUnaryMethodNode callReversed,
                        @Cached PySequenceSizeNode pySequenceSizeNode,
                        @Cached InlinedConditionProfile noReversedProfile,
                        @Cached PySequenceCheckNode pySequenceCheck,
                        @Bind PythonLanguage language,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape,
                        @Cached PRaiseNode raiseNode) {
            Object sequenceKlass = getClassNode.execute(inliningTarget, sequence);
            Object reversed = lookupReversed.execute(frame, sequenceKlass, sequence);
            if (noReversedProfile.profile(inliningTarget, reversed == PNone.NO_VALUE)) {
                if (!pySequenceCheck.execute(inliningTarget, sequence)) {
                    throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.OBJ_ISNT_REVERSIBLE, sequence);
                } else {
                    int lengthHint = pySequenceSizeNode.execute(frame, inliningTarget, sequence);
                    return PFactory.createSequenceReverseIterator(language, cls, getInstanceShape.execute(cls), sequence, lengthHint);
                }
            } else {
                return callReversed.executeObject(frame, reversed, sequence);
            }
        }
    }

    @Slot(value = SlotKind.tp_iternext, isComplex = true)
    @GenerateNodeFactory
    public abstract static class NextNode extends TpIterNextBuiltin {

        @Specialization(guards = "self.isExhausted()")
        static Object exhausted(@SuppressWarnings("unused") PBuiltinIterator self) {
            throw iteratorExhausted();
        }

        @Specialization(guards = "!self.isExhausted()")
        static Object next(VirtualFrame frame, PSequenceReverseIterator self,
                        @Bind("this") Node inliningTarget,
                        @Cached PySequenceGetItemNode getItemNode,
                        @Cached IsBuiltinObjectProfile profile) {
            if (self.index >= 0) {
                try {
                    return getItemNode.execute(frame, self.getObject(), self.index--);
                } catch (PException e) {
                    e.expectIndexError(inliningTarget, profile);
                }
            }
            self.setExhausted();
            throw iteratorExhausted();
        }

        @Specialization(guards = "!self.isExhausted()")
        static Object next(PStringReverseIterator self,
                        @Cached TruffleString.SubstringNode substringNode) {
            if (self.index >= 0) {
                return substringNode.execute(self.value, self.index--, 1, TS_ENCODING, false);
            }
            self.setExhausted();
            throw iteratorExhausted();
        }
    }

    @Slot(value = SlotKind.tp_iter, isComplex = true)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object doGeneric(Object self) {
            return self;
        }
    }

    @Builtin(name = J___LENGTH_HINT__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class LengthHintNode extends PythonUnaryBuiltinNode {

        @Specialization(guards = "self.isExhausted()")
        static int exhausted(@SuppressWarnings("unused") PBuiltinIterator self) {
            return 0;
        }

        @Specialization(guards = "!self.isExhausted()")
        static int lengthHint(PStringReverseIterator self) {
            return self.index + 1;
        }

        @Specialization(guards = {"!self.isExhausted()", "self.isPSequence()"})
        static int lengthHint(PSequenceReverseIterator self,
                        @Bind("this") Node inliningTarget,
                        @Cached SequenceNodes.LenNode lenNode,
                        @Cached PRaiseNode raiseNode) {
            int len = lenNode.execute(inliningTarget, self.getPSequence());
            if (len == -1) {
                throw raiseNode.raise(inliningTarget, TypeError, OBJ_HAS_NO_LEN, self);
            }
            if (len < self.index) {
                return 0;
            }
            return self.index + 1;
        }

        @Specialization(guards = {"!self.isExhausted()", "!self.isPSequence()"})
        static int lengthHint(VirtualFrame frame, PSequenceReverseIterator self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectSizeNode sizeNode) {
            int len = sizeNode.execute(frame, inliningTarget, self.getObject());
            if (len < self.index) {
                return 0;
            }
            return self.index + 1;
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object reduce(PStringReverseIterator self,
                        @Bind("this") Node inliningTarget,
                        @Shared("getClassNode") @Cached GetClassNode getClassNode,
                        @Bind PythonLanguage language) {
            if (self.isExhausted()) {
                return reduceInternal(inliningTarget, self, "", null, getClassNode, language);
            }
            return reduceInternal(inliningTarget, self, self.value, self.index, getClassNode, language);
        }

        @Specialization(guards = "self.isPSequence()")
        static Object reduce(PSequenceReverseIterator self,
                        @Bind("this") Node inliningTarget,
                        @Shared("getClassNode") @Cached GetClassNode getClassNode,
                        @Bind PythonLanguage language) {
            if (self.isExhausted()) {
                return reduceInternal(inliningTarget, self, PFactory.createList(language), null, getClassNode, language);
            }
            return reduceInternal(inliningTarget, self, self.getPSequence(), self.index, getClassNode, language);
        }

        @Specialization(guards = "!self.isPSequence()")
        static Object reduce(VirtualFrame frame, PSequenceReverseIterator self,
                        @Bind("this") Node inliningTarget,
                        @Cached("create(T___REDUCE__)") LookupAndCallUnaryNode callReduce,
                        @Shared("getClassNode") @Cached GetClassNode getClassNode,
                        @Bind PythonLanguage language) {
            Object content = callReduce.executeObject(frame, self.getObject());
            return reduceInternal(inliningTarget, self, content, self.index, getClassNode, language);
        }

        private static PTuple reduceInternal(Node inliningTarget, Object self, Object arg, Object state, GetClassNode getClassNode, PythonLanguage language) {
            Object revIter = getClassNode.execute(inliningTarget, self);
            PTuple args = PFactory.createTuple(language, new Object[]{arg});
            // callable, args, state (optional)
            if (state != null) {
                return PFactory.createTuple(language, new Object[]{revIter, args, state});
            } else {
                return PFactory.createTuple(language, new Object[]{revIter, args});
            }
        }
    }

    @Builtin(name = J___SETSTATE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class SetStateNode extends PythonBinaryBuiltinNode {
        @Specialization
        public static Object setState(VirtualFrame frame, PBuiltinIterator self, Object index,
                        @Bind("this") Node inliningTarget,
                        @Cached PyNumberAsSizeNode asSizeNode) {
            int idx = asSizeNode.executeExact(frame, inliningTarget, index);
            if (idx < -1) {
                idx = -1;
            }
            self.index = idx;
            return PNone.NONE;
        }
    }
}
