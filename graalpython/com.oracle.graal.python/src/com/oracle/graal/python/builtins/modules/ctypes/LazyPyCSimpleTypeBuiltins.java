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
package com.oracle.graal.python.builtins.modules.ctypes;

import static com.oracle.graal.python.builtins.modules.ctypes.CDataTypeBuiltins.J_FROM_PARAM;
import static com.oracle.graal.python.builtins.modules.ctypes.CDataTypeBuiltins.T__AS_PARAMETER_;
import static com.oracle.graal.python.builtins.modules.ctypes.FFIType.ffi_type_pointer;
import static com.oracle.graal.python.nodes.ErrorMessages.WRONG_TYPE;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DOC__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.IsInstanceNode;
import com.oracle.graal.python.builtins.modules.ctypes.CFieldBuiltins.SetFuncNode;
import com.oracle.graal.python.builtins.modules.ctypes.CtypesNodes.PyTypeCheck;
import com.oracle.graal.python.builtins.modules.ctypes.FFIType.FieldDesc;
import com.oracle.graal.python.builtins.modules.ctypes.LazyPyCSimpleTypeBuiltinsFactory.CCharPFromParamNodeFactory;
import com.oracle.graal.python.builtins.modules.ctypes.LazyPyCSimpleTypeBuiltinsFactory.CVoidPFromParamNodeFactory;
import com.oracle.graal.python.builtins.modules.ctypes.LazyPyCSimpleTypeBuiltinsFactory.CWCharPFromParamNodeFactory;
import com.oracle.graal.python.builtins.modules.ctypes.StgDictBuiltins.PyObjectStgDictNode;
import com.oracle.graal.python.builtins.modules.ctypes.StgDictBuiltins.PyTypeStgDictNode;
import com.oracle.graal.python.builtins.modules.ctypes.memory.Pointer;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeVoidPtr;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.method.PDecoratedMethod;
import com.oracle.graal.python.lib.PyLongCheckNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.runtime.object.PythonObjectSlowPathFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.strings.TruffleString;

public final class LazyPyCSimpleTypeBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        throw CompilerDirectives.shouldNotReachHere("Should not be part of initialization!");
    }

    @TruffleBoundary
    protected static void addCVoidPFromParam(PythonObjectSlowPathFactory factory, PythonLanguage language, Object type) {
        NodeFactory<CVoidPFromParamNode> rawFactory = CVoidPFromParamNodeFactory.getInstance();
        Builtin rawNodeBuiltin = CVoidPFromParamNode.class.getAnnotation(Builtin.class);
        addClassMethod(factory, language, type, rawFactory, rawNodeBuiltin);
    }

    @TruffleBoundary
    protected static void addCCharPFromParam(PythonObjectSlowPathFactory factory, PythonLanguage language, Object type) {
        NodeFactory<CCharPFromParamNode> rawFactory = CCharPFromParamNodeFactory.getInstance();
        Builtin rawNodeBuiltin = CCharPFromParamNode.class.getAnnotation(Builtin.class);
        addClassMethod(factory, language, type, rawFactory, rawNodeBuiltin);
    }

    @TruffleBoundary
    protected static void addCWCharPFromParam(PythonObjectSlowPathFactory factory, PythonLanguage language, Object type) {
        NodeFactory<CWCharPFromParamNode> rawFactory = CWCharPFromParamNodeFactory.getInstance();
        Builtin rawNodeBuiltin = CWCharPFromParamNode.class.getAnnotation(Builtin.class);
        addClassMethod(factory, language, type, rawFactory, rawNodeBuiltin);
    }

    @TruffleBoundary
    private static void addClassMethod(PythonObjectSlowPathFactory objectFactory, PythonLanguage language, Object type, NodeFactory<? extends PythonBuiltinBaseNode> nodeFactory, Builtin builtin) {
        TruffleString name = toTruffleStringUncached(builtin.name());
        Object builtinDoc = PNone.NONE;
        RootCallTarget callTarget = language.createCachedCallTarget(
                        l -> new BuiltinFunctionRootNode(l, builtin, nodeFactory, true),
                        nodeFactory.getNodeClass(),
                        builtin.name());
        int flags = PBuiltinFunction.getFlags(builtin, callTarget);
        PBuiltinFunction function = objectFactory.createBuiltinFunction(name, type, 1, flags, callTarget);
        PDecoratedMethod classMethod = objectFactory.createClassmethodFromCallableObj(function);
        function.setAttribute(T___DOC__, builtinDoc);
        WriteAttributeToObjectNode.getUncached(true).execute(type, name, classMethod);
    }

    @ImportStatic(CDataTypeBuiltins.class)
    @Builtin(name = J_FROM_PARAM, minNumOfPositionalArgs = 2, declaresExplicitSelf = true)
    @GenerateNodeFactory
    protected abstract static class CWCharPFromParamNode extends PythonBinaryBuiltinNode {

        @SuppressWarnings("unused")
        @Specialization
        Object none(Object type, PNone value) {
            /* None */
            return PNone.NONE;
        }

        @Specialization(guards = "!isNone(value)")
        Object c_wchar_p_from_param(VirtualFrame frame, Object type, Object value,
                        @Cached SetFuncNode setFuncNode,
                        @Cached IsInstanceNode isInstanceNode,
                        @Cached PyTypeCheck pyTypeCheck,
                        @Cached PyTypeStgDictNode pyTypeStgDictNode,
                        @Cached PyObjectStgDictNode pyObjectStgDictNode,
                        @Cached CWCharPFromParamNode cwCharPFromParamNode,
                        @Cached PyObjectLookupAttr lookupAttr) {
            if (PGuards.isString(value)) {
                PyCArgObject parg = factory().createCArgObject();
                parg.pffi_type = ffi_type_pointer;
                parg.tag = 'Z';
                parg.valuePointer = Pointer.allocate(parg.pffi_type, parg.pffi_type.size);
                parg.obj = setFuncNode.execute(frame, FieldDesc.Z.setfunc, parg.valuePointer, value, 0);
                return parg;
            }
            boolean res = isInstanceNode.executeWith(frame, value, type);
            if (res) {
                return value;
            }
            if (pyTypeCheck.isArrayObject(value) || pyTypeCheck.isPointerObject(value)) {
                /* c_wchar array instance or pointer(c_wchar(...)) */
                StgDictObject dt = pyObjectStgDictNode.execute(value);
                assert dt != null : "Cannot be NULL for pointer or array objects";
                StgDictObject dict = dt.proto != null ? pyTypeStgDictNode.execute(dt.proto) : null;
                if (dict != null && (dict.setfunc == FieldDesc.u.setfunc)) {
                    return value;
                }
            }
            if (PGuards.isPyCArg(value)) {
                /* byref(c_char(...)) */
                PyCArgObject a = (PyCArgObject) value;
                StgDictObject dict = pyObjectStgDictNode.execute(a.obj);
                if (dict != null && (dict.setfunc == FieldDesc.u.setfunc)) {
                    return value;
                }
            }

            Object as_parameter = lookupAttr.execute(frame, value, T__AS_PARAMETER_);
            if (as_parameter != PNone.NO_VALUE) {
                return cwCharPFromParamNode.execute(frame, type, as_parameter);
            }
            throw raise(TypeError, WRONG_TYPE);
        }
    }

    @ImportStatic(CDataTypeBuiltins.class)
    @Builtin(name = J_FROM_PARAM, minNumOfPositionalArgs = 2, declaresExplicitSelf = true)
    @GenerateNodeFactory
    protected abstract static class CVoidPFromParamNode extends PythonBinaryBuiltinNode {

        @SuppressWarnings("unused")
        @Specialization
        Object none(Object type, PNone value) {
            /* None */
            return PNone.NONE;
        }

        protected static boolean isLong(Object value, PyLongCheckNode longCheckNode) {
            return value instanceof PythonNativeVoidPtr || longCheckNode.execute(value); // PyLong_Check
        }

        @Specialization(guards = "isLong(value, longCheckNode)")
        Object voidPtr(@SuppressWarnings("unused") Object type, Object value,
                        @SuppressWarnings("unused") @Cached PyLongCheckNode longCheckNode,
                        @Exclusive @Cached SetFuncNode setFuncNode) {
            /* int, long */
            PyCArgObject parg = factory().createCArgObject();
            parg.pffi_type = ffi_type_pointer;
            parg.tag = 'P';
            parg.valuePointer = Pointer.allocate(parg.pffi_type, parg.pffi_type.size);
            setFuncNode.execute(null, FFIType.FieldSet.P_set, parg.valuePointer, value, 0);
            parg.obj = PNone.NONE;
            return parg;
        }

        @Specialization
        Object bytes(@SuppressWarnings("unused") Object type, PBytes value,
                        @Exclusive @Cached SetFuncNode setFuncNode) {
            /* bytes */
            PyCArgObject parg = factory().createCArgObject();
            parg.pffi_type = ffi_type_pointer;
            parg.tag = 'z';
            parg.valuePointer = Pointer.allocate(parg.pffi_type, parg.pffi_type.size);
            setFuncNode.execute(null, FFIType.FieldSet.z_set, parg.valuePointer, value, 0);
            parg.obj = value;
            return parg;
        }

        @Specialization
        Object string(@SuppressWarnings("unused") Object type, TruffleString value,
                        @Exclusive @Cached SetFuncNode setFuncNode) {
            /* unicode */
            PyCArgObject parg = factory().createCArgObject();
            parg.pffi_type = ffi_type_pointer;
            parg.tag = 'Z';
            parg.valuePointer = Pointer.allocate(parg.pffi_type, parg.pffi_type.size);
            setFuncNode.execute(null, FFIType.FieldSet.Z_set, parg.valuePointer, value, 0);
            parg.obj = value;
            return parg;
        }

        @Specialization(guards = {"!isNone(value)", "!isPBytes(value)", "!isString(value)", "!isLong(value, longCheckNode)"})
        Object c_void_p_from_param(VirtualFrame frame, Object type, Object value,
                        @SuppressWarnings("unused") @Cached PyLongCheckNode longCheckNode,
                        @Cached PyTypeCheck pyTypeCheck,
                        @Cached IsInstanceNode isInstanceNode,
                        @Cached PyObjectStgDictNode pyObjectStgDictNode,
                        @Cached CVoidPFromParamNode cVoidPFromParamNode,
                        @Cached TruffleString.CodePointAtIndexNode codePointAtIndexNode,
                        @Cached PyObjectLookupAttr lookupAttr) {
            /* c_void_p instance (or subclass) */
            boolean res = isInstanceNode.executeWith(frame, value, type);
            if (res) {
                /* c_void_p instances */
                return value;
            }
            /* ctypes array or pointer instance */
            if (pyTypeCheck.isArrayObject(value) || pyTypeCheck.isPointerObject(value)) {
                /* Any array or pointer is accepted */
                return value;
            }
            /* byref(...) */
            if (PGuards.isPyCArg(value)) {
                /* byref(c_xxx()) */
                PyCArgObject a = (PyCArgObject) value;
                if (a.tag == 'P') {
                    return value;
                }
            }
            /* function pointer */
            if (value instanceof PyCFuncPtrObject func && pyTypeCheck.isPyCFuncPtrObject(value)) {
                PyCArgObject parg = factory().createCArgObject();
                parg.pffi_type = ffi_type_pointer;
                parg.tag = 'P';
                parg.valuePointer = func.b_ptr;
                parg.obj = value;
                return parg;
            }
            /* c_char_p, c_wchar_p */
            StgDictObject stgd = pyObjectStgDictNode.execute(value);
            if (stgd != null && pyTypeCheck.isCDataObject(value) && PGuards.isTruffleString(stgd.proto)) { // PyUnicode_Check
                int code = codePointAtIndexNode.execute((TruffleString) stgd.proto, 0, TS_ENCODING);
                /* c_char_p, c_wchar_p */
                if (code == 'z' || code == 'Z') {
                    PyCArgObject parg = factory().createCArgObject();
                    parg.pffi_type = ffi_type_pointer;
                    parg.tag = 'Z';
                    parg.obj = value;
                    /* Remember: b_ptr points to where the pointer is stored! */
                    parg.valuePointer = ((CDataObject) value).b_ptr;
                    return parg;
                }
            }

            Object as_parameter = lookupAttr.execute(frame, value, T__AS_PARAMETER_);
            if (as_parameter != PNone.NO_VALUE) {
                return cVoidPFromParamNode.execute(frame, type, as_parameter);
            }
            throw raise(TypeError, WRONG_TYPE);
        }
    }

    @ImportStatic(CDataTypeBuiltins.class)
    @Builtin(name = J_FROM_PARAM, minNumOfPositionalArgs = 2) // , declaresExplicitSelf = true)
    @GenerateNodeFactory
    protected abstract static class CCharPFromParamNode extends PythonBinaryBuiltinNode {

        @SuppressWarnings("unused")
        @Specialization
        Object none(Object type, PNone value) {
            /* None */
            return PNone.NONE;
        }

        @Specialization
        Object bytes(@SuppressWarnings("unused") Object type, PBytes value,
                        @Cached SetFuncNode setFuncNode) {
            PyCArgObject parg = factory().createCArgObject();
            parg.pffi_type = ffi_type_pointer;
            parg.tag = 'z';
            parg.valuePointer = Pointer.allocate(parg.pffi_type, parg.pffi_type.size);
            setFuncNode.execute(null, FFIType.FieldSet.z_set, parg.valuePointer, value, 0);
            parg.obj = value;
            return parg;
        }

        @Specialization(guards = {"!isNone(value)", "!isBytes(value)"})
        Object c_char_p_from_param(VirtualFrame frame, Object type, Object value,
                        @Cached IsInstanceNode isInstanceNode,
                        @Cached PyTypeCheck pyTypeCheck,
                        @Cached PyTypeStgDictNode pyTypeStgDictNode,
                        @Cached PyObjectStgDictNode pyObjectStgDictNode,
                        @Cached CCharPFromParamNode cCharPFromParamNode,
                        @Cached PyObjectLookupAttr lookupAttr) {
            boolean res = isInstanceNode.executeWith(frame, value, type);
            if (res) {
                return value;
            }
            if (pyTypeCheck.isArrayObject(value) || pyTypeCheck.isPointerObject(value)) {
                /* c_char array instance or pointer(c_char(...)) */
                StgDictObject dt = pyObjectStgDictNode.execute(value);
                assert dt != null : "Cannot be NULL for pointer or array objects";
                StgDictObject dict = dt.proto != null ? pyTypeStgDictNode.execute(dt.proto) : null;
                if (dict != null && (dict.setfunc == FieldDesc.c.setfunc)) {
                    return value;
                }
            }
            if (PGuards.isPyCArg(value)) {
                /* byref(c_char(...)) */
                PyCArgObject a = (PyCArgObject) value;
                StgDictObject dict = pyObjectStgDictNode.execute(a.obj);
                if (dict != null && (dict.setfunc == FieldDesc.c.setfunc)) {
                    return value;
                }
            }

            Object as_parameter = lookupAttr.execute(frame, value, T__AS_PARAMETER_);
            if (as_parameter != PNone.NO_VALUE) {
                return cCharPFromParamNode.execute(frame, type, as_parameter);
            }
            throw raise(TypeError, WRONG_TYPE);
        }
    }
}
