/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols.FUN_GET_BYTE_ARRAY_TYPE_ID;

import com.oracle.graal.python.builtins.objects.cext.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;

/**
 * Native wrappers for managed objects such that they can be used as a C array by native code. The
 * major difference to other native wrappers is that they are copied to native memory if it receives
 * {@code TO_NATIVE}. This is primarily necessary for {@code char*} arrays.
 */
public abstract class CArrayWrappers {

    @ExportLibrary(InteropLibrary.class)
    public abstract static class CArrayWrapper extends PythonNativeWrapper {

        public CArrayWrapper(Object delegate) {
            super(delegate);
        }

        @ExportMessage
        public boolean isPointer(
                        @Exclusive @Cached CExtNodes.IsPointerNode pIsPointerNode) {
            return pIsPointerNode.execute(this);
        }

        @ExportMessage
        public long asPointer(
                        @CachedLibrary("this") PythonNativeWrapperLibrary lib,
                        @CachedLibrary(limit = "1") InteropLibrary interopLibrary) throws UnsupportedMessageException {
            Object nativePointer = lib.getNativePointer(this);
            if (nativePointer instanceof Long) {
                return (long) nativePointer;
            }
            return interopLibrary.asPointer(nativePointer);
        }

        @ExportMessage
        public void toNative(
                        @CachedLibrary("this") PythonNativeWrapperLibrary lib,
                        @Exclusive @Cached CExtNodes.AsCharPointerNode asCharPointerNode,
                        @Exclusive @Cached InvalidateNativeObjectsAllManagedNode invalidateNode) {
            invalidateNode.execute();
            if (!lib.isNative(this)) {
                setNativePointer(asCharPointerNode.execute(lib.getDelegate(this)));
            }
        }
    }

    /**
     * Unlike a
     * {@link com.oracle.graal.python.builtins.objects.cext.DynamicObjectNativeWrapper.PythonObjectNativeWrapper}
     * object that wraps a Python unicode object, this wrapper let's a Java String look like a
     * {@code char*}.
     */
    @ExportLibrary(InteropLibrary.class)
    @ExportLibrary(NativeTypeLibrary.class)
    public static class CStringWrapper extends CArrayWrapper {

        public CStringWrapper(String delegate) {
            super(delegate);
        }

        @ExportMessage
        final long getArraySize(
                        @CachedLibrary("this") PythonNativeWrapperLibrary lib) {
            return ((String) lib.getDelegate(this)).length();
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        final boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        final Object readArrayElement(long index,
                        @CachedLibrary("this") PythonNativeWrapperLibrary lib) throws InvalidArrayIndexException {
            try {
                int idx = PInt.intValueExact(index);
                String s = (String) lib.getDelegate(this);
                if (idx >= 0 && idx < s.length()) {
                    return s.charAt(idx);
                } else if (idx == s.length()) {
                    return '\0';
                }
            } catch (ArithmeticException e) {
                // fall through
            }
            throw InvalidArrayIndexException.create(index);
        }

        @ExportMessage
        final boolean isArrayElementReadable(long identifier,
                        @CachedLibrary("this") PythonNativeWrapperLibrary lib) {
            return 0 <= identifier && identifier < getArraySize(lib);
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        protected boolean hasNativeType() {
            return true;
        }

        @ExportMessage
        protected Object getNativeType(
                        @CachedLibrary("this") PythonNativeWrapperLibrary lib,
                        @Exclusive @Cached PCallCapiFunction callByteArrayTypeIdNode) {
            return callByteArrayTypeIdNode.call(FUN_GET_BYTE_ARRAY_TYPE_ID, ((String) lib.getDelegate(this)).length());
        }
    }

    /**
     * A native wrapper for arbitrary byte arrays (i.e. the store of a Python Bytes object) to be
     * used like a {@code char*} pointer.
     */
    @ExportLibrary(InteropLibrary.class)
    @ExportLibrary(NativeTypeLibrary.class)
    public static class CByteArrayWrapper extends CArrayWrapper {

        public CByteArrayWrapper(byte[] delegate) {
            super(delegate);
        }

        public final byte[] getByteArray(PythonNativeWrapperLibrary lib) {
            return ((byte[]) lib.getDelegate(this));
        }

        @ExportMessage
        final long getArraySize(@CachedLibrary("this") PythonNativeWrapperLibrary lib) {
            return getByteArray(lib).length;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        final boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        Object readArrayElement(long index,
                        @CachedLibrary("this") PythonNativeWrapperLibrary lib) throws InvalidArrayIndexException {
            try {
                int idx = PInt.intValueExact(index);
                byte[] arr = getByteArray(lib);
                if (idx >= 0 && idx < arr.length) {
                    return arr[idx];
                } else if (idx == arr.length) {
                    return (byte) 0;
                }
            } catch (ArithmeticException e) {
                // fall through
            }
            throw InvalidArrayIndexException.create(index);
        }

        @ExportMessage
        final boolean isArrayElementReadable(long identifier,
                        @CachedLibrary("this") PythonNativeWrapperLibrary lib) {
            return 0 <= identifier && identifier < getArraySize(lib);
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        protected boolean hasNativeType() {
            // TODO implement native type
            return false;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        protected Object getNativeType() {
            // TODO implement native type
            return null;
        }
    }
}
