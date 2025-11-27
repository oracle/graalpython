/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.capi.transitions;

import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PollingState.RQ_DISABLED_PERMANENT;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PollingState.RQ_DISABLED_TEMP;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PollingState.RQ_POLLING;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PollingState.RQ_READY;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PollingState.RQ_UNINITIALIZED;
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.ensurePointer;
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.readIntField;
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.writeDoubleField;
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.writeIntField;
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.writeLongField;
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.writePtrField;
import static com.oracle.graal.python.nfi2.NativeMemory.NULLPTR;
import static com.oracle.graal.python.nfi2.NativeMemory.calloc;
import static com.oracle.graal.python.nfi2.NativeMemory.free;
import static com.oracle.graal.python.nfi2.NativeMemory.mallocPtrArray;
import static com.oracle.graal.python.nfi2.NativeMemory.writePtrArrayElements;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.capsule.PyCapsule;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiGCSupport.GCListRemoveNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiGCSupport.PyObjectGCDelNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiGCSupport.PyObjectGCTrackNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.FromCharPointerNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol;
import com.oracle.graal.python.builtins.objects.cext.capi.PyMemoryViewWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.EnsurePythonObjectNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitionsFactory.AllocateNativeObjectStubNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitionsFactory.FirstToNativeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitionsFactory.NativePtrToPythonNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitionsFactory.NativeToPythonNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitionsFactory.NativeToPythonTransferNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitionsFactory.PythonToNativeNewRefNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitionsFactory.PythonToNativeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitionsFactory.NativeToPythonInternalNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitionsFactory.NativeToPythonReturnNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitionsFactory.PythonToNativeInternalNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitionsFactory.PythonToNativeNewRefRawNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitionsFactory.PythonToNativeRawNodeGen;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.CoerceNativePointerToLongNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtToJavaNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.common.HandleStack;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructs;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.DescriptorDeleteMarker;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.builtins.objects.type.TypeFlags;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetTypeFlagsNode;
import com.oracle.graal.python.nfi2.NativeMemory;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetClassNode.GetPythonObjectClassNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.runtime.sequence.storage.NativeSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage.StorageType;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.profiles.InlinedExactClassProfile;
import com.oracle.truffle.api.strings.TruffleString;

import sun.misc.Unsafe;

public abstract class CApiTransitions {

    // if != 0: GC every X C API calls
    private static final int GCALot = Integer.getInteger("python.GCALot", 0);
    // wait X calls before first GC
    private static final int GCALotWait = Integer.getInteger("python.GCALotWait", 0);
    private static long GCALotTotalCounter = 0;
    private static int GCALotCounter = 0;

    private static final TruffleLogger LOGGER = CApiContext.getLogger(CApiTransitions.class);

    enum PollingState {
        /** startup barrier not finished yet, polling must not run */
        RQ_UNINITIALIZED,

        /** normal steady state, polling allowed */
        RQ_READY,

        /** one thread is currently polling */
        RQ_POLLING,

        /** temporarily disabled by GraalPyPrivate_DisableReferneceQueuePolling */
        RQ_DISABLED_TEMP,

        /** shutdown/finalization, end state */
        RQ_DISABLED_PERMANENT
    }

    private CApiTransitions() {
    }

    // transfer: steal or borrow reference

    public static final class HandleContext {
        /**
         * Never use handle table index '0' to avoid that zeroed memory accidentally maps to some
         * valid object.
         */
        private static final int FIRST_VALID_INDEX = 1;
        private static final int DEFAULT_CAPACITY = 16;

        /** Threshold used to switch from exponential to linear growth. */
        private static final int LINEAR_THRESHOLD = 1024 * 1024 / Integer.BYTES;

        public HandleContext(boolean useShadowTable) {
            nativeStubLookupShadowTable = useShadowTable ? new HashMap<>() : null;
            nativeStubLookup = new Object[DEFAULT_CAPACITY];
            nativeStubLookupFreeStack = new HandleStack(DEFAULT_CAPACITY);
            nativeStubLookupFreeStack.pushRange(FIRST_VALID_INDEX, DEFAULT_CAPACITY);
        }

        public final ArrayList<Long> referencesToBeFreed = new ArrayList<>();
        public final HashMap<Long, IdReference<?>> nativeLookup = new HashMap<>();
        public final ConcurrentHashMap<Long, Long> nativeWeakRef = new ConcurrentHashMap<>();
        public final WeakHashMap<Object, WeakReference<Object>> managedNativeLookup = new WeakHashMap<>();

        private final HashMap<Long, Object> nativeStubLookupShadowTable;
        public Object[] nativeStubLookup;
        public final HandleStack nativeStubLookupFreeStack;

        public final Set<NativeStorageReference> nativeStorageReferences = new HashSet<>();
        public final Set<PyCapsuleReference> pyCapsuleReferences = new HashSet<>();

        public final ReferenceQueue<Object> referenceQueue = new ReferenceQueue<>();

        volatile PollingState referenceQueuePollingState = RQ_UNINITIALIZED;

        @TruffleBoundary
        public static <T> T putShadowTable(HashMap<Long, T> table, long pointer, T ref) {
            return table.put(pointer, ref);
        }

        @TruffleBoundary
        public static <T> T removeShadowTable(HashMap<Long, T> table, long pointer) {
            return table.remove(pointer);
        }

        @TruffleBoundary
        public static <T> T removeShadowTable(HashMap<Long, T> table, Object refOrWrapper) {
            if (refOrWrapper instanceof PythonObjectReference ref) {
                return table.remove(ref.pointer);
            } else if (refOrWrapper instanceof PythonObject pythonObject) {
                return table.remove(pythonObject.getNativePointer());
            }
            throw CompilerDirectives.shouldNotReachHere("Handle table must contain PythonObjectReference or PythonAbstractObjectNativeWrapper");
        }

        @TruffleBoundary
        public static <T> T getShadowTable(HashMap<Long, T> table, long pointer) {
            return table.get(pointer);
        }
    }

    private static HandleContext getContext() {
        return PythonContext.get(null).nativeContext;
    }

    public abstract static class IdReference<T> extends WeakReference<T> {

        public IdReference(HandleContext handleContext, T referent) {
            super(referent, handleContext.referenceQueue);
        }

    }

    /**
     * A weak and unique reference to a native wrapper of a reference counted managed object.
     */
    public static final class PythonObjectReference extends IdReference<PythonObject> {
        /**
         * This reference forces the wrapper to remain alive, and can be set to null when the
         * refcount falls to {@link PythonObject#MANAGED_REFCNT}.
         */
        private PythonObject strongReference;
        private final long pointer;

        /**
         * The index in the lookup table, where this reference is stored. This duplicates the native
         * field {@link CFields#GraalPyObject__handle_table_index} in order to save reading the
         * native field if we already have the reference object. The value may be {@code -1} in
         * which case it means that {@link #pointer} is not a tagged pointer but a pointer to some
         * other off-heap memory (e.g. {@code PyTypeObject} or other Python structs).
         */
        private final int handleTableIndex;

        /**
         * Indicates if the native memory {@link #pointer} was allocated from Java using
         * {@link com.oracle.graal.python.nfi2.NativeMemory#malloc(long)} and thus must be freed
         * using {@link com.oracle.graal.python.nfi2.NativeMemory#free(long)}. For any
         * {@link PythonObjectReference} the last collection is always on the Java side, since the
         * native side can only drop the ob_refcnt to {@link PythonObject#MANAGED_REFCNT} at which
         * point the {@link #strongReference} is removed and the Java GC will eventually get to
         * collect the object and enqueue this reference. At that point, we need to make a decision
         * how to free the native memory. For example, for managed classes we allocated a complete
         * type struct from Java ({@link FirstToNativeNode#doPythonManagedClass}), or for any
         * managed object that is represented with a stub we also allocate that stub from Java
         * ({@link AllocateNativeObjectStubNode}). These subsequently must be freed from Java as
         * well. If this field is false, however, it means the memory was allocated through C in
         * some way (presumably {@code PyObject_Malloc}, via the type's {@code tp_alloc}). So when
         * the reference is collected we dealloc using {@code GraalPyPrivate_BulkDealloc} which
         * calls the right API.
         */
        private final boolean allocatedFromJava;
        private final boolean gc;

        private PythonObjectReference(HandleContext handleContext, PythonObject referent, boolean strong, long pointer, int handleTableIndex, boolean allocatedFromJava, boolean gc) {
            super(handleContext, referent);
            this.pointer = pointer;
            this.strongReference = strong ? referent : null;
            referent.ref = this;
            this.handleTableIndex = handleTableIndex;
            this.allocatedFromJava = allocatedFromJava;
            this.gc = gc;
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine(PythonUtils.formatJString("new %s", this));
            }
        }

        static PythonObjectReference createStub(HandleContext handleContext, PythonObject referent, boolean strong, long pointer, int idx, boolean gc) {
            assert HandlePointerConverter.pointsToPyHandleSpace(pointer);
            assert idx >= 0;
            return new PythonObjectReference(handleContext, referent, strong, pointer, idx, true, gc);
        }

        static PythonObjectReference createReplacement(HandleContext handleContext, PythonObject referent, long pointer, boolean allocatedFromJava) {
            assert !HandlePointerConverter.pointsToPyHandleSpace(pointer);
            return new PythonObjectReference(handleContext, referent, true, pointer, -1, allocatedFromJava, false);
        }

        public boolean isStrongReference() {
            return strongReference != null;
        }

        public void setStrongReference(PythonObject object) {
            strongReference = object;
        }

        public int getHandleTableIndex() {
            return handleTableIndex;
        }

        public boolean isAllocatedFromJava() {
            return allocatedFromJava;
        }

        @Override
        @TruffleBoundary
        public String toString() {
            String type = strongReference != null ? "strong" : "weak";
            PythonObject referent = get();
            return String.format("PythonObjectReference<0x%x,%s,%s,id=%d>", pointer, type, referent != null ? referent : "freed", handleTableIndex);
        }
    }

    /**
     * A weak and unique reference to a native object (not directly the pointer but the
     * {@link PythonAbstractNativeObject} wrapper).
     */
    public static final class NativeObjectReference extends IdReference<PythonAbstractNativeObject> {

        final long pointer;

        public NativeObjectReference(HandleContext handleContext, PythonAbstractNativeObject referent, long pointer) {
            super(handleContext, referent);
            this.pointer = pointer;
            referent.ref = this;
            assert (pointer & 7) == 0;
            if (LOGGER.isLoggable(Level.FINE)) {
                logNew();
            }
        }

        /*
         * Only use from a thread with attached context!
         */
        @TruffleBoundary
        private void logNew() {
            PythonAbstractNativeObject referent = get();
            LOGGER.fine(String.format("NativeObjectReference<0x%x,%s>", pointer, referent != null ? referent.toStringWithContext() : "freed"));
        }

        @Override
        @TruffleBoundary
        public String toString() {
            PythonAbstractNativeObject referent = get();
            return String.format("NativeObjectReference<0x%x,%s>", pointer, referent != null ? referent : "freed");
        }
    }

    public static final class NativeStorageReference extends IdReference<NativeSequenceStorage> {
        private final SequenceStorage.StorageType type;
        private long ptr;
        private int size;

        public NativeStorageReference(HandleContext handleContext, NativeSequenceStorage storage) {
            super(handleContext, storage);
            type = storage.getElementType();
            ptr = storage.getPtr();
            size = storage.length();
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine(PythonUtils.formatJString("new %s", toString()));
            }
        }

        public Object getPtr() {
            return ptr;
        }

        public void setPtr(long ptr) {
            this.ptr = ptr;
        }

        public int getSize() {
            return size;
        }

        public void setSize(int size) {
            this.size = size;
        }

        @Override
        @TruffleBoundary
        public String toString() {
            Object ptrStr;
            try {
                ptrStr = Long.toHexString(InteropLibrary.getUncached().asPointer(ptr));
            } catch (UnsupportedMessageException e) {
                ptrStr = ptr;
            }
            return String.format("NativeStorageReference<0x%s, %d>", ptrStr, size);
        }
    }

    @TruffleBoundary
    public static void registerNativeSequenceStorage(NativeSequenceStorage storage) {
        assert PythonContext.get(null).ownsGil();
        HandleContext handleContext = getContext();
        NativeStorageReference ref = new NativeStorageReference(handleContext, storage);
        storage.setReference(ref);
        handleContext.nativeStorageReferences.add(ref);
    }

    public static final class PyCapsuleReference extends IdReference<PyCapsule> {
        private final PyCapsule.CapsuleData data;

        public PyCapsuleReference(HandleContext handleContext, PyCapsule capsule) {
            super(handleContext, capsule);
            data = capsule.getData();
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine(PythonUtils.formatJString("new %s", toString()));
            }
        }

        @Override
        @TruffleBoundary
        public String toString() {
            return String.format("PyCapsuleReference<%s>", data);
        }
    }

    @TruffleBoundary
    public static PyCapsuleReference registerPyCapsuleDestructor(PyCapsule capsule) {
        assert PythonContext.get(null).ownsGil();
        HandleContext handleContext = getContext();
        PyCapsuleReference ref = new PyCapsuleReference(handleContext, capsule);
        handleContext.pyCapsuleReferences.add(ref);
        return ref;
    }

    @TruffleBoundary
    @SuppressWarnings("try")
    public static int pollReferenceQueue() {
        PythonContext context = PythonContext.get(null);
        HandleContext handleContext = context.nativeContext;
        int manuallyCollected = 0;
        if (handleContext.referenceQueuePollingState != RQ_READY) {
            return manuallyCollected;
        }
        /*
         * Polling the reference queue may deallocate native GC objects and therefore re-enter
         * native code paths that use '_PyThreadState_GET()' to obtain the current thread's GC
         * state. So, we may only poll once the current thread has installed its native
         * 'tstate_current' pointer.
         */
        if (!context.getThreadState(context.getLanguage()).isNativeThreadStateInitialized()) {
            return manuallyCollected;
        }
        try (GilNode.UncachedAcquire ignored = GilNode.uncachedAcquire()) {
            if (handleContext.referenceQueuePollingState != RQ_READY) {
                return manuallyCollected;
            }
            if (!context.getThreadState(context.getLanguage()).isNativeThreadStateInitialized()) {
                return manuallyCollected;
            }
            ReferenceQueue<Object> queue = handleContext.referenceQueue;
            int count = 0;
            long start = 0;
            boolean polling = false;
            ArrayList<Long> referencesToBeFreed = handleContext.referencesToBeFreed;
            try {
                while (true) {
                    Object entry = queue.poll();
                    if (entry == null) {
                        if (count > 0) {
                            assert handleContext.referenceQueuePollingState == RQ_POLLING || handleContext.referenceQueuePollingState == RQ_DISABLED_PERMANENT;
                            releaseNativeObjects(context, referencesToBeFreed);
                            LOGGER.fine("collected " + count + " references from native reference queue in " + ((System.nanoTime() - start) / 1000000) + "ms");
                        }
                        return manuallyCollected;
                    }
                    if (count == 0) {
                        assert handleContext.referenceQueuePollingState == RQ_READY;
                        handleContext.referenceQueuePollingState = RQ_POLLING;
                        polling = true;
                        start = System.nanoTime();
                    } else {
                        assert handleContext.referenceQueuePollingState == RQ_POLLING;
                    }
                    count++;
                    LOGGER.fine(() -> PythonUtils.formatJString("releasing %s, no remaining managed references", entry));
                    if (entry instanceof PythonObjectReference reference) {
                        if (HandlePointerConverter.pointsToPyHandleSpace(reference.pointer)) {
                            assert !HandlePointerConverter.pointsToPyIntHandle(reference.pointer);
                            assert !HandlePointerConverter.pointsToPyFloatHandle(reference.pointer);
                            assert nativeStubLookupGet(handleContext, reference.pointer, reference.handleTableIndex) != null : Long.toHexString(reference.pointer);
                            LOGGER.finer(() -> PythonUtils.formatJString("releasing native stub lookup for managed object %x => %s", reference.pointer, reference));
                            nativeStubLookupRemove(handleContext, reference);
                            /*
                             * We may only free native object stubs if their reference count is
                             * zero. We cannot free other structs (e.g. PyDateTime_CAPI) because we
                             * don't know if they are still used from native code. Those must be
                             * free'd at context finalization.
                             */
                            long stubPointer = HandlePointerConverter.pointerToStub(reference.pointer);
                            long newRefCount = subNativeRefCount(stubPointer, MANAGED_REFCNT);
                            if (newRefCount == 0) {
                                LOGGER.finer(() -> PythonUtils.formatJString("No more references for %s (refcount->0): freeing native stub", reference));
                                freeNativeStub(reference);
                            } else {
                                LOGGER.finer(() -> PythonUtils.formatJString("Some native references to %s remain (refcount=%d): not freeing native stub yet", reference, newRefCount));
                                /*
                                 * In this case, the object is no longer referenced from managed but
                                 * still from native code (since the reference count is greater 0).
                                 * This case is possible if there are reference cycles that include
                                 * managed objects. We overwrite field 'CFields.GraalPyObject__id'
                                 * to avoid incorrect reuse of the ID which could resolve to another
                                 * object.
                                 */
                                writeIntField(stubPointer, CFields.GraalPyObject__handle_table_index, 0);
                                // this can only happen if the object is a GC object
                                assert reference.gc;
                                /*
                                 * Since the managed object is already dead (only the native object
                                 * stub is still alive), we need to remove the object from its
                                 * current GC list. Otherwise, the Python GC would try to traverse
                                 * the object on the next collection which would lead to a crash.
                                 */
                                GCListRemoveNode.executeUncached(stubPointer);
                            }
                        } else {
                            assert nativeLookupGet(handleContext, reference.pointer) != null : Long.toHexString(reference.pointer);
                            LOGGER.finer(() -> PythonUtils.formatJString("releasing native stub lookup for managed object with replacement %x => %s", reference.pointer, reference));
                            if (nativeLookupRemove(handleContext, reference.pointer) != null) {
                                // The reference was still in our lookup table, it was not otherwise
                                // freed and we can process it now.
                                if (reference.isAllocatedFromJava()) {
                                    LOGGER.finer(() -> PythonUtils.formatJString("freeing managed object %s replacement", reference));
                                    freeNativeStruct(reference);
                                } else {
                                    referencesToBeFreed.add(reference.pointer);
                                }
                            } else {
                                // This handle was removed from the native lookup table before,
                                // probably during an explicit collection. This can happen during
                                // shutdown when tp_dealloc is called for some objects and that
                                // causes upcalls and reference queue polling on references that
                                // were already removed and had their memory freed
                            }
                        }
                    } else if (entry instanceof NativeObjectReference reference) {
                        if (nativeLookupRemove(handleContext, reference.pointer) != null) {
                            // The reference was still in our lookup table, it was not otherwise
                            // freed and we can process it now
                            LOGGER.finer(() -> PythonUtils.formatJString("releasing native lookup for native object %x => %s", reference.pointer, reference));
                            processNativeObjectReference(reference, referencesToBeFreed);
                        }
                    } else if (entry instanceof NativeStorageReference reference) {
                        handleContext.nativeStorageReferences.remove(reference);
                        processNativeStorageReference(reference);
                    } else if (entry instanceof PyCapsuleReference reference) {
                        handleContext.pyCapsuleReferences.remove(reference);
                        processPyCapsuleReference(reference);
                    }
                }
            } finally {
                if (polling && handleContext.referenceQueuePollingState == RQ_POLLING) {
                    handleContext.referenceQueuePollingState = RQ_READY;
                }
            }
        }
    }

    /**
     * Subtracts {@link PythonObject#MANAGED_REFCNT} from the object's reference count and if it is
     * then {@code 0}, it puts the pointer into the list of references to be freed. Therefore, this
     * method neither frees any native memory nor runs any object destructor (guest code).
     */
    private static void processNativeObjectReference(NativeObjectReference reference, ArrayList<Long> referencesToBeFreed) {
        LOGGER.fine(() -> PythonUtils.formatJString("releasing %s", reference.toString()));
        if (subNativeRefCount(reference.pointer, MANAGED_REFCNT) == 0) {
            referencesToBeFreed.add(reference.pointer);
        }
    }

    /**
     * Calls function {@link NativeCAPISymbol#FUN_OBJECT_ARRAY_RELEASE} to decrement the reference
     * counts of the stored objects by one and frees the native array. Therefore, this operation may
     * run guest code because if the stored objects where exclusively owned by this storage, then
     * they will be freed by calling the element object's destructor.
     */
    private static void processNativeStorageReference(NativeStorageReference reference) {
        /*
         * Note: 'reference.size' may be zero if the storage has already been cleared by the Python
         * GC.
         */
        if (reference.type == StorageType.Generic && reference.size > 0) {
            PCallCapiFunction.callUncached(NativeCAPISymbol.FUN_OBJECT_ARRAY_RELEASE, reference.ptr, reference.size);
        }
        assert !InteropLibrary.getUncached().isNull(reference.ptr);
        freeNativeStorage(reference);
    }

    private static void processPyCapsuleReference(PyCapsuleReference reference) {
        LOGGER.fine(() -> PythonUtils.formatJString("releasing %s", reference.toString()));
        if (reference.data.getDestructor() != NULLPTR) {
            // Our capsule is dead, so create a temporary copy that doesn't have a reference anymore
            PyCapsule capsule = PFactory.createCapsule(PythonLanguage.get(null), reference.data);
            PCallCapiFunction.callUncached(NativeCAPISymbol.FUN_GRAALPY_CAPSULE_CALL_DESTRUCTOR, PythonToNativeNode.executeUncached(capsule), capsule.getDestructor());
        }
    }

    /**
     * Deallocates all objects in the given collection by calling {@code _Py_Dealloc} for each
     * element. This method may therefore run arbitrary guest code and strictly requires the GIL to
     * be held at the time of invocation.
     */
    private static void releaseNativeObjects(PythonContext context, ArrayList<Long> referencesToBeFreed) {
        if (!referencesToBeFreed.isEmpty()) {
            /*
             * This needs the GIL because this will call the native objects' destructors which can
             * be arbitrary guest code.
             */
            assert context.ownsGil();
            PythonContext.PythonThreadState threadState = context.getThreadState(context.getLanguage());
            /*
             * There can be an active exception. Since we might be calling arbitary python, we need
             * to stash it.
             */
            Object savedException = CExtCommonNodes.ReadAndClearNativeException.executeUncached(threadState);
            try {
                int size = referencesToBeFreed.size();
                LOGGER.fine(() -> PythonUtils.formatJString("releasing %d NativeObjectReference instances", size));
                long pointer = mallocPtrArray(size);
                for (int i = 0; i < size; i++) {
                    NativeMemory.writeLongArrayElement(pointer, i, referencesToBeFreed.get(i));
                }
                PCallCapiFunction.callUncached(NativeCAPISymbol.FUN_BULK_DEALLOC, pointer, size);
                free(pointer);
                referencesToBeFreed.clear();
            } finally {
                CExtCommonNodes.ReadAndClearNativeException.executeUncached(threadState);
                if (savedException != PNone.NO_VALUE) {
                    CExtCommonNodes.TransformExceptionToNativeNode.executeUncached(savedException);
                }
            }
        }
    }

    /**
     * Releases a native wrapper. This requires to remove the native wrapper from any lookup tables
     * and to free potentially allocated native resources. If native wrappers receive
     * {@code toNative}, either a <it>handle pointer</it> is allocated or some off-heap memory is
     * allocated. This method takes care of that and will also free any off-heap memory.
     */
    @TruffleBoundary
    public static void releaseNativeWrapper(long nativePointer) {

        // If wrapper already received toNative, release the handle or free the native memory.
        if (nativePointer != PythonObject.UNINITIALIZED) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine(PythonUtils.formatJString("Freeing native replacement/stub object with pointer: 0x%x", nativePointer));
            }
            PythonContext pythonContext = PythonContext.get(null);
            if (HandlePointerConverter.pointsToPyHandleSpace(nativePointer)) {
                if (HandlePointerConverter.pointsToPyIntHandle(nativePointer) || HandlePointerConverter.pointsToPyFloatHandle(nativePointer)) {
                    return;
                }
                // In this case, we are up to free a native object stub.
                assert tableEntryRemoved(pythonContext.nativeContext, nativePointer);
                nativePointer = HandlePointerConverter.pointerToStub(nativePointer);
            } else {
                nativeLookupRemove(pythonContext.nativeContext, nativePointer);
            }
            free(nativePointer);
        }
    }

    private static boolean tableEntryRemoved(HandleContext context, long pointer) {
        assert HandlePointerConverter.pointsToPyHandleSpace(pointer);
        int id = readIntField(HandlePointerConverter.pointerToStub(pointer), CFields.GraalPyObject__handle_table_index);
        return id <= 0 || nativeStubLookupGet(context, pointer, id) == null;
    }

    public static void deallocNativeReplacements(PythonContext context, HandleContext handleContext) {
        assert context.ownsGil();
        ArrayList<Long> referencesToBeFreed = new ArrayList<>();
        Iterator<Entry<Long, IdReference<?>>> iterator = handleContext.nativeLookup.entrySet().iterator();
        while (iterator.hasNext()) {
            IdReference<?> ref = iterator.next().getValue();
            if (ref instanceof PythonObjectReference reference) {
                if (!reference.isAllocatedFromJava()) {
                    // This memory must be freed from C
                    if (subNativeRefCount(reference.pointer, MANAGED_REFCNT) == 0) {
                        // Only the managed references exist, we can dealloc
                        referencesToBeFreed.add(reference.pointer);
                        iterator.remove();
                    } else {
                        // There is a native reference which presumably will decref this one at a
                        // later point and call tp_dealloc then
                    }
                }
            }
        }
        releaseNativeObjects(context, referencesToBeFreed);
        pollReferenceQueue();
    }

    public static void freeNativeReplacementStructs(PythonContext context, HandleContext handleContext) {
        assert context.ownsGil();
        handleContext.nativeLookup.forEach((l, ref) -> {
            if (ref instanceof PythonObjectReference reference) {
                // We don't expect references to wrappers that would have a native object stub.
                assert reference.handleTableIndex == -1;
                // We expect at this point that most if not all references left were allocated
                // from Java and can be freed here. There may be stragglers that are waiting
                // for a GC though, so we have to check.
                if (reference.isAllocatedFromJava()) {
                    freeNativeStruct(reference);
                }
            }
        });
        handleContext.nativeLookup.clear();
    }

    public static boolean disableReferenceQueuePolling(HandleContext handleContext) {
        if (handleContext.referenceQueuePollingState == RQ_READY) {
            handleContext.referenceQueuePollingState = RQ_DISABLED_TEMP;
            return false;
        }
        return true;
    }

    public static void enableReferenceQueuePolling(HandleContext handleContext) {
        if (handleContext.referenceQueuePollingState == RQ_DISABLED_TEMP) {
            handleContext.referenceQueuePollingState = RQ_READY;
        }
    }

    public static void initializeReferenceQueuePolling(HandleContext handleContext) {
        assert handleContext.referenceQueuePollingState == RQ_UNINITIALIZED : handleContext.referenceQueuePollingState;
        handleContext.referenceQueuePollingState = RQ_READY;
    }

    public static void disableReferenceQueuePollingPermanently(HandleContext handleContext) {
        handleContext.referenceQueuePollingState = RQ_DISABLED_PERMANENT;
    }

    private static void freeNativeStub(PythonObjectReference ref) {
        freeNativeStub(ref.pointer, ref.gc);
    }

    private static void freeNativeStub(long pointer, boolean gc) {
        assert HandlePointerConverter.pointsToPyHandleSpace(pointer);
        assert !HandlePointerConverter.pointsToPyIntHandle(pointer);
        assert !HandlePointerConverter.pointsToPyFloatHandle(pointer);
        if (gc) {
            PyObjectGCDelNode.executeUncached(pointer);
        } else {
            long rawPointer = HandlePointerConverter.pointerToStub(pointer);
            LOGGER.fine(() -> PythonUtils.formatJString("releasing native object stub 0x%x", rawPointer));
            free(rawPointer);
        }
    }

    private static void freeNativeStruct(PythonObjectReference ref) {
        assert ref.handleTableIndex == -1;
        assert ref.isAllocatedFromJava();
        assert !ref.gc;
        LOGGER.fine(() -> PythonUtils.formatJString("releasing %s", ref.toString()));
        free(ref.pointer);
    }

    private static void freeNativeStorage(NativeStorageReference ref) {
        LOGGER.fine(() -> PythonUtils.formatJString("releasing %s", ref.toString()));
        free(ref.ptr);
    }

    /**
     * Deallocates any native object stub that is still reachable via the
     * {@link HandleContext#nativeStubLookup lookup table}. This method modifies the
     * {@link HandleContext#nativeStubLookup stub lookup table} but runs no guest code.
     */
    public static void freeNativeObjectStubs(HandleContext handleContext) {
        // TODO(fa): this should not require the GIL (GR-51314)
        assert PythonContext.get(null).ownsGil();
        assert PythonContext.get(null).isFinalizing();
        for (int i = HandleContext.FIRST_VALID_INDEX; i < handleContext.nativeStubLookup.length; i++) {
            Object ref = handleContext.nativeStubLookup[i];
            // not all slots of the handle table are currently used
            if (ref == null) {
                continue;
            }

            assert ref instanceof PythonObject || ref instanceof PythonObjectReference || CApiContext.isSpecialSingleton(ref);

            nativeStubLookupRemove(handleContext, i);
            if (ref instanceof PythonObjectReference pythonObjectReference) {
                freeNativeStub(pythonObjectReference);
            } else if (ref instanceof PythonObject pythonObject) {
                long pointer = pythonObject.getNativePointer();
                pythonObject.clearNativePointer();
                Object type = GetClassNode.executeUncached(pythonObject);
                boolean isGc = (GetTypeFlagsNode.executeUncached(type) & TypeFlags.HAVE_GC) != 0;
                // all pointers in 'nativeStubLookup' need to be tagged pointers
                assert HandlePointerConverter.pointsToPyHandleSpace(pointer);
                freeNativeStub(pointer, isGc);
            }
            // The remaining type of objects are special singletons which are free'd separately.
        }
    }

    /**
     * Frees any native storage registered in {@link HandleContext#nativeStorageReferences}. This
     * doesn't decref the contained objects because that could run arbitrary guest code which is not
     * allowed here.
     */
    public static void freeNativeStorages(HandleContext handleContext) {
        // TODO(fa): this should not require the GIL (GR-51314)
        assert PythonContext.get(null).ownsGil();
        assert PythonContext.get(null).isFinalizing();
        handleContext.nativeStorageReferences.forEach(CApiTransitions::freeNativeStorage);
        handleContext.nativeStorageReferences.clear();
    }

    /**
     * We need to call __dealloc__ for native weakref objects before exit, as some objects might
     * need to use capi functions.
     */
    @TruffleBoundary
    public static void addNativeWeakRef(PythonContext pythonContext, PythonAbstractNativeObject object) {
        pythonContext.nativeContext.nativeWeakRef.put(getNativePointer(object), 0L);
    }

    /**
     * In case a weakref object is being collected. We must remove it from the list to avoid double
     * deallocation at exit.
     */
    @TruffleBoundary
    public static void removeNativeWeakRef(PythonContext pythonContext, long pointer) {
        pythonContext.nativeContext.nativeWeakRef.remove(pointer);
    }

    public static long getNativePointer(Object obj) {
        if (obj instanceof PythonAbstractNativeObject object) {
            return object.ref.pointer;
        } else {
            return 0;
        }
    }

    /**
     * Deallocates any native weak reference that is still reachable via
     * {@link HandleContext#nativeWeakRef}. This method then clears the table and will call
     * {@link NativeCAPISymbol#FUN_SHUTDOWN_BULK_DEALLOC} which may run arbitrary guest code.
     */
    public static void deallocateNativeWeakRefs(PythonContext pythonContext) {
        CompilerAsserts.neverPartOfCompilation();
        assert pythonContext.ownsGil();
        HandleContext context = pythonContext.nativeContext;
        int idx = -1;
        Object[] list = context.nativeWeakRef.values().toArray();
        context.nativeWeakRef.clear();
        long[] ptrArray = new long[list.length];
        for (Object ptr : list) {
            if (context.nativeLookup.containsKey(ptr)) {
                ptrArray[++idx] = (Long) ptr;
            }
        }
        if (idx != -1) {
            int len = idx + 1;
            long array = mallocPtrArray(len);
            try {
                writePtrArrayElements(array, 0, ptrArray, 0, len);
                CExtNodes.PCallCapiFunction.callUncached(NativeCAPISymbol.FUN_SHUTDOWN_BULK_DEALLOC, array, len);
            } finally {
                free(array);
                context.nativeWeakRef.clear();
            }
        }
        // we are holding the GIL; no one can create weakrefs concurrently
        assert context.nativeWeakRef.isEmpty();
    }

    public static void maybeGCALot() {
        if (GCALot != 0) {
            maybeGC();
        }
    }

    @TruffleBoundary
    private static void maybeGC() {
        GCALotTotalCounter++;
        if (GCALotTotalCounter < GCALotWait) {
            // skip
        } else if (++GCALotCounter >= GCALot) {
            LOGGER.info("GC A Lot - calling System.gc (opportunities=" + GCALotTotalCounter + ")");
            GCALotCounter = 0;
            PythonUtils.forceFullGC();
            pollReferenceQueue();
        }
    }

    @TruffleBoundary
    public static Object lookupNative(long pointer) {
        log(pointer);
        IdReference<?> reference = nativeLookupGet(getContext(), pointer);
        if (reference != null) {
            return logResultBoundary(reference.get());
        }
        return logResult(null);
    }

    @TruffleBoundary
    public static IdReference<?> nativeLookupGet(HandleContext context, long pointer) {
        return context.nativeLookup.get(pointer);
    }

    @TruffleBoundary
    public static IdReference<?> nativeLookupPut(HandleContext context, long pointer, NativeObjectReference value) {
        return context.nativeLookup.put(pointer, value);
    }

    @TruffleBoundary
    public static IdReference<?> nativeLookupPut(HandleContext context, long pointer, PythonObjectReference value) {
        return context.nativeLookup.put(pointer, value);
    }

    @TruffleBoundary
    public static IdReference<?> nativeLookupRemove(HandleContext context, long pointer) {
        return context.nativeLookup.remove(pointer);
    }

    public static Object nativeStubLookupGet(HandleContext context, long pointer, int idx) {
        if (idx <= 0) {
            if (PythonContext.DEBUG_CAPI && HandleContext.getShadowTable(context.nativeStubLookupShadowTable, pointer) != null) {
                throw CompilerDirectives.shouldNotReachHere();
            }
            return null;
        }
        Object result = context.nativeStubLookup[idx];
        if (PythonContext.DEBUG_CAPI && HandleContext.getShadowTable(context.nativeStubLookupShadowTable, pointer) != result) {
            throw CompilerDirectives.shouldNotReachHere();
        }
        return result;
    }

    /**
     * Reserves a free slot in the handle table that can later be used to store a
     * {@link PythonObjectReference} using {@link #nativeStubLookupPut}. If the handle table is
     * currently too small, it will be enlarged.
     *
     * @throws OverflowException Indicates that we cannot resize the handle table anymore. This
     *             essentially indicates a Python-level MemoryError.
     */
    private static int nativeStubLookupReserve(HandleContext context) throws OverflowException {
        int idx = context.nativeStubLookupFreeStack.pop();
        if (idx == -1) {
            idx = resizeNativeStubLookupTable(context);
        }
        assert context.nativeStubLookup[idx] == null;
        return idx;
    }

    @TruffleBoundary
    private static int resizeNativeStubLookupTable(HandleContext context) throws OverflowException {
        int oldSize = context.nativeStubLookup.length;
        // exponentially grow until 1 MB; then linearly grow by 1 MB
        int newSize = oldSize >= HandleContext.LINEAR_THRESHOLD ? PythonUtils.addExact(oldSize, HandleContext.LINEAR_THRESHOLD) : PythonUtils.multiplyExact(oldSize, 2);
        assert newSize != oldSize;
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine(String.format("Resizing native stub lookup table: %d -> %d", oldSize, newSize));
        }
        context.nativeStubLookup = Arrays.copyOf(context.nativeStubLookup, newSize);
        context.nativeStubLookupFreeStack.pushRange(oldSize, newSize);
        return context.nativeStubLookupFreeStack.pop();
    }

    private static int nativeStubLookupPut(HandleContext context, int idx, Object value, long pointer) {
        assert idx > 0;
        assert HandlePointerConverter.pointsToPyHandleSpace(pointer);
        assert value instanceof PythonObject || value instanceof PythonObjectReference || CApiContext.isSpecialSingleton(value);
        assert context.nativeStubLookup[idx] == null || context.nativeStubLookup[idx] == value;
        context.nativeStubLookup[idx] = value;
        if (PythonContext.DEBUG_CAPI) {
            Object prev = HandleContext.putShadowTable(context.nativeStubLookupShadowTable, pointer, value);
            if (prev != null && prev != value) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }
        return idx;
    }

    private static int nativeStubLookupReplaceByWeak(HandleContext context, int idx, PythonObjectReference value, long pointer) {
        assert idx > 0;
        assert idx == value.handleTableIndex;
        assert HandlePointerConverter.pointsToPyHandleSpace(pointer);
        assert context.nativeStubLookup[idx] == value.get();
        context.nativeStubLookup[idx] = value;
        if (PythonContext.DEBUG_CAPI) {
            Object prev = HandleContext.putShadowTable(context.nativeStubLookupShadowTable, pointer, value);
            if (prev != value.get()) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }
        return idx;
    }

    public static void nativeStubLookupRemove(HandleContext context, PythonObjectReference ref) {
        assert HandlePointerConverter.pointsToPyHandleSpace(ref.pointer);
        nativeStubLookupRemove(context, ref.handleTableIndex);
    }

    public static Object nativeStubLookupRemove(HandleContext context, int idx) {
        assert idx >= HandleContext.FIRST_VALID_INDEX;
        Object result = context.nativeStubLookup[idx];
        assert result instanceof PythonObjectReference || result instanceof PythonObject || CApiContext.isSpecialSingleton(result);
        context.nativeStubLookup[idx] = null;
        context.nativeStubLookupFreeStack.push(idx);
        if (PythonContext.DEBUG_CAPI && HandleContext.removeShadowTable(context.nativeStubLookupShadowTable, result) != result) {
            throw CompilerDirectives.shouldNotReachHere();
        }
        return result;
    }

    public static final class HandlePointerConverter {

        // Aligned with handles.h in our C API. Update comment there if you change or move these.
        private static final long HANDLE_TAG_BIT = 1L << 63;
        private static final long INTEGER_TAG_BIT = 1L << 62;
        private static final long FLOAT_TAG_BIT = 1L << 61;

        /**
         * Some libraries (notably cffi) do pointer tagging and therefore assume aligned pointers
         * (aligned to 8 bytes). This means, the three LSBs need to be 0.
         */
        private static final int POINTER_ALIGNMENT_SHIFT = 3;
        private static final long POINTER_ALIGNMENT_MASK = (1L << POINTER_ALIGNMENT_SHIFT) - 1L;
        private static final long _35BIT_MASK = 0xFFFFFFFFL << 3;

        public static boolean pointsToPyHandleSpace(long pointer) {
            return (pointer & HANDLE_TAG_BIT) != 0;
        }

        public static boolean pointsToPyIntHandle(long pointer) {
            return (pointer & INTEGER_TAG_BIT) != 0;
        }

        public static boolean pointsToPyFloatHandle(long pointer) {
            return (pointer & FLOAT_TAG_BIT) != 0;
        }

        public static long stubToPointer(long stubPointer) {
            assert (stubPointer & POINTER_ALIGNMENT_MASK) == 0;
            return stubPointer | HANDLE_TAG_BIT;
        }

        public static long intToPointer(int value) {
            return ((long) value << 3) & _35BIT_MASK | HANDLE_TAG_BIT | INTEGER_TAG_BIT;
        }

        public static long floatToPointer(float value) {
            long rawFloatBits = Float.floatToRawIntBits(value);
            return (rawFloatBits << 3) & _35BIT_MASK | HANDLE_TAG_BIT | FLOAT_TAG_BIT;
        }

        public static long pointerToStub(long pointer) {
            assert (pointer & ~HANDLE_TAG_BIT & POINTER_ALIGNMENT_MASK) == 0;
            return pointer & ~HANDLE_TAG_BIT;
        }

        public static long pointerToLong(long pointer) {
            assert Integer.toHexString((int) ((pointer & ~(HANDLE_TAG_BIT | INTEGER_TAG_BIT)) >> 3)).equals(Long.toHexString(((pointer & ~(HANDLE_TAG_BIT | INTEGER_TAG_BIT)) >> 3)));
            return (int) (pointer >> 3);
        }

        public static double pointerToDouble(long pointer) {
            assert Integer.toHexString((int) ((pointer & ~(HANDLE_TAG_BIT | FLOAT_TAG_BIT)) >> 3)).equals(Long.toHexString(((pointer & ~(HANDLE_TAG_BIT | FLOAT_TAG_BIT)) >> 3)));
            return Float.intBitsToFloat((int) (pointer >> 3));
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    @ImportStatic({CApiContext.class, PGuards.class})
    public abstract static class FirstToNativeNode extends Node {

        public static long executeUncached(PythonAbstractObject object, long initialRefCount) {
            return FirstToNativeNodeGen.getUncached().execute(null, object, initialRefCount);
        }

        public abstract long execute(Node inliningTarget, PythonAbstractObject object, long initialRefCount);

        @Specialization
        @TruffleBoundary
        static long doPythonManagedClass(Node inliningTarget, PythonManagedClass clazz, long initialRefCount) {
            assert !clazz.isNative();
            /*
             * Note: it's important that we first allocate the empty 'PyTypeStruct' and register it
             * to the wrapper before we do the type's initialization. Otherwise, we will run into an
             * infinite recursion because, e.g., some type uses 'None', so the 'NoneType' will be
             * transformed to native but 'NoneType' may have some field that is initialized with
             * 'None' and so on.
             *
             * If we first set the empty struct and initialize it afterward, everything is fine.
             */
            boolean heaptype = (GetTypeFlagsNode.executeUncached(clazz) & TypeFlags.HEAPTYPE) != 0;
            long size = CStructs.PyTypeObject.size();
            if (heaptype) {
                size = CStructs.PyHeapTypeObject.size();
                if (GetClassNode.executeUncached(clazz) instanceof PythonAbstractNativeObject nativeMetatype) {
                    // TODO should call the metatype's tp_alloc
                    size = TypeNodes.GetBasicSizeNode.executeUncached(nativeMetatype);
                }
            }
            /*
             * For built-in classes, we can always create a strong reference. Those classes are
             * always reachable and a weak reference is not necessary. We will release the native
             * memory in the C API finalization.
             */
            boolean isBuiltinClass = clazz instanceof PythonBuiltinClass;

            long ptr = NativeMemory.malloc(size);
            CApiTransitions.createPythonClassReference(clazz, ptr, true);
            ToNativeTypeNode.initializeType(clazz, ptr, heaptype);
            assert !isBuiltinClass || clazz.getRefCount() == IMMORTAL_REFCNT;
            return ptr;
        }

        @Specialization
        @TruffleBoundary
        static long doMemoryView(Node inliningTarget, PMemoryView mv, long initialRefCount) {
            assert !mv.isNative();
            assert initialRefCount == IMMORTAL_REFCNT;
            long ptr = PyMemoryViewWrapper.allocate(mv);
            // TODO: this passes "false" for allocatedFromJava, although it actually is. The
            // problem, however, is that this struct contains nested allocations from Java. This
            // needs to be cleaned up...
            CApiTransitions.createReference(mv, ptr, false);
            return ptr;
        }

        @Specialization(guards = "isSpecialSingleton(singletonObject)")
        @TruffleBoundary
        static long doSpecialSingleton(@SuppressWarnings("unused") Node inliningTarget, PythonAbstractObject singletonObject, long initialRefCount) {
            assert initialRefCount == IMMORTAL_REFCNT;

            Object type = GetClassNode.executeUncached(singletonObject);
            assert (GetTypeFlagsNode.executeUncached(type) & TypeFlags.HAVE_GC) == 0;

            return AllocateNativeObjectStubNodeGen.getUncached().execute(inliningTarget, singletonObject, type, CStructs.GraalPyObject, IMMORTAL_REFCNT, false);
        }

        @Specialization(guards = "!isManagedClass(pythonObject)")
        static long doOther(Node inliningTarget, PythonObject pythonObject, long initialRefCount,
                        @Exclusive @Cached InlinedConditionProfile isVarObjectProfile,
                        @Exclusive @Cached InlinedConditionProfile isGcProfile,
                        @Exclusive @Cached InlinedConditionProfile isFloatObjectProfile,
                        @Cached GetPythonObjectClassNode getClassNode,
                        @Cached(inline = false) GetTypeFlagsNode getTypeFlagsNode,
                        @Exclusive @Cached AllocateNativeObjectStubNode allocateNativeObjectStubNode) {

            // for types, we always need to allocate the full PyTypeObject
            assert !(pythonObject instanceof PythonManagedClass);

            Object type = getClassNode.execute(inliningTarget, pythonObject);

            CStructs ctype;
            if (isVarObjectProfile.profile(inliningTarget, pythonObject instanceof PTuple)) {
                ctype = CStructs.GraalPyVarObject;
            } else if (isFloatObjectProfile.profile(inliningTarget, pythonObject instanceof PFloat)) {
                ctype = CStructs.GraalPyFloatObject;
            } else {
                ctype = CStructs.GraalPyObject;
            }

            boolean gc = isGcProfile.profile(inliningTarget, (getTypeFlagsNode.execute(type) & TypeFlags.HAVE_GC) != 0);
            long taggedPointer = allocateNativeObjectStubNode.execute(inliningTarget, pythonObject, type, ctype, initialRefCount, gc);

            // allocate a native stub object (C type: GraalPy*Object)
            if (ctype == CStructs.GraalPyVarObject) {
                assert pythonObject instanceof PTuple;
                SequenceStorage sequenceStorage = ((PTuple) pythonObject).getSequenceStorage();
                long realPointer = HandlePointerConverter.pointerToStub(taggedPointer);
                writeLongField(realPointer, CFields.GraalPyVarObject__ob_size, sequenceStorage.length());
                long obItemPtr = NULLPTR;
                if (sequenceStorage instanceof NativeSequenceStorage nativeSequenceStorage) {
                    obItemPtr = nativeSequenceStorage.getPtr();
                }
                writePtrField(realPointer, CFields.GraalPyVarObject__ob_item, obItemPtr);
            } else if (ctype == CStructs.GraalPyFloatObject) {
                assert pythonObject instanceof PFloat;
                long realPointer = HandlePointerConverter.pointerToStub(taggedPointer);
                writeDoubleField(realPointer, CFields.GraalPyFloatObject__ob_fval, ((PFloat) pythonObject).getValue());
            }

            return taggedPointer;
        }

        public static long getInitialRefcnt(boolean newRef, boolean immortal) {
            if (immortal) {
                return IMMORTAL_REFCNT;
            }
            return MANAGED_REFCNT + (newRef ? 1 : 0);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    abstract static class AllocateNativeObjectStubNode extends Node {

        abstract long execute(Node inliningTarget, PythonAbstractObject object, Object type, CStructs ctype, long initialRefCount, boolean gc);

        @Specialization
        static long doGeneric(Node inliningTarget, PythonAbstractObject object, Object type, CStructs ctype, long initialRefCount, boolean gc,
                        @Cached(inline = false) GilNode gil,
                        @Cached(inline = false) CStructAccess.WriteObjectNewRefNode writeObjectNode,
                        @Cached PyObjectGCTrackNode gcTrackNode) {

            log(object);
            pollReferenceQueue();

            /*
             * Allocate a native stub object (C type: GraalPy*Object). For types that participate in
             * Python's GC, we will also allocate space for 'PyGC_Head'.
             */
            long presize = gc ? CStructs.PyGC_Head.size() : 0;
            long stubPointer = calloc(ctype.size() + presize);

            PythonContext pythonContext = PythonContext.get(inliningTarget);
            HandleContext handleContext = pythonContext.nativeContext;
            long taggedPointer = HandlePointerConverter.stubToPointer(stubPointer);

            long taggedGCHead = 0;
            if (gc) {
                // adjust allocation count of generation
                // GCState *gcstate = get_gc_state();
                long gcState = pythonContext.getCApiContext().getGCState();
                assert gcState != 0L;
                // compute start address of embedded array; essentially '&gcstate->generations[0]'
                long generations = CStructAccess.getFieldPtr(gcState, CFields.GCState__generations);

                // gcstate->generations[0].count++;
                int count = CStructAccess.readIntField(generations, CFields.GCGeneration__count);
                CStructAccess.writeIntField(generations, CFields.GCGeneration__count, count + 1);

                /*
                 * The corresponding location in CPython (i.e. 'typeobject.c: PyType_GenericAlloc')
                 * would now track the object. We don't do that yet because the object is still
                 * weakly referenced. As soon as someone increfs, the object will be added to the
                 * young generation.
                 */

                // same as in 'gcmodule.c: gc_alloc': PyObject *op = (PyObject *)(mem + presize);
                taggedGCHead = taggedPointer;
                stubPointer += presize;
                taggedPointer += presize;
            }

            CStructAccess.writeLongField(stubPointer, CFields.PyObject__ob_refcnt, initialRefCount);

            // TODO(fa): this should not require the GIL (GR-51314)
            boolean acquired = gil.acquire();
            try {
                writeObjectNode.write(stubPointer, CFields.PyObject__ob_type, type);
                int idx = nativeStubLookupReserve(handleContext);
                // We don't allow 'handleTableIndex == 0' to avoid that zeroed memory
                // accidentally maps to some valid object.
                assert idx > 0;
                CStructAccess.writeIntField(stubPointer, CFields.GraalPyObject__handle_table_index, idx);
                Object ref;
                if (initialRefCount > MANAGED_REFCNT) {
                    ref = object;
                } else {
                    /*
                     * If the object is not a 'PythonObject', it is expected to be immortal and will
                     * not reach this branch. This is, e.g., the case for singletons like PNone.
                     */
                    assert !CApiContext.isSpecialSingleton(object);
                    ref = PythonObjectReference.createStub(handleContext, (PythonObject) object, false, taggedPointer, idx, gc);
                }
                nativeStubLookupPut(handleContext, idx, ref, taggedPointer);

                assert !gc || taggedGCHead != 0;
                if (gc) {
                    /*
                     * Note: The following part will require the GIL even if we resolve GR-51314 and
                     * remove the outer acquire.
                     */
                    assert pythonContext.ownsGil();
                    gcTrackNode.executeGc(inliningTarget, taggedGCHead);
                }
            } catch (OverflowException e) {
                /*
                 * The OverflowException may be thrown by 'nativeStubLookupReserve' and indicates
                 * that we cannot resize the handle table anymore. This essentially indicates a
                 * Python-level MemoryError.
                 */
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw PRaiseNode.raiseStatic(inliningTarget, PythonBuiltinClassType.MemoryError);
            } finally {
                gil.release(acquired);
            }
            return logResult(taggedPointer);
        }
    }

    public static void createPythonClassReference(PythonManagedClass obj, long ptr, boolean allocatedFromJava) {
        CompilerAsserts.neverPartOfCompilation();
        createReference(obj, ptr, allocatedFromJava);
    }

    /**
     * Creates a {@link PythonObjectReference} to {@code delegate} and connects that to the given
     * native {@code pointer} such that the {@code pointer} can be resolved to the {@code delegate}.
     */
    @TruffleBoundary
    @SuppressWarnings("try")
    public static void createReference(PythonObject obj, long ptr, boolean allocatedFromJava) {
        try (GilNode.UncachedAcquire ignored = GilNode.uncachedAcquire()) {
            /*
             * The first test if '!obj.isNative()' in the caller is done on a fast-path but not
             * synchronized. So, repeat the test after the GIL was acquired.
             */
            if (!obj.isNative()) {
                logVoid(obj, ptr);
                obj.setNativePointer(ptr);
                pollReferenceQueue();
                HandleContext context = getContext();
                nativeLookupPut(context, ptr, PythonObjectReference.createReplacement(context, obj, ptr, allocatedFromJava));
            }
        }
    }

    /**
     * Creates a weak reference to {@code delegate} and connects that to the given {@code pointer}
     * object such that the {@code pointer} can be resolved to the {@code delegate}.
     * <p>
     * This is used in LLVM managed mode where we will not have real native pointers (i.e. addresses
     * pointing into off-heap memory) but managed pointers to objects emulating the native memory.
     * We still need to be able to resolve those managed pointers to our objects.
     * </p>
     */
    public static void createManagedReference(Object delegate, Object pointer) {
        assert PythonContext.get(null).ownsGil();
        getContext().managedNativeLookup.put(pointer, new WeakReference<>(delegate));
    }

    // logging

    private static void log(Object... args) {
        if (LOGGER.isLoggable(Level.FINER)) {
            logBoundary(args);
        }
    }

    @TruffleBoundary
    private static void logBoundary(Object... args) {
        if (LOGGER.isLoggable(Level.FINER)) {
            CompilerAsserts.neverPartOfCompilation();
            StackTraceElement element = new RuntimeException().getStackTrace()[1];
            StringBuilder str = new StringBuilder();
            String className = element.getClassName();
            if (className.contains(".")) {
                className = className.substring(className.lastIndexOf('.') + 1);
            }
            str.append(className).append(".").append(element.getMethodName()).append(": ");
            for (int i = 0; i < args.length; i++) {
                Object a = args[i];
                str.append(i == 0 ? "" : ", ");
                format(str, a);
            }
            LOGGER.finer(str.toString());
        }
    }

    private static void logVoid(Object... args) {
        log(args);
    }

    private static void format(StringBuilder str, Object a) {
        if (a instanceof Long) {
            str.append(String.format("0x%x", (long) a));
        } else if (a instanceof Integer) {
            str.append(String.format("0x%x", (int) a));
        } else {
            str.append(a);
        }
    }

    @TruffleBoundary
    private static <T> T logResultBoundary(T value) {
        if (LOGGER.isLoggable(Level.FINEST)) {
            CompilerAsserts.neverPartOfCompilation();
            StackTraceElement element = new RuntimeException().getStackTrace()[1];
            StringBuilder str = new StringBuilder("    ==> <").append(element.getLineNumber()).append("> ");
            format(str, value);
            LOGGER.finest(str.toString());
        }
        return value;
    }

    private static <T> T logResult(T value) {
        if (LOGGER.isLoggable(Level.FINEST)) {
            logResultBoundary(value);
        }
        return value;
    }

    /**
     * Resolves a native handle to the corresponding {@link PythonObject}. This node assumes that
     * {@code pointer} points to handle space (i.e.
     * {@link HandlePointerConverter#pointsToPyHandleSpace(long)} is {@code true}) and essential
     * just looks up the handle in the table. It will additionally increment the reference count if
     * the object is a subclass of {@link PythonObject}.
     */
    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class ResolveHandleNode extends Node {

        public abstract PythonObject execute(Node inliningTarget, long pointer);

        @Specialization
        static PythonObject doGeneric(Node inliningTarget, long pointer,
                        @Cached InlinedExactClassProfile profile,
                        @Cached UpdateStrongRefNode updateRefNode) {
            if (HandlePointerConverter.pointsToPyIntHandle(pointer)) {
                throw CompilerDirectives.shouldNotReachHere("ResolveHandleNode int");
            } else if (HandlePointerConverter.pointsToPyFloatHandle(pointer)) {
                throw CompilerDirectives.shouldNotReachHere("ResolveHandleNode float");
            }
            HandleContext nativeContext = PythonContext.get(inliningTarget).nativeContext;
            int idx = readIntField(HandlePointerConverter.pointerToStub(pointer), CFields.GraalPyObject__handle_table_index);
            Object reference = nativeStubLookupGet(nativeContext, pointer, idx);
            PythonObject wrapper;
            if (reference instanceof PythonObject) {
                wrapper = profile.profile(inliningTarget, (PythonObject) reference);
            } else {
                assert reference instanceof PythonObjectReference;
                wrapper = profile.profile(inliningTarget, ((PythonObjectReference) reference).get());
            }
            assert wrapper != null : "reference was collected: " + Long.toHexString(pointer);
            updateRefNode.execute(inliningTarget, wrapper, wrapper.incRef());
            return wrapper;
        }
    }

    @GenerateUncached
    @GenerateInline(false)
    public abstract static class CharPtrToPythonNode extends CExtToJavaNode {

        @Specialization
        static Object doForeign(Object value,
                        @Bind Node inliningTarget,
                        @CachedLibrary(limit = "3") InteropLibrary interopLibrary,
                        @Cached InlinedExactClassProfile classProfile,
                        @Cached InlinedConditionProfile isNullProfile,
                        @Cached FromCharPointerNode fromCharPointerNode,
                        @Cached ResolveHandleNode resolveHandleNode) {
            Object profiledValue = classProfile.profile(inliningTarget, value);
            // this branch is not a shortcut; it actually returns a different object
            if (isNullProfile.profile(inliningTarget, interopLibrary.isNull(profiledValue))) {
                return PNone.NO_VALUE;
            }
            log(profiledValue);
            assert !(profiledValue instanceof Long);
            if (profiledValue instanceof String) {
                return logResult(PythonUtils.toTruffleStringUncached((String) profiledValue));
            } else if (profiledValue instanceof TruffleString) {
                return logResult(profiledValue);
            }
            if (interopLibrary.isPointer(profiledValue)) {
                long pointer;
                try {
                    pointer = interopLibrary.asPointer(profiledValue);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
                if (HandlePointerConverter.pointsToPyHandleSpace(pointer)) {
                    assert !HandlePointerConverter.pointsToPyIntHandle(pointer);
                    assert !HandlePointerConverter.pointsToPyFloatHandle(pointer);
                    PythonObject obj = resolveHandleNode.execute(inliningTarget, pointer);
                    if (obj != null) {
                        return logResult(obj);
                    }
                }
            }
            return logResult(fromCharPointerNode.execute(profiledValue));
        }

        @NeverDefault
        public static CharPtrToPythonNode create() {
            return CApiTransitionsFactory.CharPtrToPythonNodeGen.create();
        }

        public static CharPtrToPythonNode getUncached() {
            return CApiTransitionsFactory.CharPtrToPythonNodeGen.getUncached();
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class PythonToNativeInternalNode extends Node {

        @TruffleBoundary
        public static long executeUncached(Object obj, boolean needsTransfer) {
            return PythonToNativeInternalNodeGen.getUncached().execute(null, obj, needsTransfer);
        }

        public abstract long execute(Node inliningTarget, Object object, boolean needsTransfer);

        @Specialization
        static long doNative(Node inliningTarget, PythonAbstractNativeObject obj, boolean needsTransfer,
                        @Exclusive @Cached InlinedBranchProfile hasReplicatedNativeReferences,
                        @Exclusive @Cached UpdateStrongRefNode updateRefNode) {
            if (needsTransfer && PythonContext.get(inliningTarget).isNativeAccessAllowed()) {
                long newRefcnt = CApiTransitions.addNativeRefCount(obj.getPtr(), 1);
                /*
                 * If a native object was only referenced from managed (i.e. refcnt ==
                 * MANAGED_REFCNT), it may be that its native references were already replicated to
                 * Java and the referents are only weakly referenced because of that. If we now
                 * incref, the assumption under which the Python GC made the references weak, do no
                 * longer hold (the assumption is documented in 'gcmodule.c: move_weak_reachable').
                 * Since we incref'd and now give the object to native, it may happen that the Java
                 * wrapper dies and so it's references would die although the object is still
                 * referenced from native code. To avoid this, we need to update the references of
                 * the replicated native references.
                 */
                if (PythonLanguage.get(inliningTarget).getEngineOption(PythonOptions.PythonGC) &&
                                newRefcnt == MANAGED_REFCNT + 1 && obj.getReplicatedNativeReferences() != null) {
                    hasReplicatedNativeReferences.enter(inliningTarget);
                    for (Object referent : obj.getReplicatedNativeReferences()) {
                        if (referent instanceof PythonObject pythonObject) {
                            updateRefNode.execute(inliningTarget, pythonObject, pythonObject.getRefCount());
                        }
                    }
                }
            }
            return obj.getPtr();
        }

        static boolean mapsToNull(Object obj) {
            return PNone.NO_VALUE == obj || DescriptorDeleteMarker.INSTANCE == obj;
        }

        @Specialization(guards = "mapsToNull(obj)")
        @SuppressWarnings("unused")
        static long doNullValues(Node inliningTarget, Object obj, boolean needsTransfer) {
            return 0;
        }

        @Specialization
        static long doPythonObject(Node inliningTarget, PythonObject pythonObject, boolean needsTransfer,
                        @Exclusive @Cached FirstToNativeNode firstToNativeNode,
                        @Exclusive @Cached UpdateStrongRefNode updateRefNode) {
            CompilerAsserts.partialEvaluationConstant(needsTransfer);
            assert PythonContext.get(inliningTarget).ownsGil();
            pollReferenceQueue();

            long pointer;
            if (!pythonObject.isNative()) {
                assert !CApiContext.isSpecialSingleton(pythonObject);
                PythonContext context = PythonContext.get(inliningTarget);
                boolean immortal = isImmortal(context, pythonObject);
                pointer = firstToNativeNode.execute(inliningTarget, pythonObject, FirstToNativeNode.getInitialRefcnt(needsTransfer, immortal));
                pythonObject.setNativePointer(pointer);
            } else {
                if (needsTransfer) {
                    /*
                     * This creates a new reference to the object and the ownership is transferred
                     * to the C extension. Therefore, we need to make the reference strong such that
                     * we do not deallocate the object if it's no longer referenced in the
                     * interpreter. The interpreter will be notified by an upcall as soon as the
                     * object's refcount goes down to MANAGED_RECOUNT again.
                     */
                    long refCnt = pythonObject.incRef();
                    assert refCnt > MANAGED_REFCNT;
                    updateRefNode.execute(inliningTarget, pythonObject, refCnt);
                }
                pointer = pythonObject.getNativePointer();
            }
            assert pythonObject.isNative();
            assert isGcTrackedIfNecessary(pythonObject);
            return pointer;
        }

        @Specialization(replaces = {"doNative", "doNullValues", "doPythonObject"})
        static long doGeneric(Node inliningTarget, Object obj, boolean needsTransfer,
                        @Cached InlinedExactClassProfile classProfile,
                        @Exclusive @Cached InlinedBranchProfile hasReplicatedNativeReferences,
                        @Cached EnsurePythonObjectNode ensurePythonObjectNode,
                        @Exclusive @Cached FirstToNativeNode firstToNativeNode,
                        @Exclusive @Cached UpdateStrongRefNode updateRefNode) {
            CompilerAsserts.partialEvaluationConstant(needsTransfer);
            assert PythonContext.get(inliningTarget).ownsGil();
            pollReferenceQueue();

            Object profiled = classProfile.profile(inliningTarget, obj);

            // Step 1: box values in pointers if possible
            if (profiled instanceof Integer i) {
                return HandlePointerConverter.intToPointer(i);
            } else if (profiled instanceof Long l && PInt.fitsInInt(l)) {
                return HandlePointerConverter.intToPointer(l.intValue());
            } else if (profiled instanceof Float f) {
                return HandlePointerConverter.floatToPointer(f);
            } else if (profiled instanceof Double d && PFloat.fitsInFloat(d)) {
                return HandlePointerConverter.floatToPointer(d.floatValue());
            }

            // Step 2: handle values that map to NULL
            if (mapsToNull(profiled)) {
                return 0;
            }

            // Step 3: handle native objects
            if (profiled instanceof PythonAbstractNativeObject pythonAbstractNativeObject) {
                return doNative(inliningTarget, pythonAbstractNativeObject, needsTransfer, hasReplicatedNativeReferences, updateRefNode);
            }

            PythonContext context = PythonContext.get(inliningTarget);

            /*
             * Step 4: Special singletons (e.g. PNone) are context-independent. Their native
             * companions are stored in a special cache.
             */
            long pointer;
            if (CApiContext.isSpecialSingleton(profiled)) {
                pointer = context.getCApiContext().getSingletonNativeWrapper((PythonAbstractObject) profiled);
                assert HandlePointerConverter.pointsToPyHandleSpace(pointer);
                // special singletons (e.g. PNone, PEllipsis, ..) are always immortal
                assert CApiTransitions.readNativeRefCount(HandlePointerConverter.pointerToStub(pointer)) == IMMORTAL_REFCNT;
                return pointer;
            }

            /*
             * Step 5: All objects that are given to native need to be PythonObjects. This is
             * because we need to store the pointer somewhere to preserve object/pointer identity.
             */
            PythonObject pythonObject = ensurePythonObjectNode.execute(context, profiled);
            assert !CApiContext.isSpecialSingleton(pythonObject);

            /*
             * Step 6: If the PythonObject is not already native, create the native companion. If it
             * already has a native companion, use it and maybe update the reference (strong/weak)
             * if the reference count is increased.
             */
            if (!pythonObject.isNative()) {
                boolean immortal = isImmortalPythonObject(context, pythonObject);
                pointer = firstToNativeNode.execute(inliningTarget, pythonObject, FirstToNativeNode.getInitialRefcnt(needsTransfer, immortal));
                pythonObject.setNativePointer(pointer);
            } else {
                if (needsTransfer) {
                    /*
                     * This creates a new reference to the object and the ownership is transferred
                     * to the C extension. Therefore, we need to make the reference strong such that
                     * we do not deallocate the object if it's no longer referenced in the
                     * interpreter. The interpreter will be notified by an upcall as soon as the
                     * object's refcount goes down to MANAGED_RECOUNT again.
                     */
                    long refCnt = pythonObject.incRef();
                    assert refCnt > MANAGED_REFCNT;
                    updateRefNode.execute(inliningTarget, pythonObject, refCnt);
                }
                pointer = pythonObject.getNativePointer();
            }
            assert isGcTrackedIfNecessary(pythonObject);
            assert pythonObject.isNative();
            return pointer;
        }

        /**
         * Determines if an arbitrary object is immortal.
         */
        public static boolean isImmortal(PythonContext context, Object object) {
            // boxable int/float values and any special singletons are immortal
            return object instanceof Integer ||
                            object instanceof Long l && PInt.fitsInInt(l) ||
                            object instanceof Float ||
                            object instanceof Double d && PFloat.fitsInFloat(d) ||
                            CApiContext.isSpecialSingleton(object) ||
                            object instanceof PythonObject pythonObject && isImmortalPythonObject(context, pythonObject);
        }

        /**
         * PMemoryView, static native classes, built-in classes, and singletons True/False are
         * immortal.
         */
        private static boolean isImmortalPythonObject(PythonContext context, PythonObject pythonObject) {
            return pythonObject instanceof PythonBuiltinClass || context.getTrue() == pythonObject || context.getFalse() == pythonObject || pythonObject instanceof PMemoryView;
        }

        /**
         * Verify, if the object is correctly tracked by the Python GC if necessary.
         */
        @TruffleBoundary
        private static boolean isGcTrackedIfNecessary(PythonObject object) {
            // if Python GC is not enabled, we are fine
            if (!PythonLanguage.get(null).getEngineOption(PythonOptions.PythonGC)) {
                return true;
            }
            // an object won't be GC tracked if the reference is "weak" or if it is immortal
            long refCount = object.getRefCount();
            if (refCount == MANAGED_REFCNT || refCount == IMMORTAL_REFCNT) {
                return true;
            }

            // is_gc(type(delegate)) => tracked
            boolean isGc = (GetTypeFlagsNode.executeUncached(GetClassNode.executeUncached(object)) & TypeFlags.HAVE_GC) != 0;
            return !isGc || PyObjectGCTrackNode.isGcTracked(object.getNativePointer());
        }
    }

    // TODO(NFI2) replace usages with PythonToNativeRawNode
    @GenerateUncached
    @GenerateInline(false)
    public abstract static class PythonToNativeNode extends CExtToNativeNode {

        @TruffleBoundary
        public static Object executeUncached(Object obj) {
            return PythonToNativeNodeGen.getUncached().execute(obj);
        }

        @Specialization
        static Object doGeneric(Object obj,
                        @Bind Node inliningTarget,
                        @Cached PythonToNativeInternalNode internalNode) {
            return internalNode.execute(inliningTarget, obj, false);
        }

        @NeverDefault
        public static PythonToNativeNode create() {
            return PythonToNativeNodeGen.create();
        }

        public static PythonToNativeNode getUncached() {
            return PythonToNativeNodeGen.getUncached();
        }
    }

    @GenerateUncached
    @GenerateInline(false)
    public abstract static class PythonToNativeRawNode extends Node {

        public abstract long execute(Object object);

        @TruffleBoundary
        public static long executeUncached(Object obj) {
            return PythonToNativeRawNodeGen.getUncached().execute(obj);
        }

        @Specialization
        static long doGeneric(Object obj,
                        @Bind Node inliningTarget,
                        @Cached PythonToNativeInternalNode internalNode,
                        @Cached CoerceNativePointerToLongNode coerceNode) {
            return ensurePointer(internalNode.execute(inliningTarget, obj, false), inliningTarget, coerceNode);
        }

        @NeverDefault
        public static PythonToNativeRawNode create() {
            return PythonToNativeRawNodeGen.create();
        }

        public static PythonToNativeRawNode getUncached() {
            return PythonToNativeRawNodeGen.getUncached();
        }
    }

    /**
     * Same as {@code PythonToNativeNode} but ensures that a new Python reference is returned.<br/>
     * Concept:<br/>
     * <p>
     * If the value to convert is a managed object or a Java primitive, we will (1) do nothing if a
     * fresh wrapper is created, or (2) increase the reference count by 1 if the wrapper already
     * exists.
     * </p>
     * <p>
     * If the value to convert is a {@link PythonAbstractNativeObject} (i.e. a wrapped native
     * pointer), the reference count will be increased by 1. This is necessary because if the
     * currently returning upcall function already got a new reference, it won't have increased the
     * refcnt but will eventually decreases it.<br/>
     * Consider following example:<br/>
     *
     * <pre>
     *     some.py: nativeLong0 * nativeLong1
     * </pre>
     *
     * Assume that {@code nativeLong0} is a native object with a native type. It will call
     * {@code nativeType->tp_as_number.nb_multiply}. This one then often uses
     * {@code PyNumber_Multiply} which should just pass through the newly created native reference.
     * But it will decrease the reference count since it wraps the gained native pointer. So, the
     * intermediate upcall should effectively not alter the refcnt which means that we need to
     * increase it since it will finally decrease it.
     * </p>
     */
    // TODO(NFI2) replace usage with PythonToNativeNewRefRawNode
    @GenerateUncached
    @GenerateInline(false)
    public abstract static class PythonToNativeNewRefNode extends CExtToNativeNode {

        @TruffleBoundary
        public static Object executeUncached(Object obj) {
            return PythonToNativeNewRefNodeGen.getUncached().execute(obj);
        }

        @Specialization
        static Object doGeneric(Object obj,
                        @Bind Node inliningTarget,
                        @Cached PythonToNativeInternalNode internalNode) {
            return internalNode.execute(inliningTarget, obj, true);
        }

        @NeverDefault
        public static PythonToNativeNewRefNode create() {
            return PythonToNativeNewRefNodeGen.create();
        }

        public static PythonToNativeNewRefNode getUncached() {
            return PythonToNativeNewRefNodeGen.getUncached();
        }
    }

    /**
     * Same as {@code PythonToNativeRawNode} but ensures that a new Python reference is
     * returned.<br/>
     * Concept:<br/>
     * <p>
     * If the value to convert is a managed object or a Java primitive, we will (1) do nothing if a
     * fresh wrapper is created, or (2) increase the reference count by 1 if the wrapper already
     * exists.
     * </p>
     * <p>
     * If the value to convert is a {@link PythonAbstractNativeObject} (i.e. a wrapped native
     * pointer), the reference count will be increased by 1. This is necessary because if the
     * currently returning upcall function already got a new reference, it won't have increased the
     * refcnt but will eventually decreases it.<br/>
     * Consider following example:<br/>
     *
     * <pre>
     *     some.py: nativeLong0 * nativeLong1
     * </pre>
     *
     * Assume that {@code nativeLong0} is a native object with a native type. It will call
     * {@code nativeType->tp_as_number.nb_multiply}. This one then often uses
     * {@code PyNumber_Multiply} which should just pass through the newly created native reference.
     * But it will decrease the reference count since it wraps the gained native pointer. So, the
     * intermediate upcall should effectively not alter the refcnt which means that we need to
     * increase it since it will finally decrease it.
     * </p>
     */
    @GenerateUncached
    @GenerateInline(false)
    public abstract static class PythonToNativeNewRefRawNode extends Node {

        public abstract long execute(Object object);

        @TruffleBoundary
        public static long executeUncached(Object obj) {
            return PythonToNativeNewRefRawNodeGen.getUncached().execute(obj);
        }

        @Specialization
        static long doGeneric(Object obj,
                        @Bind Node inliningTarget,
                        @Cached PythonToNativeInternalNode internalNode,
                        @Cached CoerceNativePointerToLongNode coerceNode) {
            return ensurePointer(internalNode.execute(inliningTarget, obj, true), inliningTarget, coerceNode);
        }

        @NeverDefault
        public static PythonToNativeNewRefRawNode create() {
            return PythonToNativeNewRefRawNodeGen.create();
        }

        public static PythonToNativeNewRefRawNode getUncached() {
            return PythonToNativeNewRefRawNodeGen.getUncached();
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class NativeToPythonInternalNode extends Node {

        public final Object execute(Node inliningTarget, long value, boolean needsTransfer) {
            return execute(inliningTarget, value, needsTransfer, false);
        }

        public abstract Object execute(Node inliningTarget, long value, boolean needsTransfer, boolean release);

        @TruffleBoundary
        public static Object executeUncached(long value, boolean needsTransfer) {
            return NativeToPythonInternalNodeGen.getUncached().execute(null, value, needsTransfer);
        }

        @Specialization
        @SuppressWarnings({"truffle-static-method", "truffle-sharing"})
        static Object doGeneric(Node inliningTarget, long pointer, boolean needsTransfer, boolean release,
                        @Cached InlinedConditionProfile isZeroProfile,
                        @Cached InlinedConditionProfile createNativeProfile,
                        @Cached InlinedConditionProfile isNativeProfile,
                        @Cached InlinedConditionProfile isNativeObjectProfile,
                        @Cached InlinedConditionProfile isHandleSpaceProfile,
                        @Exclusive @Cached InlinedExactClassProfile wrapperProfile,
                        @Exclusive @Cached UpdateStrongRefNode updateRefNode) {
            if (isZeroProfile.profile(inliningTarget, pointer == 0)) {
                return PNone.NO_VALUE;
            }

            PythonContext pythonContext = PythonContext.get(inliningTarget);
            HandleContext nativeContext = pythonContext.nativeContext;

            assert pythonContext.ownsGil();
            PythonAbstractObject result;
            if (isHandleSpaceProfile.profile(inliningTarget, HandlePointerConverter.pointsToPyHandleSpace(pointer))) {
                if (HandlePointerConverter.pointsToPyIntHandle(pointer)) {
                    return HandlePointerConverter.pointerToLong(pointer);
                } else if (HandlePointerConverter.pointsToPyFloatHandle(pointer)) {
                    return HandlePointerConverter.pointerToDouble(pointer);
                }
                int idx = readIntField(HandlePointerConverter.pointerToStub(pointer), CFields.GraalPyObject__handle_table_index);
                Object reference = nativeStubLookupGet(nativeContext, pointer, idx);
                if (reference instanceof PythonAbstractObject) {
                    result = (PythonAbstractObject) reference;
                } else if (reference instanceof PythonObjectReference pythonObjectReference) {
                    result = pythonObjectReference.get();
                } else {
                    assert reference == null;
                    /*
                     * Here we are encountering a weakref object that has died in the managed side,
                     * e.g. PReferenceType, but we kept alive in the native side, see
                     * pollReferenceQueue(). Though, if this happens to an object that shouldn't
                     * have died in the managed side, the native side should catch it with a null
                     * pointer check.
                     */
                    if (LOGGER.isLoggable(Level.FINE)) {
                        LOGGER.fine(() -> "managed weak reference has been collected: " + Long.toHexString(pointer));
                    }
                    return PNone.NO_VALUE;
                }
                if (result == null) {
                    int collecting = CStructAccess.readIntField(pythonContext.getCApiContext().getGCState(), CFields.GCState__collecting);
                    if (collecting == 1) {
                        return PNone.NO_VALUE;
                    }
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw CompilerDirectives.shouldNotReachHere("reference was collected: " + Long.toHexString(pointer));
                }
            } else {
                IdReference<?> lookup = nativeLookupGet(nativeContext, pointer);
                if (isNativeProfile.profile(inliningTarget, lookup != null)) {
                    Object ref = lookup.get();
                    if (createNativeProfile.profile(inliningTarget, ref == null)) {
                        if (LOGGER.isLoggable(Level.FINE)) {
                            LOGGER.fine(() -> "re-creating collected PythonAbstractNativeObject reference" + Long.toHexString(pointer));
                        }
                        return createAbstractNativeObject(nativeContext, needsTransfer, pointer);
                    }
                    if (isNativeObjectProfile.profile(inliningTarget, ref instanceof PythonAbstractNativeObject)) {
                        if (needsTransfer) {
                            addNativeRefCount(pointer, -1);
                        }
                        return ref;
                    } else {
                        assert ref instanceof PythonAbstractObject;
                        result = (PythonAbstractObject) ref;
                    }
                } else {
                    return createAbstractNativeObject(nativeContext, needsTransfer, pointer);
                }
            }
            return updateRef(inliningTarget, wrapperProfile, updateRefNode, needsTransfer, release, result);
        }

        /**
         * Resolves a object to its delegate and does appropriate reference count manipulation.
         *
         * @param node The inlining target for profiles.
         * @param wrapperProfile The object class profile.
         * @param transfer Indicates if ownership of the reference is transferred to managed space.
         * @param object The native object to unwrap.
         * @return The Python value contained in the native object.
         */
        static PythonAbstractObject updateRef(Node node, InlinedExactClassProfile wrapperProfile, UpdateStrongRefNode updateRefNode, boolean transfer, boolean release, PythonAbstractObject object) {
            PythonAbstractObject profiled = wrapperProfile.profile(node, object);
            assert !(profiled instanceof PythonAbstractNativeObject);
            if (transfer && profiled instanceof PythonObject pythonObject) {
                /*
                 * If 'transfer' is true, this means the ownership is transferred (in this case to
                 * the "interpreter"). We don't do reference counting in the interpreter, therefore
                 * we set the managed refcount once and never touch it again. Since the receiving
                 * object is a managed object, the refcount has already been initialized to
                 * MANAGED_REFCNT at the time the object was handed out to the C extension. If now
                 * the ownership is again transferred to the interpreter, then the native code
                 * *MUST* have done an incref and so the refcount must be greater than
                 * MANAGED_REFCNT.
                 */
                assert pythonObject.getRefCount() > MANAGED_REFCNT;
                updateRefNode.execute(node, pythonObject, pythonObject.decRef(), release);
            }
            return object;
        }
    }

    @GenerateUncached
    @GenerateInline(false)
    public abstract static class NativeToPythonNode extends CExtToJavaNode {

        @TruffleBoundary
        public static Object executeUncached(Object obj) {
            return NativeToPythonNodeGen.getUncached().execute(obj);
        }

        @Specialization
        static Object doLong(long value,
                        @Bind Node inliningTarget,
                        @Shared @Cached NativeToPythonInternalNode nativeToPythonInternalNode) {
            return nativeToPythonInternalNode.execute(inliningTarget, value, false);
        }

        @Specialization(limit = "1")
        static Object doInteropPointer(Object nativePointer,
                        @Bind Node inliningTarget,
                        @Shared @Cached NativeToPythonInternalNode nativeToPythonInternalNode,
                        @CachedLibrary("nativePointer") InteropLibrary lib) {
            try {
                return nativeToPythonInternalNode.execute(inliningTarget, lib.asPointer(nativePointer), false);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        @NeverDefault
        public static NativeToPythonNode create() {
            return NativeToPythonNodeGen.create();
        }

        public static NativeToPythonNode getUncached() {
            return NativeToPythonNodeGen.getUncached();
        }
    }

    @GenerateUncached
    @GenerateInline(false)
    public abstract static class NativeToPythonTransferNode extends CExtToJavaNode {

        @TruffleBoundary
        public static Object executeUncached(long pointer) {
            return NativeToPythonTransferNodeGen.getUncached().execute(pointer);
        }

        @Specialization
        static Object doLong(long pointer,
                        @Bind Node inliningTarget,
                        @Shared @Cached NativeToPythonInternalNode nativeToPythonInternalNode) {
            return nativeToPythonInternalNode.execute(inliningTarget, pointer, true);
        }

        @Specialization(limit = "1")
        static Object doInteropPointer(Object nativePointer,
                        @Bind Node inliningTarget,
                        @Shared @Cached NativeToPythonInternalNode nativeToPythonInternalNode,
                        @CachedLibrary("nativePointer") InteropLibrary lib) {
            try {
                return nativeToPythonInternalNode.execute(inliningTarget, lib.asPointer(nativePointer), true);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        @NeverDefault
        public static NativeToPythonTransferNode create() {
            return NativeToPythonTransferNodeGen.create();
        }

        public static NativeToPythonTransferNode getUncached() {
            return NativeToPythonTransferNodeGen.getUncached();
        }
    }

    @GenerateUncached
    @GenerateInline(false)
    public abstract static class NativeToPythonReturnNode extends CExtToJavaNode {

        @TruffleBoundary
        public static Object executeUncached(long pointer) {
            return NativeToPythonReturnNodeGen.getUncached().execute(pointer);
        }

        @Specialization
        static Object doLong(long pointer,
                        @Bind Node inliningTarget,
                        @Shared @Cached NativeToPythonInternalNode nativeToPythonInternalNode) {
            return nativeToPythonInternalNode.execute(inliningTarget, pointer, true, true);
        }

        @Specialization(limit = "1")
        static Object doNativePointer(Object pointer,
                        @Bind Node inliningTarget,
                        @CachedLibrary("pointer") InteropLibrary lib,
                        @Shared @Cached NativeToPythonInternalNode nativeToPythonInternalNode) {
            try {
                return doLong(lib.asPointer(pointer), inliningTarget, nativeToPythonInternalNode);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        @NeverDefault
        public static NativeToPythonReturnNode create() {
            return NativeToPythonReturnNodeGen.create();
        }

        public static NativeToPythonReturnNode getUncached() {
            return NativeToPythonReturnNodeGen.getUncached();
        }
    }

    @GenerateUncached
    @GenerateInline(false)
    public abstract static class NativePtrToPythonNode extends PNodeWithContext {

        public abstract Object execute(long object, boolean stealing);

        @TruffleBoundary
        public static Object executeUncached(long object, boolean stealing) {
            return NativePtrToPythonNodeGen.getUncached().execute(object, stealing);
        }

        @Specialization
        @SuppressWarnings({"truffle-static-method", "truffle-sharing"})
        static Object doNonWrapper(long pointer, boolean stealing,
                        @Bind Node inliningTarget,
                        @Cached InlinedConditionProfile isZeroProfile,
                        @Cached InlinedConditionProfile createNativeProfile,
                        @Cached InlinedConditionProfile isNativeProfile,
                        @Cached InlinedConditionProfile isNativeObjectProfile,
                        @Cached InlinedConditionProfile isHandleSpaceProfile,
                        @Cached InlinedExactClassProfile wrapperProfile,
                        @Cached UpdateStrongRefNode updateRefNode) {

            assert PythonContext.get(null).ownsGil();
            CompilerAsserts.partialEvaluationConstant(stealing);
            PythonAbstractObject pythonAbstractObject;

            PythonContext pythonContext = PythonContext.get(inliningTarget);
            HandleContext nativeContext = pythonContext.nativeContext;

            if (isZeroProfile.profile(inliningTarget, pointer == 0)) {
                return PNone.NO_VALUE;
            }
            assert pythonContext.ownsGil();
            if (isHandleSpaceProfile.profile(inliningTarget, HandlePointerConverter.pointsToPyHandleSpace(pointer))) {
                if (HandlePointerConverter.pointsToPyIntHandle(pointer)) {
                    return HandlePointerConverter.pointerToLong(pointer);
                } else if (HandlePointerConverter.pointsToPyFloatHandle(pointer)) {
                    return HandlePointerConverter.pointerToDouble(pointer);
                }
                int idx = readIntField(HandlePointerConverter.pointerToStub(pointer), CFields.GraalPyObject__handle_table_index);
                Object reference = nativeStubLookupGet(nativeContext, pointer, idx);
                if (reference instanceof PythonAbstractObject) {
                    pythonAbstractObject = (PythonAbstractObject) reference;
                } else if (reference instanceof PythonObjectReference pythonObjectReference) {
                    pythonAbstractObject = pythonObjectReference.get();
                } else {
                    assert reference == null;
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw CompilerDirectives.shouldNotReachHere("reference was freed: " + Long.toHexString(pointer));
                }
                if (pythonAbstractObject == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw CompilerDirectives.shouldNotReachHere("reference was collected: " + Long.toHexString(pointer));
                }
            } else {
                IdReference<?> lookup = nativeLookupGet(nativeContext, pointer);
                if (isNativeProfile.profile(inliningTarget, lookup != null)) {
                    Object ref = lookup.get();
                    if (createNativeProfile.profile(inliningTarget, ref == null)) {
                        LOGGER.fine(() -> "re-creating collected PythonAbstractNativeObject reference" + Long.toHexString(pointer));
                        return createAbstractNativeObject(nativeContext, stealing, pointer);
                    }
                    if (isNativeObjectProfile.profile(inliningTarget, ref instanceof PythonAbstractNativeObject)) {
                        if (stealing) {
                            addNativeRefCount(pointer, -1);
                        }
                        return ref;
                    } else {
                        assert ref instanceof PythonAbstractObject;
                        pythonAbstractObject = (PythonAbstractObject) ref;
                    }
                } else {
                    return createAbstractNativeObject(nativeContext, stealing, pointer);
                }
            }
            return NativeToPythonInternalNode.updateRef(inliningTarget, wrapperProfile, updateRefNode, stealing, false, pythonAbstractObject);
        }
    }

    /**
     * Very similar to {@link NativePtrToPythonNode}, this node resolves a native pointer (given as
     * Java {@code long}) to a Python object. However, it will never create a fresh
     * {@link PythonAbstractNativeObject} for a native object (it will only return one if it already
     * exists). Also, this node won't fail if a tagged pointer is given and the underlying managed
     * object was collected in the meantime. This is because it may happen that the native object
     * stub of a managed object is in the GC list and while processing it (e.g. replicating the
     * native references), the Java GC may collect it. In such cases, we don't want to fail but
     * return {@code null}.
     */
    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class GcNativePtrToPythonNode extends PNodeWithContext {

        public abstract Object execute(Node inliningTarget, long pointer);

        @Specialization
        static Object doLong(Node inliningTarget, long pointer,
                        @Cached InlinedBranchProfile isNativeProfile,
                        @Cached InlinedConditionProfile isHandleSpaceProfile) {

            PythonContext pythonContext = PythonContext.get(inliningTarget);
            HandleContext nativeContext = pythonContext.nativeContext;

            assert pointer != 0;
            assert pythonContext.ownsGil();
            if (isHandleSpaceProfile.profile(inliningTarget, HandlePointerConverter.pointsToPyHandleSpace(pointer))) {
                if (HandlePointerConverter.pointsToPyIntHandle(pointer)) {
                    return HandlePointerConverter.pointerToLong(pointer);
                } else if (HandlePointerConverter.pointsToPyFloatHandle(pointer)) {
                    return HandlePointerConverter.pointerToDouble(pointer);
                }
                int idx = readIntField(HandlePointerConverter.pointerToStub(pointer), CFields.GraalPyObject__handle_table_index);
                PythonObject pythonObject;
                Object reference = nativeStubLookupGet(nativeContext, pointer, idx);
                if (reference instanceof PythonObject) {
                    pythonObject = (PythonObject) reference;
                } else if (reference instanceof PythonObjectReference pythonObjectReference) {
                    pythonObject = pythonObjectReference.get();
                } else {
                    assert reference == null;
                    /*
                     * This should really not happen since it most likely means that we accessed
                     * free'd memory to read the handle table index.
                     */
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw CompilerDirectives.shouldNotReachHere("reference was freed: " + Long.toHexString(pointer));
                }
                return pythonObject;
            } else {
                IdReference<?> lookup = nativeLookupGet(nativeContext, pointer);
                Object referent;
                if (lookup != null && (referent = lookup.get()) != null) {
                    isNativeProfile.enter(inliningTarget);
                    assert referent instanceof PythonAbstractNativeObject;
                    return referent;
                }
                return null;
            }
        }
    }

    private static final Unsafe UNSAFE = PythonUtils.initUnsafe();
    private static final int TP_REFCNT_OFFSET = 0;

    public static long addNativeRefCount(long pointer, long refCntDelta) {
        return addNativeRefCount(pointer, refCntDelta, false);
    }

    private static long addNativeRefCount(long pointer, long refCntDelta, boolean ignoreIfDead) {
        assert PythonContext.get(null).isNativeAccessAllowed();
        assert PythonContext.get(null).ownsGil();
        LOGGER.finest(() -> PythonUtils.formatJString("addNativeRefCount %x ? + %d", pointer, refCntDelta));
        long refCount = UNSAFE.getLong(pointer + TP_REFCNT_OFFSET);
        if (ignoreIfDead && refCount == 0) {
            return 0;
        }
        if (refCount == IMMORTAL_REFCNT) {
            return IMMORTAL_REFCNT;
        }
        assert (refCount & 0xFFFFFFFF00000000L) == 0 : String.format("suspicious refcnt value during managed adjustment for %016x (%d %016x + %d)\n", pointer, refCount, refCount, refCntDelta);
        assert (refCount + refCntDelta) > 0 : String.format("refcnt reached zero during managed adjustment for %016x (%d %016x + %d)\n", pointer, refCount, refCount, refCntDelta);

        LOGGER.finest(() -> PythonUtils.formatJString("addNativeRefCount %x %x %d + %d", pointer, refCount, refCount, refCntDelta));

        UNSAFE.putLong(pointer + TP_REFCNT_OFFSET, refCount + refCntDelta);
        return refCount + refCntDelta;
    }

    public static long subNativeRefCount(long pointer, long refCntDelta) {
        assert PythonContext.get(null).isNativeAccessAllowed();
        assert PythonContext.get(null).ownsGil();
        long refCount = UNSAFE.getLong(pointer + TP_REFCNT_OFFSET);
        if (refCount == IMMORTAL_REFCNT) {
            return IMMORTAL_REFCNT;
        }
        assert (refCount & 0xFFFFFFFF00000000L) == 0 : String.format("suspicious refcnt value during managed adjustment for %016x (%d %016x - %d)\n", pointer, refCount, refCount, refCntDelta);
        assert (refCount - refCntDelta) >= 0 : String.format("refcnt below zero during managed adjustment for %016x (%d %016x - %d)\n", pointer, refCount, refCount, refCntDelta);

        LOGGER.finest(() -> PythonUtils.formatJString("subNativeRefCount %x %x %d + %d", pointer, refCount, refCount, refCntDelta));

        UNSAFE.putLong(pointer + TP_REFCNT_OFFSET, refCount - refCntDelta);
        return refCount - refCntDelta;
    }

    public static long readNativeRefCount(long pointer) {
        assert PythonContext.get(null).isNativeAccessAllowed();
        assert PythonContext.get(null).ownsGil();
        long refCount = UNSAFE.getLong(pointer + TP_REFCNT_OFFSET);
        assert refCount == IMMORTAL_REFCNT || (refCount & 0xFFFFFFFF00000000L) == 0 : String.format("suspicious refcnt value for %016x (%d %016x)\n", pointer, refCount, refCount);
        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.finest(PythonUtils.formatJString("readNativeRefCount(%x) = %d (%x)", pointer, refCount, refCount));
        }
        return refCount;
    }

    public static void writeNativeRefCount(long pointer, long newValue) {
        assert PythonContext.get(null).isNativeAccessAllowed();
        assert PythonContext.get(null).ownsGil();
        assert newValue > 0 : PythonUtils.formatJString("refcnt value to write below zero for %016x (%d %016x)\n", pointer, newValue, newValue);
        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.finest(PythonUtils.formatJString("writeNativeRefCount(%x, %d (%x))", pointer, newValue, newValue));
        }
        UNSAFE.putLong(pointer + TP_REFCNT_OFFSET, newValue);
    }

    private static PythonAbstractNativeObject createAbstractNativeObject(HandleContext handleContext, boolean transfer, long pointer) {

        pollReferenceQueue();
        PythonAbstractNativeObject result = new PythonAbstractNativeObject(pointer);
        long refCntDelta = MANAGED_REFCNT - (transfer ? 1 : 0);
        /*
         * Some APIs might be called from tp_dealloc/tp_del/tp_finalize where the refcount is 0. In
         * that case we don't want to create a new reference, since that would resurrect the object
         * and we would end up deallocating it twice.
         */
        long refCount = addNativeRefCount(pointer, refCntDelta, true);
        if (refCount > 0) {
            NativeObjectReference ref = new NativeObjectReference(handleContext, result, pointer);
            nativeLookupPut(getContext(), pointer, ref);
        } else if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine(PythonUtils.formatJString("createAbstractNativeObject: creating PythonAbstractNativeObject for a dying object (refcount 0): 0x%x", pointer));
        }

        return result;
    }

    @TruffleBoundary
    public static boolean isBackendPointerObject(Object obj) {
        return obj != null && (obj.getClass().toString().contains("LLVMPointerImpl") || obj.getClass().toString().contains("NFIPointer") || obj.getClass().toString().contains("NativePointer"));
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class NativePtrToPythonWrapperNode extends Node {

        public abstract Object execute(Node inliningTarget, long ptr, boolean strict);

        @Specialization
        static Object doGeneric(Node inliningTarget, long pointer, boolean strict,
                        @Cached InlinedConditionProfile isNullProfile,
                        @Cached InlinedConditionProfile isNativeProfile,
                        @Cached InlinedExactClassProfile nativeWrapperProfile,
                        @Cached InlinedConditionProfile isHandleSpaceProfile) {
            if (isNullProfile.profile(inliningTarget, pointer == 0)) {
                return null;
            }
            PythonContext pythonContext = PythonContext.get(inliningTarget);
            HandleContext nativeContext = pythonContext.nativeContext;
            assert pythonContext.ownsGil();
            if (isHandleSpaceProfile.profile(inliningTarget, HandlePointerConverter.pointsToPyHandleSpace(pointer))) {
                if (HandlePointerConverter.pointsToPyIntHandle(pointer)) {
                    throw CompilerDirectives.shouldNotReachHere("not implemented NativePtrToPythonWrapperNode int");
                } else if (HandlePointerConverter.pointsToPyFloatHandle(pointer)) {
                    throw CompilerDirectives.shouldNotReachHere("not implemented NativePtrToPythonWrapperNode float");
                }
                int idx = readIntField(HandlePointerConverter.pointerToStub(pointer), CFields.GraalPyObject__handle_table_index);
                PythonAbstractObject wrapper;
                Object reference = nativeStubLookupGet(nativeContext, pointer, idx);
                if (reference instanceof PythonAbstractObject) {
                    wrapper = (PythonAbstractObject) reference;
                } else {
                    assert reference == null || reference instanceof PythonObjectReference;
                    wrapper = reference == null ? null : ((PythonObjectReference) reference).get();
                }
                if (strict && wrapper == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw CompilerDirectives.shouldNotReachHere("reference was collected: " + Long.toHexString(pointer));
                }
                return wrapper;
            } else {
                IdReference<?> lookup = nativeLookupGet(nativeContext, pointer);
                if (isNativeProfile.profile(inliningTarget, lookup != null)) {
                    Object ref = lookup.get();
                    if (strict && ref == null) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        throw CompilerDirectives.shouldNotReachHere("reference was collected: " + Long.toHexString(pointer));
                    }
                    Object profiled = nativeWrapperProfile.profile(inliningTarget, ref);
                    if (profiled instanceof PythonAbstractObject pythonAbstractObject) {
                        return pythonAbstractObject;
                    }
                }
                return null;
            }
        }
    }

    /**
     * Adjusts the native wrapper's reference to be weak (if {@code refCount <= MANAGED_REFCNT}) or
     * to be strong (if {@code refCount > MANAGED_REFCNT}) if there is a reference. This node should
     * be called at appropriate points in the program, e.g., it should be called from native code if
     * the refcount falls below {@link PythonObject#MANAGED_REFCNT}.
     *
     * Additionally, if the reference to a wrapper will be made weak and the wrapper takes part in
     * the Python GC and is currently tracked, it will be removed from the GC list. This is done to
     * reduce the GC list size and avoid repeated upcalls to ensure that a
     * {@link PythonObjectReference} is weak.
     */
    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class UpdateStrongRefNode extends Node {

        public final void execute(Node inliningTarget, PythonObject pythonObject, long refCount) {
            execute(inliningTarget, pythonObject, refCount > MANAGED_REFCNT, false, false);
        }

        public final void execute(Node inliningTarget, PythonObject pythonObject, long refCount, boolean release) {
            execute(inliningTarget, pythonObject, refCount > MANAGED_REFCNT, release, false);
        }

        /**
         * Makes the handle table reference of the given wrapper weak but keeps the native object
         * stub in the GC list (if currently contained in any). The only valid use case for this
         * method is when iterating over all objects of a GC list, calling this method on each
         * object and in the end, dropping the whole GC list.
         */
        public final void clearStrongRefButKeepInGCList(Node inliningTarget, PythonObject pythonObject) {
            execute(inliningTarget, pythonObject, false, false, true);
        }

        public abstract void execute(Node inliningTarget, PythonObject pythonObject, boolean setStrong, boolean release, boolean keepInGcList);

        @Specialization
        static void doGeneric(Node inliningTarget, PythonObject pythonObject, boolean setStrong, boolean release, boolean keepInGcList,
                        @Cached InlinedConditionProfile hasRefProfile,
                        @Cached PyObjectGCTrackNode gcTrackNode,
                        @Cached GCListRemoveNode gcListRemoveNode,
                        @Cached InlinedConditionProfile isGcProfile,
                        @Cached GetClassNode getClassNode,
                        @Cached(inline = false) GetTypeFlagsNode getTypeFlagsNode) {
            assert CompilerDirectives.isPartialEvaluationConstant(keepInGcList);

            /*
             * There are two cases: (1) the pythonObject has a PythonObjectReference, and (2)
             * doesn't have one. In case of (2), the object was strongly referenced so far and we
             * may now need to introduce a weak reference.
             */
            long taggedPointer = pythonObject.getNativePointer();
            PythonObjectReference ref;
            if (hasRefProfile.profile(inliningTarget, (ref = pythonObject.ref) != null)) {
                assert ref.pointer == taggedPointer;
                if (setStrong && !ref.isStrongReference()) {
                    ref.setStrongReference(pythonObject);
                    if (ref.gc) {
                        gcTrackNode.executeOp(inliningTarget, taggedPointer);
                    }
                } else if (!setStrong && ref.isStrongReference()) {
                    ref.setStrongReference(null);
                }
            } else if (!setStrong) {
                // no PythonObjectReference in the handle table -> reference is strong

                assert pythonObject.ref == null;
                HandleContext handleContext = PythonContext.get(inliningTarget).nativeContext;
                long untaggedPointer = HandlePointerConverter.pointerToStub(taggedPointer);
                int idx = readIntField(untaggedPointer, CFields.GraalPyObject__handle_table_index);
                Object type = getClassNode.execute(inliningTarget, pythonObject);
                boolean gc = (getTypeFlagsNode.execute(type) & TypeFlags.HAVE_GC) != 0;

                /*
                 * At this point, we would commonly expect that 'pythonObject.getRefCount() ==
                 * MANAGED_REFCNT'. However, in order to break reference cycles with managed
                 * objects, we make references weak even if that is not the case. So, for all non-gc
                 * objects, we strongly expect MANAGED_REFCNT.
                 */
                assert gc || pythonObject.getRefCount() == MANAGED_REFCNT;

                if (release) {
                    writeIntField(untaggedPointer, CFields.GraalPyObject__handle_table_index, 0);
                    pythonObject.clearNativePointer();
                    Object removed = CApiTransitions.nativeStubLookupRemove(handleContext, idx);
                    assert pythonObject == removed;
                    freeNativeStub(taggedPointer, isGcProfile.profile(inliningTarget, gc));
                } else {
                    /*
                     * The reference should be weak but we may not release the native object stub.
                     * We need to create a PythonObjectReference.
                     */
                    PythonObjectReference pythonObjectReference = PythonObjectReference.createStub(handleContext, pythonObject, false, taggedPointer, idx, gc);
                    nativeStubLookupReplaceByWeak(handleContext, idx, pythonObjectReference, taggedPointer);

                    /*
                     * As soon as the reference is made weak, we remove it from the GC list because
                     * there are ways to iterate a GC list (e.g. 'PyUnstable_GC_VisitObjects') and
                     * while doing so, the objects may be accessed. Since weakly referenced objects
                     * may die any time, this could lead to dangling pointers being used.
                     */
                    if (!keepInGcList && gc) {
                        gcListRemoveNode.executeOp(inliningTarget, pythonObject.getNativePointer());
                    }
                }
            }
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class UpdateHandleTableReferenceNode extends Node {

        public final void execute(Node inliningTarget, HandleContext handleContext, long pointer, int handleTableIndex, long refCount) {
            execute(inliningTarget, handleContext, pointer, handleTableIndex, refCount > MANAGED_REFCNT, false);
        }

        public final void execute(Node inliningTarget, HandleContext handleContext, long pointer, int handleTableIndex, long refCount, boolean release) {
            execute(inliningTarget, handleContext, pointer, handleTableIndex, refCount > MANAGED_REFCNT, release);
        }

        public final void clearStrongRef(Node inliningTarget, HandleContext handleContext, long pointer, int handleTableIndex) {
            execute(inliningTarget, handleContext, pointer, handleTableIndex, false, false);
        }

        public abstract void execute(Node inliningTarget, HandleContext handleContext, long pointer, int handleTableIndex, boolean setStrong, boolean release);

        @Specialization
        static void doGeneric(Node inliningTarget, HandleContext handleContext, long pointer, int handleTableIndex, boolean setStrong, boolean release,
                        @Cached InlinedBranchProfile hasWeakRef,
                        @Cached PyObjectGCTrackNode gcTrackNode,
                        @Cached InlinedConditionProfile isGcProfile,
                        @Cached GetPythonObjectClassNode getClassNode,
                        @Cached(inline = false) GetTypeFlagsNode getTypeFlagsNode) {
            /*
             * There are two cases: (1) the pythonObject has a PythonObjectReference, and (2)
             * doesn't have one. In case of (2), the object was strongly referenced so far and we
             * may now need to introduce a weak reference.
             */
            assert HandlePointerConverter.pointsToPyHandleSpace(pointer);
            assert !HandlePointerConverter.pointsToPyIntHandle(pointer);
            assert !HandlePointerConverter.pointsToPyFloatHandle(pointer);

            boolean isLoggable = LOGGER.isLoggable(Level.FINER);

            Object ref = nativeStubLookupGet(handleContext, pointer, handleTableIndex);
            if (ref instanceof PythonObjectReference pythonObjectReference) {
                hasWeakRef.enter(inliningTarget);
                assert pythonObjectReference.pointer == pointer;
                if (setStrong && !pythonObjectReference.isStrongReference()) {
                    PythonObject pythonObject = pythonObjectReference.get();
                    if (isLoggable) {
                        logWeakToStrong(pointer, handleTableIndex, pythonObject);
                    }
                    if (pythonObject == null) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        throw CompilerDirectives.shouldNotReachHere("reference was collected: 0x" + Long.toHexString(pointer));
                    }
                    pythonObjectReference.setStrongReference(pythonObject);
                    if (pythonObjectReference.gc) {
                        gcTrackNode.executeOp(inliningTarget, pointer);
                    }
                } else if (!setStrong && pythonObjectReference.isStrongReference()) {
                    if (isLoggable) {
                        logStrongToWeak(pointer, handleTableIndex, pythonObjectReference.strongReference);
                    }
                    pythonObjectReference.setStrongReference(null);
                }
            } else if (!setStrong) {
                // no PythonObjectReference in the handle table -> reference is strong

                /*
                 * At this point, it must be a PythonObject because all PythonAbstractObjects that
                 * are not PythonObject (e.g. PEllipsis and such) are immortal and cannot be made
                 * weak.
                 */
                assert ref instanceof PythonObject;
                PythonObject pythonObject = (PythonObject) ref;

                if (isLoggable) {
                    logStrongToWeak(pointer, handleTableIndex, pythonObject);
                }

                Object type = getClassNode.execute(inliningTarget, pythonObject);
                boolean gc = (getTypeFlagsNode.execute(type) & TypeFlags.HAVE_GC) != 0;

                /*
                 * At this point, we would commonly expect that 'pythonObject.getRefCount() ==
                 * MANAGED_REFCNT'. However, in order to break reference cycles with managed
                 * objects, we make references weak even if that is not the case. So, for all non-gc
                 * objects, we strongly expect MANAGED_REFCNT.
                 */
                assert gc || pythonObject.getRefCount() == MANAGED_REFCNT;

                if (release) {
                    // clear all links between the managed and the native companion object
                    writeIntField(HandlePointerConverter.pointerToStub(pointer), CFields.GraalPyObject__handle_table_index, 0);
                    pythonObject.clearNativePointer();
                    Object removed = CApiTransitions.nativeStubLookupRemove(handleContext, handleTableIndex);
                    assert pythonObject == removed;
                    freeNativeStub(pointer, isGcProfile.profile(inliningTarget, gc));
                } else {
                    /*
                     * The reference should be weak but we may not release the native object stub.
                     * We need to create a PythonObjectReference.
                     */
                    PythonObjectReference pythonObjectReference = PythonObjectReference.createStub(handleContext, pythonObject, false, pointer, handleTableIndex, gc);
                    nativeStubLookupReplaceByWeak(handleContext, handleTableIndex, pythonObjectReference, pointer);
                }
            }
        }

        @TruffleBoundary
        private static void logStrongToWeak(long pointer, int handleTableIndex, PythonObject referent) {
            LOGGER.finer(String.format("reference 0x%x at index %d (object: %s): strong -> weak",
                            pointer, handleTableIndex, referent));
        }

        @TruffleBoundary
        private static void logWeakToStrong(long pointer, int handleTableIndex, PythonObject referent) {
            LOGGER.finer(String.format("reference 0x%x at index %d (object: %s): weak -> strong",
                            pointer, handleTableIndex, referent));
        }
    }
}
