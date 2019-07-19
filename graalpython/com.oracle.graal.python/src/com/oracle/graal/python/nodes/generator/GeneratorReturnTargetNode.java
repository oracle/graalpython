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
package com.oracle.graal.python.nodes.generator;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.StopIteration;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.graal.python.runtime.exception.ReturnException;
import com.oracle.graal.python.runtime.exception.YieldException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;

public final class GeneratorReturnTargetNode extends ExpressionNode implements GeneratorControlNode {

    @Child private StatementNode body;
    @Child private ExpressionNode returnValue;
    @Child private StatementNode parameters;
    @Child private PythonObjectFactory factory;
    @Child private GeneratorAccessNode gen = GeneratorAccessNode.create();
    @Child private PRaiseNode raise = PRaiseNode.create();

    private final BranchProfile returnProfile = BranchProfile.create();
    private final BranchProfile fallthroughProfile = BranchProfile.create();
    private final BranchProfile yieldProfile = BranchProfile.create();
    private final BranchProfile throwOnReturnProfile = BranchProfile.create();

    private final int flagSlot;

    public GeneratorReturnTargetNode(StatementNode parameters, StatementNode body, ExpressionNode returnValue, int activeFlagIndex) {
        this.body = body;
        this.returnValue = returnValue;
        this.parameters = parameters;
        this.flagSlot = activeFlagIndex;
    }

    public StatementNode getParameters() {
        return parameters;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        if (!gen.isActive(frame, flagSlot)) {
            parameters.executeVoid(frame);
            gen.setActive(frame, flagSlot, true);
        }

        try {
            body.executeVoid(frame);
            fallthroughProfile.enter();
            throw raise.raise(StopIteration);
        } catch (YieldException eye) {
            yieldProfile.enter();
            return returnValue.execute(frame);
        } catch (ReturnException ire) {
            // return statement in generators throws StopIteration with the return value
            returnProfile.enter();
            if (gen.shouldThrowOnReturn(frame.getArguments())) {
                throwOnReturnProfile.enter();
                Object retVal = returnValue.execute(frame);
                if (retVal != PNone.NONE) {
                    if (factory == null) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        factory = insert(PythonObjectFactory.create());
                    }
                    throw raise.raise(factory.createBaseException(StopIteration, factory.createTuple(new Object[]{retVal})));
                } else {
                    throw raise.raise(StopIteration);
                }
            } else {
                return null;
            }
        }
    }
}
