/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.capi;

import static com.oracle.graal.python.PythonLanguage.CONTEXT_INSENSITIVE_SINGLETONS;
import static com.oracle.graal.python.builtins.objects.PythonAbstractObject.UNINITIALIZED;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.pollReferenceQueue;
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.readIntField;
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.readLongField;
import static com.oracle.graal.python.builtins.objects.object.PythonObject.IMMORTAL_REFCNT;
import static com.oracle.graal.python.nfi2.NativeMemory.NULLPTR;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___FILE__;
import static com.oracle.graal.python.nodes.StringLiterals.T_DASH;
import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_STRING;
import static com.oracle.graal.python.nodes.StringLiterals.T_UNDERSCORE;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

import org.graalvm.collections.Pair;
import org.graalvm.shadowed.com.ibm.icu.impl.Punycode;
import org.graalvm.shadowed.com.ibm.icu.text.StringPrepParseException;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.cext.PythonCApiAssertions;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinRegistry;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltinExecutable;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.capsule.PyCapsule;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodesFactory.PyObjectCheckFunctionResultNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.FirstToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.HandleContext;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.HandlePointerConverter;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeInternalNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtContext;
import com.oracle.graal.python.builtins.objects.cext.common.LoadCExtException.ApiInitException;
import com.oracle.graal.python.builtins.objects.cext.common.LoadCExtException.ImportException;
import com.oracle.graal.python.builtins.objects.cext.common.NativePointer;
import com.oracle.graal.python.builtins.objects.cext.copying.NativeLibraryLocator;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructs;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.str.StringNodes;
import com.oracle.graal.python.builtins.objects.str.StringUtils;
import com.oracle.graal.python.builtins.objects.thread.PLock;
import com.oracle.graal.python.nfi2.NativeMemory;
import com.oracle.graal.python.nfi2.Nfi;
import com.oracle.graal.python.nfi2.NfiBoundFunction;
import com.oracle.graal.python.nfi2.NfiContext;
import com.oracle.graal.python.nfi2.NfiDowncallSignature;
import com.oracle.graal.python.nfi2.NfiLibrary;
import com.oracle.graal.python.nfi2.NfiType;
import com.oracle.graal.python.nfi2.NfiUpcallSignature;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.statement.AbstractImportNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PosixConstants;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonContext.CApiState;
import com.oracle.graal.python.runtime.PythonContext.PythonThreadState;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.util.ConcurrentWeakSet;
import com.oracle.graal.python.util.Function;
import com.oracle.graal.python.util.PythonSystemThreadTask;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.graal.python.util.SuppressFBWarnings;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.ThreadLocalAction;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.CodeRange;

import sun.misc.Unsafe;

public final class CApiContext extends CExtContext {
    private static final TruffleString T_PY_INIT = tsLiteral("PyInit_");
    private static final TruffleString T_PY_INIT_U = tsLiteral("PyInitU_");

    public static final String LOGGER_CAPI_NAME = "capi";

    /**
     * NFI signature for Python module init functions (i.e. {@code "PyInit_modname"}).
     */
    private static final NfiDowncallSignature MODINIT_SIGNATURE = Nfi.createDowncallSignature(NfiType.RAW_POINTER);

    private static final TruffleLogger LOGGER = PythonLanguage.getLogger(LOGGER_CAPI_NAME);
    public static final TruffleLogger GC_LOGGER = PythonLanguage.getLogger(CApiContext.LOGGER_CAPI_NAME + ".gc");

    /** Native pointers for context-insensitive singletons like {@link PNone#NONE}. */
    @CompilationFinal(dimensions = 1) private final long[] singletonNativePtrs;

    /**
     * Pointer to the native {@code GCState GC state}. This corresponds to CPython's
     * {@code PyInterpreterState.gc}.
     */
    private long gcState;

    /** Same as {@code import.c: extensions} but we don't keep a PDict; just a bare Java HashMap. */
    private final HashMap<Pair<TruffleString, TruffleString>, PythonModule> extensions = new HashMap<>(4);

    /** corresponds to {@code unicodeobject.c: interned} */
    private final ConcurrentWeakSet<PString> pstringInterningCache = new ConcurrentWeakSet<>();
    private final ArrayList<Object> modulesByIndex = new ArrayList<>(0);

    public final ConcurrentHashMap<Long, PLock> locks = new ConcurrentHashMap<>();
    public final AtomicLong lockId = new AtomicLong();

    /**
     * Thread local storage for PyThread_tss_* APIs
     */
    private final ConcurrentHashMap<Long, ThreadLocal<Long>> tssStorage = new ConcurrentHashMap<>();
    /**
     * Next key that will be allocated byt PyThread_tss_create
     */
    private final AtomicLong nextTssKey = new AtomicLong();

    public Object timezoneType;
    private PyCapsule pyDateTimeCAPICapsule;

    /**
     * Same as {@link #nativeSymbolCache} if there is only one context per JVM (i.e. just one engine
     * in single-context mode). Will be {@code null} in case of multiple contexts.
     */
    @CompilationFinal(dimensions = 1) private static NfiBoundFunction[] nativeSymbolCacheSingleContext;
    private static boolean nativeSymbolCacheSingleContextUsed;

    /**
     * A private (i.e. per-context) cache of C API symbols (usually helper functions).
     */
    private final NfiBoundFunction[] nativeSymbolCache;

    public static boolean isSpecialSingleton(Object delegate) {
        return getSingletonNativeWrapperIdx(delegate) != -1;
    }

    private record ClosureInfo(Object delegate, Object executable, long pointer) {
    }

    /**
     * A simple helper object that just remembers the name and the path of the original module spec
     * object and also keeps a reference to it. This should avoid redundant attribute reads.
     */
    @ValueType
    public static final class ModuleSpec {
        public final TruffleString name;
        public final TruffleString path;
        public final Object originalModuleSpec;
        private TruffleString encodedName;
        private boolean ascii;

        public ModuleSpec(TruffleString name, TruffleString path, Object originalModuleSpec) {
            this.name = name;
            this.path = path;
            this.originalModuleSpec = originalModuleSpec;
        }

        /**
         * Get the variable part of a module's export symbol name. For non-ASCII-named modules, the
         * name is encoded as per PEP 489. The hook_prefix pointer is set to either
         * ascii_only_prefix or nonascii_prefix, as appropriate.
         */
        @TruffleBoundary
        TruffleString getEncodedName() {
            if (encodedName != null) {
                return encodedName;
            }

            // Get the short name (substring after last dot)
            TruffleString basename = getBaseName(name);

            boolean canEncode = canEncode(basename);

            if (canEncode) {
                ascii = true;
            } else {
                ascii = false;
                try {
                    basename = TruffleString.fromJavaStringUncached(Punycode.encode(basename.toJavaStringUncached(), null).toString(), PythonUtils.TS_ENCODING);
                } catch (StringPrepParseException e) {
                    throw CompilerDirectives.shouldNotReachHere();
                }
            }

            // replace '-' by '_'; note: this is fast and does not use regex
            return (encodedName = StringNodes.StringReplaceNode.getUncached().execute(basename, T_DASH, T_UNDERSCORE, -1));
        }

        @TruffleBoundary
        private static boolean canEncode(TruffleString basename) {
            return TruffleString.GetCodeRangeNode.getUncached().execute(basename, PythonUtils.TS_ENCODING) == CodeRange.ASCII;
        }

        @TruffleBoundary
        public TruffleString getInitFunctionName() {
            /*
             * n.b.: 'getEncodedName' also sets 'ascii' and must therefore be called before 'ascii'
             * is queried
             */
            TruffleString s = getEncodedName();
            return StringUtils.cat((ascii ? T_PY_INIT : T_PY_INIT_U), s);
        }
    }

    /*
     * The key is the executable instance, i.e., an instance of a class that exports the
     * InteropLibrary.
     */
    private final HashMap<Object, ClosureInfo> callableClosureByExecutable = new HashMap<>();
    private final HashMap<Long, ClosureInfo> callableClosures = new HashMap<>();

    /**
     * Table of all requested {@code PyMethodDef} structures. We keep them in this table because in
     * CPython, those are usually statically allocated (or at least immortal) and once hand out a
     * pointer for a {@code PyMethodDef}, we need to ensure that it stays valid until the end.
     */
    private final HashMap<PyMethodDefHelper, Long> methodDefinitions = new HashMap<>(4);

    /**
     * This list holds a strong reference to all loaded extension libraries to keep the library
     * objects alive. This is necessary because NFI will {@code dlclose} the library (and thus
     * {@code munmap} all code) if the library object is no longer reachable. However, it can happen
     * that we still store raw function pointers (as Java {@code long} values) in a native object
     * that is referenced by a
     * {@link com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeObjectReference}.
     * For example, the {code tp_dealloc} functions may be executed a long time after all managed
     * objects of the extension died and the native library has been {@code dlclosed}'d.
     *
     * Since we have no control over the timing when certain garbage will be collected, we need to
     * ensure that the code is still mapped.
     */
    private final List<Object> loadedExtensions = new LinkedList<>();

    private final NativeLibraryLocator nativeLibraryLocator;

    public final BackgroundGCTask gcTask;
    private Thread backgroundGCTaskThread;

    public static TruffleLogger getLogger(Class<?> clazz) {
        return PythonLanguage.getLogger(LOGGER_CAPI_NAME + "." + clazz.getSimpleName());
    }

    public CApiContext(PythonContext context, NfiLibrary library, NativeLibraryLocator locator) {
        super(context, library, locator.getCapiLibrary());
        this.nativeSymbolCache = new NfiBoundFunction[NativeCAPISymbol.values().length];
        this.nativeLibraryLocator = locator;

        /*
         * Publish the native symbol cache to the static field if following is given: (1) The static
         * field hasn't been used by another instance yet (i.e. '!used'), and (2) we are in
         * single-context mode. This initialization ensures that if
         * 'CApiContext.nativeSymbolCacheSingleContext != null', the context is safe to use it and
         * just needs to do a null check.
         */
        synchronized (CApiContext.class) {
            if (!CApiContext.nativeSymbolCacheSingleContextUsed && context.getLanguage().isSingleContext()) {
                assert CApiContext.nativeSymbolCacheSingleContext == null;

                assert !context.getEnv().isPreInitialization();

                // this is the first context accessing the static symbol cache
                CApiContext.nativeSymbolCacheSingleContext = this.nativeSymbolCache;
            } else if (CApiContext.nativeSymbolCacheSingleContext != null) {
                assert CApiContext.nativeSymbolCacheSingleContextUsed;
                /*
                 * In this case, this context instance is at least the second one attempting to use
                 * the static symbol cache. We now clear the static field to indicate that every
                 * context instance should use its private cache. If a former context already used
                 * the cache and there is already compiled code, it is not necessary to invalidate
                 * the code because the cache is still valid.
                 */
                CApiContext.nativeSymbolCacheSingleContext = null;
            }
            CApiContext.nativeSymbolCacheSingleContextUsed = true;
        }

        // initialize singleton native pointers array
        singletonNativePtrs = new long[CONTEXT_INSENSITIVE_SINGLETONS.length];
        Arrays.fill(singletonNativePtrs, UNINITIALIZED);

        this.gcTask = new BackgroundGCTask(context);
    }

    @TruffleBoundary
    void addLoadedExtensionLibrary(Object nativeLibrary) {
        loadedExtensions.add(nativeLibrary);
    }

    @TruffleBoundary
    public static Object asHex(Object ptr) {
        if (ptr instanceof Number) {
            return "0x" + Long.toHexString(((Number) ptr).longValue());
        }
        return Objects.toString(ptr);
    }

    public ConcurrentWeakSet<PString> getPstringInterningCache() {
        return pstringInterningCache;
    }

    /**
     * Tries to convert the object to a pointer (type: {@code long}) to avoid materialization of
     * pointer objects. If that is not possible, the object will be returned as given.
     */
    public static Object asPointer(Object ptr, InteropLibrary lib) {
        if (lib.isPointer(ptr)) {
            try {
                return lib.asPointer(ptr);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
        return ptr;
    }

    public long nextTssKey() {
        return nextTssKey.incrementAndGet();
    }

    @TruffleBoundary
    public long tssGet(long key) {
        ThreadLocal<Long> local = tssStorage.get(key);
        if (local != null) {
            return local.get();
        }
        return NULLPTR;
    }

    @TruffleBoundary
    public void tssSet(long key, long ptr) {
        tssStorage.computeIfAbsent(key, (k) -> new ThreadLocal<>()).set(ptr);
    }

    @TruffleBoundary
    public void tssDelete(long key) {
        tssStorage.remove(key);
    }

    @ExplodeLoop(kind = LoopExplosionKind.FULL_UNROLL_UNTIL_RETURN)
    static int getSingletonNativeWrapperIdx(Object obj) {
        for (int i = 0; i < CONTEXT_INSENSITIVE_SINGLETONS.length; i++) {
            if (CONTEXT_INSENSITIVE_SINGLETONS[i] == obj) {
                return i;
            }
        }
        return -1;
    }

    public long getNonePtr() {
        return getSingletonNativeWrapper(PNone.NONE);
    }

    public long getSingletonNativeWrapper(PythonAbstractObject obj) {
        int singletonNativePtrIdx = CApiContext.getSingletonNativeWrapperIdx(obj);
        if (singletonNativePtrIdx != -1) {
            long singletonNativePtr = singletonNativePtrs[singletonNativePtrIdx];
            if (CompilerDirectives.injectBranchProbability(CompilerDirectives.SLOWPATH_PROBABILITY, singletonNativePtr == UNINITIALIZED)) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                initializeSingletonNativePtrs();
                singletonNativePtr = singletonNativePtrs[singletonNativePtrIdx];
            }
            return singletonNativePtr;
        }
        return 0;
    }

    private void initializeSingletonNativePtrs() {
        CompilerAsserts.neverPartOfCompilation();
        assert getContext().getCApiState() == CApiState.INITIALIZING || getContext().getCApiState() == CApiState.INITIALIZED;
        for (int i = 0; i < singletonNativePtrs.length; i++) {
            assert isSpecialSingleton(CONTEXT_INSENSITIVE_SINGLETONS[i]);
            assert !PythonToNativeInternalNode.mapsToNull(CONTEXT_INSENSITIVE_SINGLETONS[i]);
            assert singletonNativePtrs[i] == UNINITIALIZED;
            singletonNativePtrs[i] = FirstToNativeNode.executeUncached(CONTEXT_INSENSITIVE_SINGLETONS[i], IMMORTAL_REFCNT);
            assert singletonNativePtrs[i] != NULLPTR;
            assert singletonNativePtrs[i] != UNINITIALIZED;
        }
    }

    /**
     * Deallocates all singleton wrappers (in {@link #singletonNativePtrs}) which are immortal and
     * must therefore be explicitly free'd. This method modifies the
     * {@link HandleContext#nativeStubLookup stub lookup table} but runs not guest code.
     */
    private void freeSingletonNativeWrappers(HandleContext handleContext) {
        CompilerAsserts.neverPartOfCompilation();
        // TODO(fa): this should not require the GIL (GR-51314)
        assert getContext().ownsGil();
        for (int i = 0; i < singletonNativePtrs.length; i++) {
            long pointer = singletonNativePtrs[i];
            assert pointer == PythonObject.UNINITIALIZED || CApiTransitions.readNativeRefCount(HandlePointerConverter.pointerToStub(pointer)) == IMMORTAL_REFCNT;
            singletonNativePtrs[i] = PythonObject.NATIVE_POINTER_FREED;
            // It may be that the singleton was never used in native and there is nothing to free.
            if (pointer != PythonObject.UNINITIALIZED) {
                assert HandlePointerConverter.pointsToPyHandleSpace(pointer);
                int handleTableIndex = readIntField(HandlePointerConverter.pointerToStub(pointer), CFields.GraalPyObject__handle_table_index);
                assert CApiTransitions.nativeStubLookupGet(handleContext, pointer, handleTableIndex) instanceof PythonAbstractObject : "immortal objects should not have a weak ref";
                CApiTransitions.nativeStubLookupRemove(handleContext, handleTableIndex);
                CApiTransitions.releaseNativeWrapper(pointer);
            }
        }
    }

    /**
     * Allocates the {@code GCState} which needs to happen very early in the C API initialization
     * phase. <it>Very early</it> means it needs to happen before the first object (that takes part
     * in the GC) is sent to native. This could, e.g., be the thread-state dict that is allocated
     * when creating the {@link PThreadState native thread state}.
     */
    public long createGCState() {
        CompilerAsserts.neverPartOfCompilation();
        assert gcState == 0L;
        PythonContext.GCState state = getContext().getGcState();
        long ptr = CStructAccess.allocate(CStructs.GCState);
        CStructAccess.writeIntField(ptr, CFields.GCState__enabled, PInt.intValue(state.isEnabled()));
        CStructAccess.writeIntField(ptr, CFields.GCState__debug, state.getDebug());
        long generations = CStructAccess.getFieldPtr(ptr, CFields.GCState__generations);
        for (int i = 0; i < state.getThresholds().length; i++) {
            CStructAccess.writeStructArrayIntField(generations, i, CFields.GCGeneration__threshold, state.getThresholds()[i]);
        }
        gcState = ptr;
        return gcState;
    }

    /**
     * Fast-path method to retrieve the {@code GCState} pointer. This must only be called after
     * {@link #createGCState()} was called the first time which should happen very early during C
     * API context initialization.
     */
    public long getGCState() {
        assert gcState != 0L;
        return gcState;
    }

    /**
     * Deallocates the native {@code GCState} (pointer {@link #gcState}).
     */
    private void freeGCState() {
        CompilerAsserts.neverPartOfCompilation();
        if (gcState != 0L) {
            LOGGER.fine(String.format("Freeing GC state at 0x%x", gcState));
            NativeMemory.free(gcState);
            gcState = 0L;
        }
    }

    public Object getModuleByIndex(int i) {
        if (i < modulesByIndex.size()) {
            return modulesByIndex.get(i);
        }
        return null;
    }

    /**
     * Retrieves the C API symbol cache instance in the fastest possible way. If there is just one
     * instance of {@link CApiContext}, it will load the cache stored from the static field
     * {@link CApiContext#nativeSymbolCacheSingleContext}. Otherwise, it will load the cache from
     * the instance field {@link CApiContext#nativeSymbolCache}.
     *
     * @param caller The requesting node (may be {@code null}). Used for the fast-path lookup of the
     *            {@link CApiContext} instance (if necessary).
     * @return The C API symbol cache.
     */
    private static NfiBoundFunction[] getSymbolCache(Node caller) {
        NfiBoundFunction[] cache = nativeSymbolCacheSingleContext;
        if (cache != null) {
            return cache;
        }
        return PythonContext.get(caller).getCApiContext().nativeSymbolCache;
    }

    public static boolean isIdenticalToSymbol(Object obj, NativeCAPISymbol symbol) {
        CompilerAsserts.neverPartOfCompilation();
        InteropLibrary objLib = InteropLibrary.getUncached(obj);
        objLib.toNative(obj);
        try {
            return isIdenticalToSymbol(objLib.asPointer(obj), symbol);
        } catch (UnsupportedMessageException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean isIdenticalToSymbol(long ptr, NativeCAPISymbol symbol) {
        CompilerAsserts.neverPartOfCompilation();
        NfiBoundFunction nativeSymbol = getNativeSymbol(null, symbol);
        return nativeSymbol.getAddress() == ptr;
    }

    public static NfiBoundFunction getNativeSymbol(Node caller, NativeCAPISymbol symbol) {
        NfiBoundFunction[] nativeSymbolCache = getSymbolCache(caller);
        NfiBoundFunction result = nativeSymbolCache[symbol.ordinal()];
        if (result == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            result = lookupNativeSymbol(nativeSymbolCache, symbol);
        }
        assert result != null;
        return result;
    }

    /**
     * Lookup the given C API symbol in the library, store it to the provided cache, and return the
     * callable symbol.
     */
    private static NfiBoundFunction lookupNativeSymbol(NfiBoundFunction[] nativeSymbolCache, NativeCAPISymbol symbol) {
        CompilerAsserts.neverPartOfCompilation();
        String name = symbol.getName();
        PythonContext pythonContext = PythonContext.get(null);
        long nativeSymbolPtr = pythonContext.getCApiContext().getLibrary().lookupSymbol(name);
        NfiBoundFunction nativeSymbol = symbol.getSignature().bind(pythonContext.ensureNfiContext(), nativeSymbolPtr);
        VarHandle.storeStoreFence();
        return nativeSymbolCache[symbol.ordinal()] = nativeSymbol;
    }

    @SuppressWarnings("unused")
    public void trackObject(long ptr, PFrame.Reference curFrame, TruffleString clazzName) {
        // TODO(fa): implement tracking of container objects for cycle detection
    }

    @SuppressWarnings("unused")
    public void untrackObject(long ptr, PFrame.Reference curFrame, TruffleString clazzName) {
        // TODO(fa): implement untracking of container objects
    }

    private static final class BackgroundGCTask extends PythonSystemThreadTask {

        private BackgroundGCTask(PythonContext context) {
            super("Python GC", LOGGER);
            this.ctx = new WeakReference<>(context);
            this.rssInterval = context.getOption(PythonOptions.BackgroundGCTaskInterval);
            this.gcRSSThreshold = context.getOption(PythonOptions.BackgroundGCTaskThreshold) / (double) 100;
            this.gcRSSMinimum = context.getOption(PythonOptions.BackgroundGCTaskMinimum);
        }

        NfiBoundFunction nativeSymbol = null;

        long currentRSS = -1;
        long previousRSS = -1;
        int previousWeakrefCount = -1;

        final WeakReference<PythonContext> ctx;

        // RSS monitor interval in ms
        final int rssInterval;
        /**
         * RSS percentage increase between System.gc() calls. Low percentage will trigger
         * System.gc() more often which can cause unnecessary overhead.
         *
         * <ul>
         * Based on the {@code huggingface} example:
         * <li>less than 30%: max RSS ~22GB (>200 second per iteration)</li>
         * <li>30%: max RSS ~24GB (~150 second per iteration)</li>
         * <li>larger than 30%: max RSS ~38GB (~140 second per iteration)</li>
         * </ul>
         *
         * <pre>
         */
        final double gcRSSThreshold;

        /**
         * RSS minimum memory (in megabytes) start calling System.gc(). Default is 4GB.
         */
        final double gcRSSMinimum;

        Long getCurrentRSS() {
            if (nativeSymbol == null) {
                nativeSymbol = CApiContext.getNativeSymbol(null, NativeCAPISymbol.FUN_GET_CURRENT_RSS);
            }
            Long rss = 0L;
            try {
                rss = (Long) nativeSymbol.invoke();
            } catch (Exception ignored) {
            }
            return rss;
        }

        @Override
        protected void doRun() {
            Node location = getSafepointLocation();
            if (location == null) {
                return;
            }
            while (true) {
                TruffleSafepoint.setBlockedThreadInterruptible(location, Thread::sleep, rssInterval);
                perform();
            }
        }

        private Node getSafepointLocation() {
            PythonContext context = ctx.get();
            if (context == null) {
                return null;
            }
            return context.getLanguage().unavailableSafepointLocation;
        }

        private void perform() {
            PythonContext context = ctx.get();
            if (context == null) {
                return;
            }

            long rss = currentRSS = getCurrentRSS();
            if (rss == 0) {
                LOGGER.finer("We are unable to get resident set size (RSS) from the system. " +
                                "We will skip the java collection routine.");
                Thread.currentThread().interrupt();
                return;
            }

            // reset RSS baseline
            if (rss < this.previousRSS || this.previousRSS == -1) {
                this.previousRSS = rss;
                return;
            }

            if (rss < gcRSSMinimum) {
                return;
            }

            // skip GC if no new native weakrefs have been created.
            int currentWeakrefCount = context.nativeContext.nativeLookup.size();
            if (currentWeakrefCount < this.previousWeakrefCount || this.previousWeakrefCount == -1) {
                this.previousWeakrefCount = currentWeakrefCount;
                return;
            }

            double ratio = ((rss - this.previousRSS) / (double) this.previousRSS);
            if (ratio >= gcRSSThreshold) {
                this.previousWeakrefCount = currentWeakrefCount;

                long start = System.nanoTime();
                PythonUtils.forceFullGC();
                long gcTime = (System.nanoTime() - start) / 1000000;

                if (LOGGER.isLoggable(Level.FINER)) {
                    LOGGER.info(PythonUtils.formatJString("Background GC Task -- GC [%d ms] RSS [%d MB]->[%d MB](%.1f%%)",
                                    gcTime, previousRSS, rss, ratio * 100));
                }
                /*
                 * cap the previous RSS increase to GC_RSS_THRESHOLD. If the ratio is much larger
                 * than GC_RSS_THRESHOLD, then we should do GC more frequently. Though, if we get a
                 * lower RSS in subsequent runs, the lower RSS will be set as previous RSS (see
                 * above).
                 *
                 * Note: Resident Set Size (RSS) in the system isn't always an accurate indication
                 * of used memory but rather a combination of anonymous memory (RssAnon), file
                 * mappings (RssFile) and shmem memory (RssShmem). GC can only reduce RssAnon while
                 * RssFile is managed by the operating system which doesn't go down easily.
                 */
                this.previousRSS += (long) (this.previousRSS * gcRSSThreshold);
            }
        }
    }

    @TruffleBoundary
    public long getCurrentRSS() {
        if (backgroundGCTaskThread != null && backgroundGCTaskThread.isAlive()) {
            long rss = gcTask.currentRSS;
            if (rss == -1) {
                try {
                    // in case it just started
                    Thread.sleep(gcTask.rssInterval);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                rss = gcTask.currentRSS;
            }
            return rss;
        }
        return 0L;
    }

    @SuppressFBWarnings(value = "NP_NULL_ON_SOME_PATH") // context.get() is never null here
    void runBackgroundGCTask(PythonContext context) {
        CompilerAsserts.neverPartOfCompilation();
        if (context.getEnv().isPreInitialization() //
                        || context.getOption(PythonOptions.NoAsyncActions) //
                        || !PythonOptions.AUTOMATIC_ASYNC_ACTIONS //
                        || !context.getOption(PythonOptions.BackgroundGCTask)) {
            return;
        }
        backgroundGCTaskThread = context.createSystemThread(gcTask);
        backgroundGCTaskThread.start();
    }

    /**
     * This represents whether the current process has already loaded an instance of the native CAPI
     * extensions - this can only be loaded globally once per process or in isolation multiple
     * times.
     */
    private static final AtomicInteger nativeCAPILoaded = new AtomicInteger();
    private static final byte NO_NATIVE_CONTEXT = 0;
    private static final byte ISOLATED_NATIVE_CONTEXT = 1;
    private static final byte GLOBAL_NATIVE_CONTEXT = 2;

    private Runnable nativeFinalizerRunnable;
    private Thread nativeFinalizerShutdownHook;

    @TruffleBoundary
    public static CApiContext ensureCapiWasLoaded(String reason) {
        try {
            return CApiContext.ensureCapiWasLoaded(null, PythonContext.get(null), T_EMPTY_STRING, T_EMPTY_STRING, reason);
        } catch (Exception e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }

    @TruffleBoundary
    public static CApiContext ensureCapiWasLoaded(Node node, PythonContext context, TruffleString name, TruffleString path) throws IOException, ImportException, ApiInitException {
        return ensureCapiWasLoaded(node, context, name, path, null);
    }

    @TruffleBoundary
    @SuppressWarnings("try")
    public static CApiContext ensureCapiWasLoaded(Node node, PythonContext context, TruffleString name, TruffleString path, String reason) throws IOException, ImportException, ApiInitException {
        assert PythonContext.get(null).ownsGil(); // unsafe lazy initialization
        // The initialization may run Python code (e.g., module import in
        // GraalPyPrivate_InitBuiltinTypesAndStructs), so just holding the GIL is not enough
        if (context.getCApiState() != PythonContext.CApiState.INITIALIZED) {

            // We import those modules ahead of the initialization without the initialization lock
            // to avoid deadlocks. We would have imported them in the initialization anyway, this
            // way we can just simply look up already imported modules during the initialization
            // without running the complex import machinery and risking a deadlock
            AbstractImportNode.importModule(toTruffleStringUncached("datetime"));
            AbstractImportNode.importModule(toTruffleStringUncached("types"));

            ReentrantLock initLock = context.getcApiInitializationLock();
            try (GilNode.UncachedRelease ignored = GilNode.uncachedRelease()) {
                TruffleSafepoint.setBlockedThreadInterruptible(node, ReentrantLock::lockInterruptibly, initLock);
            }
            try {
                PythonContext.CApiState state = context.getCApiState();
                if (state == PythonContext.CApiState.INITIALIZED || state == PythonContext.CApiState.INITIALIZING) {
                    return context.getCApiContext();
                }
                if (state == PythonContext.CApiState.FAILED) {
                    throw new ApiInitException(toTruffleStringUncached("The C API initialization has previously failed."));
                }

                assert state == PythonContext.CApiState.UNINITIALIZED : state;
                // loadCApi must set C API context half-way through its execution so that it can
                // run internal Java code that needs C API context
                TruffleSafepoint safepoint = TruffleSafepoint.getCurrent();
                boolean prevAllowSideEffects = safepoint.setAllowSideEffects(false);
                try {
                    CApiContext cApiContext = loadCApi(node, context, name, path, reason);
                    assert context.getCApiState() == PythonContext.CApiState.INITIALIZING;
                    initializeThreadStateCurrentForAttachedThreads(context);
                    CApiTransitions.initializeReferenceQueuePolling(context.nativeContext);
                    context.runCApiHooks();
                    context.setCApiState(PythonContext.CApiState.INITIALIZED); // volatile write
                    try {
                        cApiContext.runBackgroundGCTask(context);
                    } catch (RuntimeException e) {
                        // This can happen when other languages restrict multithreading
                        LOGGER.warning(() -> "didn't start the background GC task due to: " + e.getMessage());
                    }
                } catch (Throwable t) {
                    context.setCApiState(PythonContext.CApiState.FAILED);
                    throw t;
                } finally {
                    safepoint.setAllowSideEffects(prevAllowSideEffects);
                }
            } finally {
                initLock.unlock();
            }
        }
        return context.getCApiContext();
    }

    private static void initializeThreadStateCurrentForAttachedThreads(PythonContext context) {
        Thread[] threads = getOtherAliveAttachedThreads(context);
        if (threads.length == 0) {
            return;
        }
        ThreadLocalAction action = new ThreadLocalAction(true, false) {
            @Override
            protected void perform(ThreadLocalAction.Access access) {
                context.initializeNativeThreadState();
            }
        };
        context.getEnv().submitThreadLocal(threads, action);
    }

    private static Thread[] getOtherAliveAttachedThreads(PythonContext context) {
        Thread currentThread = Thread.currentThread();
        ArrayList<Thread> threads = new ArrayList<>();
        for (Thread thread : context.getThreads()) {
            if (thread != currentThread && thread.isAlive()) {
                threads.add(thread);
            }
        }
        return threads.toArray(Thread[]::new);
    }

    private static CApiContext loadCApi(Node node, PythonContext context, TruffleString name, TruffleString path, String reason) throws IOException, ImportException, ApiInitException {
        Env env = context.getEnv();

        TruffleFile homePath = env.getInternalTruffleFile(context.getCAPIHome().toJavaStringUncached());
        // e.g. "libpython-native.so"
        String libName = PythonContext.getSupportLibName("python-native");
        final TruffleFile capiFile = homePath.resolve(libName).getCanonicalFile();
        try {
            boolean useNative = true;
            boolean isolateNative = PythonOptions.IsolateNativeModules.getValue(env.getOptions());
            final NativeLibraryLocator loc;
            if (!isolateNative) {
                useNative = nativeCAPILoaded.compareAndSet(NO_NATIVE_CONTEXT, GLOBAL_NATIVE_CONTEXT);
            } else {
                useNative = nativeCAPILoaded.compareAndSet(NO_NATIVE_CONTEXT, ISOLATED_NATIVE_CONTEXT) || nativeCAPILoaded.get() == ISOLATED_NATIVE_CONTEXT;
            }
            if (!useNative) {
                String actualReason = "initialize native extensions support";
                if (reason != null) {
                    actualReason = reason;
                } else if (name != null && path != null) {
                    actualReason = String.format("load a native module '%s' from path '%s'", name.toJavaStringUncached(), path.toJavaStringUncached());
                }
                throw new ApiInitException(toTruffleStringUncached(
                                String.format("Option python.IsolateNativeModules is set to 'false' and a second GraalPy context attempted to %s. " +
                                                "At least one context in this process runs with 'IsolateNativeModules' set to false. " +
                                                "Depending on the order of context creation, this means some contexts in the process " +
                                                "cannot use native module.", actualReason)));
            }
            loc = new NativeLibraryLocator(context, capiFile, isolateNative);
            context.ensureNFILanguage(node, "allowNativeAccess", "true");
            int dlopenFlags = isolateNative ? PosixConstants.RTLD_LOCAL.value : PosixConstants.RTLD_GLOBAL.value;
            LOGGER.config(() -> "loading CAPI from " + loc.getCapiLibrary() + " as native");
            NfiContext nfiContext = context.ensureNfiContext();
            NfiLibrary capiLibrary = nfiContext.loadLibrary(loc.getCapiLibrary(), dlopenFlags);
            long initFunction = capiLibrary.lookupSymbol("initialize_graal_capi");
            CApiContext cApiContext = new CApiContext(context, capiLibrary, loc);
            context.setCApiContext(cApiContext);
            context.setCApiState(PythonContext.CApiState.INITIALIZING);

            /*
             * The GC state needs to be created before the first managed object is sent to native.
             * This is because the native object stub could take part in GC and will then already
             * require the GC state.
             */
            long gcState = cApiContext.createGCState();
            PythonThreadState currentThreadState = context.getThreadState(context.getLanguage());
            long nativeThreadState = PThreadState.getOrCreateNativeThreadState(currentThreadState);

            long builtinArrayPtr = NativeMemory.mallocPtrArray(PythonCextBuiltinRegistry.builtins.length);
            try {
                for (int id = 0; id < PythonCextBuiltinRegistry.builtins.length; id++) {
                    CApiBuiltinExecutable builtin = PythonCextBuiltinRegistry.builtins[id];
                    NativeMemory.writePtrArrayElement(builtinArrayPtr, id, builtin.getNativePointer());
                }
                NfiDowncallSignature initSignature = Nfi.createDowncallSignature(NfiType.RAW_POINTER, NfiType.RAW_POINTER, NfiType.RAW_POINTER, NfiType.RAW_POINTER, NfiType.RAW_POINTER);
                // TODO(NFI2) ENV parameter
                Object nativeThreadLocalVarPointer = initSignature.invoke(nfiContext, initFunction, 0L, builtinArrayPtr, gcState, nativeThreadState);
                assert InteropLibrary.getUncached().isPointer(nativeThreadLocalVarPointer);
                assert !InteropLibrary.getUncached().isNull(nativeThreadLocalVarPointer);
                currentThreadState.setNativeThreadLocalVarPointer(nativeThreadLocalVarPointer);
            } finally {
                NativeMemory.free(builtinArrayPtr);
            }

            assert PythonCApiAssertions.assertBuiltins(capiLibrary);
            cApiContext.pyDateTimeCAPICapsule = PyDateTimeCAPIWrapper.initWrapper(context, cApiContext);

            /*
             * C++ libraries sometimes declare global objects that have destructors that call
             * Py_DECREF. Those destructors are then called during native shutdown, which is after
             * the JVM/SVM shut down and the upcall would segfault. This finalizer code rebinds
             * reference operations to native no-ops that don't upcall. In normal scenarios we call
             * it during context exit, but when the VM is terminated by a signal, the context exit
             * is skipped. For that case we set up the shutdown hook.
             */
            long finalizeFunction = capiLibrary.lookupSymbol("GraalPyPrivate_GetFinalizeCApiPointer");
            long finalizingPointer = (long) Nfi.createDowncallSignature(NfiType.RAW_POINTER).invoke(nfiContext, finalizeFunction);
            try {
                cApiContext.addNativeFinalizer(context, finalizingPointer);
            } catch (RuntimeException e) {
                // This can happen when other languages restrict multithreading
                LOGGER.warning(() -> "didn't register a native finalizer due to: " + e.getMessage());
            }

            return cApiContext;
        } catch (PException e) {
            /*
             * Python exceptions that occur during the C API initialization are just passed through
             */
            throw e;
        } catch (RuntimeException e) {
            // we cannot really check if we truly need native access, so
            // when the abi contains "managed" we assume we do not
            if (!libName.contains("managed") && !context.isNativeAccessAllowed()) {
                throw new ImportException(null, name, path, ErrorMessages.NATIVE_ACCESS_NOT_ALLOWED);
            }
            throw new ApiInitException(e);
        }
    }

    private static final Set<String> C_EXT_SUPPORTED_LIST = Set.of(
                    // Stdlib modules are considered supported
                    "_cpython_sre",
                    "_cpython_unicodedata",
                    "_sha3",
                    "_sqlite3",
                    "termios",
                    "pyexpat");

    private static String dlopenFlagsToString(int flags) {
        String str = "RTLD_NOW";
        if ((flags & PosixConstants.RTLD_LAZY.value) != 0) {
            str = "RTLD_LAZY";
        }
        if ((flags & PosixConstants.RTLD_GLOBAL.value) != 0) {
            str += "|RTLD_GLOBAL";
        }
        if ((flags & PosixConstants.RTLD_LOCAL.value) != 0) {
            str += "|RTLD_LOCAL";
        }
        return str;
    }

    /**
     * This method loads a C extension module (C API) and will initialize the corresponding native
     * contexts if necessary.
     *
     * @param location The node that's requesting this operation. This is required for reporting
     *            correct source code location in case exceptions occur.
     * @param context The Python context object.
     * @param spec The name and path of the module (also containing the original module spec
     *            object).
     * @return A Python module.
     * @throws IOException If the specified file cannot be loaded.
     * @throws ApiInitException If the corresponding native context could not be initialized.
     * @throws ImportException If an exception occurred during C extension initialization.
     */
    @TruffleBoundary
    public static Object loadCExtModule(Node location, PythonContext context, ModuleSpec spec)
                    throws IOException, ApiInitException, ImportException {
        if (getLogger(CApiContext.class).isLoggable(Level.WARNING) && context.getOption(PythonOptions.WarnExperimentalFeatures)) {
            if (!C_EXT_SUPPORTED_LIST.contains(spec.name.toJavaStringUncached())) {
                String message = "Loading C extension module %s from '%s'. Support for the Python C API is considered experimental.";
                if (!(boolean) context.getOption(PythonOptions.RunViaLauncher)) {
                    message += " See https://www.graalvm.org/latest/reference-manual/python/Native-Extensions/#embedding-limitations for the limitations. " +
                                    "You can suppress this warning by setting the context option 'python.WarnExperimentalFeatures' to 'false'.";
                }
                getLogger(CApiContext.class).warning(message.formatted(spec.name, spec.path));
            }
        }

        // we always need to load the CPython C API
        CApiContext cApiContext = CApiContext.ensureCapiWasLoaded(location, context, spec.name, spec.path);
        NfiLibrary library;

        TruffleFile realPath = context.getPublicTruffleFileRelaxed(spec.path, context.getSoAbi()).getCanonicalFile();
        String loadPath = cApiContext.nativeLibraryLocator.resolve(context, realPath);
        getLogger(CApiContext.class).config(String.format("loading module %s (real path: %s) as native", spec.path, loadPath));
        int dlopenFlags = context.getDlopenFlags();
        if (context.getOption(PythonOptions.IsolateNativeModules)) {
            if ((dlopenFlags & PosixConstants.RTLD_GLOBAL.value) != 0) {
                getLogger(CApiContext.class).warning("The IsolateNativeModules option was specified, but the dlopen flags were set to include RTLD_GLOBAL " +
                                "(likely via some call to sys.setdlopenflags). This will probably lead to broken isolation and possibly incorrect results and crashing. " +
                                "You can patch sys.setdlopenflags to trace callers and/or prevent setting the RTLD_GLOBAL flags. " +
                                "See https://www.graalvm.org/latest/reference-manual/python/Native-Extensions for more details.");
            }
            dlopenFlags |= PosixConstants.RTLD_LOCAL.value;
        }

        try {
            library = context.ensureNfiContext().loadLibrary(loadPath, dlopenFlags);
        } catch (PException e) {
            throw e;
        } catch (AbstractTruffleException e) {
            if (!realPath.exists() && realPath.toString().contains("org.graalvm.python.vfsx")) {
                // file does not exist and it is from VirtualFileSystem
                // => we probably failed to extract it due to unconventional libs location
                getLogger(CApiContext.class).severe(String.format("could not load module %s (real path: %s) from virtual file system.\n\n" +
                                "!!! Please try to run with java system property org.graalvm.python.vfs.extractOnStartup=true !!!\n" +
                                "See also: https://www.graalvm.org/python/docs/#graalpy-troubleshooting", spec.path, realPath));

            }

            throw new ImportException(CExtContext.wrapJavaException(e, location), spec.name, spec.path, ErrorMessages.CANNOT_LOAD_M, spec.path, e);
        }
        return cApiContext.initCApiModule(location, library, spec.getInitFunctionName(), spec);
    }

    /**
     * Registers a VM shutdown hook, that sets {@code graalpy_finalizing} variable to let the C side
     * know that it's not safe to do upcalls and that native wrappers might have been deallocated.
     * We need to do it in a VM shutdown hook to make sure C atexit won't crash even if our context
     * finalization didn't run.
     *
     * The memory of the shared library may have been re-used if the GraalPy context was shut down
     * (cleanly or not), the sources were collected, and NFI's mechanism for unloading libraries
     * triggered a dlclose that dropped the refcount of the python-native library to 0. We leak 1
     * byte of memory and this shutdown hook for each context that ever initialized the C API.
     */
    private void addNativeFinalizer(PythonContext context, long finalizingPointer) {
        final Unsafe unsafe = context.getUnsafe();
        if (finalizingPointer != 0L) {
            // We are writing off heap memory and registering a VM shutdown hook, there is no
            // point in creating this thread via Truffle sandbox at this point
            nativeFinalizerRunnable = () -> unsafe.putByte(finalizingPointer, (byte) 1);
            context.registerAtexitHook((c) -> nativeFinalizerRunnable.run());
            nativeFinalizerShutdownHook = new Thread(nativeFinalizerRunnable);
            Runtime.getRuntime().addShutdownHook(nativeFinalizerShutdownHook);
        }
    }

    /**
     * This method is called to exit the context assuming a
     * {@link com.oracle.truffle.api.TruffleLanguage.ExitMode#NATURAL natural exit}. This means, it
     * is allowed to run guest code. Hence, we deallocate any reachable native object here since
     * they may have custom {@code tp_dealloc} functions.
     */
    @SuppressWarnings("try")
    public void exitCApiContext() {
        CompilerAsserts.neverPartOfCompilation();
        /*
         * Deallocating native storages and objects may run arbitrary guest code. So, we need to
         * ensure that the GIL is held.
         */
        try (GilNode.UncachedAcquire ignored = GilNode.uncachedAcquire()) {
            /*
             * Polling the native reference queue is the only task we can do here because
             * deallocating objects may run arbitrary guest code that can again call into the
             * interpreter.
             */
            pollReferenceQueue();
            CApiTransitions.deallocateNativeWeakRefs(getContext());
        }
    }

    @SuppressWarnings("try")
    public void finalizeCApi(boolean cancelling) {
        CompilerAsserts.neverPartOfCompilation();
        PythonContext context = getContext();
        HandleContext handleContext = context.nativeContext;
        if (backgroundGCTaskThread != null && backgroundGCTaskThread.isAlive()) {
            context.killSystemThread(backgroundGCTaskThread);
            try {
                backgroundGCTaskThread.join(10);
            } catch (InterruptedException e) {
                LOGGER.finest("got interrupt while joining GC thread before cleaning up C API state");
            }
            backgroundGCTaskThread = null;
        }

        /*
         * Disable reference queue polling because during finalization, we will free any known
         * allocated resources (e.g. native object stubs). Calling
         * 'CApiTransitions.pollReferenceQueue' could then lead to a double-free.
         */
        CApiTransitions.disableReferenceQueuePollingPermanently(handleContext);

        TruffleSafepoint sp = TruffleSafepoint.getCurrent();
        boolean prev = sp.setAllowActions(false);
        try {
            // TODO(fa): remove GIL acquisition (GR-51314)
            try (GilNode.UncachedAcquire ignored = GilNode.uncachedAcquire()) {
                /*
                 * First we want to free all replacements for which we have to call tp_dealloc,
                 * while all our stubs are still available for the tp_dealloc code to run. Since
                 * tp_dealloc may run arbitrary user code, we must not do that if the context was
                 * canceled.
                 */
                if (!cancelling) {
                    CApiTransitions.deallocNativeReplacements(context, handleContext);
                }
                // The singletons can be freed now
                freeSingletonNativeWrappers(handleContext);
                // Now we can clear all native memory that was simply allocated from Java. This
                // must be done after the the singleton wrappers were cleared because they might
                // also end up in the lookup table and may otherwise be double-freed.
                CApiTransitions.freeNativeObjectStubs(handleContext);
                CApiTransitions.freeNativeReplacementStructs(context, handleContext);
                CApiTransitions.freeNativeStorages(handleContext);
            }
            if (pyDateTimeCAPICapsule != null) {
                PyDateTimeCAPIWrapper.destroyWrapper(pyDateTimeCAPICapsule);
            }
            // free all allocated PyMethodDef structures
            for (Long pyMethodDefPointer : methodDefinitions.values()) {
                PyMethodDefHelper.free(pyMethodDefPointer);
            }
        } finally {
            sp.setAllowActions(prev);
        }
        if (nativeFinalizerShutdownHook != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(nativeFinalizerShutdownHook);
                nativeFinalizerRunnable.run();
            } catch (IllegalStateException e) {
                // Shutdown already in progress, let it do the finalization then
            }
        }
        pyCFunctionWrappers.clear();
        freeGCState();
        /*
         * If the static symbol cache is not null, then it is guaranteed that this context instance
         * was the exclusive user of it. We can now reset the state such that other contexts created
         * after this can use it.
         */
        synchronized (CApiContext.class) {
            if (nativeSymbolCacheSingleContext != null) {
                nativeSymbolCacheSingleContext = null;
                nativeSymbolCacheSingleContextUsed = false;
            }
        }

        if (nativeLibraryLocator != null) {
            nativeLibraryLocator.close();
        }
    }

    @TruffleBoundary
    public Object initCApiModule(Node node, NfiLibrary sharedLibrary, TruffleString initFuncName, ModuleSpec spec) throws ImportException {
        PythonContext context = getContext();
        CApiContext cApiContext = context.getCApiContext();
        long pyinitFunc = sharedLibrary.lookupOptionalSymbol(initFuncName.toJavaStringUncached());
        if (pyinitFunc == 0L) {
            throw new ImportException(null, spec.name, spec.path, ErrorMessages.NO_FUNCTION_FOUND, "", initFuncName, spec.path);
        }
        long nativeResult = (long) MODINIT_SIGNATURE.invoke(context.ensureNfiContext(), pyinitFunc);

        Object result = PyObjectCheckFunctionResultNodeGen.getUncached().execute(context, initFuncName, NativeToPythonNode.executeUncached(nativeResult));
        if (!(result instanceof PythonModule)) {
            // Multi-phase extension module initialization

            /*
             * See 'importdl.c: _PyImport_LoadDynamicModuleWithSpec' before
             * 'PyModule_FromDefAndSpec' is called. The 'PyModule_FromDefAndSpec' would initialize
             * the module def as Python object but before that, CPython explicitly checks if the
             * init function did this initialization by calling 'PyModuleDef_Init' on it. So, we
             * must do it here because 'CreateModuleNode' should just ignore this case.
             */
            Object clazz = GetClassNode.executeUncached(result);
            if (clazz == PNone.NO_VALUE) {
                throw PRaiseNode.raiseStatic(node, PythonBuiltinClassType.SystemError, ErrorMessages.INIT_FUNC_RETURNED_UNINT_OBJ, initFuncName);
            }

            return CExtNodes.createModule(node, cApiContext, spec, nativeResult, sharedLibrary);
        } else {
            // see: 'import.c: _PyImport_FixupExtensionObject'
            PythonModule module = (PythonModule) result;
            module.setAttribute(T___FILE__, spec.path);
            addLoadedExtensionLibrary(sharedLibrary);

            // add to 'sys.modules'
            PDict sysModules = context.getSysModules();
            sysModules.setItem(spec.name, result);

            // _PyState_AddModule
            long moduleDef = module.getNativeModuleDef();
            int mIndex = PythonUtils.toIntError(readLongField(moduleDef, CFields.PyModuleDef_Base__m_index));
            while (modulesByIndex.size() <= mIndex) {
                modulesByIndex.add(null);
            }
            modulesByIndex.set(mIndex, module);

            // add to 'import.c: extensions'
            extensions.put(Pair.create(spec.path, spec.name), module);
            return result;
        }
    }

    @TruffleBoundary
    public PythonModule findExtension(TruffleString filename, TruffleString name) {
        return extensions.get(Pair.create(filename, name));
    }

    public long getClosurePointer(Object executable) {
        CompilerAsserts.neverPartOfCompilation();
        ClosureInfo info = callableClosureByExecutable.get(executable);
        return info == null ? -1 : info.pointer;
    }

    public Object getClosureDelegate(long pointer) {
        CompilerAsserts.neverPartOfCompilation();
        ClosureInfo info = callableClosures.get(pointer);
        return info == null ? null : info.delegate;
    }

    public Object getClosureExecutable(long pointer) {
        CompilerAsserts.neverPartOfCompilation();
        ClosureInfo info = callableClosures.get(pointer);
        return info == null ? null : info.executable;
    }

    public void setClosurePointer(Object delegate, Object executable, long pointer) {
        CompilerAsserts.neverPartOfCompilation();
        var info = new ClosureInfo(delegate, executable, pointer);
        callableClosureByExecutable.put(executable, info);
        callableClosures.put(pointer, info);
        LOGGER.finer(() -> PythonUtils.formatJString("new NFI closure: (%s, %s) -> %d 0x%x", executable.getClass().getSimpleName(), delegate, pointer, pointer));
    }

    public long registerClosure(String name, NfiUpcallSignature signature, MethodHandle methodHandle, Object key, Object delegate) {
        CompilerAsserts.neverPartOfCompilation();
        PythonContext context = getContext();
        long pointer = signature.createClosure(context.ensureNfiContext(), name, methodHandle);
        setClosurePointer(delegate, key, pointer);
        return pointer;
    }

    @TruffleBoundary
    public long getOrAllocateNativePyMethodDef(PyMethodDefHelper pyMethodDef) {
        return methodDefinitions.computeIfAbsent(pyMethodDef, PyMethodDefHelper::allocate);
    }

    /**
     * A table mapping a {@link RootCallTarget} to the appropriate {@link PyCFunctionWrapper}. This
     * could actually be shared between Python contexts but {@link PyCFunctionWrapper} is still a
     * {@link TruffleObject} and so it is assumed to be context-specific although our wrapper
     * doesn't contain any data and is just used for executing code.
     */
    private final ConcurrentHashMap<RootCallTarget, PyCFunctionWrapper> pyCFunctionWrappers = new ConcurrentHashMap<>(4);

    @TruffleBoundary
    public PyCFunctionWrapper getOrCreatePyCFunctionWrapper(RootCallTarget ct, Function<RootCallTarget, PyCFunctionWrapper> cons) {
        return pyCFunctionWrappers.computeIfAbsent(ct, cons);
    }

    public static boolean isPointerObject(Object object) {
        return object.getClass() == NativePointer.class || object.getClass().getSimpleName().contains("NFIPointer") || object.getClass().getSimpleName().contains("LLVMPointer");
    }
}
