/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.traceback.PTraceback;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.nodes.object.GetLazyClassNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.formatting.ErrorMessageFormatter;
import com.oracle.graal.python.runtime.sequence.storage.BasicSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleStackTrace;

public final class PBaseException extends PythonObject {

    private static final ErrorMessageFormatter FORMATTER = new ErrorMessageFormatter();

    private PTuple args; // can be null for lazily generated message

    // in case of lazily generated messages, these will be used to construct the message:
    private final String messageFormat;
    private final Object[] messageArgs;

    private PException exception;
    private PTraceback traceback;

    public PBaseException(LazyPythonClass cls, PTuple args) {
        super(cls);
        this.args = args;
        this.messageFormat = null;
        this.messageArgs = null;
    }

    public PBaseException(LazyPythonClass cls, String format, Object[] args) {
        super(cls);
        this.args = null;
        this.messageFormat = format;
        this.messageArgs = args;
    }

    public PException getException() {
        return exception;
    }

    public void setException(PException exception) {
        this.exception = exception;
    }

    public PTraceback getTraceback() {
        return traceback;
    }

    public void setTraceback(PTraceback traceback) {
        this.traceback = traceback;
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
     * This function must be called before handing out exceptions into the Python value space,
     * because otherwise the stack will not be correct if the exception object escapes the current
     * function.
     */
    @TruffleBoundary
    public void reifyException() {
        // TODO: frames: get rid of this entirely
        TruffleStackTrace.fillIn(exception);
    }
}
