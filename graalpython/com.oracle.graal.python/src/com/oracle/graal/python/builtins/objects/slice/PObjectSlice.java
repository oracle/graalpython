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
package com.oracle.graal.python.builtins.objects.slice;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.math.BigInteger;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.CompilerDirectives.ValueType;

public final class PObjectSlice extends PSlice {

    protected final Object startObject;
    protected final Object stopObject;
    @CompilationFinal protected Object stepObject;

    public PObjectSlice(Object start, Object stop, Object step) {
        this.startObject = start;
        this.stopObject = stop;
        this.stepObject = step;
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

    @Override
    public final Object getStart() {
        return startObject;
    }

    @Override
    public final Object getStop() {
        return stopObject;
    }

    @Override
    public final Object getStep() {
        return stepObject;
    }

    public SliceInfo computeIndices(int length) {
        return PSlice.computeIndices(getStart(), getStop(), getStep(), length);
    }

    @ValueType
    public static final class SliceObjectInfo {
        public final Object start;
        public final Object stop;
        public final Object step;

        public SliceObjectInfo(Object start, Object stop, Object step) {
            this.start = start;
            this.stop = stop;
            this.step = step;
        }
    }

    private static final BigInteger ONE = BigInteger.ONE;
    private static final BigInteger ZERO = BigInteger.ZERO;

    private static boolean pyLT(BigInteger left, BigInteger right) {
        return left.compareTo(right) < 0;
    }

    private static boolean pyGT(BigInteger left, BigInteger right) {
        return left.compareTo(right) > 0;
    }

    private static int pySign(BigInteger n) {
        return n.compareTo(ZERO);
    }

    /**
     * _PySlice_GetLongIndices
     */
    @TruffleBoundary
    public static SliceObjectInfo computeIndicesSlowPath(PObjectSlice slice, Object lengthIn, PythonObjectFactory factory) {
        boolean stepIsNegative;
        BigInteger lower, upper;
        BigInteger start, stop, step, length;
        length = (BigInteger) lengthIn;
        if (pySign(length) < 0) {
            CompilerDirectives.transferToInterpreter();
            throw PythonLanguage.getCore().raise(ValueError, ErrorMessages.LENGTH_SHOULD_NOT_BE_NEG);
        }
        if (slice.getStep() == PNone.NONE) {
            step = ONE;
            stepIsNegative = false;
        } else {
            step = (BigInteger) slice.getStep();
            stepIsNegative = pySign(step) < 0;
            if (pySign(step) == 0) {
                CompilerDirectives.transferToInterpreter();
                throw PythonLanguage.getCore().raise(ValueError, ErrorMessages.SLICE_STEP_CANNOT_BE_ZERO);
            }
        }

        /* Find lower and upper bounds for start and stop. */
        if (stepIsNegative) {
            lower = BigInteger.valueOf(-1);
            upper = lower.add(length);
        } else {
            lower = ZERO;
            upper = length;
        }

        /* Compute start. */
        if (slice.getStart() == PNone.NONE) {
            start = stepIsNegative ? upper : lower;
        } else {
            start = (BigInteger) slice.getStart();
            if (pySign(start) < 0) {
                start = start.add(length);
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
        if (slice.getStop() == PNone.NONE) {
            stop = stepIsNegative ? lower : upper;
        } else {
            stop = (BigInteger) slice.getStop();
            if (pySign(stop) < 0) {
                stop = stop.add(length);
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

        if (factory != null) {
            return new SliceObjectInfo(factory.createInt(start), factory.createInt(stop), factory.createInt(step));
        } else {
            return new SliceObjectInfo(start, stop, step);
        }
    }
}
