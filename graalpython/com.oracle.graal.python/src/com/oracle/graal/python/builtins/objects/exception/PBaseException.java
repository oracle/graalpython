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
import com.oracle.graal.python.builtins.objects.traceback.PTraceback;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.object.GetLazyClassNode;
import com.oracle.graal.python.nodes.statement.ExceptNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.formatting.ErrorMessageFormatter;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.storage.BasicSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.ConditionProfile;

public final class PBaseException extends PythonObject {
    private static final Object[] EMPTY_ARGS = new Object[0];
    private static final ErrorMessageFormatter FORMATTER = new ErrorMessageFormatter();

    private PTuple args; // can be null for lazily generated message

    // in case of lazily generated messages, these will be used to construct the message:
    private final boolean hasMessageFormat;
    private final String messageFormat;
    private final Object[] messageArgs;

    private PException exception;
    private PTraceback traceback;

    /** The frame info of the Python frame that first caught the exception. */
    private PFrame.Reference frameInfo;

    public PBaseException(LazyPythonClass cls, DynamicObject storage, PTuple args) {
        super(cls, storage);
        this.args = args;
        this.hasMessageFormat = false;
        this.messageFormat = null;
        this.messageArgs = null;
    }

    public PBaseException(LazyPythonClass cls, DynamicObject storage, String format, Object[] args) {
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

    /**
     * use {@link GetTracebackNode}
     */
    PTraceback getTraceback() {
        return traceback;
    }

    public void setTraceback(PTraceback traceback) {
        this.traceback = traceback;
        // Explicitly setting the traceback also makes the frame info for creating a lazy traceback
        // obsolete or even incorrect. So it is cleared.
        this.frameInfo = null;
    }

    public void clearTraceback() {
        this.traceback = null;
    }

    /**
     * It should usually not be necessary to use this method because that would mean that the
     * exception wasn't correctly reified. But it can make sense to do reification at a later point
     * for best-effort results.
     */
    public boolean hasTraceback() {
        return this.traceback != null;
    }

    public PFrame.Reference getFrameInfo() {
        return frameInfo;
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

    public String getFormattedMessage(GetLazyClassNode getClassNode) {
        String typeName = GetNameNode.doSlowPath(getLazyPythonClass());
        if (args == null) {
            if (messageArgs != null && messageArgs.length > 0) {
                return typeName + ": " + FORMATTER.format(getClassNode, messageFormat, getMessageArgs());
            }
            return typeName + ": " + messageFormat;
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

    /**
     * Associate this exception with a frame info that represents the {@link PFrame} instance that
     * caught the exception. Furthermore, store the location at which this exception was caught so
     * we can later reconstruct the correct traceback location.<br>
     * <p>
     * The use case for calling this method is when an exception is caught and the ExceptNode needs
     * to be recorded so that we can later have the correct info.
     * </p>
     */
    public void reifyException(PFrame.Reference info, ExceptNode node) {
        info.setCallNode(node);
        reifyException(info);
    }

    /**
     * Create the traceback for this exception using the provided {@link PFrame} instance (which
     * usually is the frame of the function that caught the exception).
     * <p>
     * This function (of {@link #reifyException(PFrame.Reference)} must be called before handing out
     * exceptions into the Python value space because otherwise the stack will not be correct if the
     * exception object escapes the current function.
     * </p>
     */
    public void reifyException(PFrame pyFrame, PythonObjectFactory factory) {
        traceback = factory.createTraceback(pyFrame, exception);
        frameInfo = pyFrame.getRef();

        // TODO: frames: provide legacy stack walk method via Python option
        // TruffleStackTrace.fillIn(exception);
    }

    /**
     * Associate this exception with a frame info that represents the {@link PFrame} instance that
     * caught the exception.<br>
     * <p>
     * In contrast to {@link #reifyException(PFrame, PythonObjectFactory)}, this method can be used
     * if the {@link PFrame} instance isn't already available and if the Truffle frame is also not
     * available to create the {@link PFrame} instance using the
     * {@link com.oracle.graal.python.nodes.frame.MaterializeFrameNode}.
     * </p>
     * <p>
     * The most common use case for calling this method is when an exception is thrown in some
     * Python code but we catch the exception in some interop node (that is certainly adopted by
     * some foreign language's root node). In this case, we do not want to eagerly create the
     * {@link PFrame} instance when calling from Python to the foreign language since this could be
     * expensive. The traceback can then be created lazily from the frame info.
     * </p>
     */
    public void reifyException(PFrame.Reference curFrameInfo) {
        assert curFrameInfo != PFrame.Reference.EMPTY;
        traceback = null;
        curFrameInfo.markAsEscaped();
        this.frameInfo = curFrameInfo;

        // TODO: frames: provide legacy stack walk method via Python option
        // TruffleStackTrace.fillIn(exception);
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isException() {
        return true;
    }

    @ExportMessage
    RuntimeException throwException(@Cached("createBinaryProfile()") ConditionProfile hasExc,
                    @Cached GetLazyClassNode getClass,
                    @Cached PRaiseNode raiseNode) {
        PException exc = getException();
        if (hasExc.profile(exc != null)) {
            throw exc;
        } else {
            Object[] newArgs = messageArgs;
            if (newArgs == null) {
                newArgs = EMPTY_ARGS;
            }
            Object format = messageFormat;
            if (format == null) {
                format = PNone.NO_VALUE;
            }
            throw raiseNode.execute(getClass.execute(this), this, format, newArgs);
        }
    }
}
