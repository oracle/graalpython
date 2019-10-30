/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__CONTAINS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LEN__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.common.IndexNodes.NormalizeIndexNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.iterator.PIntegerIterator;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.slice.PSlice.SliceInfo;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNode;
import com.oracle.graal.python.nodes.expression.CastToBooleanNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CastToIndexNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PRange)
public class RangeBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return RangeBuiltinsFactory.getFactories();
    }

    @Builtin(name = __REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReprNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        public String repr(PRange self) {
            return self.toString();
        }
    }

    @Builtin(name = __LEN__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class LenNode extends PythonUnaryBuiltinNode {
        @Specialization
        int doPRange(PRange left) {
            return left.len();
        }
    }

    @Builtin(name = __EQ__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class EqNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean doPRange(PRange left, PRange right) {
            if (left == right) {
                return true;
            }
            if (left.len() != right.len()) {
                return false;
            }
            if (left.len() == 0) {
                return true;
            }
            if (left.getStart() != right.getStart()) {
                return false;
            }
            // same start, just one element => step does not matter
            if (left.len() == 1) {
                return true;
            }
            return left.getStep() == right.getStep();
        }

        @Fallback
        @SuppressWarnings("unused")
        PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

    }

    @Builtin(name = __CONTAINS__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class ContainsNode extends PythonBinaryBuiltinNode {
        private final ConditionProfile stepOneProfile = ConditionProfile.createBinaryProfile();
        private final ConditionProfile stepMinusOneProfile = ConditionProfile.createBinaryProfile();

        @Specialization
        boolean contains(PRange self, long other) {
            int step = self.getStep();
            int start = self.getStart();
            int stop = self.getStop();

            if (stepOneProfile.profile(step == 1)) {
                return other >= start && other < stop;
            } else if (stepMinusOneProfile.profile(step == -1)) {
                return other <= start && other > stop;
            } else {
                assert step != 0;
                if (step > 0) {
                    if (other >= start && other < stop) {
                        // discard based on range
                        return false;
                    }
                } else {
                    if (other <= start && other > stop) {
                        // discard based on range
                        return false;
                    }
                }
                return (other - start) % step == 0;
            }
        }

        @Specialization
        boolean contains(PRange self, double other) {
            return (long) other == other ? contains(self, (long) other) : false;
        }

        @Specialization
        boolean contains(PRange self, PInt other) {
            try {
                return contains(self, other.longValueExact());
            } catch (ArithmeticException e) {
                return false;
            }
        }

        @SuppressWarnings("unused")
        @Fallback
        boolean containsFallback(Object self, Object other) {
            return false;
        }
    }

    @Builtin(name = __ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        PIntegerIterator iter(PRange self,
                        @Cached("createBinaryProfile()") ConditionProfile stepPositiveProfile) {
            return factory().createRangeIterator(self.getStart(), self.getStop(), self.getStep(), stepPositiveProfile);
        }
    }

    @Builtin(name = __GETITEM__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GetItemNode extends PythonBinaryBuiltinNode {
        @Child private NormalizeIndexNode normalize = NormalizeIndexNode.forRange();

        @Specialization
        Object doPRange(PRange primary, boolean idx) {
            return primary.getItemNormalized(normalize.execute(idx, primary.len()));
        }

        @Specialization
        Object doPRange(PRange primary, int idx) {
            return primary.getItemNormalized(normalize.execute(idx, primary.len()));
        }

        @Specialization
        Object doPRange(PRange primary, long idx) {
            return primary.getItemNormalized(normalize.execute(idx, primary.len()));
        }

        @Specialization
        Object doPRange(PRange primary, PInt idx) {
            return primary.getItemNormalized(normalize.execute(idx, primary.len()));
        }

        @Specialization
        Object doPRange(PRange range, PSlice slice) {
            SliceInfo info = slice.computeIndices(range.len());
            int newStep = range.getStep() * info.step;
            int newStart = info.start == PSlice.MISSING_INDEX ? range.getStart() : range.getStart() + info.start * range.getStep();
            int newStop = info.stop == PSlice.MISSING_INDEX ? range.getStop() : Math.min(range.getStop(), newStart + info.length * newStep);
            return factory().createRange(newStart, newStop, newStep);
        }

        @Fallback
        Object doGeneric(@SuppressWarnings("unused") Object range, @SuppressWarnings("unused") Object idx) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

    }

    @Builtin(name = "start", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class StartNode extends PythonUnaryBuiltinNode {
        @Specialization
        int start(PRange self) {
            return self.getStart();
        }
    }

    @Builtin(name = "step", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class StepNode extends PythonUnaryBuiltinNode {
        @Specialization
        int step(PRange self) {
            return self.getStep();
        }
    }

    @Builtin(name = "stop", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class StopNode extends PythonUnaryBuiltinNode {
        @Specialization
        int stop(PRange self) {
            return self.getStop();
        }
    }

    @Builtin(name = "index", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class IndexNode extends PythonBinaryBuiltinNode {
        @Specialization
        int doInt(PRange self, int elem) {
            if (elem >= self.getStart() && elem < self.getStop()) {
                int normalized = elem - self.getStart();
                if (normalized % self.getStep() == 0) {
                    return normalized / self.getStep();
                }
            }
            throw raise(ValueError, "%d is not in range", elem);
        }

        @Specialization
        Object doGeneric(VirtualFrame frame, PRange self, Object elem,
                        @Cached CastToIndexNode castToIntNode) {
            try {
                return doInt(self, castToIntNode.execute(frame, elem));
            } catch (PException e) {
                throw raise(ValueError, "%s is not in range", elem);
            }
        }
    }

    @Builtin(name = "count", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class CountNode extends PythonBinaryBuiltinNode {
        @Specialization
        int doInt(PRange self, int elem) {
            assert self.getStep() != 0;
            if (elem >= self.getStart() && elem < self.getStop()) {
                int normalized = elem - self.getStart();
                if (normalized % self.getStep() == 0) {
                    return 1;
                }
            }
            return 0;
        }

        @Specialization
        int doGeneric(VirtualFrame frame, PRange self, Object elem,
                        @Cached("createEq()") BinaryComparisonNode cmpNode,
                        @Cached("createIfTrueNode()") CastToBooleanNode castToBooleanNode,
                        @Cached SequenceStorageNodes.GetItemNode getItemNode) {

            int len = self.len();
            int cnt = 0;
            for (int i = 0; i < len; i++) {
                Object item = getItemNode.execute(frame, self.getSequenceStorage(), i);
                if (castToBooleanNode.executeBoolean(frame, cmpNode.executeWith(frame, elem, item))) {
                    cnt++;
                }
            }
            return cnt;
        }

        protected static BinaryComparisonNode createEq() {
            return BinaryComparisonNode.create(__EQ__, __EQ__, "==");
        }
    }
}
