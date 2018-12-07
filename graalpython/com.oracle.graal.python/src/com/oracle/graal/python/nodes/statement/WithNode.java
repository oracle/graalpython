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

import static com.oracle.graal.python.nodes.SpecialMethodNames.__ENTER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__EXIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETATTRIBUTE__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.nodes.argument.CreateArgumentsNode;
import com.oracle.graal.python.nodes.call.CallDispatchNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.datamodel.IsCallableNode;
import com.oracle.graal.python.nodes.expression.CastToBooleanNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.frame.WriteNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.frame.VirtualFrame;

public class WithNode extends StatementNode {
    @Child private StatementNode body;
    @Child private WriteNode targetNode;
    @Child private ExpressionNode withContext;
    @Child private LookupAndCallBinaryNode enterGetter = LookupAndCallBinaryNode.create(__GETATTRIBUTE__);
    @Child private LookupAndCallBinaryNode exitGetter = LookupAndCallBinaryNode.create(__GETATTRIBUTE__);
    @Child private CallDispatchNode enterDispatch = CallDispatchNode.create();
    @Child private CallDispatchNode exitDispatch = CallDispatchNode.create();
    @Child private CastToBooleanNode toBooleanNode = CastToBooleanNode.createIfTrueNode();
    @Child private CreateArgumentsNode createArgs = CreateArgumentsNode.create();

    GetClassNode getClassNode = GetClassNode.create();
    IsCallableNode isCallableNode = IsCallableNode.create();
    IsCallableNode isExitCallableNode = IsCallableNode.create();

    protected WithNode(WriteNode targetNode, StatementNode body, ExpressionNode withContext) {
        this.targetNode = targetNode;
        this.body = body;
        this.withContext = withContext;
    }

    public static WithNode create(ExpressionNode withContext, WriteNode targetNode, StatementNode body) {
        return new WithNode(targetNode, body, withContext);
    }

    private void applyValues(VirtualFrame frame, Object asNameValue) {
        if (targetNode == null) {
            return;
        } else {
            targetNode.doWrite(frame, asNameValue);
            return;
        }
    }

    public StatementNode getBody() {
        return body;
    }

    public WriteNode getTargetNode() {
        return targetNode;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        boolean gotException = false;
        Object withObject = getWithObject(frame);
        // CPython first looks up '__exit__
        Object exitCallable = exitGetter.executeObject(withObject, __EXIT__);
        Object enterCallable = enterGetter.executeObject(withObject, __ENTER__);
        PException exceptionState = doEnter(frame, withObject, enterCallable);
        try {
            doBody(frame);
        } catch (PException exception) {
            gotException = true;
            handleException(frame, withObject, exitCallable, exception);
        } finally {
            doLeave(frame, withObject, exceptionState, gotException, exitCallable);
        }
    }

    /**
     * Execute the nodes to get the with object.
     */
    protected Object getWithObject(VirtualFrame frame) {
        return withContext.execute(frame);
    }

    /**
     * Execute the body
     */
    protected void doBody(VirtualFrame frame) {
        body.executeVoid(frame);
    }

    /**
     * Leave the with-body. Call __exit__ if it hasn't already happened because of an exception, and
     * reset the exception state.
     */
    protected void doLeave(VirtualFrame frame, Object withObject, PException exceptionState, boolean gotException, Object exitCallable) {
        if (!gotException) {
            if (isExitCallableNode.execute(exitCallable)) {
                exitDispatch.executeCall(frame, exitCallable, createArgs.execute(withObject, PNone.NONE, PNone.NONE, PNone.NONE), new PKeyword[0]);
            } else {
                throw raise(TypeError, "%p is not callable", exitCallable);
            }
        }
        getContext().setCurrentException(exceptionState);
    }

    /**
     * Call the __enter__ method and return the exception state as it was before starting the with
     * statement
     */
    protected PException doEnter(VirtualFrame frame, Object withObject, Object enterCallable) {
        PException currentException = getContext().getCurrentException();
        if (isCallableNode.execute(enterCallable)) {
            applyValues(frame, enterDispatch.executeCall(frame, enterCallable, createArgs.execute(withObject), new PKeyword[0]));
        } else {
            throw raise(TypeError, "%p is not callable", enterCallable);
        }
        return currentException;
    }

    /**
     * Call __exit__ to handle the exception
     */
    protected void handleException(VirtualFrame frame, Object withObject, Object exitCallable, PException e) {
        if (!isExitCallableNode.execute(exitCallable)) {
            throw raise(TypeError, "%p is not callable", exitCallable);
        }

        e.getExceptionObject().reifyException();
        PBaseException value = e.getExceptionObject();
        PythonClass type = getClassNode.execute(value);
        Object trace = e.getExceptionObject().getTraceback(factory());
        Object returnValue = exitDispatch.executeCall(frame, exitCallable, createArgs.execute(withObject, type, value, trace), new PKeyword[0]);
        // If exit handler returns 'true', suppress
        if (toBooleanNode.executeWith(returnValue)) {
            return;
        } else {
            // else re-raise exception
            throw e;
        }
    }

    public ExpressionNode getWithContext() {
        return withContext;
    }
}
