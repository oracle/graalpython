/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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

import com.oracle.graal.python.nodes.util.ExceptionStateNodes.ExceptionState;
import com.oracle.graal.python.nodes.util.ExceptionStateNodes.SetCaughtExceptionNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.profiles.BranchProfile;

public class TryFinallyNode extends ExceptionHandlingStatementNode {
    @Child private StatementNode body;
    @Child private StatementNode finalbody;

    private final BranchProfile exceptionProfile = BranchProfile.create();

    public TryFinallyNode(StatementNode body, StatementNode finalbody) {
        this.body = body;
        this.finalbody = finalbody;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        if (finalbody == null) {
            body.executeVoid(frame);
        } else {
            try {
                body.executeVoid(frame);
            } catch (PException handledException) {
                handleException(frame, handledException);
            } catch (ControlFlowException e) {
                finalbody.executeVoid(frame);
                throw e;
            } catch (Throwable e) {
                PException pe = wrapJavaExceptionIfApplicable(e);
                if (pe == null) {
                    throw e;
                }
                handleException(frame, pe);
            }
            finalbody.executeVoid(frame);
        }
    }

    private void handleException(VirtualFrame frame, PException handledException) {
        exceptionProfile.enter();
        // any thrown Python exception is visible in the finally block
        handledException.setCatchingFrameReference(frame);
        tryChainPreexistingException(frame, handledException);
        ExceptionState exceptionState = saveExceptionState(frame);
        SetCaughtExceptionNode.execute(frame, handledException);
        try {
            finalbody.executeVoid(frame);
        } catch (PException handlerException) {
            tryChainExceptionFromHandler(handlerException, handledException);
            throw handlerException;
        } catch (Throwable e) {
            PException handlerException = wrapJavaExceptionIfApplicable(e);
            if (handlerException == null) {
                throw e;
            }
            tryChainExceptionFromHandler(handlerException, handledException);
            throw handlerException.getExceptionForReraise();
        } finally {
            restoreExceptionState(frame, exceptionState);
        }
        throw handledException.getExceptionForReraise();
    }

    public StatementNode getBody() {
        return body;
    }

    public StatementNode getFinalbody() {
        return finalbody;
    }
}
