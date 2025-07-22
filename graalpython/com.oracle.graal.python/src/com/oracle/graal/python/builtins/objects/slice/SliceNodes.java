/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.slice;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.math.BigInteger;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.ints.IntNodes.PyLongSign;
import com.oracle.graal.python.lib.PyIndexCheckNode;
import com.oracle.graal.python.lib.PyLongAsLongAndOverflowNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.util.CastToJavaBigIntegerNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;

public abstract class SliceNodes {
    @GenerateUncached
    @GenerateInline(false)
    public abstract static class CreateSliceNode extends PNodeWithContext {
        public abstract PSlice execute(Object start, Object stop, Object step);

        @SuppressWarnings("unused")
        static PSlice doInt(int start, int stop, PNone step,
                        @Bind PythonLanguage language) {
            return PFactory.createIntSlice(language, start, stop, 1, false, true);
        }

        @Specialization
        static PSlice doInt(int start, int stop, int step,
                        @Bind PythonLanguage language) {
            return PFactory.createIntSlice(language, start, stop, step);
        }

        @Specialization
        @SuppressWarnings("unused")
        static PSlice doInt(PNone start, int stop, PNone step,
                        @Bind PythonLanguage language) {
            return PFactory.createIntSlice(language, 0, stop, 1, true, true);
        }

        @Specialization
        @SuppressWarnings("unused")
        static PSlice doInt(PNone start, int stop, int step,
                        @Bind PythonLanguage language) {
            return PFactory.createIntSlice(language, 0, stop, step, true, false);
        }

        @Fallback
        static PSlice doGeneric(Object start, Object stop, Object step,
                        @Bind PythonLanguage language) {
            return PFactory.createObjectSlice(language, start, stop, step);
        }

        @NeverDefault
        public static CreateSliceNode create() {
            return SliceNodesFactory.CreateSliceNodeGen.create();
        }

        public static CreateSliceNode getUncached() {
            return SliceNodesFactory.CreateSliceNodeGen.getUncached();
        }
    }

    /**
     * Adopting logic from PySlice_AdjustIndices (sliceobject.c:248)
     */
    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class AdjustIndices extends PNodeWithContext {

        public abstract PSlice.SliceInfo execute(Node inliningTarget, int length, PSlice.SliceInfo slice);

        @Specialization
        static PSlice.SliceInfo calc(Node inliningTarget, int length, PSlice.SliceInfo slice,
                        @Cached PRaiseNode raiseNode) {
            int start = slice.start;
            int stop = slice.stop;
            int step = slice.step;

            if (step == 0) {
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.SLICE_STEP_CANNOT_BE_ZERO);
            }
            assert step > Integer.MIN_VALUE : "step must not be minimum integer value";

            int len = 0;
            if (start < 0) {
                start += length;
                if (start < 0) {
                    start = (step < 0) ? -1 : 0;
                }
            } else if (start >= length) {
                start = (step < 0) ? length - 1 : length;
            }

            if (stop < 0) {
                stop += length;
                if (stop < 0) {
                    stop = (step < 0) ? -1 : 0;
                }
            } else if (stop >= length) {
                stop = (step < 0) ? length - 1 : length;
            }

            if (step < 0) {
                if (stop < start) {
                    len = (start - stop - 1) / (-step) + 1;
                }
            } else {
                if (start < stop) {
                    len = (stop - start - 1) / step + 1;
                }
            }
            return new PSlice.SliceInfo(start, stop, step, len);
        }
    }

    /**
     * Coerce indices computation to lossy integer values
     */
    @GenerateUncached
    @SuppressWarnings("truffle-inlining")       // footprint reduction 36 -> 18
    public abstract static class ComputeIndices extends PNodeWithContext {

        public abstract PSlice.SliceInfo execute(Frame frame, PSlice slice, int i);

        @Specialization(guards = "length >= 0")
        PSlice.SliceInfo doSliceInt(PIntSlice slice, int length) {
            return slice.computeIndices(length);
        }

        @Specialization(guards = "length >= 0")
        PSlice.SliceInfo doSliceObject(VirtualFrame frame, PObjectSlice slice, int length,
                        @Bind Node inliningTarget,
                        @Cached SliceExactCastToInt castStartNode,
                        @Cached SliceExactCastToInt castStopNode,
                        @Cached SliceExactCastToInt castStepNode) {
            Object startIn = castStartNode.execute(frame, inliningTarget, slice.getStart());
            Object stopIn = castStopNode.execute(frame, inliningTarget, slice.getStop());
            Object stepIn = castStepNode.execute(frame, inliningTarget, slice.getStep());
            return PObjectSlice.computeIndices(startIn, stopIn, stepIn, length);
        }

        @Specialization(guards = "length < 0")
        PSlice.SliceInfo doSliceInt(@SuppressWarnings("unused") PSlice slice, @SuppressWarnings("unused") int length,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, ValueError, ErrorMessages.LENGTH_SHOULD_NOT_BE_NEG);
        }
    }

    /**
     * This is only applicable to slow path <i><b>internal</b></i> computations.
     */
    @GenerateUncached
    @SuppressWarnings("truffle-inlining")       // footprint reduction 48 -> 30
    public abstract static class CoerceToObjectSlice extends PNodeWithContext {

        public abstract PObjectSlice execute(PSlice slice);

        @Specialization
        PObjectSlice doSliceInt(PIntSlice slice,
                        @Bind Node inliningTarget,
                        @Shared @Cached SliceCastToToBigInt start,
                        @Shared @Cached SliceCastToToBigInt stop,
                        @Shared @Cached SliceCastToToBigInt step,
                        @Bind PythonLanguage language) {
            return PFactory.createObjectSlice(language, start.execute(inliningTarget, slice.getStart()), stop.execute(inliningTarget, slice.getStop()), step.execute(inliningTarget, slice.getStep()));
        }

        protected static boolean isBigInt(PObjectSlice slice) {
            return slice.getStart() instanceof BigInteger && slice.getStop() instanceof BigInteger && slice.getStep() instanceof BigInteger;
        }

        @Specialization(guards = "isBigInt(slice)")
        PObjectSlice doSliceObject(PObjectSlice slice) {
            return slice;
        }

        @Specialization
        PObjectSlice doSliceObject(PObjectSlice slice,
                        @Bind Node inliningTarget,
                        @Shared @Cached SliceCastToToBigInt start,
                        @Shared @Cached SliceCastToToBigInt stop,
                        @Shared @Cached SliceCastToToBigInt step,
                        @Bind PythonLanguage language) {
            return PFactory.createObjectSlice(language, start.execute(inliningTarget, slice.getStart()), stop.execute(inliningTarget, slice.getStop()), step.execute(inliningTarget, slice.getStep()));
        }

    }

    /**
     * This is only applicable to slow path <i><b>internal</b></i> computations.
     */
    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class CoerceToIntSlice extends PNodeWithContext {

        public abstract PSlice execute(Node inliningTarget, PSlice slice);

        @Specialization
        static PSlice doSliceInt(PIntSlice slice) {
            return slice;
        }

        @Specialization
        static PSlice doSliceObject(Node inliningTarget, PObjectSlice slice,
                        @Cached SliceLossyCastToInt start,
                        @Cached SliceLossyCastToInt stop,
                        @Cached SliceLossyCastToInt step,
                        @Bind PythonLanguage language) {
            return PFactory.createObjectSlice(language, start.execute(inliningTarget, slice.getStart()), stop.execute(inliningTarget, slice.getStop()), step.execute(inliningTarget, slice.getStep()));
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class SliceUnpack extends PNodeWithContext {

        public abstract PSlice.SliceInfo execute(Node inliningTarget, PSlice slice);

        @Specialization
        static PSlice.SliceInfo doSliceInt(PIntSlice slice) {
            int start = slice.getIntStart();
            if (slice.isStartNone()) {
                start = slice.getIntStep() >= 0 ? 0 : Integer.MAX_VALUE;
            }
            return new PSlice.SliceInfo(start, slice.getIntStop(), slice.getIntStep());
        }

        @Specialization
        @InliningCutoff
        static PSlice.SliceInfo doSliceObject(Node inliningTarget, PObjectSlice slice,
                        @Cached SliceLossyCastToInt toInt,
                        @Cached PRaiseNode raiseNode) {
            /* this is harder to get right than you might think */
            int start, stop, step;
            if (slice.getStep() == PNone.NONE) {
                step = 1;
            } else {
                step = (int) toInt.execute(inliningTarget, slice.getStep());
                if (step == 0) {
                    throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.SLICE_STEP_CANNOT_BE_ZERO);
                }
            }

            if (slice.getStart() == PNone.NONE) {
                start = step < 0 ? Integer.MAX_VALUE : 0;
            } else {
                start = (int) toInt.execute(inliningTarget, slice.getStart());
            }

            if (slice.getStop() == PNone.NONE) {
                stop = step < 0 ? Integer.MIN_VALUE : Integer.MAX_VALUE;
            } else {
                stop = (int) toInt.execute(inliningTarget, slice.getStop());
            }

            return new PSlice.SliceInfo(start, stop, step);
        }
    }

    /**
     * This is basically the same node as {@link SliceUnpack} but unpacks to Java {@code long}
     * fields. We need this to implement native {@code (H)PySlice_Unpack} functions where signed
     * 64-bit integers are expected.
     */
    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class SliceUnpackLong extends PNodeWithContext {

        public abstract PSlice.SliceInfoLong execute(Node inliningTarget, PSlice slice);

        @Specialization
        static PSlice.SliceInfoLong doSliceInt(Node inliningTarget, PIntSlice slice,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            long step = slice.getIntStep();
            if (step == 0) {
                throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.SLICE_STEP_CANNOT_BE_ZERO);
            }

            long start;
            if (slice.isStartNone()) {
                start = slice.getIntStep() >= 0 ? 0 : Long.MAX_VALUE;
            } else {
                start = slice.getIntStart();
            }
            return new PSlice.SliceInfoLong(start, slice.getIntStop(), slice.getIntStep());
        }

        @Specialization
        static PSlice.SliceInfoLong doSliceObject(Node inliningTarget, PObjectSlice slice,
                        @Cached SliceLossyCastToLong toInt,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            /* this is harder to get right than you might think */
            long start, stop, step;
            if (slice.getStep() == PNone.NONE) {
                step = 1;
            } else {
                step = toInt.execute(inliningTarget, slice.getStep());
                if (step == 0) {
                    throw raiseNode.raise(inliningTarget, ValueError, ErrorMessages.SLICE_STEP_CANNOT_BE_ZERO);
                }
                /*
                 * Same as in CPython 'PySlice_Unpack': Here step might be -Long.MAX_VALUE-1; in
                 * this case we replace it with -Long.MAX_VALUE. This doesn't affect the semantics,
                 * and it guards against later undefined behaviour resulting from code that does
                 * "step = -step" as part of a slice reversal.
                 */
                if (step < -Long.MAX_VALUE) {
                    step = -Long.MAX_VALUE;
                }
            }

            if (slice.getStart() == PNone.NONE) {
                start = step < 0 ? Long.MAX_VALUE : 0;
            } else {
                start = toInt.execute(inliningTarget, slice.getStart());
            }

            if (slice.getStop() == PNone.NONE) {
                stop = step < 0 ? Long.MIN_VALUE : Long.MAX_VALUE;
            } else {
                stop = toInt.execute(inliningTarget, slice.getStop());
            }

            return new PSlice.SliceInfoLong(start, stop, step);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    @ImportStatic({PythonOptions.class, PGuards.class})
    public abstract static class SliceCastToToBigInt extends Node {

        public abstract Object execute(Node inliningTarget, Object x);

        @Specialization
        protected static Object doNone(@SuppressWarnings("unused") PNone i) {
            return PNone.NONE;
        }

        @Specialization(guards = "!isPNone(i)")
        protected static Object doGeneric(Node inliningTarget, Object i,
                        @Cached InlinedBranchProfile exceptionProfile,
                        @Cached PRaiseNode raise,
                        @Cached CastToJavaBigIntegerNode cast) {
            try {
                return cast.execute(inliningTarget, i);
            } catch (PException e) {
                exceptionProfile.enter(inliningTarget);
                throw raise.raise(inliningTarget, TypeError, ErrorMessages.SLICE_INDICES_MUST_BE_INT_NONE_HAVE_INDEX);
            }
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    @ImportStatic({PythonOptions.class, PGuards.class})
    public abstract static class SliceExactCastToInt extends Node {

        public abstract Object execute(Frame frame, Node inliningTarget, Object x);

        @Specialization
        protected static Object doNone(@SuppressWarnings("unused") PNone i) {
            return PNone.NONE;
        }

        @Specialization(guards = "!isPNone(i)")
        protected static Object doGeneric(Node inliningTarget, Object i,
                        @Cached PRaiseNode raise,
                        @Cached PyIndexCheckNode indexCheckNode,
                        @Cached PyNumberAsSizeNode asSizeNode) {
            if (indexCheckNode.execute(inliningTarget, i)) {
                return asSizeNode.executeExact(null, inliningTarget, i);
            }
            throw raise.raise(inliningTarget, TypeError, ErrorMessages.SLICE_INDICES_MUST_BE_INT_NONE_HAVE_INDEX);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    @ImportStatic({PythonOptions.class, PGuards.class})
    public abstract static class SliceLossyCastToInt extends Node {

        public abstract Object execute(Node inliningTarget, Object x);

        @Specialization
        protected static Object doNone(@SuppressWarnings("unused") PNone i) {
            return PNone.NONE;
        }

        @Specialization(guards = "!isPNone(i)")
        protected static Object doGeneric(Node inliningTarget, Object i,
                        @Cached InlinedBranchProfile exceptionProfile,
                        @Cached PRaiseNode raise,
                        @Cached PyIndexCheckNode indexCheckNode,
                        @Cached PyNumberAsSizeNode asSizeNode) {
            if (indexCheckNode.execute(inliningTarget, i)) {
                return asSizeNode.executeLossy(null, inliningTarget, i);
            }
            exceptionProfile.enter(inliningTarget);
            throw raise.raise(inliningTarget, TypeError, ErrorMessages.SLICE_INDICES_MUST_BE_INT_NONE_HAVE_INDEX);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    @ImportStatic({PythonOptions.class, PGuards.class})
    public abstract static class SliceLossyCastToLong extends Node {

        public abstract long execute(Node inliningTarget, Object x);

        @Specialization(guards = "!isPNone(i)")
        static long doGeneric(Node inliningTarget, Object i,
                        @Cached PRaiseNode raise,
                        @Cached PyIndexCheckNode indexCheckNode,
                        @Cached(inline = false) PyLongSign signNode,
                        @Cached PyLongAsLongAndOverflowNode asSizeNode) {
            if (indexCheckNode.execute(inliningTarget, i)) {
                try {
                    return asSizeNode.execute(null, inliningTarget, i);
                } catch (OverflowException e) {
                    if (signNode.execute(i) < 0) {
                        return Long.MIN_VALUE;
                    }
                    return Long.MAX_VALUE;
                }
            }
            throw raise.raise(inliningTarget, TypeError, ErrorMessages.SLICE_INDICES_MUST_BE_INT_NONE_HAVE_INDEX);
        }
    }
}
