/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapper.PythonAbstractObjectNativeWrapper.IMMORTAL_REFCNT;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.pollReferenceQueue;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___FILE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___LIBRARY__;
import static com.oracle.graal.python.nodes.StringLiterals.J_LLVM_LANGUAGE;
import static com.oracle.graal.python.nodes.StringLiterals.J_NFI_LANGUAGE;
import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_STRING;

import java.io.IOException;
import java.io.PrintStream;
import java.lang.invoke.VarHandle;
import java.lang.ref.WeakReference;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

import org.graalvm.collections.Pair;
import org.graalvm.nativeimage.ImageInfo;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonOS;
import com.oracle.graal.python.builtins.modules.cext.PythonCApiAssertions;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinRegistry;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltinExecutable;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.capsule.PyCapsule;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.CreateModuleNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapper.PythonAbstractObjectNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.HandleContext;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.ToPythonWrapperNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.CheckFunctionResultNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.EnsureExecutableNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtContext;
import com.oracle.graal.python.builtins.objects.cext.common.LoadCExtException.ApiInitException;
import com.oracle.graal.python.builtins.objects.cext.common.LoadCExtException.ImportException;
import com.oracle.graal.python.builtins.objects.cext.common.NativePointer;
import com.oracle.graal.python.builtins.objects.cext.structs.CConstants;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.FreeNode;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.ReadPointerNode;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccessFactory;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructs;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.BuiltinMethodDescriptor;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.thread.PLock;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonContext.PythonThreadState;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.util.Function;
import com.oracle.graal.python.util.PythonSystemThreadTask;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.graal.python.util.Supplier;
import com.oracle.graal.python.util.SuppressFBWarnings;
import com.oracle.graal.python.util.WeakIdentityHashMap;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.Source.SourceBuilder;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.nfi.api.SignatureLibrary;

import sun.misc.Unsafe;

public final class CApiContext extends CExtContext {

    public static final String LOGGER_CAPI_NAME = "capi";

    /** Same as _PY_NSMALLNEGINTS */
    public static final int PY_NSMALLNEGINTS = 5;

    /** Same as _PY_NSMALLPOSINTS */
    public static final int PY_NSMALLPOSINTS = 257;

    /**
     * NFI source for Python module init functions (i.e. {@code "PyInit_modname"}).
     */
    private static final Source MODINIT_SRC = Source.newBuilder(J_NFI_LANGUAGE, "():POINTER", "modinit").build();

    private static final TruffleLogger LOGGER = PythonLanguage.getLogger(LOGGER_CAPI_NAME);
    public static final TruffleLogger GC_LOGGER = PythonLanguage.getLogger(CApiContext.LOGGER_CAPI_NAME + ".gc");

    /**
     * Path to this context's local copy of the C API library. This may be null, in which case the
     * library was loaded with RTLD_GLOBAL (or equivalent) and there's no isolation between
     * contexts in this operating system process. If non-null, any C extension library is copied
     * and patched before load to add a DT_NEEDED dynamic dependency on this specific file, so that
     * libraries are isolated between Python contexts.
     */
    public final String capiFileCopy;

    /** Native wrappers for context-insensitive singletons like {@link PNone#NONE}. */
    @CompilationFinal(dimensions = 1) private final PythonAbstractObjectNativeWrapper[] singletonNativePtrs;

    /**
     * This cache is used to cache native wrappers for frequently used primitives. This is strictly
     * defined to be the range {@code [-5, 256]}. CPython does exactly the same (see
     * {@code PyLong_FromLong}; implemented in macro {@code CHECK_SMALL_INT}).
     */
    @CompilationFinal(dimensions = 1) private final PrimitiveNativeWrapper[] primitiveNativeWrapperCache;

    /**
     * Pointer to a native array of long objects in interval
     * [{@link com.oracle.graal.python.builtins.objects.cext.structs.CConstants#_PY_NSMALLNEGINTS
     * -_PY_NSMALLNEGINTS},
     * {@link com.oracle.graal.python.builtins.objects.cext.structs.CConstants#_PY_NSMALLPOSINTS
     * _PY_NSMALLPOSINTS}[. This corresponds to CPython's {@code PyInterpreterState.small_ints} and
     * is actually a native mirror of {@link #primitiveNativeWrapperCache}.
     */
    private Object nativeSmallIntsArray;

    /**
     * Pointer to the native {@code GCState GC state}. This corresponds to CPython's
     * {@code PyInterpreterState.gc}.
     */
    private Object gcState;

    /** Same as {@code import.c: extensions} but we don't keep a PDict; just a bare Java HashMap. */
    private final HashMap<Pair<TruffleString, TruffleString>, PythonModule> extensions = new HashMap<>(4);

    /** corresponds to {@code unicodeobject.c: interned} */
    private PDict internedUnicode;
    private final ArrayList<Object> modulesByIndex = new ArrayList<>(0);

    public final HashMap<Long, PLock> locks = new HashMap<>();
    public final AtomicLong lockId = new AtomicLong();

    /**
     * Thread local storage for PyThread_tss_* APIs
     */
    private final ConcurrentHashMap<Long, ThreadLocal<Object>> tssStorage = new ConcurrentHashMap<>();
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
    @CompilationFinal(dimensions = 1) private static Object[] nativeSymbolCacheSingleContext;
    private static boolean nativeSymbolCacheSingleContextUsed;

    /**
     * A private (i.e. per-context) cache of C API symbols (usually helper functions).
     */
    private final Object[] nativeSymbolCache;

    private record ClosureInfo(Object closure, Object delegate, Object executable, long pointer) {
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
    private final HashMap<PyMethodDefHelper, Object> methodDefinitions = new HashMap<>(4);

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

    public final BackgroundGCTask gcTask;
    private Thread backgroundGCTaskThread;

    public static TruffleLogger getLogger(Class<?> clazz) {
        return PythonLanguage.getLogger(LOGGER_CAPI_NAME + "." + clazz.getSimpleName());
    }

    public CApiContext(PythonContext context, Object llvmLibrary, boolean useNativeBackend, String capiFilePath) {
        super(context, llvmLibrary, useNativeBackend);
        this.capiFileCopy = capiFilePath;
        this.nativeSymbolCache = new Object[NativeCAPISymbol.values().length];

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

                // we cannot be in built-time code because this is using pre-initialized contexts
                assert !ImageInfo.inImageBuildtimeCode();

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

        // initialize singleton native wrappers
        singletonNativePtrs = new PythonAbstractObjectNativeWrapper[CONTEXT_INSENSITIVE_SINGLETONS.length];
        // Other threads must see the nativeWrapper fully initialized once it becomes non-null
        for (int i = 0; i < singletonNativePtrs.length; i++) {
            assert CApiGuards.isSpecialSingleton(CONTEXT_INSENSITIVE_SINGLETONS[i]);
            /*
             * Note: this does intentionally not use 'PythonObjectNativeWrapper.wrap' because the
             * wrapper must not be reachable from the Python object since the singletons are shared.
             */
            singletonNativePtrs[i] = new PythonObjectNativeWrapper(CONTEXT_INSENSITIVE_SINGLETONS[i]);
        }

        // initialize primitive native wrapper cache
        primitiveNativeWrapperCache = new PrimitiveNativeWrapper[PY_NSMALLNEGINTS + PY_NSMALLPOSINTS];
        for (int i = 0; i < primitiveNativeWrapperCache.length; i++) {
            int value = i - PY_NSMALLNEGINTS;
            assert CApiGuards.isSmallInteger(value);
            primitiveNativeWrapperCache[i] = PrimitiveNativeWrapper.createInt(value);
        }

        // initialize Py_True and Py_False
        context.getTrue().setNativeWrapper(PrimitiveNativeWrapper.createBool(true));
        context.getFalse().setNativeWrapper(PrimitiveNativeWrapper.createBool(false));

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

    public PDict getInternedUnicode() {
        return internedUnicode;
    }

    public void setInternedUnicode(PDict internedUnicode) {
        this.internedUnicode = internedUnicode;
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
    public Object tssGet(long key) {
        ThreadLocal<Object> local = tssStorage.get(key);
        if (local != null) {
            return local.get();
        }
        return null;
    }

    @TruffleBoundary
    public void tssSet(long key, Object object) {
        tssStorage.computeIfAbsent(key, (k) -> new ThreadLocal<>()).set(object);
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

    public PythonAbstractObjectNativeWrapper getSingletonNativeWrapper(PythonAbstractObject obj) {
        int singletonNativePtrIdx = CApiContext.getSingletonNativeWrapperIdx(obj);
        if (singletonNativePtrIdx != -1) {
            return singletonNativePtrs[singletonNativePtrIdx];
        }
        return null;
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
            PythonAbstractObjectNativeWrapper singletonNativeWrapper = singletonNativePtrs[i];
            singletonNativePtrs[i] = null;
            assert singletonNativeWrapper != null;
            assert getSingletonNativeWrapperIdx(singletonNativeWrapper.getDelegate()) != -1;
            assert !singletonNativeWrapper.isNative() || singletonNativeWrapper.getRefCount() == IMMORTAL_REFCNT;
            if (singletonNativeWrapper.ref != null) {
                CApiTransitions.nativeStubLookupRemove(handleContext, singletonNativeWrapper.ref);
            }
            PyTruffleObjectFree.releaseNativeWrapperUncached(singletonNativeWrapper);
        }
    }

    public PrimitiveNativeWrapper getCachedPrimitiveNativeWrapper(int i) {
        assert CApiGuards.isSmallInteger(i);
        PrimitiveNativeWrapper primitiveNativeWrapper = primitiveNativeWrapperCache[i + 5];
        assert primitiveNativeWrapper.getRefCount() > 0;
        return primitiveNativeWrapper;
    }

    public PrimitiveNativeWrapper getCachedPrimitiveNativeWrapper(long l) {
        assert CApiGuards.isSmallLong(l);
        return getCachedPrimitiveNativeWrapper((int) l);
    }

    public PrimitiveNativeWrapper getCachedBooleanPrimitiveNativeWrapper(boolean b) {
        PythonAbstractObjectNativeWrapper wrapper = b ? getContext().getTrue().getNativeWrapper() : getContext().getFalse().getNativeWrapper();
        assert wrapper.getRefCount() > 0;
        return (PrimitiveNativeWrapper) wrapper;
    }

    /**
     * Returns or allocates (on demand) the native array {@code PyInterpreterState.small_ints} and
     * write all elements to it.
     */
    Object getOrCreateSmallInts() {
        CompilerAsserts.neverPartOfCompilation();
        // TODO(fa): this should not require the GIL (GR-51314)
        assert getContext().ownsGil();
        if (nativeSmallIntsArray == null) {
            assert CConstants._PY_NSMALLNEGINTS.intValue() == PY_NSMALLNEGINTS;
            assert CConstants._PY_NSMALLPOSINTS.intValue() == PY_NSMALLPOSINTS;
            Object smallInts = CStructAccess.AllocateNode.callocUncached(PY_NSMALLNEGINTS + PY_NSMALLPOSINTS, CStructAccess.POINTER_SIZE);
            for (int i = 0; i < PY_NSMALLNEGINTS + PY_NSMALLPOSINTS; i++) {
                CStructAccessFactory.WriteObjectNewRefNodeGen.getUncached().writeArrayElement(smallInts, i, i - PY_NSMALLNEGINTS);
            }
            nativeSmallIntsArray = smallInts;
        }
        return nativeSmallIntsArray;
    }

    /**
     * Deallocates the native small int array (pointer {@link #nativeSmallIntsArray}) and all
     * wrappers of the small ints (in {@link #primitiveNativeWrapperCache}) which are immortal and
     * must therefore be explicitly free'd. This method modifies the
     * {@link HandleContext#nativeStubLookup stub lookup table} but runs not guest code.
     */
    private void freeSmallInts(HandleContext handleContext) {
        CompilerAsserts.neverPartOfCompilation();
        // TODO(fa): this should not require the GIL (GR-51314)
        assert getContext().ownsGil();
        if (nativeSmallIntsArray != null) {
            assert verifyNativeSmallInts();
            // free the native array used to store the stub pointers of the small int wrappers
            FreeNode.executeUncached(nativeSmallIntsArray);
            nativeSmallIntsArray = null;
        }
        for (PrimitiveNativeWrapper wrapper : primitiveNativeWrapperCache) {
            assert wrapper.isIntLike() && CApiGuards.isSmallLong(wrapper.getLong());
            assert !wrapper.isNative() || wrapper.getRefCount() == IMMORTAL_REFCNT;
            if (wrapper.ref != null) {
                CApiTransitions.nativeStubLookupRemove(handleContext, wrapper.ref);
            }
            PyTruffleObjectFree.releaseNativeWrapperUncached(wrapper);
        }
    }

    /**
     * Verifies integrity of the pointers stored in the native small int array. Each pointer must
     * denote the according small int wrapper. The objects are expected to be immortal.
     */
    private boolean verifyNativeSmallInts() {
        // TODO(fa): this should not require the GIL (GR-51314)
        assert getContext().ownsGil();
        for (int i = 0; i < PY_NSMALLNEGINTS + PY_NSMALLPOSINTS; i++) {
            Object elementPtr = ReadPointerNode.getUncached().readArrayElement(nativeSmallIntsArray, i);
            PythonNativeWrapper wrapper = ToPythonWrapperNode.executeUncached(elementPtr, false);
            if (wrapper != primitiveNativeWrapperCache[i]) {
                return false;
            }
            if (primitiveNativeWrapperCache[i].isNative() && primitiveNativeWrapperCache[i].getRefCount() != IMMORTAL_REFCNT) {
                return false;
            }
        }
        return true;
    }

    /**
     * Allocates the {@code GCState} which needs to happen very early in the C API initialization
     * phase. <it>Very early</it> means it needs to happen before the first object (that takes part
     * in the GC) is sent to native. This could, e.g., be the thread-state dict that is allocated
     * when creating the {@link PThreadState native thread state}.
     */
    public Object createGCState() {
        CompilerAsserts.neverPartOfCompilation();
        assert gcState == null;
        PythonContext.GCState state = getContext().getGcState();
        Object ptr = CStructAccess.AllocateNode.allocUncached(CStructs.GCState);
        CStructAccess.WriteIntNode.writeUncached(ptr, CFields.GCState__enabled, PInt.intValue(state.isEnabled()));
        CStructAccess.WriteIntNode.writeUncached(ptr, CFields.GCState__debug, state.getDebug());
        Object generations = CStructAccess.GetElementPtrNode.getUncached().getElementPtr(ptr, CFields.GCState__generations);
        for (int i = 0; i < state.getThresholds().length; i++) {
            CStructAccess.WriteIntNode.getUncached().writeStructArrayElement(generations, i, CFields.GCGeneration__threshold, state.getThresholds()[i]);
        }
        gcState = ptr;
        return gcState;
    }

    /**
     * Fast-path method to retrieve the {@code GCState} pointer. This must only be called after
     * {@link #createGCState()} was called the first time which should happen very early during C
     * API context initialization.
     */
    public Object getGCState() {
        assert gcState != null;
        return gcState;
    }

    /**
     * Deallocates the native {@code GCState} (pointer {@link #gcState}).
     */
    private void freeGCState() {
        CompilerAsserts.neverPartOfCompilation();
        if (gcState != null) {
            FreeNode.executeUncached(gcState);
            gcState = null;
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
    private static Object[] getSymbolCache(Node caller) {
        Object[] nativeSymbolCacheSingleContext = CApiContext.nativeSymbolCacheSingleContext;
        if (nativeSymbolCacheSingleContext != null) {
            return nativeSymbolCacheSingleContext;
        }
        return PythonContext.get(caller).getCApiContext().nativeSymbolCache;
    }

    public static Object getNativeSymbol(Node caller, NativeCAPISymbol symbol) {
        Object[] nativeSymbolCache = getSymbolCache(caller);
        Object result = nativeSymbolCache[symbol.ordinal()];
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
    private static Object lookupNativeSymbol(Object[] nativeSymbolCache, NativeCAPISymbol symbol) {
        CompilerAsserts.neverPartOfCompilation();
        String name = symbol.getName();
        try {
            Object nativeSymbol = InteropLibrary.getUncached().readMember(PythonContext.get(null).getCApiContext().getLLVMLibrary(), name);
            nativeSymbol = EnsureExecutableNode.executeUncached(nativeSymbol, symbol);
            VarHandle.storeStoreFence();
            return nativeSymbolCache[symbol.ordinal()] = nativeSymbol;
        } catch (UnsupportedMessageException | UnknownIdentifierException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }

    @SuppressWarnings("unused")
    public void trackObject(Object ptr, PFrame.Reference curFrame, TruffleString clazzName) {
        // TODO(fa): implement tracking of container objects for cycle detection
    }

    @SuppressWarnings("unused")
    public void untrackObject(Object ptr, PFrame.Reference curFrame, TruffleString clazzName) {
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

        Object nativeSymbol = null;
        InteropLibrary callNative = null;

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
                callNative = InteropLibrary.getUncached(nativeSymbol);
            }
            Long rss = 0L;
            try {
                rss = (Long) callNative.execute(nativeSymbol);
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
        if (ImageInfo.inImageBuildtimeCode() //
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
     * extensions - this can only be loaded once per process.
     */
    private static final AtomicBoolean nativeCAPILoaded = new AtomicBoolean();

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

    public static String copyToTempFile(Env env, TruffleFile src, TruffleFile targetDirOrNull, String additionalDependencyPath) throws IOException {
        TruffleFile targetDir;
        if (targetDirOrNull == null) {
            targetDir = env.createTempDirectory(null, "graalpy-capi-temp");
        } else {
            targetDir = targetDirOrNull;
        }
        String[] nameParts = src.getName().split("\\.");
        String stem = nameParts[0];
        String extension = "." + nameParts[nameParts.length - 1];
        TruffleFile target = env.createTempFile(targetDir, stem, extension).getAbsoluteFile();
        LOGGER.info(() -> "Copying " + src.getPath() + " to " + target.getPath());
        if (targetDirOrNull == null) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try {
                        target.delete();
                    } catch (NoSuchFileException e) {
                        // ok, this is what we wanted
                    } catch (IOException e) {
                        LOGGER.warning("Failed to delete temporary file " + target.getPath());
                    }
                    try {
                        target.getParent().delete();
                    } catch (IOException e) {
                        LOGGER.warning("Failed to delete temporary directory " + target.getParent().getPath());
                    }
            }));
        } else {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try {
                        target.delete();
                    } catch (NoSuchFileException e) {
                        // ok, this is what we wanted
                    } catch (IOException e) {
                        LOGGER.warning("Failed to delete temporary file " + target.getPath());
                    }
            }));
        }
        synchronized (CApiContext.class) {
            target.delete();
            src.copy(target);
        }
        switch (PythonOS.getPythonOS()) {
            case PLATFORM_WIN32:
                if (additionalDependencyPath != null) {
                    // TOOD: Change the dependency from python3.11.dll to whatever name Use
                    // SetDllDirectoryA to load it
                }
                break;
            case PLATFORM_LINUX:
                // TODO: change soname to new name
                if (additionalDependencyPath != null) {
                    // TODO: Add a DT_NEEDED entry in the dynamic section and the full path to the
                    // strings section
                }
                break;
            case PLATFORM_DARWIN:
                // TODO:
                if (additionalDependencyPath != null) {
                    // TODO: Add an LC_LOAD_DYLIB command to load the additional lib
                }
                break;
            default:
                throw CompilerDirectives.shouldNotReachHere("Unsupported OS");
        }
        return target.getPath();
    }

    @TruffleBoundary
    public static Object ensureCApiLLVMLibrary(PythonContext context) throws IOException, ImportException, ApiInitException {
        assert !PythonOptions.NativeModules.getValue(context.getEnv().getOptions());
        CApiContext cApiContext = ensureCapiWasLoaded(null, context, T_EMPTY_STRING, T_EMPTY_STRING,
                        " load LLVM library (this is an internal bug, LLVM library should not be loaded when running on native backend)");
        return cApiContext.getLLVMLibrary();
    }

    @TruffleBoundary
    public static CApiContext ensureCapiWasLoaded(Node node, PythonContext context, TruffleString name, TruffleString path) throws IOException, ImportException, ApiInitException {
        return ensureCapiWasLoaded(node, context, name, path, null);
    }

    @TruffleBoundary
    public static CApiContext ensureCapiWasLoaded(Node node, PythonContext context, TruffleString name, TruffleString path, String reason) throws IOException, ImportException, ApiInitException {
        assert PythonContext.get(null).ownsGil(); // unsafe lazy initialization
        if (!context.hasCApiContext()) {
            Env env = context.getEnv();
            InteropLibrary U = InteropLibrary.getUncached();

            TruffleFile homePath = env.getInternalTruffleFile(context.getCAPIHome().toJavaStringUncached());
            // e.g. "libpython-native.so"
            String libName = context.getLLVMSupportExt("python");
            TruffleFile capiFile = homePath.resolve(libName).getCanonicalFile();
            try {
                final String capiFilePath;
                SourceBuilder capiSrcBuilder;
                boolean useNative;
                boolean isolateNative = PythonOptions.IsolateNativeModules.getValue(env.getOptions());
                if (PythonOptions.NativeModules.getValue(env.getOptions())) {
                    if (!isolateNative) {
                        useNative = nativeCAPILoaded.compareAndSet(false, true);
                        if (!useNative && warnedSecondContexWithNativeCAPI.compareAndSet(false, true)) {
                            LOGGER.warning("GraalPy option 'NativeModules' is set to true, " +
                                            "and 'IsolateNativeModules' is set to false, which " +
                                            "means only one context in the process can use native modules, " +
                                            "second and other contexts fallback to NativeModules=false and " +
                                            "will use LLVM bitcode execution via GraalVM LLVM.");
                        }
                    } else {
                        useNative = true;
                    }
                } else {
                    useNative = false;
                }
                if (useNative) {
                    context.ensureNFILanguage(node, "NativeModules", "true");
                    if (!isolateNative) {
                        capiFilePath = null;
                        capiSrcBuilder = Source.newBuilder(J_NFI_LANGUAGE, "load(RTLD_GLOBAL) \"" + capiFile.getPath() + "\"", "<libpython>");
                        LOGGER.config(() -> "loading CAPI from " + capiFile + " as native");
                    } else {
                        capiFilePath = copyToTempFile(env, capiFile, null, null);
                        capiSrcBuilder = Source.newBuilder(J_NFI_LANGUAGE, "load(RTLD_LOCAL) \"" + capiFilePath + "\"", "<libpython>");
                        LOGGER.config(() -> "loading CAPI copy from " + capiFilePath + " as native");
                    }
                } else {
                    capiFilePath = null;
                    context.ensureLLVMLanguage(node);
                    capiSrcBuilder = Source.newBuilder(J_LLVM_LANGUAGE, capiFile);
                    LOGGER.config(() -> "loading CAPI from " + capiFile + " as bitcode");
                }
                if (!context.getLanguage().getEngineOption(PythonOptions.ExposeInternalSources)) {
                    capiSrcBuilder.internal(true);
                }
                CallTarget capiLibraryCallTarget = context.getEnv().parseInternal(capiSrcBuilder.build());

                Object capiLibrary = capiLibraryCallTarget.call();
                Object initFunction = U.readMember(capiLibrary, "initialize_graal_capi");
                CApiContext cApiContext = new CApiContext(context, capiLibrary, useNative, capiFilePath);
                context.setCApiContext(cApiContext);
                try (BuiltinArrayWrapper builtinArrayWrapper = new BuiltinArrayWrapper()) {
                    /*
                     * The GC state needs to be created before the first managed object is sent to
                     * native. This is because the native object stub could take part in GC and will
                     * then already require the GC state.
                     */
                    Object gcState = cApiContext.createGCState();
                    if (useNative) {
                        Object signature = env.parseInternal(Source.newBuilder(J_NFI_LANGUAGE, "(ENV,POINTER,POINTER):VOID", "exec").build()).call();
                        initFunction = SignatureLibrary.getUncached().bind(signature, initFunction);
                        U.execute(initFunction, builtinArrayWrapper, gcState);
                    } else {
                        assert U.isExecutable(initFunction);
                        U.execute(initFunction, NativePointer.createNull(), builtinArrayWrapper, gcState);
                    }
                }

                assert PythonCApiAssertions.assertBuiltins(capiLibrary);
                cApiContext.pyDateTimeCAPICapsule = PyDateTimeCAPIWrapper.initWrapper(context, cApiContext);
                context.runCApiHooks();

                if (useNative) {
                    /*
                     * C++ libraries sometimes declare global objects that have destructors that
                     * call Py_DECREF. Those destructors are then called during native shutdown,
                     * which is after the JVM/SVM shut down and the upcall would segfault. This
                     * finalizer code rebinds reference operations to native no-ops that don't
                     * upcall. In normal scenarios we call it during context exit, but when the VM
                     * is terminated by a signal, the context exit is skipped. For that case we set
                     * up the shutdown hook.
                     */
                    Object finalizeFunction = U.readMember(capiLibrary, "GraalPy_get_finalize_capi_pointer");
                    Object finalizeSignature = env.parseInternal(Source.newBuilder(J_NFI_LANGUAGE, "():POINTER", "exec").build()).call();
                    Object finalizingPointer = SignatureLibrary.getUncached().call(finalizeSignature, finalizeFunction);
                    try {
                        cApiContext.addNativeFinalizer(context, finalizingPointer);
                        cApiContext.runBackgroundGCTask(context);
                    } catch (RuntimeException e) {
                        // This can happen when other languages restrict multithreading
                        LOGGER.warning(() -> "didn't register a native finalizer due to: " + e.getMessage());
                    }
                }

                return cApiContext;
            } catch (PException e) {
                /*
                 * Python exceptions that occur during the C API initialization are just passed
                 * through
                 */
                throw e;
            } catch (RuntimeException | UnsupportedMessageException | ArityException | UnknownIdentifierException | UnsupportedTypeException e) {
                // we cannot really check if we truly need native access, so
                // when the abi contains "managed" we assume we do not
                if (!libName.contains("managed") && !context.isNativeAccessAllowed()) {
                    throw new ImportException(null, name, path, ErrorMessages.NATIVE_ACCESS_NOT_ALLOWED);
                }
                throw new ApiInitException(e);
            }
        }
        return context.getCApiContext();
    }

    /**
     * Registers a VM shutdown hook, that sets {@code graalpy_finalizing} variable to let the C side
     * know that it's not safe to do upcalls and that native wrappers might have been deallocated.
     * We need to do it in a VM shutdown hook to make sure C atexit won't crash even if our context
     * finalization didn't run.
     */
    private void addNativeFinalizer(PythonContext context, Object finalizingPointerObj) {
        final Unsafe unsafe = context.getUnsafe();
        InteropLibrary lib = InteropLibrary.getUncached(finalizingPointerObj);
        if (!lib.isNull(finalizingPointerObj) && lib.isPointer(finalizingPointerObj)) {
            try {
                long finalizingPointer = lib.asPointer(finalizingPointerObj);
                // We are writing off heap memory and registering a VM shutdown hook, there is no
                // point in creating this thread via Truffle sandbox at this point
                nativeFinalizerRunnable = () -> unsafe.putInt(finalizingPointer, 1);
                nativeFinalizerShutdownHook = new Thread(nativeFinalizerRunnable);
                Runtime.getRuntime().addShutdownHook(nativeFinalizerShutdownHook);
            } catch (UnsupportedMessageException e) {
                throw new RuntimeException(e);
            }
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
            PythonThreadState threadState = getContext().getThreadState(getContext().getLanguage());
            Object nativeThreadState = PThreadState.getNativeThreadState(threadState);
            if (nativeThreadState != null) {
                PCallCapiFunction.callUncached(NativeCAPISymbol.FUN_PY_GC_COLLECT_NO_FAIL, nativeThreadState);
                pollReferenceQueue();
            }
            CApiTransitions.deallocateNativeWeakRefs(getContext());
        }
    }

    @SuppressWarnings("try")
    public void finalizeCApi() {
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
        CApiTransitions.disableReferenceQueuePolling(handleContext);

        TruffleSafepoint sp = TruffleSafepoint.getCurrent();
        boolean prev = sp.setAllowActions(false);
        try {
            // TODO(fa): remove GIL acquisition (GR-51314)
            try (GilNode.UncachedAcquire ignored = GilNode.uncachedAcquire()) {
                freeSmallInts(handleContext);
                freeSingletonNativeWrappers(handleContext);
                /*
                 * Clear all remaining native object stubs. This must be done after the small int
                 * and the singleton wrappers were cleared because they might also end up in the
                 * lookup table and may otherwise be double-freed.
                 */
                CApiTransitions.freeNativeObjectStubs(handleContext);
                CApiTransitions.freeClassReplacements(handleContext);
                CApiTransitions.freeNativeStorages(handleContext);
            }
            if (pyDateTimeCAPICapsule != null) {
                PyDateTimeCAPIWrapper.destroyWrapper(pyDateTimeCAPICapsule);
            }
            // free all allocated PyMethodDef structures
            for (Object pyMethodDefPointer : methodDefinitions.values()) {
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
            if (CApiContext.nativeSymbolCacheSingleContext != null) {
                CApiContext.nativeSymbolCacheSingleContext = null;
                CApiContext.nativeSymbolCacheSingleContextUsed = false;
            }
        }
    }

    @TruffleBoundary
    public Object initCApiModule(Node location, Object sharedLibrary, TruffleString initFuncName, ModuleSpec spec, InteropLibrary llvmInteropLib, CheckFunctionResultNode checkFunctionResultNode)
                    throws UnsupportedMessageException, ArityException, UnsupportedTypeException, ImportException {
        PythonContext context = getContext();
        CApiContext cApiContext = context.getCApiContext();
        Object pyinitFunc;
        try {
            pyinitFunc = llvmInteropLib.readMember(sharedLibrary, initFuncName.toJavaStringUncached());
        } catch (UnknownIdentifierException | UnsupportedMessageException e1) {
            throw new ImportException(null, spec.name, spec.path, ErrorMessages.NO_FUNCTION_FOUND, "", initFuncName, spec.path);
        }
        Object nativeResult;
        try {
            nativeResult = InteropLibrary.getUncached().execute(pyinitFunc);
        } catch (UnsupportedMessageException e) {
            Object signature = context.getEnv().parseInternal(MODINIT_SRC).call();
            nativeResult = SignatureLibrary.getUncached().call(signature, pyinitFunc);
        } catch (ArityException e) {
            // In case of multi-phase init, the init function may take more than one argument.
            // However, CPython gracefully ignores that. So, we pass just NULL pointers.
            Object[] arguments = new Object[e.getExpectedMinArity()];
            Arrays.fill(arguments, PNone.NO_VALUE);
            nativeResult = InteropLibrary.getUncached().execute(pyinitFunc, arguments);
        }

        checkFunctionResultNode.execute(context, initFuncName, nativeResult);

        Object result = NativeToPythonNode.executeUncached(nativeResult);
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
                throw PRaiseNode.raiseUncached(location, PythonBuiltinClassType.SystemError, ErrorMessages.INIT_FUNC_RETURNED_UNINT_OBJ, initFuncName);
            }

            return CreateModuleNodeGen.getUncached().execute(cApiContext, spec, result, sharedLibrary);
        } else {
            // see: 'import.c: _PyImport_FixupExtensionObject'
            PythonModule module = (PythonModule) result;
            module.setAttribute(T___FILE__, spec.path);
            module.setAttribute(T___LIBRARY__, sharedLibrary);
            addLoadedExtensionLibrary(sharedLibrary);

            // add to 'sys.modules'
            PDict sysModules = context.getSysModules();
            sysModules.setItem(spec.name, result);

            // _PyState_AddModule
            Object moduleDef = module.getNativeModuleDef();
            int mIndex = PythonUtils.toIntError(CStructAccess.ReadI64Node.getUncached().read(moduleDef, CFields.PyModuleDef_Base__m_index));
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

    /**
     * An array wrapper around {@link PythonCextBuiltinRegistry#builtins} which also implements
     * {@link InteropLibrary#toNative(Object)}. This is intended to be passed to the C API
     * initialization function. In order to avoid memory leaks if the wrapper receives
     * {@code toNative}, it should be used in a try-with-resources.
     */
    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings("static-method")
    static final class BuiltinArrayWrapper implements TruffleObject, AutoCloseable {
        private long pointer;

        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        long getArraySize() {
            return PythonCextBuiltinRegistry.builtins.length;
        }

        @ExportMessage
        boolean isArrayElementReadable(long index) {
            return 0 <= index && index < PythonCextBuiltinRegistry.builtins.length;
        }

        @ExportMessage
        @TruffleBoundary
        Object readArrayElement(long index) throws InvalidArrayIndexException {
            if (!isArrayElementReadable(index)) {
                throw InvalidArrayIndexException.create(index);
            }
            // cast is guaranteed by 'isArrayElementReadable'
            return getCAPIBuiltinExecutable((int) index);
        }

        private static CApiBuiltinExecutable getCAPIBuiltinExecutable(int id) {
            CompilerAsserts.neverPartOfCompilation();
            try {
                CApiBuiltinExecutable builtin = PythonCextBuiltinRegistry.builtins[id];
                assert builtin.call() == CApiCallPath.Direct || !isAvailable(builtin) : "name clash in builtin vs. CAPI library: " + builtin.name();
                LOGGER.finer("CApiContext.BuiltinArrayWrapper.get " + id + " / " + builtin.name());
                return builtin;
            } catch (Throwable e) {
                // this is a fatal error, so print it to stderr:
                e.printStackTrace(new PrintStream(PythonContext.get(null).getEnv().err()));
                throw new RuntimeException(e);
            }
        }

        @ExportMessage
        boolean isPointer() {
            return pointer != 0;
        }

        @ExportMessage
        long asPointer() throws UnsupportedMessageException {
            if (pointer != 0) {
                return pointer;
            }
            throw UnsupportedMessageException.create();
        }

        @ExportMessage
        @TruffleBoundary
        void toNative() {
            if (pointer == 0) {
                assert PythonContext.get(null).isNativeAccessAllowed();
                Object ptr = CStructAccess.AllocateNode.callocUncached(PythonCextBuiltinRegistry.builtins.length, CStructAccess.POINTER_SIZE);
                pointer = CExtCommonNodes.CoerceNativePointerToLongNode.executeUncached(ptr);
                if (pointer != 0) {
                    InteropLibrary lib = null;
                    for (int i = 0; i < PythonCextBuiltinRegistry.builtins.length; i++) {
                        CApiBuiltinExecutable capiBuiltinExecutable = getCAPIBuiltinExecutable(i);
                        if (lib == null || !lib.accepts(capiBuiltinExecutable)) {
                            lib = InteropLibrary.getUncached(capiBuiltinExecutable);
                        }
                        assert lib.accepts(capiBuiltinExecutable);
                        lib.toNative(capiBuiltinExecutable);
                        try {
                            CStructAccess.WritePointerNode.writeArrayElementUncached(pointer, i, lib.asPointer(capiBuiltinExecutable));
                        } catch (UnsupportedMessageException e) {
                            throw CompilerDirectives.shouldNotReachHere(e);
                        }
                    }
                }
            }
        }

        @Override
        public void close() {
            if (pointer != 0) {
                FreeNode.executeUncached(pointer);
            }
        }

        private static boolean isAvailable(CApiBuiltinExecutable builtin) {
            CApiContext cApiContext = PythonContext.get(null).getCApiContext();
            if (cApiContext == null) {
                return false;
            }
            Object llvmLibrary = cApiContext.getLLVMLibrary();
            InteropLibrary lib = InteropLibrary.getUncached(llvmLibrary);
            if (!lib.isMemberReadable(llvmLibrary, builtin.name())) {
                return false;
            }
            try {
                lib.readMember(llvmLibrary, builtin.name());
                return true;
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            } catch (UnknownIdentifierException e) {
                // NFI lied to us about symbol availability!
                return false;
            }
        }
    }

    public long getClosurePointer(Object executable) {
        CompilerAsserts.neverPartOfCompilation();
        ClosureInfo info = callableClosureByExecutable.get(executable);
        return info == null ? -1 : info.pointer;
    }

    public Object getClosureForExecutable(Object executable) {
        CompilerAsserts.neverPartOfCompilation();
        ClosureInfo info = callableClosureByExecutable.get(executable);
        return info == null ? null : info.closure;
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

    public void setClosurePointer(Object closure, Object delegate, Object executable, long pointer) {
        CompilerAsserts.neverPartOfCompilation();
        var info = new ClosureInfo(closure, delegate, executable, pointer);
        callableClosureByExecutable.put(executable, info);
        callableClosures.put(pointer, info);
        LOGGER.finer(() -> PythonUtils.formatJString("new NFI closure: (%s, %s) -> %d 0x%x", executable.getClass().getSimpleName(), delegate, pointer, pointer));
    }

    private static Source buildNFISource(Object srcObj) {
        return Source.newBuilder(J_NFI_LANGUAGE, (String) srcObj, "exec").build();
    }

    public long registerClosure(String nfiSignature, Object executable, Object delegate, SignatureLibrary signatureLibrary) {
        CompilerAsserts.neverPartOfCompilation();
        PythonContext context = getContext();
        boolean panama = context.getOption(PythonOptions.UsePanama);
        String srcString = (panama ? "with panama " : "") + nfiSignature;
        Source nfiSource = context.getLanguage().getOrCreateSource(CApiContext::buildNFISource, srcString);
        Object signature = context.getEnv().parseInternal(nfiSource).call();
        Object closure = signatureLibrary.createClosure(signature, executable);
        long pointer = PythonUtils.coerceToLong(closure, InteropLibrary.getUncached());
        setClosurePointer(closure, delegate, executable, pointer);
        return pointer;
    }

    /**
     * A cache for the wrappers of type slot methods. The key is a weak reference to the owner class
     * and the value is a table of wrappers. In order to ensure pointer identity, it is important
     * that we use the same wrapper instance as long as the class exists (and the slot wasn't
     * updated). Also, the key's type is {@link PythonManagedClass} such that any
     * {@link PythonBuiltinClassType} must be resolved, and we do not accidentally have two
     * different entries for the same built-in class.
     */
    private final WeakIdentityHashMap<PythonManagedClass, PyProcsWrapper[]> procWrappers = new WeakIdentityHashMap<>();

    @TruffleBoundary
    public Object getOrCreateProcWrapper(PythonManagedClass owner, SlotMethodDef slot, Supplier<PyProcsWrapper> supplier) {
        PyProcsWrapper[] slotWrappers = procWrappers.computeIfAbsent(owner, key -> new PyProcsWrapper[SlotMethodDef.values().length]);
        int idx = slot.ordinal();
        PyProcsWrapper wrapper = slotWrappers[idx];
        if (wrapper == null) {
            wrapper = supplier.get();
            slotWrappers[idx] = wrapper;
        }
        return wrapper;
    }

    @TruffleBoundary
    public Object getOrAllocateNativePyMethodDef(PyMethodDefHelper pyMethodDef) {
        Object pyMethodDefPointer = methodDefinitions.computeIfAbsent(pyMethodDef, PyMethodDefHelper::allocate);
        assert CApiContext.isPointerObject(pyMethodDefPointer);
        return pyMethodDefPointer;
    }

    /**
     * A table mapping {@link BuiltinMethodDescriptor} or {@link RootCallTarget} to the appropriate
     * {@link PyCFunctionWrapper}. This could actually be shared between Python contexts but
     * {@link PyCFunctionWrapper} is still a {@link TruffleObject} and so it is assumed to be
     * context-specific although our wrapper doesn't contain any data and is just used for executing
     * code.
     */
    private final ConcurrentHashMap<Object, PyCFunctionWrapper> pyCFunctionWrappers = new ConcurrentHashMap<>(4);

    @TruffleBoundary
    public PyCFunctionWrapper getOrCreatePyCFunctionWrapper(BuiltinMethodDescriptor builtinMethodDescriptor, Function<BuiltinMethodDescriptor, PyCFunctionWrapper> cons) {
        return pyCFunctionWrappers.computeIfAbsent(builtinMethodDescriptor, k -> cons.apply((BuiltinMethodDescriptor) k));
    }

    @TruffleBoundary
    public PyCFunctionWrapper getOrCreatePyCFunctionWrapper(RootCallTarget ct, Function<RootCallTarget, PyCFunctionWrapper> cons) {
        return pyCFunctionWrappers.computeIfAbsent(ct, k -> cons.apply((RootCallTarget) k));
    }

    public static boolean isPointerObject(Object object) {
        return object.getClass() == NativePointer.class || object.getClass().getSimpleName().contains("NFIPointer") || object.getClass().getSimpleName().contains("LLVMPointer");
    }
}
