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

import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.HandlePointerConverter;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonObjectReference;
import com.oracle.graal.python.builtins.objects.cext.common.NativePointer;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

public abstract class PythonNativeWrapper implements TruffleObject {

    private static final TruffleLogger LOGGER = CApiContext.getLogger(PythonNativeWrapper.class);

    private static final long UNINITIALIZED = -1;

    private final boolean replacing;
    private Object delegate;
    private long nativePointer = UNINITIALIZED;

    protected Object replacement;

    public PythonObjectReference ref;

    private PythonNativeWrapper() {
        this.replacing = false;
    }

    private PythonNativeWrapper(Object delegate, boolean replacing) {
        this.replacing = replacing;
        this.delegate = delegate;
    }

    public final Object getDelegate() {
        return delegate;
    }

    protected final void setDelegate(Object delegate) {
        assert this.delegate == null || this.delegate == delegate;
        this.delegate = delegate;
    }

    public final long getNativePointer() {
        return nativePointer;
    }

    public final void setNativePointer(long nativePointer) {
        // we should set the pointer just once
        assert this.nativePointer == UNINITIALIZED || this.nativePointer == nativePointer || nativePointer == UNINITIALIZED;
        this.nativePointer = nativePointer;
    }

    public final boolean isNative(Node inliningTarget, InlinedConditionProfile hasNativePointerProfile) {
        return hasNativePointerProfile.profile(inliningTarget, nativePointer != UNINITIALIZED);
    }

    public final boolean isNative() {
        return nativePointer != UNINITIALIZED;
    }

    /**
     * Determines if the native wrapper should always be materialized in native memory.
     * <p>
     * Native wrappers are usually materialized lazily if they receive
     * {@link InteropLibrary#toNative(Object)}. Sometimes, e.g., when using LLVM runtime, this may
     * never happen if the pointer never floats into real native code or memory. However, some
     * native wrappers emulate data structures where it does not make sense to emulate them and it
     * is more efficient to just allocate the native memory and write data to it. Also, in some
     * cases it is just necessary to enable byte-wise access (e.g. when using {@code memcpy}).
     * </p>
     * <p>
     * Therefore, wrappers may return {@code true} and the appropriate <it>Python-to-native</it>
     * transition code will consider that and eagerly return the pointer object. If {@code true} is
     * returned, the wrapper must also implement {@link #getReplacement(InteropLibrary)} which
     * returns the pointer object. Furthermore, wrappers must use
     * {@link #registerReplacement(Object, InteropLibrary)} to register the allocated native memory
     * in order that the native pointer can be resolved to the managed wrapper in the
     * <it>native-to-Python</it> transition.
     * </p>
     * 
     * @return {@code true} if the wrapper should be materialized eagerly, {@code false} otherwise.
     */
    public final boolean isReplacingWrapper() {
        return replacing;
    }

    public Object getReplacement(@SuppressWarnings("unused") InteropLibrary lib) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @TruffleBoundary
    protected final Object registerReplacement(Object pointer, InteropLibrary lib) {
        LOGGER.finest(() -> PythonUtils.formatJString("assigning %s with %s", getDelegate(), pointer));
        Object result;
        if (pointer instanceof Long lptr) {
            // need to convert to actual pointer
            result = PCallCapiFunction.callUncached(NativeCAPISymbol.FUN_CONVERT_POINTER, lptr);
            CApiTransitions.createReference(this, lptr);
        } else {
            result = pointer;
            if (lib.isPointer(pointer)) {
                assert pointer.getClass() == NativePointer.class || pointer.getClass().getSimpleName().contains("NFIPointer") || pointer.getClass().getSimpleName().contains("LLVMPointer");
                try {
                    CApiTransitions.createReference(this, lib.asPointer(pointer));
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
            } else {
                /*
                 * This branch is required for LLVM managed mode where we will never have a native
                 * pointer (i.e. an address pointing into off-heap memory) but a managed pointer to
                 * some object emulating the native memory.
                 */
                assert pointer.getClass().getSimpleName().contains("LLVMPointer");
                CApiTransitions.createManagedReference(getDelegate(), pointer);
            }
        }
        return result;
    }

    /**
     * A wrapper for a reference counted object.
     */
    public abstract static class PythonAbstractObjectNativeWrapper extends PythonNativeWrapper {
        /**
         * Reference count of an object that is only referenced by the Java heap - this is larger
         * than 1 since native code sometimes special cases for low refcounts.
         */
        public static final long MANAGED_REFCNT = 10;

        public static final long IMMORTAL_REFCNT = Long.MAX_VALUE / 2;

        protected PythonAbstractObjectNativeWrapper() {
        }

        protected PythonAbstractObjectNativeWrapper(Object delegate) {
            super(delegate, false);
        }

        protected PythonAbstractObjectNativeWrapper(Object delegate, boolean replacing) {
            super(delegate, replacing);
        }

        public final long getRefCount() {
            if (isNative()) {
                return CApiTransitions.readNativeRefCount(HandlePointerConverter.pointerToStub(getNativePointer()));
            }
            return MANAGED_REFCNT;
        }

        public final void setRefCount(Node inliningTarget, long refCount, InlinedConditionProfile hasRefProfile) {
            CApiTransitions.writeNativeRefCount(HandlePointerConverter.pointerToStub(getNativePointer()), refCount);
            updateRef(inliningTarget, refCount, hasRefProfile);
        }

        /**
         * Adjusts the native wrapper's reference to be weak (if {@code refCount <= MANAGED_REFCNT})
         * or to be strong (if {@code refCount > MANAGED_REFCNT}) if there is a reference. This
         * method should be called at appropriate points in the program, e.g., method
         * {@link #setRefCount(Node, long, InlinedConditionProfile)} uses it, or it should be called
         * from native code if the refcount falls below {@link #MANAGED_REFCNT}.
         */
        public final void updateRef(Node inliningTarget, long refCount, InlinedConditionProfile hasRefProfile) {
            if (hasRefProfile.profile(inliningTarget, ref != null)) {
                if (refCount > MANAGED_REFCNT && !ref.isStrongReference()) {
                    ref.setStrongReference(this);
                } else if (refCount <= MANAGED_REFCNT && ref.isStrongReference()) {
                    ref.setStrongReference(null);
                }
            }
        }

        @TruffleBoundary(allowInlining = true)
        public void incRef() {
            assert isNative();
            long pointer = HandlePointerConverter.pointerToStub(getNativePointer());
            long refCount = CApiTransitions.readNativeRefCount(pointer);
            if (refCount != IMMORTAL_REFCNT) {
                CApiTransitions.writeNativeRefCount(pointer, refCount + 1);
            }
            // "-1" because the refcount can briefly go below (e.g., PyTuple_SetItem)
            assert refCount >= (PythonAbstractObjectNativeWrapper.MANAGED_REFCNT - 1) : "invalid refcnt " + refCount + " during incRef in " + Long.toHexString(getNativePointer());
        }

        @TruffleBoundary(allowInlining = true)
        public long decRef() {
            long pointer = HandlePointerConverter.pointerToStub(getNativePointer());
            long refCount = CApiTransitions.readNativeRefCount(pointer);
            if (refCount != IMMORTAL_REFCNT) {
                long updatedRefCount = refCount - 1;
                CApiTransitions.writeNativeRefCount(pointer, updatedRefCount);
                // "-1" because the refcount can briefly go below (e.g., PyTuple_SetItem)
                assert updatedRefCount >= (PythonAbstractObjectNativeWrapper.MANAGED_REFCNT - 1) : "invalid refcnt " + updatedRefCount + " during decRef in " + Long.toHexString(getNativePointer());
                if (updatedRefCount == PythonAbstractObjectNativeWrapper.MANAGED_REFCNT && ref != null) {
                    // make weak
                    assert ref.isStrongReference();
                    ref.setStrongReference(null);
                }
                return updatedRefCount;
            }
            return refCount;
        }
    }

    /**
     * A wrapper for data objects usually reprsented as C structures without reference counting.
     */
    public abstract static class PythonStructNativeWrapper extends PythonNativeWrapper {

        protected PythonStructNativeWrapper() {
        }

        protected PythonStructNativeWrapper(Object delegate) {
            super(delegate, false);
        }

        protected PythonStructNativeWrapper(Object delegate, boolean replacing) {
            super(delegate, replacing);
        }
    }
}
