/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapper.PythonAbstractObjectNativeWrapper.IMMORTAL_REFCNT;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiGuards;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.FromCharPointerNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol;
import com.oracle.graal.python.builtins.objects.cext.capi.PrimitiveNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapper.PythonAbstractObjectNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.TruffleObjectNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitionsFactory.FirstToNativeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitionsFactory.NativePtrToPythonNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitionsFactory.NativeToPythonNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitionsFactory.NativeToPythonStealingNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitionsFactory.PythonToNativeNewRefNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitionsFactory.PythonToNativeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.CoerceNativePointerToLongNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtToJavaNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.common.HandleStack;
import com.oracle.graal.python.builtins.objects.cext.common.NativePointer;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.AllocateNode;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess.FreeNode;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructs;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.DescriptorDeleteMarker;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.sequence.storage.NativeSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage.ListStorageType;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
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

    private CApiTransitions() {
    }

    // transfer: steal or borrow reference

    public static final class HandleContext {
        private static final int DEFAULT_CAPACITY = 16;

        /** Threshold used to switch from exponential to linear growth. */
        private static final int LINEAR_THRESHOLD = 1024 * 1024 / Integer.BYTES;

        public HandleContext(boolean useShadowTable) {
            nativeStubLookupShadowTable = useShadowTable ? new HashMap<>() : null;
            nativeStubLookup = new PythonObjectReference[DEFAULT_CAPACITY];
            nativeStubLookupFreeStack = new HandleStack(DEFAULT_CAPACITY);
            // Never use 'handleTableIndex == 0' to avoid that zeroed memory
            // accidentally maps to some valid object.
            nativeStubLookupFreeStack.pushRange(1, DEFAULT_CAPACITY);
        }

        public final NativeObjectReferenceArrayWrapper referencesToBeFreed = new NativeObjectReferenceArrayWrapper();
        public final HashMap<Long, IdReference<?>> nativeLookup = new HashMap<>();
        public final ConcurrentHashMap<Long, Long> nativeWeakRef = new ConcurrentHashMap<>();
        public final WeakHashMap<Object, WeakReference<Object>> managedNativeLookup = new WeakHashMap<>();

        private final HashMap<Long, PythonObjectReference> nativeStubLookupShadowTable;
        public PythonObjectReference[] nativeStubLookup;
        public final HandleStack nativeStubLookupFreeStack;

        public final Set<NativeStorageReference> nativeStorageReferences = new HashSet<>();

        public final ReferenceQueue<Object> referenceQueue = new ReferenceQueue<>();

        volatile boolean referenceQueuePollActive = false;

        @TruffleBoundary
        static <T> T putShadowTable(HashMap<Long, T> table, long pointer, T ref) {
            return table.put(pointer, ref);
        }

        @TruffleBoundary
        static <T> T removeShadowTable(HashMap<Long, T> table, long pointer) {
            return table.remove(pointer);
        }

        @TruffleBoundary
        static <T> T getShadowTable(HashMap<Long, T> table, long pointer) {
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
    public static final class PythonObjectReference extends IdReference<PythonNativeWrapper> {

        /**
         * This reference forces the wrapper to remain alive, and can be set to null when the
         * refcount falls to {@link PythonAbstractObjectNativeWrapper#MANAGED_REFCNT}.
         */
        private PythonNativeWrapper strongReference;
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
         * Indicates if the native memory {@link #pointer} should be freed (using {@link FreeNode})
         * if this reference was enqueued because the referent was collected. For example, this will
         * be {@code true} if the referent is
         * {@link com.oracle.graal.python.builtins.objects.cext.capi.PythonClassNativeWrapper} and
         * the class native replacement was allocated on the heap (usually a heap type). It will be
         * {@code false} if the class wraps a static type.
         */
        private final boolean freeAtCollection;

        private PythonObjectReference(HandleContext handleContext, PythonNativeWrapper referent, boolean strong, long pointer, int handleTableIndex, boolean freeAtCollection) {
            super(handleContext, referent);
            this.pointer = pointer;
            this.strongReference = strong ? referent : null;
            referent.ref = this;
            this.handleTableIndex = handleTableIndex;
            this.freeAtCollection = freeAtCollection;
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine(PythonUtils.formatJString("new %s", toString()));
            }
        }

        static PythonObjectReference create(HandleContext handleContext, PythonAbstractObjectNativeWrapper referent, boolean strong, long pointer, int idx) {
            assert HandlePointerConverter.pointsToPyHandleSpace(pointer);
            return new PythonObjectReference(handleContext, referent, strong, pointer, idx, true);
        }

        static PythonObjectReference create(HandleContext handleContext, PythonNativeWrapper referent, long pointer, boolean freeAtCollection) {
            return new PythonObjectReference(handleContext, referent, true, pointer, -1, freeAtCollection);
        }

        public boolean isStrongReference() {
            return strongReference != null;
        }

        public void setStrongReference(PythonNativeWrapper wrapper) {
            strongReference = wrapper;
        }

        public int getHandleTableIndex() {
            return handleTableIndex;
        }

        public boolean isFreeAtCollection() {
            return freeAtCollection;
        }

        @Override
        @TruffleBoundary
        public String toString() {
            String type = strongReference != null ? "strong" : "weak";
            PythonNativeWrapper referent = get();
            return String.format("PythonObjectReference<0x%x,%s,%s,id=%d>", pointer, type, referent != null ? referent : "freed", handleTableIndex);
        }
    }

    /**
     * A weak and unique reference to a native object (not directly the pointer but the
     * {@link PythonAbstractNativeObject} wrapper).
     */
    public static final class NativeObjectReference extends IdReference<PythonAbstractNativeObject> {

        final Object object;
        final long pointer;

        public NativeObjectReference(HandleContext handleContext, PythonAbstractNativeObject referent, long pointer) {
            super(handleContext, referent);
            this.object = referent.object;
            this.pointer = pointer;
            referent.ref = this;
            assert (pointer & 7) == 0;
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine(PythonUtils.formatJString("new %s", toString()));
            }
        }

        @Override
        @TruffleBoundary
        public String toString() {
            PythonAbstractNativeObject referent = get();
            return String.format("NativeObjectReference<0x%x,%s>", pointer, referent != null ? referent : "freed");
        }
    }

    public static final class NativeStorageReference extends IdReference<NativeSequenceStorage> {
        private final ListStorageType type;
        private Object ptr;
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

        public void setPtr(Object ptr) {
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

    @TruffleBoundary
    @SuppressWarnings("try")
    public static void pollReferenceQueue() {
        HandleContext context = getContext();
        if (!context.referenceQueuePollActive) {
            try (GilNode.UncachedAcquire ignored = GilNode.uncachedAcquire()) {
                ReferenceQueue<Object> queue = context.referenceQueue;
                int count = 0;
                long start = 0;
                NativeObjectReferenceArrayWrapper referencesToBeFreed = context.referencesToBeFreed;
                while (true) {
                    Object entry = queue.poll();
                    if (entry == null) {
                        if (count > 0) {
                            assert context.referenceQueuePollActive;
                            releaseNativeObjects(referencesToBeFreed);
                            context.referenceQueuePollActive = false;
                            LOGGER.fine("collected " + count + " references from native reference queue in " + ((System.nanoTime() - start) / 1000000) + "ms");
                        }
                        return;
                    }
                    if (count == 0) {
                        assert !context.referenceQueuePollActive;
                        context.referenceQueuePollActive = true;
                        start = System.nanoTime();
                    } else {
                        assert context.referenceQueuePollActive;
                    }
                    count++;
                    if (entry instanceof PythonObjectReference reference) {
                        LOGGER.fine(() -> PythonUtils.formatJString("releasing %s", reference.toString()));
                        if (HandlePointerConverter.pointsToPyHandleSpace(reference.pointer)) {
                            assert nativeStubLookupGet(context, reference.pointer, reference.handleTableIndex) != null : Long.toHexString(reference.pointer);
                            nativeStubLookupRemove(context, reference);
                            /*
                             * We may only free native object stubs if their reference count is
                             * zero. We cannot free other structs (e.g. PyDateTime_CAPI) because we
                             * don't know if they are still used from native code. Those must be
                             * free'd at context finalization.
                             */
                            long stubPointer = HandlePointerConverter.pointerToStub(reference.pointer);
                            if (subNativeRefCount(stubPointer, PythonAbstractObjectNativeWrapper.MANAGED_REFCNT) == 0) {
                                freeNativeStub(stubPointer);
                            } else {
                                /*
                                 * In this case, the object is no longer referenced from managed but
                                 * still from native code (since the reference count is greater 0).
                                 * We therefore need to overwrite the 'CFields.GraalPyObject__id'
                                 * field because there may be referenced from managed in the future
                                 * and then we would incorrectly reuse the ID.
                                 */
                                CStructAccess.WriteIntNode.writeUncached(reference.pointer, CFields.GraalPyObject__handle_table_index, 0);
                            }
                        } else {
                            assert nativeLookupGet(context, reference.pointer) != null : Long.toHexString(reference.pointer);
                            nativeLookupRemove(context, reference.pointer);
                            if (reference.freeAtCollection) {
                                freeNativeStruct(reference);
                            }
                        }
                    } else if (entry instanceof NativeObjectReference reference) {
                        nativeLookupRemove(context, reference.pointer);
                        processNativeObjectReference(reference, referencesToBeFreed);
                    } else if (entry instanceof NativeStorageReference reference) {
                        context.nativeStorageReferences.remove(reference);
                        processNativeStorageReference(reference);
                    }
                }
            }
        }
    }

    /**
     * Subtracts {@link PythonAbstractObjectNativeWrapper#MANAGED_REFCNT} from the object's
     * reference count and if it is then {@code 0}, it puts the pointer into the list of references
     * to be freed. Therefore, this method neither frees any native memory nor runs any object
     * destructor (guest code).
     */
    private static void processNativeObjectReference(NativeObjectReference reference, NativeObjectReferenceArrayWrapper referencesToBeFreed) {
        LOGGER.fine(() -> PythonUtils.formatJString("releasing %s", reference.toString()));
        if (subNativeRefCount(reference.pointer, PythonAbstractObjectNativeWrapper.MANAGED_REFCNT) == 0) {
            referencesToBeFreed.add(reference.pointer);
        }
    }

    /**
     * Calls function {@link NativeCAPISymbol#FUN_PY_TRUFFLE_OBJECT_ARRAY_RELEASE} to decrement the
     * reference counts of the stored objects by one and frees the native array. Therefore, this
     * operation may run guest code because if the stored objects where exclusively owned by this
     * storage, then they will be freed by calling the element object's destructor.
     */
    private static void processNativeStorageReference(NativeStorageReference reference) {
        if (reference.type == ListStorageType.Generic) {
            PCallCapiFunction.callUncached(NativeCAPISymbol.FUN_PY_TRUFFLE_OBJECT_ARRAY_RELEASE, reference.ptr, reference.size);
        }
        freeNativeStorage(reference);
    }

    /**
     * Deallocates all objects in the given collection by calling {@code _Py_Dealloc} for each
     * element. This method may therefore run arbitrary guest code and strictly requires the GIL to
     * be held at the time of invocation.
     */
    private static void releaseNativeObjects(NativeObjectReferenceArrayWrapper referencesToBeFreed) {
        if (!referencesToBeFreed.isEmpty()) {
            /*
             * This needs the GIL because this will call the native objects' destructors which can
             * be arbitrary guest code.
             */
            assert PythonContext.get(null).ownsGil();
            LOGGER.fine(() -> PythonUtils.formatJString("releasing %d NativeObjectReference instances", referencesToBeFreed.getArraySize()));
            Object array = AllocateNode.allocUncached(referencesToBeFreed.getArraySize() * Long.BYTES);
            CStructAccess.WriteLongNode.getUncached().writeLongArray(array, referencesToBeFreed.getArray(), (int) referencesToBeFreed.getArraySize(), 0, 0);
            PCallCapiFunction.callUncached(NativeCAPISymbol.FUN_BULK_DEALLOC, array, referencesToBeFreed.getArraySize());
            FreeNode.executeUncached(array);
            referencesToBeFreed.reset();
        }
    }

    public static void freeClassReplacements(HandleContext handleContext) {
        assert PythonContext.get(null).ownsGil();
        handleContext.nativeLookup.forEach((l, ref) -> {
            if (ref instanceof PythonObjectReference reference) {
                // We don't expect references to wrappers that would have a native object stub.
                assert reference.handleTableIndex == -1;
                /*
                 * The ref may denote: (a) class wrappers, where some of them are backed by static
                 * native memory and some of them were allocated in heap, and (b) struct wrappers,
                 * which may be freed manually in a separate step.
                 */
                if (reference.freeAtCollection) {
                    freeNativeStruct(reference);
                }
            }
        });
        handleContext.nativeLookup.clear();
    }

    public static void disableReferenceQueuePolling(HandleContext handleContext) {
        handleContext.referenceQueuePollActive = true;
    }

    private static void freeNativeStub(long stubPointer) {
        LOGGER.fine(() -> PythonUtils.formatJString("releasing native object stub 0x%x", stubPointer));
        FreeNode.executeUncached(stubPointer);
    }

    private static void freeNativeStub(PythonObjectReference ref) {
        assert HandlePointerConverter.pointsToPyHandleSpace(ref.pointer);
        freeNativeStub(HandlePointerConverter.pointerToStub(ref.pointer));
    }

    private static void freeNativeStruct(PythonObjectReference ref) {
        assert ref.handleTableIndex == -1;
        assert ref.freeAtCollection;
        LOGGER.fine(() -> PythonUtils.formatJString("releasing %s", ref.toString()));
        FreeNode.executeUncached(ref.pointer);
    }

    private static void freeNativeStorage(NativeStorageReference ref) {
        LOGGER.fine(() -> PythonUtils.formatJString("releasing %s", ref.toString()));
        FreeNode.executeUncached(ref.ptr);
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
        for (PythonObjectReference ref : handleContext.nativeStubLookup) {
            if (ref != null) {
                nativeStubLookupRemove(handleContext, ref);
                freeNativeStub(ref);
            }
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
            Object array = CStructAccess.AllocateNode.allocUncached((long) len * Long.BYTES);
            try {
                CStructAccess.WritePointerNode.getUncached().writePointerArray(array, ptrArray, len, 0, 0);
                CExtNodes.PCallCapiFunction.callUncached(NativeCAPISymbol.FUN_SHUTDOWN_BULK_DEALLOC, array, len);
            } finally {
                CStructAccess.FreeNode.executeUncached(array);
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

    public static PythonObjectReference nativeStubLookupGet(HandleContext context, long pointer, int idx) {
        if (idx <= 0) {
            if (PythonContext.DEBUG_CAPI && HandleContext.getShadowTable(context.nativeStubLookupShadowTable, pointer) != null) {
                throw CompilerDirectives.shouldNotReachHere();
            }
            return null;
        }
        PythonObjectReference result = context.nativeStubLookup[idx];
        if (PythonContext.DEBUG_CAPI && HandleContext.getShadowTable(context.nativeStubLookupShadowTable, pointer) != result) {
            throw CompilerDirectives.shouldNotReachHere();
        }
        return result;
    }

    /**
     * Reserves a free slot in the handle table that can later be used to store a
     * {@link PythonObjectReference} using
     * {@link #nativeStubLookupPut(HandleContext, PythonObjectReference)}. If the handle table is
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

    private static int nativeStubLookupPut(HandleContext context, PythonObjectReference value) {
        assert value.handleTableIndex > 0;
        final int idx = value.handleTableIndex;
        assert context.nativeStubLookup[idx] == null || context.nativeStubLookup[idx] == value;
        context.nativeStubLookup[idx] = value;
        if (PythonContext.DEBUG_CAPI) {
            PythonObjectReference prev = HandleContext.putShadowTable(context.nativeStubLookupShadowTable, value.pointer, value);
            if (prev != null && prev != value) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }
        return idx;
    }

    public static PythonObjectReference nativeStubLookupRemove(HandleContext context, PythonObjectReference ref) {
        assert ref.handleTableIndex > 0;
        final int idx = ref.handleTableIndex;
        PythonObjectReference result = context.nativeStubLookup[idx];
        context.nativeStubLookup[idx] = null;
        context.nativeStubLookupFreeStack.push(idx);
        if (PythonContext.DEBUG_CAPI && HandleContext.removeShadowTable(context.nativeStubLookupShadowTable, ref.pointer) != result) {
            throw CompilerDirectives.shouldNotReachHere();
        }
        return result;
    }

    public static final class HandlePointerConverter {

        private static final long HANDLE_BASE = 0x8000_0000_0000_0000L;

        /**
         * Some libraries (notably cffi) do pointer tagging and therefore assume aligned pointers
         * (aligned to 8 bytes). This means, the three LSBs need to be 0.
         */
        private static final int POINTER_ALIGNMENT_SHIFT = 3;
        private static final long POINTER_ALIGNMENT_MASK = (1L << POINTER_ALIGNMENT_SHIFT) - 1L;

        public static long stubToPointer(long stubPointer) {
            assert (stubPointer & POINTER_ALIGNMENT_MASK) == 0;
            return stubPointer | HANDLE_BASE;
        }

        public static long pointerToStub(long pointer) {
            assert (pointer & ~HANDLE_BASE & POINTER_ALIGNMENT_MASK) == 0;
            return pointer & ~HANDLE_BASE;
        }

        public static boolean pointsToPyHandleSpace(long pointer) {
            return (pointer & HANDLE_BASE) != 0;
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class FirstToNativeNode extends Node {

        public static long executeUncached(PythonAbstractObjectNativeWrapper wrapper, boolean immortal) {
            return FirstToNativeNodeGen.getUncached().execute(null, wrapper, immortal);
        }

        public final long execute(Node inliningTarget, PythonAbstractObjectNativeWrapper wrapper) {
            return execute(inliningTarget, wrapper, false);
        }

        public abstract long execute(Node inliningTarget, PythonAbstractObjectNativeWrapper wrapper, boolean immortal);

        @Specialization
        static long doGeneric(Node inliningTarget, PythonAbstractObjectNativeWrapper wrapper, boolean immortal,
                        @Cached(inline = false) GilNode gil,
                        @Cached(inline = false) CStructAccess.AllocateNode allocateNode,
                        @Cached(inline = false) CStructAccess.WriteLongNode writeLongNode,
                        @Cached(inline = false) CStructAccess.WritePointerNode writePointerNode,
                        @Cached(inline = false) CStructAccess.WriteObjectNewRefNode writeObjectNode,
                        @Cached(inline = false) CStructAccess.WriteIntNode writeIntNode,
                        @Cached InlinedConditionProfile isVarObjectProfile,
                        @Cached InlinedExactClassProfile wrapperProfile,
                        @Cached GetClassNode getClassNode,
                        @Cached CoerceNativePointerToLongNode coerceToLongNode) {

            boolean acquired = gil.acquire();
            try {
                log(wrapper);
                assert !(wrapper instanceof TruffleObjectNativeWrapper);
                pollReferenceQueue();

                long initialRefCount = immortal ? IMMORTAL_REFCNT : PythonAbstractObjectNativeWrapper.MANAGED_REFCNT;

                Object delegate = NativeToPythonNode.handleWrapper(inliningTarget, wrapperProfile, false, wrapper);
                Object type = getClassNode.execute(inliningTarget, delegate);

                // allocate a native stub object (C type: PyObject)
                boolean isTuple = isVarObjectProfile.profile(inliningTarget, delegate instanceof PTuple);
                Object nativeObjectStub = allocateNode.alloc(isTuple ? CStructs.GraalPyVarObject : CStructs.GraalPyObject);
                writeLongNode.write(nativeObjectStub, CFields.PyObject__ob_refcnt, initialRefCount);
                writeObjectNode.write(nativeObjectStub, CFields.PyObject__ob_type, type);
                if (isTuple) {
                    SequenceStorage sequenceStorage = ((PTuple) delegate).getSequenceStorage();
                    writeLongNode.write(nativeObjectStub, CFields.PyVarObject__ob_size, sequenceStorage.length());
                    Object obItemPtr = 0L;
                    if (sequenceStorage instanceof NativeSequenceStorage nativeSequenceStorage) {
                        obItemPtr = nativeSequenceStorage.getPtr();
                    }
                    writePointerNode.write(nativeObjectStub, CFields.GraalPyVarObject__ob_item, obItemPtr);
                }
                HandleContext handleContext = PythonContext.get(inliningTarget).nativeContext;
                long stubPointer = coerceToLongNode.execute(inliningTarget, nativeObjectStub);
                long taggedPointer = HandlePointerConverter.stubToPointer(stubPointer);
                int idx = nativeStubLookupReserve(handleContext);
                // We don't allow 'handleTableIndex == 0' to avoid that zeroed memory
                // accidentally maps to some valid object.
                assert idx > 0;
                writeIntNode.write(stubPointer, CFields.GraalPyObject__handle_table_index, idx);
                PythonObjectReference ref = PythonObjectReference.create(handleContext, wrapper, immortal, taggedPointer, idx);
                nativeStubLookupPut(handleContext, ref);

                return logResult(taggedPointer);
            } catch (OverflowException e) {
                /*
                 * The OverflowException may be thrown by 'nativeStubLookupReserve' and indicates
                 * that we cannot resize the handle table anymore. This essentially indicates a
                 * Python-level MemoryError.
                 */
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw PRaiseNode.raiseUncached(inliningTarget, PythonBuiltinClassType.MemoryError);
            } finally {
                gil.release(acquired);
            }
        }
    }

    /**
     * Creates a {@link PythonObjectReference} to {@code delegate} and connects that to the given
     * native {@code pointer} such that the {@code pointer} can be resolved to the {@code delegate}.
     * <p>
     * This is used in LLVM managed mode where we will not have real native pointers (i.e. addresses
     * pointing into off-heap memory) but managed pointers to objects emulating the native memory.
     * We still need to be able to resolve those managed pointers to our objects.
     * </p>
     */
    @TruffleBoundary
    @SuppressWarnings("try")
    public static void createReference(PythonNativeWrapper obj, long ptr, boolean freeAtCollection) {
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
                nativeLookupPut(context, ptr, PythonObjectReference.create(context, obj, ptr, freeAtCollection));
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
     * Resolves a native handle to the corresponding {@link PythonNativeWrapper}. This node assumes
     * that {@code pointer} points to handle space (i.e.
     * {@link HandlePointerConverter#pointsToPyHandleSpace(long)} is {@code true}) and essential
     * just looks up the handle in the table. It will additionally increment the reference count if
     * the wrapper is a subclass of {@link PythonAbstractObjectNativeWrapper}.
     */
    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class ResolveHandleNode extends Node {

        public abstract PythonNativeWrapper execute(Node inliningTarget, long pointer);

        @Specialization
        static PythonNativeWrapper doGeneric(Node inliningTarget, long pointer,
                        @Cached(inline = false) CStructAccess.ReadI32Node readI32Node,
                        @Cached InlinedExactClassProfile profile) {
            HandleContext nativeContext = PythonContext.get(inliningTarget).nativeContext;
            int idx = readI32Node.read(HandlePointerConverter.pointerToStub(pointer), CFields.GraalPyObject__handle_table_index);
            PythonObjectReference reference = nativeStubLookupGet(nativeContext, pointer, idx);
            PythonNativeWrapper wrapper = profile.profile(inliningTarget, reference.get());
            assert wrapper != null : "reference was collected: " + Long.toHexString(pointer);
            if (wrapper instanceof PythonAbstractObjectNativeWrapper objectNativeWrapper) {
                objectNativeWrapper.incRef();
            }
            return wrapper;
        }
    }

    @GenerateUncached
    @GenerateInline(false)
    public abstract static class CharPtrToPythonNode extends CExtToJavaNode {

        @Specialization
        static Object doForeign(Object value,
                        @Bind("this") Node inliningTarget,
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
                    PythonNativeWrapper obj = resolveHandleNode.execute(inliningTarget, pointer);
                    if (obj != null) {
                        return logResult(obj.getDelegate());
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
    @GenerateInline(false)
    @ImportStatic({CApiGuards.class, PGuards.class})
    public abstract static class PythonToNativeNode extends CExtToNativeNode {

        @TruffleBoundary
        public static Object executeUncached(Object obj) {
            return PythonToNativeNodeGen.getUncached().execute(obj);
        }

        protected boolean needsTransfer() {
            return false;
        }

        @Specialization
        Object doNative(PythonAbstractNativeObject obj,
                        @CachedLibrary(limit = "2") InteropLibrary lib) {
            if (needsTransfer() && getContext().isNativeAccessAllowed()) {
                long ptr = PythonUtils.coerceToLong(obj.getPtr(), lib);
                CApiTransitions.addNativeRefCount(ptr, 1);
            }
            return obj.getPtr();
        }

        @Specialization
        static Object doNativePointer(NativePointer obj) {
            return obj;
        }

        @Specialization(guards = "mapsToNull(obj)")
        Object doNoValue(@SuppressWarnings("unused") Object obj) {
            return getContext().getNativeNull();
        }

        static boolean mapsToNull(Object object) {
            return PGuards.isNoValue(object) || object instanceof DescriptorDeleteMarker;
        }

        static boolean isOther(Object obj) {
            return !(obj instanceof PythonAbstractNativeObject || obj instanceof NativePointer || mapsToNull(obj));
        }

        @Specialization(guards = "isOther(obj)")
        static Object doOther(Object obj,
                        @Bind("needsTransfer()") boolean needsTransfer,
                        @Bind("this") Node inliningTarget,
                        @Cached GetNativeWrapperNode getWrapper,
                        @Cached GetReplacementNode getReplacementNode,
                        @Cached InlinedConditionProfile isStrongProfile,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {
            CompilerAsserts.partialEvaluationConstant(needsTransfer);
            pollReferenceQueue();
            PythonNativeWrapper wrapper = getWrapper.execute(obj);

            Object replacement = getReplacementNode.execute(inliningTarget, wrapper);
            if (replacement != null) {
                return replacement;
            }

            assert PythonContext.get(inliningTarget).isNativeAccessAllowed();
            assert obj != PNone.NO_VALUE;
            if (!lib.isPointer(wrapper)) {
                lib.toNative(wrapper);
            }
            if (needsTransfer && wrapper instanceof PythonAbstractObjectNativeWrapper objectNativeWrapper) {
                // native part needs to decRef to release
                objectNativeWrapper.incRef();
                /*
                 * This creates a new reference to the object and the ownership is transferred to
                 * the C extension. Therefore, we need to make the reference strong such that we do
                 * not deallocate the object if it's no longer referenced in the interpreter. The
                 * interpreter will be notified by an upcall as soon as the object's refcount goes
                 * down to MANAGED_RECOUNT again.
                 */
                assert wrapper.ref != null;
                if (isStrongProfile.profile(inliningTarget, !objectNativeWrapper.ref.isStrongReference())) {
                    objectNativeWrapper.ref.setStrongReference(objectNativeWrapper);
                }
            }
            assert wrapper != null;
            return wrapper;
        }

        @NeverDefault
        public static PythonToNativeNode create() {
            return PythonToNativeNodeGen.create();
        }

        public static PythonToNativeNode getUncached() {
            return PythonToNativeNodeGen.getUncached();
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
    @GenerateUncached
    @GenerateInline(false)
    public abstract static class PythonToNativeNewRefNode extends PythonToNativeNode {

        @Specialization
        static Object dummy(@SuppressWarnings("unused") Void dummy) {
            // needed for DSL (GR-44728)
            throw CompilerDirectives.shouldNotReachHere();
        }

        @TruffleBoundary
        public static Object executeUncached(Object obj) {
            return PythonToNativeNewRefNodeGen.getUncached().execute(obj);
        }

        @Override
        protected final boolean needsTransfer() {
            return true;
        }

        @NeverDefault
        public static PythonToNativeNewRefNode create() {
            return PythonToNativeNewRefNodeGen.create();
        }

        public static PythonToNativeNewRefNode getUncached() {
            return PythonToNativeNewRefNodeGen.getUncached();
        }
    }

    @GenerateUncached
    @GenerateInline(false)
    @ImportStatic(CApiGuards.class)
    public abstract static class NativeToPythonNode extends CExtToJavaNode {

        public abstract Object execute(PythonNativeWrapper object);

        @TruffleBoundary
        public static Object executeUncached(Object obj) {
            return NativeToPythonNodeGen.getUncached().execute(obj);
        }

        protected boolean needsTransfer() {
            return false;
        }

        @Specialization
        static Object doWrapper(PythonNativeWrapper value,
                        @Bind("$node") Node inliningTarget,
                        @Exclusive @Cached InlinedExactClassProfile wrapperProfile) {
            return handleWrapper(inliningTarget, wrapperProfile, false, value);
        }

        @Specialization(guards = "!isNativeWrapper(value)", limit = "3")
        @SuppressWarnings({"truffle-static-method", "truffle-sharing"})
        Object doNonWrapper(Object value,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary("value") InteropLibrary interopLibrary,
                        @Cached CStructAccess.ReadI32Node readI32Node,
                        @Cached InlinedConditionProfile isNullProfile,
                        @Cached InlinedConditionProfile isZeroProfile,
                        @Cached InlinedConditionProfile createNativeProfile,
                        @Cached InlinedConditionProfile isNativeProfile,
                        @Cached InlinedConditionProfile isNativeWrapperProfile,
                        @Cached InlinedConditionProfile isHandleSpaceProfile,
                        @Exclusive @Cached InlinedExactClassProfile wrapperProfile) {
            assert !(value instanceof TruffleString);
            assert !(value instanceof PythonAbstractObject);
            assert !(value instanceof Number);

            // this is just a shortcut
            if (isNullProfile.profile(inliningTarget, interopLibrary.isNull(value))) {
                return PNone.NO_VALUE;
            }
            PythonNativeWrapper wrapper;

            PythonContext pythonContext = PythonContext.get(inliningTarget);
            HandleContext nativeContext = pythonContext.nativeContext;

            if (!interopLibrary.isPointer(value)) {
                return getManagedReference(value, nativeContext);
            }
            long pointer;
            try {
                pointer = interopLibrary.asPointer(value);
            } catch (final UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
            if (isZeroProfile.profile(inliningTarget, pointer == 0)) {
                return PNone.NO_VALUE;
            }
            assert pythonContext.ownsGil();
            if (isHandleSpaceProfile.profile(inliningTarget, HandlePointerConverter.pointsToPyHandleSpace(pointer))) {
                int idx = readI32Node.read(HandlePointerConverter.pointerToStub(pointer), CFields.GraalPyObject__handle_table_index);
                PythonObjectReference reference = nativeStubLookupGet(nativeContext, pointer, idx);
                if (reference == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw CompilerDirectives.shouldNotReachHere("reference was freed: " + Long.toHexString(pointer));
                }
                wrapper = reference.get();
                if (wrapper == null) {
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
                        return createAbstractNativeObject(nativeContext, value, needsTransfer(), pointer);
                    }
                    if (isNativeWrapperProfile.profile(inliningTarget, ref instanceof PythonNativeWrapper)) {
                        wrapper = (PythonNativeWrapper) ref;
                    } else {
                        PythonAbstractNativeObject result = (PythonAbstractNativeObject) ref;
                        if (needsTransfer()) {
                            addNativeRefCount(pointer, -1);
                        }
                        return result;
                    }
                } else {
                    return createAbstractNativeObject(nativeContext, value, needsTransfer(), pointer);
                }
            }
            return handleWrapper(inliningTarget, wrapperProfile, needsTransfer(), wrapper);
        }

        /**
         * Resolves a wrapper to its delegate and does appropriate reference count manipulation.
         *
         * @param node The inlining target for profiles.
         * @param wrapperProfile The wrapper class profile.
         * @param transfer Indicates if ownership of the reference is transferred to managed space.
         * @param wrapper The native wrapper to unwrap.
         * @return The Python value contained in the native wrapper.
         */
        static Object handleWrapper(Node node, InlinedExactClassProfile wrapperProfile, boolean transfer, PythonNativeWrapper wrapper) {
            PythonNativeWrapper profiledWrapper = wrapperProfile.profile(node, wrapper);
            if (transfer && profiledWrapper instanceof PythonAbstractObjectNativeWrapper objectNativeWrapper) {
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
                assert objectNativeWrapper.getRefCount() > PythonAbstractObjectNativeWrapper.MANAGED_REFCNT;
                objectNativeWrapper.decRef();
            }
            if (profiledWrapper instanceof PrimitiveNativeWrapper primitive) {
                if (primitive.isBool()) {
                    return primitive.getBool();
                } else if (primitive.isInt()) {
                    return primitive.getInt();
                } else if (primitive.isLong()) {
                    return primitive.getLong();
                } else if (primitive.isDouble()) {
                    return primitive.getDouble();
                } else {
                    throw CompilerDirectives.shouldNotReachHere();
                }
            } else {
                return wrapper.getDelegate();
            }
        }

        @TruffleBoundary
        private static Object getManagedReference(Object value, HandleContext nativeContext) {
            assert value.toString().startsWith("ManagedMemoryBlock");
            assert PythonContext.get(null).ownsGil();
            WeakReference<Object> ref = nativeContext.managedNativeLookup.computeIfAbsent(value, o -> new WeakReference<>(new PythonAbstractNativeObject(o)));
            Object result = ref.get();
            if (result == null) {
                // value is weak as well:
                nativeContext.managedNativeLookup.put(value, new WeakReference<>(result = new PythonAbstractNativeObject(value)));
            }
            return result;
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
    public abstract static class NativeToPythonStealingNode extends NativeToPythonNode {

        @Specialization
        static Object dummy(@SuppressWarnings("unused") Void dummy) {
            // needed for DSL (GR-44728)
            throw CompilerDirectives.shouldNotReachHere();
        }

        @TruffleBoundary
        public static Object executeUncached(Object obj) {
            return NativeToPythonStealingNodeGen.getUncached().execute(obj);
        }

        @Override
        protected final boolean needsTransfer() {
            return true;
        }

        @NeverDefault
        public static NativeToPythonStealingNode create() {
            return NativeToPythonStealingNodeGen.create();
        }

        public static NativeToPythonStealingNode getUncached() {
            return NativeToPythonStealingNodeGen.getUncached();
        }
    }

    @GenerateUncached
    @GenerateInline(false)
    @ImportStatic(CApiGuards.class)
    public abstract static class NativePtrToPythonNode extends PNodeWithContext {

        public abstract Object execute(long object, boolean stealing);

        @TruffleBoundary
        public static Object executeUncached(long object, boolean stealing) {
            return NativePtrToPythonNodeGen.getUncached().execute(object, stealing);
        }

        @Specialization
        @SuppressWarnings({"truffle-static-method", "truffle-sharing"})
        Object doNonWrapper(long pointer, boolean stealing,
                        @Bind("$node") Node inliningTarget,
                        @Cached CStructAccess.ReadI32Node readI32Node,
                        @Cached InlinedConditionProfile isZeroProfile,
                        @Cached InlinedConditionProfile createNativeProfile,
                        @Cached InlinedConditionProfile isNativeProfile,
                        @Cached InlinedConditionProfile isNativeWrapperProfile,
                        @Cached InlinedConditionProfile isHandleSpaceProfile,
                        @Cached InlinedExactClassProfile wrapperProfile) {

            CompilerAsserts.partialEvaluationConstant(stealing);
            PythonNativeWrapper wrapper;

            PythonContext pythonContext = PythonContext.get(inliningTarget);
            HandleContext nativeContext = pythonContext.nativeContext;

            if (isZeroProfile.profile(inliningTarget, pointer == 0)) {
                return PNone.NO_VALUE;
            }
            assert pythonContext.ownsGil();
            if (isHandleSpaceProfile.profile(inliningTarget, HandlePointerConverter.pointsToPyHandleSpace(pointer))) {
                int idx = readI32Node.read(HandlePointerConverter.pointerToStub(pointer), CFields.GraalPyObject__handle_table_index);
                PythonObjectReference reference = nativeStubLookupGet(nativeContext, pointer, idx);
                if (reference == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw CompilerDirectives.shouldNotReachHere("reference was freed: " + Long.toHexString(pointer));
                }
                wrapper = reference.get();
                if (wrapper == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw CompilerDirectives.shouldNotReachHere("reference was collected: " + Long.toHexString(pointer));
                }
            } else {
                IdReference<?> lookup = nativeLookupGet(nativeContext, pointer);
                if (isNativeProfile.profile(inliningTarget, lookup != null)) {
                    Object ref = lookup.get();
                    if (createNativeProfile.profile(inliningTarget, ref == null)) {
                        LOGGER.fine(() -> "re-creating collected PythonAbstractNativeObject reference" + Long.toHexString(pointer));
                        return createAbstractNativeObject(nativeContext, pointer, stealing, pointer);
                    }
                    if (isNativeWrapperProfile.profile(inliningTarget, ref instanceof PythonNativeWrapper)) {
                        wrapper = (PythonNativeWrapper) ref;
                    } else {
                        PythonAbstractNativeObject result = (PythonAbstractNativeObject) ref;
                        if (stealing) {
                            addNativeRefCount(pointer, -1);
                        }
                        return result;
                    }
                } else {
                    return createAbstractNativeObject(nativeContext, pointer, stealing, pointer);
                }
            }
            return NativeToPythonNode.handleWrapper(inliningTarget, wrapperProfile, stealing, wrapper);
        }
    }

    private static final Unsafe UNSAFE = PythonUtils.initUnsafe();
    private static final int TP_REFCNT_OFFSET = 0;

    private static long addNativeRefCount(long pointer, long refCntDelta) {
        assert PythonContext.get(null).isNativeAccessAllowed();
        long refCount = UNSAFE.getLong(pointer + TP_REFCNT_OFFSET);
        assert (refCount & 0xFFFFFFFF00000000L) == 0 : String.format("suspicious refcnt value during managed adjustment for %016x (%d %016x + %d)\n", pointer, refCount, refCount, refCntDelta);
        assert (refCount + refCntDelta) > 0 : String.format("refcnt reached zero during managed adjustment for %016x (%d %016x + %d)\n", pointer, refCount, refCount, refCntDelta);

        LOGGER.finest(() -> PythonUtils.formatJString("addNativeRefCount %x %x %d + %d", pointer, refCount, refCount, refCntDelta));

        UNSAFE.putLong(pointer + TP_REFCNT_OFFSET, refCount + refCntDelta);
        return refCount + refCntDelta;
    }

    private static long subNativeRefCount(long pointer, long refCntDelta) {
        assert PythonContext.get(null).isNativeAccessAllowed();
        long refCount = UNSAFE.getLong(pointer + TP_REFCNT_OFFSET);
        assert (refCount & 0xFFFFFFFF00000000L) == 0 : String.format("suspicious refcnt value during managed adjustment for %016x (%d %016x - %d)\n", pointer, refCount, refCount, refCntDelta);
        assert (refCount - refCntDelta) >= 0 : String.format("refcnt below zero during managed adjustment for %016x (%d %016x - %d)\n", pointer, refCount, refCount, refCntDelta);

        LOGGER.finest(() -> PythonUtils.formatJString("subNativeRefCount %x %x %d + %d", pointer, refCount, refCount, refCntDelta));

        UNSAFE.putLong(pointer + TP_REFCNT_OFFSET, refCount - refCntDelta);
        return refCount - refCntDelta;
    }

    public static long readNativeRefCount(long pointer) {
        assert PythonContext.get(null).isNativeAccessAllowed();
        long refCount = UNSAFE.getLong(pointer + TP_REFCNT_OFFSET);
        assert refCount == IMMORTAL_REFCNT || (refCount & 0xFFFFFFFF00000000L) == 0 : String.format("suspicious refcnt value for %016x (%d %016x)\n", pointer, refCount, refCount);
        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.finest(PythonUtils.formatJString("readNativeRefCount(%x) = %d (%x)", pointer, refCount, refCount));
        }
        return refCount;
    }

    public static void writeNativeRefCount(long pointer, long newValue) {
        assert newValue > 0 : PythonUtils.formatJString("refcnt value to write below zero for %016x (%d %016x)\n", pointer, newValue, newValue);
        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.finest(PythonUtils.formatJString("writeNativeRefCount(%x, %d (%x))", pointer, newValue, newValue));
        }
        UNSAFE.putLong(pointer + TP_REFCNT_OFFSET, newValue);
    }

    private static Object createAbstractNativeObject(HandleContext handleContext, Object obj, boolean transfer, long pointer) {
        assert isBackendPointerObject(obj) : obj.getClass();

        pollReferenceQueue();
        PythonAbstractNativeObject result = new PythonAbstractNativeObject(obj);
        NativeObjectReference ref = new NativeObjectReference(handleContext, result, pointer);
        nativeLookupPut(getContext(), pointer, ref);

        long refCntDelta = PythonAbstractObjectNativeWrapper.MANAGED_REFCNT - (transfer ? 1 : 0);
        addNativeRefCount(pointer, refCntDelta);
        return result;
    }

    @TruffleBoundary
    public static boolean isBackendPointerObject(Object obj) {
        return obj != null && (obj.getClass().toString().contains("LLVMPointerImpl") || obj.getClass().toString().contains("NFIPointer") || obj.getClass().toString().contains("NativePointer"));
    }

    @GenerateUncached
    @GenerateInline(false)
    @ImportStatic(CApiGuards.class)
    public abstract static class ToPythonWrapperNode extends CExtToJavaNode {

        public static PythonNativeWrapper executeUncached(Object obj, boolean strict) {
            return getUncached().executeWrapper(obj, strict);
        }

        @Override
        public final Object execute(Object object) {
            return executeWrapper(object, true);
        }

        public abstract PythonNativeWrapper executeWrapper(Object obj, boolean strict);

        @Specialization(guards = "!isNativeWrapper(obj)", limit = "3")
        static PythonNativeWrapper doNonWrapper(Object obj, boolean strict,
                        @Bind("this") Node inliningTarget,
                        @Cached CStructAccess.ReadI32Node readI32Node,
                        @CachedLibrary("obj") InteropLibrary interopLibrary,
                        @Cached InlinedConditionProfile isNullProfile,
                        @Cached InlinedConditionProfile isLongProfile,
                        @Cached InlinedConditionProfile isNativeProfile,
                        @Cached InlinedExactClassProfile nativeWrapperProfile,
                        @Cached InlinedConditionProfile isHandleSpaceProfile) {
            long pointer;
            if (isLongProfile.profile(inliningTarget, obj instanceof Long)) {
                pointer = (long) obj;
            } else {
                if (!interopLibrary.isPointer(obj)) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw CompilerDirectives.shouldNotReachHere("not a pointer: " + obj);
                }
                try {
                    pointer = interopLibrary.asPointer(obj);
                } catch (final UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
            }
            if (isNullProfile.profile(inliningTarget, pointer == 0)) {
                return null;
            }
            PythonContext pythonContext = PythonContext.get(inliningTarget);
            HandleContext nativeContext = pythonContext.nativeContext;
            assert pythonContext.ownsGil();
            if (isHandleSpaceProfile.profile(inliningTarget, HandlePointerConverter.pointsToPyHandleSpace(pointer))) {
                int idx = readI32Node.read(HandlePointerConverter.pointerToStub(pointer), CFields.GraalPyObject__handle_table_index);
                PythonObjectReference reference = nativeStubLookupGet(nativeContext, pointer, idx);
                PythonNativeWrapper wrapper;
                if (reference == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw CompilerDirectives.shouldNotReachHere("reference was freed: " + Long.toHexString(pointer));
                }
                wrapper = reference.get();
                if (strict && wrapper == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw CompilerDirectives.shouldNotReachHere("reference was collected: " + Long.toHexString(pointer));
                }
                return wrapper;
            } else {
                IdReference<?> lookup = nativeLookupGet(nativeContext, pointer);
                if (isNativeProfile.profile(inliningTarget, lookup != null)) {
                    Object ref = lookup.get();
                    if (ref == null) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        throw CompilerDirectives.shouldNotReachHere("reference was collected: " + Long.toHexString(pointer));
                    }
                    Object profiled = nativeWrapperProfile.profile(inliningTarget, ref);
                    if (profiled instanceof PythonNativeWrapper nativeWrapper) {
                        return nativeWrapper;
                    }
                }
                return null;
            }
        }

        @Specialization
        static PythonNativeWrapper doWrapper(PythonNativeWrapper wrapper, @SuppressWarnings("unused") boolean strict) {
            return wrapper;
        }

        @NeverDefault
        public static ToPythonWrapperNode create() {
            return CApiTransitionsFactory.ToPythonWrapperNodeGen.create();
        }

        public static ToPythonWrapperNode getUncached() {
            return CApiTransitionsFactory.ToPythonWrapperNodeGen.getUncached();
        }
    }

    @GenerateUncached
    @GenerateInline(false)
    public abstract static class WrappedPointerToPythonNode extends CExtToJavaNode {
        @Specialization
        static Object doIt(Object object) {
            if (object instanceof PythonNativeWrapper) {
                return ((PythonNativeWrapper) object).getDelegate();
            } else {
                return object;
            }
        }
    }

    @GenerateUncached
    @GenerateInline(false)
    public abstract static class WrappedPointerToNativeNode extends CExtToNativeNode {
        @Specialization
        static Object doGeneric(Object object,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedConditionProfile isWrapperProfile,
                        @Cached GetReplacementNode getReplacementNode) {
            if (isWrapperProfile.profile(inliningTarget, object instanceof PythonNativeWrapper)) {
                Object replacement = getReplacementNode.execute(inliningTarget, (PythonNativeWrapper) object);
                if (replacement != null) {
                    return replacement;
                }
            }
            return object;
        }
    }
}
