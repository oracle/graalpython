/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.range;

import static com.oracle.graal.python.nodes.ErrorMessages.ARG_MUST_NOT_BE_ZERO;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.math.BigInteger;

import com.oracle.graal.python.builtins.objects.slice.PSlice.SliceInfo;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.util.CastToJavaBigIntegerNode;
import com.oracle.graal.python.nodes.util.ExactMath;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

public abstract class RangeNodes {

    @GenerateNodeFactory
    @GenerateUncached
    @ImportStatic(SpecialMethodNames.class)
    public abstract static class CreateBigRangeNode extends Node {
        public abstract PBigRange execute(Object start, Object stop, Object step, PythonObjectFactory factory);

        @TruffleBoundary
        private static void checkStepZero(BigInteger stepBI, PRaiseNode raise) {
            if (stepBI.compareTo(BigInteger.ZERO) == 0) {
                throw raise.raise(ValueError, ARG_MUST_NOT_BE_ZERO, "range()", 3);
            }
        }

        @Specialization
        PBigRange createBigRange(Object start, Object stop, Object step, PythonObjectFactory factory,
                        @Cached RangeNodes.LenOfRangeNode lenOfRangeNode,
                        @Cached CastToJavaBigIntegerNode startToBI,
                        @Cached CastToJavaBigIntegerNode stopToBI,
                        @Cached CastToJavaBigIntegerNode stepToBI,
                        @Cached PRaiseNode raise) {
            BigInteger stepBI = stepToBI.execute(step);
            checkStepZero(stepBI, raise);
            BigInteger startBI = startToBI.execute(start);
            BigInteger stopBI = stopToBI.execute(stop);
            BigInteger len = lenOfRangeNode.execute(startBI, stopBI, stepBI);
            return factory.createBigRange(factory.createInt(startBI), factory.createInt(stopBI), factory.createInt(stepBI), factory.createInt(len));
        }

    }

    // Base class used just for code sharing
    @ImportStatic(SpecialMethodNames.class)
    public abstract static class LenOfIntRangeBaseNode extends Node {

        public abstract int executeInt(int start, int stop, int step) throws OverflowException;

        @Specialization(guards = {"step > 0", "lo > 0", "lo < hi"})
        static int simple(int lo, int hi, int step) {
            return 1 + ((hi - 1 - lo) / step);
        }

        @Specialization(guards = {"step > 0", "lo >= hi"})
        static int zero1(@SuppressWarnings("unused") int lo, @SuppressWarnings("unused") int hi, @SuppressWarnings("unused") int step) {
            return 0;
        }

        @Specialization(guards = {"step < 0", "lo < 0", "lo > hi"})
        static int simpleNegative(int lo, int hi, int step) {
            return 1 + ((lo - 1 - hi) / -step);
        }

        @Specialization(guards = {"step < 0", "lo <= hi"})
        static int zero2(@SuppressWarnings("unused") int lo, @SuppressWarnings("unused") int hi, @SuppressWarnings("unused") int step) {
            return 0;
        }
    }

    /**
     * Computes the length of a range given its start, stop, step. Produces the result of the same
     * type as the arguments ({@code int} vs {@link BigInteger}). Overflow with integer arguments is
     * silent! However, unwanted overflows are checked with an assertion.
     */
    @GenerateNodeFactory
    @GenerateUncached
    @ImportStatic(SpecialMethodNames.class)
    public abstract static class LenOfRangeNode extends LenOfIntRangeBaseNode {
        public abstract BigInteger execute(BigInteger start, BigInteger stop, BigInteger step);

        @Override // removes the checked exception
        public abstract int executeInt(int start, int stop, int step);

        public int len(SliceInfo slice) throws ArithmeticException {
            return executeInt(slice.start, slice.stop, slice.step);
        }

        @Specialization(guards = {"step > 0", "lo < hi"})
        static int mightBeBig1(int lo, int hi, int step) throws ArithmeticException {
            long diff = (hi - (long) lo) - 1L;
            long result = (diff / step) + 1L;
            assert result == (int) result;
            return (int) result;
        }

        @Specialization(guards = {"step < 0", "lo > hi"})
        static int mightBeBig2(int lo, int hi, int step) throws ArithmeticException {
            long diff = (lo - (long) hi) - 1L;
            long result = (diff / -(long) step) + 1L;
            assert result == (int) result;
            return (int) result;
        }

        @Specialization
        @TruffleBoundary
        static Object doBigInt(BigInteger lo, BigInteger hi, BigInteger step) {
            BigInteger diff;
            BigInteger zero = BigInteger.ZERO;
            BigInteger one = BigInteger.ONE;

            BigInteger n = zero;
            if (step.compareTo(zero) > 0 && lo.compareTo(hi) < 0) {
                // if (step > 0 && lo < hi)
                // 1 + (hi - 1 - lo) / step
                diff = hi.subtract(one).subtract(lo);
                n = diff.divide(step).add(one);
            } else if (step.compareTo(zero) < 0 && lo.compareTo(hi) > 0) {
                // else if (step < 0 && lo > hi)
                // 1 + ((lo - 1 - hi) / -step)
                diff = lo.subtract(one).subtract(hi);
                n = diff.divide(step.negate()).add(one);
            }

            return n;
        }
    }

    /**
     * Attempts to produce length of an int range given its start, stop and step. This calculation
     * may overflow, which results in {@link OverflowException}. It is then the responsibility of
     * the caller to widen the arguments' types if necessary.
     */
    @GenerateNodeFactory
    @GenerateUncached
    @ImportStatic(SpecialMethodNames.class)
    public abstract static class LenOfIntRangeNodeExact extends LenOfIntRangeBaseNode {
        @Specialization(guards = {"step > 0", "lo < hi"})
        static int mightBeBig1(int lo, int hi, int step) throws OverflowException {
            long diff = ExactMath.subtractExact(ExactMath.subtractExact(hi, (long) lo), 1);
            return ExactMath.toIntExact(ExactMath.addExact(diff / step, 1));
        }

        @Specialization(guards = {"step < 0", "lo > hi"})
        static int mightBeBig2(int lo, int hi, int step) throws OverflowException {
            long diff = ExactMath.subtractExact(ExactMath.subtractExact(lo, (long) hi), 1);
            return ExactMath.toIntExact(ExactMath.addExact(diff / -(long) step, 1));
        }
    }

    /**
     * This is only applicable to slow path computations. <i><b>For internal use only.</b></i>
     */
    @GenerateNodeFactory
    @GenerateUncached
    public abstract static class CoerceToBigRange extends PNodeWithContext {

        public abstract PBigRange execute(PRange range, PythonObjectFactory factory);

        @Specialization
        PBigRange doIntRange(PIntRange range, PythonObjectFactory factory,
                        @Cached CreateBigRangeNode cast) {
            return cast.execute(range.getIntStart(), range.getIntStop(), range.getIntStep(), factory);
        }

        @Specialization
        PBigRange doBigRange(PBigRange range, @SuppressWarnings("unused") PythonObjectFactory factory) {
            return range;
        }

        public static CoerceToBigRange create() {
            return RangeNodesFactory.CoerceToBigRangeFactory.create();
        }
    }

    @GenerateNodeFactory
    @GenerateUncached
    public abstract static class PRangeStartNode extends PNodeWithContext {

        public abstract Object execute(PRange range);

        @Specialization
        Object doIntRange(PIntRange range) {
            return range.getIntStart();
        }

        @Specialization
        Object doBigRange(PBigRange range) {
            return range.getStart();
        }

        public static PRangeStartNode create() {
            return RangeNodesFactory.PRangeStartNodeFactory.create();
        }
    }

    @GenerateNodeFactory
    @GenerateUncached
    public abstract static class PRangeStopNode extends PNodeWithContext {

        public abstract Object execute(PRange range);

        @Specialization
        Object doIntRange(PIntRange range) {
            return range.getIntStop();
        }

        @Specialization
        Object doBigRange(PBigRange range) {
            return range.getStop();
        }

        public static PRangeStopNode create() {
            return RangeNodesFactory.PRangeStopNodeFactory.create();
        }
    }

    @GenerateNodeFactory
    @GenerateUncached
    public abstract static class PRangeStepNode extends PNodeWithContext {

        public abstract Object execute(PRange range);

        @Specialization
        Object doIntRange(PIntRange range) {
            return range.getIntStep();
        }

        @Specialization
        Object doBigRange(PBigRange range) {
            return range.getStep();
        }

        public static PRangeStepNode create() {
            return RangeNodesFactory.PRangeStepNodeFactory.create();
        }
    }

    @GenerateNodeFactory
    @GenerateUncached
    public abstract static class PRangeLengthNode extends PNodeWithContext {

        public abstract Object execute(PRange range);

        @Specialization
        Object doIntRange(PIntRange range) {
            return range.getIntLength();
        }

        @Specialization
        Object doBigRange(PBigRange range) {
            return range.getLength();
        }

        public static PRangeLengthNode create() {
            return RangeNodesFactory.PRangeLengthNodeFactory.create();
        }
    }
}
