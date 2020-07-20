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
package com.oracle.graal.python.builtins.objects.cext;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

@ExportLibrary(PythonNativeWrapperLibrary.class)
public abstract class PythonNativeWrapper implements TruffleObject {

    private Object delegate;
    private Object nativePointer;

    /**
     * Equivalent to {@code ob_refcnt}. We also need to maintain the reference count for native
     * wrappers because otherwise we can never free the handles. The initial value is set to
     * {@code 1} because each object has just one wrapper and when the wrapper is created, the
     * object already exists which means in CPython the {@code PyObject_Init} would already have
     * been called. The object init function sets the reference count to one.
     */
    private long refCount = 1;

    /**
     * An assumption that can be used by caches to assume that the associated {@link #nativePointer}
     * is still valid. This assumption will be invalidated when {@link #refCount} becomes zero and
     * the object is deallocated on the native side.
     */
    private Assumption handleValidAssumption;

    public PythonNativeWrapper() {
    }

    public PythonNativeWrapper(Object delegate) {
        this.delegate = delegate;
    }

    public final void increaseRefCount() {
        refCount++;
    }

    public final long decreaseRefCount() {
        return --refCount;
    }

    public final long getRefCount() {
        return refCount;
    }

    public final void setRefCount(long refCount) {
        this.refCount = refCount;
    }

    @TruffleBoundary
    public static void invalidateAssumption(Assumption handleValidAssumption) {
        handleValidAssumption.invalidate("releasing handle for native wrapper");
    }

    public final Assumption getHandleValidAssumption() {
        return handleValidAssumption;
    }

    public final Assumption ensureHandleValidAssumption() {
        CompilerAsserts.neverPartOfCompilation();
        if (handleValidAssumption == null) {
            handleValidAssumption = Truffle.getRuntime().createAssumption();
        }
        return handleValidAssumption;
    }

    protected static Assumption singleContextAssumption() {
        return PythonLanguage.getCurrent().singleContextAssumption;
    }

    @ExportMessage(name = "getDelegate")
    protected static class GetDelegate {
        @Specialization(guards = {"cachedWrapper == wrapper", "delegate != null"}, assumptions = "singleContextAssumption()")
        protected static Object getCachedDel(@SuppressWarnings("unused") PythonNativeWrapper wrapper,
                        @SuppressWarnings("unused") @Cached(value = "wrapper", weak = true) PythonNativeWrapper cachedWrapper,
                        @Cached(value = "wrapper.getDelegatePrivate()", weak = true) Object delegate) {
            return delegate;
        }

        @Specialization(replaces = "getCachedDel")
        protected static Object getGenericDel(PythonNativeWrapper wrapper) {
            return wrapper.delegate;
        }
    }

    protected final Object getDelegatePrivate() {
        return delegate;
    }

    protected void setDelegate(Object delegate) {
        assert this.delegate == null || this.delegate == delegate;
        this.delegate = delegate;
    }

    @ExportMessage(name = "getNativePointer")
    protected static class GetNativePointer {
        @Specialization(guards = {"cachedWrapper == wrapper", "nativePointer != null"}, assumptions = {"singleContextAssumption()", "cachedAssumption"})
        protected static Object getCachedPtr(@SuppressWarnings("unused") PythonNativeWrapper wrapper,
                        @SuppressWarnings("unused") @Cached(value = "wrapper", weak = true) PythonNativeWrapper cachedWrapper,
                        @SuppressWarnings("unused") @Cached("wrapper.getHandleValidAssumption()") Assumption cachedAssumption,
                        @Cached(value = "wrapper.getNativePointerPrivate()", weak = true) Object nativePointer) {
            return nativePointer;
        }

        @Specialization(replaces = "getCachedPtr")
        protected static Object getGenericPtr(PythonNativeWrapper wrapper) {
            return wrapper.nativePointer;
        }
    }

    protected final Object getNativePointerPrivate() {
        return nativePointer;
    }

    public void setNativePointer(Object nativePointer) {
        // we should set the pointer just once
        assert this.nativePointer == null || this.nativePointer.equals(nativePointer) || nativePointer == null;
        if (nativePointer == null) {
            this.handleValidAssumption = null;
        }
        this.nativePointer = nativePointer;
    }

    @ExportMessage(name = "isNative")
    protected static class IsNative {
        @Specialization(guards = {"cachedWrapper == wrapper", "nativePointer != null"}, assumptions = {"singleContextAssumption()", "cachedAssumption"})
        protected static boolean isCachedNative(@SuppressWarnings("unused") PythonNativeWrapper wrapper,
                        @SuppressWarnings("unused") @Cached(value = "wrapper", weak = true) PythonNativeWrapper cachedWrapper,
                        @SuppressWarnings("unused") @Cached("wrapper.getHandleValidAssumption()") Assumption cachedAssumption,
                        @SuppressWarnings("unused") @Cached(value = "wrapper.getNativePointerPrivate()", weak = true) Object nativePointer) {
            return true;
        }

        @Specialization(replaces = "isCachedNative")
        protected static boolean isNative(PythonNativeWrapper wrapper) {
            if (wrapper.nativePointer != null) {
                Assumption handleValidAssumption = wrapper.getHandleValidAssumption();
                // If an assumption exists, it must be valid
                return handleValidAssumption == null || handleValidAssumption.isValid();
            }
            return false;
        }
    }
}
