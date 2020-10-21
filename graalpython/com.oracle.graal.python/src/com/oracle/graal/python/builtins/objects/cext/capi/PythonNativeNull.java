/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.utilities.TriState;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;

/**
 * A simple wrapper around native {@code NULL}.
 */
@ExportLibrary(InteropLibrary.class)
@ExportLibrary(NativeTypeLibrary.class)
public class PythonNativeNull implements TruffleObject {
    private Object ptr;

    public void setPtr(Object object) {
        this.ptr = object;
    }

    public Object getPtr() {
        return ptr;
    }

    @ExportMessage
    boolean isPointer() {
        return true;
    }

    @ExportMessage(limit = "1")
    long asPointer(
                    @CachedLibrary("this.getPtr()") InteropLibrary ptrInteropLib) throws UnsupportedMessageException {
        return ptrInteropLib.asPointer(getPtr());
    }

    @ExportMessage
    boolean isNull() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    protected boolean hasNativeType() {
        // this is '((void*)0x0)', so no type
        return false;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public Object getNativeType() {
        return null;
    }

    @ExportMessage(limit = "1")
    int identityHashCode(@CachedLibrary("this.getPtr()") InteropLibrary lib) throws UnsupportedMessageException {
        return lib.identityHashCode(ptr);
    }

    @ExportMessage(limit = "1")
    boolean isIdentical(Object other, InteropLibrary otherLib,
                    @CachedLibrary("this.getPtr()") InteropLibrary lib) {
        return lib.isIdentical(ptr, other, otherLib);
    }

    @SuppressWarnings({"unused", "static-method"})
    @ExportMessage
    TriState isIdenticalOrUndefined(Object other) {
        String msg = "cannot delegate isIdenticalOrUndefined for null properly";
        CompilerDirectives.bailout(msg);
        throw new AssertionError(msg);
    }
}
