/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
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
package com.oracle.graal.python.nodes.statement;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.AssertionError;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.expression.CastToBooleanNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;

public class AssertNode extends StatementNode {

    @Child private CastToBooleanNode condition;
    @Child private PNode message;
    @Child private LookupAndCallUnaryNode callNode;

    public AssertNode(CastToBooleanNode condition, PNode message) {
        this.condition = condition;
        this.message = message;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        boolean assertionsEnabled = false;
        assert (assertionsEnabled = true) == true;
        if (assertionsEnabled) {
            try {
                if (!condition.executeBoolean(frame)) {
                    throw assertionFailed(frame);
                }
            } catch (PException e) {
                // Python exceptions just fall through
                throw e;
            } catch (Throwable e) {
                // catch any other exception and convert to Python exception
                throw assertionFailed(frame);
            }
        }

        return PNone.NONE;
    }

    private PException assertionFailed(VirtualFrame frame) {
        String assertionMessage = "";
        if (message != null) {
            try {
                Object messageObj = message.execute(frame);
                if (callNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    callNode = insert(LookupAndCallUnaryNode.create(SpecialMethodNames.__STR__));
                }
                assertionMessage = (String) callNode.executeObject(messageObj);
            } catch (PException e) {
                // again, Python exceptions just fall through
                throw e;
            } catch (Throwable e) {
                assertionMessage = "internal exception occurred";
            }
        }
        return raise(AssertionError, assertionMessage);
    }

    public CastToBooleanNode getCondition() {
        return condition;
    }

    public PNode getMessage() {
        return message;
    }
}