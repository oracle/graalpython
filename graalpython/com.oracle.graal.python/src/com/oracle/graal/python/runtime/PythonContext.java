/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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
import static com.oracle.graal.python.nodes.BuiltinNames.BUILTINS;
import static com.oracle.graal.python.nodes.BuiltinNames.__BUILTINS__;
import static com.oracle.graal.python.nodes.BuiltinNames.__MAIN__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__FILE__;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.nio.file.LinkOption;
import java.text.MessageFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.options.OptionValues;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.cext.PThreadState;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeClass;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.frame.PFrame.Reference;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.thread.PLock;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.runtime.AsyncHandler.AsyncAction;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.util.ShutdownHook;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.utilities.CyclicAssumption;

public final class PythonContext {

    private static final class PythonThreadState {

        private WeakReference<Thread> owner;

        /*
         * The reference to the last top frame on the Python stack during interop calls. Initially,
         * this is EMPTY representing the top frame.
         */
        private PFrame.Reference topframeref = Reference.EMPTY;

        private WeakReference<PLock> sentinelLock;

        /* corresponds to 'PyThreadState.curexc_*' */
        private PException currentException;

        /* corresponds to 'PyThreadState.exc_*' */
        private PException caughtException;

        PythonThreadState() {
        }

        PythonThreadState(Thread owner) {
            this.owner = new WeakReference<>(owner);
        }
    }

    static final String PREFIX = "/";
    static final String LIB_PYTHON_3 = "/lib-python/3";
    static final String LIB_GRAALPYTHON = "/lib-graalpython";
    static final String CAPI_HOME = "/capi";
    static final String NO_CORE_FATAL = "could not determine Graal.Python's core path - you must pass --python.CoreHome.";
    static final String NO_PREFIX_WARNING = "could not determine Graal.Python's sys prefix path - you may need to pass --python.SysPrefix.";
    static final String NO_CORE_WARNING = "could not determine Graal.Python's core path - you may need to pass --python.CoreHome.";
    static final String NO_STDLIB = "could not determine Graal.Python's standard library path. You need to pass --python.StdLibHome if you want to use the standard library.";
    static final String NO_CAPI = "could not determine Graal.Python's C API library path. You need to pass --python.CAPI if you want to use the C extension modules.";

    private final PythonLanguage language;
    private PythonModule mainModule;
    private final PythonCore core;
    private final List<ShutdownHook> shutdownHooks = new ArrayList<>();
    private final HashMap<Object, CallTarget> atExitHooks = new HashMap<>();
    private final HashMap<PythonNativeClass, CyclicAssumption> nativeClassStableAssumptions = new HashMap<>();
    private final AtomicLong globalId = new AtomicLong(Integer.MAX_VALUE * 2L + 4L);
    private final ThreadGroup threadGroup = new ThreadGroup(GRAALPYTHON_THREADS);

    // if set to 0 the VM will set it to whatever it likes
    private final AtomicLong pythonThreadStackSize = new AtomicLong(0);
    private final Assumption nativeObjectsAllManagedAssumption = Truffle.getRuntime().createAssumption("all C API objects are managed");

    @CompilationFinal private TruffleLanguage.Env env;

    /* this will be the single thread state if running single-threaded */
    private final PythonThreadState singleThreadState = new PythonThreadState();

    /* for fast access to the PythonThreadState object by the owning thread */
    private ThreadLocal<PythonThreadState> threadState;

    /* array of thread states */
    private PythonThreadState[] threadStates;

    /* map of thread IDs to indices for array 'threadStates' */
    private Map<Long, Integer> threadStateMapping;

    /* number of threads attached to this context */
    private int attachedThreads = 0;

    private final ReentrantLock importLock = new ReentrantLock();
    @CompilationFinal private boolean isInitialized = false;

    @CompilationFinal private PythonModule builtinsModule;
    @CompilationFinal private PDict sysModules;

    private OutputStream out;
    private OutputStream err;
    private InputStream in;
    @CompilationFinal private Object capiLibrary = null;
    private final Assumption singleThreaded = Truffle.getRuntime().createAssumption("single Threaded");

    private static final Assumption singleNativeContext = Truffle.getRuntime().createAssumption("single native context assumption");

    /* A lock for interop calls when this context is used by multiple threads. */
    private ReentrantLock interopLock;

    @CompilationFinal private HashingStorage.Equivalence slowPathEquivalence;

    /** The thread-local state object. */
    private ThreadLocal<PThreadState> customThreadState;

    /* native pointers for context-insensitive singletons like PNone.NONE */
    private final Object[] singletonNativePtrs = new Object[PythonLanguage.getNumberOfSpecialSingletons()];

    // The context-local resources
    private final PosixResources resources;
    private final AsyncHandler handler;

    // A thread-local to store the full path to the currently active import statement, for Jython
    // compat
    private final ThreadLocal<ArrayDeque<String>> currentImport = new ThreadLocal<>();

    public PythonContext(PythonLanguage language, TruffleLanguage.Env env, PythonCore core) {
        this.language = language;
        this.core = core;
        this.env = env;
        this.resources = new PosixResources();
        this.handler = new AsyncHandler(language);
        if (env == null) {
            this.in = System.in;
            this.out = System.out;
            this.err = System.err;
        } else {
            this.resources.setEnv(env);
            this.in = env.in();
            this.out = env.out();
            this.err = env.err();
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

    @TruffleBoundary(allowInlining = true)
    public long getNextGlobalId() {
        return globalId.incrementAndGet();
    }

    public OptionValues getOptions() {
        return getEnv().getOptions();
    }

    public PythonLanguage getLanguage() {
        return language;
    }

    public ReentrantLock getImportLock() {
        return importLock;
    }

    public PDict getImportedModules() {
        return sysModules;
    }

    public PDict getSysModules() {
        return sysModules;
    }

    public PythonModule getBuiltins() {
        return builtinsModule;
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

    public PythonCore getCore() {
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

    public void setCurrentException(PException e) {
        getThreadState().currentException = e;
    }

    public PException getCurrentException() {
        return getThreadState().currentException;
    }

    public void setCaughtException(PException e) {
        getThreadState().caughtException = e;
    }

    public PException getCaughtException() {
        return getThreadState().caughtException;
    }

    public void setTopFrameInfo(PFrame.Reference topframeref) {
        getThreadState().topframeref = topframeref;
    }

    public PFrame.Reference popTopFrameInfo() {
        PythonThreadState threadState = getThreadState();
        PFrame.Reference ref = threadState.topframeref;
        threadState.topframeref = null;
        return ref;
    }

    public PFrame.Reference peekTopFrameInfo() {
        return getThreadState().topframeref;
    }

    public boolean isInitialized() {
        return isInitialized;
    }

    public void initialize() {
        core.initialize(this);
        setupRuntimeInformation(false);
        core.postInitialize();
    }

    public void patch(Env newEnv) {
        setEnv(newEnv);
        setupRuntimeInformation(true);
        core.postInitialize();
    }

    /**
     * During pre-initialization, we're also loading code from the Python standard library. Since
     * some of those modules may be packages, they will have their __path__ attribute set to the
     * absolute path of the package on the build system. We use this function to patch the paths
     * during build time and after starting up from a pre-initialized context so they point to the
     * run-time package paths.
     */
    private void patchPackagePaths(String from, String to) {
        for (Object v : sysModules.getDictStorage().values()) {
            if (v instanceof PythonModule) {
                // Update module.__path__
                Object path = ((PythonModule) v).getAttribute(SpecialAttributeNames.__PATH__);
                if (path instanceof PList) {
                    Object[] paths = ((PList) path).getSequenceStorage().getCopyOfInternalArray();
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
        PythonModule sysModule = core.lookupBuiltinModule("sys");
        sysModules = (PDict) sysModule.getAttribute("modules");

        builtinsModule = core.lookupBuiltinModule(BUILTINS);

        mainModule = core.factory().createPythonModule(__MAIN__);
        mainModule.setAttribute(__BUILTINS__, builtinsModule);
        try {
            PythonObjectLibrary.getUncached().setDict(mainModule, core.factory().createDictFixedStorage(mainModule));
        } catch (UnsupportedMessageException e) {
            throw new IllegalStateException("This cannot happen - the main module doesn't accept a __dict__", e);
        }

        sysModules.setItem(__MAIN__, mainModule);

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

        applyToAllThreadStates(threadState -> threadState.currentException = null);
        isInitialized = true;
    }

    private String sysPrefix, basePrefix, coreHome, stdLibHome, capiHome;

    public void initializeHomeAndPrefixPaths(Env newEnv, String languageHome) {
        sysPrefix = newEnv.getOptions().get(PythonOptions.SysPrefix);
        basePrefix = newEnv.getOptions().get(PythonOptions.SysBasePrefix);
        coreHome = newEnv.getOptions().get(PythonOptions.CoreHome);
        stdLibHome = newEnv.getOptions().get(PythonOptions.StdLibHome);
        capiHome = newEnv.getOptions().get(PythonOptions.CAPI);

        PythonCore.writeInfo((MessageFormat.format("Initial locations:" +
                        "\n\tLanguage home: {0}" +
                        "\n\tSysPrefix: {1}" +
                        "\n\tBaseSysPrefix: {2}" +
                        "\n\tCoreHome: {3}" +
                        "\n\tStdLibHome: {4}" +
                        "\n\tCAPI: {5}", languageHome, sysPrefix, basePrefix, coreHome, stdLibHome, capiHome)));

        TruffleFile home = null;
        if (languageHome != null) {
            home = newEnv.getInternalTruffleFile(languageHome);
        }

        try {
            String envHome = System.getenv("GRAAL_PYTHONHOME");
            if (envHome != null) {
                TruffleFile envHomeFile = newEnv.getInternalTruffleFile(envHome);
                if (envHomeFile.isDirectory()) {
                    home = envHomeFile;
                }
            }
        } catch (SecurityException e) {
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

            PythonCore.writeInfo((MessageFormat.format("Updated locations:" +
                            "\n\tLanguage home: {0}" +
                            "\n\tSysPrefix: {1}" +
                            "\n\tSysBasePrefix: {2}" +
                            "\n\tCoreHome: {3}" +
                            "\n\tStdLibHome: {4}" +
                            "\n\tExecutable: {6}", home.getPath(), sysPrefix, basePrefix, coreHome, stdLibHome, newEnv.getOptions().get(PythonOptions.Executable))));
        }
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
        PythonLanguage.getLogger().warning(warning);
    }

    public boolean capiWasLoaded() {
        return this.capiLibrary != null;
    }

    public Object getCapiLibrary() {
        return this.capiLibrary;
    }

    public void setCapiWasLoaded(Object capiLibrary) {
        this.capiLibrary = capiLibrary;
    }

    public HashingStorage.Equivalence getSlowPathEquivalence() {
        if (slowPathEquivalence == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            slowPathEquivalence = new HashingStorage.SlowPathEquivalence();
        }
        return slowPathEquivalence;
    }

    @TruffleBoundary
    public void registerShutdownHook(ShutdownHook shutdownHook) {
        shutdownHooks.add(shutdownHook);
    }

    @TruffleBoundary
    public void registerShutdownHook(Object callable, CallTarget ct) {
        atExitHooks.put(callable, ct);
    }

    @TruffleBoundary
    public void deregisterShutdownHook(Object callable) {
        atExitHooks.remove(callable);
    }

    @TruffleBoundary
    public void runShutdownHooks() {
        handler.shutdown();
        for (CallTarget f : atExitHooks.values()) {
            f.call();
        }
        for (ShutdownHook h : shutdownHooks) {
            h.call(this);
        }
    }

    @TruffleBoundary
    public PThreadState getCustomThreadState() {
        if (customThreadState == null) {
            ThreadLocal<PThreadState> threadLocal = new ThreadLocal<>();
            threadLocal.set(new PThreadState());
            customThreadState = threadLocal;
        }
        return customThreadState.get();
    }

    public void initializeMainModule(String path) {
        if (path != null) {
            mainModule.setAttribute(__FILE__, path);
        }
    }

    public static Assumption getSingleNativeContextAssumption() {
        return singleNativeContext;
    }

    public Assumption getSingleThreadedAssumption() {
        return singleThreaded;
    }

    public Assumption getNativeObjectsAllManagedAssumption() {
        return nativeObjectsAllManagedAssumption;
    }

    public boolean isExecutableAccessAllowed() {
        return getEnv().isHostLookupAllowed() || getEnv().isNativeAccessAllowed();
    }

    public PosixResources getResources() {
        return resources;
    }

    /**
     * Trigger any pending asynchronous actions
     */
    public void triggerAsyncActions(VirtualFrame frame, Node location) {
        handler.triggerAsyncActions(frame, location);
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

    public void setSingletonNativePtr(PythonAbstractObject obj, Object nativePtr) {
        assert PythonLanguage.getSingletonNativePtrIdx(obj) != -1 : "invalid special singleton object";
        assert singletonNativePtrs[PythonLanguage.getSingletonNativePtrIdx(obj)] == null;
        singletonNativePtrs[PythonLanguage.getSingletonNativePtrIdx(obj)] = nativePtr;
    }

    public Object getSingletonNativePtr(PythonAbstractObject obj) {
        int singletonNativePtrIdx = PythonLanguage.getSingletonNativePtrIdx(obj);
        if (singletonNativePtrIdx != -1) {
            return singletonNativePtrs[singletonNativePtrIdx];
        }
        return null;
    }

    @TruffleBoundary
    public void acquireInteropLock() {
        interopLock.lock();
    }

    @TruffleBoundary
    public void releaseInteropLock() {
        if (interopLock.isLocked()) {
            interopLock.unlock();
        }
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
        PythonLanguage.getLogger().log(Level.FINE, () -> "Cannot access file " + path + " because there is no language home.");
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

    private PythonThreadState getThreadState() {
        if (singleThreaded.isValid()) {
            return singleThreadState;
        }
        return getThreadStateMultiThreaded();
    }

    @TruffleBoundary
    private PythonThreadState getThreadStateMultiThreaded() {
        PythonThreadState curThreadState = threadState.get();
        if (curThreadState == null) {
            // this should happen just the first time the current thread accesses the thread state
            curThreadState = getThreadStateFullLookup();
            threadState.set(curThreadState);
        }
        assert Thread.currentThread() == curThreadState.owner.get();
        return curThreadState;
    }

    private void applyToAllThreadStates(Consumer<PythonThreadState> action) {
        if (singleThreaded.isValid()) {
            action.accept(singleThreadState);
        } else {
            synchronized (this) {
                for (int i = 0; i < attachedThreads; i++) {
                    action.accept(threadStates[i]);
                }
            }
        }
    }

    @TruffleBoundary
    private synchronized PythonThreadState getThreadStateFullLookup() {
        int idx = threadStateMapping.get(Thread.currentThread().getId());
        return threadStates[idx];
    }

    public void setSentinelLockWeakref(WeakReference<PLock> sentinelLock) {
        getThreadState().sentinelLock = sentinelLock;
    }

    @TruffleBoundary
    public void initializeMultiThreading() {
        interopLock = new ReentrantLock();
        singleThreaded.invalidate();
        threadState = new ThreadLocal<>();
        synchronized (this) {
            threadStates = new PythonThreadState[]{singleThreadState};
        }
    }

    public synchronized void attachThread(Thread thread) {
        CompilerAsserts.neverPartOfCompilation();
        // The first attached thread will be the 'main' thread (or similar).
        if (threadStateMapping == null) {
            assert attachedThreads == 0;
            assert singleThreaded.isValid();
            threadStateMapping = new HashMap<>();
            threadStateMapping.put(thread.getId(), 0);
            singleThreadState.owner = new WeakReference<>(thread);
        } else {
            assert threadStates[0] == singleThreadState;
            assert attachedThreads > 0;
            threadStateMapping.put(thread.getId(), attachedThreads);
            if (attachedThreads >= threadStates.length) {
                threadStates = Arrays.copyOf(threadStates, threadStates.length * 2);
            }
            threadStates[attachedThreads] = new PythonThreadState(thread);
        }
        attachedThreads++;
    }

    public synchronized void disposeThread(Thread thread) {
        CompilerAsserts.neverPartOfCompilation();
        long threadId = thread.getId();
        assert threadStateMapping.containsKey(threadId) : "thread was not attached to this context";
        // check if there is a live sentinel lock
        if (threadStates == null) {
            releaseSentinelLock(singleThreadState.sentinelLock);
        } else {
            int idx = threadStateMapping.get(threadId);
            releaseSentinelLock(threadStates[idx].sentinelLock);
        }
        threadStateMapping.remove(threadId);
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
}
