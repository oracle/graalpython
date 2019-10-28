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
package com.oracle.graal.python.builtins.objects.range;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import com.oracle.graal.python.builtins.objects.object.PythonDataModelLibrary;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.runtime.sequence.PImmutableSequence;
import com.oracle.graal.python.runtime.sequence.storage.RangeSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

@ExportLibrary(PythonDataModelLibrary.class)
public final class PRange extends PImmutableSequence {

    private final int start;
    private final int stop;
    private final int step;
    private final int length;

    public PRange(LazyPythonClass clazz, int stop) {
        this(clazz, 0, stop, 1);
    }

    public PRange(LazyPythonClass clazz, int start, int stop) {
        this(clazz, start, stop, 1);
    }

    public PRange(LazyPythonClass clazz, int start, int stop, int step) {
        super(clazz);
        if (step == 0) {
            CompilerDirectives.transferToInterpreter();
            throw PRaiseNode.getUncached().raise(ValueError, "range() arg 3 must not be zero");
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
                throw PRaiseNode.getUncached().raise(OverflowError, "range() result has too many items");
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

    public int getItemNormalized(int index) {
        assert index < length;
        return index * step + start;
    }

    public int len() {
        return length;
    }

    @Override
    public SequenceStorage getSequenceStorage() {
        return new RangeSequenceStorage(this);
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

    @ExportMessage
    public boolean isIterable() {
        return true;
    }
}
