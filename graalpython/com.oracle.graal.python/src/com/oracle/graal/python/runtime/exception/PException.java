/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.runtime.exception;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

public final class PException extends RuntimeException implements TruffleException {
    private static final long serialVersionUID = -6437116280384996361L;

    /** A marker object indicating that there is for sure no exception. */
    public static final PException NO_EXCEPTION = new PException(null, null);

    private Node location;
    private String message = null;
    private boolean isIncompleteSource;
    private boolean exit;
    private final PBaseException pythonException;
    private boolean hideLocation = false;
    private CallTarget tracebackCutoffTarget;

    public PException(PBaseException actual, Node node) {
        this.pythonException = actual;
        this.location = node;
    }

    public static PException fromObject(PBaseException actual, Node node) {
        PException pException = new PException(actual, node);
        actual.setException(pException);
        return pException;
    }

    @Override
    public String getMessage() {
        if (message == null) {
            message = pythonException.toString();
        }
        return message;
    }

    public void setMessage(Object object) {
        message = object.toString();
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        if (this == PException.NO_EXCEPTION) {
            return "NO_EXCEPTION";
        }
        return getMessage();
    }

    @SuppressWarnings("sync-override")
    @Override
    public Throwable fillInStackTrace() {
        return null;
    }

    @Override
    public Node getLocation() {
        return location;
    }

    public boolean shouldHideLocation() {
        return hideLocation;
    }

    public void setHideLocation(boolean hideLocation) {
        this.hideLocation = hideLocation;
    }

    @Override
    public PBaseException getExceptionObject() {
        return pythonException;
    }

    public PBaseException reifyAndGetPythonException(VirtualFrame frame) {
        return reifyAndGetPythonException(frame, true);
    }

    public PBaseException reifyAndGetPythonException(VirtualFrame frame, boolean markEscaped) {
        return reifyAndGetPythonException(frame, markEscaped, true);
    }

    public PBaseException reifyAndGetPythonException(VirtualFrame frame, boolean markEscaped, boolean addCurrentFrameToTraceback) {
        PFrame.Reference info = PArguments.getCurrentFrameInfo(frame);
        return reifyAndGetPythonException(info, markEscaped, addCurrentFrameToTraceback);
    }

    public PBaseException reifyAndGetPythonException(PFrame.Reference info, boolean markEscaped, boolean addCurrentFrameToTraceback) {
        if (markEscaped) {
            info.markAsEscaped();
        }
        if (addCurrentFrameToTraceback) {
            pythonException.reifyException(info);
        } else {
            pythonException.reifyException((PFrame) null);
        }
        return pythonException;
    }

    @Override
    public boolean isInternalError() {
        return false;
    }

    @Override
    public int getStackTraceElementLimit() {
        return -1;
    }

    @Override
    public boolean isSyntaxError() {
        return pythonException != null && IsBuiltinClassProfile.profileClassSlowPath(pythonException.getLazyPythonClass(), PythonBuiltinClassType.SyntaxError);
    }

    public void setIncompleteSource(boolean val) {
        isIncompleteSource = val;
    }

    @Override
    public boolean isIncompleteSource() {
        return isSyntaxError() && isIncompleteSource;
    }

    public void setExit(boolean val) {
        exit = val;
    }

    @Override
    public boolean isExit() {
        return exit;
    }

    public void expectIndexError(IsBuiltinClassProfile profile) {
        if (!profile.profileException(this, PythonBuiltinClassType.IndexError)) {
            throw this;
        }
    }

    public void expectStopIteration(IsBuiltinClassProfile profile) {
        if (!profile.profileException(this, PythonBuiltinClassType.StopIteration)) {
            throw this;
        }
    }

    public void expectAttributeError(IsBuiltinClassProfile profile) {
        if (!profile.profileException(this, PythonBuiltinClassType.AttributeError)) {
            throw this;
        }
    }

    public void expect(PythonBuiltinClassType error, IsBuiltinClassProfile profile) {
        if (!profile.profileException(this, error)) {
            throw this;
        }
    }

    @TruffleBoundary
    public Iterable<TruffleStackTraceElement> getTruffleStackTrace() {
        if (tracebackCutoffTarget == null) {
            tracebackCutoffTarget = Truffle.getRuntime().getCurrentFrame().getCallTarget();
        }
        return TruffleStackTrace.getStackTrace(this);
    }

    public boolean shouldCutOffTraceback(TruffleStackTraceElement element) {
        return tracebackCutoffTarget != null && element.getTarget() == tracebackCutoffTarget;
    }
}
