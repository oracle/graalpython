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
package com.oracle.graal.python.nodes.subscript;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.IndexError;
import static com.oracle.graal.python.runtime.sequence.SequenceUtil.MISSING_INDEX;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;

@NodeChildren({@NodeChild(value = "first", type = PNode.class), @NodeChild(value = "second", type = PNode.class), @NodeChild(value = "third", type = PNode.class)})
@TypeSystemReference(PythonArithmeticTypes.class) // because bool -> int works here
public abstract class SliceLiteralNode extends PNode {

    public abstract PSlice execute(Object start, Object stop, Object step);

    @Specialization
    public PSlice doInt(int start, int stop, int step) {
        return factory().createSlice(start, stop, step);
    }

    @Specialization
    public PSlice doInt(@SuppressWarnings("unused") PNone start, int stop, int step) {
        return factory().createSlice(MISSING_INDEX, stop, step);
    }

    @Specialization
    public PSlice doInt(int start, int stop, @SuppressWarnings("unused") PNone step) {
        return factory().createSlice(start, stop, 1);
    }

    @Specialization(rewriteOn = ArithmeticException.class)
    public PSlice doLongExact(long start, long stop, @SuppressWarnings("unused") PNone step) {
        return factory().createSlice(PInt.intValueExact(start), PInt.intValueExact(stop), 1);
    }

    @Specialization
    public PSlice doLongGeneric(long start, long stop, @SuppressWarnings("unused") PNone step) {
        try {
            return factory().createSlice(PInt.intValueExact(start), PInt.intValueExact(stop), 1);
        } catch (ArithmeticException e) {
            throw raiseIndexError();
        }
    }

    @Specialization(rewriteOn = ArithmeticException.class)
    public PSlice doPIntExact(PInt start, PInt stop, @SuppressWarnings("unused") PNone step) {
        return factory().createSlice(start.intValueExact(), stop.intValueExact(), 1);
    }

    @Specialization
    public PSlice doPIntGeneric(PInt start, PInt stop, @SuppressWarnings("unused") PNone step) {
        try {
            return factory().createSlice(start.intValueExact(), stop.intValueExact(), 1);
        } catch (ArithmeticException e) {
            throw raiseIndexError();
        }
    }

    @Specialization(rewriteOn = ArithmeticException.class)
    public PSlice doPIntLongExact(PInt start, long stop, @SuppressWarnings("unused") PNone step) {
        return factory().createSlice(start.intValueExact(), PInt.intValueExact(stop), 1);
    }

    @Specialization
    public PSlice doPIntLongGeneric(PInt start, long stop, @SuppressWarnings("unused") PNone step) {
        try {
            return factory().createSlice(start.intValueExact(), PInt.intValueExact(stop), 1);
        } catch (ArithmeticException e) {
            throw raiseIndexError();
        }
    }

    @Specialization(rewriteOn = ArithmeticException.class)
    public PSlice doLongPIntExact(long start, PInt stop, @SuppressWarnings("unused") PNone step) {
        return factory().createSlice(PInt.intValueExact(start), stop.intValueExact(), 1);
    }

    @Specialization
    public PSlice doLongPIntGeneric(long start, PInt stop, @SuppressWarnings("unused") PNone step) {
        try {
            return factory().createSlice(PInt.intValueExact(start), stop.intValueExact(), 1);
        } catch (ArithmeticException e) {
            throw raiseIndexError();
        }
    }

    @Specialization
    public PSlice doSlice(int start, @SuppressWarnings("unused") PNone stop, @SuppressWarnings("unused") PNone step) {
        return factory().createSlice(start, MISSING_INDEX, MISSING_INDEX);
    }

    @Specialization
    public PSlice doPSlice(long start, @SuppressWarnings("unused") PNone stop, @SuppressWarnings("unused") PNone step) {
        try {
            return factory().createSlice(PInt.intValueExact(start), MISSING_INDEX, 1);
        } catch (ArithmeticException e) {
            throw raiseIndexError();
        }
    }

    @Specialization
    public PSlice doSlice(int start, @SuppressWarnings("unused") PNone stop, int step) {
        return factory().createSlice(start, MISSING_INDEX, step);
    }

    @Specialization
    public PSlice doSlice(@SuppressWarnings("unused") PNone start, int stop, @SuppressWarnings("unused") PNone step) {
        return factory().createSlice(MISSING_INDEX, stop, MISSING_INDEX);
    }

    @Specialization
    public PSlice doSlice(@SuppressWarnings("unused") PNone start, long stop, @SuppressWarnings("unused") PNone step) {
        return factory().createSlice(MISSING_INDEX, (int) stop, MISSING_INDEX);
    }

    @Specialization
    public PSlice doSlice(@SuppressWarnings("unused") PNone start, @SuppressWarnings("unused") PNone stop, int step) {
        return factory().createSlice(MISSING_INDEX, MISSING_INDEX, step);
    }

    @Specialization
    @SuppressWarnings("unused")
    public PSlice doSlice(PNone start, PNone stop, PNone step) {
        return factory().createSlice(MISSING_INDEX, MISSING_INDEX, 1);
    }

    public abstract PNode getFirst();

    public abstract PNode getSecond();

    public abstract PNode getThird();

    public static PNode create(PNode lower, PNode upper, PNode step) {
        return SliceLiteralNodeGen.create(lower, upper, step);
    }

    public static SliceLiteralNode create() {
        return SliceLiteralNodeGen.create(null, null, null);
    }
}
