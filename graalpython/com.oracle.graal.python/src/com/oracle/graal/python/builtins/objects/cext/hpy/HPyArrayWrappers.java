/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.hpy;

import java.util.Arrays;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.InvalidateNativeObjectsAllManagedNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.PCallHPyFunction;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;

public class HPyArrayWrappers {

    @ExportLibrary(InteropLibrary.class)
    abstract static class HPyObjectArrayWrapper implements TruffleObject {

        private Object[] delegate;
        private Object nativePointer;

        HPyObjectArrayWrapper(Object[] delegate) {
            this.delegate = delegate;
        }

        HPyObjectArrayWrapper(int capacity) {
            this.delegate = new Object[capacity];
        }

        public Object[] getDelegate() {
            return delegate;
        }

        void setDelegate(Object[] delegate) {
            this.delegate = delegate;
        }

        void setNativePointer(Object nativePointer) {
            this.nativePointer = nativePointer;
        }

        Object getNativePointer() {
            return this.nativePointer;
        }

        @Override
        public int hashCode() {
            CompilerAsserts.neverPartOfCompilation();
            final int prime = 31;
            int result = 1;
            result = prime * result + Arrays.hashCode(delegate);
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            // n.b.: (tfel) This is hopefully fine here, since if we get to this
            // code path, we don't speculate that either of those objects is
            // constant anymore, so any caching on them won't happen anyway
            return delegate == ((HPyObjectArrayWrapper) obj).delegate;
        }

        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        long getArraySize(
                        @Shared("lib") @CachedLibrary(limit = "1") InteropLibrary lib) throws UnsupportedMessageException {
            if (delegate != null) {
                return delegate.length;
            }
            return lib.getArraySize(nativePointer);
        }

        @ExportMessage
        boolean isArrayElementReadable(long idx,
                        @Shared("lib") @CachedLibrary(limit = "1") InteropLibrary lib) {
            if (delegate != null) {
                return 0 <= idx && idx < delegate.length;
            }
            return lib.isArrayElementReadable(nativePointer, idx);
        }

        @ExportMessage
        boolean isArrayElementModifiable(long idx,
                        @Shared("lib") @CachedLibrary(limit = "1") InteropLibrary lib) {
            return isArrayElementReadable(idx, lib);
        }

        @ExportMessage
        boolean isArrayElementInsertable(@SuppressWarnings("unused") long idx) {
            return false;
        }

        @ExportMessage
        Object readArrayElement(@SuppressWarnings("unused") long idx) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new AbstractMethodError();
        }

        @ExportMessage
        void writeArrayElement(long idx, Object value,
                        @Shared("lib") @CachedLibrary(limit = "1") InteropLibrary lib) throws UnsupportedMessageException, InvalidArrayIndexException, UnsupportedTypeException {
            if (!isPointer()) {
                delegate[(int) idx] = value;
            } else {
                lib.writeArrayElement(nativePointer, idx, value);
            }
        }

        @ExportMessage
        boolean isPointer() {
            return nativePointer != null;
        }

        @ExportMessage
        long asPointer(
                        @Shared("lib") @CachedLibrary(limit = "1") InteropLibrary lib) throws UnsupportedMessageException {
            return lib.asPointer(nativePointer);
        }
    }

    /**
     * Wraps a sequence object (like a list) such that it behaves like a {@code HPy} array (C type
     * {@code HPy *}).
     */
    @ExportLibrary(InteropLibrary.class)
    @ExportLibrary(NativeTypeLibrary.class)
    static final class HPyArrayWrapper extends HPyObjectArrayWrapper {

        public HPyArrayWrapper(Object[] delegate) {
            super(delegate);
        }

        @ExportMessage
        Object readArrayElement(long index,
                        @Shared("lib") @CachedLibrary(limit = "1") InteropLibrary lib,
                        @Cached GraalHPyNodes.HPyAsHandleNode asHandleNode) throws InvalidArrayIndexException, UnsupportedMessageException {

            assert 0 <= index && index < getArraySize(lib);
            if (!isPointer()) {
                return asHandleNode.execute(getDelegate()[(int) index]);
            }
            // This reads directly from the native array; so no conversion necessary
            return lib.readArrayElement(getNativePointer(), index);
        }

        @ExportMessage
        void toNative(
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @Cached GraalHPyNodes.PCallHPyFunction callToArrayNode,
                        @Cached.Exclusive @Cached InvalidateNativeObjectsAllManagedNode invalidateNode) {
            invalidateNode.execute();
            if (!isPointer()) {
                setNativePointer(callToArrayNode.call(context.getHPyContext(), GraalHPyNativeSymbols.GRAAL_HPY_ARRAY_TO_NATIVE, this, (long) getDelegate().length));
                setDelegate(null);
            }
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasNativeType() {
            return true;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        Object getNativeType(
                        @CachedContext(PythonLanguage.class) PythonContext context) {
            return context.getHPyContext().getHPyArrayNativeType();
        }
    }

    @ExportLibrary(NativeTypeLibrary.class)
    @ExportLibrary(InteropLibrary.class)
    static final class PtrArrayWrapper extends HPyObjectArrayWrapper {

        PtrArrayWrapper(int capacity) {
            super(capacity);
        }

        @ExportMessage
        Object readArrayElement(long idx,
                        @Shared("lib") @CachedLibrary(limit = "1") InteropLibrary lib) throws InvalidArrayIndexException, UnsupportedMessageException {
            if (!isPointer()) {
                Object result = getDelegate()[(int) idx];
                if (result == null) {
                    // TODO(fa): not sure if this is a good idea but it will do the job since it
                    // reports `isNull == true`
                    return PNone.NO_VALUE;
                }
                return result;
            }
            return lib.readArrayElement(getNativePointer(), idx);
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasNativeType() {
            return false;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        Object getNativeType() {
            return null;
        }

        @ExportMessage
        void toNative(
                        @CachedContext(PythonLanguage.class) PythonContext context,
                        @Cached PCallHPyFunction callHPyFunction) {
            if (!isPointer()) {
                setNativePointer(callHPyFunction.call(context.getHPyContext(), GraalHPyNativeSymbols.GRAAL_HPY_POINTER_ARRAY_TO_NATIVE, this, (long) getDelegate().length));
                setDelegate(null);
            }
        }
    }
}
