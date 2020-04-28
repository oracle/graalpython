/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.nodes.util.ExceptionStateNodes;
import com.oracle.graal.python.nodes.util.ExceptionStateNodes.ExceptionState;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ConditionProfile;

public abstract class ExceptionHandlingStatementNode extends StatementNode {
    @Child private ExceptionStateNodes.SaveExceptionStateNode saveExceptionStateNode;
    @Child private ExceptionStateNodes.RestoreExceptionStateNode restoreExceptionStateNode;
    @Child private ExceptionStateNodes.GetCaughtExceptionNode getCaughtExceptionNode;
    private final ConditionProfile contextChainProfile = ConditionProfile.createCountingProfile();

    protected void tryChainExceptionFromHandler(PException handlerException, TruffleException handledException) {
        // Chain the exception handled by the try block to the exception raised by the handler
        if (handledException != handlerException && handledException instanceof PException) {
            chainExceptions(handlerException.getExceptionObject(), ((PException) handledException).getReifiedException());
        }
    }

    protected void tryChainPreexistingException(VirtualFrame frame, TruffleException handledException) {
        // Chain a preexisting (before the try started) exception to the handled exception
        if (handledException instanceof PException) {
            PException pException = (PException) handledException;
            PException preexisting = getExceptionForChaining(frame);
            if (preexisting != null && pException.getExceptionObject().getContext() == null) {
                chainExceptions(pException.getExceptionObject(), preexisting.getReifiedException());
            }
        }
    }

    protected void tryChainPreexistingException(VirtualFrame frame, PBaseException handledException) {
        PException preexisting = getExceptionForChaining(frame);
        if (preexisting != null && handledException.getContext() == null) {
            chainExceptions(handledException, preexisting.getReifiedException());
        }
    }

    public void chainExceptions(PBaseException currentException, PBaseException context) {
        if (currentException.getContext() == null && currentException != context) {
            PBaseException e = context;
            while (contextChainProfile.profile(e != null)) {
                if (e.getContext() == currentException) {
                    e.setContext(null);
                }
                e = e.getContext();
            }
            if (context != null) {
                context.markAsEscaped();
            }
            currentException.setContext(context);
        }
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
}
