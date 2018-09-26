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
package com.oracle.graal.python.nodes.statement;

import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.frame.WriteNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.ExceptionHandledException;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;

@GenerateWrapper
public class ExceptNode extends PNodeWithContext implements InstrumentableNode {

    @Child private StatementNode body;
    @Child private ExpressionNode exceptType;
    @Child private WriteNode exceptName;

    public ExceptNode(StatementNode body, ExpressionNode exceptType, WriteNode exceptName) {
        this.body = body;
        this.exceptName = exceptName;
        this.exceptType = exceptType;
    }

    public ExceptNode(ExceptNode original) {
        this.body = original.body;
        this.exceptName = original.exceptName;
        this.exceptType = original.exceptType;
    }

    public void executeExcept(VirtualFrame frame, PException e) {
        PythonContext context = getContext();
        context.setCurrentException(e);
        body.executeVoid(frame);
        throw ExceptionHandledException.INSTANCE;
    }

    public boolean matchesException(VirtualFrame frame, PException e) {
        if (exceptType != null) {
            PythonObject type = null;
            Object execute = exceptType.execute(frame);
            PythonClass exceptionType = e.getType();
            if (exceptionType == execute) {
                type = e.getType();
            } else if (execute instanceof PTuple) {
                for (Object etype : ((PTuple) execute).getArray()) {
                    if (exceptionType == etype) {
                        type = (PythonObject) etype;
                    }
                }
            } else {
                PythonClass[] mro = exceptionType.getMethodResolutionOrder();
                for (PythonClass current : mro) {
                    if (execute == current) {
                        type = current;
                        break;
                    }
                }
            }

            if (type != null) {
                if (exceptName != null) {
                    exceptName.doWrite(frame, e.getExceptionObject());
                    e.getExceptionObject().reifyException();
                }
            } else {
                return false;
            }
        }
        return true;
    }

    public StatementNode getBody() {
        return body;
    }

    public ExpressionNode getExceptType() {
        return exceptType;
    }

    public WriteNode getExceptName() {
        return exceptName;
    }

    @Override
    public WrapperNode createWrapper(ProbeNode probeNode) {
        return new ExceptNodeWrapper(this, this, probeNode);
    }

    public boolean isInstrumentable() {
        return getSourceSection() != null;
    }
}
