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

import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Ignored;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.ConstCharPtrAsTruffleString;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Int;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PY_C_FUNCTION;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Pointer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyASCIIObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyBufferProcs;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyByteArrayObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyCFunctionObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyCMethodObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyCompactUnicodeObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyDescrObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyFrameObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyGetSetDef;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyInstanceMethodObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyListObject;
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
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyThreadState;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyTupleObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyTypeObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyUnicodeObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyVarObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Py_ssize_t;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.UNSIGNED_INT;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Void;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.WCHAR_T_PTR;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.destructor;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.getattrfunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.getattrofunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.getiterfunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.getter;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.inquiry;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.iternextfunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.newfunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.setattrfunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.setattrofunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.setter;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.traverseproc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.vectorcallfunc;
import static com.oracle.graal.python.builtins.objects.cext.structs.CConstants.SIZEOF_WCHAR_T;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DICTOFFSET__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DOC__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___MODULE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___NAME__;

import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBinaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiUnaryBuiltinNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.bytes.PByteArray;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.AsCharPointerNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ObSizeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.PThreadState;
import com.oracle.graal.python.builtins.objects.cext.capi.PyMethodDefWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PySequenceArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.UnicodeObjectNodes.UnicodeAsWideCharNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions;
import com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers.CStringWrapper;
import com.oracle.graal.python.builtins.objects.cext.common.CExtContext;
import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageForEach;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageForEachCallback;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetItemWithHash;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorKey;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorKeyHash;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorNext;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorValue;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageLen;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.GetSetDescriptor;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.method.PDecoratedMethod;
import com.oracle.graal.python.builtins.objects.method.PMethod;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.set.PBaseSet;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.str.StringNodes.StringLenNode;
import com.oracle.graal.python.builtins.objects.str.StringNodes.StringMaterializeNode;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.builtins.objects.type.TypeBuiltins;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetSubclassesNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.GetDictIfExistsNode;
import com.oracle.graal.python.nodes.object.InlinedGetClassNode;
import com.oracle.graal.python.nodes.object.SetDictNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.NativeByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.NativeObjectSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.api.strings.TruffleString;

public final class PythonCextSlotBuiltins {

    @CApiBuiltin(name = "Py_get_PyListObject_ob_item", ret = PyObjectPtr, args = {PyListObject}, call = Ignored)
    @CApiBuiltin(name = "Py_get_PyTupleObject_ob_item", ret = PyObjectPtr, args = {PyTupleObject}, call = Ignored)
    abstract static class Py_get_PSequence_ob_item extends CApiUnaryBuiltinNode {

        @Specialization
        static Object get(PSequence object,
                        @Cached("createClassProfile()") ValueProfile classProfile) {
            SequenceStorage sequenceStorage = classProfile.profile(object.getSequenceStorage());
            assert !(sequenceStorage instanceof NativeByteSequenceStorage);
            if (sequenceStorage instanceof NativeObjectSequenceStorage) {
                return ((NativeObjectSequenceStorage) sequenceStorage).getPtr();
            }
            return new PySequenceArrayWrapper(object, 4);
        }
    }

    @CApiBuiltin(ret = Py_ssize_t, args = {PyASCIIObject}, call = Ignored)
    abstract static class Py_get_PyASCIIObject_length extends CApiUnaryBuiltinNode {

        @Specialization
        static long get(Object object,
                        @Cached StringLenNode stringLenNode) {
            return stringLenNode.execute(object);
        }
    }

    @CApiBuiltin(ret = UNSIGNED_INT, args = {PyASCIIObject}, call = Ignored)
    abstract static class Py_get_PyASCIIObject_state_ascii extends CApiUnaryBuiltinNode {

        @TruffleBoundary
        private static boolean doCheck(TruffleString value, CharsetEncoder asciiEncoder) {
            asciiEncoder.reset();
            return asciiEncoder.canEncode(value.toJavaStringUncached());
        }

        @CompilationFinal private CharsetEncoder asciiEncoder;

        @Specialization
        int get(PString object,
                        @Cached ConditionProfile storageProfile,
                        @Cached StringMaterializeNode materializeNode) {
            // important: avoid materialization of native sequences
            if (storageProfile.profile(object.isNativeCharSequence())) {
                return object.getNativeCharSequence().isAsciiOnly() ? 1 : 0;
            }

            if (asciiEncoder == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                asciiEncoder = StandardCharsets.US_ASCII.newEncoder();
            }
            return doCheck(materializeNode.execute(object), asciiEncoder) ? 1 : 0;
        }
    }

    @CApiBuiltin(ret = UNSIGNED_INT, args = {PyASCIIObject}, call = Ignored)
    abstract static class Py_get_PyASCIIObject_state_compact extends CApiUnaryBuiltinNode {

        @Specialization
        static int get(@SuppressWarnings("unused") Object object) {
            return 0;
        }
    }

    @CApiBuiltin(ret = UNSIGNED_INT, args = {PyASCIIObject}, call = Ignored)
    abstract static class Py_get_PyASCIIObject_state_kind extends CApiUnaryBuiltinNode {

        @Specialization
        static int get(PString object,
                        @Cached ConditionProfile storageProfile) {
            // important: avoid materialization of native sequences
            if (storageProfile.profile(object.isNativeCharSequence())) {
                return object.getNativeCharSequence().getElementSize() & 0b111;
            }
            return SIZEOF_WCHAR_T.intValue() & 0b111;
        }
    }

    @CApiBuiltin(ret = UNSIGNED_INT, args = {PyASCIIObject}, call = Ignored)
    abstract static class Py_get_PyASCIIObject_state_ready extends CApiUnaryBuiltinNode {

        @Specialization
        static int get(@SuppressWarnings("unused") Object object) {
            return 1;
        }
    }

    @CApiBuiltin(ret = WCHAR_T_PTR, args = {PyASCIIObject}, call = Ignored)
    abstract static class Py_get_PyASCIIObject_wstr extends CApiUnaryBuiltinNode {

        @Specialization
        static Object get(Object object,
                        @Cached UnicodeAsWideCharNode asWideCharNode) {
            int elementSize = SIZEOF_WCHAR_T.intValue();
            return new PySequenceArrayWrapper(asWideCharNode.executeNativeOrder(object, elementSize), elementSize);
        }
    }

    @CApiBuiltin(ret = PyTypeObject, args = {PyCMethodObject}, call = Ignored)
    abstract static class Py_get_PyCMethodObject_mm_class extends CApiUnaryBuiltinNode {
        @Specialization
        static Object get(PBuiltinMethod object) {
            return object.getClassObject();
        }
    }

    @CApiBuiltin(ret = PyMethodDef, args = {PyCFunctionObject}, call = Ignored)
    abstract static class Py_get_PyCFunctionObject_m_ml extends CApiUnaryBuiltinNode {
        @Specialization
        static Object get(PythonObject object,
                        @CachedLibrary(limit = "3") DynamicObjectLibrary dylib) {
            Object methodDefPtr = dylib.getOrDefault(object, PythonCextMethodBuiltins.METHOD_DEF_PTR, null);
            if (methodDefPtr != null) {
                return methodDefPtr;
            }
            return new PyMethodDefWrapper(object);
        }
    }

    @CApiBuiltin(ret = PyObjectBorrowed, args = {PyCFunctionObject}, call = Ignored)
    abstract static class Py_get_PyCFunctionObject_m_module extends CApiUnaryBuiltinNode {
        @Specialization
        Object get(Object object,
                        @Cached PyObjectLookupAttr lookup) {
            Object module = lookup.execute(null, object, T___MODULE__);
            return module != PNone.NO_VALUE ? module : getNativeNull();
        }
    }

    @CApiBuiltin(ret = PyObjectBorrowed, args = {PyCFunctionObject}, call = Ignored)
    abstract static class Py_get_PyCFunctionObject_m_self extends CApiUnaryBuiltinNode {
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
    abstract static class Py_get_PyCFunctionObject_m_weakreflist extends CApiUnaryBuiltinNode {
        @Specialization
        static int get(@SuppressWarnings("unused") Object object) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @CApiBuiltin(ret = vectorcallfunc, args = {PyCFunctionObject}, call = Ignored)
    abstract static class Py_get_PyCFunctionObject_vectorcall extends CApiUnaryBuiltinNode {
        @Specialization
        static int get(@SuppressWarnings("unused") Object object) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @CApiBuiltin(ret = Pointer, args = {PyByteArrayObject}, call = Ignored)
    abstract static class Py_get_PyByteArrayObject_ob_start extends CApiUnaryBuiltinNode {

        @Specialization
        static Object doObStart(PByteArray object,
                        @Cached("createClassProfile()") ValueProfile classProfile) {
            SequenceStorage sequenceStorage = classProfile.profile(object.getSequenceStorage());
            assert !(sequenceStorage instanceof NativeObjectSequenceStorage);
            if (sequenceStorage instanceof NativeByteSequenceStorage) {
                return ((NativeByteSequenceStorage) sequenceStorage).getPtr();
            }
            return new PySequenceArrayWrapper(object, 1);
        }
    }

    @CApiBuiltin(ret = Py_ssize_t, args = {PyByteArrayObject}, call = Ignored)
    abstract static class Py_get_PyByteArrayObject_ob_exports extends CApiUnaryBuiltinNode {

        @Specialization
        static long get(PByteArray object) {
            return object.getExports();
        }
    }

    @CApiBuiltin(ret = Void, args = {PyByteArrayObject, Int}, call = Ignored)
    abstract static class Py_set_PyByteArrayObject_ob_exports extends CApiBinaryBuiltinNode {

        @Specialization
        static Object set(PByteArray object, int value) {
            object.setExports(value);
            return PNone.NO_VALUE;
        }
    }

    @CApiBuiltin(ret = Py_ssize_t, args = {PyCompactUnicodeObject}, call = Ignored)
    abstract static class Py_get_PyCompactUnicodeObject_wstr_length extends CApiUnaryBuiltinNode {

        @Specialization
        static long get(Object object,
                        @Cached UnicodeAsWideCharNode asWideCharNode) {
            long sizeofWchar = SIZEOF_WCHAR_T.longValue();
            PBytes result = asWideCharNode.executeNativeOrder(object, sizeofWchar);
            return result.getSequenceStorage().length() / sizeofWchar;
        }
    }

    @CApiBuiltin(ret = PyObjectBorrowed, args = {PyDescrObject}, call = Ignored)
    abstract static class Py_get_PyDescrObject_d_name extends CApiUnaryBuiltinNode {

        @Specialization
        static Object get(PBuiltinFunction object) {
            return object.getCApiName();
        }

        @Specialization
        static Object get(GetSetDescriptor object) {
            return object.getCApiName();
        }
    }

    @CApiBuiltin(ret = PyTypeObject, args = {PyDescrObject}, call = Ignored)
    abstract static class Py_get_PyDescrObject_d_type extends CApiUnaryBuiltinNode {

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
    abstract static class Py_get_PyFrameObject_f_lineno extends CApiUnaryBuiltinNode {
        @Specialization
        static int get(PFrame frame) {
            return frame.getLine();
        }
    }

    @CApiBuiltin(ret = Pointer, args = {PyGetSetDef}, call = Ignored)
    abstract static class Py_get_PyGetSetDef_closure extends CApiUnaryBuiltinNode {
        @Specialization
        static int get(@SuppressWarnings("unused") Object object) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @CApiBuiltin(ret = ConstCharPtrAsTruffleString, args = {PyGetSetDef}, call = Ignored)
    abstract static class Py_get_PyGetSetDef_doc extends CApiUnaryBuiltinNode {
        @Specialization
        Object get(PythonObject object,
                        @Cached PythonAbstractObject.PInteropGetAttributeNode getAttrNode,
                        @Cached AsCharPointerNode asCharPointerNode) {
            Object doc = getAttrNode.execute(object, T___DOC__);
            if (PGuards.isPNone(doc)) {
                return getNULL();
            } else {
                return asCharPointerNode.execute(doc);
            }
        }
    }

    @CApiBuiltin(ret = getter, args = {PyGetSetDef}, call = Ignored)
    abstract static class Py_get_PyGetSetDef_get extends CApiUnaryBuiltinNode {
        @Specialization
        static int get(@SuppressWarnings("unused") Object object) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @CApiBuiltin(ret = ConstCharPtrAsTruffleString, args = {PyGetSetDef}, call = Ignored)
    abstract static class Py_get_PyGetSetDef_name extends CApiUnaryBuiltinNode {
        @Specialization
        Object get(PythonObject object,
                        @Cached PythonAbstractObject.PInteropGetAttributeNode getAttrNode,
                        @Cached AsCharPointerNode asCharPointerNode) {
            Object name = getAttrNode.execute(object, T___NAME__);
            if (PGuards.isPNone(name)) {
                return getNULL();
            } else {
                return asCharPointerNode.execute(name);
            }
        }
    }

    @CApiBuiltin(ret = setter, args = {PyGetSetDef}, call = Ignored)
    abstract static class Py_get_PyGetSetDef_set extends CApiUnaryBuiltinNode {
        @Specialization
        static int get(@SuppressWarnings("unused") Object object) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @CApiBuiltin(ret = Pointer, args = {PyMethodDef}, call = Ignored)
    abstract static class Py_get_PyMethodDef_ml_doc extends CApiUnaryBuiltinNode {

        @Specialization
        Object get(PythonObject object,
                        @Cached ReadAttributeFromObjectNode getAttrNode,
                        @Cached CastToTruffleStringNode castToStringNode) {
            Object doc = getAttrNode.execute(object, T___DOC__);
            if (!PGuards.isPNone(doc)) {
                try {
                    return new CStringWrapper(castToStringNode.execute(doc));
                } catch (CannotCastException e) {
                    // fall through
                }
            }
            return getNULL();
        }
    }

    @CApiBuiltin(ret = Int, args = {PyMethodDef}, call = Ignored)
    abstract static class Py_get_PyMethodDef_ml_flags extends CApiUnaryBuiltinNode {
        @Specialization
        static int get(Object object) {
            if (object instanceof PBuiltinFunction) {
                return ((PBuiltinFunction) object).getFlags();
            } else if (object instanceof PBuiltinMethod) {
                return ((PBuiltinMethod) object).getBuiltinFunction().getFlags();
            }
            return 0;
        }
    }

    @CApiBuiltin(ret = PY_C_FUNCTION, args = {PyMethodDef}, call = Ignored)
    abstract static class Py_get_PyMethodDef_ml_meth extends CApiUnaryBuiltinNode {

        @TruffleBoundary
        private static Object createFunctionWrapper(Object object) {
            int flags = Py_get_PyMethodDef_ml_flags.get(object);
            PythonNativeWrapper wrapper;
            if (CExtContext.isMethNoArgs(flags)) {
                wrapper = PyProcsWrapper.createUnaryFuncWrapper(object);
            } else if (CExtContext.isMethO(flags)) {
                wrapper = PyProcsWrapper.createBinaryFuncWrapper(object);
            } else if (CExtContext.isMethVarargsWithKeywords(flags)) {
                wrapper = PyProcsWrapper.createVarargKeywordWrapper(object);
            } else if (CExtContext.isMethVarargs(flags)) {
                wrapper = PyProcsWrapper.createVarargWrapper(object);
            } else {
                throw CompilerDirectives.shouldNotReachHere("other signature " + Integer.toHexString(flags));
            }
            return wrapper;
        }

        @Specialization
        static Object getMethFromBuiltinFunction(PBuiltinFunction object) {
            PKeyword[] kwDefaults = object.getKwDefaults();
            for (int i = 0; i < kwDefaults.length; i++) {
                if (ExternalFunctionNodes.KW_CALLABLE.equals(kwDefaults[i].getName())) {
                    return kwDefaults[i].getValue();
                }
            }
            return createFunctionWrapper(object);
        }

        @Specialization
        static Object getMethFromBuiltinMethod(PBuiltinMethod object) {
            return getMethFromBuiltinFunction(object.getBuiltinFunction());
        }

        @Fallback
        Object getMeth(Object object) {
            return createFunctionWrapper(object);
        }
    }

    @CApiBuiltin(ret = Pointer, args = {PyMethodDef}, call = Ignored)
    abstract static class Py_get_PyMethodDef_ml_name extends CApiUnaryBuiltinNode {

        @Specialization
        Object getName(PythonObject object,
                        @Cached PythonAbstractObject.PInteropGetAttributeNode getAttrNode,
                        @Cached CastToTruffleStringNode castToStringNode) {
            Object name = getAttrNode.execute(object, SpecialAttributeNames.T___NAME__);
            if (!PGuards.isPNone(name)) {
                try {
                    return new CStringWrapper(castToStringNode.execute(name));
                } catch (CannotCastException e) {
                    // fall through
                }
            }
            return getNULL();
        }
    }

    @CApiBuiltin(ret = PyMethodDef, args = {PyMethodDescrObject}, call = Ignored)
    abstract static class Py_get_PyMethodDescrObject_d_method extends CApiUnaryBuiltinNode {

        @Specialization
        static Object get(PythonObject object) {
            return new PyMethodDefWrapper(object);
        }
    }

    @CApiBuiltin(ret = PyObjectBorrowed, args = {PyInstanceMethodObject}, call = Ignored)
    abstract static class Py_get_PyInstanceMethodObject_func extends CApiUnaryBuiltinNode {
        @Specialization
        static Object get(PDecoratedMethod object) {
            return object.getCallable();
        }
    }

    @CApiBuiltin(ret = PyObjectBorrowed, args = {PyMethodObject}, call = Ignored)
    abstract static class Py_get_PyMethodObject_im_func extends CApiUnaryBuiltinNode {
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
    abstract static class Py_get_PyMethodObject_im_self extends CApiUnaryBuiltinNode {

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
    abstract static class Py_get_PyModuleDef_m_doc extends CApiUnaryBuiltinNode {
        @Specialization
        static int get(@SuppressWarnings("unused") Object object) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @CApiBuiltin(ret = PyMethodDef, args = {PyModuleDef}, call = Ignored)
    abstract static class Py_get_PyModuleDef_m_methods extends CApiUnaryBuiltinNode {
        @Specialization
        static int get(@SuppressWarnings("unused") Object object) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @CApiBuiltin(ret = ConstCharPtrAsTruffleString, args = {PyModuleDef}, call = Ignored)
    abstract static class Py_get_PyModuleDef_m_name extends CApiUnaryBuiltinNode {
        @Specialization
        static int get(@SuppressWarnings("unused") Object object) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @CApiBuiltin(ret = Py_ssize_t, args = {PyModuleDef}, call = Ignored)
    abstract static class Py_get_PyModuleDef_m_size extends CApiUnaryBuiltinNode {
        @Specialization
        static int get(@SuppressWarnings("unused") Object object) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @CApiBuiltin(ret = PyModuleDef, args = {PyModuleObject}, call = Ignored)
    abstract static class Py_get_PyModuleObject_md_def extends CApiUnaryBuiltinNode {
        @Specialization
        static Object get(PythonModule object) {
            return object.getNativeModuleDef();
        }
    }

    @CApiBuiltin(ret = PyObjectBorrowed, args = {PyModuleObject}, call = Ignored)
    abstract static class Py_get_PyModuleObject_md_dict extends CApiUnaryBuiltinNode {
        @Specialization
        static Object get(Object object,
                        @Exclusive @Cached PythonAbstractObject.PInteropGetAttributeNode getDictNode) {
            return getDictNode.execute(object, SpecialAttributeNames.T___DICT__);
        }
    }

    @CApiBuiltin(ret = Pointer, args = {PyModuleObject}, call = Ignored)
    abstract static class Py_get_PyModuleObject_md_state extends CApiUnaryBuiltinNode {
        @Specialization
        static Object get(PythonModule object) {
            return object.getNativeModuleState();
        }
    }

    @CApiBuiltin(ret = Py_ssize_t, args = {PyObjectWrapper}, call = Ignored)
    abstract static class Py_get_PyObject_ob_refcnt extends CApiUnaryBuiltinNode {

        @Specialization
        static Object get(PythonNativeWrapper wrapper) {
            return wrapper.getRefCount();
        }
    }

    @CApiBuiltin(ret = PyTypeObject, args = {PyObject}, call = Ignored)
    abstract static class Py_get_PyObject_ob_type extends CApiUnaryBuiltinNode {

        @Specialization
        Object get(Object object,
                        @Cached InlinedGetClassNode getClassNode) {
            Object result = getClassNode.execute(this, object);
            assert !(result instanceof Integer);
            return result;
        }
    }

    @CApiBuiltin(ret = Py_ssize_t, args = {PySetObject}, call = Ignored)
    abstract static class Py_get_PySetObject_used extends CApiUnaryBuiltinNode {

        @Specialization
        static long get(PBaseSet object,
                        @Cached HashingStorageLen lenNode) {
            return lenNode.execute(object.getDictStorage());
        }
    }

    @CApiBuiltin(ret = PyObject, args = {PySliceObject}, call = Ignored)
    abstract static class Py_get_PySliceObject_start extends CApiUnaryBuiltinNode {
        @Specialization
        static Object doStart(PSlice object) {
            return object.getStart();
        }
    }

    @CApiBuiltin(ret = PyObject, args = {PySliceObject}, call = Ignored)
    abstract static class Py_get_PySliceObject_step extends CApiUnaryBuiltinNode {
        @Specialization
        static Object doStep(PSlice object) {
            return object.getStep();
        }
    }

    @CApiBuiltin(ret = PyObject, args = {PySliceObject}, call = Ignored)
    abstract static class Py_get_PySliceObject_stop extends CApiUnaryBuiltinNode {
        @Specialization
        static Object doStop(PSlice object) {
            return object.getStop();
        }
    }

    @CApiBuiltin(ret = PyObjectBorrowed, args = {PyThreadState}, call = Ignored)
    abstract static class Py_get_PyThreadState_dict extends CApiUnaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        static Object get(PThreadState receiver,
                        @Cached PythonObjectFactory factory) {
            PDict threadStateDict = receiver.getThreadState().getDict();
            if (threadStateDict == null) {
                threadStateDict = factory.createDict();
                receiver.getThreadState().setDict(threadStateDict);
            }
            return threadStateDict;
        }
    }

    @CApiBuiltin(ret = Pointer, args = {PyUnicodeObject}, call = Ignored)
    abstract static class Py_get_PyUnicodeObject_data extends CApiUnaryBuiltinNode {

        @Specialization
        static Object get(PString object,
                        @Cached UnicodeAsWideCharNode asWideCharNode) {
            int elementSize = SIZEOF_WCHAR_T.intValue();

            if (object.isNativeCharSequence()) {
                // in this case, we can just return the pointer
                return object.getNativeCharSequence().getPtr();
            }
            return new PySequenceArrayWrapper(asWideCharNode.executeNativeOrder(object, elementSize), elementSize);
        }
    }

    @CApiBuiltin(ret = Py_ssize_t, args = {PyVarObject}, call = Ignored)
    abstract static class Py_get_PyVarObject_ob_size extends CApiUnaryBuiltinNode {

        @Specialization
        static long get(Object object,
                        @Cached ObSizeNode obSizeNode) {
            return obSizeNode.execute(object);
        }
    }

    @CApiBuiltin(ret = Void, args = {PyFrameObject, Int}, call = Ignored)
    abstract static class Py_set_PyFrameObject_f_lineno extends CApiBinaryBuiltinNode {
        @Specialization
        static Object set(PFrame frame, int value) {
            frame.setLine(value);
            return PNone.NONE;
        }
    }

    @CApiBuiltin(ret = Void, args = {PyModuleObject, PyModuleDef}, call = Ignored)
    abstract static class Py_set_PyModuleObject_md_def extends CApiBinaryBuiltinNode {
        @Specialization
        static Object set(PythonModule object, Object value) {
            object.setNativeModuleDef(value);
            return PNone.NO_VALUE;
        }
    }

    @CApiBuiltin(ret = Void, args = {PyModuleObject, Pointer}, call = Ignored)
    abstract static class Py_set_PyModuleObject_md_state extends CApiBinaryBuiltinNode {
        @Specialization
        static Object set(PythonModule object, Object value) {
            object.setNativeModuleState(value);
            return PNone.NO_VALUE;
        }
    }

    @CApiBuiltin(ret = Void, args = {PyObjectWrapper, Py_ssize_t}, call = Ignored)
    abstract static class Py_set_PyObject_ob_refcnt extends CApiBinaryBuiltinNode {

        @Specialization
        static Object set(PythonNativeWrapper wrapper, long value) {
            CApiTransitions.setRefCount(wrapper, value);
            return PNone.NONE;
        }
    }

    @CApiBuiltin(ret = Void, args = {PyTypeObject, PyBufferProcs}, call = Ignored)
    abstract static class Py_set_PyTypeObject_tp_as_buffer extends CApiBinaryBuiltinNode {

        @Specialization(guards = "isPythonClass(object)")
        static Object set(Object object, Object bufferProcs,
                        @Cached WriteAttributeToObjectNode writeAttrNode) {
            writeAttrNode.execute(object, TypeBuiltins.TYPE_AS_BUFFER, bufferProcs);
            return PNone.NO_VALUE;
        }
    }

    @CApiBuiltin(ret = Void, args = {PyTypeObject, PyObject}, call = Ignored)
    abstract static class Py_set_PyTypeObject_tp_dict extends CApiBinaryBuiltinNode {

        @Specialization
        static Object doTpDict(PythonManagedClass object, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached GetDictIfExistsNode getDict,
                        @Cached SetDictNode setDict,
                        @Cached WriteAttributeToObjectNode writeAttrNode,
                        @Cached HashingStorageGetIterator getIterator,
                        @Cached HashingStorageIteratorNext itNext,
                        @Cached HashingStorageIteratorKey itKey,
                        @Cached HashingStorageIteratorValue itValue,
                        @Cached IsBuiltinObjectProfile isPrimitiveDictProfile1,
                        @Cached IsBuiltinObjectProfile isPrimitiveDictProfile2) {
            if (isBuiltinDict(inliningTarget, isPrimitiveDictProfile1, isPrimitiveDictProfile2, value)) {
                // special and fast case: commit items and change store
                PDict d = (PDict) value;
                HashingStorage storage = d.getDictStorage();
                HashingStorageIterator it = getIterator.execute(storage);
                while (itNext.execute(storage, it)) {
                    writeAttrNode.execute(object, itKey.execute(storage, it), itValue.execute(storage, it));
                }
                PDict existing = getDict.execute(object);
                if (existing != null) {
                    d.setDictStorage(existing.getDictStorage());
                } else {
                    d.setDictStorage(new DynamicObjectStorage(object.getStorage()));
                }
                setDict.execute(object, d);
            } else {
                // TODO custom mapping object
            }
            return PNone.NO_VALUE;
        }

        private static boolean isBuiltinDict(Node inliningTarget, IsBuiltinObjectProfile isPrimitiveDictProfile1, IsBuiltinObjectProfile isPrimitiveDictProfile2, Object value) {
            return value instanceof PDict &&
                            (isPrimitiveDictProfile1.profileObject(inliningTarget, value, PythonBuiltinClassType.PDict) ||
                                            isPrimitiveDictProfile2.profileObject(inliningTarget, value, PythonBuiltinClassType.StgDict));
        }
    }

    @CApiBuiltin(ret = Void, args = {PyTypeObject, Py_ssize_t}, call = Ignored)
    abstract static class Py_set_PyTypeObject_tp_dictoffset extends CApiBinaryBuiltinNode {

        @Specialization
        static Object doTpDictoffset(PythonManagedClass object, long value,
                        @Bind("this") Node inliningTarget,
                        @Cached TypeNodes.SetDictOffsetNode setDictOffsetNode) {
            setDictOffsetNode.execute(inliningTarget, object, value);
            return PNone.NO_VALUE;
        }

    }

    @CApiBuiltin(ret = Void, args = {PyTypeObject, PyObject}, call = Ignored)
    abstract static class Py_set_PyTypeObject_tp_subclasses extends CApiBinaryBuiltinNode {

        @GenerateUncached
        abstract static class EachSubclassAdd extends HashingStorageForEachCallback<Set<PythonAbstractClass>> {

            @Override
            public abstract Set<PythonAbstractClass> execute(Frame frame, Node inliningTarget, HashingStorage storage, HashingStorageIterator it, Set<PythonAbstractClass> subclasses);

            @Specialization
            public Set<PythonAbstractClass> doIt(Frame frame, @SuppressWarnings("unused") Node inliningTarget, HashingStorage storage, HashingStorageIterator it, Set<PythonAbstractClass> subclasses,
                            @Cached HashingStorageIteratorKey itKey,
                            @Cached HashingStorageIteratorKeyHash itKeyHash,
                            @Cached HashingStorageGetItemWithHash getItemNode) {
                long hash = itKeyHash.execute(storage, it);
                Object key = itKey.execute(storage, it);
                setAdd(subclasses, (PythonClass) getItemNode.execute(frame, storage, key, hash));
                return subclasses;
            }

            @TruffleBoundary
            protected static void setAdd(Set<PythonAbstractClass> set, PythonClass cls) {
                set.add(cls);
            }
        }

        @Specialization
        static Object doTpSubclasses(PythonClass object, PDict dict,
                        @Cached GetSubclassesNode getSubclassesNode,
                        @Cached EachSubclassAdd eachNode,
                        @Cached HashingStorageForEach forEachNode) {
            HashingStorage storage = dict.getDictStorage();
            Set<PythonAbstractClass> subclasses = getSubclassesNode.execute(object);
            forEachNode.execute(null, storage, eachNode, subclasses);
            return PNone.NO_VALUE;
        }
    }

    @CApiBuiltin(name = "Py_get_dummy", ret = Pointer, args = {Pointer}, call = Ignored)
    abstract static class PyGetSlotDummyPtr extends CApiUnaryBuiltinNode {

        @Specialization
        Object get(@SuppressWarnings("unused") Object object) {
            return getNULL();
        }
    }

    @CApiBuiltin(name = "Py_set_PyVarObject_ob_size", ret = Void, args = {PyVarObject, Py_ssize_t}, call = Ignored)
    @CApiBuiltin(name = "Py_set_PyTypeObject_tp_getattr", ret = Void, args = {PyTypeObject, getattrfunc}, call = Ignored)
    @CApiBuiltin(name = "Py_set_PyTypeObject_tp_getattro", ret = Void, args = {PyTypeObject, getattrofunc}, call = Ignored)
    @CApiBuiltin(name = "Py_set_PyTypeObject_tp_setattr", ret = Void, args = {PyTypeObject, setattrfunc}, call = Ignored)
    @CApiBuiltin(name = "Py_set_PyTypeObject_tp_setattro", ret = Void, args = {PyTypeObject, setattrofunc}, call = Ignored)
    @CApiBuiltin(name = "Py_set_PyTypeObject_tp_finalize", ret = Void, args = {PyTypeObject, destructor}, call = Ignored)
    @CApiBuiltin(name = "Py_set_PyTypeObject_tp_iter", ret = Void, args = {PyTypeObject, getiterfunc}, call = Ignored)
    @CApiBuiltin(name = "Py_set_PyTypeObject_tp_iternext", ret = Void, args = {PyTypeObject, iternextfunc}, call = Ignored)
    @CApiBuiltin(name = "Py_set_PyTypeObject_tp_base", ret = Void, args = {PyTypeObject, PyTypeObject}, call = Ignored)
    @CApiBuiltin(name = "Py_set_PyTypeObject_tp_bases", ret = Void, args = {PyTypeObject, PyObject}, call = Ignored)
    @CApiBuiltin(name = "Py_set_PyTypeObject_tp_clear", ret = Void, args = {PyTypeObject, inquiry}, call = Ignored)
    @CApiBuiltin(name = "Py_set_PyTypeObject_tp_mro", ret = Void, args = {PyTypeObject, PyObject}, call = Ignored)
    @CApiBuiltin(name = "Py_set_PyTypeObject_tp_new", ret = Void, args = {PyTypeObject, newfunc}, call = Ignored)
    @CApiBuiltin(name = "Py_set_PyTypeObject_tp_traverse", ret = Void, args = {PyTypeObject, traverseproc}, call = Ignored)
    @CApiBuiltin(name = "Py_set_PyTypeObject_tp_weaklistoffset", ret = Void, args = {PyTypeObject, Py_ssize_t}, call = Ignored)
    abstract static class PySetSlotDummyPtr extends CApiBinaryBuiltinNode {

        @SuppressWarnings("unused")
        @Specialization
        static Object set(Object object, Object value) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }
}
