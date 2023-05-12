/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.logging.Level;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiGuards;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.FromCharPointerNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.FromCharPointerNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.DynamicObjectNativeWrapper.PrimitiveNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativePointer;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.TruffleObjectNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitionsFactory.NativePtrToPythonNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitionsFactory.NativeToPythonNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitionsFactory.NativeToPythonStealingNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitionsFactory.PythonToNativeNewRefNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitionsFactory.PythonToNativeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.common.CExtToJavaNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.common.HandleStack;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccessFactory;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.DescriptorDeleteMarker;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.sequence.storage.NativeSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage.ListStorageType;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

import sun.misc.Unsafe;

public class CApiTransitions {

    // if != 0: GC every X C API calls
    private static final int GCALot = Integer.getInteger("python.GCALot", 0);
    // wait X calls before first GC
    private static final int GCALotWait = Integer.getInteger("python.GCALotWait", 0);
    private static long GCALotTotalCounter = 0;
    private static int GCALotCounter = 0;

    private static final TruffleLogger LOGGER = CApiContext.getLogger(CApiTransitions.class);

    // transfer: steal or borrow reference

    public static final class HandleContext {
        private static final int DEFAULT_CAPACITY = 10;

        public final NativeObjectReferenceArrayWrapper referencesToBeFreed = new NativeObjectReferenceArrayWrapper();
        public final HashMap<Long, IdReference<?>> nativeLookup = new HashMap<>();
        public final WeakHashMap<Object, WeakReference<PythonAbstractNativeObject>> managedNativeLookup = new WeakHashMap<>();
        public final ArrayList<PythonObjectReference> nativeHandles = new ArrayList<>(DEFAULT_CAPACITY);
        public final HandleStack nativeHandlesFreeStack = new HandleStack(DEFAULT_CAPACITY);
        public final Set<NativeStorageReference> nativeStorageReferences = new HashSet<>();

        public final ReferenceQueue<Object> referenceQueue = new ReferenceQueue<>();

        volatile boolean referenceQueuePollActive = false;

    }

    private static HandleContext getContext() {
        return PythonContext.get(null).nativeContext;
    }

    public abstract static class IdReference<T> extends WeakReference<T> {

        public IdReference(T referent) {
            super(referent, getContext().referenceQueue);
        }
    }

    public static final class PythonObjectReference extends IdReference<PythonNativeWrapper> {

        /**
         * This reference forces the object to remain alive, and can be set to null when the
         * refcount falls to {@link PythonNativeWrapper#MANAGED_REFCNT}.
         */
        PythonNativeWrapper strongReference;
        private final long pointer;

        public PythonObjectReference(PythonNativeWrapper referent, long pointer) {
            super(referent);
            this.pointer = pointer;
            this.strongReference = referent.getRefCount() > PythonNativeWrapper.MANAGED_REFCNT ? referent : null;
            LOGGER.finer(() -> PythonUtils.formatJString("new %s PythonObjectReference<%s> to %s", (strongReference == null ? "weak" : "strong"), Long.toHexString(pointer), referent));
            referent.ref = this;
        }

        @Override
        public String toString() {
            return "PythonObjectReference<" + (strongReference == null ? "" : "strong,") + Long.toHexString(pointer) + ">";
        }
    }

    public static final class NativeObjectReference extends IdReference<PythonAbstractNativeObject> {

        final Object object;
        final long pointer;

        public NativeObjectReference(PythonAbstractNativeObject referent, long pointer) {
            super(referent);
            this.object = referent.object;
            this.pointer = pointer;
            referent.ref = this;
            assert (pointer & 7) == 0;
            LOGGER.finer(() -> PythonUtils.formatJString("new NativeObjectReference<%s> to %s", Long.toHexString(pointer), referent));
        }

        @Override
        public String toString() {
            return "NativeObjectReference<" + (get() == null ? "freed," : "") + Long.toHexString(pointer) + ">";
        }
    }

    public static final class NativeStorageReference extends IdReference<NativeSequenceStorage> {
        private final ListStorageType type;
        private Object ptr;
        private int size;

        public NativeStorageReference(NativeSequenceStorage storage) {
            super(storage);
            type = storage.getElementType();
            ptr = storage.getPtr();
            size = storage.length();
            LOGGER.finer(() -> PythonUtils.formatJString("new NativeStorageReference<%s>", ptr));
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
    }

    @TruffleBoundary
    public static void registerNativeSequenceStorage(NativeSequenceStorage storage) {
        assert PythonContext.get(null).ownsGil();
        NativeStorageReference ref = new NativeStorageReference(storage);
        storage.setReference(ref);
        getContext().nativeStorageReferences.add(ref);
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
                NativeObjectReferenceArrayWrapper referencesToBeFreed = getContext().referencesToBeFreed;
                while (true) {
                    Object entry = queue.poll();
                    if (entry == null) {
                        if (count > 0) {
                            assert context.referenceQueuePollActive;
                            if (!referencesToBeFreed.isEmpty()) {
                                LOGGER.fine(() -> PythonUtils.formatJString("releasing %d NativeObjectReference instances", referencesToBeFreed.getArraySize()));
                                Object array = CStructAccessFactory.AllocateNodeGen.getUncached().alloc(referencesToBeFreed.getArraySize() * Long.BYTES);
                                CStructAccessFactory.WriteLongNodeGen.getUncached().writeLongArray(array, referencesToBeFreed.getArray(), (int) referencesToBeFreed.getArraySize(), 0, 0);
                                PCallCapiFunction.getUncached().call(NativeCAPISymbol.FUN_BULK_DEALLOC, array, referencesToBeFreed.getArraySize());
                                CStructAccessFactory.FreeNodeGen.getUncached().free(array);
                                referencesToBeFreed.reset();
                            }
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
                        LOGGER.finer(() -> PythonUtils.formatJString("releasing PythonObjectReference %s", reference));

                        if (HandlePointerConverter.pointsToPyHandleSpace(reference.pointer)) {
                            int index = HandlePointerConverter.pointerToHandleIndex(reference.pointer);
                            assert context.nativeHandles.get(index) != null;
                            context.nativeHandles.set(index, null);
                            context.nativeHandlesFreeStack.push(index);
                        } else {
                            assert nativeLookupGet(context, reference.pointer) != null : Long.toHexString(reference.pointer);
                            nativeLookupRemove(context, reference.pointer);
                        }
                    } else if (entry instanceof NativeObjectReference reference) {
                        LOGGER.finer(() -> PythonUtils.formatJString("releasing NativeObjectReference %s", reference));
                        nativeLookupRemove(context, reference.pointer);
                        if (subNativeRefCount(reference.pointer, PythonNativeWrapper.MANAGED_REFCNT) == 0) {
                            referencesToBeFreed.add(reference.pointer);
                        }
                    } else if (entry instanceof NativeStorageReference reference) {
                        LOGGER.finer(() -> PythonUtils.formatJString("releasing NativeStorageReference %s", reference));
                        context.nativeStorageReferences.remove(entry);
                        if (reference.type == ListStorageType.Generic) {
                            PCallCapiFunction.getUncached().call(NativeCAPISymbol.FUN_PY_TRUFFLE_OBJECT_ARRAY_RELEASE, reference.ptr, reference.size);
                        }
                        CStructAccessFactory.FreeNodeGen.getUncached().free(reference.ptr);
                    }
                }
            }
        }
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
            System.gc();
            pollReferenceQueue();
        }
    }

    @TruffleBoundary
    public static Object lookupNative(long pointer) {
        log(pointer);
        IdReference<?> reference = nativeLookupGet(getContext(), pointer);
        if (reference != null) {
            return logResult(reference.get());
        }
        return logResult(null);
    }

    @TruffleBoundary
    public static IdReference<?> nativeLookupGet(HandleContext context, long pointer) {
        return context.nativeLookup.get(pointer);
    }

    @TruffleBoundary
    public static IdReference<?> nativeLookupPut(HandleContext context, long pointer, IdReference<?> value) {
        return context.nativeLookup.put(pointer, value);
    }

    @TruffleBoundary
    public static IdReference<?> nativeLookupRemove(HandleContext context, long pointer) {
        return context.nativeLookup.remove(pointer);
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class HandleReleaser implements TruffleObject {
        @SuppressWarnings("static-method")
        @ExportMessage
        public boolean isExecutable() {
            return true;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        public Object execute(Object[] args,
                        @CachedLibrary(limit = "5") InteropLibrary lib) {
            assert args.length == 1;
            Object arg = args[0];
            if (arg instanceof PythonNativeWrapper || arg instanceof PythonAbstractObject) {
                return 0;
            }
            long pointer;
            if (arg instanceof Long) {
                pointer = (long) arg;
            } else {
                if (!lib.isPointer(arg)) {
                    return 0;
                }
                try {
                    pointer = lib.asPointer(arg);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
            }
            assert HandlePointerConverter.pointsToPyHandleSpace(pointer);
            release(pointer);
            return 0;
        }

        public static void release(long pointer) {
            LOGGER.finer(() -> PythonUtils.formatJString("releasing handle %016x\n", pointer));
            // TODO: release handle
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class HandleTester implements TruffleObject {
        @SuppressWarnings("static-method")
        @ExportMessage
        public boolean isExecutable() {
            return true;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        public Object execute(Object[] args,
                        @CachedLibrary(limit = "5") InteropLibrary lib) {
            assert args.length == 1;
            Object arg = args[0];
            assert !(arg instanceof PythonAbstractObject);
            if (arg instanceof PythonNativeWrapper) {
                return 1;
            }
            long pointer;
            if (arg instanceof Long) {
                pointer = (long) arg;
            } else {
                if (!lib.isPointer(arg)) {
                    return 0;
                }
                try {
                    pointer = lib.asPointer(arg);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
            }
            return HandlePointerConverter.pointsToPyHandleSpace(pointer) ? 1 : 0;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class HandleResolver implements TruffleObject {
        @SuppressWarnings("static-method")
        @ExportMessage
        public boolean isExecutable() {
            return true;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        public Object execute(Object[] args) {
            assert args.length == 1;
            long pointer = (long) args[0];
            return resolve(pointer);
        }

        public static PythonNativeWrapper resolve(long pointer) {
            PythonNativeWrapper wrapper = getContext().nativeHandles.get(HandlePointerConverter.pointerToHandleIndex(pointer)).get();
            assert wrapper != null : "reference was collected: " + Long.toHexString(pointer);
            incRef(wrapper, 1);
            return wrapper;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class HandleResolverStealing implements TruffleObject {
        @SuppressWarnings("static-method")
        @ExportMessage
        public boolean isExecutable() {
            return true;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        public Object execute(Object[] args) {
            assert args.length == 1;
            long pointer = (long) args[0];
            return resolve(pointer);
        }

        public static PythonNativeWrapper resolve(long pointer) {
            PythonNativeWrapper wrapper = getContext().nativeHandles.get(HandlePointerConverter.pointerToHandleIndex(pointer)).get();
            assert wrapper != null : "reference was collected: " + Long.toHexString(pointer);
            return wrapper;
        }
    }

    public static final class HandlePointerConverter {

        private static final long HANDLE_BASE = 0x8000_0000_0000_0000L;

        /**
         * We need to shift the pointers because some libraries, notably cffi, do pointer tagging.
         */
        private static final int HANDLE_SHIFT = 3;

        public static long handleIndexToPointer(int idx) {
            return ((long) idx << HANDLE_SHIFT) | HANDLE_BASE;
        }

        public static int pointerToHandleIndex(long pointer) {
            return (int) ((pointer & ~HANDLE_BASE) >>> HANDLE_SHIFT);
        }

        public static boolean pointsToPyHandleSpace(long pointer) {
            return (pointer & HANDLE_BASE) != 0;
        }
    }

    public static final class HandleFactory {

        public static long create(PythonNativeWrapper wrapper) {
            CompilerAsserts.neverPartOfCompilation();
            assert !(wrapper instanceof TruffleObjectNativeWrapper);
            assert PythonContext.get(null).ownsGil();
            pollReferenceQueue();
            HandleContext handleContext = getContext();
            // don't reuse handles in GCALot mode to make debugging easier
            int idx = GCALot != 0 ? -1 : handleContext.nativeHandlesFreeStack.pop();
            long pointer;
            if (idx == -1) {
                pointer = HandlePointerConverter.handleIndexToPointer(handleContext.nativeHandles.size());
                handleContext.nativeHandles.add(new PythonObjectReference(wrapper, pointer));
            } else {
                assert idx >= 0;
                pointer = HandlePointerConverter.handleIndexToPointer(idx);
                handleContext.nativeHandles.set(idx, new PythonObjectReference(wrapper, pointer));
            }
            return pointer;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class PointerContainer implements TruffleObject {

        private final long pointer;

        public PointerContainer(long pointer) {
            this.pointer = pointer;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        public boolean isPointer() {
            return true;
        }

        @ExportMessage
        public long asPointer() {
            return pointer;
        }

        @ExportMessage
        public void toNative() {
            // nothing to do
        }

        @ExportMessage
        public boolean isNull() {
            return pointer == 0;
        }

        @Override
        public String toString() {
            CompilerAsserts.neverPartOfCompilation();
            return String.format("<0x%016x>", pointer);
        }
    }

    @TruffleBoundary
    @SuppressWarnings("try")
    public static void firstToNative(PythonNativeWrapper obj) {
        /*
         * This method is called from 'toNative' messages. Therefore, we don't know the exact time
         * when this will be executed and it may happen after the GIL was released by a C extension.
         * So, we need to acquire the GIL here to be safe.
         */
        try (GilNode.UncachedAcquire ignored = GilNode.uncachedAcquire()) {
            assert !obj.isNative();
            log(obj);
            obj.setNativePointer(logResult(HandleFactory.create(obj)));
        }
    }

    @TruffleBoundary
    @SuppressWarnings("try")
    public static void firstToNative(PythonNativeWrapper obj, long ptr) {
        try (GilNode.UncachedAcquire ignored = GilNode.uncachedAcquire()) {
            /*
             * The first test if '!obj.isNative()' in the caller is done on a fast-path but not
             * synchronized. So, repeat the test after the GIL was acquired.
             */
            if (!obj.isNative()) {
                logVoid(obj, ptr);
                obj.setNativePointer(ptr);
                pollReferenceQueue();
                nativeLookupPut(getContext(), ptr, new PythonObjectReference(obj, ptr));
            }
        }
    }

    // logging

    private static void log(Object... args) {
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

    private static <T> T logResult(T value) {
        if (LOGGER.isLoggable(Level.FINEST)) {
            CompilerAsserts.neverPartOfCompilation();
            StackTraceElement element = new RuntimeException().getStackTrace()[1];
            StringBuilder str = new StringBuilder("    ==> <").append(element.getLineNumber()).append("> ");
            format(str, value);
            LOGGER.finest(str.toString());
        }
        return value;
    }

    @TruffleBoundary(allowInlining = true)
    public static long incRef(PythonNativeWrapper nativeWrapper, long value) {
        assert value > 0;
        long refCount = nativeWrapper.getRefCount();
        nativeWrapper.setRefCount(refCount + value);
        // "-1" because the refcount can briefly go below (e.g., PyTuple_SetItem)
        assert refCount >= (PythonNativeWrapper.MANAGED_REFCNT - 1) : "invalid refcnt " + refCount + " during incRef in " + Long.toHexString(nativeWrapper.getNativePointer());
        if (refCount == PythonNativeWrapper.MANAGED_REFCNT && nativeWrapper.ref != null) {
            nativeWrapper.ref.strongReference = nativeWrapper;
        }
        return refCount;
    }

    @TruffleBoundary(allowInlining = true)
    public static long decRef(PythonNativeWrapper nativeWrapper, long value) {
        assert value > 0;
        long refCount = nativeWrapper.getRefCount() - value;
        nativeWrapper.setRefCount(refCount);
        // "-1" because the refcount can briefly go below (e.g., PyTuple_SetItem)
        assert refCount >= (PythonNativeWrapper.MANAGED_REFCNT - 1) : "invalid refcnt " + refCount + " during decRef in " + Long.toHexString(nativeWrapper.getNativePointer());
        if (refCount == PythonNativeWrapper.MANAGED_REFCNT && nativeWrapper.ref != null) {
            nativeWrapper.ref.strongReference = null;
        }
        return refCount;
    }

    @TruffleBoundary(allowInlining = true)
    public static void setRefCount(PythonNativeWrapper nativeWrapper, long value) {
        long refCnt = nativeWrapper.getRefCount();
        if (value < refCnt) {
            decRef(nativeWrapper, refCnt - value);
        } else if (value > refCnt) {
            incRef(nativeWrapper, value - refCnt);
        }
    }

    private static final InteropLibrary LIB = InteropLibrary.getUncached();

    @GenerateUncached
    @GenerateInline(false)
    public abstract static class CharPtrToPythonNode extends CExtToJavaNode {

        @Specialization
        static Object doForeign(Object value,
                        @Bind("$node") Node inliningTarget,
                        @CachedLibrary(limit = "3") InteropLibrary interopLibrary,
                        @Cached InlinedConditionProfile isNullProfile) {
            // this branch is not a shortcut; it actually returns a different object
            if (isNullProfile.profile(inliningTarget, interopLibrary.isNull(value))) {
                return PNone.NO_VALUE;
            }
            return nativeCharToJava(value);
        }

        @TruffleBoundary
        public static Object nativeCharToJava(Object value) {
            log(value);
            assert !(value instanceof Long);
            if (value instanceof String) {
                return logResult(PythonUtils.toTruffleStringUncached((String) value));
            } else if (value instanceof TruffleString) {
                return logResult(value);
            }
            if (LIB.isPointer(value)) {
                long pointer;
                try {
                    pointer = LIB.asPointer(value);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
                if (HandlePointerConverter.pointsToPyHandleSpace(pointer)) {
                    PythonNativeWrapper obj = HandleResolver.resolve(pointer);
                    if (obj != null) {
                        return logResult(obj.getDelegate());
                    }
                } else {
                    IdReference<?> lookup = nativeLookupGet(CApiTransitions.getContext(), pointer);
                    if (lookup != null) {
                        Object obj = lookup.get();
                        if (obj instanceof PythonAbstractNativeObject) {
                            return logResult(obj);
                        } else {
                            return logResult(((PythonNativeWrapper) value).getDelegate());
                        }
                    }
                }
            }
            FromCharPointerNode fromCharPointerNode = FromCharPointerNodeGen.getUncached();
            return logResult(fromCharPointerNode.execute(value));
        }
    }

    @GenerateUncached
    @GenerateInline(false)
    @ImportStatic(CApiGuards.class)
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
                        @Cached PCallCapiFunction callAddRef) {
            if (needsTransfer()) {
                callAddRef.call(NativeCAPISymbol.FUN_ADDREF, obj.object, 1);
            }
            return obj.getPtr();
        }

        @Specialization
        static Object doNative(PythonNativePointer obj) {
            return obj.getPtr();
        }

        @Specialization
        Object doNative(@SuppressWarnings("unused") DescriptorDeleteMarker obj) {
            return getContext().getNativeNull().getPtr();
        }

        static boolean isOther(Object obj) {
            return !(obj instanceof PythonAbstractNativeObject || obj instanceof PythonNativePointer || obj instanceof DescriptorDeleteMarker);
        }

        @Specialization(guards = "isOther(obj)")
        Object doOther(Object obj,
                        @Cached GetNativeWrapperNode getWrapper) {
            pollReferenceQueue();
            PythonNativeWrapper wrapper = getWrapper.execute(obj);
            if (needsTransfer()) {
                // native part needs to decRef to release
                incRef(wrapper, 1);
            }
            return wrapper;
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
                        @Shared("primitive") @Cached InlinedConditionProfile isPrimitiveProfile) {
            return handleWrapper(inliningTarget, isPrimitiveProfile, false, value);
        }

        @Specialization(guards = "!isNativeWrapper(value)", limit = "3")
        @SuppressWarnings({"truffle-static-method", "truffle-sharing"})
        Object doNonWrapper(Object value,
                        @Bind("$node") Node inliningTarget,
                        @CachedLibrary("value") InteropLibrary interopLibrary,
                        @Cached InlinedConditionProfile isNullProfile,
                        @Cached InlinedConditionProfile isZeroProfile,
                        @Cached InlinedConditionProfile createNativeProfile,
                        @Cached InlinedConditionProfile isNativeProfile,
                        @Cached InlinedConditionProfile isNativeWrapperProfile,
                        @Cached InlinedConditionProfile isHandleSpaceProfile,
                        @Shared("primitive") @Cached InlinedConditionProfile isPrimitiveProfile) {
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
                PythonObjectReference reference = nativeContext.nativeHandles.get(HandlePointerConverter.pointerToHandleIndex(pointer));
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
                        return createAbstractNativeObject(value, needsTransfer(), pointer);
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
                    return createAbstractNativeObject(value, needsTransfer(), pointer);
                }
            }
            return handleWrapper(inliningTarget, isPrimitiveProfile, needsTransfer(), wrapper);
        }

        private static Object handleWrapper(Node node, InlinedConditionProfile isPrimitiveProfile, boolean transfer, PythonNativeWrapper wrapper) {
            if (transfer) {
                assert wrapper.getRefCount() >= PythonNativeWrapper.MANAGED_REFCNT;
                decRef(wrapper, 1);
            }
            if (isPrimitiveProfile.profile(node, wrapper instanceof PrimitiveNativeWrapper)) {
                PrimitiveNativeWrapper primitive = (PrimitiveNativeWrapper) wrapper;
                if (primitive.isBool()) {
                    return primitive.getBool();
                } else if (primitive.isInt()) {
                    return primitive.getInt();
                } else if (primitive.isLong()) {
                    return primitive.getLong();
                } else if (primitive.isByte()) {
                    return primitive.getByte();
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
            WeakReference<PythonAbstractNativeObject> ref = nativeContext.managedNativeLookup.computeIfAbsent(value, o -> new WeakReference<>(new PythonAbstractNativeObject(o)));
            PythonAbstractNativeObject result = ref.get();
            if (result == null) {
                // value is weak as well:
                nativeContext.managedNativeLookup.put(value, new WeakReference<>(new PythonAbstractNativeObject(value)));
            }
            return result;
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
                        @Cached InlinedConditionProfile isZeroProfile,
                        @Cached InlinedConditionProfile createNativeProfile,
                        @Cached InlinedConditionProfile isNativeProfile,
                        @Cached InlinedConditionProfile isNativeWrapperProfile,
                        @Cached InlinedConditionProfile isHandleSpaceProfile,
                        @Cached InlinedConditionProfile isPrimitiveProfile) {

            PythonNativeWrapper wrapper;

            PythonContext pythonContext = PythonContext.get(inliningTarget);
            HandleContext nativeContext = pythonContext.nativeContext;

            if (isZeroProfile.profile(inliningTarget, pointer == 0)) {
                return PNone.NO_VALUE;
            }
            assert pythonContext.ownsGil();
            if (isHandleSpaceProfile.profile(inliningTarget, HandlePointerConverter.pointsToPyHandleSpace(pointer))) {
                PythonObjectReference reference = nativeContext.nativeHandles.get(HandlePointerConverter.pointerToHandleIndex(pointer));
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
                        return createAbstractNativeObject(pointer, stealing, pointer);
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
                    return createAbstractNativeObject(pointer, stealing, pointer);
                }
            }
            return handleWrapper(inliningTarget, isPrimitiveProfile, stealing, wrapper);
        }

        private static Object handleWrapper(Node node, InlinedConditionProfile isPrimitiveProfile, boolean transfer, PythonNativeWrapper wrapper) {
            if (transfer) {
                assert wrapper.getRefCount() >= PythonNativeWrapper.MANAGED_REFCNT;
                decRef(wrapper, 1);
            }
            if (isPrimitiveProfile.profile(node, wrapper instanceof PrimitiveNativeWrapper)) {
                PrimitiveNativeWrapper primitive = (PrimitiveNativeWrapper) wrapper;
                if (primitive.isBool()) {
                    return primitive.getBool();
                } else if (primitive.isInt()) {
                    return primitive.getInt();
                } else if (primitive.isLong()) {
                    return primitive.getLong();
                } else if (primitive.isByte()) {
                    return primitive.getByte();
                } else if (primitive.isDouble()) {
                    return primitive.getDouble();
                } else {
                    throw CompilerDirectives.shouldNotReachHere();
                }
            } else {
                return wrapper.getDelegate();
            }
        }
    }

    private static final Unsafe UNSAFE = PythonUtils.initUnsafe();
    private static final int TP_REFCNT_OFFSET = 0;

    private static long addNativeRefCount(long pointer, long refCntDelta) {
        long refCount = UNSAFE.getLong(pointer + TP_REFCNT_OFFSET);
        assert (refCount & 0xFFFFFFFF00000000L) == 0 : String.format("suspicious refcnt value during managed adjustment for %016x (%d %016x + %d)\n", pointer, refCount, refCount, refCntDelta);
        assert (refCount + refCntDelta) > 0 : String.format("refcnt reached zero during managed adjustment for %016x (%d %016x + %d)\n", pointer, refCount, refCount, refCntDelta);

        LOGGER.finest(() -> PythonUtils.formatJString("addNativeRefCount %x %x %d + %d", pointer, refCount, refCount, refCntDelta));

        UNSAFE.putLong(pointer + TP_REFCNT_OFFSET, refCount + refCntDelta);
        return refCount + refCntDelta;
    }

    private static long subNativeRefCount(long pointer, long refCntDelta) {
        long refCount = UNSAFE.getLong(pointer + TP_REFCNT_OFFSET);
        assert (refCount & 0xFFFFFFFF00000000L) == 0 : String.format("suspicious refcnt value during managed adjustment for %016x (%d %016x + %d)\n", pointer, refCount, refCount, refCntDelta);
        assert (refCount - refCntDelta) >= 0 : String.format("refcnt below zero during managed adjustment for %016x (%d %016x + %d)\n", pointer, refCount, refCount, refCntDelta);

        LOGGER.finest(() -> PythonUtils.formatJString("subNativeRefCount %x %x %d + %d", pointer, refCount, refCount, refCntDelta));

        UNSAFE.putLong(pointer + TP_REFCNT_OFFSET, refCount - refCntDelta);
        return refCount - refCntDelta;
    }

    private static Object createAbstractNativeObject(Object obj, boolean transfer, long pointer) {
        assert isBackendPointerObject(obj) : obj.getClass();

        pollReferenceQueue();
        PythonAbstractNativeObject result = new PythonAbstractNativeObject(obj);
        NativeObjectReference ref = new NativeObjectReference(result, pointer);
        nativeLookupPut(getContext(), pointer, ref);

        long refCntDelta = PythonNativeWrapper.MANAGED_REFCNT - (transfer ? 1 : 0);
        addNativeRefCount(pointer, refCntDelta);
        return result;
    }

    @TruffleBoundary
    public static boolean isBackendPointerObject(Object obj) {
        return obj != null && (obj.getClass().toString().contains("LLVMPointerImpl") || obj.getClass().toString().contains("NFIPointer") || obj.getClass().toString().contains("PointerContainer"));
    }

    @TruffleBoundary
    public static PythonNativeWrapper nativeToPythonWrapper(Object obj) {
        if (obj instanceof PythonNativeWrapper) {
            return (PythonNativeWrapper) obj;
        } else if (obj instanceof PythonAbstractNativeObject) {
            throw CompilerDirectives.shouldNotReachHere();
        } else {
            long pointer;
            if (obj instanceof Long) {
                pointer = (long) obj;
            } else {
                if (!LIB.isPointer(obj)) {
                    throw CompilerDirectives.shouldNotReachHere("not a pointer: " + obj);
                }
                try {
                    pointer = LIB.asPointer(obj);
                } catch (final UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
            }
            if (pointer == 0) {
                return null;
            }
            assert PythonContext.get(null).ownsGil();
            PythonNativeWrapper wrapper;
            if (HandlePointerConverter.pointsToPyHandleSpace(pointer)) {
                PythonObjectReference reference = getContext().nativeHandles.get(HandlePointerConverter.pointerToHandleIndex(pointer));
                if (reference == null || (wrapper = reference.get()) == null) {
                    throw CompilerDirectives.shouldNotReachHere("reference was collected: " + Long.toHexString(pointer));
                }
                return wrapper;
            } else {
                IdReference<?> lookup = nativeLookupGet(getContext(), pointer);
                if (lookup != null) {
                    Object ref = lookup.get();
                    if (ref == null) {
                        throw CompilerDirectives.shouldNotReachHere("reference was collected: " + Long.toHexString(pointer));
                    }
                    if (ref instanceof PythonNativeWrapper) {
                        return (PythonNativeWrapper) ref;
                    }
                }
            }
            return null;
        }
    }
}
