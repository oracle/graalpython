/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.NotImplementedError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Direct;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Ignored;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.ConstCharPtrAsTruffleString;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Int;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Pointer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectConstPtr;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectRawPointer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectTransfer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectWrapper;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyThreadState;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyVarObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Py_hash_t;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Py_ssize_t;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.VA_LIST_PTR;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Void;
import static com.oracle.graal.python.builtins.objects.ints.PInt.intValue;
import static com.oracle.graal.python.nodes.ErrorMessages.UNHASHABLE_TYPE_P;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___BYTES__;
import static com.oracle.graal.python.nodes.StringLiterals.T_JAVA;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.io.PrintWriter;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.FormatNode;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.IsInstanceNode;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.IsSubClassNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApi5BuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBinaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiNullaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiQuaternaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiTernaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiUnaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CastArgsNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CastKwargsNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.BytesUtils;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiGuards;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ResolvePointerNode;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapper.PythonAbstractObjectNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.HandlePointerConverter;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.ToPythonWrapperNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.UpdateStrongRefNode;
import com.oracle.graal.python.builtins.objects.cext.common.GetNextVaArgNode;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltins.GetAttributeNode;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltins.SetattrNode;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.lib.PyBytesCheckNode;
import com.oracle.graal.python.lib.PyCallableCheckNode;
import com.oracle.graal.python.lib.PyLongCheckNode;
import com.oracle.graal.python.lib.PyObjectAsFileDescriptor;
import com.oracle.graal.python.lib.PyObjectAsciiNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectDelItem;
import com.oracle.graal.python.lib.PyObjectDir;
import com.oracle.graal.python.lib.PyObjectGetAttrO;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectHashNode;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.lib.PyObjectLookupAttrO;
import com.oracle.graal.python.lib.PyObjectReprAsObjectNode;
import com.oracle.graal.python.lib.PyObjectSetItem;
import com.oracle.graal.python.lib.PyObjectStrAsObjectNode;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.StringLiterals;
import com.oracle.graal.python.nodes.argument.keywords.ExpandKeywordStarargsNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupSpecialMethodNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetOrCreateDictNode;
import com.oracle.graal.python.nodes.object.IsNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.PosixSupportLibrary;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.ArrayBasedSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.EmptySequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.NativeSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

public abstract class PythonCextObjectBuiltins {

    private PythonCextObjectBuiltins() {
    }

    @CApiBuiltin(ret = Void, args = {PyObjectWrapper, Py_ssize_t}, call = Ignored)
    abstract static class PyTruffle_NotifyRefCount extends CApiBinaryBuiltinNode {
        @Specialization
        static Object doGeneric(PythonAbstractObjectNativeWrapper wrapper, long refCount,
                        @Bind Node inliningTarget,
                        @Cached UpdateStrongRefNode updateRefNode) {
            assert CApiTransitions.readNativeRefCount(HandlePointerConverter.pointerToStub(wrapper.getNativePointer())) == refCount;
            // refcounting on an immortal object should be a NOP
            assert refCount != PythonAbstractObjectNativeWrapper.IMMORTAL_REFCNT;
            updateRefNode.execute(inliningTarget, wrapper, refCount);
            return PNone.NO_VALUE;
        }
    }

    @CApiBuiltin(ret = Void, args = {Pointer, Int}, call = Ignored)
    abstract static class PyTruffle_BulkNotifyRefCount extends CApiBinaryBuiltinNode {

        @Specialization
        static Object doGeneric(Object arrayPointer, int len,
                        @Bind Node inliningTarget,
                        @Cached UpdateStrongRefNode updateRefNode,
                        @Cached CStructAccess.ReadPointerNode readPointerNode,
                        @Cached ToPythonWrapperNode toPythonWrapperNode) {

            /*
             * It may happen that due to several inc- and decrefs applied to a borrowed reference,
             * that the same pointer is in the list several times. To avoid crashes, we do the
             * processing in two phases: first, we resolve the pointers to wrappers and second, we
             * update the reference counts. In this way, we avoid that a reference is made weak when
             * processed the first time and may then be invalid if processed the second time.
             */
            PythonNativeWrapper[] resolved = new PythonNativeWrapper[len];
            for (int i = 0; i < resolved.length; i++) {
                Object elem = readPointerNode.readArrayElement(arrayPointer, i);
                resolved[i] = toPythonWrapperNode.executeWrapper(elem, false);
            }
            for (int i = 0; i < resolved.length; i++) {
                if (resolved[i] instanceof PythonAbstractObjectNativeWrapper objectNativeWrapper) {
                    long refCount = CApiTransitions.readNativeRefCount(HandlePointerConverter.pointerToStub(objectNativeWrapper.getNativePointer()));
                    // refcounting on an immortal object should be a NOP
                    assert refCount != PythonAbstractObjectNativeWrapper.IMMORTAL_REFCNT;
                    updateRefNode.execute(inliningTarget, objectNativeWrapper, refCount);
                }
            }
            return PNone.NO_VALUE;
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject, PyObject, Int}, call = Direct)
    abstract static class _PyTruffleObject_Call1 extends CApiQuaternaryBuiltinNode {
        @Specialization
        static Object doGeneric(Object callable, Object argsObj, Object kwargsObj, int singleArg,
                        @Bind Node inliningTarget,
                        @Cached CastArgsNode castArgsNode,
                        @Cached CastKwargsNode castKwargsNode,
                        @Cached CallNode callNode) {

            Object[] args;
            if (singleArg != 0) {
                args = new Object[]{argsObj};
            } else {
                args = castArgsNode.execute(null, inliningTarget, argsObj);
            }
            PKeyword[] keywords = castKwargsNode.execute(inliningTarget, kwargsObj);
            return callNode.execute(null, callable, args, keywords);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, VA_LIST_PTR}, call = Ignored)
    abstract static class PyTruffleObject_CallFunctionObjArgs extends CApiBinaryBuiltinNode {

        @Specialization
        static Object doFunction(Object callable, Object vaList,
                        @Bind Node inliningTarget,
                        @Cached GetNextVaArgNode getVaArgs,
                        @CachedLibrary(limit = "2") InteropLibrary argLib,
                        @Cached CallNode callNode,
                        @Cached NativeToPythonNode toJavaNode) {
            return callFunction(inliningTarget, callable, vaList, getVaArgs, argLib, callNode, toJavaNode);
        }

        static Object callFunction(Node inliningTarget, Object callable, Object vaList,
                        GetNextVaArgNode getVaArgs,
                        InteropLibrary argLib,
                        CallNode callNode,
                        NativeToPythonNode toJavaNode) {
            /*
             * Function 'PyObject_CallFunctionObjArgs' expects a va_list that contains just
             * 'PyObject *' and is terminated by 'NULL'.
             */
            Object[] args = new Object[4];
            int filled = 0;
            while (true) {
                Object object;
                try {
                    object = getVaArgs.execute(inliningTarget, vaList);
                } catch (InteropException e) {
                    throw CompilerDirectives.shouldNotReachHere();
                }
                if (argLib.isNull(object)) {
                    break;
                }
                if (filled >= args.length) {
                    args = PythonUtils.arrayCopyOf(args, args.length * 2);
                }
                args[filled++] = toJavaNode.execute(object);
            }
            if (filled < args.length) {
                args = PythonUtils.arrayCopyOf(args, filled);
            }
            return callNode.executeWithoutFrame(callable, args);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject, VA_LIST_PTR}, call = Ignored)
    abstract static class PyTruffleObject_CallMethodObjArgs extends CApiTernaryBuiltinNode {

        @Specialization
        static Object doMethod(Object receiver, Object methodName, Object vaList,
                        @Bind Node inliningTarget,
                        @Cached GetNextVaArgNode getVaArgs,
                        @CachedLibrary(limit = "2") InteropLibrary argLib,
                        @Cached CallNode callNode,
                        @Cached PyObjectGetAttrO getAnyAttributeNode,
                        @Cached NativeToPythonNode toJavaNode) {

            Object method = getAnyAttributeNode.execute(null, inliningTarget, receiver, methodName);
            return PyTruffleObject_CallFunctionObjArgs.callFunction(inliningTarget, method, vaList, getVaArgs, argLib, callNode, toJavaNode);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, ConstCharPtrAsTruffleString, PyObject, Int}, call = Direct)
    abstract static class _PyTruffleObject_CallMethod1 extends CApiQuaternaryBuiltinNode {
        @Specialization
        static Object doGeneric(Object receiver, TruffleString methodName, Object argsObj, int singleArg,
                        @Bind Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod,
                        @Cached CastArgsNode castArgsNode) {

            Object[] args;
            if (singleArg != 0) {
                args = new Object[]{argsObj};
            } else {
                args = castArgsNode.execute(null, inliningTarget, argsObj);
            }
            return callMethod.execute(null, inliningTarget, receiver, methodName, args);
        }
    }

    // directly called without landing function
    @CApiBuiltin(ret = PyObjectTransfer, args = {PyThreadState, PyObject, PyObjectConstPtr, Py_ssize_t, PyObject}, call = Direct)
    abstract static class _PyObject_MakeTpCall extends CApi5BuiltinNode {

        @Specialization
        static Object doGeneric(@SuppressWarnings("unused") Object threadState, Object callable, Object argsArray, long nargs, Object kwargs,
                        @Cached CStructAccess.ReadObjectNode readNode,
                        @Bind Node inliningTarget,
                        @Cached CStructAccess.ReadObjectNode readKwNode,
                        @Cached ExpandKeywordStarargsNode castKwargsNode,
                        @Cached SequenceStorageNodes.GetItemScalarNode getItemScalarNode,
                        @Cached CallNode callNode,
                        @Cached CastToTruffleStringNode castToTruffleStringNode) {
            try {

                Object[] args = readNode.readPyObjectArray(argsArray, (int) nargs);
                PKeyword[] keywords;
                if (kwargs instanceof PNone) {
                    keywords = PKeyword.EMPTY_KEYWORDS;
                } else if (kwargs instanceof PDict) {
                    keywords = castKwargsNode.execute(inliningTarget, kwargs);
                } else if (kwargs instanceof PTuple) {
                    // We have a tuple with kw names and an array with kw values
                    PTuple kwTuple = (PTuple) kwargs;
                    SequenceStorage storage = kwTuple.getSequenceStorage();
                    int kwcount = storage.length();
                    Object[] kwValues = readKwNode.readPyObjectArray(argsArray, kwcount, (int) nargs);
                    keywords = new PKeyword[kwcount];
                    for (int i = 0; i < kwcount; i++) {
                        TruffleString name = castToTruffleStringNode.execute(inliningTarget, getItemScalarNode.execute(inliningTarget, storage, i));
                        keywords[i] = new PKeyword(name, kwValues[i]);
                    }
                } else {
                    throw CompilerDirectives.shouldNotReachHere("_PyObject_MakeTpCall: keywords must be NULL, a tuple or a dict");
                }
                return callNode.execute(null, callable, args, keywords);
            } catch (CannotCastException e) {
                // I think we can just assume that there won't be more than
                // Integer.MAX_VALUE arguments.
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject}, call = Direct)
    abstract static class PyObject_Str extends CApiUnaryBuiltinNode {
        @Specialization(guards = "!isNoValue(obj)")
        Object doGeneric(Object obj,
                        @Bind Node inliningTarget,
                        @Cached PyObjectStrAsObjectNode strNode) {
            return strNode.execute(inliningTarget, obj);
        }

        @Specialization(guards = "isNoValue(obj)")
        static TruffleString asciiNone(@SuppressWarnings("unused") PNone obj) {
            return StringLiterals.T_NULL_RESULT;
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject}, call = Direct)
    abstract static class PyObject_Repr extends CApiUnaryBuiltinNode {
        @Specialization(guards = "!isNoValue(obj)")
        Object doGeneric(Object obj,
                        @Bind Node inliningTarget,
                        @Cached PyObjectReprAsObjectNode reprNode) {
            return reprNode.execute(null, inliningTarget, obj);
        }

        @Specialization(guards = "isNoValue(obj)")
        static TruffleString asciiNone(@SuppressWarnings("unused") PNone obj) {
            return StringLiterals.T_NULL_RESULT;
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject, PyObject}, call = Direct)
    abstract static class PyObject_DelItem extends CApiBinaryBuiltinNode {
        @Specialization
        static Object doGeneric(Object obj, Object k,
                        @Bind Node inliningTarget,
                        @Cached PyObjectDelItem delNode) {
            delNode.execute(null, inliningTarget, obj, k);
            return 0;
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject, PyObject, PyObject}, call = Direct)
    abstract static class PyObject_SetItem extends CApiTernaryBuiltinNode {
        @Specialization
        static Object doGeneric(Object obj, Object k, Object v,
                        @Bind Node inliningTarget,
                        @Cached PyObjectSetItem setItemNode) {
            setItemNode.execute(null, inliningTarget, obj, k, v);
            return 0;
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject, PyObject}, call = Direct)
    abstract static class PyObject_IsInstance extends CApiBinaryBuiltinNode {
        @Specialization
        static int doGeneric(Object obj, Object typ,
                        @Cached IsInstanceNode isInstanceNode) {
            return intValue((boolean) isInstanceNode.execute(null, obj, typ));
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject, PyObject}, call = Direct)
    abstract static class PyObject_IsSubclass extends CApiBinaryBuiltinNode {
        @Specialization
        static int doGeneric(Object obj, Object typ,
                        @Cached IsSubClassNode isSubclassNode) {
            return intValue((boolean) isSubclassNode.execute(null, obj, typ));
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject}, call = Direct)
    abstract static class PyObject_AsFileDescriptor extends CApiUnaryBuiltinNode {
        @Specialization
        static Object asFileDescriptor(Object obj,
                        @Bind Node inliningTarget,
                        @Cached PyLongCheckNode longCheckNode,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib,
                        @Cached TruffleString.EqualNode eqNode,
                        @Cached PyObjectAsFileDescriptor asFileDescriptorNode,
                        @Cached PRaiseNode raiseNode) {
            if (!longCheckNode.execute(inliningTarget, obj)) {
                Object posixSupport = PythonContext.get(inliningTarget).getPosixSupport();
                if (eqNode.execute(T_JAVA, posixLib.getBackend(posixSupport), TS_ENCODING)) {
                    /*
                     * For non Python 'int' objects, we refuse to hand out the fileno field when
                     * using the emulated Posix backend, because it is likely a fake.
                     */
                    throw raiseNode.raise(inliningTarget, NotImplementedError, ErrorMessages.S_NOT_SUPPORTED_ON_JAVA_POSIX_BACKEND, "PyObject_AsFileDescriptor");
                }
            }
            return asFileDescriptorNode.execute(null, inliningTarget, obj);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject}, call = Ignored)
    abstract static class PyTruffleObject_GenericGetAttr extends CApiBinaryBuiltinNode {
        @Specialization
        static Object getAttr(Object obj, Object attr,
                        @Cached GetAttributeNode getAttrNode) {
            return getAttrNode.execute(null, obj, attr);
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject, PyObject, PyObject}, call = Ignored)
    abstract static class PyTruffleObject_GenericSetAttr extends CApiTernaryBuiltinNode {
        @Specialization
        static int setAttr(Object obj, Object attr, Object value,
                        @Cached SetattrNode setAttrNode) {
            setAttrNode.execute(null, obj, attr, value);
            return 0;
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject, PyObject}, call = Direct)
    @CApiBuiltin(name = "PyObject_HasAttrString", ret = Int, args = {PyObject, ConstCharPtrAsTruffleString}, call = Direct)
    abstract static class PyObject_HasAttr extends CApiBinaryBuiltinNode {
        @Specialization
        static int hasAttr(Object obj, Object attr,
                        @Bind Node inliningTarget,
                        @Cached PyObjectLookupAttrO lookupAttrNode,
                        @Cached InlinedBranchProfile exceptioBranchProfile) {
            try {
                return lookupAttrNode.execute(null, inliningTarget, obj, attr) != PNone.NO_VALUE ? 1 : 0;
            } catch (PException e) {
                exceptioBranchProfile.enter(inliningTarget);
                return 0;
            }
        }
    }

    @CApiBuiltin(ret = Py_hash_t, args = {PyObject}, call = Direct)
    abstract static class PyObject_HashNotImplemented extends CApiUnaryBuiltinNode {
        @Specialization
        static Object unhashable(Object obj,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, PythonBuiltinClassType.TypeError, UNHASHABLE_TYPE_P, obj);
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject}, call = Direct)
    abstract static class PyObject_IsTrue extends CApiUnaryBuiltinNode {
        @Specialization
        static int isTrue(Object obj,
                        @Cached PyObjectIsTrueNode isTrueNode) {
            return isTrueNode.execute(null, obj) ? 1 : 0;
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject}, call = Direct)
    abstract static class PyObject_Bytes extends CApiUnaryBuiltinNode {
        @Specialization(guards = "isBuiltinBytes(bytes)")
        static Object bytes(PBytes bytes) {
            return bytes;
        }

        @Specialization(guards = "isNoValue(obj)")
        static Object bytesNoValue(@SuppressWarnings("unused") Object obj,
                        @Bind PythonLanguage language) {
            /*
             * Note: CPython calls PyBytes_FromString("<NULL>") but we do not directly have it.
             * Therefore, we directly create the bytes object with string "<NULL>" here.
             */
            return PFactory.createBytes(language, BytesUtils.NULL_STRING);
        }

        @Fallback
        static Object doGeneric(Object obj,
                        @Bind Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached InlinedConditionProfile hasBytes,
                        @Cached("create(T___BYTES__)") LookupSpecialMethodNode lookupBytes,
                        @Cached CallUnaryMethodNode callBytes,
                        @Cached PyBytesCheckNode check,
                        @Cached BytesNodes.BytesFromObject fromObject,
                        @Cached PRaiseNode raiseNode) {
            Object bytesMethod = lookupBytes.execute(null, getClassNode.execute(inliningTarget, obj), obj);
            if (hasBytes.profile(inliningTarget, bytesMethod != PNone.NO_VALUE)) {
                Object bytes = callBytes.executeObject(null, bytesMethod, obj);
                if (check.execute(inliningTarget, bytes)) {
                    return bytes;
                } else {
                    throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.RETURNED_NONBYTES, T___BYTES__, bytes);
                }
            }
            byte[] bytes = fromObject.execute(null, obj);
            return PFactory.createBytes(PythonLanguage.get(inliningTarget), bytes);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, call = Ignored)
    abstract static class PyTruffle_NotImplemented extends CApiNullaryBuiltinNode {
        @Specialization
        static Object run() {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, call = Ignored)
    abstract static class PyTruffle_NoValue extends CApiNullaryBuiltinNode {
        @Specialization
        static PNone doNoValue() {
            return PNone.NO_VALUE;
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, call = Ignored)
    abstract static class PyTruffle_None extends CApiNullaryBuiltinNode {
        @Specialization
        static PNone doNativeNone() {
            return PNone.NONE;
        }
    }

    @CApiBuiltin(ret = Void, args = {PyVarObject, Py_ssize_t}, call = Ignored)
    abstract static class _PyTruffle_SET_SIZE extends CApiBinaryBuiltinNode {
        @Specialization
        static PNone set(PSequence obj, long size,
                        @Bind Node inliningTarget,
                        @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Cached InlinedBranchProfile basicProfile,
                        @Cached InlinedBranchProfile nativeProfile) {
            SequenceStorage storage = getSequenceStorageNode.execute(inliningTarget, obj);
            // Can't use SetLenNode as that decrefs items for native storages when shrinking
            if (storage instanceof ArrayBasedSequenceStorage basicStorage) {
                basicProfile.enter(inliningTarget);
                basicStorage.setNewLength((int) size);
            } else if (storage instanceof NativeSequenceStorage nativeStorage) {
                nativeProfile.enter(inliningTarget);
                nativeStorage.setNewLength((int) size);
            } else if (storage instanceof EmptySequenceStorage) {
                if (size > 0) {
                    throw CompilerDirectives.shouldNotReachHere("invalid Py_SET_SIZE call");
                }
            } else {
                throw CompilerDirectives.shouldNotReachHere("unhandled storage type");
            }
            return PNone.NO_VALUE;
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObjectRawPointer}, call = Ignored)
    abstract static class _PyTruffleObject_IsFreed extends CApiUnaryBuiltinNode {
        @Specialization
        int doGeneric(Object pointer,
                        @Cached ToPythonWrapperNode toPythonWrapperNode) {
            return toPythonWrapperNode.executeWrapper(pointer, false) == null ? 1 : 0;
        }
    }

    @CApiBuiltin(ret = Void, args = {PyObjectWrapper}, call = Ignored)
    abstract static class _PyTruffleObject_Dump extends CApiUnaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        int doGeneric(Object ptrObject,
                        @Cached CStructAccess.ReadI64Node readI64) {
            PythonContext context = getContext();
            PrintWriter stderr = new PrintWriter(context.getStandardErr());

            // There are three cases we need to distinguish:
            // 1) The pointer object is a native pointer and is NOT a handle
            // 2) The pointer object is a native pointer and is a handle
            // 3) The pointer object is one of our native wrappers

            boolean isWrapper = CApiGuards.isNativeWrapper(ptrObject);

            /*
             * At this point we don't know if the pointer is invalid, so we try to resolve it to an
             * object.
             */
            Object resolved = isWrapper ? ptrObject : ResolvePointerNode.executeUncached(ptrObject);
            Object pythonObject;
            long refCnt;
            // We need again check if 'resolved' is a wrapper in case we resolved a handle.
            if (resolved instanceof PythonAbstractObjectNativeWrapper objectNativeWrapper) {
                if (objectNativeWrapper.isNative()) {
                    refCnt = objectNativeWrapper.getRefCount();
                } else {
                    refCnt = PythonAbstractObjectNativeWrapper.MANAGED_REFCNT;
                }
            } else {
                refCnt = readI64.read(PythonToNativeNode.executeUncached(resolved), CFields.PyObject__ob_refcnt);
            }
            pythonObject = NativeToPythonNode.executeUncached(ptrObject);

            // first, write fields which are the least likely to crash
            stderr.println("ptrObject address  : " + ptrObject);
            stderr.println("ptrObject refcount : " + refCnt);
            stderr.flush();

            Object type = GetClassNode.executeUncached(pythonObject);
            stderr.println("object type     : " + type);
            stderr.println("object type name: " + TypeNodes.GetNameNode.executeUncached(type));

            // the most dangerous part
            stderr.println("object repr     : ");
            stderr.flush();
            try {
                Object reprObj = PyObjectCallMethodObjArgs.executeUncached(context.getBuiltins(), BuiltinNames.T_REPR, pythonObject);
                stderr.println(CastToJavaStringNode.getUncached().execute(reprObj));
            } catch (PException | CannotCastException e) {
                // errors are ignored at this point
            }
            stderr.flush();
            return 0;
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject}, call = Direct)
    abstract static class PyObject_ASCII extends CApiUnaryBuiltinNode {
        @Specialization(guards = "!isNoValue(obj)")
        static TruffleString ascii(Object obj,
                        @Bind Node inliningTarget,
                        @Cached PyObjectAsciiNode asciiNode) {
            return asciiNode.execute(null, inliningTarget, obj);
        }

        @Specialization(guards = "isNoValue(obj)")
        static TruffleString asciiNone(@SuppressWarnings("unused") PNone obj) {
            return StringLiterals.T_NULL_RESULT;
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject}, call = Direct)
    abstract static class PyObject_Format extends CApiBinaryBuiltinNode {
        @Specialization
        static Object ascii(Object obj, Object spec,
                        @Cached FormatNode format) {
            return format.execute(null, obj, spec);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject}, call = Direct)
    abstract static class PyObject_GetIter extends CApiUnaryBuiltinNode {
        @Specialization
        static Object iter(Object object,
                        @Bind Node inliningTarget,
                        @Cached PyObjectGetIter getIter) {
            return getIter.execute(null, inliningTarget, object);
        }
    }

    @CApiBuiltin(ret = Py_hash_t, args = {PyObject}, call = Direct)
    abstract static class PyObject_Hash extends CApiUnaryBuiltinNode {
        @Specialization
        static long hash(Object object,
                        @Bind Node inliningTarget,
                        @Cached PyObjectHashNode hashNode) {
            return hashNode.execute(null, inliningTarget, object);
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject}, call = Direct)
    abstract static class PyCallable_Check extends CApiUnaryBuiltinNode {
        @Specialization
        static int doGeneric(Object object,
                        @Bind Node inliningTarget,
                        @Cached PyCallableCheckNode callableCheck) {
            return intValue(callableCheck.execute(inliningTarget, object));
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject}, call = Direct)
    abstract static class PyObject_Dir extends CApiUnaryBuiltinNode {
        @Specialization
        static Object dir(Object object,
                        @Bind Node inliningTarget,
                        @Cached PyObjectDir dir) {
            return dir.execute(null, inliningTarget, object);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject}, call = Ignored)
    abstract static class PyTruffleObject_GenericGetDict extends CApiUnaryBuiltinNode {
        @Specialization
        static Object getDict(Object object,
                        @Bind Node inliningTarget,
                        @Cached GetOrCreateDictNode getDict) {
            return getDict.execute(inliningTarget, object);
        }
    }

    @CApiBuiltin(ret = Int, args = {PyObject, PyObject}, call = Ignored)
    abstract static class PyTruffle_Is extends CApiBinaryBuiltinNode {
        @Specialization
        static int isTrue(Object a, Object b,
                        @Cached IsNode isNode) {
            return isNode.execute(a, b) ? 1 : 0;
        }
    }
}
