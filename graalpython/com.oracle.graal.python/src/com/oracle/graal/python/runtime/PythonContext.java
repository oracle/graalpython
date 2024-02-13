/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.runtime;

import static com.oracle.graal.python.builtins.PythonOS.PLATFORM_DARWIN;
import static com.oracle.graal.python.builtins.PythonOS.PLATFORM_WIN32;
import static com.oracle.graal.python.builtins.PythonOS.getPythonOS;
import static com.oracle.graal.python.builtins.modules.SysModuleBuiltins.T_CACHE_TAG;
import static com.oracle.graal.python.builtins.modules.SysModuleBuiltins.T__MULTIARCH;
import static com.oracle.graal.python.builtins.objects.str.StringUtils.cat;
import static com.oracle.graal.python.builtins.objects.thread.PThread.GRAALPYTHON_THREADS;
import static com.oracle.graal.python.nodes.BuiltinNames.T_SHA3;
import static com.oracle.graal.python.nodes.BuiltinNames.T_SYS;
import static com.oracle.graal.python.nodes.BuiltinNames.T_THREADING;
import static com.oracle.graal.python.nodes.BuiltinNames.T___BUILTINS__;
import static com.oracle.graal.python.nodes.BuiltinNames.T___MAIN__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___ANNOTATIONS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___FILE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T_INSERT;
import static com.oracle.graal.python.nodes.StringLiterals.J_EXT_DLL;
import static com.oracle.graal.python.nodes.StringLiterals.J_EXT_DYLIB;
import static com.oracle.graal.python.nodes.StringLiterals.J_EXT_SO;
import static com.oracle.graal.python.nodes.StringLiterals.J_LIB_PREFIX;
import static com.oracle.graal.python.nodes.StringLiterals.J_LLVM_LANGUAGE;
import static com.oracle.graal.python.nodes.StringLiterals.J_NATIVE;
import static com.oracle.graal.python.nodes.StringLiterals.J_NFI_LANGUAGE;
import static com.oracle.graal.python.nodes.StringLiterals.T_DASH;
import static com.oracle.graal.python.nodes.StringLiterals.T_DOT;
import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_STRING;
import static com.oracle.graal.python.nodes.StringLiterals.T_EXT_PYD;
import static com.oracle.graal.python.nodes.StringLiterals.T_EXT_SO;
import static com.oracle.graal.python.nodes.StringLiterals.T_JAVA;
import static com.oracle.graal.python.nodes.StringLiterals.T_LLVM_LANGUAGE;
import static com.oracle.graal.python.nodes.StringLiterals.T_NATIVE;
import static com.oracle.graal.python.nodes.StringLiterals.T_PATH;
import static com.oracle.graal.python.nodes.StringLiterals.T_SITE;
import static com.oracle.graal.python.nodes.StringLiterals.T_SLASH;
import static com.oracle.graal.python.nodes.StringLiterals.T_WARNINGS;
import static com.oracle.graal.python.nodes.truffle.TruffleStringMigrationHelpers.isJavaString;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.invoke.VarHandle;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.nio.file.LinkOption;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.MessageFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.options.OptionKey;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonOS;
import com.oracle.graal.python.builtins.modules.ImpModuleBuiltins;
import com.oracle.graal.python.builtins.modules.MathGuards;
import com.oracle.graal.python.builtins.modules.ctypes.CtypesModuleBuiltins.CtypesThreadState;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject.PInteropGetAttributeNode;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeClass;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext;
import com.oracle.graal.python.builtins.objects.cext.capi.PThreadState;
import com.oracle.graal.python.builtins.objects.cext.capi.PyTruffleObjectFree;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativePointer;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapper.PythonAbstractObjectNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.HandleContext;
import com.oracle.graal.python.builtins.objects.cext.common.LoadCExtException.ApiInitException;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetItem;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorNext;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorValue;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.contextvars.PContextVarsContext;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.exception.ExceptionNodes;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.frame.PFrame.Reference;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.str.StringNodes.StringReplaceNode;
import com.oracle.graal.python.builtins.objects.thread.PLock;
import com.oracle.graal.python.builtins.objects.thread.PThread;
import com.oracle.graal.python.builtins.objects.traceback.LazyTraceback;
import com.oracle.graal.python.builtins.objects.traceback.MaterializeLazyTracebackNode;
import com.oracle.graal.python.builtins.objects.traceback.PTraceback;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.compiler.CodeUnit;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.object.SetDictNode;
import com.oracle.graal.python.nodes.statement.AbstractImportNode;
import com.oracle.graal.python.nodes.util.CastToJavaIntLossyNode;
import com.oracle.graal.python.runtime.AsyncHandler.AsyncAction;
import com.oracle.graal.python.runtime.exception.ExceptionUtils;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonThreadKillException;
import com.oracle.graal.python.runtime.locale.PythonLocale;
import com.oracle.graal.python.runtime.object.IDUtils;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.Consumer;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.graal.python.util.ShutdownHook;
import com.oracle.graal.python.util.Supplier;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.ContextThreadLocal;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.ThreadLocalAction;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleContext.Builder;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NonIdempotent;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.instrumentation.AllocationReporter;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.utilities.CyclicAssumption;
import com.oracle.truffle.api.utilities.TriState;
import com.oracle.truffle.api.utilities.TruffleWeakReference;
import com.oracle.truffle.llvm.api.Toolchain;

import sun.misc.Unsafe;

public final class PythonContext extends Python3Core {
    public static final TruffleString T_IMPLEMENTATION = tsLiteral("implementation");
    public static final boolean DEBUG_CAPI = Boolean.getBoolean("python.DebugCAPI");

    private static final TruffleLogger LOGGER = PythonLanguage.getLogger(PythonContext.class);

    public final HandleContext nativeContext = new HandleContext();
    private volatile boolean finalizing;

    @TruffleBoundary
    public static String getSupportLibName(PythonOS os, String libName) {
        // note: this should be aligned with MX's "lib" substitution
        return switch (os) {
            case PLATFORM_LINUX, PLATFORM_FREEBSD, PLATFORM_SUNOS -> J_LIB_PREFIX + libName + J_EXT_SO;
            case PLATFORM_DARWIN -> J_LIB_PREFIX + libName + J_EXT_DYLIB;
            case PLATFORM_WIN32 -> libName + J_EXT_DLL;
            default -> libName;
        };
    }

    @TruffleBoundary
    public static String getSupportLibName(String libName) {
        return getSupportLibName(getPythonOS(), libName);
    }

    public static final String J_PYTHON_JNI_LIBRARY_NAME = System.getProperty("python.jni.library", getSupportLibName("pythonjni"));

    /**
     * An enum of events which can currently be traced using python's tracing
     */
    public enum TraceEvent {
        CALL("call"),
        EXCEPTION("exception"),
        LINE("line"),
        RETURN("return"),
        DISABLED("");

        public final TruffleString pythonName;

        TraceEvent(String pythonName) {
            this.pythonName = tsLiteral(pythonName);
        }
    }

    /**
     * An enum of events used by python's source code profiler
     *
     * 'call', 'return', 'c_call', 'c_return', or 'c_exception'
     */
    public enum ProfileEvent {
        CALL("call"),
        C_CALL("c_call"),
        C_EXCEPTION("c_exception"),
        C_RETURN("c_return"),
        RETURN("return");

        public final TruffleString name;

        ProfileEvent(String name) {
            this.name = tsLiteral(name);
        }
    }

    /**
     * A class to store thread-local data mostly like CPython's {@code PyThreadState}.
     */
    public static final class PythonThreadState {
        private boolean shuttingDown = false;

        /*
         * The reference to the last top frame on the Python stack during interop calls. Initially,
         * this is EMPTY representing the top frame.
         */
        PFrame.Reference topframeref = Reference.EMPTY;

        WeakReference<PLock> sentinelLock;

        /* corresponds to 'PyThreadState.curexc_value' */
        private PException currentException;

        /* corresponds to 'PyThreadState.curexc_traceback' */
        private LazyTraceback currentTraceback;

        /* corresponds to 'PyThreadState.exc_info' */
        private PException caughtException = PException.NO_EXCEPTION;

        /* set to emulate Py_ReprEnter/Leave */
        HashSet<Object> reprObjectSet;

        /* corresponds to 'PyThreadState.dict' */
        PDict dict;

        CtypesThreadState ctypes;

        /*
         * This is the native wrapper object if we need to expose the thread state as PyThreadState
         * object. We need to store it here because the wrapper may receive 'toNative' in which case
         * a handle is allocated. In order to avoid leaks, the handle needs to be free'd when the
         * owning thread (or the whole context) is disposed.
         */
        PThreadState nativeWrapper;

        /* The global tracing function, set by sys.settrace and returned by sys.gettrace. */
        Object traceFun;

        /* Keep track of execution to avoid tracing code inside the tracing function. */
        boolean tracing;

        /* Keep track of execution to avoid profiling code inside the profile function. */
        boolean profiling;

        /* The event currently being traced, only useful if tracing is true. */
        TraceEvent tracingWhat;

        /* The global profiling function, set by sys.setprofile and returned by sys.getprofile. */
        Object profileFun;

        /*
         * the current contextvars.Context for the thread.
         */
        PContextVarsContext contextVarsContext;

        /*
         * The current running event loop
         */
        Object runningEventLoop;

        /*
         * A callable that should be called for the first iteration of an async generators.
         */
        Object asyncgenFirstIter;

        /*
         * Counter for C-level recursion depth used for Py_(Enter/Leave)RecursiveCall.
         */
        public int recursionDepth;

        /*
         * The constructor needs to have this particular signature such that we can use it for
         * ContextThreadLocal.
         */
        @SuppressWarnings("unused")
        public PythonThreadState(PythonContext context, Thread owner) {
        }

        void shutdown() {
            shuttingDown = true;
        }

        public Object getRunningEventLoop() {
            return runningEventLoop;
        }

        public void setRunningEventLoop(Object runningEventLoop) {
            this.runningEventLoop = runningEventLoop;
        }

        public boolean isShuttingDown() {
            return shuttingDown;
        }

        @TruffleBoundary
        boolean reprEnter(Object item) {
            if (reprObjectSet == null) {
                reprObjectSet = new HashSet<>();
            }
            return reprObjectSet.add(item);
        }

        @TruffleBoundary
        void reprLeave(Object item) {
            reprObjectSet.remove(item);
        }

        public PException getCurrentException() {
            return currentException;
        }

        public void clearCurrentException() {
            this.currentException = null;
            this.currentTraceback = null;
        }

        public void setCurrentException(PException currentException) {
            this.currentException = currentException;
            if (currentException.getEscapedException() instanceof PBaseException pythonException) {
                this.currentTraceback = pythonException.getTraceback();
            } else {
                Object tb = ExceptionNodes.GetTracebackNode.executeUncached(currentException.getEscapedException());
                this.currentTraceback = tb instanceof PTraceback ptb ? new LazyTraceback(ptb) : null;
            }
        }

        public void setCurrentException(PException currentException, LazyTraceback currentTraceback) {
            this.currentException = currentException;
            this.currentTraceback = currentTraceback;
        }

        public PException reraiseCurrentException() {
            syncTracebackToException();
            PException exception = currentException.getExceptionForReraise(false);
            clearCurrentException();
            throw exception;
        }

        public void syncTracebackToException() {
            if (currentException.getUnreifiedException() instanceof PBaseException pythonException) {
                pythonException.setTraceback(currentTraceback);
            } else {
                PTraceback materialized = currentTraceback != null ? MaterializeLazyTracebackNode.executeUncached(currentTraceback) : null;
                ExceptionNodes.SetTracebackNode.executeUncached(currentException.getUnreifiedException(), materialized != null ? materialized : PNone.NONE);
            }
        }

        public LazyTraceback getCurrentTraceback() {
            return currentTraceback;
        }

        public void setCurrentTraceback(LazyTraceback currentTraceback) {
            this.currentTraceback = currentTraceback;
        }

        public PException getCaughtException() {
            return caughtException;
        }

        public void setCaughtException(PException caughtException) {
            this.caughtException = caughtException;
        }

        public void setTopFrameInfo(PFrame.Reference topframeref) {
            this.topframeref = topframeref;
        }

        public PFrame.Reference popTopFrameInfo() {
            PFrame.Reference ref = topframeref;
            topframeref = null;
            return ref;
        }

        public PFrame.Reference peekTopFrameInfo() {
            return topframeref;
        }

        public PDict getDict() {
            return dict;
        }

        public void setDict(PDict dict) {
            this.dict = dict;
        }

        public CtypesThreadState getCtypes() {
            return ctypes;
        }

        public void setCtypes(CtypesThreadState ctypes) {
            this.ctypes = ctypes;
        }

        public PThreadState getNativeWrapper() {
            return nativeWrapper;
        }

        public void setNativeWrapper(PThreadState nativeWrapper) {
            this.nativeWrapper = nativeWrapper;
        }

        public PContextVarsContext getContextVarsContext() {
            if (contextVarsContext == null) {
                contextVarsContext = PythonObjectFactory.getUncached().createContextVarsContext();
            }
            return contextVarsContext;
        }

        public void setContextVarsContext(PContextVarsContext contextVarsContext) {
            assert contextVarsContext != null;
            this.contextVarsContext = contextVarsContext;
        }

        public void dispose() {
            // This method may be called twice on the same object.

            /*
             * Note: we may only free the native wrappers if they have no PythonObjectReference
             * otherwise it could happen that we free them here and again in
             * 'CApiTransitions.pollReferenceQueue'.
             */
            if (dict != null) {
                PythonAbstractObjectNativeWrapper dictNativeWrapper = dict.getNativeWrapper();
                if (dictNativeWrapper != null && dictNativeWrapper.ref == null) {
                    PyTruffleObjectFree.releaseNativeWrapperUncached(dictNativeWrapper);
                }
            }
            dict = null;
            if (nativeWrapper != null && nativeWrapper.ref == null) {
                PyTruffleObjectFree.releaseNativeWrapperUncached(nativeWrapper);
                nativeWrapper = null;
            }
        }

        public Object getTraceFun() {
            return traceFun;
        }

        public void setTraceFun(Object traceFun, PythonLanguage language) {
            if (this.traceFun != traceFun) {
                language.noTracingOrProfilingAssumption.invalidate();
                this.traceFun = traceFun;
            }
        }

        public boolean isTracing() {
            return tracing;
        }

        public void tracingStart(TraceEvent newTracingWhat) {
            assert !this.tracing : "Attempt made to trace a call while inside a trace function. Did you forget to check isTracing before calling invokeTraceFunction?";
            this.tracing = true;
            setTracingWhat(newTracingWhat);
        }

        public void tracingStop() {
            this.tracing = false;
        }

        public TraceEvent getTracingWhat() {
            return tracingWhat;
        }

        public void setTracingWhat(TraceEvent tracingWhat) {
            this.tracingWhat = tracingWhat;
        }

        public void setProfileFun(Object profileFun, PythonLanguage language) {
            if (this.profileFun != profileFun) {
                language.noTracingOrProfilingAssumption.invalidate();
                this.profileFun = profileFun;
            }
        }

        public Object getProfileFun() {
            return profileFun;
        }

        public boolean isProfiling() {
            return profiling;
        }

        public void profilingStart() {
            assert !this.profiling : "Attempt made to trace a call while inside a profile function. Did you forget to check isProfiling before calling invokeTraceFunction?";
            this.profiling = true;
        }

        public void profilingStop() {
            this.profiling = false;
        }

        public Object getAsyncgenFirstIter() {
            return asyncgenFirstIter;
        }

        public void setAsyncgenFirstIter(Object asyncgenFirstIter) {
            this.asyncgenFirstIter = asyncgenFirstIter;
        }
    }

    private static final class AtExitHook {
        final Object callable;
        final Object[] arguments;
        final PKeyword[] keywords;
        final CallTarget ct;

        AtExitHook(Object callable, Object[] arguments, PKeyword[] keywords, CallTarget ct) {
            this.callable = callable;
            this.arguments = arguments;
            this.keywords = keywords;
            this.ct = ct;
        }
    }

    @GenerateUncached
    @GenerateInline(inlineByDefault = true)
    public abstract static class GetThreadStateNode extends Node {

        public abstract PythonThreadState execute(Node inliningTarget, PythonContext context);

        public final PythonThreadState execute(Node inliningTarget) {
            return execute(inliningTarget, null);
        }

        public final PythonThreadState executeCached(PythonContext context) {
            return execute(this, context);
        }

        public final PythonThreadState executeCached() {
            return executeCached(null);
        }

        public final void setTopFrameInfoCached(PythonContext context, PFrame.Reference topframeref) {
            executeCached(context).topframeref = topframeref;
        }

        public final void clearTopFrameInfoCached(PythonContext context) {
            executeCached(context).topframeref = null;
        }

        @Specialization(guards = {"noContext == null", "!curThreadState.isShuttingDown()"})
        @SuppressWarnings("unused")
        static PythonThreadState doNoShutdown(Node inliningTarget, PythonContext noContext,
                        @Bind("getThreadState(inliningTarget)") PythonThreadState curThreadState) {
            return curThreadState;
        }

        @Specialization(guards = {"noContext == null"}, replaces = "doNoShutdown")
        @InliningCutoff
        PythonThreadState doGeneric(@SuppressWarnings("unused") Node inliningTarget, PythonContext noContext) {
            PythonThreadState curThreadState = PythonLanguage.get(inliningTarget).getThreadStateLocal().get();
            if (curThreadState.isShuttingDown()) {
                PythonContext.get(this).killThread();
            }
            return curThreadState;
        }

        @Specialization(guards = "!curThreadState.isShuttingDown()")
        @SuppressWarnings("unused")
        static PythonThreadState doNoShutdownWithContext(Node inliningTarget, PythonContext context,
                        @Bind("getThreadState(inliningTarget)") PythonThreadState curThreadState) {
            return curThreadState;
        }

        @Specialization(replaces = "doNoShutdownWithContext")
        @InliningCutoff
        PythonThreadState doGenericWithContext(Node inliningTarget, PythonContext context) {
            PythonThreadState curThreadState = PythonLanguage.get(inliningTarget).getThreadStateLocal().get(context.env.getContext());
            if (CompilerDirectives.injectBranchProbability(CompilerDirectives.SLOWPATH_PROBABILITY, curThreadState.isShuttingDown())) {
                context.killThread();
            }
            return curThreadState;
        }

        @NonIdempotent
        PythonThreadState getThreadState(Node n) {
            return PythonLanguage.get(n).getThreadStateLocal().get();
        }
    }

    private static final TruffleString T_PREFIX = T_SLASH;
    private static final TruffleString T_LIB_PYTHON_3 = tsLiteral("/lib/python" + PythonLanguage.MAJOR + "." + PythonLanguage.MINOR);
    private static final TruffleString T_LIB_GRAALPYTHON = tsLiteral("/lib/graalpy" + PythonLanguage.GRAALVM_MAJOR + "." + PythonLanguage.GRAALVM_MINOR);
    private static final TruffleString T_STD_LIB_PLACEHOLDER = tsLiteral("!stdLibHome!");
    private static final String J_NO_CORE_FATAL = "could not determine Graal.Python's core path - you must pass --python.CoreHome.";
    private static final String J_NO_PREFIX_WARNING = "could not determine Graal.Python's sys prefix path - you may need to pass --python.SysPrefix.";
    private static final String J_NO_CORE_WARNING = "could not determine Graal.Python's core path - you may need to pass --python.CoreHome.";
    private static final String J_NO_STDLIB = "could not determine Graal.Python's standard library path. You need to pass --python.StdLibHome if you want to use the standard library.";
    private static final String J_NO_CAPI = "could not determine Graal.Python's C API library path. You need to pass --python.CAPI if you want to use the C extension modules.";
    private static final String J_NO_JNI = "could not determine Graal.Python's JNI library. You need to pass --python.JNILibrary if you want to run, for example, binary HPy extension modules.";

    private PythonModule mainModule;
    private final List<ShutdownHook> shutdownHooks = new ArrayList<>();
    private final List<AtExitHook> atExitHooks = new ArrayList<>();
    private final List<Runnable> capiHooks = new ArrayList<>();
    private final HashMap<PythonNativeClass, CyclicAssumption> nativeClassStableAssumptions = new HashMap<>();
    private final ThreadGroup threadGroup = new ThreadGroup(GRAALPYTHON_THREADS);
    private final IDUtils idUtils = new IDUtils();

    @CompilationFinal private SecureRandom secureRandom;

    // Equivalent of _Py_HashSecret
    @CompilationFinal(dimensions = 1) private byte[] hashSecret = new byte[24];

    // ctypes' used native libraries/functions.
    private final ConcurrentHashMap<Long, Object> ptrAdrMap = new ConcurrentHashMap<>();

    @CompilationFinal private PosixSupport posixSupport;
    @CompilationFinal private NFIZlibSupport nativeZlib;
    @CompilationFinal private NFIBz2Support nativeBz2lib;
    @CompilationFinal private NFILZMASupport nativeLZMA;

    // if set to 0 the VM will set it to whatever it likes
    private final AtomicLong pythonThreadStackSize = new AtomicLong(0);

    @CompilationFinal private TruffleLanguage.Env env;

    /* map of thread IDs to the corresponding 'threadStates' */
    private final Map<Thread, PythonThreadState> threadStateMapping = Collections.synchronizedMap(new WeakHashMap<>());
    private WeakReference<Thread> mainThread;

    private final ReentrantLock importLock = new ReentrantLock();
    @CompilationFinal private boolean isInitialized = false;

    private OutputStream out;
    private OutputStream err;
    private InputStream in;
    @CompilationFinal private CApiContext cApiContext;
    @CompilationFinal private GraalHPyContext hPyContext;

    private TruffleString soABI; // cache for soAPI

    private static final Assumption singleNativeContext = Truffle.getRuntime().createAssumption("single native context assumption");

    private static final class GlobalInterpreterLock extends ReentrantLock {
        private static final long serialVersionUID = 1L;

        public GlobalInterpreterLock() {
            super(true);
        }

        @Override
        public Thread getOwner() {
            return super.getOwner();
        }
    }

    private final GlobalInterpreterLock globalInterpreterLock = new GlobalInterpreterLock();

    /*
     * Used to avoid triggering more async handlers from an async handler. We run those only on the
     * main thread, so it doesn't have to be thread-local.
     */
    private final AtomicBoolean inAsyncHandler = new AtomicBoolean(false);

    /** Native wrappers for context-insensitive singletons like {@link PNone#NONE}. */
    @CompilationFinal(dimensions = 1) private final PythonAbstractObjectNativeWrapper[] singletonNativePtrs = new PythonAbstractObjectNativeWrapper[PythonLanguage.getNumberOfSpecialSingletons()];

    // The context-local resources
    private final AsyncHandler handler;
    private final AsyncHandler.SharedFinalizer sharedFinalizer;

    // decides if we run the async weakref callbacks and destructors
    private boolean gcEnabled = true;

    // A thread-local to store the full path to the currently active import statement, for Jython
    // compat
    private final ThreadLocal<ArrayDeque<TruffleString>> currentImport = new ThreadLocal<>();

    /** State for the locale module, the default locale can be passed as an option */
    private PythonLocale currentLocale;

    @CompilationFinal(dimensions = 1) private Object[] optionValues;
    private final AllocationReporter allocationReporter;

    /*
     * These maps are used to ensure that each "deserialization" of code in the parser gets a
     * different instance (inside one context - ASTs can still be shared between contexts).
     * Deserializing the same code multiple times is an infrequent case, but Python assumes that
     * these code instances don't share attributes like the associated filename.
     *
     * Each time a specific filename is passed to deserialization in the same context, it gets a new
     * id. The filename is stored in a weak hash map, because the code itself is a
     * context-independent object.
     */
    private final WeakHashMap<CallTarget, TruffleString> codeFilename = new WeakHashMap<>();

    /*
     * These maps are used to ensure that each "deserialization" of code in the parser gets a
     * different instance (inside one context - ASTs can still be shared between contexts).
     * Deserializing the same code multiple times is an infrequent case, but Python assumes that
     * these code instances don't share attributes like the associated filename.
     *
     * Each time a specific filename is passed to deserialization in the same context, it gets a new
     * id. The filename is stored in a weak hash map, because the code itself is a
     * context-independent object.
     */
    private final WeakHashMap<CodeUnit, TruffleString> codeUnitFilename = new WeakHashMap<>();

    private final ConcurrentHashMap<TruffleString, AtomicLong> deserializationId = new ConcurrentHashMap<>();

    private final long perfCounterStart = ImageInfo.inImageBuildtimeCode() ? 0 : System.nanoTime();

    public static final String CHILD_CONTEXT_DATA = "childContextData";
    @CompilationFinal private List<Integer> childContextFDs;
    private final ChildContextData childContextData;
    private final SharedMultiprocessingData sharedMultiprocessingData;

    private boolean codecsInitialized;
    private final List<Object> codecSearchPath = new ArrayList<>();
    private final Map<TruffleString, PTuple> codecSearchCache = new HashMap<>();
    private final Map<TruffleString, Object> codecErrorRegistry = new HashMap<>();

    private int intMaxStrDigits;
    private int minIntBitLengthOverLimit;
    private static final double LOG2_10 = Math.log(10) / Math.log(2);

    // Used by CPython tests to selectively enable or disable frozen modules.
    private TriState overrideFrozenModules = TriState.UNDEFINED;

    // the full module name for package imports
    private TruffleString pyPackageContext;

    // the actual pointer will be set when the cext is initialized
    private final PythonNativePointer nativeNull = new PythonNativePointer(null);

    public RootCallTarget signatureContainer;

    public TruffleString getPyPackageContext() {
        return pyPackageContext;
    }

    public void setPyPackageContext(TruffleString pyPackageContext) {
        this.pyPackageContext = pyPackageContext;
    }

    public List<Object> getCodecSearchPath() {
        return codecSearchPath;
    }

    public boolean isCodecsInitialized() {
        return codecsInitialized;
    }

    public void markCodecsInitialized() {
        this.codecsInitialized = true;
    }

    public Map<TruffleString, PTuple> getCodecSearchCache() {
        return codecSearchCache;
    }

    public Map<TruffleString, Object> getCodecErrorRegistry() {
        return codecErrorRegistry;
    }

    public int getIntMaxStrDigits() {
        return intMaxStrDigits;
    }

    /**
     * Returns cached bit length of a smallest positive number that is over the intMaxStrDigits
     * limit
     */
    public int getMinIntBitLengthOverLimit() {
        return minIntBitLengthOverLimit;
    }

    public void setIntMaxStrDigits(int intMaxStrDigits) {
        this.intMaxStrDigits = intMaxStrDigits;
        this.minIntBitLengthOverLimit = computeMinIntBitLengthOverLimit(intMaxStrDigits);
    }

    public TriState getOverrideFrozenModules() {
        return overrideFrozenModules;
    }

    public void setOverrideFrozenModules(TriState overrideFrozenModules) {
        this.overrideFrozenModules = overrideFrozenModules;
    }

    @TruffleBoundary
    private static int computeMinIntBitLengthOverLimit(int limit) {
        /*
         * Let L be the limit. Smallest positive number over the limit is 10 ^ L. Its bit length is
         * floor(log2(10 ^ L)) + 1. That is equivalent to floor(L * log2(10)) + 1;
         */
        double bitLength = Math.floor(limit * LOG2_10) + 1;
        if (MathGuards.fitInt(bitLength)) {
            return (int) bitLength;
        } else {
            // The number wouldn't be representable as BigInteger, so there's no practical limit
            return Integer.MAX_VALUE;
        }
    }

    public boolean tryEnterAsyncHandler() {
        return inAsyncHandler.compareAndSet(false, true);
    }

    public void leaveAsyncHandler() {
        inAsyncHandler.set(false);
    }

    public static final class ChildContextData {
        private int exitCode = 0;
        private boolean signaled;
        private final PythonContext parentCtx;
        private TruffleWeakReference<TruffleContext> ctx;

        private final AtomicBoolean exiting = new AtomicBoolean(false);
        private final CountDownLatch running = new CountDownLatch(1);

        public ChildContextData(PythonContext parentCtx) {
            this.parentCtx = parentCtx;
        }

        public void setExitCode(int exitCode) {
            this.exitCode = exitCode;
        }

        public int getExitCode() {
            return this.exitCode;
        }

        public void setSignaled(int signaledCode) {
            this.signaled = true;
            this.exitCode = signaledCode;
        }

        public boolean wasSignaled() {
            return this.signaled;
        }

        private void setTruffleContext(TruffleContext ctx) {
            assert this.ctx == null;
            assert ctx != null;
            this.ctx = new TruffleWeakReference<>(ctx);
        }

        public TruffleContext getTruffleContext() {
            return ctx.get();
        }

        public void awaitRunning() throws InterruptedException {
            running.await();
        }

        public boolean compareAndSetExiting(boolean expect, boolean update) {
            return exiting.compareAndSet(expect, update);
        }
    }

    public static final class SharedMultiprocessingData {

        /**
         * A sentinel object that remains in the {@link LinkedBlockingQueue} in the
         * {@link #pipeData}. It is pushed there in #close so that any blocking #take calls can wake
         * up and react to the end of the stream.
         */
        private static final Object SENTINEL = new Object();

        private final AtomicInteger fdCounter = new AtomicInteger(0);

        /**
         * Maps the two fake file descriptors created in {@link #pipe()} to one
         * {@link LinkedBlockingQueue}
         */
        private final ConcurrentSkipListMap<Integer, LinkedBlockingQueue<Object>> pipeData = new ConcurrentSkipListMap<>();

        /**
         * Holds ref count of file descriptors which were passed over to a spawned child context.
         * This can be either:<br>
         * <ul>
         * <li>fake file descriptors created via {@link #pipe()}</li>
         * <li>real file descriptors coming from the posix implementation</li>
         * </ul>
         */
        private final ConcurrentHashMap<Integer, Integer> fdRefCount = new ConcurrentHashMap<>();

        public SharedMultiprocessingData(ConcurrentHashMap<TruffleString, Semaphore> namedSemaphores) {
            this.namedSemaphores = namedSemaphores;
        }

        /**
         * Increases reference count for the given file descriptor.
         */
        @TruffleBoundary
        private void incrementFDRefCount(int fd) {
            fdRefCount.compute(fd, (f, count) -> (count == null) ? 1 : count + 1);
        }

        /**
         * Decreases reference count for the given file descriptor.
         *
         * @return {@code true} if ref count was decreased, {@code false} if ref count isn't tracked
         *         anymore.
         */
        @TruffleBoundary
        public boolean decrementFDRefCount(int fd) {
            Integer cnt = fdRefCount.computeIfPresent(fd, (f, count) -> {
                if (count == 0 || count == Integer.MIN_VALUE) {
                    return Integer.MIN_VALUE;
                } else {
                    assert count > 0;
                    return count - 1;
                }
            });
            return cnt != null && !fdRefCount.remove(fd, Integer.MIN_VALUE);
        }

        /**
         * @return fake (negative) fd values to avoid clash with real file descriptors and to detect
         *         potential usage by other python builtins
         */
        @TruffleBoundary
        public int[] pipe() {
            LinkedBlockingQueue<Object> q = new LinkedBlockingQueue<>();
            int writeFD = fdCounter.addAndGet(-2);
            assert isWriteFD(writeFD);
            int readFD = getPairFd(writeFD);
            pipeData.put(readFD, q);
            pipeData.put(writeFD, q);
            return new int[]{readFD, writeFD};
        }

        /**
         * Adding pipe data needs no special synchronization, since we guarantee there is only ever
         * one or no queue registered for a given fd.
         */
        @TruffleBoundary
        public void addPipeData(int fd, byte[] bytes, Runnable noFDHandler, Runnable brokenPipeHandler) {
            assert isWriteFD(fd);
            LinkedBlockingQueue<Object> q = pipeData.get(fd);
            if (q == null) {
                // the write end is already closed
                noFDHandler.run();
                throw CompilerDirectives.shouldNotReachHere();
            }
            int fd2 = getPairFd(fd);
            if (isClosed(fd2)) {
                // the read end is already closed
                brokenPipeHandler.run();
                throw CompilerDirectives.shouldNotReachHere();
            }
            q.add(bytes);
        }

        /**
         * Closing the read end of a pipe just removes the mapping from that fd to the queue.
         * Closing the write end adds the {@link #SENTINEL} value as the last value. There is a
         * potential race here for incorrect code that concurrently writes to the write end via
         * {@link #addPipeData}, in that the sentinel may prevent writes from being visible.
         */
        @TruffleBoundary
        public void closePipe(int fd) {
            LinkedBlockingQueue<Object> q = pipeData.remove(fd);
            if (q != null && isWriteFD(fd)) {
                q.offer(SENTINEL);
            }
        }

        /**
         * This needs no additional synchronization, since if the write-end of the pipe is already
         * closed, the {@link #take} call will return appropriately.
         */
        @TruffleBoundary
        public Object takePipeData(Node node, int fd, Runnable noFDHandler) {
            LinkedBlockingQueue<Object> q = pipeData.get(fd);
            if (q == null) {
                noFDHandler.run();
                throw CompilerDirectives.shouldNotReachHere();
            }
            Object[] o = new Object[]{PNone.NONE};
            TruffleSafepoint.setBlockedThreadInterruptible(node, (lbq) -> {
                o[0] = take(lbq);
            }, q);
            return o[0];
        }

        /**
         * This uses {@link ConcurrentSkipListMap#compute} to determine the blocking state. The
         * runnable may be run multiple times, so we need to check and write all possible results to
         * the result array. This ensures that if there is concurrent modification of the
         * {@link #pipeData}, we will get a valid result.
         */
        @TruffleBoundary
        public boolean isBlocking(int fd) {
            boolean[] result = new boolean[]{false};
            pipeData.compute(fd, (f, q) -> {
                if (q == null) {
                    result[0] = false;
                } else {
                    int fd2 = getPairFd(fd);
                    if (isClosed(fd2)) {
                        result[0] = false;
                    } else {
                        // this uses q.isEmpty() instead of our isEmpty(q), because we are not
                        // interested in the race between closing fd2 and this runnable. If the
                        // SENTINEL is pushed in the meantime, we should return false, just as if
                        // we had observed fd2 to be closed already.
                        result[0] = q.isEmpty();
                    }
                }
                return q;
            });
            return result[0];
        }

        private static int getPairFd(int fd) {
            return isWriteFD(fd) ? fd + 1 : fd - 1;
        }

        private static boolean isWriteFD(int fd) {
            return fd % 2 == 0;
        }

        private static Object take(LinkedBlockingQueue<Object> q) throws InterruptedException {
            Object v = q.take();
            if (v == SENTINEL) {
                q.offer(SENTINEL);
                return PythonUtils.EMPTY_BYTE_ARRAY;
            } else {
                return v;
            }
        }

        private boolean isClosed(int fd) {
            // since there is no way that any thread can be trying to read/write to this pipe FD
            // legally before it was added to pipeData in #pipe above, we don't need to
            // synchronize. If the FD is taken, and it's not in pipe data, this is a race in the
            // program, because some thread is just arbitrarily probing FDs.
            return fd >= fdCounter.get() && pipeData.get(fd) == null;
        }

        /**
         * @see PythonLanguage#namedSemaphores
         */
        private final ConcurrentHashMap<TruffleString, Semaphore> namedSemaphores;

        @TruffleBoundary
        public void putNamedSemaphore(TruffleString name, Semaphore sem) {
            namedSemaphores.put(name, sem);
        }

        @TruffleBoundary
        public Semaphore getNamedSemaphore(TruffleString name) {
            return namedSemaphores.get(name);
        }

        @TruffleBoundary
        public Semaphore removeNamedSemaphore(TruffleString name) {
            return namedSemaphores.remove(name);
        }

        private final ConcurrentHashMap<Long, Thread> childContextThreads = new ConcurrentHashMap<>();

        /**
         * {@code ChildContextData} outlives its own context, because the parent needs to be able to
         * access the exit code even after the child context was closed and thread disposed. We
         * dispose the mapping to {@code ChildContextData} when the Python code (our internal Python
         * code) asks for the exit code for the first time after the child exited.
         */
        private final ConcurrentHashMap<Long, ChildContextData> childContextData = new ConcurrentHashMap<>();

        @TruffleBoundary
        public Thread getChildContextThread(long tid) {
            return childContextThreads.get(tid);
        }

        @TruffleBoundary
        public void putChildContextThread(long id, Thread thread) {
            childContextThreads.put(id, thread);
        }

        @TruffleBoundary
        public void removeChildContextThread(long id) {
            childContextThreads.remove(id);
        }

        @TruffleBoundary
        public ChildContextData getChildContextData(long tid) {
            return childContextData.get(tid);
        }

        @TruffleBoundary
        public void removeChildContextData(long tid) {
            childContextData.remove(tid);
        }

        @TruffleBoundary
        public void putChildContextData(long id, ChildContextData data) {
            childContextData.put(id, data);
        }
    }

    public PythonContext(PythonLanguage language, TruffleLanguage.Env env) {
        super(language, env.isNativeAccessAllowed(), env.isSocketIOAllowed());
        this.env = env;
        this.allocationReporter = env.lookup(AllocationReporter.class);
        this.childContextData = (ChildContextData) env.getConfig().get(CHILD_CONTEXT_DATA);
        this.sharedMultiprocessingData = this.childContextData == null ? new SharedMultiprocessingData(language.namedSemaphores) : childContextData.parentCtx.sharedMultiprocessingData;
        this.handler = new AsyncHandler(this);
        this.sharedFinalizer = new AsyncHandler.SharedFinalizer(this);
        this.optionValues = PythonOptions.createOptionValuesStorage(env);
        this.in = env.in();
        this.out = env.out();
        this.err = env.err();
    }

    private static final ContextReference<PythonContext> REFERENCE = ContextReference.create(PythonLanguage.class);

    public static PythonContext get(Node node) {
        return REFERENCE.get(node);
    }

    public PythonNativePointer getNativeNull() {
        return nativeNull;
    }

    public AllocationReporter getAllocationReporter() {
        return allocationReporter;
    }

    public boolean isChildContext() {
        return childContextData != null;
    }

    public ChildContextData getChildContextData() {
        return childContextData;
    }

    public SharedMultiprocessingData getSharedMultiprocessingData() {
        return sharedMultiprocessingData;
    }

    public long spawnTruffleContext(int fd, int sentinel, int[] fdsToKeep) {
        ChildContextData data = new ChildContextData(isChildContext() ? childContextData.parentCtx : this);
        Builder builder = data.parentCtx.env.newInnerContextBuilder().//
                        forceSharing(getOption(PythonOptions.ForceSharingForInnerContexts)).//
                        inheritAllAccess(true).//
                        initializeCreatorContext(true).//
                        option("python.NativeModules", "false").//
                        // TODO always force java posix in spawned: test_multiprocessing_spawn fails
                        // with that. Gives "OSError: [Errno 9] Bad file number"
                        // option("python.PosixModuleBackend", "java").//
                        config(PythonContext.CHILD_CONTEXT_DATA, data);
        Thread thread = data.parentCtx.env.createThread(new ChildContextThread(fd, sentinel, data, builder));
        long tid = PThread.getThreadId(thread);
        getSharedMultiprocessingData().putChildContextThread(tid, thread);
        getSharedMultiprocessingData().putChildContextData(tid, data);
        for (int fdToKeep : fdsToKeep) {
            // prevent file descriptors from being closed when passed to another "process",
            // equivalent to fds_to_keep arg in posix fork_exec
            getSharedMultiprocessingData().incrementFDRefCount(fdToKeep);
        }
        start(thread);
        return tid;
    }

    @TruffleBoundary
    private static void start(Thread thread) {
        thread.start();
    }

    public synchronized List<Integer> getChildContextFDs() {
        if (childContextFDs == null) {
            childContextFDs = new ArrayList<>();
        }
        return childContextFDs;
    }

    private static class ChildContextThread implements Runnable {
        private static final TruffleLogger MULTIPROCESSING_LOGGER = PythonLanguage.getLogger(ChildContextThread.class);
        private static final Source MULTIPROCESSING_SOURCE = Source.newBuilder(PythonLanguage.ID,
                        "from multiprocessing.popen_truffleprocess import spawn_truffleprocess; spawn_truffleprocess(fd, sentinel)",
                        "<spawned-child-context>").internal(true).build();

        private final int fd;
        private final ChildContextData data;
        private final Builder builder;
        private final int sentinel;

        public ChildContextThread(int fd, int sentinel, ChildContextData data, Builder builder) {
            this.fd = fd;
            this.data = data;
            this.builder = builder;
            this.sentinel = sentinel;
        }

        @Override
        public void run() {
            try {
                MULTIPROCESSING_LOGGER.fine("starting spawned child context");
                TruffleContext ctx = builder.build();
                data.setTruffleContext(ctx);
                Object parent = ctx.enter(null);
                CallTarget ct = PythonContext.get(null).getEnv().parsePublic(MULTIPROCESSING_SOURCE, "fd", "sentinel");
                try {
                    data.running.countDown();
                    Object res = ct.call(fd, sentinel);
                    int exitCode = CastToJavaIntLossyNode.executeUncached(res);
                    data.setExitCode(exitCode);
                } finally {
                    ctx.leave(null, parent);
                    if (data.compareAndSetExiting(false, true)) {
                        try {
                            ctx.close();
                            MULTIPROCESSING_LOGGER.log(Level.FINE, "closed spawned child context");
                        } catch (Throwable t) {
                            MULTIPROCESSING_LOGGER.log(Level.FINE, "exception while closing spawned child context", t);
                        }
                    }
                    data.parentCtx.sharedMultiprocessingData.closePipe(sentinel);
                }
            } catch (ThreadDeath td) {
                // as a result of of TruffleContext.closeCancelled()
                throw td;
            }
        }
    }

    public ThreadGroup getThreadGroup() {
        return threadGroup;
    }

    @TruffleBoundary(allowInlining = true)
    public long getPythonThreadStackSize() {
        return pythonThreadStackSize.get();
    }

    public long getAndSetPythonsThreadStackSize(long value) {
        return pythonThreadStackSize.getAndSet(value);
    }

    public long getNextObjectId() {
        return idUtils.getNextObjectId();
    }

    public long getNextObjectId(Object object) {
        return idUtils.getNextObjectId(object);
    }

    public long getNextStringId(TruffleString string) {
        return idUtils.getNextStringId(string);
    }

    public <T> T getOption(OptionKey<T> key) {
        assert !PythonOptions.isEngineOption(key) : "Querying engine option via context.";
        if (CompilerDirectives.inInterpreter()) {
            return getEnv().getOptions().get(key);
        } else {
            return PythonOptions.getOptionUnrolling(this.optionValues, PythonOptions.getOptionKeys(), key);
        }
    }

    public ReentrantLock getImportLock() {
        return importLock;
    }

    public PFunction importFunc() {
        return getImportFunc();
    }

    public PosixSupport getPosixSupport() {
        return posixSupport;
    }

    public boolean isNativeAccessAllowed() {
        return env.isNativeAccessAllowed();
    }

    public NFIZlibSupport getNFIZlibSupport() {
        return nativeZlib;
    }

    public NFIBz2Support getNFIBz2Support() {
        return nativeBz2lib;
    }

    public NFILZMASupport getNFILZMASupport() {
        return nativeLZMA;
    }

    public ConcurrentHashMap<Long, Object> getCtypesAdrMap() {
        return ptrAdrMap;
    }

    public TruffleLanguage.Env getEnv() {
        return env;
    }

    public void setEnv(TruffleLanguage.Env newEnv) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        env = newEnv;
        in = env.in();
        out = env.out();
        err = env.err();
        posixSupport.setEnv(env);
        optionValues = PythonOptions.createOptionValuesStorage(newEnv);
    }

    /**
     * Just for testing
     */
    public void setOut(OutputStream out) {
        this.out = out;
    }

    /**
     * Just for testing
     */
    public void setErr(OutputStream err) {
        this.err = err;
    }

    public PythonModule getMainModule() {
        return mainModule;
    }

    public Python3Core getCore() {
        return this;
    }

    public InputStream getStandardIn() {
        return in;
    }

    public OutputStream getStandardErr() {
        return err;
    }

    public OutputStream getStandardOut() {
        return out;
    }

    public PFrame.Reference peekTopFrameInfo(PythonLanguage lang) {
        return getThreadState(lang).topframeref;
    }

    @TruffleBoundary
    public boolean reprEnter(Object item) {
        return getThreadState(getLanguage()).reprEnter(item);
    }

    @TruffleBoundary
    public void reprLeave(Object item) {
        getThreadState(getLanguage()).reprLeave(item);
    }

    public long getPerfCounterStart() {
        return perfCounterStart;
    }

    /**
     * Get a SecureRandom instance using a non-blocking source.
     */
    public SecureRandom getSecureRandom() {
        assert !ImageInfo.inImageBuildtimeCode();
        if (secureRandom == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            try {
                secureRandom = SecureRandom.getInstance("NativePRNGNonBlocking");
            } catch (NoSuchAlgorithmException e) {
                if (getPythonOS() == PLATFORM_WIN32) {
                    try {
                        secureRandom = SecureRandom.getInstanceStrong();
                    } catch (NoSuchAlgorithmException e2) {
                        throw new RuntimeException("Unable to obtain entropy source for random number generation (NativePRNGNonBlocking)", e2);
                    }
                } else {
                    throw new RuntimeException("Unable to obtain entropy source for random number generation (NativePRNGNonBlocking)", e);
                }
            }
        }
        return secureRandom;
    }

    public byte[] getHashSecret() {
        assert !ImageInfo.inImageBuildtimeCode();
        return hashSecret;
    }

    public void setCurrentLocale(PythonLocale locale) {
        CompilerAsserts.neverPartOfCompilation();
        if (getOption(PythonOptions.RunViaLauncher)) {
            locale.setAsJavaDefault();
        }
        currentLocale = locale;
    }

    public PythonLocale getCurrentLocale() {
        return currentLocale;
    }

    public boolean isInitialized() {
        return isInitialized;
    }

    public void initialize() {
        try {
            acquireGil();
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
        try {
            mainThread = new WeakReference<>(Thread.currentThread());
            initializePosixSupport();
            initialize(this);
            setupRuntimeInformation(false);
            postInitialize();
            if (!ImageInfo.inImageBuildtimeCode()) {
                importSiteIfForced();
            } else if (posixSupport instanceof ImageBuildtimePosixSupport) {
                ((ImageBuildtimePosixSupport) posixSupport).checkLeakingResources();
            }
        } finally {
            if (ImageInfo.inImageBuildtimeCode()) {
                mainThread = null;
            }
            releaseGil();
        }
    }

    public void patch(Env newEnv) {
        try {
            acquireGil();
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
        try {
            mainThread = new WeakReference<>(Thread.currentThread());
            setEnv(newEnv);
            setupRuntimeInformation(true);
            postInitialize();
            importSiteIfForced();
        } finally {
            releaseGil();
        }
    }

    private void importSiteIfForced() {
        if (getOption(PythonOptions.ForceImportSite)) {
            AbstractImportNode.importModule(T_SITE);
        }
        if (!getOption(PythonOptions.WarnOptions).isEmpty()) {
            // we must force an import of the warnings module here if warnings were passed
            AbstractImportNode.importModule(T_WARNINGS);
        }
        if (getOption(PythonOptions.InputFilePath).isEmpty()) {
            // When InputFilePath is set, this is handled by __graalpython__.run_path
            addSysPath0();
        }
        if (getOption(PythonOptions.SetupLLVMLibraryPaths)) {
            ImpModuleBuiltins.importFrozenModuleObject(this, toTruffleStringUncached("graalpy.sulong_support"), false);
        }
    }

    public void addSysPath0() {
        if (!getOption(PythonOptions.SafePathFlag)) {
            TruffleString path0 = computeSysPath0();
            if (path0 != null) {
                PythonModule sys = lookupBuiltinModule(T_SYS);
                Object path = sys.getAttribute(T_PATH);
                PyObjectCallMethodObjArgs.executeUncached(path, T_INSERT, 0, path0);
            }
        }
    }

    // Equivalent of pathconfig.c:_PyPathConfig_ComputeSysPath0
    private TruffleString computeSysPath0() {
        String[] args = env.getApplicationArguments();
        if (args.length == 0) {
            return null;
        }
        String argv0 = args[0];
        if (argv0.isEmpty()) {
            return T_EMPTY_STRING;
        } else if (argv0.equals("-m")) {
            try {
                return toTruffleStringUncached(env.getCurrentWorkingDirectory().getPath());
            } catch (SecurityException e) {
                return null;
            }
        } else if (!argv0.equals("-c")) {
            TruffleFile scriptFile = env.getPublicTruffleFile(argv0);
            TruffleFile parent;
            try {
                parent = scriptFile.getCanonicalFile().getParent();
            } catch (SecurityException | IOException e) {
                parent = scriptFile.getParent();
            }
            if (parent != null) {
                return toTruffleStringUncached(parent.getPath());
            }
        }
        return T_EMPTY_STRING;
    }

    /**
     * During pre-initialization, we're also loading code from the Python standard library. Since
     * some of those modules may be packages, they will have their __path__ attribute set to the
     * absolute path of the package on the build system. We use this function to patch the paths
     * during build time and after starting up from a pre-initialized context so they point to the
     * run-time package paths.
     */
    private void patchPackagePaths(TruffleString from, TruffleString to) {
        HashingStorage modulesStorage = getSysModules().getDictStorage();
        HashingStorageIterator it = HashingStorageGetIterator.executeUncached(modulesStorage);
        while (HashingStorageIteratorNext.executeUncached(modulesStorage, it)) {
            Object v = HashingStorageIteratorValue.executeUncached(modulesStorage, it);
            if (v instanceof PythonModule) {
                // Update module.__path__
                Object path = ((PythonModule) v).getAttribute(SpecialAttributeNames.T___PATH__);
                if (path instanceof PList) {
                    Object[] paths = SequenceStorageNodes.CopyInternalArrayNode.executeUncached(((PList) path).getSequenceStorage());
                    for (int i = 0; i < paths.length; i++) {
                        Object pathElement = paths[i];
                        TruffleString strPath;
                        if (pathElement instanceof PString) {
                            strPath = ((PString) pathElement).getValueUncached();
                        } else if (isJavaString(pathElement)) {
                            strPath = toTruffleStringUncached((String) pathElement);
                        } else if (pathElement instanceof TruffleString) {
                            strPath = (TruffleString) pathElement;
                        } else {
                            continue;
                        }
                        if (strPath.regionEqualsUncached(0, from, 0, from.codePointLengthUncached(TS_ENCODING), TS_ENCODING)) {
                            paths[i] = StringReplaceNode.getUncached().execute(strPath, from, to, -1);
                        }
                    }
                    ((PythonModule) v).setAttribute(SpecialAttributeNames.T___PATH__, factory().createList(paths));
                }

                // Update module.__file__
                Object file = ((PythonModule) v).getAttribute(T___FILE__);
                if (file instanceof PString) {
                    file = ((PString) file).getValueUncached();
                }
                if (file instanceof TruffleString) {
                    TruffleString strFile = (TruffleString) file;
                    ((PythonModule) v).setAttribute(T___FILE__, StringReplaceNode.getUncached().execute(strFile, from, to, -1));
                }
                if (isJavaString(file)) {
                    TruffleString strFile = toTruffleStringUncached((String) file);
                    ((PythonModule) v).setAttribute(T___FILE__, StringReplaceNode.getUncached().execute(strFile, from, to, -1));
                }
            }
        }
    }

    private void initializeLocale() {
        setCurrentLocale(PythonLocale.initializeFromTruffleEnv(env));
    }

    private void setupRuntimeInformation(boolean isPatching) {
        if (!ImageInfo.inImageBuildtimeCode()) {
            initializeHashSecret();
        }
        initializeLocale();
        setIntMaxStrDigits(getOption(PythonOptions.IntMaxStrDigits));
        if (!PythonOptions.WITHOUT_COMPRESSION_LIBRARIES) {
            nativeZlib = NFIZlibSupport.createNative(this, "");
            nativeBz2lib = NFIBz2Support.createNative(this, "");
            nativeLZMA = NFILZMASupport.createNative(this, "");
        }

        mainModule = factory().createPythonModule(T___MAIN__);
        mainModule.setAttribute(T___BUILTINS__, getBuiltins());
        mainModule.setAttribute(T___ANNOTATIONS__, factory().createDict());
        SetDictNode.executeUncached(mainModule, factory().createDictFixedStorage(mainModule));
        getSysModules().setItem(T___MAIN__, mainModule);

        if (ImageInfo.inImageBuildtimeCode()) {
            // Patch any pre-loaded packages' paths if we're running
            // pre-initialization
            patchPackagePaths(getStdlibHome(), T_STD_LIB_PLACEHOLDER);
        } else if (isPatching && ImageInfo.inImageRuntimeCode()) {
            // Patch any pre-loaded packages' paths to the new stdlib home if
            // we're patching a pre-initialized context
            patchPackagePaths(T_STD_LIB_PLACEHOLDER, getStdlibHome());
        }

        applyToAllThreadStates(ts -> ts.clearCurrentException());
        isInitialized = true;
    }

    private void initializeHashSecret() {
        assert !ImageInfo.inImageBuildtimeCode();
        Optional<Integer> hashSeed = getOption(PythonOptions.HashSeed);
        if (hashSeed.isPresent()) {
            int hashSeedValue = hashSeed.get();
            // 0 disables the option, leaving the secret at 0
            if (hashSeedValue != 0) {
                // Generate the whole secret from the seed number the same way as CPython
                // Taken from bootstrap_hash.c:lcg_urandom
                // hashSeedValue was parsed as unsigned integer
                int x = hashSeedValue;
                for (int i = 0; i < hashSecret.length; i++) {
                    x *= 214013;
                    x += 2531011;
                    /* modulo 2 ^ (8 * sizeof(int)) */
                    hashSecret[i] = (byte) ((x >>> 16) & 0xff);
                }
            }
        } else {
            // Generate random seed
            getSecureRandom().nextBytes(hashSecret);
        }
    }

    public void applyModuleOptions() {
        assert !isInitialized : "cannot apply module options after initialization";
        TruffleString sha3Backend = getLanguage().getEngineOption(PythonOptions.Sha3ModuleBackend);
        TruffleString.EqualNode eqNode = TruffleString.EqualNode.getUncached();
        if (!eqNode.execute(T_JAVA, sha3Backend, TS_ENCODING)) {
            removeBuiltinModule(T_SHA3);
        }
    }

    private void initializePosixSupport() {
        TruffleString option = getLanguage().getEngineOption(PythonOptions.PosixModuleBackend);
        PosixSupport result;
        // The resources field will be removed once all posix builtins go through PosixSupport
        TruffleString.EqualNode eqNode = TruffleString.EqualNode.getUncached();
        boolean selectedJavaBackend = eqNode.execute(T_JAVA, option, TS_ENCODING);
        if (PythonOptions.WITHOUT_NATIVE_POSIX || selectedJavaBackend) {
            if (!selectedJavaBackend) {
                writeWarning("Native Posix backend selected, but it was excluded from the runtime, " +
                                "switching to Java backend.");
            }
            result = new EmulatedPosixSupport(this);
        } else if (eqNode.execute(T_NATIVE, option, TS_ENCODING) || eqNode.execute(T_LLVM_LANGUAGE, option, TS_ENCODING)) {
            if (ImageInfo.inImageBuildtimeCode()) {
                EmulatedPosixSupport emulatedPosixSupport = new EmulatedPosixSupport(this);
                NFIPosixSupport nativePosixSupport = new NFIPosixSupport(this, option);
                result = new ImageBuildtimePosixSupport(nativePosixSupport, emulatedPosixSupport);
            } else if (ImageInfo.inImageRuntimeCode()) {
                NFIPosixSupport nativePosixSupport = new NFIPosixSupport(this, option);
                result = new ImageBuildtimePosixSupport(nativePosixSupport, null);
            } else {
                if (!getOption(PythonOptions.RunViaLauncher)) {
                    writeWarning("Native Posix backend is not fully supported when embedding. For example, standard I/O always uses file " +
                                    "descriptors 0, 1 and 2 regardless of stream redirection specified in Truffle environment");
                }
                result = new NFIPosixSupport(this, option);
            }
        } else {
            throw new IllegalStateException(String.format("Wrong value for the PosixModuleBackend option: '%s'", option));
        }
        if (LoggingPosixSupport.isEnabled()) {
            posixSupport = new LoggingPosixSupport(result);
        } else {
            posixSupport = result;
        }
    }

    private TruffleString langHome, sysPrefix, basePrefix, coreHome, capiHome, jniHome, stdLibHome;

    public void initializeHomeAndPrefixPaths(Env newEnv, String languageHome) {
        if (ImageInfo.inImageBuildtimeCode()) {
            // at buildtime we do not need these paths to be valid, since all boot files are frozen
            basePrefix = sysPrefix = langHome = coreHome = stdLibHome = capiHome = jniHome = T_DOT;
            return;
        }

        String pythonHome = newEnv.getOptions().get(PythonOptions.PythonHome);
        if (pythonHome.isEmpty()) {
            try {
                pythonHome = System.getenv("GRAAL_PYTHONHOME");
            } catch (SecurityException e) {
            }
        }

        final TruffleFile home;
        if (languageHome != null && pythonHome == null) {
            home = newEnv.getInternalTruffleFile(languageHome);
        } else if (pythonHome != null) {
            boolean envHomeIsDirectory = false;
            TruffleFile envHomeFile = null;
            try {
                envHomeFile = newEnv.getInternalTruffleFile(pythonHome);
                envHomeIsDirectory = envHomeFile.isDirectory();
            } catch (SecurityException e) {
            }
            home = envHomeIsDirectory ? envHomeFile : null;
        } else {
            home = null;
        }

        Supplier<?>[] homeCandidates = new Supplier<?>[]{
                        () -> home,
                        () -> {
                            try {
                                TruffleFile internalResource = newEnv.getInternalResource("python-home");
                                return internalResource == null ? null : internalResource.getAbsoluteFile();
                            } catch (IOException e) {
                                // fall through
                            }
                            return null;
                        }
        };
        for (Supplier<?> homeCandidateSupplier : homeCandidates) {
            sysPrefix = newEnv.getOptions().get(PythonOptions.SysPrefix);
            basePrefix = newEnv.getOptions().get(PythonOptions.SysBasePrefix);
            coreHome = newEnv.getOptions().get(PythonOptions.CoreHome);
            stdLibHome = newEnv.getOptions().get(PythonOptions.StdLibHome);
            capiHome = newEnv.getOptions().get(PythonOptions.CAPI);
            jniHome = newEnv.getOptions().get(PythonOptions.JNIHome);
            final TruffleFile homeCandidate = (TruffleFile) homeCandidateSupplier.get();
            if (homeCandidate == null) {
                continue;
            }
            boolean homeSeemsValid = !coreHome.isEmpty() && !stdLibHome.isEmpty();

            Python3Core.writeInfo(() -> MessageFormat.format("Initial locations:" +
                            "\n\tLanguage home: {0}" +
                            "\n\tSysPrefix: {1}" +
                            "\n\tBaseSysPrefix: {2}" +
                            "\n\tCoreHome: {3}" +
                            "\n\tStdLibHome: {4}" +
                            "\n\tCAPI: {5}" +
                            "\n\tJNI library: {6}" +
                            "\n\tHome candidate: {7}", languageHome, sysPrefix, basePrefix, coreHome, stdLibHome, capiHome, jniHome, homeCandidate.toString()));

            langHome = toTruffleStringUncached(homeCandidate.toString());
            if (sysPrefix.isEmpty()) {
                sysPrefix = toTruffleStringUncached(homeCandidate.getAbsoluteFile().getPath());
            }

            if (basePrefix.isEmpty()) {
                basePrefix = toTruffleStringUncached(homeCandidate.getAbsoluteFile().getPath());
            }

            if (coreHome.isEmpty()) {
                try {
                    outer: for (TruffleFile f : homeCandidate.list()) {
                        if (f.getName().equals("lib-graalpython") && f.isDirectory()) {
                            coreHome = toTruffleStringUncached(f.getPath());
                            homeSeemsValid = true;
                            break;
                        } else if (f.getName().equals("lib") && f.isDirectory()) {
                            for (TruffleFile f2 : f.list()) {
                                if (f2.getName().equals("graalpy" + PythonLanguage.GRAALVM_MAJOR + "." + PythonLanguage.GRAALVM_MINOR) && f.isDirectory()) {
                                    coreHome = toTruffleStringUncached(f2.getPath());
                                    homeSeemsValid = true;
                                    break outer;
                                }
                            }
                        }
                    }
                } catch (SecurityException | IOException e) {
                }
            }

            if (stdLibHome.isEmpty()) {
                // try stdlib layouts per sysconfig or our sources
                try {
                    outer: for (TruffleFile f : homeCandidate.list()) {
                        if (getPythonOS() == PLATFORM_WIN32 && (f.getName().equals("Lib") || f.getName().equals("lib")) && f.isDirectory()) {
                            // nt stdlib layout
                            stdLibHome = toTruffleStringUncached(f.getPath());
                        } else if (f.getName().equals("lib") && f.isDirectory()) {
                            // posix stdlib layout
                            for (TruffleFile f2 : f.list()) {
                                if (f2.getName().equals("python" + PythonLanguage.MAJOR + "." + PythonLanguage.MINOR) && f.isDirectory()) {
                                    stdLibHome = toTruffleStringUncached(f2.getPath());
                                    homeSeemsValid = true;
                                    break outer;
                                }
                            }
                        } else if (f.getName().equals("lib-python") && f.isDirectory()) {
                            // source stdlib layout
                            for (TruffleFile f2 : f.list()) {
                                if (f2.getName().equals("3") && f.isDirectory()) {
                                    stdLibHome = toTruffleStringUncached(f2.getPath());
                                    homeSeemsValid = true;
                                    break outer;
                                }
                            }
                        }
                    }
                } catch (SecurityException | IOException e) {
                }
            }

            if (capiHome.isEmpty()) {
                capiHome = coreHome;
            }

            if (jniHome.isEmpty()) {
                jniHome = coreHome;
            }

            if (homeSeemsValid) {
                break;
            }
        }

        Python3Core.writeInfo(() -> MessageFormat.format("Updated locations:" +
                        "\n\tLanguage home: {0}" +
                        "\n\tSysPrefix: {1}" +
                        "\n\tBaseSysPrefix: {2}" +
                        "\n\tCoreHome: {3}" +
                        "\n\tStdLibHome: {4}" +
                        "\n\tExecutable: {5}" +
                        "\n\tCAPI: {6}" +
                        "\n\tJNI library: {7}", langHome, sysPrefix, basePrefix, coreHome, stdLibHome, newEnv.getOptions().get(PythonOptions.Executable), capiHome,
                        jniHome));
    }

    @TruffleBoundary
    public TruffleString getLanguageHome() {
        if (langHome == null || langHome.isEmpty()) {
            langHome = T_PREFIX;
        }
        return langHome;
    }

    @TruffleBoundary
    public TruffleString getSysPrefix() {
        if (sysPrefix.isEmpty()) {
            writeWarning(J_NO_PREFIX_WARNING);
            sysPrefix = T_PREFIX;
        }
        return sysPrefix;
    }

    @TruffleBoundary
    public TruffleString getSysBasePrefix() {
        if (basePrefix.isEmpty()) {
            basePrefix = getLanguageHome();
        }
        return basePrefix;
    }

    @TruffleBoundary
    public TruffleString getCoreHome() {
        if (coreHome.isEmpty()) {
            writeWarning(J_NO_CORE_WARNING);
            coreHome = T_LIB_GRAALPYTHON;
        }
        return coreHome;
    }

    @TruffleBoundary
    public TruffleString getStdlibHome() {
        if (stdLibHome.isEmpty()) {
            writeWarning(J_NO_STDLIB);
            stdLibHome = T_LIB_PYTHON_3;
        }
        return stdLibHome;
    }

    @TruffleBoundary
    public TruffleString getCoreHomeOrFail() {
        if (coreHome.isEmpty()) {
            throw new RuntimeException(J_NO_CORE_FATAL);
        }
        return coreHome;
    }

    @TruffleBoundary
    public TruffleString getCAPIHome() {
        if (capiHome.isEmpty()) {
            writeWarning(J_NO_CAPI);
            return coreHome;
        }
        return capiHome;
    }

    @TruffleBoundary
    public TruffleString getJNIHome() {
        if (jniHome.isEmpty()) {
            writeWarning(J_NO_JNI);
            return jniHome;
        }
        return jniHome;
    }

    private static void writeWarning(String warning) {
        LOGGER.warning(warning);
    }

    @TruffleBoundary
    public void registerAtexitHook(ShutdownHook shutdownHook) {
        shutdownHooks.add(shutdownHook);
    }

    @TruffleBoundary
    public void registerAtexitHook(Object callable, Object[] arguments, PKeyword[] keywords, CallTarget ct) {
        atExitHooks.add(new AtExitHook(callable, arguments, keywords, ct));
    }

    @TruffleBoundary
    public void unregisterAtexitHook(Object callable) {
        atExitHooks.removeIf(hook -> hook.callable == callable);
    }

    @TruffleBoundary
    public void clearAtexitHooks() {
        atExitHooks.clear();
    }

    public void registerCApiHook(Runnable hook) {
        if (hasCApiContext()) {
            hook.run();
        } else {
            capiHooks.add(hook);
        }
    }

    @TruffleBoundary
    @SuppressWarnings("try")
    public void finalizeContext() {
        boolean cancelling = env.getContext().isCancelling();
        try (GilNode.UncachedAcquire gil = GilNode.uncachedAcquire()) {
            if (!cancelling) {
                // this uses the threading module and runs python code to join the threads
                shutdownThreads();
                // run any user code that's registered to shut down now
                runShutdownHooks();
            }
            // shut down async actions threads
            handler.shutdown();
            finalizing = true;
            // interrupt and join or kill python threads
            joinThreads();
            if (!cancelling) {
                // this cleanup calls into Sulong
                cleanupCApiResources();
            }
            // destroy thread state data, if anything is still running, it will crash now
            disposeThreadStates();
        }
        cleanupHPyResources();
        for (int fd : getChildContextFDs()) {
            if (!getSharedMultiprocessingData().decrementFDRefCount(fd)) {
                getSharedMultiprocessingData().closePipe(fd);
            }
        }
        mainThread = null;
    }

    @TruffleBoundary
    public int getAtexitHookCount() {
        return atExitHooks.size();
    }

    @TruffleBoundary
    public void runAtexitHooks() {
        // run atExitHooks in reverse order they were registered
        PException lastException = null;
        for (int i = atExitHooks.size() - 1; i >= 0; i--) {
            AtExitHook hook = atExitHooks.get(i);
            try {
                hook.ct.call(hook.callable, hook.arguments, hook.keywords);
            } catch (PException e) {
                lastException = e;
            }
        }
        atExitHooks.clear();
        if (lastException != null) {
            throw lastException;
        }
    }

    @TruffleBoundary
    public void runShutdownHooks() {
        try {
            runAtexitHooks();
        } catch (PException e) {
            // It was printed already, so just discard
        }
        for (ShutdownHook h : shutdownHooks) {
            h.call(this);
        }
    }

    /**
     * Release all resources held by the thread states. This function needs to run as long as the
     * context is still valid because it may call into LLVM to release handles.
     */
    @TruffleBoundary
    private void disposeThreadStates() {
        for (PythonThreadState ts : threadStateMapping.values()) {
            ts.dispose();
        }
        threadStateMapping.clear();
    }

    /**
     * Release all native wrappers of singletons. This function needs to run as long as the context
     * is still valid because it may call into LLVM to release handles.
     */
    @TruffleBoundary
    private void cleanupCApiResources() {
        for (PythonNativeWrapper singletonNativeWrapper : singletonNativePtrs) {
            /*
             * Note: we may only free the native wrappers if they have no PythonObjectReference
             * otherwise it could happen that we free them here and again in
             * 'CApiTransitions.pollReferenceQueue'.
             */
            if (singletonNativeWrapper != null && singletonNativeWrapper.ref == null) {
                PyTruffleObjectFree.releaseNativeWrapperUncached(singletonNativeWrapper);
            }
        }
        CApiTransitions.deallocateNativeWeakRefs(this);
    }

    private void cleanupHPyResources() {
        if (hPyContext != null) {
            hPyContext.finalizeContext();
        }
    }

    /**
     * This method is the equivalent to {@code pylifecycle.c: wait_for_thread_shutdown} and calls
     * {@code threading._shutdown} (if the threading module was loaded) to tear down all threads
     * created through this module. This operation must be done before flag {@link #finalizing} is
     * set to {@code true} otherwise the threads will immediately die and won't properly release
     * locks. For reference, see also {@code pylifecycle.c: Py_FinalizeEx}.
     */
    @TruffleBoundary
    private void shutdownThreads() {
        LOGGER.fine("shutting down threads");
        PDict importedModules = getSysModules();
        HashingStorage dictStorage = importedModules.getDictStorage();
        Object value = HashingStorageGetItem.executeUncached(dictStorage, T_THREADING);
        if (value != null) {
            Object attrShutdown = ReadAttributeFromObjectNode.getUncached().execute(value, SpecialMethodNames.T_SHUTDOWN);
            if (attrShutdown == PNone.NO_VALUE) {
                LOGGER.fine("threading module has no member " + SpecialMethodNames.T_SHUTDOWN);
                return;
            }
            try {
                CallNode.getUncached().execute(null, attrShutdown);
            } catch (Exception | StackOverflowError e) {
                try {
                    boolean exitException = InteropLibrary.getUncached().isException(e) && InteropLibrary.getUncached().getExceptionType(e) == ExceptionType.EXIT;
                    if (!exitException) {
                        ExceptionUtils.printPythonLikeStackTrace(e);
                        if (PythonOptions.isWithJavaStacktrace(getLanguage())) {
                            e.printStackTrace(new PrintWriter(getStandardErr()));
                        }
                    }
                } catch (UnsupportedMessageException unsupportedMessageException) {
                    throw CompilerDirectives.shouldNotReachHere();
                }
                throw e;
            }
        } else {
            // threading was not imported; this is
            LOGGER.finest("threading module was not imported");
        }
        LOGGER.fine("successfully shut down all threads");
    }

    /**
     * This method joins all threads created by this context after the GIL was released. This is
     * required by Truffle.
     */
    @SuppressWarnings("deprecation")
    private void joinThreads() {
        LOGGER.fine("joining threads");
        try {
            // make a copy of the threads, because the threads will disappear one by one from the
            // threadStateMapping as we're joining them, which gives undefined results for the
            // iterator over keySet
            LinkedList<Thread> threads = new LinkedList<>(threadStateMapping.keySet());
            boolean runViaLauncher = getOption(PythonOptions.RunViaLauncher);
            for (Thread thread : threads) {
                if (thread != Thread.currentThread()) {
                    // cannot interrupt ourselves, we're holding the GIL
                    LOGGER.finest("joining thread " + thread);
                    // the threads remaining here are either daemon threads or embedder threads that
                    // evaluated some Python code, all others were shut down via the threading
                    // module above. So we just interrupt them. Their exit is handled in the
                    // acquireGil function, which will be interrupted for these threads as long as
                    // they are still running some GraalPython code, if they are embedder threads
                    // that are not running GraalPython code anymore, they will just never receive
                    // PythonThreadKillException and continue as if nothing happened.
                    disposeThread(thread);
                    boolean isOurThread = runViaLauncher || thread.getThreadGroup() == threadGroup;
                    // Do not try so hard when running in embedded mode and the thread may not be
                    // running any GraalPython code anymore
                    int tries = isOurThread ? 100 : 5;
                    for (int i = 0; i < tries && thread.isAlive(); i++) {
                        thread.join(tries - i);
                        if (!thread.isAlive()) {
                            break;
                        }
                        LOGGER.fine("Trying to join " + thread.getName() + " failed after " + (tries - i) + "ms.");
                        if (isOurThread) {
                            thread.interrupt();
                            thread.join(tries - i);
                            if (!thread.isAlive()) {
                                break;
                            }
                            LOGGER.fine("Trying to interrupt our " + thread.getName() + " failed after " + (tries - i) + "ms.");
                        }
                        env.submitThreadLocal(new Thread[]{thread}, new ThreadLocalAction(true, false) {
                            @Override
                            protected void perform(ThreadLocalAction.Access access) {
                                throw new PythonThreadKillException();
                            }
                        });
                    }
                    if (isOurThread) {
                        if (thread.isAlive()) {
                            LOGGER.warning("Could not stop thread " + thread.getName());
                        }
                    }
                }
            }
        } catch (InterruptedException e) {
            LOGGER.finest("got interrupt while joining threads");
            Thread.currentThread().interrupt();
        }
    }

    public void initializeMainModule(TruffleString path) {
        if (path != null) {
            mainModule.setAttribute(T___FILE__, path);
        }
    }

    public static Assumption getSingleNativeContextAssumption() {
        return singleNativeContext;
    }

    public boolean isExecutableAccessAllowed() {
        return getEnv().isHostLookupAllowed() || isNativeAccessAllowed();
    }

    /**
     * Trigger any pending asynchronous actions
     */
    public static final void triggerAsyncActions(Node node) {
        TruffleSafepoint.poll(node);
    }

    public AsyncHandler getAsyncHandler() {
        return handler;
    }

    /**
     * Register an action for regular execution. Refer to {@link AsyncHandler#registerAction} for
     * details.
     */
    public void registerAsyncAction(Supplier<AsyncAction> actionSupplier) {
        handler.registerAction(actionSupplier);
    }

    /**
     * Poll async actions in case they are not set to run automatically.
     *
     * @see PythonOptions#AUTOMATIC_ASYNC_ACTIONS
     */
    public void pollAsyncActions() {
        handler.poll();
    }

    @TruffleBoundary
    public CyclicAssumption getNativeClassStableAssumption(PythonNativeClass cls, boolean createOnDemand) {
        CyclicAssumption assumption = nativeClassStableAssumptions.get(cls);
        if (assumption == null && createOnDemand) {
            assumption = new CyclicAssumption("Native class " + cls + " stable");
            nativeClassStableAssumptions.put(cls, assumption);
        }
        return assumption;
    }

    public void setSingletonNativeWrapper(PythonAbstractObject obj, PythonAbstractObjectNativeWrapper nativePtr) {
        assert PythonLanguage.getSingletonNativeWrapperIdx(obj) != -1 : "invalid special singleton object";
        assert singletonNativePtrs[PythonLanguage.getSingletonNativeWrapperIdx(obj)] == null;
        // Other threads must see the nativeWrapper fully initialized once it becomes non-null
        VarHandle.storeStoreFence();
        singletonNativePtrs[PythonLanguage.getSingletonNativeWrapperIdx(obj)] = nativePtr;
    }

    public PythonAbstractObjectNativeWrapper getSingletonNativeWrapper(PythonAbstractObject obj) {
        int singletonNativePtrIdx = PythonLanguage.getSingletonNativeWrapperIdx(obj);
        if (singletonNativePtrIdx != -1) {
            return singletonNativePtrs[singletonNativePtrIdx];
        }
        return null;
    }

    /**
     * This method is intended to be called to re-acquire the GIL after a {@link StackOverflowError}
     * was catched. To reduce the probability that re-acquiring the GIL causes again a
     * {@link StackOverflowError}, it is important to keep this method as simple as possible. In
     * particular, do not add calls if there is a way to avoid it.
     */
    public void reacquireGilAfterStackOverflow() {
        while (!ownsGil()) {
            try {
                acquireGil();
            } catch (InterruptedException ignored) {
                // just keep trying
            }
        }
    }

    /**
     * Should not be called directly.
     *
     * @see GilNode
     */
    public boolean ownsGil() {
        return globalInterpreterLock.isHeldByCurrentThread();
    }

    /**
     * Should not be used outside of {@link AsyncHandler}
     */
    Thread getGilOwner() {
        return globalInterpreterLock.getOwner();
    }

    /**
     * Should not be used outside of {@link AsyncHandler}
     */
    boolean gilHasQueuedThreads() {
        return globalInterpreterLock.hasQueuedThreads();
    }

    /**
     * Should not be called directly.
     *
     * @see GilNode
     */
    @TruffleBoundary
    boolean tryAcquireGil() {
        try {
            // Using tryLock with empty timeout to ensure fairness
            return globalInterpreterLock.tryLock(0, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Should not be called directly.
     *
     * @see GilNode
     */
    @TruffleBoundary
    void acquireGil() throws InterruptedException {
        assert !ownsGil() : dumpStackOnAssertionHelper("trying to acquire the GIL more than once");
        boolean wasInterrupted = Thread.interrupted();
        globalInterpreterLock.lockInterruptibly();
        if (wasInterrupted) {
            Thread.currentThread().interrupt();
        }
    }

    static final String dumpStackOnAssertionHelper(String msg) {
        Thread.dumpStack();
        return msg;
    }

    /**
     * Should not be called directly.
     *
     * @see GilNode
     */
    @TruffleBoundary
    void releaseGil() {
        assert globalInterpreterLock.getHoldCount() == 1 : dumpStackOnAssertionHelper("trying to release the GIL with invalid hold count " + globalInterpreterLock.getHoldCount());
        globalInterpreterLock.unlock();
    }

    /**
     * This is like {@code Env#getPublicTruffleFile(String)} but also allows access to files in the
     * language home directory matching one of the given file extensions. This is mostly useful to
     * access files of the {@code stdlib}, {@code core} or similar.
     */
    @TruffleBoundary
    public TruffleFile getPublicTruffleFileRelaxed(TruffleString path, TruffleString... allowedSuffixes) {
        TruffleFile f = env.getInternalTruffleFile(path.toJavaStringUncached());
        // 'isDirectory' does deliberately not follow symlinks because otherwise this could allow to
        // escape the language home directory.
        // Also, during image build time, we allow full internal access.
        if (ImageInfo.inImageBuildtimeCode() || isPyFileInLanguageHome(f) && (f.isDirectory(LinkOption.NOFOLLOW_LINKS) || hasAllowedSuffix(path, allowedSuffixes))) {
            return f;
        } else {
            return env.getPublicTruffleFile(path.toJavaStringUncached());
        }
    }

    @TruffleBoundary(allowInlining = true)
    private static boolean hasAllowedSuffix(TruffleString path, TruffleString[] allowedSuffixes) {
        int pathLen = path.codePointLengthUncached(TS_ENCODING);
        for (TruffleString suffix : allowedSuffixes) {
            int suffixLen = suffix.codePointLengthUncached(TS_ENCODING);
            if (suffixLen > pathLen) {
                continue;
            }
            if (path.regionEqualsUncached(pathLen - suffixLen, suffix, 0, suffixLen, TS_ENCODING)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tests if the given {@code TruffleFile} is located in the language home directory.
     */
    @TruffleBoundary
    public boolean isPyFileInLanguageHome(TruffleFile path) {
        assert !ImageInfo.inImageBuildtimeCode() : "language home won't be available during image build time";
        // The language home may be 'null' if an embedder uses Python. In this case, IO must just be
        // allowed.
        if (langHome != null) {
            // This deliberately uses 'getAbsoluteFile' and not 'getCanonicalFile' because if, e.g.,
            // 'path' is a symlink outside of the language home, the user should not be able to read
            // the symlink if 'allowIO' is false.
            TruffleFile coreHomePath = getEnv().getInternalTruffleFile(langHome.toJavaStringUncached()).getAbsoluteFile();
            TruffleFile absolutePath = path.getAbsoluteFile();
            return absolutePath.startsWith(coreHomePath);
        }
        LOGGER.log(Level.FINE, () -> "Cannot access file " + path + " because there is no language home.");
        return false;
    }

    @TruffleBoundary
    public TruffleString getCurrentImport() {
        ArrayDeque<TruffleString> ci = currentImport.get();
        if (ci == null || ci.isEmpty()) {
            return T_EMPTY_STRING;
        } else {
            return ci.peek();
        }
    }

    @TruffleBoundary
    public void pushCurrentImport(TruffleString object) {
        ArrayDeque<TruffleString> ci = currentImport.get();
        if (ci == null) {
            ci = new ArrayDeque<>();
            currentImport.set(ci);
        }
        ci.push(object);
    }

    @TruffleBoundary
    public void popCurrentImport() {
        assert currentImport.get() != null && currentImport.get().peek() != null : "invalid popCurrentImport without push";
        currentImport.get().pop();
    }

    public Thread[] getThreads() {
        CompilerAsserts.neverPartOfCompilation();
        return threadStateMapping.keySet().toArray(new Thread[0]);
    }

    public PythonThreadState getThreadState(PythonLanguage lang) {
        PythonThreadState curThreadState = lang.getThreadStateLocal().get();
        if (CompilerDirectives.injectBranchProbability(CompilerDirectives.SLOWPATH_PROBABILITY, curThreadState.isShuttingDown())) {
            killThread();
        }
        return curThreadState;
    }

    private void killThread() {
        // we're shutting down, just release and die
        CompilerDirectives.transferToInterpreter();
        if (ownsGil()) {
            releaseGil();
        }
        throw new PythonThreadKillException();
    }

    private void applyToAllThreadStates(Consumer<PythonThreadState> action) {
        if (getLanguage().singleThreadedAssumption.isValid()) {
            action.accept(getLanguage().getThreadStateLocal().get());
        } else {
            synchronized (this) {
                for (PythonThreadState ts : threadStateMapping.values()) {
                    action.accept(ts);
                }
            }
        }
    }

    @TruffleBoundary
    public void setSentinelLockWeakref(WeakReference<PLock> sentinelLock) {
        getThreadState(getLanguage()).sentinelLock = sentinelLock;
    }

    @TruffleBoundary
    public void initializeMultiThreading() {
        handler.activateGIL();
    }

    public synchronized void attachThread(Thread thread, ContextThreadLocal<PythonThreadState> threadState) {
        CompilerAsserts.neverPartOfCompilation();
        threadStateMapping.put(thread, threadState.get(thread));
    }

    public synchronized void disposeThread(Thread thread) {
        CompilerAsserts.neverPartOfCompilation();
        // check if there is a live sentinel lock
        PythonThreadState ts = threadStateMapping.get(thread);
        if (ts == null) {
            // ts already removed, that is valid during context shutdown for daemon threads
            return;
        }
        ts.shutdown();
        threadStateMapping.remove(thread);
        ts.dispose();
        releaseSentinelLock(ts.sentinelLock);
        getSharedMultiprocessingData().removeChildContextThread(PThread.getThreadId(thread));
    }

    private static void releaseSentinelLock(WeakReference<PLock> sentinelLockWeakref) {
        if (sentinelLockWeakref != null) {
            PLock sentinelLock = sentinelLockWeakref.get();
            if (sentinelLock != null) {
                // release the sentinel lock
                sentinelLock.release();
            }
        }
    }

    public boolean hasCApiContext() {
        return cApiContext != null;
    }

    public CApiContext getCApiContext() {
        return cApiContext;
    }

    public void setCApiContext(CApiContext capiContext) {
        assert this.cApiContext == null : "tried to create new C API context but it was already created";
        this.cApiContext = capiContext;
    }

    public void runCApiHooks() {
        for (Runnable capiHook : capiHooks) {
            capiHook.run();
        }
        capiHooks.clear();
    }

    public boolean hasHPyContext() {
        return hPyContext != null;
    }

    public synchronized GraalHPyContext createHPyContext(Object hpyLibrary) throws ApiInitException {
        assert hPyContext == null : "tried to create new HPy context but it was already created";
        GraalHPyContext hpyContext = new GraalHPyContext(this, hpyLibrary);
        this.hPyContext = hpyContext;
        return hpyContext;
    }

    public GraalHPyContext getHPyContext() {
        assert hPyContext != null : "tried to get HPy context but was not created yet";
        return hPyContext;
    }

    /**
     * Equivalent of {@code debug_ctx.c: hpy_debug_init_ctx}.
     */
    @TruffleBoundary
    private Object initDebugMode() {
        if (!hasHPyContext()) {
            throw CompilerDirectives.shouldNotReachHere("cannot initialize HPy debug context without HPy universal context");
        }
        // TODO: call 'hpy_debug_init_ctx'
        throw CompilerDirectives.shouldNotReachHere("not yet implemented");
    }

    public boolean isGcEnabled() {
        return gcEnabled;
    }

    public void setGcEnabled(boolean flag) {
        gcEnabled = flag;
    }

    public AsyncHandler.SharedFinalizer getSharedFinalizer() {
        return sharedFinalizer;
    }

    public boolean isFinalizing() {
        return finalizing;
    }

    public void setCodeFilename(CallTarget callTarget, TruffleString filename) {
        codeFilename.put(callTarget, filename);
    }

    public TruffleString getCodeFilename(CallTarget callTarget) {
        return codeFilename.get(callTarget);
    }

    public void setCodeUnitFilename(CodeUnit co, TruffleString filename) {
        codeUnitFilename.put(co, filename);
    }

    public TruffleString getCodeUnitFilename(CodeUnit co) {
        return codeUnitFilename.get(co);
    }

    public long getDeserializationId(TruffleString fileName) {
        return deserializationId.computeIfAbsent(fileName, f -> new AtomicLong()).incrementAndGet();
    }

    public void ensureLLVMLanguage(Node nodeForRaise) {
        if (!env.getInternalLanguages().containsKey(J_LLVM_LANGUAGE)) {
            throw PRaiseNode.raiseUncached(nodeForRaise, PythonBuiltinClassType.SystemError, ErrorMessages.LLVM_NOT_AVAILABLE);
        }
    }

    public void ensureNFILanguage(Node nodeForRaise, String optionName, String optionValue) {
        if (!env.getInternalLanguages().containsKey(J_NFI_LANGUAGE)) {
            throw PRaiseNode.raiseUncached(nodeForRaise, PythonBuiltinClassType.SystemError, ErrorMessages.NFI_NOT_AVAILABLE, optionName, optionValue);
        }
    }

    @TruffleBoundary
    public String getLLVMSupportExt(String libName) {
        if (!getOption(PythonOptions.NativeModules)) {
            ensureLLVMLanguage(null);
            LanguageInfo llvmInfo = env.getInternalLanguages().get(J_LLVM_LANGUAGE);
            Toolchain toolchain = env.lookup(llvmInfo, Toolchain.class);
            String toolchainIdentifier = toolchain.getIdentifier();
            if (!J_NATIVE.equals(toolchainIdentifier)) {
                // if not native, we always assume a Linux-like system
                return PythonContext.getSupportLibName(PythonOS.PLATFORM_LINUX, libName + '-' + toolchainIdentifier);
            }
        }
        return PythonContext.getSupportLibName(libName + '-' + J_NATIVE);
    }

    @TruffleBoundary
    public TruffleString getSoAbi() {
        if (soABI == null) {
            PythonModule sysModule = this.lookupBuiltinModule(T_SYS);
            Object implementationObj = ReadAttributeFromObjectNode.getUncached().execute(sysModule, T_IMPLEMENTATION);
            // sys.implementation.cache_tag
            TruffleString cacheTag = (TruffleString) PInteropGetAttributeNode.executeUncached(implementationObj, T_CACHE_TAG);
            // sys.implementation._multiarch
            TruffleString multiArch = (TruffleString) PInteropGetAttributeNode.executeUncached(implementationObj, T__MULTIARCH);

            TruffleString toolchainId = getPlatformId();

            // only use '.pyd' if we are on 'Win32-native'
            TruffleString soExt;
            if (getPythonOS() == PLATFORM_DARWIN && T_NATIVE.equalsUncached(toolchainId, TS_ENCODING)) {
                // not ".dylib", similar to CPython:
                // https://github.com/python/cpython/issues/37510
                soExt = T_EXT_SO;
            } else if (getPythonOS() == PLATFORM_WIN32 && T_NATIVE.equalsUncached(toolchainId, TS_ENCODING)) {
                soExt = T_EXT_PYD;
            } else {
                soExt = T_EXT_SO;
            }

            soABI = cat(T_DOT, cacheTag, T_DASH, toolchainId, T_DASH, multiArch, soExt);
        }
        return soABI;
    }

    public TruffleString getPlatformId() {
        if (!getOption(PythonOptions.NativeModules)) {
            ensureLLVMLanguage(null);
            LanguageInfo llvmInfo = env.getInternalLanguages().get(J_LLVM_LANGUAGE);
            Toolchain toolchain = env.lookup(llvmInfo, Toolchain.class);
            return toTruffleStringUncached(toolchain.getIdentifier());
        } else {
            return T_NATIVE;
        }
    }

    public Thread getMainThread() {
        if (mainThread != null) {
            return mainThread.get();
        }
        return null;
    }

    private int dlopenFlags = PosixConstants.RTLD_NOW.value;

    public int getDlopenFlags() {
        return dlopenFlags;
    }

    public void setDlopenFlags(int dlopenFlags) {
        this.dlopenFlags = dlopenFlags;
    }

    private static final class UnsafeWrapper {
        private static final Unsafe UNSAFE = initUnsafe();

        private static Unsafe initUnsafe() {
            try {
                return Unsafe.getUnsafe();
            } catch (SecurityException e) {
            }
            try {
                Field theUnsafeInstance = Unsafe.class.getDeclaredField("theUnsafe");
                theUnsafeInstance.setAccessible(true);
                return (Unsafe) theUnsafeInstance.get(Unsafe.class);
            } catch (Exception e) {
                throw new RuntimeException("exception while trying to get Unsafe.theUnsafe via reflection:", e);
            }
        }
    }

    public Unsafe getUnsafe() {
        if (isNativeAccessAllowed()) {
            return UnsafeWrapper.UNSAFE;
        }
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new RuntimeException("Native access not allowed, cannot manipulate native memory");
    }

    public long allocateNativeMemory(long size) {
        return allocateNativeMemoryBoundary(getUnsafe(), size);
    }

    @TruffleBoundary
    private static long allocateNativeMemoryBoundary(Unsafe unsafe, long size) {
        return unsafe.allocateMemory(size);
    }

    public void freeNativeMemory(long address) {
        freeNativeMemoryBoundary(getUnsafe(), address);
    }

    @TruffleBoundary
    private static void freeNativeMemoryBoundary(Unsafe unsafe, long address) {
        unsafe.freeMemory(address);
    }

    public void copyNativeMemory(long dst, byte[] src, int srcOffset, int size) {
        copyNativeMemoryBoundary(getUnsafe(), null, dst, src, byteArrayOffset(srcOffset), size);
    }

    public void copyNativeMemory(byte[] dst, int dstOffset, long src, int size) {
        copyNativeMemoryBoundary(getUnsafe(), dst, byteArrayOffset(dstOffset), null, src, size);
    }

    private static long byteArrayOffset(int offset) {
        return (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + (long) Unsafe.ARRAY_BYTE_INDEX_SCALE * (long) offset;
    }

    @TruffleBoundary
    private static void copyNativeMemoryBoundary(Unsafe unsafe, Object dst, long dstOffset, Object src, long srcOffset, int size) {
        unsafe.copyMemory(src, srcOffset, dst, dstOffset, size);
    }

    public void setNativeMemory(long pointer, int size, byte value) {
        setNativeMemoryBoundary(getUnsafe(), pointer, size, value);
    }

    @TruffleBoundary
    private static void setNativeMemoryBoundary(Unsafe unsafe, long pointer, int size, byte value) {
        unsafe.setMemory(pointer, size, value);
    }
}
