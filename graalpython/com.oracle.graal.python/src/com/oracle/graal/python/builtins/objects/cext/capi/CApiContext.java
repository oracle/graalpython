/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;

import com.oracle.graal.python.builtins.objects.cext.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols;
import org.graalvm.collections.EconomicMap;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.CAPIConversionNodeSupplier;
import com.oracle.graal.python.builtins.objects.cext.CApiGuards;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.AddRefCntNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.GetRefCntNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.SubRefCntNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.SubRefCntNodeGen;
import com.oracle.graal.python.builtins.objects.cext.DynamicObjectNativeWrapper.PrimitiveNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.common.CExtContext;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.call.GenericInvokeNode;
import com.oracle.graal.python.runtime.AsyncHandler;
import com.oracle.graal.python.runtime.ExecutionContext.CalleeContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.llvm.spi.ReferenceLibrary;

public final class CApiContext extends CExtContext {
    private static final TruffleLogger LOGGER = PythonLanguage.getLogger(CApiContext.class);

    public static final long REFERENCE_COUNT_BITS = Integer.SIZE;
    public static final long REFERENCE_COUNT_MARKER = (1L << REFERENCE_COUNT_BITS);

    /** Total amount of allocated native memory (in bytes). */
    private long allocatedMemory = 0;

    private final ReferenceQueue<Object> nativeObjectsQueue;
    private Map<Object, AllocInfo> allocatedNativeMemory;
    private final NativeReferenceStack nativeObjectWrapperList;
    private TraceMallocDomain[] traceMallocDomains;

    /** Container of pointers that have seen to be free'd. */
    private Map<Object, AllocInfo> freedNativeMemory;

    @CompilationFinal private RootCallTarget referenceCleanerCallTarget;
    @CompilationFinal private RootCallTarget triggerAsyncActionsCallTarget;

    /**
     * This cache is used to cache native wrappers for frequently used primitives. This is strictly
     * defined to be the range {@code [-5, 256]}. CPython does exactly the same (see
     * {@code PyLong_FromLong}; implemented in macro {@code CHECK_SMALL_INT}).
     */
    @CompilationFinal(dimensions = 1) private final PrimitiveNativeWrapper[] primitiveNativeWrapperCache;

    /** Just used for integrity checks if assertions are enabled. */
    @CompilationFinal private ReferenceLibrary referenceLibrary;

    /**
     * Required to emulate PyLongObject's ABI; number of bits per digit (equal to
     * {@code PYLONG_BITS_IN_DIGIT}.
     */
    @CompilationFinal private int pyLongBitsInDigit = -1;

    public CApiContext(PythonContext context, Object hpyLibrary) {
        super(context, hpyLibrary, CAPIConversionNodeSupplier.INSTANCE);
        nativeObjectsQueue = new ReferenceQueue<>();
        nativeObjectWrapperList = new NativeReferenceStack();

        // avoid 0 to be used as ID
        int nullID = nativeObjectWrapperList.reserve();
        assert nullID == 0;

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

            ArrayDeque<NativeObjectReference> refs = new ArrayDeque<>();
            do {
                if (reference instanceof NativeObjectReference) {
                    refs.add((NativeObjectReference) reference);
                }
                // consume all
                reference = nativeObjectsQueue.poll();
            } while (reference != null);

            if (!refs.isEmpty()) {
                return new CApiReferenceCleanerAction(refs);
            }

            return null;
        });
    }

    public int getPyLongBitsInDigit() {
        if (pyLongBitsInDigit < 0) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            pyLongBitsInDigit = (int) CExtNodes.PCallCapiFunction.getUncached().call(NativeCAPISymbols.FUN_GET_LONG_BITS_PER_DIGIT);
        }
        return pyLongBitsInDigit;
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
            referenceCleanerCallTarget = Truffle.getRuntime().createCallTarget(new CApiReferenceCleanerRootNode(getContext()));
        }
        return referenceCleanerCallTarget;
    }

    RootCallTarget getTriggerAsyncActionsCallTarget() {
        if (triggerAsyncActionsCallTarget == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            triggerAsyncActionsCallTarget = Truffle.getRuntime().createCallTarget(new TriggerAsyncActionsRootNode(getContext()));
        }
        return triggerAsyncActionsCallTarget;
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

    static class NativeObjectReference extends WeakReference<PythonAbstractNativeObject> {

        /**
         * The associated native pointer object that needs to be released if this reference dies.
         */
        final TruffleObject ptrObject;

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

        public TruffleObject getPtrObject() {
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
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, new String[]{"ptr", "managedRefCount"}, new String[0]);
        private static final TruffleLogger LOGGER = PythonLanguage.getLogger(CApiReferenceCleanerRootNode.class);

        @Child private SubRefCntNode refCntNode;
        @Child private CalleeContext calleeContext = CalleeContext.create();

        private final ConditionProfile customLocalsProfile = ConditionProfile.createBinaryProfile();
        private final CApiContext cApiContext;

        protected CApiReferenceCleanerRootNode(PythonContext context) {
            super(context.getLanguage());
            refCntNode = SubRefCntNodeGen.create();
            this.cApiContext = context.getCApiContext();
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object execute(VirtualFrame frame) {
            CalleeContext.enter(frame, customLocalsProfile);
            try {
                ArrayDeque<NativeObjectReference> nativeObjectReferences = (ArrayDeque<NativeObjectReference>) PArguments.getArgument(frame, 0);
                NativeObjectReference nativeObjectReference;
                while ((nativeObjectReference = pollFirst(nativeObjectReferences)) != null) {
                    if (!nativeObjectReference.resurrect) {
                        TruffleObject pointerObject = nativeObjectReference.getPtrObject();

                        cApiContext.nativeObjectWrapperList.remove(nativeObjectReference.id);
                        if (LOGGER.isLoggable(Level.FINE)) {
                            LOGGER.fine(() -> "Cleaning native object reference to " + CApiContext.asHex(pointerObject));
                        }
                        refCntNode.execute(pointerObject, nativeObjectReference.managedRefCount);
                    }
                }
            } finally {
                calleeContext.exit(frame, this);
            }
            return PNone.NONE;
        }

        @TruffleBoundary
        private static NativeObjectReference pollFirst(ArrayDeque<NativeObjectReference> deque) {
            return deque.pollFirst();
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }

        @Override
        public boolean isPythonInternal() {
            return true;
        }
    }

    /**
     * Reference cleaner action that will be executed by the {@link AsyncHandler}.
     */
    private static final class CApiReferenceCleanerAction implements AsyncHandler.AsyncAction {
        private final ArrayDeque<NativeObjectReference> nativeObjectReferences;

        public CApiReferenceCleanerAction(ArrayDeque<NativeObjectReference> nativeObjectReferences) {
            this.nativeObjectReferences = nativeObjectReferences;
        }

        @Override
        public void execute(VirtualFrame frame, Node location, PythonContext context) {
            Object[] pArguments = PArguments.create(1);
            PArguments.setArgument(pArguments, 0, nativeObjectReferences);
            GenericInvokeNode.getUncached().execute(frame, context.getCApiContext().getReferenceCleanerCallTarget(), pArguments);
        }
    }

    private static final class TriggerAsyncActionsRootNode extends PRootNode {
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, new String[0], new String[0]);

        @Child private CalleeContext calleeContext = CalleeContext.create();

        private final ConditionProfile customLocalsProfile = ConditionProfile.createBinaryProfile();
        private final PythonContext context;

        protected TriggerAsyncActionsRootNode(PythonContext context) {
            super(context.getLanguage());
            this.context = context;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            CalleeContext.enter(frame, customLocalsProfile);
            try {
                doGc();
                context.triggerAsyncActions(frame, this);
            } finally {
                calleeContext.exit(frame, this);
            }
            return 0;
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }

        @Override
        public boolean isPythonInternal() {
            return true;
        }
    }

    public NativeObjectReference lookupNativeObjectReference(int idx) {
        return nativeObjectWrapperList.get(idx);
    }

    public PythonAbstractNativeObject getPythonNativeObject(TruffleObject nativePtr, ConditionProfile newRefProfile, ConditionProfile validRefProfile, ConditionProfile resurrectProfile,
                    GetRefCntNode getObRefCntNode, AddRefCntNode addRefCntNode) {
        return getPythonNativeObject(nativePtr, newRefProfile, validRefProfile, resurrectProfile, getObRefCntNode, addRefCntNode, false);
    }

    public PythonAbstractNativeObject getPythonNativeObject(TruffleObject nativePtr, ConditionProfile newRefProfile, ConditionProfile validRefProfile, ConditionProfile resurrectProfile,
                    GetRefCntNode getObRefCntNode, AddRefCntNode addRefCntNode, boolean steal) {
        CompilerAsserts.partialEvaluationConstant(addRefCntNode);
        CompilerAsserts.partialEvaluationConstant(steal);

        int id = CApiContext.idFromRefCnt(getObRefCntNode.execute(nativePtr));

        NativeObjectReference ref;
        PythonAbstractNativeObject nativeObject;

        // If there is no mapping, we need to create a new one.
        if (newRefProfile.profile(id == 0)) {
            nativeObject = createPythonAbstractNativeObject(nativePtr, addRefCntNode, steal);
        } else if (validRefProfile.profile(id > 0)) {
            ref = lookupNativeObjectReference(id);
            nativeObject = ref.get();
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
        } else {
            LOGGER.warning(() -> String.format("cannot associate a native object reference to %s because reference count is corrupted", CApiContext.asHex(nativePtr)));
            nativeObject = new PythonAbstractNativeObject(nativePtr);
        }
        return nativeObject;
    }

    PythonAbstractNativeObject createPythonAbstractNativeObject(TruffleObject nativePtr, AddRefCntNode addRefCntNode, boolean steal) {
        PythonAbstractNativeObject nativeObject = new PythonAbstractNativeObject(nativePtr);
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
     * This method lazily initializes {@link #referenceLibrary} as a side-effect.
     */
    private boolean isReferenceToSameNativeObject(NativeObjectReference old, NativeObjectReference ref) {
        if (referenceLibrary == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            referenceLibrary = ReferenceLibrary.getFactory().getUncached(old.ptrObject);
        }
        return referenceLibrary.isSame(old.ptrObject, ref.ptrObject);
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
    public AllocInfo traceFree(Object ptr, @SuppressWarnings("unused") PFrame.Reference curFrame, @SuppressWarnings("unused") String clazzName) {
        if (allocatedNativeMemory == null) {
            allocatedNativeMemory = new HashMap<>();
        }
        if (freedNativeMemory == null) {
            freedNativeMemory = new HashMap<>();
        }
        AllocInfo allocatedValue = allocatedNativeMemory.remove(ptr);
        Object freedValue = freedNativeMemory.put(ptr, allocatedValue);
        if (freedValue != null) {
            LOGGER.severe(String.format("freeing memory that was already free'd %s (double-free)", asHex(ptr)));
        } else if (allocatedValue == null) {
            LOGGER.info(String.format("freeing non-allocated memory %s (maybe a double-free or we didn't trace the allocation)", asHex(ptr)));
        }
        return allocatedValue;
    }

    @TruffleBoundary
    public void traceAlloc(Object ptr, PFrame.Reference curFrame, String clazzName, long size) {
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
    public void trackObject(Object ptr, PFrame.Reference curFrame, String clazzName) {
        // TODO(fa): implement tracking of container objects for cycle detection
    }

    @SuppressWarnings("unused")
    public void untrackObject(Object ptr, PFrame.Reference curFrame, String clazzName) {
        // TODO(fa): implement untracking of container objects
    }

    /**
     * Use this method to register memory that is known to be allocated (i.e. static variables like
     * types). This is basically the same as
     * {@link #traceAlloc(Object, PFrame.Reference, String, long)} but does not consider it to be an
     * error if the memory is already allocated.
     */
    @TruffleBoundary
    public void traceStaticMemory(Object ptr, PFrame.Reference curFrame, String clazzName) {
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

    public void increaseMemoryPressure(GenericInvokeNode invokeNode, long size) {
        if (allocatedMemory <= getContext().getOption(PythonOptions.MaxNativeMemory)) {
            allocatedMemory += size;
            return;
        }

        // Triggering the GC and the async actions is hidden behind a call target to keep this
        // method slim.
        invokeNode.execute(getTriggerAsyncActionsCallTarget(), PArguments.create());

        if (allocatedMemory + size > getContext().getOption(PythonOptions.MaxNativeMemory)) {
            throw new OutOfMemoryError("native memory");
        }
        allocatedMemory += size;
    }

    public void increaseMemoryPressure(VirtualFrame frame, Node location, long size) {
        if (allocatedMemory <= getContext().getOption(PythonOptions.MaxNativeMemory)) {
            allocatedMemory += size;
            return;
        }

        doGc();
        getContext().triggerAsyncActions(frame, location);

        if (allocatedMemory + size > getContext().getOption(PythonOptions.MaxNativeMemory)) {
            throw new OutOfMemoryError("native memory");
        }
        allocatedMemory += size;
    }

    public void reduceMemoryPressure(long size) {
        allocatedMemory -= size;
    }

    @TruffleBoundary
    private static void doGc() {
        LOGGER.fine("full GC due to native memory");
        System.gc();
        try {
            Thread.sleep(50);
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
        public final String typeName;
        public final PFrame.Reference allocationSite;
        public final long size;

        public AllocInfo(String typeName, PFrame.Reference allocationSite, long size) {
            this.typeName = typeName;
            this.allocationSite = allocationSite;
            this.size = size;
        }

        public AllocInfo(PFrame.Reference allocationSite, String typeName) {
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

}
