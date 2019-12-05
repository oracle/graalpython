/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import java.util.Objects;

public class PSlice extends PythonBuiltinObject {

    public static final int MISSING_INDEX = Integer.MIN_VALUE;

    protected int start;
    protected int stop;
    protected int step;

    public PSlice(LazyPythonClass cls, int start, int stop, int step) {
        super(cls);
        this.start = start;
        this.stop = stop;
        this.step = step;
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        StringBuilder str = new StringBuilder("slice(");
        if (start == MISSING_INDEX) {
            str.append("None");
        } else {
            str.append(start);
        }
        str.append(", ");
        if (stop == MISSING_INDEX) {
            str.append("None");
        } else {
            str.append(stop);
        }
        str.append(", ");
        if (step == MISSING_INDEX) {
            str.append("None");
        } else {
            str.append(step);
        }

        return str.append(")").toString();
    }

    public final int getStart() {
        return start;
    }

    public final int getStop() {
        return stop;
    }

    public final int getStep() {
        return step;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof PSlice)) {
            return false;
        }
        if (this == obj) {
            return true;
        }
        PSlice other = (PSlice) obj;
        return (this.start == other.start && this.stop == other.stop && this.step == other.step);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.start, this.stop, this.step);
    }

    @ValueType
    public static final class SliceInfo {
        public final int start;
        public final int stop;
        public final int step;
        public final int length;

        public SliceInfo(int start, int stop, int step, int length) {
            this.start = start;
            this.stop = stop;
            this.step = step;
            this.length = length;
        }
    }
    public SliceInfo computeIndices(int length) {
        return PSlice.computeIndices(start, stop, step, length);

    }

    public static SliceInfo computeIndices(int start, int stop, int step, int length) {
        int tmpStart, tmpStop;
        int newLen;

        if (step == MISSING_INDEX) {
            step = 1;
        } else {
            if (step == 0) {
                CompilerDirectives.transferToInterpreter();
                throw PythonLanguage.getCore().raise(ValueError, "slice step cannot be zero");
            }
        }
        tmpStart = step < 0 ? length - 1 : 0;
        tmpStop = step < 0 ? -1 : length;

        if (start == MISSING_INDEX) {
            start = tmpStart;
        } else {
            if (start < 0) {
                start += length;
            }
            if (start < 0) {
                start = step < 0 ? -1 : 0;
            }
            if (start >= length) {
                start = step < 0 ? length - 1 : length;
            }
        }
        if (stop == MISSING_INDEX) {
            stop = tmpStop;
        } else {
            if (stop < 0) {
                stop += length;
            }
            if (stop < 0) {
                stop = step < 0 ? -1 : 0;
            }
            if (stop >= length) {
                stop = step < 0 ? length - 1 : length;
            }
        }
        if ((step < 0 && stop >= start) || (step > 0 && start >= stop)) {
            newLen = 0;
        } else if (step < 0) {
            newLen = (stop - start + 1) / step + 1;
        } else {
            newLen = (stop - start - 1) / step + 1;
        }
        return new SliceInfo(start, stop, step, newLen);
    }
}
