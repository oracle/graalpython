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
package com.oracle.graal.python.builtins.objects.range;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.IndexError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OverflowError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.builtins.objects.common.IndexNodes.checkBounds;
import static com.oracle.graal.python.nodes.BuiltinNames.J_RANGE;
import static com.oracle.graal.python.nodes.ErrorMessages.ARG_MUST_NOT_BE_ZERO;
import static com.oracle.graal.python.nodes.ErrorMessages.RANGE_OUT_OF_BOUNDS;
import static com.oracle.graal.python.nodes.PGuards.isNoValue;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.math.BigInteger;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.SysModuleBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.common.IndexNodes;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.range.RangeNodes.CoerceToBigRange;
import com.oracle.graal.python.builtins.objects.range.RangeNodes.LenOfIntRangeNodeExact;
import com.oracle.graal.python.builtins.objects.range.RangeNodes.PRangeStartNode;
import com.oracle.graal.python.builtins.objects.range.RangeNodes.PRangeStepNode;
import com.oracle.graal.python.builtins.objects.range.RangeNodes.PRangeStopNode;
import com.oracle.graal.python.builtins.objects.slice.PObjectSlice;
import com.oracle.graal.python.builtins.objects.slice.PObjectSlice.SliceObjectInfo;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.slice.PSlice.SliceInfo;
import com.oracle.graal.python.builtins.objects.slice.SliceNodes.CoerceToObjectSlice;
import com.oracle.graal.python.builtins.objects.slice.SliceNodes.ComputeIndices;
import com.oracle.graal.python.builtins.objects.str.StringUtils.SimpleTruffleStringFormatNode;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryFunc.MpSubscriptBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotHashFun.HashBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotInquiry.NbBoolBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotLen.LenBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotRichCompare.RichCmpBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSizeArgFun.SqItemBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSqContains.SqContainsBuiltinNode;
import com.oracle.graal.python.lib.IteratorExhausted;
import com.oracle.graal.python.lib.PyIndexCheckNode;
import com.oracle.graal.python.lib.PyIterNextNode;
import com.oracle.graal.python.lib.PyLongCheckExactNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyNumberIndexNode;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectHashNode;
import com.oracle.graal.python.lib.PyObjectReprAsTruffleStringNode;
import com.oracle.graal.python.lib.PyObjectRichCompareBool;
import com.oracle.graal.python.lib.RichCmpOp;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.truffle.PythonIntegerTypes;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaBigIntegerNode;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PRange)
public final class RangeBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = RangeBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return RangeBuiltinsFactory.getFactories();
    }

    // range(stop)
    // range(start, stop[, step])
    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = J_RANGE, minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    @ReportPolymorphism
    public abstract static class RangeNode extends PythonQuaternaryBuiltinNode {
        // stop
        @Specialization(guards = "isStop(start, stop, step)")
        static Object doIntStop(Object cls, int stop, @SuppressWarnings("unused") PNone start, @SuppressWarnings("unused") PNone step,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Shared("exceptionProfile") @Cached InlinedBranchProfile exceptionProfile,
                        @Shared("lenOfRangeNodeExact") @Cached LenOfIntRangeNodeExact lenOfRangeNodeExact,
                        @Shared("createBigRangeNode") @Cached RangeNodes.CreateBigRangeNode createBigRangeNode,
                        @Shared @Cached PRaiseNode raiseNode) {
            return doInt(cls, 0, stop, 1, inliningTarget, language, exceptionProfile, lenOfRangeNodeExact, createBigRangeNode, raiseNode);
        }

        @Specialization(guards = "isStop(start, stop, step)")
        static Object doPintStop(Object cls, PInt stop, @SuppressWarnings("unused") PNone start, @SuppressWarnings("unused") PNone step,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Shared("lenOfRangeNode") @Cached RangeNodes.LenOfRangeNode lenOfRangeNode,
                        @Shared @Cached PRaiseNode raiseNode) {
            return doPint(cls, PFactory.createInt(language, BigInteger.ZERO), stop, PFactory.createInt(language, BigInteger.ONE), inliningTarget, language, lenOfRangeNode, raiseNode);
        }

        @Specialization(guards = "isStop(start, stop, step)")
        static Object doGenericStop(VirtualFrame frame, Object cls, Object stop, @SuppressWarnings("unused") PNone start, @SuppressWarnings("unused") PNone step,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Shared("exceptionProfile") @Cached InlinedBranchProfile exceptionProfile,
                        @Shared("lenOfRangeNodeExact") @Cached LenOfIntRangeNodeExact lenOfRangeNodeExact,
                        @Shared("createBigRangeNode") @Cached RangeNodes.CreateBigRangeNode createBigRangeNode,
                        @Shared("cast") @Cached CastToJavaIntExactNode cast,
                        @Shared("overflowProfile") @Cached IsBuiltinObjectProfile overflowProfile,
                        @Shared("indexNode") @Cached PyNumberIndexNode indexNode,
                        @Shared @Cached PRaiseNode raiseNode) {
            return doGeneric(frame, cls, 0, stop, 1, inliningTarget, language, exceptionProfile, lenOfRangeNodeExact, createBigRangeNode, cast, overflowProfile, indexNode, raiseNode);
        }

        // start stop
        @Specialization(guards = "isStartStop(start, stop, step)")
        static Object doIntStartStop(Object cls, int start, int stop, @SuppressWarnings("unused") PNone step,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Shared("exceptionProfile") @Cached InlinedBranchProfile exceptionProfile,
                        @Shared("lenOfRangeNodeExact") @Cached LenOfIntRangeNodeExact lenOfRangeNodeExact,
                        @Shared("createBigRangeNode") @Cached RangeNodes.CreateBigRangeNode createBigRangeNode,
                        @Shared @Cached PRaiseNode raiseNode) {
            return doInt(cls, start, stop, 1, inliningTarget, language, exceptionProfile, lenOfRangeNodeExact, createBigRangeNode, raiseNode);
        }

        @Specialization(guards = "isStartStop(start, stop, step)")
        static Object doPintStartStop(Object cls, PInt start, PInt stop, @SuppressWarnings("unused") PNone step,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Shared("lenOfRangeNode") @Cached RangeNodes.LenOfRangeNode lenOfRangeNode,
                        @Shared @Cached PRaiseNode raiseNode) {
            return doPint(cls, start, stop, PFactory.createInt(language, BigInteger.ONE), inliningTarget, language, lenOfRangeNode, raiseNode);
        }

        @Specialization(guards = "isStartStop(start, stop, step)")
        static Object doGenericStartStop(VirtualFrame frame, Object cls, Object start, Object stop, @SuppressWarnings("unused") PNone step,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Shared("exceptionProfile") @Cached InlinedBranchProfile exceptionProfile,
                        @Shared("lenOfRangeNodeExact") @Cached LenOfIntRangeNodeExact lenOfRangeNodeExact,
                        @Shared("createBigRangeNode") @Cached RangeNodes.CreateBigRangeNode createBigRangeNode,
                        @Shared("cast") @Cached CastToJavaIntExactNode cast,
                        @Shared("overflowProfile") @Cached IsBuiltinObjectProfile overflowProfile,
                        @Shared("indexNode") @Cached PyNumberIndexNode indexNode,
                        @Shared @Cached PRaiseNode raiseNode) {
            return doGeneric(frame, cls, start, stop, 1, inliningTarget, language, exceptionProfile, lenOfRangeNodeExact, createBigRangeNode, cast, overflowProfile, indexNode, raiseNode);
        }

        // start stop step
        @Specialization
        static Object doInt(@SuppressWarnings("unused") Object cls, int start, int stop, int step,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Shared("exceptionProfile") @Cached InlinedBranchProfile exceptionProfile,
                        @Shared("lenOfRangeNodeExact") @Cached LenOfIntRangeNodeExact lenOfRangeNode,
                        @Shared("createBigRangeNode") @Cached RangeNodes.CreateBigRangeNode createBigRangeNode,
                        @Shared @Cached PRaiseNode raiseNode) {
            if (step == 0) {
                throw raiseNode.raise(inliningTarget, PythonErrorType.ValueError, ARG_MUST_NOT_BE_ZERO, "range()", 3);
            }
            try {
                int len = lenOfRangeNode.executeInt(inliningTarget, start, stop, step);
                return PFactory.createIntRange(language, start, stop, step, len);
            } catch (OverflowException e) {
                exceptionProfile.enter(inliningTarget);
                return createBigRangeNode.execute(inliningTarget, start, stop, step);
            }
        }

        @Specialization
        static Object doPint(@SuppressWarnings("unused") Object cls, PInt start, PInt stop, PInt step,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Shared("lenOfRangeNode") @Cached RangeNodes.LenOfRangeNode lenOfRangeNode,
                        @Shared @Cached PRaiseNode raiseNode) {
            if (step.isZero()) {
                throw raiseNode.raise(inliningTarget, PythonErrorType.ValueError, ARG_MUST_NOT_BE_ZERO, "range()", 3);
            }
            BigInteger len = lenOfRangeNode.execute(inliningTarget, start.getValue(), stop.getValue(), step.getValue());
            return PFactory.createBigRange(language, start, stop, step, PFactory.createInt(language, len));
        }

        @Specialization(guards = "isStartStopStep(start, stop, step)")
        static Object doGeneric(VirtualFrame frame, @SuppressWarnings("unused") Object cls, Object start, Object stop, Object step,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Shared("exceptionProfile") @Cached InlinedBranchProfile exceptionProfile,
                        @Shared("lenOfRangeNodeExact") @Cached LenOfIntRangeNodeExact lenOfRangeNodeExact,
                        @Shared("createBigRangeNode") @Cached RangeNodes.CreateBigRangeNode createBigRangeNode,
                        @Shared("cast") @Cached CastToJavaIntExactNode cast,
                        @Shared("overflowProfile") @Cached IsBuiltinObjectProfile overflowProfile,
                        @Shared("indexNode") @Cached PyNumberIndexNode indexNode,
                        @Shared @Cached PRaiseNode raiseNode) {
            Object lstart = indexNode.execute(frame, inliningTarget, start);
            Object lstop = indexNode.execute(frame, inliningTarget, stop);
            Object lstep = indexNode.execute(frame, inliningTarget, step);

            try {
                int istart = cast.execute(inliningTarget, lstart);
                int istop = cast.execute(inliningTarget, lstop);
                int istep = cast.execute(inliningTarget, lstep);
                return doInt(cls, istart, istop, istep, inliningTarget, language, exceptionProfile, lenOfRangeNodeExact, createBigRangeNode, raiseNode);
            } catch (PException e) {
                e.expect(inliningTarget, PythonErrorType.OverflowError, overflowProfile);
                return createBigRangeNode.execute(inliningTarget, lstart, lstop, lstep);
            }
        }

        protected static boolean isStop(Object start, Object stop, Object step) {
            return isNoValue(start) && !isNoValue(stop) && isNoValue(step);
        }

        protected static boolean isStartStop(Object start, Object stop, Object step) {
            return !isNoValue(start) && !isNoValue(stop) && isNoValue(step);
        }

        protected static boolean isStartStopStep(Object start, Object stop, Object step) {
            return !isNoValue(start) && !isNoValue(stop) && !isNoValue(step);
        }
    }

    @Slot(value = SlotKind.tp_hash, isComplex = true)
    @GenerateNodeFactory
    public abstract static class HashNode extends HashBuiltinNode {
        @Specialization
        static long hash(VirtualFrame frame, PIntRange self,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Shared("hashNode") @Cached PyObjectHashNode hashNode) {
            Object[] content = new Object[3];
            int intLength = self.getIntLength();
            content[0] = intLength;
            if (intLength == 0) {
                content[1] = PNone.NONE;
                content[2] = PNone.NONE;
            } else if (intLength == 1) {
                content[1] = self.getIntStart();
                content[2] = PNone.NONE;
            } else {
                content[1] = self.getIntStart();
                content[2] = self.getIntStep();
            }
            return hashNode.execute(frame, inliningTarget, PFactory.createTuple(language, content));
        }

        @Specialization
        static long hash(VirtualFrame frame, PBigRange self,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Shared("hashNode") @Cached PyObjectHashNode hashNode) {
            Object[] content = new Object[3];
            PInt length = self.getPIntLength();
            content[0] = length;
            if (length.compareTo(BigInteger.ZERO) == 0) {
                content[1] = PNone.NONE;
                content[2] = PNone.NONE;
            } else if (length.compareTo(BigInteger.ONE) == 0) {
                content[1] = self.getStart();
                content[2] = PNone.NONE;
            } else {
                content[1] = self.getStart();
                content[2] = self.getStep();
            }
            return hashNode.execute(frame, inliningTarget, PFactory.createTuple(language, content));
        }
    }

    @Slot(value = SlotKind.tp_repr, isComplex = true)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        public static TruffleString repr(PRange self,
                        @Bind Node inliningTarget,
                        @Cached PyObjectReprAsTruffleStringNode repr,
                        @Cached SimpleTruffleStringFormatNode simpleTruffleStringFormatNode) {
            TruffleString start = repr.execute(null, inliningTarget, self.getStart());
            TruffleString stop = repr.execute(null, inliningTarget, self.getStop());
            if (self.withStep()) {
                return simpleTruffleStringFormatNode.format("range(%s, %s, %s)", start, stop, repr.execute(null, inliningTarget, self.getStep()));
            } else {
                return simpleTruffleStringFormatNode.format("range(%s, %s)", start, stop);
            }
        }
    }

    @Slot(value = SlotKind.sq_length, isComplex = true)
    @Slot(value = SlotKind.mp_length, isComplex = true)
    @GenerateUncached
    @GenerateNodeFactory
    public abstract static class LenNode extends LenBuiltinNode {
        public abstract int execute(VirtualFrame frame, PRange range);

        public final int execute(PRange range) {
            return execute(null, range);
        }

        @Specialization
        static int doPIntRange(PIntRange self) {
            return self.getIntLength();
        }

        @Specialization
        static int doPBigRange(VirtualFrame frame, PBigRange self,
                        @Bind Node inliningTarget,
                        @Cached PyIndexCheckNode indexCheckNode,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached PRaiseNode raiseNode) {
            Object length = self.getLength();
            if (indexCheckNode.execute(inliningTarget, length)) {
                return asSizeNode.executeExact(frame, inliningTarget, length);
            }
            throw raiseNode.raise(inliningTarget, OverflowError, ErrorMessages.CANNOT_FIT_P_INTO_INDEXSIZED_INT, length);
        }

        @NeverDefault
        public static LenNode create() {
            return RangeBuiltinsFactory.LenNodeFactory.create();
        }

        public static LenNode getUncached() {
            return RangeBuiltinsFactory.LenNodeFactory.getUncached();
        }
    }

    @Slot(SlotKind.nb_bool)
    @GenerateUncached
    @GenerateNodeFactory
    abstract static class BoolNode extends NbBoolBuiltinNode {
        @Specialization
        boolean doPIntRange(PIntRange self) {
            return self.getIntLength() != 0;
        }

        @Specialization
        @TruffleBoundary
        boolean doPBigRange(PBigRange self) {
            return self.getBigIntegerLength().compareTo(BigInteger.ZERO) != 0;
        }
    }

    @Slot(value = SlotKind.tp_iter, isComplex = true)
    @GenerateNodeFactory
    abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doPIntRange(PIntRange self,
                        @Bind PythonLanguage language) {
            return PFactory.createIntRangeIterator(language, self);
        }

        @Specialization
        static Object doPIntRange(PBigRange self,
                        @Bind PythonLanguage language) {
            return PFactory.createBigRangeIterator(language, self);
        }
    }

    @Builtin(name = "start", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class StartNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object start(PRange self,
                        @Bind Node inliningTarget,
                        @Cached PRangeStartNode get) {
            return get.execute(inliningTarget, self);
        }
    }

    @Builtin(name = "step", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class StepNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object step(PRange self,
                        @Bind Node inliningTarget,
                        @Cached PRangeStepNode get) {
            return get.execute(inliningTarget, self);
        }
    }

    @Builtin(name = "stop", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class StopNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object stop(PRange self,
                        @Bind Node inliningTarget,
                        @Cached PRangeStopNode get) {
            return get.execute(inliningTarget, self);
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object reduce(PRange self,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached GetClassNode getClassNode) {
            PTuple args = PFactory.createTuple(language, new Object[]{self.getStart(), self.getStop(), self.getStep()});
            return PFactory.createTuple(language, new Object[]{getClassNode.execute(inliningTarget, self), args});
        }
    }

    @Slot(value = SlotKind.tp_richcompare, isComplex = true)
    @GenerateNodeFactory
    abstract static class EqNode extends RichCmpBuiltinNode {
        private static boolean eqInt(PIntRange range, int len, int start, int step) {
            return eqInt(range.getIntLength(), range.getIntStart(), range.getIntStep(),
                            len, start, step);
        }

        private static boolean eqInt(int llen, int lstart, int lstep, int rlen, int rstart, int rstep) {
            if (llen != rlen) {
                return false;
            }
            if (llen == 0) {
                return true;
            }
            if (lstart != rstart) {
                return false;
            }
            // same start, just one element => step does not matter
            if (llen == 1) {
                return true;
            }
            return lstep == rstep;
        }

        @TruffleBoundary
        private static boolean eqBigInt(BigInteger llen, BigInteger lstart, BigInteger lstep, BigInteger rlen, BigInteger rstart, BigInteger rstep) {
            if (llen.compareTo(rlen) != 0) {
                return false;
            }
            if (llen.compareTo(BigInteger.ZERO) == 0) {
                return true;
            }
            if (lstart.compareTo(rstart) != 0) {
                return false;
            }
            // same start, just one element => step does not matter
            if (llen.compareTo(BigInteger.ONE) == 0) {
                return true;
            }
            return lstep.compareTo(rstep) == 0;
        }

        @Specialization(guards = "op.isEqOrNe()")
        static boolean eqIntInt(PIntRange left, PIntRange right, RichCmpOp op) {
            if (left == right) {
                return op.isEq();
            }
            return eqInt(left.getIntLength(), left.getIntStart(), left.getIntStep(),
                            right.getIntLength(), right.getIntStart(), right.getIntStep()) == op.isEq();
        }

        @Specialization(guards = "op.isEqOrNe()")
        static boolean eqIntBig(VirtualFrame frame, PIntRange left, PBigRange right, RichCmpOp op,
                        @Bind Node inliningTarget,
                        @Shared @Cached RangeNodes.CoerceToBigRange intToBigRange,
                        @Shared @Cached PyNumberAsSizeNode asSizeNode) {
            try {
                int rlen = asSizeNode.executeExact(frame, inliningTarget, right.getPIntLength());
                int rstart = asSizeNode.executeExact(frame, inliningTarget, right.getPIntStart());
                int rstep = asSizeNode.executeExact(frame, inliningTarget, right.getPIntStep());
                return eqInt(left, rlen, rstart, rstep) == op.isEq();
            } catch (PException e) {
                return eqBigInt(intToBigRange.execute(inliningTarget, left), right, op);
            }
        }

        @Specialization(guards = "op.isEqOrNe()")
        static boolean eqIntBig(VirtualFrame frame, PBigRange left, PIntRange right, RichCmpOp op,
                        @Bind Node inliningTarget,
                        @Shared @Cached RangeNodes.CoerceToBigRange intToBigRange,
                        @Shared @Cached PyNumberAsSizeNode asSizeNode) {
            try {
                int llen = asSizeNode.executeExact(frame, inliningTarget, left.getPIntLength());
                int lstart = asSizeNode.executeExact(frame, inliningTarget, left.getPIntStart());
                int lstep = asSizeNode.executeExact(frame, inliningTarget, left.getPIntStep());
                return eqInt(right, llen, lstart, lstep) == op.isEq();
            } catch (PException e) {
                return eqBigInt(left, intToBigRange.execute(inliningTarget, right), op);
            }
        }

        @Specialization(guards = "op.isEqOrNe()")
        static boolean eqBigInt(PBigRange left, PBigRange right, RichCmpOp op) {
            if (left == right) {
                return op.isEq();
            }
            return eqBigInt(left.getBigIntegerLength(), left.getBigIntegerStart(), left.getBigIntegerStep(),
                            right.getBigIntegerLength(), right.getBigIntegerStart(), right.getBigIntegerStep()) == op.isEq();
        }

        @Fallback
        @SuppressWarnings("unused")
        static Object doOther(Object left, Object right, RichCmpOp op) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Slot(value = SlotKind.sq_item, isComplex = true)
    @GenerateNodeFactory
    public abstract static class RangeSqItemNode extends SqItemBuiltinNode {
        @Specialization
        static int doInt(PIntRange self, int index,
                        @Bind Node inliningTarget,
                        @Cached InlinedConditionProfile negativeIndexProfile,
                        @Cached PRaiseNode raiseNode) {
            if (negativeIndexProfile.profile(inliningTarget, index < 0)) {
                index += self.getIntLength();
            }
            checkBounds(inliningTarget, raiseNode, ErrorMessages.RANGE_OUT_OF_BOUNDS, index, self.getIntLength());
            return self.getIntItemNormalized(index);
        }

        @Specialization
        @InliningCutoff
        static Object doBigInt(PBigRange self, int index,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language) {
            return PFactory.createInt(language, self.getBigIntItemNormalized(GetItemNode.computeBigRangeItem(inliningTarget, self, index)));
        }
    }

    @Slot(value = SlotKind.mp_subscript, isComplex = true)
    @GenerateNodeFactory
    @GenerateUncached
    @ImportStatic(PGuards.class)
    public abstract static class GetItemNode extends MpSubscriptBuiltinNode {

        public abstract Object execute(VirtualFrame frame, PRange range, Object index);

        public final Object execute(PRange range, Object index) {
            return execute(null, range, index);
        }

        protected static boolean allNone(PObjectSlice slice) {
            return slice.getStart() == PNone.NONE && slice.getStop() == PNone.NONE && slice.getStep() == PNone.NONE;
        }

        protected static boolean canBeIndex(Node inliningTarget, Object idx, PyIndexCheckNode indexCheckNode) {
            return indexCheckNode.execute(inliningTarget, idx);
        }

        @Specialization(guards = "allNone(slice)")
        Object doPRangeObj(PRange self, @SuppressWarnings("unused") PObjectSlice slice) {
            return self;
        }

        @Specialization(guards = "canBeIndex(this, idx, indexCheckNode)")
        static Object doPRange(VirtualFrame frame, PIntRange self, Object idx,
                        @SuppressWarnings("unused") @Bind Node inliningTarget,
                        @SuppressWarnings("unused") @Shared @Cached PyIndexCheckNode indexCheckNode,
                        @Shared @Cached PyNumberAsSizeNode asSizeNode,
                        @Shared @Cached IndexNodes.NormalizeIndexCustomMessageNode normalize) {
            return self.getIntItemNormalized(normalize.execute(asSizeNode.executeExact(frame, inliningTarget, idx), self.getIntLength(), RANGE_OUT_OF_BOUNDS));
        }

        @Specialization(guards = "canBeIndex(this, idx, indexCheckNode)")
        static Object doPRange(PBigRange self, Object idx,
                        @SuppressWarnings("unused") @Bind Node inliningTarget,
                        @Shared @Cached CastToJavaBigIntegerNode toBigInt,
                        @SuppressWarnings("unused") @Shared @Cached PyIndexCheckNode indexCheckNode) {
            return PFactory.createInt(PythonLanguage.get(inliningTarget), self.getBigIntItemNormalized(computeBigRangeItem(inliningTarget, self, idx, toBigInt)));
        }

        @Specialization(guards = "!canBeIndex(this, slice, indexCheckNode)")
        @InliningCutoff
        static Object doPRangeSliceSlowPath(VirtualFrame frame, PIntRange self, PSlice slice,
                        @Bind Node inliningTarget,
                        @Shared @Cached ComputeIndices compute,
                        @Shared @Cached IsBuiltinObjectProfile profileError,
                        @Shared @Cached CoerceToBigRange toBigIntRange,
                        @Shared @Cached CoerceToObjectSlice toBigIntSlice,
                        @Shared @Cached LenOfIntRangeNodeExact lenOfRangeNodeExact,
                        @Shared @Cached RangeNodes.LenOfRangeNode lenOfRangeNode,
                        @SuppressWarnings("unused") @Shared @Cached PyIndexCheckNode indexCheckNode) {
            try {
                final int rStart = self.getIntStart();
                final int rStep = self.getIntStep();
                SliceInfo info = compute.execute(frame, slice, self.getIntLength());
                return createRange(inliningTarget, info, rStart, rStep, lenOfRangeNodeExact);
            } catch (PException pe) {
                pe.expect(inliningTarget, PythonBuiltinClassType.OverflowError, profileError);
                // pass
            } catch (CannotCastException | OverflowException e) {
                // pass
            }
            PBigRange rangeBI = toBigIntRange.execute(inliningTarget, self);
            BigInteger rangeStart = rangeBI.getBigIntegerStart();
            BigInteger rangeStep = rangeBI.getBigIntegerStep();

            SliceObjectInfo info = PObjectSlice.computeIndicesSlowPath(toBigIntSlice.execute(slice), rangeBI.getBigIntegerLength(), false);
            return createRange(inliningTarget, info, rangeStart, rangeStep, lenOfRangeNode);
        }

        @Specialization(guards = "!canBeIndex(this, slice, indexCheckNode)")
        static Object doPRangeSliceSlowPath(VirtualFrame frame, PBigRange self, PSlice slice,
                        @Bind Node inliningTarget,
                        // Note the dummy profiles: it is better to have everything @Shared
                        @SuppressWarnings("unused") @Shared @Cached InlinedConditionProfile isNumIndexProfile,
                        @SuppressWarnings("unused") @Shared @Cached InlinedConditionProfile isSliceIndexProfile,
                        @Shared @Cached ComputeIndices compute,
                        @Shared @Cached IsBuiltinObjectProfile profileError,
                        @Shared @Cached CoerceToBigRange toBigIntRange,
                        @Shared @Cached CoerceToObjectSlice toBigIntSlice,
                        @Shared @Cached LenOfIntRangeNodeExact lenOfRangeNodeExact,
                        @Shared @Cached RangeNodes.LenOfRangeNode lenOfRangeNode,
                        @SuppressWarnings("unused") @Shared @Cached PyIndexCheckNode indexCheckNode,
                        @Shared @Cached PyNumberAsSizeNode asSizeNode,
                        // unused node to avoid mixing shared and non-shared inlined nodes
                        @SuppressWarnings("unused") @Shared @Cached PRaiseNode raiseNode) {
            try {
                int rStart = asSizeNode.executeExact(frame, inliningTarget, self.getStart());
                int rStep = asSizeNode.executeExact(frame, inliningTarget, self.getStep());
                SliceInfo info = compute.execute(frame, slice, asSizeNode.executeExact(frame, inliningTarget, self.getLength()));
                return createRange(inliningTarget, info, rStart, rStep, lenOfRangeNodeExact);
            } catch (PException pe) {
                pe.expect(inliningTarget, PythonBuiltinClassType.OverflowError, profileError);
                // pass
            } catch (CannotCastException | OverflowException e) {
                // pass
            }
            PBigRange rangeBI = toBigIntRange.execute(inliningTarget, self);
            BigInteger rangeStart = rangeBI.getBigIntegerStart();
            BigInteger rangeStep = rangeBI.getBigIntegerStep();

            SliceObjectInfo info = PObjectSlice.computeIndicesSlowPath(toBigIntSlice.execute(slice), rangeBI.getBigIntegerLength(), false);
            return createRange(inliningTarget, info, rangeStart, rangeStep, lenOfRangeNode);
        }

        @Specialization
        @InliningCutoff
        static Object doGeneric(VirtualFrame frame, PRange self, Object idx,
                        @Bind Node inliningTarget,
                        @Shared @Cached InlinedConditionProfile isNumIndexProfile,
                        @Shared @Cached InlinedConditionProfile isSliceIndexProfile,
                        @Shared @Cached ComputeIndices compute,
                        @Shared @Cached IsBuiltinObjectProfile profileError,
                        @Shared @Cached CoerceToBigRange toBigIntRange,
                        @Shared @Cached CoerceToObjectSlice toBigIntSlice,
                        @Shared @Cached LenOfIntRangeNodeExact lenOfRangeNodeExact,
                        @Shared @Cached RangeNodes.LenOfRangeNode lenOfRangeNode,
                        @Shared @Cached CastToJavaBigIntegerNode toBigInt,
                        @Shared @Cached PyIndexCheckNode indexCheckNode,
                        @Shared @Cached PyNumberAsSizeNode asSizeNode,
                        @Shared @Cached IndexNodes.NormalizeIndexCustomMessageNode normalize,
                        @Shared @Cached PRaiseNode raiseNode) {
            if (isNumIndexProfile.profile(inliningTarget, canBeIndex(inliningTarget, idx, indexCheckNode))) {
                if (self instanceof PIntRange) {
                    return doPRange(frame, (PIntRange) self, idx, inliningTarget, indexCheckNode, asSizeNode, normalize);
                }
                return doPRange((PBigRange) self, idx, inliningTarget, toBigInt, indexCheckNode);
            }
            if (isSliceIndexProfile.profile(inliningTarget, idx instanceof PSlice)) {
                PSlice slice = (PSlice) idx;
                if (self instanceof PIntRange) {
                    return doPRangeSliceSlowPath(frame, (PIntRange) self, slice, inliningTarget, compute, profileError, toBigIntRange, toBigIntSlice, lenOfRangeNodeExact, lenOfRangeNode,
                                    indexCheckNode);
                }
                return doPRangeSliceSlowPath(frame, (PBigRange) self, slice, inliningTarget, isNumIndexProfile, isSliceIndexProfile, compute, profileError, toBigIntRange, toBigIntSlice,
                                lenOfRangeNodeExact, lenOfRangeNode, indexCheckNode, asSizeNode, raiseNode);
            }
            throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.OBJ_INDEX_MUST_BE_INT_OR_SLICES, "range", idx);
        }

        @TruffleBoundary
        private static BigInteger computeBigRangeItem(Node inliningTarget, PBigRange range, Object idx, CastToJavaBigIntegerNode toBigInt) {
            BigInteger index = toBigInt.execute(inliningTarget, idx);
            return computeBigRangeItem(inliningTarget, range, index);
        }

        @TruffleBoundary
        private static BigInteger computeBigRangeItem(Node inliningTarget, PBigRange range, int index) {
            return computeBigRangeItem(inliningTarget, range, BigInteger.valueOf(index));
        }

        private static BigInteger computeBigRangeItem(Node inliningTarget, PBigRange range, BigInteger index) {
            CompilerAsserts.neverPartOfCompilation();
            BigInteger length = range.getBigIntegerLength();
            BigInteger i;
            if (index.compareTo(BigInteger.ZERO) < 0) {
                i = length.add(index);
            } else {
                i = index;
            }

            if (i.compareTo(BigInteger.ZERO) < 0 || i.compareTo(length) >= 0) {
                throw PRaiseNode.raiseStatic(inliningTarget, IndexError, ErrorMessages.RANGE_OBJ_IDX_OUT_OF_RANGE);
            }
            return i;
        }

        private static PIntRange createRange(Node inliningTarget, SliceInfo info, int rStart, int rStep, LenOfIntRangeNodeExact lenOfRangeNode) throws OverflowException {
            int newStep = rStep * info.step;
            int newStart = rStart + info.start * rStep;
            int newStop = rStart + info.stop * rStep;
            int len = lenOfRangeNode.executeInt(inliningTarget, newStart, newStop, newStep);
            return PFactory.createIntRange(PythonLanguage.get(inliningTarget), newStart, newStop, newStep, len);
        }

        @TruffleBoundary
        private static PBigRange createRange(Node inliningTarget, SliceObjectInfo info, BigInteger rStart, BigInteger rStep, RangeNodes.LenOfRangeNode lenOfRangeNode) {
            BigInteger sliceStart = (BigInteger) info.start;
            BigInteger sliceStop = (BigInteger) info.stop;
            BigInteger sliceStep = (BigInteger) info.step;

            BigInteger step = rStep.multiply(sliceStep);
            BigInteger start = rStart.add(sliceStart.multiply(rStep));
            BigInteger stop = rStart.add(sliceStop.multiply(rStep));
            BigInteger len = lenOfRangeNode.execute(inliningTarget, start, stop, step);
            PythonLanguage language = PythonLanguage.get(null);
            return PFactory.createBigRange(language, PFactory.createInt(language, start), PFactory.createInt(language, stop), PFactory.createInt(language, step), PFactory.createInt(language, len));
        }

        @NeverDefault
        public static GetItemNode create() {
            return RangeBuiltinsFactory.GetItemNodeFactory.create();
        }

        public static GetItemNode getUncached() {
            return RangeBuiltinsFactory.GetItemNodeFactory.getUncached();
        }
    }

    @Slot(value = SlotKind.sq_contains, isComplex = true)
    @GenerateNodeFactory
    @ImportStatic(PGuards.class)
    @TypeSystemReference(PythonIntegerTypes.class)
    abstract static class ContainsNode extends SqContainsBuiltinNode {
        private static final BigInteger MINUS_ONE = BigInteger.ONE.negate();

        public abstract boolean execute(VirtualFrame frame, PRange self, Object value);

        private static boolean containsInt(Node inliningTarget, PIntRange self, int other, InlinedConditionProfile stepOneProfile, InlinedConditionProfile stepMinusOneProfile) {
            int step = self.getIntStep();
            int start = self.getIntStart();
            int stop = self.getIntStop();
            boolean cmp2;
            boolean cmp3;

            if (stepOneProfile.profile(inliningTarget, step == 1)) {
                return other >= start && other < stop;
            } else if (stepMinusOneProfile.profile(inliningTarget, step == -1)) {
                return other <= start && other > stop;
            } else {
                assert step != 0;
                if (step > 0) {
                    // positive steps: start <= ob < stop
                    cmp2 = start <= other;
                    cmp3 = other < stop;
                } else {
                    // negative steps: stop < ob <= start
                    cmp2 = stop < other;
                    cmp3 = other <= start;
                }

                if (!cmp2 || !cmp3) {
                    return false;
                }

                // Check that the stride does not invalidate ob's membership.
                return (other - start) % step == 0;
            }
        }

        @TruffleBoundary
        private boolean containsBigInt(PBigRange self, long other) {
            return containsBigInt(self, BigInteger.valueOf(other));
        }

        @TruffleBoundary
        private static boolean containsBigInt(PBigRange self, BigInteger other) {
            BigInteger step = self.getBigIntegerStep();
            BigInteger start = self.getBigIntegerStart();
            BigInteger stop = self.getBigIntegerStop();
            boolean cmp2;
            boolean cmp3;

            if (step.compareTo(BigInteger.ONE) == 0) {
                return other.compareTo(start) >= 0 && other.compareTo(stop) < 0;
            } else if (step.compareTo(MINUS_ONE) == 0) {
                return other.compareTo(start) <= 0 && other.compareTo(stop) > 0;
            } else {
                assert step.compareTo(BigInteger.ZERO) != 0;
                if (step.compareTo(BigInteger.ZERO) > 0) {
                    // positive steps: start <= ob < stop
                    cmp2 = start.compareTo(other) <= 0;
                    cmp3 = other.compareTo(stop) < 0;
                } else {
                    // negative steps: stop < ob <= start
                    cmp2 = stop.compareTo(other) < 0;
                    cmp3 = other.compareTo(start) <= 0;
                }

                if (!cmp2 || !cmp3) {
                    return false;
                }

                // Check that the stride does not invalidate ob's membership.
                BigInteger tmp1 = other.subtract(start);
                if (tmp1.compareTo(BigInteger.ZERO) == 0) {
                    return true;
                }
                return tmp1.mod(step).compareTo(BigInteger.ZERO) == 0;
            }
        }

        protected boolean doubleIsExactInteger(double value) {
            return (value % 1) == 0;
        }

        @Specialization
        boolean containsFastNumInt(PIntRange self, int other,
                        @Bind Node inliningTarget,
                        @Shared @Cached InlinedConditionProfile stepOneProfile,
                        @Shared @Cached InlinedConditionProfile stepMinusOneProfile) {
            return containsInt(inliningTarget, self, other, stepOneProfile, stepMinusOneProfile);
        }

        @Specialization
        boolean containsFastNumLong(PIntRange self, long other,
                        @Bind Node inliningTarget,
                        @Shared @Cached InlinedConditionProfile stepOneProfile,
                        @Shared @Cached InlinedConditionProfile stepMinusOneProfile) {
            try {
                return containsInt(inliningTarget, self, PInt.intValueExact(other), stepOneProfile, stepMinusOneProfile);
            } catch (OverflowException e) {
                return false;
            }
        }

        protected static boolean isBuiltinPInt(Node inliningTarget, Object value, PyLongCheckExactNode isBuiltin) {
            return isBuiltin.execute(inliningTarget, value);
        }

        @Specialization(guards = "isBuiltinPInt(this, other, isBuiltin)", limit = "1")
        static boolean containsFastNumPInt(PIntRange self, PInt other,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached InlinedConditionProfile stepOneProfile,
                        @Exclusive @Cached InlinedConditionProfile stepMinusOneProfile,
                        @SuppressWarnings("unused") @Exclusive @Cached PyLongCheckExactNode isBuiltin) {
            try {
                return containsInt(inliningTarget, self, other.intValueExact(), stepOneProfile, stepMinusOneProfile);
            } catch (OverflowException e) {
                return false;
            }
        }

        @Specialization(guards = "doubleIsExactInteger(other)")
        static boolean containsFastNum(PIntRange self, double other,
                        @Bind Node inliningTarget,
                        @Shared @Cached InlinedConditionProfile stepOneProfile,
                        @Shared @Cached InlinedConditionProfile stepMinusOneProfile) {
            return containsInt(inliningTarget, self, (int) other, stepOneProfile, stepMinusOneProfile);
        }

        @Specialization
        boolean containsSlowNum(PBigRange self, int other) {
            return containsBigInt(self, other);
        }

        @Specialization
        boolean containsSlowNum(PBigRange self, long other) {
            return containsBigInt(self, other);
        }

        @Specialization(guards = "doubleIsExactInteger(other)")
        boolean containsSlowNum(PBigRange self, double other) {
            return containsBigInt(self, (long) other);
        }

        @Specialization(guards = "isBuiltinPInt(inliningTarget, other, isBuiltin)", limit = "1")
        static boolean containsSlowNum(PBigRange self, PInt other,
                        @SuppressWarnings("unused") @Bind Node inliningTarget,
                        @SuppressWarnings("unused") @Exclusive @Cached PyLongCheckExactNode isBuiltin) {
            return containsBigInt(self, other.getValue());
        }

        @Specialization(guards = "!canBeInteger(elem) || !isBuiltinPInt(inliningTarget, elem, isBuiltin)", limit = "1")
        static boolean containsIterator(VirtualFrame frame, PRange self, Object elem,
                        @Bind Node inliningTarget,
                        @Cached PyObjectGetIter getIter,
                        @Cached PyIterNextNode nextNode,
                        @Cached PyObjectRichCompareBool eqNode,
                        @SuppressWarnings("unused") @Exclusive @Cached PyLongCheckExactNode isBuiltin) {
            Object iter = getIter.execute(frame, inliningTarget, self);
            while (true) {
                Object item;
                try {
                    item = nextNode.execute(frame, inliningTarget, iter);
                } catch (IteratorExhausted e) {
                    return false;
                }
                if (eqNode.executeEq(frame, inliningTarget, elem, item)) {
                    return true;
                }
            }
        }

        @NeverDefault
        public static ContainsNode create() {
            return RangeBuiltinsFactory.ContainsNodeFactory.create();
        }
    }

    @Builtin(name = "index", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @ImportStatic(PGuards.class)
    abstract static class IndexNode extends PythonBinaryBuiltinNode {

        private static int fastIntIndex(PIntRange self, int elem) {
            int normalized = elem - self.getIntStart();
            if (normalized % self.getIntStep() == 0) {
                return normalized / self.getIntStep();
            }
            return -1;
        }

        @TruffleBoundary
        private static BigInteger slowIntIndex(Node inliningTarget, PBigRange self, Object elem, CastToJavaBigIntegerNode castToBigInt) {
            BigInteger start = self.getBigIntegerStart();
            BigInteger step = self.getBigIntegerStep();
            BigInteger value = castToBigInt.execute(inliningTarget, elem);
            BigInteger normalized = value.subtract(start);
            if (normalized.remainder(step).equals(BigInteger.ZERO)) {
                return normalized.divide(step);
            }
            return null;
        }

        @Specialization
        static int doFastRange(VirtualFrame frame, PIntRange self, int elem,
                        @Bind Node inliningTarget,
                        @Shared @Cached ContainsNode containsNode,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            if (containsNode.execute(frame, self, elem)) {
                int index = fastIntIndex(self, elem);
                if (index != -1) {
                    return index;
                }
            }
            throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.D_IS_NOT_IN_RANGE, elem);
        }

        @Specialization(guards = "canBeInteger(elem)")
        static Object doFastRangeGeneric(VirtualFrame frame, PIntRange self, Object elem,
                        @Bind Node inliningTarget,
                        @Shared @Cached ContainsNode containsNode,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            if (containsNode.execute(frame, self, elem)) {
                int value = asSizeNode.executeExact(frame, inliningTarget, elem);
                int index = fastIntIndex(self, value);
                if (index != -1) {
                    return index;
                }
            }
            throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.IS_NOT_IN_RANGE, elem);
        }

        @Specialization(guards = "canBeInteger(elem)")
        static Object doLongRange(VirtualFrame frame, PBigRange self, Object elem,
                        @Bind Node inliningTarget,
                        @Shared @Cached ContainsNode containsNode,
                        @Cached CastToJavaBigIntegerNode castToBigInt,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            if (containsNode.execute(frame, self, elem)) {
                BigInteger index = slowIntIndex(inliningTarget, self, elem, castToBigInt);
                if (index != null) {
                    return PFactory.createInt(PythonLanguage.get(inliningTarget), index);
                }
            }
            throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.D_IS_NOT_IN_RANGE, elem);
        }

        /**
         * XXX: (mq) currently sys.maxsize in {@link SysModuleBuiltins#MAXSIZE} is
         * {@link Integer#MAX_VALUE}.
         */
        @Specialization(guards = "!canBeInteger(elem)")
        static Object containsIterator(VirtualFrame frame, PIntRange self, Object elem,
                        @Bind Node inliningTarget,
                        @Cached PyObjectGetIter getIter,
                        @Cached PyIterNextNode nextNode,
                        @Cached PyObjectRichCompareBool eqNode,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            int idx = 0;
            Object iter = getIter.execute(frame, inliningTarget, self);
            while (true) {
                Object item;
                try {
                    item = nextNode.execute(frame, inliningTarget, iter);
                } catch (IteratorExhausted e) {
                    break;
                }
                if (eqNode.executeEq(frame, inliningTarget, elem, item)) {
                    return idx;
                }
                if (idx == SysModuleBuiltins.MAXSIZE) {
                    throw raiseNode.raiseOverflow(inliningTarget);
                }
                idx++;
            }
            throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.D_IS_NOT_IN_RANGE, elem);
        }
    }

    @Builtin(name = "count", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class CountNode extends PythonBinaryBuiltinNode {
        @Specialization
        int doInt(PIntRange self, int elem) {
            assert self.getIntStep() != 0;
            if (elem >= self.getIntStart() && elem < self.getIntStop()) {
                int normalized = elem - self.getIntStart();
                if (normalized % self.getIntStep() == 0) {
                    return 1;
                }
            }
            return 0;
        }

        @Specialization
        int doInt(PIntRange self, long elem) {
            assert self.getIntStep() != 0;
            if (elem >= self.getIntStart() && elem < self.getIntStop()) {
                long normalized = elem - self.getIntStart();
                if (normalized % self.getIntStep() == 0) {
                    return 1;
                }
            }
            return 0;
        }

        @Specialization
        @TruffleBoundary
        int doInt(PIntRange self, PInt elem) {
            assert self.getIntStep() != 0;
            BigInteger value = elem.getValue();
            BigInteger start = BigInteger.valueOf(self.getIntStart());
            BigInteger stop = BigInteger.valueOf(self.getIntStop());
            BigInteger step = BigInteger.valueOf(self.getIntStep());
            if (value.compareTo(start) >= 0 && value.compareTo(stop) < 0) {
                BigInteger normalized = value.subtract(start);
                if (normalized.remainder(step).equals(BigInteger.ZERO)) {
                    return 1;
                }
            }
            return 0;
        }

        @Specialization(guards = "isInteger(elem)")
        @TruffleBoundary
        int doInt(PBigRange self, Object elem,
                        @Bind Node inliningTarget,
                        @Cached CastToJavaBigIntegerNode castToBigInt) {
            BigInteger start = self.getBigIntegerStart();
            BigInteger stop = self.getBigIntegerStop();
            BigInteger step = self.getBigIntegerStep();
            BigInteger value = castToBigInt.execute(inliningTarget, elem);
            if (value.compareTo(start) >= 0 && value.compareTo(stop) < 0) {
                BigInteger normalized = value.subtract(start);
                if (normalized.remainder(step).equals(BigInteger.ZERO)) {
                    return 1;
                }
            }
            return 0;
        }

        static boolean isInteger(Object value) {
            return value instanceof Integer || value instanceof Long || value instanceof PInt;
        }

        static boolean isFallback(Object value) {
            return !isInteger(value);
        }

        @Specialization(guards = "isFallback(elem)")
        static int doGeneric(VirtualFrame frame, PRange self, Object elem,
                        @Bind Node inliningTarget,
                        @Cached PyObjectGetIter getIter,
                        @Cached PyIterNextNode nextNode,
                        @Cached PyObjectRichCompareBool eqNode,
                        @Cached PRaiseNode raiseNode) {
            int count = 0;
            Object iter = getIter.execute(frame, inliningTarget, self);
            while (true) {
                Object item;
                try {
                    item = nextNode.execute(frame, inliningTarget, iter);
                } catch (IteratorExhausted e) {
                    return count;
                }
                if (eqNode.executeEq(frame, inliningTarget, elem, item)) {
                    if (count == SysModuleBuiltins.MAXSIZE) {
                        throw raiseNode.raiseOverflow(inliningTarget);
                    }
                    count = count + 1;
                }
            }
        }
    }
}
