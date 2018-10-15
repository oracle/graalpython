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

import static com.oracle.graal.python.runtime.PythonOptions.CatchAllExceptions;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.ExceptionHandledException;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.ValueProfile;

public class TryExceptNode extends StatementNode implements TruffleObject {
    @Child private StatementNode body;
    @Children private final ExceptNode[] exceptNodes;
    @Child private StatementNode orelse;
    @CompilationFinal private TryExceptNodeMessageResolution.CatchesFunction catchesFunction;
    @CompilationFinal private ValueProfile exceptionStateProfile;

    @CompilationFinal boolean seenException;
    private final boolean shouldCatchAll;
    private final Assumption singleContextAssumption;

    public TryExceptNode(StatementNode body, ExceptNode[] exceptNodes, StatementNode orelse) {
        this.body = body;
        body.markAsTryBlock();
        this.exceptNodes = exceptNodes;
        this.orelse = orelse;
        this.shouldCatchAll = PythonOptions.getOption(getContext(), CatchAllExceptions);
        this.singleContextAssumption = PythonLanguage.getCurrent().singleContextAssumption;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        // store current exception state for later restore
        PException exceptionState = getContext().getCurrentException();
        try {
            body.executeVoid(frame);
        } catch (PException ex) {
            catchException(frame, ex, exceptionState);
            return;
        } catch (Exception e) {
            if (!seenException) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                seenException = true;
            }

            if (shouldCatchAll()) {
                if (e instanceof ControlFlowException) {
                    throw e;
                } else {
                    PException pe = PException.fromObject(getBaseException(e), this);
                    try {
                        catchException(frame, pe, exceptionState);
                    } catch (PException pe_thrown) {
                        if (pe_thrown != pe) {
                            throw e;
                        }
                    }
                }
            } else {
                throw e;
            }
        }
        orelse.executeVoid(frame);
    }

    private boolean shouldCatchAll() {
        if (singleContextAssumption.isValid()) {
            return shouldCatchAll;
        } else {
            return PythonOptions.getOption(getContext(), CatchAllExceptions);
        }
    }

    @TruffleBoundary
    private PBaseException getBaseException(Exception t) {
        return factory().createBaseException(PythonErrorType.ValueError, t.getMessage(), new Object[0]);
    }

    @ExplodeLoop
    private void catchException(VirtualFrame frame, PException exception, PException exceptionState) {
        boolean wasHandled = false;
        for (ExceptNode exceptNode : exceptNodes) {
            // we want a constant loop iteration count for ExplodeLoop to work,
            // so we always run through all except handlers
            if (!wasHandled) {
                if (exceptNode.matchesException(frame, exception)) {
                    try {
                        exceptNode.executeExcept(frame, exception);
                    } catch (ExceptionHandledException e) {
                        wasHandled = true;
                    } catch (ControlFlowException e) {
                        // restore previous exception state, this won't happen if the except block
                        // raises an exception
                        getContext().setCurrentException(exceptionState);
                        throw e;
                    }
                }
            }
        }
        if (!wasHandled) {
            throw exception;
        }
        // restore previous exception state, this won't happen if the except block
        // raises an exception
        getContext().setCurrentException(exceptionState);
    }

    public StatementNode getBody() {
        return body;
    }

    public ExceptNode[] getExceptNodes() {
        return exceptNodes;
    }

    public StatementNode getOrelse() {
        return orelse;
    }

    @Override
    public ForeignAccess getForeignAccess() {
        return TryExceptNodeMessageResolutionForeign.ACCESS;
    }

    public TryExceptNodeMessageResolution.CatchesFunction getCatchesFunction() {
        return this.catchesFunction;
    }

    public void setCatchesFunction(TryExceptNodeMessageResolution.CatchesFunction catchesFunction) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        this.catchesFunction = catchesFunction;
    }
}
