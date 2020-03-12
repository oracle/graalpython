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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.cext.CAPIConversionNodeSupplier;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.AddRefCntNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.SubRefCntNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.SubRefCntNodeGen;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.common.CExtContext;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.call.GenericInvokeNode;
import com.oracle.graal.python.runtime.AsyncHandler;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.llvm.spi.ReferenceLibrary;

public final class CApiContext extends CExtContext {

    public static final long REFERENCE_COUNT_MARKER = (1L<<32);

    private final ReferenceQueue<Object> nativeObjectsQueue;
    private Map<Object, AllocInfo> allocatedNativeMemory;
    private final EconomicMap<Object, NativeObjectReference> nativeObjectWrapperMap;

    /** Container of pointers that have seen to be free'd. */
    private Map<Object, AllocInfo> freedNativeMemory;

    private RootCallTarget referenceCleanerCallTarget;

    public CApiContext(PythonContext context, Object hpyLibrary) {
        super(context, hpyLibrary, CAPIConversionNodeSupplier.INSTANCE);
        nativeObjectsQueue = new ReferenceQueue<>();
        nativeObjectWrapperMap = EconomicMap.create(new ReferenceEquivalenceStrategy());

        context.registerAsyncAction(() -> {
            Reference<? extends Object> reference = null;
            try {
                reference = nativeObjectsQueue.remove();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (reference instanceof NativeObjectReference) {
                return new CApiReferenceCleanerAction((NativeObjectReference) reference);
            }
            return null;
        });
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
            referenceCleanerCallTarget = Truffle.getRuntime().createCallTarget(new CApiReferenceCleanerRootNode(getContext().getLanguage()));
        }
        return referenceCleanerCallTarget;
    }

    private static final class ReferenceEquivalenceStrategy extends Equivalence {
        private final ReferenceLibrary referenceLibrary = ReferenceLibrary.getFactory().getUncached();

        @Override
        public boolean equals(Object a, Object b) {
            return referenceLibrary.isSame(a, b);
        }

        @Override
        @TruffleBoundary
        public int hashCode(Object o) {
            return o.hashCode();
        }
    }

    static class NativeObjectReference extends WeakReference<PythonAbstractNativeObject> {

        final TruffleObject ptrObject;
        boolean resurrect;
        long managedRefCount;

        public NativeObjectReference(PythonAbstractNativeObject referent, ReferenceQueue<? super PythonAbstractNativeObject> q, long managedRefCount) {
            super(referent, q);
            this.ptrObject = referent.getPtr();
            this.managedRefCount = managedRefCount;
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

        @Child private SubRefCntNode refCntNode;

        protected CApiReferenceCleanerRootNode(TruffleLanguage<?> language) {
            super(language);
            refCntNode = SubRefCntNodeGen.create();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object pointerObject = PArguments.getArgument(frame, 0);
            Long managedRefCount = (Long) PArguments.getArgument(frame, 1);
            return refCntNode.execute(pointerObject, managedRefCount);
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
        private final NativeObjectReference nativeObjectReference;

        public CApiReferenceCleanerAction(NativeObjectReference nativeObjectReference) {
            this.nativeObjectReference = nativeObjectReference;
        }

        @Override
        public void execute(VirtualFrame frame, Node location, PythonContext context) {
            if (!nativeObjectReference.resurrect) {
                TruffleObject ptrObject = nativeObjectReference.getPtrObject();

                context.getCApiContext().nativeObjectWrapperMap.removeKey(ptrObject);
                Object[] pArguments = PArguments.create(2);
                PArguments.setArgument(pArguments, 0, ptrObject);
                PArguments.setArgument(pArguments, 1, nativeObjectReference.managedRefCount);
                GenericInvokeNode.getUncached().execute(frame, context.getCApiContext().getReferenceCleanerCallTarget(), pArguments);
            }
        }
    }

    public PythonAbstractNativeObject getPythonNativeObject(int idx) {
        // TODO
        return null;
    }

    public PythonAbstractNativeObject getPythonNativeObject(TruffleObject nativePtr, ConditionProfile newRefProfile, ConditionProfile resurrectProfile, AddRefCntNode addRefCntNode) {
        return getPythonNativeObject(nativePtr, newRefProfile, resurrectProfile, addRefCntNode, false);
    }

    public PythonAbstractNativeObject getPythonNativeObject(TruffleObject nativePtr, ConditionProfile newRefProfile, ConditionProfile resurrectProfile, AddRefCntNode addRefCntNode, boolean steal) {
        CompilerAsserts.partialEvaluationConstant(addRefCntNode);
        CompilerAsserts.partialEvaluationConstant(steal);

        NativeObjectReference ref = lookupNativeObjectReference(nativePtr);
        PythonAbstractNativeObject nativeObject;

        // If there is no mapping, we need to create a new one.
        if (newRefProfile.profile(ref == null)) {
            nativeObject = createPythonAbstractNativeObject(nativePtr, addRefCntNode, steal);
        } else {
            nativeObject = ref.get();
            if (resurrectProfile.profile(nativeObject == null)) {
                // Bad luck: the mapping is still there and wasn't cleaned up but we need a new
                // mapping. Therefore, we need to cancel the cleaner action and set a new native
                // object reference.
                ref.markAsResurrected();
                nativeObject = new PythonAbstractNativeObject(nativePtr);

                ref = new NativeObjectReference(nativeObject, nativeObjectsQueue, ref.managedRefCount);
                putNativeObjectReference(nativePtr, ref);
            }
            if(steal) {
                ref.managedRefCount++;
            }
        }
        return nativeObject;
    }

    private PythonAbstractNativeObject createPythonAbstractNativeObject(TruffleObject nativePtr, AddRefCntNode addRefCntNode, boolean steal) {
        addRefCntNode.execute(nativePtr, REFERENCE_COUNT_MARKER);
        PythonAbstractNativeObject nativeObject = new PythonAbstractNativeObject(nativePtr);
        putNativeObjectReference(nativePtr, new NativeObjectReference(nativeObject, nativeObjectsQueue, steal ? REFERENCE_COUNT_MARKER  + 1: REFERENCE_COUNT_MARKER));
        return nativeObject;
    }

    @TruffleBoundary
    private NativeObjectReference putNativeObjectReference(Object nativePtr, NativeObjectReference ref) {
        return nativeObjectWrapperMap.put(nativePtr, ref);
    }

    @TruffleBoundary
    public NativeObjectReference lookupNativeObjectReference(Object nativePtr) {
        return nativeObjectWrapperMap.get(nativePtr);
    }

    @TruffleBoundary
    public void traceFree(Object ptr) {
        traceFree(ptr, null, null);
    }

    @TruffleBoundary
    public AllocInfo traceFree(Object ptr, PFrame.Reference curFrame, String clazzName) {
        if (allocatedNativeMemory == null) {
            allocatedNativeMemory = new HashMap<>();
        }
        if (freedNativeMemory == null) {
            freedNativeMemory = new HashMap<>();
        }
        AllocInfo allocatedValue = allocatedNativeMemory.remove(ptr);
        Object freedValue = freedNativeMemory.put(ptr, allocatedValue);
        if (freedValue != null) {
            PythonLanguage.getLogger().severe(String.format("freeing memory that was already free'd %s (double-free)", asHex(ptr)));
        } else if (allocatedValue == null) {
            PythonLanguage.getLogger().info(String.format("freeing non-allocated memory %s (maybe a double-free or we didn't trace the allocation)", asHex(ptr)));
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
        for (NativeObjectReference nor : nativeObjectWrapperMap.getValues()) {
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
}
