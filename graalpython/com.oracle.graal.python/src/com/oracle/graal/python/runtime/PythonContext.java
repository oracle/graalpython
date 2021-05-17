/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates.
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

import static com.oracle.graal.python.builtins.objects.thread.PThread.GRAALPYTHON_THREADS;
import static com.oracle.graal.python.nodes.BuiltinNames.__BUILTINS__;
import static com.oracle.graal.python.nodes.BuiltinNames.__MAIN__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__ANNOTATIONS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__FILE__;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.nio.file.LinkOption;
import java.text.MessageFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.options.OptionKey;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.PythonAbstractObjectFactory.PInteropGetAttributeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeClass;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext;
import com.oracle.graal.python.builtins.objects.cext.capi.PThreadState;
import com.oracle.graal.python.builtins.objects.cext.capi.PyTruffleObjectFree.ReleaseHandleNode;
import com.oracle.graal.python.builtins.objects.cext.capi.PyTruffleObjectFreeFactory.ReleaseHandleNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDebugContext;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.frame.PFrame.Reference;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.thread.PLock;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.runtime.AsyncHandler.AsyncAction;
import com.oracle.graal.python.runtime.exception.ExceptionUtils;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonThreadKillException;
import com.oracle.graal.python.runtime.object.IDUtils;
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
import com.oracle.truffle.api.ThreadLocalAction;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.CachedLanguage;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.instrumentation.AllocationReporter;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.utilities.CyclicAssumption;
import com.oracle.truffle.llvm.api.Toolchain;

public final class PythonContext {
    private static final Source IMPORT_WARNINGS_SOURCE = Source.newBuilder(PythonLanguage.ID, "import warnings\n", "<internal>").internal(true).build();
    private static final Source FORCE_IMPORTS_SOURCE = Source.newBuilder(PythonLanguage.ID, "import site\n", "<internal>").internal(true).build();
    private static final TruffleLogger LOGGER = PythonLanguage.getLogger(PythonContext.class);
    private volatile boolean finalizing;

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

        /* corresponds to 'PyThreadState.curexc_*' */
        PException currentException;

        /* corresponds to 'PyThreadState.exc_*' */
        PException caughtException;

        /* set to emulate Py_ReprEnter/Leave */
        HashSet<Object> reprObjectSet;

        /* corresponds to 'PyThreadState.dict' */
        PDict dict;

        /*
         * This is the native wrapper object if we need to expose the thread state as PyThreadState
         * object. We need to store it here because the wrapper may receive 'toNative' in which case
         * a handle is allocated. In order to avoid leaks, the handle needs to be free'd when the
         * owning thread (or the whole context) is disposed.
         */
        PThreadState nativeWrapper;

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

        public void setCurrentException(PException currentException) {
            this.currentException = currentException;
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

        public PThreadState getNativeWrapper() {
            return nativeWrapper;
        }

        public void setNativeWrapper(PThreadState nativeWrapper) {
            this.nativeWrapper = nativeWrapper;
        }

        public void dispose() {
            // This method may be called twice on the same object.
            ReleaseHandleNode releaseHandleNode = ReleaseHandleNodeGen.getUncached();
            if (dict != null && dict.getNativeWrapper() != null) {
                releaseHandleNode.execute(dict.getNativeWrapper());
            }
            dict = null;
            if (nativeWrapper != null) {
                releaseHandleNode.execute(nativeWrapper);
                nativeWrapper = null;
            }
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
    public abstract static class GetThreadStateNode extends Node {

        public abstract PythonThreadState execute(PythonContext context);

        public final PythonThreadState execute() {
            return execute(null);
        }

        public final PException getCaughtException(PythonContext context) {
            return execute(context).caughtException;
        }

        public final PException getCaughtException() {
            return execute(null).caughtException;
        }

        public final void setCaughtException(PythonContext context, PException exception) {
            execute(context).caughtException = exception;
        }

        public final void setCaughtException(PException exception) {
            execute(null).caughtException = exception;
        }

        public final PException getCurrentException(PythonContext context) {
            return execute(context).currentException;
        }

        public final PException getCurrentException() {
            return execute(null).currentException;
        }

        public final void setTopFrameInfo(PythonContext context, PFrame.Reference topframeref) {
            execute(context).topframeref = topframeref;
        }

        public final void clearTopFrameInfo(PythonContext context) {
            execute(context).topframeref = null;
        }

        public final void setCurrentException(PythonContext context, PException exception) {
            execute(context).currentException = exception;
        }

        public final void setCurrentException(PException exception) {
            execute(null).currentException = exception;
        }

        public final void setTopFrameInfo(PFrame.Reference topframeref) {
            execute(null).topframeref = topframeref;
        }

        public final PFrame.Reference getTopFrameInfo(PythonContext context) {
            return execute(context).topframeref;
        }

        public final PFrame.Reference getTopFrameInfo() {
            return execute(null).topframeref;
        }

        @Specialization(guards = {"noContext == null", "!curThreadState.isShuttingDown()"})
        @SuppressWarnings("unused")
        static PythonThreadState doNoShutdown(PythonContext noContext,
                        @Shared("language") @CachedLanguage PythonLanguage language,
                        @Bind("getThreadState(language)") PythonThreadState curThreadState) {
            return curThreadState;
        }

        @Specialization(guards = {"noContext == null"}, replaces = "doNoShutdown")
        static PythonThreadState doGeneric(@SuppressWarnings("unused") PythonContext noContext,
                        @Shared("language") @CachedLanguage PythonLanguage language,
                        @Shared("context") @CachedContext(PythonLanguage.class) ContextReference<PythonContext> context) {
            PythonThreadState curThreadState = language.getThreadStateLocal().get();
            if (curThreadState.isShuttingDown()) {
                context.get().killThread();
            }
            return curThreadState;
        }

        @Specialization(guards = "!curThreadState.isShuttingDown()")
        @SuppressWarnings("unused")
        static PythonThreadState doNoShutdownWithContext(PythonContext context,
                        @Shared("language") @CachedLanguage PythonLanguage language,
                        @Bind("getThreadState(language)") PythonThreadState curThreadState) {
            return curThreadState;
        }

        @Specialization(replaces = "doNoShutdownWithContext")
        static PythonThreadState doGenericWithContext(PythonContext context,
                        @Shared("language") @CachedLanguage PythonLanguage language) {
            PythonThreadState curThreadState = language.getThreadStateLocal().get(context.env.getContext());
            if (CompilerDirectives.injectBranchProbability(CompilerDirectives.SLOWPATH_PROBABILITY, curThreadState.isShuttingDown())) {
                context.killThread();
            }
            return curThreadState;
        }

        static PythonThreadState getThreadState(PythonLanguage language) {
            return language.getThreadStateLocal().get();
        }
    }

    static final String PREFIX = "/";
    static final String LIB_PYTHON_3 = "/lib-python/3";
    static final String LIB_GRAALPYTHON = "/lib-graalpython";
    static final String NO_CORE_FATAL = "could not determine Graal.Python's core path - you must pass --python.CoreHome.";
    static final String NO_PREFIX_WARNING = "could not determine Graal.Python's sys prefix path - you may need to pass --python.SysPrefix.";
    static final String NO_CORE_WARNING = "could not determine Graal.Python's core path - you may need to pass --python.CoreHome.";
    static final String NO_STDLIB = "could not determine Graal.Python's standard library path. You need to pass --python.StdLibHome if you want to use the standard library.";
    static final String NO_CAPI = "could not determine Graal.Python's C API library path. You need to pass --python.CAPI if you want to use the C extension modules.";

    private final PythonLanguage language;
    private PythonModule mainModule;
    private final Python3Core core;
    private final List<ShutdownHook> shutdownHooks = new ArrayList<>();
    private final List<AtExitHook> atExitHooks = new ArrayList<>();
    private final HashMap<PythonNativeClass, CyclicAssumption> nativeClassStableAssumptions = new HashMap<>();
    private final ThreadGroup threadGroup = new ThreadGroup(GRAALPYTHON_THREADS);
    private final IDUtils idUtils = new IDUtils();

    @CompilationFinal private PosixSupport posixSupport;
    @CompilationFinal private NFIZlibSupport nativeZlib;
    @CompilationFinal private NFIBz2Support nativeBz2lib;
    @CompilationFinal private NFILZMASupport nativeLZMA;

    // if set to 0 the VM will set it to whatever it likes
    private final AtomicLong pythonThreadStackSize = new AtomicLong(0);
    private final Assumption nativeObjectsAllManagedAssumption = Truffle.getRuntime().createAssumption("all C API objects are managed");

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
    @CompilationFinal private GraalHPyDebugContext hPyDebugContext;

    private String soABI; // cache for soAPI

    private static final Assumption singleNativeContext = Truffle.getRuntime().createAssumption("single native context assumption");

    private static final class GlobalInterpreterLock extends ReentrantLock {
        private static final long serialVersionUID = 1L;

        @Override
        public Thread getOwner() {
            return super.getOwner();
        }
    }

    private final GlobalInterpreterLock globalInterpreterLock = new GlobalInterpreterLock();

    /** Native wrappers for context-insensitive singletons like {@link PNone#NONE}. */
    @CompilationFinal(dimensions = 1) private final PythonNativeWrapper[] singletonNativePtrs = new PythonNativeWrapper[PythonLanguage.getNumberOfSpecialSingletons()];

    // The context-local resources
    private EmulatedPosixSupport resources;
    private final AsyncHandler handler;
    private final AsyncHandler.SharedFinalizer sharedFinalizer;

    // decides if we run the async weakref callbacks and destructors
    private boolean gcEnabled = true;

    // A thread-local to store the full path to the currently active import statement, for Jython
    // compat
    private final ThreadLocal<ArrayDeque<String>> currentImport = new ThreadLocal<>();

    @CompilationFinal(dimensions = 1) private Object[] optionValues;
    private AllocationReporter allocationReporter;

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
    private final WeakHashMap<CallTarget, String> codeFilename = new WeakHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> deserializationId = new ConcurrentHashMap<>();

    public PythonContext(PythonLanguage language, TruffleLanguage.Env env, Python3Core core) {
        this.language = language;
        this.core = core;
        this.env = env;
        this.handler = new AsyncHandler(this);
        this.sharedFinalizer = new AsyncHandler.SharedFinalizer(this);
        this.optionValues = PythonOptions.createOptionValuesStorage(env);
        this.in = env.in();
        this.out = env.out();
        this.err = env.err();
    }

    public AllocationReporter getAllocationReporter() {
        if (allocationReporter == null) {
            return allocationReporter = env.lookup(AllocationReporter.class);
        }
        return allocationReporter;
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

    public long getNextStringId(String string) {
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

    public PythonLanguage getLanguage() {
        return language;
    }

    public ReentrantLock getImportLock() {
        return importLock;
    }

    public PythonModule getSysModule() {
        return core.getSysModule();
    }

    public PDict getSysModules() {
        return core.getSysModules();
    }

    public PythonModule getBuiltins() {
        return core.getBuiltins();
    }

    public Object getPosixSupport() {
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

    public TruffleLanguage.Env getEnv() {
        return env;
    }

    public void setEnv(TruffleLanguage.Env newEnv) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        env = newEnv;
        in = env.in();
        out = env.out();
        err = env.err();
        resources.setEnv(env);
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
        return core;
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

    public void setCurrentException(PythonLanguage language, PException e) {
        getThreadState(language).currentException = e;
    }

    public PException getCurrentException(PythonLanguage lang) {
        return getThreadState(lang).currentException;
    }

    public void setCaughtException(PythonLanguage lang, PException e) {
        getThreadState(lang).caughtException = e;
    }

    public PException getCaughtException(PythonLanguage lang) {
        return getThreadState(lang).caughtException;
    }

    public void setTopFrameInfo(PythonLanguage lang, PFrame.Reference topframeref) {
        getThreadState(lang).topframeref = topframeref;
    }

    public PFrame.Reference popTopFrameInfo(PythonLanguage lang) {
        return getThreadState(lang).popTopFrameInfo();
    }

    public PFrame.Reference peekTopFrameInfo(PythonLanguage lang) {
        return getThreadState(lang).topframeref;
    }

    @TruffleBoundary
    public boolean reprEnter(Object item) {
        return getThreadState(PythonLanguage.getCurrent()).reprEnter(item);
    }

    @TruffleBoundary
    public void reprLeave(Object item) {
        getThreadState(PythonLanguage.getCurrent()).reprLeave(item);
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
            core.initialize(this);
            setupRuntimeInformation(false);
            core.postInitialize();
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
            core.postInitialize();
            importSiteIfForced();
        } finally {
            releaseGil();
        }
    }

    private void importSiteIfForced() {
        if (getOption(PythonOptions.ForceImportSite)) {
            CallTarget site = env.parsePublic(FORCE_IMPORTS_SOURCE);
            site.call();
        }
        if (!getOption(PythonOptions.WarnOptions).isEmpty()) {
            // we must force an import of the warnings module here if warnings were passed
            CallTarget site = env.parsePublic(IMPORT_WARNINGS_SOURCE);
            site.call();
        }
        if (!getOption(PythonOptions.IsolateFlag)) {
            String path0 = computeSysPath0();
            if (path0 != null) {
                PythonModule sys = core.lookupBuiltinModule("sys");
                Object path = sys.getAttribute("path");
                PythonObjectLibrary.getUncached().lookupAndCallRegularMethod(path, null, "insert", 0, path0);
            }
        }
    }

    // Equivalent of pathconfig.c:_PyPathConfig_ComputeSysPath0
    private String computeSysPath0() {
        String[] args = env.getApplicationArguments();
        if (args.length == 0) {
            return null;
        }
        String argv0 = args[0];
        if (argv0.isEmpty()) {
            return "";
        } else if (argv0.equals("-m")) {
            try {
                return env.getCurrentWorkingDirectory().getPath();
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
                return parent.getPath();
            }
        }
        return "";
    }

    /**
     * During pre-initialization, we're also loading code from the Python standard library. Since
     * some of those modules may be packages, they will have their __path__ attribute set to the
     * absolute path of the package on the build system. We use this function to patch the paths
     * during build time and after starting up from a pre-initialized context so they point to the
     * run-time package paths.
     */
    private void patchPackagePaths(String from, String to) {
        for (Object v : HashingStorageLibrary.getUncached().values(getSysModules().getDictStorage())) {
            if (v instanceof PythonModule) {
                // Update module.__path__
                Object path = ((PythonModule) v).getAttribute(SpecialAttributeNames.__PATH__);
                if (path instanceof PList) {
                    Object[] paths = SequenceStorageNodes.CopyInternalArrayNode.getUncached().execute(((PList) path).getSequenceStorage());
                    for (int i = 0; i < paths.length; i++) {
                        Object pathElement = paths[i];
                        String strPath;
                        if (pathElement instanceof PString) {
                            strPath = ((PString) pathElement).getValue();
                        } else if (pathElement instanceof String) {
                            strPath = (String) pathElement;
                        } else {
                            continue;
                        }
                        if (strPath.startsWith(from)) {
                            paths[i] = strPath.replace(from, to);
                        }
                    }
                    ((PythonModule) v).setAttribute(SpecialAttributeNames.__PATH__, core.factory().createList(paths));
                }

                // Update module.__file__
                Object file = ((PythonModule) v).getAttribute(SpecialAttributeNames.__FILE__);
                String strFile = null;
                if (file instanceof PString) {
                    strFile = ((PString) file).getValue();
                } else if (file instanceof String) {
                    strFile = (String) file;
                }
                if (strFile != null) {
                    ((PythonModule) v).setAttribute(SpecialAttributeNames.__FILE__, strFile.replace(from, to));
                }
            }
        }
    }

    private void setupRuntimeInformation(boolean isPatching) {
        nativeZlib = NFIZlibSupport.createNative(this, "");
        nativeBz2lib = NFIBz2Support.createNative(this, "");
        nativeLZMA = NFILZMASupport.createNative(this, "");

        mainModule = core.factory().createPythonModule(__MAIN__);
        mainModule.setAttribute(__BUILTINS__, getBuiltins());
        mainModule.setAttribute(__ANNOTATIONS__, core.factory().createDict());
        try {
            PythonObjectLibrary.getUncached().setDict(mainModule, core.factory().createDictFixedStorage(mainModule));
        } catch (UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException("This cannot happen - the main module doesn't accept a __dict__", e);
        }

        getSysModules().setItem(__MAIN__, mainModule);

        final String stdLibPlaceholder = "!stdLibHome!";
        if (ImageInfo.inImageBuildtimeCode()) {
            // Patch any pre-loaded packages' paths if we're running
            // pre-initialization
            patchPackagePaths(getStdlibHome(), stdLibPlaceholder);
        } else if (isPatching && ImageInfo.inImageRuntimeCode()) {
            // Patch any pre-loaded packages' paths to the new stdlib home if
            // we're patching a pre-initialized context
            patchPackagePaths(stdLibPlaceholder, getStdlibHome());
        }

        applyToAllThreadStates(ts -> ts.currentException = null);
        isInitialized = true;
    }

    private void initializePosixSupport() {
        String option = getLanguage().getEngineOption(PythonOptions.PosixModuleBackend);
        PosixSupport result;
        // The resources field will be removed once all posix builtins go through PosixSupport
        switch (option) {
            case "java":
                result = resources = new EmulatedPosixSupport(this, false);
                break;
            case "native":
            case "llvm":
                // TODO this condition will be moved into a factory method in NFIPosixBackend
                // for now it's here because we still need to expose the emulated backend as
                // 'resources'
                if (ImageInfo.inImageBuildtimeCode()) {
                    EmulatedPosixSupport emulatedPosixSupport = new EmulatedPosixSupport(this, false);
                    NFIPosixSupport nativePosixSupport = new NFIPosixSupport(this, option);
                    result = new ImageBuildtimePosixSupport(nativePosixSupport, emulatedPosixSupport);
                    resources = emulatedPosixSupport;
                } else if (ImageInfo.inImageRuntimeCode()) {
                    NFIPosixSupport nativePosixSupport = new NFIPosixSupport(this, option);
                    result = new ImageBuildtimePosixSupport(nativePosixSupport, null);
                    resources = new EmulatedPosixSupport(this, true);
                    resources.setEnv(env);
                } else {
                    if (!getOption(PythonOptions.RunViaLauncher)) {
                        writeWarning("Native Posix backend is not fully supported when embedding. For example, standard I/O always uses file " +
                                        "descriptors 0, 1 and 2 regardless of stream redirection specified in Truffle environment");
                    }
                    result = new NFIPosixSupport(this, option);
                    resources = new EmulatedPosixSupport(this, true);
                    resources.setEnv(env);
                }
                break;
            default:
                throw new IllegalStateException(String.format("Wrong value for the PosixModuleBackend option: '%s'", option));
        }
        if (LoggingPosixSupport.isEnabled()) {
            posixSupport = new LoggingPosixSupport(result);
        } else {
            posixSupport = result;
        }
    }

    private String sysPrefix, basePrefix, coreHome, stdLibHome, capiHome;

    public void initializeHomeAndPrefixPaths(Env newEnv, String languageHome) {
        sysPrefix = newEnv.getOptions().get(PythonOptions.SysPrefix);
        basePrefix = newEnv.getOptions().get(PythonOptions.SysBasePrefix);
        coreHome = newEnv.getOptions().get(PythonOptions.CoreHome);
        stdLibHome = newEnv.getOptions().get(PythonOptions.StdLibHome);
        capiHome = newEnv.getOptions().get(PythonOptions.CAPI);

        Python3Core.writeInfo(() -> MessageFormat.format("Initial locations:" +
                        "\n\tLanguage home: {0}" +
                        "\n\tSysPrefix: {1}" +
                        "\n\tBaseSysPrefix: {2}" +
                        "\n\tCoreHome: {3}" +
                        "\n\tStdLibHome: {4}" +
                        "\n\tCAPI: {5}", languageHome, sysPrefix, basePrefix, coreHome, stdLibHome, capiHome));

        String envHome = null;
        try {
            envHome = System.getenv("GRAAL_PYTHONHOME");
        } catch (SecurityException e) {
        }

        final TruffleFile home;
        if (languageHome != null && envHome == null) {
            home = newEnv.getInternalTruffleFile(languageHome);
        } else if (envHome != null) {
            boolean envHomeIsDirectory = false;
            TruffleFile envHomeFile = null;
            try {
                envHomeFile = newEnv.getInternalTruffleFile(envHome);
                envHomeIsDirectory = envHomeFile.isDirectory();
            } catch (SecurityException e) {
            }
            home = envHomeIsDirectory ? envHomeFile : null;
        } else {
            home = null;
        }

        if (home != null) {
            if (sysPrefix.isEmpty()) {
                sysPrefix = home.getAbsoluteFile().getPath();
            }

            if (basePrefix.isEmpty()) {
                basePrefix = home.getAbsoluteFile().getPath();
            }

            if (coreHome.isEmpty()) {
                try {
                    for (TruffleFile f : home.list()) {
                        if (f.getName().equals("lib-graalpython") && f.isDirectory()) {
                            coreHome = f.getPath();
                            break;
                        }
                    }
                } catch (SecurityException | IOException e) {
                }
            }

            if (stdLibHome.isEmpty()) {
                try {
                    outer: for (TruffleFile f : home.list()) {
                        if (f.getName().equals("lib-python") && f.isDirectory()) {
                            for (TruffleFile f2 : f.list()) {
                                if (f2.getName().equals("3") && f.isDirectory()) {
                                    stdLibHome = f2.getPath();
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
        }

        if (ImageInfo.inImageBuildtimeCode()) {
            // use relative paths at buildtime to avoid freezing buildsystem paths
            TruffleFile base = newEnv.getInternalTruffleFile(basePrefix).getAbsoluteFile();
            newEnv.setCurrentWorkingDirectory(base);
            basePrefix = ".";
            sysPrefix = base.relativize(newEnv.getInternalTruffleFile(sysPrefix)).getPath();
            if (sysPrefix.isEmpty()) {
                sysPrefix = ".";
            }
            coreHome = base.relativize(newEnv.getInternalTruffleFile(coreHome)).getPath();
            stdLibHome = base.relativize(newEnv.getInternalTruffleFile(stdLibHome)).getPath();
            capiHome = base.relativize(newEnv.getInternalTruffleFile(capiHome)).getPath();
        }

        Python3Core.writeInfo(() -> MessageFormat.format("Updated locations:" +
                        "\n\tLanguage home: {0}" +
                        "\n\tSysPrefix: {1}" +
                        "\n\tBaseSysPrefix: {2}" +
                        "\n\tCoreHome: {3}" +
                        "\n\tStdLibHome: {4}" +
                        "\n\tExecutable: {5}" +
                        "\n\tCAPI: {6}", home != null ? home.getPath() : "", sysPrefix, basePrefix, coreHome, stdLibHome, newEnv.getOptions().get(PythonOptions.Executable), capiHome));
    }

    @TruffleBoundary
    public String getSysPrefix() {
        if (sysPrefix.isEmpty()) {
            writeWarning(NO_PREFIX_WARNING);
            sysPrefix = PREFIX;
        }
        return sysPrefix;
    }

    @TruffleBoundary
    public String getSysBasePrefix() {
        if (basePrefix.isEmpty()) {
            String homePrefix = language.getHome();
            if (homePrefix == null || homePrefix.isEmpty()) {
                homePrefix = PREFIX;
            }
            basePrefix = homePrefix;
        }
        return basePrefix;
    }

    @TruffleBoundary
    public String getCoreHome() {
        if (coreHome.isEmpty()) {
            writeWarning(NO_CORE_WARNING);
            coreHome = LIB_GRAALPYTHON;
        }
        return coreHome;
    }

    @TruffleBoundary
    public String getStdlibHome() {
        if (stdLibHome.isEmpty()) {
            writeWarning(NO_STDLIB);
            stdLibHome = LIB_PYTHON_3;
        }
        return stdLibHome;
    }

    @TruffleBoundary
    public String getCoreHomeOrFail() {
        if (coreHome.isEmpty()) {
            throw new RuntimeException(NO_CORE_FATAL);
        }
        return coreHome;
    }

    @TruffleBoundary
    public String getCAPIHome() {
        if (capiHome.isEmpty()) {
            writeWarning(NO_CAPI);
            return coreHome;
        }
        return capiHome;
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

    @TruffleBoundary
    @SuppressWarnings("try")
    public void finalizeContext() {
        try (GilNode.UncachedAcquire gil = GilNode.uncachedAcquire()) {
            shutdownThreads();
            runShutdownHooks();
            finalizing = true;
            joinThreads();
            cleanupCApiResources();
            disposeThreadStates();
        }
        cleanupHPyResources();
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
        handler.shutdown();
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
        ReleaseHandleNode releaseHandleNode = ReleaseHandleNodeGen.getUncached();
        for (PythonNativeWrapper singletonNativeWrapper : singletonNativePtrs) {
            if (singletonNativeWrapper != null) {
                releaseHandleNode.execute(singletonNativeWrapper);
            }
        }
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
        Object value = HashingStorageLibrary.getUncached().getItem(dictStorage, "threading");
        if (value != null) {
            Object attrShutdown = ReadAttributeFromObjectNode.getUncached().execute(value, SpecialMethodNames.SHUTDOWN);
            if (attrShutdown == PNone.NO_VALUE) {
                LOGGER.fine("threading module has no member " + SpecialMethodNames.SHUTDOWN);
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
            for (Thread thread : threads) {
                if (thread != Thread.currentThread()) {
                    // cannot interrupt ourselves, we're holding the GIL
                    LOGGER.finest("joining thread " + thread);
                    // the threads remaining here are daemon threads, all others were shut down via
                    // the threading module above. So we just interrupt them. Their exit is handled
                    // in the acquireGil function, which will be interrupted for these threads
                    disposeThread(thread);
                    for (int i = 0; i < 100 && thread.isAlive(); i++) {
                        env.submitThreadLocal(new Thread[]{thread}, new ThreadLocalAction(true, false) {
                            @Override
                            protected void perform(ThreadLocalAction.Access access) {
                                throw new PythonThreadKillException();
                            }
                        });
                        thread.interrupt();
                        thread.join(2);
                    }
                    if (thread.isAlive()) {
                        LOGGER.warning("Could not join thread " + thread.getName() + ". Trying to kill it.");
                    }
                    thread.stop();
                    if (thread.isAlive()) {
                        LOGGER.warning("Could not kill thread " + thread.getName());
                    }
                }
            }
        } catch (InterruptedException e) {
            LOGGER.finest("got interrupt while joining threads");
            Thread.currentThread().interrupt();
        }
    }

    public void initializeMainModule(String path) {
        if (path != null) {
            mainModule.setAttribute(__FILE__, path);
        }
    }

    public static Assumption getSingleNativeContextAssumption() {
        return singleNativeContext;
    }

    public final Assumption getNativeObjectsAllManagedAssumption() {
        return nativeObjectsAllManagedAssumption;
    }

    public boolean isExecutableAccessAllowed() {
        return getEnv().isHostLookupAllowed() || getEnv().isNativeAccessAllowed();
    }

    public EmulatedPosixSupport getResources() {
        return resources;
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

    public void registerAsyncAction(Supplier<AsyncAction> actionSupplier) {
        handler.registerAction(actionSupplier);
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

    public void setSingletonNativeWrapper(PythonAbstractObject obj, PythonNativeWrapper nativePtr) {
        assert PythonLanguage.getSingletonNativeWrapperIdx(obj) != -1 : "invalid special singleton object";
        assert singletonNativePtrs[PythonLanguage.getSingletonNativeWrapperIdx(obj)] == null;
        singletonNativePtrs[PythonLanguage.getSingletonNativeWrapperIdx(obj)] = nativePtr;
    }

    public PythonNativeWrapper getSingletonNativeWrapper(PythonAbstractObject obj) {
        int singletonNativePtrIdx = PythonLanguage.getSingletonNativeWrapperIdx(obj);
        if (singletonNativePtrIdx != -1) {
            return singletonNativePtrs[singletonNativePtrIdx];
        }
        return null;
    }

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
    boolean ownsGil() {
        return globalInterpreterLock.isHeldByCurrentThread();
    }

    /**
     * Should not be used outside of {@link AsyncHandler}
     */
    Thread getGilOwner() {
        return globalInterpreterLock.getOwner();
    }

    /**
     * Should not be called directly.
     *
     * @see GilNode
     */
    @TruffleBoundary
    boolean tryAcquireGil() {
        return globalInterpreterLock.tryLock();
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
    public TruffleFile getPublicTruffleFileRelaxed(String path, String... allowedSuffixes) {
        TruffleFile f = env.getInternalTruffleFile(path);
        // 'isDirectory' does deliberately not follow symlinks because otherwise this could allow to
        // escape the language home directory.
        // Also, during image build time, we allow full internal access.
        if (ImageInfo.inImageBuildtimeCode() || isPyFileInLanguageHome(f) && (f.isDirectory(LinkOption.NOFOLLOW_LINKS) || hasAllowedSuffix(path, allowedSuffixes))) {
            return f;
        } else {
            return env.getPublicTruffleFile(path);
        }
    }

    @TruffleBoundary(allowInlining = true)
    private static boolean hasAllowedSuffix(String path, String[] allowedSuffixes) {
        for (String suffix : allowedSuffixes) {
            if (path.endsWith(suffix)) {
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
        String languageHome = language.getHome();

        // The language home may be 'null' if an embedder uses Python. In this case, IO must just be
        // allowed.
        if (languageHome != null) {
            // This deliberately uses 'getAbsoluteFile' and not 'getCanonicalFile' because if, e.g.,
            // 'path' is a symlink outside of the language home, the user should not be able to read
            // the symlink if 'allowIO' is false.
            TruffleFile coreHomePath = env.getInternalTruffleFile(languageHome).getAbsoluteFile();
            TruffleFile absolutePath = path.getAbsoluteFile();
            return absolutePath.startsWith(coreHomePath);
        }
        LOGGER.log(Level.FINE, () -> "Cannot access file " + path + " because there is no language home.");
        return false;
    }

    @TruffleBoundary
    public String getCurrentImport() {
        ArrayDeque<String> ci = currentImport.get();
        if (ci == null || ci.isEmpty()) {
            return "";
        } else {
            return ci.peek();
        }
    }

    @TruffleBoundary
    public void pushCurrentImport(String object) {
        ArrayDeque<String> ci = currentImport.get();
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
        if (language.singleThreadedAssumption.isValid()) {
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

    public void setCapiWasLoaded(CApiContext capiContext) {
        assert this.cApiContext == null : "tried to create new C API context but it was already created";
        this.cApiContext = capiContext;
    }

    public boolean hasHPyContext() {
        return hPyContext != null;
    }

    public synchronized void createHPyContext(Object hpyLibrary) {
        assert hPyContext == null : "tried to create new HPy context but it was already created";
        hPyContext = new GraalHPyContext(this, hpyLibrary);
    }

    public GraalHPyContext getHPyContext() {
        assert hPyContext != null : "tried to get HPy context but was not created yet";
        return hPyContext;
    }

    /**
     * Equivalent of {@code debug_ctx.c: hpy_debug_get_ctx}.
     */
    public GraalHPyDebugContext getHPyDebugContext() {
        if (hPyDebugContext == null) {
            hPyDebugContext = initDebugMode();
        }
        return hPyDebugContext;
    }

    /**
     * Equivalent of {@code debug_ctx.c: hpy_debug_init_ctx}.
     */
    @TruffleBoundary
    private GraalHPyDebugContext initDebugMode() {
        if (!hasHPyContext()) {
            throw CompilerDirectives.shouldNotReachHere("cannot initialize HPy debug context without HPy context");
        }
        getLanguage().noHPyDebugModeAssumption.invalidate();
        return new GraalHPyDebugContext(hPyContext);
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

    public void setCodeFilename(CallTarget callTarget, String filename) {
        codeFilename.put(callTarget, filename);
    }

    public String getCodeFilename(CallTarget callTarget) {
        return codeFilename.get(callTarget);
    }

    public long getDeserializationId(String fileName) {
        return deserializationId.computeIfAbsent(fileName, f -> new AtomicLong()).incrementAndGet();
    }

    @TruffleBoundary
    public String getSoAbi() {
        if (soABI == null) {
            PythonModule sysModule = getCore().lookupBuiltinModule("sys");
            Object implementationObj = ReadAttributeFromObjectNode.getUncached().execute(sysModule, "implementation");
            // sys.implementation.cache_tag
            String cacheTag = (String) PInteropGetAttributeNodeGen.getUncached().execute(implementationObj, "cache_tag");
            // sys.implementation._multiarch
            String multiArch = (String) PInteropGetAttributeNodeGen.getUncached().execute(implementationObj, "_multiarch");

            LanguageInfo llvmInfo = env.getInternalLanguages().get(PythonLanguage.LLVM_LANGUAGE);
            Toolchain toolchain = env.lookup(llvmInfo, Toolchain.class);
            String toolchainId = toolchain.getIdentifier();

            // only use '.dylib' if we are on 'Darwin-native'
            String soExt;
            if ("darwin".equals(PythonUtils.getPythonOSName()) && "native".equals(toolchainId)) {
                soExt = ".dylib";
            } else {
                soExt = ".so";
            }

            soABI = "." + cacheTag + "-" + toolchainId + "-" + multiArch + soExt;
        }
        return soABI;
    }

    public Thread getMainThread() {
        if (mainThread != null) {
            return mainThread.get();
        }
        return null;
    }
}
