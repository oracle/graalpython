/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.hpy.llvm;

import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.GraalHPyHandleReference;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyHandle;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

/**
 * This class implements a simple interop array that has elements with following layout:
 *
 * <pre>
 *     [nativeSpacePtr0, destroyFun0, nativeSpacePtr1, destroyFun1, ...]
 * </pre>
 *
 * So, each element at indices {@code i % 2 == 0} is a native space pointer and each element at
 * indices {@code i % 2 == 1} is the corresponding pointer to a destroy function. On the C side,
 * this should be use like this:
 *
 * <pre>
 *     typedef void (*destroyfunc_t)(void *);
 *     void bulk_cleanup(void *nativeSpaceArrayWrapper) {
 *         int64_t n = polyglot_get_array_size(nativeSpaceArrayWrapper);
 *         void *nativeSpacePtr;
 *         destroyfunc_t destroyfunc;
 *         for (int64_t i = 0; i < n; i += 2) {
 *             nativeSpacePtr = nativeSpaceArrayWrapper[i];
 *             destroyfunc = nativeSpaceArrayWrapper[i+1];
 *             ... 
 *         }
 *     }
 * </pre>
 */
@ExportLibrary(InteropLibrary.class)
final class NativeSpaceArrayWrapper implements TruffleObject {

    final GraalHPyHandleReference[] data;

    public NativeSpaceArrayWrapper(GraalHPyHandleReference[] data) {
        this.data = data;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasArrayElements() {
        return true;
    }

    @ExportMessage
    long getArraySize() {
        return data.length * 2L;
    }

    @ExportMessage
    boolean isArrayElementReadable(long i) {
        return i < data.length * 2L;
    }

    @ExportMessage
    Object readArrayElement(long i) {
        GraalHPyHandleReference ref = data[(int) i / 2];
        if (ref != null) {
            if (i % 2 == 0) {
                return ref.getNativeSpace();
            } else {
                Object destroyFunc = ref.getDestroyFunc();
                return destroyFunc != null ? destroyFunc : GraalHPyHandle.NULL_HANDLE;
            }
        }
        /*
         * At this point, we need to return something that fulfills 'interopLib.isNull(obj)'.
         * However, it MUST NOT be any 'PythonAbstractObject' because the interop messages could try
         * to acquire the GIL.
         */
        return GraalHPyHandle.NULL_HANDLE;
    }
}
