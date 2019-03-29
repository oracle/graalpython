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
package com.oracle.graal.python.nodes.statement;

import com.oracle.graal.python.nodes.util.ExceptionStateNodes.GetCaughtExceptionNode;
import com.oracle.graal.python.nodes.util.ExceptionStateNodes.SetCaughtExceptionNode;
import com.oracle.graal.python.nodes.util.ExceptionStateNodesFactory.GetCaughtExceptionNodeGen;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ControlFlowException;

public class TryFinallyNode extends StatementNode {
    @Child private StatementNode body;
    @Child private StatementNode finalbody;
    @Child private GetCaughtExceptionNode getCaughtExceptionNode;
    @Child private SetCaughtExceptionNode setCaughtExceptionNode;

    public TryFinallyNode(StatementNode body, StatementNode finalbody) {
        this.body = body;
        this.finalbody = finalbody;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        PException exceptionState = ensureGetCaughtExceptionNode().execute(frame);
        if (finalbody == null) {
            try {
                body.executeVoid(frame);
            } finally {
                // restore
                restoreExceptionState(frame, exceptionState);
            }
        } else {
            try {
                body.executeVoid(frame);
            } catch (PException e) {
                // any thrown Python exception is visible in the finally block
                setCaughtExceptionNode.execute(frame, e);
                throw e;
            } finally {
                try {
                    finalbody.executeVoid(frame);
                } catch (ControlFlowException e) {
                    // restore
                    restoreExceptionState(frame, exceptionState);
                    throw e;
                }
                // restore
                restoreExceptionState(frame, exceptionState);
            }
        }
    }

    private void restoreExceptionState(VirtualFrame frame, PException exceptionState) {
        if (setCaughtExceptionNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            setCaughtExceptionNode = insert(SetCaughtExceptionNode.create());
        }
        setCaughtExceptionNode.execute(frame, exceptionState);
    }

    public StatementNode getBody() {
        return body;
    }

    public StatementNode getFinalbody() {
        return finalbody;
    }

    private GetCaughtExceptionNode ensureGetCaughtExceptionNode() {
        if (getCaughtExceptionNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getCaughtExceptionNode = insert(GetCaughtExceptionNodeGen.create());
        }
        return getCaughtExceptionNode;
    }
}
