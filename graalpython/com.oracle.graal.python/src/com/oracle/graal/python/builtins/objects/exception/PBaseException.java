/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 *     one is included with the Software (each a "Larger Work" to which the
 *     Software is contributed by such licensors),
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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.traceback.PTraceback;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.nodes.control.TopLevelExceptionHandler;
import com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.formatting.ErrorMessageFormatter;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.nodes.RootNode;

public final class PBaseException extends PythonObject {

    private static final ErrorMessageFormatter FORMATTER = new ErrorMessageFormatter();

    private PTuple args; // can be null for lazily generated message

    // in case of lazily generated messages, these will be used to construct the message:
    private final String messageFormat;
    private final Object[] messageArgs;

    private PException exception;

    private List<TruffleStackTraceElement> stackTrace;
    private PTraceback[] traceback;

    public PBaseException(PythonClass cls, PTuple args) {
        super(cls);
        this.args = args;
        this.messageFormat = null;
        this.messageArgs = null;
    }

    public PBaseException(PythonClass cls, String format, Object[] args) {
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

    public PTraceback getTraceback(PythonObjectFactory factory, int index) {
        if (index < 0 || index >= traceback.length) {
            return null;
        }
        if (traceback[index] == null) {
            traceback[index] = factory.createTraceback(this, index);
        }
        return traceback[index];
    }

    public PTraceback getTraceback(PythonObjectFactory factory) {
        reifyException();
        return getTraceback(factory, traceback.length - 1);
    }

    public void setTraceback(PTraceback traceback) {
        this.traceback = traceback.getException().traceback;
    }

    public void clearTraceback() {
        this.traceback = new PTraceback[0];
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
        return messageArgs;
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        if (args == null) {
            if (messageArgs != null && messageArgs.length > 0) {
                return getPythonClass().getName() + ": " + FORMATTER.format(messageFormat, messageArgs);
            }
            return getPythonClass().getName() + ": " + messageFormat;
        } else if (args.len() == 0) {
            return getPythonClass().getName();
        } else if (args.len() == 1) {
            return getPythonClass().getName() + ": " + args.getItem(0).toString();
        } else {
            return getPythonClass().getName() + ": " + args.toString();
        }
    }

    public List<TruffleStackTraceElement> getStackTrace() {
        return stackTrace;
    }

    /**
     * This function must be called before handing out exceptions into the Python value space,
     * because otherwise the stack will not be correct if the exception object escapes the current
     * function.
     */
    @TruffleBoundary
    public void reifyException() {
        if (stackTrace == null && traceback == null) {
            TruffleStackTraceElement.fillIn(exception);
            stackTrace = new ArrayList<>(TruffleStackTraceElement.getStackTrace(exception));
            Iterator<TruffleStackTraceElement> iter = stackTrace.iterator();
            while (iter.hasNext()) {
                TruffleStackTraceElement element = iter.next();
                // remove all top level exception handlers - they shouldn't show up
                if (element.getTarget() != null) {
                    RootNode rootNode = element.getTarget().getRootNode();
                    if (rootNode instanceof TopLevelExceptionHandler || rootNode instanceof BuiltinFunctionRootNode) {
                        iter.remove();
                    }
                }
            }

            traceback = new PTraceback[stackTrace.size()];
        }
    }

    @TruffleBoundary
    public PFrame getPFrame(PythonObjectFactory factory, int index) {
        assert index >= 0 && index < stackTrace.size() : "PBaseException.getPFrame index out of bounds";
        Frame frame = stackTrace.get(index).getFrame();
        PFrame pFrame;
        if (frame != null) {
            // we have a frame, try to get the pFrame from the magic arguments first
            pFrame = PArguments.getPFrame(frame);
            if (pFrame == null) {
                pFrame = factory.createPFrame(this, index);
                PArguments.setPFrame(frame, pFrame);
            }
        } else {
            pFrame = factory.createPFrame(this, index);
        }
        return pFrame;
    }
}
