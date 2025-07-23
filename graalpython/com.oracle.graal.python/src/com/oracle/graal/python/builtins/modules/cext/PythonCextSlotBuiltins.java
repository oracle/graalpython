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

import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Ignored;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.ConstCharPtrAsTruffleString;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Int;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Pointer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyASCIIObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyByteArrayObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyCFunctionObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyCMethodObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyDescrObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyFrameObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyGetSetDef;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyInstanceMethodObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyListObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyLongObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyMethodDef;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyMethodDescrObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyMethodObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyModuleDef;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyModuleObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectBorrowed;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectPtr;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectWrapper;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PySetObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PySliceObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyTupleObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyTypeObjectBorrowed;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyUnicodeObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyVarObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Py_ssize_t;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.UINTPTR_T;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.UNSIGNED_INT;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Void;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.getter;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.setter;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.vectorcallfunc;
import static com.oracle.graal.python.nodes.HiddenAttr.METHOD_DEF_PTR;
import static com.oracle.graal.python.nodes.HiddenAttr.NATIVE_STORAGE;
import static com.oracle.graal.python.nodes.HiddenAttr.PROMOTED_START;
import static com.oracle.graal.python.nodes.HiddenAttr.PROMOTED_STEP;
import static com.oracle.graal.python.nodes.HiddenAttr.PROMOTED_STOP;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___MODULE__;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBinaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiUnaryBuiltinNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.PByteArray;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.AsCharPointerNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ObSizeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.PyMethodDefHelper;
import com.oracle.graal.python.builtins.objects.cext.capi.PySequenceArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapper.PythonAbstractObjectNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageLen;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.GetSetDescriptor;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.method.PDecoratedMethod;
import com.oracle.graal.python.builtins.objects.method.PMethod;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.set.PBaseSet;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.str.NativeCharSequence;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.str.StringNodes;
import com.oracle.graal.python.builtins.objects.str.StringNodes.StringLenNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectSetAttr;
import com.oracle.graal.python.nodes.HiddenAttr;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode.GetFixedAttributeNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.NativeByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.NativeObjectSequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.InternalByteArray;
import com.oracle.truffle.api.strings.TruffleString;

public final class PythonCextSlotBuiltins {

    @CApiBuiltin(name = "GraalPyPrivate_Get_PyListObject_ob_item", ret = PyObjectPtr, args = {PyListObject}, call = Ignored)
    @CApiBuiltin(name = "GraalPyPrivate_Get_PyTupleObject_ob_item", ret = PyObjectPtr, args = {PyTupleObject}, call = Ignored)
    abstract static class GraalPyPrivate_Get_PSequence_ob_item extends CApiUnaryBuiltinNode {

        @Specialization
        static Object get(PSequence object) {
            assert !(object.getSequenceStorage() instanceof NativeByteSequenceStorage);
            return PySequenceArrayWrapper.ensureNativeSequence(object);
        }
    }

    @CApiBuiltin(ret = Py_ssize_t, args = {PyASCIIObject}, call = Ignored)
    abstract static class GraalPyPrivate_Get_PyASCIIObject_length extends CApiUnaryBuiltinNode {

        @Specialization
        static long get(Object object,
                        @Cached StringLenNode stringLenNode) {
            return stringLenNode.execute(object);
        }
    }

    @CApiBuiltin(ret = UNSIGNED_INT, args = {PyASCIIObject}, call = Ignored)
    abstract static class GraalPyPrivate_Get_PyASCIIObject_state_ascii extends CApiUnaryBuiltinNode {

        @Specialization
        int get(PString object,
                        @Bind Node inliningTarget,
                        @Cached InlinedConditionProfile storageProfile,
                        @Cached TruffleString.GetCodeRangeNode getCodeRangeNode) {
            // important: avoid materialization of native sequences
            if (storageProfile.profile(inliningTarget, object.isNativeCharSequence())) {
                return object.getNativeCharSequence().isAsciiOnly() ? 1 : 0;
            }

            TruffleString string = object.getMaterialized();
            return PInt.intValue(getCodeRangeNode.execute(string, TS_ENCODING) == TruffleString.CodeRange.ASCII);
        }
    }

    @CApiBuiltin(ret = UNSIGNED_INT, args = {PyASCIIObject}, call = Ignored)
    abstract static class GraalPyPrivate_Get_PyASCIIObject_state_compact extends CApiUnaryBuiltinNode {

        @Specialization
        static int get(@SuppressWarnings("unused") Object object) {
            return 0;
        }
    }

    @CApiBuiltin(ret = UNSIGNED_INT, args = {PyASCIIObject}, call = Ignored)
    abstract static class GraalPyPrivate_Get_PyASCIIObject_state_interned extends CApiUnaryBuiltinNode {

        @Specialization
        static int get(PString object,
                        @Bind Node inliningTarget,
                        @Cached StringNodes.IsInternedStringNode isInternedStringNode) {
            return isInternedStringNode.execute(inliningTarget, object) ? 1 : 0;
        }
    }

    @CApiBuiltin(ret = UNSIGNED_INT, args = {PyASCIIObject}, call = Ignored)
    abstract static class GraalPyPrivate_Get_PyASCIIObject_state_kind extends CApiUnaryBuiltinNode {

        @Specialization
        static int get(PString object,
                        @Bind Node inliningTarget,
                        @Cached InlinedConditionProfile storageProfile,
                        @Cached TruffleString.GetCodeRangeNode getCodeRangeNode) {
            // important: avoid materialization of native sequences
            if (storageProfile.profile(inliningTarget, object.isNativeCharSequence())) {
                return object.getNativeCharSequence().getElementSize() & 0b111;
            }
            TruffleString string = object.getMaterialized();
            TruffleString.CodeRange range = getCodeRangeNode.execute(string, TS_ENCODING);
            if (range.isSubsetOf(TruffleString.CodeRange.LATIN_1)) {
                return 1;
            } else if (range.isSubsetOf(TruffleString.CodeRange.BMP)) {
                return 2;
            } else {
                return 4;
            }
        }
    }

    @CApiBuiltin(ret = UNSIGNED_INT, args = {PyASCIIObject}, call = Ignored)
    abstract static class GraalPyPrivate_Get_PyASCIIObject_state_ready extends CApiUnaryBuiltinNode {

        @Specialization
        static int get(@SuppressWarnings("unused") Object object) {
            return 1;
        }
    }

    @CApiBuiltin(ret = PyTypeObjectBorrowed, args = {PyCMethodObject}, call = Ignored)
    abstract static class GraalPyPrivate_Get_PyCMethodObject_mm_class extends CApiUnaryBuiltinNode {
        @Specialization
        static Object get(PBuiltinMethod object) {
            return object.getClassObject();
        }
    }

    @CApiBuiltin(ret = PyMethodDef, args = {PyCFunctionObject}, call = Ignored)
    abstract static class GraalPyPrivate_Get_PyCFunctionObject_m_ml extends CApiUnaryBuiltinNode {
        @Specialization
        static Object get(PythonBuiltinObject object,
                        @Bind Node inliningTarget,
                        @Cached HiddenAttr.ReadNode readNode) {
            PBuiltinFunction resolved;
            if (object instanceof PBuiltinMethod builtinMethod) {
                resolved = builtinMethod.getBuiltinFunction();
            } else if (object instanceof PBuiltinFunction builtinFunction) {
                resolved = builtinFunction;
            } else {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw CompilerDirectives.shouldNotReachHere("requesting PyMethodDef for an incompatible function/method type: " + object.getClass().getSimpleName());
            }
            Object methodDefPtr = readNode.execute(inliningTarget, resolved, METHOD_DEF_PTR, null);
            if (methodDefPtr != null) {
                return methodDefPtr;
            }
            CApiContext cApiContext = getCApiContext(inliningTarget);
            return PyMethodDefHelper.create(cApiContext, resolved);
        }
    }

    @CApiBuiltin(ret = PyMethodDef, args = {PyCFunctionObject, PyMethodDef}, call = Ignored)
    abstract static class GraalPyPrivate_Set_PyCFunctionObject_m_ml extends CApiBinaryBuiltinNode {
        @Specialization
        static Object get(PythonBuiltinObject object, Object methodDefPtr,
                        @Bind Node inliningTarget,
                        @Cached HiddenAttr.WriteNode writeNode) {
            PBuiltinFunction resolved;
            if (object instanceof PBuiltinMethod builtinMethod) {
                resolved = builtinMethod.getBuiltinFunction();
            } else if (object instanceof PBuiltinFunction builtinFunction) {
                resolved = builtinFunction;
            } else {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw CompilerDirectives.shouldNotReachHere("writing PyMethodDef for an incompatible function/method type: " + object.getClass().getSimpleName());
            }
            writeNode.execute(inliningTarget, resolved, METHOD_DEF_PTR, methodDefPtr);
            return PNone.NO_VALUE;
        }
    }

    @CApiBuiltin(ret = PyObjectBorrowed, args = {PyCFunctionObject}, call = Ignored)
    abstract static class GraalPyPrivate_Get_PyCFunctionObject_m_module extends CApiUnaryBuiltinNode {
        @Specialization
        Object get(Object object,
                        @Bind Node inliningTarget,
                        @Cached PyObjectLookupAttr lookup) {
            Object module = lookup.execute(null, inliningTarget, object, T___MODULE__);
            return module != PNone.NO_VALUE ? module : getNativeNull();
        }
    }

    @CApiBuiltin(ret = Void, args = {PyCFunctionObject, PyObjectBorrowed}, call = Ignored)
    abstract static class GraalPyPrivate_Set_PyCFunctionObject_m_module extends CApiBinaryBuiltinNode {
        @Specialization
        Object set(Object object, Object value,
                        @Bind Node inliningTarget,
                        @Cached PyObjectSetAttr setattr) {
            setattr.execute(null, inliningTarget, object, T___MODULE__, value);
            return PNone.NO_VALUE;
        }
    }

    @CApiBuiltin(ret = PyObjectBorrowed, args = {PyCFunctionObject}, call = Ignored)
    abstract static class GraalPyPrivate_Get_PyCFunctionObject_m_self extends CApiUnaryBuiltinNode {
        @Specialization
        static Object get(PBuiltinMethod object) {
            return object.getSelf();
        }

        @Specialization
        static Object get(PMethod object) {
            return object.getSelf();
        }
    }

    @CApiBuiltin(ret = PyObjectBorrowed, args = {PyCFunctionObject}, call = Ignored)
    abstract static class GraalPyPrivate_Get_PyCFunctionObject_m_weakreflist extends CApiUnaryBuiltinNode {
        @Specialization
        static int get(@SuppressWarnings("unused") Object object) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @CApiBuiltin(ret = vectorcallfunc, args = {PyCFunctionObject}, call = Ignored)
    abstract static class GraalPyPrivate_Get_PyCFunctionObject_vectorcall extends CApiUnaryBuiltinNode {
        @Specialization
        static int get(@SuppressWarnings("unused") Object object) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @CApiBuiltin(ret = Pointer, args = {PyByteArrayObject}, call = Ignored)
    abstract static class GraalPyPrivate_Get_PyByteArrayObject_ob_start extends CApiUnaryBuiltinNode {

        @Specialization
        static Object doObStart(PByteArray object) {
            assert !(object.getSequenceStorage() instanceof NativeObjectSequenceStorage);
            return PySequenceArrayWrapper.ensureNativeSequence(object);
        }
    }

    @CApiBuiltin(ret = Py_ssize_t, args = {PyByteArrayObject}, call = Ignored)
    abstract static class GraalPyPrivate_Get_PyByteArrayObject_ob_exports extends CApiUnaryBuiltinNode {

        @Specialization
        static long get(PByteArray object) {
            return object.getExports();
        }
    }

    @CApiBuiltin(ret = Void, args = {PyByteArrayObject, Int}, call = Ignored)
    abstract static class GraalPyPrivate_Set_PyByteArrayObject_ob_exports extends CApiBinaryBuiltinNode {

        @Specialization
        static Object set(PByteArray object, int value) {
            object.setExports(value);
            return PNone.NO_VALUE;
        }
    }

    @CApiBuiltin(ret = PyObjectBorrowed, args = {PyDescrObject}, call = Ignored)
    abstract static class GraalPyPrivate_Get_PyDescrObject_d_name extends CApiUnaryBuiltinNode {

        @Specialization
        static Object get(PBuiltinFunction object) {
            return object.getCApiName();
        }

        @Specialization
        static Object get(GetSetDescriptor object) {
            return object.getCApiName();
        }
    }

    @CApiBuiltin(ret = PyTypeObjectBorrowed, args = {PyDescrObject}, call = Ignored)
    abstract static class GraalPyPrivate_Get_PyDescrObject_d_type extends CApiUnaryBuiltinNode {

        @Specialization
        Object get(PBuiltinFunction object) {
            Object enclosingType = object.getEnclosingType();
            return enclosingType != null ? enclosingType : getNativeNull();
        }

        @Specialization
        static Object get(GetSetDescriptor object) {
            return object.getType();
        }
    }

    @CApiBuiltin(ret = Int, args = {PyFrameObject}, call = Ignored)
    abstract static class GraalPyPrivate_Get_PyFrameObject_f_lineno extends CApiUnaryBuiltinNode {
        @Specialization
        static int get(PFrame frame) {
            return frame.getLine();
        }
    }

    @CApiBuiltin(ret = Pointer, args = {PyGetSetDef}, call = Ignored)
    abstract static class GraalPyPrivate_Get_PyGetSetDef_closure extends CApiUnaryBuiltinNode {
        @Specialization
        static int get(@SuppressWarnings("unused") Object object) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @CApiBuiltin(ret = ConstCharPtrAsTruffleString, args = {PyGetSetDef}, call = Ignored)
    abstract static class GraalPyPrivate_Get_PyGetSetDef_doc extends CApiUnaryBuiltinNode {
        @Specialization
        Object get(PythonObject object,
                        @Cached(parameters = "T___DOC__") GetFixedAttributeNode getAttrNode,
                        @Cached AsCharPointerNode asCharPointerNode) {
            Object doc = getAttrNode.execute(null, object);
            if (PGuards.isPNone(doc)) {
                return getNULL();
            } else {
                return asCharPointerNode.execute(doc);
            }
        }
    }

    @CApiBuiltin(ret = getter, args = {PyGetSetDef}, call = Ignored)
    abstract static class GraalPyPrivate_Get_PyGetSetDef_get extends CApiUnaryBuiltinNode {
        @Specialization
        static int get(@SuppressWarnings("unused") Object object) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @CApiBuiltin(ret = ConstCharPtrAsTruffleString, args = {PyGetSetDef}, call = Ignored)
    abstract static class GraalPyPrivate_Get_PyGetSetDef_name extends CApiUnaryBuiltinNode {
        @Specialization
        Object get(PythonObject object,
                        @Cached(parameters = "T___NAME__") GetFixedAttributeNode getAttrNode,
                        @Cached AsCharPointerNode asCharPointerNode) {
            Object name = getAttrNode.execute(null, object);
            if (PGuards.isPNone(name)) {
                return getNULL();
            } else {
                return asCharPointerNode.execute(name);
            }
        }
    }

    @CApiBuiltin(ret = setter, args = {PyGetSetDef}, call = Ignored)
    abstract static class GraalPyPrivate_Get_PyGetSetDef_set extends CApiUnaryBuiltinNode {
        @Specialization
        static int get(@SuppressWarnings("unused") Object object) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @CApiBuiltin(ret = PyMethodDef, args = {PyMethodDescrObject}, call = Ignored)
    abstract static class GraalPyPrivate_Get_PyMethodDescrObject_d_method extends CApiUnaryBuiltinNode {

        @Specialization
        static Object get(PBuiltinFunction builtinFunction,
                        @Bind Node inliningTarget,
                        @Cached HiddenAttr.ReadNode readNode) {
            Object methodDefPtr = readNode.execute(inliningTarget, builtinFunction, METHOD_DEF_PTR, null);
            if (methodDefPtr != null) {
                return methodDefPtr;
            }
            /*
             * Note: 'PBuiltinFunction' is the only Java class we use to represent a
             * 'method_descriptor' (CPython type 'PyMethodDescr_Type').
             */
            return PyMethodDefHelper.create(getCApiContext(inliningTarget), builtinFunction);
        }
    }

    @CApiBuiltin(ret = PyObjectBorrowed, args = {PyInstanceMethodObject}, call = Ignored)
    abstract static class GraalPyPrivate_Get_PyInstanceMethodObject_func extends CApiUnaryBuiltinNode {
        @Specialization
        static Object get(PDecoratedMethod object) {
            return object.getCallable();
        }
    }

    @CApiBuiltin(ret = PyObjectBorrowed, args = {PyMethodObject}, call = Ignored)
    abstract static class GraalPyPrivate_Get_PyMethodObject_im_func extends CApiUnaryBuiltinNode {
        @Specialization
        static Object get(PBuiltinMethod object) {
            return object.getFunction();
        }

        @Specialization
        static Object get(PMethod object) {
            return object.getFunction();
        }
    }

    @CApiBuiltin(ret = PyObjectBorrowed, args = {PyMethodObject}, call = Ignored)
    abstract static class GraalPyPrivate_Get_PyMethodObject_im_self extends CApiUnaryBuiltinNode {

        @Specialization
        static Object get(PBuiltinMethod object) {
            return object.getSelf();
        }

        @Specialization
        static Object get(PMethod object) {
            return object.getSelf();
        }
    }

    @CApiBuiltin(ret = ConstCharPtrAsTruffleString, args = {PyModuleDef}, call = Ignored)
    abstract static class GraalPyPrivate_Get_PyModuleDef_m_doc extends CApiUnaryBuiltinNode {
        @Specialization
        static int get(@SuppressWarnings("unused") Object object) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @CApiBuiltin(ret = PyMethodDef, args = {PyModuleDef}, call = Ignored)
    abstract static class GraalPyPrivate_Get_PyModuleDef_m_methods extends CApiUnaryBuiltinNode {
        @Specialization
        static int get(@SuppressWarnings("unused") Object object) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @CApiBuiltin(ret = ConstCharPtrAsTruffleString, args = {PyModuleDef}, call = Ignored)
    abstract static class GraalPyPrivate_Get_PyModuleDef_m_name extends CApiUnaryBuiltinNode {
        @Specialization
        static int get(@SuppressWarnings("unused") Object object) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @CApiBuiltin(ret = Py_ssize_t, args = {PyModuleDef}, call = Ignored)
    abstract static class GraalPyPrivate_Get_PyModuleDef_m_size extends CApiUnaryBuiltinNode {
        @Specialization
        static int get(@SuppressWarnings("unused") Object object) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @CApiBuiltin(ret = PyModuleDef, args = {PyModuleObject}, call = Ignored)
    abstract static class GraalPyPrivate_Get_PyModuleObject_md_def extends CApiUnaryBuiltinNode {
        @Specialization
        static Object get(PythonModule object) {
            return object.getNativeModuleDef();
        }
    }

    @CApiBuiltin(ret = PyObjectBorrowed, args = {PyModuleObject}, call = Ignored)
    abstract static class GraalPyPrivate_Get_PyModuleObject_md_dict extends CApiUnaryBuiltinNode {
        @Specialization
        static Object get(Object object,
                        @Exclusive @Cached(parameters = "T___DICT__") GetFixedAttributeNode getDictNode) {
            return getDictNode.execute(null, object);
        }
    }

    @CApiBuiltin(ret = Pointer, args = {PyModuleObject}, call = Ignored)
    abstract static class GraalPyPrivate_Get_PyModuleObject_md_state extends CApiUnaryBuiltinNode {
        @Specialization
        static Object get(PythonModule object,
                        @Bind Node inliningTarget) {
            return object.getNativeModuleState() != null ? object.getNativeModuleState() : PythonContext.get(inliningTarget).getNativeNull();
        }
    }

    @CApiBuiltin(ret = Py_ssize_t, args = {PyObjectWrapper}, call = Ignored)
    abstract static class GraalPyPrivate_Get_PyObject_ob_refcnt extends CApiUnaryBuiltinNode {

        @Specialization
        static Object get(PythonAbstractObjectNativeWrapper wrapper) {
            /*
             * We are allocating native object stubs for each wrapper. Therefore, reference counting
             * should only be done on the native side. However, we allow access for debugging
             * purposes.
             */
            if (PythonContext.DEBUG_CAPI) {
                return wrapper.getRefCount();
            }
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @CApiBuiltin(ret = PyTypeObjectBorrowed, args = {PyObject}, call = Ignored)
    abstract static class GraalPyPrivate_Get_PyObject_ob_type extends CApiUnaryBuiltinNode {

        @Specialization
        static Object get(Object object,
                        @Bind Node inliningTarget) {
            /*
             * We are allocating native object stubs for each wrapper. Therefore, accesses to
             * 'ob_type' should only be done on the native side. However, we allow access for
             * debugging purposes and in managed mode.
             */
            assert PythonContext.DEBUG_CAPI || !PythonContext.get(inliningTarget).isNativeAccessAllowed();
            Object result = GetClassNode.executeUncached(object);
            assert !(result instanceof Integer);
            return result;
        }
    }

    @CApiBuiltin(ret = Py_ssize_t, args = {PySetObject}, call = Ignored)
    abstract static class GraalPyPrivate_Get_PySetObject_used extends CApiUnaryBuiltinNode {

        @Specialization
        static long get(PBaseSet object,
                        @Bind Node inliningTarget,
                        @Cached HashingStorageLen lenNode) {
            return lenNode.execute(inliningTarget, object.getDictStorage());
        }
    }

    @GenerateInline
    @GenerateCached(false)
    abstract static class GetSliceField extends Node {
        abstract Object execute(Node inliningTarget, PSlice object, HiddenAttr key, Object value);

        @Specialization
        static Object get(Node inliningTarget, PSlice object, HiddenAttr key, Object value,
                        @Cached HiddenAttr.ReadNode read,
                        @Cached HiddenAttr.WriteNode write,
                        @Cached PythonCextBuiltins.PromoteBorrowedValue promote) {
            Object promotedValue = read.execute(inliningTarget, object, key, null);
            if (promotedValue == null) {
                promotedValue = promote.execute(inliningTarget, value);
                if (promotedValue == null) {
                    return value;
                }
                write.execute(inliningTarget, object, key, promotedValue);
            }
            return promotedValue;
        }
    }

    @CApiBuiltin(ret = PyObjectBorrowed, args = {PySliceObject}, call = Ignored)
    abstract static class GraalPyPrivate_Get_PySliceObject_start extends CApiUnaryBuiltinNode {

        @Specialization
        static Object doStart(PSlice object,
                        @Bind Node inliningTarget,
                        @Cached GetSliceField getSliceField) {
            return getSliceField.execute(inliningTarget, object, PROMOTED_START, object.getStart());
        }
    }

    @CApiBuiltin(ret = PyObjectBorrowed, args = {PySliceObject}, call = Ignored)
    abstract static class GraalPyPrivate_Get_PySliceObject_step extends CApiUnaryBuiltinNode {

        @Specialization
        static Object doStep(PSlice object,
                        @Bind Node inliningTarget,
                        @Cached GetSliceField getSliceField) {
            return getSliceField.execute(inliningTarget, object, PROMOTED_STEP, object.getStep());
        }
    }

    @CApiBuiltin(ret = PyObjectBorrowed, args = {PySliceObject}, call = Ignored)
    abstract static class GraalPyPrivate_Get_PySliceObject_stop extends CApiUnaryBuiltinNode {

        @Specialization
        static Object doStop(PSlice object,
                        @Bind Node inliningTarget,
                        @Cached GetSliceField getSliceField) {
            return getSliceField.execute(inliningTarget, object, PROMOTED_STOP, object.getStop());
        }
    }

    @CApiBuiltin(ret = Pointer, args = {PyUnicodeObject}, call = Ignored)
    abstract static class GraalPyPrivate_Get_PyUnicodeObject_data extends CApiUnaryBuiltinNode {

        @Specialization
        static Object get(PString object,
                        @Bind Node inliningTarget,
                        @Cached TruffleString.GetCodeRangeNode getCodeRangeNode,
                        @Cached TruffleString.SwitchEncodingNode switchEncodingNode,
                        @Cached TruffleString.GetInternalByteArrayNode getInternalByteArrayNode,
                        @Cached CStructAccess.AllocateNode allocateNode,
                        @Cached CStructAccess.WriteByteNode writeByteNode,
                        @Cached HiddenAttr.WriteNode writeAttribute) {
            if (object.isNativeCharSequence()) {
                // in this case, we can just return the pointer
                return object.getNativeCharSequence().getPtr();
            }
            TruffleString string = object.getMaterialized();
            TruffleString.CodeRange range = getCodeRangeNode.execute(string, TS_ENCODING);
            TruffleString.Encoding encoding;
            int charSize;
            boolean isAscii = false;
            if (range == TruffleString.CodeRange.ASCII) {
                isAscii = true;
                charSize = 1;
                encoding = TruffleString.Encoding.US_ASCII;
            } else if (range.isSubsetOf(TruffleString.CodeRange.LATIN_1)) {
                charSize = 1;
                encoding = TruffleString.Encoding.ISO_8859_1;
            } else if (range.isSubsetOf(TruffleString.CodeRange.BMP)) {
                charSize = 2;
                encoding = TruffleString.Encoding.UTF_16;
            } else {
                charSize = 4;
                encoding = TruffleString.Encoding.UTF_32;
            }
            string = switchEncodingNode.execute(string, encoding);
            InternalByteArray byteArray = getInternalByteArrayNode.execute(string, encoding);
            int byteLength = byteArray.getLength() + /* null terminator */ charSize;
            Object ptr = allocateNode.alloc(byteLength);
            writeByteNode.writeByteArray(ptr, byteArray.getArray(), byteArray.getLength(), byteArray.getOffset(), 0);
            /*
             * Set native char sequence, so we can just return the pointer the next time.
             */
            NativeCharSequence nativeSequence = new NativeCharSequence(ptr, byteArray.getLength() / charSize, charSize, isAscii);
            object.setNativeCharSequence(nativeSequence);
            /*
             * Create a native sequence storage to manage the lifetime of the native memory.
             *
             * TODO it would be nicer if the native char sequence could manage its own memory
             */
            writeAttribute.execute(inliningTarget, object, NATIVE_STORAGE, NativeByteSequenceStorage.create(ptr, byteLength, byteLength, true));
            return ptr;
        }
    }

    @CApiBuiltin(ret = Py_ssize_t, args = {PyVarObject}, call = Ignored)
    abstract static class GraalPyPrivate_Get_PyVarObject_ob_size extends CApiUnaryBuiltinNode {

        @Specialization
        static long get(Object object,
                        @Bind Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached TypeNodes.IsSameTypeNode isSameTypeNode,
                        @Cached ObSizeNode obSizeNode) {
            assert !isSameTypeNode.execute(inliningTarget, getClassNode.execute(inliningTarget, object), PythonBuiltinClassType.PInt);
            assert !isSameTypeNode.execute(inliningTarget, getClassNode.execute(inliningTarget, object), PythonBuiltinClassType.Boolean);
            return obSizeNode.execute(inliningTarget, object);
        }
    }

    @CApiBuiltin(ret = UINTPTR_T, args = {PyLongObject}, call = Ignored)
    abstract static class GraalPyPrivate_Get_PyLongObject_long_value_lv_tag extends CApiUnaryBuiltinNode {

        @Specialization
        static long getLvTag(Object n,
                        @Bind Node inliningTarget,
                        @Cached CExtNodes.LvTagNode lvTagNode) {
            return lvTagNode.execute(inliningTarget, n);
        }
    }
}
