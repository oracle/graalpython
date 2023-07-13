/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
// skip GIL
package com.oracle.graal.python.builtins.objects.cext.capi;

import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions;
import com.oracle.graal.python.builtins.objects.cext.common.NativePointer;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

@ExportLibrary(InteropLibrary.class)
public abstract class PythonReplacingNativeWrapper extends PythonNativeWrapper {

    private static final TruffleLogger LOGGER = CApiContext.getLogger(PythonNativeWrapper.class);

    protected Object replacement;

    public PythonReplacingNativeWrapper() {
        // empty
    }

    public PythonReplacingNativeWrapper(Object delegate) {
        super(delegate);
    }

    public final Object getReplacement() {
        return replacement;
    }

    protected final void setReplacement(Object pointer, InteropLibrary lib) {
        LOGGER.finest(() -> PythonUtils.formatJString("assigning %s with %s", getDelegate(), pointer));
        if (pointer instanceof Long) {
            // need to convert to actual pointer
            replacement = PCallCapiFunction.getUncached().call(NativeCAPISymbol.FUN_CONVERT_POINTER, pointer);
            CApiTransitions.firstToNative(this, (long) pointer);
        } else {
            replacement = pointer;
            if (lib.isPointer(pointer)) {
                assert pointer.getClass() == NativePointer.class || pointer.getClass().getSimpleName().contains("NFIPointer") || pointer.getClass().getSimpleName().contains("LLVMPointer");
                try {
                    CApiTransitions.firstToNative(this, lib.asPointer(pointer));
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
            } else {
                assert pointer.getClass().getSimpleName().contains("LLVMPointer");
                CApiTransitions.firstToNativeManaged(getDelegate(), pointer);
            }
        }
    }

    @ExportMessage
    boolean isPointer() {
        return isNative();
    }

    @ExportMessage
    long asPointer() {
        assert getNativePointer() != -1;
        return getNativePointer();
    }

    protected abstract Object allocateReplacememtObject();

    @ExportMessage
    @TruffleBoundary
    protected void toNative(
                    @CachedLibrary(limit = "3") InteropLibrary lib) {
        if (!isNative()) {
            setRefCount(Long.MAX_VALUE / 2); // make this object immortal

            setReplacement(allocateReplacememtObject(), lib);
        }
    }
}
