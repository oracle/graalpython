/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.ConstCharPtrAsTruffleString;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Int;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Pointer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectTransfer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.VoidNoReturn;
import static com.oracle.graal.python.runtime.exception.ExceptionUtils.printPythonLikeStackTrace;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBinaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiNullaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiTernaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiUnaryBuiltinNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PRaiseNativeNode;
import com.oracle.graal.python.builtins.objects.contextvars.PContextVar;
import com.oracle.graal.python.builtins.objects.contextvars.PContextVarsContext;
import com.oracle.graal.python.lib.PyContextCopyCurrent;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

public final class PythonCextContextBuiltins {

    @CApiBuiltin(ret = VoidNoReturn, args = {}, call = Ignored)
    abstract static class GraalPyPrivate_PrintStacktrace extends CApiNullaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        static Object stacktrace() {
            printPythonLikeStackTrace();
            return 0;
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {ConstCharPtrAsTruffleString, PyObject}, call = Direct)
    abstract static class PyContextVar_New extends CApiBinaryBuiltinNode {
        @Specialization
        static Object doGeneric(TruffleString name, Object def,
                        @Cached CallNode callContextvar) {
            return callContextvar.executeWithoutFrame(PythonBuiltinClassType.ContextVar, name, def);
        }

        @Specialization
        static Object doGeneric(PNone name, @SuppressWarnings("unused") Object def) {
            assert name == PNone.NO_VALUE;
            return PNone.NO_VALUE;
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject, Pointer}, call = Ignored)
    abstract static class GraalPyPrivate_ContextVar_Get extends CApiTernaryBuiltinNode {
        @Specialization
        static Object doGeneric(Object var, Object def, Object marker,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @Cached PRaiseNativeNode.Lazy raiseNative) {
            if (!(var instanceof PContextVar)) {
                return raiseNative.get(inliningTarget).raise(null, marker, PythonBuiltinClassType.TypeError, ErrorMessages.INSTANCE_OF_CONTEXTVAR_EXPECTED);
            }
            PythonContext.PythonThreadState threadState = context.getThreadState(context.getLanguage(inliningTarget));
            Object result = ((PContextVar) var).getValue(inliningTarget, threadState);
            if (result == null) {
                if (def == PNone.NO_VALUE) {
                    if (((PContextVar) var).getDefault() == PContextVar.NO_DEFAULT) {
                        result = PNone.NO_VALUE;
                    } else {
                        result = ((PContextVar) var).getDefault();
                    }
                } else {
                    result = def;
                }
            }
            return result;
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject}, call = Direct)
    abstract static class PyContextVar_Set extends CApiBinaryBuiltinNode {
        @Specialization
        static Object doGeneric(Object var, Object val,
                        @Bind Node inliningTarget,
                        @Bind PythonContext pythonContext,
                        @Cached PRaiseNode raiseNode) {
            if (!(var instanceof PContextVar pvar)) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.INSTANCE_OF_CONTEXTVAR_EXPECTED);
            }
            PythonLanguage language = pythonContext.getLanguage(inliningTarget);
            PythonContext.PythonThreadState threadState = pythonContext.getThreadState(language);
            Object oldValue = pvar.getValue(inliningTarget, threadState);
            pvar.setValue(inliningTarget, threadState, val);
            return PFactory.createContextVarsToken(language, pvar, oldValue);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, call = Direct)
    abstract static class PyContext_CopyCurrent extends CApiNullaryBuiltinNode {
        @Specialization
        static Object doGeneric(
                        @Bind Node inliningTarget,
                        @Cached PyContextCopyCurrent copyCurrent) {
            return copyCurrent.execute(inliningTarget);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject}, call = Direct)
    abstract static class PyContext_Copy extends CApiUnaryBuiltinNode {
        @Specialization
        static Object doGeneric(PContextVarsContext context,
                        @Bind PythonLanguage language) {
            return PFactory.copyContextVarsContext(language, context);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, call = Direct)
    abstract static class PyContext_New extends CApiNullaryBuiltinNode {
        @Specialization
        static Object doGeneric(
                        @Bind PythonLanguage language) {
            return PFactory.createContextVarsContext(language);
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject}, call = Direct)
    abstract static class PyContext_Enter extends CApiUnaryBuiltinNode {
        @Specialization
        static Object doGeneric(PContextVarsContext context,
                        @Bind Node inliningTarget,
                        @Bind PythonContext pythonContext,
                        @Cached PRaiseNode raiseNode) {
            PythonContext.PythonThreadState threadState = pythonContext.getThreadState(pythonContext.getLanguage(inliningTarget));
            context.enter(inliningTarget, threadState, raiseNode);
            return 0;
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject}, call = Direct)
    abstract static class PyContext_Exit extends CApiUnaryBuiltinNode {
        @Specialization
        static Object doGeneric(PContextVarsContext context,
                        @Bind Node inliningTarget,
                        @Bind PythonContext pythonContext) {
            PythonContext.PythonThreadState threadState = pythonContext.getThreadState(pythonContext.getLanguage(inliningTarget));
            context.leave(threadState);
            return 0;
        }
    }
}
