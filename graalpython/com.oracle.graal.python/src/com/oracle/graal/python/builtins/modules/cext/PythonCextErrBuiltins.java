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
package com.oracle.graal.python.builtins.modules.cext;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_FLUSH;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_WRITE;
import static com.oracle.graal.python.builtins.objects.exception.PBaseException.T_CODE;
import static com.oracle.graal.python.nodes.BuiltinNames.T_EXCEPTHOOK;
import static com.oracle.graal.python.nodes.BuiltinNames.T_LAST_TRACEBACK;
import static com.oracle.graal.python.nodes.BuiltinNames.T_LAST_TYPE;
import static com.oracle.graal.python.nodes.BuiltinNames.T_LAST_VALUE;
import static com.oracle.graal.python.nodes.ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC_WAS_S_P;
import static com.oracle.graal.python.nodes.ErrorMessages.EXCEPTION_NOT_BASEEXCEPTION;
import static com.oracle.graal.python.nodes.ErrorMessages.MUST_BE_MODULE_CLASS;
import static com.oracle.graal.python.nodes.ErrorMessages.S_S_BAD_ARG_TO_INTERNAL_FUNC;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___CAUSE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___CONTEXT__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DOC__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___MODULE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___TRACEBACK__;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinConstructors.StrNode;
import com.oracle.graal.python.builtins.modules.BuiltinConstructors.TypeNode;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.IsInstanceNode;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.IsSubClassNode;
import com.oracle.graal.python.builtins.modules.PosixModuleBuiltins.ExitNode;
import com.oracle.graal.python.builtins.modules.SysModuleBuiltins;
import com.oracle.graal.python.builtins.modules.SysModuleBuiltins.ExcInfoNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.NativeBuiltin;
import com.oracle.graal.python.builtins.modules.cext.PythonCextFileBuiltins.PyFileWriteObjectNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PRaiseNativeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.TransformExceptionToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeNull;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetItem;
import com.oracle.graal.python.builtins.objects.dict.DictBuiltins.SetItemNode;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.traceback.GetTracebackNode;
import com.oracle.graal.python.builtins.objects.traceback.LazyTraceback;
import com.oracle.graal.python.builtins.objects.traceback.PTraceback;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.tuple.TupleBuiltins;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsTypeNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectSetAttr;
import com.oracle.graal.python.lib.PyObjectStrAsObjectNode;
import com.oracle.graal.python.nodes.WriteUnraisableNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsNode;
import com.oracle.graal.python.nodes.util.ExceptionStateNodes.GetCaughtExceptionNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonContext.GetThreadStateNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.ExceptionUtils;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.LoopConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendsModule = PythonCextBuiltins.PYTHON_CEXT)
@GenerateNodeFactory
public final class PythonCextErrBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PythonCextErrBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
    }

    @Builtin(name = "PyErr_Restore", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class PyErrRestoreNode extends PythonTernaryBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        Object run(PNone typ, PNone val, PNone tb) {
            getContext().setCurrentException(getLanguage(), null);
            return PNone.NONE;
        }

        @Specialization
        Object run(@SuppressWarnings("unused") Object typ, PBaseException val, @SuppressWarnings("unused") PNone tb) {
            PythonContext context = getContext();
            PythonLanguage language = getLanguage();
            context.setCurrentException(language, PException.fromExceptionInfo(val, (LazyTraceback) null, PythonOptions.isPExceptionWithJavaStacktrace(language)));
            return PNone.NONE;
        }

        @Specialization
        Object run(@SuppressWarnings("unused") Object typ, PBaseException val, PTraceback tb) {
            PythonContext context = getContext();
            PythonLanguage language = getLanguage();
            context.setCurrentException(language, PException.fromExceptionInfo(val, tb, PythonOptions.isPExceptionWithJavaStacktrace(language)));
            return PNone.NONE;
        }
    }

    @Builtin(name = "PyErr_Fetch")
    @GenerateNodeFactory
    abstract static class PyErrFetchNode extends NativeBuiltin {
        @Specialization
        public Object run(@Cached GetThreadStateNode getThreadStateNode,
                        @Cached GetClassNode getClassNode,
                        @Cached GetTracebackNode getTracebackNode) {
            PException currentException = getThreadStateNode.getCurrentException();
            Object result;
            if (currentException == null) {
                result = getContext().getNativeNull();
            } else {
                PBaseException exception = currentException.getEscapedException();
                Object traceback = null;
                if (currentException.getTraceback() != null) {
                    traceback = getTracebackNode.execute(currentException.getTraceback());
                }
                if (traceback == null) {
                    traceback = getContext().getNativeNull();
                }
                result = factory().createTuple(new Object[]{getClassNode.execute(exception), exception, traceback});
                getThreadStateNode.setCurrentException(null);
            }
            return result;
        }

        static PyErrFetchNode create() {
            return PythonCextErrBuiltinsFactory.PyErrFetchNodeFactory.create(null);
        }
    }

    @Builtin(name = "PyErr_Occurred", maxNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyErrOccurredNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object run(Object errorMarker,
                        @Cached GetThreadStateNode getThreadStateNode,
                        @Cached GetClassNode getClassNode) {
            PException currentException = getThreadStateNode.getCurrentException();
            if (currentException != null) {
                // getClassNode acts as a branch profile
                return getClassNode.execute(currentException.getUnreifiedException());
            }
            return errorMarker;
        }
    }

    @Builtin(name = "PyErr_SetExcInfo", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class PyErrSetExcInfoNode extends PythonBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        Object doClear(PNone typ, PNone val, PNone tb) {
            getContext().setCaughtException(getLanguage(), PException.NO_EXCEPTION);
            return PNone.NONE;
        }

        @Specialization
        Object doFull(@SuppressWarnings("unused") Object typ, PBaseException val, PTraceback tb) {
            PythonContext context = getContext();
            PythonLanguage language = getLanguage();
            context.setCaughtException(language, PException.fromExceptionInfo(val, tb, PythonOptions.isPExceptionWithJavaStacktrace(language)));
            return PNone.NONE;
        }

        @Specialization
        Object doWithoutTraceback(@SuppressWarnings("unused") Object typ, PBaseException val, @SuppressWarnings("unused") PNone tb) {
            return doFull(typ, val, null);
        }

        @Fallback
        @SuppressWarnings("unused")
        Object doFallback(Object typ, Object val, Object tb) {
            // TODO we should still store the values to return them with 'PyErr_GetExcInfo' (or
            // 'sys.exc_info')
            return PNone.NONE;
        }
    }

    /**
     * Exceptions are usually printed using the traceback module or the hook function
     * {@code sys.excepthook}. This is the last resort if the hook function itself failed.
     */
    @Builtin(name = "PyErr_Display", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class PyErrDisplayNode extends PythonTernaryBuiltinNode {

        @Specialization
        @SuppressWarnings("unused")
        Object run(Object typ, PBaseException val, Object tb) {
            if (val.getException() != null) {
                ExceptionUtils.printPythonLikeStackTrace(val.getException());
            }
            return PNone.NO_VALUE;
        }
    }

    @Builtin(name = "PyErr_CreateAndSetException", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyErrCreateAndSetExceptionNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "!isExceptionClass(frame, type, isTypeNode, isSubClassNode)")
        static Object create(VirtualFrame frame, Object type, Object value,
                        @SuppressWarnings("unused") @Cached IsTypeNode isTypeNode,
                        @SuppressWarnings("unused") @Cached IsSubClassNode isSubClassNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.execute(frame, PNone.NONE, PythonBuiltinClassType.SystemError, EXCEPTION_NOT_BASEEXCEPTION, new Object[]{type});
        }

        @Specialization(guards = "isExceptionClass(frame, type, isTypeNode, isSubClassNode)")
        Object create(VirtualFrame frame, Object type, Object value,
                        @SuppressWarnings("unused") @Cached IsTypeNode isTypeNode,
                        @SuppressWarnings("unused") @Cached IsInstanceNode isInstanceNode,
                        @SuppressWarnings("unused") @Cached IsSubClassNode isSubClassNode,
                        @Cached CallNode callConstructor,
                        @Cached BranchProfile noneValueProfile,
                        @Cached BranchProfile exValueProfile,
                        @Cached BranchProfile exProfile,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                if (value instanceof PNone) {
                    noneValueProfile.enter();
                    PBaseException ex = (PBaseException) callConstructor.execute(frame, type);
                    throw getRaiseNode().raiseExceptionObject(ex);
                } else if (isInstanceNode.executeWith(frame, value, PythonBuiltinClassType.PBaseException)) {
                    exValueProfile.enter();
                    throw getRaiseNode().raiseExceptionObject((PBaseException) value);
                } else {
                    exProfile.enter();
                    PBaseException ex = (PBaseException) callConstructor.execute(frame, type, value);
                    throw getRaiseNode().raiseExceptionObject(ex);
                }
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return PNone.NONE;
            }
        }

        protected static boolean isExceptionClass(VirtualFrame frame, Object obj, IsTypeNode isTypeNode, IsSubClassNode isSubClassNode) {
            return isTypeNode.execute(obj) && isSubClassNode.executeWith(frame, obj, PythonBuiltinClassType.PBaseException);
        }
    }

    @Builtin(name = "_PyErr_BadInternalCall", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class PyErrBadInternalCallNode extends PythonTernaryBuiltinNode {
        @Specialization
        static Object raiseNone(VirtualFrame frame, Object filename, Object lineno, Object obj,
                        @Cached PRaiseNativeNode raiseNativeNode,
                        @Cached StrNode strNode,
                        @Cached ConditionProfile profile) {
            if (profile.profile(filename == PNone.NONE || lineno == PNone.NONE)) {
                return raiseNativeNode.raise(frame, PNone.NONE, SystemError, BAD_ARG_TO_INTERNAL_FUNC_WAS_S_P, strNode.executeWith(frame, obj), obj);
            } else {
                return raiseNativeNode.raise(frame, PNone.NONE, SystemError, S_S_BAD_ARG_TO_INTERNAL_FUNC, strNode.executeWith(frame, obj), obj);
            }
        }
    }

    @Builtin(name = "PyErr_NewException", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class PyErrNewExceptionNode extends PythonTernaryBuiltinNode {

        @Specialization
        Object newEx(VirtualFrame frame, TruffleString name, Object base, PDict dict,
                        @Cached HashingStorageGetItem getItem,
                        @Cached TruffleString.IndexOfCodePointNode indexOfCodepointNode,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.SubstringNode substringNode,
                        @Cached SetItemNode setItemNode,
                        @Cached PRaiseNativeNode raiseNativeNode,
                        @Cached TypeNode typeNode,
                        @Cached BranchProfile notDotProfile,
                        @Cached BranchProfile notModuleProfile,
                        @Cached ConditionProfile baseProfile,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                int length = codePointLengthNode.execute(name, TS_ENCODING);
                int dotIdx = indexOfCodepointNode.execute(name, '.', 0, length, TS_ENCODING);
                if (dotIdx < 0) {
                    notDotProfile.enter();
                    return raiseNativeNode.raise(frame, getContext().getNativeNull(), SystemError, MUST_BE_MODULE_CLASS, "PyErr_NewException", "name");
                }
                if (getItem.execute(frame, dict.getDictStorage(), base) == null) {
                    notModuleProfile.enter();
                    setItemNode.execute(frame, dict, T___MODULE__, substringNode.execute(name, 0, dotIdx, TS_ENCODING, false));
                }
                PTuple bases;
                if (baseProfile.profile(base instanceof PTuple)) {
                    bases = (PTuple) base;
                } else {
                    bases = factory().createTuple(new Object[]{base});
                }
                return typeNode.execute(frame, PythonBuiltinClassType.PythonClass, substringNode.execute(name, dotIdx + 1, length - dotIdx - 1, TS_ENCODING, false), bases, dict,
                                PKeyword.EMPTY_KEYWORDS);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return getContext().getNativeNull();
            }
        }
    }

    @Builtin(name = "PyErr_NewExceptionWithDoc", minNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    abstract static class PyErrNewExceptionWithDocNode extends PythonQuaternaryBuiltinNode {

        @Specialization
        static Object raise(VirtualFrame frame, TruffleString name, TruffleString doc, Object base, PDict dict,
                        @Cached PyErrNewExceptionNode newExNode,
                        @Cached WriteAttributeToObjectNode writeAtrrNode) {
            Object ex = newExNode.execute(frame, name, base, dict);
            writeAtrrNode.execute(ex, T___DOC__, doc);
            return ex;
        }
    }

    @Builtin(name = "PyErr_GetExcInfo")
    @GenerateNodeFactory
    abstract static class PyErrGetExcInfoNode extends PythonBuiltinNode {
        @Specialization
        Object info(VirtualFrame frame,
                        @Cached ExcInfoNode excInfoNode,
                        @Cached GetCaughtExceptionNode getCaughtExceptionNode,
                        @Cached BranchProfile noExceptionProfile) {
            if (getCaughtExceptionNode.execute(frame) == null) {
                noExceptionProfile.enter();
                return getContext().getNativeNull();
            }
            return excInfoNode.execute(frame);
        }
    }

    @Builtin(name = "PyErr_GivenExceptionMatches")
    @GenerateNodeFactory
    abstract static class PyErrGivenExceptionMatchesNode extends PythonBinaryBuiltinNode {
        public abstract int executeInt(VirtualFrame frame, Object err, Object exc);

        @Specialization(guards = {"isPTuple(exc) || isTupleSubtype(frame, exc, getClassNode, isSubtypeNode)"}, limit = "1")
        static int matches(VirtualFrame frame, Object err, Object exc,
                        @Shared("getClass") @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @Shared("isSubtype") @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached TupleBuiltins.LenNode lenNode,
                        @Cached TupleBuiltins.GetItemNode getItemNode,
                        @Cached PyErrGivenExceptionMatchesNode matchesNode,
                        @Cached IsTypeNode isTypeNode,
                        @Cached LoopConditionProfile loopProfile) {
            int len = (int) lenNode.execute(frame, exc);
            loopProfile.profileCounted(len);
            for (int i = 0; loopProfile.profile(i < len); i++) {
                Object e = getItemNode.execute(frame, exc, i);
                if (matchesNode.executeInt(frame, err, e) != 0) {
                    return 1;
                }
            }
            return 0;
        }

        @Specialization(guards = {"!isPTuple(exc)", "!isTupleSubtype(frame, exc, getClassNode, isSubtypeNode)"}, limit = "1")
        static int matches(VirtualFrame frame, Object errArg, Object exc,
                        @Shared("getClass") @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @Shared("isSubtype") @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached IsInstanceNode isInstanceNode,
                        @Cached IsTypeNode isTypeNode,
                        @Cached IsSubClassNode isSubClassNode,
                        @Cached IsNode isNode,
                        @Cached BranchProfile isBaseExceptionProfile,
                        @Cached ConditionProfile isExceptionProfile) {
            Object err = errArg;
            if (isInstanceNode.executeWith(frame, errArg, PythonBuiltinClassType.PBaseException)) {
                isBaseExceptionProfile.enter();
                err = getClassNode.execute(err);
            }
            if (isExceptionProfile.profile(isExceptionClass(frame, err, isTypeNode, isSubClassNode) && isExceptionClass(frame, exc, isTypeNode, isSubClassNode))) {
                return isSubClassNode.executeWith(frame, err, exc) ? 1 : 0;
            } else {
                return isNode.execute(exc, err) ? 1 : 0;
            }
        }

        protected boolean isTupleSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PTuple);
        }

        private static boolean isExceptionClass(VirtualFrame frame, Object obj, IsTypeNode isTypeNode, IsSubClassNode isSubClassNode) {
            return isTypeNode.execute(obj) && isSubClassNode.executeWith(frame, obj, PythonBuiltinClassType.PBaseException);
        }
    }

    @Builtin(name = "_PyErr_WriteUnraisableMsg", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyErrWriteUnraisableMsgNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object write(VirtualFrame frame, Object msg, Object obj,
                        @Cached PyErrFetchNode fetchNode,
                        @Cached TupleBuiltins.GetItemNode getItemNode,
                        @Cached WriteAttributeToObjectNode writeAttrNode,
                        @Cached GetThreadStateNode getThreadStateNode,
                        @Cached WriteUnraisableNode writeUnraisableNode,
                        @Cached BranchProfile noValProfile) {
            Object val = null;
            Object tb = null;
            Object fetched = fetchNode.execute(frame);
            if (fetched != getContext().getNativeNull()) {
                PTuple fetchedTuple = (PTuple) fetched;
                val = getItemNode.execute(frame, fetchedTuple, 1);
                tb = getItemNode.execute(frame, fetchedTuple, 2);
            }
            if (!(val instanceof PBaseException)) {
                noValProfile.enter();
                // This means an invalid call, but this function is not supposed to raise exceptions
                return PNone.NONE;
            }
            if (tb == getContext().getNativeNull()) {
                tb = PNone.NONE;
            }
            TruffleString m = null;
            if (msg instanceof TruffleString) {
                m = (TruffleString) msg;
            }
            writeAttrNode.execute(val, T___TRACEBACK__, tb);
            writeUnraisableNode.execute(frame, (PBaseException) val, m, (obj instanceof PNone) ? PNone.NONE : obj);
            getThreadStateNode.setCaughtException(PException.NO_EXCEPTION);
            return PNone.NONE;
        }
    }

    @Builtin(name = "PyErr_PrintEx", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyErrPrintExNode extends PythonBuiltinNode {
        @TruffleBoundary
        @Specialization
        Object raise(int set_sys_last_vars,
                        @Cached PyErrGivenExceptionMatchesNode matchesNode,
                        @Cached PyErrOccurredNode errOccuredNode,
                        @Cached TupleBuiltins.GetItemNode getItemNode,
                        @Cached IsInstanceNode isInstanceNode,
                        @Cached ExcInfoNode excInfoNode,
                        @Cached PyErrRestoreNode restoreNode,
                        @Cached PyFileWriteObjectNode writeFileNode,
                        @Cached ExitNode exitNode,
                        @Cached PyErrFetchNode fetchNode,
                        @Cached PyErrDisplayNode errDisplayNode) {
            PythonNativeNull nativeNull = getContext().getNativeNull();

            Object err = errOccuredNode.execute(null, nativeNull);
            PythonModule sys = getCore().getSysModule();
            if (err != nativeNull && matchesNode.executeInt(null, err, PythonBuiltinClassType.SystemExit) != 0) {
                handleSystemExit(excInfoNode, getItemNode, isInstanceNode, restoreNode, (SysModuleBuiltins) sys.getBuiltins(), writeFileNode, exitNode);
            }
            Object fetched = fetchNode.execute(null);
            Object type = null;
            Object val = null;
            Object tb = null;

            if (fetched != nativeNull) {
                PTuple fetchedTuple = (PTuple) fetched;
                type = getItemNode.execute(null, fetchedTuple, 0);
                val = getItemNode.execute(null, fetchedTuple, 1);
                tb = getItemNode.execute(null, fetchedTuple, 2);
            }
            if (type == null || type == PNone.NONE) {
                return PNone.NONE;
            }
            if (tb == nativeNull) {
                tb = PNone.NONE;
            }
            if (PyObjectLookupAttr.getUncached().execute(null, val, T___TRACEBACK__) == PNone.NONE) {
                WriteAttributeToObjectNode.getUncached().execute(val, T___TRACEBACK__, tb);
            }

            if (set_sys_last_vars != 0) {
                writeLastVars(sys, type, val, tb, restoreNode);
            }
            Object exceptHook = PyObjectLookupAttr.getUncached().execute(null, sys, T_EXCEPTHOOK);
            if (exceptHook != PNone.NO_VALUE) {
                hanleExceptHook(exceptHook, type, val, tb, excInfoNode, getItemNode, sys, errDisplayNode);
            }
            return PNone.NONE;
        }

        @TruffleBoundary
        private void writeLastVars(PythonModule sys, Object type, Object val, Object tb, PyErrRestoreNode restoreNode) {
            try {
                WriteAttributeToObjectNode.getUncached().execute(sys, T_LAST_TYPE, type);
                WriteAttributeToObjectNode.getUncached().execute(sys, T_LAST_VALUE, val);
                WriteAttributeToObjectNode.getUncached().execute(sys, T_LAST_TRACEBACK, tb);
            } catch (PException e) {
                restoreNode.execute(null, PNone.NONE, PNone.NONE, PNone.NONE);
            }
        }

        @TruffleBoundary
        private void hanleExceptHook(Object exceptHook, Object type, Object val, Object tb, ExcInfoNode excInfoNode,
                        TupleBuiltins.GetItemNode getItemNode, PythonModule sys, PyErrDisplayNode errDisplayNode) {
            try {
                CallNode.getUncached().execute(exceptHook, type, val, tb);
            } catch (PException e) {
                PTuple sysInfo = excInfoNode.execute(null);
                Object type1 = getItemNode.execute(null, sysInfo, 0);
                Object val1 = getItemNode.execute(null, sysInfo, 1);
                Object tb1 = getItemNode.execute(null, sysInfo, 2);
                // not quite the same as 'PySys_WriteStderr' but close
                Object stdErr = ((SysModuleBuiltins) sys.getBuiltins()).getStdErr();
                Object writeMethod = PyObjectGetAttr.getUncached().execute(null, stdErr, T_WRITE);
                CallNode.getUncached().execute(null, writeMethod, PyObjectStrAsObjectNode.getUncached().execute(null, "Error in sys.excepthook:\n"));
                errDisplayNode.execute(null, type1, val1, tb1);
                CallNode.getUncached().execute(null, writeMethod, PyObjectStrAsObjectNode.getUncached().execute(null, "\nOriginal exception was:\n"));
                errDisplayNode.execute(null, type, val, tb);
                PyObjectCallMethodObjArgs.getUncached().execute(null, stdErr, T_FLUSH);
            }
        }

        @TruffleBoundary
        private static void handleSystemExit(ExcInfoNode excInfoNode, TupleBuiltins.GetItemNode getItemNode, IsInstanceNode isInstanceNode,
                        PyErrRestoreNode restoreNode, SysModuleBuiltins sys, PyFileWriteObjectNode writeFileNode, ExitNode exitNode) {
            PTuple sysInfo = excInfoNode.execute(null);
            int rc = 0;
            Object returnObject = null;
            Object val = getItemNode.execute(null, sysInfo, 1);
            Object codeAttr = PyObjectLookupAttr.getUncached().execute(null, val, T_CODE);
            if (val != PNone.NONE && !(codeAttr instanceof PNone)) {
                returnObject = codeAttr;
            }
            if (!(codeAttr instanceof PNone) && isInstanceNode.executeWith(null, codeAttr, PythonBuiltinClassType.PInt)) {
                rc = (int) codeAttr;
            } else {
                restoreNode.execute(null, PNone.NONE, PNone.NONE, PNone.NONE);
                Object stdErr = sys.getStdErr();
                if (stdErr != null && stdErr != PNone.NONE) {
                    writeFileNode.execute(null, returnObject, stdErr, 1);
                } else {
                    Object stdOut = sys.getStdOut();
                    Object writeMethod = PyObjectGetAttr.getUncached().execute(null, stdOut, T_WRITE);
                    CallNode.getUncached().execute(null, writeMethod, PyObjectStrAsObjectNode.getUncached().execute(null, returnObject));
                    PyObjectCallMethodObjArgs.getUncached().execute(null, stdOut, T_FLUSH);
                }
            }
            exitNode.execute(null, rc);
        }
    }

    @Builtin(name = "PyException_SetCause", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyExceptionSetCauseNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object setCause(VirtualFrame frame, Object exc, Object cause,
                        @Cached PyObjectSetAttr setAttrNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                setAttrNode.execute(frame, exc, T___CAUSE__, cause);
                return PNone.NONE;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return getContext().getNativeNull();
            }
        }
    }

    @Builtin(name = "PyException_GetContext", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyExceptionGetContextNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object setCause(VirtualFrame frame, Object exc,
                        @Cached PyObjectGetAttr getAttrNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return getAttrNode.execute(frame, exc, T___CONTEXT__);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return getContext().getNativeNull();
            }
        }
    }

    @Builtin(name = "PyException_SetContext", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyExceptionSetContextNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object setCause(VirtualFrame frame, Object exc, Object context,
                        @Cached PyObjectSetAttr setAttrNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                setAttrNode.execute(frame, exc, T___CONTEXT__, context);
                return PNone.NONE;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return getContext().getNativeNull();
            }
        }
    }
}
