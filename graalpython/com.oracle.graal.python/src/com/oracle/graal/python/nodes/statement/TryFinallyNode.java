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

import com.oracle.graal.python.builtins.objects.exception.ExceptionInfo;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.traceback.LazyTraceback;
import com.oracle.graal.python.nodes.util.ExceptionStateNodes.ExceptionState;
import com.oracle.graal.python.nodes.util.ExceptionStateNodes.RestoreExceptionStateNode;
import com.oracle.graal.python.nodes.util.ExceptionStateNodes.SaveExceptionStateNode;
import com.oracle.graal.python.nodes.util.ExceptionStateNodes.SetCaughtExceptionNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.profiles.BranchProfile;

public class TryFinallyNode extends StatementNode {
    @Child private StatementNode body;
    @Child private StatementNode finalbody;
    @Child private SaveExceptionStateNode getCaughtExceptionNode;
    @Child private RestoreExceptionStateNode restoreExceptionStateNode;

    private final BranchProfile exceptionProfile = BranchProfile.create();

    public TryFinallyNode(StatementNode body, StatementNode finalbody) {
        this.body = body;
        this.finalbody = finalbody;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        ExceptionState exceptionState = ensureGetCaughtExceptionNode().execute(frame);
        if (finalbody == null) {
            try {
                body.executeVoid(frame);
            } finally {
                // restore
                restoreExceptionState(frame, exceptionState);
            }
        } else {
            boolean executeFinalbody = true;
            PBaseException caughtException = null;
            LazyTraceback caughtTraceback = null;
            try {
                body.executeVoid(frame);
            } catch (PException e) {
                exceptionProfile.enter();
                // any thrown Python exception is visible in the finally block
                caughtException = e.getExceptionObject();
                PFrame.Reference info = PArguments.getCurrentFrameInfo(frame);
                info.markAsEscaped();
                caughtException.reifyException(info);
                caughtTraceback = caughtException.getTraceback();
                SetCaughtExceptionNode.execute(frame, new ExceptionInfo(caughtException, caughtException.getTraceback()));
            } catch (ControlFlowException e) {
                throw e;
            } catch (Throwable e) {
                // Don't execute finally block on exceptions that occured in the interpreter itself
                CompilerDirectives.transferToInterpreter();
                executeFinalbody = false;
                throw e;
            } finally {
                CompilerAsserts.partialEvaluationConstant(executeFinalbody);
                if (executeFinalbody) {
                    try {
                        finalbody.executeVoid(frame);
                    } catch (ControlFlowException e) {
                        // restore
                        restoreExceptionState(frame, exceptionState);
                        throw e;
                    } catch (PException e) {
                        if (caughtException != null && e.getExceptionObject() != null) {
                            e.getExceptionObject().setContext(caughtException);
                        }
                        throw e;
                    }
                    if (caughtException != null) {
                        throw caughtException.getExceptionForReraise(caughtTraceback);
                    }
                }
                // restore
                restoreExceptionState(frame, exceptionState);
            }
        }
    }

    private void restoreExceptionState(VirtualFrame frame, ExceptionState exceptionState) {
        if (exceptionState != null) {
            if (restoreExceptionStateNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                restoreExceptionStateNode = insert(RestoreExceptionStateNode.create());
            }
            restoreExceptionStateNode.execute(frame, exceptionState);
        }
    }

    public StatementNode getBody() {
        return body;
    }

    public StatementNode getFinalbody() {
        return finalbody;
    }

    private SaveExceptionStateNode ensureGetCaughtExceptionNode() {
        if (getCaughtExceptionNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getCaughtExceptionNode = insert(SaveExceptionStateNode.create());
        }
        return getCaughtExceptionNode;
    }
}
