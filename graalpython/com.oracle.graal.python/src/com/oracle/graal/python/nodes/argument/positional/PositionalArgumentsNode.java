/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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

import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.PrimitiveValueProfile;

public final class PositionalArgumentsNode extends Node {

    public static PositionalArgumentsNode create(ExpressionNode[] arguments, ExpressionNode starArgs) {
        assert starArgs != null;
        return new PositionalArgumentsNode(arguments, starArgs, ExecutePositionalStarargsNode.create());
    }

    @Children protected final ExpressionNode[] arguments;
    @Child private ExpressionNode starArgsExpression;
    @Child private ExecutePositionalStarargsNode starArgs;

    private final PrimitiveValueProfile starArgsLengthProfile = PrimitiveValueProfile.createEqualityProfile();

    private PositionalArgumentsNode(ExpressionNode[] arguments, ExpressionNode starArgsExpression, ExecutePositionalStarargsNode starArgs) {
        this.arguments = arguments;
        this.starArgsExpression = starArgsExpression;
        this.starArgs = starArgs;
    }

    @ExplodeLoop
    public Object[] execute(VirtualFrame frame) {
        Object[] starArgsArray = starArgs.executeWith(frame, starArgsExpression.execute(frame));
        int starArgsLength = starArgsLengthProfile.profile(starArgsArray.length);
        Object[] values = new Object[arguments.length + starArgsLength];
        for (int i = 0; i < arguments.length; i++) {
            values[i] = arguments[i].execute(frame);
        }
        System.arraycopy(starArgsArray, 0, values, arguments.length, starArgsLength);
        return values;
    }

    public static Object[] prependArgument(Object primary, Object[] arguments) {
        Object[] result = new Object[arguments.length + 1];
        result[0] = primary;
        System.arraycopy(arguments, 0, result, 1, arguments.length);
        return result;
    }

    @ExplodeLoop
    public static Object[] evaluateArguments(VirtualFrame frame, ExpressionNode[] arguments) {
        CompilerAsserts.compilationConstant(arguments);
        Object[] values = new Object[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            values[i] = arguments[i].execute(frame);
        }
        return values;
    }
}
