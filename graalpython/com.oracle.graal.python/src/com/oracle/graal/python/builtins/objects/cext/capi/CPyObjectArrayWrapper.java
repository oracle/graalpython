/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ReleaseNativeWrapperNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNode;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

import sun.misc.Unsafe;

/**
 * A native wrapper for Python object arrays to be used like a {@code PyObject *arr[]}.
 */
@ExportLibrary(InteropLibrary.class)
public final class CPyObjectArrayWrapper extends PythonNativeWrapper {

    private static final Unsafe UNSAFE = PythonUtils.initUnsafe();

    @TruffleBoundary
    private static long allocateBoundary(long size) {
        return UNSAFE.allocateMemory(size);
    }

    @TruffleBoundary
    private static void freeBoundary(long ptr) {
        UNSAFE.freeMemory(ptr);
    }

    private final Object[] wrappers;

    public CPyObjectArrayWrapper(Object[] delegate) {
        super(delegate);
        wrappers = new Object[delegate.length];
    }

    public Object[] getObjectArray() {
        return ((Object[]) getDelegate());
    }

    @ExportMessage
    boolean isPointer() {
        return isNative();
    }

    @ExportMessage
    long asPointer() {
        return getNativePointer();
    }

    @ExportMessage
    long getArraySize() {
        return wrappers.length;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasArrayElements() {
        return true;
    }

    @ExportMessage
    Object readArrayElement(long index,
                    @Shared("toNativeNode") @Cached PythonToNativeNode toNativeNode) throws InvalidArrayIndexException {
        try {
            int idx = PInt.intValueExact(index);
            if (idx >= 0 && idx < wrappers.length) {
                if (wrappers[idx] == null) {
                    Object[] arr = getObjectArray();
                    wrappers[idx] = toNativeNode.execute(arr[idx]);
                }
                return wrappers[idx];
            }
        } catch (OverflowException e) {
            // fall through
        }
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw InvalidArrayIndexException.create(index);
    }

    @ExportMessage
    boolean isArrayElementReadable(long identifier) {
        return 0 <= identifier && identifier < wrappers.length;
    }

    /**
     * Copies a Java {@code Object[]} to a native {@code PyObject *arr[]}. For this, the native
     * memory is allocated off-heap using {@code Unsafe}.
     */
    @ExportMessage
    void toNative(
                    @Shared("toNativeNode") @Cached PythonToNativeNode toNativeNode,
                    @CachedLibrary(limit = "3") InteropLibrary interopLib) {
        if (!PythonContext.get(toNativeNode).isNativeAccessAllowed()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new RuntimeException(ErrorMessages.NATIVE_ACCESS_NOT_ALLOWED.toJavaStringUncached());
        }
        if (!isNative()) {
            Object[] data = getObjectArray();
            long ptr = allocateBoundary((long) wrappers.length * Long.BYTES);
            try {
                for (int i = 0; i < data.length; i++) {
                    if (wrappers[i] == null) {
                        wrappers[i] = toNativeNode.execute(data[i]);
                    }
                    // we need a pointer, so manually send toNative
                    interopLib.toNative(wrappers[i]);
                    UNSAFE.putLong(ptr + (long) i * Long.BYTES, interopLib.asPointer(wrappers[i]));
                }
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
            setNativePointer(ptr);
        }
    }

    public void free(ReleaseNativeWrapperNode releaseNativeWrapperNode) {
        /*
         * TODO we currently don't implement immediate releases of wrappers.
         *
         * If we ever do and we incref items we put in the wrappers array, we need to be careful
         * with native objects. They would need to be decref'd here and the commented out code below
         * doesn't do this.
         */
        // for (int i = 0; i < wrappers.length; i++) {
        // releaseNativeWrapperNode.execute(wrappers[i]);
        // }
        if (isNative()) {
            if (!PythonContext.get(releaseNativeWrapperNode).isNativeAccessAllowed()) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new RuntimeException(ErrorMessages.NATIVE_ACCESS_NOT_ALLOWED.toJavaStringUncached());
            }
            freeBoundary(getNativePointer());
        }
    }
}
