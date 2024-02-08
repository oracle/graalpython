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
package com.oracle.graal.python.builtins.objects.cext.capi;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapper.PythonStructNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions;
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

/**
 * Emulates CPython's {@code PyThreadState} struct.
 * <p>
 * This wrapper does intentionally not implement {@link InteropLibrary#isPointer(Object)},
 * {@link InteropLibrary#asPointer(Object)}, and {@link InteropLibrary#toNative(Object)} because the
 * factory method {@link #getThreadState(PythonLanguage, PythonContext)} will already return the
 * appropriate pointer object that implements that.
 * </p>
 */
public final class PThreadState extends PythonStructNativeWrapper {

    @TruffleBoundary
    private PThreadState(PythonThreadState threadState) {
        super(threadState, true);
        // 'registerReplacement' will set the native pointer if not running LLVM managed mode.
        replacement = registerReplacement(allocateCLayout(threadState), InteropLibrary.getUncached());
    }

    public static Object getThreadState(PythonLanguage language, PythonContext context) {
        return getThreadState(context.getThreadState(language));
    }

    public static Object getThreadState(PythonThreadState threadState) {
        PThreadState nativeWrapper = threadState.getNativeWrapper();
        if (nativeWrapper == null) {
            nativeWrapper = new PThreadState(threadState);
            threadState.setNativeWrapper(nativeWrapper);
        }
        return nativeWrapper.replacement;
    }

    public PythonThreadState getThreadState() {
        return (PythonThreadState) getDelegate();
    }

    @TruffleBoundary
    private static Object allocateCLayout(PythonThreadState threadState) {
        PythonToNativeNode toNative = PythonToNativeNodeGen.getUncached();

        Object ptr = CStructAccess.AllocateNode.allocUncached(CStructs.PyThreadState);
        CStructAccess.WritePointerNode writePtrNode = CStructAccessFactory.WritePointerNodeGen.getUncached();
        PythonContext pythonContext = PythonContext.get(null);
        PDict threadStateDict = threadState.getDict();
        if (threadStateDict == null) {
            threadStateDict = pythonContext.factory().createDict();
            threadState.setDict(threadStateDict);
        }
        writePtrNode.write(ptr, CFields.PyThreadState__dict, toNative.execute(threadStateDict));
        CApiContext cApiContext = PythonContext.get(null).getCApiContext();
        writePtrNode.write(ptr, CFields.PyThreadState__small_ints, cApiContext.getOrCreateSmallInts());
        return ptr;
    }
}
