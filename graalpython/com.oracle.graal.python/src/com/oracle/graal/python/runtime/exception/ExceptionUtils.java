/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.exception.GetExceptionTracebackNode;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.traceback.LazyTraceback;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

public final class ExceptionUtils {
    private ExceptionUtils() {
    }

    @TruffleBoundary
    public static void printPythonLikeStackTrace() {
        CompilerAsserts.neverPartOfCompilation("printPythonLikeStackTrace is a debug method");
        final ArrayList<String> stack = new ArrayList<>();
        Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<Frame>() {
            public Frame visitFrame(FrameInstance frameInstance) {
                RootCallTarget target = (RootCallTarget) frameInstance.getCallTarget();
                RootNode rootNode = target.getRootNode();
                Node location = frameInstance.getCallNode();
                if (location == null) {
                    location = rootNode;
                }
                appendStackLine(stack, location, rootNode, true);
                return null;
            }
        });
        printStack(stack);
    }

    /**
     * this method is similar to 'PyErr_WriteUnraisable'
     */
    @TruffleBoundary
    public static void printPythonLikeStackTrace(Throwable e) {
        List<TruffleStackTraceElement> stackTrace = TruffleStackTrace.getStackTrace(e);
        if (stackTrace != null) {
            ArrayList<String> stack = new ArrayList<>();
            for (TruffleStackTraceElement frame : stackTrace) {
                Node location = frame.getLocation();
                RootNode rootNode = frame.getTarget().getRootNode();
                appendStackLine(stack, location, rootNode, false);
            }
            printStack(stack);
        }
        InteropLibrary lib = InteropLibrary.getUncached();
        if (lib.isException(e) && lib.hasExceptionMessage(lib)) {
            try {
                System.err.println(lib.getExceptionMessage(e));
            } catch (UnsupportedMessageException unsupportedMessageException) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        } else {
            System.err.println(e.getMessage());
        }
    }

    private static void appendStackLine(ArrayList<String> stack, Node location, RootNode rootNode, boolean evenWithoutSource) {
        StringBuilder sb = new StringBuilder();
        SourceSection sourceSection = location != null ? location.getEncapsulatingSourceSection() : null;
        String rootName = rootNode.getName();
        if (sourceSection != null) {
            sb.append("  ");
            String path = sourceSection.getSource().getPath();
            if (path != null) {
                sb.append("File ");
            }
            sb.append('"');
            sb.append(sourceSection.getSource().getName());
            sb.append("\", line ");
            sb.append(sourceSection.getStartLine());
            sb.append(", in ");
        } else if (evenWithoutSource) {
            sb.append("unknown location in ");
        }
        if (sourceSection != null || evenWithoutSource) {
            sb.append(rootName);
            stack.add(sb.toString());
        }
    }

    private static void printStack(final ArrayList<String> stack) {
        System.err.println("Traceback (most recent call last):");
        ListIterator<String> listIterator = stack.listIterator(stack.size());
        while (listIterator.hasPrevious()) {
            System.err.println(listIterator.previous());
        }
    }

    /**
     * This function is kind-of analogous to PyErr_PrintEx
     */
    @TruffleBoundary
    public static void printExceptionTraceback(PythonContext context, PBaseException pythonException) {
        Object type = GetClassNode.getUncached().execute(pythonException);
        Object tb = GetExceptionTracebackNode.getUncached().execute(pythonException);

        Object hook = context.lookupBuiltinModule("sys").getAttribute(BuiltinNames.EXCEPTHOOK);
        if (hook != PNone.NO_VALUE) {
            try {
                // Note: it is important to pass frame 'null' because that will cause the
                // CallNode to tread the invoke like a foreign call and access the top frame ref
                // in the context.
                CallNode.getUncached().execute(null, hook, new Object[]{type, pythonException, tb}, PKeyword.EMPTY_KEYWORDS);
            } catch (PException internalError) {
                // More complex handling of errors in exception printing is done in our
                // Python code, if we get here, we just fall back to the launcher
                throw pythonException.getExceptionForReraise(pythonException.getTraceback());
            }
        } else {
            try {
                context.getEnv().err().write("sys.excepthook is missing\n".getBytes());
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }
    }

    @TruffleBoundary
    public static void printJavaStackTrace(PException e) {
        LazyTraceback traceback = e.getTraceback();
        // Find the exception for the original raise site (not for subsequent reraises)
        while (traceback != null && traceback.getNextChain() != null) {
            traceback = traceback.getNextChain();
        }
        if (traceback != null) {
            PException exception = traceback.getException();
            // PException itself has Java-level stacktraces always disabled.
            // In case of PExceptions that wrap real Java exceptions, the cause has the stacktrace
            // for the original exception.
            // In case of ordinary PExceptions, when WithJavaStacktrace is > 1, they have a
            // synthetic cause that carries the stacktrace created at the same place.
            if (exception.getCause() != null && exception.getCause().getStackTrace().length != 0) {
                exception.getCause().printStackTrace();
            }
        }
    }
}
