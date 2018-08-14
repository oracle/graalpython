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

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.runtime.exception.PythonErrorType;

public class SequenceUtil {

    public static final String STEP_CANNOT_BE_ZERO = "slice step cannot be zero";

    public static final int MISSING_INDEX = Integer.MIN_VALUE;

    public static int normalizeSliceStart(PSlice slice, int size) {
        return normalizeSliceStart(slice.getStart(), slice.getStep() == MISSING_INDEX ? 1 : slice.getStep(), size);
    }

    public static int normalizeSliceStart(int start, int step, int size) {
        if (start == MISSING_INDEX) {
            return step < 0 ? size - 1 : 0;
        }

        return normalizeIndexUnchecked(start, size);
    }

    public static int normalizeSliceStop(PSlice slice, int size) {
        return normalizeSliceStop(slice.getStop(), slice.getStep() == MISSING_INDEX ? 1 : slice.getStep(), size);
    }

    public static int normalizeSliceStop(int stop, int step, int size) {
        if (stop == MISSING_INDEX) {
            return step < 0 ? -1 : size;
        }

        return normalizeIndexUnchecked(stop, size);
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

    public static int normalizeIndex(int index, int length, String outOfBoundsMessage) {
        int normalized = normalizeIndexUnchecked(index, length);
        if (normalized < 0 || normalized >= length) {
            throw PythonLanguage.getCore().raise(PythonErrorType.IndexError, outOfBoundsMessage);
        }
        return normalized;
    }

    private static int normalizeIndexUnchecked(int index, int length) {
        return index < 0 ? index + length : index;
    }
}
