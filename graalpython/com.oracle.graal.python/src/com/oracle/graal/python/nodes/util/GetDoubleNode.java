/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
 * Copyright (c) 2014, Regents of the University of California
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
package com.oracle.graal.python.nodes.util;

import com.oracle.graal.python.builtins.modules.MathGuards;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.nodes.PBaseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.nodes.Node;

@TypeSystemReference(PythonArithmeticTypes.class)
@ImportStatic(MathGuards.class)
public abstract class GetDoubleNode extends PBaseNode {

    @Node.Child private LookupAndCallUnaryNode callFloatNode;

    abstract public double execute(Object x);

    public static GetDoubleNode create() {
        return GetDoubleNodeGen.create();
    }

    @Specialization
    public double toDouble(long x) {
        return x;
    }

    @Specialization
    public double toDouble(PInt x) {
        return x.doubleValue();
    }

    @Specialization
    public double toDouble(double x) {
        return x;
    }

    @Specialization(guards = "!isNumber(x)")
    public double toDouble(Object x) {
        if (callFloatNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            callFloatNode = insert(LookupAndCallUnaryNode.create(SpecialMethodNames.__FLOAT__));
        }
        Object result = callFloatNode.executeObject(x);
        if (result == PNone.NO_VALUE) {
            throw raise(TypeError, "must be real number, not %p", x);
        }
        if (result instanceof PFloat) {
            return ((PFloat) result).getValue();
        }
        if (result instanceof Float || result instanceof Double) {
            return (double) result;
        }
        throw raise(TypeError, "%p.__float__ returned non-float (type %p)", x, result);
    }
}
