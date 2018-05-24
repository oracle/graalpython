/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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
package com.oracle.graal.python.runtime.sequence;

import java.math.BigInteger;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;

public class SequenceUtil {

    public static final String STEP_CANNOT_BE_ZERO = "slice step cannot be zero";

    public static final class NormalizeIndexNode extends Node {
        public static final String RANGE_OUT_OF_BOUNDS = "range index out of range";
        public static final String TUPLE_OUT_OF_BOUNDS = "tuple index out of range";
        public static final String LIST_OUT_OF_BOUNDS = "list index out of range";
        public static final String LIST_ASSIGN_OUT_OF_BOUNDS = "list assignment index out of range";
        public static final String ARRAY_OUT_OF_BOUNDS = "array index out of range";
        public static final String ARRAY_ASSIGN_OUT_OF_BOUNDS = "array assignment index out of range";

        private final ConditionProfile negativeIndexProfile = ConditionProfile.createBinaryProfile();
        private final ConditionProfile outOfBoundsProfile = ConditionProfile.createBinaryProfile();
        @CompilationFinal private PythonCore core;

        public static NormalizeIndexNode create() {
            return new NormalizeIndexNode();
        }

        public int forList(int index, int length) {
            return execute(index, length, LIST_OUT_OF_BOUNDS);
        }

        public int forList(long index, int length) {
            return execute(index, length, LIST_OUT_OF_BOUNDS);
        }

        public int forList(PInt index, int length) {
            return execute(index, length, LIST_OUT_OF_BOUNDS);
        }

        public int forListAssign(int index, int length) {
            return execute(index, length, LIST_ASSIGN_OUT_OF_BOUNDS);
        }

        public int forListAssign(long index, int length) {
            return execute(index, length, LIST_ASSIGN_OUT_OF_BOUNDS);
        }

        public int forTuple(int index, int length) {
            return execute(index, length, TUPLE_OUT_OF_BOUNDS);
        }

        public int forTuple(long index, int length) {
            return execute(index, length, TUPLE_OUT_OF_BOUNDS);
        }

        public int forArray(int index, int length) {
            return execute(index, length, ARRAY_OUT_OF_BOUNDS);
        }

        public int forArray(long index, int length) {
            return execute(index, length, ARRAY_OUT_OF_BOUNDS);
        }

        public int forArrayAssign(int index, int length) {
            return execute(index, length, ARRAY_ASSIGN_OUT_OF_BOUNDS);
        }

        public int forArrayAssign(long index, int length) {
            return execute(index, length, ARRAY_ASSIGN_OUT_OF_BOUNDS);
        }

        public int forRange(int index, int length) {
            return execute(index, length, RANGE_OUT_OF_BOUNDS);
        }

        public int forRange(long index, int length) {
            return execute(index, length, RANGE_OUT_OF_BOUNDS);
        }

        public int execute(PInt index, int length, String outOfBoundsMessage) {
            int idx = index.intValue();
            if (outOfBoundsProfile.profile(!eq(index.getValue(), idx))) {
                // anything outside the int range is considered to be out of range
                if (core == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    core = PythonLanguage.getCore();
                }
                throw core.raise(PythonErrorType.IndexError, this, outOfBoundsMessage);
            }
            return execute(idx, length, outOfBoundsMessage);
        }

        @TruffleBoundary
        private static final boolean eq(BigInteger index, int idx) {
            return index.equals(BigInteger.valueOf(idx));
        }

        public int execute(long index, int length, String outOfBoundsMessage) {
            int idx = (int) index;
            if (outOfBoundsProfile.profile(idx != index)) {
                // anything outside the int range is considered to be out of range
                if (core == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    core = PythonLanguage.getCore();
                }
                throw core.raise(PythonErrorType.IndexError, this, outOfBoundsMessage);
            }
            return execute(idx, length, outOfBoundsMessage);
        }

        public int execute(int index, int length, String outOfBoundsMessage) {
            int idx = index;
            if (negativeIndexProfile.profile(idx < 0)) {
                idx += length;
            }
            if (outOfBoundsProfile.profile(idx < 0 || idx >= length)) {
                if (core == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    core = PythonLanguage.getCore();
                }
                throw core.raise(PythonErrorType.IndexError, this, outOfBoundsMessage);
            }
            return idx;
        }
    }

    public static final int MISSING_INDEX = Integer.MIN_VALUE;

    public static int normalizeSliceStart(PSlice slice, int size, String outOfBoundsMessage) {
        return normalizeSliceStart(slice.getStart(), slice.getStep() == MISSING_INDEX ? 1 : slice.getStep(), size, outOfBoundsMessage);
    }

    public static int normalizeSliceStart(int start, int step, int size, String outOfBoundsMessage) {
        if (start == MISSING_INDEX) {
            return step < 0 ? size - 1 : 0;
        }

        return normalizeIndex(start, size, outOfBoundsMessage);
    }

    public static int normalizeSliceStop(PSlice slice, int size, String outOfBoundsMessage) {
        return normalizeSliceStop(slice.getStop(), slice.getStep() == MISSING_INDEX ? 1 : slice.getStep(), size, outOfBoundsMessage);
    }

    public static int normalizeSliceStop(int stop, int step, int size, String outOfBoundsMessage) {
        if (stop == MISSING_INDEX) {
            return step < 0 ? -1 : size;
        }

        return normalizeIndex(stop, size, outOfBoundsMessage);
    }

    public static int normalizeSliceStep(PSlice slice) {
        return normalizeSliceStep(slice.getStep());
    }

    public static int normalizeSliceStep(int step) {
        if (step == MISSING_INDEX) {
            return 1;
        }
        if (step == 0) {
            throw PythonLanguage.getCore().raise(PythonErrorType.ValueError, STEP_CANNOT_BE_ZERO);
        }
        return step;
    }

    /**
     * Make step a long in case adding the start, stop and step together overflows an int.
     */
    public static final int sliceLength(int start, int stop, long step) {
        int ret;
        if (step > 0) {
            ret = (int) ((stop - start + step - 1) / step);
        } else {
            ret = (int) ((stop - start + step + 1) / step);
        }

        if (ret < 0) {
            return 0;
        }

        return ret;
    }

    /*
     * Compare the specified object/length pairs.
     *
     * @return value >= 0 is the index where the sequences differs. -1: reached the end of sequence1
     * without a difference -2: reached the end of both sequences without a difference -3: reached
     * the end of sequence2 without a difference
     */
    public static int cmp(PSequence sequence1, PSequence sequence2) {
        int length1 = sequence1.len();
        int length2 = sequence2.len();

        for (int i = 0; i < length1 && i < length2; i++) {
            if (!sequence1.getItem(i).equals(sequence2.getItem(i))) {
                return i;
            }
        }
        if (length1 == length2) {
            return -2;
        }
        return length1 < length2 ? -1 : -3;
    }

    public static int normalizeIndex(int index, int length, String outOfBoundsMessage) {
        int idx = index;
        if (idx < 0) {
            idx += length;
        }
        if (idx < 0 || idx >= length) {
            throw PythonLanguage.getCore().raise(PythonErrorType.IndexError, outOfBoundsMessage);
        }
        return idx;
    }

}
