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
package com.oracle.graal.python.nodes.argument;

import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class ReadVarArgsNode extends ReadArgumentNode {
    private final int index;
    @Child PythonObjectFactory factory = null;

    ReadVarArgsNode(int paramIndex, boolean isBuiltin) {
        index = paramIndex;
        if (!isBuiltin) {
            factory = PythonObjectFactory.create();
        }
    }

    public static ReadVarArgsNode create(int paramIndex) {
        return create(paramIndex, false);
    }

    public static ReadVarArgsNode create(int paramIndex, boolean isBuiltin) {
        return ReadVarArgsNodeGen.create(paramIndex, isBuiltin);
    }

    public abstract Object[] executeObjectArray(VirtualFrame frame);

    @Specialization
    Object extractVariableVarargs(VirtualFrame frame) {
        return output(PArguments.getVariableArguments(frame));
    }

    private Object output(Object[] varArgs) {
        if (isBuiltin()) {
            return varArgs;
        } else {
            return factory.createTuple(varArgs);
        }
    }

    public boolean isBuiltin() {
        return factory == null;
    }

    public int getIndex() {
        return index;
    }
}
