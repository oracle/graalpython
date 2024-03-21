/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.nodes.BuiltinNames.T_SYS;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.exception.ExceptionNodes;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.traceback.LazyTraceback;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.bytecode.BytecodeFrameInfo;
import com.oracle.graal.python.nodes.bytecode.FrameInfo;
import com.oracle.graal.python.nodes.bytecode_dsl.BytecodeDSLFrameInfo;
import com.oracle.graal.python.nodes.bytecode_dsl.PBytecodeDSLRootNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.exception.TopLevelExceptionHandler;
import com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

public final class ExceptionUtils {
    private ExceptionUtils() {
    }

    @TruffleBoundary
    public static void printPythonLikeStackTrace() {
        CompilerAsserts.neverPartOfCompilation("printPythonLikeStackTrace is a debug method");
        final ArrayList<String> stack = new ArrayList<>();
        Truffle.getRuntime().iterateFrames((FrameInstanceVisitor<Frame>) frameInstance -> {
            RootCallTarget target = (RootCallTarget) frameInstance.getCallTarget();
            RootNode rootNode = target.getRootNode();
            Node location = frameInstance.getCallNode();
            if (location == null) {
                location = rootNode;
            }
            int lineno = getLineno(frameInstance.getFrame(FrameInstance.FrameAccess.READ_ONLY), location, frameInstance);
            appendStackLine(stack, location, rootNode, true, lineno);
            return null;
        });
        printStack(new PrintWriter(System.err, true), stack);
    }

    private static int getLineno(Frame frame, Node location, FrameInstance frameInstance) {
        if (frame != null && frame.getFrameDescriptor().getInfo() instanceof FrameInfo frameInfo) {
            if (PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER) {
                BytecodeNode bytecodeNode = null;
                if (frameInstance != null) {
                    bytecodeNode = BytecodeNode.get(frameInstance);
                } else {
                    // NB: This fails for the top stack frame, which has no BytecodeNode.
                    bytecodeNode = BytecodeNode.get(location);
                }

                if (bytecodeNode != null) {
                    return bytecodeNode.getBytecodeLocation(frame, location).findSourceLocation().getStartLine();
                }
            } else {
                return ((BytecodeFrameInfo) frameInfo).getLine(frame);
            }
        }
        return -1;

    }

    @TruffleBoundary
    public static void printToStdErr(String message, Object... args) {
        PrintWriter printWriter = new PrintWriter(System.err, true);
        printWriter.printf(message, args);
    }

    /**
     * this method is similar to 'PyErr_WriteUnraisable' and may be used when the context is not
     * available.
     */
    @TruffleBoundary
    public static void printPythonLikeStackTrace(Throwable e) {
        printPythonLikeStackTrace(new PrintWriter(System.err, true), e);
    }

    /**
     * this method is similar to 'PyErr_WriteUnraisable'
     */
    @TruffleBoundary
    public static void printPythonLikeStackTrace(PythonContext context, Throwable e) {
        printPythonLikeStackTrace(new PrintWriter(context.getEnv().err(), true), e);
    }

    @TruffleBoundary
    public static void printPythonLikeStackTrace(PrintWriter p, Throwable e) {
        List<TruffleStackTraceElement> stackTrace = TruffleStackTrace.getStackTrace(e);
        if (stackTrace != null) {
            ArrayList<String> stack = new ArrayList<>();
            for (TruffleStackTraceElement frame : stackTrace) {
                Node location = frame.getLocation();
                RootNode rootNode = frame.getTarget().getRootNode();
                int lineno = getLineno(frame.getFrame(), location, null);
                appendStackLine(stack, location, rootNode, false, lineno);
            }
            printStack(p, stack);
        }
        InteropLibrary lib = InteropLibrary.getUncached();
        if (lib.isException(e) && lib.hasExceptionMessage(lib)) {
            try {
                p.println(lib.getExceptionMessage(e));
            } catch (UnsupportedMessageException unsupportedMessageException) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        } else {
            String message = e.getMessage();
            p.println(message == null ? e.getClass().getSimpleName() : message);
        }
    }

    private static void appendStackLine(ArrayList<String> stack, Node location, RootNode rootNode, boolean evenWithoutSource, int lineno) {
        if (rootNode instanceof TopLevelExceptionHandler) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        SourceSection sourceSection = location != null ? location.getEncapsulatingSourceSection() : null;
        if (sourceSection != null) {
            sb.append("  File \"");
            Source source = sourceSection.getSource();
            sb.append(source.getPath() != null ? source.getPath() : source.getName());
            sb.append("\", line ");
            sb.append(lineno > 0 ? lineno : sourceSection.getStartLine());
            sb.append(", in ");
            sb.append(rootNode.getName());
        } else if (evenWithoutSource) {
            if (rootNode instanceof BuiltinFunctionRootNode) {
                sb.append("  Builtin function ");
                sb.append(rootNode.getName());
                sb.append(" (node class ");
                sb.append(((BuiltinFunctionRootNode) rootNode).getFactory().getNodeClass().getName());
                sb.append(")");
            } else {
                sb.append("  Builtin root ");
                sb.append(rootNode.getName());
                sb.append(" (class ");
                sb.append(rootNode.getClass().getName());
                sb.append(")");
            }
        }
        if (sb.length() > 0) {
            stack.add(sb.toString());
        }
    }

    private static void printStack(PrintWriter p, final ArrayList<String> stack) {
        p.println("Traceback (most recent call last):");
        ListIterator<String> listIterator = stack.listIterator(stack.size());
        while (listIterator.hasPrevious()) {
            p.println(listIterator.previous());
        }
    }

    /**
     * This function is kind-of analogous to PyErr_PrintEx
     */
    @TruffleBoundary
    public static void printExceptionTraceback(PythonContext context, Object pythonException) {
        Object type = GetClassNode.executeUncached(pythonException);
        Object tb = ExceptionNodes.GetTracebackNode.executeUncached(pythonException);

        Object hook = context.lookupBuiltinModule(T_SYS).getAttribute(BuiltinNames.T_EXCEPTHOOK);
        if (hook != PNone.NO_VALUE) {
            try {
                // Note: it is important to pass frame 'null' because that will cause the
                // CallNode to tread the invoke like a foreign call and access the top frame ref
                // in the context.
                CallNode.getUncached().execute(null, hook, new Object[]{type, pythonException, tb}, PKeyword.EMPTY_KEYWORDS);
            } catch (PException internalError) {
                // More complex handling of errors in exception printing is done in our
                // Python code, if we get here, we just fall back to the launcher
                if (pythonException instanceof PBaseException managedException) {
                    throw managedException.getExceptionForReraise(managedException.getTraceback());
                } else {
                    throw PException.fromObject(pythonException, null, false);
                }
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
        LazyTraceback traceback = null;
        if (e.getUnreifiedException() instanceof PBaseException pythonException) {
            traceback = pythonException.getTraceback();
        }
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
            if (exception != null && exception.getCause() != null && exception.getCause().getStackTrace().length != 0) {
                exception.getCause().printStackTrace();
            }
        }
    }

    public static PException wrapJavaException(Throwable e, Node node, PBaseException pythonException) {
        return PException.fromObject(pythonException, node, e);
    }
}
