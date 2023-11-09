/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.RecursionError;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Direct;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Ignored;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.ConstCharPtr;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Int;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyFrameObjectTransfer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectBorrowed;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectConstPtr;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectTransfer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyThreadState;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Void;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApi11BuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiNullaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiUnaryBuiltinNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.FromCharPointerNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.PThreadState;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.code.CodeNodes;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.argument.CreateArgumentsNode;
import com.oracle.graal.python.nodes.call.GenericInvokeNode;
import com.oracle.graal.python.nodes.frame.GetCurrentFrameRef;
import com.oracle.graal.python.nodes.frame.ReadCallerFrameNode;
import com.oracle.graal.python.nodes.object.GetDictIfExistsNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonContext.PythonThreadState;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

public final class PythonCextCEvalBuiltins {

    @CApiBuiltin(ret = PyThreadState, args = {}, acquiresGIL = false, call = Direct)
    abstract static class PyEval_SaveThread extends CApiNullaryBuiltinNode {
        private static final TruffleLogger LOGGER = CApiContext.getLogger(PyEval_SaveThread.class);

        @Specialization
        static PThreadState save(@Cached GilNode gil) {
            PythonContext context = PythonContext.get(gil);
            PThreadState threadState = PThreadState.getThreadState(PythonLanguage.get(gil), context);
            LOGGER.fine("C extension releases GIL");
            gil.release(context, true);
            return threadState;
        }
    }

    @CApiBuiltin(ret = Void, args = {PyThreadState}, acquiresGIL = false, call = Direct)
    abstract static class PyEval_RestoreThread extends CApiUnaryBuiltinNode {
        private static final TruffleLogger LOGGER = CApiContext.getLogger(PyEval_RestoreThread.class);

        @Specialization
        static Object restore(@SuppressWarnings("unused") Object ptr,
                        @Cached GilNode gil) {
            PythonContext context = PythonContext.get(gil);
            /*
             * The thread state is not really used but fetching it checks if we are shutting down
             * and will handle that properly.
             */
            context.getThreadState(PythonLanguage.get(gil));
            LOGGER.fine("C extension acquires GIL");
            gil.acquire(context);
            return PNone.NO_VALUE;
        }
    }

    @CApiBuiltin(ret = PyObjectBorrowed, args = {}, call = Direct)
    abstract static class PyEval_GetBuiltins extends CApiNullaryBuiltinNode {
        @Specialization
        Object release(
                        @Cached GetDictIfExistsNode getDictNode) {
            PythonModule cext = getCore().getBuiltins();
            return getDictNode.execute(cext);
        }
    }

    @CApiBuiltin(ret = PyFrameObjectTransfer, args = {}, call = Direct)
    abstract static class PyEval_GetFrame extends CApiNullaryBuiltinNode {
        @Specialization
        Object getFrame(
                        @Bind("this") Node inliningTarget,
                        @Cached GetCurrentFrameRef getCurrentFrameRef,
                        @Cached ReadCallerFrameNode readCallerFrameNode) {
            PFrame.Reference reference = getCurrentFrameRef.execute(null, inliningTarget);
            return readCallerFrameNode.executeWith(reference, 0);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject, PyObject, PyObjectConstPtr, Int, PyObjectConstPtr, Int, PyObjectConstPtr, Int, PyObject, PyObject}, call = Ignored)
    abstract static class _PyTruffleEval_EvalCodeEx extends CApi11BuiltinNode {
        @Specialization
        static Object doGeneric(PCode code, Object globals, Object locals,
                        Object argumentArrayPtr, int argumentCount, Object kwsPtr, int kwsCount, Object defaultValueArrayPtr, int defaultValueCount,
                        Object kwdefaultsWrapper, Object closureObj,
                        @Bind("this") Node inliningTarget,
                        @Cached CStructAccess.ReadObjectNode readNode,
                        @Cached PythonCextBuiltins.CastKwargsNode castKwargsNode,
                        @Cached CastToTruffleStringNode castToStringNode,
                        @Cached SequenceNodes.GetObjectArrayNode getObjectArrayNode,
                        @Cached CodeNodes.GetCodeSignatureNode getSignatureNode,
                        @Cached CodeNodes.GetCodeCallTargetNode getCallTargetNode,
                        @Cached CreateArgumentsNode.CreateAndCheckArgumentsNode createAndCheckArgumentsNode,
                        @Cached GenericInvokeNode invokeNode) {
            Object[] defaults = readNode.readPyObjectArray(defaultValueArrayPtr, defaultValueCount);
            PKeyword[] kwdefaults = castKwargsNode.execute(inliningTarget, kwdefaultsWrapper);
            PCell[] closure = null;
            if (closureObj != PNone.NO_VALUE) {
                // CPython also just accesses the object as tuple without further checks.
                closure = PCell.toCellArray(getObjectArrayNode.execute(inliningTarget, closureObj));
            }
            Object[] kws = readNode.readPyObjectArray(kwsPtr, kwsCount * 2);

            PKeyword[] keywords = PKeyword.create(kws.length / 2);
            for (int i = 0; i < kws.length / 2; i += 2) {
                TruffleString keywordName = castToStringNode.execute(inliningTarget, kws[i]);
                keywords[i] = new PKeyword(keywordName, kws[i + 1]);
            }

            // prepare Python frame arguments
            Object[] userArguments = readNode.readPyObjectArray(argumentArrayPtr, argumentCount);
            Signature signature = getSignatureNode.execute(inliningTarget, code);
            Object[] pArguments = createAndCheckArgumentsNode.execute(inliningTarget, code, userArguments, keywords, signature, null, null, defaults, kwdefaults, false);

            // set custom locals
            if (!(locals instanceof PNone)) {
                PArguments.setSpecialArgument(pArguments, locals);
            }
            PArguments.setClosure(pArguments, closure);
            // TODO(fa): set builtins in globals
            // PythonModule builtins = getContext().getBuiltins();
            // setBuiltinsInGlobals(globals, setBuiltins, builtins, lib);
            if (globals instanceof PythonObject) {
                PArguments.setGlobals(pArguments, (PythonObject) globals);
            } else {
                // TODO(fa): raise appropriate exception
            }

            RootCallTarget rootCallTarget = getCallTargetNode.execute(inliningTarget, code);
            return invokeNode.execute(rootCallTarget, pArguments);
        }
    }

    @CApiBuiltin(ret = Int, args = {ConstCharPtr}, call = Direct)
    abstract static class Py_EnterRecursiveCall extends CApiUnaryBuiltinNode {
        @Specialization
        int doGeneric(Object where) {
            PythonLanguage language = PythonLanguage.get(this);
            PythonContext context = PythonContext.get(this);
            PythonThreadState threadState = context.getThreadState(language);
            if (++threadState.recursionDepth > CApiContext.DEFAULT_RECURSION_LIMIT) {
                throw raiseRecursionError(this, where);
            }
            return 0;
        }

        @TruffleBoundary
        private static PException raiseRecursionError(Node node, Object where) {
            TruffleString msg = CastToTruffleStringNode.executeUncached(FromCharPointerNodeGen.getUncached().execute(where));
            TruffleString.ConcatNode.getUncached().execute(ErrorMessages.MAXIMUM_RECURSION_DEPTH_EXCEEDED, msg, TS_ENCODING, false);
            throw PRaiseNode.raiseUncached(node, RecursionError, msg);
        }
    }

    @CApiBuiltin(ret = Void, args = {}, call = Direct)
    abstract static class Py_LeaveRecursiveCall extends CApiNullaryBuiltinNode {
        @Specialization
        Object doGeneric() {
            PythonLanguage language = PythonLanguage.get(this);
            PythonContext context = PythonContext.get(this);
            PythonThreadState threadState = context.getThreadState(language);
            --threadState.recursionDepth;
            return PNone.NO_VALUE;
        }
    }
}
