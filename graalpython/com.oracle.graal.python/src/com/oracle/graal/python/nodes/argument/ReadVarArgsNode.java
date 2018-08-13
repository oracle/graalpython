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
package com.oracle.graal.python.nodes.argument;

import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;

public abstract class ReadVarArgsNode extends ReadIndexedArgumentNode {
    /**
     * Controls if the varargs are wrapped in a tuple
     */
    private final boolean builtin;

    ReadVarArgsNode(int paramIndex, boolean isBuiltin) {
        super(paramIndex);
        builtin = isBuiltin;
    }

    public static ReadVarArgsNode create(int paramIndex) {
        return create(paramIndex, false);
    }

    public static ReadVarArgsNode create(int paramIndex, boolean isBuiltin) {
        return ReadVarArgsNodeGen.create(paramIndex, isBuiltin);
    }

    public abstract Object[] executeObjectArray(VirtualFrame frame);

    protected int getAndCheckUserArgsLen(VirtualFrame frame) {
        int length = getUserArgsLen(frame);
        if (length >= PythonOptions.getIntOption(getContext(), PythonOptions.VariableArgumentReadUnrollingLimit)) {
            return -1;
        }
        return length;
    }

    protected static int getUserArgsLen(VirtualFrame frame) {
        return PArguments.getUserArgumentLength(frame);
    }

    @Specialization(guards = {"getUserArgsLen(frame) == userArgumentLength"})
    @ExplodeLoop
    Object extractVarargs(VirtualFrame frame,
                    @Cached("getAndCheckUserArgsLen(frame)") int userArgumentLength) {
        if (index >= userArgumentLength) {
            return output();
        } else {
            Object[] varArgs = new Object[userArgumentLength - index];
            CompilerAsserts.compilationConstant(varArgs.length);
            for (int i = 0; i < varArgs.length; i++) {
                varArgs[i] = PArguments.getArgument(frame, i + index);
            }
            return output(varArgs);
        }
    }

    @Specialization(replaces = "extractVarargs")
    Object extractVariableVarargs(VirtualFrame frame) {
        int userArgumentLength = getUserArgsLen(frame);
        if (index >= userArgumentLength) {
            return output();
        } else {
            Object[] varArgs = new Object[userArgumentLength - index];
            for (int i = 0; i < varArgs.length; i++) {
                varArgs[i] = PArguments.getArgument(frame, i + index);
            }
            return output(varArgs);
        }
    }

    private Object output() {
        if (builtin) {
            return new Object[0];
        } else {
            return factory().createEmptyTuple();
        }
    }

    private Object output(Object[] varArgs) {
        if (builtin) {
            return varArgs;
        } else {
            return factory().createTuple(varArgs);
        }
    }

    public boolean isBuiltin() {
        return builtin;
    }
}
