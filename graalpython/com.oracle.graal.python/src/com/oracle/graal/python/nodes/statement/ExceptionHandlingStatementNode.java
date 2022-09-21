/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.nodes.statement;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.RecursionError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.SystemError;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.util.ExceptionStateNodes;
import com.oracle.graal.python.nodes.util.ExceptionStateNodes.ExceptionState;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.LoopConditionProfile;

public abstract class ExceptionHandlingStatementNode extends StatementNode {
    @Child private ExceptionStateNodes.SaveExceptionStateNode saveExceptionStateNode;
    @Child private ExceptionStateNodes.RestoreExceptionStateNode restoreExceptionStateNode;
    @Child private ExceptionStateNodes.GetCaughtExceptionNode getCaughtExceptionNode;
    @Child private PythonObjectFactory ofactory;
    @Child private InteropLibrary lib;
    @CompilationFinal private LoopConditionProfile contextChainHandledProfile;
    @CompilationFinal private LoopConditionProfile contextChainContextProfile;

    protected void tryChainExceptionFromHandler(PException handlerException, PException handledException) {
        // Chain the exception handled by the try block to the exception raised by the handler
        if (handledException != handlerException) {
            chainExceptions(handlerException.getUnreifiedException(), handledException, getContextChainHandledProfile(), getContextChainContextProfile());
        }
    }

    protected void tryChainPreexistingException(VirtualFrame frame, PException handledException) {
        // Chain a preexisting (before the try started) exception to the handled exception
        PException preexisting = getExceptionForChaining(frame);
        if (preexisting != null) {
            chainExceptions(handledException.getUnreifiedException(), preexisting, getContextChainHandledProfile(), getContextChainContextProfile());
        }
    }

    protected void tryChainPreexistingException(VirtualFrame frame, PBaseException handledException) {
        PException preexisting = getExceptionForChaining(frame);
        if (preexisting != null) {
            chainExceptions(handledException, preexisting, getContextChainHandledProfile(), getContextChainContextProfile());
        }
    }

    public static void chainExceptions(PBaseException currentException, PException contextException, ConditionProfile p1, ConditionProfile p2) {
        PBaseException context = contextException.getUnreifiedException();
        if (currentException != context) {
            PBaseException e = currentException;
            while (p1.profile(e != null)) {
                if (e.getContext() == context) {
                    // We have already chained this exception in an inner block, do nothing
                    return;
                }
                e = e.getContext();
            }
            e = context;
            while (p2.profile(e != null)) {
                if (e.getContext() == currentException) {
                    e.setContext(null);
                }
                e = e.getContext();
            }
            if (context != null) {
                contextException.markFrameEscaped();
            }
            currentException.setContext(context);
        }
    }

    public static void chainExceptions(PBaseException currentException, PException contextException) {
        chainExceptions(currentException, contextException, ConditionProfile.getUncached(), ConditionProfile.getUncached());
    }

    protected void restoreExceptionState(VirtualFrame frame, ExceptionState e) {
        if (e != null) {
            if (restoreExceptionStateNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                restoreExceptionStateNode = insert(ExceptionStateNodes.RestoreExceptionStateNode.create());
            }
            restoreExceptionStateNode.execute(frame, e);
        }
    }

    protected ExceptionState saveExceptionState(VirtualFrame frame) {
        if (saveExceptionStateNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            saveExceptionStateNode = insert(ExceptionStateNodes.SaveExceptionStateNode.create());
        }
        return saveExceptionStateNode.execute(frame);
    }

    private PException getExceptionForChaining(VirtualFrame frame) {
        if (getCaughtExceptionNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getCaughtExceptionNode = insert(ExceptionStateNodes.GetCaughtExceptionNode.create());
        }
        return getCaughtExceptionNode.execute(frame);
    }

    private ConditionProfile getContextChainHandledProfile() {
        if (contextChainHandledProfile == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            contextChainHandledProfile = LoopConditionProfile.createCountingProfile();
        }
        return contextChainHandledProfile;
    }

    private ConditionProfile getContextChainContextProfile() {
        if (contextChainContextProfile == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            contextChainContextProfile = LoopConditionProfile.createCountingProfile();
        }
        return contextChainContextProfile;
    }

    protected final PythonObjectFactory factory() {
        if (ofactory == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            ofactory = insert(PythonObjectFactory.create());
        }
        return ofactory;
    }

    private InteropLibrary getInteropLibrary() {
        if (lib == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            lib = insert(InteropLibrary.getFactory().createDispatched(3));
        }
        return lib;
    }

    protected final boolean isTruffleException(Throwable t) {
        return getInteropLibrary().isException(t);
    }

    protected final boolean shouldCatchAllExceptions() {
        return PythonLanguage.get(this).getEngineOption(PythonOptions.CatchAllExceptions);
    }

    public static PException wrapJavaException(Throwable e, Node node, PBaseException pythonException) {
        PException pe = PException.fromObject(pythonException, node, e);
        pe.setHideLocation(true);
        // Host exceptions have their stacktrace already filled in, call this to set
        // the cutoff point to the catch site
        pe.getTruffleStackTrace();
        return pe;
    }

    protected PException exceptionStateForTruffleException(AbstractTruffleException exception) {
        return wrapJavaException(exception, this, factory().createBaseException(SystemError, ErrorMessages.M, new Object[]{exception}));
    }

    protected final PException wrapJavaExceptionIfApplicable(Throwable e) {
        if (e instanceof ControlFlowException) {
            return null;
        }
        if (shouldCatchAllExceptions() && (e instanceof Exception || e instanceof AssertionError)) {
            return wrapJavaException(e, this, factory().createBaseException(SystemError, ErrorMessages.M, new Object[]{e}));
        }
        if (e instanceof StackOverflowError) {
            PythonContext.get(this).reacquireGilAfterStackOverflow();
            return wrapJavaException(e, this, factory().createBaseException(RecursionError, ErrorMessages.MAXIMUM_RECURSION_DEPTH_EXCEEDED, new Object[]{}));
        }
        return null;
    }
}
