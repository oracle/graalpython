/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.builtins.objects.cext.InvalidateNativeObjectsAllManagedNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyAsHandleNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.PCallHPyFunction;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;

/**
 * Wraps a sequence object (like a list) such that it behaves like a {@code HPy} array (C type
 * {@code HPy *}).
 */
@ExportLibrary(InteropLibrary.class)
@ExportLibrary(NativeTypeLibrary.class)
final class HPyArrayWrapper implements TruffleObject {

    private final Object[] delegate;
    private Object nativePointer;

    public HPyArrayWrapper(Object[] delegate) {
        this.delegate = delegate;
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
        return delegate == ((HPyArrayWrapper) obj).delegate;
    }

    @ExportMessage
    final long getArraySize() {
        return delegate.length;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    final boolean hasArrayElements() {
        return true;
    }

    @ExportMessage
    final Object readArrayElement(long index,
                    @Cached HPyAsHandleNode asHandleNode) {

        assert 0 <= index && index < delegate.length;
        return asHandleNode.execute(delegate[(int) index]);
    }

    @ExportMessage
    final boolean isArrayElementReadable(long identifier) {
        return 0 <= identifier && identifier < getArraySize();
    }

    @ExportMessage
    public void toNative(
                    @CachedContext(PythonLanguage.class) PythonContext context,
                    @Cached PCallHPyFunction callToArrayNode,
                    @Exclusive @Cached InvalidateNativeObjectsAllManagedNode invalidateNode) {
        invalidateNode.execute();
        if (nativePointer == null) {
            long size = getArraySize();
            this.nativePointer = callToArrayNode.call(context.getHPyContext(), GraalHPyNativeSymbols.GRAAL_HPY_ARRAY_TO_NATIVE, delegate, size);
        }
    }

    @ExportMessage
    public boolean isPointer(
                    @CachedLibrary(limit = "1") InteropLibrary interopLibrary) {
        if (nativePointer instanceof Long) {
            return true;
        }
        return interopLibrary.isPointer(nativePointer);
    }

    @ExportMessage
    public long asPointer(
                    @CachedLibrary(limit = "1") InteropLibrary interopLibrary) throws UnsupportedMessageException {
        if (nativePointer instanceof Long) {
            return (long) nativePointer;
        }
        return interopLibrary.asPointer(nativePointer);
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    protected boolean hasNativeType() {
        return true;
    }

    @ExportMessage
    Object getNativeType(
                    @CachedContext(PythonLanguage.class) PythonContext context) {
        return context.getHPyContext().getHPyArrayNativeType();
    }
}
