/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.modules.ctypes.CDataTypeBuiltins.from_param;
import static com.oracle.graal.python.builtins.modules.ctypes.FFIType.ffi_type_pointer;
import static com.oracle.graal.python.builtins.modules.ctypes.FFIType.nfi_type_string;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__DOC__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.IsInstanceNode;
import com.oracle.graal.python.builtins.modules.ctypes.CFieldBuiltins.SetFuncNode;
import com.oracle.graal.python.builtins.modules.ctypes.CtypesNodes.PyTypeCheck;
import com.oracle.graal.python.builtins.modules.ctypes.CtypesNodes.PyTypeCheckExact;
import com.oracle.graal.python.builtins.modules.ctypes.FFIType.FieldDesc;
import com.oracle.graal.python.builtins.modules.ctypes.LazyPyCSimpleTypeBuiltinsFactory.CCharPFromParamNodeFactory;
import com.oracle.graal.python.builtins.modules.ctypes.LazyPyCSimpleTypeBuiltinsFactory.CVoidPFromParamNodeFactory;
import com.oracle.graal.python.builtins.modules.ctypes.LazyPyCSimpleTypeBuiltinsFactory.CWCharPFromParamNodeFactory;
import com.oracle.graal.python.builtins.modules.ctypes.StgDictBuiltins.PyObjectStgDictNode;
import com.oracle.graal.python.builtins.modules.ctypes.StgDictBuiltins.PyTypeStgDictNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.lib.PyLongCheckNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

public class LazyPyCSimpleTypeBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        throw CompilerDirectives.shouldNotReachHere("Should not be part of initialization!");
    }

    @TruffleBoundary
    protected static void addCVoidPFromParam(PythonLanguage language, Object type) {
        NodeFactory<CVoidPFromParamNode> rawFactory = CVoidPFromParamNodeFactory.getInstance();
        Builtin rawNodeBuiltin = CVoidPFromParamNode.class.getAnnotation(Builtin.class);
        addClassMethod(language, type, rawFactory, rawNodeBuiltin);
    }

    @TruffleBoundary
    protected static void addCCharPFromParam(PythonLanguage language, Object type) {
        NodeFactory<CCharPFromParamNode> rawFactory = CCharPFromParamNodeFactory.getInstance();
        Builtin rawNodeBuiltin = CCharPFromParamNode.class.getAnnotation(Builtin.class);
        addClassMethod(language, type, rawFactory, rawNodeBuiltin);
    }

    @TruffleBoundary
    protected static void addCWCharPFromParam(PythonLanguage language, Object type) {
        NodeFactory<CWCharPFromParamNode> rawFactory = CWCharPFromParamNodeFactory.getInstance();
        Builtin rawNodeBuiltin = CWCharPFromParamNode.class.getAnnotation(Builtin.class);
        addClassMethod(language, type, rawFactory, rawNodeBuiltin);
    }

    @TruffleBoundary
    private static void addClassMethod(PythonLanguage language, Object type, NodeFactory<? extends PythonBuiltinBaseNode> factory, Builtin builtin) {
        String name = builtin.name();
        Object builtinDoc = PNone.NONE;
        RootCallTarget callTarget = language.createCachedCallTarget(
                        l -> new BuiltinFunctionRootNode(l, builtin, factory, false),
                        factory.getNodeClass(),
                        builtin.name());
        int flags = PBuiltinFunction.getFlags(builtin, callTarget);
        PythonObjectFactory f = PythonObjectFactory.getUncached();
        PBuiltinFunction function = f.createBuiltinFunction(builtin.name(), type, 1, flags, callTarget);
        function.setAttribute(__DOC__, builtinDoc);
        WriteAttributeToObjectNode.getUncached(true).execute(type, name, function);
    }

    @ImportStatic(CDataTypeBuiltins.class)
    @Builtin(name = from_param, minNumOfPositionalArgs = 2, declaresExplicitSelf = true)
    @GenerateNodeFactory
    public abstract static class CWCharPFromParamNode extends PythonBinaryBuiltinNode {

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
                        @Cached PyTypeCheckExact pyTypeCheckExact,
                        @Cached PyTypeStgDictNode pyTypeStgDictNode,
                        @Cached PyObjectStgDictNode pyObjectStgDictNode,
                        @Cached CWCharPFromParamNode cwCharPFromParamNode,
                        @Cached("create(_as_parameter_)") LookupAttributeInMRONode lookupAsParam) {
            if (PGuards.isString(value)) {
                PyCArgObject parg;
                FieldDesc fd = FieldDesc.Z;
                parg = factory().createCArgObject();
                parg.pffi_type = nfi_type_string;
                parg.tag = 'Z';
                parg.value = PtrValue.bytes(PythonUtils.EMPTY_BYTE_ARRAY);
                parg.obj = setFuncNode.execute(frame, fd.setfunc, parg.value, value, 0);
                return parg;
            }
            boolean res = isInstanceNode.executeWith(frame, value, type);
            if (res) {
                return value;
            }
            if (pyTypeCheck.isArrayObject(value) || pyTypeCheck.isPointerObject(value)) {
                /* c_wchar array instance or pointer(c_wchar(...)) */
                StgDictObject dt = pyObjectStgDictNode.execute(value);
                StgDictObject dict;
                assert dt != null; /* Cannot be NULL for pointer or array objects */
                dict = dt.proto != null ? pyTypeStgDictNode.execute(dt.proto) : null;
                if (dict != null && (dict.setfunc == FieldDesc.u.setfunc)) {
                    return value;
                }
            }
            if (pyTypeCheckExact.isPyCArg(value)) {
                /* byref(c_char(...)) */
                PyCArgObject a = (PyCArgObject) value;
                StgDictObject dict = pyObjectStgDictNode.execute(a.obj);
                if (dict != null && (dict.setfunc == FieldDesc.u.setfunc)) {
                    return value;
                }
            }

            Object as_parameter = lookupAsParam.execute(value);
            if (as_parameter != null) {
                return cwCharPFromParamNode.call(frame, type, as_parameter);
            }
            /* XXX better message */
            throw raise(TypeError, "wrong type");
        }
    }

    @ImportStatic(CDataTypeBuiltins.class)
    @Builtin(name = from_param, minNumOfPositionalArgs = 2, declaresExplicitSelf = true)
    @GenerateNodeFactory
    public abstract static class CVoidPFromParamNode extends PythonBinaryBuiltinNode {

        @SuppressWarnings("unused")
        @Specialization
        Object none(Object type, PNone value) {
            /* None */
            return PNone.NONE;
        }

        @Specialization(guards = "!isNone(value)")
        Object c_void_p_from_param(VirtualFrame frame, Object type, Object value,
                        @Cached PyTypeCheckExact pyTypeCheckExact,
                        @Cached PyTypeCheck pyTypeCheck,
                        @Cached PyLongCheckNode longCheckNode,
                        @Cached SetFuncNode setFuncNode,
                        @Cached IsInstanceNode isInstanceNode,
                        @Cached PyObjectStgDictNode pyObjectStgDictNode,
                        @Cached CVoidPFromParamNode cVoidPFromParamNode,
                        @Cached("create(_as_parameter_)") LookupAttributeInMRONode lookupAsParam) {
            /* Should probably allow buffer interface as well */
            /* int, long */
            if (longCheckNode.execute(value)) { // PyLong_Check
                FieldDesc fd = FieldDesc.P;
                PyCArgObject parg = factory().createCArgObject();
                parg.pffi_type = ffi_type_pointer;
                parg.tag = 'P';
                parg.value = PtrValue.object(value);
                parg.obj = setFuncNode.execute(frame, fd.setfunc, parg.value, value, 0);
                return parg;
            }
            /* XXX struni: remove later */
            /* bytes */
            if (PGuards.isBytes(value)) { // PyBytes_Check
                FieldDesc fd = FieldDesc.z;
                PyCArgObject parg = factory().createCArgObject();
                parg.pffi_type = nfi_type_string;
                parg.tag = 'z';
                parg.value = PtrValue.bytes(PythonUtils.EMPTY_BYTE_ARRAY);
                parg.obj = setFuncNode.execute(frame, fd.setfunc, parg.value, value, 0);
                return parg;
            }
            /* unicode */
            if (PGuards.isString(value)) { // PyUnicode_Check
                FieldDesc fd = FieldDesc.Z;
                PyCArgObject parg = factory().createCArgObject();
                parg.pffi_type = nfi_type_string;
                parg.tag = 'Z';
                parg.value = PtrValue.bytes(PythonUtils.EMPTY_BYTE_ARRAY);
                parg.obj = setFuncNode.execute(frame, fd.setfunc, parg.value, value, 0);
                return parg;
            }
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
            if (pyTypeCheckExact.isPyCArg(value)) {
                /* byref(c_xxx()) */
                PyCArgObject a = (PyCArgObject) value;
                if (a.tag == 'P') {
                    return value;
                }
            }
            /* function pointer */
            if (value instanceof PyCFuncPtrObject && pyTypeCheck.isPyCFuncPtrObject(value)) {
                PyCFuncPtrObject func = (PyCFuncPtrObject) value;
                PyCArgObject parg = factory().createCArgObject();
                parg.pffi_type = ffi_type_pointer;
                parg.tag = 'P';
                parg.value = func.b_ptr;
                parg.obj = value;
                return parg;
            }
            /* c_char_p, c_wchar_p */
            StgDictObject stgd = pyObjectStgDictNode.execute(value);
            if (stgd != null && pyTypeCheck.isCDataObject(value) && PGuards.isString(stgd.proto)) { // PyUnicode_Check
                switch (PString.charAt((String) stgd.proto, 0)) {
                    case 'z': /* c_char_p */
                    case 'Z': /* c_wchar_p */
                        PyCArgObject parg = factory().createCArgObject();
                        parg.pffi_type = nfi_type_string;
                        parg.tag = 'Z';
                        parg.obj = value;
                        /* Remember: b_ptr points to where the pointer is stored! */
                        parg.value = (((CDataObject) value).b_ptr);
                        return parg;
                }
            }

            Object as_parameter = lookupAsParam.execute(value);
            if (as_parameter != null) {
                return cVoidPFromParamNode.call(frame, type, as_parameter);
            }
            /* XXX better message */
            throw raise(TypeError, "wrong type");
        }
    }

    @ImportStatic(CDataTypeBuiltins.class)
    @Builtin(name = from_param, minNumOfPositionalArgs = 2, declaresExplicitSelf = true)
    @GenerateNodeFactory
    public abstract static class CCharPFromParamNode extends PythonBinaryBuiltinNode {

        @SuppressWarnings("unused")
        @Specialization
        Object none(Object type, PNone value) {
            /* None */
            return PNone.NONE;
        }

        @Specialization(guards = "!isNone(value)")
        Object c_char_p_from_param(VirtualFrame frame, Object type, Object value,
                        @Cached PyTypeCheckExact pyTypeCheckExact,
                        @Cached SetFuncNode setFuncNode,
                        @Cached IsInstanceNode isInstanceNode,
                        @Cached PyTypeCheck pyTypeCheck,
                        @Cached PyTypeStgDictNode pyTypeStgDictNode,
                        @Cached PyObjectStgDictNode pyObjectStgDictNode,
                        @Cached CCharPFromParamNode cCharPFromParamNode,
                        @Cached("create(_as_parameter_)") LookupAttributeInMRONode lookupAsParam) {
            if (PGuards.isBytes(value)) { // PyBytes_Check
                FieldDesc fd = FieldDesc.z;
                PyCArgObject parg = factory().createCArgObject();
                parg.pffi_type = nfi_type_string;
                parg.tag = 'z';
                parg.value.specialize(parg.pffi_type.size, 0, value);
                parg.obj = setFuncNode.execute(frame, fd.setfunc, parg.value, value, 0);
                return parg;
            }
            boolean res = isInstanceNode.executeWith(frame, value, type);
            if (res) {
                return value;
            }
            if (pyTypeCheck.isArrayObject(value) || pyTypeCheck.isPointerObject(value)) {
                /* c_char array instance or pointer(c_char(...)) */
                StgDictObject dt = pyObjectStgDictNode.execute(value);
                assert dt != null; /* Cannot be NULL for pointer or array objects */
                StgDictObject dict = dt.proto != null ? pyTypeStgDictNode.execute(dt.proto) : null;
                if (dict != null && (dict.setfunc == FieldDesc.c.setfunc)) {
                    return value;
                }
            }
            if (pyTypeCheckExact.isPyCArg(value)) {
                /* byref(c_char(...)) */
                PyCArgObject a = (PyCArgObject) value;
                StgDictObject dict = pyObjectStgDictNode.execute(a.obj);
                if (dict != null && (dict.setfunc == FieldDesc.c.setfunc)) {
                    return value;
                }
            }

            Object as_parameter = lookupAsParam.execute(value);
            if (as_parameter != null) {
                return cCharPFromParamNode.call(frame, type, as_parameter);
            }
            /* XXX better message */
            throw raise(TypeError, "wrong type");
        }
    }
}
