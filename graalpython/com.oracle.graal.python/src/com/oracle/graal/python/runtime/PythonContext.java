/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates.
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
import static com.oracle.graal.python.builtins.PythonOS.getPythonOS;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.options.OptionKey;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.modules.ctypes.CtypesModuleBuiltins.CtypesThreadState;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.PythonAbstractObjectFactory.PInteropGetAttributeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeClass;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext;
import com.oracle.graal.python.builtins.objects.cext.capi.PThreadState;
import com.oracle.graal.python.builtins.objects.cext.capi.PyDateTimeCAPIWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyTruffleObjectFree.ReleaseHandleNode;
import com.oracle.graal.python.builtins.objects.cext.capi.PyTruffleObjectFreeFactory.ReleaseHandleNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeNull;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDebugContext;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNativeSymbol;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.PCallHPyFunctionNodeGen;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.frame.PFrame.Reference;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.method.PMethod;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.thread.PLock;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.object.SetDictNode;
import com.oracle.graal.python.nodes.util.CastToJavaIntLossyNode;
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
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleContext.Builder;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.instrumentation.AllocationReporter;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.memory.MemoryFence;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.utilities.CyclicAssumption;
import com.oracle.truffle.api.utilities.TruffleWeakReference;
import com.oracle.truffle.llvm.api.Toolchain;

public final class PythonContext extends Python3Core {
    private static final Source IMPORT_WARNINGS_SOURCE = Source.newBuilder(PythonLanguage.ID, "import warnings\n", "<internal>").internal(true).build();
    private static final Source FORCE_IMPORTS_SOURCE = Source.newBuilder(PythonLanguage.ID, "import site\n", "<internal>").internal(true).build();
    private static final TruffleLogger LOGGER = PythonLanguage.getLogger(PythonContext.class);
    private volatile boolean finalizing;

    private static String getJniSoExt() {
        if (getPythonOS() == PLATFORM_DARWIN) {
            return ".dylib";
        }
        return ".so";
    }

    public static final String PYTHON_JNI_LIBRARY_NAME = System.getProperty("python.jni.library", "libpythonjni" + getJniSoExt());

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
        PException caughtException = PException.NO_EXCEPTION;

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
                        @Bind("getThreadState()") PythonThreadState curThreadState) {
            return curThreadState;
        }

        @Specialization(guards = {"noContext == null"}, replaces = "doNoShutdown")
        PythonThreadState doGeneric(@SuppressWarnings("unused") PythonContext noContext) {
            PythonThreadState curThreadState = PythonLanguage.get(this).getThreadStateLocal().get();
            if (curThreadState.isShuttingDown()) {
                PythonContext.get(this).killThread();
            }
            return curThreadState;
        }

        @Specialization(guards = "!curThreadState.isShuttingDown()")
        @SuppressWarnings("unused")
        static PythonThreadState doNoShutdownWithContext(PythonContext context,
                        @Bind("getThreadState()") PythonThreadState curThreadState) {
            return curThreadState;
        }

        @Specialization(replaces = "doNoShutdownWithContext")
        PythonThreadState doGenericWithContext(PythonContext context) {
            PythonThreadState curThreadState = PythonLanguage.get(this).getThreadStateLocal().get(context.env.getContext());
            if (CompilerDirectives.injectBranchProbability(CompilerDirectives.SLOWPATH_PROBABILITY, curThreadState.isShuttingDown())) {
                context.killThread();
            }
            return curThreadState;
        }

        PythonThreadState getThreadState() {
            return PythonLanguage.get(this).getThreadStateLocal().get();
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
    static final String NO_JNI = "could not determine Graal.Python's JNI library. You need to pass --python.JNILibrary if you want to run, for example, binary HPy extension modules.";

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
    private final Assumption nativeObjectsAllManagedAssumption = Truffle.getRuntime().createAssumption("all C API objects are managed");

    @CompilationFinal private TruffleLanguage.Env env;

    /* map of thread IDs to the corresponding 'threadStates' */
    private final Map<Thread, PythonThreadState> threadStateMapping = Collections.synchronizedMap(new WeakHashMap<>());
    private WeakReference<Thread> mainThread;

    private final ReentrantLock importLock = new ReentrantLock();
    @CompilationFinal private boolean isInitialized = false;
    private boolean isInitializedNonCompilationFinal;

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
    private final AsyncHandler handler;
    private final AsyncHandler.SharedFinalizer sharedFinalizer;

    // decides if we run the async weakref callbacks and destructors
    private boolean gcEnabled = true;

    // A thread-local to store the full path to the currently active import statement, for Jython
    // compat
    private final ThreadLocal<ArrayDeque<String>> currentImport = new ThreadLocal<>();

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
    private final WeakHashMap<CallTarget, String> codeFilename = new WeakHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> deserializationId = new ConcurrentHashMap<>();

    private final long perfCounterStart = ImageInfo.inImageBuildtimeCode() ? 0 : System.nanoTime();

    public static final String CHILD_CONTEXT_DATA = "childContextData";
    @CompilationFinal private List<Integer> childContextFDs;
    private final ChildContextData childContextData;
    private final SharedMultiprocessingData sharedMultiprocessingData;

    private final List<Object> codecSearchPath = new ArrayList<>();
    private final Map<String, PTuple> codecSearchCache = new HashMap<>();
    private final Map<String, Object> codecErrorRegistry = new HashMap<>();

    // the full module name for package imports
    private String pyPackageContext;

    private final PythonNativeNull nativeNull = new PythonNativeNull();

    public String getPyPackageContext() {
        return pyPackageContext;
    }

    public void setPyPackageContext(String pyPackageContext) {
        this.pyPackageContext = pyPackageContext;
    }

    public List<Object> getCodecSearchPath() {
        return codecSearchPath;
    }

    public Map<String, PTuple> getCodecSearchCache() {
        return codecSearchCache;
    }

    public Map<String, Object> getCodecErrorRegistry() {
        return codecErrorRegistry;
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

        public SharedMultiprocessingData(ConcurrentHashMap<String, Semaphore> namedSemaphores) {
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
        private final ConcurrentHashMap<String, Semaphore> namedSemaphores;

        @TruffleBoundary
        public void putNamedSemaphore(String name, Semaphore sem) {
            namedSemaphores.put(name, sem);
        }

        @TruffleBoundary
        public Semaphore getNamedSemaphore(String name) {
            return namedSemaphores.get(name);
        }

        @TruffleBoundary
        public Semaphore removeNamedSemaphore(String name) {
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

    public PythonContext(PythonLanguage language, TruffleLanguage.Env env, PythonParser parser) {
        super(language, parser, env.isNativeAccessAllowed());
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

    public PythonNativeNull getNativeNull() {
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

        Builder builder = data.parentCtx.env.newContextBuilder().config(PythonContext.CHILD_CONTEXT_DATA, data);
        Thread thread = data.parentCtx.env.createThread(new ChildContextThread(fd, sentinel, data, builder));

        // TODO always force java posix in spawned
        long tid = thread.getId();
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
                LOGGER.fine("starting spawned child context");
                Source source = Source.newBuilder(PythonLanguage.ID,
                                "from multiprocessing.spawn import spawn_truffleprocess; spawn_truffleprocess(" + fd + ", " + sentinel + ")",
                                "<spawned-child-context>").internal(true).build();
                CallTarget ct;
                TruffleContext ctx = builder.build();
                data.setTruffleContext(ctx);
                Object parent = ctx.enter(null);
                ct = PythonContext.get(null).getEnv().parsePublic(source);
                try {
                    data.running.countDown();
                    Object res = ct.call();
                    int exitCode = CastToJavaIntLossyNode.getUncached().execute(res);
                    data.setExitCode(exitCode);
                } finally {
                    ctx.leave(null, parent);
                    if (data.compareAndSetExiting(false, true)) {
                        try {
                            ctx.close();
                            LOGGER.log(Level.FINE, "closed spawned child context");
                        } catch (Throwable t) {
                            LOGGER.log(Level.FINE, t, () -> "exception while closing spawned child context");
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

    public ReentrantLock getImportLock() {
        return importLock;
    }

    public PMethod importFunc() {
        return getImportFunc();
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
                throw new RuntimeException("Unable to obtain entropy source for random number generation (NativePRNGNonBlocking)", e);
            }
        }
        return secureRandom;
    }

    public byte[] getHashSecret() {
        assert !ImageInfo.inImageBuildtimeCode();
        return hashSecret;
    }

    public boolean isInitialized() {
        if (PythonUtils.ASSERTIONS_ENABLED && isInitializedNonCompilationFinal != isInitialized) {
            // We cannot use normal assertion, because those are removed in compilation
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw CompilerDirectives.shouldNotReachHere(String.format("%b != %b", isInitializedNonCompilationFinal, isInitialized));
        }
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
            CallTarget site = env.parsePublic(FORCE_IMPORTS_SOURCE);
            site.call();
        }
        if (!getOption(PythonOptions.WarnOptions).isEmpty()) {
            // we must force an import of the warnings module here if warnings were passed
            CallTarget site = env.parsePublic(IMPORT_WARNINGS_SOURCE);
            site.call();
        }
        if (getOption(PythonOptions.InputFilePath).isEmpty()) {
            // When InputFilePath is set, this is handled by __graalpython__.run_path
            addSysPath0();
        }
    }

    public void addSysPath0() {
        if (!getOption(PythonOptions.IsolateFlag)) {
            String path0 = computeSysPath0();
            if (path0 != null) {
                PythonModule sys = lookupBuiltinModule("sys");
                Object path = sys.getAttribute("path");
                PyObjectCallMethodObjArgs.getUncached().execute(null, path, "insert", 0, path0);
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
                    ((PythonModule) v).setAttribute(SpecialAttributeNames.__PATH__, factory().createList(paths));
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
        if (!ImageInfo.inImageBuildtimeCode()) {
            initializeHashSecret();
        }
        nativeZlib = NFIZlibSupport.createNative(this, "");
        nativeBz2lib = NFIBz2Support.createNative(this, "");
        nativeLZMA = NFILZMASupport.createNative(this, "");

        mainModule = factory().createPythonModule(__MAIN__);
        mainModule.setAttribute(__BUILTINS__, getBuiltins());
        mainModule.setAttribute(__ANNOTATIONS__, factory().createDict());
        SetDictNode.getUncached().execute(mainModule, factory().createDictFixedStorage(mainModule));
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
        isInitializedNonCompilationFinal = true;
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

    private void initializePosixSupport() {
        String option = getLanguage().getEngineOption(PythonOptions.PosixModuleBackend);
        PosixSupport result;
        // The resources field will be removed once all posix builtins go through PosixSupport
        switch (option) {
            case "java":
                result = new EmulatedPosixSupport(this);
                break;
            case "native":
            case "llvm":
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

    private String sysPrefix, basePrefix, coreHome, stdLibHome, capiHome, jniHome;

    public void initializeHomeAndPrefixPaths(Env newEnv, String languageHome) {
        sysPrefix = newEnv.getOptions().get(PythonOptions.SysPrefix);
        basePrefix = newEnv.getOptions().get(PythonOptions.SysBasePrefix);
        coreHome = newEnv.getOptions().get(PythonOptions.CoreHome);
        stdLibHome = newEnv.getOptions().get(PythonOptions.StdLibHome);
        capiHome = newEnv.getOptions().get(PythonOptions.CAPI);
        jniHome = newEnv.getOptions().get(PythonOptions.JNIHome);

        Python3Core.writeInfo(() -> MessageFormat.format("Initial locations:" +
                        "\n\tLanguage home: {0}" +
                        "\n\tSysPrefix: {1}" +
                        "\n\tBaseSysPrefix: {2}" +
                        "\n\tCoreHome: {3}" +
                        "\n\tStdLibHome: {4}" +
                        "\n\tCAPI: {5}" +
                        "\n\tJNI library: {6}", languageHome, sysPrefix, basePrefix, coreHome, stdLibHome, capiHome, jniHome));

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

            if (jniHome.isEmpty()) {
                jniHome = coreHome;
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
            jniHome = base.relativize(newEnv.getInternalTruffleFile(jniHome)).getPath();
        }

        Python3Core.writeInfo(() -> MessageFormat.format("Updated locations:" +
                        "\n\tLanguage home: {0}" +
                        "\n\tSysPrefix: {1}" +
                        "\n\tBaseSysPrefix: {2}" +
                        "\n\tCoreHome: {3}" +
                        "\n\tStdLibHome: {4}" +
                        "\n\tExecutable: {5}" +
                        "\n\tCAPI: {6}" +
                        "\n\tJNI library: {7}", home != null ? home.getPath() : "", sysPrefix, basePrefix, coreHome, stdLibHome, newEnv.getOptions().get(PythonOptions.Executable), capiHome,
                        jniHome));
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
            String homePrefix = getLanguage().getHome();
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

    @TruffleBoundary
    public String getJNIHome() {
        if (jniHome.isEmpty()) {
            writeWarning(NO_JNI);
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
                        env.submitThreadLocal(new Thread[]{thread}, new ThreadLocalAction(true, false) {
                            @Override
                            protected void perform(ThreadLocalAction.Access access) {
                                throw new PythonThreadKillException();
                            }
                        });
                        if (isOurThread) {
                            thread.interrupt();
                        }
                        thread.join(2);
                    }
                    if (isOurThread) {
                        // Thread#stop is not supported on SVM
                        if (!ImageInfo.inImageCode()) {
                            if (thread.isAlive()) {
                                LOGGER.warning("could not join thread " + thread.getName() + ". Trying to stop it.");
                            }
                            thread.stop();
                        }
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
        // Other threads must see the nativeWrapper fully initialized once it becomes non-null
        MemoryFence.storeStore();
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
        String languageHome = getLanguage().getHome();

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
        getSharedMultiprocessingData().removeChildContextThread(thread.getId());
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

        PyDateTimeCAPIWrapper.initWrapper(capiContext);

        for (Runnable capiHook : capiHooks) {
            capiHook.run();
        }
        capiHooks.clear();
    }

    public boolean hasHPyContext() {
        return hPyContext != null;
    }

    public synchronized GraalHPyContext createHPyContext(Object hpyLibrary) {
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
        GraalHPyDebugContext debugCtx = new GraalHPyDebugContext(hPyContext);
        PCallHPyFunctionNodeGen.getUncached().call(debugCtx, GraalHPyNativeSymbol.GRAAL_HPY_SET_DEBUG_CONTEXT, debugCtx);
        return debugCtx;
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
            PythonModule sysModule = this.lookupBuiltinModule("sys");
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
            if (getPythonOS() == PLATFORM_DARWIN && "native".equals(toolchainId)) {
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
