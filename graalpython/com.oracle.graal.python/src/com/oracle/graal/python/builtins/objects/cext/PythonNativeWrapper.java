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

import java.lang.ref.WeakReference;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

@ExportLibrary(PythonNativeWrapperLibrary.class)
public abstract class PythonNativeWrapper implements TruffleObject {

    private Object delegate;
    private Object nativePointer;

    /** equivalent to {@code ob_refcnt}; needed to deallocate handles */
    private int refCount;

    public PythonNativeWrapper() {
    }

    public PythonNativeWrapper(Object delegate) {
        this.delegate = delegate;
    }

    public final void increaseRefCount() {
        refCount++;
    }

    public final int decreaseRefCount() {
        return --refCount;
    }

    public final int getRefCount() {
        return refCount;
    }

    public final void setRefCount(int refCount) {
        this.refCount = refCount;
    }

    protected static Assumption singleContextAssumption() {
        return PythonLanguage.getCurrent().singleContextAssumption;
    }

    protected static final boolean isEq(Object obj, Object obj2) {
        return obj == obj2;
    }

    protected static WeakReference<PythonNativeWrapper> weak(PythonNativeWrapper wrapper) {
        return new WeakReference<>(wrapper);
    }

    @ExportMessage(name = "getDelegate")
    protected static class GetDelegate {
        @Specialization(guards = {"isEq(cachedWrapper.get(), wrapper)", "!isEq(delegate.get(), null)"}, assumptions = "singleContextAssumption()")
        protected static Object getCachedDel(@SuppressWarnings("unused") PythonNativeWrapper wrapper,
                        @Exclusive @SuppressWarnings("unused") @Cached("weak(wrapper)") WeakReference<PythonNativeWrapper> cachedWrapper,
                        @Cached("wrapper.getDelegatePrivate()") WeakReference<Object> delegate) {
            return delegate.get();
        }

        @Specialization(replaces = "getCachedDel")
        protected static Object getGenericDel(PythonNativeWrapper wrapper) {
            return wrapper.delegate;
        }
    }

    protected final WeakReference<Object> getDelegatePrivate() {
        return new WeakReference<>(delegate);
    }

    protected void setDelegate(Object delegate) {
        assert this.delegate == null || this.delegate == delegate;
        this.delegate = delegate;
    }

    @ExportMessage(name = "getNativePointer")
    protected static class GetNativePointer {
        @Specialization(guards = {"isEq(cachedWrapper.get(), wrapper)", "!isEq(nativePointer.get(), null)"}, assumptions = "singleContextAssumption()")
        protected static Object getCachedPtr(@SuppressWarnings("unused") PythonNativeWrapper wrapper,
                        @Exclusive @SuppressWarnings("unused") @Cached("weak(wrapper)") WeakReference<PythonNativeWrapper> cachedWrapper,
                        @Exclusive @Cached("wrapper.getNativePointerPrivate()") WeakReference<Object> nativePointer) {
            return nativePointer.get();
        }

        @Specialization(replaces = "getCachedPtr")
        protected static Object getGenericPtr(PythonNativeWrapper wrapper) {
            return wrapper.nativePointer;
        }
    }

    protected final WeakReference<Object> getNativePointerPrivate() {
        return new WeakReference<>(nativePointer);
    }

    public void setNativePointer(Object nativePointer) {
        // we should set the pointer just once
        assert this.nativePointer == null || this.nativePointer.equals(nativePointer) || nativePointer == null;

        // we must not set the pointer for one of the context-insensitive singletons
        assert PythonLanguage.getSingletonNativePtrIdx(delegate) == -1;

        this.nativePointer = nativePointer;
    }

    @ExportMessage(name = "isNative")
    protected static class IsNative {
        @Specialization(guards = {"isEq(cachedWrapper.get(), wrapper)", "!isEq(nativePointer.get(), null)"}, assumptions = "singleContextAssumption()")
        protected static boolean isCachedNative(@SuppressWarnings("unused") PythonNativeWrapper wrapper,
                        @Exclusive @SuppressWarnings("unused") @Cached("weak(wrapper)") WeakReference<PythonNativeWrapper> cachedWrapper,
                        @Exclusive @SuppressWarnings("unused") @Cached("wrapper.getNativePointerPrivate()") WeakReference<Object> nativePointer) {
            return true;
        }

        @Specialization(replaces = "isCachedNative")
        protected static boolean isNative(PythonNativeWrapper wrapper) {
            return wrapper.nativePointer != null;
        }
    }
}
