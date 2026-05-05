/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.cext;

import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Direct;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Ignored;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.ConstCharPtr;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Int;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Pointer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectRawPointer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.VoidNoReturn;
import static com.oracle.graal.python.runtime.nativeaccess.NativeMemory.NULLPTR;
import static com.oracle.graal.python.runtime.exception.ExceptionUtils.printPythonLikeStackTrace;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PRaiseNativeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.CharPtrToPythonNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNewRefNode;
import com.oracle.graal.python.builtins.objects.contextvars.PContextVar;
import com.oracle.graal.python.builtins.objects.contextvars.PContextVarsContext;
import com.oracle.graal.python.lib.PyContextCopyCurrent;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.strings.TruffleString;

public final class PythonCextContextBuiltins {

    @CApiBuiltin(ret = VoidNoReturn, args = {}, call = Ignored)
    @TruffleBoundary
    static void GraalPyPrivate_PrintStacktrace() {
        printPythonLikeStackTrace();
    }

    @CApiBuiltin(ret = PyObjectRawPointer, args = {ConstCharPtr, PyObjectRawPointer}, call = Direct)
    static long PyContextVar_New(long namePtr, long defPtr) {
        if (namePtr == NULLPTR) {
            return NULLPTR;
        }
        TruffleString name = (TruffleString) CharPtrToPythonNode.getUncached().execute(namePtr);
        Object def = defPtr == NULLPTR ? PNone.NO_VALUE : NativeToPythonNode.executeRawUncached(defPtr);
        return PythonToNativeNewRefNode.executeLongUncached(CallNode.executeUncached(PythonBuiltinClassType.ContextVar, name, def));
    }

    @CApiBuiltin(ret = PyObjectRawPointer, args = {PyObjectRawPointer, PyObjectRawPointer, Pointer}, call = Ignored)
    static long GraalPyPrivate_ContextVar_Get(long varPtr, long defPtr, long marker) {
        Object var = NativeToPythonNode.executeRawUncached(varPtr);
        if (!(var instanceof PContextVar pvar)) {
            return PRaiseNativeNode.raiseStatic(marker, PythonBuiltinClassType.TypeError, ErrorMessages.INSTANCE_OF_CONTEXTVAR_EXPECTED);
        }
        PythonContext context = PythonContext.get(null);
        PythonContext.PythonThreadState threadState = context.getThreadState(PythonLanguage.get(null));
        Object result = pvar.getValue(null, threadState);
        if (result == null) {
            if (defPtr == NULLPTR) {
                result = pvar.getDefault() == PContextVar.NO_DEFAULT ? PNone.NO_VALUE : pvar.getDefault();
            } else {
                result = NativeToPythonNode.executeRawUncached(defPtr);
            }
        }
        return PythonToNativeNewRefNode.executeLongUncached(result);
    }

    @CApiBuiltin(ret = PyObjectRawPointer, args = {PyObjectRawPointer, PyObjectRawPointer}, call = Direct)
    static long PyContextVar_Set(long varPtr, long valPtr) {
        Object var = NativeToPythonNode.executeRawUncached(varPtr);
        Object val = NativeToPythonNode.executeRawUncached(valPtr);
        if (!(var instanceof PContextVar pvar)) {
            throw PRaiseNode.raiseStatic(null, PythonBuiltinClassType.TypeError, ErrorMessages.INSTANCE_OF_CONTEXTVAR_EXPECTED);
        }
        PythonLanguage language = PythonLanguage.get(null);
        PythonContext pythonContext = PythonContext.get(null);
        PythonContext.PythonThreadState threadState = pythonContext.getThreadState(language);
        Object oldValue = pvar.getValue(null, threadState);
        pvar.setValue(null, threadState, val);
        return PythonToNativeNewRefNode.executeLongUncached(PFactory.createContextVarsToken(language, pvar, oldValue));
    }

    @CApiBuiltin(ret = PyObjectRawPointer, call = Direct)
    static long PyContext_CopyCurrent() {
        return PythonToNativeNewRefNode.executeLongUncached(PyContextCopyCurrent.executeUncached());
    }

    @CApiBuiltin(ret = PyObjectRawPointer, args = {PyObjectRawPointer}, call = Direct)
    static long PyContext_Copy(long contextPtr) {
        PContextVarsContext context = (PContextVarsContext) NativeToPythonNode.executeRawUncached(contextPtr);
        return PythonToNativeNewRefNode.executeLongUncached(PFactory.copyContextVarsContext(PythonLanguage.get(null), context));
    }

    @CApiBuiltin(ret = PyObjectRawPointer, call = Direct)
    static long PyContext_New() {
        return PythonToNativeNewRefNode.executeLongUncached(PFactory.createContextVarsContext(PythonLanguage.get(null)));
    }

    @CApiBuiltin(ret = Int, args = {PyObjectRawPointer}, call = Direct)
    static int PyContext_Enter(long contextPtr) {
        PContextVarsContext context = (PContextVarsContext) NativeToPythonNode.executeRawUncached(contextPtr);
        PythonContext pythonContext = PythonContext.get(null);
        PythonContext.PythonThreadState threadState = pythonContext.getThreadState(PythonLanguage.get(null));
        context.enter(null, threadState, PRaiseNode.getUncached());
        return 0;
    }

    @CApiBuiltin(ret = Int, args = {PyObjectRawPointer}, call = Direct)
    static int PyContext_Exit(long contextPtr) {
        PContextVarsContext context = (PContextVarsContext) NativeToPythonNode.executeRawUncached(contextPtr);
        PythonContext pythonContext = PythonContext.get(null);
        PythonContext.PythonThreadState threadState = pythonContext.getThreadState(PythonLanguage.get(null));
        context.leave(threadState);
        return 0;
    }
}
