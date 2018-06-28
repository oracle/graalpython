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
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.nodes.PBaseNode;
import com.oracle.graal.python.nodes.PGuards;
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
public abstract class GetIntNode extends PBaseNode {

    @Node.Child private LookupAndCallUnaryNode callIndexNode;

    public abstract Object execute(Object x);

    public static GetIntNode create() {
        return GetIntNodeGen.create();
    }

    @Specialization
    public long toInt(long x) {
        return x;
    }

    @Specialization
    public PInt toInt(PInt x) {
        return x;
    }

    @Specialization
    public long toInt(double x) {
        throw raise(TypeError, "'float' object cannot be interpreted as an integer");
    }

    @Specialization(guards = "!isNumber(x)")
    public Object toInt(Object x) {
        if (callIndexNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            callIndexNode = insert(LookupAndCallUnaryNode.create(SpecialMethodNames.__INDEX__));
        }
        Object result = callIndexNode.executeObject(x);
        if (result == PNone.NONE) {
            throw raise(TypeError, "'%p' object cannot be interpreted as an integer", x);
        }
        if (!PGuards.isInteger(result) && !PGuards.isPInt(result) && !(result instanceof Boolean)) {
            throw raise(TypeError, " __index__ returned non-int (type %p)", result);
        }
        return result;
    }
}
