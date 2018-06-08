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
package com.oracle.graal.python.builtins.objects.slice;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.runtime.sequence.SequenceUtil.MISSING_INDEX;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;

public class PSlice extends PythonBuiltinObject {

    protected int start;
    protected int stop;
    protected final int step;

    public PSlice(PythonClass cls, int start, int stop, int step) {
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

    public SliceInfo computeActualIndices(int len) {
        int newStart = this.start;
        int newStop = this.stop;
        int newStep = this.step;
        int tmpStart, tmpStop;
        int newLen;

        if (newStep == MISSING_INDEX) {
            newStep = 1;
        } else {
            if (newStep == 0) {
                CompilerDirectives.transferToInterpreter();
                throw PythonLanguage.getCore().raise(ValueError, "slice step cannot be zero");
            }
        }
        tmpStart = newStep < 0 ? len - 1 : 0;
        tmpStop = newStep < 0 ? -1 : len;

        if (newStart == MISSING_INDEX) {
            newStart = tmpStart;
        } else {
            if (newStart < 0) {
                newStart += len;
            }
            if (newStart < 0) {
                newStart = newStep < 0 ? -1 : 0;
            }
            if (newStart >= len) {
                newStart = newStep < 0 ? len - 1 : len;
            }
        }
        if (newStop == MISSING_INDEX) {
            newStop = tmpStop;
        } else {
            if (newStop < 0) {
                newStop += len;
            }
            if (newStop < 0) {
                newStop = newStep < 0 ? -1 : 0;
            }
            if (newStop >= len) {
                newStop = newStep < 0 ? len - 1 : len;
            }
        }
        if ((newStep < 0 && newStop >= newStart) || (newStep > 0 && newStart >= newStop)) {
            newLen = 0;
        } else if (newStep < 0) {
            newLen = (newStop - newStart + 1) / newStep + 1;
        } else {
            newLen = (newStop - newStart - 1) / newStep + 1;
        }
        return new SliceInfo(newStart, newStop, newStep, newLen);
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
}
