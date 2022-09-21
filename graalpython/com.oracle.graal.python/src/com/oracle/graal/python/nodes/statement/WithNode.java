/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates.
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

import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ENTER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___EXIT__;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.exception.GetExceptionTracebackNode;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.special.CallQuaternaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupSpecialMethodSlotNode;
import com.oracle.graal.python.nodes.expression.CoerceToBooleanNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.frame.WriteNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.ExceptionStateNodes.ExceptionState;
import com.oracle.graal.python.nodes.util.ExceptionStateNodes.SetCaughtExceptionNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.profiles.BranchProfile;

public class WithNode extends ExceptionHandlingStatementNode {
    @Child private StatementNode body;
    @Child private WriteNode targetNode;
    @Child private ExpressionNode withContext;
    @Child private LookupSpecialMethodSlotNode enterSpecialGetter = LookupSpecialMethodSlotNode.create(SpecialMethodSlot.Enter);
    @Child private LookupSpecialMethodSlotNode exitSpecialGetter = LookupSpecialMethodSlotNode.create(SpecialMethodSlot.Exit);
    @Child private CallUnaryMethodNode enterDispatch = CallUnaryMethodNode.create();
    @Child private CallQuaternaryMethodNode exitDispatch = CallQuaternaryMethodNode.create();
    @Child private CoerceToBooleanNode toBooleanNode = CoerceToBooleanNode.createIfTrueNode();
    @Child private GetClassNode getClassNode = GetClassNode.create();
    @Child private PRaiseNode raiseNode;
    @Child private GetExceptionTracebackNode getExceptionTracebackNode;

    private final BranchProfile noEnter = BranchProfile.create();
    private final BranchProfile noExit = BranchProfile.create();

    protected WithNode(WriteNode targetNode, StatementNode body, ExpressionNode withContext) {
        this.targetNode = targetNode;
        this.body = body;
        this.withContext = withContext;
    }

    public static WithNode create(ExpressionNode withContext, WriteNode targetNode, StatementNode body) {
        return new WithNode(targetNode, body, withContext);
    }

    private void applyValues(VirtualFrame frame, Object asNameValue) {
        if (targetNode != null) {
            targetNode.executeObject(frame, asNameValue);
        }
    }

    public StatementNode getBody() {
        return body;
    }

    public WriteNode getTargetNode() {
        return targetNode;
    }

    private PRaiseNode getRaiseNode() {
        if (raiseNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            raiseNode = insert(PRaiseNode.create());
        }
        return raiseNode;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        executeImpl(frame, false);
    }

    @Override
    public Object returnExecute(VirtualFrame frame) {
        return executeImpl(frame, true);
    }

    private Object executeImpl(VirtualFrame frame, boolean isReturn) {
        Object withObject = getWithObject(frame);
        Object clazz = getClassNode.execute(withObject);
        Object enterCallable = enterSpecialGetter.execute(frame, clazz, withObject);
        if (enterCallable == PNone.NO_VALUE) {
            noEnter.enter();
            throw getRaiseNode().raise(PythonBuiltinClassType.AttributeError, ErrorMessages.OBJ_P_HAS_NO_ATTR_S, withObject, T___ENTER__);
        }
        Object exitCallable = exitSpecialGetter.execute(frame, clazz, withObject);
        if (exitCallable == PNone.NO_VALUE) {
            noExit.enter();
            throw getRaiseNode().raise(PythonBuiltinClassType.AttributeError, ErrorMessages.OBJ_P_HAS_NO_ATTR_S, withObject, T___EXIT__);
        }
        doEnter(frame, withObject, enterCallable);
        Object result;
        try {
            result = doBody(frame, isReturn);
        } catch (PException pe) {
            if (handleException(frame, withObject, exitCallable, pe.setCatchingFrameAndGetEscapedException(frame, this), pe, pe)) {
                return PNone.NONE;
            } else {
                throw pe.getExceptionForReraise();
            }
        } catch (ControlFlowException e) {
            doLeave(frame, withObject, exitCallable);
            throw e;
        } catch (AbstractTruffleException e) {
            if (isTruffleException(e)) {
                if (handleException(frame, withObject, exitCallable, e, exceptionStateForTruffleException(e), null)) {
                    return PNone.NONE;
                }
            }
            throw e;
        } catch (Throwable e) {
            PException pe = wrapJavaExceptionIfApplicable(e);
            if (pe == null) {
                throw e;
            }
            if (handleException(frame, withObject, exitCallable, pe.setCatchingFrameAndGetEscapedException(frame, this), pe, pe)) {
                return PNone.NONE;
            } else {
                throw pe.getExceptionForReraise();
            }
        }
        doLeave(frame, withObject, exitCallable);
        return result;
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
    protected Object doBody(VirtualFrame frame, boolean isReturn) {
        return body.genericExecute(frame, isReturn);
    }

    /**
     * Leave the with-body. Call __exit__ if it hasn't already happened because of an exception, and
     * reset the exception state.
     */
    protected void doLeave(VirtualFrame frame, Object withObject, Object exitCallable) {
        exitDispatch.execute(frame, exitCallable, withObject, PNone.NONE, PNone.NONE, PNone.NONE);
    }

    /**
     * Call the __enter__ method
     */
    protected void doEnter(VirtualFrame frame, Object withObject, Object enterCallable) {
        applyValues(frame, enterDispatch.executeObject(frame, enterCallable, withObject));
    }

    /**
     * Call __exit__ to handle the exception
     */
    protected boolean handleException(VirtualFrame frame, Object withObject, Object exitCallable, Object exceptionObject, PException exceptionState, PException chain) {
        if (exceptionObject instanceof PBaseException) {
            tryChainPreexistingException(frame, (PBaseException) exceptionObject);
        }
        ExceptionState savedExceptionState = saveExceptionState(frame);
        SetCaughtExceptionNode.execute(frame, exceptionState);
        Object type = getClassNode.execute(exceptionObject);
        Object tb = getTraceback(exceptionObject);
        // If exit handler returns 'true', suppress
        boolean handled;
        try {
            Object returnValue = exitDispatch.execute(frame, exitCallable, withObject, type, exceptionObject, tb);
            handled = toBooleanNode.executeBoolean(frame, returnValue);
        } catch (PException handlerException) {
            if (chain != null) {
                tryChainExceptionFromHandler(handlerException, chain);
            }
            throw handlerException;
        } catch (Exception | StackOverflowError | AssertionError e) {
            if (chain != null) {
                PException handlerException = wrapJavaExceptionIfApplicable(e);
                if (handlerException == null) {
                    throw e;
                }
                tryChainExceptionFromHandler(handlerException, chain);
                throw handlerException.getExceptionForReraise();
            }
            throw e;
        } finally {
            restoreExceptionState(frame, savedExceptionState);
        }
        return handled;
    }

    private Object getTraceback(Object e) {
        if (getExceptionTracebackNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getExceptionTracebackNode = insert(GetExceptionTracebackNode.create());
        }
        return getExceptionTracebackNode.execute(e);
    }
}
