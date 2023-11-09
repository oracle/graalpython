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

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapper.PythonStructNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitionsFactory.PythonToNativeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccessFactory;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructs;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonContext.PythonThreadState;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

/**
 * Emulates CPython's {@code PyThreadState} struct.
 */
@ExportLibrary(InteropLibrary.class)
public final class PThreadState extends PythonStructNativeWrapper {

    private final PythonThreadState threadState;

    private Object replacement;

    private PThreadState(PythonThreadState threadState) {
        this.threadState = threadState;
    }

    public static PThreadState getThreadState(PythonLanguage language, PythonContext context) {
        PythonThreadState threadState = context.getThreadState(language);
        PThreadState nativeWrapper = threadState.getNativeWrapper();
        if (nativeWrapper == null) {
            nativeWrapper = new PThreadState(threadState);
            threadState.setNativeWrapper(nativeWrapper);
        }
        // does not require a 'to_sulong' since it is already a native wrapper type
        return nativeWrapper;
    }

    public PythonThreadState getThreadState() {
        return threadState;
    }

    @TruffleBoundary
    private Object allocateReplacementObject() {
        PythonToNativeNode toNative = PythonToNativeNodeGen.getUncached();
        Object ptr = CStructAccessFactory.AllocateNodeGen.getUncached().alloc(CStructs.PyThreadState, true);
        CStructAccess.WritePointerNode writePtrNode = CStructAccessFactory.WritePointerNodeGen.getUncached();
        PythonContext pythonContext = PythonContext.get(null);
        Object nullValue = pythonContext.getNativeNull().getPtr();
        PDict threadStateDict = threadState.getDict();
        if (threadStateDict == null) {
            threadStateDict = pythonContext.factory().createDict();
            threadState.setDict(threadStateDict);
        }
        writePtrNode.write(ptr, CFields.PyThreadState__dict, toNative.execute(threadStateDict));
        writePtrNode.write(ptr, CFields.PyThreadState__interp, nullValue);
        return ptr;
    }

    @Override
    public boolean isReplacingWrapper() {
        return true;
    }

    @Override
    public Object getReplacement(InteropLibrary lib) {
        if (replacement == null) {
            replacement = registerReplacement(allocateReplacementObject(), lib);
        }
        return replacement;
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

    @ExportMessage
    @TruffleBoundary
    protected void toNative(
                    @CachedLibrary(limit = "3") InteropLibrary lib) {
        if (!isNative()) {
            getReplacement(lib);
        }
    }
}
