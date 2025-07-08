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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Direct;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Ignored;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_FLUSH;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_WRITE;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.ConstCharPtrAsTruffleString;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Int;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectTransfer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyThreadState;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Void;
import static com.oracle.graal.python.builtins.objects.exception.PBaseException.T_CODE;
import static com.oracle.graal.python.nodes.BuiltinNames.T_EXCEPTHOOK;
import static com.oracle.graal.python.nodes.BuiltinNames.T_LAST_TRACEBACK;
import static com.oracle.graal.python.nodes.BuiltinNames.T_LAST_TYPE;
import static com.oracle.graal.python.nodes.BuiltinNames.T_LAST_VALUE;
import static com.oracle.graal.python.nodes.ErrorMessages.EXCEPTION_NOT_BASEEXCEPTION;
import static com.oracle.graal.python.nodes.ErrorMessages.MUST_BE_MODULE_CLASS;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DOC__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___MODULE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___TRACEBACK__;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.IsSubClassNode;
import com.oracle.graal.python.builtins.modules.PosixModuleBuiltins.ExitNode;
import com.oracle.graal.python.builtins.modules.SysModuleBuiltins;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBinaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiNullaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiQuaternaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiTernaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiUnaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextFileBuiltins.PyFile_WriteObject;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.ReadAndClearNativeException;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetItem;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.exception.ExceptionNodes;
import com.oracle.graal.python.builtins.objects.exception.GetEscapedExceptionNode;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.exception.PrepareExceptionNode;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.traceback.PTraceback;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TypeBuiltins.TypeNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsTypeNode;
import com.oracle.graal.python.lib.PyDictSetItem;
import com.oracle.graal.python.lib.PyLongAsLongNode;
import com.oracle.graal.python.lib.PyLongCheckNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectSetAttr;
import com.oracle.graal.python.lib.PyObjectStrAsObjectNode;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.WriteUnraisableNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.nodes.util.ExceptionStateNodes.GetCaughtExceptionNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonContext.GetThreadStateNode;
import com.oracle.graal.python.runtime.PythonContext.PythonThreadState;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.ExceptionUtils;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

public final class PythonCextErrBuiltins {
    private static Object noneToNativeNull(Node node, Object obj) {
        return obj instanceof PNone ? PythonContext.get(node).getNativeNull() : obj;
    }

    @CApiBuiltin(ret = Void, args = {PyObject, PyObject}, call = Ignored)
    abstract static class PyTruffleErr_SetTraceback extends CApiBinaryBuiltinNode {

        @Specialization
        static Object set(Object exception, PTraceback tb) {
            if (exception instanceof PBaseException pythonException) {
                pythonException.setTraceback(tb);
            }
            return PNone.NO_VALUE;
        }

        @Specialization
        static Object clear(Object exception, @SuppressWarnings("unused") PNone tb) {
            if (exception instanceof PBaseException pythonException) {
                pythonException.clearTraceback();
            }
            return PNone.NO_VALUE;
        }
    }

    @CApiBuiltin(ret = Void, args = {PyThreadState, PyObject}, call = Direct)
    abstract static class _PyErr_SetHandledException extends CApiBinaryBuiltinNode {

        @Specialization
        @SuppressWarnings("unused")
        static Object doClear(@SuppressWarnings("unused") Object threadState, PNone val,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context) {
            PythonLanguage lang = context.getLanguage(inliningTarget);
            context.getThreadState(lang).setCaughtException(PException.NO_EXCEPTION);
            return PNone.NONE;
        }

        @Specialization
        static Object doFull(@SuppressWarnings("unused") Object threadState, PBaseException val,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context) {
            PythonLanguage language = context.getLanguage(inliningTarget);
            PException e = PException.fromExceptionInfo(val, PythonOptions.isPExceptionWithJavaStacktrace(language));
            context.getThreadState(language).setCaughtException(e);
            return PNone.NONE;
        }
    }

    /**
     * Exceptions are usually printed using the traceback module or the hook function
     * {@code sys.excepthook}. This is the last resort if the hook function itself failed.
     */
    @CApiBuiltin(ret = Void, args = {PyObject, PyObject, PyObject}, call = Direct)
    abstract static class PyErr_Display extends CApiTernaryBuiltinNode {

        @Specialization
        @SuppressWarnings("unused")
        Object run(Object typ, PBaseException val, Object tb) {
            if (val.getException() != null) {
                ExceptionUtils.printPythonLikeStackTrace(val.getException());
            }
            return PNone.NO_VALUE;
        }
    }

    @CApiBuiltin(ret = Void, args = {PyObject, PyObject}, call = Direct)
    abstract static class _PyTruffleErr_CreateAndSetException extends CApiBinaryBuiltinNode {
        @Specialization(guards = "!isExceptionClass(inliningTarget, type, isTypeNode, isSubClassNode)")
        static Object create(Object type, @SuppressWarnings("unused") Object value,
                        @SuppressWarnings("unused") @Shared @Cached IsTypeNode isTypeNode,
                        @SuppressWarnings("unused") @Shared @Cached IsSubClassNode isSubClassNode,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, PythonBuiltinClassType.SystemError, EXCEPTION_NOT_BASEEXCEPTION, new Object[]{type});
        }

        @Specialization(guards = "isExceptionClass(inliningTarget, type, isTypeNode, isSubClassNode)")
        static Object create(Object type, Object value,
                        @Bind Node inliningTarget,
                        @SuppressWarnings("unused") @Shared @Cached IsTypeNode isTypeNode,
                        @SuppressWarnings("unused") @Shared @Cached IsSubClassNode isSubClassNode,
                        @Cached PrepareExceptionNode prepareExceptionNode) {
            Object exception = prepareExceptionNode.execute(null, type, value);
            throw PRaiseNode.raiseExceptionObject(inliningTarget, exception);
        }

        protected static boolean isExceptionClass(Node inliningTarget, Object obj, IsTypeNode isTypeNode, IsSubClassNode isSubClassNode) {
            return isTypeNode.execute(inliningTarget, obj) && isSubClassNode.executeWith(null, obj, PythonBuiltinClassType.PBaseException);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {ConstCharPtrAsTruffleString, PyObject, PyObject}, call = Direct)
    abstract static class PyErr_NewException extends CApiTernaryBuiltinNode {

        @Specialization
        static Object newEx(TruffleString name, Object base, Object dict,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached HashingStorageGetItem getItem,
                        @Cached TruffleString.IndexOfCodePointNode indexOfCodepointNode,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.SubstringNode substringNode,
                        @Cached PyDictSetItem setItemNode,
                        @Cached TypeNode typeNode,
                        @Cached InlinedBranchProfile notDotProfile,
                        @Cached InlinedBranchProfile notModuleProfile,
                        @Cached InlinedConditionProfile baseProfile,
                        @Cached PRaiseNode raiseNode) {
            if (base == PNone.NO_VALUE) {
                base = PythonErrorType.Exception;
            }
            if (dict == PNone.NO_VALUE) {
                dict = PFactory.createDict(language);
            }
            int length = codePointLengthNode.execute(name, TS_ENCODING);
            int dotIdx = indexOfCodepointNode.execute(name, '.', 0, length, TS_ENCODING);
            if (dotIdx < 0) {
                notDotProfile.enter(inliningTarget);
                throw raiseNode.raise(inliningTarget, SystemError, MUST_BE_MODULE_CLASS, "PyErr_NewException", "name");
            }
            if (getItem.execute(null, inliningTarget, ((PDict) dict).getDictStorage(), base) == null) {
                notModuleProfile.enter(inliningTarget);
                setItemNode.execute(null, inliningTarget, (PDict) dict, T___MODULE__, substringNode.execute(name, 0, dotIdx, TS_ENCODING, false));
            }
            PTuple bases;
            if (baseProfile.profile(inliningTarget, base instanceof PTuple)) {
                bases = (PTuple) base;
            } else {
                bases = PFactory.createTuple(language, new Object[]{base});
            }
            return typeNode.execute(null, PythonBuiltinClassType.PythonClass, substringNode.execute(name, dotIdx + 1, length - dotIdx - 1, TS_ENCODING, false), bases, dict,
                            PKeyword.EMPTY_KEYWORDS);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {ConstCharPtrAsTruffleString, ConstCharPtrAsTruffleString, PyObject, PyObject}, call = Direct)
    abstract static class PyErr_NewExceptionWithDoc extends CApiQuaternaryBuiltinNode {

        @Specialization
        static Object raise(TruffleString name, Object doc, Object base, Object dict,
                        @Cached PyErr_NewException newExNode,
                        @Cached WriteAttributeToObjectNode writeAtrrNode,
                        @Bind PythonLanguage language) {
            if (base == PNone.NO_VALUE) {
                base = PythonErrorType.Exception;
            }
            if (dict == PNone.NO_VALUE) {
                dict = PFactory.createDict(language);
            }
            Object ex = newExNode.execute(name, base, dict);
            if (doc != PNone.NO_VALUE) {
                writeAtrrNode.execute(ex, T___DOC__, doc);
            }
            return ex;
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, call = Ignored)
    abstract static class PyTruffleErr_GetExcInfo extends CApiNullaryBuiltinNode {
        @Specialization
        Object info(
                        @Bind Node inliningTarget,
                        @Cached GetCaughtExceptionNode getCaughtExceptionNode,
                        @Cached GetClassNode getClassNode,
                        @Cached ExceptionNodes.GetTracebackNode getTracebackNode,
                        @Cached InlinedBranchProfile noExceptionProfile,
                        @Cached GetEscapedExceptionNode getEscapedExceptionNode,
                        @Bind PythonLanguage language) {
            AbstractTruffleException currentException = getCaughtExceptionNode.executeFromNative();
            if (currentException == null) {
                noExceptionProfile.enter(inliningTarget);
                return getNativeNull();
            }
            assert currentException != PException.NO_EXCEPTION;
            Object exception = getEscapedExceptionNode.execute(inliningTarget, currentException);
            Object traceback = noneToNativeNull(inliningTarget, getTracebackNode.execute(inliningTarget, exception));
            return PFactory.createTuple(language, new Object[]{getClassNode.execute(inliningTarget, exception), exception, traceback});
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyThreadState}, call = Direct)
    abstract static class _PyErr_GetHandledException extends CApiUnaryBuiltinNode {

        @Specialization
        static Object get(@SuppressWarnings("unused") Object threadState,
                        @Bind("this") Node inliningTarget,
                        @Cached GetCaughtExceptionNode getCaughtExceptionNode,
                        @Cached GetEscapedExceptionNode getEscapedExceptionNode) {
            AbstractTruffleException caughtException = getCaughtExceptionNode.executeFromNative();
            if (caughtException == null) {
                return PythonContext.get(inliningTarget).getNativeNull();
            }
            assert caughtException != PException.NO_EXCEPTION;
            return getEscapedExceptionNode.execute(inliningTarget, caughtException);
        }
    }

    @CApiBuiltin(ret = Void, args = {ConstCharPtrAsTruffleString, PyObject}, call = Direct)
    abstract static class _PyErr_WriteUnraisableMsg extends CApiBinaryBuiltinNode {
        @Specialization
        static Object write(Object msg, Object obj,
                        @Bind Node inliningTarget,
                        @Cached GetThreadStateNode getThreadStateNode,
                        @Cached WriteUnraisableNode writeUnraisableNode,
                        @Cached CastToTruffleStringNode castToTruffleStringNode,
                        @Cached ReadAndClearNativeException readAndClearNativeException) {
            PythonContext.PythonThreadState threadState = getThreadStateNode.execute(inliningTarget, PythonContext.get(inliningTarget));
            Object currentException = readAndClearNativeException.execute(inliningTarget, threadState);
            if (currentException == PNone.NO_VALUE) {
                // This means an invalid call, but this function is not supposed to raise exceptions
                return PNone.NONE;
            }
            TruffleString m;
            try {
                m = castToTruffleStringNode.execute(inliningTarget, msg);
            } catch (CannotCastException e) {
                m = null;
            }
            writeUnraisableNode.execute(currentException, m, (obj == PNone.NO_VALUE) ? PNone.NONE : obj);
            return PNone.NONE;
        }
    }

    @CApiBuiltin(ret = Void, args = {Int}, call = Direct)
    abstract static class PyErr_PrintEx extends CApiUnaryBuiltinNode {

        @TruffleBoundary
        @Specialization
        static Object print(int set_sys_last_vars,
                        @Cached PyFile_WriteObject writeFileNode,
                        @Cached ExitNode exitNode,
                        @Cached PyErr_Display errDisplayNode) {
            PythonContext context = PythonContext.get(null);

            PythonThreadState threadState = context.getThreadState(context.getLanguage());
            Object currentException = ReadAndClearNativeException.executeUncached(threadState);
            PythonModule sys = context.getSysModule();

            if (currentException == PNone.NO_VALUE) {
                return PNone.NONE;
            }

            if (IsBuiltinObjectProfile.profileObjectUncached(currentException, PythonBuiltinClassType.SystemExit)) {
                handleSystemExit(currentException, (SysModuleBuiltins) sys.getBuiltins(), writeFileNode, exitNode);
            }

            Object type = GetClassNode.executeUncached(currentException);
            Object tb = ExceptionNodes.GetTracebackNode.executeUncached(currentException);
            if (set_sys_last_vars != 0) {
                writeLastVars(sys, type, currentException, tb);
            }
            Object exceptHook = PyObjectLookupAttr.executeUncached(sys, T_EXCEPTHOOK);
            if (exceptHook != PNone.NO_VALUE) {
                handleExceptHook(exceptHook, type, currentException, tb, sys, errDisplayNode);
            }
            return PNone.NONE;
        }

        private static void writeLastVars(PythonModule sys, Object type, Object val, Object tb) {
            CompilerAsserts.neverPartOfCompilation();
            try {
                WriteAttributeToObjectNode.getUncached().execute(sys, T_LAST_TYPE, type);
                WriteAttributeToObjectNode.getUncached().execute(sys, T_LAST_VALUE, val);
                WriteAttributeToObjectNode.getUncached().execute(sys, T_LAST_TRACEBACK, tb);
            } catch (PException e) {
                // Ignore
            }
        }

        private static void handleExceptHook(Object exceptHook, Object type, Object val, Object tb, PythonModule sys, PyErr_Display errDisplayNode) {
            CompilerAsserts.neverPartOfCompilation();
            try {
                CallNode.executeUncached(exceptHook, type, val, tb);
            } catch (PException e) {
                // not quite the same as 'PySys_WriteStderr' but close
                Object stdErr = ((SysModuleBuiltins) sys.getBuiltins()).getStdErr();
                Object writeMethod = PyObjectGetAttr.executeUncached(stdErr, T_WRITE);
                CallNode.executeUncached(writeMethod, PyObjectStrAsObjectNode.getUncached().execute(null, "Error in sys.excepthook:\n"));
                Object exc2 = e.getEscapedException();
                errDisplayNode.execute(GetClassNode.executeUncached(exc2), exc2, ExceptionNodes.GetTracebackNode.executeUncached(exc2));
                CallNode.executeUncached(writeMethod, PyObjectStrAsObjectNode.getUncached().execute(null, "\nOriginal exception was:\n"));
                errDisplayNode.execute(type, val, tb);
                PyObjectCallMethodObjArgs.executeUncached(stdErr, T_FLUSH);
            }
        }

        private static void handleSystemExit(Object currentException, SysModuleBuiltins sys, PyFile_WriteObject writeFileNode, ExitNode exitNode) {
            CompilerAsserts.neverPartOfCompilation();
            int rc = 0;
            Object returnObject = null;
            Object codeAttr = PyObjectLookupAttr.executeUncached(currentException, T_CODE);
            if (!(codeAttr instanceof PNone)) {
                returnObject = codeAttr;
            }
            if (!(codeAttr instanceof PNone) && PyLongCheckNode.executeUncached(codeAttr)) {
                rc = (int) PyLongAsLongNode.executeUncached(codeAttr);
            } else {
                Object stdErr = sys.getStdErr();
                if (stdErr != null && stdErr != PNone.NONE) {
                    writeFileNode.execute(returnObject, stdErr, 1);
                } else {
                    Object stdOut = sys.getStdOut();
                    Object writeMethod = PyObjectGetAttr.executeUncached(stdOut, T_WRITE);
                    CallNode.executeUncached(writeMethod, PyObjectStrAsObjectNode.getUncached().execute(null, returnObject));
                    PyObjectCallMethodObjArgs.executeUncached(stdOut, T_FLUSH);
                }
            }
            exitNode.execute(null, rc);
        }
    }

    @CApiBuiltin(ret = Void, args = {PyObject, PyObject}, call = Direct)
    abstract static class PyException_SetCause extends CApiBinaryBuiltinNode {
        @Specialization
        Object setCause(Object exc, Object cause,
                        @Bind Node inliningTarget,
                        @Cached ExceptionNodes.SetCauseNode setCauseNode) {
            setCauseNode.execute(inliningTarget, exc, cause != PNone.NO_VALUE ? cause : PNone.NONE);
            return PNone.NO_VALUE;
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject}, call = Direct)
    abstract static class PyException_GetCause extends CApiUnaryBuiltinNode {
        @Specialization
        Object getCause(Object exc,
                        @Bind Node inliningTarget,
                        @Cached ExceptionNodes.GetCauseNode getCauseNode) {
            return noneToNativeNull(inliningTarget, getCauseNode.execute(inliningTarget, exc));
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject}, call = Direct)
    abstract static class PyException_GetContext extends CApiUnaryBuiltinNode {
        @Specialization
        Object setCause(Object exc,
                        @Bind Node inliningTarget,
                        @Cached ExceptionNodes.GetContextNode getContextNode) {
            return noneToNativeNull(inliningTarget, getContextNode.execute(inliningTarget, exc));
        }
    }

    @CApiBuiltin(ret = Void, args = {PyObject, PyObject}, call = Direct)
    abstract static class PyException_SetContext extends CApiBinaryBuiltinNode {
        @Specialization
        Object setContext(Object exc, Object context,
                        @Bind Node inliningTarget,
                        @Cached ExceptionNodes.SetContextNode setContextNode) {
            setContextNode.execute(inliningTarget, exc, context != PNone.NO_VALUE ? context : PNone.NONE);
            return PNone.NO_VALUE;
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject}, call = Direct)
    abstract static class PyException_GetTraceback extends CApiUnaryBuiltinNode {

        @Specialization
        Object getTraceback(Object exc,
                        @Bind Node inliningTarget,
                        @Cached ExceptionNodes.GetTracebackNode getTracebackNode) {
            return noneToNativeNull(inliningTarget, getTracebackNode.execute(inliningTarget, exc));
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject, PyObject}, call = Direct)
    abstract static class PyException_SetTraceback extends CApiBinaryBuiltinNode {

        @Specialization
        Object setTraceback(Object exc, Object traceback,
                        @Bind Node inliningTarget,
                        @Cached PyObjectSetAttr setAttrNode) {
            setAttrNode.execute(inliningTarget, exc, T___TRACEBACK__, traceback);
            return 0;
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject}, call = Direct)
    abstract static class PyException_GetArgs extends CApiUnaryBuiltinNode {

        @Specialization
        static Object get(Object exc,
                        @Bind Node inliningTarget,
                        @Cached ExceptionNodes.GetArgsNode getArgsNode) {
            return getArgsNode.execute(inliningTarget, exc);
        }
    }

    @CApiBuiltin(ret = Void, args = {PyObject, PyObject}, call = Direct)
    abstract static class PyException_SetArgs extends CApiBinaryBuiltinNode {

        @Specialization
        static Object set(PBaseException exc, PTuple args,
                        @Bind Node inliningTarget,
                        @Cached ExceptionNodes.SetArgsNode setArgsNode) {
            setArgsNode.execute(inliningTarget, exc, args);
            return PNone.NO_VALUE;
        }
    }
}
