/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext.LLVMType;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.GetLLVMType;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.IsPointerNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ReleaseNativeWrapperNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ToNewRefNode;
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
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;

import sun.misc.Unsafe;

/**
 * A native wrapper for Python object arrays to be used like a {@code PyObject *arr[]}.
 */
@ExportLibrary(InteropLibrary.class)
@ExportLibrary(value = NativeTypeLibrary.class, useForAOT = false)
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

    public Object[] getObjectArray(PythonNativeWrapperLibrary lib) {
        return ((Object[]) lib.getDelegate(this));
    }

    @ExportMessage
    boolean isPointer(
                    @Cached IsPointerNode pIsPointerNode) {
        return pIsPointerNode.execute(this);
    }

    @ExportMessage
    long asPointer(
                    @CachedLibrary("this") PythonNativeWrapperLibrary lib) {
        Object nativePointer = lib.getNativePointer(this);
        assert nativePointer instanceof Long;
        return (long) nativePointer;
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
                    @CachedLibrary("this") PythonNativeWrapperLibrary lib,
                    @Shared("toNewRefNode") @Cached ToNewRefNode toNewRefNode) throws InvalidArrayIndexException {
        try {
            int idx = PInt.intValueExact(index);
            if (idx >= 0 && idx < wrappers.length) {
                if (wrappers[idx] == null) {
                    Object[] arr = getObjectArray(lib);
                    wrappers[idx] = toNewRefNode.execute(arr[idx]);
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

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasNativeType() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    Object getNativeType(
                    @Cached GetLLVMType getLLVMType) {
        return getLLVMType.execute(LLVMType.PyObject_ptr_ptr_t);
    }

    /**
     * Copies a Java {@code Object[]} to a native {@code PyObject *arr[]}. For this, the native
     * memory is allocated off-heap using {@code Unsafe}.
     */
    @ExportMessage
    void toNative(
                    @CachedLibrary("this") PythonNativeWrapperLibrary lib,
                    @Cached InvalidateNativeObjectsAllManagedNode invalidateNode,
                    @Shared("toNewRefNode") @Cached ToNewRefNode toNewRefNode,
                    @CachedLibrary(limit = "3") InteropLibrary interopLib) {
        if (!PythonContext.get(lib).isNativeAccessAllowed()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new RuntimeException(ErrorMessages.NATIVE_ACCESS_NOT_ALLOWED.toJavaStringUncached());
        }
        invalidateNode.execute();
        if (!lib.isNative(this)) {
            Object[] data = getObjectArray(lib);
            long ptr = allocateBoundary((long) wrappers.length * Long.BYTES);
            try {
                for (int i = 0; i < data.length; i++) {
                    if (wrappers[i] == null) {
                        wrappers[i] = toNewRefNode.execute(data[i]);
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

    public void free(PythonNativeWrapperLibrary lib, ReleaseNativeWrapperNode releaseNativeWrapperNode) {
        for (int i = 0; i < wrappers.length; i++) {
            releaseNativeWrapperNode.execute(wrappers[i]);
        }
        if (lib.isNative(this)) {
            if (!PythonContext.get(lib).isNativeAccessAllowed()) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new RuntimeException(ErrorMessages.NATIVE_ACCESS_NOT_ALLOWED.toJavaStringUncached());
            }
            freeBoundary((long) lib.getNativePointer(this));
        }
    }
}
