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
package com.oracle.graal.python.builtins.objects.range;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.IndexError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.PImmutableSequence;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.SequenceUtil;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;

public final class PRange extends PImmutableSequence {

    private final int start;
    private final int stop;
    private final int step;
    private final int length;

    public PRange(PythonClass clazz, int stop) {
        this(clazz, 0, stop, 1);
    }

    public PRange(PythonClass clazz, int start, int stop) {
        this(clazz, start, stop, 1);
    }

    public PRange(PythonClass clazz, int start, int stop, int step) {
        super(clazz);
        if (step == 0) {
            CompilerDirectives.transferToInterpreter();
            throw PythonLanguage.getCore().raise(ValueError, "range() arg 3 must not be zero");
        }

        int n;
        if (step > 0) {
            n = getLenOfRange(start, stop, step);
        } else {
            n = getLenOfRange(stop, start, -step);
        }

        this.start = start;
        this.stop = stop;
        this.step = step;
        this.length = n;
    }

    public static int getLenOfRange(int lo, int hi, int step) {
        int n = 0;
        if (lo < hi) {
            // the base difference may be > Integer.MAX_VALUE
            long diff = (long) hi - (long) lo - 1;
            // any long > Integer.MAX_VALUE or < Integer.MIN_VALUE gets casted
            // to a
            // negative number
            n = (int) ((diff / step) + 1);
            if (n < 0) {
                CompilerDirectives.transferToInterpreter();
                throw PythonLanguage.getCore().raise(OverflowError, "range() result has too many items");
            }
        }
        return n;
    }

    public int getStart() {
        return start;
    }

    public int getStep() {
        return step;
    }

    public int getStop() {
        return stop;
    }

    public Object getItemNormalized(int index) {
        if (index >= length) {
            CompilerDirectives.transferToInterpreter();
            throw PythonLanguage.getCore().raise(IndexError, "range object index out of range");
        }

        return index * step + start;
    }

    @Override
    public Object getSlice(PythonObjectFactory factory, int sliceStart, int sliceStop, int sliceStep, int slicelength) {
        // Parameters 'sliceStart', 'sliceStop', ... are again a range but of indices.
        int newStep = step * sliceStep;
        int newStart = sliceStart == SequenceUtil.MISSING_INDEX ? start : start + sliceStart * step;
        int newStop = sliceStop == SequenceUtil.MISSING_INDEX ? stop : Math.min(stop, newStart + slicelength * newStep);
        return factory.createRange(newStart, newStop, newStep);
    }

    @Override
    public boolean lessThan(PSequence sequence) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int len() {
        return length;
    }

    @Override
    public SequenceStorage getSequenceStorage() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int index(Object value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        if (step == 1) {
            return String.format("range(%d, %d)", start, stop);
        } else {
            return String.format("range(%d, %d, %d)", start, stop, step);
        }

    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof PRange) {
            PRange otherRange = (PRange) other;
            return otherRange.getStart() == getStart() && otherRange.getStop() == getStop() && otherRange.getStep() == getStep();
        } else {
            return false;
        }
    }

}
