/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
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
package com.oracle.graal.python.nodes.argument.positional;

import com.oracle.graal.python.nodes.EmptyNode;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;

@NodeChildren({@NodeChild(value = "primary"), @NodeChild(value = "splat", type = ExecutePositionalStarargsNode.class)})
public abstract class PositionalArgumentsNode extends PNode {
    @Children private final PNode[] arguments;

    public static PositionalArgumentsNode create(PNode[] arguments, PNode starargs) {
        return PositionalArgumentsNodeGen.create(arguments, EmptyNode.create(), ExecutePositionalStarargsNodeGen.create(starargs == null ? EmptyNode.create() : starargs));
    }

    public static PositionalArgumentsNode create() {
        return PositionalArgumentsNodeGen.create(new PNode[0], EmptyNode.create(), ExecutePositionalStarargsNodeGen.create(EmptyNode.create()));
    }

    PositionalArgumentsNode(PNode[] arguments) {
        this.arguments = arguments;
    }

    public int getArgumentLength() {
        return arguments.length;
    }

    @Override
    public final Object[] execute(VirtualFrame frame) {
        return execute(frame, null);
    }

    public abstract Object[] execute(VirtualFrame frame, Object primary);

    protected abstract Object[] executeWithArguments(VirtualFrame frame, Object primary, Object[] starargs);

    public final Object[] executeWithArguments(Object primary, Object[] starargs) {
        assert arguments.length == 0;
        return executeWithArguments(null, primary, starargs);
    }

    @Specialization(guards = {"starargs.length == starLen", "(primary == null) == primaryWasNull"}, limit = "getVariableArgumentInlineCacheLimit()")
    @ExplodeLoop
    Object[] argumentsCached(VirtualFrame frame, Object primary, Object[] starargs,
                    @Cached("starargs.length") int starLen,
                    @Cached("primary == null") boolean primaryWasNull) {
        final int argLen = arguments.length;
        CompilerAsserts.partialEvaluationConstant(primaryWasNull);
        int offset = 0;
        if (!primaryWasNull) {
            offset = 1;
        }
        CompilerAsserts.partialEvaluationConstant(offset);
        final int length = argLen + starLen + offset;
        CompilerAsserts.partialEvaluationConstant(length);
        final Object[] values = new Object[length];
        if (!primaryWasNull) {
            values[0] = primary;
        }
        for (int i = 0; i < argLen; i++) {
            values[offset + i] = arguments[i].execute(frame);
        }
        for (int i = 0; i < starLen; i++) {
            values[offset + argLen + i] = starargs[i];
        }
        return values;
    }

    @Specialization(replaces = "argumentsCached")
    Object[] arguments(VirtualFrame frame, Object primary, Object[] starargs) {
        final int argLen = arguments.length;
        final int starLen = starargs.length;
        int offset = primary == null ? 0 : 1;
        final int length = argLen + starLen + offset;
        final Object[] values = new Object[length];
        if (primary != null) {
            values[0] = primary;
        }
        for (int i = 0; i < argLen; i++) {
            values[offset + i] = arguments[i].execute(frame);
        }
        for (int i = 0; i < starLen; i++) {
            values[offset + argLen + i] = starargs[i];
        }
        return values;
    }
}
