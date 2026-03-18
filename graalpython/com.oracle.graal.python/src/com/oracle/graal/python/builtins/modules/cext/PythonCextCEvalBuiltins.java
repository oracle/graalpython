/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Direct;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Ignored;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Int;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyFrameObjectBorrowed;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectBorrowed;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectConstPtr;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectRawPointer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyThreadState;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Void;
import static com.oracle.graal.python.nfi2.NativeMemory.NULLPTR;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.ToNativeBorrowedNode;
import com.oracle.graal.python.builtins.objects.cext.capi.PThreadState;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNewRefNode;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.code.CodeNodes;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.lib.PyEvalGetGlobals;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.argument.CreateArgumentsNode;
import com.oracle.graal.python.nodes.call.CallDispatchers;
import com.oracle.graal.python.nodes.frame.ReadFrameNode;
import com.oracle.graal.python.nodes.object.GetDictIfExistsNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.strings.TruffleString;

public final class PythonCextCEvalBuiltins {
    private static final TruffleLogger LOGGER = CApiContext.getLogger(PythonCextCEvalBuiltins.class);

    @CApiBuiltin(ret = PyThreadState, args = {}, acquireGil = false, call = Direct)
    static long PyEval_SaveThread() {
        PythonContext context = PythonContext.get(null);
        long threadState = PThreadState.getOrCreateNativeThreadState(PythonLanguage.get(null), context);
        LOGGER.fine("C extension releases GIL");
        GilNode.getUncached().release(context, true);
        return threadState;
    }

    @CApiBuiltin(ret = Void, args = {PyThreadState}, acquireGil = false, call = Direct)
    static void PyEval_RestoreThread(@SuppressWarnings("unused") long ptr) {
        PythonContext context = PythonContext.get(null);
        /*
         * The thread state is not really used but fetching it checks if we are shutting down and
         * will handle that properly.
         */
        context.getThreadState(PythonLanguage.get(null));
        LOGGER.fine("C extension acquires GIL");
        GilNode.getUncached().acquire(context, null);
    }

    @CApiBuiltin(ret = PyObjectBorrowed, args = {}, call = Direct)
    static long PyEval_GetBuiltins() {
        PythonModule cext = PythonContext.get(null).getBuiltins();
        return ToNativeBorrowedNode.executeUncached(GetDictIfExistsNode.getUncached().execute(cext));
    }

    @CApiBuiltin(ret = PyFrameObjectBorrowed, args = {}, call = Direct)
    static long PyEval_GetFrame() {
        PFrame pFrame = ReadFrameNode.getUncached().getCurrentPythonFrame(null);
        return pFrame != null ? ToNativeBorrowedNode.executeUncached(pFrame) : NULLPTR;
    }

    @CApiBuiltin(ret = PyObjectRawPointer, args = {PyObjectRawPointer, PyObjectRawPointer, PyObjectRawPointer, PyObjectConstPtr, Int, PyObjectConstPtr, Int, PyObjectConstPtr, Int,
                    PyObjectRawPointer, PyObjectRawPointer}, call = Ignored)
    static long GraalPyPrivate_Eval_EvalCodeEx(long codePtr, long globalsPtr, long localsPtr,
                    long argumentArrayPtr, int argumentCount, long kwsPtr, int kwsCount, long defaultValueArrayPtr, int defaultValueCount,
                    long kwdefaultsWrapperPtr, long closureObjPtr) {
        PCode code = (PCode) NativeToPythonNode.executeRawUncached(codePtr);
        PythonObject globals = (PythonObject) NativeToPythonNode.executeRawUncached(globalsPtr);
        Object locals = NativeToPythonNode.executeRawUncached(localsPtr);
        Object kwdefaultsWrapper = NativeToPythonNode.executeRawUncached(kwdefaultsWrapperPtr);
        Object closureObj = NativeToPythonNode.executeRawUncached(closureObjPtr);
        Object[] defaults = CStructAccess.ReadObjectNode.getUncached().readPyObjectArray(defaultValueArrayPtr, defaultValueCount);
        if (!PGuards.isPNone(kwdefaultsWrapper) && !PGuards.isDict(kwdefaultsWrapper)) {
            throw PRaiseNode.raiseStatic(null, SystemError, ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC);
        }
        PKeyword[] kwdefaults = PythonCextBuiltins.CastKwargsNode.executeUncached(kwdefaultsWrapper);
        PCell[] closure = null;
        if (closureObj != PNone.NO_VALUE) {
            // CPython also just accesses the object as tuple without further checks.
            closure = PCell.toCellArray(SequenceNodes.GetObjectArrayNode.executeUncached(closureObj));
        }
        Object[] kws = CStructAccess.ReadObjectNode.getUncached().readPyObjectArray(kwsPtr, kwsCount * 2);
        PKeyword[] keywords = PKeyword.create(kws.length / 2);
        for (int i = 0, j = 0; i < kws.length; i += 2, j++) {
            TruffleString keywordName = CastToTruffleStringNode.castKnownStringUncached(kws[i]);
            keywords[j] = new PKeyword(keywordName, kws[i + 1]);
        }

        Object[] userArguments = CStructAccess.ReadObjectNode.getUncached().readPyObjectArray(argumentArrayPtr, argumentCount);
        Signature signature = CodeNodes.GetCodeSignatureNode.executeUncached(code);
        PFunction function = PFactory.createFunction(PythonLanguage.get(null), code.getName(), code, globals, closure);
        Object[] pArguments = CreateArgumentsNode.executeUncached(code, userArguments, keywords, signature, null, null, defaults, kwdefaults, false);
        if (!(locals instanceof PNone)) {
            PArguments.setSpecialArgument(pArguments, locals);
        }
        PArguments.setFunctionObject(pArguments, function);
        // TODO(fa): set builtins in globals
        // PythonModule builtins = getContext().getBuiltins();
        // setBuiltinsInGlobals(globals, setBuiltins, builtins, lib);
        PArguments.setGlobals(pArguments, globals);

        RootCallTarget rootCallTarget = CodeNodes.GetCodeCallTargetNode.executeUncached(code);
        Object result = CallDispatchers.SimpleIndirectInvokeNode.executeUncached(rootCallTarget, pArguments);
        return PythonToNativeNewRefNode.executeLongUncached(result);
    }

    @CApiBuiltin(ret = PyObjectBorrowed, args = {}, call = Direct)
    static long PyEval_GetGlobals() {
        PythonObject globals = PyEvalGetGlobals.executeUncached(null);
        return globals != null ? ToNativeBorrowedNode.executeUncached(globals) : NULLPTR;
    }
}
