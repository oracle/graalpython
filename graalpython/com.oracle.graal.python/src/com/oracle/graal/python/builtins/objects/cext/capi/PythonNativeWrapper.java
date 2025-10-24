/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.HandlePointerConverter;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonObjectReference;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

public abstract class PythonNativeWrapper implements TruffleObject {
    private static final long UNINITIALIZED = -1;

    private Object delegate;
    private long nativePointer = UNINITIALIZED;

    public PythonObjectReference ref;

    private PythonNativeWrapper() {
    }

    private PythonNativeWrapper(Object delegate) {
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
     * A wrapper for a reference counted object.
     */
    public abstract static class PythonAbstractObjectNativeWrapper extends PythonNativeWrapper {
        /**
         * Reference count of an object that is only referenced by the Java heap - this is larger
         * than 1 since native code sometimes special cases for low refcounts.
         */
        public static final long MANAGED_REFCNT = 10;

        public static final long IMMORTAL_REFCNT = 0xFFFFFFFFL; // from include/object.h

        protected PythonAbstractObjectNativeWrapper() {
        }

        protected PythonAbstractObjectNativeWrapper(Object delegate) {
            super(delegate);
        }

        public final long getRefCount() {
            if (isNative()) {
                return CApiTransitions.readNativeRefCount(HandlePointerConverter.pointerToStub(getNativePointer()));
            }
            return MANAGED_REFCNT;
        }

        public long incRef() {
            assert isNative();
            long pointer = HandlePointerConverter.pointerToStub(getNativePointer());
            long refCount = CApiTransitions.readNativeRefCount(pointer);
            assert refCount >= PythonAbstractObjectNativeWrapper.MANAGED_REFCNT : "invalid refcnt " + refCount + " during incRef in " + Long.toHexString(getNativePointer());
            if (refCount != IMMORTAL_REFCNT) {
                CApiTransitions.writeNativeRefCount(pointer, refCount + 1);
                return refCount + 1;
            }
            return IMMORTAL_REFCNT;
        }

        public long decRef() {
            assert isNative();
            long pointer = HandlePointerConverter.pointerToStub(getNativePointer());
            long refCount = CApiTransitions.readNativeRefCount(pointer);
            if (refCount != IMMORTAL_REFCNT) {
                long updatedRefCount = refCount - 1;
                CApiTransitions.writeNativeRefCount(pointer, updatedRefCount);
                assert updatedRefCount >= PythonAbstractObjectNativeWrapper.MANAGED_REFCNT : "invalid refcnt " + updatedRefCount + " during decRef in " + Long.toHexString(getNativePointer());
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
            super(delegate);
        }
    }
}
