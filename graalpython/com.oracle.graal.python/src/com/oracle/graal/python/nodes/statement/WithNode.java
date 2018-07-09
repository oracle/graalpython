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

import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETATTRIBUTE__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.argument.CreateArgumentsNode;
import com.oracle.graal.python.nodes.call.CallDispatchNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.datamodel.IsCallableNode;
import com.oracle.graal.python.nodes.expression.CastToBooleanNode;
import com.oracle.graal.python.nodes.frame.WriteNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

@NodeChildren({@NodeChild(value = "withContext", type = PNode.class)})
public abstract class WithNode extends StatementNode {

    @Child private PNode body;
    @Child private PNode targetNode;
    @Child private LookupAndCallBinaryNode enterGetter = LookupAndCallBinaryNode.create(__GETATTRIBUTE__);
    @Child private LookupAndCallBinaryNode exitGetter = LookupAndCallBinaryNode.create(__GETATTRIBUTE__);
    @Child private CallDispatchNode enterDispatch = CallDispatchNode.create();
    @Child private CallDispatchNode exitDispatch = CallDispatchNode.create();
    @Child private CastToBooleanNode toBooleanNode = CastToBooleanNode.createIfTrueNode();
    @Child private CreateArgumentsNode createArgs = CreateArgumentsNode.create();

    protected WithNode(PNode targetNode, PNode body) {
        this.targetNode = targetNode;
        this.body = body;
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
    protected Object runWith(VirtualFrame frame, Object withObject,
                    @Cached("create()") IsCallableNode isCallableNode,
                    @Cached("create()") IsCallableNode isExitCallableNode) {

        boolean gotException = false;
        // CPython first looks up '__exit__
        Object exitCallable = exitGetter.executeObject(withObject, "__exit__");
        Object enterCallable = enterGetter.executeObject(withObject, "__enter__");

        if (isCallableNode.execute(enterCallable)) {
            applyValues(frame, enterDispatch.executeCall(enterCallable, createArgs.execute(withObject), new PKeyword[0]));
        } else {
            throw raise(TypeError, "%p is not callable", enterCallable);
        }

        try {
            body.execute(frame);
        } catch (PException exception) {
            gotException = true;
            return handleException(withObject, exitCallable, exception, isExitCallableNode);
        } finally {
            if (!gotException) {
                if (isExitCallableNode.execute(exitCallable)) {
                    exitDispatch.executeCall(exitCallable, createArgs.execute(withObject, PNone.NONE, PNone.NONE, PNone.NONE), new PKeyword[0]);
                } else {
                    throw raise(TypeError, "%p is not callable", exitCallable);
                }
            }
        }
        return PNone.NONE;
    }

    private Object handleException(Object withObject, Object exitCallable, PException e, IsCallableNode isExitCallableNode) {
        if (!isExitCallableNode.execute(exitCallable)) {
            throw raise(TypeError, "%p is not callable", exitCallable);
        }

        e.getExceptionObject().reifyException();
        Object type = e.getType();
        Object value = e.getExceptionObject();
        Object trace = e.getExceptionObject().getTraceback(factory());
        Object returnValue = exitDispatch.executeCall(exitCallable, createArgs.execute(withObject, type, value, trace), new PKeyword[0]);
        // If exit handler returns 'true', suppress
        if (toBooleanNode.executeWith(returnValue)) {
            return PNone.NONE;
        }
        // else re-raise exception
        throw e;
    }
}
