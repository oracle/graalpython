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
package com.oracle.graal.python.builtins.objects.slice;

import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.HashNotImplemented;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.slice.PSlice.SliceInfo;
import com.oracle.graal.python.builtins.objects.slice.SliceNodes.CoerceToObjectSlice;
import com.oracle.graal.python.builtins.objects.slice.SliceNodes.ComputeIndices;
import com.oracle.graal.python.builtins.objects.slice.SliceNodes.SliceCastToToBigInt;
import com.oracle.graal.python.builtins.objects.slice.SliceNodes.SliceExactCastToInt;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotRichCompare.RichCmpBuiltinNode;
import com.oracle.graal.python.lib.RichCmpOp;
import com.oracle.graal.python.lib.PyObjectRichCompare;
import com.oracle.graal.python.lib.PyObjectRichCompareBool;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
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
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PSlice)
@HashNotImplemented
public final class SliceBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = SliceBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return SliceBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.tp_repr, isComplex = true)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        public static TruffleString repr(PSlice self) {
            return toTruffleStringUncached(self.toString());
        }
    }

    @Slot(value = SlotKind.tp_richcompare, isComplex = true)
    @GenerateNodeFactory
    abstract static class EqNode extends RichCmpBuiltinNode {
        @Specialization
        static boolean doIntSliceEq(PIntSlice left, PIntSlice right, RichCmpOp op,
                        @Bind("$node") Node inliningTarget,
                        @Cached InlinedConditionProfile startCmpProfile,
                        @Cached InlinedConditionProfile stopCmpProfile,
                        @Cached InlinedConditionProfile stepCmpProfile,
                        @Cached PRaiseNode raiseNode) {
            // Inlined tuple comparison specialized for ints
            if (startCmpProfile.profile(inliningTarget, left.start != right.start)) {
                return cmpVal(inliningTarget, left.start, right.start, left.startIsNone, right.startIsNone, op, raiseNode);
            }
            if (stopCmpProfile.profile(inliningTarget, left.stop != right.stop)) {
                return cmpVal(inliningTarget, left.stop, right.stop, false, false, op, raiseNode);
            }
            if (stepCmpProfile.profile(inliningTarget, left.step != right.step)) {
                return cmpVal(inliningTarget, left.step, right.step, left.stepIsNone, right.stepIsNone, op, raiseNode);
            }
            return op.isEq() || op.isLe() || op.isGe();
        }

        private static boolean cmpVal(Node inliningTarget, int leftVal, int rightVal, boolean leftValIsNone, boolean rightValIsNone, RichCmpOp op, PRaiseNode raiseNode) {
            if (op.isEqOrNe()) {
                return op.isNe();
            }
            if (leftValIsNone || rightValIsNone) {
                Object leftObj = leftValIsNone ? PNone.NONE : leftVal;
                Object rightObj = rightValIsNone ? PNone.NONE : rightVal;
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.NOT_SUPPORTED_BETWEEN_INSTANCES, op.getOpName(), leftObj, rightObj);
            }
            return op.compare(leftVal, rightVal);
        }

        static boolean noIntSlices(PSlice a, PSlice b) {
            return !(a instanceof PIntSlice && b instanceof PIntSlice);
        }

        @Specialization(guards = {"noIntSlices(left, right)", "left == right"})
        static boolean sliceCmpIdentical(VirtualFrame frame, PSlice left, PSlice right, RichCmpOp op) {
            // CPython fast-path, can have visible behavior, because we skip richcmp
            return op.isEq() || op.isLe() || op.isGe();
        }

        @Specialization(guards = {"noIntSlices(left, right)", "left != right"})
        static Object sliceCmpWithLib(VirtualFrame frame, PSlice left, PSlice right, RichCmpOp op,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedConditionProfile startCmpProfile,
                        @Cached InlinedConditionProfile stopCmpProfile,
                        @Cached InlinedConditionProfile stepCmpProfile,
                        @Cached PyObjectRichCompareBool eqNode,
                        @Cached PyObjectRichCompare cmpNode) {
            // Inlined tuple comparison specialized for tuples of 3 items to avoid the tuples
            // allocation
            if (startCmpProfile.profile(inliningTarget, !eqNode.executeEq(frame, inliningTarget, left.getStart(), right.getStart()))) {
                return cmpNode.execute(frame, inliningTarget, left.getStart(), right.getStart(), op);
            }
            if (stopCmpProfile.profile(inliningTarget, !eqNode.executeEq(frame, inliningTarget, left.getStop(), right.getStop()))) {
                return cmpNode.execute(frame, inliningTarget, left.getStop(), right.getStop(), op);
            }
            if (stepCmpProfile.profile(inliningTarget, !eqNode.executeEq(frame, inliningTarget, left.getStep(), right.getStep()))) {
                return cmpNode.execute(frame, inliningTarget, left.getStep(), right.getStep(), op);
            }
            return op.isEq() || op.isLe() || op.isGe();
        }

        @Fallback
        @SuppressWarnings("unused")
        static Object doOthers(VirtualFrame frame, Object left, Object right, RichCmpOp op) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = "start", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class StartNode extends PythonUnaryBuiltinNode {

        @Specialization
        protected static Object get(PIntSlice self) {
            return self.getStart();
        }

        @Specialization
        protected static Object get(PObjectSlice self) {
            return self.getStart();
        }
    }

    @Builtin(name = "stop", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class StopNode extends PythonUnaryBuiltinNode {

        @Specialization
        protected static Object get(PIntSlice self) {
            return self.getStop();
        }

        @Specialization
        protected static Object get(PObjectSlice self) {
            return self.getStop();
        }
    }

    @Builtin(name = "step", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class StepNode extends PythonUnaryBuiltinNode {

        @Specialization
        protected static Object get(PIntSlice self) {
            return self.getStep();
        }

        @Specialization
        protected static Object get(PObjectSlice self) {
            return self.getStep();
        }
    }

    @Builtin(name = "indices", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class IndicesNode extends PythonBinaryBuiltinNode {

        private static PTuple doPSlice(VirtualFrame frame, PSlice self, int length, ComputeIndices compute, PythonLanguage language) {
            SliceInfo sliceInfo = compute.execute(frame, self, length);
            return PFactory.createTuple(language, new Object[]{sliceInfo.start, sliceInfo.stop, sliceInfo.step});
        }

        protected static boolean isSafeIntSlice(PSlice self, Object length) {
            return self instanceof PIntSlice && length instanceof Integer;
        }

        @Specialization
        static PTuple safeInt(VirtualFrame frame, PIntSlice self, int length,
                        @Bind PythonLanguage language,
                        @Shared @Cached ComputeIndices compute) {
            return doPSlice(frame, self, length, compute, language);
        }

        @Specialization(guards = "!isPNone(length)", rewriteOn = PException.class)
        static PTuple doSliceObject(VirtualFrame frame, PSlice self, Object length,
                        @Bind("this") Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Exclusive @Cached SliceExactCastToInt toInt,
                        @Shared @Cached ComputeIndices compute) {
            return doPSlice(frame, self, (int) toInt.execute(frame, inliningTarget, length), compute, language);
        }

        @Specialization(guards = "!isPNone(length)", replaces = {"doSliceObject"})
        static PTuple doSliceObjectWithSlowPath(VirtualFrame frame, PSlice self, Object length,
                        @Bind("this") Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Exclusive @Cached SliceExactCastToInt toInt,
                        @Shared @Cached ComputeIndices compute,
                        @Cached IsBuiltinObjectProfile profileError,
                        @Cached SliceCastToToBigInt castLengthNode,
                        @Cached CoerceToObjectSlice castNode) {
            try {
                return doPSlice(frame, self, (int) toInt.execute(frame, inliningTarget, length), compute, language);
            } catch (PException pe) {
                if (!profileError.profileException(inliningTarget, pe, PythonBuiltinClassType.OverflowError)) {
                    throw pe;
                }
                // pass
            }

            Object lengthIn = castLengthNode.execute(inliningTarget, length);
            PObjectSlice.SliceObjectInfo sliceInfo = PObjectSlice.computeIndicesSlowPath(castNode.execute(self), lengthIn, true);
            return PFactory.createTuple(language, new Object[]{sliceInfo.start, sliceInfo.stop, sliceInfo.step});
        }

        @Specialization(guards = {"isPNone(length)"})
        static PTuple lengthNone(@SuppressWarnings("unused") PSlice self, @SuppressWarnings("unused") Object length,
                        @Bind("this") Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, ValueError);
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object reduce(PSlice self,
                        @Bind("this") Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached GetClassNode getClassNode) {
            PTuple args = PFactory.createTuple(language, new Object[]{self.getStart(), self.getStop(), self.getStep()});
            return PFactory.createTuple(language, new Object[]{getClassNode.execute(inliningTarget, self), args});
        }
    }
}
