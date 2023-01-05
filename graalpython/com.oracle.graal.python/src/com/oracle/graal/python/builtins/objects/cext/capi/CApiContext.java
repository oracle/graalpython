/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___FILE__;
import static com.oracle.graal.python.nodes.StringLiterals.J_GET_;
import static com.oracle.graal.python.nodes.StringLiterals.J_LLVM_LANGUAGE;
import static com.oracle.graal.python.nodes.StringLiterals.J_TYPE_ID;
import static com.oracle.graal.python.util.PythonUtils.EMPTY_TRUFFLESTRING_ARRAY;
import static com.oracle.graal.python.util.PythonUtils.tsArray;

import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Pair;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.AddRefCntNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.AttachLLVMTypeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.GetRefCntNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.AsPythonObjectNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.CreateModuleNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.ResolveHandleNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.DynamicObjectNativeWrapper.PrimitiveNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeObjectReferenceArrayWrapper.PointerArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeObjectReferenceArrayWrapper.RefCountArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.CheckFunctionResultNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtContext;
import com.oracle.graal.python.builtins.objects.cext.common.LoadCExtException.ApiInitException;
import com.oracle.graal.python.builtins.objects.cext.common.LoadCExtException.ImportException;
import com.oracle.graal.python.builtins.objects.cext.common.ReferenceStack;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.IndirectCallNode;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.call.GenericInvokeNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.AsyncHandler;
import com.oracle.graal.python.runtime.ExecutionContext.CalleeContext;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonContext.GetThreadStateNode;
import com.oracle.graal.python.runtime.PythonContext.PythonThreadState;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInterface;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.Source.SourceBuilder;
import com.oracle.truffle.api.strings.TruffleString;

public final class CApiContext extends CExtContext {
    public static final String LOGGER_CAPI_NAME = "capi";
    private static final TruffleLogger LOGGER = PythonLanguage.getLogger(LOGGER_CAPI_NAME);

    /**
     * A dummy context to disambiguate between <it>context not yet created</it> and <it>context
     * should be looked up lazily</it>
     */
    static final CApiContext LAZY_CONTEXT = new CApiContext();

    public static final long REFERENCE_COUNT_BITS = Integer.SIZE;
    public static final long REFERENCE_COUNT_MARKER = (1L << REFERENCE_COUNT_BITS);
    /* a random number between 1 and 20 */
    private static final int MAX_COLLECTION_RETRIES = 17;

    /** Total amount of allocated native memory (in bytes). */
    private long allocatedMemory = 0;

    private final ReferenceQueue<Object> nativeObjectsQueue;
    private Map<Object, AllocInfo> allocatedNativeMemory;
    private final ReferenceStack<NativeObjectReference> nativeObjectWrapperList;
    private TraceMallocDomain[] traceMallocDomains;

    /** Container of pointers that have seen to be free'd. */
    private Map<Object, AllocInfo> freedNativeMemory;

    @CompilationFinal private RootCallTarget referenceCleanerCallTarget;

    /**
     * This cache is used to cache native wrappers for frequently used primitives. This is strictly
     * defined to be the range {@code [-5, 256]}. CPython does exactly the same (see
     * {@code PyLong_FromLong}; implemented in macro {@code CHECK_SMALL_INT}).
     */
    @CompilationFinal(dimensions = 1) private final PrimitiveNativeWrapper[] primitiveNativeWrapperCache;

    /** Just used for integrity checks if assertions are enabled. */
    @CompilationFinal private InteropLibrary interoplibrary;

    /**
     * Required to emulate PyLongObject's ABI; number of bits per digit (equal to
     * {@code PYLONG_BITS_IN_DIGIT}.
     */
    @CompilationFinal private int pyLongBitsInDigit = -1;

    /** Cache for polyglot types of primitive and pointer types. */
    @CompilationFinal(dimensions = 1) private final Object[] llvmTypeCache;

    /** same as {@code moduleobject.c: max_module_number} */
    private long maxModuleNumber;

    /** Same as {@code import.c: extensions} but we don't keep a PDict; just a bare Java HashMap. */
    private final HashMap<Pair<TruffleString, TruffleString>, Object> extensions = new HashMap<>(4);

    private final ArrayList<Object> modulesByIndex = new ArrayList<>(0);

    /**
     * Thread local storage for PyThread_tss_* APIs
     */
    private final ConcurrentHashMap<Long, ThreadLocal<Object>> tssStorage = new ConcurrentHashMap<>();
    /**
     * Next key that will be allocated byt PyThread_tss_create
     */
    private AtomicLong nextTssKey = new AtomicLong();

    public static TruffleLogger getLogger(Class<?> clazz) {
        return PythonLanguage.getLogger(LOGGER_CAPI_NAME + "." + clazz.getSimpleName());
    }

    /**
     * Private dummy constructor just for {@link #LAZY_CONTEXT}.
     */
    private CApiContext() {
        super(null, null, null);
        nativeObjectsQueue = null;
        nativeObjectWrapperList = null;
        primitiveNativeWrapperCache = null;
        llvmTypeCache = null;
    }

    public CApiContext(PythonContext context, Object hpyLibrary) {
        super(context, hpyLibrary, CAPIConversionNodeSupplier.INSTANCE);
        nativeObjectsQueue = new ReferenceQueue<>();
        nativeObjectWrapperList = new ReferenceStack<>();

        // avoid 0 to be used as ID
        int nullID = nativeObjectWrapperList.reserve();
        assert nullID == 0;

        // initialize primitive and pointer type cache
        llvmTypeCache = new Object[LLVMType.values().length];

        // initialize primitive native wrapper cache
        primitiveNativeWrapperCache = new PrimitiveNativeWrapper[262];
        for (int i = 0; i < primitiveNativeWrapperCache.length; i++) {
            PrimitiveNativeWrapper nativeWrapper = PrimitiveNativeWrapper.createInt(i - 5);
            nativeWrapper.increaseRefCount();
            primitiveNativeWrapperCache[i] = nativeWrapper;
        }

        context.registerAsyncAction(() -> {
            Reference<?> reference = null;
            try {
                reference = nativeObjectsQueue.remove();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            ArrayList<NativeObjectReference> refs = new ArrayList<>();
            do {
                if (reference instanceof NativeObjectReference) {
                    refs.add((NativeObjectReference) reference);
                }
                // consume all
                reference = nativeObjectsQueue.poll();
            } while (reference != null);

            if (!refs.isEmpty()) {
                return new CApiReferenceCleanerAction(refs.toArray(new NativeObjectReference[0]));
            }

            return null;
        });
    }

    public int getPyLongBitsInDigit() {
        if (pyLongBitsInDigit < 0) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            pyLongBitsInDigit = (int) CExtNodes.PCallCapiFunction.getUncached().call(NativeCAPISymbol.FUN_GET_LONG_BITS_PER_DIGIT);
        }
        return pyLongBitsInDigit;
    }

    public Object getLLVMTypeID(LLVMType llvmType) {
        return llvmTypeCache[llvmType.ordinal()];
    }

    public void setLLVMTypeID(LLVMType llvmType, Object llvmTypeId) {
        llvmTypeCache[llvmType.ordinal()] = llvmTypeId;
    }

    public long getAndIncMaxModuleNumber() {
        return maxModuleNumber++;
    }

    @TruffleBoundary
    public static Object asHex(Object ptr) {
        if (ptr instanceof Number) {
            return "0x" + Long.toHexString(((Number) ptr).longValue());
        }
        return Objects.toString(ptr);
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
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException();
            }
        }
        return ptr;
    }

    private RootCallTarget getReferenceCleanerCallTarget() {
        if (referenceCleanerCallTarget == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            referenceCleanerCallTarget = PythonLanguage.get(null).createCachedCallTarget(l -> new CApiReferenceCleanerRootNode(l), CApiReferenceCleanerRootNode.class);
        }
        return referenceCleanerCallTarget;
    }

    public TraceMallocDomain getTraceMallocDomain(int domainIdx) {
        return traceMallocDomains[domainIdx];
    }

    public int findOrCreateTraceMallocDomain(long id) {
        int oldLength;
        if (traceMallocDomains != null) {
            for (int i = 0; i < traceMallocDomains.length; i++) {
                if (traceMallocDomains[i].id == id) {
                    return i;
                }
            }

            // create new domain
            oldLength = traceMallocDomains.length;
            traceMallocDomains = Arrays.copyOf(traceMallocDomains, traceMallocDomains.length + 1);
        } else {
            oldLength = 0;
            traceMallocDomains = new TraceMallocDomain[1];
        }
        traceMallocDomains[oldLength] = new TraceMallocDomain(id);
        return oldLength;
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

    public PrimitiveNativeWrapper getCachedPrimitiveNativeWrapper(int i) {
        assert CApiGuards.isSmallInteger(i);
        PrimitiveNativeWrapper primitiveNativeWrapper = primitiveNativeWrapperCache[i + 5];
        primitiveNativeWrapper.increaseRefCount();
        assert primitiveNativeWrapper.getRefCount() > 0;
        return primitiveNativeWrapper;
    }

    public PrimitiveNativeWrapper getCachedPrimitiveNativeWrapper(long l) {
        assert CApiGuards.isSmallLong(l);
        return getCachedPrimitiveNativeWrapper((int) l);
    }

    @TruffleBoundary
    @Override
    protected Store initializeSymbolCache() {
        PythonLanguage language = getContext().getLanguage();
        Shape symbolCacheShape = language.getCApiSymbolCacheShape();
        // We will always get an empty shape from the language and we do always add same key-value
        // pairs (in the same order). So, in the end, each context should get the same shape.
        Store s = new Store(symbolCacheShape);
        for (NativeCAPISymbol sym : NativeCAPISymbol.getValues()) {
            DynamicObjectLibrary.getUncached().put(s, sym, PNone.NO_VALUE);
        }
        return s;
    }

    public Object getModuleByIndex(int i) {
        if (i < modulesByIndex.size()) {
            return modulesByIndex.get(i);
        }
        return null;
    }

    static class NativeObjectReference extends WeakReference<PythonAbstractNativeObject> {

        /**
         * The associated native pointer object that needs to be released if this reference dies.
         */
        final Object ptrObject;

        /** The ID of this reference, i.e., the index of the ref in the global reference list. */
        final int id;

        /**
         * If {@code true}, the native object should not be released because a new managed ref was
         * created.
         */
        boolean resurrect;

        /**
         * When stealing references, this is the number of stolen reference counts (need to be
         * subtracted in the end).
         */
        long managedRefCount;

        public NativeObjectReference(PythonAbstractNativeObject referent, ReferenceQueue<? super PythonAbstractNativeObject> q, long managedRefCount, int id) {
            super(referent, q);
            this.ptrObject = referent.getPtr();
            this.managedRefCount = managedRefCount;
            this.id = id;
        }

        public Object getPtrObject() {
            return ptrObject;
        }

        public void markAsResurrected() {
            resurrect = true;
        }
    }

    /**
     * Simple root node that executes a reference decrease.
     */
    private static final class CApiReferenceCleanerRootNode extends PRootNode {
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, tsArray("ptr", "managedRefCount"), EMPTY_TRUFFLESTRING_ARRAY);
        private static final TruffleLogger LOGGER = CApiContext.getLogger(CApiReferenceCleanerRootNode.class);

        @Child private CalleeContext calleeContext;
        @Child private InteropLibrary pointerObjectLib;
        @Child private PCallCapiFunction callBulkSubref;

        protected CApiReferenceCleanerRootNode(PythonLanguage language) {
            super(language);
            this.calleeContext = CalleeContext.create();
            this.callBulkSubref = PCallCapiFunction.create();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            calleeContext.enter(frame);
            try {
                NativeObjectReference[] nativeObjectReferences = (NativeObjectReference[]) PArguments.getArgument(frame, 0);
                int cleaned = 0;
                CApiContext cApiContext = PythonContext.get(this).getCApiContext();
                long allocatedNativeMem = cApiContext.allocatedMemory;
                long startTime = 0;
                long middleTime = 0;
                final int n = nativeObjectReferences.length;
                boolean loggable = LOGGER.isLoggable(Level.FINE);

                if (loggable) {
                    startTime = System.currentTimeMillis();
                }

                /*
                 * Note about the order of operations - we need to call the finalizers first before
                 * removing the objects from the wrapper list because the finalizers may still make
                 * upcalls and those need the wrappers to work correctly.
                 */

                callBulkSubref.call(NativeCAPISymbol.FUN_BULK_SUBREF, new PointerArrayWrapper(nativeObjectReferences), new RefCountArrayWrapper(nativeObjectReferences), (long) n);

                if (loggable) {
                    middleTime = System.currentTimeMillis();
                }

                if (LOGGER.isLoggable(Level.FINER)) {
                    // it's not an OSR loop, so we do this before the loop
                    if (n > 0 && pointerObjectLib == null) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        pointerObjectLib = insert(InteropLibrary.getFactory().create(nativeObjectReferences[0].ptrObject));
                    }

                    for (int i = 0; i < n; i++) {
                        NativeObjectReference nativeObjectReference = nativeObjectReferences[i];
                        Object pointerObject = nativeObjectReference.ptrObject;
                        if (!nativeObjectReference.resurrect) {
                            cApiContext.nativeObjectWrapperList.remove(nativeObjectReference.id);
                            if (!nativeObjectReference.resurrect && !pointerObjectLib.isNull(pointerObject)) {
                                cApiContext.checkAccess(pointerObject, pointerObjectLib);
                                LOGGER.finer(() -> "Cleaning native object reference to " + CApiContext.asHex(pointerObject));
                                cleaned++;
                            }
                        }
                    }
                } else {
                    for (int i = 0; i < n; i++) {
                        NativeObjectReference nativeObjectReference = nativeObjectReferences[i];
                        if (!nativeObjectReference.resurrect) {
                            cApiContext.nativeObjectWrapperList.remove(nativeObjectReference.id);
                        }
                    }
                }

                if (loggable) {
                    final long countDuration = System.currentTimeMillis() - middleTime;
                    final long duration = middleTime - startTime;
                    final int finalCleaned = cleaned;
                    final long freedNativeMemory = allocatedNativeMem - cApiContext.allocatedMemory;
                    LOGGER.fine(() -> "Total queued references: " + n);
                    LOGGER.fine(() -> "Cleaned references: " + finalCleaned);
                    LOGGER.fine(() -> "Free'd native memory: " + freedNativeMemory);
                    LOGGER.fine(() -> "Count duration: " + countDuration);
                    LOGGER.fine(() -> "Duration: " + duration);
                }
            } finally {
                calleeContext.exit(frame, this);
            }
            return PNone.NONE;
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }

        @Override
        public String getName() {
            return "native_reference_cleaner";
        }

        @Override
        public boolean isInternal() {
            return false;
        }

        @Override
        public boolean isPythonInternal() {
            return false;
        }
    }

    /**
     * Reference cleaner action that will be executed by the {@link AsyncHandler}.
     */
    private static final class CApiReferenceCleanerAction implements AsyncHandler.AsyncAction {

        private final NativeObjectReference[] nativeObjectReferences;

        public CApiReferenceCleanerAction(NativeObjectReference[] nativeObjectReferences) {
            this.nativeObjectReferences = nativeObjectReferences;
        }

        @Override
        public void execute(PythonContext context) {
            Object[] pArguments = PArguments.create(1);
            PArguments.setArgument(pArguments, 0, nativeObjectReferences);
            GenericInvokeNode.getUncached().execute(context.getCApiContext().getReferenceCleanerCallTarget(), pArguments);
        }
    }

    public NativeObjectReference lookupNativeObjectReference(int idx) {
        return nativeObjectWrapperList.get(idx);
    }

    public PythonAbstractNativeObject getPythonNativeObject(Object nativePtr, ConditionProfile newRefProfile, ConditionProfile validRefProfile, ConditionProfile resurrectProfile,
                    GetRefCntNode getObRefCntNode, AddRefCntNode addRefCntNode, AttachLLVMTypeNode attachLLVMTypeNode) {
        return getPythonNativeObject(nativePtr, newRefProfile, validRefProfile, resurrectProfile, getObRefCntNode, addRefCntNode, false, attachLLVMTypeNode);
    }

    public PythonAbstractNativeObject getPythonNativeObject(Object nativePtr, ConditionProfile newRefProfile, ConditionProfile validRefProfile, ConditionProfile resurrectProfile,
                    GetRefCntNode getObRefCntNode, AddRefCntNode addRefCntNode, boolean steal, AttachLLVMTypeNode attachLLVMTypeNode) {
        CompilerAsserts.partialEvaluationConstant(addRefCntNode);
        CompilerAsserts.partialEvaluationConstant(steal);

        int id = CApiContext.idFromRefCnt(getObRefCntNode.execute(this, nativePtr));

        NativeObjectReference ref;

        // If there is no mapping, we need to create a new one.
        if (newRefProfile.profile(id == 0)) {
            return createPythonAbstractNativeObject(nativePtr, addRefCntNode, steal, attachLLVMTypeNode);
        } else if (validRefProfile.profile(id > 0)) {
            ref = lookupNativeObjectReference(id);
            if (ref != null) {
                PythonAbstractNativeObject nativeObject = ref.get();
                if (resurrectProfile.profile(nativeObject == null)) {
                    // Bad luck: the mapping is still there and wasn't cleaned up but we need a new
                    // mapping. Therefore, we need to cancel the cleaner action and set a new native
                    // object reference.
                    ref.markAsResurrected();
                    nativeObject = new PythonAbstractNativeObject(nativePtr);
                    assert id == ref.id;

                    ref = new NativeObjectReference(nativeObject, nativeObjectsQueue, ref.managedRefCount, id);
                    NativeObjectReference old = nativeObjectWrapperList.resurrect(id, ref);
                    assert isReferenceToSameNativeObject(old, ref) : "resurrected native object reference does not point to same native object";
                }
                if (steal) {
                    ref.managedRefCount++;
                }
                return nativeObject;
            }
            return createPythonAbstractNativeObject(nativePtr, addRefCntNode, steal, attachLLVMTypeNode);
        } else {
            LOGGER.warning(() -> PythonUtils.formatJString("cannot associate a native object reference to %s because reference count is corrupted", CApiContext.asHex(nativePtr)));
        }
        return new PythonAbstractNativeObject(nativePtr);
    }

    PythonAbstractNativeObject createPythonAbstractNativeObject(Object nativePtr, AddRefCntNode addRefCntNode, boolean steal, AttachLLVMTypeNode attachLLVMTypeNode) {
        PythonAbstractNativeObject nativeObject = new PythonAbstractNativeObject(attachLLVMTypeNode.execute(nativePtr));
        int nativeRefID = nativeObjectWrapperList.reserve();
        assert nativeRefID != -1;

        long nativeRefCnt = CApiContext.idToRefCnt(nativeRefID);
        assert nativeRefCnt >= REFERENCE_COUNT_MARKER;
        NativeObjectReference ref = new NativeObjectReference(nativeObject, nativeObjectsQueue, nativeRefCnt + (steal ? 1L : 0L), nativeRefID);

        addRefCntNode.execute(nativePtr, nativeRefCnt);
        nativeObjectWrapperList.commit(nativeRefID, ref);
        return nativeObject;
    }

    /**
     * Checks if the given {@link NativeObjectReference} objects point to the same native object.
     * This method lazily initializes {@link #interoplibrary} as a side-effect.
     */
    private boolean isReferenceToSameNativeObject(NativeObjectReference old, NativeObjectReference ref) {
        if (interoplibrary == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            interoplibrary = InteropLibrary.getFactory().getUncached();
        }
        return interoplibrary.isIdentical(old.ptrObject, ref.ptrObject, interoplibrary);
    }

    static int idFromRefCnt(long refCnt) {
        long idx = refCnt >> REFERENCE_COUNT_BITS;
        assert idx >= 0;
        return (int) idx;
    }

    static long idToRefCnt(int id) {
        long nativeRefCnt = (long) id << REFERENCE_COUNT_BITS;
        assert nativeRefCnt >= REFERENCE_COUNT_MARKER;
        return nativeRefCnt;
    }

    @TruffleBoundary
    public void traceFree(Object ptr) {
        traceFree(ptr, null, null);
    }

    @TruffleBoundary
    public AllocInfo traceFree(Object ptr, @SuppressWarnings("unused") PFrame.Reference curFrame, @SuppressWarnings("unused") TruffleString clazzName) {
        if (allocatedNativeMemory == null) {
            allocatedNativeMemory = new HashMap<>();
        }
        if (freedNativeMemory == null) {
            freedNativeMemory = new HashMap<>();
        }
        AllocInfo allocatedValue = allocatedNativeMemory.remove(ptr);
        Object freedValue = freedNativeMemory.put(ptr, allocatedValue);
        if (freedValue != null) {
            LOGGER.severe(PythonUtils.formatJString("freeing memory that was already free'd %s (double-free)", asHex(ptr)));
        } else if (allocatedValue == null) {
            LOGGER.info(PythonUtils.formatJString("freeing non-allocated memory %s (maybe a double-free or we didn't trace the allocation)", asHex(ptr)));
        }
        return allocatedValue;
    }

    @TruffleBoundary
    public void traceAlloc(Object ptr, PFrame.Reference curFrame, TruffleString clazzName, long size) {
        if (allocatedNativeMemory == null) {
            allocatedNativeMemory = new HashMap<>();
        }
        Object value = allocatedNativeMemory.put(ptr, new AllocInfo(clazzName, curFrame, size));
        if (freedNativeMemory != null) {
            freedNativeMemory.remove(ptr);
        }
        assert value == null : "native memory allocator reserved same memory twice";
    }

    @SuppressWarnings("unused")
    public void trackObject(Object ptr, PFrame.Reference curFrame, TruffleString clazzName) {
        // TODO(fa): implement tracking of container objects for cycle detection
    }

    @SuppressWarnings("unused")
    public void untrackObject(Object ptr, PFrame.Reference curFrame, TruffleString clazzName) {
        // TODO(fa): implement untracking of container objects
    }

    /**
     * Use this method to register memory that is known to be allocated (i.e. static variables like
     * types). This is basically the same as
     * {@link #traceAlloc(Object, PFrame.Reference, TruffleString, long)} but does not consider it
     * to be an error if the memory is already allocated.
     */
    @TruffleBoundary
    public void traceStaticMemory(Object ptr, PFrame.Reference curFrame, TruffleString clazzName) {
        if (allocatedNativeMemory == null) {
            allocatedNativeMemory = new HashMap<>();
        }
        if (freedNativeMemory != null) {
            freedNativeMemory.remove(ptr);
        }
        allocatedNativeMemory.put(ptr, new AllocInfo(curFrame, clazzName));
    }

    @TruffleBoundary
    public boolean isAllocated(Object ptr) {
        if (freedNativeMemory != null && freedNativeMemory.containsKey(ptr)) {
            assert !allocatedNativeMemory.containsKey(ptr);
            return false;
        }
        return true;
    }

    public void increaseMemoryPressure(long size, Node node) {
        PythonContext context = getContext();
        if (allocatedMemory <= context.getOption(PythonOptions.MaxNativeMemory)) {
            allocatedMemory += size;
            return;
        }
        triggerGC(context, size, node);
    }

    public void increaseMemoryPressure(VirtualFrame frame, GetThreadStateNode getThreadStateNode, IndirectCallNode caller, long size) {
        PythonContext context = getContext();
        if (allocatedMemory + size <= context.getOption(PythonOptions.MaxNativeMemory)) {
            allocatedMemory += size;
            return;
        }

        PythonThreadState threadState = getThreadStateNode.execute(context);
        Object savedState = IndirectCallContext.enter(frame, threadState, caller);
        try {
            triggerGC(context, size, caller);
        } finally {
            IndirectCallContext.exit(frame, threadState, savedState);
        }
    }

    @TruffleBoundary
    private void triggerGC(PythonContext context, long size, NodeInterface caller) {
        long delay = 0;
        for (int retries = 0; retries < MAX_COLLECTION_RETRIES; retries++) {
            delay += 50;
            doGc(delay);
            PythonContext.triggerAsyncActions((Node) caller);
            if (allocatedMemory + size <= context.getOption(PythonOptions.MaxNativeMemory)) {
                allocatedMemory += size;
                return;
            }
        }
        throw new OutOfMemoryError("native memory");
    }

    public void reduceMemoryPressure(long size) {
        allocatedMemory -= size;
    }

    @TruffleBoundary
    private static void doGc(long millis) {
        LOGGER.fine("full GC due to native memory");
        PythonUtils.forceFullGC();
        try {
            Thread.sleep(millis);
        } catch (InterruptedException x) {
            // Restore interrupt status
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Tests if any read/write access to the given pointer object is invalid. This should be used to
     * test access before getting the type of reference count of native objects.
     */
    public void checkAccess(Object pointerObject, InteropLibrary lib) {
        if (getContext().getOption(PythonOptions.TraceNativeMemory)) {
            Object ptrVal = CApiContext.asPointer(pointerObject, lib);
            if (!isAllocated(ptrVal)) {
                LOGGER.severe(() -> "Access to invalid memory at " + CApiContext.asHex(ptrVal));
            }
        }
    }

    /**
     * Internal method for debugging purposes. This method looks up how many phantom references are
     * currently in the escaped references list. This is useful to check if the current reference
     * count of a native object is consistent with the upcoming decrements.
     */
    @SuppressWarnings("unused")
    public List<Integer> containsAddress(long l) {
        CompilerAsserts.neverPartOfCompilation();
        int i = 0;
        List<Integer> indx = new ArrayList<>();
        InteropLibrary lib = InteropLibrary.getFactory().getUncached();
        for (NativeObjectReference nor : nativeObjectWrapperList) {
            Object obj = nor.ptrObject;

            try {
                if (lib.isPointer(obj) && lib.asPointer(obj) == l) {
                    indx.add(i);
                }
            } catch (UnsupportedMessageException e) {
                // ignore
            }
            i++;
        }
        return indx;
    }

    public static final class AllocInfo {
        public final TruffleString typeName;
        public final PFrame.Reference allocationSite;
        public final long size;

        public AllocInfo(TruffleString typeName, PFrame.Reference allocationSite, long size) {
            this.typeName = typeName;
            this.allocationSite = allocationSite;
            this.size = size;
        }

        public AllocInfo(PFrame.Reference allocationSite, TruffleString typeName) {
            this(typeName, allocationSite, -1);
        }
    }

    public static final class TraceMallocDomain {
        private final long id;
        private final EconomicMap<Object, Long> allocatedMemory;

        public TraceMallocDomain(long id) {
            this.id = id;
            this.allocatedMemory = EconomicMap.create();
        }

        @TruffleBoundary
        public void track(Object pointerObject, long size) {
            allocatedMemory.put(pointerObject, size);
        }

        @TruffleBoundary
        public long untrack(Object pointerObject) {
            Long value = allocatedMemory.removeKey(pointerObject);
            if (value != null) {
                // TODO(fa): be more restrictive?
                return value;
            }
            return 0;
        }

        public long getId() {
            return id;
        }
    }

    /**
     * Enum of basic C types. These type names need to stay in sync with the declarations in
     * 'modsupport.c'.
     */
    public enum LLVMType {
        int_t,
        uint_t,
        int8_t,
        int16_t,
        int32_t,
        int64_t,
        uint8_t,
        uint16_t,
        uint32_t,
        uint64_t,
        long_t,
        ulong_t,
        longlong_t,
        ulonglong_t,
        float_t,
        double_t,
        size_t,
        Py_ssize_t,
        Py_complex,
        PyObject,
        PyMethodDef,
        PyTypeObject,
        PyObject_ptr_t,
        char_ptr_t,
        void_ptr_t,
        int8_ptr_t,
        int16_ptr_t,
        int32_ptr_t,
        int64_ptr_t,
        uint8_ptr_t,
        uint16_ptr_t,
        uint32_ptr_t,
        uint64_ptr_t,
        Py_complex_ptr_t,
        PyObject_ptr_ptr_t,
        float_ptr_t,
        double_ptr_t,
        Py_ssize_ptr_t,
        PyThreadState;

        private final NativeCAPISymbol getter = NativeCAPISymbol.getByName(J_GET_ + name() + J_TYPE_ID);

        public NativeCAPISymbol getGetterFunctionName() {
            if (getter == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw CompilerDirectives.shouldNotReachHere("no getter for LLVMType " + name());
            }
            return getter;
        }

        public static boolean isPointer(LLVMType llvmType) {
            switch (llvmType) {
                case PyObject_ptr_t:
                case char_ptr_t:
                case int8_ptr_t:
                case int16_ptr_t:
                case int32_ptr_t:
                case int64_ptr_t:
                case uint8_ptr_t:
                case uint16_ptr_t:
                case uint32_ptr_t:
                case uint64_ptr_t:
                case Py_complex_ptr_t:
                case PyObject_ptr_ptr_t:
                case float_ptr_t:
                case double_ptr_t:
                case Py_ssize_ptr_t:
                    return true;
            }
            return false;
        }

        public static boolean isPointerToPrimitive(LLVMType llvmType) {
            switch (llvmType) {
                case int8_ptr_t:
                case int16_ptr_t:
                case int32_ptr_t:
                case int64_ptr_t:
                case uint8_ptr_t:
                case uint16_ptr_t:
                case uint32_ptr_t:
                case uint64_ptr_t:
                case float_ptr_t:
                case double_ptr_t:
                case char_ptr_t:
                    return true;
            }
            return false;
        }
    }

    // XXX: We have to hold on to this so that NFI doesn't unload the library again
    static Object nativeLibpython;

    @TruffleBoundary
    public static CApiContext ensureCapiWasLoaded(Node node, PythonContext context, TruffleString name, TruffleString path) throws IOException, ImportException, ApiInitException {
        if (!context.hasCApiContext()) {
            Env env = context.getEnv();

            TruffleFile homePath = env.getInternalTruffleFile(context.getCAPIHome().toJavaStringUncached());
            TruffleFile capiFile = homePath.resolve("libpython" + context.getSoAbi().toJavaStringUncached());
            Object capiLibrary;
            try {
                SourceBuilder capiSrcBuilder = Source.newBuilder(J_LLVM_LANGUAGE, capiFile);
                if (!context.getLanguage().getEngineOption(PythonOptions.ExposeInternalSources)) {
                    capiSrcBuilder.internal(true);
                }
                CallTarget capiLibraryCallTarget = context.getEnv().parseInternal(capiSrcBuilder.build());
                // keep the call target of 'libpython' alive; workaround until GR-32297 is fixed
                context.getLanguage().capiLibraryCallTarget = capiLibraryCallTarget;
                capiLibrary = capiLibraryCallTarget.call();

                String libpython = System.getProperty("LibPythonNativeLibrary");
                if (libpython != null) {
                    SourceBuilder nfiSrcBuilder = Source.newBuilder("nfi", "load(RTLD_GLOBAL) \"" + libpython + "\"", "<libpython-native>");
                    nativeLibpython = context.getEnv().parseInternal(nfiSrcBuilder.build()).call();
                }

                CApiContext cApiContext = new CApiContext(context, capiLibrary);
                context.setCapiWasLoaded(cApiContext);
                return cApiContext;
            } catch (PException e) {
                /*
                 * Python exceptions that occur during the C API initialization are just passed
                 * through
                 */
                throw e;
            } catch (RuntimeException e) {
                if (!context.isNativeAccessAllowed()) {
                    throw new ImportException(null, name, path, ErrorMessages.NATIVE_ACCESS_NOT_ALLOWED);
                }
                throw new ApiInitException(wrapJavaException(e, node), name, ErrorMessages.CAPI_LOAD_ERROR, capiFile.getAbsoluteFile().getPath());
            }
        }
        return context.getCApiContext();
    }

    @TruffleBoundary
    public Object initCApiModule(Node location, Object llvmLibrary, TruffleString initFuncName, ModuleSpec spec, InteropLibrary llvmInteropLib, CheckFunctionResultNode checkFunctionResultNode)
                    throws UnsupportedMessageException, ArityException, UnsupportedTypeException, ImportException {
        PythonContext context = getContext();
        Object pyinitFunc;
        try {
            pyinitFunc = llvmInteropLib.readMember(llvmLibrary, initFuncName.toJavaStringUncached());
        } catch (UnknownIdentifierException | UnsupportedMessageException e1) {
            throw new ImportException(null, spec.name, spec.path, ErrorMessages.NO_FUNCTION_FOUND, "", initFuncName, spec.path);
        }
        InteropLibrary pyInitFuncLib = InteropLibrary.getUncached(pyinitFunc);
        Object nativeResult;
        try {
            nativeResult = pyInitFuncLib.execute(pyinitFunc);
        } catch (ArityException e) {
            // In case of multi-phase init, the init function may take more than one argument.
            // However, CPython gracefully ignores that. So, we pass just NULL pointers.
            Object[] arguments = new Object[e.getExpectedMinArity()];
            Arrays.fill(arguments, PNone.NO_VALUE);
            nativeResult = pyInitFuncLib.execute(pyinitFunc, arguments);
        }

        checkFunctionResultNode.execute(context, initFuncName, nativeResult);

        Object result = AsPythonObjectNodeGen.getUncached().execute(ResolveHandleNodeGen.getUncached().execute(nativeResult));
        if (!(result instanceof PythonModule)) {
            // Multi-phase extension module initialization

            /*
             * See 'importdl.c: _PyImport_LoadDynamicModuleWithSpec' before
             * 'PyModule_FromDefAndSpec' is called. The 'PyModule_FromDefAndSpec' would initialize
             * the module def as Python object but before that, CPython explicitly checks if the
             * init function did this initialization by calling 'PyModuleDef_Init' on it. So, we
             * must do it here because 'CreateModuleNode' should just ignore this case.
             */
            Object clazz = GetClassNode.getUncached().execute(result);
            if (clazz == PNone.NO_VALUE) {
                throw PRaiseNode.raiseUncached(location, PythonBuiltinClassType.SystemError, ErrorMessages.INIT_FUNC_RETURNED_UNINT_OBJ, initFuncName);
            }

            return CreateModuleNodeGen.getUncached().execute(context.getCApiContext(), spec, result);
        } else {
            // see: 'import.c: _PyImport_FixupExtensionObject'
            PythonModule module = (PythonModule) result;
            module.setAttribute(T___FILE__, spec.path);

            // add to 'sys.modules'
            PDict sysModules = context.getSysModules();
            sysModules.setItem(spec.name, result);

            // _PyState_AddModule
            Object moduleDef = module.getNativeModuleDef();
            InteropLibrary moduleDefLib = InteropLibrary.getUncached(moduleDef);
            try {
                Object mIndexObject = moduleDefLib.readMember(moduleDefLib.readMember(moduleDef, "m_base"), "m_index");
                int mIndex = InteropLibrary.getUncached().asInt(mIndexObject);
                while (modulesByIndex.size() <= mIndex) {
                    modulesByIndex.add(null);
                }
                modulesByIndex.set(mIndex, module);
            } catch (UnknownIdentifierException | UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }

            // add to 'import.c: extensions'
            extensions.put(Pair.create(spec.path, spec.name), module.getNativeModuleDef());
            return result;
        }
    }

}
