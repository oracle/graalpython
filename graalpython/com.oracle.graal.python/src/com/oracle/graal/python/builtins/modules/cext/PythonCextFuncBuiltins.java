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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Direct;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Ignored;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.ConstCharPtrAsTruffleString;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Int;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectBorrowed;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectTransfer;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___ANNOTATIONS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___MODULE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___NAME__;
import static com.oracle.graal.python.runtime.nativeaccess.NativeMemory.NULLPTR;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBinaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiUnaryBuiltinNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.EnsurePythonObjectNode;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.ToNativeBorrowedNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonInternalNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeInternalNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetItem;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.method.PDecoratedMethod;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.object.GetOrCreateDictNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.strings.TruffleString;

public final class PythonCextFuncBuiltins {

    private static PCell getCell(long cellPtr) {
        return (PCell) NativeToPythonInternalNode.executeUncached(cellPtr, false);
    }

    private static Object getPromotedCellRef(long cellPtr) {
        PCell cell = getCell(cellPtr);
        Object ref = cell.getRef();
        if (ref == null) {
            return null;
        }
        Object promotedRef = EnsurePythonObjectNode.executeUncached(PythonContext.get(null), ref, false);
        if (promotedRef != ref) {
            cell.setRef(promotedRef);
        }
        return promotedRef;
    }

    private static void checkCell(long cellPtr) {
        Object cell = NativeToPythonInternalNode.executeUncached(cellPtr, false);
        if (!(cell instanceof PCell)) {
            throw PRaiseNode.raiseStatic(null, SystemError, ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject}, call = Direct)
    static long PyCell_Get(long cellPtr) {
        checkCell(cellPtr);
        Object ref = getPromotedCellRef(cellPtr);
        return ref == null ? NULLPTR : PythonToNativeInternalNode.executeNewRefUncached(ref);
    }

    @CApiBuiltin(ret = Int, args = {PyObject, PyObject}, call = Direct)
    static int PyCell_Set(long cellPtr, long valuePtr) {
        checkCell(cellPtr);
        getCell(cellPtr).setRef(valuePtr == NULLPTR ? null : NativeToPythonInternalNode.executeUncached(valuePtr, false));
        return 0;
    }

    @CApiBuiltin(ret = PyObjectBorrowed, args = {PyObject}, call = Direct)
    static long GraalPyCell_GET(long cellPtr) {
        Object ref = getPromotedCellRef(cellPtr);
        return ref == null ? NULLPTR : ToNativeBorrowedNode.executeUncached(ref);
    }

    @CApiBuiltin(ret = ArgDescriptor.Void, args = {PyObject, PyObjectTransfer}, call = Direct)
    static void GraalPyCell_SET(long cellPtr, long valuePtr) {
        getCell(cellPtr).setRef(valuePtr == NULLPTR ? null : NativeToPythonInternalNode.executeUncached(valuePtr, true));
    }

    private static void checkFunction(long functionPtr) {
        Object function = NativeToPythonInternalNode.executeUncached(functionPtr, false);
        if (!(function instanceof PFunction)) {
            throw PRaiseNode.raiseStatic(null, SystemError, ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC);
        }
    }

    @CApiBuiltin(ret = PyObjectBorrowed, args = {PyObject}, call = Direct)
    static long PyFunction_GetCode(long functionPtr) {
        checkFunction(functionPtr);
        return GraalPyFunction_GET_CODE(functionPtr);
    }

    @CApiBuiltin(ret = PyObjectBorrowed, args = {PyObject}, call = Direct)
    static long PyFunction_GetGlobals(long functionPtr) {
        checkFunction(functionPtr);
        return GraalPyFunction_GET_GLOBALS(functionPtr);
    }

    @CApiBuiltin(ret = PyObjectBorrowed, args = {PyObject}, call = Direct)
    static long PyFunction_GetModule(long functionPtr) {
        checkFunction(functionPtr);
        return GraalPyFunction_GET_MODULE(functionPtr);
    }

    @CApiBuiltin(ret = PyObjectBorrowed, args = {PyObject}, call = Direct)
    static long PyFunction_GetDefaults(long functionPtr) {
        checkFunction(functionPtr);
        return GraalPyFunction_GET_DEFAULTS(functionPtr);
    }

    @CApiBuiltin(ret = PyObjectBorrowed, args = {PyObject}, call = Direct)
    static long PyFunction_GetKwDefaults(long functionPtr) {
        checkFunction(functionPtr);
        return GraalPyFunction_GET_KW_DEFAULTS(functionPtr);
    }

    @CApiBuiltin(ret = PyObjectBorrowed, args = {PyObject}, call = Direct)
    static long PyFunction_GetClosure(long functionPtr) {
        checkFunction(functionPtr);
        return GraalPyFunction_GET_CLOSURE(functionPtr);
    }

    @CApiBuiltin(ret = PyObjectBorrowed, args = {PyObject}, call = Direct)
    static long PyFunction_GetAnnotations(long functionPtr) {
        checkFunction(functionPtr);
        return GraalPyFunction_GET_ANNOTATIONS(functionPtr);
    }

    @CApiBuiltin(ret = PyObjectBorrowed, args = {PyObject}, call = Direct)
    static long GraalPyFunction_GET_CODE(long functionPtr) {
        PFunction function = (PFunction) NativeToPythonInternalNode.executeUncached(functionPtr, false);
        return ToNativeBorrowedNode.executeUncached(function.getCode());
    }

    @CApiBuiltin(ret = PyObjectBorrowed, args = {PyObject}, call = Direct)
    static long GraalPyFunction_GET_GLOBALS(long functionPtr) {
        PFunction function = (PFunction) NativeToPythonInternalNode.executeUncached(functionPtr, false);
        Object globals = function.getGlobals();
        return ToNativeBorrowedNode.executeUncached(globals instanceof PythonModule ? GetOrCreateDictNode.executeUncached(globals) : globals);
    }

    @CApiBuiltin(ret = PyObjectBorrowed, args = {PyObject}, call = Direct)
    static long GraalPyFunction_GET_MODULE(long functionPtr) {
        PFunction function = (PFunction) NativeToPythonInternalNode.executeUncached(functionPtr, false);
        Object module = ReadAttributeFromObjectNode.getUncached().execute(function, T___MODULE__);
        if (module == PNone.NO_VALUE) {
            Object globals = function.getGlobals();
            if (globals instanceof PythonModule) {
                module = ReadAttributeFromObjectNode.getUncached().execute(globals, T___NAME__);
                if (module == PNone.NO_VALUE) {
                    module = PNone.NONE;
                }
            } else if (globals instanceof PDict dict) {
                module = HashingStorageGetItem.executeUncached(dict.getDictStorage(), T___NAME__);
                if (module == null) {
                    module = PNone.NONE;
                }
            } else {
                module = PNone.NONE;
            }
        }
        Object promotedModule = EnsurePythonObjectNode.executeUncached(PythonContext.get(null), module, false);
        WriteAttributeToObjectNode.getUncached().execute(function, T___MODULE__, promotedModule);
        return ToNativeBorrowedNode.executeUncached(promotedModule);
    }

    @CApiBuiltin(ret = PyObjectBorrowed, args = {PyObject}, call = Direct)
    static long GraalPyFunction_GET_DEFAULTS(long functionPtr) {
        PFunction function = (PFunction) NativeToPythonInternalNode.executeUncached(functionPtr, false);
        Object[] defaults = function.getDefaults();
        return defaults.length == 0 ? NULLPTR : ToNativeBorrowedNode.executeUncached(PFactory.createTuple(PythonLanguage.get(null), defaults));
    }

    @CApiBuiltin(ret = PyObjectBorrowed, args = {PyObject}, call = Direct)
    static long GraalPyFunction_GET_KW_DEFAULTS(long functionPtr) {
        PFunction function = (PFunction) NativeToPythonInternalNode.executeUncached(functionPtr, false);
        Object defaults = function.getKwDefaultsDict(PythonLanguage.get(null));
        return defaults == PNone.NONE ? NULLPTR : ToNativeBorrowedNode.executeUncached(defaults);
    }

    @CApiBuiltin(ret = PyObjectBorrowed, args = {PyObject}, call = Direct)
    static long GraalPyFunction_GET_CLOSURE(long functionPtr) {
        PFunction function = (PFunction) NativeToPythonInternalNode.executeUncached(functionPtr, false);
        PCell[] closure = function.getClosure();
        return closure == null ? NULLPTR : ToNativeBorrowedNode.executeUncached(PFactory.createTuple(PythonLanguage.get(null), closure));
    }

    @CApiBuiltin(ret = PyObjectBorrowed, args = {PyObject}, call = Direct)
    static long GraalPyFunction_GET_ANNOTATIONS(long functionPtr) {
        PFunction function = (PFunction) NativeToPythonInternalNode.executeUncached(functionPtr, false);
        Object annotations = ReadAttributeFromObjectNode.getUncached().execute(function, T___ANNOTATIONS__);
        return annotations != PNone.NO_VALUE ? ToNativeBorrowedNode.executeUncached(annotations) : NULLPTR;
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject}, call = Direct)
    abstract static class PyStaticMethod_New extends CApiUnaryBuiltinNode {
        @Specialization
        static Object staticmethod(Object func,
                        @Bind PythonLanguage language) {
            PDecoratedMethod res = PFactory.createStaticmethod(language);
            res.setCallable(func);
            return res;
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject}, call = Direct)
    abstract static class PyClassMethod_New extends CApiUnaryBuiltinNode {
        @Specialization
        static Object staticmethod(Object callable,
                        @Bind PythonLanguage language) {
            return PFactory.createClassmethodFromCallableObj(language, callable);
        }
    }

    @CApiBuiltin(ret = ArgDescriptor.Void, args = {PyObject, ConstCharPtrAsTruffleString}, call = Ignored)
    abstract static class GraalPyPrivate_CFunction_SetDoc extends CApiBinaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        static Object setString(Object functionObj, TruffleString doc) {
            return setDoc(functionObj, doc);
        }

        @Specialization(guards = "isNoValue(nullValue)")
        @TruffleBoundary
        static Object setNull(Object functionObj, @SuppressWarnings("unused") PNone nullValue) {
            return setDoc(functionObj, null);
        }

        private static PNone setDoc(Object functionObj, TruffleString doc) {
            PBuiltinFunction function;
            if (functionObj instanceof PBuiltinFunction builtinFunction) {
                function = builtinFunction;
            } else if (functionObj instanceof PBuiltinMethod builtinMethod) {
                function = builtinMethod.getBuiltinFunction();
            } else {
                throw CompilerDirectives.shouldNotReachHere("Unexpected object passed to GraalPyCFunction_SetDoc");
            }
            CFunctionDocUtils.writeDocAndTextSignature(function, function.getName(),
                            doc != null ? doc : PNone.NO_VALUE, function.getFlags());
            return PNone.NO_VALUE;
        }
    }
}
