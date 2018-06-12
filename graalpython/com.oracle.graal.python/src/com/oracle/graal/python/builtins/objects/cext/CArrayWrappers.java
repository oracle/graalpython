/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 *     one is included with the Software (each a "Larger Work" to which the
 *     Software is contributed by such licensors),
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

import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.TruffleObject;

/**
 * Native wrappers for managed objects such that they can be used as a C array by native code. The
 * major difference to other native wrappers is that they are copied to native memory if it receives
 * {@code TO_NATIVE}. This is primarily necessary for {@code char*} arrays.
 */
public abstract class CArrayWrappers {
    public static class CArrayWrapper<T> implements TruffleObject {

        private final T delegate;
        private Object nativePointer;

        public CArrayWrapper(T delegate) {
            this.delegate = delegate;
        }

        public T getDelegate() {
            return delegate;
        }

        public Object getNativePointer() {
            return nativePointer;
        }

        public void setNativePointer(Object nativePointer) {
            assert this.nativePointer == null;
            this.nativePointer = nativePointer;
        }

        public boolean isNative() {
            return nativePointer != null;
        }

        static boolean isInstance(TruffleObject o) {
            return o instanceof CArrayWrapper;
        }

        public ForeignAccess getForeignAccess() {
            return CStringWrapperMRForeign.ACCESS;
        }
    }

    /**
     * Unlike a
     * {@link com.oracle.graal.python.builtins.objects.cext.NativeWrappers.PythonObjectNativeWrapper}
     * object that wraps a Python unicode object, this wrapper let's a Java String look like a
     * {@code char*}.
     */
    public static class CStringWrapper extends CArrayWrapper<String> {

        public CStringWrapper(String delegate) {
            super(delegate);
        }
    }

    /**
     * A native wrapper for arbitrary byte arrays (i.e. the store of a Python Bytes object) to be
     * used like a {@code char*} pointer.
     */
    public static class CByteArrayWrapper extends CArrayWrapper<byte[]> {

        public CByteArrayWrapper(byte[] delegate) {
            super(delegate);
        }
    }
}