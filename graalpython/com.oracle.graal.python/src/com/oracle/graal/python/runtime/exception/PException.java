/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.traceback.LazyTraceback;
import com.oracle.graal.python.builtins.objects.traceback.PTraceback;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

/**
 * Serves both as a throwable carrier of the python exception object and as a represenation of the
 * exception state at a single point in the program. An important invariant is that it must never be
 * rethrown after the contained exception object has been exposed to the program, instead, a new
 * object must be created for each throw.
 */
public final class PException extends RuntimeException implements TruffleException {
    private static final long serialVersionUID = -6437116280384996361L;

    /** A marker object indicating that there is for sure no exception. */
    public static final PException NO_EXCEPTION = new PException(null, (Node) null);

    private Node location;
    private String message = null;
    private boolean isIncompleteSource;
    private boolean exit;
    private final PBaseException pythonException;
    private boolean hideLocation = false;
    private CallTarget tracebackCutoffTarget;
    private PFrame.Reference frameInfo;
    private LazyTraceback traceback;
    private boolean reified = false;

    public PException(PBaseException actual, Node node) {
        this.pythonException = actual;
        this.location = node;
    }

    public PException(PBaseException actual, LazyTraceback traceback) {
        this.pythonException = actual;
        this.traceback = traceback;
        reified = true;
    }

    public static PException fromObject(PBaseException actual, Node node, boolean withJavaStacktrace) {
        PException pException = new PException(actual, node);
        actual.setException(pException);
        if (withJavaStacktrace) {
            pException = (PException) pException.forceFillInStackTrace();
        }
        return pException;
    }

    public static PException fromExceptionInfo(PBaseException pythonException, PTraceback traceback, boolean withJavaStacktrace) {
        LazyTraceback lazyTraceback = null;
        if (traceback != null) {
            lazyTraceback = new LazyTraceback(traceback);
        }
        return fromExceptionInfo(pythonException, lazyTraceback, withJavaStacktrace);
    }

    public static PException fromExceptionInfo(PBaseException pythonException, LazyTraceback traceback, boolean withJavaStacktrace) {
        pythonException.ensureReified();
        PException pException = new PException(pythonException, traceback);
        pythonException.setException(pException);
        if (withJavaStacktrace) {
            pException = (PException) pException.forceFillInStackTrace();
        }
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

    public Throwable forceFillInStackTrace() {
        return super.fillInStackTrace();
    }

    @Override
    public Node getLocation() {
        return location;
    }

    public void setLocation(Node location) {
        this.location = location;
    }

    public boolean shouldHideLocation() {
        return hideLocation;
    }

    public void setHideLocation(boolean hideLocation) {
        this.hideLocation = hideLocation;
    }

    /**
     * Return the associated {@link PBaseException}. This method doesn't ensure traceback
     * consistency and should be avoided unless you can guarantee that the exception will not escape
     * to the program. Use {@link PException#setCatchingFrameAndGetEscapedException(VirtualFrame)
     * reifyAndGetPythonException}.
     */
    @Override
    public PBaseException getExceptionObject() {
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
        if (pythonException == null) {
            return false;
        }
        Object clazz = PythonObjectLibrary.getUncached().getLazyPythonClass(pythonException);
        return IsBuiltinClassProfile.profileClassSlowPath(clazz, PythonBuiltinClassType.SyntaxError) ||
                        IsSubtypeNode.getUncached().execute(clazz, PythonBuiltinClassType.SyntaxError);
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

    public void expectStopIteration(IsBuiltinClassProfile profile, PRaiseNode raise, Object o) {
        if (!profile.profileException(this, PythonBuiltinClassType.StopIteration)) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            Object clazz = PythonObjectLibrary.getUncached().getLazyPythonClass(getExceptionObject());
            if (IsBuiltinClassProfile.profileClassSlowPath(clazz, PythonBuiltinClassType.AttributeError)) {
                throw raise.raise(PythonBuiltinClassType.TypeError, ErrorMessages.OBJ_NOT_ITERABLE, PythonObjectLibrary.getUncached().getLazyPythonClass(o));
            }
            throw this;
        }
    }

    public void expectStopIteration(IsBuiltinClassProfile profile, PythonObjectLibrary lib) {
        if (!profile.profileException(this, PythonBuiltinClassType.StopIteration, lib)) {
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

    public void setCatchingFrameReference(PFrame.Reference frameInfo) {
        this.frameInfo = frameInfo;
    }

    /**
     * Save the exception handler's frame for the traceback. Should be called by all
     * exception-handling structures that need their current frame to be visible in the traceback,
     * i.e except, finally and __exit__. The frame is not yet marked as escaped.
     *
     * @param frame The current frame of the exception handler.
     */
    public void setCatchingFrameReference(VirtualFrame frame) {
        setCatchingFrameReference(PArguments.getCurrentFrameInfo(frame));
    }

    /**
     * Shortcut for {@link #setCatchingFrameReference(PFrame.Reference) reify} and @{link
     * {@link #getEscapedException()}}
     */
    public PBaseException setCatchingFrameAndGetEscapedException(VirtualFrame frame) {
        this.frameInfo = PArguments.getCurrentFrameInfo(frame);
        return this.getEscapedException();
    }

    public void markFrameEscaped() {
        if (this.frameInfo != null) {
            this.frameInfo.markAsEscaped();
        }
    }

    /**
     * Get the python exception while ensuring that the traceback frame is marked as escaped
     */
    public PBaseException getEscapedException() {
        markFrameEscaped();
        return pythonException;
    }

    /**
     * Get traceback from the time the exception was caught (reified). The contained python object's
     * traceback may not be the same as it is mutable and thus may change after being caught.
     */
    public LazyTraceback getTraceback() {
        ensureReified();
        return traceback;
    }

    /**
     * Set traceback for the exception state. This has no effect on the contained python exception
     * object at this point but it may be synced to the object at a later point if the exception
     * state gets reraised (for example with `raise` without arguments as opposed to the exception
     * object itself being explicitly reraised with `raise e`).
     */
    public void setTraceback(LazyTraceback traceback) {
        ensureReified();
        this.traceback = traceback;
    }

    /**
     * If not done already, create the traceback for this exception state using the frame previously
     * provided to {@link #setCatchingFrameReference(PFrame.Reference) setCatchingFrameReference}
     * and sync it to the attached python exception
     */
    public void ensureReified() {
        if (!reified) {
            // Frame may be null when the catch handler is the C boundary, which is internal and
            // shouldn't leak to the traceback
            if (frameInfo != null) {
                assert frameInfo != PFrame.Reference.EMPTY;
                frameInfo.markAsEscaped();
            }
            // Make a snapshot of the traceback at the point of the exception handler. This may be
            // called later than in the exception handler, but only in cases when the exception
            // hasn't escaped to the prgram and thus couldn't have changed in the meantime
            traceback = pythonException.internalReifyException(frameInfo);
            reified = true;
        }
    }

    /**
     * Prepare a new exception to be thrown to provide the semantics of a reraise. The difference
     * between this method and creating a new exception using
     * {@link #fromObject(PBaseException, Node, boolean) fromObject} is that this method makes the
     * traceback look like the last catch didn't happen, which is desired in `raise` without
     * arguments, at the end of `finally`, `__exit__`...
     */
    public PException getExceptionForReraise() {
        return pythonException.getExceptionForReraise(getTraceback());
    }
}
