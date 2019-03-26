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
package com.oracle.graal.python.nodes.subscript;

import static com.oracle.graal.python.builtins.objects.slice.PSlice.MISSING_INDEX;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.subscript.SliceLiteralNodeGen.CastToSliceComponentNodeGen;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.profiles.BranchProfile;

@NodeChild(value = "first", type = ExpressionNode.class)
@NodeChild(value = "second", type = ExpressionNode.class)
@NodeChild(value = "third", type = ExpressionNode.class)
@TypeSystemReference(PythonArithmeticTypes.class) // because bool -> int works here
public abstract class SliceLiteralNode extends ExpressionNode {
    @Child private PythonObjectFactory factory = PythonObjectFactory.create();
    @Child private CastToSliceComponentNode castStartNode;
    @Child private CastToSliceComponentNode castStopNode;
    @Child private CastToSliceComponentNode castStepNode;

    public abstract PSlice execute(Object start, Object stop, Object step);

    @Specialization
    public PSlice doInt(int start, int stop, int step) {
        return factory.createSlice(start, stop, step);
    }

    @Specialization
    public PSlice doInt(int start, int stop, PNone step) {
        return factory.createSlice(start, stop, castStep(step));
    }

    @Fallback
    public PSlice doGeneric(Object start, Object stop, Object step) {
        return factory.createSlice(castStart(start), castStop(stop), castStep(step));
    }

    private int castStart(Object o) {
        if (castStartNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            castStartNode = insert(CastToSliceComponentNode.create(MISSING_INDEX, MISSING_INDEX));
        }
        return castStartNode.execute(o);
    }

    private int castStop(Object o) {
        if (castStopNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            castStopNode = insert(CastToSliceComponentNode.create(MISSING_INDEX, MISSING_INDEX));
        }
        return castStopNode.execute(o);
    }

    private int castStep(Object o) {
        if (castStepNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            castStepNode = insert(CastToSliceComponentNode.create(1, Integer.MAX_VALUE));
        }
        return castStepNode.execute(o);
    }

    public abstract PNode getFirst();

    public abstract PNode getSecond();

    public abstract PNode getThird();

    public static SliceLiteralNode create(ExpressionNode lower, ExpressionNode upper, ExpressionNode step) {
        return SliceLiteralNodeGen.create(lower, upper, step);
    }

    public static SliceLiteralNode create() {
        return SliceLiteralNodeGen.create(null, null, null);
    }

    abstract static class CastToSliceComponentNode extends PNodeWithContext {

        private final int defaultValue;
        private final int overflowValue;
        private final BranchProfile indexErrorProfile = BranchProfile.create();

        public CastToSliceComponentNode(int defaultValue, int overflowValue) {
            this.defaultValue = defaultValue;
            this.overflowValue = overflowValue;
        }

        public abstract int execute(int i);

        public abstract int execute(long i);

        public abstract int execute(Object i);

        @Specialization
        int doNone(@SuppressWarnings("unused") PNone i) {
            return defaultValue;
        }

        @Specialization
        int doBoolean(boolean i) {
            return PInt.intValue(i);
        }

        @Specialization
        int doInt(int i) {
            return i;
        }

        @Specialization
        int doLong(long i) {
            try {
                return PInt.intValueExact(i);
            } catch (ArithmeticException e) {
                indexErrorProfile.enter();
                return overflowValue;
            }
        }

        @Specialization
        int doPInt(PInt i) {
            try {
                return i.intValueExact();
            } catch (ArithmeticException e) {
                indexErrorProfile.enter();
                return overflowValue;
            }
        }

        public static CastToSliceComponentNode create(int defaultValue, int overflowValue) {
            return CastToSliceComponentNodeGen.create(defaultValue, overflowValue);
        }
    }
}
