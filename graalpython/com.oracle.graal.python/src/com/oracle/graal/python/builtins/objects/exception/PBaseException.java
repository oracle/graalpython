/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.exception;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.traceback.LazyTraceback;
import com.oracle.graal.python.builtins.objects.traceback.PTraceback;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.formatting.ErrorMessageFormatter;
import com.oracle.graal.python.runtime.sequence.storage.BasicSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.object.DynamicObject;

public final class PBaseException extends PythonObject {
    private static final Object[] EMPTY_ARGS = new Object[0];
    private static final ErrorMessageFormatter FORMATTER = new ErrorMessageFormatter();

    private PTuple args; // can be null for lazily generated message

    // in case of lazily generated messages, these will be used to construct the message:
    private final boolean hasMessageFormat;
    private final String messageFormat;
    private final Object[] messageArgs;

    private PException exception;
    private LazyTraceback traceback;

    private PBaseException context;
    private PBaseException cause;
    private boolean suppressContext = false;

    public PBaseException getContext() {
        return context;
    }

    public void setContext(PBaseException context) {
        this.context = context;
    }

    public PBaseException getCause() {
        return cause;
    }

    public void setCause(PBaseException cause) {
        this.cause = cause;
        this.suppressContext = true;
    }

    public boolean getSuppressContext() {
        return suppressContext;
    }

    public void setSuppressContext(boolean suppressContext) {
        this.suppressContext = suppressContext;
    }

    public PBaseException(Object cls, DynamicObject storage, PTuple args) {
        super(cls, storage);
        this.args = args;
        this.hasMessageFormat = false;
        this.messageFormat = null;
        this.messageArgs = null;
    }

    public PBaseException(Object cls, DynamicObject storage) {
        super(cls, storage);
        this.args = null;
        this.hasMessageFormat = false;
        this.messageFormat = null;
        this.messageArgs = null;
    }

    public PBaseException(Object cls, DynamicObject storage, String format, Object[] args) {
        super(cls, storage);
        this.args = null;
        this.hasMessageFormat = true;
        this.messageFormat = format;
        this.messageArgs = args;
    }

    public PException getException() {
        return exception;
    }

    public void setException(PException exception) {
        this.exception = exception;
    }

    public void ensureReified() {
        if (exception != null) {
            // If necessary, this will call back to this object to set the traceback
            exception.ensureReified();
        }
    }

    public LazyTraceback getTraceback() {
        ensureReified();
        return traceback;
    }

    public void setTraceback(LazyTraceback traceback) {
        ensureReified();
        this.traceback = traceback;
    }

    public void setTraceback(PTraceback traceback) {
        setTraceback(new LazyTraceback(traceback));
    }

    public void clearTraceback() {
        this.traceback = null;
    }

    /**
     * Can be null in case of lazily formatted arguments.
     */
    public PTuple getArgs() {
        return args;
    }

    public void setArgs(PTuple args) {
        this.args = args;
    }

    public String getMessageFormat() {
        return messageFormat;
    }

    public boolean hasMessageFormat() {
        return hasMessageFormat;
    }

    public Object[] getMessageArgs() {
        // clone message args to ensure that they stay unmodified
        return messageArgs.clone();
    }

    public String getFormattedMessage(PythonObjectLibrary lib) {
        final Object clazz;
        if (lib == null) {
            clazz = PythonObjectLibrary.getUncached().getLazyPythonClass(this);
        } else {
            clazz = lib.getLazyPythonClass(this);
        }
        String typeName = GetNameNode.doSlowPath(clazz);
        if (args == null) {
            if (messageArgs != null && messageArgs.length > 0) {
                return typeName + ": " + FORMATTER.format(lib, messageFormat, getMessageArgs());
            } else if (hasMessageFormat) {
                return typeName + ": " + messageFormat;
            } else {
                return typeName;
            }
        } else if (args.getSequenceStorage().length() == 0) {
            return typeName;
        } else if (args.getSequenceStorage().length() == 1) {
            SequenceStorage store = args.getSequenceStorage();
            Object item = store instanceof BasicSequenceStorage ? store.getItemNormalized(0) : "<unknown>";
            return typeName + ": " + item.toString();
        } else {
            return typeName + ": " + args.toString();
        }
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return getFormattedMessage(null);
    }

    public LazyTraceback internalReifyException(PFrame.Reference curFrameInfo) {
        assert curFrameInfo != PFrame.Reference.EMPTY;
        traceback = new LazyTraceback(curFrameInfo, exception, traceback);
        return traceback;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isException() {
        return true;
    }

    @ExportMessage
    RuntimeException throwException(@CachedLibrary("this") PythonObjectLibrary lib,
                    @Cached PRaiseNode raiseNode) {
        Object[] newArgs = messageArgs;
        if (newArgs == null) {
            newArgs = EMPTY_ARGS;
        }
        Object format = messageFormat;
        if (format == null) {
            format = PNone.NO_VALUE;
        }
        throw raiseNode.execute(lib.getLazyPythonClass(this), this, format, newArgs);
    }

    /**
     * Prepare a {@link PException} for reraising this exception, as done by <code>raise</code>
     * without arguments.
     *
     * <p>
     * We must be careful to never rethrow a PException that has already been caught and exposed to
     * the program, because its Truffle lazy stacktrace may have been already materialized, which
     * would prevent it from capturing frames after the rethrow. So we need a new PException even
     * though its just a dumb rethrow. We also need a new PException for the reason below.
     * </p>
     *
     * <p>
     * CPython reraises the exception with the traceback captured in sys.exc_info, discarding the
     * traceback in the exception, which may have changed since the time the exception had been
     * caught. This can happen due to explicit modification by the program or, more commonly, by
     * accumulating more frames by being reraised in the meantime. That's why this method takes an
     * explicit traceback argument
     * </p>
     *
     * <p>
     * Reraises shouldn't be visible in the stacktrace. We mark them as such.
     * </p>
     **/
    public PException getExceptionForReraise(LazyTraceback reraiseTraceback) {
        setTraceback(reraiseTraceback);
        PException newException = PException.fromObject(this, exception.getLocation());
        newException.setHideLocation(true);
        return newException;
    }
}
