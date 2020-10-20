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

import com.oracle.graal.python.builtins.objects.cext.capi.CArrayWrappers.CStringWrapper;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

/**
 * Used to wrap {@link PythonClass} when used in native code. This wrapper mimics the correct shape
 * of the corresponding native type {@code struct _typeobject}.
 */
public class PythonClassNativeWrapper extends DynamicObjectNativeWrapper.PythonObjectNativeWrapper {
    private final CStringWrapper nameWrapper;
    private Object getBufferProc;
    private Object releaseBufferProc;

    private PythonClassNativeWrapper(PythonManagedClass object, String name) {
        super(object);
        this.nameWrapper = new CStringWrapper(name);
    }

    public CStringWrapper getNameWrapper() {
        return nameWrapper;
    }

    public Object getGetBufferProc() {
        return getBufferProc;
    }

    public void setGetBufferProc(Object getBufferProc) {
        this.getBufferProc = getBufferProc;
    }

    public Object getReleaseBufferProc() {
        return releaseBufferProc;
    }

    public void setReleaseBufferProc(Object releaseBufferProc) {
        this.releaseBufferProc = releaseBufferProc;
    }

    public static PythonClassNativeWrapper wrap(PythonManagedClass obj, String name) {
        // important: native wrappers are cached
        PythonClassNativeWrapper nativeWrapper = obj.getClassNativeWrapper();
        if (nativeWrapper == null) {
            nativeWrapper = new PythonClassNativeWrapper(obj, name);
            obj.setNativeWrapper(nativeWrapper);
        }
        return nativeWrapper;
    }

    public static PythonClassNativeWrapper wrapNewRef(PythonManagedClass obj, String name) {
        // important: native wrappers are cached
        PythonClassNativeWrapper nativeWrapper = obj.getClassNativeWrapper();
        if (nativeWrapper == null) {
            nativeWrapper = new PythonClassNativeWrapper(obj, name);
            obj.setNativeWrapper(nativeWrapper);
        } else {
            // it already existed, so we need to increase the reference count
            nativeWrapper.increaseRefCount();
        }
        return nativeWrapper;
    }

    @Override
    @TruffleBoundary
    public String toString() {
        PythonNativeWrapperLibrary lib = PythonNativeWrapperLibrary.getUncached();
        return String.format("PythonClassNativeWrapper(%s, isNative=%s)", lib.getDelegate(this), lib.isNative(this));
    }
}
