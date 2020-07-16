/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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
package com.oracle.graal.python.builtins.objects.slice;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.util.Objects;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.ValueType;

public abstract class PSlice extends PythonBuiltinObject {

    public PSlice() {
        super(PythonBuiltinClassType.PSlice, PythonBuiltinClassType.PSlice.getInstanceShape());
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        StringBuilder str = new StringBuilder("slice(");
        str.append(getStart()).append(", ");
        str.append(getStop()).append(", ");
        str.append(getStep());

        return str.append(")").toString();
    }

    public abstract Object getStart();

    public abstract Object getStop();

    public abstract Object getStep();

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof PSlice)) {
            return false;
        }
        if (this == obj) {
            return true;
        }
        PSlice other = (PSlice) obj;
        return (this.getStart() == other.getStart() && this.getStop() == other.getStop() && this.getStep() == other.getStep());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getStart(), this.getStop(), this.getStep());
    }

    @ValueType
    public static final class SliceInfo {
        public final int start;
        public final int stop;
        public final int step;

        public SliceInfo(int start, int stop, int step) {
            this.start = start;
            this.stop = stop;
            this.step = step;
        }
    }

    protected static void checkNegative(int length) {
        if (length < 0) {
            CompilerDirectives.transferToInterpreter();
            throw PythonLanguage.getCore().raise(ValueError, ErrorMessages.LENGTH_SHOULD_NOT_BE_NEG);
        }
    }

    private static final int ONE = 1;
    private static final int ZERO = 0;

    private static boolean pyLT(int left, int right) {
        return left < right;
    }

    private static boolean pyGT(int left, int right) {
        return left > right;
    }

    private static int pySign(int n) {
        return n == 0 ? 0 : n < 0 ? -1 : 1;
    }

    public static SliceInfo computeIndices(Object startIn, Object stopIn, Object stepIn, int length) {
        assert length >= 0;
        boolean stepIsNegative;
        int lower, upper;
        int start, stop, step;
        if (stepIn == PNone.NONE) {
            step = ONE;
            stepIsNegative = false;
        } else {
            step = (int) stepIn;
            stepIsNegative = pySign(step) < 0;
            if (pySign(step) == 0) {
                CompilerDirectives.transferToInterpreter();
                throw PythonLanguage.getCore().raise(ValueError, ErrorMessages.SLICE_STEP_CANNOT_BE_ZERO);
            }
        }

        /* Find lower and upper bounds for start and stop. */
        if (stepIsNegative) {
            lower = -1;
            upper = lower + length;
        } else {
            lower = ZERO;
            upper = length;
        }

        /* Compute start. */
        if (startIn == PNone.NONE) {
            start = stepIsNegative ? upper : lower;
        } else {
            start = (int) startIn;
            if (pySign(start) < 0) {
                start = start + length;
                // PyObject_RichCompareBool(start, lower, Py_LT);
                if (pyLT(start, lower)) {
                    start = lower;
                }
            } else {
                // PyObject_RichCompareBool(start, upper, Py_GT);
                if (pyGT(start, upper)) {
                    start = upper;
                }
            }
        }

        /* Compute stop. */
        if (stopIn == PNone.NONE) {
            stop = stepIsNegative ? lower : upper;
        } else {
            stop = (int) stopIn;
            if (pySign(stop) < 0) {
                stop = stop + length;
                // PyObject_RichCompareBool(stop, lower, Py_LT);
                if (pyLT(stop, lower)) {
                    stop = lower;
                }
            } else {
                // PyObject_RichCompareBool(stop, upper, Py_GT);
                if (pyGT(stop, upper)) {
                    stop = upper;
                }
            }
        }
        return new SliceInfo(start, stop, step);
    }
}
