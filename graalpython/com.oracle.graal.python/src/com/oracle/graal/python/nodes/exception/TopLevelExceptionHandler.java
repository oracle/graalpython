/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.exception;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.RecursionError;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_WRITE;
import static com.oracle.graal.python.nodes.BuiltinNames.T_SYS;
import static com.oracle.graal.python.runtime.exception.ExceptionUtils.printToStdErr;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.SystemExit;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.exception.ExceptionNodes;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectStrAsObjectNode;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.bytecode.PBytecodeRootNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetClassNode.GetPythonObjectClassNode;
import com.oracle.graal.python.nodes.object.GetOrCreateDictNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaLongLossyNode;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCalleeContext;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.ExceptionUtils;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonExitException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

public final class TopLevelExceptionHandler extends RootNode {
    private final RootCallTarget innerCallTarget;
    private final PException exception;
    private final SourceSection sourceSection;
    private final Source source;

    @Child private GilNode gilNode = GilNode.create();

    /*
     * Necessary to forward materializeInstrumentableNodes calls to the root node. Truffle assumes
     * that all source sections of a source are created when the first call target is initialized.
     * That may sometimes happen before the bytecode root's call target is initialized, so we need
     * to have a node that will trigger the materialization of all nested functions that haven't
     * been created yet at this point.
     */
    @Child private Node instrumentationForwarder;

    public TopLevelExceptionHandler(PythonLanguage language, RootNode child, Source source) {
        super(language);
        this.sourceSection = child.getSourceSection();
        this.innerCallTarget = PythonUtils.getOrCreateCallTarget(child);
        this.exception = null;
        this.source = source;
        if (child instanceof PBytecodeRootNode) {
            instrumentationForwarder = ((PBytecodeRootNode) child).createInstrumentationMaterializationForwarder();
        }
    }

    public TopLevelExceptionHandler(PythonLanguage language, PException exception) {
        super(language);
        this.sourceSection = exception.getLocation().getEncapsulatingSourceSection();
        this.innerCallTarget = null;
        this.exception = exception;
        this.source = null;
    }

    private PythonLanguage getPythonLanguage() {
        return getLanguage(PythonLanguage.class);
    }

    private PythonContext getContext() {
        return PythonContext.get(this);
    }

    @Override
    @SuppressWarnings("try")
    public Object execute(VirtualFrame frame) {
        boolean wasAcquired = gilNode.acquire();
        try {
            if (exception != null) {
                throw handlePythonException(exception);
            } else {
                checkInitialized();
                PythonContext pythonContext = getContext();
                PythonLanguage lang = getPythonLanguage();
                assert pythonContext.getThreadState(lang).getCurrentException() == null;
                try {
                    return run(frame);
                } catch (PException e) {
                    assert !PArguments.isPythonFrame(frame);
                    Object pythonException = e.getEscapedException();
                    if (pythonException instanceof PBaseException managedException && getContext().isChildContext() && isSystemExit(managedException)) {
                        return handleChildContextExit(managedException);
                    }
                    throw handlePythonException(e);
                } catch (StackOverflowError e) {
                    CompilerDirectives.transferToInterpreter();
                    PythonContext context = getContext();
                    context.reacquireGilAfterStackOverflow();
                    PBaseException newException = context.factory().createBaseException(RecursionError, ErrorMessages.MAXIMUM_RECURSION_DEPTH_EXCEEDED, new Object[]{});
                    PException pe = ExceptionUtils.wrapJavaException(e, this, newException);
                    throw handlePythonException(pe);
                } catch (ThreadDeath e) {
                    // do not handle, result of TruffleContext.closeCancelled()
                    throw e;
                } catch (Throwable e) {
                    handleJavaException(e);
                    throw e;
                }
            }
        } finally {
            gilNode.release(wasAcquired);
        }
    }

    @TruffleBoundary
    private void checkInitialized() {
        Python3Core core = getContext();
        if (core.isCoreInitialized() &&
                        (PythonLanguage.MIME_TYPE.equals(source.getMimeType()))) {
            getContext().initializeMainModule(toTruffleStringUncached(source.getPath()));
        }
    }

    @TruffleBoundary
    private PException handlePythonException(PException pException) {
        Object pythonException = pException.getEscapedException();
        if (pythonException instanceof PBaseException managedException && isSystemExit(managedException)) {
            handleSystemExit(managedException);
        }
        if (getContext().getOption(PythonOptions.AlwaysRunExcepthook)) {
            Object type = GetClassNode.executeUncached(pythonException);
            Object tb = ExceptionNodes.GetTracebackNode.executeUncached(pythonException);

            PythonModule sys = getContext().lookupBuiltinModule(T_SYS);
            sys.setAttribute(BuiltinNames.T_LAST_TYPE, type);
            sys.setAttribute(BuiltinNames.T_LAST_VALUE, pythonException);
            sys.setAttribute(BuiltinNames.T_LAST_TRACEBACK, tb);

            ExceptionUtils.printExceptionTraceback(getContext(), pythonException);
            if (PythonOptions.isPExceptionWithJavaStacktrace(getPythonLanguage())) {
                ExceptionUtils.printJavaStackTrace(pException);
            }
            if (!getSourceSection().getSource().isInteractive()) {
                if (getContext().isChildContext()) {
                    getContext().getChildContextData().setExitCode(1);
                }
                throw new PythonExitException(this, 1);
            }
        }
        // Before we leave Python, format the message since outside the context
        if (pythonException instanceof PBaseException managedException) {
            pException.setMessage(managedException.getFormattedMessage());
        }
        throw pException;
    }

    private static boolean isSystemExit(PBaseException pythonException) {
        return IsBuiltinClassProfile.profileClassSlowPath(GetPythonObjectClassNode.executeUncached(pythonException), SystemExit);
    }

    @TruffleBoundary
    private void handleJavaException(Throwable e) {
        try {
            boolean exitException = InteropLibrary.getUncached().isException(e) && InteropLibrary.getUncached().getExceptionType(e) == ExceptionType.EXIT;
            if (!exitException) {
                ExceptionUtils.printPythonLikeStackTrace(getContext(), e);
                boolean withJavaStacktrace = PythonOptions.isWithJavaStacktrace(getPythonLanguage());
                if (e instanceof AssertionError && !withJavaStacktrace) {
                    printToStdErr("To get more information about the failed assertion rerun with --python.WithJavaStacktrace=3\n");
                }
                if (withJavaStacktrace) {
                    e.printStackTrace();
                }
            }
        } catch (UnsupportedMessageException unsupportedMessageException) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @Override
    public SourceSection getSourceSection() {
        return sourceSection;
    }

    @TruffleBoundary
    private void handleSystemExit(PBaseException pythonException) {
        PythonContext theContext = getContext();
        if (theContext.getOption(PythonOptions.InspectFlag) && !getSourceSection().getSource().isInteractive()) {
            // Don't exit if -i flag was given and we're not yet running interactively
            return;
        }
        try {
            int exitcode = getExitCode(pythonException);
            throw new PythonExitException(this, exitcode);
        } catch (CannotCastException e) {
            // fall through
        }
        if (handleAlwaysRunExceptHook(theContext, pythonException)) {
            throw new PythonExitException(this, 1);
        }
        throw pythonException.getExceptionForReraise(pythonException.getTraceback());
    }

    @TruffleBoundary
    private Object handleChildContextExit(PBaseException pythonException) throws PException {
        // avoid throwing PythonExitException from spawned child context, return only exitCode
        try {
            return getExitCode(pythonException);
        } catch (CannotCastException cce) {
            // fall through
        }
        if (handleAlwaysRunExceptHook(getContext(), pythonException)) {
            return 1;
        }
        throw pythonException.getExceptionForReraise(pythonException.getTraceback());
    }

    private static int getExitCode(PBaseException pythonException) throws CannotCastException {
        final Object[] exceptionAttributes = pythonException.getExceptionAttributes();
        int exitcode = 0;
        if (exceptionAttributes != null) {
            final Object code = exceptionAttributes[0];
            if (code != PNone.NONE) {
                exitcode = (int) CastToJavaLongLossyNode.executeUncached(code);
            }
        }
        return exitcode;
    }

    @TruffleBoundary
    private static boolean handleAlwaysRunExceptHook(PythonContext theContext, PBaseException pythonException) {
        if (theContext.getOption(PythonOptions.AlwaysRunExcepthook)) {
            // If we failed to dig out the exit code we just print and leave
            Object stderr = theContext.getStderr();
            Object message = PyObjectStrAsObjectNode.getUncached().execute(null, pythonException);
            PyObjectCallMethodObjArgs.executeUncached(stderr, T_WRITE, message);
            return true;
        }
        return false;
    }

    private Object run(VirtualFrame frame) {
        Object[] arguments = PArguments.create(frame.getArguments().length);
        for (int i = 0; i < frame.getArguments().length; i++) {
            PArguments.setArgument(arguments, i, frame.getArguments()[i]);
        }
        PythonContext pythonContext = getContext();
        PythonModule mainModule = null;
        if (source.isInternal()) {
            // internal sources are not run in the main module
            PArguments.setGlobals(arguments, pythonContext.factory().createDict());
        } else {
            mainModule = pythonContext.getMainModule();
            PDict mainDict = GetOrCreateDictNode.executeUncached(mainModule);
            PArguments.setGlobals(arguments, mainModule);
            PArguments.setSpecialArgument(arguments, mainDict);
            PArguments.setException(arguments, PException.NO_EXCEPTION);
        }
        Object state = IndirectCalleeContext.enterIndirect(getPythonLanguage(), pythonContext, arguments);
        try {
            Object result = innerCallTarget.call(arguments);
            if (mainModule != null && result == PNone.NONE && !source.isInteractive()) {
                return mainModule;
            } else {
                return result;
            }
        } finally {
            IndirectCalleeContext.exit(getPythonLanguage(), pythonContext, state);
        }
    }

    @Override
    public boolean isInternal() {
        return true;
    }
}
