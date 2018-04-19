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
package com.oracle.graal.python.nodes.statement;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ZeroDivisionError;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.function.PythonCallable;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.argument.CreateArgumentsNode;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode;
import com.oracle.graal.python.nodes.call.CallDispatchNode;
import com.oracle.graal.python.nodes.expression.CastToBooleanNode;
import com.oracle.graal.python.nodes.frame.WriteNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

@NodeChildren({@NodeChild(value = "withContext", type = PNode.class)})
public abstract class WithNode extends StatementNode {

    @Child private PNode body;
    @Child private PNode targetNode;
    @Child private GetAttributeNode enterGetter;
    @Child private GetAttributeNode exitGetter;
    @Child private CallDispatchNode enterDispatch;
    @Child private CallDispatchNode exitDispatch;
    @Child private CastToBooleanNode exitResultIsTrueish;
    @Child private CreateArgumentsNode createArgs;

    protected WithNode(PNode targetNode, PNode body) {
        this.targetNode = targetNode;
        this.body = body;
        this.enterGetter = GetAttributeNode.create();
        this.exitGetter = GetAttributeNode.create();
        this.enterDispatch = CallDispatchNode.create("__enter__");
        this.exitDispatch = CallDispatchNode.create("__enter__");
        this.exitResultIsTrueish = CastToBooleanNode.createIfTrueNode();
        this.createArgs = CreateArgumentsNode.create();
    }

    public static WithNode create(PNode withContext, PNode targetNode, PNode body) {
        return WithNodeGen.create(targetNode, body, withContext);
    }

    private void applyValues(VirtualFrame frame, Object asNameValue) {
        if (targetNode == null) {
            return;
        } else {
            ((WriteNode) targetNode).doWrite(frame, asNameValue);
            return;
        }
    }

    public abstract PNode getWithContext();

    public PNode getBody() {
        return body;
    }

    public PNode getTargetNode() {
        return targetNode;
    }

    @Specialization
    protected Object runWith(VirtualFrame frame, PythonObject withObject) {
        boolean gotException = false;
        Object enterCallable = enterGetter.execute(withObject, "__enter__");
        Object exitCallable = exitGetter.execute(withObject, "__exit__");
        try {
            applyValues(frame, enterDispatch.executeCall(PythonCallable.expect(enterCallable), createArgs.execute(withObject), new PKeyword[0]));
        } catch (UnexpectedResultException e1) {
            CompilerDirectives.transferToInterpreter();
            throw raise(TypeError, "%s is not callable", e1.getResult());
        }
        try {
            body.execute(frame);
        } catch (RuntimeException exception) {
            CompilerDirectives.transferToInterpreter();
            gotException = true;
            return handleException(withObject, exception);
        } finally {
            if (!gotException) {
                try {
                    return exitDispatch.executeCall(PythonCallable.expect(exitCallable), createArgs.execute(withObject, PNone.NONE, PNone.NONE, PNone.NONE),
                                    new PKeyword[0]);
                } catch (UnexpectedResultException e1) {
                    CompilerDirectives.transferToInterpreter();
                    throw raise(TypeError, "%s is not callable", e1.getResult());
                }
            }
        }
        assert false;
        return null;
    }

    private Object handleException(PythonObject withObject, RuntimeException e) {
        RuntimeException exception = e;
        PythonCallable exitCallable = null;
        try {
            exitCallable = PythonCallable.expect(exitGetter.execute(withObject, "__exit__"));
        } catch (UnexpectedResultException e1) {
            CompilerDirectives.transferToInterpreter();
            throw raise(TypeError, "%s is not callable", e1.getResult());
        }
        if (exception instanceof ArithmeticException && exception.getMessage().endsWith("divide by zero")) {
            // TODO: no ArithmeticExceptions should propagate outside of operations
            exception = raise(ZeroDivisionError, "division by zero");
        }
        if (exception instanceof PException) {
            PException pException = (PException) exception;
            pException.getExceptionObject().reifyException();
            Object type = pException.getType();
            Object value = pException.getExceptionObject();
            Object trace = pException.getExceptionObject().getTraceback(factory());
            Object returnValue = exitDispatch.executeCall(exitCallable, createArgs.execute(withObject, type, value, trace), new PKeyword[0]);
            // Corner cases:
            if (exitResultIsTrueish.executeWith(returnValue)) {
                return returnValue;
            }
        }
        throw exception;
    }
}
